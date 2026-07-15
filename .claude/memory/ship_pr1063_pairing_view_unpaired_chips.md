---
name: ship_pr1063_pairing_view_unpaired_chips
description: PR#1063出荷（/pairings 閲覧時に未組み合わせ選手を待機中チップ表示。純フロント単一タスク、親#1061/子#1062）
type: ship
---

# PR #1063 出荷記録 — /pairings 閲覧時に未組み合わせ選手をチップ表示

- **PRタイトル**: feat(pairings): 閲覧時に未組み合わせ選手を待機中チップで表示
- **PR**: https://github.com/poponta2020/match-tracker/pull/1063
- **カテゴリ**: ship（pairing-view-unpaired-chips）
- **出荷日**: 2026-07-15
- **クローズIssue**: #1061（親）・#1062（子）（PR本文 closing keyword でマージ時クローズ）

## 変更内容（フロントエンドのみ・BE/DB/API 変更なし）

1. `pairingDisplayLogic.js` — 純関数 `shouldShowViewModeUnpairedSection({isReadOnly,isViewMode,pairings,waitingPlayers})` = `(isReadOnly||isViewMode) && pairings.length>0 && waitingPlayers.length>0`。編集モードの待機中ガード `!isReadOnly && !isViewMode` の**厳密な補集合（相互排他）**。`pairings.length>0` は `isReadOnly` が組0件でも真になりうるため参加者一覧（`pairings.length===0`）との二重表示防止に必須。
2. `pairingDisplayLogic.test.js` — AC-1〜5 の真理値表テスト（5 it）追加。
3. `PairingGenerator.jsx` — 組み合わせ一覧 `.map` 直後・編集待機中セクションより前に、当該純関数 true のとき「待機中 N名」＋読み取り専用 `PlayerChip` を描画。チップのみ（活動プルダウン・選手追加・D&D・ドロップゾーンなし）。
4. `docs/spec/matching.md`・`docs/SCREEN_LIST.md` #19 を in-place 更新（pairing-cancelled-opponent と同じく機能観点/画面観点の両方に記載）。

## 核心の学び — waitingPlayers は {id,name} のみ

`waitingPlayers` は `loadExistingPairingsToState` で `{id,name}` のみで構築され、級(kyuRank)・段(danRank)・ロールを持たない。これが2つの別観点のバグを生んだ:
- **advisor（実装直前）**: `kyuRank={player.kyuRank}` が undefined → チップ枠線が常にグレーで参加者一覧と見た目が不一致（§2/§6/AC-6）。
- **Codex R1（should_fix）**: `sortPlayersByRank(waitingPlayers)` は role→kyu→dan→name 順のため、生の {id,name} を渡すと名前順のみになり参加者一覧の並びと不一致。

**両方の根治**: ソート前に `participants.find` で full participant を補完（`{...full, ...player}`）してから `sortPlayersByRank` に渡す。これで並び・級別枠線色ともに参加者一覧チップと一致。純関数テスト＋lint は描画データ欠落（kyuRank undefined・並び順）を構造的に検出できないのが教訓。

## レビュー（auto-review-loop、2R収束・effort=medium・累計 63,431/500,000 tokens）

- **R1（medium, 31,798）**: verdict=needs_changes、should_fix 1（上記の sortPlayersByRank 並び）→ participants 補完で修正。
- **R2（medium, 31,633）**: **pass**（blocker/should_fix/nit ゼロ）。
- 詳細は [[auto_review_round_pr1063]]（システム auto-memory 側）。実装詳細は [[impl_pairing_view_unpaired_chips]]。

## 検証

- フロント全 670 テスト＋lint（0 error）green。
- **AC-6 は verify（未実施）**: 実マウント描画（チップ表示・級別色・活動プルダウン非表示・表示位置）はリーンフロー標準では未確認。ユーザーが `/verify`・手動確認する場合の観点＝閲覧モードで一部だけ組がある試合を開き、組み合わせ一覧直後に待機中チップが級別色付き・参加者一覧と同じ並びで並び、活動プルダウン/選手追加/D&D が出ないこと。
- DoD: D1（memory）初回 FAIL（本記録で解消）。CI `test` は pending のままマージ（v0.9.0 方針。赤なら追修正）。

## 主要コミット

- 450d01b2 feat 本体（純関数＋テスト＋JSX＋docs）
- 3de2b4db fix（kyuRank 枠線色補完・advisor 指摘）
- 4004aa31 fix（ソート前 participants 補完・Codex R1 指摘）
