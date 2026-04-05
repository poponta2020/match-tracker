---
status: completed
---

# キャンセル待ちオファー通知の統合 改修実装手順書

## 実装タスク

### タスク1: WaitlistPromotionService — オファーLINE通知の分離

- [x] 完了
- **概要:** `promoteNextWaitlisted()` からLINEオファー通知の即時送信を分離し、呼び出し元でバッチ送信できるようにする
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/WaitlistPromotionService.java`
    - `promoteNextWaitlisted()` (L397): `lineNotificationService.sendWaitlistOfferNotification(next)` の呼び出しを削除。アプリ内通知 `createOfferNotification()` (L394) はそのまま残す
    - `AdminWaitlistNotificationData` の `promotedParticipant` フィールドは既にあるので、呼び出し元でオファー対象者を取得可能
- **依存タスク:** なし
- **対応Issue:** #310

### タスク2: LineNotificationService — 統合Flexメッセージ構築

- [x] 完了
- **概要:** 複数試合対応の統合オファーFlexメッセージ構築メソッドと、残りオファー通知メソッドを追加する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java`
    - 新メソッド `sendConsolidatedWaitlistOfferNotification(List<PracticeParticipant> offeredParticipants, PracticeSession session, String triggerAction, Player triggerPlayer)` — 統合Flexを構築して送信
    - 新メソッド `sendRemainingOfferNotification(List<PracticeParticipant> remainingOffered)` — 部分参加後の残りオファーFlexを構築して送信
    - 新メソッド `buildConsolidatedOfferFlex(...)` — 統合Flex構築（パターン1/2に対応）
    - 新メソッド `buildRemainingOfferFlex(...)` — 残りオファーFlex構築（パターン3/4に対応）
    - 配色: 個別参加 `#27AE60`(緑)、すべて参加 `#2E86C1`(青)、辞退 `#E74C3C`(赤)
    - 1試合のみの場合は「すべての試合に参加」ボタン非表示
    - 応答期限は `offeredParticipants` 内の最も遅い `offerDeadline` を表示
    - 応答期限12時間未満の緊急表示を維持
    - 既存の `sendWaitlistOfferNotification(PracticeParticipant)` は LINE連携後一斉送信 (L687) で使用されているため残す（単一試合版として内部で `sendConsolidatedWaitlistOfferNotification` を呼ぶようリファクタ可）
    - `buildConfirmMessage()` に `waitlist_accept_all` / `waitlist_decline_all` の文言追加
- **依存タスク:** なし
- **対応Issue:** #311

### タスク3: WaitlistPromotionService — 一括応答・部分参加後通知

- [x] 完了
- **概要:** 一括承諾/一括辞退メソッドと、部分参加後の残りオファー通知ロジックを追加する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/WaitlistPromotionService.java`
    - 新メソッド `respondToOfferAll(Long sessionId, Long playerId, boolean accept)` — 同一セッション内の全OFFEREDを一括承諾/辞退
      - 承諾時: 全OFFEREDをWONに変更
      - 辞退時: 全OFFEREDをDECLINEDに変更し、各試合で `promoteNextWaitlisted()` を発動。管理者通知をバッチ送信
    - `respondToOffer()` (L409-460) に追加ロジック:
      - 承諾後、同一セッション×同一プレイヤーの残りOFFEREDを検索 (`practiceParticipantRepository.findBySessionIdAndPlayerIdAndStatus(sessionId, playerId, OFFERED)`)
      - 残りがあれば `lineNotificationService.sendRemainingOfferNotification(remainingOffered)` を呼び出し
- **依存タスク:** タスク2
- **対応Issue:** #312

### タスク4: LineWebhookController — 新アクションのハンドリング

- [x] 完了
- **概要:** LINE postbackの新アクション `waitlist_accept_all` / `waitlist_decline_all` に対応する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LineWebhookController.java`
    - `CONFIRMABLE_ACTIONS` (L51-53) に `"waitlist_accept_all"`, `"waitlist_decline_all"` を追加
    - `handleConfirmationRequest()` (L210-294) に新アクションのセッション情報取得ロジック追加（`sessionId` + `playerId` パラメータから取得）
    - `executeOriginalAction()` (L333-344) に新アクションのcase追加 → 新メソッド `handleWaitlistResponseAll()` を呼び出し
    - 新メソッド `handleWaitlistResponseAll(LineChannel channel, String replyToken, String action, Map<String, String> params, Long playerId)` — `waitlistPromotionService.respondToOfferAll()` を呼び出し、結果メッセージを返信
- **依存タスク:** タスク3
- **対応Issue:** #313

### タスク5: LotteryController — 一括応答APIエンドポイント・オファー統合通知

