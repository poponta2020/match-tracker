-- PR #751 レビュー指摘 (WARNING 1/2) を踏まえた matches.venue_id の再 backfill
-- 旧 add_venue_id_to_matches.sql は created_by 基準 / 全ステータス対象で誤った venue_id を
-- 永続化するケースがあったため、既存値をリセットし新ロジックで再計算する。
-- 新ロジック: 試合参加者（player1 / player2）の active 参加（WON / PENDING）venue を集約

-- 1) 既存の venue_id を NULL にリセット（旧ロジックで入った値も再評価する）
UPDATE matches SET venue_id = NULL;

-- 2) 試合参加者の active 参加 venue が一意なら採用
UPDATE matches m SET venue_id = subq.venue_id
FROM (
  SELECT m.id AS match_id, MIN(ps.venue_id) AS venue_id
  FROM matches m
  JOIN practice_participants pp ON pp.player_id IN (m.player1_id, m.player2_id)
  JOIN practice_sessions ps ON ps.id = pp.session_id
  WHERE ps.session_date = m.match_date
    AND ps.venue_id IS NOT NULL
    AND pp.status IN ('WON', 'PENDING')
  GROUP BY m.id
  HAVING COUNT(DISTINCT ps.venue_id) = 1
) subq
WHERE m.id = subq.match_id;

-- 3) 同日の練習会場が一意であれば採用（残り NULL 分）
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
