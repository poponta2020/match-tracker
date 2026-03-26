package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import java.time.LocalDateTime;

/**
 * アプリ内通知エンティティ
 */
@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notification_player", columnList = "player_id"),
    @Index(name = "idx_notification_read", columnList = "player_id, is_read")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 通知先プレイヤーID */
    @Column(name = "player_id", nullable = false)
    private Long playerId;

    /** 通知種別 */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private NotificationType type;

    /** 通知タイトル */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** 通知本文 */
    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    /** 参照先エンティティ種別（例: "PRACTICE_PARTICIPANT"） */
    @Column(name = "reference_type", length = 50)
    private String referenceType;

    /** 参照先ID */
    @Column(name = "reference_id")
    private Long referenceId;

    /** 既読フラグ */
    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    /** 作成日時 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 論理削除日時 */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public enum NotificationType {
        /** 抽選結果（当選）※廃止：既存データ参照用に残す */
        LOTTERY_WON,
        /** 抽選結果（全試合当選まとめ） */
        LOTTERY_ALL_WON,
        /** 抽選結果（落選以外は全当選まとめ） */
        LOTTERY_REMAINING_WON,
        /** 抽選結果（落選・キャンセル待ち）※セッション単位 */
        LOTTERY_WAITLISTED,
        /** キャンセル待ちからの繰り上げ連絡 */
        WAITLIST_OFFER,
        /** 繰り上げ応答期限切れ警告 */
        OFFER_EXPIRING,
        /** 繰り上げ応答期限切れ */
        OFFER_EXPIRED
    }

    @PrePersist
    protected void onCreate() {
        createdAt = JstDateTimeUtil.now();
    }
}
