---
name: ship-pr1023-pairings-decard-ui
description: PR#1023（対戦組み合わせ画面/pairings の脱カードUIリデザイン）の出荷記録
metadata:
  type: project
---

## 概要
対戦組み合わせ画面 `PairingGenerator`（`/pairings`）を「白い浮きカード・ピル・級ごとの多色枠」から、hairline・余白・文字階層で組む**脱カードデザイン**に全面リデザイン。全状態（新規作成A／編集B／閲覧+キャンセルC／補助状態D）に適用。

## 経緯・進め方（設計フロー）
`/define-feature` 起動直後、ユーザー依頼で `/design-screen` に直行し、現状(A/B)を Claude Design（Match Tracker Design System）に push。ユーザーが Claude Design 上で **A を直接リデザイン**（脱カード方向を確立）。参加者チップは「変更前の元のピル形式を維持」とユーザー指定。B/C/D にも同方針を展開し、全状態モックを確定→ `design-spec.md` を lock。純UI改修のため **requirements.md は起こさず design-spec.md を実装契約**として `/implement` へ（design-screen「片レンズに縮む」ルール）。

## 主要な設計判断
- **脱カード（Anti-Slop）**: cream `#f2ede6` 面に hairline `#e7e0d4`/`#ddd3c2`、muted トークン、塗りは主アクション紺 `#1A3654` と active タブ/パネル `#ebe4d8` のみ。ロック/結果/キャンセルは塗りバッジをやめフラット色ラベル。
- **選手名チップは従来のピル形式を維持**（`PlayerChip`/`DraggablePlayerChip` 無改修。ユーザー明示指定）。
- **会場名はヘッダ表示**: 既存 `PracticeSessionDto.venueName` を使用（バックエンド改修なし）。整形は純粋関数 `pairingHeader.js`（`formatHeaderDate`/`resolveHeaderVenue`）へ切り出し本番/テスト共有。
- **ユーザー合意の意図的インタラクション差分3点**（純視覚でない・design-spec「意図的な挙動差分」）: ①この画面での日付変更を廃止（ヘッダ表示のみ・`?date=`/当日デフォルト）②LINE生成をトグル格納 ③参加者リスト常時展開。→ 途中で AskUserQuestion により確認して確定。
- 中核ロジック（`pairingDragLogic`/`pairingLockLogic`/`pairingDisplayLogic`/`lineTextTarget`/`cardRules`）・API・DTO は無改修（挙動不変＝回帰AC AC-R1〜R8）。

## auto-review-loop
- **Codex**: R1 pass → AC指摘で修正 → R2 pass（blocker/should_fix 0・累計約94kトークン）。
- **AC 適合（acceptance-reviewer）**: 初回 **fail**（AC-2 が検証手段に明示した「会場名フォールバックの auto-test」が未実装。implementation-plan タスク1は完了扱いだった）→ `pairingHeader.js` 抽出＋`pairingHeader.test.js` 追加で解消 → **pass**。
- **追加 /code-review（high・差分>400行）**: correctness finder **バグなし([])**／cleanup finder が **docs正典の陳腐化**（`docs/SCREEN_LIST.md` #19 が旧「自動マッチング」ラベル等のまま。D2 gate は docs/features 追加で機械的に通るが正典は未更新）を指摘 → SCREEN_LIST.md と `docs/spec/matching.md` §画面 を in-place 更新。formatHeaderDate 名衝突（BulkResultInput の同名別実装）は非ブロッキングとして受容（1PR=1機能・他画面統合は範囲外）。

## 未実施の検証（重要）
- **ライブ目視（390px でのタブ⇔連結パネルの継ぎ目・ヘッダ使い方ドロップダウン位置・タイトル省略）は実装セッションでは未実施**。当セッションにブラウザ駆動/スクショツールが無く、自動検証（lint/vitest/build/CI）のみ。ユーザーは「普通にship」を選択。**Vercel プレビューがデプロイ済みなので実機ビジュアルはそこで確認可能**。AC-1/3/4/5 はコード整合済みだが目視は残タスク。

## DoDゲート
D1（memory記録）以外は初回で全PASS（A1テスト・A2 lint・B1 CI green・C1 codex pass）。本記録で D1 を満たす。※CI `test` green（4m42s）。

## Issue・PR
- GitHub Issue: **なし**（design-screen→implement 直行のため子Issue未作成）。
- PR #1023: https://github.com/poponta2020/match-tracker/pull/1023
- 成果物: `docs/features/pairings-ui-change/`（design-spec.md=契約, implementation-plan.md）。Claude Design グループ「対戦組み合わせ画面 (現状)」に A/B/C/D 4カード。

## 教訓
- worktree のフロント node_modules は本体へのジャンクション運用だが、本セッションでは**本体 node_modules 自体が不完全**（`.bin` 空・`@adobe/css-tools` 欠落）で vitest が起動不能だった。本体 `karuta-tracker-ui` で `npm install`（74パッケージ追加）して復旧。
- `formatHeaderDate` の名前衝突（BulkResultInput.jsx に同名別挙動）— 将来 3画面共通ヘッダを触るときは統合を検討（今回は範囲外で受容）。
