---
status: completed
---
# LINE連携完了時の抽選結果自動送信 要件定義書

## 1. 概要

### 目的
LINE連携を完了したユーザーに対し、確定済みの抽選結果がある場合は自動的にLINE通知を送信する。

### 背景・動機
- LINE連携が抽選実行後に行われた場合、そのユーザーだけ抽選結果のLINE通知を受け取れない
- 管理者が個別に再送する手段もないため、LINE未連携だったユーザーが連携した瞬間に自動で送りたい

## 2. ユーザーストーリー

### 対象ユーザー
- LINE連携を完了した全ユーザー（PLAYER / ADMIN / SUPER_ADMIN）

### 利用シナリオ
1. 管理者が4月分の抽選を確定する
2. ユーザーAはまだLINE未連携だったため、抽選結果のLINE通知を受け取っていない
3. ユーザーAが通知設定画面からLINE連携を完了する
4. 連携完了メッセージ（「連携が完了しました！」）に続いて、4月分（+ 確定済みの5月分があればそちらも）の抽選結果が自動的にLINEで送信される

## 3. 機能要件

### 3.1 処理フロー

1. `LineChannelService.linkChannel()` が呼ばれてLINE連携が完了する
2. 連携完了後、当月・翌月の確定済み抽選結果を確認する
3. そのユーザーが WON または WAITLISTED のステータスを持つセッションがある場合、抽選結果をLINE送信する
4. 送信形式は既存の `sendLotteryResults()` と同じ（全当選→テキスト1通、一部落選→Flex Message）

### 3.2 ビジネスルール

- 送信対象月: 当月 + 翌月（両方に確定済み結果がある場合は両方送信）
- 抽選確定済み（`LotteryExecution.confirmedAt != null`）の月のみ対象
- そのユーザーのステータスが WON または WAITLISTED である参加レコードがある場合のみ送信
- LINE連携自体の成否には影響しない（送信失敗してもログ記録のみ、連携は巻き戻さない）
- 連携完了メッセージ（「連携が完了しました！」）とは別メッセージとして送信

### 3.3 エラーケース

- 確定済み抽選結果がない場合 → 何も送信しない（正常動作）
- そのユーザーの参加レコードがない場合 → 何も送信しない（正常動作）
- LINE送信に失敗した場合 → ログにエラー記録、連携は成功のまま

## 4. 技術設計

### 4.1 API設計

新規APIエンドポイントは不要。Webhook内の既存フローに処理を追加する。

### 4.2 DB設計

新規テーブル・カラムの追加は不要。既存の `LotteryExecution` / `PracticeParticipant` を参照するのみ。

### 4.3 バックエンド設計

#### LineNotificationService
新規メソッド `sendLotteryResultsForPlayer(Long playerId)` を追加:
- 当月・翌月を算出
- 各月について `LotteryService.isLotteryConfirmed(year, month)` で確定済みかチェック
- 確定済みの場合、`PracticeParticipantRepository` からそのユーザーの WON/WAITLISTED レコードを取得
- 既存の `sendLotteryResults()` と同じ形式で送信（個人分のみ）

#### LineChannelService.linkChannel()
連携完了後に `LineNotificationService.sendLotteryResultsForPlayer(playerId)` を呼び出す。
try-catch で囲み、例外時はログ記録のみ。

### 4.4 処理フロー

```
Webhook: verifyCode() 成功
  → linkChannel(channelId, lineUserId)  // 連携完了
    → lineNotificationService.sendLotteryResultsForPlayer(playerId)
      → 当月チェック: isLotteryConfirmed(2026, 4)?
        → YES → そのユーザーの参加レコード取得 → LINE送信
      → 翌月チェック: isLotteryConfirmed(2026, 5)?
        → YES → そのユーザーの参加レコード取得 → LINE送信
```

## 5. 影響範囲

### 変更が必要な既存ファイル
| ファイル | 変更内容 |
|---------|---------|
| `LineNotificationService.java` | `sendLotteryResultsForPlayer(playerId)` メソッド追加 |
| `LineChannelService.java` | `linkChannel()` 内に抽選結果送信呼び出しを追加 |

### 既存機能への影響
- LINE連携フロー自体は変更なし（送信処理を追加するのみ）
- 連携完了の応答時間が若干延びる可能性がある（LINE送信分）が、実用上問題なし
- 既存の `sendLotteryResults()` は変更しない（新規メソッドで個人向け送信を実装）

## 6. 設計判断の根拠

| 判断 | 理由 |
|------|------|
| `linkChannel()` 内で直接呼び出す | 非同期にする必要がないほど軽い処理（1ユーザー分のみ）。Webhook応答後の送信なので遅延も許容範囲 |
| 既存の `sendLotteryResults()` を変更せず新メソッド追加 | 既存の一括送信ロジックに影響を与えない。個人向けの送信ロジックを分離する方が責務が明確 |
| 当月 + 翌月を対象 | 月末に連携した場合に翌月分も漏れなく送信するため |
