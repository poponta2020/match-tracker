-- densuke_member_mappings に (densuke_url_id, densuke_member_id) のユニーク制約を追加
-- 同一伝助メンバーIDが複数のプレイヤーにマッピングされることを防止する

-- Step 1: 重複候補を確認する（実行前に目視確認推奨）
-- SELECT dmm.id, dmm.densuke_url_id, dmm.densuke_member_id, dmm.player_id, p.name, dmm.created_at
-- FROM densuke_member_mappings dmm
-- JOIN players p ON p.id = dmm.player_id
-- WHERE (dmm.densuke_url_id, dmm.densuke_member_id) IN (
--   SELECT densuke_url_id, densuke_member_id
--   FROM densuke_member_mappings
--   GROUP BY densuke_url_id, densuke_member_id
--   HAVING COUNT(*) > 1
-- )
-- ORDER BY dmm.densuke_url_id, dmm.densuke_member_id, dmm.created_at;

-- Step 2: 既存の重複データを解消する
-- 保持基準: 最も古い行（MIN(id)＝最初に作成されたマッピング）を正とし、後から作られた重複行を削除する
-- 理由: 最初のマッピングが本来の正しい紐付けであり、後続の重複は誤マッピングにより生じたものであるため
DELETE FROM densuke_member_mappings
WHERE id NOT IN (
  SELECT MIN(id)
  FROM densuke_member_mappings
  GROUP BY densuke_url_id, densuke_member_id
);

-- Step 3: 重複が解消された状態でユニーク制約を追加（再実行耐性あり）
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'uq_densuke_member_mappings_url_member'
  ) THEN
    ALTER TABLE densuke_member_mappings
      ADD CONSTRAINT uq_densuke_member_mappings_url_member
      UNIQUE (densuke_url_id, densuke_member_id);
  END IF;
END $$;
