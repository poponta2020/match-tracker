---
status: completed
design_required: true
completed_sections: [ユーザーストーリー, 機能要件, Acceptance Criteria と Non-goals, 技術的制約・契約]
approved_at: 2026-07-16
---

# design.md（脱AIスロップ・デザイン正典）要件定義書

## 1. 概要

- **目的**: プロジェクトの視覚デザインが全体的に「AIっぽい（生成AIの量産テンプレ的）」印象になっている問題を、**AIコーディングエージェントが毎回参照するデザイン正典 `docs/design/design.md`** を新設することで根治に向かわせる。今後の `/implement`・`/design-screen` がこの正典を参照し、一貫した「和紙・畳」トーンで実装するようにする。
- **背景・動機**:
  - 現状、実パレット（畳グリーン `#4a6b5a` / クリーム面 `#f9f6f2`・`#f2ede6` / インク紺 `#1A2744`・`#1A3654` / タウプ罫線 `#82655a` 系）は **tailwind.config に未登録**で、JSX の inline hex **1,103箇所** ＋ matches 系 CSS の局所変数 ＋ SCREEN_LIST の散文に **三分裂**している。config にあるのは実UIで未使用の孤児トークン（赤 `primary`）だけ。
  - 「AIっぽさ」は **未移行の旧カード画面（Landing / Home / Lottery / settings / practice / densuke 等）に偏在**。逆に **matches（MatchForm / TorifudaRecord）・pairings・videos は既に「脱カード＝ヘアライン＋余白＋文字階層＋和紙トーン」に刷新済み**で、社内に手本がある。
  - Google が公式フォーマット `google-labs-code/design.md` を公開（単一ファイル・YAML front matter トークン＋Markdown 散文・8セクション標準）。コミュニティ標準（awesome-design-md）も単一ファイル。
- **ゴールデンスタンダードの調査結論（成果物構造）**: **単一ファイル**（`docs/design/design.md`）。セクション順は `Overview → Colors → Typography → Layout → Elevation & Depth → Shapes → Components → Do's and Don'ts`。分割は標準に含まれないため採用しない。
- **Anti-Slop の要諦**: 汎用的な design.md は**それ自体がスロップ**。トークンも Don'ts も**このコードベースの実物（既存 washi 実装と実際のスロップ）から**書き起こす。

## 2. ユーザーストーリー

- **対象ユーザー**: 本リポジトリで作業する開発者／AIエージェント（`/implement`・`/design-screen`）。エンドユーザーは間接的に「一貫した非AIっぽいUI」の恩恵を受ける。
- **目的**: 画面ごと・セッションごとにブランドルールを再指定せずとも、出力が「和紙 Anti-Slop」言語で一貫すること。とりわけ **AIが多用しがちなカード表現を抑制**したい。
- **利用シナリオ**:
  1. 開発者が新画面/改修を `/implement` する際、エージェントが `docs/design/design.md` を読み、カードでなくヘアライン＋余白＋セクションで構造化する。
  2. `/design-screen` が design.md をデザイン基準として参照する。
  3. 今回のパイロット（Home）が「正典に準拠した生きた手本」となり、以降の未移行画面改修（後続Issue）はこれを模倣する。

## 3. 機能要件

### 3.1 画面と遷移

- 本機能の主成果物は**ドキュメント（`docs/design/design.md`）**であり、恒常的な新規画面は無い。
- **パイロット改修対象は Home（`/`＝認証後トップ、`karuta-tracker-ui/src/pages/Home.jsx`）のみ**。画面遷移・情報構造・データは変えず、**視覚表現のみ**を脱カード化する。遷移地図に変更なし。
- Home 以外の未移行画面は**今回対象外**。design.md を参照して後続Issueで順次移行する。

### 3.2 成果物の中身（design.md が満たすべき仕様）

**(a) YAML front matter（トークン＝参照仕様）** — 現行 washi 語彙から抽出したセマンティック名で定義。**tailwind.config には配線しない**（§6 の契約参照）。少なくとも `colors` / `typography` / `rounded` / `spacing` / `components` を含む。抽出元は最も洗練された既存セット＝ matches 系 CSS 局所変数（`--surface #fffdf9`, `--ink #1A2744`, `--navy #1A3654`, `--taupe*`, `--hair*`, semantic `green-700 #15803d` / `red-700 #b91c1c`）＋ シェルの畳グリーン `#4a6b5a` 系・クリーム `#f9f6f2`/`#f2ede6`。

