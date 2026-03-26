---
status: completed
audit_source: 会話内レポート
selected_items: [1, 2]
---
# 抜け番機能 改修要件定義書

## 1. 改修概要

- **対象機能:** 抜け番（bye）機能
- **改修の背景:** `/audit-feature` による監査で以下の問題が検出された
  1. MatchFormでの抜け番判定がペアリング存在前提のため、組み合わせ未作成時に本人が抜け番入力できない
  2. `bye_activities` テーブルのみ物理削除で、他テーブル（`players`）の論理削除パターンと不統一
- **改修スコープ:** 上記2項目

---

## 2. 改修内容

### 2.1 MatchFormでの手動抜け番入力対応

**現状の問題:**
- `MatchForm.jsx:251-253` の判定条件 `!myPairing && matchPairings.length > 0` により、ペアリング未作成時は `matchPairings.length === 0` で抜け番と判定されない
- 本人が抜け番だったと知っていても、活動種別を入力する手段がない

**修正方針:**
- ペアリング未作成の試合で、通常フォーム内に「抜け番として記録」ボタンを追加
- ボタン押下で `isByeMatch = true` に切り替え、既存の抜け番UIを表示
- 抜け番UIから通常フォームに戻す「戻る」ボタンも追加

**修正後のあるべき姿:**
- ペアリング作成済みの試合 → 従来通り自動判定（変更なし）
- ペアリング未作成の試合 → 通常フォーム + 「抜け番として記録」ボタンが表示される
- ユーザーが「抜け番として記録」を押すと抜け番活動UIに切り替わる

### 2.2 bye_activities の論理削除統一

**現状の問題:**
- `bye_activities` テーブルに `deleted_at` カラムがなく、物理削除（SQL DELETE）を使用
- `players` テーブルは `deleted_at` による論理削除パターン
- `ByeActivityService.createBatch()` で既存レコードを物理削除→再作成

**修正方針:**
- `bye_activities` に `deleted_at TIMESTAMP NULL` カラムを追加
- 全削除操作を論理削除（`deleted_at = CURRENT_TIMESTAMP`）に変更
- 全取得クエリに `deleted_at IS NULL` 条件を追加
- ユニーク制約を論理削除対応に修正

**修正後のあるべき姿:**
- 削除操作は `deleted_at` に現在日時を設定するだけで、レコードは残る
- 取得クエリは論理削除されたレコードを自動的に除外
- `createBatch` は既存レコードを論理削除→新規作成

---

## 3. 技術設計

### 3.1 API変更

**変更なし。** 既存の6エンドポイントのインターフェースはそのまま維持。内部ロジックのみ変更。

### 3.2 DB変更

**`bye_activities` テーブルへのカラム追加:**
```sql
ALTER TABLE bye_activities ADD COLUMN deleted_at TIMESTAMP NULL DEFAULT NULL;
```

**ユニーク制約の変更:**
- 現在: `UNIQUE (session_date, match_number, player_id)`
- 変更後: 論理削除されていないレコード間でのユニーク性を保証
- PostgreSQL の場合、部分ユニークインデックスを使用:
  ```sql
  DROP CONSTRAINT uk_bye_activities_unique;
  CREATE UNIQUE INDEX uk_bye_activities_unique
    ON bye_activities (session_date, match_number, player_id)
    WHERE deleted_at IS NULL;
  ```

**インデックス追加:**
```sql
CREATE INDEX idx_bye_activities_deleted_at ON bye_activities (deleted_at);
```

### 3.3 フロントエンド変更

**変更対象: `MatchForm.jsx` のみ**

| 変更箇所 | 内容 |
|---------|------|
| 通常フォーム表示部分（行662-810付近） | 「抜け番として記録」ボタンを対戦相手セクション付近に追加 |
| 抜け番UI表示部分（行590-661付近） | 「通常入力に戻る」ボタンを追加 |
| state追加 | `manualByeMode`（手動で抜け番モードに切り替えたかのフラグ） |

**表示条件ロジック:**
```
// 「抜け番として記録」ボタンの表示条件
!isByeMatch && !isEdit && !isExistingMatch && !pairing && ペアリング未作成の試合

// 抜け番UIの表示条件（既存 + 手動切り替え対応）
(isByeMatch || manualByeMode) && !isEdit
```

