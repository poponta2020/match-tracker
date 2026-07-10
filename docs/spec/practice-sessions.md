# 練習日・ホーム

> **責務:** 練習日管理・出欠登録・ホーム画面（ダッシュボード）の仕様
> **関連画面:** `/`（ホーム）、`/practice`（練習日一覧・カレンダー）、`/practice/participation`（練習参加登録）、`/practice/cancel`（参加キャンセル）
> **主要実装:** `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/PracticeSessionController.java`、`.../controller/HomeController.java`、`.../service/PracticeSessionService.java`、`.../service/PracticeParticipantService.java`、`.../entity/PracticeSession.java`、`.../entity/PracticeParticipant.java`、`karuta-tracker-ui/src/pages/Home.jsx`、`karuta-tracker-ui/src/pages/practice/`

## 機能仕様

### ホーム画面（ダッシュボード）

ログイン後のメイン画面。以下の情報をまとめて表示する。

| セクション | 内容 |
|---|---|
| ナビゲーションバー | 選手名表示、ハンバーガーメニュー（プロフィール、管理メニュー、カレンダー購読、ログアウト） |
| 繰り上げオファーバナー | 未応答の繰り上げ参加通知がある場合に表示。タップで通知一覧に遷移 |
| 次の練習 | 次回参加予定の練習日・時間・会場・参加試合番号。当日の場合は `TODAY` バッジ表示 |
| 組み合わせ作成リンク | 全ロール。次の練習日の組み合わせ作成画面へのショートカット |
| 参加率TOP3（団体別） | 当月の参加率上位3名 + 自分の参加率。所属団体でフィルタリング。1団体所属時はラベルなしで1セクション、複数団体所属時は「全体→団体A→団体B」の順で複数セクション表示。**参加率 = 有効参加(WON/PENDING)試合数 ÷ 当日以前の予定試合数(Σ totalMatches)**。抜け番(非ABSENT)も参加に含むが、各セッションで予定試合数を上限にキャップするため100%を超えない（無効ステータスのCANCELLED/DECLINED/WAITLISTED/OFFERED/WAITLIST_DECLINEDは分子に含めない） |
| 次回の参加者 | 次の練習の参加者一覧（段位順ソート、自分はハイライト） |

コールドスタート対応として、3秒以上ローディングが続くと「サーバーを起動中...」メッセージを表示。
タブフォーカス復帰時にデータを自動リフレッシュ。

### 練習日管理

#### 練習日（PracticeSession）

1日の練習を表すエンティティ。同じ日付でも団体が異なれば別セッションとして登録可能（`sessionDate` + `organizationId` の複合一意制約）。

| フィールド | 型 | 必須 | 説明 |
|---|---|---|---|
| `sessionDate` | LocalDate | Yes | 練習日（団体IDとの複合一意） |
| `totalMatches` | Integer | Yes | その日の試合数 |
| `venueId` | Long | No | 会場ID |
| `notes` | Text | No | 備考 |
| `startTime` | LocalTime | No | 開始時刻 |
| `endTime` | LocalTime | No | 終了時刻 |
| `capacity` | Integer | No | 定員 |

#### 練習参加管理（PracticeParticipant）

各選手の参加登録を試合番号単位で管理する。抽選システムのステータス管理も担う。

| フィールド | 型 | 必須 | 説明 |
|---|---|---|---|
| `sessionId` | Long | Yes | 練習日ID |
| `playerId` | Long | Yes | 選手ID |
| `matchNumber` | Integer | No | 参加する試合番号（1〜7）。NULLの場合は全試合参加 |
| `status` | ParticipantStatus | Yes | 参加ステータス（デフォルト: `WON`）。`PENDING`/`WON`/`WAITLISTED`/`OFFERED`/`DECLINED`/`CANCELLED`/`WAITLIST_DECLINED` |
| `waitlistNumber` | Integer | No | キャンセル待ち番号（WAITLISTED時のみ） |
| `lotteryId` | Long | No | 紐づく抽選実行ID |
| `offeredAt` | LocalDateTime | No | 繰り上げ通知日時 |
| `offerDeadline` | LocalDateTime | No | 繰り上げ応答期限 |
| `respondedAt` | LocalDateTime | No | 繰り上げ応答日時 |
| `dirty` | Boolean | Yes, DEFAULT TRUE | アプリ側で操作済みフラグ（trueの場合、伝助への書き戻し対象） |

