---
status: locked
round: 6
chosen_direction: iPhone実機上限版のリキッドグラス（すりガラス+縁ハイライト+鏡面スジ+浮いた角丸ピル・ニュートラル半透明・アイコンのみ・スライドカプセル・ドラッグ切替）
prototype_branch: design/bottom-nav-liquid-glass
prototype_base: f04d227a
preview_url: http://localhost:5174/design-proto/glass-nav
locked_at: 2026-07-21
---
# ボトムナビ リキッドグラス化 design-spec（確定）

対象: [karuta-tracker-ui/src/components/Layout.jsx](../../../karuta-tracker-ui/src/components/Layout.jsx) のボトムナビ `<nav>`。要件は [requirements.md](requirements.md)。レイアウト詳細の正典は [design-prototype.patch](design-prototype.patch)。

## 確定した見た目

- **浮いた角丸グリーンガラスのピル**: `<nav>` は fixed のまま transform 無し（iOS Safari の fixed 解除回避）。ピル本体は `max-w-md` 中央寄せ・左右 `px-4`・下端 `safe-area + 10px`・`rounded-[28px]`。
- **ガラス素材**: `backdrop-filter: blur(18px) saturate(190%)` ＋ **ニュートラル（無彩色）半透明グラデ** `rgba(90,92,96,0.6)→rgba(58,60,64,0.7)`（当初のグリーンから変更）。上縁の光 `inset 0 1.5px 1px rgba(255,255,255,0.55)`＋下縁陰＋外側ドロップシャドウ。斜めの鏡面スジ（skew-x -18deg のグラデ帯）。
  - **アイコン色との関係**: 現状アイコンは白（アクティブ=白ソリッド）。白の視認性を保つため無彩色は**濃いめ**にしている。将来「白っぽい明るいガラス」にする場合はアイコンを濃色へ反転する必要がある（実装後の目視確認で最終判断）。
- **アイコンのみ**（ラベル削除）。各リンクに日本語 `aria-label`（ホーム/対戦/練習/戦績/設定）＋アクティブに `aria-current="page"`。アイコンは `h-7 w-7`（28px）。
- **アイコンの塗り分け（Instagram風）**: 非アクティブ=アウトライン / アクティブ=ソリッド。
  - Home/練習/戦績/設定 = **Heroicons**（`@heroicons/react/24/outline` ↔ `/24/solid`）。歯車・棒グラフも綺麗に塗れる。
  - 対戦のみ **lucide の Swords**（Heroicons に剣が無いため）。アクティブ時は `fill="currentColor"` で塗る。
  - 非アクティブの Heroicons は `strokeWidth 1.8` で lucide 線幅に寄せる。
- **アクティブカプセル**: 半透明白の**横長楕円**（`62×48px`、`rounded-full`）＋ inset ハイライト。アイコンの背後（`z-index:2`、アイコンは `z-1`）。

## 確定したインタラクション

**カプセルは 1個だけ**をスロット間で `translate` させる（各リンク内の条件表示＝瞬間移動をやめた）。位置は `slotWidth*(index+0.5)` を中心に px 計算（`trackRef` を ResizeObserver で実測）。

- **タップで移動**: ルート変更（本実装）／`protoIndex`（プレビュー）で activeIndex が変わり、カプセルが該当位置へ **glide（`transform 427ms` バネ easing）**。同時に:
  - ピル全体が **流れる一発パフ**（`@keyframes navPuff` で個別 `scale` を 1→1.045→1、`240ms ease-out`。毎回 animation リスタート）。「膨らみ切って止まる」を避けるためにキーフレーム化。
  - カプセルも一瞬膨らんで戻る（`interacting` 240ms＋楕円 `scale 1.18`）。
