package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.ChannelType;
import com.karuta.matchtracker.entity.LineChannel;
import com.karuta.matchtracker.entity.LineChannelAssignment;
import com.karuta.matchtracker.entity.LineChannelAssignment.AssignmentStatus;
import com.karuta.matchtracker.entity.LineMessageLog.LineNotificationType;
import com.karuta.matchtracker.entity.LineNotificationPreference;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.repository.LineChannelAssignmentRepository;
import com.karuta.matchtracker.repository.LineChannelRepository;
import com.karuta.matchtracker.repository.LineNotificationPreferenceRepository;
import com.karuta.matchtracker.repository.MentorRelationshipRepository;
import com.karuta.matchtracker.repository.PlayerOrganizationRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * LineNotificationService の試合動画登録通知（{@code sendMatchVideoRegisteredNotification}）に
 * 関する単体テスト。
 *
 * 当事者からの登録者除外・第三者登録時の両者送信・通知設定OFFのスキップを検証する。
 * LINE チャネル解決（{@code resolveChannel}）は実装ロジックをそのまま通し、
 * リポジトリ層をモックして到達分岐を検証する。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LineNotificationService 試合動画登録通知")
class LineNotificationServiceTest {

    @Mock private LineChannelRepository lineChannelRepository;
    @Mock private LineChannelAssignmentRepository lineChannelAssignmentRepository;
    @Mock private LineNotificationPreferenceRepository lineNotificationPreferenceRepository;
    @Mock private LineMessageLogService lineMessageLogService;
    @Mock private LineMessagingService lineMessagingService;
    @Mock private PracticeSessionRepository practiceSessionRepository;
    @Mock private PracticeParticipantRepository practiceParticipantRepository;
    @Mock private PlayerOrganizationRepository playerOrganizationRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private LotteryQueryService lotteryQueryService;
    @Mock private VenueRepository venueRepository;
    @Mock private MentorRelationshipRepository mentorRelationshipRepository;

    private LineNotificationService service;

    private static final LocalDate MATCH_DATE = LocalDate.of(2026, 6, 12);

    @BeforeEach
    void setUp() {
        service = new LineNotificationService(
                lineChannelRepository,
                lineChannelAssignmentRepository,
                lineNotificationPreferenceRepository,
                lineMessageLogService,
                lineMessagingService,
                practiceSessionRepository,
                practiceParticipantRepository,
                playerOrganizationRepository,
                playerRepository,
                lotteryQueryService,
                venueRepository,
                mentorRelationshipRepository);

        // 登録者名・対戦カード名の解決に使われる（全テスト共通）
        when(playerRepository.findAllById(any())).thenReturn(List.of(
                Player.builder().id(1L).name("山田太郎").build(),
                Player.builder().id(2L).name("佐藤花子").build(),
                Player.builder().id(3L).name("第三者").build()));
    }

    /** LINKED 状態のアサインメントを返すよう仕込む（チャネルも LINKED で月間上限未満）。 */
    private void linkPlayer(Long playerId, LineNotificationPreference pref) {
        LineChannelAssignment assignment = LineChannelAssignment.builder()
                .id(playerId)
                .lineChannelId(900L + playerId)
                .playerId(playerId)
                .lineUserId("U" + playerId)
                .channelType(ChannelType.PLAYER)
                .status(AssignmentStatus.LINKED)
                .build();
        when(lineChannelAssignmentRepository.findByPlayerIdAndChannelTypeAndStatusIn(
                eq(playerId), eq(ChannelType.PLAYER), any()))
                .thenReturn(Optional.of(assignment));

        LineChannel channel = LineChannel.builder()
                .id(900L + playerId)
                .channelAccessToken("token" + playerId)
                .status(LineChannel.ChannelStatus.LINKED)
                .monthlyMessageCount(0)
                .build();
        when(lineChannelRepository.findById(900L + playerId)).thenReturn(Optional.of(channel));

        if (pref != null) {
            when(lineNotificationPreferenceRepository.findByPlayerId(playerId))
                    .thenReturn(List.of(pref));
        }
    }

    private LineNotificationPreference prefWithMatchVideo(Long playerId, boolean enabled) {
        return LineNotificationPreference.builder()
                .playerId(playerId)
                .organizationId(1L)
                .matchVideoRegistered(enabled)
                .build();
    }

