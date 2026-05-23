-- matches テーブルに venue_id カラムを追加し、既存データを backfill する
-- 関連機能: docs/features/match-list-venue-and-match-number/

-- 1) カラム追加 + FK + INDEX
ALTER TABLE matches ADD COLUMN venue_id BIGINT NULL;
ALTER TABLE matches ADD CONSTRAINT fk_matches_venue
    FOREIGN KEY (venue_id) REFERENCES venues(id) ON DELETE SET NULL;
CREATE INDEX idx_matches_venue ON matches(venue_id);

-- 2) backfill: PracticeParticipant 経由（その選手がその日に参加した練習会の venue_id を最優先）
UPDATE matches m SET venue_id = subq.venue_id
FROM (
  SELECT DISTINCT ON (ps.session_date, pp.player_id)
    ps.session_date,
    pp.player_id,
    ps.venue_id
  FROM practice_sessions ps
  JOIN practice_participants pp ON pp.session_id = ps.id
  WHERE ps.venue_id IS NOT NULL
  ORDER BY ps.session_date, pp.player_id, ps.id
) subq
WHERE m.match_date = subq.session_date
  AND m.created_by = subq.player_id
  AND m.venue_id IS NULL;

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
