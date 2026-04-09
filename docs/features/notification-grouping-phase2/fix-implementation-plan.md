---
status: completed
---

# 空き枠通知セッション統合 + オファー承諾管理者通知修正 改修実装手順書

## 実装タスク

### タスク1: respondToOfferAll(accept=true) 管理者通知バグ修正
- [x] 完了
- **概要:** 一括承諾時に管理者通知が送信されていないバグを修正。ループ内で `AdminWaitlistNotificationData` を蓄積し、ループ後に `sendBatchedAdminWaitlistNotifications` を呼ぶ。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/WaitlistPromotionService.java` — `respondToOfferAll()` accept=true 分岐に管理者通知処理を追加
- **依存タスク:** なし
- **対応Issue:** #384

### タスク2: LineNotificationService — 統合版空き枠通知メソッド新設
- [x] 完了
- **概要:** セッション単位で空き枠通知をまとめるメソッドとFlex Message構築メソッドを新設。オファー用Flex Messageと同じ構造（ヘッダー + ボディ + 試合別ボタン + 全試合ボタン）。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java`
    - `sendConsolidatedSameDayVacancyNotification(PracticeSession, Map<Integer,Integer>, Long)` 新設
    - `sendConsolidatedAdminVacancyNotification(PracticeSession, Map<Integer,Integer>)` 新設
    - `buildConsolidatedSameDayVacancyFlex(String, Map<Integer,Integer>, Long, boolean)` 新設
    - `buildVacancyJoinButtons(Map<Integer,Integer>, Long)` 新設
- **依存タスク:** なし
- **対応Issue:** #385

### タスク3: SameDayVacancyScheduler — セッション単位統合送信
- [x] 完了
- **概要:** `processSession()` を改修。試合ごとの個別送信をやめ、空き試合をMapに蓄積し統合版メソッドで1回送信。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/SameDayVacancyScheduler.java` — `processSession()` 改修
- **依存タスク:** タスク2
- **対応Issue:** #386

### タスク4: expireOfferedForSameDayConfirmation — 空き枠通知統合
- [x] 完了
- **概要:** 12:00確定時の空き枠通知を、試合ごとの個別送信からセッション単位統合に変更。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/WaitlistPromotionService.java` — `expireOfferedForSameDayConfirmation()` L921-934 改修
- **依存タスク:** タスク2
- **対応Issue:** #387

### タスク5: handleSameDayJoinAll — 全試合一括参加処理の新設
- [x] 完了
- **概要:** `same_day_join_all` postbackアクション対応。WebhookControllerにハンドラー追加、WaitlistPromotionServiceに一括参加ロジック新設。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LineWebhookController.java`
    - `CONFIRMABLE_ACTIONS` に `"same_day_join_all"` 追加
    - 確認ダイアログ分岐に `same_day_join_all` のセッションラベル取得・メッセージ追加
    - `executeOriginalAction` switch に `"same_day_join_all"` 追加
    - `handleSameDayJoinAll()` メソッド新設
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/WaitlistPromotionService.java`
    - `handleSameDayJoinAll(Long sessionId, Long playerId)` メソッド新設
- **依存タスク:** なし
- **対応Issue:** #388

### タスク6: テスト追加・既存テスト修正
- [x] 完了
- **概要:** 改修箇所のユニットテストを追加・修正。
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/WaitlistPromotionServiceTest.java`
    - `respondToOfferAll` accept=true の管理者通知テスト追加
    - `expireOfferedForSameDayConfirmation` のverify対象を統合版に変更
    - `handleSameDayJoinAll` のテスト追加
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/scheduler/SameDayVacancySchedulerTest.java`
    - verify対象を統合版メソッドに変更
- **依存タスク:** タスク1, 2, 3, 4, 5
- **対応Issue:** #389

## 実装順序
1. タスク1（依存なし — バグ修正、最小変更）
2. タスク2（依存なし — 統合版メソッド新設）
3. タスク3（タスク2に依存 — スケジューラ改修）
4. タスク4（タスク2に依存 — 12:00確定時改修）
5. タスク5（依存なし — 一括参加処理新設）
6. タスク6（タスク1-5に依存 — テスト）
