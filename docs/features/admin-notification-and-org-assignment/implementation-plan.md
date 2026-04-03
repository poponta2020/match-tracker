---
status: completed
---
# ADMIN向け当日通知拡張 & 空き枠通知改善 & 所属団体設定UI 実装手順書

## 実装タスク

### タスク1: DBマイグレーション
- [x] 完了
- **概要:** `line_notification_preferences` に `admin_same_day_cancel` カラム追加、`line_message_logs` の `notification_type` CHECK制約に `ADMIN_SAME_DAY_CANCEL` を追加
- **変更対象ファイル:**
  - `database/add_admin_same_day_cancel_preference.sql`（新規）— `ALTER TABLE line_notification_preferences ADD COLUMN IF NOT EXISTS admin_same_day_cancel BOOLEAN NOT NULL DEFAULT TRUE;`
  - `database/update_line_message_log_notification_type_check.sql`（更新）— CHECK制約に `ADMIN_SAME_DAY_CANCEL` を追加
- **依存タスク:** なし
- **対応Issue:** #260

---

### タスク2: バックエンド Entity / DTO / Repository 変更
- [x] 完了
- **概要:** `ADMIN_SAME_DAY_CANCEL` の enum追加、通知設定フィールド追加、団体別ADMIN検索メソッド追加
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/LineMessageLog.java`（行68付近）— `LineNotificationType` enumに `ADMIN_SAME_DAY_CANCEL` を追加（`SAME_DAY_CANCEL` と `SAME_DAY_VACANCY` の間）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/LineNotificationPreference.java`（行87-89の後）— `adminSameDayCancel` フィールド追加（Boolean, default true, `@Column(name = "admin_same_day_cancel")`）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/LineNotificationPreferenceDto.java`（行26付近）— `adminSameDayCancel` フィールド追加 + `fromEntity()` にマッピング追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/PlayerRepository.java`（行68以降）— `findByRoleAndAdminOrganizationIdAndActive(Role role, Long orgId)` メソッド追加（`@Query` で `deletedAt IS NULL` 条件付き）
- **依存タスク:** タスク1（ランタイム依存。コンパイルは独立）
- **対応Issue:** #261

---

### タスク3: バックエンド Service — 管理者受信者ヘルパー & 通知メソッド変更
- [x] 完了
- **概要:** `getAdminRecipientsForSession()` ヘルパーを新設し、4つの通知メソッドでADMIN向け送信を追加。トグル判定の `organizationId=0` 統一も行う
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java` — 以下の変更:
    - **新設: `getAdminRecipientsForSession(PracticeSession session)`** — SUPER_ADMIN全員 + 該当団体のADMINを返すヘルパー
    - **変更: `sendSameDayCancelNotification()`（行1238）** — 既存WON参加者への送信後に、`getAdminRecipientsForSession()` で取得した管理者に `ADMIN_SAME_DAY_CANCEL` で送信追加
    - **変更: `sendSameDayJoinNotification()`（行1375）** — 同上、管理者に `ADMIN_SAME_DAY_CANCEL` で送信追加
    - **変更: `sendSameDayConfirmationNotification()`（行961）** — 送信先を `findByRoleAndActive(SUPER_ADMIN)` から `getAdminRecipientsForSession()` に変更。WON参加者スキップロジックを廃止
    - **変更: `sendAdminWaitlistNotification()`（行708）** — 送信先を `getAdminRecipientsForSession()` に変更
    - **変更: `isNotificationEnabled()`（行1571）** — `ADMIN_SAME_DAY_CANCEL` を `organizationId=0` レコードで判定する条件追加。`ADMIN_WAITLIST_UPDATE` も `organizationId=0` 判定に統一
    - **変更: `isLineTypeEnabled()`（行1595）** — switch文に `case ADMIN_SAME_DAY_CANCEL -> pref.getAdminSameDayCancel()` 追加
    - **変更: `updatePreferences()`（行873）** — `pref.setAdminSameDayCancel(dto.isAdminSameDayCancel())` 追加
- **依存タスク:** タスク2
- **対応Issue:** #262

---

### タスク4: バックエンド Service — SAME_DAY_VACANCY 送信先拡大
- [x] 完了
- **概要:** `SAME_DAY_VACANCY` の送信先を「セッション参加者（非WON）」から「団体全メンバー（WON除く）」に変更
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java` — 以下の変更:
    - **変更: `sendSameDayVacancyNotification()`（行1267）** — 受信者取得ロジックを `practiceParticipantRepository.findBySessionId()` から `playerOrganizationRepository.findByOrganizationId(session.getOrganizationId())` に変更し、該当試合のWON参加者を除外
    - **変更: `sendSameDayVacancyUpdateNotification()`（行1401）** — 同上の送信先拡大
