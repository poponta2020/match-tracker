-- 札分けリマインダー通知（1試合目開始3時間前）:
--   line_notification_preferences に card_division_reminder カラムを追加。
--   既存の通知種別はデフォルト ON だが、本種別は購読制のため DEFAULT FALSE（既存慣習と逆）。
--   line_message_log の notification_type CHECK 制約に CARD_DIVISION_REMINDER を追加。
--
-- CHECK 制約は本番 line_message_log_notification_type_check（2026-07-15 時点の introspect で
-- 全 24 種別）に CARD_DIVISION_REMINDER を加えた 25 種別で張り直す。
-- テンプレート add_match_video_registered_notification.sql は ADMIN_DENSUKE_* 4 種別が欠落した
-- 古いリストのため、そのままコピーせず本番 introspect + enum を突き合わせた最新リストを用いる。

ALTER TABLE line_notification_preferences
    ADD COLUMN IF NOT EXISTS card_division_reminder BOOLEAN NOT NULL DEFAULT FALSE;

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
        'ADMIN_DENSUKE_NAME_COLLISION',
        'ADMIN_DENSUKE_ROWID_ISSUE',
        'ADMIN_DENSUKE_DELETE_DETECTED',
        'ADMIN_KADERU_SYNC_COMPLETED',
        'ADMIN_KADERU_SYNC_FAILED',
        'MATCH_VIDEO_REGISTERED',
        'CARD_DIVISION_REMINDER'
    ));
