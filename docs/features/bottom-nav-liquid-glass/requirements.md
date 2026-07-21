---
status: completed
completed_sections: [ユーザーストーリー, 機能要件, Acceptance Criteria と Non-goals, 技術的制約・契約]
next_section: デザイン収束ループ（design-screen）
design_required: true
mode: change
approved_at: 2026-07-21
---
# ボトムナビ リキッドグラス化 要件定義書（改修）

## 0. 現行挙動ベースライン（変更前）

- 実装: [karuta-tracker-ui/src/components/Layout.jsx](../../../karuta-tracker-ui/src/components/Layout.jsx)、表示制御は [BottomNavContext](../../../karuta-tracker-ui/src/context/BottomNavContext.jsx)
- ボトムナビは画面下端に**左右いっぱいの不透明グリーンバー**（`bg-[#4a6b5a]` / 上ボーダー `#3d5a4c`）として固定表示。
- 項目は5つ・順序固定: **Home**(`/`) / **Match**(`/matches/results`) / **Schedule**(`/practice`) / **Record**(`/matches`) / **Settings**(`/settings`)。各項目は **lucide-react アイコン＋テキストラベル**を縦積みで表示。
- アクティブ判定は前方一致を含む `isBottomNavActive()`。アクティブ項目は**白アイコン＋白の太字ラベル**、非アクティブは `#b8ccbf`。
- `BottomNavContext.isVisible` で表示/非表示を制御。非表示時は内側 div に `translate-y-full` でスライドアウト＋ `pointer-events-none` / `tabIndex=-1` / `aria-hidden`。`fixed` 要素自体には transform を持たせず iOS Safari の fixed 解除を回避（既存の意図的な構造）。
- 下端は `env(safe-area-inset-bottom)` を尊重。本文は `paddingBottom: calc(3.5rem + safe-area)` でバー高さ分を確保。
- 非表示連携の実利用: [MatchCommentThread.jsx](../../../karuta-tracker-ui/src/pages/matches/MatchCommentThread.jsx) が入力フォーカス中に `setVisible(false)`。

## 1. 概要

- **目的**: ボトムナビを、iOS26 のリキッドグラス／Instagram・Tinder のような**半透明ガラス調の浮いた角丸ピル**に刷新し、モダンな見た目にする。
- **背景・動機**: iOS26 で登場したリキッドグラスの質感を取り入れたい。ただし本アプリの主要動作環境は **iPhone の Safari（WebKit）**であり、Web で技術的に到達できる範囲に収める。

## 2. ユーザーストーリー

- 対象ユーザー: 本アプリを iPhone で日常利用する会員・管理者。
- 目的: よりモダンで洗練されたナビ体験。ガラス越しに背景が透けることで奥行きを感じられる。
- 利用シナリオ: 各画面を行き来する際、下部のガラスピルで現在地（アクティブ項目）を認識しつつタップ移動する。

## 3. 機能要件

### 3.1 画面と遷移（見た目は design-spec 参照）

- 対象は**ボトムナビゲーション単一コンポーネント**（全画面共通で表示される [Layout.jsx](../../../karuta-tracker-ui/src/components/Layout.jsx) の `<nav>`）。**画面遷移・ルーティングは一切変更しない**（Home/Match/Schedule/Record/Settings の遷移先・順序は現状のまま）。
- 見た目の刷新点（詳細・確定値は design-spec.md が正典）:
  - **形状**: 左右端に余白を持つ**浮いた角丸ピル**（画面下端から少し浮かせる）。
  - **素材**: **ニュートラル（無彩色）の半透明ガラス**＋ `backdrop-filter: blur() saturate()` で背後が透けてぼける。（当初グリーンガラス案から変更。白アイコンの視認性のため無彩色は濃いめ）
  - **質感**: 上縁の**光ハイライト**（inset）＋斜めの**鏡面スジ（sheen）**で立体感を出す。
  - **項目表示**: **アイコンのみ**（テキストラベルは非表示化）。
  - **アイコンの塗り分け**: 非アクティブ=アウトライン / アクティブ=**ソリッド（塗り／Instagram風）**。Home/練習/戦績/設定 は **Heroicons**（outline↔solid）、対戦のみ lucide の剣（Heroicons に剣が無いため。アクティブ時 `fill`）。
  - **アクティブ表現**: 選択中項目のアイコン背後に **横長楕円のカプセル状ハイライト**を敷く。
  - **カプセル移動**: カプセルは 1個だけをスロット間で**スライド**（タップ時は該当位置へ glide、瞬間移動しない）。
  - **インタラクション演出**: タップ/ドラッグ時に「一瞬膨らんで戻る」スプリング演出（iOS26 の bubbly glass を CSS スケールで近似）。

### 3.2 ビジネスルール（処理ルール・制約条件）