一意制約: `(sessionId, playerId, matchNumber)`

#### 参加キャンセル画面（/practice/cancel）

WON（抽選済みの当選）登録と PENDING（抽選前の参加申込）の両方をキャンセルできる専用ページ。カレンダー画面の「出欠登録」モーダル経由で遷移。

**フロー:**
1. キャンセル専用カレンダーが表示され、自分にキャンセル可能な参加（WON または PENDING）がある日が**抽選状態に関わらず赤系の色で統一ハイライト**（試合数を赤字で表示）
2. 日付を選択すると、その日の自分の参加試合一覧が表示される。バッジによる「当選」「申込」の区別は行わず、抽選の有無に関わらず同じUIで「第N試合」として表示
3. キャンセルしたい試合を選択（同一日内で複数選択可能）
4. キャンセル理由を選択（必須）
5. 「キャンセルする」ボタン → 確認ダイアログ（当日12:00以降の場合は「当日キャンセルとなります。補充募集が行われます。」の追加警告を表示）
6. 確定でキャンセル処理実行 → 共通コンポーネント `SaveProgressOverlay` で全画面オーバーレイ（キャンセル処理中／完了／エラー）を表示 → 完了画面の「カレンダーに戻る」ボタン押下で `/practice` に遷移する（旧仕様の `alert('キャンセル処理が完了しました')` 通知は廃止）。エラー時は「閉じる」でオーバーレイを閉じ、選択状態（試合・理由）を維持したまま画面に戻って再試行できる。Esc キー・背景クリックではオーバーレイは閉じない

**PENDING（抽選前申込）キャンセルの挙動:**
- ステータスが `PENDING` → `CANCELLED` に遷移する
- 抽選前のため、繰り上げ通知（waitlist promotion）や当日補充フローは発生しない
- 伝助への同期（`dirty=true`）は通常のキャンセル同様に行われる

**クエリパラメータ対応:** `?year=YYYY&month=M` で対象月を指定する。カレンダー画面の「出欠登録」モーダル（当月扱いの月でのみ「キャンセル登録」ボタンが表示される）からの遷移時にカレンダー表示中の年月が引き継がれる。クエリ未指定時は現在年月にフォールバック。

**月ナビゲーションの廃止:** 旧仕様では画面上部に前月／翌月ボタンと `YearMonthPicker` で月を切り替え可能だったが、対象月はクエリパラメータで固定する仕様に変更し、画面内の月送り操作は廃止した。タイトル「参加キャンセル」直下に「○年○月」を中央寄せで静的表示する。

**キャンセル理由:**
| コード | 表示名 |
|--------|--------|
| `HEALTH` | 体調不良 |
| `WORK_SCHOOL` | 仕事・学業の都合 |
| `FAMILY` | 家庭の事情 |
| `TRANSPORT` | 交通機関の問題 |
| `OTHER` | その他（自由記述入力） |

#### 当日キャンセル補充フロー

当日12:00（JST）以降にWON参加者がキャンセルした場合、全体募集＋先着ボタン方式で空き枠を即時補充するフロー。通常の繰り上げフロー（キャンセル待ち1番へのオファー）とは異なり、応答期限が短い当日は全体への一斉募集で迅速に枠を埋める。

**フェーズ1: 12:00確定フェーズ**

1. `SameDayConfirmationScheduler`（毎日12:00 JST）が `WaitlistPromotionService.expireOfferedForSameDayConfirmation()` に委譲し、当日セッションの OFFERED 参加者を一括で DECLINED に変更（dirty=true設定により伝助同期での再活性化を防止）
2. 空き枠が発生した場合、当日キャンセル補充フロー（先着ボタン方式）を自動的にトリガー
3. WON参加者全員にメンバーリストFlex Message（青ヘッダー）を送信

**フェーズ2: 当日キャンセル→補充フェーズ**

