---
name: fixreview-pr1007-pointer-events
description: PR#1007のCodexレビューで発覚した「非表示時の透明navがクリックをブロックする」回帰の修正記録
metadata:
  type: project
---

PR #1007（Issue #1006: iOS Safariスクロール時にボトムナビが追随する不具合の修正）のCodexレビュー ラウンド1でCRITICAL指摘。

## 対応した指摘
- [CRITICAL] (round1, Codex) 非表示時の透明なnavが画面下部のクリックをブロックする → `Layout.jsx` の外側`<nav>`に `isVisible` 連動の `pointer-events-none/auto` と `aria-hidden` を追加して解消
- [WARNING] (round2, Codex) `aria-hidden`配下にフォーカス可能な`Link`が残る → 各`Link`に`tabIndex={isVisible ? 0 : -1}`を追加してキーボードフォーカスも連動させて解消
- [correctness] (code-review high effort) 非表示→表示の切り替え時、300msスライドイン完了前に外側navのpointer-events-autoが即時有効化され、まだ視覚的に到達していない領域のタップを奪ってしまう → pointer-events判定をtransformを持つ内側divへ移し、外側navは常時`pointer-events-none`に固定（transformされた要素の当たり判定は見た目の位置に追従するため解消）
- [conventions] (code-review high effort) docs/DESIGN.md がaria-hidden/tabIndexのa11y制御追加を反映していなかった → 追記

## 見送った指摘
- [WARNING] (code-review high effort) `MatchCommentThread.jsx`の`handleNavBlur`が100ms遅延で`setVisible(true)`を呼ぶため、キーボードでテキストエリアを離れた直後の1回目のTabでボトムナビを読み飛ばすことがある。この遅延自体は本PR対象外の既存機能（Issue #436）であり、読み飛ばしはナビが実際にまだ非表示の間のみ発生し次のTabで解消するため、1PR=1機能の原則に基づき本PRのスコープ外として見送り

## 背景（なぜ起きたか）
fixed要素自体からtransformを外し内側divに移す修正（iOS Safariのスクロール追随バグ対策）を行った際、外側のfixed navは非表示時もレイアウト上のボックスサイズ（子要素分の高さ）を画面下部に保持し続ける。transformは子のみを視覚的に動かすため、z-50の当たり判定領域は元の位置に残ってしまう。**fixed要素とtransformを分離するパターンを使う際は、pointer-events判定を「transformを持つ要素」側に置く（当たり判定がtransformに追従するため）と、非表示時・アニメーション中のどちらもクリックを奪わずに済む。**

## テスト結果
- フロントエンドテスト全件PASS（51ファイル590件）

## PR
- ブランチ: fix/bottom-nav-scroll-drift
- PR #1007: https://github.com/poponta2020/match-tracker/pull/1007
