-- Add dedupe_key column to line_message_log for session-level duplicate prevention.
-- Without this column, the duplicate check only considers player+type+date,
-- which suppresses notifications for different sessions on the same day.
ALTER TABLE line_message_log ADD COLUMN IF NOT EXISTS dedupe_key VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_lml_dedupe
    ON line_message_log (player_id, notification_type, dedupe_key, sent_at);
