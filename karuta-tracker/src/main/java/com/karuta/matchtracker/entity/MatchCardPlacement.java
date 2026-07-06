package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import java.time.LocalDateTime;

/**
 * 取り札配置エンティティ（各プレイヤーの私的データ）。
 *
 * その試合の出札50枚のうち、記録者が「どこで取った/取られたか」を配置した札。
 * 1試合×1プレイヤー×1札で最大1レコード。「不明」の札は行を持たない。
 *
 * 値の語彙:
 *  takenBy: SELF / OPPONENT
 *  field  : ENEMY / OWN
 *  side   : LEFT / RIGHT
 *  tier   : TOP / MIDDLE / BOTTOM
 */
@Entity
@Table(name = "match_card_placements",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_match_card_placements",
            columnNames = {"match_id", "player_id", "card_no"})
    },
    indexes = {
        @Index(name = "idx_match_card_placements_player", columnList = "player_id, match_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchCardPlacement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    /** 札番号 1〜100 */
    @Column(name = "card_no", nullable = false)
    private Integer cardNo;

    @Column(name = "taken_by", nullable = false, length = 16)
    private String takenBy;

    @Column(nullable = false, length = 8)
    private String field;

    @Column(nullable = false, length = 8)
    private String side;

    @Column(nullable = false, length = 8)
    private String tier;

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
