-- 隣室空き通知: NotificationType に ADJACENT_ROOM_AVAILABLE を追加
-- push_notification_preferences に adjacent_room カラムを追加

-- notifications の type CHECK制約を更新
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
    'ADJACENT_ROOM_AVAILABLE'
  ]));

-- push_notification_preferences に隣室通知設定カラムを追加
ALTER TABLE push_notification_preferences
  ADD COLUMN IF NOT EXISTS adjacent_room BOOLEAN NOT NULL DEFAULT true;
