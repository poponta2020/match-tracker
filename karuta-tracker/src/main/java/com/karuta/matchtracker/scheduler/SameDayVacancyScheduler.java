package com.karuta.matchtracker.scheduler;

import com.karuta.matchtracker.entity.LotteryExecution;
import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.repository.LotteryExecutionRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.service.LineNotificationService;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 当日0:00空き枠通知スケジューラ
 *
 * 毎日0:00 JSTに実行し、当日セッションのうち
 * 抽選実行済み・WON数 < 定員・WAITLISTED数 = 0 の試合について
 * 空き枠通知を自動送信する。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SameDayVacancyScheduler {

    private final PracticeSessionRepository practiceSessionRepository;
    private final PracticeParticipantRepository practiceParticipantRepository;
    private final LotteryExecutionRepository lotteryExecutionRepository;
    private final LineNotificationService lineNotificationService;

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Tokyo")
    public void checkAndNotifyVacancies() {
        LocalDate today = JstDateTimeUtil.today();
        List<PracticeSession> sessions = practiceSessionRepository.findByDateRange(today, today);

        if (sessions.isEmpty()) {
            log.debug("No sessions found for today ({})", today);
            return;
        }

        log.info("SameDayVacancy scheduler started for {} session(s) on {}", sessions.size(), today);

        int notifiedCount = 0;
        for (PracticeSession session : sessions) {
            try {
                notifiedCount += processSession(session, today);
            } catch (Exception e) {
                log.error("Failed to process vacancy check for session {} on {}: {}",
                        session.getId(), session.getSessionDate(), e.getMessage(), e);
            }
        }

        log.info("SameDayVacancy scheduler completed: {} match(es) notified", notifiedCount);
    }

    private int processSession(PracticeSession session, LocalDate today) {
        // 抽選実行済み判定
        Long orgId = session.getOrganizationId();
        boolean lotteryExecuted = orgId != null
                ? lotteryExecutionRepository.existsByTargetYearAndTargetMonthAndOrganizationIdAndStatus(
                        today.getYear(), today.getMonthValue(), orgId, LotteryExecution.ExecutionStatus.SUCCESS)
                : false;

        if (!lotteryExecuted) {
            log.debug("Lottery not executed for session {} (orgId={}), skipping", session.getId(), orgId);
            return 0;
        }

        int capacity = session.getCapacity() != null ? session.getCapacity() : 0;
        if (capacity <= 0) return 0;

        int totalMatches = session.getTotalMatches() != null ? session.getTotalMatches() : 1;

        // 空き試合を蓄積（matchNumber → 空き枠数）
        Map<Integer, Integer> vacanciesByMatch = new LinkedHashMap<>();

        for (int matchNumber = 1; matchNumber <= totalMatches; matchNumber++) {
            List<PracticeParticipant> wonParticipants = practiceParticipantRepository
                    .findBySessionIdAndMatchNumberAndStatus(session.getId(), matchNumber, ParticipantStatus.WON);
            List<PracticeParticipant> waitlistedParticipants = practiceParticipantRepository
                    .findBySessionIdAndMatchNumberAndStatus(session.getId(), matchNumber, ParticipantStatus.WAITLISTED);

            if (wonParticipants.size() < capacity && waitlistedParticipants.isEmpty()) {
                int vacancies = capacity - wonParticipants.size();
                vacanciesByMatch.put(matchNumber, vacancies);
                log.info("Vacancy detected: session {} match {} (won={}, capacity={}, waitlisted=0)",
                        session.getId(), matchNumber, wonParticipants.size(), capacity);
            }
        }

        if (!vacanciesByMatch.isEmpty()) {
            // セッション単位で統合送信
            lineNotificationService.sendConsolidatedSameDayVacancyNotification(session, vacanciesByMatch, null);
            lineNotificationService.sendConsolidatedAdminVacancyNotification(session, vacanciesByMatch);
        }

        return vacanciesByMatch.size();
    }
}
