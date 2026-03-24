package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * LINEチャネル割り当てエンティティ
 */
@Entity
@Table(name = "line_channel_assignments", indexes = {
    @Index(name = "idx_line_assignment_player", columnList = "player_id"),
    @Index(name = "idx_line_assignment_channel", columnList = "line_channel_id"),
    @Index(name = "idx_line_assignment_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LineChannelAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** LINEチャネルID（FK） */
    @Column(name = "line_channel_id", nullable = false)
    private Long lineChannelId;

    /** プレイヤーID（FK） */
    @Column(name = "player_id", nullable = false)
    private Long playerId;

    /** LINE userId（follow時に取得） */
    @Column(name = "line_user_id", length = 50)
    private String lineUserId;

    /** 割り当てステータス */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private LineAssignmentStatus status = LineAssignmentStatus.PENDING;

    /** 割り当て日時 */
    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    /** follow完了（LINKED化）日時 */
    @Column(name = "linked_at")
    private LocalDateTime linkedAt;

    /** 解除日時 */
    @Column(name = "unlinked_at")
    private LocalDateTime unlinkedAt;

    /** 回収警告通知日時 */
    @Column(name = "reclaim_warned_at")
    private LocalDateTime reclaimWarnedAt;

    /** 作成日時 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (assignedAt == null) {
            assignedAt = LocalDateTime.now();
        }
    }
}
