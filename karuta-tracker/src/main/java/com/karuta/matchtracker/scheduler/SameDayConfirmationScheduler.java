package com.karuta.matchtracker.scheduler;

import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.service.LineNotificationService;
import com.karuta.matchtracker.service.WaitlistPromotionService;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 当日12:00確定スケジューラ
 *
 * 毎日12:00 JSTに実行し、以下を処理する:
 * 1. 当日セッションのOFFERED状態の参加者を全てDECLINEDに強制変更
 *    （dirty=true設定・通知送信・空き枠がある場合は当日空き募集フローを発動）
 * 2. WON参加者に試合ごとのメンバーリストをLINE通知
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SameDayConfirmationScheduler {

    private final PracticeSessionRepository practiceSessionRepository;
    private final WaitlistPromotionService waitlistPromotionService;
    private final LineNotificationService lineNotificationService;

    @Scheduled(cron = "0 0 12 * * *", zone = "Asia/Tokyo")
    @Transactional
    public void confirmSameDayParticipants() {
        LocalDate today = JstDateTimeUtil.today();
        List<PracticeSession> sessions = practiceSessionRepository.findByDateRange(today, today);

        if (sessions.isEmpty()) {
            log.debug("No sessions found for today ({})", today);
            return;
        }

        log.info("SameDayConfirmation scheduler started for {} session(s) on {}", sessions.size(), today);

        for (PracticeSession session : sessions) {
            try {
                waitlistPromotionService.expireOfferedForSameDayConfirmation(session);
                lineNotificationService.sendSameDayConfirmationNotification(session);
            } catch (Exception e) {
                log.error("Failed to process session {} on {}: {}",
                        session.getId(), session.getSessionDate(), e.getMessage(), e);
            }
        }

        log.info("SameDayConfirmation scheduler completed");
    }
}
