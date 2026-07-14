package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.ChannelType;
import com.karuta.matchtracker.entity.LineChannel;
import com.karuta.matchtracker.entity.LineChannelAssignment;
import com.karuta.matchtracker.entity.LineChannelAssignment.AssignmentStatus;
import com.karuta.matchtracker.entity.LineMessageLog.LineNotificationType;
import com.karuta.matchtracker.entity.LineNotificationPreference;
import com.karuta.matchtracker.entity.PlayerOrganization;
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

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 札分けリマインダー送信（{@code sendCardDivisionReminder}）の単体テスト。
 *
 * <ul>
 *   <li>AC-6: 送信対象は「セッションの団体で購読 ON（per-org）× LINE 連携済み」のみ
 *       （未購読・未連携はスキップ）。</li>
 *   <li>AC-8: 同一 (セッション, プレイヤー) では {@code tryAcquireSendRight} により二重送信されない。</li>
 * </ul>
 * resolveChannel は実ロジックを通し、リポジトリ層をモックして到達分岐を検証する。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LineNotificationService 札分けリマインダー送信")
class LineNotificationServiceCardDivisionSendTest {

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

    private static final Long ORG = 1L;
    private static final Long SESSION_ID = 555L;
    private static final String TEXT = "【7/5 かでる2・7】\n1試合目：一の位1.3.5.6.7";

    @BeforeEach
    void setUp() {
        service = new LineNotificationService(
                lineChannelRepository, lineChannelAssignmentRepository, lineNotificationPreferenceRepository,
                lineMessageLogService, lineMessagingService, practiceSessionRepository,
                practiceParticipantRepository, playerOrganizationRepository, playerRepository,
                lotteryQueryService, venueRepository, mentorRelationshipRepository);
    }

    @Test
    @DisplayName("AC-6: 購読ON×連携済みのみ送信（未購読・未連携はスキップ）")
    void sendsOnlyToLinkedSubscribers() {
        // p1: 購読ON＋連携済み → 送信 / p2: 未購読 → 除外 / p3: 購読ON だが未連携 → resolveChannel で除外
        orgMembers(11L, 12L, 13L);
        subscribe(11L, true);
        subscribe(12L, false);
        subscribe(13L, true);
        linkPlayer(11L);
        linkPlayer(13L, false); // 未連携
        // 送信権は確保できる・送信成功
        when(lineMessageLogService.tryAcquireSendRight(anyLong(), anyLong(), any(), any(), any()))
                .thenReturn(true);
        when(lineMessagingService.sendPushMessage(any(), any(), any())).thenReturn(true);

        service.sendCardDivisionReminder(SESSION_ID, ORG, TEXT);

        // p1 のみ実送信
        verify(lineMessagingService, times(1)).sendPushMessage(eq("token11"), eq("U11"), eq(TEXT));
        verify(lineMessagingService, never()).sendPushMessage(eq("token12"), any(), any());
        verify(lineMessagingService, never()).sendPushMessage(eq("token13"), any(), any());
        // dedupeKey = sessionId で送信権確保
        verify(lineMessageLogService).tryAcquireSendRight(
                anyLong(), eq(11L), eq(LineNotificationType.CARD_DIVISION_REMINDER), eq(TEXT), eq("555"));
    }

    @Test
    @DisplayName("AC-8: 送信権を確保できない（既送信）なら実送信しない＝二重送信されない")
    void doesNotResendWhenAlreadySent() {
        orgMembers(11L);
        subscribe(11L, true);
        linkPlayer(11L);
        // 既に送信済み（RESERVED/SUCCESS が存在）→ 送信権を確保できない
        when(lineMessageLogService.tryAcquireSendRight(anyLong(), anyLong(), any(), any(), any()))
                .thenReturn(false);

        service.sendCardDivisionReminder(SESSION_ID, ORG, TEXT);

        verify(lineMessagingService, never()).sendPushMessage(any(), any(), any());
    }

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    private void orgMembers(Long... playerIds) {
        List<PlayerOrganization> members = java.util.Arrays.stream(playerIds)
                .map(pid -> PlayerOrganization.builder().playerId(pid).organizationId(ORG).build())
                .toList();
        when(playerOrganizationRepository.findByOrganizationId(ORG)).thenReturn(members);
    }

    /** per-org 購読フラグ（gate 用）＋ findByPlayerId（resolveChannel の isNotificationEnabled 用）を仕込む。 */
    private void subscribe(Long playerId, boolean enabled) {
        LineNotificationPreference pref = LineNotificationPreference.builder()
                .playerId(playerId).organizationId(ORG).cardDivisionReminder(enabled).build();
        when(lineNotificationPreferenceRepository.findByPlayerIdAndOrganizationId(playerId, ORG))
                .thenReturn(Optional.of(pref));
        when(lineNotificationPreferenceRepository.findByPlayerId(playerId))
                .thenReturn(List.of(pref));
    }

    private void linkPlayer(Long playerId) {
        linkPlayer(playerId, true);
    }

    /** linked=true で LINKED アサインメント＋チャネル（月間上限未満）を仕込む。false は未連携。 */
    private void linkPlayer(Long playerId, boolean linked) {
        if (!linked) {
            when(lineChannelAssignmentRepository.findByPlayerIdAndChannelTypeAndStatusIn(
                    eq(playerId), eq(ChannelType.PLAYER), any())).thenReturn(Optional.empty());
            return;
        }
        long channelId = 900L + playerId;
        LineChannelAssignment assignment = LineChannelAssignment.builder()
                .id(playerId).lineChannelId(channelId).playerId(playerId).lineUserId("U" + playerId)
                .channelType(ChannelType.PLAYER).status(AssignmentStatus.LINKED).build();
        when(lineChannelAssignmentRepository.findByPlayerIdAndChannelTypeAndStatusIn(
                eq(playerId), eq(ChannelType.PLAYER), any())).thenReturn(Optional.of(assignment));

        LineChannel channel = LineChannel.builder()
                .id(channelId).channelAccessToken("token" + playerId)
                .status(LineChannel.ChannelStatus.LINKED).monthlyMessageCount(0).build();
        when(lineChannelRepository.findById(channelId)).thenReturn(Optional.of(channel));
    }
}
