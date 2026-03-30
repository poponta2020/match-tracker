-- practice_sessions テーブルの session_date UNIQUE制約を
-- (session_date, organization_id) の複合ユニーク制約に変更
-- 目的: 異なる団体が同日に練習セッションを持てるようにする

-- 既存の session_date 単体のUNIQUE制約を削除
ALTER TABLE practice_sessions DROP CONSTRAINT IF EXISTS practice_sessions_session_date_key;

-- (session_date, organization_id) の複合ユニーク制約を追加
ALTER TABLE practice_sessions ADD CONSTRAINT uk_session_date_organization UNIQUE (session_date, organization_id);
