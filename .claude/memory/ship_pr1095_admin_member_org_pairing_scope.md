---
name: ship_pr1095_admin_member_org_pairing_scope
description: PR#1095出荷（他団体会員でもあるADMINが会員団体の対戦組み合わせを閲覧・操作できないバグ、Issue#1094）。ADMINをPLAYERと同じ会員団体スコープに統一。BEのみ・DB/FE無改修。auto-review 1R Codex pass(high)
type: reference
---

PR #1095 `fix/admin-member-org-pairing-scope`（バグ修正、Closes #1094）出荷記録。

## バグと症状
- 他団体の会員でもある **ADMIN** が、その会員団体の対戦組み合わせを閲覧・操作できない。
- 実例: 森保滉大(player id=39)=role ADMIN・`admin_organization_id=1`(わすらもち会)・`player_organizations` は org1+org2(北大)の両方。北大の `/pairings` を開くと**組み合わせが空表示**。本番 2026-07-16 は北大に `match_pairings` 29件が実在するが、わすらもち会はその日セッション無し → ADMIN は空。PLAYER なら見えていた（「ADMINだから見えない」）。

## 根本原因
- 読み取り `OrganizationScopeResolver.resolveEffectiveOrganizationId` が ADMIN を `admin_organization_id` に強制。フロントは organizationId を渡さない（`pairings.js`/`practices.js`）ため会員他団体が自団体に絞られ空になる。
- 書き込み（`MatchPairingController` の validateScopeByDate/validateScopeByPairingId/resolveOrganizationIdForScopedWrite/…ByPairingId）・選手追加（`PracticeSessionService.checkScopeByDate`）も同型で ADMIN を admin_org 強制。

## 修正（ユーザー承認済みの仕様変更）
対戦組み合わせの操作を **ADMIN も PLAYER と同じ「会員団体スコープ」に統一**（SUPER_ADMIN のみ全団体横断）。
- 閲覧: `resolveViewingOrganizationId` 新設（ADMIN/PLAYER 共通＝未指定→null非限定 / 明示→所属団体のみ・非所属403、SUPER_ADMIN 素通し）→ match-pairings /date・/date-and-match、practice-sessions /date に適用。
- 書き込み4ヘルパー＋checkScopeByDate の ADMIN 分岐を PLAYER と統一。checkScopeByDate は未使用の `adminOrganizationId` 引数を削除（呼び出し元・テスト同時更新）。
- 個別削除 `DELETE /{id}` のロールは ADMIN+ のまま維持（ユーザー選択「ロール現状維持」）。
- **残置が正**: `resolveEffectiveOrganizationId` は `KaderuSyncTrigger`（同期トリガー・ADMIN は admin_org 必須）と `MatchVideo` が使用のため不変。`checkAdminScope`（ADMIN専用管理系）も不変。

## 設計上の判断ポイント（引き継ぎ）
- 会員パリティのみ（非会員でも書ける「完全撤廃」にはしない）＝ユーザー選択。個別削除ロールも現状維持＝ユーザー選択。
- read/write が同一エンドポイントを共用する二重責務が根。閲覧専用リゾルバに分離することで書き込み系（KaderuSync等）を壊さず閲覧だけ開放。
- 影響が同じ画面の PairingSummary/MatchForm/MatchResultsView（すべて practiceAPI.getByDate→getSessionByDate 経由）にも副次的に波及＝他団体会員ADMINで整合改善。

## 検証・出荷
- 回帰テスト: OrganizationScopeResolverTest（viewing の ADMIN 会員パリティ）/ MatchPairingControllerTest（ADMIN が admin_org≠会員団体X で autoMatch 可・非会員403・閲覧非限定）/ PracticeSessionServiceTest（checkScopeByDate ADMIN 会員パリティ）/ PracticeSessionControllerTest（getSessionByDate 非限定）。バックエンド全スイート BUILD SUCCESSFUL。
- docs: `design/architecture.md`（権限モデル・マトリックス）＋`spec/matching.md`（API注記）を同一コミットで更新。DB/マイグレーション/フロント変更なし。
- auto-review: **1ラウンド Codex verdict=pass**（effort=high、blockers/should_fix/nits すべて0）。要件: `docs/bugs/1094-admin-member-org-pairing-scope/requirements.md`。
- Issue: Closes #1094。
