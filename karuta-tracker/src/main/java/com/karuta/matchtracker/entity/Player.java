package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 選手マスタエンティティ
 *
 * 選手の基本情報とアカウント情報を管理します。
 * 認証情報（名前、パスワード）、ロール、論理削除フラグを含みます。
 */
@Entity
@Table(name = "players", indexes = {
    @Index(name = "idx_name_active", columnList = "name, deleted_at"),
    @Index(name = "idx_deleted_at", columnList = "deleted_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 選手名（ログインに使用）
     */
    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    /**
     * パスワード（BCryptでハッシュ化）
     */
    @Column(name = "password", nullable = false, length = 255)
    private String password;

    /**
     * 性別
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false)
    private Gender gender;

    /**
     * 利き手
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "dominant_hand", nullable = false)
    private DominantHand dominantHand;

    /**
     * 段位
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "dan_rank")
    private DanRank danRank;

    /**
     * 級位
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "kyu_rank")
    private KyuRank kyuRank;

    /**
     * 所属かるた会
     */
    @Column(name = "karuta_club", length = 200)
    private String karutaClub;

    /**
     * 備考
     */
    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    /**
     * ロール（権限管理）
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    @Builder.Default
    private Role role = Role.PLAYER;

    /**
     * 削除日時（論理削除）
     * NULLの場合はアクティブな選手
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

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
     * 性別の列挙型
     */
    public enum Gender {
        男性, 女性, その他
    }

    /**
     * 利き手の列挙型
     */
    public enum DominantHand {
        右, 左, 両
    }

    /**
     * 段位の列挙型
     */
    public enum DanRank {
        無段, 初段, 弐段, 参段, 四段, 五段, 六段, 七段, 八段
    }

    /**
     * 級位の列挙型
     */
    public enum KyuRank {
        E級, D級, C級, B級, A級
    }

    /**
     * ロールの列挙型
     */
    public enum Role {
        SUPER_ADMIN,  // 最上位管理者：全機能
        ADMIN,        // 管理者：練習日管理、組み合わせ作成、基本機能
        PLAYER        // 一般選手：基本機能のみ
    }

    /**
     * 論理削除されているかを判定
     * @return 削除済みの場合true
     */
    public boolean isDeleted() {
        return deletedAt != null;
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
