---
status: completed
---

# 伝助との双方向同期機能 改修実装手順書（v2）

## 実装タスク

### タスク1: `DensukeSyncService` の新規作成

- [x] 完了
- **概要:** 同期フローを集約する `DensukeSyncService` クラスを新規作成する。`syncForOrganization()` / `syncAll()` / `triggerWriteAsync()` の3メソッドを持つ。
- **変更対象ファイル:**
  - `service/DensukeSyncService.java`（**新規作成**） — `syncForOrganization(year, month, orgId, userId)`: 指定団体の書き込み→読み取り。`syncAll()`: 当月+翌月の全団体処理。`triggerWriteAsync()`: `@Async` による即時書き込み
- **依存タスク:** なし
- **対応Issue:** #171

### タスク2: `AsyncConfig` の追加

- [x] 完了
- **概要:** `@EnableAsync` を有効化する設定クラスを追加する。既存の設定クラスに追加するか、新規 `AsyncConfig.java` を作成する。
- **変更対象ファイル:**
  - `config/AsyncConfig.java`（**新規作成** or 既存の設定クラスに追記） — `@EnableAsync` の設定
- **依存タスク:** なし
- **対応Issue:** #174

### タスク3: `DensukeSyncScheduler` のリファクタリング

- [x] 完了
- **概要:** スケジューラーのインターバルを60秒→5分に変更し、同期ロジックを `DensukeSyncService.syncAll()` への委譲に変更する。
- **変更対象ファイル:**
  - `scheduler/DensukeSyncScheduler.java` — `@Scheduled(fixedDelay = 300000)` に変更。`syncDensuke()` の中身を `DensukeSyncService.syncAll()` の呼び出しに置き換え
- **依存タスク:** タスク1
- **対応Issue:** #176

### タスク4: `PracticeSessionController` の改修

- [x] 完了
- **概要:** `importFromDensuke()` エンドポイントを削除し、`syncDensuke()` を `DensukeSyncService` への委譲に変更する。
- **変更対象ファイル:**
  - `controller/PracticeSessionController.java` — `importFromDensuke()` メソッド（L366-393）を削除。`syncDensuke()` メソッド内の書き込み+読み取りロジックを `DensukeSyncService.syncForOrganization()` の呼び出しに変更
- **依存タスク:** タスク1
- **対応Issue:** #178

### タスク5: `removeParticipantFromMatch()` の論理削除化

- [x] 完了
- **概要:** 物理削除を論理削除（`status=CANCELLED`, `dirty=true`）に変更する。
- **変更対象ファイル:**
  - `service/PracticeParticipantService.java` — `removeParticipantFromMatch()` で `deleteBySessionIdAndPlayerIdAndMatchNumber()` の代わりに、対象レコードを取得して `status=CANCELLED`, `dirty=true` に更新
  - `repository/PracticeParticipantRepository.java` — `findBySessionIdAndPlayerIdAndMatchNumber()` メソッドが未定義の場合は追加
- **依存タスク:** なし
- **対応Issue:** #180

### タスク6: `updateSession()` の差分更新化

- [x] 完了
- **概要:** deleteAll + re-create パターンを差分更新に変更し、既存参加者の dirty 値を保持する。
- **変更対象ファイル:**
  - `service/PracticeSessionService.java` — `updateSession()` 内の参加者処理を差分更新に書き換え。既存playerIdセットとリクエストplayerIdセットを比較し、新規追加（dirty=true）/削除（CANCELLED+dirty=true）/継続（変更なし）/totalMatches変更対応を実装
- **依存タスク:** タスク5（論理削除の方針を統一するため）
- **対応Issue:** #182

### タスク7: イベント駆動書き込みのトリガー追加

- [x] 完了
- **概要:** 参加者の状態が変更される各操作の末尾に `DensukeSyncService.triggerWriteAsync()` の呼び出しを追加する。
- **変更対象ファイル:**
  - `service/PracticeParticipantService.java` — `addParticipantToMatch()`, `setMatchParticipants()`, `registerSameDay()`, `registerBeforeDeadline()`, `removeParticipantFromMatch()` の末尾に追加
  - `service/PracticeSessionService.java` — `updateSession()` の末尾に追加
  - `service/WaitlistPromotionService.java` — `declineWaitlistBySession()`, `respondToOffer()` の末尾に追加
- **依存タスク:** タスク1, タスク5, タスク6
- **対応Issue:** #183

## 実装順序

1. **タスク1** — `DensukeSyncService` 新規作成（依存なし、後続タスクの基盤）
2. **タスク2** — `AsyncConfig` 追加（依存なし、タスク1と並行可能）
3. **タスク5** — `removeParticipantFromMatch()` 論理削除化（依存なし）
4. **タスク3** — スケジューラーのリファクタリング（タスク1に依存）
5. **タスク4** — Controllerの改修（タスク1に依存、タスク3と並行可能）
6. **タスク6** — `updateSession()` 差分更新化（タスク5に依存）
7. **タスク7** — イベント駆動トリガー追加（タスク1,5,6に依存、最後に実施）
