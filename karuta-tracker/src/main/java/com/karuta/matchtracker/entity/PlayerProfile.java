package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 選手情報履歴エンティティ
 *
 * 選手の級・段位の変更履歴を管理します。
 * 過去の対戦時点での選手の級・段位を正確に記録するための履歴テーブルです。
 */
@Entity
@Table(name = "player_profiles", indexes = {
    @Index(name = "idx_player_date", columnList = "player_id, valid_from, valid_to"),
    @Index(name = "idx_valid_to", columnList = "valid_to")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 選手ID
     */
    @Column(name = "player_id", nullable = false)
    private Long playerId;

    /**
     * 所属かるた会
     */
    @Column(name = "karuta_club", nullable = false, length = 200)
    private String karutaClub;

    /**
     * 級
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "grade", nullable = false)
    private Grade grade;

    /**
     * 段位
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "dan", nullable = false)
    private Dan dan;

    /**
     * 有効開始日
     */
    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    /**
     * 有効終了日（NULLなら現在有効）
     */
    @Column(name = "valid_to")
    private LocalDate validTo;

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
     * 級の列挙型
     */
    public enum Grade {
        A, B, C, D, E
    }

    /**
     * 段位の列挙型
     */
    public enum Dan {
        無, 初, 二, 三, 四, 五, 六, 七, 八
    }

    /**
     * 現在有効なプロフィールかを判定
     * @return 現在有効な場合true
     */
    public boolean isCurrent() {
        return validTo == null;
    }

    /**
     * エンティティ保存前の処理
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    /**
     * エンティティ更新前の処理
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
