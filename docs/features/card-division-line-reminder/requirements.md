---
status: completed
design_required: false
design_waived: true  # ユーザーが design-screen をスキップと明示。UIは既存パターン踏襲（§8参照）
completed_sections: [ユーザーストーリー, 機能要件, Acceptance Criteria と Non-goals, 技術的制約・契約]
next_section: —
---
# 札分け確認＆LINE通知（card-division-line-reminder）要件定義書

## 1. 概要

- **目的**: その日の練習の「札分け（各試合の出札ルール＝札組の番号）」を、全プレイヤーが (a) アプリ画面でテキスト形式でコピー取得でき、(b) 希望者は1試合目開始の3時間前にLINEで自動受信できるようにする。
- **背景・動機**: 現状の札分けは対戦組み合わせ画面（`/pairings/summary`）で ADMIN が確認・LINE手動送信しているが、一般プレイヤーが自分で当日の札組を確認する導線がない。プレイヤー自身が事前に札組を把握し準備できるようにする。

## 2. ユーザーストーリー

- **対象ユーザー**: 全プレイヤー（PLAYER 以上）。わすらもち会／北海道大学かるた会いずれのメンバーも利用する。
- **目的**: 練習前に当日の札組（各試合で使う札の番号ルール）を把握し、暗記・準備をしたい。
- **利用シナリオ**:
  - 設定画面から「札分け確認」へ入り、自分の練習会を選んで当日の札組テキストを確認・コピーする。
  - LINE通知をオンにしておき、1試合目開始の3時間前に自動で札組を受け取る。

## 3. 機能要件

### 3.1 画面と遷移（画面インベントリ＋ナビゲーション地図。見た目は design-spec 参照）

- **設定画面（`SettingsPage.jsx`, `/settings`）**: グリッドに「札分け確認」への遷移ボタンを新設（全プレイヤーに表示）。
- **新規「札分け確認」画面（新パス、例 `/settings/card-division`。PLAYER+）**: 以下を持つ。**練習会（わすら/北大）ごとに1ブロック**を表示し、各ブロックが札分けテキストと購読トグルを持つ（チェックボックスと LINE オンオフを per-org トグル1本に統合＝購読制）。
  - **表示対象の練習会**: 当該プレイヤーの参加練習会（`OrganizationSettings` の選択）。両団体所属なら両方のブロックを出す。
  - **札分けテキスト表示＋コピー**: 各練習会ブロックに、その練習会の「当日」セッションの札組をテキスト形式で表示し、クリップボードにコピーできる。**テキストの閲覧・コピーはトグル状態に依存せず常時可能**。当日セッションが無い練習会は空表示。
  - **LINE購読トグル**（`card_division_reminder`、練習会ごと・デフォルト OFF）: オンにするとその練習会の当日セッションの1試合目開始3時間前にLINEで札組を受信する（＝「札分けを取得する練習会の指定」と「LINE通知オンオフ」を1つのトグルで表現）。
  - **LINE未連携時の案内**: トグル付近に「LINE登録済みでない場合は 設定 → 通知設定 からLINEの友だち登録を行ってください」を表示し、通知設定画面（`/settings/notifications`）への導線を付ける。
  - **戻る導線**: `PageHeader backTo="/settings"`。
- 既存の `/pairings/summary`（ADMIN 中心の対戦組み合わせサマリー）は**変更しない**（別画面として併存）。

### 3.2 ビジネスルール（処理ルール・制約条件・エラーケース・例外処理）

**札組の生成ルール（既存踏襲・団体非依存）**
- 札組は「日付」＋再生成カウンタ `nonce`（`card_rule_nonce` テーブルの日付別値、既定0）から決定論的に生成する（既存 `cardRules.js` と同一アルゴリズム）。
- **同じ日なら わすら と 北大 の札組は完全に同一**。団体で変わるのは「会場名・試合数・通知時刻」のみ。
- 試合数は当該セッションの `totalMatches`（既定3）。