- 項目セット・ルート・順序は**変更しない**（アイコンの図柄は塗り分けのため Heroicons/lucide 混在に変更）。
- アクティブ判定ロジック（`isBottomNavActive` の前方一致含む）は**現状と同一の結果**を返す。
- `isVisible` によるスライド表示/非表示制御は**そのまま維持**（見た目の器だけ差し替える）。
- **ドラッグでタブ切替（新インタラクション・A案）**: カプセルを掴んで水平ドラッグすると指に追従し、離した位置の最寄り項目へ遷移する。**タップ操作は主経路として維持**（ドラッグは補助）。ドラッグ中はページスクロールと競合しないこと（`touch-action: none` ＋ pointer capture、4px 未満の移動はタップ扱い）。
- **タップ移動時**はカプセルが該当位置へ滑らかに移動（glide）し、ピル全体が流れる一発スプリング演出をする。**ドラッグ中**はカプセル・ピルが膨らんで保持し、離すと元サイズへ戻る。
- **アクセシビリティ**: アイコンのみ化でテキストによるアクセシブル名が失われるため、各リンクに `aria-label`（従来ラベル文言: Home / Match / Schedule / Record / Settings 相当。文言は日本語化も design/実装時に検討可）を付与し、スクリーンリーダーで項目名が読み上げられること。
- **タップ領域**: 各項目のタップ領域は 44px 以上を確保（アイコンのみでも押しやすさを落とさない）。
- **safe-area**: 浮いたピルでも `safe-area-inset-bottom` を尊重し、iPhone のホームインジケータ領域にめり込まない。本文最下部がピルの下に隠れない下パディングを確保する。

## 4. Acceptance Criteria

| ID | 条件（客観的に判定できる文） | 検証手段 |
|----|------|------|
| AC-1 | ボトムナビは5項目（Home/Match/Schedule/Record/Settings）を現状の遷移先・順序のまま保持する | auto-test |
| AC-2 | 各項目は可視テキストラベルを表示せず、アイコンのみで表示される | auto-test |
| AC-3 | 各項目リンクは `aria-label`（項目名）を持ち、スクリーンリーダーで項目名が読み上げられる | auto-test |
| AC-4 | アクティブ判定は現状と同一で、各パスで正しい項目がアクティブになる（前方一致含む） | auto-test |
| AC-5 | アクティブ項目の背後にカプセル状ハイライト要素が表示され、非アクティブには表示されない | auto-test |
| AC-6 | `isVisible=false` でナビがスライドアウトし、`aria-hidden` / `tabIndex=-1` / `pointer-events-none` になる（回帰） | auto-test |
| AC-7 | MatchCommentThread のフォーカス連動での非表示（`setVisible(false)`）が維持される（回帰） | auto-test |
| AC-8 | 既存のフロントエンドテスト・lint がすべて成功する（回帰） | auto-test |
| AC-9 | バーが半透明のグリーンガラスとなり、背後のコンテンツが透けてぼける（backdrop-filter が適用される） | verify |
| AC-10 | バーが画面下端から浮いた角丸ピル形状で、左右に余白を持つ | verify |
| AC-11 | 上縁の光ハイライトと斜めの鏡面スジで立体的なガラス質感が出る | verify |
| AC-12 | `safe-area-inset-bottom` が尊重され、最下部コンテンツがピルの下に隠れない | verify |
| AC-13 | iPhone Safari（WebKit）実機で backdrop-filter のすりガラスが崩れず表示される | manual |
| AC-14 | アクティブ項目のアイコンがソリッド（塗り）、非アクティブがアウトラインで表示される（Home/練習/戦績/設定=Heroicons、対戦=lucide剣のfill） | auto-test |
| AC-15 | カプセルは 1個で、アクティブ項目位置にのみ表示される（各リンク内の重複描画をしない） | auto-test |
| AC-16 | カプセルを水平ドラッグすると指に追従し、離した位置の最寄り項目へ遷移する | verify |
| AC-17 | タップ移動でカプセルが該当位置へ滑らかに移動し、瞬間移動しない | verify |
| AC-18 | タップ/ドラッグ時にピル・カプセルの「膨らんで戻る」演出が出る | verify |
| AC-19 | ドラッグ中にページが縦スクロールしない（`touch-action`／pointer capture で競合しない） | verify |
| AC-20 | カプセル位置計算・最寄りスロット判定などの純ロジックが単体テストで検証される | auto-test |

## 5. Non-goals（今回やらないこと）

- **屈折／レンズ効果（SVG `feDisplacementMap` による背景歪み）は実装しない**。iOS WebKit は `backdrop-filter: url(#svg)` を非対応で iPhone では映らないため。CSS 関数形（blur/saturate）＋擬似ハイライトで質感を出す方針に確定。
- 項目セット・ルート・順序の変更（アイコンの図柄・塗りは変更するが、遷移先・並びは不変）。
- ドラッグでの**並び順カスタマイズ／保存**（B案）。今回のドラッグは「切替」のみ（保存を伴わない）。
- ダークモード対応（アプリは現状ライトのみ。背景 `#f2ede6` 前提）。
- ボトムナビ以外の UI（ヘッダー等）のガラス化。
- `isVisible` スライド表示制御ロジック自体の仕様変更（器の見た目だけ差し替え）。

