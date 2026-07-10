# 本番DB introspect 結果（2026-07-10・AC-R5 照合用）

取得方法: `C:/tmp/dbtool` の Q.java（JDBC + preferIPv4Stack）で本番 Render PostgreSQL（karuta_tracker_0txw）から information_schema.columns / pg_indexes を取得。

## players（17カラム）

| # | column | type | nullable | default |
|---|---|---|---|---|
| 1 | id | bigint | NO | |
| 2 | created_at | timestamp | NO | |
| 3 | dan_rank | varchar(255) | YES | |
| 4 | deleted_at | timestamp | YES | |
| 5 | dominant_hand | varchar(255) | NO | |
| 6 | gender | varchar(255) | NO | |
| 7 | karuta_club | varchar(200) | YES | |
| 8 | kyu_rank | varchar(255) | YES | |
| 9 | name | varchar(100) | NO | |
| 10 | password | varchar(255) | NO | |
| 11 | remarks | text | YES | |
| 12 | role | varchar(255) | NO | |
| 13 | updated_at | timestamp | NO | |
| 14 | last_login_at | timestamp | YES | |
| 15 | require_password_change | boolean | NO | false |
| 16 | admin_organization_id | bigint | YES | |
| 17 | ical_feed_token | varchar(64) | NO | |

インデックス: players_pkey(id) / uk...(name UNIQUE) / idx_name_active(name, deleted_at) / idx_deleted_at(deleted_at) / **idx_players_ical_feed_token(ical_feed_token UNIQUE)**

## matches（17カラム）

| # | column | type | nullable | default |
|---|---|---|---|---|
| 1 | id | bigint | NO | |
| 2 | created_at | timestamp | NO | |
| 3 | created_by | bigint | NO | |
| 4 | match_date | date | NO | |
| 5 | match_number | integer | NO | |
| 6 | opponent_name | varchar(100) | YES | |
| 7 | player1_id | bigint | NO | |
| 8 | player2_id | bigint | NO | |
| 9 | score_difference | integer | YES | |
| 10 | updated_at | timestamp | NO | |
| 11 | updated_by | bigint | NO | |
| 12 | winner_id | bigint | NO | |
| 13 | player1_kyu_rank | varchar(10) | YES | |
| 14 | player2_kyu_rank | varchar(10) | YES | |
| 15 | notes | text | YES | |
| 16 | venue_id | bigint | YES | |
| 17 | is_lesson | boolean | NO | false |

インデックス: matches_pkey(id) / idx_matches_date(match_date) / idx_matches_date_match_number(match_date, match_number) / idx_matches_date_player1 / idx_matches_date_player2 / idx_matches_winner / **idx_matches_venue(venue_id)** / **uq_matches_date_number_players(match_date, match_number, player1_id, player2_id) UNIQUE**

## 旧ドキュメントとの主な乖離（db.md で解消すべき点）

1. 旧 SPECIFICATION §6.2 players: `admin_organization_id` と `ical_feed_token` が欠落
2. 旧 SPECIFICATION §6.2 matches: `player1_kyu_rank` / `player2_kyu_rank` / `venue_id` が欠落、`uq_matches_date_number_players` / `idx_matches_venue` 未記載
3. 旧 DESIGN §3.2: `ical_feed_token` 欠落の可能性、ENUM / DATETIME / AUTO_INCREMENT は MySQL 残滓（本番は varchar / timestamp / bigint）
4. CHECK 制約は本番に存在しない（既知: 範囲検証はアプリ層のみ）
