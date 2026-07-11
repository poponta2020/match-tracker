---
status: completed
change_type: pure-ui
contract: design-spec.md （本手順書は薄い。視覚の正典は design-spec と Claude Design の A/B/C/D 4カード）
---
# 対戦組み合わせ画面（PairingGenerator）脱カードUI改修 実装手順書

> 純UI改修（挙動変更ゼロ・純フロント）。要件契約は `design-spec.md`（AC / 回帰AC / Non-goals / トークン）。
> 主対象は `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx` のマークアップ／Tailwind クラス置換。
> **挙動側（`pairingDragLogic` / `pairingLockLogic` / `pairingDisplayLogic` / `lineTextTarget` / `cardRules` / API・DTO）は触らない。**

## 実装タスク

### タスク1: 共通シェル（ヘッダ・試合番号タブ・パネル）の脱カード化
- [x] 完了
- **目的:** 緑ヘッダに `日付＋会場名`（`venueName`、null/空なら日付のみ）。**本文の日付入力＋「今日」ボタンは削除**（意図的差分1）。使い方ボタンは `PageHeader` の `rightActions` へ移設（`PairingHelp` に header variant 追加）。試合番号を下線タブ＋連結パネル（cream `#ebe4d8`）に。全状態共通の土台。
- **対応AC:** AC-1, AC-2, AC-3, AC-6
- **主な変更領域:** `PairingGenerator.jsx`（ヘッダ／`PageHeader` 連携・タブ描画）、`PairingHelp.jsx`（header variant のトリガー配色のみ）。`currentSession.venueName` を利用
- **必要なテスト:** 会場名 null フォールバックの分岐（軽量 auto-test 可）
- **完了条件:** AC-2/AC-3/AC-6 を verify、フォールバック分岐テスト green

### タスク2: 新規作成前（A）の脱カード化
- [x] 完了
- **目的:** 参加者一覧（元ピルチップ維持・**折りたたみ廃止で常時展開**＝意図的差分3）・主アクション（「対戦編集」・挙動不変）・**LINE生成をトグル格納**（意図的差分2）。
- **対応AC:** AC-1, AC-7, AC-8, AC-R1, AC-R6, AC-R7
- **主な変更領域:** `PairingGenerator.jsx` 参加者セクション・自動組み合わせボタン・LINE導線
- **完了条件:** 参加者チップが元ピル（AC-R1）、`pairings.length===0` 表示条件維持（AC-R6）

### タスク3: 編集モード（B）の脱カード化
- [x] 完了
- **目的:** ペア一覧を hairline 区切り縦積みに。ロック行=muted名＋色ラベル。待機者・ドロップゾーン・保存フッタ。選手チップは元ピル維持。
- **対応AC:** AC-1, AC-4, AC-R1, AC-R2, AC-R3, AC-R4
- **主な変更領域:** `PairingGenerator.jsx` ペアリスト描画・待機者・保存行
- **必要なテスト:** 既存 `pairingDragLogic.test.js` / `pairingLockLogic` 系を**変更せず green 維持**
- **完了条件:** D&D/タップ・ロック保護・明示保存が回帰なし（AC-R2〜R4）

### タスク4: 閲覧モード（C）の脱カード化（キャンセル表示の整合）
- [x] 完了
- **目的:** プレーン中央寄せ名・「編集」導線・対戦相手キャンセル（取消線＋右端タグ・両方非表示）・結果入力済ラベル。旧カード版 `pairing-cancelled-opponent-a.html` を脱カードに置換。
- **対応AC:** AC-1, AC-5, AC-R5
- **主な変更領域:** `PairingGenerator.jsx` 閲覧モード描画（`showsResultLockedRow` / `hasAnyCancelled` / `shouldHideRow` の**呼び出しは維持**、見た目だけ変更）
- **必要なテスト:** 既存 `pairingDisplayLogic.test.js` green 維持
- **完了条件:** AC-5・AC-R5 verify、表示ロジックテスト green

### タスク5: 補助状態（D）の脱カード化
- [x] 完了
- **目的:** エラー/通知/未保存バナー（意味色保持・薄tint）・選手追加モーダル（warm-white surface）・ローディング（緑スピナー）。
- **対応AC:** AC-1
- **主な変更領域:** `PairingGenerator.jsx` バナー・モーダル・ローディング表示
- **完了条件:** 各状態が脱カードトークンで表示（verify）

### タスク6: 全体回帰確認
- [x] 完了
- **目的:** 挙動変更ゼロを担保。
- **対応AC:** AC-R2〜R6, AC-R8
- **完了条件:** `cd karuta-tracker-ui && npm run test` green、`npm run lint` green、`/verify` で A/B/C/D 全状態を実動作確認

## 実装順序
1. タスク1（共通シェル・依存なし）
2. タスク2・3・4・5（各状態。1に依存。並行可）
3. タスク6（全タスクに依存・最終回帰）