- **依存タスク:** タスク2
- **対応Issue:** #263

---

### タスク5: バックエンド Scheduler — 0:00空き枠通知スケジューラ
- [x] 完了
- **概要:** 毎日0:00 JSTに当日セッションの空き枠を検出し、条件を満たす試合に対して空き枠通知を自動送信するスケジューラを新規作成
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/SameDayVacancyScheduler.java`（新規）— 以下の処理フロー:
    1. 当日の全練習セッションを取得
    2. 各セッションの抽選実行済み判定（`LotteryExecutionRepository.existsByTargetYearAndTargetMonthAndOrganizationIdAndStatus()`）
    3. 各試合の `WON数 < 定員` かつ `WAITLISTED数 = 0` 判定
    4. 条件を満たす試合に対して `lineNotificationService.sendSameDayVacancyNotification()` を呼び出し
    5. 管理者向けに `getAdminRecipientsForSession()` で取得した管理者に `ADMIN_SAME_DAY_CANCEL` で送信
  - **参考:** 既存の `SameDayConfirmationScheduler.java` のスケジューラパターンに従う
  - **Cron式:** `0 0 0 * * *`（JST対応はアプリケーションのタイムゾーン設定に従う）
- **依存タスク:** タスク3, タスク4
- **対応Issue:** #264

---

### タスク6: フロントエンド — PlayerEdit 管理団体ドロップダウン
- [x] 完了
- **概要:** SUPER_ADMINが選手編集画面でADMINの管理団体を選択・保存できるドロップダウンを追加
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/players/PlayerEdit.jsx` — 以下の変更:
    - **import追加:** `organizationAPI` を `api/organizations.js` からインポート
    - **state追加:** `organizations`（団体一覧）, `adminOrganizationId`（選択中の団体ID）
    - **useEffect追加:** コンポーネントマウント時に `organizationAPI.getAll()` で団体一覧取得。編集時は既存プレイヤーの `adminOrganizationId` をプリセレクト
    - **UI追加（行375付近、ロールセレクタ直下）:** `role === 'ADMIN'` の場合に「管理団体」ラベル + ドロップダウン表示。「スーパー管理者専用」バッジ付き
    - **handleSubmit変更（行186-188付近）:** ロールがADMINの場合、ロール更新後に `organizationAPI.updateAdminOrganization(id, adminOrganizationId)` を呼び出し
- **依存タスク:** なし（バックエンドAPIは既存）
- **対応Issue:** #265

---

