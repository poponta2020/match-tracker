# データベース設計 — テーブル定義（3/3: 本番のみ・旧ドキュメント未記載テーブル）

> [docs/design/db.md](./db.md) の「テーブル定義」の分割ファイル。責務・ER図・初期データは db.md を参照。前半は [db-tables-1.md](./db-tables-1.md)・[db-tables-2.md](./db-tables-2.md)。
> 本ファイルは本番 introspect（2026-07-10）で判明した、旧 SPEC/DESIGN のいずれにも記載が無かった7テーブルをまとめる。列・インデックスは introspect の実物どおりで確定しているが、業務説明（用途・使用箇所）は未整備のものが多い。

## テーブル定義

#### adjacent_room_notifications（隣室空き通知送信済み記録）（出典: introspect から追加）

隣室（adjacent room）の空き状況変化の通知送信済みを記録し、重複送信を防止すると推測される（`push_notification_preferences.adjacent_room` の通知トグルに対応。業務説明は未整備）。

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| session_id | BIGINT | NOT NULL, FK(practice_sessions.id) | 対象練習セッションID（推測） |
| remaining_count | INT | NOT NULL | 残り枠数 |
| notified_at | TIMESTAMP | NOT NULL | 通知送信日時 |

一意制約: `(session_id, remaining_count)`（同一の空き枠数での重複通知を防止。`ukjqdtrfhxg89l4y9ef2s2w399i` と `uq_adjacent_room_notifications` の同一内容の索引が2本存在＝命名の重複、実害なし）
インデックス: `adjacent_room_notifications_pkey(id)`

---

#### room_availability_cache（会場空き状況チェック結果キャッシュ）（出典: introspect から追加）

会場（部屋）の空き状況チェック結果のキャッシュ。`room-checker`（CI ジョブ / スケジューラ）が外部予約システムを定期チェックして更新すると推測される（業務説明は未整備）。

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| room_name | VARCHAR(50) | NOT NULL | 部屋名 |
| target_date | DATE | NOT NULL | 対象日 |
| time_slot | VARCHAR(20) | NOT NULL | 時間帯区分（業務説明は未整備） |
| status | VARCHAR(10) | NOT NULL | 空き状況（業務説明は未整備） |
| checked_at | TIMESTAMP | NOT NULL | チェック日時 |

一意制約: `(room_name, target_date, time_slot)`（`uknu10eyrsrod48gv93n1a9qjtc` と `uq_room_availability_cache` の同一内容の索引が2本存在＝命名の重複、実害なし）
インデックス: `room_availability_cache_pkey(id)`

---

#### kaderu_sync_trigger_events（外部連携「kaderu」同期トリガーイベント）（出典: introspect から追加）

外部システム「kaderu」との同期をトリガーした GitHub Actions 実行のイベント記録と推測される（業務説明は未整備）。

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| organization_id | BIGINT | NOT NULL, FK(organizations.id) | 対象団体ID |
| triggered_by_player_id | BIGINT | NOT NULL, FK(players.id) | トリガーした選手ID |
| triggered_at | TIMESTAMP | NOT NULL | トリガー日時 |
| status | VARCHAR(16) | NOT NULL | 実行ステータス（業務説明は未整備） |
| github_run_id | BIGINT | — | GitHub Actions の run ID |
| completed_at | TIMESTAMP | — | 完了日時 |
| summary | TEXT | — | 実行結果サマリ |
| failure_reason | TEXT | — | 失敗理由 |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | — |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | — |

一意制約: `uk_kaderu_sync_pending(organization_id) WHERE status = 'PENDING'`（部分ユニークインデックス。団体ごとに同時実行中の同期は1件まで）
インデックス: `kaderu_sync_trigger_events_pkey(id)`, `idx_kaderu_sync_status_triggered(status, triggered_at)`

---

#### line_confirmation_tokens（LINE経由確認操作用ワンタイムトークン）（出典: introspect から追加）

LINEボット経由の確認アクション（action + params で処理内容を保持）用のワンタイムトークンと推測される（業務説明は未整備）。

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| token | VARCHAR(64) | NOT NULL, UNIQUE | トークン文字列 |
| action | VARCHAR(50) | NOT NULL | 確認対象のアクション種別（業務説明は未整備） |
| params | TEXT | NOT NULL | アクションのパラメータ（形式未整備、JSON等の可能性） |
| player_id | BIGINT | NOT NULL, FK(players.id) | 対象選手ID |
| created_at | TIMESTAMP | NOT NULL, DEFAULT now() | 発行日時 |
| expires_at | TIMESTAMP | NOT NULL | 有効期限 |
| used_at | TIMESTAMP | — | 使用日時 |

