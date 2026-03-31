-- 抽選結果確定フロー用カラム追加
ALTER TABLE lottery_executions ADD COLUMN IF NOT EXISTS confirmed_at TIMESTAMP NULL;
ALTER TABLE lottery_executions ADD COLUMN IF NOT EXISTS confirmed_by BIGINT NULL;
