-- match_comments テーブルに LINE通知送信済みフラグを追加
-- 既存コメントは既に即時通知済みのため true に設定
ALTER TABLE match_comments ADD COLUMN line_notified BOOLEAN NOT NULL DEFAULT false;
UPDATE match_comments SET line_notified = true;
