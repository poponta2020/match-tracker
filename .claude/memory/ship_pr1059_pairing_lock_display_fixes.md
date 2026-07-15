---
name: ship_pr1059_pairing_lock_display_fixes
description: PR#1059出荷（/pairingsロック表示3改善＝通知削除/バッジ重複解消/リセット改名、親Issue#1058）。純フロント・単一タスク直列、auto-review 1R pass
type: ship
---

# PR #1059 出荷記録 — /pairings ロック表示まわりの改善（pairing-lock-display-fixes）

- **PRタイトル**: feat(pairings): ロック表示3改善（通知削除・バッジ重複解消・「全削除」→「対戦をリセット」）
- **PR**: https://github.com/poponta2020/match-tracker/pull/1059
- **カテゴリ**: ship（pairing-lock-display-fixes）
- **出荷日**: 2026-07-15
- **クローズIssue**: #1058（親。PR本文 closing keyword でマージ時クローズ）
- **性質**: 純フロント表示・文言変更（BE/DB/API 不変）。単一タスク・単一Wave直列、main 直実装（委譲なし）。

## 変更内容（3改善・1コミット 4458329e）

- **A（通知削除）**: `runAutoMatch` の「…はロックされています。」通知ブロックを設定コードごと削除。「参加者数が異なる…」通知は温存（`lockedCount` は participant-count 通知が使うので残存＝dangling なし）。
- **B（バッジ重複解消）**: `pairingLockLogic.js` に純関数 `shouldShowManualLockBadge` = `!!(pairing && locked && !hasResult) && !canShowUnlock(...)` を追加。`canShowUnlock` の厳密な補集合なので編集モードはバッジ×/解除○、閲覧・読み取り専用はバッジ○で排他。本番JSXと `ViewRow` テストハーネスの条件式コピーを両方この関数へ置換（退行検知）。
- **C（リセット改名）**: 「全削除」→「対戦をリセット」ボタン、確認ダイアログ2分岐を「（ロック済みのN組を除く／既存の）対戦組み合わせをリセットしますか？」に統一。削除処理本体は不変。
- テスト: `shouldShowManualLockBadge` 単体7ケース追加（編集/閲覧/読み取り専用/結果入力済み/未ロック/排他/null安全）。docs: `SCREEN_LIST.md` #19 のロック表示記述を in-place 更新。

## レビュー（auto-review-loop、1R収束・effort=medium）

- **R1**（Codex tokens 32,055 / 累計 32,055 / 上限 500,000）: **verdict=pass**、blockers/should_fix/nits すべて0 → 即収束（nit修正・確認ラウンドなし）。
- good_points: 表示条件を純関数に切り出し本番JSX・テストハーネスで共有＝条件式コピーによる退行リスク低減／結果入力済み・未ロック・null pairing まで単体テストで回帰検知。
- DoD: A2 lint PASS・C1 review PASS・D2 docs PASS。A1テストはCI委譲SKIP・A3 typecheck 未定義SKIP。D1（memory）初回FAIL（repo `.claude/memory/` に記録未作成）→本記録で解消。CI `test` は pending のままマージ（v0.9.0 マージ前CI待ちなし・赤なら追修正）。

## 教訓

- **docs更新前に対象文字列の実在を grep 確認**: 要件は「SCREEN_LIST #19 の『全削除』記述を更新」としたが #19 に `全削除` の文字列は無く（`全削除` は #20 の札ルール配列キー用）、ロック表示記述のみ更新した。要件が想定した記述が実在するとは限らない。
- **AC-6（非退行）ブラインドスポット**: 変更/削除するユーザー向け文言（`はロックされています`/`全削除`/`組み合わせを削除しますか`）をテストツリー全体で grep しアサートするテストが無いことを着手前に確認（advisor 指摘）。今回ゼロ→642 green が真の非退行シグナル。
- **verify型AC はブラウザ検証せず**（lean pipeline 方針）: AC-1 は通知 setter を唯一箇所削除＋他 producer 無しを grep 確認済で構造的に保証、AC-5 は静的ラベル。両者ともブラウザ preview を立てる価値なし（advisor 同意）。
- 実装詳細メモ（auto-memory）: impl_pairing_lock_display_fixes / feature_pairing_lock_display_fixes。
