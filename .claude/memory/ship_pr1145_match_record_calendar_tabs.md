---
name: ship-pr1145-match-record-calendar-tabs
description: 戦績確認画面 /matches を「カレンダー／戦績確認」2タブ構成に統合を出荷（PR #1145、親#1142子#1143#1144）
type: project
category: ship
tags: [matches, calendar, frontend, react, tabs, ui]
---

# PR #1145 出荷記録 — match-record-calendar-tabs

- PR: https://github.com/poponta2020/match-tracker/pull/1145
- Issue: 親 #1142 ／ 子 #1143（タスク1）#1144（タスク2）— PR 本文の closing keyword で子を自動クローズ
- 要件書: `docs/features/match-record-calendar-tabs/requirements.md`
- 設計/実装の詳細は harness memory [[impl_match_record_calendar_tabs]]、auto-review は [[auto_review_round_pr1145]]
- 出荷日: 2026-07-21

## 何を変えたか（純フロントエンド・2タスク直列・main 直実装）

- **`pages/matches/MatchViewTabs.jsx`（新規）**: 緑バー直下の下線アクティブ式2タブ帯（カレンダー左/戦績確認右・表示のみ）。
- **`pages/matches/MatchCalendar.jsx`（新規）**: カレンダータブ本体。`useAuth()`+`matchAPI.getByPlayerId(self)` で**常に自分**の試合を自前取得（戦績確認タブの playerId と独立）。日セルに試合数バッジ（今日=緑リング/選択日=角丸ボックス+薄緑背景/日曜赤・土曜青/はみ出しセル不可）、年月ラベル押下で `YearMonthPicker` ジャンプ+当月総試合数バッジ、日選択で `N試合目： 勝敗+枚数差 相手名（級） お手N`+個人メモ表示（見出し `M/D 会場名`・会場は日付一意・指導は「指導」・級/お手不明は省略・抜け番除外・読取専用）、項目タップで `/matches/:id`。`referenceDate` prop でテスト時「今日」固定。
- **`utils/matchCalendar.js`（新規）**: 週グリッド生成・日付/月集計・M/D整形・相手級単字化・会場解決の純関数群。
- **`pages/matches/MatchList.jsx`**: タブコンテナ化。緑バーから `fixed` を剥がし緑バー+タブ帯を1つの固定ヘッダーブロックに束ね、`view==='calendar'` は record の loading/派生計算に依存しない early return。タブ切替は `setSearchParams(view のみ差替, {replace:true})`＝playerId 保持。**メイン fetch effect を `view==='record'` でガード**（カレンダー表示中の重複/未使用取得を防止＝auto-review R1 対応）。検索ボタンに `aria-label` 付与。
- test: `matchCalendar.test.js` / `MatchViewTabs.test.jsx` / `MatchCalendar.test.jsx` / `MatchList.tabs.test.jsx`（新規計38件）。既存 `MatchList.test.jsx`/`integration.test.jsx` は回帰網として全 green。
- docs: `docs/SCREEN_LIST.md` 行7を2タブ構成へ更新。

## 設計の肝

- **BE/DB/@RequireRole 変更ゼロ**（AC-16）: 既存 `GET /matches/player/{id}` レスポンスをクライアント集計。migration 不要。
- **playerId 常時 URL 保持・カレンダーは無視**: 他選手戦績→カレンダー(自分)→戻ると他選手戦績を復元。追加の状態管理不要。
- **`no-irregular-whitespace` はデフォルトで template literal 内の全角スペースを検出**（通常の文字列リテラルはスキップ）。見出し `M/D　会場名` は `'　' + venue` の文字列連結で回避。
- **既存パターン準拠のTZ**: 「今日/当月」は端末ローカルTZ算出（同型の `PracticeList`・`MatchList` と同一）。auto-review の JST 指摘は既存パターン準拠として棄却（[[auto_review_round_pr1145]]）。

## auto-review（Codex CLI・effort medium）

**2R収束・R2 pass**（累計約177k/500k tok）。R1=should_fix2件（blocker0）→ finding1「カレンダー表示中の戦績確認データ重複取得」を view ガードで修正・finding2「JST判定」はFP棄却。medium 選択の判断＝docs/test膨張で file数13>8/行1681>400の機械的 high 判定を過大計上として medium に倒し的中。詳細 [[auto_review_round_pr1145]]。

## テスト・AC

- フロント全794テスト green（68ファイル・フレークなし）・lint 0 error・`git grep DESIGN-PROTO` 0件・本番ビルド成功。
- DoD: A1=SKIP（CI委譲）・A2 lint=PASS・A3=SKIP・B1 CI=PASS（pending マージ）・C1 レビュー=PASS（r2 pass）・D1 memory=PASS・D2 docs=PASS。
- **AC-16（BE無改修）は manual＝変更は `karuta-tracker-ui/src`+`docs` のみで充足**。

## ship 後の確認事項（未検証）

- **ヘッダーのパディング（record `pt-[84px]`/検索展開 `pt-[132px]`・カレンダー `pt-[42px]`）はテスト/ビルドで検出不可の視覚要素**。`space-y-6`+`pt-16` 前提で既存の余白（約35px gap）を保つよう算出したが、auth+backend 必須のため実機 `/matches` 目視は未実施（カレンダー本体は /design-screen でブラウザ承認済み）。マージ後に実機で「本文がヘッダーに隠れていないか／過大gapがないか」を一度目視推奨（最有力の微調整点）。
