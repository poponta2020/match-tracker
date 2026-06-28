-- ============================================================
-- Issue #833: 「小峯帆乃夏（こみねほのか）」さん 重複プレイヤーの統合
-- ============================================================
--
-- ■ 背景
-- 2026-03-15 の伝助一括登録経路が DensukeScraper.stripLeadingEmoji を
-- 通らず、プレイヤー名先頭に初心者マーク 🔰 (U+1F530) が付いたまま
-- 別IDで登録され、同一人物が重複した（Issue #671 の不可視文字版と同型の再発）。
--
-- ■ 対象（2026-05-30 本番DB karuta_tracker_k40h で確認。読みは「こみねほのか」）
--   id=19  小峯帆乃夏    … 🔰なし・正しい表記。練習35/試合/伝助2。← マスター（残す）
--   id=37  🔰小峯帆乃夏  … 🔰あり・同名重複。練習50/試合20/伝助1/通知27/所属2。← id=19 に統合し論理削除
--   id=139 小峰帆乃香    … 別人だが業務データ皆無の誤登録（峰・香）。← 論理削除（オーナー判断）
--
--   ※ オーナー確認: 正しい人物は id=19「小峯帆乃夏」。id=37 をここへ統合する。
--   ※ id=138「庄田裕菜」は全くの別人。本SQLは一切触れない。
--
-- ■ players.name は UNIQUE(name) かつ deleted_at 非考慮
--   id=19 は既に正しい名前を保有しているため、マスターを id=19 にすると
--   name のリネーム/🔰除去が一切不要で UNIQUE(name) 衝突が起きない（最もクリーン）。
--   source id=37 は論理削除時に名前へ統合マーカーを付与して 🔰 も外す（byte 検索の
--   清浄化。付与後の名前は接尾辞付きで一意なので衝突しない）。
--
-- ■ 各テーブルの UNIQUE 衝突（master=19 と source=37 で 2026-05-30 に事前計算済み）
--   practice_participants: UNIQUE(session_id, player_id, match_number)
--     → session 361/362/363 の 9 件が衝突（19 と 37 が同一練習試合に二重参加）。
--       衝突する 37 側 9 行は削除し、残り 41 行を 19 へ付け替える。
--   player_organizations: UNIQUE(player_id, organization_id)
--     → org1 が衝突（19,37 とも所属）。37 側 org1 を削除、org2 を 19 へ付け替え。
--   matches: UNIQUE(match_date, match_number, player1_id, player2_id)
--     → 19 対 37 の対戦は 0 件、付け替え後の重複/自己対戦も 0 件。単純付け替え可。
--   match_pairings / densuke_member_mappings / notifications: 衝突なし。単純付け替え。
--   その他 (mentor/notes/comments/profile/push/line/bye/lottery): 19・37 とも 0 件。
--
-- ■ 適用方法: psql --single-transaction -v ON_ERROR_STOP=1 で全体を 1 トランザクション。
--   前提ガード/後検証ガードの DO ブロックが、名前不一致や統合による自己対戦化・
--   残存参照を検出した場合に例外で全体を中断する（別環境・リストア後DBで id が
--   別人に再採番されていても誤実行しない / #671 と同方針）。
-- ============================================================

-- ------------------------------------------------------------
-- 安全柵(前提): 期待する master(19)/source(37) でなければ中断。
--   さらに統合で自己対戦(19 vs 19)が生じる条件（19と37が同一試合/ペア）が
--   無いことも確認する。本番はライブ稼働中のため、レースで生じても例外で全体を巻き戻す。
-- ------------------------------------------------------------
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM players WHERE id = 19 AND name = '小峯帆乃夏' AND deleted_at IS NULL) THEN
    RAISE EXCEPTION 'ABORT: master id=19 is not active "小峯帆乃夏" (got %)',
      (SELECT name FROM players WHERE id = 19);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM players WHERE id = 37 AND name = E'\U0001F530' || '小峯帆乃夏' AND deleted_at IS NULL) THEN
    RAISE EXCEPTION 'ABORT: source id=37 is not active "🔰小峯帆乃夏" (got %)',
      (SELECT name FROM players WHERE id = 37);
  END IF;
  IF EXISTS (SELECT 1 FROM matches
             WHERE (player1_id=37 AND player2_id=37)
                OR (player1_id=19 AND player2_id=37)
                OR (player1_id=37 AND player2_id=19)) THEN
    RAISE EXCEPTION 'ABORT: a match pairs 19 and/or 37 together; remap would create a self-match';
  END IF;
  IF EXISTS (SELECT 1 FROM match_pairings
             WHERE (player1_id=37 AND player2_id=37)
                OR (player1_id=19 AND player2_id=37)
                OR (player1_id=37 AND player2_id=19)) THEN
    RAISE EXCEPTION 'ABORT: a pairing pairs 19 and/or 37 together; remap would create a self-pairing';
  END IF;
END $$;

-- ------------------------------------------------------------
-- Step 1. practice_participants: 衝突する 37 側 9 行を削除 → 残りを 19 へ付け替え
--   削除対象 = 同一 (session_id, match_number) に 19 が既に存在する 37 の行
-- ------------------------------------------------------------
DELETE FROM practice_participants pp37
WHERE pp37.player_id = 37
  AND EXISTS (
    SELECT 1 FROM practice_participants pp19
    WHERE pp19.player_id = 19
      AND pp19.session_id = pp37.session_id
      AND pp19.match_number = pp37.match_number
  );

