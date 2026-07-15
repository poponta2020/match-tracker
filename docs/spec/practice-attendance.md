# 出欠登録

> **責務:** カレンダー画面（`/practice`）の出欠登録モーダル・試合別ステータスグリッド・記号凡例、練習参加登録画面（`/practice/participation`）、および1日分の出欠登録画面（`/practice/attendance`）の仕様
> **関連画面:** `/practice`（出欠登録モーダル・試合別ステータスグリッド）、`/practice/participation`（月まとめ参加登録）、`/practice/attendance`（1日分の参加＋理由付きキャンセル）
> **主要実装:** `karuta-tracker-ui/src/components/AttendanceRegisterModal.jsx`、`karuta-tracker-ui/src/components/SaveProgressOverlay.jsx`、`karuta-tracker-ui/src/pages/practice/PracticeList.jsx`、`karuta-tracker-ui/src/pages/practice/PracticeParticipation.jsx`、`karuta-tracker-ui/src/pages/practice/PracticeSessionAttendance.jsx`、`karuta-tracker-ui/src/pages/practice/utils/attendanceMode.js`、`karuta-tracker-ui/src/pages/practice/utils/attendanceScreen.js`、`karuta-tracker-ui/src/pages/practice/utils/sameDayConfirm.js`、`karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeSessionService.java`（`findSessionSummariesByYearMonth`）（練習日エンティティ・API・練習日管理全般は docs/spec/practice-sessions.md 参照）

## 機能仕様

### 出欠登録画面

- 月単位のカレンダー表示で練習日を一覧
- 各練習日の各試合番号ごとに参加チェックを切り替え
- 月をまたぐナビゲーション機能
- 一括保存
- **保存進捗オーバーレイ**: 保存ボタン押下時は共通コンポーネント `SaveProgressOverlay` で全画面オーバーレイ（保存中／完了／エラー）を表示する。完了画面で「カレンダーに戻る」ボタンを押下すると `/practice` に遷移する（旧仕様の1秒タイマー自動遷移は廃止）。エラー時は「閉じる」を押下するとオーバーレイが閉じ、編集中のチェック状態を維持したまま元の画面に戻って再試行できる。Esc キー・背景クリックではオーバーレイは閉じない。SAME_DAY 当日12:00以降の確認ダイアログは現状維持で、ダイアログで「はい」が押された後にオーバーレイが表示される
- **抽選済みセッション**: チェックボックスの代わりにステータスバッジ（当選/待ち/応答待 等）を表示（キャンセルは専用ページから実施）
- **クエリパラメータ対応**: `?year=YYYY&month=M` で初期表示月を指定可能。カレンダー画面の「出欠登録」モーダルからの遷移時はカレンダー表示中の年月が引き継がれる。不正値時は現在月にフォールバック

#### 出欠登録モーダル（カレンダー画面の入口）

カレンダー画面（`/practice`）右下フローティングの「**出欠一括登録**」ボタン押下時に表示されるモーダル。`AttendanceRegisterModal` コンポーネント。月まとめの参加登録・キャンセル導線の入口（1日分の出欠は #18-2 の `/practice/attendance` を参照）。

- **タイトル:** 「出欠登録」
- **サブテキスト:** 「YYYY年M月の出欠登録を行います。」（カレンダー表示中の年月を明示）
- **ボタン:**
  - 「参加登録」: `/practice/participation?year=YYYY&month=M` へ遷移してモーダルを閉じる（常に表示）
  - 「キャンセル登録」: `/practice/cancel?year=YYYY&month=M` へ遷移してモーダルを閉じる。**カレンダー表示月が「当月扱い」のときのみ表示**し、「来月扱い」のときは非表示（後述の判定ルール参照）
  - 「閉じる」: 遷移せずモーダルを閉じる
- **配置:** カレンダー画面の右下フローティング「**出欠一括登録**」ボタン（**過去月のときは非表示**）から開く。**選択セッション詳細部のインライン「出欠登録」ボタン（過去日でない場合のみ表示）は本モーダルを経由せず、1日分の出欠登録画面（`/practice/attendance?sessionId=<id>`）へ直接遷移する**
- **「当月扱い／来月扱い／過去月」の判定（共通ヘルパー `resolveAttendanceMode`）:**
  - 表示月 < 現在年月 → **過去月**（FAB・キャンセル登録ボタンともに非表示）
  - 表示月 == 現在年月 → **当月扱い**（両ボタン表示）
  - 表示月 > 現在年月 で月内セッションに抽選確定済み（`PlayerParticipationStatusDto.lotteryExecuted` がいずれかのセッションで `true`）が **1つでもある** → **当月扱い**（両ボタン表示）
  - 表示月 > 現在年月 で抽選確定済みセッションが0個 → **来月扱い**（「参加登録」のみ表示、「キャンセル登録」は非表示）
  - 判定単位は「月単位」。個別セッションのロック判定は各画面で別途行う