- [x] 完了
- **概要:** Web版の一括応答APIエンドポイント追加と、キャンセル処理後のオファー統合通知送信を追加する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java`
    - 新エンドポイント `POST /api/lottery/respond-offer-all` — `OfferBatchResponseRequest`（sessionId, accept）を受け取り、`waitlistPromotionService.respondToOfferAll()` を呼び出し
    - キャンセル一括処理 (L254-278): `notificationDataList` から `promotedParticipant` をプレイヤーIDでグルーピングし、`lineNotificationService.sendConsolidatedWaitlistOfferNotification()` で統合通知送信
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/OfferBatchResponseRequest.java` (新規)
    - フィールド: `sessionId` (Long, required), `accept` (Boolean, required)
- **依存タスク:** タスク2, タスク3
- **対応Issue:** #314

### タスク6: DensukeImportService — オファー統合通知送信

- [x] 完了
- **概要:** 伝助同期後のバッチ通知に、プレイヤー向け統合オファー通知を追加する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeImportService.java`
    - `sendPendingNotifications()` (L760-772) に追加:
      - `pendingNotifications` から `promotedParticipant` が存在するものを抽出
      - セッションID × プレイヤーIDでグルーピング
      - グループごとに `lineNotificationService.sendConsolidatedWaitlistOfferNotification()` を呼び出し
      - トリガー情報（triggerAction, triggerPlayerId）は各グループの先頭から取得
- **依存タスク:** タスク1, タスク2
- **対応Issue:** #315

### タスク7: LotteryService — 管理者編集のオファー通知抑制

- [x] 完了
- **概要:** `adminEditParticipants()` 内の `promoteNextWaitlisted()` 呼び出しを通知抑制版に切り替え、統合通知を送信する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryService.java`
    - `adminEditParticipants()` (L822付近): `promoteNextWaitlisted()` の戻り値を蓄積し、処理完了後にオファー統合通知をまとめて送信
    - 管理者通知も同様にバッチ化（現在は個別送信の可能性あり）
- **依存タスク:** タスク1, タスク2
- **対応Issue:** #316

### タスク8: フロントエンド — OfferResponse画面の統合対応

- [x] 完了
- **概要:** Web版オファー応答画面を複数試合対応にする
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/api/lottery.js`
    - 新メソッド `respondOfferAll: (sessionId, accept) => client.post('/lottery/respond-offer-all', { sessionId, accept })` を追加
    - 新メソッド `getSessionOffers: (sessionId) => client.get('/lottery/session-offers/' + sessionId)` を追加（統合オファー画面用）
  - `karuta-tracker-ui/src/pages/lottery/OfferResponse.jsx`
    - 既存の `?id=participantId` パラメータは後方互換として維持
    - オファー詳細取得時に同一セッション×同一プレイヤーの他のOFFEREDも取得して表示
    - UI構成:
      - 複数試合の場合: 個別参加ボタン（緑）× N + すべて参加ボタン（青）+ 辞退ボタン（赤）
      - 1試合の場合: 参加ボタン（緑）+ 辞退ボタン（赤）
    - 個別参加後: 残りオファーの画面に更新（ページリロードまたは状態更新）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java`
    - 新エンドポイント `GET /api/lottery/session-offers/{sessionId}` — 指定セッション内の自分のOFFEREDを一覧取得
- **依存タスク:** タスク5
- **対応Issue:** #317

### タスク9: テスト

- [x] 完了
- **概要:** 新機能・改修箇所のユニットテスト・統合テストを追加する
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/WaitlistPromotionServiceTest.java`
    - `respondToOfferAll()` の正常系・異常系テスト
    - `respondToOffer()` 部分参加後の残りオファー通知テスト
    - `promoteNextWaitlisted()` からLINE通知が送信されないことの確認
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/LineNotificationServiceTest.java` (新規 or 既存に追加)
    - `sendConsolidatedWaitlistOfferNotification()` のFlex構築テスト（パターン1〜4）
    - 1試合/複数試合でのボタン構成の差異テスト
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/controller/LineWebhookControllerTest.java`
    - `waitlist_accept_all` / `waitlist_decline_all` のpostbackハンドリングテスト
- **依存タスク:** タスク1〜8
- **対応Issue:** #318

## 実装順序

1. **タスク1**（依存なし）— オファー通知の分離
2. **タスク2**（依存なし）— 統合Flex構築メソッド ※タスク1と並行可能
3. **タスク3**（タスク2に依存）— 一括応答・残りオファー通知
4. **タスク4**（タスク3に依存）— LINE postback新アクション
5. **タスク5**（タスク2, 3に依存）— APIエンドポイント・コントローラ ※タスク4と並行可能
6. **タスク6**（タスク1, 2に依存）— 伝助同期のオファー統合通知 ※タスク3〜5と並行可能
7. **タスク7**（タスク1, 2に依存）— 管理者編集のオファー通知抑制 ※タスク6と並行可能
8. **タスク8**（タスク5に依存）— フロントエンド
9. **タスク9**（全タスクに依存）— テスト
