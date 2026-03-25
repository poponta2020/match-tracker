package com.karuta.matchtracker.scheduler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karuta.matchtracker.entity.*;
import com.karuta.matchtracker.entity.LineMessageLog.LineNotificationType;
import com.karuta.matchtracker.entity.LineNotificationScheduleSetting.ScheduleNotificationType;
import com.karuta.matchtracker.repository.*;
import com.karuta.matchtracker.service.LineNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * LINE通知リマインダースケジューラ
 *
 * 毎日AM8:00に実行し、参加予定リマインダー・締め切りリマインダーを送信する。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LineReminderScheduler {

    private final LineNotificationScheduleSettingRepository scheduleSettingRepository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final PracticeParticipantRepository practiceParticipantRepository;
    private final LineMessageLogRepository lineMessageLogRepository;
    private final LineNotificationService lineNotificationService;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("M月d日");

    @Scheduled(cron = "0 0 8 * * *") // 毎日AM8:00
    public void sendReminders() {
        log.info("LINE reminder scheduler started");
        sendPracticeReminders();
        sendDeadlineReminders();
        log.info("LINE reminder scheduler completed");
    }

    private void sendPracticeReminders() {
        var settingOpt = scheduleSettingRepository
            .findByNotificationType(ScheduleNotificationType.PRACTICE_REMINDER);
        if (settingOpt.isEmpty() || !settingOpt.get().getEnabled()) return;

        List<Integer> daysBefore = parseDaysBefore(settingOpt.get().getDaysBefore());
        LocalDate today = LocalDate.now();

        for (int days : daysBefore) {
            LocalDate targetDate = today.plusDays(days);
            var sessionOpt = practiceSessionRepository.findBySessionDate(targetDate);
            if (sessionOpt.isEmpty()) continue;

            {
                PracticeSession session = sessionOpt.get();
                String dateStr = session.getSessionDate().format(DATE_FORMAT);
                String dayLabel = days == 1 ? "明日" : days + "日後";
                String message = String.format("%s%sは練習日です。準備をお忘れなく！", dayLabel, dateStr);

                // 当該セッションのWON参加者に送信
                List<PracticeParticipant> participants = practiceParticipantRepository
                    .findBySessionIdAndStatus(session.getId(), ParticipantStatus.WON);

                List<Long> uniquePlayerIds = participants.stream()
                    .map(PracticeParticipant::getPlayerId).distinct().toList();

                for (Long playerId : uniquePlayerIds) {
                    // 重複送信チェック
                    if (!lineMessageLogRepository.existsSuccessfulSince(
                            playerId, LineNotificationType.PRACTICE_REMINDER,
                            today.atStartOfDay())) {
                        lineNotificationService.sendToPlayer(
                            playerId, LineNotificationType.PRACTICE_REMINDER, message);
                    }
                }
            }
        }
    }

    private void sendDeadlineReminders() {
        // 締め切りリマインダーは現時点では練習セッションに締め切り日フィールドがないため
        // 将来の拡張ポイントとして構造のみ用意
        var settingOpt = scheduleSettingRepository
            .findByNotificationType(ScheduleNotificationType.DEADLINE_REMINDER);
        if (settingOpt.isEmpty() || !settingOpt.get().getEnabled()) return;

        log.debug("Deadline reminder: no deadline field in PracticeSession yet, skipping");
    }

    private List<Integer> parseDaysBefore(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse days_before JSON: {}", json);
            return List.of();
        }
    }
}
