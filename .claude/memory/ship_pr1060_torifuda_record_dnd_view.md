---
name: ship_pr1060_torifuda_record_dnd_view
description: PR#1060出荷（取り札記録の改修: D&D配置・決まり字順・象限内動的幅・保存後遷移・試合詳細の読取専用表示。フロントのみ、親#1052/子#1053-#1057）
type: ship
---

# PR #1060 出荷記録 — 取り札記録: 操作性向上と閲覧表示

- **PRタイトル**: 取り札記録: D&D配置・決まり字順・動的幅・保存後遷移・読取専用表示（フロントのみ）
- **PR**: https://github.com/poponta2020/match-tracker/pull/1060
- **カテゴリ**: ship（torifuda-record-dnd-view）
- **出荷日**: 2026-07-15
- **クローズIssue**: #1052（親）・#1053・#1054・#1055・#1056・#1057（PR本文 closing keyword でマージ時クローズ）

## 変更内容（フロントエンドのみ・BE/DB 変更なし）

1. **① D&D 配置**: `@dnd-kit`（Pointer/Touch センサー）で不明→マス／マス→別マス／マス→不明を実装。既存タップ操作は維持。ドロップ計算 `computeDrop` と trailing-click ガードを純モジュール `torifudaDragLogic.js` に分離。`DraggableChip` に `CSS.Translate` transform を適用しドラッグ中の札をポインタ追従表示。盤面の折りたたみブロックに `data-swipe-ignore` を付与し試合番号スワイプと分離（C-5）。
2. **② 決まり字順ソート**: `FIRST_CHAR_ORDER`（むすめふさほせ…）＋`KANA_COLLATION`（五十音）＋`compareCardsByDecisionOrder`（`・`除去・札番号 tiebreaker）。表示順のみ・計数不変。
3. **③ 象限内の横幅動的吸収**: `.tr-quad` を flex 化し各 half の flexGrow を札数比例に、min-width で最小幅確保。
4. **④ 保存後遷移＋読取専用表示**: 新規保存成功後 `/matches/:id` へ遷移（編集は `/matches`・抜け番は `/` 不変）。試合詳細は**当該試合の当事者本人が通常URLで閲覧時のみ**、取り札（読み取り専用盤面・配置済みのみ）とお手付き詳細（読み取り専用）を表示（メンター `?playerId=`・非当事者の直接アクセスは非取得）。
5. `docs/spec/matches.md`・`docs/SCREEN_LIST.md`(No.8/9/10) を in-place 更新。

## レビュー（auto-review-loop、4R収束・全 effort=high・累計 ~207.6k/500k tokens）

- **R1**: blocker 1（MatchDetail が `isOtherPlayer` だけで判定→非当事者の直接アクセスで取得）→ 当事者本人性ガード追加。
- **R2**: blocker 1（決まり字ソート crash＝`kimariji` の `?? String` フォールバック見落としの **false-positive**）→ 防御＋tiebreaker で hardening。should_fix 2（D&D transform 未適用でドラッグ中に動かない／固定 sleep テストが flaky）→ 実修正。
- **R3**: blocker 1（試合切替時に前試合の私的記録が残り得る）→ effect 冒頭 `setCardRecord(null)`＋依存を当事者ID(primitive)に絞る。
- **R4**: **pass**（blocker/should_fix/nit ゼロ）。
- 詳細は [[auto_review_round_pr1060]]（システム auto-memory 側）。

## 検証

- 全 658 テスト＋lint（0 error）green。
- **auto-test は logic 層まで**: 実ドラッグの配置/移動/解除（C-1/2/3 の実操作）・幅吸収（C-8/9）・readOnly 盤面の見た目（C-12 視覚）は jsdom（レイアウト不在）で e2e 検証できないため実機 verify 推奨。`computeDrop`・trailing-click ガード・既存タップ・スワイプ分離・遷移・readOnly 描画は auto-test で担保。
- DoD: D1（memory）初回 FAIL（本記録で解消）。CI `test` は pending のままマージ（v0.9.0 方針。赤なら追修正）。

## 主要コミット

- d61fa33b タスク1 決まり字順ソート / 966180f1 タスク2 保存後遷移 / 8e8b7461 タスク3 D&D / e14c6108 タスク4 動的幅 / 0b6e3dbe タスク5 読取専用表示 / 245d1022 docs
- 7a8f043e fix R1(本人性ガード) / caf8065d fix R2(transform追従・ソート防御・flaky解消) / 4b261463 fix R3(stale記録クリア)
