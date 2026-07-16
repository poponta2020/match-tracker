package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.ChannelType;
import com.karuta.matchtracker.entity.LineBroadcastGroup;
import com.karuta.matchtracker.entity.LineChannel;
import com.karuta.matchtracker.entity.LineChannel.ChannelStatus;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.repository.LineChannelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CardDivisionBroadcastService の単体テスト。
 * AC-4（選択決定性）/ AC-5（1体消費＋即時加算）/ AC-6（二重送信防止）/ AC-8（団体分離）/ AC-9（枯渇スキップ）。
 * LINE 送信・送信ログ操作はモック。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CardDivisionBroadcastService テスト")
class CardDivisionBroadcastServiceTest {

    @Mock private CardDivisionTextService cardDivisionTextService;
    @Mock private LineChannelRepository lineChannelRepository;
    @Mock private LineMessagingService lineMessagingService;
    @Mock private LineBroadcastSendService lineBroadcastSendService;

    @InjectMocks private CardDivisionBroadcastService service;

    private static final Long GROUP_ID = 1L;
    private static final Long ORG = 2L;
    private static final Long SESSION_ID = 100L;
    private final LocalDateTime now = LocalDateTime.of(2026, 7, 16, 8, 0);

    private LineChannel bot(long id, int monthlyCount) {
        return LineChannel.builder()
                .id(id)
                .lineChannelId("line-" + id)
                .channelSecret("s")
                .channelAccessToken("token-" + id)
                .channelType(ChannelType.GROUP)
                .status(ChannelStatus.AVAILABLE)
                .broadcastGroupId(GROUP_ID)
                .lineGroupId("G-" + id)
                .monthlyMessageCount(monthlyCount)
                .build();
    }

    private LineBroadcastGroup group(Integer expectedRecipient) {
        return LineBroadcastGroup.builder()
                .id(GROUP_ID).organizationId(ORG).name("北大全体").enabled(true)
                .expectedRecipientCount(expectedRecipient).build();
    }

    private PracticeSession session(Long org) {
        return PracticeSession.builder()
                .id(SESSION_ID).organizationId(org).sessionDate(LocalDate.of(2026, 7, 16))
                .totalMatches(4).build();
    }

    @BeforeEach
    void setUp() {
        when(cardDivisionTextService.buildTextForSession(any())).thenReturn("7/16(木) 会場\n1試合目 ...");
    }

    @Nested
    @DisplayName("selectBot 純関数（AC-4）")
    class SelectBot {
        @Test
        @DisplayName("残枠のあるbotのうち当月送信数が最大の1体（使い切ってから次へ）")
        void picksMostUsedEligible() {
            List<LineChannel> candidates = List.of(bot(1, 0), bot(2, 130), bot(3, 199));
            // expected=70 → eligible: id1(0+70=70), id2(130+70=200) ; id3(199+70=269>200) 除外
            Optional<LineChannel> picked = CardDivisionBroadcastService.selectBot(candidates, 70);
            assertThat(picked).isPresent();
            assertThat(picked.get().getId()).isEqualTo(2L); // 最大消費(130) をまず使い切る
        }

        @Test
        @DisplayName("全botが枠不足なら空")
        void emptyWhenAllExhausted() {
            List<LineChannel> candidates = List.of(bot(1, 150), bot(2, 199));
            assertThat(CardDivisionBroadcastService.selectBot(candidates, 70)).isEmpty();
        }

        @Test
        @DisplayName("当月送信数が同数なら id 昇順で決定")
        void tieBreakByIdAsc() {
            List<LineChannel> candidates = List.of(bot(3, 50), bot(1, 50), bot(2, 50));
            Optional<LineChannel> picked = CardDivisionBroadcastService.selectBot(candidates, 70);
            assertThat(picked).isPresent();
            assertThat(picked.get().getId()).isEqualTo(1L);
        }
    }

    @Test
    @DisplayName("AC-5: 1体のみ送信し、その bot の当月消費が想定受信数分だけ即時加算される")
    void sendsWithSingleBotAndIncrements() {
        when(lineChannelRepository.findByBroadcastGroupIdAndChannelType(GROUP_ID, ChannelType.GROUP))
                .thenReturn(List.of(bot(1, 0), bot(2, 130)));
        when(lineBroadcastSendService.hasBlockingSend(GROUP_ID, SESSION_ID)).thenReturn(false);
        when(lineBroadcastSendService.tryAcquire(eq(GROUP_ID), eq(SESSION_ID), anyLong(), anyInt()))
                .thenReturn(true);
        when(lineMessagingService.sendPushMessage(anyString(), anyString(), anyString())).thenReturn(true);

        service.processGroupBroadcast(group(70), session(ORG), now);

        // 選択は id2（消費130を使い切る）1体のみ
        verify(lineBroadcastSendService).tryAcquire(GROUP_ID, SESSION_ID, 2L, 70);
        verify(lineMessagingService, times(1)).sendPushMessage(eq("token-2"), eq("G-2"), anyString());
        verify(lineBroadcastSendService).markSucceeded(GROUP_ID, SESSION_ID);
        verify(lineBroadcastSendService).incrementChannelMonthlyCount(2L, 70);
        // 他 bot は消費されない
        verify(lineBroadcastSendService, never()).incrementChannelMonthlyCount(eq(1L), anyInt());
    }

