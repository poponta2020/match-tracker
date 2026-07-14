---
name: ship_pr1044_torifuda_place_over_chip
description: PR#1044出荷（取り札記録の盤面で札選択中は既存チップの上でもそのマスへ配置可）。stopPropagation子がモード操作で親の当たり判定を潰す不具合
type: ship
---

# PR #1044 出荷記録 — 取り札記録の札配置（チップの上でも配置可）

- **PRタイトル**: fix(取り札記録): 札選択中は既存チップの上でもそのマスへ配置できるようにする
- **PR**: https://github.com/poponta2020/match-tracker/pull/1044
- **カテゴリ**: ship / quickfix（torifuda-place-over-chip）
- **出荷日**: 2026-07-15
- **対応Issue**: なし（ユーザー依頼からの直接 quickfix）

## 修正したバグ（症状）

個人の対戦結果入力画面「取り札記録」（`MatchForm` 折りたたみ内の `TorifudaBoard`）で、不明の札を選択→マスをタップで配置するタップ選択式UI。マスが札で埋まっていると、既に置かれている札チップの上をタップしても配置されず、チップ間のわずかな隙間（マスの余白）を狙わないと配置できず非常に使いづらかった。

## 根本原因

配置済みチップ `tr-chip` の `onClick` が常に `e.stopPropagation(); unplace(c)` だった。`stopPropagation` により親マス `tr-half` の配置 `onClick`（`place`）へイベントが伝播しない。マス内はチップが `flex-wrap` で敷き詰まるため、親マスの余白を直接タップできる面積がほぼ消え、当たり判定が死んでいた。

## 変更内容

- `TorifudaBoard.jsx`: チップの `onClick` を「札選択中（`selected != null` ＝arm状態）なら `place(field, side, tier, takenBy)` でそのマスへ配置／非選択なら従来どおり `unplace(c)`」に分岐。`stopPropagation` は維持し二重発火を防止。
- `TorifudaBoard.jsx`: 盤面内ヘルプ説明文を新挙動に合わせて更新（マス内に札があってもその上でOK、非選択時のみ不明に戻す）。
- `TorifudaBoard.test.jsx`（新規・このコンポーネント初テスト）: arm中に既存チップ上タップ＝そのマスへ配置／非選択でチップタップ＝不明に戻す、の2分岐を fireEvent 実クリックで検証。
- `docs/spec/matches.md`: 取り札記録フローの配置操作記述を更新。
- FE純UIのみ。API/DTO/DBスキーマ変更なし。

## レビュー（auto-review-loop、1R pass のためラウンド記録なし）

- 1ラウンド pass（effort=low: 差分98行・2ファイル・全て低リスクパス `karuta-tracker-ui/src/**`(api除く)の trivial 判定）、blockers/should_fix/nits=0、Codex tokens 23,730/500,000
- CI pending のままマージ（v0.9.0 方針: マージ前CI待ちなし）

## 教訓

`stopPropagation` を持つ子要素が親のクリック領域を覆うUIは、モードのある操作（arm状態）で親の当たり判定が死ぬ。子の onClick をモードで分岐させるのが最小修正。純表示コンポーネント（context/API非依存）は単体テストが安価で、既存テストゼロでも一緒に入れる価値が高い。

## コミット

- 0502129c fix(取り札記録): 札選択中は既存チップの上でもそのマスへ配置できるようにする
- a5447ad7 docs(matches): 取り札記録の札配置フローに「チップの上でも配置可」を明記
