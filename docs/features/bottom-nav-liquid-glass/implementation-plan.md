---
status: completed
---
# ボトムナビ リキッドグラス化 実装手順書

前提: 見た目・インタラクションは [design-spec.md](design-spec.md)（status: locked）と [design-prototype.patch](design-prototype.patch)（実差分・実装のたたき台）が正典。要件・AC は [requirements.md](requirements.md) §4。
対象は単一コンポーネント [karuta-tracker-ui/src/components/Layout.jsx](../../../karuta-tracker-ui/src/components/Layout.jsx) ＋ `src/index.css`（keyframes）＋ `package.json`（依存追加）。純フロントエンド・BE/DB 改修なし。

## 実装タスク

### タスク1: ガラスナビの productionize（patch 適用＋DESIGN-PROTO 除去＋依存追加）
- [x] 完了
- **対応Issue:** #1162
- **目的:** design-prototype.patch を土台に本番実装へ落とし込む。プロトタイプ足場を完全に除去する。
- **対応AC:** AC-1〜AC-6, AC-9〜AC-19（見た目・インタラクション本体）
- **主な変更領域:** `karuta-tracker-ui/src/components/Layout.jsx`、`karuta-tracker-ui/src/index.css`、`karuta-tracker-ui/package.json`
- **依存タスク:** なし
- **手順:**
  1. `npm install @heroicons/react`（`karuta-tracker-ui/`）で依存追加（package.json + lock）。
  2. `git apply docs/features/bottom-nav-liquid-glass/design-prototype.patch`（当たらなければ patch を読んで手動移植）。
  3. **DESIGN-PROTO を完全除去**（design-spec の「DESIGN-PROTO」節に一覧）:
     - `App.jsx` の `/design-proto/glass-nav` ルート＋`DesignGlassNavPreview` import を削除、`src/pages/DesignGlassNavPreview.jsx` を削除。
     - Layout.jsx の `protoActive`（?active 上書き）、`isProto`、`protoIndex`、`commitSelect`/`onItemClick` の proto 分岐を削除。除去後: `commitSelect` は常に `navigate`、`onItemClick` は `preventDefault` せず Link の通常遷移に任せる、`activeIndex` は `routeActiveIndex` のみ。
  4. `git grep -n "DESIGN-PROTO"` = **0件** を確認。
- **必要なテスト:** （タスク2に集約。ここではビルドが通ること）
- **完了条件:** `npm run build` 成功・`npm run lint` 成功・`git grep DESIGN-PROTO`=0件。

### タスク2: 純ロジックの単体テスト＋既存テスト回帰
- [ ] 完了
- **対応Issue:** #1163
- **目的:** ドラッグ位置計算・アクティブ判定などの純ロジックを単体テストで固定し、既存テストの回帰を通す。
- **対応AC:** AC-20（純ロジック）、AC-1〜AC-5・AC-14〜AC-15（DOM 構造 auto-test）、AC-8（既存テスト・lint 回帰）
- **主な変更領域:** `karuta-tracker-ui/src/components/Layout.jsx`（純ロジックのユーティリティ切り出し）、`karuta-tracker-ui/src/components/Layout.test.jsx`（新規）、必要に応じ影響テスト修正
- **依存タスク:** タスク1（同一ファイルを触るため直列）
- **手順:**
  1. **純ロジックを util 切り出し**（jsdom は実ドラッグ不可のため。過去の `computeDrop` 抽出と同型）: `nearestIndex(centerPx, trackWidth, count)`・カプセル中心計算・`pointerToCenter` のクランプ等を `utils/bottomNav.js` 等へ。→ 単体テスト（境界: 端スロット・クランプ・丸め）。
  2. **DOM 構造テスト**（`Layout.test.jsx`・happy-dom/jsdom）: 5リンクの `aria-label`・`href`・順序、アクティブ時 `aria-current="page"` とソリッド↔アウトラインの出し分け、カプセルが1個でアクティブ位置にのみ出る、`isVisible=false` で `aria-hidden`/`tabIndex=-1`（AC-6 回帰）。
  3. **旧ナビ構造の grep 再確認**（自己教訓）: `grep` で旧ラベル文言（Home/Match/Schedule/Record/Settings のテキスト）・Layout レンダリングに依存する既存アサートが無いか確認し、あれば修正（現状 Layout を import するテストは無い見込みだが必ず確認）。MatchCommentThread の `useBottomNav` モック（AC-7 回帰）はそのまま通ること。
  4. `npm run test`・`npm run lint` 全 green。
- **完了条件:** 新規単体テスト green・既存テスト全 green・lint 成功。

## 実装順序（Wave）
- Wave 1: タスク1
- Wave 2: タスク2（タスク1 に依存・同一ファイル）
※ 単一コンポーネントのため直列。並行 Wave なし。

## SHIP 前の必須ゲート（最重要・自動テストでは代替不可）
`verify` AC（AC-9,10,11,16,17,18,19）はプロトタイプ確認時 **Chromium** で満たしたもの。**iPhone Safari（WebKit）実機での目視確認（AC-13）を SHIP のハードゲート**とする。Chromium では原理的に確認できない以下を実機で必ず見る:
- backdrop-filter のぼかしが 28px 角丸を正しくクリップするか（矩形に見えたら子レイヤーで mask するフォールバック）。
- **`position: fixed` ナビ内のドラッグでページ縦スクロール／pull-to-refresh が起きないか**（`touch-action:none`＋pointer capture の効き。今回の最も新規で不安定な箇所）。
- 個別 `scale` プロパティ＋`navPuff` の imperative リスタートが WebKit で実アニメーションするか。
- **ガラス色**: ニュートラル無彩色の濃淡と白アイコンの視認性（ユーザー目視で最終判断。明るいガラス希望ならアイコン濃色化）。