- **背景:** 旧仕様では右下「参加登録」と左下「参加キャンセル」の2フローティングボタンが分離していたが、本モーダルへの統合により単一エントリーポイント化された。左下のキャンセル専用フローティングは廃止

#### カレンダー画面のセル表示（試合別ステータスグリッド）

カレンダー画面（`/practice`）の各セルでは、日付・会場名に加えて**試合別ステータスグリッド**を表示し、どの試合に空きがあるかをセルをタップしなくても一目で把握できるようにする。

- **対象画面:** `PracticeList.jsx` のカレンダー本体
- **配置:** セル内の会場名の下に、十分な余白を空けて中央寄せで配置（小さな記号グリッド）
- **セル高さ:** 既存の `h-20`（80px）を維持
- **表示対象日:** 過去日も含めて全日付で判定・表示する

**グリッド仕様:**

| 状態（`CapacityStatus`） | 記号 | 配色 |
|--------------------------|------|------|
| `AVAILABLE`（空きあり） | `○` | 緑（`text-green-400`） |
| `NEARLY_FULL`（残り席数 1〜2） | `△` | オレンジ（`text-orange-300`） |
| `FULL`（満員） | `×` | 赤（`text-red-400`） |

- 記号は小さい文字（`text-[9px]` 程度）・`font-bold`
- グリッドは **3列固定**（`grid-cols-3`）、最大3行 = 最大9試合
- 試合番号順に **左詰め・行優先（row-major）** で配置:
  - 例: 3試合 → 行1: `○ ○ ×`
  - 例: 4試合 → 行1: `○ ○ ○`、行2: `×`（左端、空きスロットは詰めない）
  - 例: 7試合 → 行1: `○ ○ ○`、行2: `△ △ ×`、行3: `×`
- グリッドはセル内で **水平方向は中央寄せ**（`w-fit mx-auto`、行内は左詰め）

**判定ロジック（バックエンドの `findSessionSummariesByYearMonth` で算出）:**

各試合について以下を計算:
- 判定に使う `capacity` は `session.capacity` を優先し、`session.capacity == null` のときは紐づく `venue.capacity`（venue 既定 capacity）にフォールバックする。両方 `null` の場合は判定を行わず `matchCapacityStatuses = null` を返す（グリッド非表示）。
- 実質枠取得人数 `effectiveCount` = `COUNT(WON) + COUNT(PENDING) + COUNT(OFFERED)`（試合番号別）
  - `WAITLISTED` / `DECLINED` / `CANCELLED` / `WAITLIST_DECLINED` はカウントに含めない
  - `PENDING` を含めるのは抽選なし運用（`pairingIncludesPending = true`）でも「実質枠を取っている」とみなすため
  - `OFFERED`（繰り上げ通知応答待ち）は名目上枠を確保しているのでカウントに含める
- 残り席数 `remaining` = `capacity - effectiveCount`
- 各試合の状態判定（優先順位順）:
  1. `effectiveCount >= capacity` （= `remaining <= 0`） → `FULL`
  2. `0 < remaining <= 2` → `NEARLY_FULL`
  3. それ以外（= `remaining > 2`） → `AVAILABLE`

判定対象は試合番号 1 〜 `min(totalMatches, 9)`。参加者ゼロの試合は `effectiveCount = 0` で扱い、capacity が設定されていれば残り席数 = capacity となるため通常は `AVAILABLE`。

**グリッドを描画しない条件:**

以下のいずれかに該当する場合、グリッドそのものを描画しない（会場名のみのセルになる）:

1. その日に練習セッションが **2件以上** ある（同日複数団体）
2. 判定に使う有効 capacity が `null` または `0` 以下（有効 capacity は `session.capacity` を優先し、`session.capacity == null` の場合のみ `venue.capacity` にフォールバック。`session.capacity = 0` のように明示値が `0` 以下のときは venue 既定値にはフォールバックせずそのまま非表示）
3. セッションの `totalMatches` が `null` または `0` 以下
4. `matchCapacityStatuses` が `null`（バックエンド側で算出不可だった場合）
5. `totalMatches >= 10`（3×3 グリッドに収まらない）
6. `matchCapacityStatuses` に既知 enum 値以外の不正値が混入

