---
status: completed
---

# 伝助同期・キャンセル待ち機能 改修実装手順書（2026-04-02 監査対応）

## 実装タスク

### タスク1: WaitlistPromotionServiceTest のアサーション修正
- [x] 完了
- **概要:** `handleSameDayJoin_noVacancy` テストのエラーメッセージ期待値を実装に合わせて修正
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/WaitlistPromotionServiceTest.java` — L422: `"先を越されました"` → `"定員に達してしまいました"` に修正
- **依存タスク:** なし
- **対応Issue:** （Issue作成後に記入）

---

### タスク2: DensukeImportServiceTest のフェーズ判定mock修正
- [x] 完了
- **概要:** `lotteryExecutionRepository.findTopBySessionIdOrderByExecutedAtDesc()` のmockを `lotteryDeadlineHelper` / `lotteryService` のmockに置き換え、現行のフェーズ判定ロジックに合わせる
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/DensukeImportServiceTest.java` — 以下のテストメソッドを修正:
    - `testImportCreatesSessionAndRegistersParticipants` (L92): `lotteryExecutionRepository` mock → `lotteryDeadlineHelper.getDeadlineType(1L)` → MONTHLY, `isBeforeDeadline` → true
    - `testImportUsesExistingSession` (L121): 同上
    - `testImportSkipsLotteryExecutedSession` (L133-151): Phase2スキップを正しく検証するよう書き直し。`getDeadlineType` → MONTHLY, `isBeforeDeadline` → false, `isLotteryConfirmed` → false
    - `testImportTracksUnmatchedNames` (L166): `lotteryExecutionRepository` mock → `lotteryDeadlineHelper` mock
    - `testImportNotifiesAdminsOnUnmatchedNames` (L194): 同上
    - `testImportRemovesAbsentParticipants` (L234): 同上
    - `testRegisterAndSync` (L260): 同上
    - `testImportWithTargetDateFilter` (L301): 同上
    - `testCreatedByIsPassedToSession` (L328): 同上
    - `testImportDoesNotRemoveDirtyParticipants` (L455): 同上
    - `testImportSetsCleanFlagForDensukeAddedParticipants` (L509): 同上
- **依存タスク:** なし
- **対応Issue:** （Issue作成後に記入）

---

### タスク3: DensukeSyncService の @Transactional 削除
- [x] 完了
- **概要:** `syncForOrganization()` から `@Transactional` を削除し、外部HTTP通信中のDBコネクション保持を解消する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeSyncService.java` — L31: `@Transactional` アノテーション削除。import文 `org.springframework.transaction.annotation.Transactional` も不要になるため削除
- **依存タスク:** なし
- **対応Issue:** （Issue作成後に記入）
- **注意:** 本番環境の伝助同期に影響する変更。`writeToDensuke()` と `importFromDensuke()` が各自 `@Transactional` を持つことを事前に確認済み。

---

## 実装順序

1. タスク1（依存なし・テストのみ）
2. タスク2（依存なし・テストのみ）
3. タスク3（依存なし・プロダクションコード変更）

タスク1・2はテストコードのみの変更であり安全。タスク3は本番影響があるため最後に実施し、変更前後でテストが全てパスすることを確認する。