1. 12:00以降にWON参加者がキャンセルした場合:
   - WON参加者にキャンセル発生通知を送信
   - 非WON参加者（WAITLISTED等）に空き募集Flex Message（オレンジヘッダー、「参加する」ボタン付き）を送信
   - 管理者（ADMIN/SUPER_ADMIN）にもキャンセル発生通知を送信
2. 空き募集メッセージ内の「参加する」ボタンはpostback `action=same_day_join` を送信
3. **通知のセッション統合**: 同一セッション×同一プレイヤーが複数試合を同時にキャンセルした場合（伝助同期の一括反映・アプリ UI での複数選択キャンセル等）、以下の3種の通知はそれぞれ1通にまとめて送信される:
   - キャンセル発生通知: 「〇〇さんが今日の1、3試合目をキャンセルしました」のように試合番号を集約
   - 空き募集通知: セッション内の複数空き試合を1通のFlex Messageに集約（既存の0:00/12:00スケジューラと同じ形式）
   - 管理者通知: セッション単位で1通にまとまる
4. 異なるプレイヤーのキャンセルはプレイヤー単位で別々に通知される

**フェーズ3: 先着参加**

1. LINEボタンのpostback `action=same_day_join` で先着1名がWONとして登録される
2. 2人目以降はエラー（409 Conflict: 枠が既に埋まっている旨を表示）
3. 参加確定時:
   - 参加者本人に参加通知を送信
   - WON参加者全員に枠状況通知を送信（残り枠ありの場合はオレンジヘッダー＋「参加する」ボタン、枠埋まりの場合はグレーヘッダー＋ボタンなし）

**アプリ経由の参加登録通知**

12:00以降にアプリ経由（管理者の手動編集等）でWON登録された場合も、WONメンバーに通知を送信する。

**伝助同期経由の参加登録通知**

12:00以降に伝助上で○に変更され、伝助同期によりWONとして登録・昇格された場合も、枠状況通知（空き残り or 枠埋まり）を団体全メンバー（該当試合WON除く）に送信する。新規登録・WAITLISTED→WON昇格・再有効化（CANCELLED等→WON）のいずれも対象。

**LINE通知トグル（5種）**

当日キャンセル補充フローの通知は以下の独立トグルで制御される。いずれも `line_notification_preferences` テーブルで管理。管理者用トグルは `organizationId=0` レコードで判定。

| トグル | 設定キー | デフォルト | 対象 | 説明 |
|--------|---------|-----------|------|------|
| 参加者確定通知 | `same_day_confirmation` | true | 全員 | 12:00確定時のメンバーリスト送信（WON参加者向け） |
| 当日キャンセル通知 | `same_day_cancel` | true | 全員 | WON参加者へのキャンセル発生通知 |
| 空き募集通知 | `same_day_vacancy` | true | 全員 | 団体全メンバーへの空き枠募集通知（該当試合WON参加者除く） |
| 参加者確定通知（管理者用） | `admin_same_day_confirmation` | true | ADMIN/SUPER_ADMIN | WON参加者でなくてもメンバーリストを受信できる管理者専用トグル |
| 当日キャンセル・参加・空き枠通知（管理者用） | `admin_same_day_cancel` | true | ADMIN/SUPER_ADMIN | 当日のキャンセル・先着参加・空き枠情報を管理者用LINEに送信 |

**Flex Messageデザイン**

| メッセージ | ヘッダー色 | ボタン | 送信先 |
|-----------|-----------|--------|--------|
| 参加者確定（メンバーリスト） | 青 | なし | WON参加者 + 該当団体ADMIN + 全SUPER_ADMIN（管理者通知） |
| 空き募集 | オレンジ | 「参加する」 | 団体全メンバー（該当試合WON除く） |
| 残り枠あり（枠状況通知） | オレンジ | 「参加する」 | 団体全メンバー（該当試合WON除く） |
| 枠埋まり（枠状況通知） | グレー | なし | 団体全メンバー（該当試合WON除く） |

## 画面

### ホーム画面（ダッシュボード）詳細
**パス**: `/`

