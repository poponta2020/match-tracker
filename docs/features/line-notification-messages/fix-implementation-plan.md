---
status: completed
---
# LINEの通知メッセージの仕様 改修実装手順書

## 実装タスク

### タスク1: SPECIFICATION.md の仕様書整備（項目1,2,5,7）
- [x] 完了
- **概要:** 仕様書の不整合・未記載を一括修正する
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md`
    - 7.15節: `POST /send/lottery-result` を削除し、`POST /api/lottery/notify-results` への統合を注記
    - 4.3節: `sendOfferResponseConfirmation`（繰り上げ応答確認通知）の仕様を追記
    - 4.3節: 締め切りリマインダーが未実装であることを明記
    - 4.3節: チャネルアクセストークンが現状平文保存であることを明記
- **依存タスク:** なし
- **対応Issue:** #56

### タスク2: sendToPlayer / sendFlexToPlayer の共通処理抽出（項目3）
- [x] 完了
- **概要:** 両メソッドで重複している前処理（LINKED状態チェック・通知設定チェック・チャネル取得・月間上限チェック）を共通メソッドに抽出する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java`
    - 共通前処理を返すprivateメソッド（例: `resolveChannel`）を新設。チャネル情報またはSendResultを返す
    - `sendToPlayer`（行447-491）から共通部分を抽出し共通メソッドを呼ぶ形に変更
    - `sendFlexToPlayer`（行397-442）から共通部分を抽出し共通メソッドを呼ぶ形に変更
- **依存タスク:** なし
- **対応Issue:** #57

### タスク3: LINE送信結果を人数ベースに変更（項目4）
- [x] 完了
- **概要:** 抽選結果LINE送信のカウントをメッセージ通数からプレイヤー人数に変更し、UIの表示も更新する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/LineSendResultResponse.java`
    - `sentCount` → `sentPlayerCount` にリネーム（`failedCount` → `failedPlayerCount`、`skippedCount` → `skippedPlayerCount`）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java`
    - `sendLotteryResults`（行188-257）のカウントロジックをプレイヤー単位に変更: プレイヤーごとに送信結果（SUCCESS/FAILED/SKIPPED）を1回だけカウント。1通でも成功していればSUCCESS扱い
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java`
    - 行412-414: `lineResult.getSentCount()` → `lineResult.getSentPlayerCount()` に変更（キー名 `lineSent` は維持）
    - `notifyStatus` のレスポンスで返す `sentCount` も人数ベースに変更（line_message_logから distinct player_id でカウント）
  - `karuta-tracker-ui/src/pages/practice/PracticeList.jsx`
    - 行354: `${statusRes.data.sentCount}件` → `${statusRes.data.sentCount}人`
    - 行359: `LINE送信: ${result.data.lineSent}件` → `LINE送信: ${result.data.lineSent}人`
- **依存タスク:** タスク2（共通処理抽出後のコードに対して変更するため）
- **対応Issue:** #58

### タスク4: 参加予定リマインダーの日数ラベル改善（項目6）
- [ ] 完了
- **概要:** リマインダーメッセージの日数ラベルを自然な日本語に改善する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/LineReminderScheduler.java`
    - 行65: `days == 1 ? "明日" : days + "日後"` → `days == 1 ? "明日" : days == 2 ? "明後日" : ""`
    - 行66: メッセージフォーマットを調整。ラベルが空文字の場合は日付のみでメッセージを構築
- **依存タスク:** なし
- **対応Issue:** #59

## 実装順序
1. タスク1（仕様書整備 — 依存なし、他タスクと並行可能）
2. タスク2（共通処理抽出 — 依存なし）
3. タスク3（人数ベースカウント — タスク2に依存）
4. タスク4（日数ラベル改善 — 依存なし、タスク1と並行可能）
