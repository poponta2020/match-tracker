package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import java.time.LocalDateTime;

/**
 * LINE通知種別ごとのON/OFF設定エンティティ
 */
@Entity
@Table(name = "line_notification_preferences", indexes = {
    @Index(name = "idx_lnp_player", columnList = "player_id")
}, uniqueConstraints = {
    @UniqueConstraint(columnNames = {"player_id", "organization_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LineNotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** プレイヤーID */
    @Column(name = "player_id", nullable = false)
    private Long playerId;

    /** 団体ID */
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    /** 抽選結果 */
    @Column(name = "lottery_result", nullable = false)
    @Builder.Default
    private Boolean lotteryResult = true;

    /** キャンセル待ち連絡 */
    @Column(name = "waitlist_offer", nullable = false)
    @Builder.Default
    private Boolean waitlistOffer = true;

    /** オファー期限切れ */
    @Column(name = "offer_expired", nullable = false)
    @Builder.Default
    private Boolean offerExpired = true;

    /** 対戦組み合わせ */
    @Column(name = "match_pairing", nullable = false)
    @Builder.Default
    private Boolean matchPairing = true;

    /** 参加予定リマインダー */
    @Column(name = "practice_reminder", nullable = false)
    @Builder.Default
    private Boolean practiceReminder = true;

    /** 締め切りリマインダー */
    @Column(name = "deadline_reminder", nullable = false)
    @Builder.Default
    private Boolean deadlineReminder = true;

    /** 更新日時 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        updatedAt = JstDateTimeUtil.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = JstDateTimeUtil.now();
    }
}
