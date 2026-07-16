# データベース設計 — テーブル定義（2/3: densuke_urls 〜 match_videos）

> [docs/design/db.md](./db.md) の「テーブル定義」の分割ファイル。責務・ER図・初期データは db.md を参照。前半は [db-tables-1.md](./db-tables-1.md)、本番のみテーブルは [db-tables-3.md](./db-tables-3.md)。

#### densuke_urls（伝助URL管理）

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| year | INT | NOT NULL | 年 |
| month | INT | NOT NULL | 月 |
| url | VARCHAR(500) | NOT NULL | 伝助URL |
| organization_id | BIGINT | NOT NULL, FK | 団体ID（organizations.id） |
| densuke_sd | VARCHAR(32) | — | 伝助の編集用シークレット (sd)。アプリから自動作成した URL のみ値が入る。手動登録は NULL。将来の編集・削除 API で使用想定（出典: DESIGN のみ） |
| created_at | TIMESTAMP | NOT NULL | — |
| updated_at | TIMESTAMP | NOT NULL | — |

（本番 introspect 照合済み: url は VARCHAR(500)、created_at/updated_at は TIMESTAMP。⚠すべて解消）

一意制約: `(year, month, organization_id)`（`densuke_urls_year_month_org_key`）
インデックス: `densuke_urls_pkey(id)`

---

#### lottery_executions（抽選実行履歴）

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| target_year | INT | NOT NULL | 対象年 |
| target_month | INT | NOT NULL | 対象月 |
| session_id | BIGINT | — | 対象セッションID（再抽選時） |
| execution_type | VARCHAR(20) | NOT NULL | AUTO/MANUAL/MANUAL_RELOTTERY |
| executed_by | BIGINT | — | 実行者（自動はNULL） |
| executed_at | TIMESTAMP | NOT NULL | 実行日時 |
| status | VARCHAR(10) | NOT NULL | SUCCESS/FAILED/PARTIAL |
| details | TEXT | — | 処理詳細（DESIGNではJSON形式と付記） |
| confirmed_at | TIMESTAMP | — | 確定日時（NULL = 未確定） |
| confirmed_by | BIGINT | — | 確定者のプレイヤーID（NULL = 未確定） |
| organization_id | BIGINT | — | 団体ID |
| seed | BIGINT | — | 抽選シード値（出典: SPEC のみ）⚠本番に存在しない |
| priority_player_ids | TEXT | — | 管理者指定優先選手IDリスト（JSON配列文字列）（出典: SPEC のみ） |

（本番 introspect 照合済み: execution_type/status は VARCHAR、executed_at は TIMESTAMP。⚠一部解消。`seed` 列は本番に存在しないため要確認として残す（SPECのみの記載でDB定義と乖離。アプリコードでの使用実態は未調査））

インデックス: `lottery_executions_pkey(id)`, `idx_lottery_target` (target_year, target_month), `idx_lottery_org` (target_year, target_month, organization_id)（出典: DESIGN のみ）

---

#### notifications（アプリ内通知）

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| player_id | BIGINT | NOT NULL | 通知先プレイヤー |
| type | VARCHAR(30) | NOT NULL | LOTTERY_WON/LOTTERY_ALL_WON/LOTTERY_REMAINING_WON/LOTTERY_WAITLISTED/WAITLIST_OFFER/OFFER_EXPIRING/OFFER_EXPIRED/CHANNEL_RECLAIM_WARNING/DENSUKE_UNMATCHED_NAMES |
| title | VARCHAR(200) | NOT NULL | 通知タイトル |
| message | TEXT | — | 通知本文 |
| reference_type | VARCHAR(50) | — | 参照先エンティティ種別（PRACTICE_SESSION/PRACTICE_PARTICIPANT） |
| reference_id | BIGINT | — | 参照先ID |
| is_read | BOOLEAN | NOT NULL, DEFAULT FALSE | 既読フラグ |
| created_at | TIMESTAMP | NOT NULL | — |
| deleted_at | TIMESTAMP | — | 論理削除日時 |

（本番 introspect 照合済み: type は VARCHAR(30)、title は VARCHAR(200)、created_at/deleted_at は TIMESTAMP。⚠すべて解消）

