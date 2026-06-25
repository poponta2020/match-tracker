---
status: completed
---
# 試合番号デフォルト遷移（時刻・入力状況ベース） 要件定義書

## 1. 概要

### 目的
試合結果一覧画面と試合結果一括入力画面を開いたとき、**現在時刻と入力状況に応じて「今おそらく見たい／入力したい試合番号」を初期表示する**ようにし、毎回1試合目から手動でタブ／スワイプ切替する手間をなくす。

### 背景・動機
- 現状、両画面とも初期表示は常に「1試合目」固定（`useState(1)`）。
- 練習が2試合目・3試合目と進んでいても、画面を開くたびに1試合目が表示され、目的の試合番号まで毎回切り替える必要がある。
- 会場には試合番号ごとの開始・終了時刻（`VenueMatchSchedule`）が既に定義されており、これを使えば「今はおおむね何試合目の時間帯か」を判定できる。

---

## 2. ユーザーストーリー

- **対象ユーザー:** 練習当日に試合結果を確認・入力する運営者／選手（ADMIN / PLAYER）。
- **ユーザーの目的:** 練習の進行に合わせて、今行われている（または直前に終わった）試合番号の結果一覧・入力画面をすぐ開きたい。
- **利用シナリオ:**
  - 2試合目の最中（例: 18:40）に一覧画面を開く → 自動的に2試合目が表示される。
  - 1試合目の結果入力を終えた状態で一括入力画面を開く → 自動的に2試合目の入力画面が表示される。
  - 過去の練習日の結果を見るときは従来通り1試合目から表示される（時刻で勝手に動かない）。

---

## 3. 機能要件

対象画面は以下の2つ。どちらも「初期表示時のデフォルト試合番号」を決めるロジックのみを変更する。ユーザーがタブタップ／スワイプで切り替えた後の挙動は一切変更しない。

| 画面 | ファイル | ルート |
|------|---------|--------|
| 試合結果一覧 | `karuta-tracker-ui/src/pages/matches/MatchResultsView.jsx` | `/matches/results/:sessionId?date=YYYY-MM-DD` |
| 試合結果一括入力 | `karuta-tracker-ui/src/pages/matches/BulkResultInput.jsx` | `/matches/bulk-input/:sessionId` |

### 3.1 共通ロジック：時刻ベースのデフォルト試合番号

**適用条件（すべて満たす場合のみ発動）:**
1. 表示対象が **当日**（端末のローカル日付 === 練習の `sessionDate`）であること。
2. その練習の会場に **試合番号ごとの開始・終了時刻（`venueSchedules`）が定義されている**こと。

**判定ロジック:**
- 猶予時間 `GRACE_MINUTES = 15`（固定値）。
- 現在時刻（端末のローカル時刻）を `now` とする。
- 試合番号を昇順に走査し、`now < (N試合目の終了時刻 + 15分)` を満たす **最小の試合番号 N** をデフォルトとする。
- どの試合番号の境界にも該当しない（= 最終試合の「終了時刻+15分」を過ぎている）場合は **1試合目に戻す**。

**境界の考え方（要件例との整合）:**

例: 1試合目 17:05–18:15、2試合目 18:15–19:30 の場合

| 現在時刻の範囲 | デフォルト試合番号 | 根拠 |
|---------------|------------------|------|
| 〜 18:30 未満（開始前含む） | 1試合目 | 1試合目終了 18:15 + 15分 = 18:30 |
| 18:30 〜 19:45 未満 | 2試合目 | 2試合目終了 19:30 + 15分 = 19:45 |
| 19:45 以降 | 1試合目（戻す） | 最終試合の「終了+15分」を過ぎた |

→ 試合が切り替わっても**15分間は前の試合番号がデフォルトのまま**（結果入力の猶予）。

**適用条件を満たさない場合（過去日・未来日、会場スケジュール未定義など）:** 時刻ベースは発動せず、従来通り **1試合目** を初期表示する。

### 3.2 一括入力画面の上位制約：入力済みに応じたデフォルト

試合結果一括入力画面（`BulkResultInput.jsx`）には、**3.1 の時刻ベースより優先される**上位制約を設ける。

**判定ロジック:**
- 結果が**すべて入力済み**の試合番号のうち**最大のもの**を `n` とする（入力済みの定義は後述）。
- `n` が存在する（= 1つ以上の試合番号が全入力済み）場合：デフォルト = `min(n + 1, totalMatches)`。
  - 1試合目が未入力でも、2試合目が全入力済みなら 3試合目をデフォルトにする（n-1 の入力状況は問わない）。
  - 全試合が入力済みの場合は `n + 1 > totalMatches` となるため、最終試合をデフォルトにする。
