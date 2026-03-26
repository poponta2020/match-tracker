---
status: completed
---

# キャンセル周りの一連の処理 改修実装手順書

## 実装タスク

### タスク1: JstDateTimeUtil ユーティリティクラスの新設
- [x] 完了
- **概要:** `ZoneId.of("Asia/Tokyo")` 定数と `now()` / `today()` 静的メソッドを持つユーティリティクラスを新設する。プロジェクト全体のタイムゾーン統一の基盤となる。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/util/JstDateTimeUtil.java` — 新規作成。`JST` 定数、`now()` → `LocalDateTime.now(JST)`、`today()` → `LocalDate.now(JST)` の静的メソッドを提供
- **依存タスク:** なし
- **対応Issue:** #23

### タスク2: キャンセル・繰り上げ関連のタイムゾーン修正
- [x] 完了
- **概要:** キャンセル・繰り上げフローに直接関わるファイルの `LocalDate.now()` / `LocalDateTime.now()` を `JstDateTimeUtil` に置換する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryDeadlineHelper.java` — `isToday()`: `LocalDate.now()` → `JstDateTimeUtil.today()`、`isBeforeDeadline()`: `LocalDateTime.now()` → `JstDateTimeUtil.now()`、`calculateOfferDeadline()`: `LocalDateTime.now()` → `JstDateTimeUtil.now()`
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/WaitlistPromotionService.java` — `cancelledAt`, `offeredAt`, `respondedAt` の `LocalDateTime.now()` → `JstDateTimeUtil.now()`
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/OfferExpiryScheduler.java` — `findExpiredOffers(LocalDateTime.now())` → `JstDateTimeUtil.now()`
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryService.java` — `executedAt(LocalDateTime.now())` の2箇所 → `JstDateTimeUtil.now()`
- **依存タスク:** タスク1
- **対応Issue:** #24

### タスク3: スケジューラ・サービス全般のタイムゾーン修正
- [x] 完了
- **概要:** キャンセル関連以外のスケジューラ・サービスの `now()` / `today()` を `JstDateTimeUtil` に置換する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/LotteryScheduler.java` — `LocalDate.now()` 2箇所 → `JstDateTimeUtil.today()`
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/LineReminderScheduler.java` — `LocalDate.now()` → `JstDateTimeUtil.today()`
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/LineChannelReclaimScheduler.java` — `LocalDateTime.now()` 4箇所 → `JstDateTimeUtil.now()`
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/DensukeSyncScheduler.java` — `LocalDate.now()` → `JstDateTimeUtil.today()`
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/InviteTokenService.java` — `LocalDateTime.now()` 2箇所 → `JstDateTimeUtil.now()`
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineLinkingService.java` — `LocalDateTime.now()` 2箇所 → `JstDateTimeUtil.now()`
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineChannelService.java` — `LocalDateTime.now()` 3箇所 → `JstDateTimeUtil.now()`
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PlayerService.java` — `LocalDateTime.now()` 2箇所 → `JstDateTimeUtil.now()`
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeSessionService.java` — `LocalDate.now()` → `JstDateTimeUtil.today()`
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeParticipantService.java` — `LocalDate.now()` → `JstDateTimeUtil.today()`
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeImportService.java` — `LocalDate.now()` → `JstDateTimeUtil.today()`
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/GoogleCalendarSyncService.java` — `LocalDate.now()` → `JstDateTimeUtil.today()`、既存の `ZoneId.of("Asia/Tokyo")` → `JstDateTimeUtil.JST` に統一
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/HomeController.java` — `LocalDate.now()` → `JstDateTimeUtil.today()`
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/ErrorResponse.java` — `LocalDateTime.now()` 2箇所 → `JstDateTimeUtil.now()`
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/PlayerProfileDto.java` — `LocalDate.now()` → `JstDateTimeUtil.today()`
- **依存タスク:** タスク1
- **対応Issue:** #25

### タスク4: Entity の @PrePersist/@PreUpdate タイムゾーン修正
- [x] 完了
- **概要:** 全Entityの `@PrePersist` / `@PreUpdate` メソッド内の `LocalDateTime.now()` を `JstDateTimeUtil.now()` に置換する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/ByeActivity.java` — 3箇所
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/GoogleCalendarEvent.java` — 3箇所
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/InviteToken.java` — `@PrePersist` 1箇所 + `isExpired()` 1箇所
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/LineChannel.java` — 3箇所
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/LineChannelAssignment.java` — 2箇所
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/LineLinkingCode.java` — `@PrePersist` 1箇所 + `isExpired()` 1箇所
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/LineMessageLog.java` — 1箇所
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/LineNotificationPreference.java` — 2箇所
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/LineNotificationScheduleSetting.java` — 2箇所
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/LotteryExecution.java` — 1箇所
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/Match.java` — 3箇所
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/MatchPairing.java` — 3箇所
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/Notification.java` — 1箇所
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/Player.java` — 3箇所
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/PlayerProfile.java` — 3箇所
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/PracticeParticipant.java` — 3箇所
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/PracticeSession.java` — 3箇所
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/PushSubscription.java` — 3箇所
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/SystemSetting.java` — 2箇所
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/Venue.java` — 3箇所
- **依存タスク:** タスク1
- **対応Issue:** #26

