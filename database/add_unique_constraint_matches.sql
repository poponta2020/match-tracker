-- 同日・同試合番号・同ペアの重複登録を防止するユニーク制約を追加
-- player1_id < player2_id が保証されている前提

-- まず既存の重複データを確認・削除（古い方を残す）
DELETE m1 FROM matches m1
INNER JOIN matches m2
  ON m1.match_date = m2.match_date
  AND m1.match_number = m2.match_number
  AND m1.player1_id = m2.player1_id
  AND m1.player2_id = m2.player2_id
  AND m1.id > m2.id;

-- ユニーク制約を追加
ALTER TABLE matches
  ADD CONSTRAINT uq_matches_date_number_players
  UNIQUE (match_date, match_number, player1_id, player2_id);