## 6. 技術的制約・契約

- **変更禁止挙動**: `BottomNavContext` の `isVisible` スライド／アクティブ判定結果／各項目のルート／safe-area 尊重／MatchCommentThread のフォーカス連動非表示。
- **WebKit 制約（最重要）**: `backdrop-filter` に `url()` の SVG フィルタ参照を使わない（iOS 非対応）。使えるのは CSS 関数形（`blur()` `saturate()` 等）のみ。この制約は Non-goal（屈折不採用）の技術的根拠。
- **プレビューの非等価性**: 本セッションの Browser プレビューは中身が Chromium。ガラス表現の最終検証は**プレビューだけでは不十分**な項目（AC-13）があり、iPhone 実機確認はユーザー領域。ただし今回採用する CSS 関数形の backdrop-filter は iOS でも同挙動のため、プレビューでの見た目確認は概ね有効。
- **transform × backdrop-filter の共存**: 既存はスライドを内側 div の `translate-y` で実現。transform を持つ要素に backdrop-filter を載せるとクリップ不具合が出ることがあるため、レイヤー構成（backdrop-filter を載せる要素と transform を持つ要素の分離）は技術計画で確定する。
- **アクセシビリティ契約**: icon-only 化に伴い `aria-label` 必須（現状はラベルテキストがアクセシブル名を提供していた）。
- **利用技術**: Tailwind v3。デザイントークンは `tailwind.config.js` / `src/index.css`。
- **新規依存追加**: `@heroicons/react`（アクティブ=ソリッド／非アクティブ=アウトラインの塗り分け用。lucide はアウトライン専用で歯車・棒グラフが `fill` で潰れるため）。実装タスクで `package.json` に追加する。
- **個別 transform プロパティ**: スライド（translateY）と膨らみ（scale）を分離するため CSS 個別 `scale` プロパティを使用（iOS Safari 14.1+ 対応、iOS26 は当然対応）。これでスプリング演出が translateY を壊さない。
- **ドラッグ実装**: Pointer Events ＋ `setPointerCapture` ＋ `touch-action: none`。カプセル位置は `trackRef` の実測幅から px 計算（ResizeObserver）。純ロジック（位置→最寄りスロット等）は単体テスト可能に切り出す（ドラッグ実挙動は実機/verify）。
- **技術論点（design で解決済み）**: (a) スライド transform とガラス backdrop-filter は**同一要素（ピル本体）**に載せて回避、(b) カプセルは 1個の要素を translate でスライド、(c) 鏡面スジはインライン background の子要素。

## 7. 設計判断の根拠

- **屈折を捨てて iPhone 実機の上限に倒した**理由: リキッドガラスの核心である屈折は SVG displacement に依存し、iOS WebKit の `backdrop-filter: url()` 非対応により iPhone では描画されない（＝一番見せたい効果が一番見せたい端末で消える）。すりガラス＋縁の光＋鏡面スジ＋浮いたピルで「屈折している質感」を目の錯覚として成立させるのが、クロスブラウザで確実に出荷できる現実的な最大値。
- **アイコンのみ＋カプセルアクティブ**: Instagram/Tinder・iOS26 の参照イメージに合わせスマートさを優先。分かりやすさの低下は `aria-label` とアクティブカプセルの明確な視認性で補う。
- **ニュートラル半透明ガラス**: 当初はブランドの緑を半透明化する案だったが、ユーザー判断で**無彩色の半透明**に変更。白アイコンの視認性のため濃いめの無彩色にする。
- **アイコンの塗り分けに Heroicons を追加**: lucide はアウトライン専用で、`fill` すると歯車（設定）・棒グラフ（戦績）が潰れることを design で実証。塗り版を持つ Heroicons へ差し替え、剣が無い対戦のみ lucide を維持。
- **ドラッグ切替（A案）採用**: 「掴んで移動」の tactile な操作をユーザーが要望。並び順保存（B案）は別スコープとして Non-goal。

## デザインへの宿題（→ /design-screen）: すべて design-spec.md で解決済み（status: locked）

## 変更履歴

- 2026-07-21: 新規作成（改修）。不透明グリーンバー＋ラベル付き5項目 → 半透明ガラスの浮いた角丸ピル＋アイコンのみ＋カプセルアクティブへ刷新。屈折/レンズ効果は iOS 非対応につき Non-goal に確定。
- 2026-07-21: design 収束で emergent を反映。(1) ガラスをグリーン→**ニュートラル無彩色**に変更、(2) アクティブ=ソリッド/非アクティブ=アウトラインの**塗り分け**（`@heroicons/react` 追加、対戦のみ lucide 剣）、(3) **ドラッグでタブ切替（A案）**追加、(4) カプセルを1個スライド＋**横長楕円**化、(5) タップ/ドラッグの**スプリング演出**追加。AC-14〜20 追加、Non-goals に「並び順保存（B案）」追記。
