-- line_channels テーブルに channel_type カラムを追加
ALTER TABLE line_channels ADD COLUMN channel_type VARCHAR(10) NOT NULL DEFAULT 'PLAYER';

-- 既存レコードを PLAYER に設定（デフォルト値で自動適用済みだが明示的に）
UPDATE line_channels SET channel_type = 'PLAYER' WHERE channel_type IS NULL;

-- インデックス追加
CREATE INDEX idx_line_channel_type ON line_channels (channel_type);

-- line_channel_assignments テーブルに channel_type カラムを追加
ALTER TABLE line_channel_assignments ADD COLUMN channel_type VARCHAR(10) NOT NULL DEFAULT 'PLAYER';

-- 既存レコードを PLAYER に設定
UPDATE line_channel_assignments SET channel_type = 'PLAYER' WHERE channel_type IS NULL;

-- インデックス追加
CREATE INDEX idx_lca_player_type ON line_channel_assignments (player_id, channel_type);