**表示内容**:
- **ナビゲーションバー**: 選手名、プロフィールアイコン → `/profile` に遷移
- **繰り上げオファーバナー**: 未応答の繰り上げ参加通知がある場合に表示。タップで通知一覧に遷移
- **次の練習（NEXT / TODAY）**:
  - 次回参加予定の練習日・時間・会場・参加試合番号
  - 当日の場合は `TODAY` バッジ表示、全ロールに「組み合わせを作成」ボタン
  - 参加者一覧（段位順ソート、自分はハイライト、キャンセル待ちは別セクション）
- **参加率TOP3（団体別フィルタリング）**:
  - 当月の参加率上位3名 + 自分がTOP3に入っていない場合は自分の参加率も表示
  - ユーザーの所属団体（organization）でフィルタリング
  - **1団体所属時**: 団体名ラベルなしで1セクション
  - **複数団体所属時**: 「全体」（全所属団体合算）→ 団体A → 団体B の順で複数セクション表示
  - レスポンス構造: `participationGroups` 配列（各要素に `organizationId`, `organizationName`, `top3`, `myRate`）
  - **参加率の算出**: 分子は有効参加（`WON`/`PENDING`）の試合数のみ（`CANCELLED`/`DECLINED`/`WAITLISTED`/`OFFERED`/`WAITLIST_DECLINED` は除外）。抜け番（非ABSENT、`matchNumber=null`）も参加に含むが、各セッションで予定試合数（`totalMatches`）を上限にキャップしてから合算するため参加率は100%を超えない。分母は当日以前の各セッションの `totalMatches` 合計。算出ロジックは `PracticeParticipantService.buildParticipationRates`

**データフロー**:
1. `GET /api/home?playerId={playerId}` - ホーム画面統合APIで全データを1リクエストで取得
   - 次の練習情報（`nextPractice`）
   - 参加率グループ（`participationGroups`）: 所属団体に応じて団体別・全体の参加率を返す
   - 未読通知数（`unreadNotificationCount`）
   - 繰り上げオファー有無（`hasPendingOffer`）
2. 画面フォーカス時に自動再取得（`window.addEventListener('focus')`）

**ナビゲーション**:
- 繰り上げオファーバナー → `/notifications`
- 「参加登録」リンク → `/practice/participation`
- 「組み合わせを作成」ボタン（全ロール、当日のみ） → `/pairings?date={sessionDate}`

### 練習日一覧（カレンダー形式）
**パス**: `/practice`

**表示内容**:
- **月選択（前月/次月ボタン）**
- **カレンダーグリッド（日曜～土曜）**
- **参加状況バッジ**
  - ●（緑色）: 全試合参加
  - ◐（黄色）: 部分参加（一部の試合のみ）
  - バッジなし: 未参加
- **各日付に練習日がある場合、背景色変更＋場所名省略表示**
- **今日の日付をハイライト**
- **出欠登録ボタン（右下フローティング、過去月は非表示）**: 押下で `AttendanceRegisterModal` を開く。モーダル内で「参加登録」「キャンセル登録」を選択し、それぞれ `?year=YYYY&month=M` 付きで該当ページへ遷移。旧仕様の「参加登録」「参加キャンセル」2フローティング構成は本モーダル統合で「出欠登録」1ボタンに集約された。**カレンダー表示月の判定により FAB の表示／モーダル内のボタン構成が変わる**: 過去月（表示月 < 現在年月）→ FAB 非表示／当月扱い（現在年月、または未来月で抽選確定済みセッションが1つ以上）→ FAB 表示・モーダルに「参加登録」「キャンセル登録」両方表示／来月扱い（未来月で抽選確定済みセッションが0個）→ FAB 表示・モーダルに「参加登録」のみ表示。判定は `pages/practice/utils/attendanceMode.js` の `resolveAttendanceMode` ヘルパーで共通化され、データソースは月変更時に既に取得している `practiceAPI.getPlayerParticipationStatus(playerId, year, month)` のレスポンス内 `lotteryExecuted: { sessionId → boolean }`
- **日付クリック→モーダルポップアップ**

