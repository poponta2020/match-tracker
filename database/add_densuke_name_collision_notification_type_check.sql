-- A-4: 名寄せ衝突通知 DENSUKE_NAME_COLLISION を notifications.type の CHECK 制約に追加する。
-- Hibernate ddl-auto=update は既存 CHECK 制約を自動更新しないため手動で対応する。
-- 適用しないと名寄せ衝突通知（DENSUKE_NAME_COLLISION）のアプリ内通知挿入が CHECK 違反で
-- 失敗し、伝助インポートのトランザクションを巻き込む恐れがある。
-- 関連: docs/features/lottery-densuke-integrity/（タスク5 / A-4-a）

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
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
    'DENSUKE_UNMATCHED_NAMES',
    'DENSUKE_NAME_COLLISION',
    'ADJACENT_ROOM_AVAILABLE'
  ]));
