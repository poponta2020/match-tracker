-- match_pairings の「同日・同試合番号・同ペア（順不同）」の重複登録を防止するユニークインデックス。
--
-- 背景: Issue #900。match_pairings には matches の uq_matches_date_number_players 相当の
-- 一意制約が無く、createBatch/create が dedup せず save するため、フロントが同一ペアを
-- 多重に含むペイロードを送ると重複行が大量に蓄積した（2026-06-23 第2試合 久米田×飯島 20行）。
--
-- matches と異なり match_pairings は player1_id < player2_id を正規化していないため、
-- 列そのままの UNIQUE 制約では (a,b) と (b,a) の順不同重複を防げない。
-- LEAST/GREATEST による関数インデックスで順不同に正規化して一意性を担保する。
--
-- 対象DB: Render PostgreSQL（本番兼ローカル開発）。Hibernate では関数インデックスを
-- 管理できないため、本ファイルを psql で手動適用する（CLAUDE.md のDBマイグレーション適用ルール参照）。

-- 1) 念のため既存の順不同重複を除去（最小IDの1行を残し、それ以外を削除）
DELETE FROM match_pairings mp
USING match_pairings keep
WHERE mp.session_date = keep.session_date
  AND mp.match_number = keep.match_number
  AND LEAST(mp.player1_id, mp.player2_id)    = LEAST(keep.player1_id, keep.player2_id)
  AND GREATEST(mp.player1_id, mp.player2_id) = GREATEST(keep.player1_id, keep.player2_id)
  AND mp.id > keep.id;

-- 2) 順不同ペアのユニークインデックスを作成
CREATE UNIQUE INDEX IF NOT EXISTS uq_match_pairings_date_number_players
  ON match_pairings (
    session_date,
    match_number,
    LEAST(player1_id, player2_id),
    GREATEST(player1_id, player2_id)
  );
