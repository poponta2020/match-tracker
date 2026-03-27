package com.karuta.matchtracker.config;

import com.karuta.matchtracker.entity.LineNotificationScheduleSetting;
import com.karuta.matchtracker.entity.LineNotificationScheduleSetting.ScheduleNotificationType;
import com.karuta.matchtracker.repository.LineNotificationScheduleSettingRepository;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * アプリケーション起動時に初期データを投入する
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final LineNotificationScheduleSettingRepository scheduleSettingRepository;

    @Override
    public void run(ApplicationArguments args) {
        initScheduleSettings();
    }

    private void initScheduleSettings() {
        for (ScheduleNotificationType type : ScheduleNotificationType.values()) {
            if (scheduleSettingRepository.findByNotificationType(type).isEmpty()) {
                LineNotificationScheduleSetting setting = LineNotificationScheduleSetting.builder()
                        .notificationType(type)
                        .enabled(true)
                        .daysBefore("[3, 1]")
                        .updatedAt(JstDateTimeUtil.now())
                        .build();
                scheduleSettingRepository.save(setting);
                log.info("初期スケジュール設定を作成しました: {}", type);
            }
        }
    }
}
