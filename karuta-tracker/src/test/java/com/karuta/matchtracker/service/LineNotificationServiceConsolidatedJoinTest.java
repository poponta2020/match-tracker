package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.*;
import com.karuta.matchtracker.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LineNotificationService 統合参加通知テスト")
class LineNotificationServiceConsolidatedJoinTest {

    @Mock
    private LineChannelRepository lineChannelRepository;
    @Mock
    private LineChannelAssignmentRepository lineChannelAssignmentRepository;
    @Mock
    private LineNotificationPreferenceRepository lineNotificationPreferenceRepository;
    @Mock
    private LineMessageLogService lineMessageLogService;
    @Mock
    private LineMessagingService lineMessagingService;
    @Mock
    private PracticeSessionRepository practiceSessionRepository;
    @Mock
    private PracticeParticipantRepository practiceParticipantRepository;
    @Mock
    private PlayerOrganizationRepository playerOrganizationRepository;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private LotteryQueryService lotteryQueryService;
    @Mock
    private VenueRepository venueRepository;

    @Spy
    @InjectMocks
    private LineNotificationService lineNotificationService;

    private PracticeSession createSession() {
        return PracticeSession.builder()
                .id(100L).sessionDate(LocalDate.of(2026, 4, 20))
                .capacity(6).totalMatches(3).organizationId(1L).build();
    }

    @Nested
    @DisplayName("sendConsolidatedSameDayJoinNotification")
    class SendConsolidatedSameDayJoinNotificationTests {

        @Test
        @DisplayName("複数試合の受信者を重複排除し、本人を除外して通知する")
        void recipientDeduplicationAndSelfExclusion() {
            PracticeSession session = createSession();

            // 1試合目のWON: player10, player20（参加者本人）
            PracticeParticipant won1a = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1).status(ParticipantStatus.WON).build();
            PracticeParticipant won1b = PracticeParticipant.builder()
                    .id(2L).sessionId(100L).playerId(20L).matchNumber(1).status(ParticipantStatus.WON).build();
            // 2試合目のWON: player10, player30
            PracticeParticipant won2a = PracticeParticipant.builder()
                    .id(3L).sessionId(100L).playerId(10L).matchNumber(2).status(ParticipantStatus.WON).build();
            PracticeParticipant won2b = PracticeParticipant.builder()
                    .id(4L).sessionId(100L).playerId(30L).matchNumber(2).status(ParticipantStatus.WON).build();

            when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.WON))
                    .thenReturn(List.of(won1a, won1b));
            when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatus(100L, 2, ParticipantStatus.WON))
                    .thenReturn(List.of(won2a, won2b));

            // sendToPlayerをスタブ化
            doReturn(LineNotificationService.SendResult.SKIPPED).when(lineNotificationService)
                    .sendToPlayer(anyLong(), any(), anyString());
            // 管理者なし
            when(playerRepository.findByRoleAndActive(Player.Role.SUPER_ADMIN)).thenReturn(List.of());
            when(playerRepository.findByRoleAndAdminOrganizationIdAndActive(Player.Role.ADMIN, 1L)).thenReturn(List.of());

            // joinedPlayerId=20 で実行
            lineNotificationService.sendConsolidatedSameDayJoinNotification(session, List.of(1, 2), "参加者", 20L);

            // player10, player30 に通知（player20=本人は除外、player10は重複排除で1回のみ）
            verify(lineNotificationService).sendToPlayer(eq(10L), eq(LineMessageLog.LineNotificationType.SAME_DAY_CANCEL), anyString());
            verify(lineNotificationService).sendToPlayer(eq(30L), eq(LineMessageLog.LineNotificationType.SAME_DAY_CANCEL), anyString());
            verify(lineNotificationService, never()).sendToPlayer(eq(20L), any(), anyString());
            // 合計2回（重複排除済み）
            verify(lineNotificationService, times(2)).sendToPlayer(anyLong(), any(), anyString());
        }

        @Test
        @DisplayName("試合番号がソートされたメッセージが送信される")
        void matchNumbersSorted() {
            PracticeSession session = createSession();

            PracticeParticipant won = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(3).status(ParticipantStatus.WON).build();

            // 3試合目と1試合目にWON参加者（逆順で渡す）
            when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatus(100L, 3, ParticipantStatus.WON))
                    .thenReturn(List.of(won));
            when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.WON))
                    .thenReturn(List.of(PracticeParticipant.builder()
                            .id(2L).sessionId(100L).playerId(10L).matchNumber(1).status(ParticipantStatus.WON).build()));

            doReturn(LineNotificationService.SendResult.SKIPPED).when(lineNotificationService)
                    .sendToPlayer(anyLong(), any(), anyString());
            when(playerRepository.findByRoleAndActive(Player.Role.SUPER_ADMIN)).thenReturn(List.of());
            when(playerRepository.findByRoleAndAdminOrganizationIdAndActive(Player.Role.ADMIN, 1L)).thenReturn(List.of());

            // 逆順で渡してもソートされるか
            lineNotificationService.sendConsolidatedSameDayJoinNotification(session, List.of(3, 1), "テスト太郎", 99L);

            // メッセージが "1,3" の順でソートされていること
            verify(lineNotificationService).sendToPlayer(eq(10L), eq(LineMessageLog.LineNotificationType.SAME_DAY_CANCEL),
                    eq("テスト太郎さんが今日の1,3試合目に参加します"));
        }

        @Test
        @DisplayName("管理者にも通知が送信される")
        void adminNotificationSent() {
            PracticeSession session = createSession();

            PracticeParticipant won = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1).status(ParticipantStatus.WON).build();

            when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.WON))
                    .thenReturn(List.of(won));

            doReturn(LineNotificationService.SendResult.SKIPPED).when(lineNotificationService)
                    .sendToPlayer(anyLong(), any(), anyString());

            Player admin = Player.builder().id(50L).name("管理者").role(Player.Role.SUPER_ADMIN).build();
            when(playerRepository.findByRoleAndActive(Player.Role.SUPER_ADMIN)).thenReturn(List.of(admin));
            when(playerRepository.findByRoleAndAdminOrganizationIdAndActive(Player.Role.ADMIN, 1L)).thenReturn(List.of());

            lineNotificationService.sendConsolidatedSameDayJoinNotification(session, List.of(1), "参加者", 20L);

            // 管理者に通知が送信される
            verify(lineNotificationService).sendToPlayer(eq(50L), eq(LineMessageLog.LineNotificationType.ADMIN_SAME_DAY_CANCEL), anyString());
        }

        @Test
        @DisplayName("参加試合が空リストの場合は何も送信しない")
        void emptyMatchList() {
            PracticeSession session = createSession();

            lineNotificationService.sendConsolidatedSameDayJoinNotification(session, List.of(), "参加者", 20L);

            verify(lineNotificationService, never()).sendToPlayer(anyLong(), any(), anyString());
        }
    }
}
