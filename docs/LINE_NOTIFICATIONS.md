# LINE通知 全種別一覧

全LINE通知種別の発火条件・送信先・チャネル・トグルを網羅的にまとめたリファレンス。

> 最終更新: 2026-04-03

---

## 通知種別サマリー

| # | 通知タイプ | チャネル | 送信先 | トリガー方式 |
|---|-----------|---------|--------|------------|
| 1 | `LOTTERY_RESULT` | PLAYER | 抽選対象プレイヤー | 管理者手動送信 |
| 2 | `WAITLIST_OFFER` | PLAYER | 繰り上げ対象者（1名） | イベント発火型（自動） |
| 3 | `OFFER_EXPIRED` | PLAYER | オファー期限切れの本人（1名） | スケジュール型（5分ごと / 12:00） |
| 4 | `MATCH_PAIRING` | PLAYER | WON参加者 | 管理者手動送信 |
| 5 | `PRACTICE_REMINDER` | PLAYER | WON参加者 | スケジュール型（毎日8:00） |
| 6 | `DEADLINE_REMINDER` | PLAYER | — | **未実装** |
| 7 | `ADMIN_WAITLIST_UPDATE` | ADMIN | 該当団体ADMIN + 全SUPER_ADMIN | イベント発火型（自動） |
| 8 | `WAITLIST_POSITION_UPDATE` | PLAYER | WAITLISTEDユーザー | イベント発火型（自動） |
| 9 | `SAME_DAY_CONFIRMATION` | PLAYER | WON参加者 | スケジュール型（毎日12:00） |
| 10 | `SAME_DAY_CANCEL` | PLAYER | WON参加者（本人除く） | イベント発火型（自動） |
| 11 | `SAME_DAY_VACANCY` | PLAYER | 団体全メンバー（該当試合WON除く） | イベント発火型（自動）/ スケジュール型（0:00） |
| 12 | `ADMIN_SAME_DAY_CONFIRMATION` | ADMIN | 該当団体ADMIN + 全SUPER_ADMIN（WON問わず送信） | スケジュール型（毎日12:00） |
| 13 | `ADMIN_SAME_DAY_CANCEL` | ADMIN | 該当団体ADMIN + 全SUPER_ADMIN | イベント発火型（自動）/ スケジュール型（0:00） |

---

## 各通知種別の詳細

### 1. LOTTERY_RESULT（抽選結果）

| 項目 | 内容 |
|------|------|
| **発火条件** | 管理者が抽選結果通知を手動送信 / LINE連携完了時に未通知の確定済み結果がある場合 |
| **トリガー** | `LotteryController.notifyAll()`, `notifyWaitlisted()`, `LineChannelService.sendPendingLotteryResultsForChannel()` |
| **送信先** | 抽選対象の各プレイヤー（WON/WAITLISTED） |
| **チャネル** | PLAYER |
| **トグル** | `lotteryResult` |
| **内容** | 全当選→テキスト / 一部落選→テキスト+セッション別Flex（辞退ボタン付き） / 全落選→テキスト+Flex |

### 2. WAITLIST_OFFER（繰り上げオファー）

| 項目 | 内容 |
|------|------|
| **発火条件** | キャンセル待ちから繰り上げ（OFFERED化）されたとき / LINE連携完了時にOFFERED状態のものがあるとき / オファー応答時 |
| **トリガー** | `WaitlistPromotionService.promoteNextWaitlisted()`, `LotteryController.respondToWaitlistOffer()` |
| **送信先** | 繰り上げオファーを受けた本人（1名） |
| **チャネル** | PLAYER |
| **トグル** | `waitlistOffer` |
| **内容** | Flex Message（セッション名・試合番号・応答期限・「参加する」「辞退する」ボタン付き） / 応答確認テキスト |

### 3. OFFER_EXPIRED（オファー期限切れ）

| 項目 | 内容 |
|------|------|
| **発火条件** | `OfferExpiryScheduler`（5分ごと）が期限切れOFFERED検出時 / `SameDayConfirmationScheduler`（12:00）が当日OFFEREDを一括処理時 |
| **トリガー** | `WaitlistPromotionService.expireOffer()`, `expireOfferedForSameDayConfirmation()` |
| **送信先** | オファー期限切れとなった本人（1名） |
| **チャネル** | PLAYER |
| **トグル** | `offerExpired` |
| **内容** | テキスト「{セッション名} {N}試合目の繰り上げ参加の期限が切れました」 |

### 4. MATCH_PAIRING（対戦組み合わせ）

| 項目 | 内容 |
|------|------|
| **発火条件** | 管理者が対戦組み合わせLINE送信を手動実行 |
| **トリガー** | `LineAdminController.sendMatchPairing()` |
| **送信先** | 当該セッションの全WON参加者 |
| **チャネル** | PLAYER |
| **トグル** | `matchPairing` |
| **内容** | テキスト「今日の練習の対戦組み合わせはこちらです」 |