- `n` が存在しない（入力済みの試合番号が1つもない）場合：**3.1 の時刻ベース**にフォールバック（当日かつスケジュールありなら時刻ベース、それ以外は1試合目）。

**「すべて入力済み」の定義:**
- その試合番号に紐づく**全ペアリング**（`MatchPairing`）に対応する**試合結果（`Match`）が存在する**こと。
- ペアリングが0件の試合番号は「入力済み」とみなさない（既存の `isMatchCompleted` と同じ判定）。

**優先順位の整理（一括入力画面・当日の例）:**

| 状況 | 時刻ベースの候補 | 入力済み最大+1 | 最終的なデフォルト |
|------|----------------|---------------|------------------|
| 1試合目入力済み / 現在18:00 | 1試合目 | 2試合目 | **2試合目**（上位制約優先） |
| 入力済みなし / 現在18:40 | 2試合目 | （該当なし） | **2試合目**（時刻ベース） |
| 入力済みなし / 現在10:00 | 1試合目 | （該当なし） | **1試合目** |

### 3.3 一覧画面の挙動

試合結果一覧画面（`MatchResultsView.jsx`）のデフォルト試合番号は次の優先順位で決定する。
1. **URLクエリ `matchNumber` が指定されている場合**（一括入力画面からの保存後遷移など）→ その試合番号を**最優先**で初期表示する（時刻ベースより優先）。
2. それ以外で **3.1 の時刻ベース**（当日かつスケジュールあり）。
3. いずれにも該当しなければ 1試合目。

入力状況による上位制約（3.2）は一覧画面には設けない。

### 3.4 エラーケース・境界条件

- **会場が未設定 / `venueSchedules` が空:** 時刻ベース非適用 → 1試合目。
- **`venueSchedules` に一部の試合番号しか時刻がない:** 時刻が定義された試合番号のみで 3.1 の走査を行う（定義のない番号はスキップ）。
- **当日だが対戦（ペアリング）がまだ1件も組まれていない:** 時刻ベースで決まった試合番号を初期選択する（対戦の有無・作成時刻は判定に使わない＝ヒアリング合意）。結果該当データが無ければ画面は従来通り「データなし」表示になる。
- **`endTime + 15分` が24時を超える深夜練習:** 当日の `now` は最大23:59のため境界に到達せず、最終試合がデフォルトのまま（実用上問題なし）。

---

## 4. 技術設計

### 4.1 設計方針
- **フロントエンドのみで完結。バックエンド・DBの改修は不要。**
  - 試合番号ごとの時刻 = `PracticeSessionDto.venueSchedules`（取得済み）。
  - ペアリング・試合結果 = 両画面で取得済み（`pairingAPI.getByDate` / `matchAPI.getByDate`）。
  - 現在時刻 = 端末のローカル時刻。
- 初期試合番号の決定ロジックは**共通ユーティリティ**に切り出し、2画面から呼ぶ。

### 4.2 API設計
**変更なし。** 既存APIをそのまま利用する。
- `GET /practice-sessions/{id}` / `GET /practice-sessions/date?date=` → `venueSchedules`（試合番号ごとの `startTime`/`endTime`）
- `GET /match-pairings/date?date=&light=true` → ペアリング一覧
- `GET /matches?date=` → 試合結果一覧

### 4.3 DB設計
**変更なし。**
- `venue_match_schedules`（`venue_id`, `match_number`, `start_time`, `end_time`）を参照のみ。

### 4.4 フロントエンド設計

**新規ユーティリティ（例: `karuta-tracker-ui/src/pages/matches/defaultMatchNumber.js`）**

```
GRACE_MINUTES = 15

// 時刻ベースのデフォルト試合番号（当日・スケジュールありが前提）
// schedules: [{ matchNumber, startTime: "HH:mm:ss", endTime: "HH:mm:ss" }]
// now: Date（端末ローカル時刻）
function timeBasedDefaultMatchNumber(schedules, now):
    nowMinutes = now.getHours()*60 + now.getMinutes()
    sorted = schedules（endTimeあり）を matchNumber 昇順
    for s in sorted:
        boundary = toMinutes(s.endTime) + GRACE_MINUTES
        if nowMinutes < boundary:
            return s.matchNumber
    return 1   // 最終試合の終了+15分を過ぎた → 1に戻す

// 一覧画面用
function defaultForResultsView({ urlMatchNumber, venueSchedules, sessionDate, now }):
    if (urlMatchNumber) return urlMatchNumber   // 保存後遷移などの明示指定を最優先
    if (!isToday(sessionDate, now) || !venueSchedules?.length) return 1
    return timeBasedDefaultMatchNumber(venueSchedules, now)

// 一括入力画面用（入力済み制約が上位）
function defaultForBulkInput({ completedMatchNumbers, totalMatches, venueSchedules, sessionDate, now }):
    maxCompleted = max(completedMatchNumbers) // 無ければ null
    if (maxCompleted != null) return min(maxCompleted + 1, totalMatches)
    if (!isToday(sessionDate, now) || !venueSchedules?.length) return 1
    return timeBasedDefaultMatchNumber(venueSchedules, now)
```

