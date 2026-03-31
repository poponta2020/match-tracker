---
status: completed
---
# LINE連携完了時の抽選結果自動送信 実装手順書

## 実装タスク

### タスク1: LineNotificationService — sendLotteryResultsForPlayer() 追加
- [x] 完了
- **概要:** 特定ユーザー1人に対して、確定済みの抽選結果をLINE送信する新規メソッドを追加する。既存の `sendLotteryResults()` のロジック（全当選→テキスト、一部落選→Flex Message）を個人向けに再利用する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java`
    - 新規メソッド `sendLotteryResultsForPlayer(Long playerId)`:
      1. 当月・翌月の年月を算出
      2. 各月について `LotteryService.isLotteryConfirmed(year, month)` で確定済みかチェック
      3. 確定済みの場合、`PracticeParticipantRepository.findBySessionDateYearAndMonth()` から全参加者を取得し、対象ユーザーの WON/WAITLISTED レコードだけフィルタ
      4. 既存の送信ロジック（全当選テキスト / 一部落選Flex / 全落選Flex）と同じ形式で送信
- **依存タスク:** なし

### タスク2: LineChannelService.linkChannel() — 抽選結果送信呼び出し追加
- [x] 完了
- **概要:** LINE連携完了直後に `sendLotteryResultsForPlayer()` を呼び出す。例外時はログ記録のみで連携自体は成功させる。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineChannelService.java`
    - `linkChannel()` メソッド末尾に追加:
      ```java
      try {
          lineNotificationService.sendLotteryResultsForPlayer(assignment.getPlayerId());
      } catch (Exception e) {
          log.error("Failed to send lottery results after LINE linking for player {}", assignment.getPlayerId(), e);
      }
      ```
    - `LineNotificationService` の DI を追加
- **依存タスク:** タスク1

## 実装順序
1. タスク1（依存なし）
2. タスク2（タスク1に依存）