UPDATE practice_participants SET player_id = 19 WHERE player_id = 37;

-- ------------------------------------------------------------
-- Step 2. player_organizations: 重複する 37/org1 を削除 → 残り (org2) を 19 へ付け替え
-- ------------------------------------------------------------
DELETE FROM player_organizations po37
WHERE po37.player_id = 37
  AND EXISTS (
    SELECT 1 FROM player_organizations po19
    WHERE po19.player_id = 19
      AND po19.organization_id = po37.organization_id
  );

UPDATE player_organizations SET player_id = 19 WHERE player_id = 37;

-- ------------------------------------------------------------
-- Step 3. 単純付け替え（衝突なしを事前確認済み）
-- ------------------------------------------------------------
UPDATE matches               SET player1_id = 19 WHERE player1_id = 37;
UPDATE matches               SET player2_id = 19 WHERE player2_id = 37;
UPDATE matches               SET winner_id  = 19 WHERE winner_id  = 37;
UPDATE match_pairings        SET player1_id = 19 WHERE player1_id = 37;
UPDATE match_pairings        SET player2_id = 19 WHERE player2_id = 37;
UPDATE densuke_member_mappings SET player_id = 19 WHERE player_id = 37;
UPDATE notifications         SET player_id = 19 WHERE player_id = 37;

-- ------------------------------------------------------------
-- Step 4. source(37) を論理削除（統合マーカー付与＋🔰除去。接尾辞付きで name は一意）
-- ------------------------------------------------------------
UPDATE players
SET name = '小峯帆乃夏（統合済 id37→id19）',
    deleted_at = NOW(),
    updated_at = NOW()
WHERE id = 37
  AND deleted_at IS NULL
  AND name = E'\U0001F530' || '小峯帆乃夏';

-- ------------------------------------------------------------
-- Step 5. 別人だが業務データ皆無の誤登録 id=139 を論理削除（オーナー判断）
-- ------------------------------------------------------------
UPDATE players
SET deleted_at = NOW(),
    updated_at = NOW()
WHERE id = 139
  AND deleted_at IS NULL
  AND name = '小峰帆乃香';

-- ------------------------------------------------------------
-- 安全柵(後検証): source(37) の業務データが残っていない／自己対戦が
--   生じていないことをコミット前に確認。レースで混入した場合はここで
--   例外を投げ、トランザクション全体を巻き戻す。
-- ------------------------------------------------------------
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM matches WHERE player1_id=37 OR player2_id=37 OR winner_id=37)
     OR EXISTS (SELECT 1 FROM match_pairings WHERE player1_id=37 OR player2_id=37)
     OR EXISTS (SELECT 1 FROM practice_participants WHERE player_id=37)
     OR EXISTS (SELECT 1 FROM densuke_member_mappings WHERE player_id=37)
     OR EXISTS (SELECT 1 FROM notifications WHERE player_id=37)
     OR EXISTS (SELECT 1 FROM player_organizations WHERE player_id=37) THEN
    RAISE EXCEPTION 'ABORT: leftover references to id=37 remain after remap';
  END IF;
  IF EXISTS (SELECT 1 FROM matches WHERE player1_id=19 AND player2_id=19)
     OR EXISTS (SELECT 1 FROM match_pairings WHERE player1_id=19 AND player2_id=19) THEN
    RAISE EXCEPTION 'ABORT: a self-match/self-pairing (19 vs 19) was created';
  END IF;
END $$;

-- ============================================================
-- 検証クエリ（適用後に手動確認。トランザクション末尾で出力）
-- ============================================================
-- (1) 「こみねほのか」: id=19「小峯帆乃夏」のみ active。37/139 は deleted。
SELECT id, name, encode(convert_to(name, 'UTF8'), 'hex') AS name_hex, deleted_at
FROM players WHERE name LIKE '%帆乃%' ORDER BY id;

-- (2) マスター id=19 に集約された件数
SELECT
  (SELECT count(*) FROM matches WHERE player1_id=19 OR player2_id=19) AS matches_19,
  (SELECT count(*) FROM practice_participants WHERE player_id=19)     AS practice_19,
  (SELECT count(*) FROM notifications WHERE player_id=19)             AS notifs_19,
  (SELECT count(*) FROM densuke_member_mappings WHERE player_id=19)   AS densuke_19,
  (SELECT count(*) FROM player_organizations WHERE player_id=19)      AS orgs_19;

-- (3) source(37) に業務データが残っていないこと（すべて 0 を想定）
SELECT
  (SELECT count(*) FROM matches WHERE player1_id=37 OR player2_id=37 OR winner_id=37) AS matches_37,
  (SELECT count(*) FROM practice_participants WHERE player_id=37)     AS practice_37,
  (SELECT count(*) FROM match_pairings WHERE player1_id=37 OR player2_id=37) AS pairings_37,
  (SELECT count(*) FROM notifications WHERE player_id=37)             AS notifs_37,
  (SELECT count(*) FROM densuke_member_mappings WHERE player_id=37)   AS densuke_37,
  (SELECT count(*) FROM player_organizations WHERE player_id=37)      AS orgs_37;

-- (4) 庄田裕菜(id=138) が無傷であること
SELECT id, name, updated_at, deleted_at FROM players WHERE id = 138;