NotificationType列挙型（出典: DESIGN のみ。値の説明が詳しい）:
- `LOTTERY_WON` - 抽選結果（当選）※廃止：既存データ参照用に残す
- `LOTTERY_ALL_WON` - 抽選結果（全試合当選まとめ）
- `LOTTERY_REMAINING_WON` - 抽選結果（落選以外は全当選まとめ）
- `LOTTERY_WAITLISTED` - 抽選結果（落選・キャンセル待ち）※セッション単位にまとめ
- `WAITLIST_OFFER` - キャンセル待ちからの繰り上げ連絡
- `OFFER_EXPIRING` - 繰り上げ応答期限切れ警告
- `OFFER_EXPIRED` - 繰り上げ応答期限切れ
- `CHANNEL_RECLAIM_WARNING` - LINEチャネル回収警告
- `DENSUKE_UNMATCHED_NAMES` - 伝助同期：未登録者あり（管理者向け）

インデックス: `notifications_pkey(id)`, `idx_notification_player` (player_id), `idx_notification_read` (player_id, is_read)（出典: DESIGN のみ）

---

#### push_subscriptions（Web Pushサブスクリプション）

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| player_id | BIGINT | NOT NULL | プレイヤーID |
| endpoint | TEXT | NOT NULL | Push APIエンドポイント |
| p256dh_key | VARCHAR(500) | NOT NULL | 暗号化キー（P-256 DH公開鍵） |
| auth_key | VARCHAR(500) | NOT NULL | 認証キー |
| user_agent | VARCHAR(500) | — | ブラウザ情報 |
| created_at | TIMESTAMP | NOT NULL | — |
| updated_at | TIMESTAMP | NOT NULL | — |

（本番 introspect 照合済み: p256dh_key/auth_key は VARCHAR(500) NOT NULL、user_agent は VARCHAR(500)、created_at/updated_at は TIMESTAMP。⚠すべて解消。DESIGN表記が正だった）

インデックス: `push_subscriptions_pkey(id)`, `idx_push_player` (player_id)（出典: DESIGN のみ）

---

#### push_notification_preferences（Web Push通知設定）

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| player_id | BIGINT | NOT NULL | プレイヤーID |
| organization_id | BIGINT | NOT NULL | 団体ID（出典: SPEC のみ。DESIGNには本カラムの記載なし） |
| enabled | BOOLEAN | NOT NULL, DEFAULT FALSE | Web Push全体のON/OFF |
| lottery_result | BOOLEAN | NOT NULL, DEFAULT TRUE | 抽選結果 |
| waitlist_offer | BOOLEAN | NOT NULL, DEFAULT TRUE | 繰り上げ連絡 |
| offer_expiring | BOOLEAN | NOT NULL, DEFAULT TRUE | 期限切れ警告 |
| offer_expired | BOOLEAN | NOT NULL, DEFAULT TRUE | 期限切れ |
| channel_reclaim_warning | BOOLEAN | NOT NULL, DEFAULT TRUE | LINE回収警告 |
| densuke_unmatched | BOOLEAN | NOT NULL, DEFAULT TRUE | 伝助未登録者 |
| created_at | TIMESTAMP | NOT NULL | — |
| updated_at | TIMESTAMP | NOT NULL | — |
| adjacent_room | BOOLEAN | NOT NULL, DEFAULT TRUE | 隣室空き通知（`adjacent_room_notifications` テーブルに対応。出典: introspect から追加） |

（本番 introspect 照合済み: created_at/updated_at は TIMESTAMP。⚠解消。**一意制約は SPEC 表記が正**——本番は `(player_id, organization_id)` の複合UNIQUEで、団体単位に通知設定を分けている。DESIGN の「player_id 単体UNIQUE」表記は誤り）

一意制約: `(player_id, organization_id)`（`uk_pnp_player_org` / `ukb2uj1bchm5iy7eugk2f4sxue3`。同一内容の索引が2本存在＝命名の重複、実害なし）
インデックス: `push_notification_preferences_pkey(id)`, `idx_pnp_player` (player_id)（出典: DESIGN のみ）

---

