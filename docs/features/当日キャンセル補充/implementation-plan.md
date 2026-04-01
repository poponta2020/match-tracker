---
status: completed
---
# 当日キャンセル補充 実装手順書

## 実装タスク

### タスク1: DB・Enum・Entity変更
- [ ] 完了
- **対応Issue:** #220
- **概要:** LineNotificationType enumに3値追加、LineNotificationPreferenceに3つのboolean列追加、DBマイグレーション実行
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/LineMessageLog.java` — LineNotificationType enumに `SAME_DAY_CONFIRMATION`, `SAME_DAY_CANCEL`, `SAME_DAY_VACANCY` を追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/LineNotificationPreference.java` — `sameDayConfirmation`, `sameDayCancel`, `sameDayVacancy` boolean列を追加（デフォルト: true）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/LineNotificationPreferenceDto.java` — 3フィールド追加、fromEntity()更新
  - `database/` — マイグレーションSQL作成・実行（ALTER TABLE ADD COLUMN IF NOT EXISTS）
- **依存タスク:** なし

### タスク2: LotteryDeadlineHelper拡張
- [ ] 完了
- **対応Issue:** #221
- **概要:** MONTHLY型にも当日12:00判定を適用するメソッド追加。既存の`isToday()`とは別に、「当日12:00以降か」を判定する`isAfterSameDayNoon(LocalDate sessionDate)`メソッドを追加
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryDeadlineHelper.java` — `isAfterSameDayNoon()` メソッド追加
- **依存タスク:** なし

### タスク3: 12:00スケジューラ（SameDayConfirmationScheduler）
- [ ] 完了
- **対応Issue:** #222
- **概要:** 毎日12:00 JST実行。当日セッションのOFFERED→DECLINED強制失効 + WON参加者に確定通知送信
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/SameDayConfirmationScheduler.java` — 新規作成
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java` — `sendSameDayConfirmationNotification()` メソッド追加（全試合メンバーリストのFlex Message構築・送信）
- **依存タスク:** タスク1, タスク2

### タスク4: WaitlistPromotionService — 当日12:00以降キャンセル時の新フロー
- [ ] 完了
- **対応Issue:** #223
- **概要:** `cancelParticipation`の当日判定を12:00基準に変更。12:00以降のキャンセル時に、キャンセル通知（全参加者向け）＋空き募集通知（非WON向けFlex Message with ボタン）を送信
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/WaitlistPromotionService.java` — `cancelParticipation()`の当日分岐を`isAfterSameDayNoon()`に変更、新メソッド`handleSameDayCancelAndRecruit()`追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java` — `sendSameDayCancelNotification()`（テキスト）、`sendSameDayVacancyNotification()`（Flex Message + ボタン）追加
- **依存タスク:** タスク1, タスク2

### タスク5: 空き募集ボタン応答（same_day_join）
- [ ] 完了
- **対応Issue:** #224
- **概要:** LINEボタンのpostback `action=same_day_join` を処理。先着1名をWONに変更し、参加通知＋枠状況通知を送信。練習開始時間を過ぎていたら無効。2人目以降はエラー
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LineWebhookController.java` — postbackハンドラに `same_day_join` アクション追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/WaitlistPromotionService.java` — `handleSameDayJoin(sessionId, matchNumber, playerId)` メソッド追加。排他制御（synchronized or DB楽観ロック）で先着1名を保証
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java` — `POST /api/lottery/same-day-join` エンドポイント追加（Web経由のフォールバック）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java` — `sendSameDayJoinNotification()`（WONメンバー向け）、`sendSameDayVacancyUpdateNotification()`（残り枠 or 枠埋まりFlex Message）追加
- **依存タスク:** タスク1, タスク4

### タスク6: 12:00以降アプリ参加登録時の通知
- [ ] 完了
- **対応Issue:** #225
- **概要:** 12:00以降にアプリ経由で定員未満の試合に参加登録された場合、その試合のWONメンバーに「〇〇さんが参加します」通知を送信
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeParticipantService.java` — 参加登録処理後に12:00以降判定を追加、該当時にLineNotificationService呼び出し
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java` — `sendSameDayJoinNotification()` を再利用（タスク5で作成済み）
- **依存タスク:** タスク2, タスク5

### タスク7: フロントエンド — 当日12:00以降キャンセル確認ダイアログ
- [ ] 完了
- **対応Issue:** #226
- **概要:** 北大かるた会（MONTHLY型）でも当日12:00以降のキャンセル時に「直前のキャンセルとなりますがよろしいですか？」確認ダイアログを表示。既存のSAME_DAY型ダイアログ条件を拡張
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/practice/PracticeCancelPage.jsx` — キャンセル確定前の条件分岐に、MONTHLY型＋当日12:00以降の判定を追加
- **依存タスク:** なし

### タスク8: フロントエンド — LINE通知設定トグル追加
- [ ] 完了
- **対応Issue:** #227
- **概要:** 通知設定画面に「参加者確定通知」「当日キャンセル通知」「空き募集通知」の3つの独立トグルを追加
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/notifications/NotificationSettings.jsx` — `getLineTypesForOrg()`に3項目追加、トグルのstate管理・API呼び出し対応
- **依存タスク:** タスク1

### タスク9: OfferExpirySchedulerとの競合防止
- [ ] 完了
- **対応Issue:** #228
- **概要:** 12:00にSameDayConfirmationSchedulerがOFFEREDを一括失効させるため、OfferExpirySchedulerが12:00直前に同じオファーを失効させないよう制御。具体的には、オファー期限を12:00以降に設定されたものはOfferExpirySchedulerの対象外にする、またはSameDayConfirmationSchedulerの処理を冪等にする
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/SameDayConfirmationScheduler.java` — 冪等処理（OFFERED状態のもののみ処理、既にDECLINEDなら無視）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/WaitlistPromotionService.java` — `calculateOfferDeadline()`で当日セッションの場合にオファー期限を12:00に設定（12:00前に自然失効しないようにする）
- **依存タスク:** タスク3

### タスク10: テスト
- [ ] 完了
- **対応Issue:** #229
- **概要:** 主要フローの単体テスト・統合テストを追加
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/WaitlistPromotionServiceTest.java` — 当日12:00以降キャンセル→補充フローのテスト追加
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/scheduler/SameDayConfirmationSchedulerTest.java` — 新規作成。12:00オファー失効＋確定通知のテスト
- **依存タスク:** タスク1〜9

## 実装順序

1. タスク1（DB・Enum・Entity） — 依存なし
2. タスク2（LotteryDeadlineHelper拡張） — 依存なし
3. タスク7（フロントエンド・キャンセルダイアログ） — 依存なし
4. タスク8（フロントエンド・通知トグル） — タスク1に依存
5. タスク3（12:00スケジューラ） — タスク1, 2に依存
6. タスク9（競合防止） — タスク3に依存
7. タスク4（当日キャンセル新フロー） — タスク1, 2に依存
8. タスク5（ボタン応答） — タスク1, 4に依存
9. タスク6（アプリ参加登録通知） — タスク2, 5に依存
10. タスク10（テスト） — 全タスクに依存
