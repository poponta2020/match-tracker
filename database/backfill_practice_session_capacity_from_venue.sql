-- ============================================================================
-- backfill_practice_session_capacity_from_venue.sql
--
-- 目的:
--   既存 practice_sessions のうち capacity IS NULL のレコードを、
--   紐づく venue.capacity の値でバックフィルする。
--
-- 背景:
--   DensukeImportService.findOrCreateSession() で伝助同期から練習日を自動作成
--   する際、venue 解決は行っていたが capacity を設定していなかった。
--   そのため本番 DB に capacity NULL のレコードが多数残ってしまい、
--   カレンダー画面の試合別ステータスグリッド（〇△×）が表示されない不具合が発生。
--
-- 対象見積もり: 約 45 件
--   - わすらもち会 (venue_id=3,4): 33 件
--   - 北海道大学かるた会: 12 件
--   いずれも対応する venue.capacity が設定済み（NULL の venue はなし）。
--
-- 影響:
--   capacity が NULL だったセッションが venue 既定値で埋まることで、
--   カレンダーの試合別ステータスグリッドが表示されるようになる。
--   抽選結果や WaitlistPromotion の挙動は他カラムで確定済みのため影響なし。
--
-- 安全性:
--   - venue_id IS NULL のレコードは対象外（漏れの確認: 0 件想定）
--   - venue.capacity IS NULL のレコードも対象外（NULL のままにする）
--   - NOT NULL 制約は付け加えない（将来の「定員無制限」運用余地のため）
-- ============================================================================

UPDATE practice_sessions ps
SET capacity = v.capacity
FROM venues v
WHERE ps.venue_id = v.id
  AND ps.capacity IS NULL
  AND v.capacity IS NOT NULL;
