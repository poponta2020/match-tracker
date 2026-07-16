# データベース設計

> **責務:** 全テーブル定義・ER図・初期データの唯一の置き場（テーブル定義はこのファイル群以外に書かない）

本番 introspect（2026-07-10）と照合済み（全39テーブル。詳細は `docs/features/ai-dev-optimization/prod-introspect-*`）。

## ER図

旧SPECIFICATION §6.1 と旧DESIGN §3.1（いずれも2026-07-10のドメイン分割で本ファイルに統合済み。原文は git 履歴参照）の記法・詳細度が異なるため、両方をそのまま統合して掲載する（一方が他方の上位互換ではなく、SPEC は players 起点の関係を網羅的に列挙し、DESIGN は organizations・invite_tokens 等の団体系テーブルの関係を含む）。

### 旧SPECIFICATION由来（テキスト表記）

```
players ──< matches (player1Id, player2Id, winnerId)
players ──< practice_participants (playerId)
players ──< player_profiles (playerId)
players ──< match_pairings (player1Id, player2Id)
players ──< bye_activities (playerId)
players ──< notifications (playerId)
players ──< push_subscriptions (playerId)
players ──< push_notification_preferences (playerId, organizationId)
players ──< line_channel_assignments (playerId)
players ──< line_linking_codes (playerId)
players ──< line_notification_preferences (playerId)
players ──< line_message_log (playerId)
players ──< mentor_relationships (mentorId, menteeId)
players ──< match_comments (menteeId, authorId)

line_channels ──< line_channel_assignments (lineChannelId)
line_channels ──< line_linking_codes (lineChannelId)
line_channels ──< line_message_log (lineChannelId)

organizations ──< line_broadcast_group (organizationId)
line_broadcast_group ──< line_channels (broadcastGroupId, GROUP種別botのみ)
line_broadcast_group ──< line_broadcast_send (broadcastGroupId)
practice_sessions ──< line_broadcast_send (sessionId)
line_broadcast_group ──< line_chat_reservations (broadcastGroupId)
practice_sessions ──< line_chat_reservations (sessionId)

practice_sessions ──< practice_participants (sessionId)
practice_sessions ──< lottery_executions (sessionId)

practice_participants ──> lottery_executions (lotteryId)

venues ──< practice_sessions (venueId)
venues ──< venue_match_schedules (venueId)
```

### 旧DESIGN由来（ボックス図）

DESIGN 側は organizations・player_organizations・match_personal_notes・invite_tokens・densuke_urls など、SPEC の一覧に無い関係を含む（特に団体（organizations）を起点とした関係）。原文のまま掲載する。

```
[players] 1───多 [matches] 多───1 [players]
    │                   │
    │                   │1
    │                   多
    │              [match_personal_notes] 多───1 [players]
    │
    │1
    │
    多
[practice_participants] 多───1 [practice_sessions] 多───1 [venues]    [system_settings]
    │                                                       │
    │多                                                     │1
    │                                                       │
    1                                                       多
[match_pairings]                                    [venue_match_schedules]
    │
    │多
    │
    1
[player_profiles]

[lottery_executions]
[notifications] 多───1 [players]
[organizations] 1───多 [player_organizations] 多───1 [players]
               1───多 [practice_sessions]
               1───多 [system_settings]
               1───多 [invite_tokens]
               1───多 [push_notification_preferences]
               1───多 [line_notification_preferences]
[push_subscriptions] 多───1 [players]
[push_notification_preferences] 多───1 [players]
[densuke_urls] 多───1 [organizations]
[invite_tokens] 多───1 [players]  (created_by)
```

本番 introspect（2026-07-10）で確認済み: DESIGN 3.1 の図が示す `organizations 1───多 line_notification_preferences` の関係は正しい。`line_notification_preferences` には `organization_id` カラムが本番に存在し、一意制約も `(player_id, organization_id)` の複合UNIQUE（旧ドキュメントの「organization_id列なし・player_id単体UNIQUE」表記は誤りだったため修正済み。詳細は [db-tables-2.md](./db-tables-2.md) の該当テーブル参照）。

## テーブル定義

行数の都合により以下の3ファイルに分割する（テーブル名単位で SPEC と DESIGN を統合。統合方針・凡例は各ファイル冒頭を参照）。

- [db-tables-1.md](./db-tables-1.md) — players 〜 venue_match_schedules（旧ドキュメントに記載のあるテーブル群 前半）
- [db-tables-2.md](./db-tables-2.md) — densuke_urls 〜 match_videos（旧ドキュメントに記載のあるテーブル群 後半）
- [db-tables-3.md](./db-tables-3.md) — 本番 introspect のみに存在し旧ドキュメント未記載だった7テーブル（adjacent_room_notifications / room_availability_cache / kaderu_sync_trigger_events / line_confirmation_tokens / match_card_placements / match_otetsuki_details / card_rule_nonce）

## データベース初期データ

出典: DESIGN.md 付録B「データベース初期データ」

### デフォルトスーパー管理者

```sql
INSERT INTO players (name, password, gender, role, created_at, updated_at)
VALUES ('土居悠太', 'password123', '男性', 'SUPER_ADMIN', NOW(), NOW());
```
