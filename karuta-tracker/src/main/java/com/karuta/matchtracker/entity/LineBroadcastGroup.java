package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import java.time.LocalDateTime;

/**
 * 全体LINE配信グループ設定エンティティ。
 *
 * <p>団体（organization）ごとに1つの全体LINEグループを表す。割り当てられた {@link ChannelType#GROUP}
 * 種別チャネル群（bot）をローテーションして、当日セッションの札分けテキストを一斉配信する。
 * 今回セットアップするのは北海道大学かるた会（org_id=2）の1グループのみだが、per-org でN個持てる設計。
 */
@Entity
@Table(name = "line_broadcast_group", indexes = {
    @Index(name = "idx_lbg_org", columnList = "organization_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LineBroadcastGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 配信対象の団体ID */
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    /** 管理用表示名 */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 有効フラグ（無効なら配信対象外） */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /**
     * 想定受信数（枠会計 {@code 当月送信数 + 想定受信数 ≤ 200} に使う）。
     * null の場合は送信時に LINE のグループ人数取得APIから実測する。
     */
    @Column(name = "expected_recipient_count")
    private Integer expectedRecipientCount;

    /**
     * チャット予約送信のワーカーが対象チャットを照合するための識別情報（初回登録時に保存・nullable）。
     * OAM のチャットURL等に含まれる安定した識別子。Phase 3（本番投入）で登録する。
     */
    @Column(name = "chat_room_id", length = 100)
    private String chatRoomId;

    /** チャット予約送信のワーカーが照合する表示名（グループ名。不一致なら TARGET_CHAT_MISMATCH で停止）。 */
    @Column(name = "chat_room_name", length = 200)
    private String chatRoomName;

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