### タスク7: フロントエンド — NotificationSettings トグル追加
- [x] 完了
- **概要:** 管理者通知セクションに `adminSameDayCancel` トグルを追加
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/notifications/NotificationSettings.jsx` — 以下の変更:
    - **handleToggleAdminLinePref のデフォルト値（行383）:** `adminSameDayCancel: true` を追加
    - **管理者通知セクション（行789-796の後）:** 新トグル追加:
      ```
      ラベル: 当日キャンセル・参加・空き枠通知
      説明: 当日のキャンセル・先着参加・空き枠情報を管理者用LINEに送信
      キー: adminSameDayCancel
      ```
- **依存タスク:** タスク2（バックエンドDTO変更。UIの動作確認にはバックエンドが必要だが、コード変更は独立）
- **対応Issue:** #266

---

### タスク8: バックエンドテスト
- [x] 完了
- **概要:** 要件定義書 §6 のテスト要件に基づくユニットテスト作成
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/LineNotificationServiceTest.java`（新規 or 既存に追加）— 以下のテストケース:
    - `ADMIN_SAME_DAY_CANCEL` 送信: キャンセル時・参加時にADMIN/SUPER_ADMINに届くこと
    - `ADMIN_SAME_DAY_CONFIRMATION` 送信: WON参加者の管理者にもスキップせず届くこと
    - `ADMIN_WAITLIST_UPDATE` 送信: 該当団体のADMINにも届くこと
    - 送信先フィルタリング: ADMINは自団体のセッションのみ、SUPER_ADMINは全セッション
    - `SAME_DAY_VACANCY` 送信先: 団体メンバー全員に届くこと（セッション未登録者含む）
    - 管理者通知トグル: `organizationId=0` レコードのON/OFFが全ADMIN_系通知で正しく動作すること
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/scheduler/SameDayVacancySchedulerTest.java`（新規）— 以下のテストケース:
    - 条件判定: 抽選実行済み + WON < 定員 + WAITLISTED = 0 のときのみ通知すること
    - 条件不一致: 抽選未実行 / 定員達成 / WAITLISTED存在 で通知しないこと
    - 試合単位: 1セッション内で条件を満たす試合のみ通知すること
- **依存タスク:** タスク3, タスク4, タスク5
- **対応Issue:** #267

---

### タスク9: ドキュメント更新
- [x] 完了
- **概要:** CLAUDE.mdのドキュメント更新ルールに従い、仕様書・画面一覧・設計書を最新の状態に更新
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` — 通知仕様（ADMIN_SAME_DAY_CANCEL追加、ADMIN送信先拡張、SAME_DAY_VACANCY送信先変更、0:00空き枠通知）、通知トグル表、PlayerEdit画面の管理団体ドロップダウン
  - `docs/DESIGN.md` — バックエンド設計変更（Entity/DTO/Repository/Service/Scheduler）、フロントエンド設計変更（PlayerEdit/NotificationSettings）
  - `docs/SCREEN_LIST.md` — PlayerEdit画面の変更反映（管理団体ドロップダウン追加）
  - `docs/LINE_NOTIFICATIONS.md` — 実装後の実態と一致するよう最終確認・更新
- **依存タスク:** タスク1〜8（全実装完了後）
- **対応Issue:** #268

---

## 実装順序

```
タスク1（DB migration）─┐
                        ├→ タスク2（Entity/DTO/Repository）─┬→ タスク3（管理者通知メソッド変更）─┐
                        │                                    │                                      │
タスク6（PlayerEdit UI）│                                    └→ タスク4（VACANCY送信先拡大）────────┤
                        │                                                                           │
                        │                                    タスク7（NotificationSettings UI）      │
                        │                                                                           │
                        │                                                                           ├→ タスク5（0:00スケジューラ）
                        │                                                                           │
                        │                                                                           ├→ タスク8（テスト）
                        │                                                                           │
                        └───────────────────────────────────────────────────────────────────────────→ タスク9（ドキュメント）
```

**並行実装可能なグループ:**
- **グループA（バックエンド基盤）:** タスク1 → タスク2
- **グループB（フロントエンド、独立）:** タスク6（いつでも開始可能）、タスク7（タスク2のDTO完了後が望ましい）
- **グループC（バックエンド通知ロジック）:** タスク3, タスク4（タスク2完了後、並行可能）
- **グループD（スケジューラ）:** タスク5（タスク3, 4完了後）
- **グループE（テスト・ドキュメント）:** タスク8（タスク3-5完了後）、タスク9（全タスク完了後）
