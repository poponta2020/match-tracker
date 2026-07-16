package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import java.time.LocalDateTime;

/**
 * 全体LINE配信の送信ログ兼 dedupe エンティティ。
 *
 * <p>個人通知の {@code line_message_log}（player_id スコープの dedupe）は全体配信には流用できないため、
 * (配信グループ, セッション) スコープの専用テーブルで原子的に「一度きり」を担保する。
 * 部分ユニークインデックス {@code idx_lbs_dedupe (broadcast_group_id, session_id) WHERE status IN ('SUCCESS','RESERVED')}
 * ＋ {@code INSERT ... ON CONFLICT DO NOTHING} により、ポーリングや再起動で複数回トリガーされても1回に収束する。
 * このテーブルが管理画面の配信履歴・枯渇判定の実体も兼ねる。
 *
 * <p>部分ユニークインデックスは Hibernate(ddl-auto=update) では自動生成されないため、migration SQL
 * ＋ {@code DataInitializer#validateBroadcastDedupeIndex} で作成・検証する。
 */
@Entity
@Table(name = "line_broadcast_send", indexes = {
    @Index(name = "idx_lbs_group_session", columnList = "broadcast_group_id, session_id"),
    @Index(name = "idx_lbs_group_sent", columnList = "broadcast_group_id, sent_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LineBroadcastSend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 配信グループID（FK） */
    @Column(name = "broadcast_group_id", nullable = false)
    private Long broadcastGroupId;

    /** 対象練習セッションID（FK） */
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** 配信に使用した bot（GROUP 種別チャネル）ID。枯渇 SKIPPED 時は null */
    @Column(name = "line_channel_id")
    private Long lineChannelId;

    /** 想定受信数（送信成功時に bot の当月消費へ即時加算する値） */
    @Column(name = "recipient_count")
    private Integer recipientCount;

    /** 送信ステータス */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BroadcastStatus status;

    /** 失敗・スキップ理由 */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** 送信（予約）日時 */
    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    public enum BroadcastStatus {
        /** 送信権を確保済み（送信処理中）。dedupe 対象 */
        RESERVED,
        /** 送信成功。dedupe 対象 */
        SUCCESS,
        /** 送信失敗（再試行可能・dedupe 非対象） */
        FAILED,
        /** 全bot枯渇でスキップ（送信せず＝課金なし・dedupe 非対象） */
        SKIPPED
    }

    @PrePersist
    protected void onCreate() {
        if (sentAt == null) {
            sentAt = JstDateTimeUtil.now();
        }
    }
}