- **ドラッグで切替（A案 drag-to-switch）**: カプセルを掴んで指に追従（`dragging` 中は transition なし）。掴んでいる間は**カプセルもピルも膨らんで保持**（カプセル `scale 1.18`、ピル個別 `scale 1.035`、`scale 200ms ease-out`）。指の近傍アイコンを先取りハイライト（`highlightIndex`）。**離すと最寄りスロットへ snap → 遷移 → 元サイズへ戻る**。
- **タップ/ドラッグ判定**: pointerdown からの移動が `4px` 未満はタップ、以上はドラッグ。`setPointerCapture` でドラッグ中のイベントを確保。カプセルはアクティブスロットのみを覆うので、他アイコンのタップ（Link）は素通り＝ネイティブ遷移。
- **スケール速度**: ユーザー要望で「拡大→縮小」「移動」を 0.75倍速（ゆっくりめ）に調整済み（パフ 180→240ms、カプセル移動 320→427ms）。

## 主要な実装ポイント（iOS/WebKit）

- **backdrop-filter × transform**: backdrop-filter は ancestor に transform があると背景を拾えず透明化する。スライド用 `translateY` と backdrop-filter を**同一要素（ピル本体）**に載せて回避。
- **個別 transform プロパティ**: ピルの「スライド(translateY)」と「膨らみ(scale)」を分離するため CSS 個別 `scale` プロパティを使用（iOS Safari 14.1+ 対応）。これで navPuff アニメが translateY を壊さない。
- **touch-action: none** ＋ `setPointerCapture` でドラッグとページスクロールの競合を防ぐ。
- 屈折/レンズ効果は不採用（iOS WebKit は `backdrop-filter: url(#svg)` 非対応）。要件の Non-goal 通り。

## 主要トークン/数値

| 項目 | 値 |
|---|---|
| ガラス blur / saturate | 18px / 190% |
| ガラス背景 | `linear-gradient(rgba(90,92,96,.6)→rgba(58,60,64,.7))`（ニュートラル無彩色） |
| ピル角丸 / 幅 | `28px` / `max-w-md`（左右 px-4） |
| カプセル | `62×48px` 楕円、`rgba(255,255,255,.22)` |
| ピル膨らみ scale | `1.035`（保持）/ navPuff `1.045`（一発） |
| カプセル膨らみ scale | `1.18` |
| カプセル移動 | `transform 427ms cubic-bezier(0.34,1.56,0.64,1)` |
| パフ | `navPuff 240ms ease-out` |
| 本文下パディング | `calc(5rem + safe-area)` |

## 要件への宿題（→ /define-feature bottom-nav-liquid-glass）

プロトタイプ中に、当初「見た目のみ」だった要件を超える **emergent logic / 依存追加**が確定した。requirements.md へ反映が必要:

1. **新規依存 `@heroicons/react` の追加**（Home/練習/戦績/設定 のアウトライン↔ソリッド用）。実装タスクで `package.json` に追加する。
2. **ドラッグでタブ切替（A案）という新インタラクション**の追加（当初の「見た目のみ」から拡張）。AC 追加＋アクセシビリティ（タップは主経路として維持、ドラッグは補助）。
3. **アイコンの outline↔solid 切替**・**スプリング演出（タップ=流れる一発 / ドラッグ=保持）**・**楕円カプセル**の追加。
4. 上記に伴う AC 追加・Non-goals 更新（屈折不採用は据え置き）。

## DESIGN-PROTO（productionize 時に除去。`git grep DESIGN-PROTO` = 0件で検証）

- `App.jsx` の `/design-proto/glass-nav` ルート＋`DesignGlassNavPreview` の import。
- `src/pages/DesignGlassNavPreview.jsx`（プレビュー専用ページ・丸ごと削除）。
- Layout.jsx の `protoActive`（?active 上書き）、`isProto`、`protoIndex`、`commitSelect`/`onItemClick` の proto 分岐。
  - productionize 後: `commitSelect` は常に `navigate`、`onItemClick` は Link の通常遷移に任せる（`preventDefault` 削除）、activeIndex は `routeActiveIndex` のみ。
