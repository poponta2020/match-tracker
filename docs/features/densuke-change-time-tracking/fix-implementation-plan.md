---
status: completed
---

# densuke-change-time-tracking 改修実装手順書

## 実装タスク

### タスク1: DensukeScraper に title 属性パース機能を追加
- [x] 完了
- **対応Issue:** #544
- **概要:** 伝助ページヘッダの `<a title="M/d HH:mm">` を parse し、`DensukeData.memberLastChangeTimes` として保持する。挙動変更なし、純粋に情報を追加するだけ。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeScraper.java` — `DensukeData` に `Map<String, LocalDateTime> memberLastChangeTimes` フィールド追加、`parse()` のヘッダーループで title 取得、static helper `parseDensukeTitleAsDateTime(String title, int year)` を追加
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/DensukeScraperTest.java` — title パースの単体テストを追加（正常、空文字、parse失敗、複数メンバー）
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/DensukeScraperLiveSnapshotTest.java` — 現在のスナップショットで `memberLastChangeTimes` が期待通り返ることを検証
- **依存タスク:** なし
- **完了条件:**
  - `DensukeData.getMemberLastChangeTimes()` がメンバー名→LocalDateTime の map を返す
  - title が空文字または parse 不可なメンバーは map に含まれない
  - `./gradlew test --tests "*DensukeScraper*"` が全 PASS

### タスク2: Phase1/Phase3 状態遷移ログを drift 情報付きに拡張 + 10分超WARN
- [ ] 完了
- **対応Issue:** #545
- **概要:** `DensukeImportService` の Phase1/Phase3 で状態遷移を INFO ログ出力している各箇所に、`densukeTitleTime` / `detectedAt` / `driftMinutes` を追記する。10 分を超える drift は独立した WARN ログも出す。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeImportService.java` —
    - 定数 `DRIFT_WARN_THRESHOLD_MINUTES = 10` を追加
    - `processPhase1` / `processPhase3` の signature に `Map<String, LocalDateTime> memberLastChangeTimes` 追加
    - `processPhase3Maru` / `processPhase3Sankaku` / `processPhase3Batsu` 等の下位メソッドに同 map + `playerIdMap` を伝搬
    - **package-private** helper `formatDriftLog(...)`, `warnIfDrifted(...)` を追加（単体テスト可能にするため）
    - 既存の INFO ログ（`Phase1: removed`, `Phase1: reactivated`, `Phase3-A6`, `Phase3-A8`, `Phase3-B2`, `Phase3-C2`, `Phase3-C4`, `Phase3-C6`）を `formatDriftLog` 呼び出しに変更
    - Phase3 系の下位ログ（`Phase3: registered/reactivated player ... as WON/WAITLISTED`）も同様に drift 情報付与
    - 各状態遷移時に `warnIfDrifted` で 10分超の drift を WARN（Phase1 含む全状態遷移で一貫）
    - `detectedAt` は `processPhase1` / `processPhase3` の入口で **1回** `JstDateTimeUtil.now()` を取得し引数渡し
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/DensukeImportServiceTest.java` ほか関連テスト — ログ assert がある場合は新フォーマットに合わせて更新
  - `formatDriftLog` / `warnIfDrifted` の単体テストを追加（正常系、title未取得、drift>10分WARN発火、drift<=10分WARN抑制）
- **依存タスク:** タスク1 (#544)
- **完了条件:**
  - Phase3系の全 INFO ログが `densukeTitle=... detectedAt=... drift=Nm` 形式を含む
  - `driftMinutes > 10` のケースで `WARN Densuke change-time drift detected:` ログが出る
  - title が取れないメンバーは `densukeTitle=(unknown)` と表示され WARN 抑制
  - `./gradlew test --tests "*DensukeImportService*"` が全 PASS

### 設計決定事項（タスク2）

**Q1 → A**: Phase3 下位ログ（`registerNewParticipant` / `createWaitlisted` / `reactivateAsNewParticipant` / `reactivateAsWaitlisted` 内の `Phase3: registered/reactivated` 系 INFO ログ）も上位ログと同様に drift 情報を付与し、WARN 対象とする（一貫性優先）

**Q2 → A**: `formatDriftLog` / `warnIfDrifted` は **package-private** にして `DensukeImportServiceTest` から直接呼べるようにする（単体テスト容易性）

## 実装順序
1. **タスク1**: DensukeScraper で title を parse して map を持たせる（依存なし）
2. **タスク2**: タスク1 の map を受け取り、ログを拡張＋drift WARN を追加（タスク1に依存）

## テスト戦略
- **タスク1**: 単体テスト `DensukeScraperTest` に新 test メソッドを追加（title="4/22 22:06" の parse、title="" の無視、title="xxx" の parse失敗スキップ、live snapshot での実データ検証）
- **タスク2**: 既存の `DensukeImportServiceTest` / `DensukeImportServicePhaseCoverageTest` で `memberLastChangeTimes` を渡すテストケースを最小限追加。ログ文字列の assertion がある場合のみ更新。

## デプロイ
- 本番DB適用: 不要
- Render: 通常の push → auto-deploy
- デプロイ後、次回 Densuke sync (5分以内) で新ログが確認できるはず