### タスク5: respondToOffer に offerDeadline 超過チェック追加
- [x] 完了
- **概要:** `WaitlistPromotionService.respondToOffer()` で OFFERED ステータスチェック後に `offerDeadline` の超過判定を追加し、期限切れ後の応答を拒否する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/WaitlistPromotionService.java` — `respondToOffer()` メソッドに `participant.getOfferDeadline() != null && JstDateTimeUtil.now().isAfter(participant.getOfferDeadline())` チェックを追加。超過時は `IllegalStateException("応答期限が過ぎています")` をスロー
- **依存タスク:** タスク2（JSTタイムゾーン修正後に実装）
- **対応Issue:** #27

### タスク6: offer-detail API 新設 + OfferResponse 画面改修
- [x] 完了
- **概要:** 個別オファー詳細取得 API を新設し、OfferResponse 画面に期限表示・期限切れ判定・処理済み判定を追加する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java` — `GET /api/lottery/offer-detail/{participantId}` エンドポイント追加。`@RequireRole({SUPER_ADMIN, ADMIN, PLAYER})`、PLAYER は自分のレコードのみ。`WaitlistStatusDto.WaitlistEntry` を返却
  - `karuta-tracker-ui/src/api/lottery.js` — `getOfferDetail(participantId)` メソッド追加
  - `karuta-tracker-ui/src/pages/lottery/OfferResponse.jsx` — 画面表示時に `getOfferDetail` を呼び出し、セッション日付・試合番号・応答期限を表示。`offerDeadline < now` なら「応答期限が過ぎています」表示 + ボタン無効化。ステータスが `OFFERED` 以外なら「このオファーは処理済みです」表示
- **依存タスク:** なし
- **対応Issue:** #28

### タスク7: editParticipants で CANCELLED 変更時に繰り上げフロー発動
- [x] 完了
- **概要:** 管理者が editParticipants で WON → CANCELLED にステータスを変更した場合、繰り上げフローを発動させる。当日チェックも適用する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java` — `editParticipants()` のステータス変更処理で、旧ステータスが `WON` かつ新ステータスが `CANCELLED` の場合、セッション日付を取得し `lotteryDeadlineHelper.isToday()` でなければ `waitlistPromotionService.promoteNextWaitlisted()` を呼び出す。`LotteryDeadlineHelper` と `WaitlistPromotionService` の注入が必要（`WaitlistPromotionService` は注入済み、`LotteryDeadlineHelper` を追加注入）
- **依存タスク:** なし
- **対応Issue:** #29

### タスク8: バックエンドに過去日キャンセル防止バリデーション追加
- [x] 完了
- **概要:** PLAYER ロールが過去の練習日のキャンセルを API 経由で実行することを防止する。ADMIN+ は許可する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java` — `cancelParticipation()` の認可チェック後、PLAYER ロールの場合に各参加者のセッション日付が `JstDateTimeUtil.today()` より前かどうかチェック。過去日なら `IllegalStateException("過去の練習のキャンセルはできません")` をスロー
- **依存タスク:** タスク1（JstDateTimeUtil が必要）
- **対応Issue:** #30

### タスク9: 短時間期限のオファー通知に注意喚起追加
- [x] 完了
- **概要:** オファー期限まで12時間未満の場合に、LINE Flex Message とアプリ内通知に注意文言を追加する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java` — `sendWaitlistOfferNotification()` で `offerDeadline` と `JstDateTimeUtil.now()` の差を計算。12時間未満なら `buildWaitlistOfferFlex()` のボディに「※ 応答期限まで残りわずかです。お早めにご回答ください。」テキストを追加。`buildWaitlistOfferFlex()` に `isUrgent` パラメータを追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/NotificationService.java` — `createOfferNotification()` で同様に12時間未満判定を行い、通知メッセージに注意文言を付加
- **依存タスク:** タスク1（JstDateTimeUtil が必要）
- **対応Issue:** #31

---

## 実装順序

依存関係を考慮した推奨順序:

1. **タスク1: JstDateTimeUtil 新設**（依存なし、全タスクの基盤）
2. **タスク2: キャンセル・繰り上げ関連の TZ 修正**（タスク1に依存）
3. **タスク3: スケジューラ・サービス全般の TZ 修正**（タスク1に依存、タスク2と並行可）
4. **タスク4: Entity の TZ 修正**（タスク1に依存、タスク2・3と並行可）
5. **タスク5: respondToOffer 期限チェック**（タスク2に依存）
6. **タスク6: offer-detail API + OfferResponse 画面改修**（依存なし、独立実装可）
7. **タスク7: editParticipants 繰り上げフロー**（依存なし、独立実装可）
8. **タスク8: 過去日キャンセル防止**（タスク1に依存）
9. **タスク9: 短期限オファー注意喚起**（タスク1に依存）
