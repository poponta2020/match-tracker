---
status: completed
---

# 伝助スケジュール書き込み同期 改修実装手順書

## 実装タスク

### タスク1: LineNotificationService に伝助スケジュール同期失敗通知メソッドを追加

- [x] 完了
- **概要:** スケジュール push 失敗時に管理者へ通知するためのメソッドを既存通知サービスに追加する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java`
    - 新メソッド `sendDensukeScheduleSyncFailedNotification(Long organizationId, String errorMessage)` を追加
    - 既存の `sendDensukePageCreatedNotification` のパターン踏襲（通知タイプは新規追加 or 既存流用）
  - 必要なら `Notification.NotificationType` に新規 enum 値追加 (例: `DENSUKE_SCHEDULE_SYNC_FAILED`)
  - `LineNotificationPreference` への通知タイプ ON/OFF 制御カラム追加（任意）
- **依存タスク:** なし
- **対応Issue:** #790
- **注意:**
  - 通知タイプを enum に追加する場合は DB マイグレーション要否を確認（既存実装パターンを踏襲）
  - 既存通知設定 UI への影響は最小化（追加カラムだけならフロント側で表示しなくても動作する）

---

### タスク2: DensukeScheduleWriteService 新規作成（コアサービス）

- [ ] 完了
- **概要:** アプリ→伝助へのスケジュール push を担う新サービスを作成。差分検出 → POST /update → 失敗時通知のフローを実装。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeScheduleWriteService.java` (新規)
    - **メインメソッド:** `pushNewSchedulesToDensuke(int year, int month, Long organizationId)`
      - 引数の `(year, month, organizationId)` で `densuke_urls` を `SELECT FOR UPDATE` で取得（競合制御）
      - 該当 URL がなければ early return（伝助ページ未作成のケース）
      - `(year, month, organizationId)` の `practice_sessions` を全件取得
      - `DensukeScraper.scrape(url, year)` で伝助の現スケジュールを取得
      - 差分計算: アプリにあって伝助にない日付集合を算出
      - 差分なしなら early return
      - 差分セッションを `DensukePageCreateService.buildScheduleText()` に渡してテキスト生成
      - GET `/list?cd=...` で `pageId` と Cookie を取得（既存 `DensukeWriteService.extractPageId()` を流用 or 共通化）
      - POST `https://densuke.biz/update` (cd, id=pageId, postfix=空, schedule=差分テキスト)
      - レスポンス 302 確認 → 成功
      - 302 以外 / IOException → `LineNotificationService.sendDensukeScheduleSyncFailedNotification()` 呼び出し + WARN ログ
    - **非同期エントリポイント:** `@Async public void pushNewSchedulesToDensukeAsync(int year, int month, Long organizationId)` で非同期実行可能に
    - 公開メソッドのトランザクション: `@Transactional` 必須（SELECT FOR UPDATE のため）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeWriteService.java`
    - `extractPageId(Document)` を package-private 維持（他サービスから利用可能に） — 既に package-private なら変更不要
    - `extractCd(String)` / `extractBase(String)` も同様（既に static package-private）
- **依存タスク:** タスク1 (#790)
- **対応Issue:** #791
- **注意:**
  - `DensukePageCreateService` から `buildScheduleText()` を呼ぶため、循環依存に注意。`DensukePageCreateService` から `DensukeScheduleWriteService` への参照は持たせない（一方向）
  - `buildScheduleText` は会場・試合時刻が必須なので、不整合データがある場合は `IllegalStateException` をキャッチして failure 通知（既存セッション作成は止めない）
  - `SELECT FOR UPDATE` は `DensukeUrlRepository` にカスタムクエリ追加が必要
  - Cookie 取得は `/list` 経由（既存 `DensukeWriteService` と同じパターン）

---

### タスク3: PracticeSessionService.createSession に afterCommit + @Async フック追加

- [ ] 完了
- **概要:** 練習日新規作成成功時に、afterCommit で伝助 push を非同期発火する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeSessionService.java` (L390-L453 `createSession`)
    - `practiceSessionRepository.save(session)` の後（L428 以降）に `TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { afterCommit() { ... } })` を追加
    - `afterCommit` 内で `densukeScheduleWriteService.pushNewSchedulesToDensukeAsync(year, month, organizationId)` を呼ぶ
    - `try/catch` で `@Async` ディスパッチ失敗を握りつぶし（既存 `DensukePageCreateService.java:157-167` パターン踏襲）
  - `DensukeScheduleWriteService` を `PracticeSessionService` の DI フィールドに追加
