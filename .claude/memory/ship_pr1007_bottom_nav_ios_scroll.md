---
name: ship-pr1007-bottom-nav-ios-scroll
description: PR#1007（iOS Safariでボトムナビがスクロールに追随するバグ修正）の出荷記録
metadata:
  type: project
---

## バグの概要
iOS Safariで画面を上方向にスクロールすると、`position:fixed`のボトムナビゲーションが画面下部に固定されず、コンテンツと同じ速度で追随して上に流れてしまう。

## 深刻度
軽微（変更ファイルはフロントエンド1個 + docs、レイヤー横断なし）

## 根本原因
2026-04-11のコミット `fd0ab31`（Fixes #436、ボトムナビのスライドアニメーション追加）で、`position:fixed` のナビ要素自体に `transition-transform`/`translate-y-*` を直接付与していた。iOS SafariではPosition:fixed要素にtransformを直接適用すると、要素が「本当のfixed」として扱われなくなり、スクロールに追随してしまう既知のWebKitの挙動があり、これが原因と判明（git履歴で確認・Codexレビューとcode-reviewで多角的に検証済み）。

## 適用した修正
`Layout.jsx` のボトムナビ構造を、fixed指定専用の外側`<nav>`（transformなし・常時`pointer-events-none`）とスライドアニメーション用の内側`<div>`（transform + `pointer-events-auto/none`をtranslateと同じ条件に同居）に分離。非表示時は`aria-hidden`と各`Link`の`tabIndex={isVisible ? 0 : -1}`でスクリーンリーダー・キーボード操作からも除外。docs/DESIGN.mdにa11y制御を追記。

auto-review-loopで4ラウンド（Codex 3回 + code-review high effort 1回）実施し、以下を検出・対応:
- [CRITICAL] 非表示時の透明なnavがクリックをブロック → pointer-events切替で解消
- [WARNING] aria-hidden配下のLinkがフォーカス可能 → tabIndex制御で解消
- [correctness] スライドイン中(300ms)のクリック吸収 → pointer-events判定をtransform要素側に統一して解消
- [conventions] docs/DESIGN.md未更新 → 追記
- 見送り: MatchCommentThread.jsxの100ms非同期setVisibleに起因するtabIndex読み飛ばし（別機能#436のスコープ、1PR=1機能の原則により対応せず）

詳細: [fixreview_pr1007_pointer_events.md](fixreview_pr1007_pointer_events.md)

## CI
初回CIでフロントエンドテスト`MatchResultsView.swipe.test.jsx`が失敗したが、既知の並列実行フレーク（無関係な既存テスト、単体実行では常にPASS）と確認しrerunでgreen化。

## DoDゲート
`--skip-dod`で出荷。理由: `npm run lint`がFAIL（46エラー・13警告）だが、mainブランチ時点で同数の既存lintエラーが存在しており本PRとは無関係と確認済み。フォローアップとして Issue #1019 を切り出した。

## Issue・PR
- Issue #1006: https://github.com/poponta2020/match-tracker/issues/1006 （マージ時に自動クローズ）
- PR #1007: https://github.com/poponta2020/match-tracker/pull/1007 （マージ済み）
- フォローアップ Issue #1019（既存lint負債の解消）: https://github.com/poponta2020/match-tracker/issues/1019
