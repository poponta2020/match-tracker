---
status: completed
---
# 抜け番機能 改修実装手順書

## 実装タスク

### タスク1: bye_activities テーブルに deleted_at カラム追加
- [x] 完了
- **概要:** 論理削除用の `deleted_at` カラムを追加し、ユニーク制約を部分インデックスに変更する
- **変更対象ファイル:**
  - `database/alter_bye_activities_add_soft_delete.sql` — 新規作成。ALTER TABLE で `deleted_at` カラム追加、ユニーク制約を部分インデックスに変更、インデックス追加
- **依存タスク:** なし
- **対応Issue:** #17

### タスク2: ByeActivity Entity に deletedAt フィールド追加
- [x] 完了
- **概要:** Entity に `deletedAt` フィールドと `isDeleted()` メソッドを追加する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/ByeActivity.java` — `deletedAt` フィールド（`@Column(name = "deleted_at")`）と `isDeleted()` メソッドを追加
- **依存タスク:** タスク1
- **対応Issue:** #18

### タスク3: ByeActivityRepository の論理削除対応
- [ ] 完了
- **概要:** 全クエリメソッドに `deleted_at IS NULL` 条件を追加し、物理削除メソッドを論理削除メソッドに置換する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/ByeActivityRepository.java` — 全 find メソッドを `@Query` に変更して `AND b.deletedAt IS NULL` 追加。`deleteBySessionDateAndMatchNumber` を廃止し、`@Modifying @Query` で `softDeleteBySessionDateAndMatchNumber` を新規追加
- **依存タスク:** タスク2
- **対応Issue:** #19

### タスク4: ByeActivityService の論理削除対応
- [ ] 完了
- **概要:** Service 層の削除ロジックを論理削除に変更する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/ByeActivityService.java` — `delete()` を `deletedAt = now()` + `save()` に変更。`createBatch()` の `deleteBySessionDateAndMatchNumber` を `softDeleteBySessionDateAndMatchNumber` に変更。`getPlayerIdForActivity()` で論理削除済みを除外
- **依存タスク:** タスク3
- **対応Issue:** #20

### タスク5: MatchForm に手動抜け番切り替え機能を追加
- [ ] 完了
- **概要:** ペアリング未作成の試合で「抜け番として記録」ボタンを表示し、手動で抜け番UIに切り替えられるようにする
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/MatchForm.jsx` — `manualByeMode` state を追加。通常フォーム内に「抜け番として記録」ボタンを追加（ペアリング未作成時のみ表示）。抜け番UI内に「通常入力に戻る」ボタンを追加。表示条件を `(isByeMatch || manualByeMode) && !isEdit` に変更
- **依存タスク:** なし（項目1は項目2と独立）
- **対応Issue:** #21

## 実装順序
1. タスク1: DB マイグレーション（依存なし）
2. タスク2: Entity 変更（タスク1に依存）
3. タスク3: Repository 変更（タスク2に依存）
4. タスク4: Service 変更（タスク3に依存）
5. タスク5: フロントエンド変更（独立、タスク1-4と並行可能）