### 5. PRACTICE_REMINDER（参加予定リマインダー）

| 項目 | 内容 |
|------|------|
| **発火条件** | 毎日AM8:00、設定された `daysBefore` に該当する練習日があるとき |
| **トリガー** | `LineReminderScheduler.sendPracticeReminders()` |
| **送信先** | 対象セッションの全WON参加者 |
| **チャネル** | PLAYER |
| **トグル** | `practiceReminder` |
| **内容** | テキスト（1日前:「明日は{会場名}での練習に参加予定です！」、2日前:「明後日...」等） |

### 6. DEADLINE_REMINDER（締め切りリマインダー）

| 項目 | 内容 |
|------|------|
| **発火条件** | **未実装**（スケジューラの構造のみ存在。PracticeSessionに締め切り日フィールドが未追加のため） |
| **送信先** | — |
| **チャネル** | PLAYER |
| **トグル** | `deadlineReminder` |

### 7. ADMIN_WAITLIST_UPDATE（管理者向けキャンセル待ち状況通知）

| 項目 | 内容 |
|------|------|
| **発火条件** | キャンセル待ち列に変動があったとき（キャンセル、降格、オファー辞退、オファー期限切れ、キャンセル待ち辞退、当日キャンセル補充） |
| **トリガー** | `WaitlistPromotionService.notifyAdminsAboutWaitlistChange()` → `LineNotificationService.sendAdminWaitlistNotification()` |
| **送信先** | 該当団体のADMIN + 全SUPER_ADMIN |
| **チャネル** | ADMIN |
| **トグル** | `adminWaitlistUpdate`（`organizationId=0` レコードで判定） |
| **内容** | Flex Message（紫ヘッダー「キャンセル待ち状況通知」。発生イベント・繰り上げオファー先・残りキャンセル待ち列を表示） |

### 8. WAITLIST_POSITION_UPDATE（キャンセル待ち順番通知）

| 項目 | 内容 |
|------|------|
| **発火条件** | ADMIN_WAITLIST_UPDATE と同じタイミング |
| **トリガー** | `WaitlistPromotionService.notifyAdminsAboutWaitlistChange()` → `LineNotificationService.sendWaitlistPositionUpdateNotifications()` |
| **送信先** | 残りのWAITLISTEDユーザー全員（当該試合の待ち列にいる人） |
| **チャネル** | PLAYER |
| **トグル** | `waitlistOffer`（流用） |
| **内容** | Flex Message（ADMIN_WAITLIST_UPDATEと同じFlex。発生イベント・繰り上げオファー先・残り待ち列を表示） |

### 9. SAME_DAY_CONFIRMATION（当日12:00参加者確定）

| 項目 | 内容 |
|------|------|
| **発火条件** | 毎日12:00 JST、当日の練習セッションがある場合 |
| **トリガー** | `SameDayConfirmationScheduler` → `LineNotificationService.sendSameDayConfirmationNotification()` |
| **送信先** | 当該セッションの全WON参加者 |
| **チャネル** | PLAYER |
| **トグル** | `sameDayConfirmation` |
| **内容** | Flex Message（青ヘッダー「本日の練習メンバー」。試合ごとにメンバーリストを段位順3人1行で表示） |

### 10. SAME_DAY_CANCEL（当日キャンセル通知 / 当日参加通知）

| 項目 | 内容 |
|------|------|
| **発火条件A（キャンセル）** | 当日12:00以降のキャンセル時 |
| **発火条件B（参加）** | 空き募集ボタンで参加登録時 / アプリ経由で当日12:00以降にWON登録時 |
| **トリガーA** | `WaitlistPromotionService.handleSameDayCancelAndRecruit()` → `sendSameDayCancelNotification()` |
| **トリガーB** | `WaitlistPromotionService.handleSameDayJoin()` / `PracticeParticipantService.notifySameDayJoinIfApplicable()` → `sendSameDayJoinNotification()` |
| **送信先** | 当該セッション（or試合）の全WON参加者（本人除く） |
| **チャネル** | PLAYER |
| **トグル** | `sameDayCancel` |
| **内容A** | テキスト「{名前}さんが今日の{N}試合目をキャンセルしました」 |
| **内容B** | テキスト「{名前}さんが今日の{N}試合目に参加します」 |

> **注**: `sendSameDayJoinNotification()` が `SAME_DAY_CANCEL` タイプを流用している

### 11. SAME_DAY_VACANCY（当日空き募集 / 枠状況更新）

