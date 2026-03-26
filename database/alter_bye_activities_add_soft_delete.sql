-- 抜け番活動記録テーブルに論理削除カラムを追加
ALTER TABLE bye_activities ADD COLUMN deleted_at TIMESTAMP NULL DEFAULT NULL;

-- 既存のユニーク制約を削除し、論理削除対応の部分ユニークインデックスに変更
ALTER TABLE bye_activities DROP CONSTRAINT uk_bye_activities_unique;
CREATE UNIQUE INDEX uk_bye_activities_unique
    ON bye_activities (session_date, match_number, player_id)
    WHERE deleted_at IS NULL;

-- 論理削除カラム用インデックス
CREATE INDEX idx_bye_activities_deleted_at ON bye_activities (deleted_at);
