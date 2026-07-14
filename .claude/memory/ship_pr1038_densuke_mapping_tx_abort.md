---
name: ship-pr1038-densuke-mapping-tx-abort
description: 伝助メンバーマッピングの一意制約違反1件で書き込みバッチ全体が破棄されるバグ（Issue #1036）を REQUIRES_NEW 隔離で修正し出荷
type: ship
---

# PR #1038: fix(densuke) メンバーマッピングの一意制約違反1件で書き込みバッチ全体が破棄される問題を修正

- PR: https://github.com/poponta2020/match-tracker/pull/1038（Closes #1036）
- Requirements: docs/bugs/1036-densuke-mapping-tx-abort/requirements.md
- コミット: 0563ece0（本修正＋回帰テスト＋docs）
- auto-review-loop: 1ラウンド pass（effort=high、blockers/should_fix/nits 0件、Codex累計トークン27,125）。1R pass のためラウンド記録は本記録に集約
- CI pending のままマージする方針（v0.9.0。赤なら追修正）

## 根本原因
`DensukeWriteService#saveMemberMapping` がマッピング INSERT を呼び出し元バッチと同一トランザクションで実行していた。`DensukeMemberMapping` は IDENTITY 採番のため save() 時点で即 INSERT が発行され、一意制約違反（TOCTOU 競合）時に PostgreSQL が現トランザクション全体を abort（25P02）。catch 内の TOCTOU 救済クエリ自体が abort 済みトランザクションで必ず失敗し、バッチ内の後続 DB 操作（他プレイヤーのマッピング・row_id・dirty=false 更新）も全滅、コミットが UnexpectedRollbackException でバッチ全体が破棄されていた。伝助への HTTP 書き込みは外部副作用として実行済みのため、スケジューラ経路では dirty=true が残り30分ごとに同じ衝突→ERROR を繰り返す構図。PR #1035（Issue #1034）の同型バグの横展開で発見。

## 修正
- INSERT を新設 `DensukeMemberMappingWriter`（`@Transactional(REQUIRES_NEW)`・別コネクション・別 Bean で self-invocation 回避）に隔離。abort は内側トランザクションのみとなり、呼び出し元は健全なまま TOCTOU 救済とバッチ継続ができる
- PR #1035 の exists 事前チェック方式でなく REQUIRES_NEW 隔離を採用した理由: saveMemberMapping には事前チェックが既存で、残る穴が「チェック後の並行登録で INSERT 自体が失敗するケース」そのものだったため。またマッピングは冪等マスターデータで先行コミット無害（#1035 の通知マーカーは通知送信と不可分だったので REQUIRES_NEW 不採用、と真逆の判断になる点に注意）
- 事前チェック・戻り値契約（同一プレイヤー true／別プレイヤー false）・呼び出し元 3 箇所の API は不変

## 回帰テスト
- `DensukeMemberMappingTxIntegrationTest`（service パッケージ配置で package-private を直接呼ぶ。TestContainers 実 PostgreSQL）: TOCTOU 競合（同一/別プレイヤー）後も外側トランザクションの後続 save・コミットが成功し衝突以外の書き込みが永続化されることを検証。修正前は本番と同一の 25P02（current transaction is aborted）で fail することを確認済み
- 教訓: JDK プロキシ（Spring Data リポジトリ）の @MockitoSpyBean に doCallRealMethod は使えない（Cannot call abstract real method）。「1回目空振り→2回目実DB」は不可なので、2回目以降はコミット済み行と同内容を doReturn で明示 stub し、トランザクション健全性は後続の素通し save＋コミット後カウントで検証する

## 横展開
- OrganizationService#ensurePlayerBelongsToOrganization — 同型バグ。並行セッションの PR #1039（Issue #1037、ON CONFLICT DO NOTHING 方式）で対応済み。#1034 発の同型横展開はこれで全件完了