#### line_channels（LINEチャネル情報）

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| channel_name | VARCHAR(100) | — | 管理用表示名 |
| line_channel_id | VARCHAR(50) | NOT NULL, UNIQUE | LINE発行のチャネルID |
| channel_secret | VARCHAR(255) | NOT NULL | チャネルシークレット（DESIGNでは「暗号化保存」と付記） |
| channel_access_token | TEXT | NOT NULL | アクセストークン（DESIGNでは「暗号化保存」と付記） |
| channel_type | VARCHAR(10) | NOT NULL, DEFAULT 'PLAYER' | チャネル用途（PLAYER: 選手用 / ADMIN: 管理者用 / GROUP: 全体LINE配信用bot） |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'AVAILABLE' | AVAILABLE/ASSIGNED/LINKED/DISABLED |
| friend_add_url | TEXT | — | 友だち追加URL |
| monthly_message_count | INT | NOT NULL, DEFAULT 0 | 当月送信数 |
| message_count_reset_at | TIMESTAMP | — | リセット日時 |
| created_at | TIMESTAMP | NOT NULL | — |
| updated_at | TIMESTAMP | NOT NULL | — |
| qr_code_url | TEXT | — | 友だち追加QRコード画像URL（出典: introspect から追加） |
| basic_id | VARCHAR(30) | — | LINE公式アカウントのベーシックID（出典: introspect から追加） |
| broadcast_group_id | BIGINT | — | 全体配信グループ line_broadcast_group.id（GROUP種別botのみ設定・個人割当プールとの分離用） |
| line_group_id | VARCHAR(50) | — | 招待された全体LINEグループのID（join Webhookで捕捉・未捕捉なら配信不可） |

（本番 introspect 照合済み: message_count_reset_at/created_at/updated_at は TIMESTAMP。⚠解消。qr_code_url/basic_id は旧ドキュメント未記載のため追加。channel_type に CHECK 制約は無い＝GROUP 追加時の張り直し不要。broadcast_group_id/line_group_id は card-division-group-broadcast で追加）

インデックス: `line_channels_pkey(id)`, `idx_line_channel_status` (status), `idx_line_channel_line_id` (line_channel_id), `idx_line_channel_type` (channel_type), `idx_line_channel_broadcast_group` (broadcast_group_id)（出典: DESIGN のみ）

---

#### line_channel_assignments（LINEチャネル割り当て）

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| line_channel_id | BIGINT | NOT NULL, FK | line_channels.id |
| player_id | BIGINT | NOT NULL, FK | players.id |
| line_user_id | VARCHAR(50) | — | LINE userId（follow時に取得） |
| channel_type | VARCHAR(10) | NOT NULL, DEFAULT 'PLAYER' | チャネル用途（PLAYER/ADMIN、非正規化） |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'PENDING' | PENDING/LINKED/UNLINKED/RECLAIMED |
| assigned_at | TIMESTAMP | NOT NULL | 割り当て日時 |
| linked_at | TIMESTAMP | — | LINKED化日時 |
| unlinked_at | TIMESTAMP | — | 解除日時 |
| reclaim_warned_at | TIMESTAMP | — | 回収警告日時 |
| created_at | TIMESTAMP | NOT NULL | — |

（本番 introspect 照合済み: assigned_at/linked_at/unlinked_at/reclaim_warned_at/created_at は TIMESTAMP。⚠解消）

インデックス: `line_channel_assignments_pkey(id)`, `idx_lca_channel` (line_channel_id), `idx_lca_player` (player_id), `idx_lca_status` (status), `idx_lca_player_type` (player_id, channel_type)（出典: DESIGN のみ）。加えて同一内容の旧命名インデックス `idx_line_assignment_channel`(line_channel_id) / `idx_line_assignment_player`(player_id) / `idx_line_assignment_status`(status) が重複して存在（実害なし、出典: introspect から追加）

---

#### line_linking_codes（ワンタイムコード）

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| player_id | BIGINT | NOT NULL, FK | players.id |
| line_channel_id | BIGINT | NOT NULL, FK | line_channels.id |
| code | VARCHAR(8) | NOT NULL, UNIQUE | ワンタイムコード（英数字8桁） |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'ACTIVE' | ACTIVE/USED/EXPIRED/INVALIDATED |
| attempt_count | INT | NOT NULL, DEFAULT 0 | 検証失敗回数（DESIGNでは「5回で無効化」と付記） |
| expires_at | TIMESTAMP | NOT NULL | 有効期限（DESIGNでは「発行から10分後」と付記） |
| used_at | TIMESTAMP | — | 使用日時 |
| created_at | TIMESTAMP | NOT NULL | — |

