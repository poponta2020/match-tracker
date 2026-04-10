package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import com.karuta.matchtracker.util.JstDateTimeUtil;

import java.time.LocalDateTime;

/**
 * 隣室通知の重複防止エンティティ（セッション×残り人数の段階ごとに1回のみ通知）
 */
@Entity
@Table(name = "adjacent_room_notifications", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"session_id", "remaining_count"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdjacentRoomNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 通知済みセッションID */
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** 通知時の残り人数（4, 3, 2, 1, 0） */
    @Column(name = "remaining_count", nullable = false)
    private Integer remainingCount;

    /** 通知日時 */
    @Column(name = "notified_at", nullable = false)
    private LocalDateTime notifiedAt;

    @PrePersist
    protected void onCreate() {
        notifiedAt = JstDateTimeUtil.now();
    }
}