**テキスト形式**
```
【7/5 かでる2・7】
1試合目：一の位1.3.5.6.7
2試合目：1.4.5　41(こひ)抜き
3試合目：十の位2.4.6.8.9
```
- ヘッダ `【M/D 会場名】`: 月・日は**10の位が0なら省略**（07/05→7/5、10/09→10/9、12/25→12/25）。会場名はセッションの会場名。
- 各行 `N試合目：<札ルール>`: 札ルールは既存 `rule.description` 相当（`一の位…` / `十の位…` / `…　N抜き`）。
- **抜き札の決まり字付与**: 抜き（nuki）の行のみ、抜く札番号に決まり字を括弧書きで添える → `1.4.5　41(こひ)抜き`（41番＝こひ）。決まり字マスタは `kimariji.js` の `KIMARIJI`（1〜100、`"00"`=100番は `parseInt||100` で解決）。一の位／十の位の行には決まり字を付けない。
- **対戦ペアは載せない**（札組のみ。`/pairings/summary` とは別物）。

**画面表示ルール**
- 表示対象は「当該プレイヤーの参加練習会（`OrganizationSettings` の選択）」×「当日（JST）にセッションがあるもの」。参加練習会ごとにヘッダ付きブロックを表示する。
- 参加練習会に当日セッションが無い場合は、そのブロックは「本日は練習がありません」等の空表示（LINEも送らない）。
- 参加練習会が未設定のプレイヤーの初期表示は、当日セッションのある団体を表示する（実装時に確定）。

**LINE通知ルール**
- **対象（購読制）**: その練習会の LINE購読トグル（`card_division_reminder`）が ON かつ LINE連携済みのプレイヤーに、当該練習会の当日セッションについて送信する。**参加確定（WON等）の有無は問わない**（札組は練習会全員に共通の公開情報のため）。
- **タイミング**: セッションの1試合目開始時刻の**3時間前**（JST）。開始時刻は `venue_match_schedules`（会場×`match_number=1`）を第一情報源とし、無ければ `PracticeSession.startTime`、それも無ければ**送信しない**（画面表示は時刻に依存せず可能）。
- **デフォルト OFF**: 新規・既存プレイヤーとも既定は通知オフ（既存 LINE preference の「デフォルトON」慣習とは逆。明示実装する）。
- **本文**: 画面と同一の札組テキスト（`【M/D 会場名】` ＋各試合の札組）。
- **二重送信防止**: 同一 (セッション, プレイヤー) では1回のみ（`dedupeKey` による原子的送信権確保）。
- 複数練習会を購読し当日いずれもセッションがある場合は、練習会（セッション）ごとに別メッセージを送る。
- 月間送信上限（200通/チャネル）・連携状態確認・送信ログは既存 `LineNotificationService.sendToPlayer()` の挙動を踏襲。

## 4. Acceptance Criteria

