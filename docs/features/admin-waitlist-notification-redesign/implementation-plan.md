---
status: completed
---
# 管理者向けキャンセル待ち状況通知 Flex Message 改修 実装手順書

## 実装タスク

### タスク1: 通知データモデルの導入と WaitlistPromotionService の通知分離

- [ ] 完了
- **概要:** 現在 `WaitlistPromotionService` 内で即時送信している管理者通知を、通知情報を蓄積して返す方式に変更する。通知に必要な情報を保持するデータクラスを導入し、各メソッドから通知を分離する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/AdminWaitlistNotificationData.java` — 新規作成。通知に必要な情報（triggerAction, triggerPlayerId, sessionId, matchNumber, offeredPlayer）を保持するデータクラス
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/WaitlistPromotionService.java` — 以下を変更:
    - `notifyAdminsAboutWaitlistChange` を、複数の `AdminWaitlistNotificationData` をセッション単位でまとめて送信する `sendBatchedAdminWaitlistNotifications` メソッドに置き換え
    - `cancelParticipation`: 通知を即時送信せず、呼び出し元に通知が必要かの情報を返す（戻り値の変更 or 通知データの返却）。ただし `LotteryController` から複数件ループで呼ばれるため、通知送信を `cancelParticipation` 内部から外に出し、`LotteryController` 側でまとめる必要がある
    - `demoteToWaitlist`: 単一試合のため、内部で通知データ1件を作成してそのまま送信（まとめ不要）
    - `respondToOffer`（オファー辞退）: 単一試合のため同上
    - `expireOffer`（オファー期限切れ）: 単一試合のため同上
    - `declineWaitlistBySession`: 既に複数試合をループしているので、通知データを蓄積してからまとめ送信に変更
    - `handleSameDayCancelAndRecruit`: 単一試合のため同上
- **依存タスク:** なし
- **対応Issue:** #279

### タスク2: LotteryController のキャンセルバッチ通知対応

- [ ] 完了
- **概要:** `LotteryController.cancelParticipation` で複数 `participantId` をループでキャンセルする際、通知情報を蓄積し、ループ完了後にセッション×トリガー×プレイヤーでグルーピングしてまとめ送信する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java` — キャンセルループ後にまとめ通知を呼び出す
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/WaitlistPromotionService.java` — `cancelParticipation` が通知データを返す or 通知送信なしで処理だけ行うオプションを提供
- **依存タスク:** タスク1
- **対応Issue:** #280

### タスク3: DensukeImportService のキャンセル/降格バッチ通知対応

- [ ] 完了
- **概要:** 伝助同期で同一セッション・同一プレイヤーの複数試合が同時にキャンセル/降格される場合の通知まとめ対応。`processPhase3Batsu`（キャンセル）と `processPhase3Sankaku`（降格）で発生する通知をセッション単位で蓄積し、試合ごとの処理完了後にまとめ送信する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeImportService.java` — 通知データの蓄積とまとめ送信の呼び出し
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/WaitlistPromotionService.java` — `cancelParticipation` / `demoteToWaitlist` に通知送信をスキップするオプションを提供し、代わりに通知データを返す
- **依存タスク:** タスク1
- **対応Issue:** #281

### タスク4: LineNotificationService の Flex Message 構成改修

- [ ] 完了
- **概要:** `sendAdminWaitlistNotification` と `buildAdminWaitlistFlex` を改修し、新しいFlex Message構成に対応する。`sendWaitlistPositionUpdateNotifications` も同様に改修。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java` — 以下を変更:
    - `sendAdminWaitlistNotification`: シグネチャを変更。`int matchNumber` → `List<Integer> matchNumbers`、各試合のキャンセル待ち列情報（`Map<Integer, List<PracticeParticipant>>`）と各試合のオファー先情報（`Map<Integer, Player>`）を受け取る
    - `buildAdminWaitlistFlex`: 新しいFlex構成に全面改修
      - ヘッダー: triggerAction に応じたテキスト切り替え（ヘッダーテキストマッピング）
      - セッション情報: `〇月〇日（会場名：定員〇〇名）` の専用フォーマット（`getSessionLabel` は変更しない）
      - 該当試合: カンマ区切りの試合番号列挙
      - イベント内容: triggerAction に応じたイベント文言マッピング
      - キャンセル待ち列: 全試合同一判定→まとめ or 試合別表示、1番の人のオファー状態による緑太字切り替え
    - `sendWaitlistPositionUpdateNotifications`: 同じ新しいFlexを使用するよう改修。シグネチャも合わせて変更
    - altText: `【管理者通知】〇月〇日: {選手名}が{イベント文言}`
- **依存タスク:** タスク1
- **対応Issue:** #282

### タスク5: テスト修正と動作確認

- [x] 完了
- **概要:** シグネチャ変更に伴うテストの修正と、全体テストの実行。
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/WaitlistPromotionServiceTest.java` — 通知呼び出しのverify修正
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/LineNotificationRoutingTest.java` — 必要に応じて修正
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/LineNotificationServiceAdminTest.java` — 必要に応じて修正
  - 伝助同期関連テスト — 通知スキップ/まとめ送信のverify修正
- **依存タスク:** タスク1, タスク2, タスク3, タスク4
- **対応Issue:** #283

## 実装順序

1. **タスク1**（依存なし）— 通知データモデル導入と WaitlistPromotionService の通知分離。全体の基盤
2. **タスク2, タスク3, タスク4**（タスク1に依存、互いに独立）— 並行実装可能
   - タスク2: LotteryController のバッチ通知対応
   - タスク3: DensukeImportService のバッチ通知対応
   - タスク4: LineNotificationService の Flex 構成改修
3. **タスク5**（全タスクに依存）— テスト修正と全体動作確認