**(b) 散文セクション（Google標準8節・この順）**:
1. **Overview** — 「和紙・畳」トーンの狙い、密なモバイルUI、Anti-Slop の宣言。
2. **Colors** — セマンティックトークンと用途。孤児の赤 `primary`・旧マテリアル色（`#4CAF50`/`#2196F3`/`#f44336` 等 venues 系）を**新規使用禁止（レガシー扱い）**と明記。
3. **Typography** — システムフォントスタック維持（web フォント導入禁止）。`text-sm`/`text-xs` 中心の密度、見出し/本文/ラベル/数値強調の役割。
4. **Layout** — 緑トップバー＋下部5タブの共通シェル。コンテンツは**カードでなく余白＋ヘアライン罫線＋セクション見出し**で構造化。
5. **Elevation & Depth** — 影は**浮遊レイヤー（モーダル/ドロップダウン/ボトムシート）のみ**。コンテンツに影を付けない。
6. **Shapes** — チップ/ピルは `rounded-full`（`PlayerChip`）、入力/ボタンは小 radius、**装飾的 `rounded-xl/2xl` カードを避ける**。
7. **Components** — 既存アトム（`PlayerChip`）、ボタン（navy primary）、入力（washi）、一覧（`divide-y` ヘアライン行）、表（テーブル）。**カードの代替パターン**（ヘアライン区切りリスト / 見出し＋余白セクション / テーブル）を明示。参照実装＝matches/pairings/videos。
8. **Do's and Don'ts** — **本アプリ固有のスロップを名指しで禁止**（§3.3）。

### 3.3 カード方針（原則禁止＋許可リスト）＋ 固有 Don'ts

- **原則**: コンテンツの区切りに `bg-white … rounded-* … shadow-*` のカードを**使わない**。代わりにヘアライン罫線・余白・セクション見出しで構造化する。
- **許可リスト（カードが許される場面）**: モーダル / ダイアログ / ドロップダウン / ボトムシート / オーバーレイ、および**明確に独立した単一操作単位**。
- **固有 Don'ts（各々このアプリの実物スロップ由来）**:
  - ❌ アイコン＋見出し＋短説明の「**3枚横並び機能カード**」グリッド（Landing の生成AI定番）
  - ❌ **色帯ヘッダー＋淡色ボディの複合ダッシュボードカード**（Home）
  - ❌ **Trophy/メダルでランキング装飾**・丸数字ランクバッジの多用（Home TOP3）
  - ❌ 全カードに灰1px罫線（borderless / hairline 優先）
  - ❌ **コンテンツに影**（`shadow-*`）を付ける
  - ❌ パレット外の新規 hex を発明（緑3系統・灰3系統の量産・旧マテリアル色の新規使用）
  - ❌ web フォント（Inter/Roboto 等）導入・未要望のダークモード追加
- **Do's**: ヘアライン（`#e2d9d0`/`#d8cfbf` 系）＋余白＋見出しで構造化 ／ matches・pairings・videos の脱カード実装を参照元に ／ 一覧は `divide-y` ヘアライン行・表データはテーブル ／ 色はトークン名で参照。

### 3.4 Home パイロット改修（脱カード・washi。確定デザインは design-spec.md が正典）

確定デザイン（全状態・ピクセル値・和紙パラメータ）は [design-spec.md](design-spec.md)（`status: locked`）を参照。要点:

- **FE のみ**。バックエンド `/api/home` DTO は不変。実装は既存 inline-hex / washi パターン（tailwind トークン非配線＝フォーク#3）。
- 深緑ヒーロー `#33503f` を**常時表示・単一デザイン化**（登録状態で色帯を分けない・null 時は空状態）。CTA はヒーロー内ヘアライン行「対戦確認画面へ／参加登録」。
- 参加率は**カードでなく行の背景バー**（順位｜名前＋参加試合数｜%）。団体ヘッダーに総試合数。Trophy・丸数字バッジ・プログレスバー独立表示は廃止。
- 背景に**和紙繊維テクスチャ**（決定論的 seamless タイル画像・§6 技術メモ）。
- **設計原則: 脱スロップ ≠ ミニマリズム**（敵は浮いた角丸＋影の箱のみ。色面・明朝・テクスチャ・バーは推奨）。
- §3.4a の意図的例外を除き、参加率算定・遷移先・データ表示は改修前後で不変（回帰）。

