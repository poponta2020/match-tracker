-- 試合動画登録通知: 試合動画登録時に対戦当事者（登録者自身を除く）へ送る LINE 通知
-- line_notification_preferences に match_video_registered カラムを追加
-- line_message_log の notification_type CHECK制約に MATCH_VIDEO_REGISTERED を追加
--
-- CHECK 制約は rename_kaderu_sync_to_admin_kaderu_sync.sql 時点の許容リスト（全 19 種別）に
-- MATCH_VIDEO_REGISTERED を加えた最新リストで張り直す。

ALTER TABLE line_notification_preferences
    ADD COLUMN IF NOT EXISTS match_video_registered BOOLEAN NOT NULL DEFAULT TRUE;

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
        'SAME_DAY_VACANCY',
        'ADMIN_SAME_DAY_CONFIRMATION',
        'ADMIN_SAME_DAY_CANCEL',
        'MENTOR_COMMENT',
        'MENTEE_MEMO_UPDATE',
        'DENSUKE_PAGE_CREATED',
        'ADMIN_DENSUKE_PUSH_FAILED',
        'ADMIN_KADERU_SYNC_COMPLETED',
        'ADMIN_KADERU_SYNC_FAILED',
        'MATCH_VIDEO_REGISTERED'
    ));
