-- matches テーブルに venue_id カラムを追加し、既存データを backfill する
-- 関連機能: docs/features/match-list-venue-and-match-number/

-- 1) カラム追加 + FK + INDEX
ALTER TABLE matches ADD COLUMN venue_id BIGINT NULL;
ALTER TABLE matches ADD CONSTRAINT fk_matches_venue
    FOREIGN KEY (venue_id) REFERENCES venues(id) ON DELETE SET NULL;
CREATE INDEX idx_matches_venue ON matches(venue_id);

-- 2) backfill: 試合参加者（player1 / player2）が同日・同試合番号に active 参加した
--    practice_sessions の venue_id を集約し、一意であれば採用。
--    管理者代理登録時に created_by の参加会場が誤って採用されるのを防ぐ。
--    match_number でも絞ることで、同日複数会場・別試合番号の参加レコード混入を避ける。
--    pp.match_number IS NULL は「全試合参加」を意味する legacy データのため対象に含める。
UPDATE matches m SET venue_id = subq.venue_id
FROM (
  SELECT m.id AS match_id, MIN(ps.venue_id) AS venue_id
  FROM matches m
  JOIN practice_participants pp ON pp.player_id IN (m.player1_id, m.player2_id)
  JOIN practice_sessions ps ON ps.id = pp.session_id
  WHERE ps.session_date = m.match_date
    AND (pp.match_number = m.match_number OR pp.match_number IS NULL)
    AND ps.venue_id IS NOT NULL
    AND pp.status IN ('WON', 'PENDING')
    AND m.venue_id IS NULL
  GROUP BY m.id
  HAVING COUNT(DISTINCT ps.venue_id) = 1
) subq
WHERE m.id = subq.match_id;

-- 3) backfill: 同日の練習会場が一意であれば採用（複数会場が混在する日は NULL のまま）
UPDATE matches m SET venue_id = subq.venue_id
FROM (
  SELECT ps.session_date, MIN(ps.venue_id) AS venue_id
  FROM practice_sessions ps
  WHERE ps.venue_id IS NOT NULL
  GROUP BY ps.session_date
  HAVING COUNT(DISTINCT ps.venue_id) = 1
) subq
WHERE m.match_date = subq.session_date
  AND m.venue_id IS NULL;