### 3.4a Home 改修の意図的例外（visual-only を超える変更。§4 AC 化）

1. 参加者チップ（名前一覧＋待ち/キャンセルの別セクション）→ **人数「N名」（確定参加者のみ）に集約**。待ち/キャンセルは Home から落とす。
2. **トップバー（`NavigationMenu`）撤去**（ユーザー名・`/profile` アイコン）。プロフィールは**設定→プロフィール**で到達可（`SettingsPage.jsx` 確認済み）。
3. 参加率の分数「X/Y」→ **団体ヘッダー総試合数（Y）＋各行の参加数「N試合」（X）**。
4. CTA 文言「組み合わせを作成」→ **「対戦確認画面へ」**（遷移先同一 `/pairings?date=`）。
5. **繰り上げオファーバナー撤去**（Home から落とす。通知一覧で確認）。
6. 次の練習ヒーローの**常時表示・単一デザイン化**（既存 API は未登録でも次の練習を返すため BE 改修不要）。

### 3.5 正典の配線（enforcement）

design.md を「正典」として機能させるため、**同一コミット**で以下を更新する：
- `.claude/project-profile.md` の `## design-system`（現状 tailwind.config＋"claude.ai/design" を指す → design.md を一次参照に）
- `CLAUDE.md`（UI改修時に `docs/design/design.md` を参照・厳守する旨をナビゲーション規約へ1行追加）
- docs レジストリ（profile の `## docs`）に design.md を登録

## 4. Acceptance Criteria

| ID | 条件（客観的に判定できる文） | 検証手段 |
|----|------|------|
| AC-1 | `docs/design/design.md` が存在し、Google標準8セクション（Overview/Colors/Typography/Layout/Elevation & Depth/Shapes/Components/Do's and Don'ts）の見出しと、パース可能な YAML front matter（`colors`/`typography`/`rounded`/`spacing`/`components` を含む）を持つ | auto-test（見出し走査＋front matter パース） |
| AC-2 | front matter のトークンが現行 washi パレット由来のセマンティック名で、孤児の赤 `primary` と旧マテリアル色を「新規使用禁止」と本文に明記している | manual |
| AC-3 | Do's and Don'ts が本アプリ固有スロップ（3枚横並び機能カード／色帯複合カード／Trophy TOP3／コンテンツ影付きカード）を名指しで禁止している | manual |
| AC-4 | `Home.jsx` のコンテンツ区切りカード（`bg-white` かつ `rounded` かつ `shadow` の同一要素）が 0 件（許可リスト該当のモーダル等を除く） | auto-test（`Home.jsx` ソースのパターン走査アサート） |
| AC-5 | Home 改修でパレット外の新規 hex が導入されていない（既存 washi/inline-hex＋深緑 `#33503f`・タン繊維系のみ） | verify |
| AC-6 | **【回帰・要】** §3.4a の6例外を除き、Home の参加率算定・遷移先・データ値（率/試合数/順位/自分の行）が改修前後で不変 | auto-test（既存 Home テスト更新後 green）＋ verify |
| AC-7 | design.md が正典として配線されている：`.claude/project-profile.md` の `## design-system`・`CLAUDE.md` のナビゲーション規約・docs レジストリが design.md を参照 | auto-test（各ファイルの参照文字列 grep） |
| AC-8 | 既存テスト・lint・typecheck がすべて成功する | auto-test |
| AC-9 | Home を実機表示し、脱カード後もデータ欠落・レイアウト崩れが無く、AIっぽさが低減している。全状態（TODAY/NEXT/未登録/練習なし・複数団体/1団体・自分がTOP3圏外）を確認 | verify |
| AC-10 | `Home.jsx` が `NavigationMenu` を import/描画していない（トップバー撤去。例外2） | auto-test（ソース grep） |
| AC-11 | `Home.jsx` が `hasPendingOffer` バナー・`PlayerChip`（参加者チップ）・Trophy を描画していない（例外1,5＋Trophy廃止） | auto-test（ソース grep） |
| AC-12 | 次の練習ヒーローの CTA 文言が「対戦確認画面へ」で遷移先が `/pairings?date=`、未登録時は「参加登録」→`/practice/participation`（例外4,6） | verify |
| AC-13 | 本文背景に和紙テクスチャが**決定論的な静的アセット**（コミット済み画像）として `repeat` 適用され、`Home` マウント時に `Math.random` の canvas を実行しない | auto-test（アセット存在＋Home に乱数 canvas 呼出しが無い grep）＋ verify |
| AC-14 | design.md 本文に「脱スロップ ≠ ミニマリズム（敵は浮いた角丸＋影の箱のみ。色面・明朝・テクスチャ・バーは推奨）」の原則が明記されている | manual |

