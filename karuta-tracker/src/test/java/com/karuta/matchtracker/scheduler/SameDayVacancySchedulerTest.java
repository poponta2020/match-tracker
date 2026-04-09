package com.karuta.matchtracker.scheduler;

import com.karuta.matchtracker.entity.LotteryExecution;
import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.repository.LotteryExecutionRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.service.LineNotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SameDayVacancyScheduler テスト")
class SameDayVacancySchedulerTest {

    @Mock
    private PracticeSessionRepository practiceSessionRepository;
    @Mock
    private PracticeParticipantRepository practiceParticipantRepository;
    @Mock
    private LotteryExecutionRepository lotteryExecutionRepository;
    @Mock
    private LineNotificationService lineNotificationService;

    @InjectMocks
    private SameDayVacancyScheduler scheduler;

    // ===== ヘルパーメソッド =====

    private PracticeSession buildSession(Long id, int capacity, int totalMatches, Long orgId) {
        return PracticeSession.builder()
                .id(id)
                .sessionDate(LocalDate.now())
                .capacity(capacity)
                .totalMatches(totalMatches)
                .organizationId(orgId)
                .build();
    }

    private List<PracticeParticipant> buildParticipants(Long sessionId, int matchNumber,
                                                         ParticipantStatus status, int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> PracticeParticipant.builder()
                        .id((long) (matchNumber * 100 + i))
                        .sessionId(sessionId)
                        .playerId((long) i)
                        .matchNumber(matchNumber)
                        .status(status)
                        .build())
                .toList();
    }

    // ===== テストケース =====

    @Test
    @DisplayName("抽選実行済み + WON < 定員 + WAITLISTED = 0 → 通知送信される")
    void lotteryExecuted_wonLessThanCapacity_noWaitlisted_notifySent() {
        PracticeSession session = buildSession(1L, 6, 2, 1L);
        when(practiceSessionRepository.findByDateRange(any(), any())).thenReturn(List.of(session));

        // 抽選実行済み
        when(lotteryExecutionRepository.existsByTargetYearAndTargetMonthAndOrganizationIdAndStatus(
                anyInt(), anyInt(), eq(1L), eq(LotteryExecution.ExecutionStatus.SUCCESS)))
                .thenReturn(true);

        // Match 1: WON=4 < capacity=6, WAITLISTED=0 → 通知対象
        when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatus(1L, 1, ParticipantStatus.WON))
                .thenReturn(buildParticipants(1L, 1, ParticipantStatus.WON, 4));
        when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatus(1L, 1, ParticipantStatus.WAITLISTED))
                .thenReturn(Collections.emptyList());

        // Match 2: WON=6 == capacity=6 → 通知対象外
        when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatus(1L, 2, ParticipantStatus.WON))
                .thenReturn(buildParticipants(1L, 2, ParticipantStatus.WON, 6));
        when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatus(1L, 2, ParticipantStatus.WAITLISTED))
                .thenReturn(Collections.emptyList());

        scheduler.checkAndNotifyVacancies();

        // Match 1 のみ統合通知される（空き2名: capacity=6, won=4）
        Map<Integer, Integer> expectedVacancies = Map.of(1, 2);
        verify(lineNotificationService).sendConsolidatedSameDayVacancyNotification(session, expectedVacancies, null);
        verify(lineNotificationService).sendConsolidatedAdminVacancyNotification(session, expectedVacancies);
    }

    @Test
    @DisplayName("抽選未実行 → 通知しない")
    void lotteryNotExecuted_noNotification() {
        PracticeSession session = buildSession(1L, 6, 2, 1L);
        when(practiceSessionRepository.findByDateRange(any(), any())).thenReturn(List.of(session));

        // 抽選未実行
        when(lotteryExecutionRepository.existsByTargetYearAndTargetMonthAndOrganizationIdAndStatus(
                anyInt(), anyInt(), eq(1L), eq(LotteryExecution.ExecutionStatus.SUCCESS)))
                .thenReturn(false);

        scheduler.checkAndNotifyVacancies();

        verify(lineNotificationService, never()).sendConsolidatedSameDayVacancyNotification(any(), any(), any());
        verify(lineNotificationService, never()).sendConsolidatedAdminVacancyNotification(any(), any());
    }

    @Test
    @DisplayName("定員達成 → 通知しない")
    void capacityMet_noNotification() {
        PracticeSession session = buildSession(1L, 6, 1, 1L);
        when(practiceSessionRepository.findByDateRange(any(), any())).thenReturn(List.of(session));

        when(lotteryExecutionRepository.existsByTargetYearAndTargetMonthAndOrganizationIdAndStatus(
                anyInt(), anyInt(), eq(1L), eq(LotteryExecution.ExecutionStatus.SUCCESS)))
                .thenReturn(true);

        // WON=6 == capacity=6
        when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatus(1L, 1, ParticipantStatus.WON))
                .thenReturn(buildParticipants(1L, 1, ParticipantStatus.WON, 6));
        when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatus(1L, 1, ParticipantStatus.WAITLISTED))
                .thenReturn(Collections.emptyList());

        scheduler.checkAndNotifyVacancies();

        verify(lineNotificationService, never()).sendConsolidatedSameDayVacancyNotification(any(), any(), any());
        verify(lineNotificationService, never()).sendConsolidatedAdminVacancyNotification(any(), any());
    }

    @Test
    @DisplayName("WAITLISTED存在 → 通知しない")
    void waitlistedExists_noNotification() {
        PracticeSession session = buildSession(1L, 6, 1, 1L);
        when(practiceSessionRepository.findByDateRange(any(), any())).thenReturn(List.of(session));

        when(lotteryExecutionRepository.existsByTargetYearAndTargetMonthAndOrganizationIdAndStatus(
                anyInt(), anyInt(), eq(1L), eq(LotteryExecution.ExecutionStatus.SUCCESS)))
                .thenReturn(true);

        // WON=4 < capacity=6 だが WAITLISTED=1 > 0 → 通知しない
        when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatus(1L, 1, ParticipantStatus.WON))
                .thenReturn(buildParticipants(1L, 1, ParticipantStatus.WON, 4));
        when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatus(1L, 1, ParticipantStatus.WAITLISTED))
                .thenReturn(buildParticipants(1L, 1, ParticipantStatus.WAITLISTED, 1));

        scheduler.checkAndNotifyVacancies();

        verify(lineNotificationService, never()).sendConsolidatedSameDayVacancyNotification(any(), any(), any());
        verify(lineNotificationService, never()).sendConsolidatedAdminVacancyNotification(any(), any());
    }

    @Test
    @DisplayName("セッションがない日 → 何もしない")
    void noSessions_nothingHappens() {
        when(practiceSessionRepository.findByDateRange(any(), any())).thenReturn(Collections.emptyList());

        scheduler.checkAndNotifyVacancies();

        verify(lotteryExecutionRepository, never())
                .existsByTargetYearAndTargetMonthAndOrganizationIdAndStatus(anyInt(), anyInt(), anyLong(), any());
        verify(practiceParticipantRepository, never())
                .findBySessionIdAndMatchNumberAndStatus(anyLong(), anyInt(), any());
        verify(lineNotificationService, never()).sendConsolidatedSameDayVacancyNotification(any(), any(), any());
        verify(lineNotificationService, never()).sendConsolidatedAdminVacancyNotification(any(), any());
    }
}
