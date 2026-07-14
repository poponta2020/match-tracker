---
status: completed
---
# 札分け確認＆LINE通知（card-division-line-reminder）実装手順書

> 技術設計の要点:
> - **札組テキストはバックエンドで一元生成**（新 `GET /api/card-division`）。画面もLINEも同一サービスを使い、JS/Java 二重実装のドリフトを防ぐ。フロントは**表示のみ**（`cardRules.js`/`kimariji.js` は変更しない）。
> - Java 側の札組導出は既存 `cardRules.js` と**バイト一致**が必須（`/pairings/summary` と同一日で食い違わせない）→ ゴールデン・パリティテストで担保。
> - 新 LINE 通知種別は**デフォルト OFF**（既存慣習と逆）。通知判定は **per-(player, org) 直接参照**（既存 `LineNotificationService` L2783 の DENSUKE パターンを反転して踏襲）。
> - 二重送信防止は `dedupeKey = sessionId`（同一セッション×プレイヤーで1回）。

## 実装タスク

### タスク1: 新LINE通知種別＋preferenceカラム（デフォルトOFF）＋マイグレーション　★main担当（本番DB適用を含む）
- [x] 完了（本番DB適用済み 2026-07-15: card_division_reminder カラム DEFAULT FALSE ＋ CHECK制約25種別）
- **目的:** 札分けリマインダー用の通知種別と per-(player, org) 購読フラグ（既定OFF）を用意する。
- **対応AC:** AC-4, AC-5
- **主な変更領域:**
  - `karuta-tracker/src/main/java/.../entity/LineMessageLog.java`（enum `LineNotificationType` に `CARD_DIVISION_REMINDER` 追加。`ADMIN_` なし＝PLAYERチャネル）
  - `.../entity/LineNotificationPreference.java`（`Boolean cardDivisionReminder`、`@Column(... columnDefinition="BOOLEAN NOT NULL DEFAULT FALSE")`、`@Builder.Default = false`）
  - `.../dto/LineNotificationPreferenceDto.java`（フィールド＋`fromEntity()`）
  - `.../service/LineNotificationService.java`（`updatePreferences` にマッピング／`isLineTypeEnabled` の switch に `case CARD_DIVISION_REMINDER -> pref.getCardDivisionReminder();`／per-org 判定ヘルパ `isCardDivisionReminderEnabled(playerId, orgId)` = `findByPlayerIdAndOrganizationId(...).map(getCardDivisionReminder).orElse(false)`）
  - `database/add_card_division_reminder_preference.sql`（`ALTER TABLE line_notification_preferences ADD COLUMN card_division_reminder BOOLEAN NOT NULL DEFAULT FALSE` ＋ `line_message_log_notification_type_check` CHECK 制約を新種別込みで張り直し。雛形: `database/add_match_video_registered_notification.sql`）→ **本番 Render PostgreSQL に psql 適用**
- **依存タスク:** なし（先行必須のバックエンド共有ホットスポット）
- **必要なテスト:** `isCardDivisionReminderEnabled` が「レコード無し＝false／`card_division_reminder=false`＝false／`true`＝true」を返す（AC-4）。preference の DTO 往復（get/update）で新フィールドが保存・取得される（AC-5）。
- **完了条件:** 上記テスト green・`./gradlew test` 通過・マイグレーション本番適用済み・`docs/design/db.md`（新カラム）と `docs/spec/notifications.md`（新種別）を同コミットで更新。
- **対応Issue:** #1046