**同日複数セッションの扱い:**

- 同一日に複数団体のセッションがある場合はグリッドを描画しない（ごちゃつき回避）
- 会場名は従来通り全セッション分を縦に並べて表示

**参加状況背景色との関係:**

- 既存の参加状況による背景色（`confirmed` = 薄い緑系 `bg-[#e8efea]` / `waitlisted` = 薄い黄系 `bg-[#fefcf5]`）は保持するが、グリッド記号が読みやすいよう既存より一段薄くしてある
- 罫線（`border-2 border-[#a3c4ad]` 等）は維持

**防御的挙動:**

- フロントエンドは `matchCapacityStatuses` が `null` / 配列長0 / 不正値（既知 enum 値以外）混入のとき、グリッドを描画しない
- バックエンドの集計でエラーが起きた場合も `matchCapacityStatuses = null` にフォールバックし、カレンダー表示を阻害しない

**API対応:**

- 本フィールドはサマリーAPI（`GET /api/practice-sessions/year-month/summary`）のレスポンスでのみ返却される（API詳細は docs/spec/practice-sessions.md 参照）
- `getById` / `getByDate` などの詳細APIには現状返さない（必要になれば別途追加）

#### カレンダー画面の記号の凡例（ⓘ 記号の見方）

新しく入ったメンバーが記号（○△×）やセル背景色の意味を迷わず読み取れるよう、カレンダー画面（`/practice`）に **記号の凡例** を提供する（UXライティング導入 第1弾）。凡例は **表示専用** で、データ変更・API呼び出しは一切行わない。

- **対象画面:** `PracticeList.jsx`（フロントのみ。バックエンド・DB・マイグレーションの変更なし）
- **アフォーダンス:** カレンダーテーブル直上・右寄せに「ⓘ 記号の見方」ボタン（`lucide-react` の `Info` アイコン＋テキスト、`aria-label="記号の見方を開く"`）を配置。セッションの有無・月の切り替えに関わらず **常時表示**
- **パネル方式:** ボタンにアンカーしたドロップダウン（既存 `YearMonthPicker` と同方式）。**パネル外タップ・再タップ・閉じるボタン（×）** で閉じる
- **初回自動表示:** この画面を初めて開いた端末では、表示時に凡例パネルを **自動で開く**。`localStorage` キー `practiceCalendarLegendSeen` で既読管理し、値があれば以降は自動表示しない（**端末単位**。`localStorage` 例外時は try/catch でガードし、最悪「毎回開く」挙動になっても機能を阻害しない）

**凡例の内容（2グループ＋補足）:**

凡例はカレンダーセルに重なる2種類の情報（試合共通の空き状況／個人ごとの参加状況）を明確に分離して説明する。配色はカレンダー本体と一致させる。

| グループ | 記号/スウォッチ | 意味 | 配色 |
|----------|-----------------|------|------|
| 試合の空き状況（試合ごと） | `○` | 空きあり | 緑（`text-green-400`） |
| 〃 | `△` | 残りわずか | オレンジ（`text-orange-300`） |
| 〃 | `×` | 満員 | 赤（`text-red-400`） |
| あなたの参加状況 | ■ スウォッチ | 参加確定 | 緑系（セル背景 `bg-[#e8efea]` / `border-[#a3c4ad]` と同色） |
| 〃 | ■ スウォッチ | キャンセル待ち | 黄系（セル背景 `bg-[#fefcf5]` / `border-[#e8d48b]` と同色） |

- 「試合の空き状況」グループには「（試合ごと）」「※試合ごとの空き状況です」の補足を添える
- ○△× の意味は既存の判定ロジック（`matchCapacityStatuses`、カレンダー画面のセル表示（試合別ステータスグリッド） 参照）を文章化するのみで、判定ロジック自体は変更しない

## 画面

### 練習参加登録
**パス**: `/practice/participation`

