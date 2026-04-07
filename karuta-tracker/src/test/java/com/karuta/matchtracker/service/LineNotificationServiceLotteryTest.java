package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.*;
import com.karuta.matchtracker.repository.*;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LineNotificationService 抽選結果通知テスト")
class LineNotificationServiceLotteryTest {

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

    @Nested
    @DisplayName("sendLotteryResultsForPlayer OFFERED集約テスト")
    class SendLotteryResultsForPlayerOfferedConsolidationTests {

        @Test
        @DisplayName("同一セッションの複数OFFERED → sendConsolidatedWaitlistOfferNotificationが1回呼ばれる")
        void sameSession_multipleOffered_oneConsolidatedCall() {
            LocalDateTime fixedNow = LocalDateTime.of(2026, 4, 7, 10, 0);

            PracticeSession session = PracticeSession.builder()
                    .id(100L).sessionDate(LocalDate.of(2026, 4, 20))
                    .capacity(6).totalMatches(3).organizationId(1L).build();

            PracticeParticipant offered1 = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(50L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED)
                    .offerDeadline(fixedNow.plusDays(1)).build();
            PracticeParticipant offered2 = PracticeParticipant.builder()
                    .id(2L).sessionId(100L).playerId(50L).matchNumber(2)
                    .status(ParticipantStatus.OFFERED)
                    .offerDeadline(fixedNow.plusDays(1)).build();

            try (MockedStatic<JstDateTimeUtil> jstMock = mockStatic(JstDateTimeUtil.class)) {
                jstMock.when(JstDateTimeUtil::now).thenReturn(fixedNow);

                when(practiceParticipantRepository.findBySessionDateYearAndMonth(2026, 4))
                        .thenReturn(List.of(offered1, offered2));
                when(practiceParticipantRepository.findBySessionDateYearAndMonth(2026, 5))
                        .thenReturn(List.of());
                when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
                when(lotteryQueryService.isLotteryConfirmed(2026, 4, 1L)).thenReturn(true);

                // sendConsolidatedWaitlistOfferNotification の内部呼び出しをスタブ化
                doNothing().when(lineNotificationService).sendConsolidatedWaitlistOfferNotification(
                        anyList(), any(PracticeSession.class), any(), (Player) any());

                // sendToPlayer の内部呼び出しもスタブ化
                doReturn(LineNotificationService.SendResult.SKIPPED).when(lineNotificationService).sendToPlayer(
                        anyLong(), any(), anyString());

                lineNotificationService.sendLotteryResultsForPlayer(50L);

                // 同一セッションの2つのOFFEREDが1回の呼び出しに統合される
                verify(lineNotificationService, times(1)).sendConsolidatedWaitlistOfferNotification(
                        argThat(list -> list.size() == 2
                                && list.stream().allMatch(p -> p.getSessionId() == 100L)),
                        eq(session), isNull(), (Player) isNull());
            }
        }

        @Test
        @DisplayName("異なるセッションのOFFERED → セッション数分sendConsolidatedWaitlistOfferNotificationが呼ばれる")
        void differentSessions_offered_separateCalls() {
            LocalDateTime fixedNow = LocalDateTime.of(2026, 4, 7, 10, 0);

            PracticeSession session100 = PracticeSession.builder()
                    .id(100L).sessionDate(LocalDate.of(2026, 4, 20))
                    .capacity(6).totalMatches(2).organizationId(1L).build();
            PracticeSession session200 = PracticeSession.builder()
                    .id(200L).sessionDate(LocalDate.of(2026, 4, 27))
                    .capacity(6).totalMatches(2).organizationId(1L).build();

            PracticeParticipant offered1 = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(50L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED)
                    .offerDeadline(fixedNow.plusDays(1)).build();
            PracticeParticipant offered2 = PracticeParticipant.builder()
                    .id(2L).sessionId(200L).playerId(50L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED)
                    .offerDeadline(fixedNow.plusDays(1)).build();

            try (MockedStatic<JstDateTimeUtil> jstMock = mockStatic(JstDateTimeUtil.class)) {
                jstMock.when(JstDateTimeUtil::now).thenReturn(fixedNow);

                when(practiceParticipantRepository.findBySessionDateYearAndMonth(2026, 4))
                        .thenReturn(List.of(offered1, offered2));
                when(practiceParticipantRepository.findBySessionDateYearAndMonth(2026, 5))
                        .thenReturn(List.of());
                when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session100));
                when(practiceSessionRepository.findById(200L)).thenReturn(Optional.of(session200));
                when(lotteryQueryService.isLotteryConfirmed(2026, 4, 1L)).thenReturn(true);

                doNothing().when(lineNotificationService).sendConsolidatedWaitlistOfferNotification(
                        anyList(), any(PracticeSession.class), any(), (Player) any());
                doReturn(LineNotificationService.SendResult.SKIPPED).when(lineNotificationService).sendToPlayer(
                        anyLong(), any(), anyString());

                lineNotificationService.sendLotteryResultsForPlayer(50L);

                // 異なるセッションなので2回呼ばれる
                verify(lineNotificationService, times(2)).sendConsolidatedWaitlistOfferNotification(
                        anyList(), any(PracticeSession.class), isNull(), (Player) isNull());

                // セッション100のOFFERED
                verify(lineNotificationService).sendConsolidatedWaitlistOfferNotification(
                        argThat(list -> list.size() == 1 && list.get(0).getSessionId() == 100L),
                        eq(session100), isNull(), (Player) isNull());
                // セッション200のOFFERED
                verify(lineNotificationService).sendConsolidatedWaitlistOfferNotification(
                        argThat(list -> list.size() == 1 && list.get(0).getSessionId() == 200L),
                        eq(session200), isNull(), (Player) isNull());
            }
        }
    }
}
