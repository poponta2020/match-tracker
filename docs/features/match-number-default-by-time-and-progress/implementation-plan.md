---
status: completed
---
# 試合番号デフォルト遷移（時刻・入力状況ベース） 実装手順書

要件定義書: `docs/features/match-number-default-by-time-and-progress/requirements.md`

実装範囲はフロントエンドのみ（`karuta-tracker-ui/`）。バックエンド・DB・APIの変更なし。テストは vitest（`npm run test`）、純粋ロジックは `*.test.js`（先例: `src/pages/matches/byePlayersLogic.test.js`）。

---

## 実装タスク

### タスク1: 試合番号デフォルト決定ユーティリティの新規作成
- [x] 完了
- **概要:** 初期表示する試合番号を算出する純粋関数群を新規ユーティリティに実装し、vitest で単体テストを追加する。画面のデータ構造に依存しない純粋関数として切り出す。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/defaultMatchNumber.js`（新規） — 以下をエクスポート:
    - `GRACE_MINUTES = 15`（定数）
    - `toMinutes(timeStr)` — `"HH:mm:ss"` / `"HH:mm"` を分に変換
    - `isToday(sessionDate, now)` — `sessionDate`（`"YYYY-MM-DD"`）が端末ローカル日付の当日かを判定
    - `timeBasedDefaultMatchNumber(venueSchedules, now)` — `now`（Date）の分換算が `endTime + 15分` 未満となる最小 `matchNumber` を返す。`endTime` を持つ番号を昇順走査し、該当なし（最終試合の終了+15分超過）なら `1` を返す
    - `getCompletedMatchNumbers({ pairings, matches, totalMatches })` — 各試合番号について「全ペアリングに対応する Match が存在する」なら入力済みとみなし、入力済み番号の配列を返す（ペアリング0件の番号は含めない。既存 `isMatchCompleted` と同じ判定・player id は min/max 正規化で突合）
    - `defaultForResultsView({ urlMatchNumber, venueSchedules, sessionDate, now })` — `urlMatchNumber` があれば最優先で返す。なければ「当日 かつ `venueSchedules` あり」で時刻ベース、それ以外は `1`
    - `defaultForBulkInput({ completedMatchNumbers, totalMatches, venueSchedules, sessionDate, now })` — 入力済みがあれば `min(max(completedMatchNumbers) + 1, totalMatches)`。皆無なら「当日 かつ `venueSchedules` あり」で時刻ベース、それ以外は `1`
  - `karuta-tracker-ui/src/pages/matches/defaultMatchNumber.test.js`（新規） — 単体テスト。最低限カバーするケース:
    - 時刻ベース: 要件例（1試合目17:05–18:15 / 2試合目18:15–19:30）で 18:00→1、18:30→2、19:00→2、19:45→1（最終超過で戻る）、開始前16:00→1
    - 一部の試合番号のみ時刻定義ありのケース
    - `getCompletedMatchNumbers`: 全入力済み / 一部入力済み / ペアリング0件 / player id 逆順の突合
    - `defaultForBulkInput`: 1試合目未入力＆2試合目入力済み→3、全入力済み→最終試合、入力済み皆無→時刻ベース
    - `defaultForResultsView`: `urlMatchNumber` 優先、過去日→1、スケジュール無し→1
- **依存タスク:** なし
- **完了条件:** `npm run test` で本ファイルの全テスト通過。`npm run lint` 通過。
- **対応Issue:** #938

### タスク2: 試合結果一覧画面へのデフォルト適用
- [ ] 完了
- **概要:** `MatchResultsView` で URLクエリ `matchNumber` を読み取り、初期データ取得完了後に `defaultForResultsView` の結果で `currentMatchNumber` を1回だけ設定する。既存のタブ✓表示（`isMatchCompleted`）はタスク1の `getCompletedMatchNumbers` と整合するよう必要に応じて共通化する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/MatchResultsView.jsx` — `useSearchParams` で `matchNumber` を取得。`venueSchedules`・日付確定後に `defaultForResultsView` を呼び初期 `currentMatchNumber` を設定（初回のみ。既存の `initialFetchDone`/`lastFetchedDate` パターンに合わせ、ユーザー切替を上書きしない）。
  - `karuta-tracker-ui/src/pages/matches/MatchResultsView.swipe.test.jsx` もしくは新規テスト — デフォルト初期表示（URL指定優先・当日時刻ベース・過去日1固定）の確認を追加。
- **依存タスク:** タスク1
- **完了条件:** 当日は時刻ベース、過去日は1試合目、URL `matchNumber` 指定時はその番号で初期表示。切替操作・スワイプの既存挙動が不変。`npm run test` / `npm run lint` 通過。
- **対応Issue:** #939

### タスク3: 試合結果一括入力画面へのデフォルト適用＋保存後遷移
- [ ] 完了
- **概要:** `BulkResultInput` で初期データ取得完了後に入力済み試合番号を算出し、`defaultForBulkInput` の結果で `currentMatchNumber` を1回だけ設定する。保存後に一覧画面へ遷移する際、現在の `currentMatchNumber` を URLクエリ `matchNumber` として渡す。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/BulkResultInput.jsx` — セッション・ペアリング・既存結果が揃った後に `getCompletedMatchNumbers` → `defaultForBulkInput` で初期 `currentMatchNumber` を設定（初回のみ）。保存後の `navigate('/matches/results/:sessionId', ...)` に `?matchNumber=<currentMatchNumber>` を付与（既存クエリがあれば維持）。
  - `karuta-tracker-ui/src/pages/matches/BulkResultInput.navigation.test.jsx` もしくは新規テスト — 入力済み制約による初期表示、保存後遷移URLへの `matchNumber` 付与を確認。
- **依存タスク:** タスク1
- **完了条件:** 入力済み最大+1（皆無なら時刻ベース／過去日は1）で初期表示。保存後の一覧遷移で入力していた番号が引き継がれる。既存の保存・スワイプ挙動が不変。`npm run test` / `npm run lint` 通過。
- **対応Issue:** #940

### タスク4: ドキュメント更新
- [ ] 完了
- **概要:** 機能追加に伴い仕様・画面・設計ドキュメントを更新する（CLAUDE.md のドキュメント更新ルール）。
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` — 両画面のデフォルト試合番号決定ルール（時刻ベース／一括入力の入力済み制約／保存後の番号引き継ぎ）を追記。
  - `docs/SCREEN_LIST.md` — 試合結果一覧・一括入力画面の初期表示挙動の変更を反映。
  - `docs/DESIGN.md` — `defaultMatchNumber.js` ユーティリティと両画面の初期化フローを追記。
- **依存タスク:** タスク2・タスク3
- **完了条件:** 3ドキュメントに変更が反映され、実装と一致している。
- **対応Issue:** #941

---

## 実装順序
1. タスク1（依存なし）
2. タスク2（タスク1に依存） / タスク3（タスク1に依存） ※2と3は並行可
3. タスク4（タスク2・タスク3に依存）
