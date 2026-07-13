---
status: approved
issue: 1034
---
# バグ改修要件: 隣室通知スケジューラーが通知済み段階の再実行で毎回 rollback-only エラーを出す

## 再現手順

1. 隣室チェック対象会場（例: venue 3 すずらん）の未来セッションを残り人数 ≤ 4 の状態にし、`room_availability_cache` の隣室（はまなす / evening）を「○」にする
2. スケジューラー `AdjacentRoomNotificationScheduler#checkCapacityAndNotify` を実行 → 通知送信＋ `adjacent_room_notifications` に (session_id, remaining_count) が記録される
3. 残り人数を変えずに再度スケジューラーを実行する

- 期待: 通知済み段階なので静かにスキップして正常終了する
- 実際: `Failed to process adjacent room check for session {id}: Transaction silently rolled back because it has been marked as rollback-only` の ERROR ログが出る（本番では 2026-07-13 12:30Z/13:00Z/14:00Z/14:30Z に session 1000 で発生。11:30Z/12:00Z/13:30Z は remaining=4→3→1 と段階が変わったため正常送信）

## 根本原因

`AdjacentRoomNotificationScheduler#processSession`（[AdjacentRoomNotificationScheduler.java:123-134](../../../karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/AdjacentRoomNotificationScheduler.java)）が、重複通知防止を「`save`+`flush` の一意制約違反（`DataIntegrityViolationException`）をトランザクション内で catch して握りつぶす」方式で実装している。

Hibernate は flush 失敗時点でトランザクションを **rollback-only にマーク**するため、catch して `return 0` しても `TransactionTemplate.execute` のコミット処理で `UnexpectedRollbackException`（"Transaction silently rolled back because it has been marked as rollback-only"）が throw され、スケジューラー外側の catch（74-82行）が ERROR ログを出す。

- 実害はログ汚染（定員接近が続く限り 30 分ごとに ERROR）。重複スキップ経路はコミットすべき変更を持たないためデータ欠損はない
- `AdjacentRoomNotificationRepository#existsBySessionIdAndRemainingCount` が定義済みなのに未使用
- 既存ユニットテストは `TransactionTemplate` をモックしてコールバック即実行にしているため、コミット時の rollback-only 検査を再現できず検出できなかった

## 修正方針

1. **事前存在チェック**: insert 前に `existsBySessionIdAndRemainingCount` で通知済み段階を判定し、通知済みなら例外を発生させずに `return 0`（通常経路。これで本番の 30 分ごと ERROR は消える）
2. **並列競合バックストップの無害化**: 一意制約 + `save`/`flush` + catch は複数インスタンス同時実行時の TOCTOU 対策として残すが、catch 内で `TransactionStatus#setRollbackOnly()` を呼びローカル rollback-only を立てる。これにより `TransactionTemplate` はコミットを試行せず**静かにロールバック**し（この経路にコミットすべき変更はない）、`UnexpectedRollbackException` は発生しない
3. `processSession` のシグネチャに `TransactionStatus` を追加（呼び出し元のラムダから渡す）

## Acceptance Criteria

| ID | 条件 | 検証手段 |
|----|------|------|
| AC-1 | 通知済みの (session_id, remaining_count) が存在する状態で、実 DB トランザクション（TestContainers PostgreSQL + 実 TransactionTemplate）のスケジューラー実行をしても ERROR ログ・例外が発生せず、通知の再送・重複レコードも発生しない | auto-test（統合回帰テスト） |
| AC-2 | 既存テスト・ビルドがすべて成功する（デグレなし） | auto-test（`./gradlew test`） |
| AC-3 | 事前チェックをすり抜けて insert が一意制約違反になった場合（並列競合）も例外を漏らさずスキップし、トランザクションをローカル rollback-only にしてコミット試行を回避する | auto-test（ユニットテスト） |
| AC-4 | 未通知の新しい残り人数段階では従来どおり通知が送信される | auto-test（既存ユニットテスト） |

## Non-goals

- 通知文言・宛先・閾値（残り4人）・実行間隔（30分）・対象会場の変更
- remaining が増減した場合の再通知ポリシーの見直し
- `OrganizationService#ensurePlayerBelongsToOrganization` の同型潜在バグ（トランザクション内での一意制約違反握りつぶし）— 別タスクに切り出し済み
- 周辺コードのリファクタリング

## 影響範囲

- `karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/AdjacentRoomNotificationScheduler.java` — `processSession` の重複判定ロジックのみ変更。呼び出し元は自クラスの `checkCapacityAndNotify` のみ（`@Scheduled` エントリポイント、外部呼び出しなし）
- `AdjacentRoomNotificationRepository#existsBySessionIdAndRemainingCount` — 既存定義を利用（変更なし）
- 通知送信経路（`NotificationService#createAndPush`）・`AdjacentRoomService` — 変更なし
- FE への影響なし（バックエンドのスケジューラー内部のみ）
