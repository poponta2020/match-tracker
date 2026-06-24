-- match_pairings に手動ロックフラグ locked を追加する。
-- 結果未入力でもユーザーが明示的にロックした組を、自動組み合わせ・一括保存・回戦削除から
-- 保護するためのフラグ。既存行はすべて false（未ロック）となるよう DEFAULT FALSE / NOT NULL。
-- 参照: docs/features/pairing-manual-lock/requirements.md
ALTER TABLE match_pairings ADD COLUMN locked BOOLEAN NOT NULL DEFAULT FALSE;