（本番 introspect 照合済み: expires_at/used_at/created_at は TIMESTAMP。⚠解消）

インデックス: `line_linking_codes_pkey(id)`, `idx_llc_player` (player_id), `idx_llc_code` (code), `idx_llc_channel` (line_channel_id)（出典: DESIGN のみ）。加えて `code` の UNIQUE 制約に対応する索引 `ukcfx6g7kuuiuc7y6pws2mrkumd` も存在（`code` 列の `UNIQUE` 制約と同一内容、実害なし）

---

#### line_notification_preferences（LINE通知設定）

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| player_id | BIGINT | NOT NULL, FK | players.id |
| organization_id | BIGINT | NOT NULL, FK | 団体ID（organizations.id）。本番に存在（本番 introspect 照合済み。出典: introspect から追加） |
| lottery_result | BOOLEAN | NOT NULL, DEFAULT TRUE | 抽選結果 |
| waitlist_offer | BOOLEAN | NOT NULL, DEFAULT TRUE | キャンセル待ち連絡 |
| offer_expired | BOOLEAN | NOT NULL, DEFAULT TRUE | オファー期限切れ |
| match_pairing | BOOLEAN | NOT NULL, DEFAULT TRUE | 対戦組み合わせ |
| practice_reminder | BOOLEAN | NOT NULL, DEFAULT TRUE | 参加予定リマインダー |
| deadline_reminder | BOOLEAN | NOT NULL, DEFAULT TRUE | 締め切りリマインダー |
| same_day_confirmation | BOOLEAN | NOT NULL, DEFAULT TRUE | 当日参加者確定通知（WON参加者向け） |
| same_day_cancel | BOOLEAN | NOT NULL, DEFAULT TRUE | 当日キャンセル通知 |
| same_day_vacancy | BOOLEAN | NOT NULL, DEFAULT TRUE | 当日空き募集通知 |
| admin_same_day_confirmation | BOOLEAN | NOT NULL, DEFAULT TRUE | 参加者確定通知（管理者向け） |
| admin_same_day_cancel | BOOLEAN | NOT NULL, DEFAULT TRUE | 当日キャンセル・参加・空き枠通知（管理者向け） |
| admin_waitlist_update | BOOLEAN | NOT NULL, DEFAULT TRUE | 管理者向けキャンセル待ち繰り上げ更新通知（出典: introspect から追加・業務説明は未整備） |
| mentor_comment | BOOLEAN | NOT NULL, DEFAULT TRUE | メンターコメント・メモ更新通知 |
| densuke_page_created | BOOLEAN | NOT NULL, DEFAULT TRUE | 伝助ページ自動作成完了通知（出典: introspect から追加） |
| match_video_registered | BOOLEAN | NOT NULL, DEFAULT TRUE | 試合動画登録通知 |
| card_division_reminder | BOOLEAN | NOT NULL, DEFAULT FALSE | 札分けリマインダー（1試合目開始3時間前）。購読制のため既存慣習と逆で**デフォルト FALSE** |
| updated_at | TIMESTAMP | NOT NULL | — |

（本番 introspect 照合済み: updated_at は TIMESTAMP。⚠解消。**一意制約・organization_id は DESIGN の ER図（db.md）表記が正**——本番には `organization_id` カラムが存在し、一意制約は `player_id` 単体ではなく `(player_id, organization_id)` の複合UNIQUE。旧ドキュメントの「player_id 単体UNIQUE・organization_id列なし」表記は誤り。加えて `admin_waitlist_update` / `densuke_page_created` の2列が旧ドキュメント未記載だったため追加）
⚠要確認: admin_same_day_confirmation の説明 — SPECでは「参加者確定通知（管理者向け）」、DESIGNでは「参加者確定通知（管理者向け・SUPER_ADMIN専用）」と対象範囲の記載が異なる（意味論の差のため要確認のまま残す）

