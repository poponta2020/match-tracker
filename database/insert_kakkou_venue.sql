-- 東区民センター「かっこう（和室）」を venues に追加
-- 隣室空き確認（東🌸 ↔ かっこう）機能で使用
-- 既存レコードがあれば何もしない（冪等）
INSERT INTO venues (name, capacity, default_match_count, created_at, updated_at)
VALUES ('かっこう', 4, 2, NOW(), NOW())
ON CONFLICT (name) DO NOTHING;
