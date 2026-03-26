package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import java.time.LocalDateTime;

/**
 * スケジュール型LINE通知の管理者設定エンティティ
 */
@Entity
@Table(name = "line_notification_schedule_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LineNotificationScheduleSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 通知種別（UNIQUE） */
    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, unique = true, length = 30)
    private ScheduleNotificationType notificationType;

    /** 有効/無効 */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /** 送信日数（JSON配列文字列。例: "[3, 1]"） */
    @Column(name = "days_before", nullable = false, length = 50)
    private String daysBefore;

    /** 更新日時 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 最終更新者 */
    @Column(name = "updated_by")
    private Long updatedBy;

    public enum ScheduleNotificationType {
        PRACTICE_REMINDER,
        DEADLINE_REMINDER
    }

    @PrePersist
    protected void onCreate() {
        updatedAt = JstDateTimeUtil.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = JstDateTimeUtil.now();
    }
}