一意制約: `(player_id, organization_id)`（`uk_lnp_player_org` / `uk75ftqtqrp1sgx9dtdhlw7xvl`。同一内容の索引が2本存在＝命名の重複、実害なし）
インデックス: `line_notification_preferences_pkey(id)`, `idx_lnp_player` (player_id)

---

#### line_notification_schedule_settings（スケジュール型通知設定）

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| notification_type | VARCHAR(30) | NOT NULL, UNIQUE | PRACTICE_REMINDER/DEADLINE_REMINDER |
| enabled | BOOLEAN | NOT NULL, DEFAULT TRUE | 有効/無効 |
| days_before | VARCHAR(50) | NOT NULL | 送信日数JSON配列（DESIGN例: `"[3, 1]"`） |
| updated_at | TIMESTAMP | NOT NULL | — |
| updated_by | BIGINT | — | 最終更新者 |

（本番 introspect 照合済み: updated_at は TIMESTAMP。⚠解消）

インデックス: `line_notification_schedule_settings_pkey(id)`, `ukp3hpgn8bh5gih8xvvrm92aj2u` UNIQUE (notification_type)

---

#### line_message_log（LINE送信ログ）

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| line_channel_id | BIGINT | NOT NULL, FK | line_channels.id |
| player_id | BIGINT | NOT NULL, FK | players.id |
| notification_type | VARCHAR(30) | NOT NULL | 通知種別 |
| message_content | TEXT | NOT NULL | メッセージ内容 |
| status | VARCHAR(20) | NOT NULL | SUCCESS/FAILED/SKIPPED/RESERVED |
| error_message | TEXT | — | エラー内容 |
| reference_id | BIGINT | — | 参照先ID（出典: introspect から追加・業務説明は未整備） |
| dedupe_key | VARCHAR(100) | — | 重複排除キー（セッションID等） |
| sent_at | TIMESTAMP | NOT NULL | 送信日時 |

（本番 introspect 照合済み: sent_at は TIMESTAMP。⚠解消。reference_id 列は旧ドキュメント未記載のため追加）

部分ユニーク制約: `(player_id, notification_type, dedupe_key, sent_at::date) WHERE status IN ('SUCCESS', 'RESERVED') AND dedupe_key IS NOT NULL`（`idx_lml_dedupe_daily_unique`。本番 introspect 照合済み: `dedupe_key IS NOT NULL` 条件が旧ドキュメント未記載だったため追加）

インデックス: `line_message_log_pkey(id)`, `idx_lml_channel` (line_channel_id), `idx_lml_player` (player_id), `idx_lml_type_sent` (notification_type, sent_at), `idx_lml_dedupe` (player_id, notification_type, dedupe_key, sent_at)（出典: DESIGN のみ）。加えて同一内容の旧命名インデックス `idx_line_log_channel` / `idx_line_log_player` / `idx_line_log_type_sent` が重複して存在（実害なし、出典: introspect から追加）

**重複送信防止方式:** RESERVED → SUCCESS の2段階ステータス遷移により、送信権の原子的確保とクラッシュ耐性を両立。dedupeKeyは試合単位通知では `sessionId:matchNumber`、セッション統合通知では `sessionId` を使用。

重複送信防止フロー（原子的送信権確保方式。出典: DESIGN のみ）:
1. `tryAcquireSendRight`: dedupeKey付きで `RESERVED` ステータスのログをINSERT（ON CONFLICT DO NOTHING）
2. LINE APIで送信実行
3. 成功時: `markReservationSucceeded` で RESERVED → SUCCESS に更新
4. 失敗時: `markReservationFailed` で RESERVED → FAILED に更新（次回リトライ可能）
5. クラッシュ時: RESERVED が残留するため、次回送信前に `releaseStaleReservations` で10分超過のRESERVEDをFAILEDに解放し、再送信を可能にする

dedupeKeyの粒度（出典: DESIGN のみ）:
- 試合単位通知（sendSameDayVacancyNotification）: `sessionId:matchNumber`
- セッション統合通知（sendConsolidatedSameDayVacancyNotification）: `sessionId`

---

#### line_broadcast_group（全体LINE配信グループ設定）

