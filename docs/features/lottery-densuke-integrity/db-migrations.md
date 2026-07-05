# DB マイグレーション（本改修で追加した CHECK 制約更新）

本改修で通知 enum 値を追加した。Hibernate `ddl-auto=update` は既存の CHECK 制約を
自動更新しないため、対応する CHECK 制約更新 SQL を `database/` に追加した。
**未適用のまま該当通知が発火すると CHECK 違反で挿入が失敗する**（CLAUDE.md「DBマイグレーション適用ルール」）。

| SQL | 対象 | 追加値 | 未適用時の影響 |
|---|---|---|---|
| `database/add_densuke_name_collision_notification_type_check.sql` | `notifications.type` | `DENSUKE_NAME_COLLISION`（A-4/タスク5） | 名寄せ衝突のアプリ内通知挿入が CHECK 違反で失敗し、**伝助インポートのTXを巻き込む恐れ** |
| `database/add_admin_densuke_confirm_diff_message_log_check.sql` | `line_message_log.notification_type` | `ADMIN_DENSUKE_CONFIRM_DIFF`（A-3）、`ADMIN_DENSUKE_NAME_COLLISION`（A-4・LINE通知）、`ADMIN_DENSUKE_ROWID_ISSUE`（B-3・行不一致通知） | 確定前差分・名寄せ衝突・row_id問題の LINE ログ挿入が失敗（`@Async` 内で捕捉されるため確定/同期はブロックしないが通知が失われる） |

いずれも DROP → 現行 enum 全値 ＋ 新値で ADD し直す冪等スクリプト。**現行 enum の全値を列挙済み**
（`Notification.NotificationType` / `LineMessageLog.LineNotificationType` と一致）。

## 本番適用（保留中）
- **本コミット時点で本番未適用**。理由: 本番 Postgres への接続が認証段階で EOF となり疎通不可
  （Render 無料枠スピンダウン or 接続情報ローテーションの可能性。タスク6と同じ状況）。
- 接続復旧後、`CLAUDE.local.md` の接続情報で psql / JDBC ツール経由で両 SQL を適用し、
  `\d line_message_log` / `\d notifications` で CHECK 制約に新値が含まれることを確認する。
- デプロイ前後いずれかで必ず適用すること（デプロイ後、名寄せ衝突検知や確定前差分検知が
  発火する前に適用されている必要がある）。
