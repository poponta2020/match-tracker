package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.LineNotificationScheduleSetting;
import com.karuta.matchtracker.entity.LineNotificationScheduleSetting.ScheduleNotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * LINE通知スケジュール設定リポジトリ
 */
@Repository
public interface LineNotificationScheduleSettingRepository extends JpaRepository<LineNotificationScheduleSetting, Long> {

    /** 通知種別で設定を取得 */
    Optional<LineNotificationScheduleSetting> findByNotificationType(ScheduleNotificationType notificationType);
}
