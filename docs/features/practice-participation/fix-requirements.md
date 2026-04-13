---
status: completed
audit_source: 会話内レポート
selected_items: [1, 2, 3]
---
# 練習参加登録（practice-participation） 改修要件定義書

## 1. 改修概要

- **対象機能**: 練習参加登録画面（`/practice/participation`）
- **改修の背景**: `/audit-feature` による監査で検出された問題3件に対応
- **改修スコープ**:
  1. クロス団体soft-deleteバグの修正（高）
  2. playerId検証の追加（中）
  3. DESIGN.md 5.3.2 の更新（低）

## 2. 改修内容

### 2.1 [高] クロス団体soft-deleteバグの修正

- **現状の問題**: `PracticeParticipantService.java` の `registerSameDay`（L175-177）と `registerBeforeDeadline`（L215-217）で `practiceSessionRepository.findByYearAndMonth()` を使用し、月内の全団体のセッションIDを取得してsoft-deleteしている。複数団体に所属する選手が参加登録を保存すると、別団体のセッションの参加登録までCANCELLEDになる。
- **修正方針**: `findByYearAndMonth` を `findByYearAndMonthAndOrganizationId` に変更し、soft-deleteの範囲を対象団体のセッションに限定する。`organizationId` は `registerParticipations()` メソッド内（L139-144）で既に取得済みのため、`registerSameDay` / `registerBeforeDeadline` に引数として渡す。
- **修正後のあるべき姿**: soft-deleteは対象団体のセッションのみに適用され、別団体の参加登録に影響しない。

### 2.2 [中] playerId検証の追加

- **現状の問題**: `PracticeSessionController.java` の `POST /participations`（L272-278）に `@RequireRole` や認証検証がなく、リクエストボディの `playerId` をそのまま信頼している。理論上、他人の `playerId` を指定して参加登録を操作できる。
- **修正方針**: Controllerメソッドに `HttpServletRequest httpRequest` を追加し、`currentUserId` と `currentUserRole` を取得。PLAYERロールの場合、`request.getPlayerId()` と `currentUserId` が一致しなければ `ForbiddenException`（403）を返す。ADMIN / SUPER_ADMIN は任意の `playerId` を操作可能（管理機能として必要）。
- **修正後のあるべき姿**: PLAYERは自分自身の参加登録のみ操作可能。管理者は全選手の操作が可能。

### 2.3 [低] DESIGN.md 5.3.2 の更新

- **現状の問題**: DESIGN.md 5.3.2「練習参加登録」の記載が実装の進化に追いついていない。
  - 「年月選択（セレクトボックス）」→ 実際は左右矢印ボタンのナビゲーション
  - 団体名の色分け表示、参加人数バッジ、抽選ステータス表示（WON/WAITLISTED等のバッジ）が未記載
  - SAME_DAYタイプの12:00以降確認ダイアログが未記載
  - データフローに `getPlayerParticipationStatus` API の記載なし
- **修正方針**: 実装の現状に合わせて表示内容・データフローを更新する。
- **修正後のあるべき姿**: DESIGN.md 5.3.2 が現在の実装を正確に反映している。

## 3. 技術設計

### 3.1 API変更

**変更なし。** エンドポイントのシグネチャ・リクエスト/レスポンス形式は変更しない。Controllerレベルでの認証検証を内部的に追加するのみ。

DESIGN.md 4.6 の権限記載を「PLAYER: 自分のみ / ADMIN, SUPER_ADMIN: 全選手」に更新する。

### 3.2 DB変更

**変更なし。** テーブル・カラム・制約の変更は不要。

### 3.3 フロントエンド変更

**変更なし。** フロントエンドは既に `currentPlayer.id` を `playerId` として送信しており、正規の使用では問題が発生しない。

### 3.4 バックエンド変更

| ファイル | 変更内容 |
|---------|---------|
| `PracticeParticipantService.java` | `registerSameDay` / `registerBeforeDeadline` のシグネチャに `Long organizationId` 追加、内部の `findByYearAndMonth` → `findByYearAndMonthAndOrganizationId` に変更、`registerParticipations` からの呼び出し時に `organizationId` を渡す |
| `PracticeSessionController.java` | `registerParticipations` メソッドに `HttpServletRequest httpRequest` パラメータ追加、PLAYERロールの場合に `playerId` と `currentUserId` の一致検証、不一致時に `ForbiddenException` |
| `PracticeParticipantServiceTest.java` | 既存テスト（`findByYearAndMonth` をモックしている箇所）を `findByYearAndMonthAndOrganizationId` に更新、クロス団体シナリオのテスト追加 |

### 3.5 ドキュメント変更

| ファイル | 変更内容 |
|---------|---------|
| `docs/DESIGN.md` 5.3.2 | 表示内容・データフローを実装に合わせて更新 |
| `docs/DESIGN.md` 4.6 | 権限記載を更新 |

## 4. 影響範囲

- **影響を受ける既存機能**: なし。`registerSameDay` / `registerBeforeDeadline` はprivateメソッドであり、`registerParticipations` からのみ呼ばれる。呼び出し元の外部インターフェース（APIエンドポイント）は変更なし。
- **破壊的変更**: なし。APIリクエスト/レスポンスの形式は変更しないため、フロントエンドへの影響なし。
- **テスト影響**: `PracticeParticipantServiceTest` の既存テスト4件（`findByYearAndMonth` をモックしている箇所）のモック対象メソッドを変更する必要がある。

## 5. 設計判断の根拠

- **項目1**: `findByYearAndMonthAndOrganizationId` は `PracticeSessionRepository` に既に存在するため、新しいRepositoryメソッドの追加は不要。最小限の変更で修正できる。
- **項目2**: 既存のController実装パターン（`httpRequest.getAttribute("currentUserId")` / `getAttribute("currentUserRole")`）に従い、一貫性のある実装とする。ADMIN/SUPER_ADMINに制限を設けないのは、管理者が代理で参加登録を行うユースケースが存在するため。
- **項目3**: 機能的な変更ではなくドキュメントの整合性確保のみ。
