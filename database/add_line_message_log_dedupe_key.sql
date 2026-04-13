-- Add dedupe_key column to line_message_log for session-level duplicate prevention.
-- Without this column, the duplicate check only considers player+type+date,
-- which suppresses notifications for different sessions on the same day.
ALTER TABLE line_message_log ADD COLUMN IF NOT EXISTS dedupe_key VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_lml_dedupe
    ON line_message_log (player_id, notification_type, dedupe_key, sent_at);

-- Partial unique index: prevent duplicate SUCCESS/RESERVED logs for the same player+type+dedupeKey per day.
-- Acts as a DB-level safety net against race conditions in concurrent duplicate checks.
-- RESERVED is included to prevent concurrent processes from both acquiring send rights.
-- NOTE: sent_at is stored in JST (via JstDateTimeUtil.now()), so sent_at::date yields the JST date.
DROP INDEX IF EXISTS idx_lml_dedupe_daily_unique;
CREATE UNIQUE INDEX idx_lml_dedupe_daily_unique
    ON line_message_log (player_id, notification_type, dedupe_key, (sent_at::date))
    WHERE status IN ('SUCCESS', 'RESERVED') AND dedupe_key IS NOT NULL;
