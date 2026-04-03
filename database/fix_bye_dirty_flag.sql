-- 伝助同期の未入力保護: BYE dirtyフラグ補正SQL
-- 実行タイミング: タスク1〜4のコード変更をデプロイした直後に1回実行
-- 目的: 既存のBYE(matchNumber=NULL)エントリのdirty=trueを解消し、
--        不要な伝助同期トリガーを停止する
--
-- 実行手順:
-- 1. Render PostgreSQLに接続
-- 2. 事前確認 → 補正実行 → 事後確認 の順に実行
-- 3. 次回の通常同期サイクル（5分以内）で不要な×上書きが発生しないことを伝助上で確認

-- 事前確認: 対象件数の把握
SELECT COUNT(*) AS bye_dirty_count
FROM practice_participants
WHERE match_number IS NULL AND dirty = true;

-- 補正実行
UPDATE practice_participants
SET dirty = false
WHERE match_number IS NULL AND dirty = true;

-- 事後確認: 対象が0件になったことを確認
SELECT COUNT(*) AS bye_dirty_count
FROM practice_participants
WHERE match_number IS NULL AND dirty = true;
