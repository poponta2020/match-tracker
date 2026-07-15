---
status: completed
---
# 札分け確認 v2（今日＋明日表示）実装手順書

> 対象は requirements.md の **§4.1 v2 改修（AC-14〜AC-21）**。v1（LINE通知・BE生成・スケジューラ）は出荷済みで、本改修では**一切変更しない**。
>
> 技術設計の要点:
> - **BE 無改修・フロント限定**。札分け取得 API（`GET /api/card-division`）は既に任意 `date`（既定 JST 今日）に対応済み。`CardDivision.jsx` を「今日（date未指定）→ 明日（今日レスポンス `date` を +1）」の2回取得に改修する。
> - **明日の日付は BE 基準アンカー**: 今日レスポンスの `CardDivisionTextDto.date` を **ISO 文字列で +1 日**（純関数 `addOneIsoDay`。TZ非依存）。フロント `new Date()` で明日を作らない（AC-17）。
> - 明日は**暫定**表示（注記付き。AC-18）。今日分の表示・コピー・per-org 購読トグルは v1 挙動を維持（AC-21）。

## 実装タスク

### タスク1: `CardDivision.jsx` を今日＋明日の2日分表示に改修（＋テスト＋docs）
- [x] 完了
- **目的:** 各練習会ブロックに「今日」「明日」の札分けを2件表示し、日ごとにコピーできるようにする。明日は BE 基準日付でアンカーし暫定注記を付ける。バックエンド・LINE通知・生成ロジックには一切触れない。
- **対応AC:** AC-14, AC-15, AC-16, AC-17, AC-18, AC-19, AC-20, AC-21（回帰: AC-9〜AC-13 も維持）
- **主な変更領域:**（フロントのみ。BE パッケージは変更しない）
  - `karuta-tracker-ui/src/pages/settings/CardDivision.jsx`
    - 純関数 `addOneIsoDay(iso)` を **`karuta-tracker-ui/src/utils/date.js` に追加・export**（`'2026-07-15' → '2026-07-16'`。`Date.UTC` ベースで月末・年末も繰り上げ、TZ非依存。ローカル `new Date()` 現在時刻に依存しない）。※コンポーネントファイル（`.jsx`）から非コンポーネントを export すると `react-refresh/only-export-components` の lint error になるため、既存の日付ユーティリティ（TZ依存の `toLocalISODate` 系）と同居させ、ユニットテストは `src/utils/date.test.js` に置く。`CardDivision.jsx` は import して使用。
    - `OrgCardDivisionBlock` を改修: ①今日を `getCardDivision(playerId, org.id)`（date未指定）で取得 → レスポンスの `date` を控える。②`addOneIsoDay(todayDate)` で明日の日付を求め `getCardDivision(playerId, org.id, tomorrowDate)` で取得（今日取得の完了後＝直列。today の `date` をアンカーにするため）。③今日・明日それぞれ `{date, hasSession, text}` を保持。
    - 表示: 各日を小コンポーネント（例 `DayCardDivision`）で「ラベル（`今日 M/D` / `明日 M/D`）＋テキストエリア＋その日専用コピーボタン」。セッション無しは「今日は練習がありません」「明日は練習がありません」。**明日ブロックに「暫定（確定前に変わる場合あり）」注記**（`text-xs text-gray-500` 等）。コピーの copied 状態は日ごとに独立。
    - per-org 購読トグル・LINE未連携案内はブロック下部に**1つのまま維持**（v1 と同一。トグルは通知＝当日分のまま）。ラベル「本日の札分け」は「今日 M/D」に置換（AC-21 の文言変更のみ許容）。
  - `karuta-tracker-ui/src/pages/settings/CardDivision.test.jsx`（既存テストを今日＋明日構造へ更新＋新規AC）
    - 既存の `mocks.getCardDivision.mockResolvedValue(...)` は全呼び出しに同じ値を返すため、**今日/明日で別レスポンスを返す**よう `mockResolvedValueOnce`（1回目=今日）→`mockResolvedValueOnce`（2回目=明日）へ変更。
    - AC-14: 今日・明日の2枠が表示される。AC-15: 明日 `hasSession:false` で「明日は練習がありません」。AC-16: 今日・明日それぞれのコピーで `writeText` が各テキストで呼ばれる（**clipboard は必ず `{ writeText: vi.fn() }` を持たせてから spy**＝既存パターン踏襲、[[auto_review_round_pr1051]] の教訓）。AC-17: 1回目呼び出しが date 未指定、2回目が今日レスポンス `date`（例 `'2026-07-15'`）の +1（`'2026-07-16'`）で呼ばれることをアサート＋`addOneIsoDay` のユニットテスト（月末/年末繰り上げ含む）。AC-18: 明日ブロックに暫定注記。AC-21回帰: 購読トグル・未連携案内・0件導線が不変。
  - `karuta-tracker-ui/src/utils/date.js` / `date.test.js`（`addOneIsoDay` を追加＝上記の lint 都合による切り出し先）
  - `docs/SCREEN_LIST.md`（§8.10 札分け確認の説明を「今日＋明日の2日分表示」に更新）
- **依存タスク:** なし（単一タスク）
- **必要なテスト:** 上記 AC-14〜AC-18・回帰 AC-21 の Vitest（`CardDivision.test.jsx`）と `addOneIsoDay` ユニットテスト。テストファースト。
- **完了条件:** `npm run test`（Vitest）green・`npm run lint` green。BE 側は無変更（`git diff` で `karuta-tracker/` に差分が無い＝AC-19/AC-20）。`docs/SCREEN_LIST.md` を同コミットで更新。
- **対応Issue:** #1065

## 実装順序（Wave）
- Wave 1: タスク1（単一。フロント1ファイル＋テスト＋docs で完結）
