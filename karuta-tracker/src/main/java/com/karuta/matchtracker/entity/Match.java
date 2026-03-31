package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import java.time.LocalDateTime;

/**
 * 対戦結果エンティティ
 *
 * 1対1の対戦結果を記録します。
 * player1_id < player2_id の制約をアプリケーション層で保証します。
 */
@Entity
@Table(name = "matches",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_matches_date_number_players",
            columnNames = {"match_date", "match_number", "player1_id", "player2_id"})
    },
    indexes = {
        @Index(name = "idx_matches_date", columnList = "match_date"),
        @Index(name = "idx_matches_date_player1", columnList = "match_date, player1_id"),
        @Index(name = "idx_matches_date_player2", columnList = "match_date, player2_id"),
        @Index(name = "idx_matches_winner", columnList = "winner_id"),
        @Index(name = "idx_matches_date_match_number", columnList = "match_date, match_number")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 対戦日
     */
    @Column(name = "match_date", nullable = false)
    private LocalDate matchDate;

    /**
     * その日の第何試合目か
     */
    @Column(name = "match_number", nullable = false)
    private Integer matchNumber;

    /**
     * 選手1のID（player1_id < player2_id を保証）
     */
    @Column(name = "player1_id", nullable = false)
    private Long player1Id;

    /**
     * 選手2のID
     */
    @Column(name = "player2_id", nullable = false)
    private Long player2Id;

    /**
     * 勝者のID
     */
    @Column(name = "winner_id", nullable = false)
    private Long winnerId;

    /**
     * 枚数差（1～50）
     */
    @Column(name = "score_difference", nullable = false)
    private Integer scoreDifference;

    /**
     * 選手1の対戦時の級位
     */
    @Column(name = "player1_kyu_rank", length = 10)
    private String player1KyuRank;

    /**
     * 選手2の対戦時の級位
     */
    @Column(name = "player2_kyu_rank", length = 10)
    private String player2KyuRank;

    /**
     * 対戦相手名（システム未登録の選手用）
     */
    @Column(name = "opponent_name", length = 100)
    private String opponentName;

    /**
     * 作成者のID
     */
    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    /**
     * 最終更新者のID
     */
    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

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

    /**
     * エンティティ保存前の処理
     */
    @PrePersist
    protected void onCreate() {
        createdAt = JstDateTimeUtil.now();
        updatedAt = JstDateTimeUtil.now();
        ensurePlayer1LessThanPlayer2();
    }

    /**
     * エンティティ更新前の処理
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = JstDateTimeUtil.now();
        ensurePlayer1LessThanPlayer2();
    }

    /**
     * player1_id < player2_id を保証
     * 必要に応じて player1 と player2 を入れ替える
     */
    private void ensurePlayer1LessThanPlayer2() {
        if (player1Id != null && player2Id != null && player1Id > player2Id) {
            Long temp = player1Id;
            player1Id = player2Id;
            player2Id = temp;

            String tempRank = player1KyuRank;
            player1KyuRank = player2KyuRank;
            player2KyuRank = tempRank;
        }
    }

    /**
     * player1が勝者かを判定
     * @return player1が勝者の場合true
     */
    public boolean isPlayer1Winner() {
        return winnerId.equals(player1Id);
    }

    /**
     * player2が勝者かを判定
     * @return player2が勝者の場合true
     */
    public boolean isPlayer2Winner() {
        return winnerId.equals(player2Id);
    }
}
