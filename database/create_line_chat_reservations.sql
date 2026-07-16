-- LINEチャット予約送信による札分け全体配信（line-chat-reserve-broadcast）
--   1. line_chat_reservations（予約キュー本体）。
--      (broadcast_group_id, session_id) は非CANCELLED行が常に1件になる部分ユニークインデックス
--      idx_lcr_group_session_active で担保する。取消→再予約は旧行を CANCELLED にして新行を挿入するため、
--      CANCELLED 行は複数残りうる（部分ユニークの対象外）。
--      Hibernate(ddl-auto=update) は部分インデックスを自動生成しないため、migration SQL
--      ＋ DataInitializer#validateChatReservationDedupeIndex で作成・検証する。
--   2. line_broadcast_group にチャットルーム識別情報カラム（chat_room_id / chat_room_name）を追加。
--      ワーカーが「対象グループのチャットを開いて名称＋識別情報を照合」するために使う（Phase 3 で登録・nullable）。
--   3. line_message_log.notification_type の CHECK 制約に ADMIN_CHAT_RESERVE_ALERT を追加。
--      本番introspect（2026-07-17）で現行25値の CHECK が存在することを確認済み。既存値を1つも落とさず
--      新値を足すため、DROP → 全25値＋新値で張り直す（テンプレ古さで既存値を潰す事故を避ける）。
--
-- 本番 Render PostgreSQL への適用必須（entity 変更と同一 PR・CLAUDE.md 最重要ルール）。

-- ============================================================
-- 1. 予約キュー本体
-- ============================================================
CREATE TABLE IF NOT EXISTS line_chat_reservations (
    id BIGSERIAL PRIMARY KEY,
    broadcast_group_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN (
        'PENDING', 'RESERVING', 'RESERVED', 'FAILED',
        'MANUAL_REVIEW_REQUIRED', 'CANCEL_PENDING', 'CANCELLED', 'DRY_RUN_SUCCEEDED'
    )),
    message_text TEXT NOT NULL,
    scheduled_send_at TIMESTAMP NOT NULL,
    error_code VARCHAR(50),
    error_message TEXT,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- ワーカーのポーリング（PENDING/CANCEL_PENDING 取得）・リコンサイル（未来のactive行）用
CREATE INDEX IF NOT EXISTS idx_lcr_status ON line_chat_reservations (status);
CREATE INDEX IF NOT EXISTS idx_lcr_group_session ON line_chat_reservations (broadcast_group_id, session_id);
CREATE INDEX IF NOT EXISTS idx_lcr_scheduled ON line_chat_reservations (scheduled_send_at);

-- (配信グループ, セッション) の非CANCELLED行は常に1件（冪等・二重予約防止）。
-- CANCELLED は再予約の履歴として複数残せるよう対象外にする。
CREATE UNIQUE INDEX IF NOT EXISTS idx_lcr_group_session_active
    ON line_chat_reservations (broadcast_group_id, session_id)
    WHERE status <> 'CANCELLED';

-- ============================================================
-- 2. 配信グループにチャットルーム識別情報を追加（ワーカーの照合用・nullable）
-- ============================================================
ALTER TABLE line_broadcast_group ADD COLUMN IF NOT EXISTS chat_room_id VARCHAR(100);
ALTER TABLE line_broadcast_group ADD COLUMN IF NOT EXISTS chat_room_name VARCHAR(200);

-- ============================================================
-- 3. line_message_log.notification_type CHECK に ADMIN_CHAT_RESERVE_ALERT を追加
--    （現行25値＋新値で張り直す。既存値は1つも削らない）
-- ============================================================
ALTER TABLE line_message_log DROP CONSTRAINT IF EXISTS line_message_log_notification_type_check;
ALTER TABLE line_message_log ADD CONSTRAINT line_message_log_notification_type_check
    CHECK (notification_type IN (
        'LOTTERY_RESULT', 'WAITLIST_OFFER', 'OFFER_EXPIRED', 'MATCH_PAIRING',
        'PRACTICE_REMINDER', 'DEADLINE_REMINDER', 'ADMIN_WAITLIST_UPDATE',
        'WAITLIST_POSITION_UPDATE', 'SAME_DAY_CONFIRMATION', 'SAME_DAY_CANCEL',
        'ADMIN_SAME_DAY_CANCEL', 'SAME_DAY_VACANCY', 'ADMIN_SAME_DAY_CONFIRMATION',
        'MENTOR_COMMENT', 'MENTEE_MEMO_UPDATE', 'DENSUKE_PAGE_CREATED',
        'ADMIN_DENSUKE_PUSH_FAILED', 'ADMIN_DENSUKE_CONFIRM_DIFF',
        'ADMIN_DENSUKE_NAME_COLLISION', 'ADMIN_DENSUKE_ROWID_ISSUE',
        'ADMIN_DENSUKE_DELETE_DETECTED', 'ADMIN_KADERU_SYNC_COMPLETED',
        'ADMIN_KADERU_SYNC_FAILED', 'MATCH_VIDEO_REGISTERED', 'CARD_DIVISION_REMINDER',
        'ADMIN_CHAT_RESERVE_ALERT'
    ));