**他のフロントエンドファイルへの影響: なし**
- `BulkResultInput.jsx` — 変更不要（差分計算による判定で制限なし）
- `MatchResultsView.jsx` — 変更不要（表示のみ）
- `PairingGenerator.jsx` — 変更不要（waitingPlayerIds 直接指定）
- `byeActivities.js`（API） — 変更不要（インターフェース変更なし）

### 3.4 バックエンド変更

#### Entity: `ByeActivity.java`
- `deletedAt` フィールド追加（`LocalDateTime`, `@Column(name = "deleted_at")`）
- `isDeleted()` メソッド追加

#### Repository: `ByeActivityRepository.java`
全クエリメソッドに `deleted_at IS NULL` 条件を追加:

| メソッド | 変更内容 |
|---------|---------|
| `findBySessionDateOrderByMatchNumber` | `@Query` に `AND b.deletedAt IS NULL` 追加 |
| `findBySessionDateAndMatchNumber` | 同上 |
| `findByPlayerIdOrderBySessionDateDesc` | 同上 |
| `findByPlayerIdAndActivityTypeOrderBySessionDateDesc` | 同上 |
| `findBySessionDateAndMatchNumberAndPlayerId` | 同上 |
| `deleteBySessionDateAndMatchNumber` | 廃止 → `softDeleteBySessionDateAndMatchNumber` に置換 |

新規メソッド追加:
- `softDeleteBySessionDateAndMatchNumber` — `@Modifying @Query` で `SET deleted_at = CURRENT_TIMESTAMP`

#### Service: `ByeActivityService.java`

| メソッド | 変更内容 |
|---------|---------|
| `getByDate` | 変更なし（Repositoryで対応） |
| `getByDateAndMatch` | 変更なし（Repositoryで対応） |
| `getByPlayer` | 変更なし（Repositoryで対応） |
| `create` | upsert時に論理削除済みレコードの考慮（ユニーク制約対応） |
| `createBatch` | `deleteBySessionDateAndMatchNumber` → `softDeleteBySessionDateAndMatchNumber` に変更 |
| `update` | 変更なし |
| `delete` | `deleteById` → `deletedAt = now()` + `save()` に変更 |
| `getPlayerIdForActivity` | 論理削除済みを除外する条件追加 |

#### Controller: `ByeActivityController.java`
- 変更なし（Service層で対応）

#### DTO:
- 変更なし

---

## 4. 影響範囲

### 影響を受ける既存機能

| 機能 | 影響度 | 詳細 |
|------|-------|------|
| MatchForm（本人入力） | 中 | UIの追加変更あり（項目1） |
| BulkResultInput（管理者一括入力） | なし | 変更不要 |
| MatchResultsView（結果閲覧） | なし | 変更不要 |
| PairingGenerator（ペアリング生成） | なし | 変更不要 |
| ByeActivity CRUD API | 低 | 内部ロジックのみ変更、インターフェース維持 |

### 破壊的変更の有無
- **APIインターフェース:** 破壊的変更なし
- **DBスキーマ:** カラム追加（NULL許容）のため、既存データに影響なし
- **ユニーク制約:** 部分インデックスへの変更。既存データには影響なし（`deleted_at` は全て NULL）

### フロントエンド `byeActivityAPI.delete()` の使用状況
- フロントエンド全体で **呼び出しなし**（バックエンドのみ実装）
- 論理削除への変更はフロントエンドに影響しない

---

## 5. 設計判断の根拠

| 判断 | 理由 |
|------|------|
| 手動トグル方式を採用（項目1） | ペアリング未作成時のみ表示されるため、混乱を招きにくい。既存の自動判定ロジックは変更不要 |
| `deleted_at` カラム方式を採用（項目2） | `players` テーブルと同じパターンで統一性が高い。`@Where` アノテーションは使用せず、`players` と同様にカスタムクエリで対応 |
| PostgreSQL 部分ユニークインデックスを採用 | 論理削除されたレコードはユニーク制約から除外する必要があるため。PostgreSQLの `WHERE` 付きインデックスで対応 |
| `createBatch` は論理削除→再作成方式 | 既存の「削除→再作成」パターンを維持しつつ、物理削除を論理削除に置換するだけのため、影響が最小限 |
