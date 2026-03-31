-- lottery_executions テーブルに organization_id カラムを追加
-- 団体ごとの抽選実行・確定を区別するために必要

ALTER TABLE lottery_executions ADD COLUMN organization_id BIGINT;

-- 既存データの organization_id を埋める（該当セッションから推定）
-- 既存レコードがある場合は手動で適切な値を設定すること

-- 団体付きの重複チェック・検索用インデックス
CREATE INDEX idx_lottery_org ON lottery_executions (target_year, target_month, organization_id);