**モーダル内容**:
- 日付（曜日付き）
- 場所
- 試合別参加者（アコーディオン形式）
  - ▶ X試合目 (Y名) ← クリックで展開
  - ▼ X試合目 (Y名) ← 展開時
    - 自分が参加する試合は緑色でハイライト
    - 参加者リスト（箇条書き）
- 備考
- 出欠登録ボタン（選択セッション詳細モーダル内、過去日でない場合のみ表示）: 押下で `AttendanceRegisterModal` を開き、参加登録 / キャンセル登録のいずれにも年月を引き継いで遷移可能
- 編集・削除ボタン（SUPER_ADMIN のみ表示）

**`openToday` パラメータによる自動ポップアップ（LINEリッチメニュー導線）**:
- `/practice?openToday=true` でアクセスすると、当日の練習セッションがあればモーダルポップアップを自動表示する
- パラメータ処理後、`openToday` はURLから除去される（`setSearchParams` で `replace: true`、ブラウザ履歴に残さない）
- 処理済みフラグ（`useRef`）で重複表示を防止
- 未ログイン時は `PrivateRoute` が `/login` へリダイレクト（`location` を `state.from` に保持）し、ログイン成功後に元URL（`/practice?openToday=true`）へ復帰する

**データフロー**:
1. `practiceAPI.getAll()` で全セッション取得
2. `practiceAPI.getPlayerParticipations(currentPlayer.id, year, month)` で自分の参加状況取得
3. セッションごとに参加状況を判定（全試合/部分/未参加）
4. カレンダーセルにバッジ表示
5. モーダル内でアコーディオンを緑色表示

## フロー

### 練習参加登録フロー

```
[ユーザー操作]
1. カレンダー画面（/practice）の「出欠登録ボタン」（右下フローティング、または選択セッション詳細モーダル内）をクリック
   → 出欠登録モーダル（AttendanceRegisterModal）が開く
   → モーダル内「参加登録」を選択
   → カレンダー表示中の年月を引き継いで /practice/participation?year=YYYY&month=M へ移動
   ↓
2. 年月選択（例: 2026年3月）※カレンダーから遷移時は自動設定
   ↓
[フロントエンド]
3. GET /api/practice-sessions/year-month?year=2026&month=3
   ← 練習日一覧（id, sessionDate, venueId, totalMatches）
   ↓
4. GET /api/practice-sessions/participations/player/{playerId}?year=2026&month=3
   ← 既存参加状況 {sessionId: [matchNumbers]}
   ↓
5. テーブル表示
   - 各練習日に試合番号（1～totalMatches）のチェックボックス
   - 既存参加状況を反映してチェック済み
   ↓
[ユーザー操作]
6. チェックボックス操作（試合ごとに参加/不参加選択）
   ↓
7. 「保存する」ボタンクリック
   ↓
[フロントエンド]
8. POST /api/practice-sessions/participations
   ↓
[バックエンド: PracticeSessionService]
9. その月の全セッションIDを取得
   ↓
10. 既存参加記録を一括削除
   ↓
11. 新規参加記録を一括登録
   ↓
12. レスポンス: 201 Created
   ↓
[フロントエンド]
13. SaveProgressOverlay を success 状態へ切替え（「参加登録を保存しました」「カレンダーに戻る」ボタン） → ユーザーが「カレンダーに戻る」を押下 → /practice へ遷移
```

## API

