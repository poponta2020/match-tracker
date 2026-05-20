-- ============================================================
-- Issue #671: 不可視BIDI制御文字を含むプレイヤー名の重複レコード論理削除
-- ============================================================
--
-- 本番DBに混入した不可視Unicode文字 (U+202A LEFT-TO-RIGHT EMBEDDING 等)
-- 付きの重複プレイヤーを論理削除する。
--
-- 経緯:
-- 伝助メンバー名フィールドに不可視BIDI制御文字が混入したまま登録され、
-- DensukeImportService が同名プレイヤーとマッチできず「未登録者」として
-- 検出 → 管理者の一括登録により別IDで重複作成されていた。
--
-- 対象 (2026-05-20 時点で本番DB確認済み):
--   id=39  "森保滉大"        ← 正規 (残す)
--   id=140 "(U+202A)⭐森保滉大"  ← 重複 (論理削除)
--   id=141 "(U+202A)森保滉大"    ← 重複 (論理削除)
--
-- id=140 / id=141 に紐づくレコード件数 (2026-05-20 確認):
--   practice_participants: 0, matches: 0, match_pairings: 0,
--   match_personal_notes: 0, notifications: 0,
--   densuke_member_mappings: 0, player_profiles: 0,
--   push_subscriptions: 0, line_*: 0, bye_activities: 0
--   → 業務データに影響なし
--   player_organizations / push_notification_preferences /
--     line_notification_preferences は各1件のみ。残置しても
--     deleted_at フィルタで参照されないため副作用なし。
--
-- コード側修正は DensukeScraper.stripLeadingEmoji の Cf (FORMAT) カテゴリ
-- 全般除去で対応済み。本SQL適用後、伝助同期は正規 id=39 にマッチする。
-- ============================================================

-- 安全柵: id 単独ではなく「期待する不可視文字付き名前」と AND 結合する。
-- 別環境・リストア後DBで万一 id=140/141 が別人に再採番されていた場合に
-- 誤って論理削除しないようにする。U& リテラルで U+202A LRE と U+2B50 ⭐ を
-- 明示的にエスケープし、それ以外の漢字はソース上でも視認できる literal を使う。
UPDATE players
SET deleted_at = NOW()
WHERE deleted_at IS NULL
  AND (
        (id = 140 AND name = U&'\202A\2B50' || '森保滉大')
    OR  (id = 141 AND name = U&'\202A'      || '森保滉大')
  );

-- 検証クエリ: 実行後に id=39 のみがアクティブで残ることを確認
SELECT id, name, length(name) AS chars, deleted_at
FROM players
WHERE name LIKE '%森保%' OR name LIKE '%滉大%'
ORDER BY id;
