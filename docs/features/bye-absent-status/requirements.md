---
status: completed
---
# 抜け番「休み」ステータス 要件定義書

## 1. 概要
- **目的:** 練習に登録済み・対戦も組まれていたのに当日無断で来なかった選手を「休み」として記録し、通常の抜け番と区別する
- **背景:** 現在の抜け番は全て「練習に来ているが試合相手がいない」前提だが、実際には無断欠席者も抜け番に含まれるケースがある。参加率の正確性と無断欠席の追跡のために区別が必要

## 2. ユーザーストーリー
- **対象ユーザー:** 管理者（ADMIN / SUPER_ADMIN）
- **ユーザーの目的:** 無断欠席した選手を正しく記録し、参加率に影響させず、欠席回数を追跡したい
- **利用シナリオ:**
  1. 対戦を組んだ後、当日来ない選手がいることが判明
  2. 管理者が対戦を組みなおす（来ない選手を外す）→ 来なかった選手は抜け番リストに入る
  3. 試合結果一括入力画面（BulkResultInput）またはPairingGeneratorで、抜け番の無断欠席者のアクティビティを「休み」に設定
  4. 「休み」に設定された選手は参加率にカウントされない
  5. 無断欠席回数は内部データとして蓄積される

## 3. 機能要件
### 3.1 画面仕様
- **操作場所:** 試合結果一括入力画面（BulkResultInput.jsx）およびPairingGenerator.jsxの抜け番アクティビティ選択ドロップダウン
- **UI変更:** 既存のアクティビティ選択肢（読み / 一人取り / 見学 / 見学対応 / その他）に「休み」を追加
- **操作フロー:** 他のアクティビティ選択と全く同じ（ドロップダウンから選択するだけ）
- **各試合（第1試合〜第7試合）ごとに個別に設定可能**
- **MatchResultsView.jsx:** 「休み」アクティビティの表示対応（アイコン追加）

### 3.2 ビジネスルール
- **無断欠席回数のカウント:** `ByeActivity` の `activity_type = ABSENT` のレコード数を試合単位で集計（第1試合がABSENTなら1回としてカウント）
- **参加率（`PracticeParticipant`）の削除条件:** その日の該当選手の全ての `ByeActivity` が `ABSENT` の場合のみ、`PracticeParticipant`（`matchNumber = null`）を削除 → 参加率にカウントされなくなる
- **一部のみABSENTの場合:** `PracticeParticipant` は削除しない（来ている試合があるなら参加扱い）
- **「休み」→ 他アクティビティへの変更時:** 全ABSENTでなくなった場合、`PracticeParticipant` を復元する
- **無断欠席回数はUI上には表示しない**（内部データとしてのみ保持）
- **「休み」選択時、`freeText` は不要**（OTHERのみfreeText入力）

## 4. 技術設計
### 4.1 API設計
既存APIの拡張のみ。新規エンドポイントは不要。

| エンドポイント | 変更内容 |
|---------------|---------|
| `POST /api/bye-activities/batch` | `activityType` に `ABSENT` を受付。保存後に `PracticeParticipant` 評価ロジック実行 |
| `PUT /api/bye-activities/{id}` | `activityType` に `ABSENT` を受付。更新後に `PracticeParticipant` 評価ロジック実行 |
| `POST /api/bye-activities` | Enumに追加されるため受付可能（本人入力用だが休みを自分で選ぶことは想定外） |

### 4.2 DB設計
テーブル変更なし。`bye_activities.activity_type` カラム（VARCHAR(20)）に `ABSENT` という新しい値が入るのみ。

### 4.3 フロントエンド設計

**変更対象ファイル:**

| ファイル | 変更内容 |
|---------|---------|
| `BulkResultInput.jsx` | `ACTIVITY_TYPES` 定数に `{ value: 'ABSENT', label: '休み' }` を追加 |
| `PairingGenerator.jsx` | `ACTIVITY_TYPES` 定数に `{ value: 'ABSENT', label: '休み' }` を追加 |
| `MatchResultsView.jsx` | `ACTIVITY_ICONS` マッピングに `ABSENT` 用アイコンを追加 |
| `MatchForm.jsx` | アクティビティ選択肢に「休み」を追加（本人入力画面だが整合性のため） |

### 4.4 バックエンド設計

**変更対象ファイル:**

| ファイル | 変更内容 |
|---------|---------|
| `ActivityType.java` | `ABSENT("休み")` を追加 |
| `ByeActivityService.java` | `create()` / `createBatch()` / `update()` に保存後の `PracticeParticipant` 評価ロジックを追加 |
| `PracticeParticipantRepository.java` | セッションID + プレイヤーID + `matchNumber IS NULL` での検索・削除用クエリを追加 |

**PracticeParticipant 評価ロジック（ByeActivityServiceに実装）:**
1. 保存/更新対象の `sessionDate` と `playerId` を取得
2. 同日・同選手の全 `ByeActivity` を取得
3. 全レコードが `ABSENT` → `PracticeParticipant`（`matchNumber = null`）を削除
4. 1つでも `ABSENT` 以外がある → `PracticeParticipant`（`matchNumber = null`）が存在しなければ復元（作成）

**無断欠席回数の集計:**
- 既存の `getByPlayer(playerId, ActivityType.ABSENT)` で取得可能（新規実装不要）

## 5. 影響範囲

### 5.1 変更が必要な既存ファイル

**バックエンド:**
- `ActivityType.java` — Enum値追加
- `ByeActivityService.java` — PracticeParticipant評価ロジック追加
- `PracticeParticipantRepository.java` — クエリ追加

**フロントエンド:**
- `BulkResultInput.jsx` — ACTIVITY_TYPES定数に追加
- `PairingGenerator.jsx` — ACTIVITY_TYPES定数に追加
- `MatchResultsView.jsx` — ACTIVITY_ICONSマッピングに追加
- `MatchForm.jsx` — アクティビティ選択肢に追加

### 5.2 既存機能への影響
- **参加率計算（`PracticeParticipantService`）:** 計算ロジック自体は変更不要。`PracticeParticipant` レコードの有無で計算されるため、レコード削除/復元で正しく反映される
- **対戦組み合わせ（`MatchPairingService`）:** 影響なし。抜け番の `PracticeParticipant` 作成ロジックはそのまま。ABSENTの評価は後続の `ByeActivityService` で行う
- **DTO（`ByeActivityDto` / リクエストDTO）:** 変更不要。Enumに追加されるだけで自動対応
- **既存の抜け番データ:** 影響なし。既存データは従来の `ActivityType` のまま

### 5.3 注意点
- `ByeActivityService` から `PracticeParticipantRepository` と `PracticeSessionRepository` への依存が新たに発生する
- `PracticeParticipant` の復元時、`sessionId` が必要なため `PracticeSessionRepository` でセッションを取得する必要がある

## 6. 設計判断の根拠
- **`PracticeParticipant` の削除/復元方式を採用した理由:** 参加率の計算ロジック（`PracticeParticipantService`）を変更せずに済むため。計算ロジック側にByeActivity依存を持ち込むと複雑化する
- **別テーブルではなく `ActivityType` Enum拡張とした理由:** 「休み」は抜け番の一種であり、DBスキーマ変更なしで対応でき、集計も既存のクエリで可能
- **全ABSENT時のみ `PracticeParticipant` 削除とした理由:** 一部の試合だけ休みで途中から来た場合は「参加した」と見なすのが妥当
