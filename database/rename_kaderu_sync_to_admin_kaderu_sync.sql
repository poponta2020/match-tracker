-- Kaderu同期通知種別を ADMIN チャネル経由にするため命名を変更:
--   KADERU_SYNC_COMPLETED → ADMIN_KADERU_SYNC_COMPLETED
--   KADERU_SYNC_FAILED    → ADMIN_KADERU_SYNC_FAILED
--
-- 背景:
-- /practice/new は ADMIN+ 限定だが、押下者の PLAYER チャネル binding は
-- LINKED でないことが多い。LineNotificationType.getRequiredChannelType() は
-- 名前が ADMIN_ で始まる種別を ADMIN チャネル要求と判定するため、ADMIN_ プレフィクスを
-- 付けることで押下者の ADMIN チャネル経由で通知が届くようになる。
--
-- 既存 KADERU_SYNC_* の line_message_log レコードは 0 件 (LINE 通知が SKIPPED で
-- 終わっていたため) なので、データ移行は不要。CHECK 制約のみ置換する。

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
        'ADMIN_KADERU_SYNC_FAILED'
    ));
