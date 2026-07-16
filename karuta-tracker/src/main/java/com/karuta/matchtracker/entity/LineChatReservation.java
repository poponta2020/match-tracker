package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import java.time.LocalDateTime;

/**
 * LINEチャット予約送信の予約キューエンティティ（line-chat-reserve-broadcast）。
 *
 * <p>札分けの全体LINE配信を、Messaging API push（通数課金）ではなく LINE Official Account Manager の
 * 「チャット予約送信」（無料通数の対象外）で行うための予約レコード。アプリは (配信グループ, セッション)
 * 単位で予約タスクを生成し、VM常駐の Playwright ワーカーが OAM 上で予約登録して結果を報告する。
 * 実送信はLINE側の予約機構が担う。
 *
 * <p><b>一意性</b>: 非 {@code CANCELLED} 行は (broadcast_group_id, session_id) につき常に1件
 * （部分ユニークインデックス {@code idx_lcr_group_session_active}）。編集は「取消→再予約」に正規化するため、
 * 旧行を {@code CANCELLED} にして新行を挿入する運用となり CANCELLED 行は履歴として複数残りうる。
 *
 * <p><b>状態遷移</b>（結果報告APIで検証・タスク3）:
 * <pre>
 *   PENDING → RESERVING → RESERVED | FAILED | MANUAL_REVIEW_REQUIRED | DRY_RUN_SUCCEEDED
 *   FAILED → PENDING（管理画面の手動再試行）
 *   CANCEL_PENDING → CANCELLED | MANUAL_REVIEW_REQUIRED
 * </pre>
 *
 * <p>部分ユニークインデックスは Hibernate(ddl-auto=update) では自動生成されないため、migration SQL
 * （{@code database/create_line_chat_reservations.sql}）＋ {@code DataInitializer} で作成・検証する。
 */
@Entity
@Table(name = "line_chat_reservations", indexes = {
    @Index(name = "idx_lcr_status", columnList = "status"),
    @Index(name = "idx_lcr_group_session", columnList = "broadcast_group_id, session_id"),
    @Index(name = "idx_lcr_scheduled", columnList = "scheduled_send_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LineChatReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 配信グループID（FK） */
    @Column(name = "broadcast_group_id", nullable = false)
    private Long broadcastGroupId;

    /** 対象練習セッションID（FK） */
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** 予約ステータス */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ReservationStatus status;

    /** 予約する本文（個人通知と完全同一の札分けテキスト） */
    @Column(name = "message_text", nullable = false, columnDefinition = "TEXT")
    private String messageText;

    /** 送信予定時刻（JST。1試合目開始30分前／情報なしは8:00） */
    @Column(name = "scheduled_send_at", nullable = false)
    private LocalDateTime scheduledSendAt;

    /** エラーコード（TARGET_CHAT_MISMATCH / LINE_AUTH_EXPIRED 等・機械可読） */
    @Column(name = "error_code", length = 50)
    private String errorCode;

    /** エラー詳細（本文・認証情報は含めない） */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** 試行回数 */
    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    /** 作成日時 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 更新日時 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 予約ステータス。
     * <ul>
     *   <li>{@code PENDING} — 予約待ち（ワーカーが未処理／手動再試行で戻された）</li>
     *   <li>{@code RESERVING} — ワーカーが処理中（30分超滞留で MANUAL_REVIEW_REQUIRED へ昇格）</li>
     *   <li>{@code RESERVED} — LINE側に予約登録済み（「送信予定」検証を通過）。フォールバックpushを抑止する</li>
     *   <li>{@code FAILED} — 予約失敗（再試行可能）</li>
     *   <li>{@code MANUAL_REVIEW_REQUIRED} — 結果不明・重複検出・滞留等で人手確認が必要（自動再試行しない・pushも抑止）</li>
     *   <li>{@code CANCEL_PENDING} — 取消要求済み（セッション削除/変更検知）。ワーカーがLINE側予約を削除する</li>
     *   <li>{@code CANCELLED} — 取消完了（履歴として残る・部分ユニークの対象外）</li>
     *   <li>{@code DRY_RUN_SUCCEEDED} — dry-run（確定ボタンを押さずに全手順＋スクショ）成功</li>
     * </ul>
     */
    public enum ReservationStatus {
        PENDING,
        RESERVING,
        RESERVED,
        FAILED,
        MANUAL_REVIEW_REQUIRED,
        CANCEL_PENDING,
        CANCELLED,
        DRY_RUN_SUCCEEDED
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = JstDateTimeUtil.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = JstDateTimeUtil.now();
    }
}
