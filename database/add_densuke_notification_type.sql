-- notifications テーブルの type CHECK制約を更新
-- CHANNEL_RECLAIM_WARNING, DENSUKE_UNMATCHED_NAMES を追加

ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
  CHECK (type::text = ANY(ARRAY[
    'LOTTERY_WON',
    'LOTTERY_ALL_WON',
    'LOTTERY_REMAINING_WON',
    'LOTTERY_WAITLISTED',
    'WAITLIST_OFFER',
    'OFFER_EXPIRING',
    'OFFER_EXPIRED',
    'CHANNEL_RECLAIM_WARNING',
    'DENSUKE_UNMATCHED_NAMES'
  ]));
