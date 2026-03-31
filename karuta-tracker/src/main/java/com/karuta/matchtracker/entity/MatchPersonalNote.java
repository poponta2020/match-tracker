package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import java.time.LocalDateTime;

/**
 * 個人メモ・お手付き記録エンティティ
 *
 * 各プレイヤーが自分の試合に対して記録する個人的なメモとお手付き回数。
 * 1試合につき各プレイヤー1レコード。
 */
@Entity
@Table(name = "match_personal_notes",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_match_personal_notes",
            columnNames = {"match_id", "player_id"})
    },
    indexes = {
        @Index(name = "idx_match_personal_notes_player", columnList = "player_id, match_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchPersonalNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "otetsuki_count")
    private Integer otetsukiCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

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
