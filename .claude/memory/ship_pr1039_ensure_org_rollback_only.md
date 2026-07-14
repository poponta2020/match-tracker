---
name: ship-pr1039-ensure-org-rollback-only
description: 自動所属 ensurePlayerBelongsToOrganization の並列競合 rollback-only 500（Issue #1037）を ON CONFLICT DO NOTHING の原子的 INSERT で修正し出荷。#1034/#1036 同型バグ横展開の最終残
type: ship
---

# PR #1039: fix(organizations) 自動所属の並列競合で呼び出し元Txが UnexpectedRollbackException 500 になるバグを修正

- PR: https://github.com/poponta2020/match-tracker/pull/1039（Closes #1037）
- Requirements: docs/bugs/1037-ensure-org-rollback-only/requirements.md
- コミット: d798f638（本修正＋回帰テスト＋requirements）
- auto-review-loop: 1ラウンド pass（effort=medium、blockers/should_fix/nits 0件、Codex累計トークン25,724）。1R pass のためラウンド記録は本記録に集約
- CI pending のままマージする方針（v0.9.0。赤なら追修正）
- これで PR #1035（Issue #1034）発の同型バグ横展開（隣室通知→伝助マッピング #1038→本件）は**全件完了**

## 根本原因
`OrganizationService#ensurePlayerBelongsToOrganization` が `@Transactional` 内で save() の DataIntegrityViolationException を catch して握りつぶしていた。`PlayerOrganization` は IDENTITY 採番のため save() 時点で即 INSERT が発行され、並列リクエスト（参加登録の二重送信等）の一意制約違反が SimpleJpaRepository.save の @Transactional プロキシを通過した時点で呼び出し元トランザクションが rollback-only にマークされる。catch では解除不能で、呼び出し元（PracticeParticipantService.registerParticipations / PlayerService 一括更新 / DensukeImportService）のコミットが UnexpectedRollbackException → 500、業務処理全体が silent rollback。

## 修正
- `PlayerOrganizationRepository#insertIfAbsent`: `INSERT ... ON CONFLICT (player_id, organization_id) DO NOTHING` の native query（挿入行数を返す。created_at は @PrePersist を通らないため JstDateTimeUtil.now() を引数渡し）
- サービスは try/catch を廃止し、挿入 1 行なら通知設定作成、0 行なら冪等 skip。事前 exists チェックは fast path として維持
- **同型3連発でも修正パターンは3種**（文脈で使い分けた判断根拠）: #1035=exists事前チェック+setRollbackOnly バックストップ（マーカーと通知送信が同一Txで不可分）／#1038=REQUIRES_NEW 別Bean隔離（冪等マスターデータで先行コミット無害・バッチ継続が必要）／本件=ON CONFLICT DO NOTHING（例外の発生自体を除去。呼び出し元Txと運命共同体のまま=業務ロールバック時に所属も巻き戻る現行意味論を完全保持。REQUIRES_NEW だと所属だけ残る半端が生じるため不採用）
- 本番DBの UNIQUE (player_id, organization_id) 実在は Q.java introspect で確認済み（同名制約2本・FKなし）。ON CONFLICT (cols) は一致する一意インデックスが無いと実行時エラーになるため、スキーマ乖離前科のある本番では introspect 必須

## 回帰テスト
- `OrganizationServiceEnsureMembershipIntegrationTest`（TestContainers 実PG、@DataJpaTest + @Import(OrganizationService) + @Transactional(NOT_SUPPORTED) + TransactionTemplate で呼び出し元Txを明示模擬）: TOCTOU 競合（@MockitoSpyBean で exists のみ false stub）で呼び出し元Txが正常コミットされることを検証。**修正前コードで UnexpectedRollbackException により fail することを確認済み**。非競合の新規所属（通知設定作成含む）・所属済み no-op も実DBで検証（native SQL の実行時検証を兼ねる）
- ユニットテスト `OrganizationServiceTest` は save モック→insertIfAbsent モックに更新
