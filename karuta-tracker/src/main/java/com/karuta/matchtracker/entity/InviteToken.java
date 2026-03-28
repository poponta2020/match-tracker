package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.*;

import com.karuta.matchtracker.util.JstDateTimeUtil;
import java.time.LocalDateTime;

/**
 * 招待トークンエンティティ
 * 管理者が発行し、新規ユーザーが登録時に使用する
 */
@Entity
@Table(name = "invite_tokens", indexes = {
        @Index(name = "idx_invite_tokens_token", columnList = "token", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InviteToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * トークン文字列（UUID）
     */
    @Column(name = "token", nullable = false, unique = true, length = 36)
    private String token;

    /**
     * トークン種別
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TokenType type;

    /**
     * 有効期限
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * 使用済み日時（SINGLE_USE のみ）
     */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    /**
     * 使用した選手ID（SINGLE_USE のみ）
     */
    @Column(name = "used_by")
    private Long usedBy;

    /**
     * 発行者の選手ID
     */
    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    /**
     * 団体ID（登録時のplayer_organizations初期値に使用）
     */
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = JstDateTimeUtil.now();
    }

    /**
     * トークンが有効かどうか判定
     */
    public boolean isValid() {
        if (JstDateTimeUtil.now().isAfter(expiresAt)) {
            return false;
        }
        if (type == TokenType.SINGLE_USE && usedAt != null) {
            return false;
        }
        return true;
    }

    /**
     * トークン種別
     */
    public enum TokenType {
        /** グループ用: 期限内なら何人でも使用可能 */
        MULTI_USE,
        /** 個人用: 1回限り */
        SINGLE_USE
    }
}