| 項目 | 内容 |
|------|------|
| **発火条件A（空き募集）** | 当日12:00以降のキャンセル時（空き枠あり） / 12:00確定時にOFFERED期限切れ後の空き枠あり |
| **発火条件B（枠状況更新）** | 空き募集ボタンで参加登録後 / 伝助同期で当日12:00以降にWON登録後 |
| **発火条件C（0:00空き枠）** | 毎日0:00 JST、当日セッションで抽選実行済み・WON数＜定員・WAITLISTED＝0の試合がある場合 |
| **トリガーA** | `WaitlistPromotionService` → `sendSameDayVacancyNotification()` |
| **トリガーB** | `WaitlistPromotionService.handleSameDayJoin()` / `DensukeImportService.notifyVacancyUpdateIfNeeded()` → `sendSameDayVacancyUpdateNotification()` |
| **トリガーC** | `SameDayVacancyScheduler`（新規） → `sendSameDayVacancyNotification()` |
| **送信先** | 当該セッションの団体に所属する全プレイヤー（該当試合のWON参加者を除く） |
| **チャネル** | PLAYER |
| **トグル** | `sameDayVacancy` |
| **内容A** | Flex Message（オレンジヘッダー「空き枠のお知らせ」+「参加する」ボタン） |
| **内容B（空きあり）** | Flex Message（オレンジ「{名前}さんが参加登録。残り{N}名」+「参加する」ボタン） |
| **内容B（定員達成）** | Flex Message（グレー「{N}試合目は定員に達しました！」ボタンなし） |
| **内容C** | 内容Aと同じ（オレンジヘッダー「空き枠のお知らせ」+「参加する」ボタン） |

### 12. ADMIN_SAME_DAY_CONFIRMATION（管理者向け12:00確定通知）

| 項目 | 内容 |
|------|------|
| **発火条件** | 毎日12:00 JST、SAME_DAY_CONFIRMATION送信と同時 |
| **トリガー** | `SameDayConfirmationScheduler` → `sendSameDayConfirmationNotification()` 内で送信 |
| **送信先** | 該当団体のADMIN + 全SUPER_ADMIN（WON参加者かどうかに関係なく必ず送信） |
| **チャネル** | ADMIN |
| **トグル** | `adminSameDayConfirmation`（`organizationId=0` レコードで判定） |
| **内容** | SAME_DAY_CONFIRMATION と同一のFlex Message（青ヘッダー「本日の練習メンバー」） |

### 13. ADMIN_SAME_DAY_CANCEL（管理者向け当日キャンセル・参加・空き枠通知）

| 項目 | 内容 |
|------|------|
| **発火条件A（キャンセル）** | 当日12:00以降のキャンセル時 |
| **発火条件B（参加）** | 空き募集ボタンで参加登録時 / アプリ経由で当日12:00以降にWON登録時 |
| **発火条件C（0:00空き枠）** | 毎日0:00 JST、当日セッションで抽選実行済み・WON数＜定員・WAITLISTED＝0の試合がある場合 |
| **トリガーA** | `sendSameDayCancelNotification()` 内で管理者にも送信 |
| **トリガーB** | `sendSameDayJoinNotification()` 内で管理者にも送信 |
| **トリガーC** | `SameDayVacancyScheduler`（新規） → 管理者向け送信 |
| **送信先** | 該当団体のADMIN + 全SUPER_ADMIN |
| **チャネル** | ADMIN（`ADMIN_` プレフィクスで判定） |
| **トグル** | `adminSameDayCancel`（`organizationId=0` レコードで判定） |
| **内容A** | テキスト「{名前}さんが今日の{N}試合目をキャンセルしました」（SAME_DAY_CANCELと同一） |
| **内容B** | テキスト「{名前}さんが今日の{N}試合目に参加します」（SAME_DAY_CANCELと同一） |
| **内容C** | Flex Message（オレンジヘッダー「空き枠のお知らせ」+「参加する」ボタン。SAME_DAY_VACACNYと同一） |

---

## 補足

### リッチメニューからのリプライ（通知ではない）

LINE Webhookからの応答は **リプライAPI** で返すため、`LineNotificationType` は使わず `LineMessageLog` にも記録されない:

- 今日の参加者照会
- キャンセル待ち照会
- 当日参加申込一覧

### DensukeImportService からの間接的な通知

伝助同期は `WaitlistPromotionService` のメソッドを呼ぶことで、`WAITLIST_OFFER`, `OFFER_EXPIRED`, `ADMIN_WAITLIST_UPDATE`, `WAITLIST_POSITION_UPDATE`, `SAME_DAY_CANCEL`, `SAME_DAY_VACANCY`, `ADMIN_SAME_DAY_CANCEL` が間接的に発火し得る。直接呼び出しは `sendSameDayVacancyUpdateNotification()` のみ（当日12:00以降に伝助同期でWON登録された場合）。

### チャネルタイプの判定ルール

`LineNotificationType.getRequiredChannelType()` で判定:
- `ADMIN_` プレフィクスで始まるもの → `ChannelType.ADMIN`（管理者用チャネル）
- それ以外 → `ChannelType.PLAYER`（選手用チャネル）
