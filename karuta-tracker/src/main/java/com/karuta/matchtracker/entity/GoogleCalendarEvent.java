package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import java.time.LocalDateTime;

/**
 * Google Calendarイベントマッピングエンティティ
 *
 * 練習セッションとGoogle CalendarイベントIDの対応を、
 * プレイヤーごとに管理します（冪等同期用）。
 */
@Entity
@Table(name = "google_calendar_events",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_gcal_player_session",
            columnNames = {"player_id", "session_id"})
    },
    indexes = {
        @Index(name = "idx_gcal_player_id", columnList = "player_id"),
        @Index(name = "idx_gcal_session_id", columnList = "session_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoogleCalendarEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * プレイヤーID
     */
    @Column(name = "player_id", nullable = false)
    private Long playerId;

    /**
     * 練習セッションID
     */
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /**
     * Google CalendarのイベントID
     */
    @Column(name = "google_event_id", nullable = false, length = 1024)
    private String googleEventId;

    /**
     * 同期時点でのセッション更新日時（変更検知用）
     */
    @Column(name = "synced_session_updated_at")
    private LocalDateTime syncedSessionUpdatedAt;

    /**
     * 作成日時
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新日時
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = JstDateTimeUtil.now();
        updatedAt = JstDateTimeUtil.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = JstDateTimeUtil.now();
    }
}