一意制約: `line_confirmation_tokens_token_key` UNIQUE (token)
インデックス: `line_confirmation_tokens_pkey(id)`, `idx_lct_expires(expires_at)`, `idx_lct_token(token)`

---

#### match_card_placements（試合の陣形・カード配置記録）（出典: introspect から追加）

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| match_id | BIGINT | NOT NULL, FK(matches.id) | 対象試合ID |
| player_id | BIGINT | NOT NULL, FK(players.id) | 対象選手ID |
| card_no | SMALLINT | NOT NULL | 札番号 |
| taken_by | VARCHAR(16) | NOT NULL | 取得方法（業務説明は未整備） |
| field | VARCHAR(8) | NOT NULL | 陣（自陣/敵陣、業務説明は未整備） |
| side | VARCHAR(8) | NOT NULL | 左右（業務説明は未整備） |
| tier | VARCHAR(8) | NOT NULL | 段（業務説明は未整備） |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | — |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | — |

一意制約: `uq_match_card_placements(match_id, player_id, card_no)`
インデックス: `match_card_placements_pkey(id)`, `idx_match_card_placements_player(player_id, match_id)`

---

#### match_otetsuki_details（お手付き詳細記録）（出典: introspect から追加）

`match_personal_notes.otetsuki_count`（お手付き回数の集計値）の内訳を1件ずつ記録するテーブルと推測される（業務説明は未整備）。

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| match_id | BIGINT | NOT NULL, FK(matches.id) | 対象試合ID |
| player_id | BIGINT | NOT NULL, FK(players.id) | 対象選手ID |
| seq | INT | NOT NULL | 試合内の連番 |
| otetsuki_type | VARCHAR(16) | NOT NULL | お手付き種別（業務説明は未整備） |
| hikkake_target | VARCHAR(24) | — | 引っかけ対象（業務説明は未整備） |
| anki_direction | VARCHAR(40) | — | 暗記方向（業務説明は未整備） |
| mishearing_read_card_no | SMALLINT | — | 聞き間違え：読まれた札番号 |
| mishearing_touched_card_no | SMALLINT | — | 聞き間違え：触った札番号 |
| other_text | TEXT | — | 「その他」自由記述 |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | — |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | — |

一意制約: `uq_match_otetsuki_details(match_id, player_id, seq)`
インデックス: `match_otetsuki_details_pkey(id)`, `idx_match_otetsuki_details_player(player_id, match_id)`

---

#### card_rule_nonce（カード配置ルール用nonce値）（出典: introspect から追加）

日付単位で `match_card_placements` のランダム配置生成に使うnonce値（乱数シード）を保持すると推測される（業務説明は未整備）。

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| session_date | DATE | NOT NULL | 対象日付 |
| nonce | INT | NOT NULL, DEFAULT 0 | nonce値 |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | — |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | — |

一意制約: `uq_card_rule_nonce_date(session_date)`
インデックス: `card_rule_nonce_pkey(id)`

---

#### auth_tokens（認証トークン）（出典: auth-tokenization で追加）

ログイン時にサーバが発行する認証トークンの状態を保持する。生トークンは保存せず SHA-256 ハッシュ（hex 64文字）のみを保存する（DB が漏洩しても保存値からトークンを再利用できないようにするため）。失効は `revoked_at` による論理失効で、行は削除しない。

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| player_id | BIGINT | NOT NULL | トークンの持ち主の選手ID（選手単位の一括失効に使う） |
| token_hash | VARCHAR(64) | NOT NULL, UNIQUE | 生トークンの SHA-256 hex。検証時の検索キー |
| issued_at | TIMESTAMP | NOT NULL | 発行日時 |
| expires_at | TIMESTAMP | NOT NULL | 有効期限（発行の約1年後） |
| revoked_at | TIMESTAMP | — | 失効日時。NULL なら有効 |

インデックス: `auth_tokens_pkey(id)`, `auth_tokens_token_hash_key(token_hash)`, `idx_auth_tokens_player_id(player_id)`, `idx_auth_tokens_expires_at(expires_at)`
