---
# docs/design/design.md — Match Tracker 脱AIスロップ・デザイン正典
#
# 【重要】このトークンは「参照仕様」であり tailwind.config / index.css には配線しない
# （design-md-anti-slop フォーク#3 の結論。SSOT 化＝config トークン登録は別機能で後続）。
# 実装は既存の inline-hex / washi パターンで、下記トークンの「値」を参照して書く。
# 値の一次情報源は karuta-tracker-ui/src/pages/matches/TorifudaRecord.css（最も洗練された既存セット）。
name: Match Tracker — 和紙 Anti-Slop Design System

colors:
  # シェル（chrome）= 色面ブロッキングの"面"。データではなく領域を分ける緑。
  shell-green: "#4a6b5a"        # 畳グリーン。上部トップシェル・下部5タブ（Layout）
  hero-green: "#33503f"         # 深緑ヒーロー（Home 次の練習）。shell-green の一段深い同系色
  # 地（surface）
  cream: "#f9f6f2"              # 主クリーム面
  cream-deep: "#f2ede6"         # 本文の地（和紙繊維テクスチャの下地。body 背景）
  surface: "#fffdf9"            # 入力・小面のサーフェス白系（matches 系）
  # 墨（ink / data）= データ・数値・順位・率は"紺"。緑=chrome / 紺=data の役割分担。
  ink: "#1A2744"               # 本文の基本色
  navy: "#1A3654"              # データ/数値の"墨"。強調数値・順位・率%
  # タウプ（muted / hairline 系）
  taupe-text: "#8a7568"        # text-muted の確定値（washi-taupe。design-screen で決着）
  taupe-1: "#8a8275"
  taupe-2: "#9a9183"
  taupe-3: "#b3ac9e"
  taupe-tx: "#6b6453"
  # ヘアライン（罫線）= カードの代わりに領域を仕切る細線
  hair-1: "#d8cfbf"
  hair-2: "#e3dccf"
  hair-3: "#d0c5b8"
  # 茶（自分・下線のアクセント）
  brown-name: "#3d2b21"        # 一覧の名前色
  brown-self: "#82655a"        # 自分の行の茶ライン・率
  brown-underline: "#5f3a2d"   # 見出し下線
  # セマンティック（勝敗など）
  green-700: "#15803d"         # 勝ち・肯定
  red-700: "#b91c1c"           # 負け・警告
  # レガシー（新規使用禁止・既存箇所も順次退役）
  legacy-primary-red: "#ef4444"    # tailwind.config の孤児 primary（赤）。実UI未使用
  legacy-material-green: "#4CAF50" # 旧マテリアル（venues 系）
  legacy-material-blue: "#2196F3"  # 旧マテリアル
  legacy-material-red: "#f44336"   # 旧マテリアル

typography:
  sans: "system-ui, -apple-system, 'Hiragino Kaku Gothic ProN', 'Yu Gothic', Meiryo, sans-serif"
  mincho: "'Hiragino Mincho ProN', 'Yu Mincho', 'Noto Serif JP', serif"  # OS 内蔵。web フォント新規導入禁止
  scale:
    display: "2.75rem"  # 明朝ディスプレイ（大きな日付・大数値）
    base: "0.875rem"    # text-sm（高密度の基準）
    small: "0.75rem"    # text-xs（ラベル・補助情報）

rounded:
  pill: "9999px"        # チップ・ピル（PlayerChip）は rounded-full
  control: "8px"        # 入力・ボタンは小 radius
  content: "0"          # コンテンツ区切りに装飾的な角丸（xl/2xl）を使わない

spacing:
  section: "1.5rem"     # セクション間の余白（カードの代わりの構造化）
  row: "1.75rem"        # 高密度の行（28px。参加率バー等）
  gutter: "1.25rem"     # 本文左右パディング（px-5 相当）

components:
  card:
    policy: "原則禁止（allowlist のみ許可）"
    allow: [modal, dialog, dropdown, bottom-sheet, overlay]
    instead: [hairline-divided-list, heading-plus-whitespace-section, table]
  button-primary: "navy 塗り（#1A3654）"
  input: "washi（下線・surface・focus は #82655a）"
  list: "divide-y ヘアライン行"
  chip: "rounded-full ピル（PlayerChip）"

elevation:
  content: none                       # コンテンツに影を付けない
  floating: "modal / dropdown / bottom-sheet のみ影を許可"

references:
  - "karuta-tracker-ui/src/pages/matches/"   # MatchForm / BulkResultInput / MatchResultsView / TorifudaRecord
  - "karuta-tracker-ui/src/pages/pairings/"  # PairingGenerator（脱カード済み）
  - "karuta-tracker-ui/src/pages/videos/"    # 動画倉庫（脱カード済み）
  - "karuta-tracker-ui/src/pages/Home.jsx"   # 本正典の"生きた手本"（design-md-anti-slop パイロット）
