package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.time.LocalDate;

/**
 * 練習参加者エンティティ
 *
 * どの練習日にどの選手が参加したかを記録します。
 */
@Entity
@Table(name = "practice_participants",
    indexes = {
        @Index(name = "idx_participant_session", columnList = "session_id"),
        @Index(name = "idx_participant_player", columnList = "player_id")
    },
    uniqueConstraints = {
        // 同じセッション、同じ選手、同じ試合番号の組み合わせは一意
        // match_numberがnullの場合、複数レコード可能（DBの仕様上nullは一意制約から除外される）
        @UniqueConstraint(name = "uk_session_player_match", columnNames = {"session_id", "player_id", "match_number"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PracticeParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 練習セッションID
     */
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /**
     * 参加選手ID
     */
    @Column(name = "player_id", nullable = false)
    private Long playerId;

    /**
     * 参加する試合番号（1〜7）
     * nullの場合は全試合に参加することを示す（後方互換性のため）
     */
    @Column(name = "match_number")
    private Integer matchNumber;

    /**
     * 参加ステータス
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ParticipantStatus status = ParticipantStatus.WON;

    /**
     * キャンセル待ち番号（WAITLISTED時のみ）
     */
    @Column(name = "waitlist_number")
    private Integer waitlistNumber;

    /**
     * 紐づく抽選実行ID
     */
    @Column(name = "lottery_id")
    private Long lotteryId;

    /**
     * キャンセル理由コード
     */
    @Column(name = "cancel_reason", length = 50)
    private String cancelReason;

    /**
     * キャンセル理由詳細（その他の場合）
     */
    @Column(name = "cancel_reason_detail", columnDefinition = "TEXT")
    private String cancelReasonDetail;

    /**
     * キャンセル日時
     */
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    /**
     * 繰り上げ通知日時
     */
    @Column(name = "offered_at")
    private LocalDateTime offeredAt;

    /**
     * 繰り上げ応答期限
     */
    @Column(name = "offer_deadline")
    private LocalDateTime offerDeadline;

    /**
     * 繰り上げ応答日時
     */
    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

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
    }

    /**
     * エンティティ更新前の処理
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
