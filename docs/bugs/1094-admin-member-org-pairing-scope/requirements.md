---
status: approved
issue: 1094
---
# バグ改修要件: 他団体の会員でもあるADMINが会員団体の対戦組み合わせを閲覧・操作できない

## 再現手順

1. player id=39 森保滉大（role=ADMIN・`admin_organization_id=1`＝わすらもち会、`player_organizations` はわすらもち会・北海道大学かるた会の両方の会員）でログイン
2. 北大の練習日（例: `2026-07-16`。北大セッションに `match_pairings` 29件が実在、わすらもち会はセッション無し）で `/pairings?date=2026-07-16` を開く
3. **実際**: 対戦組み合わせが空表示（実データ29件が隠される）／ 書き込み系（作成・編集・選手追加）も自団体外で403
4. **期待**: 北大（会員団体）の組み合わせを閲覧・作成・編集・選手追加できる

## 根本原因

読み取りAPIの組織スコープ解決 `OrganizationScopeResolver.resolveEffectiveOrganizationId`
（`karuta-tracker/src/main/java/com/karuta/matchtracker/util/OrganizationScopeResolver.java`）が、
ADMIN に対して問答無用で `admin_organization_id` を強制する。フロントは組み合わせ・セッション取得に
`organizationId` を渡さない（`karuta-tracker-ui/src/api/pairings.js` / `practices.js`）ため、
北大の日付スコープ読み取りが自団体（わすらもち会）に絞られ、参加者集合が空 → 北大ペアが全除外され空になる。
PLAYER は同じ経路で `null`（非限定）となり見えている。

書き込み側も同型で、ADMIN を `admin_organization_id` に強制する:
- `MatchPairingController.validateScopeByDate` / `validateScopeByPairingId` /
  `resolveOrganizationIdForScopedWrite` / `resolveOrganizationIdForScopedWriteByPairingId`
- `PracticeSessionService.checkScopeByDate`（`addParticipantToMatch` 専用・PLAYER+）

## 修正方針

対戦組み合わせの操作について、**ADMIN を PLAYER と同じ「会員団体スコープ」に統一**する
（SUPER_ADMIN は従来どおり全団体横断）。閲覧・書き込み・（同画面の）選手追加すべてに適用。

- **閲覧**: `OrganizationScopeResolver` に `resolveViewingOrganizationId` を新設。
  ADMIN/PLAYER 共通＝未指定は `null`（非限定）／明示は所属団体のみ許可・非所属は403、SUPER_ADMIN は素通し。
  適用先: `MatchPairingController.getByDate` / `getByDateAndMatchNumber`、`PracticeSessionController.getSessionByDate`。
  `resolveEffectiveOrganizationId`（`KaderuSyncTrigger` / `MatchVideo` が使用）は変更しない。
- **書き込み**: `MatchPairingController` の団体スコープ検証4ヘルパーで、ADMIN の自団体強制を廃止し
  PLAYER と同じ会員団体ベースに統一（ADMIN 分岐を PLAYER と同一ロジックにまとめる）。ロール制限は現状維持。
- **選手追加**: `PracticeSessionService.checkScopeByDate` も同様に ADMIN を会員パリティ化。

## Acceptance Criteria

| ID | 条件 | 検証手段 |
|----|------|------|
| AC-1 | 閲覧：admin_org=Y の ADMIN が会員団体 X の組み合わせ・セッションを取得可（resolver: ADMIN×未指定→null / ×所属→その団体 / ×非所属→403）| auto-test（回帰テスト） |
| AC-2 | 書込：会員団体 X に ADMIN が autoMatch/createBatch/updatePlayer/reset/全削除/個別削除 実行可・非会員は403。SUPER_ADMIN 不変・PLAYER 不変 | auto-test |
| AC-3 | 選手追加：会員団体 X の試合に ADMIN が参加者追加可・非会員は403 | auto-test |
| AC-4 | 既存テスト・lint がすべて成功（KaderuSyncTrigger / MatchVideo / checkAdminScope は挙動不変）| auto-test |

## Non-goals

- 森保さんを北大の対戦に「組み込む」こと（北大は PENDING・抽選ありのため別問題。今回は関われるようにするだけ）
- 完全撤廃（非会員でも書ける）にはしない＝会員パリティを維持
- 個別削除（`DELETE /{id}`）のロールを PLAYER へ開放しない（現状維持 ADMIN+）
- `KaderuSyncTrigger` / `MatchVideo.getDateCandidates` / `checkAdminScope`（ADMIN専用管理系）は不変
- フロント変更・DB変更・マイグレーションなし

## 修正ステップ

- [x] ステップ1: `OrganizationScopeResolver.resolveViewingOrganizationId` 新設＋回帰テスト（対応AC-1 / util / 依存なし / 完了条件: ADMIN会員パリティ・非所属403・SUPER_ADMIN素通しをテストで担保）
- [x] ステップ2: `MatchPairingController` の read2経路を viewing resolver へ切替、write4ヘルパーを会員パリティ化＋テスト（対応AC-1/AC-2 / controller / 依存: ステップ1）
- [x] ステップ3: `PracticeSessionService.checkScopeByDate` を会員パリティ化（未使用の adminOrganizationId 引数を削除）＋テスト（対応AC-3 / service / 依存なし）
- [x] ステップ4: 全テスト green 確認（対応AC-4。バックエンド全スイート BUILD SUCCESSFUL。フロント無改修のため lint/test は非影響）

## 影響範囲

- backend 4ファイル: `util/OrganizationScopeResolver.java`（+メソッド）、`controller/MatchPairingController.java`（read2＋write4ヘルパー）、`controller/PracticeSessionController.java`（read1）、`service/PracticeSessionService.java`（checkScopeByDate）＋各テスト
- 副次的に PairingSummary / MatchForm / MatchResultsView のセッション取得（`practiceAPI.getByDate`→`getSessionByDate`）も他団体会員 ADMIN で整合
- DB・マイグレーション・フロント変更なし