    @Test
    @DisplayName("登録者=player1 のとき、登録者本人は除外され player2 のみが通知対象になる")
    void testRegistrantExcludedWhenParty() {
        // registrant=1（player1）。player2 は未連携にして resolveChannel が早期 null → SKIPPED。
        when(lineChannelAssignmentRepository.findByPlayerIdAndChannelTypeAndStatusIn(
                eq(2L), eq(ChannelType.PLAYER), any()))
                .thenReturn(Optional.empty());

        service.sendMatchVideoRegisteredNotification(1L, 1L, 2L, MATCH_DATE, 1, 100L);

        // player2 についてはアサインメント探索が行われる（送信試行に到達）
        verify(lineChannelAssignmentRepository).findByPlayerIdAndChannelTypeAndStatusIn(
                eq(2L), eq(ChannelType.PLAYER), any());
        // 登録者 player1 については一切探索されない（通知対象外）
        verify(lineChannelAssignmentRepository, never()).findByPlayerIdAndChannelTypeAndStatusIn(
                eq(1L), any(), any());
    }

    @Test
    @DisplayName("第三者（非当事者）が登録 → player1 / player2 の両方が通知対象になる")
    void testThirdPartyNotifiesBoth() {
        // registrant=3（非当事者）。両者とも未連携で SKIPPED だが、両者へ送信試行が走ることを検証。
        when(lineChannelAssignmentRepository.findByPlayerIdAndChannelTypeAndStatusIn(
                any(), eq(ChannelType.PLAYER), any()))
                .thenReturn(Optional.empty());

        service.sendMatchVideoRegisteredNotification(3L, 1L, 2L, MATCH_DATE, 1, null);

        verify(lineChannelAssignmentRepository).findByPlayerIdAndChannelTypeAndStatusIn(
                eq(1L), eq(ChannelType.PLAYER), any());
        verify(lineChannelAssignmentRepository).findByPlayerIdAndChannelTypeAndStatusIn(
                eq(2L), eq(ChannelType.PLAYER), any());
    }

    @Test
    @DisplayName("通知設定がOFFの選手には送信されない（push が呼ばれずSKIPPEDログのみ）")
    void testPreferenceOffSkipsSend() {
        // registrant=3。player1 は LINKED だが matchVideoRegistered=OFF。player2 は未連携。
        linkPlayer(1L, prefWithMatchVideo(1L, false));
        when(lineChannelAssignmentRepository.findByPlayerIdAndChannelTypeAndStatusIn(
                eq(2L), eq(ChannelType.PLAYER), any()))
                .thenReturn(Optional.empty());

        service.sendMatchVideoRegisteredNotification(3L, 1L, 2L, MATCH_DATE, 1, 100L);

        // OFF のため push は一度も呼ばれない
        verify(lineMessagingService, never()).sendPushMessage(anyString(), anyString(), anyString());
        // SKIPPED ログが「通知設定がOFF」で記録される
        verify(lineMessageLogService).save(
                eq(901L), eq(1L), eq(LineNotificationType.MATCH_VIDEO_REGISTERED),
                anyString(), eq(com.karuta.matchtracker.entity.LineMessageLog.MessageStatus.SKIPPED),
                eq("通知設定がOFF"), isNull());
    }

    @Test
    @DisplayName("設定ONの当事者にはメッセージ本文（登録者名・対戦カード・リンク）が送信される")
    void testEnabledRecipientReceivesMessage() {
        // registrant=3。player1 は LINKED かつ matchVideoRegistered=ON。matchId ありで /matches リンク。
        linkPlayer(1L, prefWithMatchVideo(1L, true));
        when(lineChannelAssignmentRepository.findByPlayerIdAndChannelTypeAndStatusIn(
                eq(2L), eq(ChannelType.PLAYER), any()))
                .thenReturn(Optional.empty());
        when(lineMessagingService.sendPushMessage(anyString(), anyString(), anyString()))
                .thenReturn(true);

        service.sendMatchVideoRegisteredNotification(3L, 1L, 2L, MATCH_DATE, 1, 555L);

        // player1 の LINE userId 宛にメッセージ送信。本文に登録者名・対戦カード・matchリンクを含む
        verify(lineMessagingService).sendPushMessage(eq("token1"), eq("U1"), argThat(msg ->
                msg.contains("第三者さん")
                        && msg.contains("山田太郎 vs 佐藤花子")
                        && msg.contains("/matches/555")));
    }
}
