-- A-3: 抽選確定前の伝助差分通知 ADMIN_DENSUKE_CONFIRM_DIFF を
-- line_message_log.notification_type の CHECK 制約に追加する。
-- Hibernate ddl-auto=update は既存 CHECK 制約を自動更新しないため手動で対応する。
-- 適用しないと LINE メッセージログ挿入（ADMIN_DENSUKE_CONFIRM_DIFF）が CHECK 違反で失敗する。
-- 関連: docs/features/lottery-densuke-integrity/（タスク7 / A-3）

ALTER TABLE line_message_log DROP CONSTRAINT IF EXISTS line_message_log_notification_type_check;
ALTER TABLE line_message_log ADD CONSTRAINT line_message_log_notification_type_check
    CHECK (notification_type IN (
        'LOTTERY_RESULT',
        'WAITLIST_OFFER',
        'OFFER_EXPIRED',
        'MATCH_PAIRING',
        'PRACTICE_REMINDER',
        'DEADLINE_REMINDER',
        'ADMIN_WAITLIST_UPDATE',
        'WAITLIST_POSITION_UPDATE',
        'SAME_DAY_CONFIRMATION',
        'SAME_DAY_CANCEL',
        'ADMIN_SAME_DAY_CANCEL',
        'SAME_DAY_VACANCY',
        'ADMIN_SAME_DAY_CONFIRMATION',
        'MENTOR_COMMENT',
        'MENTEE_MEMO_UPDATE',
        'DENSUKE_PAGE_CREATED',
        'ADMIN_DENSUKE_PUSH_FAILED',
        'ADMIN_DENSUKE_CONFIRM_DIFF',
        'ADMIN_KADERU_SYNC_COMPLETED',
        'ADMIN_KADERU_SYNC_FAILED',
        'MATCH_VIDEO_REGISTERED'
    ));
