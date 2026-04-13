---
status: completed
---
# 練習参加登録（practice-participation） 改修実装手順書

## 実装タスク

### タスク1: クロス団体soft-deleteバグの修正
- [x] 完了
- **概要:** `registerSameDay` / `registerBeforeDeadline` の `findByYearAndMonth` を `findByYearAndMonthAndOrganizationId` に変更し、soft-deleteの範囲を対象団体に限定する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeParticipantService.java`
    - `registerSameDay` のシグネチャに `Long organizationId` 追加（L171）
    - L175-177: `findByYearAndMonth(...)` → `findByYearAndMonthAndOrganizationId(..., organizationId)`
    - `registerBeforeDeadline` のシグネチャに `Long organizationId` 追加（L214）
    - L215-217: `findByYearAndMonth(...)` → `findByYearAndMonthAndOrganizationId(..., organizationId)`
    - `registerParticipations` 内の呼び出し箇所（L156, L159）で `organizationId` を渡す
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/PracticeParticipantServiceTest.java`
    - `findByYearAndMonth` をモックしている既存テスト4件のモック対象を `findByYearAndMonthAndOrganizationId` に変更
    - クロス団体シナリオのテスト追加（別団体のセッションがsoft-deleteされないことを検証）
- **依存タスク:** なし
- **対応Issue:** #442

### タスク2: playerId検証の追加
- [ ] 完了
- **概要:** `POST /participations` エンドポイントにPLAYERロールの自己検証を追加する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/PracticeSessionController.java`
    - `registerParticipations` メソッド（L272-278）に `HttpServletRequest httpRequest` パラメータ追加
    - `currentUserId` / `currentUserRole` を取得し、PLAYERロールの場合に `playerId` との一致を検証
    - 不一致時に `ForbiddenException("他の選手の参加登録は操作できません")` をスロー
- **依存タスク:** なし
- **対応Issue:** #443

### タスク3: DESIGN.md の更新
- [ ] 完了
- **概要:** DESIGN.md 5.3.2（画面設計）と 4.6（API権限）を実装に合わせて更新する
- **変更対象ファイル:**
  - `docs/DESIGN.md`
    - セクション 5.3.2: 表示内容を実装に合わせて更新（矢印ナビゲーション、団体色分け、参加人数バッジ、抽選ステータス表示、SAME_DAY確認ダイアログ、締切後チェックボックス制限）
    - セクション 5.3.2: データフローに `getPlayerParticipationStatus` API、`getDeadline` API、`getOrganizations` API を追加
    - セクション 4.6: `POST /api/practice-sessions/participations` の権限を「PLAYER: 自分のみ / ADMIN, SUPER_ADMIN: 全選手」に更新
- **依存タスク:** タスク2（権限記載はタスク2の実装内容に依存）
- **対応Issue:** #444

## 実装順序

1. タスク1（依存なし） — クロス団体soft-deleteバグの修正
2. タスク2（依存なし） — playerId検証の追加
3. タスク3（タスク2に依存） — DESIGN.md の更新

タスク1とタスク2は独立しており並行実装可能。タスク3はタスク2の権限仕様が確定してから実施する。
