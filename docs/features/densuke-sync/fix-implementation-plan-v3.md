---
status: completed
---

# 伝助との双方向同期機能 改修実装手順書（v3）

## 実装タスク

### タスク1: DensukeImportService Phase3 ユニットテスト追加（3-A: 伝助○）

- [ ] 完了
- **概要:** Phase3 の伝助○（processPhase3Maru）に対応する 12 テストケースを追加
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/DensukeImportServiceTest.java` — 以下のテストメソッドを追加:
    - `testPhase3Maru_3A1_unregistered_freeCapacity_createsAsWon`
    - `testPhase3Maru_3A2_unregistered_overCapacity_createsAsWaitlisted`
    - `testPhase3Maru_3A3_unregistered_noCapacity_createsAsWon`
    - `testPhase3Maru_3A4_won_notDirty_skips`
    - `testPhase3Maru_3A5_won_dirty_skips`
    - `testPhase3Maru_3A6_waitlisted_notDirty_setsDirtyTrue`
    - `testPhase3Maru_3A7_waitlisted_dirty_skips`
    - `testPhase3Maru_3A8a_offered_notDirty_deadlineValid_acceptsOffer`
    - `testPhase3Maru_3A8b_offered_notDirty_deadlineExpired_skips`
    - `testPhase3Maru_3A9_offered_dirty_skips`
    - `testPhase3Maru_3A10_cancelled_notDirty_freeCapacity_reactivatesAsWon`
    - `testPhase3Maru_3A11_cancelled_notDirty_overCapacity_reactivatesAsWaitlisted`
- **依存タスク:** なし
- **対応Issue:** #236

---

### タスク2: DensukeImportService Phase3 ユニットテスト追加（3-B: 伝助△）

- [ ] 完了
- **概要:** Phase3 の伝助△（processPhase3Sankaku）に対応する 9 テストケースを追加
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/DensukeImportServiceTest.java` — 以下のテストメソッドを追加:
    - `testPhase3Sankaku_3B1_unregistered_createsAsWaitlisted`
    - `testPhase3Sankaku_3B2_won_notDirty_demotesToWaitlisted`
    - `testPhase3Sankaku_3B3_won_dirty_skips`
    - `testPhase3Sankaku_3B4_waitlisted_notDirty_skips`
    - `testPhase3Sankaku_3B5_waitlisted_dirty_skips`
    - `testPhase3Sankaku_3B6_offered_notDirty_skips`
    - `testPhase3Sankaku_3B7_offered_dirty_skips`
    - `testPhase3Sankaku_3B8_cancelled_notDirty_reactivatesAsWaitlisted`
    - `testPhase3Sankaku_3B9_cancelled_dirty_skips`
- **依存タスク:** なし
- **対応Issue:** #237

---

### タスク3: DensukeImportService Phase3 ユニットテスト追加（3-C: 伝助×/空白）