## 5. Non-goals（今回やらないこと）

- **tailwind.config / index.css へのトークン登録（SSOT化）**（フォーク#3の結論。design.md トークンは参照仕様に留める。別機能として後続）
- **inline hex の一斉置換**（後続）
- **Home 以外の未移行画面**（Landing / Lottery / settings / practice / densuke 等）の改修（design.md を参照して後続Issueで）
- Home の**ロジック・データ・API・遷移の変更**（visual-only 厳守）
- 新規 web フォント導入・ダークモード追加
- 既に locked の matches / pairings / videos の**再改修**（参照元として温存）
- design.md の**多ファイル分割**（標準は単一ファイル）

## 6. 技術的制約・契約

- **Home 改修は FE のみ**：`/api/home` の DTO・参加率算定・遷移先は不変（AC-6 回帰）。§3.4a の6例外のみ意図的変更。データ取得ロジック（fetch/focus refresh/abort）は温存。
- **design.md front-matter のトークンは「参照仕様」**であり **tailwind.config には配線しない**（フォーク#3）。Home も既存 inline-hex/washi パターンで実装する。
- **パレットは現行 washi 語彙＋確定追加色に限定**（深緑ヒーロー `#33503f`・タン繊維系）、その他の新規 hex 禁止。孤児 `primary`・旧マテリアル色はレガシー扱い。
- **text-muted は washi-taupe に確定**（`#8a7568` 系。design-screen で決着）。データ/数値は"墨"の紺 `#1A3654`。
- **和紙テクスチャは決定論的 seamless タイル画像**として実装（毎マウントの乱数 canvas 禁止・AC-13）。生成スクリプトはコミット。
- **システムフォント維持**（web フォント禁止）。明朝は OS 内蔵の Hiragino Mincho / Yu Mincho / Noto Serif JP。
- design.md 配線（profile / CLAUDE.md / docs レジストリ）は**同一コミット**で更新（CLAUDE.md 規約#4 / DoD D2）。

## 7. デザインへの宿題（→ /design-screen design-md-anti-slop）— **解決済み**

design-screen（v15 で locked）で全て決着。詳細は [design-spec.md](design-spec.md)。
- Home 各要素の脱カード分解 → 深緑ヒーロー＋行背景バー＋和紙テクスチャに確定。
- Trophy／プログレスバー／丸数字バッジ → 廃止（率は行背景バー・順位は素の明朝数字）。
- text-muted → washi-taupe `#8a7568` に確定。
- 許可リスト該当のカード的要素 → Home には無し（モーダル系のみで Home 内に登場せず）。

## 8. 設計判断の根拠

- **単一ファイル**: Google 公式・コミュニティ標準がともに単一ファイル。本プロジェクト規模では分割は保守コスト増で過剰。
- **washi 語彙の正典化**: ゼロから新デザイン言語を作るとそれ自体が汎用スロップになる。既に locked で洗練された matches/pairings/videos の実装を canon 化するのが最短かつ最も一貫する。
- **doc + 1画面パイロット**: doc 単独では inline hex が残り脱AI化が遅効。1画面（Home）を正典準拠に改修して「生きた手本」を作ることで、後続の未移行画面改修が模倣で進む。全画面一括は 1PR=1機能 と衝突するため見送り。
- **フォーク#3の解決**: ユーザーは「doc+パイロット」を選び「SSOT化（config トークン登録）」を選ばなかった。よってパイロットは tailwind トークンに配線せず既存パターンで実装し、config トークン化は独立した後続機能とする。
