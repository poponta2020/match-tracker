-- iCalカレンダーフィード機能用のスキーマ変更
-- 対応Issue: #651（親Issue: #650）
--
-- 1. players.ical_feed_token   : iCalフィードURL用の推測困難なトークン（UNIQUE, NOT NULL）
-- 2. player_organizations.calendar_display_name : ユーザー×団体ごとのカレンダー表示名（NULL可）

-- pgcrypto 拡張（gen_random_bytes 用）
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. players.ical_feed_token カラム追加（一旦NULL可で追加し、既存行に値を埋めてからNOT NULL化）
ALTER TABLE players ADD COLUMN ical_feed_token VARCHAR(64);

UPDATE players
SET ical_feed_token = encode(gen_random_bytes(24), 'hex')
WHERE ical_feed_token IS NULL;

CREATE UNIQUE INDEX idx_players_ical_feed_token ON players(ical_feed_token);

ALTER TABLE players ALTER COLUMN ical_feed_token SET NOT NULL;

-- 2. player_organizations.calendar_display_name カラム追加
ALTER TABLE player_organizations ADD COLUMN calendar_display_name VARCHAR(50);
