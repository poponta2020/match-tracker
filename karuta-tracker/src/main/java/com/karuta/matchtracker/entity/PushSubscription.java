package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Web Pushサブスクリプションエンティティ
 */
@Entity
@Table(name = "push_subscriptions", indexes = {
    @Index(name = "idx_push_player", columnList = "player_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** プレイヤーID */
    @Column(name = "player_id", nullable = false)
    private Long playerId;

    /** Push APIエンドポイント */
    @Column(name = "endpoint", nullable = false, columnDefinition = "TEXT")
    private String endpoint;

    /** P-256 DH公開鍵 */
    @Column(name = "p256dh_key", nullable = false, length = 500)
    private String p256dhKey;

    /** 認証キー */
    @Column(name = "auth_key", nullable = false, length = 500)
    private String authKey;

    /** ブラウザ情報 */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /** 作成日時 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 更新日時 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
