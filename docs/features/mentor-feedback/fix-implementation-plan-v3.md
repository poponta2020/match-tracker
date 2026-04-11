---
status: completed
---
# メンティーメモ更新時のメンターLINE通知 改修実装手順書

## 実装タスク

### タスク1: LineNotificationType に MENTEE_MEMO_UPDATE を追加
- [x] 完了
- **概要:** 通知種別enum に新しい値を追加し、通知設定のマッピングを定義する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/LineMessageLog.java` — `LineNotificationType` enum に `MENTEE_MEMO_UPDATE` を追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java` — `isLineTypeEnabled()` の switch 文に `case MENTEE_MEMO_UPDATE -> pref.getMentorComment()` を追加
- **依存タスク:** なし
- **対応Issue:** #429

### タスク2: LineNotificationService にメモ更新通知メソッドを追加
- [x] 完了
- **概要:** Flex Message を構築してメンターに送信するメソッドを追加する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java` — `sendMemoUpdateFlexNotification(Long menteeId, Match match, String memoContent)` public メソッドと `buildMemoUpdateFlex(String menteeName, Match match, String opponentName, String memoContent)` private メソッドを追加
- **依存タスク:** タスク1
- **対応Issue:** #430

### タスク3: MatchService にメモ変更検知・通知トリガーを追加
- [x] 完了
- **概要:** メモ保存時に内容の変更を検知し、変更があった場合にメンターへ通知を送る
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchService.java` — `LineNotificationService` を依存注入。`upsertPersonalNote()` 内で旧メモと新メモを比較し、変更時に `lineNotificationService.sendMemoUpdateFlexNotification()` を呼び出し（通知失敗時もメモ保存が成功するよう try-catch で囲む）
- **依存タスク:** タスク2
- **対応Issue:** #431

### タスク4: テスト検証
- [x] 完了
- **概要:** 既存テストが通ることを確認し、デグレードがないことを検証する
- **変更対象ファイル:**
  - 既存テストの実行（`./gradlew test`）
- **依存タスク:** タスク3
- **対応Issue:** #432

## 実装順序
1. タスク1（依存なし）
2. タスク2（タスク1に依存）
3. タスク3（タスク2に依存）
4. タスク4（タスク3に依存）
