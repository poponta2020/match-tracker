---
status: completed
---
# design-md-anti-slop 実装手順書

**FE のみ**（バックエンド改修なし・DBマイグレーションなし）。確定デザインは [design-spec.md](design-spec.md)（locked）、要件・AC は [requirements.md](requirements.md) §4。

## 実装タスク

### タスク1: `docs/design/design.md` 正典の執筆（app-wide）
- [ ] 完了
- **目的:** 脱AIスロップのデザイン正典を新設。今後の /implement・/design-screen が参照する。
- **対応AC:** AC-1, AC-2, AC-3, AC-14
- **主な変更領域:** `docs/design/design.md`（新規）
- **依存タスク:** なし
- **対応Issue:** #1156
- **内容:**
  - Google 標準・**単一ファイル**。YAML front matter＝トークン（`colors`/`typography`/`rounded`/`spacing`/`components`。現行 washi 語彙由来のセマンティック名）＋ 散文8セクション（Overview/Colors/Typography/Layout/Elevation & Depth/Shapes/Components/Do's and Don'ts）。
  - トークンは**参照仕様**（tailwind.config には配線しない）。抽出元は matches 系 CSS 局所変数＋シェル緑＋確定追加色（深緑 `#33503f`・タン繊維系）。孤児の赤 `primary`・旧マテリアル色（`#4CAF50` 等）は「新規使用禁止（レガシー）」と明記。
  - **Do's and Don'ts は本アプリ固有スロップを名指し禁止**: 3枚横並び機能カード／色帯ヘッダー複合カード／Trophy でランキング装飾・丸数字カラーバッジ／コンテンツに影付きカード／全カード灰1px罫線／web フォント／未要望のダークモード。
  - **原則「脱スロップ ≠ ミニマリズム」を明記**（敵は浮いた角丸＋影の箱のみ。色面ブロッキング・明朝ディスプレイ・和紙テクスチャ・バーは推奨）＝AC-14。
  - カード方針＝**原則禁止＋許可リスト**（モーダル/ダイアログ/ドロップダウン/ボトムシートのみ可）。
  - 参照実装として matches（MatchForm/BulkResultInput/MatchResultsView/TorifudaRecord）・pairings・videos・**Home（本改修）**を挙げる。
- **完了条件:** 8セクション見出し＋パース可能な front matter を持つ（AC-1 の走査/パースが通る）。

### タスク2: 正典の配線（enforcement）
- [ ] 完了
- **目的:** design.md を「正典」として機能させる。
- **対応AC:** AC-7
- **主な変更領域:** `.claude/project-profile.md`（`## design-system`・`## docs`）、`CLAUDE.md`（ナビゲーション規約）、`karuta-tracker-ui/CLAUDE.md`（デザイントークン行）
- **依存タスク:** タスク1（design.md を参照するため）
- **対応Issue:** #1157
- **内容:** `## design-system` を design.md 一次参照に更新／`## docs` レジストリに design.md を「デザイン正典」として登録／ルート CLAUDE.md のナビゲーション規約に「UI 改修時は `docs/design/design.md` を参照・厳守」を1行追加。
- **完了条件:** 各ファイルに design.md への参照文字列が存在（AC-7 grep が通る）。

### タスク3: 和紙 seamless タイルの生成＋アセット化
- [x] 完了
- **目的:** 本文背景の和紙繊維テクスチャを決定論的な静的アセットにする。
- **対応AC:** AC-13
- **主な変更領域:** `karuta-tracker-ui/src/assets/`（生成した PNG）、`scripts/`（生成スクリプト）
- **依存タスク:** なし
- **対応Issue:** #1158
- **内容:** design-spec §3 の確定パラメータ（繊維トーン重み・数量・len・alpha・簀の目/糸目・塵）を**seed 固定**で描画し、**seamless にタイリングできる** PNG（例 512×512）を生成するスクリプト（node-canvas 等）をコミット。エッジ跨ぎ要素のラップ or 半タイルオフセット法で seam を消す。生成物 PNG もコミット。
- **完了条件:** アセット PNG が存在し、Home が `background: #f2ede6 url(...) repeat` で参照できる。**Home マウント時に乱数 canvas を実行しない**。

### タスク4: Home.jsx 脱カード・リデザイン実装
- [x] 完了
- **目的:** design-spec.md の全状態を実装し、Home を「生きた手本」にする。
- **対応AC:** AC-4, AC-5, AC-6, AC-9, AC-10, AC-11, AC-12, AC-13
- **主な変更領域:** `karuta-tracker-ui/src/pages/Home.jsx`、Home のテスト（`karuta-tracker-ui/src/**/Home*.test.jsx` があれば）
- **依存タスク:** タスク3（和紙アセットを import するため）
- **対応Issue:** #1159
- **内容:**
  - design-spec §2 の**全状態**を実装: 深緑ヒーロー常時表示・単一デザイン（TODAY/NEXT/未登録=参加登録リンク/練習なし=空状態）、CTA「対戦確認画面へ／参加登録」、参加率=行背景バー（順位｜名前＋参加試合数｜%）、団体ヘッダー総試合数、自分の行（茶ライン・YOUラベル無し）、和紙背景 repeat。
  - **撤去**: `NavigationMenu`（トップバー）、`hasPendingOffer` バナー、`PlayerChip`（参加者チップ）＋待ち/キャンセルセクション、Trophy／丸数字バッジ／独立プログレスバー。未使用 import 整理。
  - **温存**: データ取得（`fetchData`・focus refresh・abort）・参加率算定・遷移先。
  - **テスト更新**: 表示変更に追随（チップ→「N名」、分数→「N試合」、NavigationMenu/バナー撤去、CTA文言）。回帰=率/試合数/順位/自分の行/遷移先の値は不変。
- **完了条件:** AC-4〜13 の auto-test/grep が通り、`/show-app` で全状態が崩れず表示（AC-9）。

## 実装順序（Wave = 並行実装できるタスクの組）
- **Wave 1**: タスク1（design.md）, タスク3（和紙タイル）— 互いに独立・変更領域が重ならない → 並行可
- **Wave 2**: タスク2（配線・タスク1に依存）, タスク4（Home・タスク3に依存）— 互いに独立（docs/config vs Home.jsx）→ 並行可

> DBマイグレーション: **なし**。本番DB適用: **不要**。