---

# Match Tracker Design — 和紙 Anti-Slop

> 競技かるた「北大かるた会」の対戦記録アプリの視覚デザイン正典。**AI コーディングエージェント（`/implement`・`/design-screen`）が毎回参照し、一貫した「和紙・畳」トーンで実装するための単一ファイル。** トークン（front matter）は参照仕様であり tailwind.config には配線しない。Home 固有のピクセル値は [`docs/features/design-md-anti-slop/design-spec.md`](../features/design-md-anti-slop/design-spec.md)、横断的な原則・トークンは本ファイルが正典。

## Overview

密なモバイル UI（片手・通知動線・毎日見る主役画面）を、生成 AI の量産テンプレではなく「和紙・畳」トーンで構造化する。手段は4つ:

1. **色面ブロッキング** — 境界箱ではなく背景色で領域を分ける。緑シェル（`shell-green`）／深緑ヒーロー（`hero-green`）／和紙の地（`cream-deep`）の3面。
2. **明朝ディスプレイ** — 日付・順位・数値・見出しは明朝（`mincho`）。データの"墨"感を出す。
3. **高密度の行** — カードでなくヘアライン＋余白＋背景バーで情報を積む（`text-sm`/`text-xs` 基調）。
4. **和紙繊維テクスチャ** — 本文の地に雲龍紙の楮繊維（決定論的な静的タイル）。

### 最重要原則: 脱スロップ ≠ ミニマリズム

**敵は「浮いた角丸＋影の箱（カード）」だけ。** 豊かさ（大きな明朝数字・全幅の色面・和紙テクスチャ・背景バー）はむしろ**推奨**する。ミニマリズム（引き算・地味化）に倒すのは誤り — design-screen で初回の"引き算"案は「地味すぎ」で棄却された。色面・明朝・テクスチャ・バーで**密度と豊かさ**を出したうえで、箱を消す。

### Anti-Slop の要諦

汎用的な design.md は**それ自体がスロップ**。本ファイルのトークンも Don'ts も、**このコードベースの実物**（既存 washi 実装＝matches/pairings/videos と、実際に残っているスロップ画面）から書き起こしている。ゼロから新デザイン言語を作らない。

## Colors

役割で色を固定する。**緑＝chrome（領域・面）／紺＝data（数値・順位・率）／茶＝自分・アクセント／タウプ＝muted／ヘアライン＝仕切り**。

| 役割 | トークン | 値 | 用途 |
|---|---|---|---|
| シェル緑 | `shell-green` | `#4a6b5a` | 上部トップシェル・下部5タブ |
| ヒーロー深緑 | `hero-green` | `#33503f` | Home 次の練習ヒーロー |
| 主クリーム | `cream` | `#f9f6f2` | 主面 |
| 本文の地 | `cream-deep` | `#f2ede6` | body 背景（和紙テクスチャの下地） |
| サーフェス | `surface` | `#fffdf9` | 入力・小面 |
| インク | `ink` | `#1A2744` | 本文基本色 |
| 墨（データ） | `navy` | `#1A3654` | 数値・順位・率% |
| text-muted | `taupe-text` | `#8a7568` | 補助テキスト（確定値） |
| ヘアライン | `hair-1..3` | `#d8cfbf` 他 | 罫線・仕切り |
| 自分の茶 | `brown-self` | `#82655a` | 自分の行のライン・率 |
| 見出し下線 | `brown-underline` | `#5f3a2d` | セクション見出し下の短い下線 |
| 勝ち/負け | `green-700`/`red-700` | `#15803d`/`#b91c1c` | セマンティック |

**新規使用禁止（レガシー扱い）**:

- **孤児の赤 `primary`**（tailwind.config の唯一のカスタム色。赤スケール `#ef4444` 系）。実 UI では未使用で、これを"ブランドカラー"と誤認してボタン等に塗らないこと。
- **旧マテリアル色** `#4CAF50` / `#2196F3` / `#f44336`（venues 系に残存）。新規画面・改修では使わず、触れた箇所は上表のセマンティック/washi 語彙へ寄せる。
- パレット外の**新規 hex を発明しない**（緑3系統・灰3系統の量産、旧マテリアル色の再利用は禁止）。確定追加色は `hero-green #33503f`・タン繊維系（和紙タイル）のみ。

## Typography

**システム内蔵フォントのみ。web フォント（Inter/Roboto/Noto Sans 等）を新規導入しない。**

- **本文・UI**: `sans`（システムスタック）。密度は `text-sm`（0.875rem）を基準に、ラベル・補助は `text-xs`（0.75rem）。
- **明朝ディスプレイ**: `mincho`（`'Hiragino Mincho ProN','Yu Mincho','Noto Serif JP',serif`。OS 内蔵）。**日付・順位・数値・見出し**に使い、データの"墨"感を出す。大きな日付は `display`（2.75rem）。
- 役割: 見出し＝明朝 or 太字 sans／本文＝sans／ラベル＝`text-xs` タウプ／**数値強調＝明朝 navy**。

