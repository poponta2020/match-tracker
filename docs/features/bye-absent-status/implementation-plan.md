---
status: completed
---
# 抜け番「休み」ステータス 実装手順書

## 実装タスク

### タスク1: ActivityType Enum に ABSENT を追加
- [x] 完了
- **概要:** バックエンドの `ActivityType` Enum に `ABSENT("休み")` を追加する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/ActivityType.java` — `ABSENT("休み")` を追加
- **依存タスク:** なし
- **対応Issue:** #201

### タスク2: PracticeParticipantRepository にクエリ追加
- [x] 完了
- **概要:** `PracticeParticipant` の検索・削除に必要なリポジトリクエリを追加する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/PracticeParticipantRepository.java` — セッションID + プレイヤーID + `matchNumber IS NULL` での検索クエリと削除クエリを追加
- **依存タスク:** なし
- **対応Issue:** #202

### タスク3: ByeActivityService に PracticeParticipant 評価ロジックを追加
- [x] 完了
- **概要:** `create()` / `createBatch()` / `update()` の保存後に、同日・同選手の全 `ByeActivity` を確認し、全て ABSENT なら `PracticeParticipant`（`matchNumber = null`）を削除、そうでなければ復元するロジックを追加する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/ByeActivityService.java` — 以下を実装:
    1. `PracticeParticipantRepository` と `PracticeSessionRepository` の依存注入を追加
    2. `evaluatePracticeParticipant(LocalDate sessionDate, Long playerId)` メソッドを新規作成
       - 同日・同選手の全 `ByeActivity` を取得
       - 全レコードが `ABSENT` → `PracticeParticipant`（`matchNumber = null`）を削除
       - 1つでも `ABSENT` 以外 → `PracticeParticipant`（`matchNumber = null`）が存在しなければ作成
    3. `create()` / `createBatch()` / `update()` の各メソッド末尾で `evaluatePracticeParticipant()` を呼び出す
- **依存タスク:** タスク1, タスク2
- **対応Issue:** #203

### タスク4: フロントエンド — アクティビティ選択肢に「休み」を追加
- [x] 完了
- **概要:** 抜け番アクティビティのドロップダウンに「休み」を追加する
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/BulkResultInput.jsx` — `ACTIVITY_TYPES` 定数に `{ value: 'ABSENT', label: '休み' }` を追加
  - `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx` — `ACTIVITY_TYPES` 定数に `{ value: 'ABSENT', label: '休み' }` を追加
  - `karuta-tracker-ui/src/pages/matches/MatchForm.jsx` — アクティビティ選択肢に「休み」を追加
- **依存タスク:** なし
- **対応Issue:** #204

### タスク5: フロントエンド — MatchResultsView に「休み」表示対応
- [x] 完了
- **概要:** 試合結果表示画面で「休み」アクティビティのアイコンを表示できるようにする
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/MatchResultsView.jsx` — `ACTIVITY_ICONS` マッピングに `ABSENT` 用アイコンを追加
- **依存タスク:** なし
- **対応Issue:** #205

### タスク6: テスト
- [x] 完了
- **概要:** ABSENT に関するテストケースを追加する
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/ByeActivityServiceTest.java` — 以下のテストを追加:
    - ABSENT で保存時に全ABSENTなら PracticeParticipant が削除されること
    - 一部のみABSENTなら PracticeParticipant が残ること
    - ABSENT → 他アクティビティに変更時に PracticeParticipant が復元されること
    - createBatch で ABSENT を含む場合の動作
- **依存タスク:** タスク1, タスク2, タスク3
- **対応Issue:** #206

## 実装順序
1. タスク1（依存なし）+ タスク2（依存なし）+ タスク4（依存なし）+ タスク5（依存なし）— 並列実行可能
2. タスク3（タスク1, タスク2 に依存）
3. タスク6（タスク1, タスク2, タスク3 に依存）