団体ごとの全体LINEグループ（bot群ローテで札分けを一斉配信）。card-division-group-broadcast で追加。

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| organization_id | BIGINT | NOT NULL | 配信対象団体 organizations.id |
| name | VARCHAR(100) | NOT NULL | 管理用表示名 |
| enabled | BOOLEAN | NOT NULL, DEFAULT TRUE | 無効なら配信対象外 |
| expected_recipient_count | INT | — | 想定受信数（枠会計用。未設定なら送信時に実グループ人数APIで解決） |
| created_at | TIMESTAMP | NOT NULL, DEFAULT now() | — |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT now() | — |

インデックス: `line_broadcast_group_pkey(id)`, `idx_lbg_org_unique` (organization_id・UNIQUE＝1団体1グループを担保)

---

#### line_broadcast_send（全体LINE配信ログ兼 dedupe）

全体配信の送信ログ兼「一度きり」担保。個人通知の player_id スコープ dedupe は流用できないため、(配信グループ, セッション) スコープの専用表。card-division-group-broadcast で追加。

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| broadcast_group_id | BIGINT | NOT NULL | line_broadcast_group.id |
| session_id | BIGINT | NOT NULL | practice_sessions.id |
| line_channel_id | BIGINT | — | 配信に使用した bot（GROUP種別チャネル）。枯渇 SKIPPED 時は null |
| recipient_count | INT | — | 想定受信数（成功時に bot の当月消費へ即時加算する値） |
| status | VARCHAR(20) | NOT NULL, CHECK | RESERVED/SUCCESS/FAILED/SKIPPED |
| error_message | TEXT | — | 失敗・スキップ理由 |
| sent_at | TIMESTAMP | NOT NULL | 送信（予約）日時 |

部分ユニーク制約: `(broadcast_group_id, session_id) WHERE status IN ('SUCCESS', 'RESERVED')`（`idx_lbs_dedupe`。`INSERT ... ON CONFLICT DO NOTHING` で原子的に一度きりを担保。FAILED/SKIPPED は再試行のため対象外）

インデックス: `line_broadcast_send_pkey(id)`, `idx_lbs_group_session` (broadcast_group_id, session_id), `idx_lbs_group_sent` (broadcast_group_id, sent_at)

**重複送信防止・回復フロー（個人版と同型）:** `tryAcquireBroadcastRight`(RESERVED insert・ON CONFLICT) → 送信 → 成功 `markBroadcastSucceeded`(SUCCESS) / 失敗 `markBroadcastFailed`(FAILED)。クラッシュ時は `releaseStaleBroadcastReservations`（10分＝配信ウィンドウ最小30分より短い）で残留 RESERVED を FAILED に解放し同一ウィンドウ内で再送可能にする。

---

#### mentor_relationships（メンター関係）

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| mentor_id | BIGINT | NOT NULL, FK | players.id（メンター） |
| mentee_id | BIGINT | NOT NULL, FK | players.id（メンティー） |
| organization_id | BIGINT | NOT NULL, FK | organizations.id |
| status | VARCHAR(20) | NOT NULL, CHECK | PENDING / ACTIVE / REJECTED |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | — |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | — |

（本番 introspect 照合済み: created_at/updated_at は TIMESTAMP。⚠解消。CHECK制約は introspect の対象外のため未照合のまま残す）

ユニーク制約: `(mentor_id, mentee_id, organization_id)`（`uq_mentor_relationship` / `ukd3oqxohw1wi4onmjdc286fs44`。同一内容の索引が2本存在＝命名の重複、実害なし）
インデックス: `mentor_relationships_pkey(id)`

---

#### match_comments（メンターコメント）

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| match_id | BIGINT | NOT NULL, FK | matches.id |
| mentee_id | BIGINT | NOT NULL, FK | players.id（コメント対象のメンティー） |
| author_id | BIGINT | NOT NULL, FK | players.id（投稿者） |
| content | TEXT | NOT NULL | コメント内容 |
| line_notified | BOOLEAN | NOT NULL, DEFAULT FALSE | LINE通知送信済みフラグ（出典: DESIGN のみ） |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | — |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | — |
| deleted_at | TIMESTAMP | — | 論理削除 |

（本番 introspect 照合済み: created_at/updated_at/deleted_at は TIMESTAMP。⚠解消）

インデックス: `match_comments_pkey(id)`, `idx_match_comments_thread(match_id, mentee_id, deleted_at, created_at)`

---