**各画面の変更点**
- `MatchResultsView.jsx`: URLクエリ `matchNumber` を読み取る。初期データ取得後（`venueSchedules` と日付が確定したタイミング）に `defaultForResultsView`（URL指定を最優先）の結果で `currentMatchNumber` を1回だけ設定する。
- `BulkResultInput.jsx`: 初期データ取得後（セッション・ペアリング・既存結果が揃ったタイミング）に、入力済み試合番号を算出し `defaultForBulkInput` の結果で `currentMatchNumber` を1回だけ設定する。**保存後に一覧画面へ遷移する際は、現在表示中の `currentMatchNumber` を URL クエリ `matchNumber` として渡す**（例: `/matches/results/:sessionId?matchNumber=2`。既存の `date` 等のクエリがあれば併せて維持する）。
- 「入力済み試合番号の算出」は、`MatchResultsView` 内の既存 `isMatchCompleted(matchNumber)` 相当のロジックを共通化して両画面で再利用する。

**初期化のタイミング・冪等性**
- 初期データ取得完了後の**1回だけ**デフォルトを適用する（ユーザーが切り替えた後に再フェッチで上書きしないよう、初回フラグで制御）。既存の `initialFetchDone` 等のパターンに合わせる。

### 4.5 バックエンド設計
**変更なし。**

---

## 5. 影響範囲

### 変更が必要な既存ファイル
- `karuta-tracker-ui/src/pages/matches/MatchResultsView.jsx` — 初期 `currentMatchNumber` の決定ロジック追加。既存 `isMatchCompleted` を共通化。
- `karuta-tracker-ui/src/pages/matches/BulkResultInput.jsx` — 初期 `currentMatchNumber` の決定ロジック追加。
- 新規: `karuta-tracker-ui/src/pages/matches/defaultMatchNumber.js`（ユーティリティ）＋単体テスト。

### 既存機能への影響
- **両画面の初期表示試合番号が変わる**（1固定 → 時刻／入力状況ベース）。タブ・スワイプ切替の挙動は不変。
- **他画面からの遷移:** 本要件で `MatchResultsView` は URL クエリ `matchNumber` を受け付けるようになる（従来は未使用）。指定された場合は最優先で表示する。
- **保存後の遷移:** 一括入力画面で保存すると `/matches/results/:sessionId` へ遷移する。このとき**入力していた試合番号を URL クエリ `matchNumber` で引き継ぎ**、遷移先の一覧画面はそれを最優先で表示する（時刻ベースより優先）。これにより「たった今入力していた試合番号」がそのまま表示される。

### 互換性
- API・DBスキーマの変更なし。後方互換。

---

## 6. 設計判断の根拠

| 判断 | 内容 | 根拠 |
|------|------|------|
| 時刻源は `VenueMatchSchedule` | 試合番号ごとの開始・終了時刻は既存の会場スケジュールを使用 | ヒアリング合意。新規スキーマ不要で既存データを活用できる |
| 下限は現在時刻のみ | 「対戦が組まれた時刻」は判定に使わない | ヒアリング合意（「対戦の組まれた時間は気にしなくていい」） |
| 当日のみ適用 | 過去日・未来日は時刻ベースを発動せず1試合目 | ヒアリング合意。過去日に現在時刻で動くのは無意味 |
| 切替境界は「終了+15分」 | 試合切替後15分は前の試合番号を維持 | 要件例（18:30 / 19:45）と整合。結果入力の猶予を確保 |
| 最終試合後は1試合目に戻す | 最終試合の「終了+15分」超過後は1試合目 | ヒアリング合意 |
| 15分は固定値 | 設定可能化はしない | ヒアリング合意。実装をシンプルに保つ |
| 現在時刻は端末ローカル | サーバー時刻は使わない | ヒアリング合意。フロント完結でバックエンド改修不要 |
| 一括入力の上位制約 | 入力済み最大番号 +1 を時刻ベースより優先 | 要件指定。練習進行と入力実態を反映 |
| 保存後遷移は番号引き継ぎ | 一括入力→一覧の遷移時、URLクエリ `matchNumber` で入力中の試合番号を引き継ぎ、一覧では最優先表示 | ヒアリング合意。入力直後の確認で番号がズレる違和感を防ぐ |
| フロント完結 | バックエンド・DB改修なし | 必要データは全て取得済み。影響範囲を最小化 |
