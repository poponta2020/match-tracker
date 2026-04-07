---
status: completed
---

# 通知セッション単位グルーピング漏れ修正 改修実装手順書

## 実装タスク

### タスク1: WaitlistPromotionService — expireOfferSuppressed 新設
- [x] 完了
- **概要:** `expireOffer()` の通知抑制版を新設。LINE通知・管理者通知を送信せず、通知データを返す。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/WaitlistPromotionService.java` — `expireOfferSuppressed()` メソッド追加。返り値は `ExpireOfferResult`（AdminWaitlistNotificationData + promoted participant）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/ExpireOfferResult.java` — 新規DTO（notifData + promotedParticipant を保持）
- **依存タスク:** なし
- **対応Issue:** #350

### タスク2: OfferExpiryScheduler — セッション×プレイヤーグルーピング
- [x] 完了
- **概要:** `checkExpiredOffers()` を改修。期限切れオファーを `expireOfferSuppressed()` で処理し、通知データを蓄積後にまとめて送信。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/OfferExpiryScheduler.java` — `checkExpiredOffers()` のループ改修
    - 期限切れオファーごとに `expireOfferSuppressed()` を呼び、結果を蓄積
    - `sendOfferExpiredNotification` は従来通り個別送信（期限切れ本人への通知）
    - 蓄積した promoted をセッション×プレイヤーでグルーピング
    - `sendConsolidatedWaitlistOfferNotification` でプレイヤー向け統合通知
    - AdminWaitlistNotificationData をセッション単位でグルーピングして `sendBatchedAdminWaitlistNotifications`
- **依存タスク:** タスク1
- **対応Issue:** #351

### タスク3: respondToOfferAll — 繰り上げ先通知のバッチ化
- [x] 完了
- **概要:** 一括辞退時のループ内個別通知を蓄積→一括送信に変更。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/WaitlistPromotionService.java` — `respondToOfferAll()` L558-564 を改修
    - ループ内: `sendWaitlistOfferNotification` 呼び出しを削除、promoted をリストに蓄積
    - ループ後: セッション×プレイヤーでグルーピングし `sendConsolidatedWaitlistOfferNotification` 呼び出し
- **依存タスク:** なし
- **対応Issue:** #352

### タスク4: sendLotteryResultsForPlayer — OFFERED通知のセッション単位統合
- [x] 完了
- **概要:** LINE連携直後のOFFERED通知をセッション単位でまとめて送信。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java` — `sendLotteryResultsForPlayer()` L704-707 を改修
    - `offered` をセッション単位でグルーピング
    - 各セッションについて `sendConsolidatedWaitlistOfferNotification` 呼び出し（triggerAction=null）
- **依存タスク:** なし
- **対応Issue:** #353

### タスク5: テスト追加・既存テスト修正
- [x] 完了
- **概要:** 改修箇所のユニットテストを追加・修正。
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/WaitlistPromotionServiceTest.java`
    - `expireOfferSuppressed` のテスト追加
    - `RespondToOfferAllTest` の verify 対象を `sendConsolidatedWaitlistOfferNotification` に変更
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/scheduler/OfferExpirySchedulerTest.java`（新規 or 既存に追加）
    - 同一セッション×同一プレイヤーで3試合期限切れ → 統合通知1通の検証
    - 異なるセッションの期限切れ → セッションごとに1通の検証
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/LineNotificationServiceTest.java`（既存に追加）
    - `sendLotteryResultsForPlayer` で複数OFFERED → セッション単位統合の検証
- **依存タスク:** タスク1, 2, 3, 4
- **対応Issue:** #354

## 実装順序
1. タスク1（依存なし — DTO + メソッド新設）
2. タスク2（タスク1に依存 — スケジューラ改修）
3. タスク3（依存なし — respondToOfferAll 改修）
4. タスク4（依存なし — sendLotteryResultsForPlayer 改修）
5. タスク5（タスク1-4に依存 — テスト）
