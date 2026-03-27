-- 選手テーブルにパスワード変更要求フラグを追加
ALTER TABLE players ADD COLUMN require_password_change BOOLEAN NOT NULL DEFAULT FALSE;
