---
status: completed
---

# 抽選機能 改修実装手順書

## 実装タスク

### タスク1: ユーザーID伝播基盤の整備
- [x] 完了
- **概要:** フロントエンドから `X-User-Id` ヘッダーを送信し、バックエンドのインターセプターで検証・リクエスト属性にセットする。全 Controller のハードコード `currentUserId = 1L` をリクエスト属性からの取得に置換する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/api/client.js` — `X-User-Id` ヘッダーを `currentPlayer.id` から送信
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/interceptor/RoleCheckInterceptor.java` — `X-User-Id` ヘッダー抽出・検証・`request.setAttribute("currentUserId", id)` セット
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java` — `currentUserId` をリクエスト属性から取得
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/SystemSettingController.java` — 同上
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/PracticeSessionController.java` — 同上
- **依存タスク:** なし
- **対応Issue:** #7

### タスク2: lotteryId null バグ修正
- [x] 完了
- **概要:** `executeLottery` / `reExecuteLottery` で `LotteryExecution` を processSession 呼び出し前に save し、有効な lotteryId を確保する。処理完了後に details を update して再 save する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryService.java` — `executeLottery`: save を processSession ループ前に移動。`reExecuteLottery`: save を processMatch ループ前に移動。両メソッドで処理後に details 更新して再 save
- **依存タスク:** なし
- **対応Issue:** #8

### タスク3: スケジューラの締め切り判定修正
- [x] 完了
- **概要:** `LotteryScheduler.checkAndExecuteLottery` の月末日ハードコード判定を、`LotteryDeadlineHelper.isAfterDeadline` による動的判定に変更する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/LotteryScheduler.java` — `checkAndExecuteLottery` の判定ロジック変更: 月末日チェック廃止 → `lotteryDeadlineHelper.isAfterDeadline(nextYear, nextMonth)` で翌月分の締め切り超過を判定
- **依存タスク:** なし
- **対応Issue:** #9

### タスク4: findMonthlyLoserPlayerIds クエリ修正
- [x] 完了
- **概要:** セッションIDの大小比較をセッション日付の比較に変更し、セッション作成順序に依存しない正確な先行セッション判定にする。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/PracticeParticipantRepository.java` — `findMonthlyLoserPlayerIds` の JPQL: `ps.id < :currentSessionId` → `ps.sessionDate < (SELECT ps2.sessionDate FROM PracticeSession ps2 WHERE ps2.id = :currentSessionId)` に変更
- **依存タスク:** なし
- **対応Issue:** #10

### タスク5: details JSON 構築の ObjectMapper 化
- [x] 完了
- **概要:** `LotteryService` の `processSession` / `processMatch` で `StringBuilder` による手動 JSON 構築を、Jackson `ObjectMapper` による安全なシリアライズに置換する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryService.java` — `ObjectMapper` を注入。details 用の内部レコード/クラスを定義。`processSession` / `processMatch` の戻り値を文字列からオブジェクトに変更し、最終的に `objectMapper.writeValueAsString()` で JSON 化
- **依存タスク:** なし
- **対応Issue:** #11

### タスク6: buildLotteryResult の Service 層移動 + venueName 設定
- [x] 完了
- **概要:** `LotteryController.buildLotteryResult` を `LotteryService` に移動し、Controller からリポジトリ直接注入を削除する。同時に `VenueRepository` を使って `venueName` を設定する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryService.java` — `buildLotteryResult` メソッドを追加。`PlayerRepository`, `PracticeParticipantRepository`, `VenueRepository` を注入。会場名を `VenueRepository` から取得して `LotteryResultDto.venueName` にセット
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java` — `buildLotteryResult` 削除。`PlayerRepository`, `PracticeParticipantRepository`, `PracticeSessionRepository` の直接注入を削除。結果取得系エンドポイントを `lotteryService` 呼び出しに変更
- **依存タスク:** なし
- **対応Issue:** #12

### タスク7: 抽選結果通知の管理者明示送信化
- [x] 完了
- **概要:** 抽選実行時の自動通知を削除し、管理者が明示的に送信する統合エンドポイントを新設する。アプリ内通知と LINE 通知を一括処理する。既存の LINE 専用送信エンドポイントを廃止する。フロントエンドに通知送信ボタンを追加する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryService.java` — `executeLottery` から `notificationService.createLotteryResultNotifications()` 呼び出しを削除
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java` — `GET /notify-status` エンドポイント新設（ADMIN+）: Notification テーブルで送信済みチェック。`POST /notify-results` エンドポイント新設（ADMIN+）: `notificationService.createLotteryResultNotifications()` + `lineNotificationService.sendLotteryResults()` を呼び出し、結果を返却
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LineAdminController.java` — `POST /send/lottery-result` エンドポイントを削除
  - `karuta-tracker-ui/src/api/lottery.js` — `notifyStatus(year, month)`, `notifyResults(year, month)` 追加
  - `karuta-tracker-ui/src/api/line.js` — `sendLotteryResult` 削除
  - `karuta-tracker-ui/src/pages/practice/PracticeList.jsx` — モーダルに「通知送信」ボタン追加。notify-status で送信済みチェック → 2回目以降は確認ダイアログ。既存の LINE 送信ボタンがあれば削除/統合