#### invite_tokens（招待トークン）（出典: DESIGN のみ）

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | ID |
| token | VARCHAR(36) | NOT NULL, UNIQUE | トークン文字列（UUID） |
| type | VARCHAR(20) | NOT NULL | MULTI_USE（グループ用）/ SINGLE_USE（個人用） |
| organization_id | BIGINT | NOT NULL | 紐付ける団体ID（登録された選手はこの団体に所属。DB FK は付与せず、`InviteTokenService` で `organizations.id` の存在検証を行う） |
| expires_at | TIMESTAMP | NOT NULL | 有効期限 |
| used_at | TIMESTAMP | — | 使用日時（SINGLE_USEのみ） |
| used_by | BIGINT | — | 使用した選手ID（SINGLE_USEのみ） |
| created_by | BIGINT | NOT NULL | 発行者の選手ID |
| created_at | TIMESTAMP | NOT NULL | 作成日時 |

（本番 introspect 照合済み: type は VARCHAR(20)（MySQL時代の ENUM 表記は残滓）、expires_at/used_at/created_at は TIMESTAMP。⚠すべて解消）

インデックス: `invite_tokens_pkey(id)`, `idx_invite_tokens_token` (token) UNIQUE

---

#### match_videos（試合動画台帳）（出典: DESIGN のみ）

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | 動画ID |
| match_date | DATE | NOT NULL | 試合日 |
| match_number | INT | NOT NULL | 試合番号（その日の何試合目か） |
| player1_id | BIGINT | NOT NULL, FK(players.id) ON DELETE RESTRICT | 選手1ID（player1_id < player2_id を保証） |
| player2_id | BIGINT | NOT NULL, FK(players.id) ON DELETE RESTRICT | 選手2ID |
| provider | VARCHAR(20) | NOT NULL, DEFAULT 'YOUTUBE' | 動画プロバイダ（現状は YOUTUBE のみ） |
| video_url | TEXT | NOT NULL | 動画URL |
| youtube_video_id | VARCHAR(20) | — | YouTube動画ID（埋め込み用） |
| title | VARCHAR(255) | — | 動画タイトル |
| created_by | BIGINT | NOT NULL, FK(players.id) ON DELETE RESTRICT | 登録者ID |
| updated_by | BIGINT | NOT NULL, FK(players.id) ON DELETE RESTRICT | 更新者ID |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 登録日時 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 更新日時 |

（本番 introspect 照合済み: created_at/updated_at は TIMESTAMP。⚠解消）

制約: UNIQUE: `uq_match_videos_match` (match_date, match_number, player1_id, player2_id)

インデックス: `match_videos_pkey(id)`, `idx_match_videos_player1` (player1_id), `idx_match_videos_player2` (player2_id)

特殊ロジック:
- `matches` / `match_pairings` とは FK を持たず、`(match_date, match_number, player1_id, player2_id)` の自然キーで対応付く。これにより結果未入力（ペアリングのみ）の試合にも動画を登録でき、結果入力後に自動で試合詳細へ表示される
- `@PrePersist`/`@PreUpdate`で player1_id < player2_id を自動保証（`MatchVideo` エンティティ。選手IDのみ入れ替え）
- 動画台帳（倉庫）のページング検索 `MatchVideoRepository.search()` は選手ID・開始日・終了日を全て nullable で受け取り、null の条件は無視する。年月絞り込みは呼び出し側で年月→開始日/終了日の範囲に変換して渡す
  - nullable な `LocalDate` パラメータは、PostgreSQL JDBC の型推論で bytea と誤推論されるのを防ぐため JPQL の `CAST(:startDate AS date)` で明示的に date 型へキャストしている
  - `MatchVideoService.search()` は範囲変換前に `year`/`month` を検証する（`year` 非null時のみ）。`month` は 1〜12（`MSG_INVALID_MONTH`）、`year` は 2000〜2100（`MSG_INVALID_YEAR`）。範囲外は `IllegalArgumentException`（GlobalExceptionHandler で400）。`month` 単独指定（`year==null`）は既存挙動どおり無視する。`LocalDate.of` の `DateTimeException`→500 を防ぐため、`YearMonthRange.of` 側にも month の防御的ガードを置く（ユーザー向け400メッセージは search 側で出す）
