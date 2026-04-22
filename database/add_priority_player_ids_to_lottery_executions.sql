-- lottery_executions テーブルに priority_player_ids カラムを追加
-- 管理者が指定した優先選手IDをJSON配列文字列として監査用に保存する

ALTER TABLE lottery_executions
ADD COLUMN priority_player_ids TEXT NULL;

COMMENT ON COLUMN lottery_executions.priority_player_ids IS
  '管理者指定優先選手IDのJSON配列（例: [1,7,12]）。指定なしの場合はNULLまたは[]';
