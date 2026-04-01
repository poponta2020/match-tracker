-- 管理者向けキャンセル待ち状況通知の設定カラムを追加
ALTER TABLE line_notification_preferences
    ADD COLUMN IF NOT EXISTS admin_waitlist_update BOOLEAN NOT NULL DEFAULT TRUE;

-- line_message_log の notification_type チェック制約を更新
ALTER TABLE line_message_log DROP CONSTRAINT IF EXISTS line_message_log_notification_type_check;
ALTER TABLE line_message_log ADD CONSTRAINT line_message_log_notification_type_check
    CHECK (notification_type IN ('LOTTERY_RESULT', 'WAITLIST_OFFER', 'OFFER_EXPIRED', 'MATCH_PAIRING', 'PRACTICE_REMINDER', 'DEADLINE_REMINDER', 'ADMIN_WAITLIST_UPDATE'));