- **依存タスク:** タスク2 (#791)
- **対応Issue:** #792
- **注意:**
  - **`DensukeImportService.findOrCreateSession()` 経由のセッション作成では push しないこと** — 伝助から取り込んだセッションを伝助に書き戻すと無限ループの危険あり。`PracticeSessionService.createSession` 経由のみフック。
  - フックは createSession の `@Transactional` ブロック内で登録するが、`afterCommit` は実際にはコミット成功後に走る
  - ロールバック時は push されない（TransactionSynchronization の標準動作）
  - 既存 createSession の戻り値・例外挙動は変更しない

---

### タスク4: DensukeSyncService にフォロー同期ステップを追加

- [ ] 完了
- **概要:** 5分スケジューラ実行時に「アプリと伝助のスケジュール差分」もチェックして自動補完する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeSyncService.java`
    - `syncAll()` の冒頭（L46 `writeToDensuke()` の前 or 後）に `densukeScheduleWriteService.pushAllForCurrentAndNextMonth()` 呼び出しを追加
    - 新規メソッド `DensukeScheduleWriteService.pushAllForCurrentAndNextMonth()` を追加：当月・翌月の全 `densuke_urls` に対して `pushNewSchedulesToDensuke()` を順次実行（既に存在するスケジュールは差分検出でスキップされるので冪等）
    - エラーは WARN ログのみ（管理者通知はスケジューラ経路では発火しない、フラッディング防止）
- **依存タスク:** タスク2 (#791)
- **対応Issue:** #793
- **注意:**
  - 既存の `writeToDensuke()` (参加者書き込み) と `importFromDensuke()` (取り込み) との実行順は要検討
    - 推奨順序: `pushSchedules()` → `writeToDensuke()` (参加者) → `importFromDensuke()` (取り込み)
    - 理由: スケジュールがそろってから参加者を書き、最後に伝助の最新状態を取り込む
  - 1 URL 失敗しても次の URL へ進む（フォルトトレラント）

---

### タスク5: 単体テスト追加

- [ ] 完了
- **概要:** タスク1-4 で追加したロジックに対する単体テストを追加する。
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/DensukeScheduleWriteServiceTest.java` (新規)
    - **追加するテストケース:**
      1. `testPushSucceedsWithNewSchedules` - 伝助にない日付があれば POST /update が呼ばれて成功
      2. `testPushSkipsWhenNoDiff` - アプリと伝助のスケジュールが一致なら POST しない
      3. `testPushSkipsWhenNoDensukeUrl` - 対象年月の densuke_url が無ければ early return
      4. `testPushNotifiesAdminOnHttpFailure` - HTTP 302 以外で `LineNotificationService` の失敗通知メソッドが呼ばれる
      5. `testPushNotifiesAdminOnIOException` - IOException 時も失敗通知が呼ばれる
      6. `testPushSkipsAndNotifiesOnVenueMissing` - 会場未設定セッションがあれば失敗通知＋スキップ
      7. `testPushHandlesConcurrentExecutionViaDbLock` - DB行ロックの動作確認（インテグレーションテスト風）
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/PracticeSessionServiceTest.java`
    - 追加テスト: `testCreateSessionTriggersAsyncDensukePushAfterCommit`
    - 追加テスト: `testCreateSessionDoesNotTriggerPushOnRollback`
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/DensukeSyncServiceTest.java`
    - 追加テスト: `testSyncAllInvokesScheduleFollowUpSync`
- **依存タスク:** タスク2 (#791), タスク3 (#792), タスク4 (#793)
- **対応Issue:** #794
- **注意:**
  - HTTP POST のモックは Jsoup の `Connection` モック or 既存テストパターン踏襲
  - DB行ロックの実テストは H2 では限定的 — まずはユニットテストでロックメソッドが呼ばれることを確認、結合テストは別途
  - 既存 `DensukeImportServiceTest`, `DensukeWriteServiceTest` のパターン参照

---

### タスク6: ドキュメント更新

- [ ] 完了
- **概要:** `CLAUDE.md` のドキュメント更新ルールに従い、仕様書・設計書を更新する。
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` — 「アプリ→伝助 練習日同期」セクションを追記
  - `docs/DESIGN.md` — `DensukeScheduleWriteService` のアーキテクチャ・シーケンス図追記
  - `docs/伝助双方向同期.md` — 練習日 push の新方向を反映
- **依存タスク:** タスク2 (#791), タスク3 (#792), タスク4 (#793)
- **対応Issue:** #795
- **注意:**
  - 既存の伝助関連記述を破壊しないよう、追記中心に行う
  - SCREEN_LIST.md は画面変更がないため更新不要
  - スパイク調査結果は `docs/features/densuke-schedule-write-sync/spike-findings.md` に既に記録済み（このファイルもPRに含める）

---

### タスク7（オプション）: スパイク用テストイベントのクリーンアップ

- [ ] 完了 / N/A
- **概要:** スパイク調査時に作成したテストイベントを削除する。
- **対象:** `cd=Mswvm6w4XEYJAXse` (URL: https://densuke.biz/list?cd=Mswvm6w4XEYJAXse)
- **変更対象ファイル:** なし（運用作業）
- **依存タスク:** タスク5 (#794) (実装・テスト完了後)
- **対応Issue:** なし (オプション・運用作業のためIssue化省略)
- **注意:**
  - 削除 API は本改修の範囲外なので未調査
  - 削除コマンドが分かれば手動 curl で実行、分からなければ放置（運用上の問題なし）
  - 必要なら本タスクをスキップ可

---

## 実装順序

1. **タスク1**: LineNotificationService 通知メソッド追加（依存なし）
2. **タスク2**: DensukeScheduleWriteService コア実装（タスク1 依存）
3. **タスク3**: PracticeSessionService フック追加（タスク2 依存）
4. **タスク4**: DensukeSyncService フォロー同期追加（タスク2 依存）
5. **タスク5**: 単体テスト追加（タスク2, 3, 4 依存）
6. **タスク6**: ドキュメント更新（タスク2, 3, 4 依存）
7. **タスク7**: テストイベントクリーンアップ（オプション）

全タスクを同一PRに含める（CLAUDE.md「ドキュメントの更新は実装コードと同じコミットに含める」ルール準拠）。タスク7のみ別作業（運用）として切り出し可。

## DB マイグレーション

- **基本不要** — 既存テーブル `densuke_urls`, `practice_sessions`, `venues`, `venue_match_schedules` のみ利用
- **条件付き要:** タスク1 で `NotificationType` enum に新値を追加する場合、`notifications.type` 列の制約に対応した SQL マイグレーションが必要な可能性あり
  - 既存実装が enum → VARCHAR で保存している場合は不要
  - DBコンストレイントがある場合は `database/` 配下に SQL ファイル追加 + 本番適用
  - 確認方法: `LineNotificationPreference.java` および既存マイグレーション (`database/*.sql`) を確認

## レビュー観点

- `DensukeImportService` から呼ばれるセッション作成 (findOrCreateSession) では push されないこと（無限ループ防止）
- 同一年月で並行実行された場合のロック動作（SELECT FOR UPDATE）
- 差分検出ロジックの正確性（アプリと伝助の日付集合比較）
- HTTP 失敗時の管理者通知が確実に発火すること
- 通知のフラッディング防止（5分スケジューラ経路では通知しない）
- buildScheduleText の例外（会場未設定等）が握りつぶされず通知につながること
- afterCommit フックのロールバック時挙動（push されない）
- 既存テスト (`DensukeImportServiceTest`, `DensukeWriteServiceTest`, `PracticeSessionServiceTest`, `DensukeSyncServiceTest`, `DensukePageCreateServiceTest`) が全てパスすること

## 残課題（本改修スコープ外）

- スケジュール変更（既存日程の編集）— 別 typ で実装可能と思われる
- スケジュール削除（伝助からの日程削除）— `editdate` 経由で実装可能
- 伝助イベント自体の削除 API
- これらは将来の機能拡張として別途要件定義する
