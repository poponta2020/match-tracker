# かるたトラッカー システム設計書

最終更新日: 2026-03-25

## 目次
1. [システム概要](#1-システム概要)
2. [アーキテクチャ](#2-アーキテクチャ)
3. [データベース設計](#3-データベース設計)
4. [API設計](#4-api設計)
5. [画面設計](#5-画面設計)
   - 5.1 [画面一覧](#51-画面一覧)
   - 5.2 [画面遷移と導線](#52-画面遷移と導線)
   - 5.3 [主要画面設計](#53-主要画面設計)
6. [権限設計](#6-権限設計)
7. [主要機能フロー](#7-主要機能フロー)
8. [未実装機能・TODO](#8-未実装機能todo)
9. [補足事項](#9-補足事項)

---

## 1. システム概要

### 1.1 目的
競技かるたの練習・試合記録を管理し、対戦組み合わせの自動生成や統計分析を支援するWebアプリケーション。

### 1.2 主要機能
- 選手管理（登録・更新・削除・ロール管理）
- 試合記録（登録・更新・削除・統計・一括入力）
- 練習日管理（SUPER_ADMIN専用）
- 練習参加登録（試合ごと・月単位一括登録）
- 練習参加状況可視化（カレンダーバッジ・試合別ハイライト）
- 対戦組み合わせ自動生成（過去対戦履歴考慮）
- 会場管理（会場マスタ・試合時間割）
- 抽選機能（定員超過時の自動抽選・キャンセル待ち・繰り上げ）
- アプリ内通知（抽選結果・繰り上げ連絡）
- Web Push通知（VAPID認証）
- LINE通知（LINE Messaging API連携・チャネル管理・ワンタイムコード紐付け）
- Google Calendar連携（練習予定の同期）
- 伝助連携（出欠情報スクレイピング）
- メンター機能（メンター指名・承認・コメントフィードバック）
- 選手統計情報

### 1.3 技術スタック
**バックエンド**:
- Java 21
- Spring Boot 3.4.1
- Spring Data JPA
- Hibernate（PostgreSQLDialect）
- PostgreSQL 16（Render.com ホスティング）
- Gradle
- Jsoup 1.17.2（伝助HTMLスクレイピング）
- Google Calendar API v3（カレンダー同期）
- Web Push（nl.martijndwars:web-push:5.1.1 + Bouncy Castle）
- Testcontainers（テスト用PostgreSQL）

**フロントエンド**:
- React 19
- React Router v7
- Axios
- Tailwind CSS
- Vite
- Recharts（統計グラフ）
- Lucide React（アイコン）

---

## 2. アーキテクチャ

### 2.1 システム構成
```
[Browser] ←→ [React SPA (Port 5173)]
              ↓ HTTP
              [Spring Boot API (Port 8080)]
              ↓ JDBC
              [PostgreSQL 16 (Render.com)]
```

### 2.2 レイヤー構成
```
Controller Layer (REST API)
    ↓
Service Layer (ビジネスロジック)
    ↓
Repository Layer (データアクセス)
    ↓
Entity Layer (JPA Entity)
```

### 2.3 認証・認可
**現在の実装**:
- 簡易的なヘッダーベース認証（`X-User-Role`, `X-User-Id`）
- パスワード平文比較
- `@RequireRole` アノテーション + `RoleCheckInterceptor`（ロール検証 + ユーザーID伝播）
- `AdminScopeValidator`（`util/AdminScopeValidator.java`）— ADMINの団体スコープ検証ユーティリティ。ADMINが自団体以外のリソースを操作しようとした場合に `ForbiddenException` をスロー。各Controllerから共通利用
- フロントエンド `RoleRoute`（`components/RoleRoute.jsx`）— ルートレベルのロール保護コンポーネント。`PrivateRoute`（ログインチェック）の内側で使用し、権限不足時はホームにリダイレクト
- `PrivateRoute` は未認証時に `/login` へリダイレクトする際、遷移元の `location`（パス＋クエリパラメータ）を `state.from` に保持する。`Login` はログイン成功後に `state.from` があれば元URLへ復帰する（LINEリッチメニュー等の外部導線で未ログイン時に正しく復帰するため）

**TODO**:
- Spring Security + JWT導入
- BCryptパスワードハッシュ化
- セッション管理

---

## 3. データベース設計

### 3.1 ER図
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
[google_calendar_events] 多───1 [players]
                         多───1 [practice_sessions]
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

### 3.2 テーブル定義

#### players（選手マスタ）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 選手ID |
| name | VARCHAR(100) | NOT NULL, UNIQUE | 選手名（ログインID） |
| password | VARCHAR(255) | NOT NULL | パスワード |
| gender | ENUM | | 性別（男性/女性/その他） |
| dominant_hand | ENUM | | 利き手（右/左/両） |
| dan_rank | ENUM | | 段位（無段～八段） |
| kyu_rank | ENUM | | 級位（E級～A級） |
| karuta_club | VARCHAR(200) | | 所属かるた会 |
| remarks | TEXT | | 備考 |
| role | ENUM | NOT NULL, DEFAULT 'PLAYER' | ロール（SUPER_ADMIN/ADMIN/PLAYER） |
| require_password_change | BOOLEAN | NOT NULL, DEFAULT FALSE | パスワード変更要求フラグ |
| admin_organization_id | BIGINT | FK → organizations.id | ADMINの所属団体ID（PLAYER/SUPER_ADMINはNULL） |
| last_login_at | DATETIME | | 最終ログイン日時 |
| deleted_at | DATETIME | | 論理削除フラグ |
| created_at | DATETIME | NOT NULL | 作成日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

**インデックス**:
- `idx_name_active` (name, deleted_at)
- `idx_deleted_at` (deleted_at)

---

#### organizations（団体マスタ）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 団体ID |
| code | VARCHAR(50) | NOT NULL, UNIQUE | 団体コード（wasura, hokudai） |
| name | VARCHAR(200) | NOT NULL | 団体名 |
| color | VARCHAR(10) | NOT NULL | テーマカラー（例: #22c55e） |
| deadline_type | VARCHAR(20) | NOT NULL | 締め切りタイプ（SAME_DAY / MONTHLY） |
| created_at | TIMESTAMP | NOT NULL | 作成日時 |
| updated_at | TIMESTAMP | NOT NULL | 更新日時 |

---

#### player_organizations（ユーザー×団体紐づけ）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | ID |
| player_id | BIGINT | NOT NULL | 選手ID |
| organization_id | BIGINT | NOT NULL | 団体ID |
| created_at | TIMESTAMP | NOT NULL | 作成日時 |

**ユニーク制約**: (player_id, organization_id)

**自動所属**: 練習参加登録時・伝助経由の新規選手登録時に、該当団体へ未所属であれば `OrganizationService.ensurePlayerBelongsToOrganization()` により自動追加（通知設定のデフォルトレコードも同時作成）。退会は手動操作のみ。

---

#### matches（対戦結果）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 試合ID |
| match_date | DATE | NOT NULL | 試合日 |
| match_number | INT | NOT NULL | 試合番号（その日の何試合目か） |
| player1_id | BIGINT | NOT NULL, FK | 選手1ID（player1_id < player2_id を保証） |
| player2_id | BIGINT | NOT NULL, FK | 選手2ID |
| winner_id | BIGINT | NOT NULL, FK | 勝者ID |
| score_difference | INT | NOT NULL | 枚数差（1～50） |
| opponent_name | VARCHAR(100) | | 未登録選手名（簡易登録用） |
| created_by | BIGINT | NOT NULL | 登録者ID |
| updated_by | BIGINT | NOT NULL | 更新者ID |
| created_at | DATETIME | NOT NULL | 登録日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

**インデックス**:
- `idx_matches_date` (match_date)
- `idx_matches_date_player1` (match_date, player1_id)
- `idx_matches_date_player2` (match_date, player2_id)
- `idx_matches_winner` (winner_id)
- `idx_matches_date_match_number` (match_date, match_number)

**特殊ロジック**:
- `@PrePersist`/`@PreUpdate`で player1_id < player2_id を自動保証

---

#### match_personal_notes（個人メモ・お手付き記録）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | レコードID |
| match_id | BIGINT | NOT NULL, FK(matches.id, CASCADE) | 対象試合ID |
| player_id | BIGINT | NOT NULL, FK(players.id, RESTRICT) | 記録者ID |
| notes | TEXT | | 個人メモ |
| otetsuki_count | INT | CHECK(0〜20) | お手付き回数（nullは未入力） |
| created_at | DATETIME | NOT NULL | 登録日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

**制約**:
- UNIQUE: `uq_match_personal_notes` (match_id, player_id)
- CHECK: `otetsuki_count >= 0 AND otetsuki_count <= 20`

**インデックス**:
- `idx_match_personal_notes_player` (player_id, match_id)

---

#### practice_sessions（練習日情報）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 練習日ID |
| session_date | DATE | NOT NULL | 練習日 |
| total_matches | INT | NOT NULL | 予定試合数 |
| venue_id | BIGINT | FK | 会場ID（venuesテーブル参照） |
| notes | TEXT | | メモ・備考 |
| start_time | TIME | | 開始時刻 |
| end_time | TIME | | 終了時刻 |
| capacity | INT | | 定員（抽選判定に使用） |
| reservation_confirmed_at | DATETIME | | 隣室予約確認日時（NULLは未確認） |
| organization_id | BIGINT | NOT NULL, FK | 団体ID（organizations.id） |
| created_by | BIGINT | NOT NULL | 登録者ID |
| updated_by | BIGINT | NOT NULL | 更新者ID |
| created_at | DATETIME | NOT NULL | 登録日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

**制約**:
- UNIQUE (session_date, organization_id)

**インデックス**:
- `idx_session_date` (session_date)

---

#### practice_participants（練習参加者）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 参加記録ID |
| session_id | BIGINT | NOT NULL, FK | 練習セッションID |
| player_id | BIGINT | NOT NULL, FK | 参加選手ID |
| match_number | INT | | 参加試合番号（1～7、NULLは全試合） |
| status | ENUM | NOT NULL, DEFAULT 'WON' | 参加ステータス（PENDING/WON/WAITLISTED/OFFERED/DECLINED/CANCELLED） |
| waitlist_number | INT | | キャンセル待ち番号（WAITLISTED時のみ） |
| lottery_id | BIGINT | | 紐づく抽選実行ID |
| cancel_reason | VARCHAR(50) | | キャンセル理由コード（HEALTH/WORK_SCHOOL/FAMILY/TRANSPORT/OTHER） |
| cancel_reason_detail | TEXT | | キャンセル理由詳細（OTHER時の自由記述） |
| cancelled_at | DATETIME | | キャンセル日時 |
| offered_at | DATETIME | | 繰り上げ通知日時 |
| offer_deadline | DATETIME | | 繰り上げ応答期限 |
| responded_at | DATETIME | | 繰り上げ応答日時 |
| dirty | BOOLEAN | NOT NULL, DEFAULT TRUE | アプリ側操作済みフラグ。true=伝助への書き戻し対象、false=伝助から取り込み済み |
| created_at | DATETIME | NOT NULL | 登録日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

**制約**:
- UNIQUE (session_id, player_id, match_number)

**インデックス**:
- `idx_participant_session` (session_id)
- `idx_participant_player` (player_id)

**ParticipantStatus列挙型**:
- `PENDING` - 参加希望（抽選前）
- `WON` - 当選（参加確定）
- `WAITLISTED` - キャンセル待ち
- `OFFERED` - 繰り上げ通知済み（応答待ち）
- `DECLINED` - 繰り上げ辞退（明示的辞退または応答期限切れ）
- `CANCELLED` - 当選後キャンセル
- `WAITLIST_DECLINED` - キャンセル待ち辞退

---

#### system_settings（システム設定）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 設定ID |
| setting_key | VARCHAR(100) | NOT NULL, UNIQUE | 設定キー |
| setting_value | VARCHAR(255) | NOT NULL | 設定値 |
| updated_at | DATETIME | NOT NULL | 更新日時 |
| updated_by | BIGINT | | 更新者ID |

**初期データ**:
- `lottery_deadline_days_before` = `0`（締切日数：月初から何日前。0=前月末日の0時）

---

#### match_pairings（対戦組み合わせ）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 組み合わせID |
| session_date | DATE | NOT NULL | 練習日 |
| match_number | INT | NOT NULL | 試合番号 |
| player1_id | BIGINT | NOT NULL, FK | 選手1ID |
| player2_id | BIGINT | NOT NULL, FK | 選手2ID |
| created_by | BIGINT | NOT NULL | 登録者ID |
| created_at | DATETIME | NOT NULL | 登録日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

---

#### bye_activities（抜け番活動記録）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | ID |
| session_date | DATE | NOT NULL | 練習日 |
| match_number | INT | NOT NULL | 試合番号 |
| player_id | BIGINT | NOT NULL, FK | 抜け番の選手ID |
| activity_type | VARCHAR(20) | NOT NULL | 活動種別（READING/SOLO_PICK/OBSERVING/ASSIST_OBSERVING/OTHER/ABSENT） |
| free_text | VARCHAR(255) | | 「その他」選択時の自由記述 |
| created_by | BIGINT | NOT NULL | 登録者ID |
| updated_by | BIGINT | NOT NULL | 更新者ID |
| created_at | TIMESTAMP | NOT NULL | 登録日時 |
| updated_at | TIMESTAMP | NOT NULL | 更新日時 |
| deleted_at | TIMESTAMP | NULL | 論理削除日時 |

**ユニーク制約**: `uk_bye_activities_unique (session_date, match_number, player_id) WHERE deleted_at IS NULL`（部分ユニークインデックス）

**インデックス**:
- `idx_bye_activities_date` (session_date)
- `idx_bye_activities_date_match` (session_date, match_number)
- `idx_bye_activities_player` (player_id)
- `idx_bye_activities_deleted_at` (deleted_at)

---

#### player_profiles（選手情報履歴）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | プロフィールID |
| player_id | BIGINT | NOT NULL, FK | 選手ID |
| karuta_club | VARCHAR(200) | NOT NULL | 所属かるた会 |
| grade | ENUM | NOT NULL | 級位（A～E） |
| dan | ENUM | NOT NULL | 段位（無～八） |
| valid_from | DATE | NOT NULL | 有効開始日 |
| valid_to | DATE | | 有効終了日（NULL=現在有効） |
| created_at | DATETIME | NOT NULL | 登録日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

**インデックス**:
- `idx_player_date` (player_id, valid_from, valid_to)
- `idx_valid_to` (valid_to)

---

#### venues（練習会場マスタ）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 会場ID |
| name | VARCHAR(200) | NOT NULL, UNIQUE | 会場名 |
| default_match_count | INT | NOT NULL | デフォルト試合数 |
| created_at | DATETIME | NOT NULL | 作成日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

---

#### venue_match_schedules（会場試合時間割）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 時間割ID |
| venue_id | BIGINT | NOT NULL, FK | 会場ID |
| match_number | INT | NOT NULL | 試合番号 |
| start_time | TIME | NOT NULL | 開始時刻 |
| end_time | TIME | NOT NULL | 終了時刻 |

**制約**:
- UNIQUE (venue_id, match_number)

---

#### lottery_executions（抽選実行履歴）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 抽選ID |
| target_year | INT | NOT NULL | 対象年 |
| target_month | INT | NOT NULL | 対象月 |
| execution_type | ENUM | NOT NULL | 実行種別（AUTO/MANUAL/MANUAL_RELOTTERY） |
| session_id | BIGINT | | 対象セッションID（再抽選時のみ） |
| executed_by | BIGINT | | 実行者ID（自動の場合はNULL） |
| executed_at | DATETIME | NOT NULL | 実行日時 |
| status | ENUM | NOT NULL | 実行結果（SUCCESS/FAILED/PARTIAL） |
| details | TEXT | | 処理詳細（JSON形式） |
| confirmed_at | TIMESTAMP | | 確定日時（NULL = 未確定） |
| confirmed_by | BIGINT | | 確定者のプレイヤーID（NULL = 未確定） |
| organization_id | BIGINT | | 団体ID |

**インデックス**:
- `idx_lottery_target` (target_year, target_month)
- `idx_lottery_org` (target_year, target_month, organization_id)

---

#### notifications（アプリ内通知）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 通知ID |
| player_id | BIGINT | NOT NULL, FK | 通知先プレイヤーID |
| type | ENUM | NOT NULL | 通知種別 |
| title | VARCHAR(200) | NOT NULL | 通知タイトル |
| message | TEXT | | 通知本文 |
| reference_type | VARCHAR(50) | | 参照先エンティティ種別 |
| reference_id | BIGINT | | 参照先ID |
| is_read | BOOLEAN | NOT NULL, DEFAULT false | 既読フラグ |
| created_at | DATETIME | NOT NULL | 作成日時 |
| deleted_at | DATETIME | | 論理削除日時 |

**NotificationType列挙型**:
- `LOTTERY_WON` - 抽選結果（当選）※廃止：既存データ参照用に残す
- `LOTTERY_ALL_WON` - 抽選結果（全試合当選まとめ）
- `LOTTERY_REMAINING_WON` - 抽選結果（落選以外は全当選まとめ）
- `LOTTERY_WAITLISTED` - 抽選結果（落選・キャンセル待ち）※セッション単位にまとめ
- `WAITLIST_OFFER` - キャンセル待ちからの繰り上げ連絡
- `OFFER_EXPIRING` - 繰り上げ応答期限切れ警告
- `OFFER_EXPIRED` - 繰り上げ応答期限切れ
- `CHANNEL_RECLAIM_WARNING` - LINEチャネル回収警告
- `DENSUKE_UNMATCHED_NAMES` - 伝助同期：未登録者あり（管理者向け）

**インデックス**:
- `idx_notification_player` (player_id)
- `idx_notification_read` (player_id, is_read)

---

#### google_calendar_events（Googleカレンダーイベントマッピング）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | ID |
| player_id | BIGINT | NOT NULL, FK | プレイヤーID |
| session_id | BIGINT | NOT NULL, FK | 練習セッションID |
| google_event_id | VARCHAR(1024) | NOT NULL | GoogleカレンダーイベントID |
| synced_session_updated_at | DATETIME | | 同期時点でのセッション更新日時 |
| created_at | DATETIME | NOT NULL | 作成日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

**制約**:
- UNIQUE (player_id, session_id)

**インデックス**:
- `idx_gcal_player_id` (player_id)
- `idx_gcal_session_id` (session_id)

---

#### push_subscriptions（Web Pushサブスクリプション）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | ID |
| player_id | BIGINT | NOT NULL, FK | プレイヤーID |
| endpoint | TEXT | NOT NULL | Push APIエンドポイント |
| p256dh_key | VARCHAR(500) | NOT NULL | P-256 DH公開鍵 |
| auth_key | VARCHAR(500) | NOT NULL | 認証キー |
| user_agent | VARCHAR(500) | | ブラウザ情報 |
| created_at | DATETIME | NOT NULL | 作成日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

**インデックス**:
- `idx_push_player` (player_id)

---

#### push_notification_preferences（Web Push通知設定）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | ID |
| player_id | BIGINT | NOT NULL, FK, UNIQUE | プレイヤーID |
| enabled | BOOLEAN | NOT NULL, DEFAULT FALSE | Web Push全体のON/OFF |
| lottery_result | BOOLEAN | NOT NULL, DEFAULT TRUE | 抽選結果 |
| waitlist_offer | BOOLEAN | NOT NULL, DEFAULT TRUE | 繰り上げ連絡 |
| offer_expiring | BOOLEAN | NOT NULL, DEFAULT TRUE | 期限切れ警告 |
| offer_expired | BOOLEAN | NOT NULL, DEFAULT TRUE | 期限切れ |
| channel_reclaim_warning | BOOLEAN | NOT NULL, DEFAULT TRUE | LINE回収警告 |
| densuke_unmatched | BOOLEAN | NOT NULL, DEFAULT TRUE | 伝助未登録者 |
| created_at | DATETIME | NOT NULL | 作成日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

**インデックス**:
- `idx_pnp_player` (player_id)

---

#### densuke_urls（伝助URL管理）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | ID |
| year | INT | NOT NULL | 対象年 |
| month | INT | NOT NULL | 対象月 |
| url | VARCHAR(500) | NOT NULL | 伝助URL |
| organization_id | BIGINT | NOT NULL, FK | 団体ID（organizations.id） |
| created_at | DATETIME | NOT NULL | 作成日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

**制約**:
- UNIQUE (year, month, organization_id)

---

#### densuke_member_mappings（伝助メンバーIDキャッシュ）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | ID |
| densuke_url_id | BIGINT | NOT NULL, FK | 伝助URL ID（densuke_urls.id） |
| player_id | BIGINT | NOT NULL, FK | 選手ID（players.id） |
| densuke_member_id | VARCHAR(50) | NOT NULL | 伝助メンバーID（`mi` パラメータ値） |
| created_at | DATETIME | NOT NULL | 作成日時 |

**制約**:
- UNIQUE (densuke_url_id, player_id)

---

#### densuke_row_ids（伝助行IDキャッシュ）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | ID |
| densuke_url_id | BIGINT | NOT NULL, FK | 伝助URL ID（densuke_urls.id） |
| densuke_row_id | VARCHAR(50) | NOT NULL | `join-{id}` フィールドのID値 |
| session_date | DATE | NOT NULL | 対象日付 |
| match_number | INT | NOT NULL | 対象試合番号 |
| created_at | DATETIME | NOT NULL | 作成日時 |

**制約**:
- UNIQUE (densuke_url_id, session_date, match_number)

---

#### line_channels（LINEチャネル情報）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | ID |
| channel_name | VARCHAR(100) | | 管理用表示名 |
| line_channel_id | VARCHAR(50) | NOT NULL, UNIQUE | LINE発行のチャネルID |
| channel_secret | VARCHAR(255) | NOT NULL | チャネルシークレット（暗号化保存） |
| channel_access_token | TEXT | NOT NULL | アクセストークン（暗号化保存） |
| channel_type | VARCHAR(10) | NOT NULL, DEFAULT 'PLAYER' | チャネル用途（PLAYER: 選手用 / ADMIN: 管理者用） |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'AVAILABLE' | AVAILABLE / ASSIGNED / LINKED / DISABLED |
| friend_add_url | TEXT | | 友だち追加URL |
| monthly_message_count | INT | NOT NULL, DEFAULT 0 | 当月送信数 |
| message_count_reset_at | DATETIME | | 送信数リセット日時 |
| created_at | DATETIME | NOT NULL | 作成日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

**インデックス**:
- `idx_line_channel_status` (status)
- `idx_line_channel_line_id` (line_channel_id)
- `idx_line_channel_type` (channel_type)

---

#### line_channel_assignments（LINEチャネル割り当て）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | ID |
| line_channel_id | BIGINT | NOT NULL, FK → line_channels.id | LINEチャネルID |
| player_id | BIGINT | NOT NULL, FK → players.id | プレイヤーID |
| line_user_id | VARCHAR(50) | | follow時に取得するLINE userId |
| channel_type | VARCHAR(10) | NOT NULL, DEFAULT 'PLAYER' | チャネル用途（PLAYER / ADMIN、非正規化） |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'PENDING' | PENDING / LINKED / UNLINKED / RECLAIMED |
| assigned_at | DATETIME | NOT NULL | 割り当て日時 |
| linked_at | DATETIME | | LINKED化日時 |
| unlinked_at | DATETIME | | 解除日時 |
| reclaim_warned_at | DATETIME | | 回収警告通知日時 |
| created_at | DATETIME | NOT NULL | 作成日時 |

**インデックス**:
- `idx_lca_channel` (line_channel_id)
- `idx_lca_player` (player_id)
- `idx_lca_status` (status)
- `idx_lca_player_type` (player_id, channel_type)

---

#### line_linking_codes（ワンタイムコード）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | ID |
| player_id | BIGINT | NOT NULL, FK → players.id | プレイヤーID |
| line_channel_id | BIGINT | NOT NULL, FK → line_channels.id | LINEチャネルID |
| code | VARCHAR(8) | NOT NULL, UNIQUE | 英数字8桁のワンタイムコード |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'ACTIVE' | ACTIVE / USED / EXPIRED / INVALIDATED |
| attempt_count | INT | NOT NULL, DEFAULT 0 | 検証失敗回数（5回で無効化） |
| expires_at | DATETIME | NOT NULL | 有効期限（発行から10分後） |
| used_at | DATETIME | | 使用日時 |
| created_at | DATETIME | NOT NULL | 発行日時 |

**インデックス**:
- `idx_llc_player` (player_id)
- `idx_llc_code` (code)
- `idx_llc_channel` (line_channel_id)

---

#### line_notification_preferences（LINE通知設定）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | ID |
| player_id | BIGINT | NOT NULL, UNIQUE, FK → players.id | プレイヤーID |
| lottery_result | BOOLEAN | NOT NULL, DEFAULT TRUE | 抽選結果 |
| waitlist_offer | BOOLEAN | NOT NULL, DEFAULT TRUE | キャンセル待ち連絡 |
| offer_expired | BOOLEAN | NOT NULL, DEFAULT TRUE | オファー期限切れ |
| match_pairing | BOOLEAN | NOT NULL, DEFAULT TRUE | 対戦組み合わせ |
| practice_reminder | BOOLEAN | NOT NULL, DEFAULT TRUE | 参加予定リマインダー |
| deadline_reminder | BOOLEAN | NOT NULL, DEFAULT TRUE | 締め切りリマインダー |
| same_day_confirmation | BOOLEAN | NOT NULL, DEFAULT TRUE | 当日参加者確定通知（WON参加者向け） |
| same_day_cancel | BOOLEAN | NOT NULL, DEFAULT TRUE | 当日キャンセル通知 |
| same_day_vacancy | BOOLEAN | NOT NULL, DEFAULT TRUE | 当日空き募集通知 |
| admin_same_day_confirmation | BOOLEAN | NOT NULL, DEFAULT TRUE | 参加者確定通知（管理者向け・SUPER_ADMIN専用） |
| mentor_comment | BOOLEAN | NOT NULL, DEFAULT TRUE | メンターコメント・メモ更新通知 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

---

#### line_notification_schedule_settings（スケジュール型通知設定）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | ID |
| notification_type | VARCHAR(30) | NOT NULL, UNIQUE | PRACTICE_REMINDER / DEADLINE_REMINDER |
| enabled | BOOLEAN | NOT NULL, DEFAULT TRUE | 有効/無効 |
| days_before | VARCHAR(50) | NOT NULL | 送信日数（JSON配列。例: "[3, 1]"） |
| updated_at | DATETIME | NOT NULL | 更新日時 |
| updated_by | BIGINT | | 最終更新者のplayer_id |

---

#### line_message_log（LINE送信ログ）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | ID |
| line_channel_id | BIGINT | NOT NULL, FK → line_channels.id | LINEチャネルID |
| player_id | BIGINT | NOT NULL, FK → players.id | プレイヤーID |
| notification_type | VARCHAR(30) | NOT NULL | 通知種別 |
| message_content | TEXT | NOT NULL | 送信メッセージ内容 |
| status | VARCHAR(20) | NOT NULL | SUCCESS / FAILED / SKIPPED / RESERVED |
| error_message | TEXT | | 失敗時のエラー内容 |
| dedupe_key | VARCHAR(100) | | 重複排除キー（セッションID等） |
| sent_at | DATETIME | NOT NULL | 送信日時 |

**インデックス**:
- `idx_lml_channel` (line_channel_id)
- `idx_lml_player` (player_id)
- `idx_lml_type_sent` (notification_type, sent_at)
- `idx_lml_dedupe` (player_id, notification_type, dedupe_key, sent_at)
- `idx_lml_dedupe_daily_unique` (player_id, notification_type, dedupe_key, sent_at::date) WHERE status IN ('SUCCESS', 'RESERVED') — 部分ユニーク制約

**重複送信防止フロー（原子的送信権確保方式）:**
1. `tryAcquireSendRight`: dedupeKey付きで `RESERVED` ステータスのログをINSERT（ON CONFLICT DO NOTHING）
2. LINE APIで送信実行
3. 成功時: `markReservationSucceeded` で RESERVED → SUCCESS に更新
4. 失敗時: `markReservationFailed` で RESERVED → FAILED に更新（次回リトライ可能）
5. クラッシュ時: RESERVED が残留するため、次回送信前に `releaseStaleReservations` で10分超過のRESERVEDをFAILEDに解放し、再送信を可能にする

**dedupeKeyの粒度:**
- 試合単位通知（sendSameDayVacancyNotification）: `sessionId:matchNumber`
- セッション統合通知（sendConsolidatedSameDayVacancyNotification）: `sessionId`

---

#### mentor_relationships（メンター関係）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | ID |
| mentor_id | BIGINT | NOT NULL, FK → players.id | メンターの選手ID |
| mentee_id | BIGINT | NOT NULL, FK → players.id | メンティーの選手ID |
| organization_id | BIGINT | NOT NULL, FK → organizations.id | 団体ID |
| status | VARCHAR(20) | NOT NULL, CHECK | PENDING / ACTIVE / REJECTED |
| created_at | DATETIME | NOT NULL | 作成日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

**制約**:
- UNIQUE: `(mentor_id, mentee_id, organization_id)`

---

#### match_comments（メンターコメント）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | ID |
| match_id | BIGINT | NOT NULL, FK → matches.id | 試合ID |
| mentee_id | BIGINT | NOT NULL, FK → players.id | コメント対象のメンティーID |
| author_id | BIGINT | NOT NULL, FK → players.id | 投稿者ID |
| content | TEXT | NOT NULL | コメント内容 |
| line_notified | BOOLEAN | NOT NULL, DEFAULT FALSE | LINE通知送信済みフラグ |
| created_at | DATETIME | NOT NULL | 作成日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |
| deleted_at | DATETIME | | 論理削除日時 |

**インデックス**:
- `idx_match_comments_thread` (match_id, mentee_id, deleted_at, created_at)

---

#### invite_tokens（招待トークン）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | ID |
| token | VARCHAR(36) | NOT NULL, UNIQUE | トークン文字列（UUID） |
| type | ENUM | NOT NULL | MULTI_USE（グループ用）/ SINGLE_USE（個人用） |
| expires_at | DATETIME | NOT NULL | 有効期限 |
| used_at | DATETIME | | 使用日時（SINGLE_USEのみ） |
| used_by | BIGINT | | 使用した選手ID（SINGLE_USEのみ） |
| created_by | BIGINT | NOT NULL | 発行者の選手ID |
| created_at | DATETIME | NOT NULL | 作成日時 |

**インデックス**:
- `idx_invite_tokens_token` (token) UNIQUE

---

## 4. API設計

### 4.1 共通仕様

**ベースURL**: `http://localhost:8080/api`

**タイムゾーン**: 全ての日時処理は `JstDateTimeUtil`（`util/JstDateTimeUtil.java`）を使用してJST（Asia/Tokyo）で統一。`LocalDate.now()` / `LocalDateTime.now()` は直接使用せず、`JstDateTimeUtil.today()` / `JstDateTimeUtil.now()` を使用する。

**リクエストヘッダー**:
- `Content-Type: application/json`
- `X-User-Role: {SUPER_ADMIN|ADMIN|PLAYER}` (現在の簡易実装)

**レスポンス形式**:
```json
// 成功時
{
  "id": 1,
  "name": "田中太郎",
  ...
}

// エラー時
{
  "message": "エラーメッセージ",
  "timestamp": "2025-11-17T12:00:00"
}
```

**HTTPステータスコード**:
- 200: OK
- 201: Created
- 204: No Content
- 400: Bad Request
- 401: Unauthorized
- 403: Forbidden
- 404: Not Found
- 409: Conflict
- 500: Internal Server Error

---

### 4.1.1 団体API (`/api/organizations`)

#### GET /api/organizations
- **権限**: なし
- **説明**: 団体一覧を取得
- **レスポンス**: `[{ id, code, name, color, deadlineType }]`

#### GET /api/organizations/players/{playerId}
- **権限**: 本人またはSUPER_ADMIN（`currentUserId` と `playerId` の一致チェック）
- **説明**: ユーザーの参加団体一覧を取得
- **レスポンス**: `[{ id, code, name, color, deadlineType }]`

#### PUT /api/organizations/players/{playerId}
- **権限**: 本人またはSUPER_ADMIN（`currentUserId` と `playerId` の一致チェック）
- **説明**: ユーザーの参加団体を更新（最低1つ必須）
- **リクエスト**: `{ organizationIds: [1, 2] }`
- **レスポンス**: 更新後の団体一覧

#### PUT /api/organizations/admin/{playerId}
- **権限**: SUPER_ADMIN
- **説明**: ADMINの団体紐づけを変更
- **リクエスト**: `{ organizationId: 1 }`

---

### 4.2 選手API

#### POST /api/players/login
**説明**: ログイン
**権限**: なし
**リクエスト**:
```json
{
  "name": "田中太郎",
  "password": "password123"
}
```
**レスポンス**: `LoginResponse`
```json
{
  "id": 1,
  "name": "田中太郎",
  "gender": "男性",
  "role": "PLAYER",
  "dominantHand": "右",
  "danRank": "三段",
  "kyuRank": "A級",
  "karutaClub": "東京かるた会",
  "adminOrganizationId": null,
  "organizationIds": [1, 3],
  "firstLogin": false,
  "requirePasswordChange": false
}
```

#### GET /api/players
**説明**: 全アクティブ選手取得
**権限**: なし
**レスポンス**: `List<PlayerDto>`
- 各選手に `organizationIds`（所属団体IDリスト）が含まれる

#### POST /api/players
**説明**: 選手登録
**権限**: SUPER_ADMIN
**リクエスト**: `PlayerCreateRequest`
```json
{
  "name": "田中太郎",
  "password": "password123",
  "gender": "男性",
  "dominantHand": "右",
  "danRank": "三段",
  "kyuRank": "A級",
  "karutaClub": "東京かるた会",
  "remarks": "備考"
}
```

#### PUT /api/players/{id}
**説明**: 選手情報更新
**権限**: なし
**リクエスト**: `PlayerUpdateRequest`（全フィールドオプショナル）

#### DELETE /api/players/{id}
**説明**: 選手削除（論理削除）
**権限**: SUPER_ADMIN

#### PUT /api/players/{id}/role
**説明**: ロール変更
**権限**: SUPER_ADMIN
**リクエスト**:
```json
{
  "role": "ADMIN"
}
```

---

### 4.2.1 招待トークンAPI (`/api/invite-tokens`)

#### POST /api/invite-tokens?type={type}&createdBy={id}
**説明**: 招待トークン生成
**権限**: ADMIN+
**パラメータ**: `type`（MULTI_USE / SINGLE_USE）, `createdBy`（発行者ID）
**レスポンス**: `InviteTokenResponse`
```json
{
  "token": "550e8400-e29b-41d4-a716-446655440000",
  "type": "MULTI_USE",
  "expiresAt": "2026-03-28T12:00:00",
  "createdAt": "2026-03-25T12:00:00"
}
```

#### GET /api/invite-tokens/validate/{token}
**説明**: トークン有効性検証
**権限**: なし（公開）
**レスポンス**: `InviteTokenResponse`（無効時は404またはエラー）

#### POST /api/invite-tokens/register
**説明**: 招待トークンを使った公開登録
**権限**: なし（公開）
**リクエスト**: `PublicRegisterRequest`
```json
{
  "token": "550e8400-e29b-41d4-a716-446655440000",
  "name": "田中太郎",
  "password": "password123",
  "gender": "男性",
  "dominantHand": "右"
}
```
**レスポンス**: `PlayerDto`

---

### 4.3 試合記録API

#### POST /api/matches
**説明**: 簡易試合登録（対戦相手名）
**権限**: なし
**リクエスト**: `MatchSimpleCreateRequest`
```json
{
  "matchDate": "2025-11-17",
  "matchNumber": 1,
  "playerId": 1,
  "opponentName": "佐藤花子",
  "result": "勝ち",
  "scoreDifference": 5,
  "personalNotes": "右下段が甘かった",
  "otetsukiCount": 3
}
```

#### POST /api/matches/detailed
**説明**: 詳細試合登録（両選手ID）
**権限**: なし
**リクエスト**: `MatchCreateRequest`
```json
{
  "matchDate": "2025-11-17",
  "matchNumber": 1,
  "player1Id": 1,
  "player2Id": 2,
  "winnerId": 1,
  "scoreDifference": 5,
  "personalNotes": "右下段が甘かった",
  "otetsukiCount": 3
}
```

#### GET /api/matches?date={date}
**説明**: 日付別試合一覧
**権限**: なし
**レスポンス**: `List<MatchDto>`（リクエストユーザーの個人メモ・お手付きを `myPersonalNotes` / `myOtetsukiCount` として含む）

#### GET /api/matches/player/{playerId}
**説明**: 選手の試合履歴
**権限**: なし

#### GET /api/matches/player/{playerId}/statistics
**説明**: 選手統計
**権限**: なし
**レスポンス**: `MatchStatisticsDto`
```json
{
  "playerId": 1,
  "playerName": "田中太郎",
  "totalMatches": 100,
  "wins": 65,
  "winRate": 0.65
}
```

---

### 4.4 抜け番活動API

#### GET /api/bye-activities?date={date}&matchNumber={matchNumber}
**説明**: 指定日の抜け番活動を取得（matchNumber指定時はその試合のみ）
**権限**: なし
**レスポンス**: `List<ByeActivityDto>`

#### POST /api/bye-activities
**説明**: 抜け番活動を作成（本人入力）
**権限**: なし
**リクエスト**: `ByeActivityCreateRequest`
```json
{
  "sessionDate": "2026-03-24",
  "matchNumber": 1,
  "playerId": 5,
  "activityType": "READING",
  "freeText": null
}
```

#### POST /api/bye-activities/batch?date={date}&matchNumber={matchNumber}
**説明**: 抜け番活動を一括作成（既存レコード削除後に再作成）
**権限**: ADMIN+
**リクエスト**: `List<ByeActivityBatchItemRequest>`
```json
[
  { "playerId": 5, "activityType": "READING", "freeText": null },
  { "playerId": 8, "activityType": "OTHER", "freeText": "審判練習" }
]
```

#### PUT /api/bye-activities/{id}
**説明**: 抜け番活動を更新
**権限**: なし
**リクエスト**: `ByeActivityUpdateRequest`
```json
{ "activityType": "SOLO_PICK", "freeText": null }
```

#### GET /api/bye-activities/player/{playerId}?type={activityType}
**説明**: 選手別の活動履歴（集計用）
**権限**: なし

#### DELETE /api/bye-activities/{id}
**説明**: 抜け番活動を削除
**権限**: ADMIN+

---

### 4.5 練習日API

#### POST /api/practice-sessions
**説明**: 練習日作成
**権限**: SUPER_ADMIN
**リクエスト**: `PracticeSessionCreateRequest`
```json
{
  "sessionDate": "2025-11-20",
  "totalMatches": 7,
  "venueId": 1,
  "notes": "備考",
  "startTime": "13:00",
  "endTime": "17:00",
  "capacity": 20,
  "participantIds": [1, 2, 3]
}
```

**特記事項**:
- `participantIds` で指定された参加者は全試合に自動参加
- 例: 3名の参加者、7試合 → 21レコード作成（3 × 7）
- ユーザーは後から参加登録画面で試合を調整可能

#### GET /api/practice-sessions
**説明**: 全練習日取得（降順）
**権限**: なし
**レスポンス**: `List<PracticeSessionDto>`
```json
[
  {
    "id": 10,
    "sessionDate": "2025-11-20",
    "totalMatches": 7,
    "venueId": 1,
    "participantCount": 15,
    "completedMatches": 3,
    "matchParticipantCounts": {
      "1": 10,
      "2": 12,
      "3": 8
    },
    "matchParticipants": {
      "1": ["田中太郎", "佐藤花子", ...],
      "2": [...],
      ...
    }
  }
]
```

#### GET /api/practice-sessions/year-month?year={year}&month={month}
**説明**: 年月別練習日取得
**権限**: なし

#### PUT /api/practice-sessions/{id}
**説明**: 練習日更新
**権限**: SUPER_ADMIN

**特記事項**:
- 更新時も `participantIds` で指定された参加者は全試合に自動参加
- 既存の参加記録は削除され、新しい参加記録が作成される

#### DELETE /api/practice-sessions/{id}
**説明**: 練習日削除
**権限**: SUPER_ADMIN

---

### 4.6 練習参加登録API

#### POST /api/practice-sessions/participations
**説明**: 一括参加登録（月単位）
**権限**: PLAYER: 自分のみ / ADMIN, SUPER_ADMIN: 全選手
**リクエスト**: `PracticeParticipationRequest`
```json
{
  "playerId": 1,
  "year": 2025,
  "month": 11,
  "participations": [
    {"sessionId": 10, "matchNumber": 1},
    {"sessionId": 10, "matchNumber": 2},
    {"sessionId": 11, "matchNumber": 1}
  ]
}
```

#### GET /api/practice-sessions/participations/player/{playerId}?year={year}&month={month}
**説明**: 月別参加状況取得
**権限**: なし
**レスポンス**:
```json
{
  "10": [1, 2],
  "11": [1]
}
```

---

### 4.7 対戦組み合わせAPI

#### POST /api/match-pairings/auto-match
**説明**: 自動マッチング
**権限**: SUPER_ADMIN, ADMIN
**リクエスト**: `AutoMatchingRequest`
```json
{
  "sessionDate": "2025-11-20",
  "matchNumber": 1
}
```
**レスポンス**: `AutoMatchingResult`
```json
{
  "pairings": [
    {
      "player1Id": 1,
      "player1Name": "田中太郎",
      "player2Id": 2,
      "player2Name": "佐藤花子",
      "score": -14.28,
      "recentMatches": [
        {"date": "2025-11-13", "daysAgo": 7}
      ]
    }
  ],
  "waitingPlayers": [
    {"id": 5, "name": "山田太郎"}
  ],
  "lockedPairings": [
    {
      "player1Id": 3,
      "player1Name": "鈴木一郎",
      "player2Id": 4,
      "player2Name": "高橋次郎",
      "score": 0.0,
      "recentMatches": []
    }
  ]
}
```

#### POST /api/match-pairings/batch?date={date}&matchNumber={matchNumber}
**説明**: 一括組み合わせ作成（結果入力済みペアリングはロック保持）
**権限**: SUPER_ADMIN, ADMIN
**リクエスト**: `MatchPairingBatchRequest`
```json
{
  "pairings": [
    {"player1Id": 1, "player2Id": 2},
    {"player1Id": 3, "player2Id": 4}
  ],
  "waitingPlayerIds": [5]
}
```

#### GET /api/match-pairings/date?date={date}
**説明**: 日付別組み合わせ取得
**権限**: なし

#### DELETE /api/match-pairings/{id}/with-result
**説明**: ペアリングと対応する試合結果を同時削除（リセット）
**権限**: SUPER_ADMIN, ADMIN
**レスポンス**: 削除されたペアリング情報（`MatchPairingDto`、`hasResult=true`、`matchId`付き）

#### DELETE /api/match-pairings/date-and-match?date={date}&matchNumber={matchNumber}
**説明**: 組み合わせ削除
**権限**: SUPER_ADMIN, ADMIN

---

### 4.8 会場API

#### GET /api/venues
**説明**: 全会場取得
**権限**: なし
**レスポンス**: `List<VenueDto>`

#### GET /api/venues/{id}
**説明**: 会場ID指定取得
**権限**: なし
**レスポンス**: `VenueDto`

#### POST /api/venues
**説明**: 会場新規作成
**権限**: なし
**リクエスト**: `VenueCreateRequest`
```json
{
  "name": "市民館",
  "defaultMatchCount": 7,
  "schedules": [
    {"matchNumber": 1, "startTime": "13:00", "endTime": "13:50"},
    {"matchNumber": 2, "startTime": "14:00", "endTime": "14:50"}
  ]
}
```

#### PUT /api/venues/{id}
**説明**: 会場更新
**権限**: なし
**リクエスト**: `VenueUpdateRequest`

#### DELETE /api/venues/{id}
**説明**: 会場削除
**権限**: なし

---

### 4.9 抽選API

#### GET /api/lottery/deadline?year={year}&month={month}
**説明**: 指定年月の締め切り日時を取得（一般ユーザーの締め切り表示用）
**権限**: 認証不要
**レスポンス**:
```json
{
  "deadline": "2026-03-29T00:00:00",
  "noDeadline": false
}
```
締め切りなしモード時: `{ "deadline": null, "noDeadline": true }`

#### POST /api/lottery/execute
**説明**: 手動抽選実行（月単位）。「締め切りなし」モード時は締め切り前チェックをスキップ。重複チェック・確定チェックは団体単位で行われる
**権限**: SUPER_ADMIN, ADMIN（ADMINは自団体のみ）
**リクエスト**: `LotteryExecutionRequest`
```json
{
  "year": 2026,
  "month": 3
}
```
**レスポンス**: `LotteryExecution`

#### POST /api/lottery/re-execute/{sessionId}
**説明**: セッション再抽選
**権限**: SUPER_ADMIN, ADMIN
**レスポンス**: `LotteryExecution`

#### GET /api/lottery/results?year={year}&month={month}
**説明**: 月別抽選結果取得
**権限**: なし
**レスポンス**: `List<LotteryResultDto>`

#### GET /api/lottery/results/{sessionId}
**説明**: セッション別抽選結果取得
**権限**: なし

#### GET /api/lottery/my-results?year={year}&month={month}
**説明**: 自分の抽選結果取得（ログインユーザーの結果のみ）
**権限**: SUPER_ADMIN, ADMIN, PLAYER

#### POST /api/lottery/cancel
**説明**: 参加キャンセル（理由付き・複数対応）。PLAYERは自分の参加のみキャンセル可能かつ過去日のキャンセル不可、ADMIN+は他人分・過去日も可。
**権限**: SUPER_ADMIN, ADMIN, PLAYER
**リクエスト**: `CancelRequest`
```json
{
  "participantId": 123,
  "participantIds": [123, 456],
  "cancelReason": "HEALTH",
  "cancelReasonDetail": "（OTHERの場合のみ）"
}
```

#### POST /api/lottery/respond-offer
**説明**: 繰り上げオファーへの応答。PLAYERは自分のオファーのみ応答可能、ADMIN+は他人分も可。応答期限超過時はエラー。
**権限**: SUPER_ADMIN, ADMIN, PLAYER
**リクエスト**: `OfferResponseRequest`
```json
{
  "participantId": 123,
  "accept": true
}
```

#### GET /api/lottery/offer-detail/{participantId}
**説明**: 個別オファー詳細取得。PLAYERは自分のレコードのみ参照可能、ADMIN+は全員参照可能。
**権限**: SUPER_ADMIN, ADMIN, PLAYER
**レスポンス**: `WaitlistStatusDto.WaitlistEntry`
```json
{
  "participantId": 123,
  "sessionId": 45,
  "sessionDate": "2026-04-05",
  "venueName": "市民館",
  "startTime": "13:00",
  "endTime": "17:00",
  "matchNumber": 2,
  "waitlistNumber": 1,
  "status": "OFFERED",
  "offerDeadline": "2026-04-04T23:59:59"
}
```

#### POST /api/lottery/respond-offer-all
**説明**: 繰り上げオファー一括応答。同一セッション内の自分の全OFFEREDを一括承諾/辞退する。
**権限**: SUPER_ADMIN, ADMIN, PLAYER
**リクエスト**: `OfferBatchResponseRequest`
```json
{
  "sessionId": 45,
  "accept": true
}
```
**レスポンス**:
```json
{
  "result": "accepted",
  "count": 3
}
```

#### GET /api/lottery/session-offers/{sessionId}
**説明**: セッション内の自分のOFFERED一覧取得。ログインユーザーのOFFEREDのみ返す。
**権限**: SUPER_ADMIN, ADMIN, PLAYER
**レスポンス**: `List<WaitlistStatusDto.WaitlistEntry>`
```json
[
  {
    "participantId": 123,
    "sessionId": 45,
    "sessionDate": "2026-04-05",
    "venueName": "市民館",
    "matchNumber": 1,
    "status": "OFFERED",
    "offerDeadline": "2026-04-04T23:59:59"
  },
  {
    "participantId": 124,
    "sessionId": 45,
    "sessionDate": "2026-04-05",
    "venueName": "市民館",
    "matchNumber": 3,
    "status": "OFFERED",
    "offerDeadline": "2026-04-04T23:59:59"
  }
]
```

#### GET /api/lottery/waitlist-status
**説明**: キャンセル待ち状況取得（ログインユーザーの状況のみ）
**権限**: SUPER_ADMIN, ADMIN, PLAYER
**レスポンス**: `WaitlistStatusDto`

#### GET /api/lottery/notify-status?year={year}&month={month}
**説明**: 抽選結果通知の送信済みチェック
**権限**: SUPER_ADMIN, ADMIN
**レスポンス**:
```json
{ "sent": true, "sentCount": 24 }
```

#### POST /api/lottery/notify-results
**説明**: 抽選結果通知の統合送信（アプリ内通知 + LINE通知を一括送信）
**権限**: SUPER_ADMIN, ADMIN
**リクエスト**:
```json
{ "year": 2026, "month": 4 }
```
**レスポンス**:
```json
{ "inAppCount": 24, "lineSent": 20, "lineFailed": 0, "lineSkipped": 4 }
```

#### POST /api/lottery/decline-waitlist
**説明**: キャンセル待ち辞退（セッション単位）。辞退後、後続のキャンセル待ち番号を自動繰り上げ。
**権限**: SUPER_ADMIN, ADMIN, PLAYER（PLAYERは自分のみ）
**リクエスト**:
```json
{ "sessionId": 100, "playerId": 10 }
```
**レスポンス**:
```json
{ "declinedCount": 2, "message": "2件のキャンセル待ちを辞退しました" }
```

#### POST /api/lottery/rejoin-waitlist
**説明**: キャンセル待ち復帰（セッション単位）。復帰時のキャンセル待ち番号は最後尾。
**権限**: SUPER_ADMIN, ADMIN, PLAYER（PLAYERは自分のみ）
**リクエスト**:
```json
{ "sessionId": 100, "playerId": 10 }
```
**レスポンス**:
```json
{ "rejoinedCount": 2, "message": "キャンセル待ちに復帰しました（2件）" }
```

#### PUT /api/lottery/admin/edit-participants
**説明**: 管理者による参加者手動編集。WON→CANCELLEDへのステータス変更時は繰り上げフローが自動発動（当日は除く）。
**権限**: SUPER_ADMIN, ADMIN
**リクエスト**: `AdminEditParticipantsRequest`

#### POST /api/lottery/confirm
**説明**: 抽選結果を確定し、伝助への一括書き戻しをトリガー。`confirmed_at`/`confirmed_by` を設定。団体単位で確定状態を管理
**権限**: SUPER_ADMIN, ADMIN（ADMINは自団体のみ）
**リクエスト**: `LotteryExecutionRequest` (year, month, organizationId)
**レスポンス**: `LotteryExecution`

#### POST /api/lottery/preview
**説明**: 抽選プレビュー。抽選アルゴリズムを実行するがDBには保存しない。締め切り前チェック・確定済みチェックあり
**権限**: SUPER_ADMIN, ADMIN（ADMINは自団体のみ）
**リクエスト**: `LotteryExecutionRequest` (year, month, organizationId)
**レスポンス**: `List<LotteryResultDto>`

#### POST /api/lottery/notify-waitlisted
**説明**: キャンセル待ち（WAITLISTED）の参加者のみにアプリ内通知 + LINE通知を送信
**権限**: SUPER_ADMIN, ADMIN（ADMINは自団体のみ）
**リクエスト**: `{ year, month, organizationId }`
**レスポンス**: `{ inAppCount, lineSent, lineFailed, lineSkipped }`

#### GET /api/lottery/executions?year={year}&month={month}
**説明**: 抽選実行履歴取得。`confirmedAt` フィールドで確定状態を確認可能
**権限**: なし

#### POST /api/lottery/same-day-join
**説明**: 当日先着参加。12:00以降にキャンセルで空いた枠に先着1名がWONとして参加登録される。枠が既に埋まっている場合は409 Conflict。LINEのpostback `action=same_day_join` またはアプリから呼び出し可能。
**権限**: SUPER_ADMIN, ADMIN, PLAYER
**リクエスト**:
```json
{
  "sessionId": 100,
  "matchNumber": 2,
  "playerId": 10
}
```
**レスポンス（成功）**: 200 OK
```json
{
  "message": "参加が確定しました"
}
```
**レスポンス（枠なし）**: 409 Conflict
```json
{
  "error": "この枠は既に埋まっています"
}
```

---

### 4.10 通知API

#### GET /api/notifications?playerId={playerId}
**説明**: 通知一覧取得
**権限**: なし
**レスポンス**: `List<NotificationDto>`

#### GET /api/notifications/unread-count?playerId={playerId}
**説明**: 未読通知数取得
**権限**: なし
**レスポンス**: `{"count": 3}`

#### PUT /api/notifications/{id}/read
**説明**: 通知を既読にする
**権限**: なし

#### DELETE /api/notifications?playerId={playerId}
**説明**: 通知を一括削除（論理削除）
**権限**: なし
**レスポンス**: `{"deleted": 5}`

---

### 4.10 Google Calendar API

#### POST /api/google-calendar/sync
**説明**: Google Calendarと練習予定を同期
**権限**: なし
**リクエスト**: `GoogleCalendarSyncRequest`
```json
{
  "accessToken": "ya29.xxx...",
  "playerId": 1
}
```
**レスポンス**: `GoogleCalendarSyncResponse`

---

### 4.11 Web Push API

#### GET /api/push-subscriptions/vapid-public-key
**説明**: VAPID公開鍵を取得
**権限**: なし
**レスポンス**: `{"publicKey": "BPxxx..."}`

#### POST /api/push-subscriptions
**説明**: Push購読を登録
**権限**: なし
**リクエスト**: `PushSubscriptionRequest`
```json
{
  "playerId": 1,
  "endpoint": "https://fcm.googleapis.com/...",
  "p256dhKey": "BPxxx...",
  "authKey": "xxx..."
}
```

#### DELETE /api/push-subscriptions?playerId={playerId}&endpoint={endpoint}
**説明**: Push購読を解除
**権限**: なし

#### GET /api/push-subscriptions/preferences/{playerId}
**説明**: Web Push通知設定を取得（レコードなしの場合はデフォルト値を返す）
**権限**: なし
**レスポンス**:
```json
{
  "playerId": 1,
  "enabled": true,
  "lotteryResult": true,
  "waitlistOffer": true,
  "offerExpiring": true,
  "offerExpired": true,
  "channelReclaimWarning": true,
  "densukeUnmatched": true
}
```

#### PUT /api/push-subscriptions/preferences
**説明**: Web Push通知設定を更新（レコードがなければ新規作成）
**権限**: なし
**リクエスト**: `PushNotificationPreferenceDto`
```json
{
  "playerId": 1,
  "enabled": true,
  "lotteryResult": true,
  "waitlistOffer": false,
  "offerExpiring": true,
  "offerExpired": true,
  "channelReclaimWarning": true,
  "densukeUnmatched": true
}
```

---

### 4.13 LINE通知API

#### POST /api/line/{channelType}/enable
**説明**: LINE通知を有効化する（チャネル割り当て＋ワンタイムコード発行）
**権限**: なし（channelType=ADMIN の場合、Service層でADMIN/SUPER_ADMINロールをチェック）
**パスパラメータ**: `channelType` — PLAYER または ADMIN
**リクエスト**: `{ "playerId": Long }`
**レスポンス**: `{ "friendAddUrl": String, "linkingCode": String, "codeExpiresAt": String, "status": String }`

#### DELETE /api/line/{channelType}/disable
**説明**: LINE通知を無効化する（チャネル解放）
**権限**: なし
**パスパラメータ**: `channelType` — PLAYER または ADMIN
**リクエスト**: `{ "playerId": Long }`

#### POST /api/line/{channelType}/reissue-code
**説明**: ワンタイムコードを再発行する
**権限**: なし
**パスパラメータ**: `channelType` — PLAYER または ADMIN
**リクエスト**: `{ "playerId": Long }`
**レスポンス**: `{ "linkingCode": String, "codeExpiresAt": String }`

#### GET /api/line/{channelType}/status?playerId={playerId}
**説明**: LINE連携状態を取得する（用途別）
**権限**: なし
**パスパラメータ**: `channelType` — PLAYER または ADMIN
**レスポンス**: `{ "enabled": Boolean, "linked": Boolean, "friendAddUrl": String }`

#### GET /api/line/preferences?playerId={playerId}
**説明**: 通知種別ごとの設定を取得する
**権限**: なし

#### PUT /api/line/preferences
**説明**: 通知種別ごとの設定を更新する
**権限**: なし
**リクエスト**: `LineNotificationPreferenceDto`

#### POST /api/line/webhook/{lineChannelId}
**説明**: LINEプラットフォームからのWebhookを受信する
**認証**: x-line-signatureヘッダーによる署名検証
**処理イベント**: follow（コード入力案内返信）、message（コード検証・紐付け）、postback（繰り上げオファー応答）、unfollow（記録のみ）

**postbackイベント処理フロー**:
1. postbackデータをパース（`action=waitlist_accept&participantId=123`）
2. LINE userId → LineChannelAssignment → playerId の紐付けを検証
3. participantIdの所有者がpostback送信者と一致することを確認
4. ステータスがOFFERED以外の場合は「処理済み」を返信
5. `WaitlistPromotionService.respondToOffer()` を呼び出し
6. 結果をReply APIで返信（承諾/辞退の確認メッセージ）

### 4.14 LINE管理者API

#### GET /api/admin/line/channels?channelType={channelType}
**説明**: チャネル一覧を取得する（用途別フィルタ対応）
**権限**: SUPER_ADMIN
**クエリパラメータ**: `channelType`（任意）— PLAYER / ADMIN。未指定時は全件返却

#### POST /api/admin/line/channels
**説明**: チャネルを登録する（個別）。リクエストボディに `channelType` を含める（デフォルト: PLAYER）
**権限**: SUPER_ADMIN

#### POST /api/admin/line/channels/import
**説明**: チャネルを一括登録する（JSON配列）
**権限**: SUPER_ADMIN

#### PUT /api/admin/line/channels/{channelId}/disable
**説明**: チャネルを無効化する
**権限**: SUPER_ADMIN

#### PUT /api/admin/line/channels/{channelId}/enable
**説明**: チャネルを有効化する
**権限**: SUPER_ADMIN

#### DELETE /api/admin/line/channels/{channelId}/assignment
**説明**: チャネルの強制割り当て解除
**権限**: SUPER_ADMIN

#### ~~POST /api/admin/line/send/lottery-result~~ （廃止）
**説明**: `POST /api/lottery/notify-results` に統合。アプリ内通知とLINE通知を一括送信する。

#### POST /api/admin/line/send/match-pairing
**説明**: 対戦組み合わせをLINE送信する
**権限**: SUPER_ADMIN, ADMIN
**リクエスト**: `{ "sessionId": Long }`
**レスポンス**: `{ "sentCount": Integer, "failedCount": Integer, "skippedCount": Integer }`

#### GET /api/admin/line/schedule-settings
**説明**: スケジュール型通知の設定を取得する
**権限**: SUPER_ADMIN, ADMIN

#### PUT /api/admin/line/schedule-settings
**説明**: スケジュール型通知の設定を更新する
**権限**: SUPER_ADMIN, ADMIN
**リクエスト**: `{ "notificationType": String, "enabled": Boolean, "daysBefore": [Integer] }`

#### POST /api/admin/line/channels/migrate-webhook-urls
**説明**: 全チャネルのWebhook URLをLINEチャネルIDベースに一括移行する。LINE Messaging APIを呼び出し、各チャネルのWebhook URLを `/api/line/webhook/{lineChannelId}` 形式に更新する。
**権限**: SUPER_ADMIN
**レスポンス**: `{ "successCount": Integer, "failedCount": Integer, "skippedCount": Integer }`

#### POST /api/admin/line/rich-menu/setup
**説明**: PLAYERチャネル全体にリッチメニューを一括設定する。画像をアップロードし、全PLAYERチャネルにリッチメニューを作成・画像設定・デフォルト適用する。
**権限**: SUPER_ADMIN
**リクエスト**: `multipart/form-data` — `image`: リッチメニュー画像（PNG/JPEG、2500x1686px）
**レスポンス**: `{ "successCount": Integer, "failureCount": Integer, "failures": [String] }`

### 4.15 システム設定API (`/api/system-settings`)

#### GET /api/system-settings
**説明**: 全設定取得
**権限**: SUPER_ADMIN, ADMIN

#### GET /api/system-settings/{key}
**説明**: 設定値取得
**権限**: なし

#### PUT /api/system-settings/{key}
**説明**: 設定値更新
**権限**: SUPER_ADMIN, ADMIN
**リクエスト**:
```json
{
  "value": "3"
}
```

**利用可能な設定キー**:
| キー | 説明 | デフォルト値 |
|------|------|-------------|
| `lottery_deadline_days_before` | 締切日数（月初から何日前） | `0` |

### 4.16 メンター関係API (`/api/mentor-relationships`)

#### POST /api/mentor-relationships
**説明**: メンター指名（PENDING状態で作成）
**権限**: ALL
**リクエスト**:
```json
{
  "mentorId": 1,
  "organizationId": 1
}
```
**バリデーション**: 自分自身指名不可、同一組み合わせの重複チェック（REJECTED済みは再指名可）

#### PUT /api/mentor-relationships/{id}/approve
**説明**: メンター関係承認（PENDING → ACTIVE）
**権限**: ALL（メンター本人のみ）

#### PUT /api/mentor-relationships/{id}/reject
**説明**: メンター関係拒否（PENDING → REJECTED）
**権限**: ALL（メンター本人のみ）

#### DELETE /api/mentor-relationships/{id}
**説明**: メンター関係解除（物理削除）
**権限**: ALL（メンターまたはメンティー本人のみ）

#### GET /api/mentor-relationships/my-mentors
**説明**: 自分のメンター一覧取得（ACTIVE + PENDING）
**権限**: ALL

#### GET /api/mentor-relationships/my-mentees
**説明**: 自分のメンティー一覧取得（ACTIVEのみ）
**権限**: ALL

#### GET /api/mentor-relationships/pending
**説明**: 承認待ちリクエスト取得（自分がメンターのPENDING）
**権限**: ALL

### 4.17 メンターコメントAPI (`/api/matches/{matchId}/comments`)

#### GET /api/matches/{matchId}/comments?menteeId={menteeId}
**説明**: コメント一覧取得
**権限**: ALL（メンティー本人またはACTIVEメンターのみ）

#### POST /api/matches/{matchId}/comments
**説明**: コメント投稿（LINE通知は即時送信しない。`line_notified = false` で保留）
**権限**: ALL（メンティー本人またはACTIVEメンターのみ）
**リクエスト**:
```json
{
  "menteeId": 1,
  "content": "コメント内容"
}
```

#### POST /api/matches/{matchId}/comments/notify?menteeId={menteeId}
**説明**: 未通知コメントをまとめてLINE Flex Messageで送信
**権限**: ALL（メンティー本人またはACTIVEメンターのみ）
**処理**: 現在のユーザーが書いた未通知コメント（`line_notified = false`）を収集し、1つのFlex Messageにまとめて送信。送信成功時のみ `line_notified = true` に更新。
**レスポンス**:
```json
{
  "notifiedCount": 3,
  "result": "SUCCESS"
}
```
**result値**: `SUCCESS`（全件送信成功）/ `FAILED`（送信失敗）/ `SKIPPED`（通知対象外）

#### PUT /api/matches/{matchId}/comments/{commentId}
**説明**: コメント編集（投稿者本人のみ）
**権限**: ALL
**リクエスト**:
```json
{
  "content": "更新後のコメント内容"
}
```

#### DELETE /api/matches/{matchId}/comments/{commentId}
**説明**: コメント論理削除（投稿者本人のみ）
**権限**: ALL

---

## 5. 画面設計

### 5.1 画面一覧

| 画面名 | パス | 権限 | 説明 |
|-------|------|------|------|
| ランディング | /landing | なし | 公開ランディングページ |
| ログイン | /login | なし | ログイン画面 |
| ユーザー登録 | /register | なし | 新規ユーザー登録 |
| ダッシュボード | / | 全員 | ホーム画面（今日の対戦カード・統計） |
| プロフィール | /profile | 全員 | 自分のプロフィール閲覧 |
| プロフィール編集 | /profile/edit | 全員 | 自分のプロフィール編集 |
| 練習日一覧 | /practice | 全員 | カレンダー形式練習日一覧（参加状況バッジ表示） |
| 練習日登録・編集 | /practice/new, /practice/:id/edit | SUPER_ADMIN | 練習日作成・更新 |
| 練習参加登録 | /practice/participation | 全員 | 月単位参加登録 |
| 試合一覧 | /matches | 全員 | 試合一覧 |
| 試合登録・編集 | /matches/new, /matches/:id/edit | 全員 | 試合登録・更新 |
| 試合詳細 | /matches/:id | 全員 | 試合詳細表示 |
| 試合結果表示 | /matches/results | 全員 | 日付別試合結果ビュー |
| 一括試合結果入力 | /matches/bulk-input | 全員 | 複数試合の一括入力 |
| 対戦組み合わせ生成 | /pairings/generate | 全員 | 組み合わせ生成・表示 |
| 組み合わせサマリー | /pairings/summary | 全員 | 日別組み合わせ一覧 |
| 選手一覧 | /players | 全員 | 選手一覧 |
| 選手詳細 | /players/:id | 全員 | 選手詳細・統計 |
| 選手登録・編集 | /players/new, /players/:id/edit | SUPER_ADMIN | 選手作成・更新 |
| 会場一覧 | /venues | 全員 | 会場マスタ一覧 |
| 会場登録・編集 | /venues/new, /venues/:id/edit | 全員 | 会場作成・更新 |
| 抽選結果 | /lottery/results | 全員 | 月別抽選結果表示 |
| 繰り上げ応答 | /lottery/offer-response | 全員 | 繰り上げオファーへの応答 |
| キャンセル待ち状況 | /lottery/waitlist-status | 全員 | キャンセル待ち一覧 |
| 通知一覧 | /notifications | 全員 | アプリ内通知一覧 |
| LINE通知設定 | /settings/line | 全員 | LINE連携の有効化/無効化、友だち追加URL・ワンタイムコード表示、通知種別ON/OFF |
| LINEチャネル管理 | /admin/line/channels | SUPER_ADMIN | チャネル一覧・登録・無効化・強制解除 |
| LINE通知スケジュール設定 | /admin/line/schedule | ADMIN+ | スケジュール型通知の送信日数設定 |
| 伝助管理 | /admin/densuke | ADMIN+ | 団体別の伝助URL管理・手動同期実行・書き込み状況・未登録者一括登録（ADMINは自団体のみ、SUPER_ADMINは全団体） |
| 抽選管理 | /admin/lottery | ADMIN+ | 抽選プレビュー実行→結果確認→確定→通知送信の一連のワークフロー。システム設定へのリンクあり |
| メンター管理 | /settings/mentor | 全員 | メンター指名・承認・拒否・解除、メンティー管理（試合履歴への導線あり） |
| プライバシーポリシー | /privacy-policy | なし | プライバシーポリシー |
| 利用規約 | /terms-of-service | なし | 利用規約 |

---

### 5.2 画面遷移と導線

#### ホーム画面からの導線
ホーム画面（`/`）には以下のクイックアクション:
- **試合記録**: `/matches/new` へ遷移（試合登録）
- **練習記録**: `/practice` へ遷移（カレンダー画面）
- **組み合わせ**: `/pairings/generate` へ遷移（対戦組み合わせ生成）

カレンダー画面（`/practice`）を練習関連機能の中心ハブとして設計。
- カレンダー画面から以下に遷移可能:
  - 参加登録画面（年月を引き継ぐ）
  - 練習日編集（SUPER_ADMINのみ）

#### 練習関連の導線フロー
```
ホーム画面（/）
  ↓ 「練習記録」クリック
カレンダー画面（/practice）
  ├─ 「参加登録」ボタン → 参加登録画面（/practice/participation）
  │                        ↓ 保存後1秒待機
  │                        └→ カレンダー画面に自動戻り（データ再取得）
  │
  ├─ 日付クリック → モーダル表示
  │   └─ 「参加登録」ボタン → 参加登録画面（年月引き継ぎ）
  │
  └─ 「編集」ボタン（SUPER_ADMINのみ） → 練習日編集画面
```

---

### 5.3 主要画面設計

#### 5.3.0 ホーム画面（ダッシュボード）
**パス**: `/`

**表示内容**:
- **ナビゲーションバー**: 選手名、ハンバーガーメニュー（プロフィール、管理メニュー、ログアウト）、未読通知バッジ
- **繰り上げオファーバナー**: 未応答の繰り上げ参加通知がある場合に表示。タップで通知一覧に遷移
- **次の練習（NEXT / TODAY）**:
  - 次回参加予定の練習日・時間・会場・参加試合番号
  - 当日の場合は `TODAY` バッジ表示、ADMIN以上には「組み合わせを作成」ボタン
  - 参加者一覧（段位順ソート、自分はハイライト、キャンセル待ちは別セクション）
- **参加率TOP3（団体別フィルタリング）**:
  - 当月の参加率上位3名 + 自分がTOP3に入っていない場合は自分の参加率も表示
  - ユーザーの所属団体（organization）でフィルタリング
  - **1団体所属時**: 団体名ラベルなしで1セクション
  - **複数団体所属時**: 「全体」（全所属団体合算）→ 団体A → 団体B の順で複数セクション表示
  - レスポンス構造: `participationGroups` 配列（各要素に `organizationId`, `organizationName`, `top3`, `myRate`）

**データフロー**:
1. `GET /api/home?playerId={playerId}` - ホーム画面統合APIで全データを1リクエストで取得
   - 次の練習情報（`nextPractice`）
   - 参加率グループ（`participationGroups`）: 所属団体に応じて団体別・全体の参加率を返す
   - 未読通知数（`unreadNotificationCount`）
   - 繰り上げオファー有無（`hasPendingOffer`）
2. 画面フォーカス時に自動再取得（`window.addEventListener('focus')`）

**ナビゲーション**:
- 繰り上げオファーバナー → `/notifications`
- 「参加登録」リンク → `/practice/participation`
- 「組み合わせを作成」ボタン（ADMIN、当日のみ） → `/pairings?date={sessionDate}`

---

#### 5.3.1 練習日一覧（カレンダー形式）
**パス**: `/practice`

**表示内容**:
- **月選択（前月/次月ボタン）**
- **カレンダーグリッド（日曜～土曜）**
- **参加状況バッジ**
  - ●（緑色）: 全試合参加
  - ◐（黄色）: 部分参加（一部の試合のみ）
  - バッジなし: 未参加
- **各日付に練習日がある場合、背景色変更＋場所名省略表示**
- **今日の日付をハイライト**
- **参加登録ボタン（カレンダー上部）**: 年月を引き継いで参加登録画面に遷移
- **日付クリック→モーダルポップアップ**

**モーダル内容**:
- 日付（曜日付き）
- 場所
- 試合別参加者（アコーディオン形式）
  - ▶ X試合目 (Y名) ← クリックで展開
  - ▼ X試合目 (Y名) ← 展開時
    - 自分が参加する試合は緑色でハイライト
    - 参加者リスト（箇条書き）
- 備考
- 参加登録ボタン（モーダル内）: 年月を引き継いで参加登録画面に遷移
- 編集・削除ボタン（SUPER_ADMIN のみ表示）

**`openToday` パラメータによる自動ポップアップ（LINEリッチメニュー導線）**:
- `/practice?openToday=true` でアクセスすると、当日の練習セッションがあればモーダルポップアップを自動表示する
- パラメータ処理後、`openToday` はURLから除去される（`setSearchParams` で `replace: true`、ブラウザ履歴に残さない）
- 処理済みフラグ（`useRef`）で重複表示を防止
- 未ログイン時は `PrivateRoute` が `/login` へリダイレクト（`location` を `state.from` に保持）し、ログイン成功後に元URL（`/practice?openToday=true`）へ復帰する

**データフロー**:
1. `practiceAPI.getAll()` で全セッション取得
2. `practiceAPI.getPlayerParticipations(currentPlayer.id, year, month)` で自分の参加状況取得
3. セッションごとに参加状況を判定（全試合/部分/未参加）
4. カレンダーセルにバッジ表示
5. モーダル内でアコーディオンを緑色表示

---

#### 5.3.2 練習参加登録
**パス**: `/practice/participation`

**表示内容**:
- **年月ナビゲーション**: 固定ヘッダーに左右矢印ボタン（ChevronLeft / ChevronRight）で月を切り替え。カレンダー画面から遷移時は年月が引き継がれる
- **練習日一覧（テーブル形式）**
  - 日付列
  - 場所列
  - 団体名バッジ: 各セッションの所属団体を略称（例: わすら、北大）で色付きバッジ表示。色は団体の `color` フィールドから取得
  - 試合番号チェックボックス列（1～7）
    - 既存参加状況を反映してチェック済み
    - **参加人数バッジ**: 各試合の参加者数を表示。定員に対する割合で色分け（赤: 80%以上、橙: 60%、黄: 40%、緑: 40%未満）
  - **抽選ステータス表示**（抽選実行済みセッション）: チェックボックスの代わりにステータスバッジを表示
    - WON（緑）、WAITLISTED（黄・番号付き）、OFFERED（青）、PENDING（灰）、DECLINED/CANCELLED（灰）
  - **締切後の制限**: 締切後は既存の参加登録チェックボックスを無効化（解除不可）。抽選実行済みセッションは全チェックボックスが操作不可
- **SAME_DAYタイプ確認ダイアログ**: SAME_DAYタイプの団体で12:00以降に保存する場合、管理者への連絡確認ダイアログを表示
- **保存ボタン**: 成功後、1秒待ってカレンダー画面に自動遷移

**データフロー**:
1. ページ読み込み時に以下のAPIを並列取得:
   - `GET /api/practice-sessions/year-month` — 月内の練習セッション一覧
   - `GET /api/practice-sessions/participations/player/{playerId}` — 既存参加登録
   - `GET /api/practice-sessions/participations/player/{playerId}/status` — 抽選・締切ステータス（参加状況詳細、抽選実行有無、締切前後）
   - `GET /api/lottery/deadline` — 締切情報の表示用
   - `GET /api/organizations` — 団体名・色・締切タイプ
2. 取得データをもとにテーブルを描画（チェックボックス or ステータスバッジ）
3. チェックボックス操作
4. 保存ボタン → `POST /api/practice-sessions/participations`（PLAYERロールは自分のplayerIdのみ操作可能）
5. 成功メッセージ表示（1秒間）
6. `/practice`（カレンダー画面）へ自動ナビゲート
7. カレンダー画面で自動的にデータ再取得

---

#### 5.3.3 対戦組み合わせ
**パス**: `/pairings`

**表示内容**:
- 日付選択
- 試合番号選択（1～7）
- 参加者一覧（チェックボックス）
- 「自動マッチング」ボタン → `POST /api/match-pairings/auto-match`
- 提案されたペア一覧（ドラッグ&ドロップ対応）
  - 選手カード（DraggablePlayerChip）を長押し/クリックでドラッグして入れ替え
  - 最近の対戦履歴（日付、何日前）
- 手動調整: 選手カード同士のスワップ、待機リストとの入れ替え
- 新規ペアリング作成ドロップゾーン（待機選手をドロップして新規行作成）
- 「組み合わせ確定」ボタン → `POST /api/match-pairings/batch`（片方空欄時は無効化）
- 待機者リスト（DroppableSlot、選手はDraggablePlayerChip）

**アルゴリズム**:
- 過去30日の対戦履歴取得
- スコア = -(100 / 最終対戦日からの日数)
- 初対戦: スコア0（優先）
- 1日前: -100点、2日前: -50点、7日前: -14点
- 貪欲法で最適ペアリング

---

#### 5.3.4 会場管理
**パス**: `/venues`

**表示内容**:
- 会場一覧（名前、デフォルト試合数）
- 「新規登録」ボタン → `/venues/new`
- 各会場の編集・削除ボタン

**会場登録・編集フォーム**:
- 会場名（必須、ユニーク）
- デフォルト試合数
- 試合時間割（試合番号ごとの開始・終了時刻）

---

#### 5.3.5 抽選結果
**パス**: `/lottery/results`

**表示内容**:
- 年月選択
- セッション別の抽選結果
  - 当選者リスト（WON）
  - キャンセル待ちリスト（WAITLISTED、番号順）
  - 各参加者のステータス表示
- 管理者向け: 手動抽選実行ボタン、再抽選ボタン、参加者編集

---

#### 5.3.6 通知一覧
**パス**: `/notifications`

**表示内容**:
- 通知一覧（新しい順）
  - 通知タイトル
  - 通知本文
  - 種別アイコン（当選/落選/繰り上げ等）
  - 既読/未読状態
  - 作成日時
- クリックで既読にする
- ヘッダーに未読バッジ表示

---

## 6. 権限設計

### 6.1 ロール定義

| ロール | 説明 |
|-------|------|
| SUPER_ADMIN | 最上位管理者。全機能アクセス可能。選手登録・削除、練習日作成・削除、ロール変更、抽選実行可能。 |
| ADMIN | 管理者。対戦組み合わせ作成・削除、セッション再抽選可能。練習日作成・削除は不可。 |
| PLAYER | 一般選手。基本機能（試合記録、参加登録、閲覧）のみ。 |

### 6.2 権限マトリックス

| 機能カテゴリ | 機能 | SUPER_ADMIN | ADMIN | PLAYER |
|------------|------|------------|-------|--------|
| 認証 | ログイン | ○ | ○ | ○ |
| 選手管理 | 選手登録 | ○ | × | × |
| | 選手削除 | ○ | × | × |
| | ロール変更 | ○ | × | × |
| | 選手更新 | ○ | ○ | ○ |
| | 選手一覧・詳細 | ○ | ○ | ○ |
| 試合記録 | 試合登録・更新・削除 | ○ | ○ | ○ |
| | 試合一覧・詳細・統計 | ○ | ○ | ○ |
| 練習日 | 練習日作成・更新・削除 | ○ | × | × |
| | 練習日一覧・詳細 | ○ | ○ | ○ |
| 練習参加 | 参加登録 | ○ | ○ | ○ |
| | 参加状況閲覧 | ○ | ○ | ○ |
| 対戦組み合わせ | 組み合わせ作成・削除 | ○ | ○ | × |
| | 自動マッチング | ○ | ○ | × |
| | 組み合わせ閲覧 | ○ | ○ | ○ |
| 会場管理 | 会場CRUD | ○ | ○ | ○ |
| 抽選 | 月別抽選実行 | ○ | ○（自団体のみ） | × |
| | 抽選結果確定 | ○ | ○（自団体のみ） | × |
| | セッション再抽選 | ○ | ○ | × |
| | 参加者手動編集 | ○ | ○ | × |
| | 抽選結果閲覧 | ○ | ○ | ○ |
| | キャンセル・繰り上げ応答 | ○ | ○ | ○ |
| 通知 | 通知閲覧・既読 | ○ | ○ | ○ |
| メンター | メンター指名・承認・拒否・解除 | ○ | ○ | ○ |
| | メンターコメント投稿・編集・削除 | ○ | ○ | ○ |
| Google Calendar | 同期 | ○ | ○ | ○ |
| Web Push | 購読管理 | ○ | ○ | ○ |
| LINE通知 | LINE連携有効化/無効化 | ○ | ○ | ○ |
| | 通知種別ON/OFF設定 | ○ | ○ | ○ |
| | チャネル管理（登録・無効化・強制解除） | ○ | × | × |
| | 抽選結果/組み合わせLINE一括送信 | ○ | ○ | × |
| | スケジュール型通知設定 | ○ | ○ | × |

### 6.3 実装方法

**バックエンド**:
- `@RequireRole` アノテーションをコントローラーメソッドに付与
- `RoleCheckInterceptor` がリクエストヘッダー `X-User-Role` と `X-User-Id` を検証
- `@RequireRole` 付きエンドポイントでは `X-User-Id` 必須（リクエスト属性 `currentUserId` / `currentUserRole` にセット）
- 権限不足の場合は `403 Forbidden`
- ADMIN時は `RoleCheckInterceptor` が `adminOrganizationId` をリクエスト属性にセット
- `AdminScopeValidator.validateScope()` でADMINの団体スコープを統一的に検証（練習日・組み合わせ・抽選・LINE送信・抜け番・伝助・システム設定）

**フロントエンド**:
- `AuthContext` で `currentPlayer.role` を管理
- `BottomNavContext` でボトムナビゲーションの表示/非表示を管理（`isVisible` state、デフォルト `true`）。`App.jsx` で `BottomNavProvider` として全体をラップ。`Layout.jsx` が `isVisible` を参照してスライドアニメーション（`translate-y-0` ⇔ `translate-y-full`）で切り替え
- Axiosインターセプターで全リクエストに `X-User-Role` ヘッダー追加
- `isSuperAdmin()`, `isAdmin()` などのヘルパー関数で条件付き表示
- `RoleRoute` コンポーネントで管理者専用ルートを保護（`/admin/*`, `/players/*`, `/practice/new` 等）

---

## 7. 主要機能フロー

### 7.1 練習参加登録フロー

```
[ユーザー操作]
1. カレンダー画面（/practice）の「参加登録ボタン」クリック
   または モーダル内の「参加登録ボタン」クリック
   → 年月を引き継いで /practice/participation へ移動
   ↓
2. 年月選択（例: 2026年3月）※カレンダーから遷移時は自動設定
   ↓
[フロントエンド]
3. GET /api/practice-sessions/year-month?year=2026&month=3
   ← 練習日一覧（id, sessionDate, venueId, totalMatches）
   ↓
4. GET /api/practice-sessions/participations/player/{playerId}?year=2026&month=3
   ← 既存参加状況 {sessionId: [matchNumbers]}
   ↓
5. テーブル表示
   - 各練習日に試合番号（1～totalMatches）のチェックボックス
   - 既存参加状況を反映してチェック済み
   ↓
[ユーザー操作]
6. チェックボックス操作（試合ごとに参加/不参加選択）
   ↓
7. 「保存する」ボタンクリック
   ↓
[フロントエンド]
8. POST /api/practice-sessions/participations
   ↓
[バックエンド: PracticeSessionService]
9. その月の全セッションIDを取得
   ↓
10. 既存参加記録を一括削除
   ↓
11. 新規参加記録を一括登録
   ↓
12. レスポンス: 201 Created
   ↓
[フロントエンド]
13. 成功メッセージ表示 → 1秒待機 → /practice へ自動ナビゲート
```

---

### 7.2 自動マッチングフロー

```
[ユーザー操作]
1. /pairings へ移動
   ↓
2. 日付・試合番号選択
   ↓
[フロントエンド]
3. 参加者一覧取得・表示（デフォルト全員選択）
   ↓
[ユーザー操作]
4. 参加者選択調整 → 「自動マッチング」ボタンクリック
   ↓
[バックエンド: MatchPairingService.autoMatch()]
5. ロック済みペアリング判定（既存ペアリング + 対応するmatchesの存在チェック）
   ↓
6. ロック済みプレイヤーを参加者リストから除外
   ↓
7. 過去30日の対戦履歴を取得
   ↓
8. ペアごとの最終対戦日マップ作成
   ↓
9. 同日既存対戦を除外
   ↓
10. 参加者シャッフル → 貪欲法でスコア最高ペアを選択
   ↓
11. レスポンス: AutoMatchingResult（ペア一覧 + 待機者リスト + ロック済みペア一覧）
   ↓
[ユーザー操作]
12. 手動調整 → 「組み合わせ確定」ボタンクリック
   ↓
[バックエンド]
13. ロック済みペアリングを保持しつつ、未ロック分を削除 → 新規一括登録
```

---

### 7.3 試合記録登録フロー（簡易登録）

```
[ユーザー操作]
1. /matches/new へ移動（ホーム画面から遷移時はlocation.stateで自動入力）
   ↓
2. フォーム入力（試合日、試合番号、対戦相手名、結果、札差、コメント）
   ↓
3. 「登録」ボタンクリック
   ↓
[バックエンド: MatchService.createMatchSimple()]
4. 重複チェック → Match エンティティ構築 → 保存
   ↓
5. 選手名付与（enrichMatchesWithPlayerNames） → レスポンス: 201 Created
   ↓
[フロントエンド]
6. 成功メッセージ表示 → /matches へリダイレクト
```

---

### 7.4 抽選フロー

```
[管理者操作]
1. 抽選結果画面で「抽選実行」ボタンクリック
   ↓
[バックエンド: LotteryService.executeLottery()]
2. 対象月のセッション一覧取得
   ↓
3. セッションごとに、試合番号ごとの参加希望者を取得
   ↓
4. 定員超過判定（capacity vs 参加希望者数）
   ↓
5. 定員内 → 全員WON、定員超過 → ランダム抽選
   - 当選者: status = WON
   - 落選者: status = WAITLISTED + waitlist_number付与
   ↓
6. 通知作成（LOTTERY_WON / LOTTERY_WAITLISTED）
   ↓
7. Web Push通知送信（購読者に対して）
   ↓
8. LotteryExecution履歴保存
   ↓
[フロントエンド]
9. 抽選結果表示（当選者・キャンセル待ちリスト）

[キャンセル時]
1. 当選者が「キャンセル」→ status = CANCELLED
   ↓
2. キャンセル待ちが存在する場合のみ以下を実行（定員未達時は通知なし）:
   キャンセル待ち1番を繰り上げ → status = OFFERED, offerDeadline設定
   ↓
3. 繰り上げ通知（WAITLIST_OFFER）+ Web Push + LINE Flex Message（参加/辞退ボタン付き）
   ↓
4. 繰り上げ者が応答（Webアプリ or LINEボタン、どちらからでも可）:
   - 承諾 → status = WON
   - 辞退/期限切れ → status = DECLINED → 次の待ち番号を繰り上げ
```

---

### 7.5 当日キャンセル補充フロー

```
[12:00確定フェーズ — SameDayConfirmationScheduler → WaitlistPromotionService.expireOfferedForSameDayConfirmation()]
1. 当日セッションのOFFERED参加者を一括でDECLINEDに変更（dirty=true設定で伝助同期による再活性化を防止）
   ↓
2. 空き枠が発生した場合、当日キャンセル補充フロー（先着ボタン方式）を自動トリガー
   - 非WON参加者に空き募集Flex Message送信（オレンジヘッダー、「参加する」ボタン付き）
   ↓
3. WON参加者にメンバーリストFlex Message（青ヘッダー）をLINE送信
   - 通知トグル: sameDayConfirmation

[当日キャンセル→補充フェーズ — WaitlistPromotionService.cancelParticipation()]
1. 12:00以降にWON参加者がキャンセル
   ↓
2. LotteryDeadlineHelper.isAfterSameDayNoon() で12:00以降判定
   ↓
3. WON参加者にキャンセル通知送信（通知トグル: sameDayCancel）
   ↓
4. 非WON参加者に空き募集Flex Message送信（オレンジヘッダー、「参加する」ボタン付き）
   - postback: action=same_day_join&sessionId={id}&matchNumber={num}
   - 通知トグル: sameDayVacancy

[先着参加フェーズ — LINEボタン or アプリ]
1. LINEボタンpostback → LineWebhookController（same_day_joinアクション）
   または アプリ → LotteryController POST /api/lottery/same-day-join
   ↓
2. WaitlistPromotionService.handleSameDayJoin(sessionId, matchNumber, playerId)
   ↓
3. 空き枠チェック → 先着1名がWON（2人目以降: 409 Conflict）
   ↓
4. 参加者本人に参加確定通知送信
   ↓
5. WON参加者全員に枠状況通知送信
   - 残り枠あり: オレンジヘッダー + 「参加する」ボタン
   - 枠埋まり: グレーヘッダー + ボタンなし

[アプリ経由参加登録通知 — PracticeParticipantService]
1. 12:00以降にアプリ経由でWON登録された場合
   ↓
2. WONメンバーに通知送信

[伝助同期経由参加登録通知 — DensukeImportService]
1. 12:00以降に伝助上で○に変更され、伝助同期によりWONとして登録・昇格された場合
   ↓
2. DensukeImportService.notifyVacancyUpdateIfNeeded() が枠状況通知を送信
   - 対象: 新規WON登録 / WAITLISTED→WON昇格 / 再有効化（CANCELLED等→WON）
   - 内容: sendSameDayVacancyUpdateNotification()（残り枠あり or 枠埋まり）
```

**変更クラス一覧:**

| クラス | 変更内容 |
|--------|---------|
| `SameDayConfirmationScheduler`（新規） | scheduler/ — 毎日12:00 JSTに`WaitlistPromotionService.expireOfferedForSameDayConfirmation()`へ委譲 + メンバーリスト送信 |
| `SameDayVacancyScheduler`（新規） | scheduler/ — 毎日0:00 JSTに当日セッションの空き枠検出＋`SAME_DAY_VACANCY`/`ADMIN_SAME_DAY_CANCEL`自動送信 |
| `WaitlistPromotionService` | `expireOfferedForSameDayConfirmation()`追加（dirty=true設定＋空き枠補充トリガー）、cancelParticipationの12:00以降分岐追加、handleSameDayJoinメソッド追加。**管理者通知バッチ化**: `cancelParticipationSuppressed()`・`demoteToWaitlistSuppressed()`で通知データ（`AdminWaitlistNotificationData`）を返し、`sendBatchedAdminWaitlistNotifications()`でセッション×トリガー×プレイヤー単位でまとめ送信 |
| `LineNotificationService` | 確定通知/キャンセル通知/空き募集通知/参加通知/枠状況通知メソッド追加、`getAdminRecipientsForSession()`ヘルパー追加（該当団体ADMIN + 全SUPER_ADMIN）、`sendAdminVacancyNotification()`追加、ADMIN向け送信をキャンセル/参加/確定/キャンセル待ち通知に追加、`isNotificationEnabled()`で全ADMIN_系通知を`organizationId=0`判定に統一、SAME_DAY_VACANCY送信先を団体全メンバーに拡大。**管理者通知Flex改修**: `sendAdminWaitlistNotification()`が`List<Integer> matchNumbers`+試合別キャンセル待ち列を受け取り、トリガー種別に応じたヘッダー/イベント文言、複数試合まとめ表示、キャンセル待ち列の全試合同一判定に対応 |
| `LotteryDeadlineHelper` | isAfterSameDayNoon()追加、calculateOfferDeadline当日対応 |
| `PracticeParticipantService` | 12:00以降参加時の通知送信追加 |
| `DensukeImportService` | 伝助同期でWON登録時の枠状況通知送信追加（notifyVacancyUpdateIfNeeded） |
| `LineWebhookController` | same_day_joinポストバックハンドリング追加 |
| `LotteryController` | POST /api/lottery/same-day-joinエンドポイント追加 |
| `PlayerRepository` | `findByRoleAndAdminOrganizationIdAndActive()`メソッド追加 |
| `PlayerEdit.jsx` | ADMIN管理団体ドロップダウン追加（SUPER_ADMIN専用） |
| `NotificationSettings.jsx` | `adminSameDayCancel`トグル追加 |

**Flex Messageデザイン:**

| メッセージ種別 | ヘッダー色 | ボタン | 送信先 |
|---------------|-----------|--------|--------|
| 参加者確定（メンバーリスト） | 青（#1E88E5） | なし | WON参加者 + 該当団体ADMIN + 全SUPER_ADMIN（管理者通知） |
| 空き募集 | オレンジ（#FF6B00） | 「参加する」（postback） | 団体全メンバー（該当試合WON除く） |
| 残り枠あり（枠状況通知） | オレンジ（#FF6B00） | 「参加する」（postback） | 団体全メンバー（該当試合WON除く） |
| 枠埋まり（枠状況通知） | グレー（#9E9E9E） | なし | 団体全メンバー（該当試合WON除く） |

**DB変更:**

| 対象 | 変更内容 |
|------|---------|
| `LineNotificationType` enum | `SAME_DAY_CONFIRMATION`, `SAME_DAY_CANCEL`, `ADMIN_SAME_DAY_CANCEL`, `SAME_DAY_VACANCY`, `ADMIN_SAME_DAY_CONFIRMATION` の5値追加 |
| `line_notification_preferences` テーブル | `same_day_confirmation`, `same_day_cancel`, `same_day_vacancy`, `admin_same_day_confirmation`, `admin_same_day_cancel` の5カラム追加 |

### 7.6 メンター指名・コメントフロー

```
[メンター指名フロー]
1. メンティーが /settings/mentor でメンター指名
   ↓
2. POST /api/mentor-relationships → PENDING状態で作成
   ↓
3. メンター側の承認待ちリクエストに表示
   ↓
4. メンターが承認（PUT /{id}/approve → ACTIVE）
   または拒否（PUT /{id}/reject → REJECTED）

[メンターコメントフロー（バッチ送信方式）]
1. 試合詳細画面（/matches/:id）でメンティーまたはメンターがコメント投稿
   ↓
2. POST /api/matches/{matchId}/comments → コメント作成（line_notified = false）
   ※ LINE通知は即時送信しない
   ↓
3. ユーザーが「LINE通知を送信（N件）」ボタンを押下
   ↓
4. POST /api/matches/{matchId}/comments/notify → 未通知コメントを収集
   ↓
5. Flex Messageを構築（送信者名・試合情報・コメント一覧）
   ↓
6. LINE通知送信（MENTOR_COMMENT）
   - メンティーがボタン押下 → 全ACTIVEメンターにFlex Message送信
   - メンターがボタン押下 → メンティーにFlex Message送信
   ↓
7. 送信成功時のみ line_notified = true に更新（失敗時は再送可能）

[メンティーメモ更新通知フロー]
1. メンティーが試合編集画面（/matches/:id/edit）で個人メモを更新
   ↓
2. PUT /api/matches/{id}/detailed or PUT /api/matches/{id} → メモ内容が変更されたか比較
   ↓
3. 変更あり → トランザクションコミット後に通知処理を実行
   ↓
4. 全ACTIVEメンターを取得 → mentor_comment トグルがONのメンターにFlex Message送信（MENTEE_MEMO_UPDATE）
   ↓
5. 送信結果をline_message_logに記録（通知失敗時もメモ保存は正常完了）
```

**関連クラス:**

| クラス | 説明 |
|--------|------|
| `MentorRelationship` | entity/ — メンター関係エンティティ（PENDING/ACTIVE/REJECTED） |
| `MatchComment` | entity/ — メンターコメントエンティティ（論理削除対応、`line_notified` で通知状態管理） |
| `MentorRelationshipController` | controller/ — メンター関係CRUD（7エンドポイント） |
| `MatchCommentController` | controller/ — コメントCRUD + 通知送信（5エンドポイント） |
| `MentorRelationshipService` | service/ — 指名・承認・拒否・解除のビジネスロジック |
| `MatchCommentService` | service/ — コメント投稿・編集・削除・アクセス権検証・バッチ通知送信 |
| `LineNotificationService` | service/ — MENTOR_COMMENT / MENTEE_MEMO_UPDATE Flex Message構築・送信 |
| `MentorManagement.jsx` | pages/mentor/ — メンター管理画面 |
| `MatchCommentThread.jsx` | pages/matches/ — コメントスレッドUI（textarea の focus/blur で `BottomNavContext.setVisible` を制御。blur 時 100ms 遅延でチラつき防止。unmount 時に `setVisible(true)` でリセット） |

**DB変更:**

| 対象 | 変更内容 |
|------|---------|
| `mentor_relationships` テーブル | 新規作成（mentor_id, mentee_id, organization_id, status） |
| `match_comments` テーブル | 新規作成（match_id, mentee_id, author_id, content, deleted_at） |
| `LineNotificationType` enum | `MENTOR_COMMENT` 追加 |
| `line_notification_preferences` テーブル | `mentor_comment` カラム追加（DEFAULT TRUE） |
| `line_message_log` CHECK制約 | `MENTOR_COMMENT` 追加 |
| `LineNotificationType` enum | `MENTEE_MEMO_UPDATE` 追加 |
| `line_message_log` CHECK制約 | `MENTEE_MEMO_UPDATE` 追加 |

### 7.7 隣室予約→会場拡張フロー

```
[ユーザー操作（ADMIN+）]
1. 練習日詳細モーダルで隣室が「空き」の場合、「隣室を予約」ボタンを表示
   ↓
2. 「隣室を予約」ボタンクリック
   ↓
[フロントエンド: PracticeList.jsx]
3. kaderuAPI.openReserve() でかでる2・7の予約画面を開く
   ↓
4a. 成功時 → 「予約完了を報告」ボタンを表示（manual_pending状態）
4b. DISABLED時 → 同じく「予約完了を報告」ボタンを表示（手動予約を案内）
   ↓
[ユーザー操作]
5. かでる2・7サイトで予約を完了
   ↓
6. 「予約完了を報告」ボタンクリック
   ↓
[フロントエンド]
7. POST /api/practice-sessions/{id}/confirm-reservation
   ↓
[バックエンド: AdjacentRoomService.confirmReservation()]
8. reservation_confirmed_at をセット（確認者は updated_by で記録）
   ↓
9. レスポンス: 200 OK + 更新後のセッション情報
   ↓
[フロントエンド]
10. 「会場を拡張」ボタンを表示（reservationReady = true）
   ↓
[ユーザー操作]
11. 「会場を拡張」ボタンクリック → 確認ダイアログ
   ↓
[フロントエンド]
12. POST /api/practice-sessions/{id}/expand-venue
   ↓
[バックエンド: AdjacentRoomService.expandVenue()]
13. 会場を拡張後会場に変更、定員を更新
   ↓
14. レスポンス: 200 OK + 更新後のセッション情報
```

**変更対象テーブル・コード**:

| 対象 | 変更内容 |
|------|---------|
| `practice_sessions` テーブル | `reservation_confirmed_at` カラム追加 |
| `AdjacentRoomService` | `confirmReservation()`, `expandVenue()` メソッド追加 |
| `PracticeSessionController` | `POST /{id}/confirm-reservation`, `POST /{id}/expand-venue` エンドポイント追加（ADMIN+） |
| `PracticeList.jsx` | 隣室予約→予約完了報告→会場拡張の3段階UIフロー |

### 7.8 かでる予約 → 練習日自動登録フロー

```
[GitHub Actions: sync-kaderu-reservations.yml]
  cron: */30 * * * *  または  workflow_dispatch
  ↓
[Node.js: scrape-mypage.js]
1. かでる2・7サイトにPlaywright(headless)でログイン
   - 環境変数: KADERU_USER_ID / KADERU_PASSWORD
   ↓
2. マイページ → 予約申込一覧ページへ遷移
   ↓
3. 当月+翌月のテーブルをパース
   - 利用日時 → 日付 + 時間帯判定
   - 利用施設 → 部屋名抽出（すずらん/はまなす/あかなら/えぞまつ）
   ↓
4. 夜間(17:00-21:00)のみフィルタ → JSON出力
   ↓
[Node.js: sync-reservations.js]
5. JSONを受け取り、日付ごとに部屋をグルーピング
   - 隣室ペア判定: すずらん+はまなす→拡張ID:7, あかなら+えぞまつ→拡張ID:9
   ↓
6. DB接続 → organizations テーブルから hokudai の ID を取得
   ↓
7. 日付ごとに practice_sessions を照合:
   a. 存在しない → INSERT（venue_id, totalMatches, startTime=17:00, endTime=21:00）
   b. 単室で既存 + 隣室が予約にある → UPDATE（拡張会場に変更, capacity更新）
   c. それ以外 → スキップ
   ↓
8. 処理結果サマリーを出力
```

**関連テーブル**:

| テーブル | 操作 |
|---------|------|
| `organizations` | SELECT（hokudai の ID 取得） |
| `venues` | SELECT（defaultMatchCount, capacity 取得） |
| `practice_sessions` | SELECT / INSERT / UPDATE |

**スクリプトファイル**:

| ファイル | 説明 |
|---------|------|
| `scripts/room-checker/scrape-mypage.js` | スクレイピング専用（JSON出力） |
| `scripts/room-checker/sync-reservations.js` | DB同期（scrape-mypage.jsを子プロセスで実行） |
| `.github/workflows/sync-kaderu-reservations.yml` | 30分cron + 手動トリガー |

---

## 8. 未実装機能・TODO

### 8.1 優先度: 高

#### セキュリティ
- [ ] **Spring Security + JWT認証** 導入
  - 現在の簡易ヘッダー認証（`X-User-Role`）を本格化
  - JWTトークン発行・検証
  - リフレッシュトークン
- [ ] **BCryptパスワードハッシュ化**
  - 現在は平文比較
  - `PasswordEncoder` 導入

#### API
- [ ] **選手プロフィールAPI** 実装
  - `GET /api/players/{id}/profiles/current`
  - `GET /api/players/{id}/profiles`
  - `POST /api/players/{id}/profiles`
- [ ] **ページネーション** 導入
  - 試合一覧、選手一覧で大量データ対応

### 8.2 優先度: 中

#### 実装済み（v2.1.0）
- [x] **LINE通知機能**
  - LINE Messaging API連携（Push/Reply API）
  - チャネル管理（割り当て・回収・送信数API同期）
  - ワンタイムコードによるアカウント紐付け
  - 6種別の通知（抽選結果・キャンセル待ち・オファー期限切れ・対戦組み合わせ・リマインダー2種）
  - 管理者向け一括送信機能
  - スケジューラ3種（リマインダー/チャネル回収/送信数API同期）
  - フロントエンド画面3種（設定/チャネル管理/スケジュール設定）
  - キャンセル待ち繰り上げ通知のFlex Message対応（参加/辞退ボタン付き）
  - LINEからのpostback応答によるWebアプリ連携（LINE上で直接参加/辞退可能）

#### 機能追加
- [ ] **統計ページ拡充**
  - 選手別勝率ランキング
  - 月別試合数推移
  - 対戦相手別成績
- [ ] **試合結果CSVエクスポート**
- [ ] **練習参加率レポート**

#### UI/UX改善
- [ ] **レスポンシブデザイン** 最適化
  - タブレット対応
  - スマホ横画面対応
- [ ] **ダークモード** 対応
- [ ] **ローディング状態** 改善
  - スケルトンスクリーン導入

### 8.3 優先度: 低

#### 拡張機能
- [ ] **画像アップロード**
  - 選手プロフィール画像
  - 練習風景写真
- [ ] **試合動画アップロード** （将来）
- [ ] **SNSシェア機能**
- [ ] **多言語対応** （英語等）

#### その他
- [ ] **E2Eテスト** 導入（Cypress、Playwright等）
- [ ] **監視・ログ** システム（Prometheus、Grafana等）

---

## 9. 補足事項

### 9.1 設計上の重要ポイント

#### player1_id < player2_id 制約
- `Match` エンティティで `@PrePersist`/`@PreUpdate` により自動保証
- データベースインデックスの効率化
- クエリ簡略化

#### 論理削除
- `Player.deletedAt` により選手を論理削除
- 削除済み選手は更新・ログイン不可
- 過去の試合記録は保持（データ整合性）
- `ByeActivity.deletedAt` により抜け番活動記録を論理削除
- 一括作成時（`createBatch`）は既存レコードを論理削除してから再作成

#### 試合ごと参加登録
- `PracticeParticipant.matchNumber` により、各練習日の各試合ごとに参加登録可能
- フロントエンドで試合ごとのチェックボックスを実装
- `PracticeSessionDto.matchParticipants` で試合ごとの参加者名リスト提供

#### 自動マッチングアルゴリズム
- 過去30日の対戦履歴を基にスコアリング
- 日数が経過するほど高スコア（再対戦しやすい）
- 初対戦: スコア0（最優先）
- 同日既存対戦を除外
- シャッフル + 貪欲法で最適ペアリング

#### 管理者登録時の自動試合参加
- 管理者が練習日に参加者を登録すると、その参加者は全試合に自動参加
- 理由: 運用の簡便性を優先（初期設定の手間を削減）
- ユーザーは後から参加登録画面で試合ごとに調整可能

#### 練習セッション更新時の一意制約違反対策
- 問題: 練習セッション更新時に参加者を変更すると `Duplicate entry` エラー発生
- 原因: `deleteBySessionId()` が即座にDBに反映されず、新規レコード挿入時に既存レコードと衝突
- 解決策: `practiceParticipantRepository.flush()` を `deleteBySessionId()` 直後に実行

#### 会場マスタによる練習日管理
- 練習セッションは `venue_id` で会場を参照
- 会場には `defaultMatchCount`（デフォルト試合数）と時間割（`venue_match_schedules`）を設定可能
- 練習日作成時に会場のデフォルト値を適用可能

#### 抽選・キャンセル待ちシステム
- 定員（`practice_sessions.capacity`）超過時に自動抽選を実行
- 落選者にはキャンセル待ち番号を付与
- 当選者キャンセル時は自動繰り上げ（応答期限付き）。定員未達（キャンセル待ちなし）の場合は通知なし
- 応答期限切れ時は次の待ち番号に自動繰り上げ
- アプリ内通知 + Web Push で即座に通知

#### 伝助連携
- 伝助（出欠管理ツール）との双方向同期
  - **アプリ→伝助（イベント駆動）**: 参加者の状態変更時に `DensukeSyncService.triggerWriteAsync()` を `@Async` で即時実行。`DensukeWriteService` が `dirty=true` かつ `matchNumber IS NOT NULL` の参加者のみを対象に、dirty行に対応するスロットだけを伝助へHTTP POSTで書き込み（未入力保護: アプリに未登録のマスは送信しない）
  - **伝助→アプリ（5分スケジューラー）**: `DensukeSyncScheduler` が5分間隔で `DensukeSyncService.syncAll()` を呼び出し、JsoupによるHTMLスクレイピングで出欠情報を取得
  - **Phase3 3-A6（WAITLISTED + 伝助○）**: 当日12:00 JST以降かつ空き枠あり（WON数 < 定員）の場合、`DensukeImportService.processPhase3Maru` がWAITLISTED→WONに昇格し後続のキャンセル待ち番号を繰り上げ（dirty=false）。12:00前または空き枠なしの場合はdirty=trueにして△で書き戻す（抽選バイパス防止）
- **同期フロー集約**: `DensukeSyncService` がスケジューラー・手動同期・イベント駆動書き込みの全フローを統括
  - `syncAll()`: 当月+翌月の全団体を処理（スケジューラー用）
  - `syncForOrganization()`: 指定団体の書き込み→読み取り（Controller・スケジューラー共用）
  - `triggerWriteAsync()`: dirty参加者の即時書き込み（イベント駆動用、`@Async`）
- **参加者削除**: `removeParticipantFromMatch()` は物理削除ではなく論理削除（`status=CANCELLED`, `dirty=true`）で処理。`DensukeWriteService` が「x」（不参加）として伝助に書き戻す。`softDeleteByPlayerIdAndSessionIds` は `matchNumber IS NOT NULL` 条件でBYEを除外
- **セッション更新**: `updateSession()` は差分更新方式。既存参加者の `dirty` フラグを保持し、不要な伝助書き込みを防止。BYE（matchNumber=null）は削除ループから除外
- **未入力保護**: 通常同期ではdirty行のみ送信し、伝助上の未入力マスを×で上書きしない。BYE（matchNumber=null）は伝助の行に対応しないため常に `dirty=false` で管理し同期対象外とする。抽選確定同期（`writeAllForLotteryConfirmation`）は現行の全体書き戻し方針を維持
- 団体×月単位でURL管理（`densuke_urls`テーブル、`organization_id` でマルチ団体対応）
- メンバーID・行IDをキャッシュテーブル（`densuke_member_mappings`, `densuke_row_ids`）に保存
- スケジューラー・手動同期ともに ① 書き込み → ② 読み取り の順で実行。全団体のURLをループ処理
- **セキュリティ**: `PUT /densuke-url` は `https://densuke.biz/` ドメインのみ受付（SSRF対策）
- **認証**: `GET /densuke-url` は PLAYER 以上の認証が必要（未認証アクセス不可）
- **権限**: ADMINは自団体の伝助URLのみ操作可能。SUPER_ADMINは全団体操作可能
- **未登録者通知**: ADMINは自団体の未登録者のみ通知。SUPER_ADMINは全団体分を通知
- **キャッシュ**: `PlayerService.findAllPlayersRaw()` に Caffeine 60秒 TTL を適用（スケジューラーのDB負荷軽減）

#### Google Calendar連携
- OAuth2アクセストークンベースでGoogle Calendar APIを呼び出し
- 練習予定をカレンダーイベントとして同期（冪等）
- `google_calendar_events`テーブルでイベントIDマッピングを管理
- セッション更新時に変更検知して再同期

---

### 9.2 開発環境セットアップ

#### 前提条件
- Java 21
- Node.js 18+
- PostgreSQL 16（Render.comに接続、環境変数必須）

#### バックエンド起動
```bash
cd karuta-tracker
DB_URL="jdbc:postgresql://dpg-d6t1e77kijhs73er5ug0-a.oregon-postgres.render.com:5432/karuta_tracker_b297" \
DB_USERNAME="karuta" \
DB_PASSWORD="b1FgPgpxsqE83Z1sVoRdes2EdxTAKAal" \
./gradlew bootRun
```
→ `http://localhost:8080`

#### フロントエンド起動
```bash
cd karuta-tracker-ui
npm install
npm run dev
```
→ `http://localhost:5173`

---

### 9.3 リリースノート

#### v2.0.0（2026-03-23）
- **会場管理機能**
  - 会場マスタ（名前、デフォルト試合数）
  - 会場別試合時間割
  - 練習セッションとの連携（venue_id）
- **抽選・キャンセル待ち機能**
  - 定員超過時の自動抽選
  - キャンセル待ち（番号順管理）
  - 繰り上げ通知（応答期限付き）
  - 管理者による参加者手動編集
  - 抽選実行履歴
- **アプリ内通知**
  - 抽選結果通知（当選/落選）
  - 繰り上げ連絡・期限切れ通知
  - 未読バッジ表示
- **Web Push通知（VAPID認証）**
  - ブラウザプッシュ通知対応
  - サブスクリプション管理
- **Google Calendar連携**
  - OAuth2によるカレンダー同期
  - 冪等同期（イベントIDマッピング）
- **伝助連携**
  - 出欠情報スクレイピング・インポート
- **技術スタック更新**
  - MySQL → PostgreSQL 16（Render.comホスティング）
  - React 18 → 19、React Router v6 → v7
  - Recharts、Lucide React追加
- **新規画面**
  - ランディングページ、プライバシーポリシー、利用規約
  - 試合結果表示、一括試合結果入力
  - 会場一覧・登録、抽選結果、繰り上げ応答、キャンセル待ち状況
  - 通知一覧
- **CI/CD**
  - GitHub Actions（JUnit + Jacoco、最低カバレッジ60%）
  - Render.comデプロイ設定

#### v1.2.0（2025-11-20）
- **今日の対戦カード機能（ホーム画面）**
  - 対戦相手表示、試合結果入力連携、試合切り替えタブ
- **試合結果入力フォーム改善**
  - ホーム画面からの遷移時に自動入力
- **バグ修正**
  - 練習セッション更新時の参加者重複エラーを解消

#### v1.1.0（2025-11-19）
- **練習参加状況可視化機能**
  - カレンダーバッジ、モーダル内ハイライト
- **UI/UX改善**
  - 参加登録ボタン配置、保存後自動遷移
- **管理者機能強化**
  - 参加者全試合自動参加

#### v1.0.0（2025-11-17）
- 初回リリース
- 選手管理、試合記録、練習日管理、練習参加登録、対戦組み合わせ自動生成
- ロールベース権限管理（SUPER_ADMIN/ADMIN/PLAYER）

---

## 付録

### A. 用語集
- **選手**: システム登録済みのかるた競技者
- **試合**: 2人の選手（または選手と未登録相手）の対戦記録
- **練習日**: かるた練習会の日程
- **練習参加**: 選手が特定の練習日・試合番号に参加すること
- **対戦組み合わせ**: 特定の練習日・試合番号における選手ペアリング
- **自動マッチング**: 過去対戦履歴を考慮した最適ペアリング提案
- **会場**: 練習が行われる場所。試合時間割を持つ
- **抽選**: 定員超過時に参加者をランダムに選出する仕組み
- **キャンセル待ち**: 抽選落選者が順番待ちする仕組み
- **繰り上げ**: 当選者キャンセル時にキャンセル待ち者を参加確定にする仕組み
- **伝助**: 外部の出欠管理ツール。スクレイピングで連携

### B. データベース初期データ

#### デフォルトスーパー管理者
```sql
INSERT INTO players (name, password, gender, role, created_at, updated_at)
VALUES ('土居悠太', 'password123', '男性', 'SUPER_ADMIN', NOW(), NOW());
```

---

以上
