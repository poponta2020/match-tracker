---
status: completed
---
# 戦績確認画面の2タブ化（戦績確認／カレンダー）実装手順書

純フロントエンド・1PR。BE / DB / `@RequireRole` は触らない。カレンダーは既存 `GET /matches/player/{selfId}` の enrichment 済みレスポンスをクライアント集計して構築する。design-spec.md（locked）＋ design-prototype.patch（apply OK・基点 `7e8f9b2d`）を土台にする。

## 技術設計の要点

- **統合方針:** `/matches`（`MatchList`）を **view（`record` / `calendar`）でタブ切替できるコンテナ化**する。ルートは増やさず URL クエリ `?view=calendar`（無指定＝record）でタブ状態を持つ。`playerId` は常に URL 保持し、タブ切替は `setSearchParams(..., { replace: true })`。
- **カレンダータブは常に自分:** `MatchCalendar` は `useAuth().currentPlayer` と `matchAPI.getByPlayerId(currentPlayer.id)` で自分の試合を自前 fetch する（record タブの `playerId` とは独立）。
- **純ロジックの分離:** カレンダー週グリッド生成・日付/月集計・整形（M/D、相手級の単字化）は `utils/matchCalendar.js` に純関数として切り出し、jsdom/日付に依存しない単体テストを可能にする（プロジェクトの `defaultMatchNumber.js` 等と同型）。
- **テスト容易性:** `MatchCalendar` は「今日」を固定できるよう任意 prop `referenceDate`（既定 `new Date()`）を受ける（DESIGN-PROTO ではなく恒久のテスト用シーム）。
- **DESIGN-PROTO の扱い:** patch 内の `DesignCalendarPreview.jsx` とその `App.jsx` ルート/import は**本体へ持ち込まない**（プレビュー足場）。`MatchViewTabs.jsx` / `MatchCalendar.jsx` は本物（マーカー無し）。

## 実装タスク

### タスク1: カレンダー本体・タブ帯・純ロジックの新設（productionize + 単体テスト）
- [x] 完了
- **目的:** `MatchViewTabs` / `MatchCalendar` / `utils/matchCalendar.js` を patch から productionize し、純ロジックと表示の単体テストを整備する。`MatchCalendar` を自前 fetch（`matchAPI.getByPlayerId(self)`）へ配線し、`referenceDate` シームを追加。
- **対応AC:** AC-6, AC-7, AC-8, AC-9, AC-10, AC-11, AC-12, AC-13, AC-14, AC-CAL-PICK
- **対応Issue:** #1143
- **主な変更領域:** `karuta-tracker-ui/src/pages/matches/MatchCalendar.jsx`（新規）, `karuta-tracker-ui/src/pages/matches/MatchViewTabs.jsx`（新規）, `karuta-tracker-ui/src/utils/matchCalendar.js`（新規）, 各 `*.test.jsx`。**patch の `DesignCalendarPreview.jsx`・`App.jsx` の DESIGN-PROTO 改変は取り込まない**。
- **依存タスク:** なし
- **必要なテスト（テストファースト）:**
  - `utils/matchCalendar.test.js`（純関数）: 週グリッド生成（前後月はみ出しセル）・日付→試合数集計・月間総試合数・M/D整形（十の位0省略）・相手級の単字化（`A級`→`A`・null→なし）。
  - `MatchCalendar.test.jsx`（`matchAPI` モック＋`referenceDate` 固定）: 日セルの試合数バッジ・初期は今日選択・日選択で1行 `名前（級） お手N`（級なし=括弧なし/お手つき不明=お手なし）・指導表示・空日「記録がありません」（登録ボタン無し）・見出し `M/D 会場名`（会場は日付一意）・月間試合数バッジ・年月ピッカー開閉と月ジャンプ連動・項目タップで `/matches/:id` 遷移。抜け番（READING/SOLO_PICK 相当）が来ても集計/リストに出ないこと。
  - `MatchViewTabs.test.jsx`: タブ順（カレンダー左／戦績確認右）・アクティブ下線・`onChange`。
- **完了条件:** 新規テスト green・本体側 `git grep DESIGN-PROTO` = 0件・lint 通過。

### タスク2: /matches の2タブ統合（MatchList コンテナ化）
- [x] 完了
- **目的:** `MatchList` を view でタブ切替可能にする。緑トップバー直下に `MatchViewTabs`、`view=calendar` で `MatchCalendar` を描画。カレンダータブのトップバーは自分の名前＋級（期間フィルタ・選手検索は非表示）。タブ切替は `playerId` を保った `replace`。戦績確認（record）の既存挙動は不変。
- **対応AC:** AC-1, AC-2, AC-3, AC-4, AC-5, AC-15（回帰）, AC-16, AC-17
- **対応Issue:** #1144
- **主な変更領域:** `karuta-tracker-ui/src/pages/matches/MatchList.jsx`, `karuta-tracker-ui/src/pages/matches/MatchList.test.jsx`（追記 or 統合テスト新規）。
- **依存タスク:** タスク1（`MatchViewTabs` / `MatchCalendar` 前提）
- **必要なテスト:** タブ帯が表示され既定は record・`view=calendar` で URL クエリと `MatchCalendar` 描画・`playerId=他選手` でもカレンダーは自分を表示・カレンダー→record で `playerId` 復元・タブ切替が `replace`。**既存 `MatchList.test.jsx` が全て green（回帰＝戦績確認の統計/フィルタ/検索/他選手閲覧/自分に戻す/リスト）**。
- **完了条件:** 全テスト green・lint 通過。

## 実装順序（Wave = 並行実装できるタスクの組）
- Wave 1: タスク1
- Wave 2: タスク2（タスク1 に依存＝直列。両タスクとも `MatchList.jsx` 周辺の同一領域を触るため並行にしない）

## 注記
- **BE / DB 変更なし**（AC-16）。migration・`@RequireRole` 追加なし。
- design-prototype.patch は `git apply` で当てられれば土台に、当たらなければ patch を読んで手動移植（新規3ファイル＋App.jsx の DESIGN-PROTO 部は取り込まない）。
- 実装時、`MatchList` は 765 行の既存複雑コンポーネント。回帰 AC-15 を厚めに担保する（既存テスト維持＋タブ導入での top bar / padding 影響を確認）。
