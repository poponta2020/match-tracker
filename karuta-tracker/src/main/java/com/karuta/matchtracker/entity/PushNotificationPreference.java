package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import java.time.LocalDateTime;

/**
 * Web Push通知種別ごとのON/OFF設定エンティティ
 */
@Entity
@Table(name = "push_notification_preferences", indexes = {
    @Index(name = "idx_pnp_player", columnList = "player_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushNotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** プレイヤーID（UNIQUE） */
    @Column(name = "player_id", nullable = false, unique = true)
    private Long playerId;

    /** Web Push全体のON/OFF */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = false;

    /** 抽選結果（LOTTERY_ALL_WON / LOTTERY_REMAINING_WON / LOTTERY_WAITLISTED） */
    @Column(name = "lottery_result", nullable = false)
    @Builder.Default
    private Boolean lotteryResult = true;

    /** キャンセル待ち繰り上げ（WAITLIST_OFFER） */
    @Column(name = "waitlist_offer", nullable = false)
    @Builder.Default
    private Boolean waitlistOffer = true;

    /** 繰り上げ期限切れ警告（OFFER_EXPIRING） */
    @Column(name = "offer_expiring", nullable = false)
    @Builder.Default
    private Boolean offerExpiring = true;

    /** 繰り上げ期限切れ（OFFER_EXPIRED） */
    @Column(name = "offer_expired", nullable = false)
    @Builder.Default
    private Boolean offerExpired = true;

    /** LINEチャネル回収警告（CHANNEL_RECLAIM_WARNING） */
    @Column(name = "channel_reclaim_warning", nullable = false)
    @Builder.Default
    private Boolean channelReclaimWarning = true;

    /** 伝助未登録者（DENSUKE_UNMATCHED_NAMES） */
    @Column(name = "densuke_unmatched", nullable = false)
    @Builder.Default
    private Boolean densukeUnmatched = true;

    /** 作成日時 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 更新日時 */
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
