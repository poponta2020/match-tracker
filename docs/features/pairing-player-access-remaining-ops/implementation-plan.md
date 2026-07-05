---
status: completed
---
# 対戦組み合わせ画面 残存admin専用操作のPLAYER開放 実装手順書

## 実装タスク

### タスク1: PracticeSessionController/Service — 参加者追加のPLAYER開放とスコープ検証新設
- [x] 完了
- **概要:** `addParticipantToMatch`（POST `/api/practice-sessions/date/{date}/matches/{matchNumber}/participants/{playerId}`）にPLAYERロールを許可し、`checkAdminScopeByDate` をPLAYER分岐対応に拡張する。呼び出し元はこのメソッド1箇所のみなので、他のADMIN専用エンドポイント（`checkAdminScope`を使う別メソッド群）には影響しない。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/PracticeSessionController.java`
    - `addParticipantToMatch`（行381付近）の `@RequireRole({Role.SUPER_ADMIN, Role.ADMIN})` を `@RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})` に変更
    - `currentUserId` を `httpRequest.getAttribute("currentUserId")` から取得し、`checkAdminScopeByDate` 呼び出しに渡す（メソッドシグネチャ変更に合わせる）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeSessionService.java`
    - `checkAdminScopeByDate(LocalDate date, String role, Long adminOrganizationId)` に `Long currentUserId` 引数を追加し、`checkScopeByDate` にリネーム
    - ロジックを `MatchPairingController.validateScopeByDate`（[MatchPairingController.java:287](../../../karuta-tracker/src/main/java/com/karuta/matchtracker/controller/MatchPairingController.java#L287)）と同じ考え方に拡張:
      - `SUPER_ADMIN` → 早期return（スコープ強制なし）
      - `ADMIN` → 既存通り `practiceSessionRepository.findBySessionDateAndOrganizationId(date, adminOrganizationId)` で照合、無ければ `ForbiddenException`
      - `PLAYER` → `organizationService.getPlayerOrganizationIds(currentUserId)` で所属団体IDリストを取得し、対象日付のセッションの組織IDがそのいずれかに含まれなければ `ForbiddenException`
      - それ以外のロール → `ForbiddenException`
- **依存タスク:** なし
- **対応Issue:** #986

### タスク2: MatchPairingController — 全削除・結果込みリセットのPLAYER開放
- [x] 完了
- **概要:** `deleteByDateAndMatchNumber`（DELETE `/api/match-pairings/date-and-match`）と `resetWithResult`（DELETE `/api/match-pairings/{id}/with-result`）にPLAYERロールを許可する。両者とも既に汎用化済みの `validateScopeByDate` / `validateScopeByPairingId` を通っているため、スコープ検証ロジック自体の変更は不要。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/MatchPairingController.java`
    - `deleteByDateAndMatchNumber`（行187-198付近）の `@RequireRole({Role.SUPER_ADMIN, Role.ADMIN})` を `@RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})` に変更
    - `resetWithResult`（行232-241付近）の `@RequireRole({Role.SUPER_ADMIN, Role.ADMIN})` を `@RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})` に変更
    - **据え置き（変更しない）:** `delete`（DELETE `/{id}`、行175-182、UI未使用のためADMIN+のまま）
- **依存タスク:** なし
- **対応Issue:** #987

### タスク3: PairingGenerator.jsx — 「全削除」「結果込みリセット」ボタンの表示条件変更
- [x] 完了
- **概要:** 「全削除」ボタンと「結果込みリセット」ボタンからロール判定 `(isAdmin() || isSuperAdmin())` を撤去し、ログイン済みなら誰でも表示・実行できるようにする。既存の `window.confirm()` 確認ダイアログはそのまま維持する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx`
    - 行980付近: `{!isReadOnly && !isViewMode && (isAdmin() || isSuperAdmin()) && (` → `{!isReadOnly && !isViewMode && (`
    - 行1041付近: `{!isReadOnly && !isViewMode && pairing.id && pairing.hasResult && (isAdmin() || isSuperAdmin()) && (` → `{!isReadOnly && !isViewMode && pairing.id && pairing.hasResult && (`
    - 行10の `import { isAdmin, isSuperAdmin } from '../../utils/auth';` は、上記撤去後にファイル内で他に使用箇所が無ければ削除する（要grep確認）
- **依存タスク:** なし
- **対応Issue:** #988

### タスク4: バックエンドテスト追加
- [x] 完了
- **概要:** タスク1・2で開放した3エンドポイントについて、PLAYER自所属団体成功・所属外403のテストケースを追加する。
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/controller/MatchPairingControllerTest.java`
    - `deleteByDateAndMatchNumber` / `resetWithResult` について、PLAYER自所属団体成功・所属外403のケースを追加（既存の `create`/`createBatch`/`autoMatch` 向けテストパターンを踏襲）
  - `PracticeSessionController` の参加者追加系テスト（既存テストクラスを特定し追加。無ければ `PracticeSessionControllerTest` を新設）
    - `addParticipantToMatch` について、PLAYER自所属団体成功・所属外403のケースを追加
- **依存タスク:** タスク1、タスク2（実装確定後にテストを書く）
- **対応Issue:** #989

### タスク5: ドキュメント更新
- [x] 完了
- **概要:** 仕様書・設計書に権限変更を反映する。
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` — 該当3エンドポイントの権限表記を「ADMIN+」→「PLAYER+」に修正
  - `docs/DESIGN.md` — 権限設計の節があれば、PLAYERスコープ強制の対象操作一覧に3操作を追記
- **依存タスク:** タスク1〜4（コード変更が確定してからドキュメントに反映）
- **対応Issue:** #990

## 実装順序

1. **タスク1** — 参加者追加のバックエンド開放とスコープ検証新設（依存なし、最優先）
2. **タスク2** — 全削除・結果込みリセットのバックエンド開放（依存なし、タスク1と並列可能）
3. **タスク3** — フロントエンドのボタン表示条件変更（依存なし、タスク1・2と並列可能）
4. **タスク4** — バックエンドテスト追加（タスク1・2に依存）
5. **タスク5** — ドキュメント更新（タスク1〜4確定後）

※ タスク1〜3は互いに独立しているため並列実装が可能。リリース時はバックエンド（タスク1・2）とフロントエンド（タスク3）を同時にデプロイする。
