---
status: completed
---
# LINE操作確認ダイアログ 実装手順書

## 実装タスク

### タスク1: DBテーブル・エンティティ・リポジトリの作成
- [x] 完了
- **概要:** `line_confirmation_token` テーブルを新設し、対応するJPAエンティティとリポジトリを作成する
- **変更対象ファイル:**
  - `database/create_line_confirmation_token.sql` — 新規作成。CREATE TABLEのマイグレーションSQL
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/LineConfirmationToken.java` — 新規作成。エンティティクラス（id, token, action, params, playerId, createdAt, expiresAt, usedAt）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/LineConfirmationTokenRepository.java` — 新規作成。findByToken()、deleteByExpiresAtBefore() を定義
- **依存タスク:** なし
- **対応Issue:** #244

### タスク2: LineConfirmationServiceの作成
- [x] 完了
- **概要:** トークンの発行・検証・消費・クリーンアップを担当するサービスクラスを作成する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineConfirmationService.java` — 新規作成
    - `createToken(action, params, playerId)`: 期限切れトークン全削除 → UUID発行 → DB保存 → トークン文字列を返す
    - `consumeToken(token, playerId)`: トークン検証（存在・未使用・期限内・本人）→ used_atを更新 → 元のaction・paramsを返す。検証NGの場合は例外をスロー
- **依存タスク:** タスク1
- **対応Issue:** #245

### タスク3: LineMessagingServiceにFlex reply送信メソッドを追加
- [x] 完了
- **概要:** 現在テキストのみ対応している `sendReplyMessage()` に加え、Flex Messageをreplyで送信する `sendReplyFlexMessage()` を追加する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineMessagingService.java` — `sendReplyFlexMessage(channelAccessToken, replyToken, altText, flexContents)` を追加。既存の `sendReplyMessage()`（78-97行目）のパターンを踏襲し、messagesのtypeを `flex` にする
- **依存タスク:** なし
- **対応Issue:** #246

### タスク4: LineNotificationServiceに確認用Flex構築メソッドを追加
- [x] 完了
- **概要:** 確認ダイアログ用のFlex Messageを構築するメソッドを追加する。DBからセッション情報（日付・会場名）を取得して表示文言を生成する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java` — 以下のメソッドを追加:
    - `buildConfirmationFlex(action, sessionDate, venueName, matchNumber, token)`: 確認用Flex（Bubble）を構築。ヘッダー + 確認文言 + 「確定」「キャンセル」ボタン。操作種別に応じて文言を切り替え。既存の `buildWaitlistOfferFlex()`（150-230行目）のFlexパターンを踏襲
- **依存タスク:** なし
- **対応Issue:** #247

### タスク5: LineWebhookControllerのpostback処理を変更
- [ ] 完了
- **概要:** `handlePostback()` を変更し、元アクション受信時は確認Flex返却、`confirm_*` 受信時は本来の処理実行、`cancel_confirm` 受信時はキャンセル応答を行うようにする
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LineWebhookController.java` — `handlePostback()`（131-221行目）を以下のように変更:
    1. アクション判定の分岐を追加:
       - `waitlist_accept`, `waitlist_decline`, `waitlist_decline_session`, `same_day_join` → 確認フローへ（新規メソッド `handleConfirmationRequest()` を追加）
       - `confirm_waitlist_accept`, `confirm_waitlist_decline`, `confirm_waitlist_decline_session`, `confirm_same_day_join` → トークン検証後に既存処理を実行
       - `cancel_confirm` → 「操作をキャンセルしました」をreply
    2. `handleConfirmationRequest()` の処理:
       - postbackデータのIDからDB参照でセッション情報（日付・会場名・試合番号）を取得
       - `LineConfirmationService.createToken()` でトークン発行
       - `LineNotificationService.buildConfirmationFlex()` で確認Flex構築
       - `LineMessagingService.sendReplyFlexMessage()` で返信
    3. `handleConfirmAction()` の処理:
       - `LineConfirmationService.consumeToken()` でトークン検証・消費
       - 検証OK → 元のaction・paramsを復元して既存の処理ロジックを呼び出し
       - 検証NG → 「この確認は期限切れです。もう一度操作してください。」をreply
    4. 既存の `handleSameDayJoin()`, `handleWaitlistDeclineSession()`, waitlist_accept/decline処理は内部メソッドとして残し、確認フロー経由で呼ばれるようにする
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LineWebhookController.java` — `sendReply()` ヘルパーに加え、Flex返信用の `sendReplyFlex()` ヘルパーを追加
- **依存タスク:** タスク1, タスク2, タスク3, タスク4
- **対応Issue:** #248

### タスク6: テスト
- [ ] 完了
- **概要:** 新規・変更クラスのユニットテストを作成する
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/LineConfirmationServiceTest.java` — 新規作成。トークン発行・検証成功・期限切れ・使用済み・本人不一致・クリーンアップのテスト
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/controller/LineWebhookControllerTest.java` — 既存テストがあれば追加、なければ新規作成。確認フローの統合テスト（元アクション→確認Flex返却、confirm→処理実行、cancel→キャンセル応答、期限切れトークン→エラー応答）
- **依存タスク:** タスク5
- **対応Issue:** #249

## 実装順序
1. タスク1（DBテーブル・エンティティ・リポジトリ）— 依存なし
2. タスク3（LineMessagingServiceにFlex reply追加）— 依存なし
3. タスク4（LineNotificationServiceに確認Flex構築追加）— 依存なし
4. タスク2（LineConfirmationService）— タスク1に依存
5. タスク5（LineWebhookController変更）— タスク1〜4すべてに依存
6. タスク6（テスト）— タスク5に依存

※ タスク1, 3, 4は並行実施可能
