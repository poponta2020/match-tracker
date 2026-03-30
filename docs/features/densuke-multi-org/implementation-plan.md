---
status: completed
---
# 伝助マルチ団体対応 実装手順書

## 実装タスク

### タスク1: DBマイグレーション — densuke_urls に organization_id 追加
- [x] 完了
- **概要:** `densuke_urls` テーブルに `organization_id` カラムを追加し、ユニーク制約を `(year, month, organization_id)` に変更する。既存データはわすらもち会にマイグレーション。
- **変更対象ファイル:**
  - `database/add_organization_id_to_densuke_urls.sql` — 新規作成。ALTER TABLE文
- **依存タスク:** なし
- **対応Issue:** #129

### タスク2: バックエンド エンティティ・リポジトリ変更
- [x] 完了
- **概要:** `DensukeUrl` エンティティに `organizationId` フィールドを追加し、リポジトリのクエリメソッドを変更する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/DensukeUrl.java` — `organizationId` フィールド追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/DensukeUrlRepository.java` — `findByYearAndMonth` → `findByYearAndMonthAndOrganizationId` に変更、`findByYearAndMonth`（List返却版）追加（スケジューラー用）
- **依存タスク:** タスク1
- **対応Issue:** #130

### タスク3: バックエンド サービス変更 — PracticeSessionService
- [x] 完了
- **概要:** 伝助URL CRUD メソッドに `organizationId` パラメータを追加し、ADMIN権限チェックを追加する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeSessionService.java` — `getDensukeUrl()`, `saveDensukeUrl()` に `organizationId` パラメータ追加、ADMIN権限チェック
- **依存タスク:** タスク2
- **対応Issue:** #131

### タスク4: バックエンド サービス変更 — DensukeImportService
- [x] 完了
- **概要:** インポート処理を団体別に実行するよう変更。セッション作成時に `organizationId` を設定。未登録者通知を団体別ADMINに限定。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeImportService.java` — `importFromDensuke()` に `organizationId` パラメータ追加、セッション作成時に `organizationId` 設定、`notifyAdminsOfUnmatchedNames()` に `organizationId` パラメータ追加しADMINフィルタ
- **依存タスク:** タスク2
- **対応Issue:** #132

### タスク5: バックエンド サービス変更 — DensukeWriteService
- [x] 完了
- **概要:** 書き込み処理を団体別に実行するよう変更。ステータス取得も団体別に対応。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeWriteService.java` — `writeToDensuke()` で全団体のURLを取得してループ処理、`getStatus()` に `organizationId` パラメータ追加
- **依存タスク:** タスク2
- **対応Issue:** #133

### タスク6: バックエンド スケジューラー変更
- [x] 完了
- **概要:** 自動同期スケジューラーを全団体の伝助URLをループ処理するよう変更。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/DensukeSyncScheduler.java` — `syncForMonth()` を全団体分ループ実行に変更
- **依存タスク:** タスク4, タスク5
- **対応Issue:** #134

### タスク7: バックエンド コントローラー変更 — 伝助API
- [x] 完了
- **概要:** 伝助関連APIエンドポイントに `organizationId` パラメータを追加し、ADMIN権限チェックを実装。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/PracticeSessionController.java` — `getDensukeUrl`, `saveDensukeUrl`, `syncDensuke`, `getDensukeWriteStatus`, `importFromDensuke`, `registerAndSyncDensuke` に `organizationId` パラメータ追加、ADMIN権限チェック
- **依存タスク:** タスク3, タスク4, タスク5
- **対応Issue:** #135

### タスク8: バックエンド 抽選処理の団体フィルタ修正
- [x] 完了
- **概要:** 抽選実行時に団体別にセッションを取得するよう修正。抽選結果通知も団体別にフィルタ。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryService.java` — `executeLottery()` 内のセッション取得を団体別にフィルタ
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java` — `notifyResults()`, `getNotifyStatus()` に `organizationId` パラメータ追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/PracticeSessionRepository.java` — `findByYearAndMonthAndOrganizationId()` 追加
- **依存タスク:** タスク2
- **対応Issue:** #136

### タスク9: バックエンド 対戦・結果の団体フィルタ追加
- [x] 完了
- **概要:** 対戦組み合わせ・結果取得APIにユーザーの所属団体フィルタを追加。同じ日に両団体の練習がある場合に、所属団体のセッションのみ返すようにする。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/MatchPairingController.java` — `getByDate()`, `getByDateAndMatchNumber()` にユーザー所属団体フィルタ追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/MatchController.java` — `getMatchesByDate()` にユーザー所属団体フィルタ追加
- **依存タスク:** なし
- **対応Issue:** #137

### タスク10: フロントエンド APIクライアント変更
- [ ] 完了
- **概要:** 伝助関連API呼び出しに `organizationId` パラメータを追加。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/api/practices.js` — `getDensukeUrl`, `saveDensukeUrl`, `syncDensuke`, `registerAndSyncDensuke`, `getDensukeWriteStatus` に `organizationId` パラメータ追加
- **依存タスク:** タスク7
- **対応Issue:** #138

### タスク11: フロントエンド 伝助管理画面の団体対応
- [ ] 完了
- **概要:** 伝助管理画面を団体別にURL入力・同期・ステータス表示するUIに変更。ADMINは自団体のみ、SUPER_ADMINは全団体表示。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/densuke/DensukeManagement.jsx` — 管理可能団体リストを取得、団体ごとにURL入力欄・同期ボタン・ステータスブロックを並べて表示、各API呼び出しに `organizationId` 付与
- **依存タスク:** タスク10
- **対応Issue:** #139

### タスク12: フロントエンド 対戦・結果画面の団体フィルタ対応
- [ ] 完了
- **概要:** バックエンド側で団体フィルタが適用されるため、フロントエンド側は主にAPIレスポンスの変更に対応。同日に複数団体のセッションがある場合の表示を確認・調整。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx` — 必要に応じてUI調整
  - `karuta-tracker-ui/src/pages/matches/BulkResultInput.jsx` — 必要に応じてUI調整
  - `karuta-tracker-ui/src/pages/matches/MatchResultsView.jsx` — 必要に応じてUI調整
- **依存タスク:** タスク9
- **対応Issue:** #140

## 実装順序
1. タスク1: DBマイグレーション（依存なし）
2. タスク2: エンティティ・リポジトリ変更（タスク1に依存）
3. タスク3, 4, 5, 8, 9: バックエンド サービス・コントローラー変更（タスク2に依存、並行実施可能）
4. タスク6: スケジューラー変更（タスク4, 5に依存）
5. タスク7: 伝助APIコントローラー変更（タスク3, 4, 5に依存）
6. タスク10: フロントエンド APIクライアント（タスク7に依存）
7. タスク11: 伝助管理画面UI（タスク10に依存）
8. タスク12: 対戦・結果画面調整（タスク9に依存）