### 練習日 (`/api/practice-sessions`)

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `/{id}` | ALL | ID指定で取得 |
| GET | `/date?date=` | ALL | 日付指定で取得（参加者込み） |
| GET | `/year-month?year=&month=` | ALL | 年月指定取得（ログインユーザーの参加団体でフィルタ） |
| GET | `/year-month/summary?year=&month=` | ALL | 年月サマリー（軽量・参加団体フィルタ） |
| GET | `/next-participation?playerId=` | ALL | 次の参加予定（参加団体フィルタ） |
| GET | `/dates?fromDate=` | ALL | 日付一覧（軽量・参加団体フィルタ） |
| GET | `/exists?date=` | ALL | 日付存在確認 |
| GET | `/{id}/participants` | ALL | 参加者一覧 |
| GET | `/participations/player/{id}?year=&month=` | ALL | 月別参加状況（アクティブな参加レコードのみ返す。`CANCELLED`/`DECLINED`/`WAITLIST_DECLINED` は除外し、キャンセル後の再登録判定に使える）。ステータス詳細やキャンセル済み状態を含めて確認したい場合は `/participations/player/{id}/status` を使う |
| GET | `/participations/player/{id}/status?year=&month=` | ALL | 月別参加状況（抽選ステータス付き）。レスポンスに `beforeDeadline: boolean`（締切前かどうか）を含む。フロントエンドはこの値を使って締切後の既存登録チェックボックスを disabled 化する |
| POST | `/` | SUPER_ADMIN | セッション作成 |
| PUT | `/{id}` | SUPER_ADMIN | セッション更新 |
| PUT | `/{id}/total-matches?totalMatches=` | SUPER_ADMIN | 試合数変更 |
| DELETE | `/{id}` | SUPER_ADMIN | セッション削除 |
| POST | `/participations` | ALL | 出欠一括登録 |
| PUT | `/{sid}/matches/{num}/participants` | SUPER_ADMIN | 試合別参加者設定 |
| POST | `/date/{date}/matches/{num}/participants/{pid}` | PLAYER+ | 参加者追加（ADMIN/PLAYERは自/所属団体のみ — `checkScopeByDate`） |
| DELETE | `/{sid}/matches/{num}/participants/{pid}` | ADMIN+ | 参加者削除 |
| POST | `/{id}/confirm-reservation` | ADMIN+ | 隣室予約完了を記録（`reservation_confirmed_at` をセット） |
| POST | `/{id}/expand-venue` | ADMIN+ | 会場を隣室と合わせた大部屋に拡張（予約確認済みが前提）。拡張時に WAITLISTED を waitlist_number 昇順で **OFFERED（期限付き・要承諾）** に昇格し昇格者へオファー通知（アプリ内＋LINE）を送信。新定員に収まらない超過分は WAITLISTED のまま据え置き。**auto-confirm・既存 OFFERED の応答期限一律クリアは廃止（B-1）**。応答期限が既に過ぎている場合は昇格しない。練習編集 (`PUT /api/practice-sessions/{id}`) で capacity を増やした場合も同じ昇格処理を実行 |

#### POST /api/practice-sessions
**説明**: 練習日作成
**権限**: SUPER_ADMIN
**リクエスト**: `PracticeSessionCreateRequest`
```json
{
  "sessionDate": "2025-11-20",
  "totalMatches": 7,
  "venueId": 1,
  "notes": "備考",
  "startTime": "13:00",
  "endTime": "17:00",
  "capacity": 20,
  "participantIds": [1, 2, 3]
}
```

**特記事項**:
- `participantIds` で指定された参加者は全試合に自動参加
- 例: 3名の参加者、7試合 → 21レコード作成（3 × 7）
- ユーザーは後から参加登録画面で試合を調整可能

#### GET /api/practice-sessions
**説明**: 全練習日取得（降順）
**権限**: なし
**レスポンス**: `List<PracticeSessionDto>`
```json
[
  {
    "id": 10,
    "sessionDate": "2025-11-20",
    "totalMatches": 7,
    "venueId": 1,
    "participantCount": 15,
    "completedMatches": 3,
    "matchParticipantCounts": {
      "1": 10,
      "2": 12,
      "3": 8
    },
    "matchParticipants": {
      "1": ["田中太郎", "佐藤花子", ...],
      "2": [...],
      ...
    }
  }
]
```

#### GET /api/practice-sessions/year-month?year={year}&month={month}
**説明**: 年月別練習日取得
**権限**: なし

#### GET /api/practice-sessions/year-month/summary?year={year}&month={month}
**説明**: 年月別練習日サマリー取得（カレンダー画面向け軽量レスポンス）
**権限**: なし

**特記事項**:
- 参加者詳細（試合別参加者リスト、ランク・ロール）まではエンリッチせず、会場名と試合別定員到達状況のみ付与
- 認証済みユーザーがある場合（リクエスト属性 `currentUserId`）は当該プレイヤーの所属団体で絞り込む。`playerId` クエリパラメータは受け付けない
- 月内全セッションの参加者を一括取得して集計するため N+1 にならない

