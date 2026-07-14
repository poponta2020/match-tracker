---
name: ship_pr1050_pairing_avoid_previous_practice
description: PR#1050出荷（自動シャッフルで同一団体の前回練習日ペアを強く回避、親Issue#1041/子#1042#1043）。ソフト強ペナルティ=グレースフル劣化
type: ship
---

# PR #1050 出荷記録 — 自動シャッフルで前回練習日の対戦相手も避ける

- **PRタイトル**: feat(pairing): 自動シャッフルで同一団体の前回練習日ペアを強く回避する
- **PR**: https://github.com/poponta2020/match-tracker/pull/1050
- **カテゴリ**: ship（pairing-avoid-previous-practice-opponents）
- **出荷日**: 2026-07-15
- **クローズIssue**: #1041（親）・#1042・#1043（PR本文 closing keyword でマージ時クローズ）

## 変更内容

- `MatchPairingService.autoMatch` のスコアリングに「同一団体の前回練習日ペア集合」への固定ペナルティ（`PREVIOUS_PRACTICE_PENALTY=-1000`）加算を追加。他に組める相手がいる限り前回の対戦相手を再形成しない。
- **ソフトペナルティ（有限値）＝グレースフル劣化**: 少人数で組みようがない場合は待機者を増やさず最後の手段として許容。同日ペアの完全除外は従来どおり維持。
- `findPreviousPracticePairKeys` 新設: 過去セッションを降順に遡り直近の対戦がある日を特定（30日窓に非依存、対戦なしの日はスキップ）。`match_pairings` と `matches` の両方から収集し、`getSessionAllPlayerIds(day, org)` の参加者で AND フィルタ（別団体除外）。
- `PracticeSessionRepository.findPastSessionDatesByOrganizationId` 追加（読み取り専用finder 1本・`Pageable` 上限）。
- 公開契約（AutoMatchingRequest/Result）・DTO・DBスキーマ不変（マイグレーション N/A）・FE変更なし。
- `docs/spec/matching.md` のアルゴリズム記述3箇所を更新。`MatchPairingServiceTest` に AC-1/2/3(+matches経路)/4/8 の shuffle 不変テストを追加。

## レビュー（auto-review-loop、2R収束）

- **R1**（effort=high, tokens 34,772）: needs_changes・blocker 1件（AC-3 が matches 経路の別団体除外を未検証）→ main が直接修正（matches のみから前回ペアを収集する決定的テスト追加, commit 9146b4db）
- **R2**（effort=high, tokens 35,360, 累計 70,132/500,000）: **pass**・blocker/should_fix/nit ゼロ
- DoD: D1（memory）は初回 FAIL（記録未作成）→本記録で解消。他は PASS/SKIP。CI `test` は pending のままマージ（v0.9.0 方針: マージ前CI待ちなし。赤なら追修正）

## 設計・テストの教訓

- ペナルティ順序が安全な理由: スコアパスの非・前回ペアのベースは常に `(-100, 0]`（同日は事前除外・履歴クエリ endDate 排他で `daysAgo>=1`）→ 前回ペアは `[-1100, -1000)` で厳密に下位。`bestScore` 初期値 `NEGATIVE_INFINITY` かつ前回ペアも除外しないためグレースフル劣化。
- **AC-3 の cross-org 除外は behavioral に shuffle 不変検証が原理的に不可能**（除外対象は org 参加者外の選手を含み現参加者でもない→greedy 出力に無影響）。→ verify とペナルティ由来の決定的回避で担保。AC-8 は組数/待機者のみ主張（N>3 では greedy 末端効果で回避不変でない）。

## コミット

- ccd02a83 feat(pairing): 前回練習日ペア回避の本体実装＋テスト＋spec
- eb8cc6d0 docs(matching): フロー手順一覧の整合
- 9146b4db test(pairing): matches 経路と団体スコープ除外の検証テスト追加
