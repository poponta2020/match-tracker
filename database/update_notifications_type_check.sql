-- notifications テーブルの type CHECK制約を更新
-- LOTTERY_ALL_WON, LOTTERY_REMAINING_WON を追加
-- Hibernate ddl-auto=update では既存のCHECK制約が自動更新されないため手動で対応

ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
  CHECK (type::text = ANY(ARRAY[
    'LOTTERY_WON',
    'LOTTERY_ALL_WON',
    'LOTTERY_REMAINING_WON',
    'LOTTERY_WAITLISTED',
    'WAITLIST_OFFER',
    'OFFER_EXPIRING',
    'OFFER_EXPIRED'
  ]));