### タスク2: 札組テキスト生成のサーバー移植＋取得API
- [x] 完了（cardRules.js の PRNG を Java 移植・ゴールデンパリティテスト green。GET /api/card-division）
- **目的:** 日付・団体（会場・試合数）から札組テキストを生成する単一のサーバーサービスと取得APIを作る。画面・LINE 双方がこれを使う。
- **対応AC:** AC-1, AC-2, AC-3
- **主な変更領域:**
  - 新規 `.../service/CardDivisionTextService.java`（`cardRules.js` の移植: FNV-1a 32bit `hashSeed`、`mulberry32`、seeded Fisher-Yates `pickRandom`、3試合サイクルの `generateCardRules`。`int`/符号なし32bitの厳密再現に注意＝`Math.imul`相当は `(int)`乗算、`>>> 0`相当は `& 0xFFFFFFFFL`）。テキスト整形（`【M/D 会場名】` の10の位0省略、`N試合目：<描画>`、抜き行のみ `番号(決まり字)抜き`）。nonce は `CardRuleNonceService.getNonce(date)`、会場名はセッション→Venue から解決。
  - 新規 `.../util/Kimariji.java`（または `resources/kimariji.json`）＝札番号1〜100→決まり字マスタ。`kimariji.js` の `KIMARIJI` を**補正値込み**で複製（041=こひ, 068=こころに, 082=おも, 100=もも）。抜き札番号は `parseInt(removedCard)||100` 相当（"00"→100）。
  - 新規 `.../controller/CardDivisionController.java`（`GET /api/card-division?date=&organizationId=`、`@RequireRole` PLAYER+。当日該当団体のセッションが無ければ空/該当なしを返す）＋必要なら `dto/CardDivisionTextDto.java`
  - 参照: `CardRuleNonceService`、`PracticeSessionRepository`（date+org でセッション取得）、`VenueRepository`（会場名）
- **依存タスク:** なし（タスク1と変更領域が重ならない＝並行可。`LineMessageLog`/`LineNotificationPreference`/`LineNotificationService` は触らない）
- **必要なテスト:**
  - **ゴールデン・パリティテスト（AC-1）**: 複数の `(date, nonce, totalMatches)` について、Java が導出する各試合の (種別, digits, removedCard) が既存 `cardRules.js` の出力と一致する。期待値は `cardRules.js` を実行して採取したフィクスチャ（もしくは既存 `cardRules.test.js` の確定値）を JUnit に埋め込む。
  - 抜き行の決まり字付与（41→`41(こひ)`, 1→`1(あきの)`, 100→`100(もも)`）＋一の位/十の位行に決まり字なし（AC-2）。
  - ヘッダ `【M/D 会場名】` の10の位0省略（7/5・10/9・12/25）（AC-3）。
- **完了条件:** 上記テスト green・`docs/spec/matching.md`（札分けテキストのサーバー生成）を同コミットで更新。
- **対応Issue:** #1047

### タスク3: 1試合目3時間前スケジューラ＋LINE送信　★scheduler/通知の高リスクパス
- [x] 完了（CardDivisionReminderScheduler＋sendCardDivisionReminder。per-orgゲート・dedupeKey=sessionId）
- **目的:** 当日セッションの1試合目開始3時間前に、購読者へ札組テキストをLINE送信する。
- **対応AC:** AC-6, AC-7, AC-8
- **主な変更領域:**
  - 新規 `.../scheduler/CardDivisionReminderScheduler.java`（`@Scheduled(fixedDelay=...) `≒5分、`zone="Asia/Tokyo"`、時刻は `JstDateTimeUtil`。雛形 `OfferExpiryScheduler`）。処理: 当日(JST)セッション抽出 → 各セッションの**1試合目開始時刻**を `VenueMatchScheduleRepository.findByVenueIdOrderByMatchNumberAsc` の `match_number=1`→無ければ `PracticeSession.startTime`→**両方無ければスキップ（AC-7）**。`now` が `[開始-3h, 開始)` に入るセッションについて、その団体の購読者（`isCardDivisionReminderEnabled(playerId, orgId)` true かつ LINE 連携済み）へ `CardDivisionTextService` のテキストを送信。
  - `.../service/LineNotificationService.java`（送信は `sendToPlayer(...)`。**per-session dedup**のため `dedupeKey=sessionId` を通す。`sendToPlayer` は dedupeKey 引数を持たないため、`existsSuccessfulSinceWithDedupeKey(playerId, CARD_DIVISION_REMINDER, dedupeKey, today.atStartOfDay())` での事前判定＋dedupeKey を書き込む送信経路を用意（既存 `tryAcquireSendRight`/dedupeKey 版オーバーロードに倣う小改修）。AC-8）
