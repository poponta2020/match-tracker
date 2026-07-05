-- ============================================================================
-- A-4-b 重複4名（川瀬/高橋/山野/むらやま）統合: 事前調査クエリ集
--
-- 使い方: 各ブロックを個別ファイルに切り出して dbtool/Q.java で1文ずつ実行する
--   （Q.java は1文=1ファイル。複数文はここでは資料として1ファイルにまとめてある）
-- 目的: 統合対象ペアの playerId 特定・マスター選択・参照件数・衝突有無を確認する。
-- すべて読み取り専用（SELECT のみ）。
-- ============================================================================

-- [1] 正規化後に同名となる選手（空白由来の重複候補）を一覧
--     stripped = 半角/タブ/全角(U+3000)/NBSP(U+00A0) を除去した名前
WITH norm AS (
  SELECT id, name, deleted_at,
         replace(replace(replace(replace(name, chr(32), ''), chr(9), ''), chr(12288), ''), chr(160), '') AS stripped
  FROM players
)
SELECT a.id,
       replace(replace(replace(a.name, chr(32), '[SP]'), chr(12288), '[ZEN]'), chr(160), '[NBSP]') AS name_vis,
       a.stripped,
       (a.deleted_at IS NOT NULL) AS self_deleted,
       (SELECT count(*) FROM norm b WHERE b.id <> a.id AND b.stripped = a.stripped AND b.deleted_at IS NULL)      AS active_namesakes,
       (SELECT count(*) FROM norm b WHERE b.id <> a.id AND b.stripped = a.stripped AND b.deleted_at IS NOT NULL) AS deleted_namesakes
FROM norm a
WHERE (SELECT count(*) FROM norm b WHERE b.stripped = a.stripped AND b.deleted_at IS NULL) > 1
ORDER BY a.stripped, a.id;
-- → ここで 川瀬/高橋/山野/むらやま の active な重複ペアの id を確定する。
--   マスター(TO)は「空白等を含まない正規形の名前」を持つ側を原則とする（星野 #932 と同方針）。
--   両方が非正規 or 判断に迷う場合はユーザーに確認する。

-- ============================================================================
-- [2] players を参照する全 FK カラムを列挙（想定外の参照列がないか確認）
SELECT tc.table_name, kcu.column_name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu
  ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema
JOIN information_schema.constraint_column_usage ccu
  ON tc.constraint_name = ccu.constraint_name AND tc.table_schema = ccu.table_schema
WHERE tc.constraint_type = 'FOREIGN KEY' AND ccu.table_name = 'players'
ORDER BY tc.table_name, kcu.column_name;
-- → 既知の再ポイント対象:
--     matches(player1_id, player2_id, winner_id), match_pairings(player1_id, player2_id),
--     practice_participants(player_id)
--   既知の削除対象(重複行): player_organizations, line_notification_preferences,
--     push_notification_preferences (いずれも player_id)
--   これ以外の FK 参照列が出たら、MergeDuplicates の設定に追加するか、
--   統合前に手当てが必要。監査列(created_by/updated_by 等)や priority_player_ids も要確認。

-- ============================================================================
-- [3] 特定ペア <FROM>-<TO> の参照件数 + マージ後衝突チェック（<FROM>,<TO> を差し替えて実行）
--     衝突が1件でも > 0 の場合は自動統合を止め、手動で先に解消する。
SELECT * FROM (
  SELECT 'ref.matches.player1_id'      AS chk, count(*)::text AS val FROM matches               WHERE player1_id = <FROM>
  UNION ALL SELECT 'ref.matches.player2_id',      count(*)::text FROM matches               WHERE player2_id = <FROM>
  UNION ALL SELECT 'ref.matches.winner_id',       count(*)::text FROM matches               WHERE winner_id  = <FROM>
  UNION ALL SELECT 'ref.pairings.player1_id',     count(*)::text FROM match_pairings        WHERE player1_id = <FROM>
  UNION ALL SELECT 'ref.pairings.player2_id',     count(*)::text FROM match_pairings        WHERE player2_id = <FROM>
  UNION ALL SELECT 'ref.pp.player_id',            count(*)::text FROM practice_participants WHERE player_id  = <FROM>
  UNION ALL SELECT 'ref.player_orgs',             count(*)::text FROM player_organizations          WHERE player_id = <FROM>
  UNION ALL SELECT 'ref.line_prefs',              count(*)::text FROM line_notification_preferences WHERE player_id = <FROM>
  UNION ALL SELECT 'ref.push_prefs',              count(*)::text FROM push_notification_preferences WHERE player_id = <FROM>
  -- 衝突: pp UNIQUE(session_id, player_id, match_number) — FROM と TO が同一(session,match)に併存
  UNION ALL SELECT 'CONFLICT.pp_overlap', count(*)::text
    FROM practice_participants a JOIN practice_participants b
      ON a.session_id = b.session_id AND a.match_number IS NOT DISTINCT FROM b.match_number
    WHERE a.player_id = <FROM> AND b.player_id = <TO>
  -- 衝突: matches が自己対戦(p1==p2)化
  UNION ALL SELECT 'CONFLICT.matches_selfmatch', count(*)::text
    FROM matches WHERE (player1_id=<FROM> OR player2_id=<FROM>)
      AND (CASE WHEN player1_id=<FROM> THEN <TO> ELSE player1_id END)
        = (CASE WHEN player2_id=<FROM> THEN <TO> ELSE player2_id END)
  -- 衝突: matches UNIQUE(match_date,match_number,player1_id,player2_id) 重複化
  UNION ALL SELECT 'CONFLICT.matches_uq_dup', COALESCE(SUM(cnt-1),0)::text FROM (
    SELECT match_date, match_number,
      CASE WHEN player1_id=<FROM> THEN <TO> ELSE player1_id END AS p1,
      CASE WHEN player2_id=<FROM> THEN <TO> ELSE player2_id END AS p2, count(*) AS cnt
    FROM matches GROUP BY 1,2,3,4 HAVING count(*) > 1) d
  -- 衝突: match_pairings が自己ペア(p1==p2)化
  UNION ALL SELECT 'CONFLICT.pairings_selfpair', count(*)::text
    FROM match_pairings WHERE (player1_id=<FROM> OR player2_id=<FROM>)
      AND (CASE WHEN player1_id=<FROM> THEN <TO> ELSE player1_id END)
        = (CASE WHEN player2_id=<FROM> THEN <TO> ELSE player2_id END)
  -- 衝突: match_pairings UNIQUE(session_date,match_number,LEAST,GREATEST) 重複化
  UNION ALL SELECT 'CONFLICT.pairings_uq_dup', COALESCE(SUM(cnt-1),0)::text FROM (
    SELECT session_date, match_number,
      LEAST(  CASE WHEN player1_id=<FROM> THEN <TO> ELSE player1_id END,
              CASE WHEN player2_id=<FROM> THEN <TO> ELSE player2_id END) AS lo,
      GREATEST(CASE WHEN player1_id=<FROM> THEN <TO> ELSE player1_id END,
              CASE WHEN player2_id=<FROM> THEN <TO> ELSE player2_id END) AS hi, count(*) AS cnt
    FROM match_pairings GROUP BY 1,2,3,4 HAVING count(*) > 1) d
) x ORDER BY chk;
