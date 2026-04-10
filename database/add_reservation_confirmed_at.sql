-- 隣室予約確認日時カラムを追加
-- 予約ステップ完了時にタイムスタンプがセットされ、expand-venue実行時にサーバー側で検証される
ALTER TABLE practice_sessions ADD COLUMN IF NOT EXISTS reservation_confirmed_at TIMESTAMP;
