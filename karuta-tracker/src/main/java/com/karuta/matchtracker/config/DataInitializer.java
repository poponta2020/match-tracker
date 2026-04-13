package com.karuta.matchtracker.config;

import com.karuta.matchtracker.entity.LineNotificationScheduleSetting;
import com.karuta.matchtracker.entity.LineNotificationScheduleSetting.ScheduleNotificationType;
import com.karuta.matchtracker.repository.LineNotificationScheduleSettingRepository;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * アプリケーション起動時に初期データを投入・検証する
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final LineNotificationScheduleSettingRepository scheduleSettingRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        initScheduleSettings();
        validateDedupeIndex();
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

    /**
     * tryAcquireSendRight が依存する部分ユニークインデックス (idx_lml_dedupe_daily_unique) の存在を検証する。
     * Hibernate ddl-auto=update ではこのインデックスは自動作成されないため、
     * 存在しなければ自動作成を試み、それでも失敗した場合はfail-fastする。
     */
    private void validateDedupeIndex() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'idx_lml_dedupe_daily_unique'",
                    Integer.class);
            if (count != null && count > 0) {
                log.info("dedupe インデックス検証OK: idx_lml_dedupe_daily_unique が存在します");
                return;
            }
            log.warn("必須インデックス idx_lml_dedupe_daily_unique が未検出。自動作成を試みます...");
            ensureDedupeKeyColumn();
            jdbcTemplate.execute(
                    "CREATE UNIQUE INDEX IF NOT EXISTS idx_lml_dedupe_daily_unique "
                    + "ON line_message_log (player_id, notification_type, dedupe_key, (sent_at::date)) "
                    + "WHERE status IN ('SUCCESS', 'RESERVED') AND dedupe_key IS NOT NULL");
            log.info("idx_lml_dedupe_daily_unique を自動作成しました");
        } catch (Exception e) {
            log.error("dedupe インデックスの検証/作成に失敗しました: {}", e.getMessage());
            throw new IllegalStateException(
                    "必須インデックス idx_lml_dedupe_daily_unique の作成に失敗しました。"
                    + " database/add_line_message_log_dedupe_key.sql を手動で実行してください。", e);
        }
    }

    private void ensureDedupeKeyColumn() {
        jdbcTemplate.execute(
                "ALTER TABLE line_message_log ADD COLUMN IF NOT EXISTS dedupe_key VARCHAR(100)");
        jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS idx_lml_dedupe "
                + "ON line_message_log (player_id, notification_type, dedupe_key, sent_at)");
    }
}