| ID | 条件（客観的に判定できる文） | 検証手段 |
|----|------|------|
| AC-1 | サーバー側の札組導出が、同一 `(date, nonce, totalMatches)` に対して既存 `cardRules.js`（`getCardRules`→各試合の種別/digits/removedCard）の出力とゴールデン一致する。フィクスチャは `cardRules.js` を**実際に走らせて**採取し、3サイクル位置すべて・サイクル境界をまたぐ `totalMatches`（3/4/6/7）・`nonce≠0` を1件以上含む網羅的な組で用意する | auto-test |
| AC-2 | 抜き（nuki）の行に `番号(決まり字)抜き` 形式で決まり字が付与される（例: 41→`41(こひ)`、1→`1(あきの)`、100→`100(もも)`）。一の位・十の位の行には決まり字を付けない | auto-test |
| AC-3 | ヘッダが `【M/D 会場名】` 形式で、月・日の10の位が0のとき省略される（7/5・10/9・12/25 が正しい） | auto-test |
| AC-4 | 新 LINE 通知種別（札分けリマインダー）の preference が、レコード無し・新規作成いずれも既定 OFF と判定される | auto-test |
| AC-5 | 札分けLINE通知トグルの取得・更新 API が動作し、per-(player, org) で保存される | auto-test |
| AC-6 | スケジューラが「1試合目開始（`venue_match_schedules` match_number=1、無ければ `PracticeSession.startTime`）の3時間前」ウィンドウの当日セッションを抽出し、購読ON×該当練習会チェック×LINE連携済みのプレイヤーに送信対象とする | auto-test |
| AC-7 | 開始時刻が `venue_match_schedules` にも `PracticeSession.startTime` にも無いセッションは通知送信の対象にならない | auto-test |
| AC-8 | 同一 (セッション, プレイヤー) への札分け通知は二重送信されない（`dedupeKey` で1回に収束） | auto-test |
| AC-9 | 設定画面グリッドに「札分け確認」ボタンが表示され、全プレイヤーが新画面へ遷移できる | verify |
| AC-10 | 新画面で参加練習会（わすら/北大）ごとに当日の札組テキストが表示・コピーできる（閲覧はトグル非依存）。当日セッションが無い練習会は空表示になる | verify |
| AC-11 | 練習会ごとの LINE購読トグルが既定 OFF で表示され、LINE未連携時に友だち登録の案内＋通知設定への導線が表示される | verify |
| AC-12 | 既存 `/pairings/summary` の札ルール表示・LINE送信テキストが不変（`cardRules.js` を変更しない）。git 差分で `cardRules.js`／`cardRules.test.js` に変更が無いことを DoD で確認する | manual（git差分/DoD） |
| AC-13 | 既存テスト（JUnit / Vitest）・lint がすべて成功する | auto-test |

## 5. Non-goals（今回やらないこと）

- **練習会ごとに異なる札組**（nonce を (日付, 団体) で再設計すること）はしない。札組は日付で決まり団体非依存のまま。
- **対戦ペアの表示・送信**はしない（札組のみ。ペア表示は既存 `/pairings/summary`）。
- **過去日・任意日付の閲覧**はしない（当日のみ。v1）。
- **決まり字マスタの FE/BE 一元化**はしない（BE に複製し、パリティテストで一致を担保）。
- **既存 `cardRules.js`・`/pairings/summary` の改修／リファクタ**はしない（1PR=1機能）。
- **管理者による一斉送信・スケジュール設定 UI**（`/admin/line/schedule` 系）の拡張はしない。
- **LINE友だち登録フロー自体の変更**はしない（既存 `/settings/notifications` の連携フローを案内・流用）。

## 6. 技術的制約・契約

