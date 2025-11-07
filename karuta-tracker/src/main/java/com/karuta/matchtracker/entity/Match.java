package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 対戦結果エンティティ
 *
 * 1対1の対戦結果を記録します。
 * player1_id < player2_id の制約をアプリケーション層で保証します。
 */
@Entity
@Table(name = "matches", indexes = {
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
     * コメント
     */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

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
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        ensurePlayer1LessThanPlayer2();
    }

    /**
     * エンティティ更新前の処理
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
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
