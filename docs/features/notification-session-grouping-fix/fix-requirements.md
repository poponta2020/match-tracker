---
status: completed
audit_source: バグ報告 + 会話内調査レポート
selected_items: [1, 2, 3]
---

# 通知セッション単位グルーピング漏れ修正 改修要件定義書

## 1. 改修概要

### 対象機能
キャンセル待ち繰り上げオファーのLINE通知（プレイヤー向け・管理者向け）

### 改修の背景
PR #319 で `sendConsolidatedWaitlistOfferNotification`（統合版）が導入され、主要経路（DensukeImportService, LotteryService, LotteryController）では同一セッション×同一プレイヤーのオファーが1通にまとまるようになった。しかし以下の3経路では個別版 `sendWaitlistOfferNotification` が残っており、試合単位で個別通知が送信される。

### 改修スコープ
1. `OfferExpiryScheduler` + `expireOffer()`: オファー期限切れ時の繰り上げ通知・管理者通知
2. `respondToOfferAll()`: 一括辞退時の繰り上げ先通知
3. `sendLotteryResultsForPlayer()`: LINE連携直後のOFFEREDオファー通知

## 2. 改修内容

### 2.1 項目1: OfferExpiryScheduler + expireOffer()

**現状の問題:**
- `OfferExpiryScheduler.checkExpiredOffers()` (L53-55) が期限切れオファーを1件ずつループで `expireOffer()` を呼ぶ
- PR #346 (commit `6d7474d`) で `expireOffer()` に `sendWaitlistOfferNotification`（個別版）が追加された
- 管理者通知も `List.of(notifData)` で1件ずつ `sendBatchedAdminWaitlistNotifications` に渡している
- 結果: 3試合分のオファー期限切れで、プレイヤーに3通の個別通知 + 管理者に3通の個別通知が送信

**修正方針:**
- `WaitlistPromotionService` に `expireOfferSuppressed()` を新設（LINE通知・管理者通知を抑制し、通知データを返す）
- `OfferExpiryScheduler` 側で期限切れオファーをセッション×繰り上げ先プレイヤーでグルーピング
- グルーピング後に `sendConsolidatedWaitlistOfferNotification` でまとめて送信
- 管理者通知もセッション単位でバッチ化して `sendBatchedAdminWaitlistNotifications` に渡す

**修正後のあるべき姿:**
- 同一セッション×同一プレイヤーの繰り上げオファーが1通の統合Flexメッセージにまとまる
- 管理者通知もセッション単位で1通にまとまる
- 期限切れ通知（`sendOfferExpiredNotification`）は従来通り個別送信（期限切れの人への通知なので統合不要）

### 2.2 項目2: respondToOfferAll() L558-564

**現状の問題:**
- 一括辞退（decline all）時、各試合のループ内で `sendWaitlistOfferNotification`（個別版）を呼んでいる
- 例: 3試合一括辞退 → 繰り上げ先が同一プレイヤーなら3通の個別通知

**修正方針:**
- ループ内では `promoted` をリストに蓄積するのみ
- ループ後にセッション×プレイヤーでグルーピングし `sendConsolidatedWaitlistOfferNotification` で送信
- 管理者通知は既にバッチ化済み（L576）なので変更不要

**修正後のあるべき姿:**
- 一括辞退で複数試合の繰り上げが同一プレイヤーに集中した場合、1通の統合Flex通知にまとまる
- 繰り上げ先が異なるプレイヤーの場合は、プレイヤーごとに1通ずつ

### 2.3 項目3: sendLotteryResultsForPlayer() L704-707

**現状の問題:**
- LINE連携直後に抽選結果を送信する際、OFFEREDの参加者をループで個別 `sendWaitlistOfferNotification` している
- 複数セッション×複数試合でOFFEREDの場合に個別通知になる

**修正方針:**
- `offered` をセッション単位でグルーピング
- 各セッションについて `sendConsolidatedWaitlistOfferNotification` を呼ぶ
- triggerAction は `null`（LINE連携直後の初回通知であり、特定のトリガーアクションがないため）

**修正後のあるべき姿:**
- 同一セッション内の複数OFFEREDが1通の統合Flexにまとまる
- 異なるセッションのOFFEREDはセッションごとに1通

## 3. 技術設計

### 3.1 API変更
なし

### 3.2 DB変更
なし

### 3.3 フロントエンド変更
なし

### 3.4 バックエンド変更

#### WaitlistPromotionService.java
- `expireOfferSuppressed(PracticeParticipant)` を新設
  - `expireOffer()` と同じ処理だが、LINE通知（繰り上げ先・管理者）を送信しない
  - 返り値: 通知に必要なデータ（`AdminWaitlistNotificationData` + promoted participant）
- 既存の `expireOffer()` はそのまま残す（単体呼び出し用の後方互換）

#### OfferExpiryScheduler.java
- `checkExpiredOffers()` を改修
  - 期限切れオファーのループで `expireOfferSuppressed()` を呼び、通知データを蓄積
  - ループ後にセッション×繰り上げ先プレイヤーでグルーピング
  - `sendConsolidatedWaitlistOfferNotification` でプレイヤー向け統合通知
  - `sendBatchedAdminWaitlistNotifications` でセッション単位の管理者通知

#### WaitlistPromotionService.java (respondToOfferAll)
- L558-564 のループ内: `sendWaitlistOfferNotification` 呼び出しを削除し、`promoted` をリストに蓄積
- ループ後: セッション×プレイヤーでグルーピングして `sendConsolidatedWaitlistOfferNotification` で送信

#### LineNotificationService.java (sendLotteryResultsForPlayer)
- L704-707 のループを削除
- `offered` をセッション単位でグルーピングし、`sendConsolidatedWaitlistOfferNotification` で送信

## 4. 影響範囲

### 影響を受ける既存機能
- `expireOffer()` の呼び出し元は `OfferExpiryScheduler` のみ → 影響限定的
- `respondToOfferAll()` の呼び出し元は `LineWebhookController` と `LotteryController` → テストで検証
- `sendLotteryResultsForPlayer()` の呼び出し元は `LineChannelService.sendPendingLotteryResultsForChannel()` のみ → 影響限定的

### 破壊的変更の有無
- なし（既存の `expireOffer()` はそのまま残す）
- `respondToOfferAll()` と `sendLotteryResultsForPlayer()` は内部実装の変更のみ

### テスト影響
- `WaitlistPromotionServiceTest`: `expireOffer` 関連テスト、`RespondToOfferAllTest` の verify 対象が変更
- `LineWebhookControllerTest`: mock 検証の更新が必要な可能性

## 5. 設計判断の根拠

### なぜ `expireOfferSuppressed` を新設するか
- `DensukeImportService` / `LotteryController` で採用されている `cancelParticipationSuppressed` パターンと同じ設計
- 既存の `expireOffer()` を破壊せず、スケジューラ側でバッチ処理を組み立てられる
- 他のサービスから `expireOffer()` が直接呼ばれる可能性を考慮した後方互換

### なぜ `respondToOffer()` (単体辞退) は改修しないか
- 単体辞退は1試合のみの操作 → 繰り上げ先が1試合分なので統合の必要なし
- 個別版 `sendWaitlistOfferNotification` の使用が妥当
