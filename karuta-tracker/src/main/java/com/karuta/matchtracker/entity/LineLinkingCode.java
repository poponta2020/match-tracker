package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * LINEアカウント紐付け用ワンタイムコードエンティティ
 */
@Entity
@Table(name = "line_linking_codes", indexes = {
    @Index(name = "idx_llc_player", columnList = "player_id"),
    @Index(name = "idx_llc_code", columnList = "code"),
    @Index(name = "idx_llc_channel", columnList = "line_channel_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LineLinkingCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** プレイヤーID（FK） */
    @Column(name = "player_id", nullable = false)
    private Long playerId;

    /** LINEチャネルID（FK） */
    @Column(name = "line_channel_id", nullable = false)
    private Long lineChannelId;

    /** 英数字8桁のワンタイムコード */
    @Column(name = "code", nullable = false, unique = true, length = 8)
    private String code;

    /** ステータス */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private CodeStatus status = CodeStatus.ACTIVE;

    /** 検証失敗回数 */
    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    /** 有効期限 */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** 使用日時 */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    /** 発行日時 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum CodeStatus {
        ACTIVE,
        USED,
        EXPIRED,
        INVALIDATED
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isMaxAttemptsReached() {
        return attemptCount >= 5;
    }
}
