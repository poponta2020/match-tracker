---
name: ship_pr1164_bottom_nav_liquid_glass
description: PR#1164出荷（ボトムナビをリキッドグラス化＝浮いた角丸ピル＋アイコンのみ＋カプセルスライド＋ドラッグ切替、純FE）。auto-review 3R収束(medium→high→high、R3 pass)。実機AC-13が唯一の残SHIPゲート
metadata:
  type: ship
---

PR #1164 出荷（ボトムナビ リキッドグラス化・親 Issue #1161／子 #1162・#1163）。純フロントエンド（BE/DB/API/認証 変更なし）。slug=bottom-nav-liquid-glass。design-spec locked round6・design-prototype.patch を土台に productionize。

## 変更（`git apply` せず productionize 版を直書き→`git grep DESIGN-PROTO`=0件 by construction）
- `Layout.jsx`: 不透明グリーンバー＋ラベル付き5項目 → **ニュートラル無彩色の半透明ガラス**（`backdrop-filter: blur(18px) saturate(190%)`＋上縁ハイライト＋斜め鏡面スジ）の**浮いた角丸ピル**（`max-w-md` 中央寄せ・`rounded-[28px]`・下端 safe-area+10px 浮かせ・本文下pad `5rem`）。**アイコンのみ**化＋日本語 `aria-label`（ホーム/対戦/練習/戦績/設定）＋`aria-current`。非アクティブ=アウトライン/アクティブ=ソリッド（`@heroicons/react@2.2.0` 追加、対戦のみ lucide 剣の fill）。アクティブカプセル1個を translateX スライド（タップ=glide／水平ドラッグで最寄り遷移＝A案）。膨らみは個別 `scale`＋`@keyframes navPuff`（index.css）。
- `utils/bottomNav.js`（新）: `slotWidthOf`/`nearestIndex`/`capsuleCenterOf`/`clampCenter` の純ロジックを切出し（jsdom は実ドラッグ不可のため）。
- テスト新規: `bottomNav.test.js`（境界・クランプ・ゼロ除算ガード）、`Layout.test.jsx`（5項目 aria-label/href/順序・アイコンのみ・前方一致アクティブ・ソリッド↔アウトライン・カプセル1個・isVisible=false スライドアウト・ドラッグ確定/中断）。
- docs: `SCREEN_LIST.md` の下部ナビ表・Layout 説明・アニメーション記述を実挙動に更新（旧「➕Add /matches/new」等の stale 表と ⚠要確認 ラベル注記を解消）。

## auto-review（詳細は auto_review_round_pr1164）
- **3R 収束**（effort medium→high→high・R2 で blocker 出て escalate・R3 pass・偽陽性ゼロ・累計約136k）。
- **R1(medium)**: pointercancel/別ポインターの終了イベントで誤タブ遷移 → `onCapsulePointerCancel` 分離（非遷移リセット）＋move/up に `pointerId` ガード。
- **R2(high)**: (a) pointerup が stale な `dragCenter` state で遷移先確定 → 解放イベントの `pointerToCenter(e.clientX)` 基準に変更。(b) 非表示 `translateY(150%)` が safe-area+10px 浮かせ量を含まず iPhone でピル上端が残存（AC-6 退行・**Chromium は inset=0 で偽green**）→ `translateY(calc(100% + env(safe-area-inset-bottom,0px) + 10px))`。
- **教訓**: 実find3件すべて iOS 実機/Chromium 偽green の既知リスク上の実バグ。**pure UI でも verify/manual 依存の挙動（ドラッグ・safe-area スライドアウト）は auto-review で穴が出る**。

## テスト・検証
- FE **844 テスト green**（807 既存＋37 新規、`--no-file-parallelism` 逐次）。`npm run build` 成功。lint 0 errors（14 warn は全て既存無関係ファイル）。
- **iPhone Safari（WebKit）実機の目視確認（AC-13）が SHIP の唯一の残ハードゲート＝ユーザー領域**（角丸クリップ・fixed 内ドラッグのスクロール競合・個別scale/navPuff の WebKit 挙動・ガラス色目視）。Chromium プレビューは承認済みプロトと同一表示の再確認に留まり実機ゲートは満たせないため未実施。

## DoD / マージ
- CI: Vercel 2件 pass（このプロジェクトは PR/push の test CI を手動化済み＝test.yml 非自動）。C1=codex R3 pass。