    @Test
    @DisplayName("AC-6: 既に送信済み/送信中なら送信しない")
    void skipsWhenBlockingSendExists() {
        when(lineBroadcastSendService.hasBlockingSend(GROUP_ID, SESSION_ID)).thenReturn(true);

        service.processGroupBroadcast(group(70), session(ORG), now);

        verify(lineChannelRepository, never()).findByBroadcastGroupIdAndChannelType(anyLong(), any());
        verify(lineBroadcastSendService, never()).tryAcquire(anyLong(), anyLong(), anyLong(), anyInt());
        verify(lineMessagingService, never()).sendPushMessage(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("AC-6: tryAcquire が false（並行/前回が確保済み）なら送信しない")
    void skipsWhenAcquireLost() {
        when(lineChannelRepository.findByBroadcastGroupIdAndChannelType(GROUP_ID, ChannelType.GROUP))
                .thenReturn(List.of(bot(1, 0)));
        when(lineBroadcastSendService.hasBlockingSend(GROUP_ID, SESSION_ID)).thenReturn(false);
        when(lineBroadcastSendService.tryAcquire(anyLong(), anyLong(), anyLong(), anyInt())).thenReturn(false);

        service.processGroupBroadcast(group(70), session(ORG), now);

        verify(lineMessagingService, never()).sendPushMessage(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("AC-9: 全bot枯渇なら送信せず SKIPPED を記録する（課金なし）")
    void recordsSkippedWhenExhausted() {
        when(lineChannelRepository.findByBroadcastGroupIdAndChannelType(GROUP_ID, ChannelType.GROUP))
                .thenReturn(List.of(bot(1, 150), bot(2, 199)));
        when(lineBroadcastSendService.hasBlockingSend(GROUP_ID, SESSION_ID)).thenReturn(false);
        when(lineBroadcastSendService.hasSkippedSince(anyLong(), anyLong(), any())).thenReturn(false);

        service.processGroupBroadcast(group(70), session(ORG), now);

        verify(lineBroadcastSendService, never()).tryAcquire(anyLong(), anyLong(), anyLong(), anyInt());
        verify(lineMessagingService, never()).sendPushMessage(anyString(), anyString(), anyString());
        verify(lineBroadcastSendService).recordSkipped(eq(GROUP_ID), eq(SESSION_ID), anyString());
    }

    @Test
    @DisplayName("AC-8: group と session の団体が異なれば送信しない（防御）")
    void refusesCrossOrg() {
        service.processGroupBroadcast(group(70), session(999L), now);

        verify(lineBroadcastSendService, never()).releaseStale(any());
        verify(lineBroadcastSendService, never()).tryAcquire(anyLong(), anyLong(), anyLong(), anyInt());
        verify(lineMessagingService, never()).sendPushMessage(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("bot未割当/グループID未捕捉なら SKIPPED（未参加）")
    void recordsSkippedWhenNoCandidates() {
        LineChannel noGroupId = bot(1, 0);
        noGroupId.setLineGroupId(null);
        when(lineChannelRepository.findByBroadcastGroupIdAndChannelType(GROUP_ID, ChannelType.GROUP))
                .thenReturn(List.of(noGroupId));
        when(lineBroadcastSendService.hasBlockingSend(GROUP_ID, SESSION_ID)).thenReturn(false);
        when(lineBroadcastSendService.hasSkippedSince(anyLong(), anyLong(), any())).thenReturn(false);

        service.processGroupBroadcast(group(70), session(ORG), now);

        verify(lineBroadcastSendService).recordSkipped(eq(GROUP_ID), eq(SESSION_ID), anyString());
        verify(lineMessagingService, never()).sendPushMessage(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("想定受信数はグループ設定値を優先し、人数取得APIを呼ばない")
    void usesConfiguredExpectedRecipient() {
        when(lineChannelRepository.findByBroadcastGroupIdAndChannelType(GROUP_ID, ChannelType.GROUP))
                .thenReturn(List.of(bot(1, 0)));
        when(lineBroadcastSendService.hasBlockingSend(GROUP_ID, SESSION_ID)).thenReturn(false);
        when(lineBroadcastSendService.tryAcquire(anyLong(), anyLong(), anyLong(), anyInt())).thenReturn(true);
        when(lineMessagingService.sendPushMessage(anyString(), anyString(), anyString())).thenReturn(true);

        service.processGroupBroadcast(group(70), session(ORG), now);

        verify(lineMessagingService, never()).getGroupMemberCount(anyString(), anyString());
        verify(lineBroadcastSendService).tryAcquire(GROUP_ID, SESSION_ID, 1L, 70);
    }

    @Test
    @DisplayName("想定受信数の設定が無ければ人数取得APIで解決する")
    void resolvesExpectedFromApiWhenUnset() {
        when(lineChannelRepository.findByBroadcastGroupIdAndChannelType(GROUP_ID, ChannelType.GROUP))
                .thenReturn(List.of(bot(1, 0)));
        when(lineBroadcastSendService.hasBlockingSend(GROUP_ID, SESSION_ID)).thenReturn(false);
        when(lineMessagingService.getGroupMemberCount("token-1", "G-1")).thenReturn(68);
        when(lineBroadcastSendService.tryAcquire(anyLong(), anyLong(), anyLong(), anyInt())).thenReturn(true);
        when(lineMessagingService.sendPushMessage(anyString(), anyString(), anyString())).thenReturn(true);

        service.processGroupBroadcast(group(null), session(ORG), now);

        verify(lineBroadcastSendService).tryAcquire(GROUP_ID, SESSION_ID, 1L, 68);
        verify(lineBroadcastSendService).incrementChannelMonthlyCount(1L, 68);
    }
}