- **依存タスク:** タスク1（enum・per-org判定）、タスク2（テキスト生成）。※`LineNotificationService` をタスク1と共有するため順序制約あり（タスク1完了後）。
- **必要なテスト:** 抽出ロジック（3時間前ウィンドウ・開始時刻フォールバック・時刻データ無しスキップ AC-7）、購読者フィルタ（per-org購読ON×連携済みのみ AC-6）、二重送信されない（同一 (session, player) で dedup AC-8）。
- **完了条件:** 上記テスト green・`docs/spec/notifications.md`（スケジューラ表に追記）を同コミットで更新。
- **対応Issue:** #1048

### タスク4: 設定導線＋「札分け確認」画面（表示・コピー・LINEトグル・未連携案内）
- [x] 完了（設定グリッド導線＋/settings/card-division＋api/cardDivision.js。Vitest 5件 green・lint 0エラー）
- **目的:** 全プレイヤーが当日の札組を確認・コピーでき、練習会ごとにLINE通知を購読できるUIを追加する。
- **対応AC:** AC-9, AC-10, AC-11
- **主な変更領域:**
  - `karuta-tracker-ui/src/pages/SettingsPage.jsx`（`gridItems` に `{ label: '札分け確認', icon: <lucideアイコン>, path: '/settings/card-division', visible: true }` 追加＋アイコン import）
  - `karuta-tracker-ui/src/App.jsx`（`/settings/card-division` ルート追加。`ProtectedPage`）
  - 新規 `karuta-tracker-ui/src/pages/settings/CardDivision.jsx`（**参加練習会ごとに1ブロック**＝わすら/北大。各ブロックにその団体の当日テキストを新API取得・表示・コピー〔`PairingSummary` の textarea＋コピー流用〕。閲覧はトグル非依存・常時可。当日セッション無しは空表示。**チェックボックスと LINE オンオフは per-org トグル1本に統合**＝各ブロックの「この練習会の札分けをLINEで受け取る」トグル〔`card_division_reminder`、既定OFF〕。LINE未連携時の案内「LINE登録済みでない場合は 設定→通知設定 からLINEの友だち登録を行ってください」＋`/settings/notifications` 導線。`NotificationSettings` のトグル/連携状態パターン流用）
  - 新規 `karuta-tracker-ui/src/api/cardDivision.js`（`GET /api/card-division`）。LINEトグル/状態は既存 `api/line.js`（`getPreferences`/`updatePreferences`/`getStatus`）を流用
- **依存タスク:** タスク1（preference DTO の新フィールド）、タスク2（`/api/card-division` の契約）。※FE のため タスク3 とは変更領域が重ならず並行可。
- **必要なテスト:** 既存の Vitest 方針に従い、テキスト表示/コピー・トグル・未連携案内の主要分岐（AC-10, AC-11）を最小限カバー。導線表示（AC-9）は verify。
- **完了条件:** `npm run test`・`npm run lint` 通過・`docs/SCREEN_LIST.md`（新画面）を同コミットで更新。
- **対応Issue:** #1049

## 実装順序（Wave = 並行実装できるタスクの組）
- **Wave 1**: タスク1／タスク2（変更領域が重ならない＝並行。タスク1=LINE通知系ファイル＋DB、タスク2=新規 CardDivision 系ファイル）
- **Wave 2**: タスク3（タスク1・2 に依存）／タスク4（タスク1・2 に依存。タスク3 とは BE/FE で直交＝並行）

## 横断確認（DoD で検証）
- AC-12: 既存 `cardRules.js` を変更していない（`/pairings/summary` 不変）
- AC-13: 既存テスト（JUnit/Vitest）・lint がすべて green
- 本番DBマイグレーション適用済み（タスク1）