- **札組テキスト生成のサーバー移植**: LINE はサーバー送信のため、決定論PRNG（FNV-1a 32bit ハッシュ→mulberry32→seeded Fisher-Yates）・`expandRule` 相当・`KIMARIJI`・整形を Java へ移植する。移植は既存JSと独立の実装とし、**ゴールデン・クロス言語パリティテスト**（複数 date/nonce/totalMatches サンプルでJS期待値と一致）で担保する。`KIMARIJI` の補正値（041=こひ, 068=こころに, 082=おも）を正確に複製する。
- **既存 `cardRules.js` は変更しない**（`/pairings/summary` の挙動保護・ついでリファクタ禁止）。
- **DBマイグレーション（本番適用必須）**: `line_notification_preferences` に札分けリマインダー用 boolean カラム（**DEFAULT FALSE**）を追加。`LineMessageLog.LineNotificationType` の CHECK 制約（`line_message_log_notification_type_check`）を新種別で張り直す。entity 変更と同一 PR に SQL を含め、本番 Render PostgreSQL に適用する（CLAUDE.md 最重要ルール）。雛形: `database/add_match_video_registered_notification.sql`。
- **デフォルト OFF の明示実装**: 既存 LINE preference はカラム DEFAULT TRUE＋レコード無しで許可（デフォルトON）。本種別はカラム DEFAULT FALSE とし、`isEnabled`（判定 switch）でも「レコード無し＝OFF／新種別は false 基準」を明示する。
- **購読の団体スコープ**: 通知判定は per-(player, org) で行う。既存 `isNotificationEnabled` の「所属団体いずれかで ON なら送信（anyMatch）」汎用パスに依存せず、スケジューラ側でセッションの団体に対応する preference 行を org 指定で確認する。
- **通知送信**: `LineNotificationService.sendToPlayer(playerId, <新種別>, text)` を用いる（連携確認・月200通上限・送信ログを踏襲）。新種別は `ADMIN_` プレフィックスなし（PLAYER チャネル）。
- **スケジューラ**: `scheduler/` に新規クラスを追加。`OfferExpiryScheduler`（5分ポーリング＋3時間前ウィンドウ `EXPIRING_WARNING_HOURS=3`）を雛形とし、`@Scheduled(fixedDelay=…)` または数分 cron、`zone = "Asia/Tokyo"`、時刻は `JstDateTimeUtil` を使用。二重送信防止は `dedupeKey = セッションID`（`tryAcquireSendRight` の `INSERT … ON CONFLICT`）。
- **開始時刻の情報源**: `venue_match_schedules`（`findByVenueIdOrderByMatchNumberAsc` の match_number=1 の `startTime`）を第一とし、無ければ `PracticeSession.startTime`、いずれも無ければ通知スキップ。
- **エンドポイント権限**: 新規 API には `@RequireRole` を付与（札分け取得・preference は PLAYER+）。
- **運用上の制約（本番ログで検証済み）**: 早朝の通知（例 9:00 開始→6:00 送信）が成立するには、Render 無料枠のスピンダウンを外部監視（UptimeRobot による `/ping`）で回避している必要がある。**2026-07-13〜14 の本番ログで、`AdjacentRoomNotificationScheduler`（30分間隔）等が JST 深夜〜早朝（21:00 UTC = 6:00 JST を含む）まで無停止で発火していることを確認済み**＝サービスは24/7でウォーム維持されており早朝発火は実証済み。ただしこの外部監視が停止すると早朝の `@Scheduled` が落ちうる、という運用依存は残る（既知）。
- **ドキュメント更新**: 実装と同じコミットで `docs/spec/notifications.md`（新通知種別・スケジューラ）、`docs/spec/matching.md`（札分けテキスト・サーバー生成）、`docs/SCREEN_LIST.md`（新画面）、`docs/design/db.md`（新カラム）を in-place 更新する。

## 7. 設計判断の根拠

- **札組を団体非依存のまま採用**: 既存が日付シード決定論（保存最小・全端末一致）で、`/pairings/summary` と整合。団体別 nonce 化は既存機能への影響とスキーマ変更が大きく、費用対効果が悪い（ユーザー承認済み）。
- **購読制（参加者非限定）**: 札組は練習会全員に共通の公開情報であり、参加判定を通知に持ち込むと複雑化するため（ユーザー承認済み）。
- **サーバー側でテキスト生成（JS移植＋パリティテスト）**: LINE はサーバー送信で、送信時にクライアントが介在しないため。既存JSを壊さず並行実装し、ゴールデンテストでドリフトを防ぐ。
- **開始時刻は venue_match_schedules 優先**: セッション `start_time` は実データがほぼ NULL のため。「1試合目開始」の語義通り match_number=1 の時刻を用い、参加試合の最小値（iCal のロジック）は用いない。
- **デフォルト OFF**: ユーザー指定。全プレイヤー開放のため、望まない通知を送らない安全側に倒す。

## 8. デザインへの宿題（→ /design-screen card-division-line-reminder）

- 「札分け確認」画面のレイアウト（練習会チェックボックス・テキストボックス＋コピー・LINEトグル・未連携案内の配置）。既存 `NotificationSettings.jsx`（トグル/連携UI）・`OrganizationSettings.jsx`（練習会チェックボックス）・`PairingSummary.jsx`（テキストボックス＋コピー）のパターン踏襲を基本とする。
- チェックボックスの初期選択（参加練習会に一致 / 全オフ）と、当日セッションなし時の空表示文言。
- 設定グリッドの「札分け確認」ボタンのアイコン・ラベル・配置位置。
- LINEトグルと練習会チェックボックスの関係の見せ方（購読制であることが直感的に伝わるUI）。
