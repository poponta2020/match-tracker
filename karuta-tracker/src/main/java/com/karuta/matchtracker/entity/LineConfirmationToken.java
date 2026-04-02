package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import java.time.LocalDateTime;

/**
 * LINE操作確認トークンエンティティ
 */
@Entity
@Table(name = "line_confirmation_tokens", indexes = {
    @Index(name = "idx_lct_token", columnList = "token"),
    @Index(name = "idx_lct_expires", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LineConfirmationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** UUID確認トークン */
    @Column(name = "token", nullable = false, unique = true, length = 64)
    private String token;

    /** 元のアクション名（waitlist_accept等） */
    @Column(name = "action", nullable = false, length = 50)
    private String action;

    /** 元のpostbackパラメータ（JSON形式） */
    @Column(name = "params", nullable = false, columnDefinition = "TEXT")
    private String params;

    /** 操作者のプレイヤーID */
    @Column(name = "player_id", nullable = false)
    private Long playerId;

    /** 発行日時 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 有効期限（created_at + 5分） */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** 使用日時（NULLなら未使用） */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = JstDateTimeUtil.now();
    }

    public boolean isExpired() {
        return JstDateTimeUtil.now().isAfter(expiresAt);
    }

    public boolean isUsed() {
        return usedAt != null;
    }
}