- **依存タスク:** タスク6（buildLotteryResult 移動後に Controller を触るため、競合回避）
- **対応Issue:** #13

### タスク8: 認可チェック追加 + playerId 廃止
- [x] 完了
- **概要:** `/cancel`, `/respond-offer` に認可チェック（自分のレコードのみ操作可能、ADMIN+ は他人分も可）を追加する。`/my-results`, `/waitlist-status` から `playerId` パラメータを廃止し、ログインユーザーの ID を使用する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java` — `cancelParticipation`: currentUserId と participant.playerId の一致検証（ADMIN+ は免除）。`respondToOffer`: 同上。`getMyLotteryResults`: playerId パラメータ削除 → currentUserId 使用。`getWaitlistStatus`: 同上
  - `karuta-tracker-ui/src/api/lottery.js` — `getMyResults(year, month)` から playerId 引数削除。`getWaitlistStatus()` から playerId 引数削除
  - `karuta-tracker-ui/src/pages/lottery/LotteryResults.jsx` — API 呼び出しから playerId 削除
  - `karuta-tracker-ui/src/pages/lottery/WaitlistStatus.jsx` — API 呼び出しから playerId 削除
- **依存タスク:** タスク1（ユーザーID伝播基盤が必須）
- **対応Issue:** #14

### タスク9: 仕様書への未記載仕様の反映
- [x] 完了
- **概要:** `lottery_normal_reserve_percent`（一般枠最低保証、デフォルト30%）とキャンセル待ち順番の前試合引き継ぎロジックを仕様書に追記する。
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` — 3.7.2 抽選アルゴリズムの特徴に追記
  - `docs/requirements/lottery-system.md` — 4.2.3 抽選アルゴリズムに追記
- **依存タスク:** なし
- **対応Issue:** #15

---

## 実装順序

依存関係とリスクを考慮した推奨順序:

1. **タスク1: ユーザーID伝播基盤**（依存なし、タスク8 の前提）
2. **タスク2: lotteryId null 修正**（依存なし、重大バグ）
3. **タスク3: スケジューラ修正**（依存なし）
4. **タスク4: クエリ修正**（依存なし）
5. **タスク5: ObjectMapper 化**（依存なし）
6. **タスク6: buildLotteryResult 移動 + venueName**（依存なし、タスク7 の前に実施推奨）
7. **タスク7: 通知の管理者明示送信化**（タスク6 に依存）
8. **タスク8: 認可チェック + playerId 廃止**（タスク1 に依存）
9. **タスク9: 仕様書更新**（依存なし、最後に実施）