**表示内容**:
- **年月ナビゲーション**: 固定ヘッダーに左右矢印ボタン（ChevronLeft / ChevronRight）で月を切り替え。カレンダー画面の出欠登録モーダルから遷移時は `?year=YYYY&month=M` クエリパラメータで初期表示月が引き継がれる（不正値時は現在月にフォールバック、後方互換あり）
- **練習日一覧（テーブル形式）**
  - 日付列
  - 場所列
  - 団体名バッジ: 各セッションの所属団体を略称（例: わすら、北大）で色付きバッジ表示。色は団体の `color` フィールドから取得
  - 試合番号チェックボックス列（1～7）
    - 既存参加状況を反映してチェック済み
    - チェックボックス周囲のラベル領域もタップ対象にし、スマホ幅でも選択しやすいタップ領域を確保
    - **参加人数バッジ**: 各試合の参加者数を表示。定員に対する割合で色分け（赤: 80%以上、橙: 60%、黄: 40%、緑: 40%未満）
  - **抽選ステータス表示**（抽選実行済みセッション）: チェックボックスの代わりにステータスバッジを表示
    - WON（緑）、WAITLISTED（黄・番号付き）、OFFERED（青）、PENDING（灰）、DECLINED/CANCELLED（灰）
  - **既存登録チェック外しの可否（「当月扱い／来月扱い」で切り替え）**: `resolveAttendanceMode(year, month, lotteryExecuted)` の結果に応じて挙動を切り替える
    - **当月扱い**（現在年月、または未来月で抽選確定済みセッションが1つ以上ある月）: 既存登録（`initialParticipations` に含まれる試合）のチェックボックスを一律無効化（解除不可）。既存登録のキャンセルはキャンセル画面（`/practice/cancel`）の理由付きキャンセルに誘導する
    - **来月扱い**（未来月で抽選確定済みセッションが0個の月）: 既存登録もチェックを外せる（API上は未登録に戻す＝理由なしキャンセル）。`registerParticipations` 保存時に差分が反映される
    - **抽選実行済みセッション**: 当月扱い／来月扱いに関わらず全チェックボックスが操作不可（ステータスバッジ表示）
    - **締切後**: 既存仕様どおり、`beforeDeadline=false` のとき既存登録は disabled（来月扱いの月でも維持）
- **SAME_DAYタイプ確認ダイアログ**: SAME_DAYタイプの団体で当日12:00以降、かつ「当日のセッション」の参加状態に初期値からの実際の差分があるときのみ、管理者への連絡確認ダイアログを表示する。同月内の別日セッションだけを変更した保存では当日セッションは触られないためダイアログは出さない。判定ロジックは `karuta-tracker-ui/src/pages/practice/utils/sameDayConfirm.js` の `needsSameDayConfirm` に切り出され、単体テストでカバー
- **保存ボタン**: 押下後、API 呼び出し直前から共通コンポーネント `SaveProgressOverlay` で全画面オーバーレイ（保存中／完了／エラー）を表示する。完了画面の「カレンダーに戻る」ボタンを押下したときのみ `/practice` に遷移する（旧仕様の1秒タイマー自動遷移は廃止）。エラー時は「閉じる」で編集中のチェック状態を維持したまま元画面に戻り再試行できる。Esc キー・背景クリックではオーバーレイは閉じない。同じオーバーレイは PracticeCancelPage のキャンセル実行フロー（旧 `alert` 通知の置き換え）にも使用される

**データフロー**:
1. ページ読み込み時に以下のAPIを並列取得:
   - `GET /api/practice-sessions/year-month` — 月内の練習セッション一覧
   - `GET /api/practice-sessions/participations/player/{playerId}` — 既存参加登録
   - `GET /api/practice-sessions/participations/player/{playerId}/status` — 抽選・締切ステータス（参加状況詳細、抽選実行有無、締切前後）
   - `GET /api/lottery/deadline` — 締切情報の表示用
   - `GET /api/organizations` — 団体名・色・締切タイプ
2. 取得データをもとにテーブルを描画（チェックボックス or ステータスバッジ）
3. チェックボックス操作
4. 保存ボタン → API 呼び出し直前で `SaveProgressOverlay` を `saving` 状態に切替え → `POST /api/practice-sessions/participations`（PLAYERロールは自分のplayerIdのみ操作可能）
5. 成功時: オーバーレイを `success` 状態に切替え（「参加登録を保存しました」と「カレンダーに戻る」ボタン）。失敗時: `error` 状態に切替え、サーバーからのエラーメッセージ（`err.response?.data?.message`）を表示
6. ユーザーが「カレンダーに戻る」を押下 → `/practice`（カレンダー画面）へ遷移（エラー時は「閉じる」で編集中のチェック状態を保持したまま画面に戻り再試行可）
7. カレンダー画面で自動的にデータ再取得