## Layout

**共通シェル**: 上部＝`shell-green` のトップバー（Layout の固定バー）、下部＝5タブのボトムナビ（`shell-green`）。この2つが色面の"枠"。

**コンテンツはカードで区切らない。** 余白（`spacing.section`）＋ヘアライン罫線（`hair-*`）＋セクション見出しで構造化する。領域を分けたいときは、箱を描くのではなく**背景色（色面）**か**ヘアライン**を使う。全幅の色面（ヒーロー等）は `-mx-*` で本文パディングを打ち消して端まで伸ばす（色面ブロッキング）。

一覧は `divide-y` のヘアライン行、表形式データは素のテーブル（CSS Grid の列揃え）で並べる。

## Elevation & Depth

**影はレイヤーの分離にのみ使う。**

- **許可**: モーダル / ダイアログ / ドロップダウン / ボトムシート / オーバーレイ（＝浮遊レイヤー）。
- **禁止**: コンテンツ（一覧・見出し・データ行・ヒーロー）に `shadow-*` を付けること。地に貼り付いた情報に影は要らない。深さは色面とヘアラインで表現する。

## Shapes

- **チップ・ピル**: `rounded-full`（`PlayerChip`）。
- **入力・ボタン**: 小 radius（`rounded.control` = 8px 程度）。
- **禁止**: 装飾目的の `rounded-xl` / `rounded-2xl` の大角丸を、コンテンツの箱に付けること（＝カード化の入口）。コンテンツ区切りは角丸ゼロ（`rounded.content`）＝色面かヘアラインで分ける。

## Components

既存アトム／パターンを再利用し、**カードの代替**を明示する。

- **PlayerChip**（`components/PlayerChip.jsx`）: `rounded-full` の選手ピル。
- **ボタン（primary）**: navy 塗り（`#1A3654`）。ベタ塗りの巨大 CTA を"浮かせ"ない（ヒーロー内はヘアライン区切りのアクション行が既定）。
- **入力**: washi（下線・`surface`・focus 時 `#82655a`）。
- **一覧**: `divide-y` のヘアライン行。
- **表**: テーブル（Grid 列揃え）。

**カードの代替パターン（原則こちらを使う）**:

1. **ヘアライン区切りリスト**（`divide-y` + `hair-*`）
2. **見出し＋余白セクション**（`spacing.section`）
3. **テーブル**（列揃えの高密度行）

**参照実装**（迷ったらここを読む）: `matches`（MatchForm / BulkResultInput / MatchResultsView / TorifudaRecord）・`pairings`・`videos`、そして本改修の **Home**（`pages/Home.jsx`＝生きた手本）。

## Do's and Don'ts

### Don'ts（このアプリの実物スロップを名指しで禁止）

- ❌ **アイコン＋見出し＋短説明の「3枚横並び機能カード」グリッド**（Landing に残る生成 AI の定番）。
- ❌ **色帯ヘッダー＋淡色ボディの複合ダッシュボードカード**（旧 Home の TODAY/NEXT カード）。
- ❌ **Trophy／メダルでランキングを装飾**・**丸数字カラーバッジ**の多用（旧 Home の参加率 TOP3）。順位は素の明朝数字、率は行の背景バーで表す。
- ❌ **独立したプログレスバー**を率表示に使う（行背景バーに統合する）。
- ❌ **コンテンツに影**（`shadow-*`）を付ける。
- ❌ **全カードに灰1px罫線**（borderless / hairline 優先）。
- ❌ パレット外の**新規 hex を発明**（緑3系統・灰3系統の量産、旧マテリアル色の新規使用）。
- ❌ **web フォント**（Inter/Roboto 等）の新規導入・**未要望のダークモード**追加。
- ❌ 装飾的な `rounded-xl`/`rounded-2xl` の**浮いた箱**でコンテンツを囲う。

### Do's

- ✅ **ヘアライン（`#d8cfbf`/`#e3dccf` 系）＋余白＋見出し**で構造化する。
- ✅ **色面ブロッキング**（緑シェル／深緑ヒーロー／和紙の地）で領域を分ける。
- ✅ **明朝**を日付・順位・数値・見出しに使い、密度と豊かさを出す。
- ✅ 一覧は `divide-y` ヘアライン行、表データはテーブル。率は**行の背景バー**。
- ✅ **matches・pairings・videos・Home** の脱カード実装を参照元にする。
- ✅ 色は**トークン名で参照**し、役割（緑=chrome / 紺=data / 茶=自分 / タウプ=muted）を守る。
