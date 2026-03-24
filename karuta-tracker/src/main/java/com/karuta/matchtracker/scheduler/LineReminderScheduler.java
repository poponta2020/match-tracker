package com.karuta.matchtracker.scheduler;

import com.karuta.matchtracker.dto.LineSendResultDto;
import com.karuta.matchtracker.entity.LineNotificationScheduleSetting;
import com.karuta.matchtracker.entity.LineNotificationType;
import com.karuta.matchtracker.repository.LineNotificationScheduleSettingRepository;
import com.karuta.matchtracker.service.LineNotificationService;
import com.karuta.matchtracker.service.LotteryDeadlineHelper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * LINEリマインダースケジューラ
 *
 * 毎日AM 8:00に実行し、参加予定リマインダーと締め切りリマインダーを送信する。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LineReminderScheduler {

    private final LineNotificationScheduleSettingRepository scheduleSettingRepository;
    private final LineNotificationService lineNotificationService;
    private final LotteryDeadlineHelper lotteryDeadlineHelper;
    private final ObjectMapper objectMapper;

    /**
     * 毎日AM 8:00に実行
     */
    @Scheduled(cron = "0 0 8 * * *")
    public void sendReminders() {
        log.info("LINE reminder scheduler started");

        try {
            sendPracticeReminders();
        } catch (Exception e) {
            log.error("Error sending practice reminders: {}", e.getMessage(), e);
        }

        try {
            sendDeadlineReminders();
        } catch (Exception e) {
            log.error("Error sending deadline reminders: {}", e.getMessage(), e);
        }

        log.info("LINE reminder scheduler completed");
    }

    /**
     * 参加予定リマインダーを送信
     */
    private void sendPracticeReminders() {
        Optional<LineNotificationScheduleSetting> settingOpt = scheduleSettingRepository
                .findByNotificationType(LineNotificationType.PRACTICE_REMINDER);

        if (settingOpt.isEmpty() || !settingOpt.get().getEnabled()) {
            return;
        }

        List<Integer> daysBefore = parseDaysBefore(settingOpt.get().getDaysBefore());
        LocalDate today = LocalDate.now();

        for (int days : daysBefore) {
            LocalDate targetDate = today.plusDays(days);
            log.info("Sending practice reminders for {}", targetDate);
            LineSendResultDto result = lineNotificationService.sendPracticeReminders(targetDate);
            log.info("Practice reminders for {}: sent={}, skipped={}",
                    targetDate, result.getSentCount(), result.getSkippedCount());
        }
    }

    /**
     * 締め切りリマインダーを送信
     */
    private void sendDeadlineReminders() {
        Optional<LineNotificationScheduleSetting> settingOpt = scheduleSettingRepository
                .findByNotificationType(LineNotificationType.DEADLINE_REMINDER);

        if (settingOpt.isEmpty() || !settingOpt.get().getEnabled()) {
            return;
        }

        List<Integer> daysBefore = parseDaysBefore(settingOpt.get().getDaysBefore());
        LocalDate today = LocalDate.now();

        // 次月の締め切り日を計算（当月末日）
        LocalDate nextMonthFirst = today.withDayOfMonth(1).plusMonths(1);
        int nextYear = nextMonthFirst.getYear();
        int nextMonth = nextMonthFirst.getMonthValue();
        LocalDate deadline = today.withDayOfMonth(today.lengthOfMonth());

        for (int days : daysBefore) {
            if (today.equals(deadline.minusDays(days))) {
                log.info("Sending deadline reminders ({} days before deadline {})", days, deadline);
                LineSendResultDto result = lineNotificationService.sendDeadlineReminders(
                        nextYear, nextMonth, deadline);
                log.info("Deadline reminders: sent={}, skipped={}",
                        result.getSentCount(), result.getSkippedCount());
            }
        }
    }

    private List<Integer> parseDaysBefore(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to parse daysBefore: {}", json);
            return List.of();
        }
    }
}