### 1日分の出欠登録
**パス**: `/practice/attendance?sessionId=<セッションID>`（静的セグメントのため React Router v6 のランキングで `/practice/:id` より優先。ガードは全ロール）

カレンダー（`/practice`）のセッション詳細ポップアップ「出欠登録」ボタンから遷移する、**押した日付のそのセッション1件**に閉じた出欠画面。月まとめの `/practice/participation`・`/practice/cancel` の挙動 union を1セッションにスコープして再構成したもの（新規API・DB・マイグレーションなし。既存 `registerParticipations` / `cancelMultiple` を流用）。同日に複数団体のセッションがあってもポップアップで開いた1件のみを対象とする。

**表示内容**:
- **上部バー（緑）**: ← 戻る（`/practice`）＋「M/D(曜) 会場名」。直下に団体カラーのドット＋団体名（`org.color`）。
- **参加/キャンセルの排他振り分け**（純関数 `resolveAttendanceSections`。各試合は最大1セクションにのみ現れる）:
  - **参加する試合**（未参加・抽選前・伝助削除でない試合）: 各行「第N試合／時間／人数 or 満員ラベル／チェック」。末尾に「満員でも申込できます（キャンセル待ちになる場合あり）」注記＋「参加を保存」。
  - **参加をキャンセル**（WON / PENDING。**当月扱いのみ**）: 試合選択→理由（体調不良／仕事・学業／家庭／交通機関／その他。`その他`＝自由記述必須）→「選択した試合をキャンセル」（実行前 `window.confirm`）。
  - **読み取り専用**（WAITLISTED / OFFERED 等、操作対象外の参加）: ステータス表示のみ。
- **満員でもチェック可**: capacity 到達（`matchParticipantCounts[n] >= capacity`＝カレンダーの `FULL` と同閾値）でも抽選前は登録可。満員は情報表示にとどめチェックボックスを無効化しない。登録不可は「抽選確定済み（ステータス表示）」「伝助削除（×）」のみ。
- **当月扱い／来月扱いの区別**（`resolveAttendanceMode`）:
  - **当月扱い**: 既存アクティブ参加は参加トグルに出さず、取り消しは理由付きキャンセルで行う。
  - **来月扱い**（未来月・抽選確定なし）: 全試合をトグル表示し登録済みは pre-check。外すと理由なし取消（`registerParticipations` の全置換で反映）。理由付きキャンセルセクションは非表示。
  - **抽選確定済みセッション**: 参加トグル不可（保存ボタンなし）、WON / PENDING のみ理由付きキャンセル可。
- **伝助削除承認済みの試合番号**: 参加トグルを出さず × 表示（参加不可）。
- **SAME_DAY 当日12:00以降の確認ダイアログ**: 参加保存は `needsSameDayConfirm`（SAME_DAY 団体・当日・変更あり）、キャンセルは当日12時判定で追加確認を表示（各既存画面と同一判定）。
- **完了/エラー**: `SaveProgressOverlay`（保存中／完了／エラー）。完了「カレンダーに戻る」→ `/practice`。エラーは状態維持で再試行。参加保存は `expectedVersion` を送り 409 は再読込。

**データフロー**:
1. `sessionId` 付きで遷移。`GET /api/practice-sessions/{id}`（会場・団体・`venueSchedules`・`matchParticipantCounts`・`densukeDeletionCandidateMatchNumbers`・`capacity`・`totalMatches`）と `GET /api/organizations` を取得し、セッション日付から年月を導出。
2. 続けて `GET /api/practice-sessions/participations/player/{playerId}`（**全置換ペイロードの seed**）と `.../status`（`participations[sessionId]` の status・participantId・waitlistNumber、`version`、`lotteryExecuted`、`hasAnyExecutedLotteryInMonth`）を取得。**seed の取得失敗は握りつぶさず全体エラーに流し、保存ボタンに到達させない**（空 seed で保存すると対象日以外の同月参加が全消えするため）。
3. 参加保存: 月の参加マップ（seed）に対象セッションの希望集合を差し替えて全置換ペイロードを組み（純関数 `buildMonthParticipationsPayload`。他日の参加を必ず保持）、`POST /api/practice-sessions/participations`。
4. キャンセル: 対象セッションの選択試合（WON/PENDING）の `participantId` を集約し `POST /api/lottery/cancel`（`participantIds`）。