- [ ] 完了
- **概要:** Phase3 の伝助×/空白（processPhase3Batsu）に対応する 9 テストケースを追加
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/DensukeImportServiceTest.java` — 以下のテストメソッドを追加:
    - `testPhase3Batsu_3C1_unregistered_skips`
    - `testPhase3Batsu_3C2_won_notDirty_cancelsAndPromotes`
    - `testPhase3Batsu_3C3_won_dirty_skips`
    - `testPhase3Batsu_3C4_waitlisted_notDirty_declines_renumbersQueue`
    - `testPhase3Batsu_3C5_waitlisted_dirty_skips`
    - `testPhase3Batsu_3C6_offered_notDirty_declinesAndPromotes`
    - `testPhase3Batsu_3C7_offered_dirty_skips`
    - `testPhase3Batsu_3C8_cancelled_notDirty_skips`
    - `testPhase3Batsu_3C9_cancelled_dirty_skips`
- **依存タスク:** なし
- **対応Issue:** #238

---

### タスク4: 通知タイプ `DENSUKE_ROW_ID_MISMATCH` の追加（バックエンド）

- [ ] 完了
- **概要:** 行ID不一致通知用の enum 値・Preference フィールド・isTypeEnabled 分岐を追加
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/Notification.java` — `NotificationType` enum に `DENSUKE_ROW_ID_MISMATCH` を追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/LineMessageLog.java` — `LineNotificationType` enum に `DENSUKE_ROW_ID_MISMATCH` を追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/PushNotificationPreference.java` — `densukeRowIdMismatch` フィールド追加（`@Column(name = "densuke_row_id_mismatch")`, デフォルト `true`）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/LineNotificationPreference.java` — `densukeRowIdMismatch` フィールド追加（同上）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/NotificationService.java` — `isTypeEnabled()` の switch に `case DENSUKE_ROW_ID_MISMATCH -> pref.getDensukeRowIdMismatch()` を追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java` — `isLineTypeEnabled()` の switch に `case DENSUKE_ROW_ID_MISMATCH -> pref.getDensukeRowIdMismatch()` を追加
- **依存タスク:** なし
- **対応Issue:** #239

---

### タスク5: DBマイグレーション

- [ ] 完了
- **概要:** 2つの Preference テーブルに `densuke_row_id_mismatch` カラムを追加するマイグレーションSQL
- **変更対象ファイル:**
  - `database/add_densuke_row_id_mismatch_preferences.sql`（**新規作成**） — 以下のSQL:
    ```sql
    ALTER TABLE push_notification_preferences
      ADD COLUMN densuke_row_id_mismatch BOOLEAN NOT NULL DEFAULT true;
    ALTER TABLE line_notification_preferences
      ADD COLUMN densuke_row_id_mismatch BOOLEAN NOT NULL DEFAULT true;
    ```
- **依存タスク:** タスク4
- **対応Issue:** #240

---

### タスク6: DensukeWriteService のログ引き上げ＋通知呼び出し

- [ ] 完了
- **概要:** `parseAndSaveRowIds()` の行ID不一致時にログを `error` に引き上げ、管理者にLINE＋アプリ内通知を送信
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeWriteService.java` — 以下の変更:
    1. `parseAndSaveRowIds()` 内の `log.warn()` → `log.error()` に変更
    2. 管理者通知メソッド呼び出しを追加（`DENSUKE_UNMATCHED_NAMES` の `notifyAdminsOfUnmatchedNames()` と同様のパターン）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java` — `sendAdminRowIdMismatchNotification()` メソッドを追加（`sendAdminWaitlistNotification()` と同様のパターンで、SUPER_ADMIN + 対象団体の ADMIN に Flex Message を送信）
- **依存タスク:** タスク4, タスク5
- **対応Issue:** #241

---

### タスク7: フロントエンド通知設定画面の更新

- [ ] 完了
- **概要:** 通知設定画面に「伝助行ID不一致通知」トグルを追加（管理者のみ表示）
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/notifications/NotificationSettings.jsx` — 以下の変更:
    1. `getPushTypesForOrg()` の `isAdmin` ブロック内に `{ key: 'densukeRowIdMismatch', label: '伝助行ID不一致' }` を追加
    2. `getLineTypesForOrg()` に管理者向け分岐を追加し `{ key: 'densukeRowIdMismatch', label: '伝助行ID不一致' }` を追加
- **依存タスク:** タスク4, タスク5
- **対応Issue:** #242

---

## 実装順序

1. **タスク1〜3**（並行可能・依存なし）— Phase3 テスト追加
2. **タスク4**（依存なし）— 通知タイプ enum・Preference・isTypeEnabled 追加
3. **タスク5**（タスク4に依存）— DBマイグレーション
4. **タスク6**（タスク4,5に依存）— DensukeWriteService のログ変更＋通知呼び出し
5. **タスク7**（タスク4,5に依存）— フロントエンド設定画面更新
