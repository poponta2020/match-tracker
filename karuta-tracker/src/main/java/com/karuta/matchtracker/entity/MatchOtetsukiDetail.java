package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import java.time.LocalDateTime;

/**
 * お手付き詳細エンティティ（各プレイヤーの私的データ）。
 *
 * お手付き回数分、種類別の詳細を seq 順に保持する。
 *
 * 値の語彙:
 *  otetsukiType : HIKKAKE / ANKI_MISS / MISHEARING / OTHER
 *  hikkakeTarget: OWN_RIGHT_TOP / OWN_LEFT_TOP / ENEMY_RIGHT_TOP / ENEMY_LEFT_TOP（ひっかけ時）
 *  ankiDirection: SENT_TO_ENEMY_TOUCHED_OWN / RECEIVED_FROM_ENEMY_TOUCHED_ENEMY（暗記間違え時）
 *  mishearingReadCardNo / mishearingTouchedCardNo: 1〜100（聞き間違い時）
 *  otherText: 自由記述（その他）
 */
@Entity
@Table(name = "match_otetsuki_details",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_match_otetsuki_details",
            columnNames = {"match_id", "player_id", "seq"})
    },
    indexes = {
        @Index(name = "idx_match_otetsuki_details_player", columnList = "player_id, match_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchOtetsukiDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(nullable = false)
    private Integer seq;

    @Column(name = "otetsuki_type", nullable = false, length = 16)
    private String otetsukiType;

    @Column(name = "hikkake_target", length = 24)
    private String hikkakeTarget;

    @Column(name = "anki_direction", length = 40)
    private String ankiDirection;

    @Column(name = "mishearing_read_card_no")
    private Integer mishearingReadCardNo;

    @Column(name = "mishearing_touched_card_no")
    private Integer mishearingTouchedCardNo;

    @Column(name = "other_text", columnDefinition = "TEXT")
    private String otherText;

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
