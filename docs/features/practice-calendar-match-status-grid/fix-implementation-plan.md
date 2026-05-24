---
status: completed
---

# 練習カレンダー試合別ステータスグリッド 改修実装手順書

## 実装タスク

### タスク 1: DensukeImportService にセッション作成時の capacity 設定を追加
- [x] 完了
- **概要:** 伝助同期から新規の練習日を作成する際、解決済み venue の既定 capacity を `PracticeSession.builder()` に渡す。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeImportService.java`
    - L833-852 付近の `createSession` ヘルパー内、venue 解決ロジックを使って `venueObj` を取り出せるようにし、`PracticeSession.builder()` に `.capacity(venueObj != null ? venueObj.getCapacity() : null)` を追加
    - 既存の `venueId = venue.getId()` 取得時の `venue` 参照をそのまま再利用する（local 変数のスコープ調整が必要）
- **依存タスク:** なし
- **対応Issue:** #767

### タスク 2: DensukeImportServiceTest にテスト追加
- [x] 完了
- **概要:** 伝助同期セッション作成時、venue 既定 capacity が `PracticeSession.capacity` にセットされることを検証する。
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/DensukeImportServiceTest.java`（既存有無を実装前に確認。なければ新規作成）
    - ケース 1: venue 解決済み → `capacity == venue.getCapacity()`
    - ケース 2: venue 未解決 (`venueId == null`) → `capacity == null`
- **依存タスク:** タスク 1
- **対応Issue:** #768

### タスク 3: PracticeSessionService にフォールバックロジックを追加
- [x] 完了
- **概要:** `findSessionSummariesByYearMonth` で取得済みの venue 情報を活用し、`computeMatchCapacityStatuses` に venue 既定 capacity を渡す。`session.getCapacity()` が null なら venue 既定 capacity にフォールバックする。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeSessionService.java`
    - L150-159: 既存の `venueNameMap (Long → String)` の取得を、`Venue` オブジェクトマップ `(Long → Venue)` に置き換え（または並行保持）。`dto.setVenueName(...)` の呼び出しは `venueMap.get(...).getName()` 経由に変更
    - L200-210 付近: `computeMatchCapacityStatuses(session, effectiveCounts)` 呼び出しに venue 既定 capacity を渡す引数を追加
    - L225-249: `computeMatchCapacityStatuses` のシグネチャに `Integer venueDefaultCapacity` 引数を追加し、`Integer capacity = session.getCapacity()` の直後に `if (capacity == null) { capacity = venueDefaultCapacity; }` を挿入
- **依存タスク:** なし（タスク 1 とは独立。並行作業可）
- **対応Issue:** #769

### タスク 4: PracticeSessionServiceTest にフォールバックテストを追加
- [x] 完了
- **概要:** capacity フォールバックの 3 ケースを検証する。
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/PracticeSessionServiceTest.java`
    - ケース A: `session.capacity` 設定済み → 既存どおり session の値で判定
    - ケース B: `session.capacity == null` かつ venue 既定 capacity あり → venue 既定値で判定（〇×△ が返る）
    - ケース C: `session.capacity == null` かつ venue 既定 capacity も null → 既存どおり `matchCapacityStatuses == null`
- **依存タスク:** タスク 3
- **対応Issue:** #770

### タスク 5: 既存 NULL レコードのバックフィル SQL を作成
- [x] 完了
- **概要:** 本番 DB 上の `capacity IS NULL` かつ `venue_id IS NOT NULL` のレコード 45 件を venue 既定値で UPDATE する SQL を新規作成する。
- **変更対象ファイル:**
  - `database/backfill_practice_session_capacity_from_venue.sql`（新規）
    - 内容:
      ```sql
      UPDATE practice_sessions ps
      SET capacity = v.capacity
      FROM venues v
      WHERE ps.venue_id = v.id
        AND ps.capacity IS NULL
        AND v.capacity IS NOT NULL;
      ```
    - 冒頭にコメントで適用背景と影響行数の見積もり（45 件想定）を記載
- **依存タスク:** なし
- **対応Issue:** #771

### タスク 6: 本番 DB に SQL 適用
- [ ] 完了
- **概要:** `CLAUDE.local.md` の接続情報を使って psql 経由で本番 DB に適用し、影響行数を確認する。
- **変更対象ファイル:** なし（実行のみ）
  - 事前確認: `SELECT COUNT(*) FROM practice_sessions WHERE capacity IS NULL AND venue_id IS NOT NULL;` → 45 を確認
  - 実行: `PGPASSWORD='...' psql -h ... -U karuta -d karuta_tracker_k40h -f database/backfill_practice_session_capacity_from_venue.sql`
  - 事後確認: 同じ COUNT クエリが 0 になること、団体別件数の再確認
  - CLAUDE.md ルールに従い、適用タイミング（マージ前 or 直後）はユーザーと相談の上で実施
- **依存タスク:** タスク 5
- **対応Issue:** #772

### タスク 7: ドキュメント更新
- [x] 完了
- **概要:** 仕様書・設計書に capacity フォールバックの挙動を明記する。
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md`
    - 試合別ステータスグリッド表示の項目に「`capacity` 未設定時は venue 既定 capacity にフォールバック」を追記
  - `docs/DESIGN.md`
    - `PracticeSessionService.computeMatchCapacityStatuses` の仕様欄に venue 既定値フォールバックを追記
    - `DensukeImportService` の挙動説明に「venue 解決時は capacity を venue 既定値で初期化」を追記
  - `docs/SCREEN_LIST.md`
    - 必要なら「カレンダー画面」項目の表示条件説明を更新（capacity 未設定でも venue 既定値があれば表示）
- **依存タスク:** タスク 1, 3
- **対応Issue:** #773

---

## 実装順序

依存関係に基づいた実装順序:

1. **タスク 1** (DensukeImportService 修正) ＋ **タスク 5** (バックフィル SQL 作成) ＋ **タスク 3** (PracticeSessionService 修正) — 互いに独立、並行作業可
2. **タスク 2** (DensukeImportService テスト) ← タスク 1 完了後
3. **タスク 4** (PracticeSessionService テスト) ← タスク 3 完了後
4. **タスク 7** (ドキュメント更新) ← タスク 1, 3 完了後
5. **タスク 6** (本番 DB 適用) ← タスク 5 完了後、ユーザー確認の上で実施。通常はマージ後に実行

すべてのタスクが完了 → `/prepare-pr` → `/review` → `/ship` の通常パイプラインへ。