**レスポンス**: `List<PracticeSessionDto>`（サマリー版）

| フィールド | 型 | 説明 |
|-----------|-----|------|
| `id` | Long | 練習セッションID |
| `sessionDate` | LocalDate | 練習日 |
| `totalMatches` | Integer | 総試合数 |
| `venueId` | Long | 会場ID |
| `venueName` | String | 会場名（サマリーAPIで付与） |
| `capacity` | Integer | 定員（試合別の上限人数） |
| `organizationId` | Long | 団体ID |
| `matchCapacityStatuses` | `List<String enum>` | 試合単位の定員到達状況。要素 enum は `AVAILABLE` / `NEARLY_FULL` / `FULL`。`matchCapacityStatuses[i]` が第 `(i+1)` 試合の状態。長さは `min(totalMatches, 9)`。**サマリーAPIのみで返却**され、他のエンドポイント（`getById` / `getByDate` 等）では設定されない。算出不可時は `null` |

**`matchCapacityStatuses` の判定ロジック:**

- 判定に使う `capacity` は `session.capacity` を優先し、`session.capacity == null` のときは紐づく `venue.capacity`（venue 既定 capacity）にフォールバックする。これにより、伝助同期で作成された capacity 未設定セッションでもサマリーAPIがグリッドを返せるようになる（`LotteryService` の `processSession` 既存フォールバックと同じ思想で、表示側でも防御を二重化）。
- 算出スキップ（`matchCapacityStatuses = null`）の条件:
  - フォールバック適用後の有効 `capacity` が `null` または `0` 以下（`session.capacity == null` の場合のみ `venue.capacity` にフォールバックする。`session.capacity = 0` のような明示値は venue 既定値で上書きせず、そのままスキップ判定に流す）
  - `totalMatches == null || totalMatches <= 0 || totalMatches >= 10`
  - 集計中に例外発生
- 上記以外: 試合番号 1〜`totalMatches` の各試合について以下を算出:
  - 実質枠取得人数 `effectiveCount` = `COUNT(WON) + COUNT(PENDING) + COUNT(OFFERED)`（試合番号別）
    - `WAITLISTED` / `DECLINED` / `CANCELLED` / `WAITLIST_DECLINED` はカウントに含めない
  - 残り席数 `remaining` = `capacity - effectiveCount`
  - 状態判定:
    1. `effectiveCount >= capacity` （= `remaining <= 0`）→ `FULL`
    2. `0 < remaining <= 2` → `NEARLY_FULL`
    3. それ以外（= `remaining > 2`）→ `AVAILABLE`

#### PUT /api/practice-sessions/{id}
**説明**: 練習日更新
**権限**: SUPER_ADMIN

**特記事項**:
- 更新時も `participantIds` で指定された参加者は全試合に自動参加
- 既存の参加記録は削除され、新しい参加記録が作成される

#### DELETE /api/practice-sessions/{id}
**説明**: 練習日削除
**権限**: SUPER_ADMIN

### 練習参加登録

#### POST /api/practice-sessions/participations
**説明**: 一括参加登録（月単位）
**権限**: PLAYER: 自分のみ / ADMIN, SUPER_ADMIN: 全選手
**リクエスト**: `PracticeParticipationRequest`
```json
{
  "playerId": 1,
  "year": 2025,
  "month": 11,
  "participations": [
    {"sessionId": 10, "matchNumber": 1},
    {"sessionId": 10, "matchNumber": 2},
    {"sessionId": 11, "matchNumber": 1}
  ]
}
```

#### GET /api/practice-sessions/participations/player/{playerId}?year={year}&month={month}
**説明**: 月別参加状況取得（アクティブな参加レコードのみ。`CANCELLED`/`DECLINED`/`WAITLIST_DECLINED` は除外し、キャンセル後の再登録判定に使えるようにする）
**権限**: なし
**レスポンス**:
```json
{
  "10": [1, 2],
  "11": [1]
}
```
