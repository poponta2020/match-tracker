-- Add admin_same_day_cancel toggle to line_notification_preferences
ALTER TABLE line_notification_preferences
  ADD COLUMN IF NOT EXISTS admin_same_day_cancel BOOLEAN NOT NULL DEFAULT TRUE;
