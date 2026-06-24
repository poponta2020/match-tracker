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
- カレンダー購読（iCalフィード方式、Google/Apple/Outlookで自動更新）
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
- biweekly 0.6.7（iCalフィード生成）
- Web Push（nl.martijndwars:web-push:5.1.1 + Bouncy Castle）
- Testcontainers（テスト用PostgreSQL）
- Apache HttpClient 4.5（会場予約プロキシの会場サイト中継）

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
- 対戦組み合わせ書き込みAPI（`MatchPairingController`）のスコープ検証は `validateScopeByDate` / `validateScopeByPairingId` で実施。SUPER_ADMIN はスコープなし、ADMIN は `adminOrganizationId` で照合、PLAYER は `OrganizationService.getPlayerOrganizationIds(currentUserId)` で取得した所属団体IDリストに対象セッション／ペアリングの組織IDが含まれているかを照合する
- 選手起点の最近ペアリング取得 `GET /api/match-pairings/player/{playerId}`（`MatchPairingService.getRecentByPlayerId` / `MatchPairingRepository.findRecentByPlayerId`）は、`@RequireRole` 全ロールの参照系で団体スコープを適用しない（閲覧は全選手可。選手別履歴の参照という用途が getByDate 等の組織限定取得と異なるため）。`(player1Id = :playerId OR player2Id = :playerId)` で `sessionDate DESC, matchNumber DESC` 順・`Pageable` で直近30件に制限し、選手名は `collectPlayerNames` で一括解決（N+1回避）。動画倉庫の登録モーダル「選手起点」で結果未入力（`match_pairings` のみ）の試合も選択肢に含めるための軽量レスポンス（`recentMatches`・試合結果は付与しない）
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
| venue_id | BIGINT | FK(venues.id) ON DELETE SET NULL | 試合が行われた会場ID（NULL可。古いデータで backfill 不可・PracticeSession 削除済みの場合は NULL） |
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
- `idx_matches_venue` (venue_id)

**特殊ロジック**:
- `@PrePersist`/`@PreUpdate`で player1_id < player2_id を自動保証
- `venue_id` は新規登録時に `MatchService.resolveVenueId()` で自動決定:
  1. 試合参加者（簡易登録は `request.playerId`、詳細登録は `player1_id` / `player2_id`）が同日・同試合番号に active 参加（`status IN ('WON','PENDING')`）した `practice_sessions` の `venue_id` を集約し、一意であれば採用（`pp.match_number IS NULL` の legacy データも含む）
  2. 同日の `practice_sessions` の `venue_id` が一意であればその値（複数会場が混在する日は NULL のまま、誤割り当てを回避）
  3. いずれにも該当しなければ NULL
- `created_by` ではなく試合参加者を基準にするのは、ADMIN 代理登録時に管理者の参加 venue が誤って入るのを防ぐため
- `match_number` でも絞るのは、同日複数会場で選手が両方に参加している場合に、対象試合と無関係の参加会場が混ざって venue_id が一意決定できないのを防ぐため
- 更新時は `venue_id` を変更しない（不変）

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
| setting_key | VARCHAR(100) | NOT NULL | 設定キー |
| setting_value | VARCHAR(255) | NOT NULL | 設定値 |
| organization_id | BIGINT | NOT NULL, FK | 団体ID |
| updated_at | DATETIME | NOT NULL | 更新日時 |
| updated_by | BIGINT | | 更新者ID |

一意制約: `(setting_key, organization_id)`

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

**ユニークインデックス**: `uq_match_pairings_date_number_players (session_date, match_number, LEAST(player1_id, player2_id), GREATEST(player1_id, player2_id))`（順不同ペアの関数ユニークインデックス。同日・同試合番号・同ペアの重複登録を防止。`match_pairings` は `matches` と異なり `player1_id < player2_id` を正規化しないため LEAST/GREATEST で順不同に正規化する。Hibernate では関数インデックスを管理できないため Render PostgreSQL へ手動適用。Issue #900）

**重複・ゾンビ組み合わせ対策**（`MatchPairingService`、Issue #900）:
- `create`: 同一（日・試合番号・順不同ペア）が既存なら重複作成せず既存を返す（冪等）。
- `createBatch`: リクエスト内の同一ペア（順不同）を初出のみ採用して重複排除し、ロック済みペアと同一の新規も除外する。フロントの組み合わせ state に同一ペアが多重蓄積したペイロードでも1行に正規化される。
- `createBatch` / `deleteByDateAndMatchNumber`: 「当日のどのセッションでも両選手が同時に参加していない」かつ結果なしのペア（ゾンビ）も削除する。組織スコープのフィルタ（`filterPairingsBySession`、両選手が同一団体セッション参加者のみ対象）から漏れるペア（両者非参加／片方だけ参加／両者が別セッション・別団体に分かれて参加）は従来は削除されず、再生成のたびに重複が累積し、フロントの一括削除でも消せなかった。ただし**組織スコープ操作では他団体データを破壊しないよう**、両選手とも「自団体セッション参加者」または「当日どのセッションの参加者でもない（誰のものでもない）」ペアに限定する（他団体参加者を含むペアは保護）。SUPER_ADMIN（organizationId=null）は団体横断で掃除する。
- `updatePlayer`: 差し替え後のペアが同日・同試合番号で既存と重複する場合は検証エラー（ユニークインデックス由来の500を回避）。

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

#### iCalフィード用カラム

`players` テーブルに以下のカラムを追加:

| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| ical_feed_token | VARCHAR(64) | NOT NULL, UNIQUE | iCalフィードURL用の推測困難な48文字トークン（pgcrypto + hex） |

`player_organizations` テーブルに以下のカラムを追加:

| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| calendar_display_name | VARCHAR(50) | NULLable | カレンダー上の団体表示名カスタマイズ（NULL なら Organization.name を使う） |

**インデックス**:
- `idx_players_ical_feed_token` UNIQUE (ical_feed_token)

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
| densuke_sd | VARCHAR(32) | | 伝助の編集用シークレット (sd)。アプリから自動作成した URL のみ値が入る。手動登録は NULL。将来の編集・削除 API で使用想定 |
| created_at | DATETIME | NOT NULL | 作成日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

**制約**:
- UNIQUE (year, month, organization_id)

---

#### densuke_templates（伝助ページ作成テンプレート）
団体ごとに1レコード保持。伝助ページ自動作成時のタイトル・説明・連絡先メアドのデフォルト値を保存する。

| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | ID |
| organization_id | BIGINT | NOT NULL, UNIQUE, FK | 団体ID（organizations.id） |
| title_template | VARCHAR(200) | NOT NULL | タイトルテンプレート（プレースホルダー `{year}`、`{month}`、`{organization_name}` を置換） |
| description | TEXT | | 伝助イベント説明文 |
| contact_email | VARCHAR(255) | | 主催者連絡先メアド（伝助の `email` フィールドに送信。控えメールの受信先） |
| created_at | DATETIME | NOT NULL | 作成日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

**制約**:
- UNIQUE (organization_id)

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
| organization_id | BIGINT | NOT NULL | 紐付ける団体ID（登録された選手はこの団体に所属。DB FK は付与せず、`InviteTokenService` で `organizations.id` の存在検証を行う） |
| expires_at | DATETIME | NOT NULL | 有効期限 |
| used_at | DATETIME | | 使用日時（SINGLE_USEのみ） |
| used_by | BIGINT | | 使用した選手ID（SINGLE_USEのみ） |
| created_by | BIGINT | NOT NULL | 発行者の選手ID |
| created_at | DATETIME | NOT NULL | 作成日時 |

**インデックス**:
- `idx_invite_tokens_token` (token) UNIQUE

---

#### match_videos（試合動画台帳）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 動画ID |
| match_date | DATE | NOT NULL | 試合日 |
| match_number | INT | NOT NULL | 試合番号（その日の何試合目か） |
| player1_id | BIGINT | NOT NULL, FK(players.id) ON DELETE RESTRICT | 選手1ID（player1_id < player2_id を保証） |
| player2_id | BIGINT | NOT NULL, FK(players.id) ON DELETE RESTRICT | 選手2ID |
| provider | VARCHAR(20) | NOT NULL, DEFAULT 'YOUTUBE' | 動画プロバイダ（現状は YOUTUBE のみ） |
| video_url | TEXT | NOT NULL | 動画URL |
| youtube_video_id | VARCHAR(20) | | YouTube動画ID（埋め込み用） |
| title | VARCHAR(255) | | 動画タイトル |
| created_by | BIGINT | NOT NULL, FK(players.id) ON DELETE RESTRICT | 登録者ID |
| updated_by | BIGINT | NOT NULL, FK(players.id) ON DELETE RESTRICT | 更新者ID |
| created_at | DATETIME | NOT NULL | 登録日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

**制約**:
- UNIQUE: `uq_match_videos_match` (match_date, match_number, player1_id, player2_id)

**インデックス**:
- `idx_match_videos_player1` (player1_id)
- `idx_match_videos_player2` (player2_id)

**特殊ロジック**:
- `matches` / `match_pairings` とは FK を持たず、`(match_date, match_number, player1_id, player2_id)` の自然キーで対応付く。これにより結果未入力（ペアリングのみ）の試合にも動画を登録でき、結果入力後に自動で試合詳細へ表示される
- `@PrePersist`/`@PreUpdate`で player1_id < player2_id を自動保証（`MatchVideo` エンティティ。選手IDのみ入れ替え）
- 動画台帳（倉庫）のページング検索 `MatchVideoRepository.search()` は選手ID・開始日・終了日を全て nullable で受け取り、null の条件は無視する。年月絞り込みは呼び出し側で年月→開始日/終了日の範囲に変換して渡す
  - nullable な `LocalDate` パラメータは、PostgreSQL JDBC の型推論で bytea と誤推論されるのを防ぐため JPQL の `CAST(:startDate AS date)` で明示的に date 型へキャストしている
  - `MatchVideoService.search()` は範囲変換前に `year`/`month` を検証する（`year` 非null時のみ）。`month` は 1〜12（`MSG_INVALID_MONTH`）、`year` は 2000〜2100（`MSG_INVALID_YEAR`）。範囲外は `IllegalArgumentException`（GlobalExceptionHandler で400）。`month` 単独指定（`year==null`）は既存挙動どおり無視する。`LocalDate.of` の `DateTimeException`→500 を防ぐため、`YearMonthRange.of` 側にも month の防御的ガードを置く（ユーザー向け400メッセージは search 側で出す）

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

#### PUT /api/players/bulk
**説明**: 複数選手の一括更新（性別・級・段位・かるた会の上書き＋所属練習会の追加）
**権限**: ADMIN+（`@RequireRole(SUPER_ADMIN, ADMIN)`）。対象選手の団体スコープ検証は行わない（トラストベースでADMINも全選手を編集可）
**リクエスト**: `PlayerBulkUpdateRequest`
```json
{
  "updates": [
    {
      "playerId": 12,
      "gender": "男性",
      "kyuRank": "E級",
      "danRank": "無段",
      "karutaClub": "北海道大学かるた会",
      "addOrganizationIds": [3]
    }
  ]
}
```
**レスポンス**: 更新後の `List<PlayerDto>`（`organizationIds` 付き）
**挙動**:
- `gender / kyuRank / danRank / karutaClub` は非 null の項目のみ `players` 列を上書き（級↔段位の整合は単体更新と同様フロントで算出）
- `addOrganizationIds` は既存に無い団体のみ `player_organizations` に追加（追加のみ・冪等。`(player_id, organization_id)` ユニーク制約で二重登録を防止）
- `@Transactional` による all-or-nothing（1件でも失敗すれば全件ロールバック）
- `"/bulk"` は `"/{id}"` より優先してマッチするため単体更新と競合しない

---

### 4.2.1 招待トークンAPI (`/api/invite-tokens`)

#### POST /api/invite-tokens?type={type}&createdBy={id}&organizationId={orgId}
**説明**: 招待トークン生成
**権限**: ADMIN+
**パラメータ**:
- `type`（MULTI_USE / SINGLE_USE）
- `createdBy`（発行者ID）
- `organizationId`（紐付ける団体ID）
  - SUPER_ADMIN: 必須。クエリで指定された団体IDが採用される
  - ADMIN: 不要。サーバ側で発行者の所属団体（`adminOrganizationId`）が自動採用され、クエリ指定値は無視される
**バリデーション**:
- `organizationId` 解決後に NULL を検証し、空の場合は 400 Bad Request を返す（ADMIN で `adminOrganizationId` 未設定の場合も同様）
- 解決された `organizationId` が `organizations` テーブルに存在しない場合は 404 Not Found を返す（`ResourceNotFoundException`）
**レスポンス**: `InviteTokenResponse`
```json
{
  "token": "550e8400-e29b-41d4-a716-446655440000",
  "type": "MULTI_USE",
  "organizationId": 1,
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

#### GET /api/matches/{id}?playerId={viewedPlayerId}
**説明**: 対戦詳細を取得
**権限**:
- `playerId` 指定なし: 認証ユーザーであればアクセス可
- `playerId` 指定あり かつ `playerId !== currentUserId`: `currentUserId` が `playerId` の ACTIVE メンターであること（非メンターは 403 Forbidden）

**レスポンス**: `MatchDto`
- `playerId` 指定あり時は、指定された選手視点で勝敗・対戦相手名が算出される（メンターがメンティーの試合を見るときに「メンティー視点で負けた」と正しく表示するため）
- メンター閲覧時はメンティーの個人メモ（`menteePersonalNotes` / `menteeOtetsukiCount`）も含まれる

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

### 4.3.1 試合動画API (`/api/match-videos`)

動画台帳（YouTube限定公開URLと試合の紐付け）の管理API。詳細フローは「7.6.1 試合動画フロー」を参照。

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| POST | `/` | ALL | 動画登録 |
| PUT | `/{id}` | ALL（登録者本人 or ADMIN+） | URL差し替え（所有者チェックはサービス層） |
| DELETE | `/{id}` | ALL（登録者本人 or ADMIN+） | 紐付け削除（物理削除） |
| GET | `/?date=` | ALL | 指定日の動画一覧 |
| GET | `/date-candidates?date=&organizationId=` | ALL | 指定日の動画登録候補（登録モーダル「日付から」用。参加日スコープなし・組織スコープあり〔pairings/matches とも対称〕。`List<MatchVideoDateCandidateDto>`） |
| GET | `/search?playerId=&year=&month=&mine=&page=&size=` | ALL | 動画倉庫の検索・ページング（`PagedResponse<MatchVideoDto>`） |

**GET `/date-candidates` レスポンス**: `List<MatchVideoDateCandidateDto>`
- 組み合わせ（`match_pairings`）+ 試合結果（`matches`）を自然キー `(matchDate, matchNumber, min(p1,p2), max(p1,p2))` で統合・重複排除（同一キーは matches 優先）し、各候補に `registered` / `hasResult` / `matchId` を付与
- **参加日スコープ（`hasSessionOnDateForUser`）は適用しない**（撮影担当・第三者登録など非参加ユーザーでも候補を選べる）。サービスは操作ユーザーIDを受け取らない
- **組織スコープは pairings / matches の両方で対称に維持**: `OrganizationScopeResolver` で effectiveOrgId を解決し、`MatchPairingService.getByDate(date, true, organizationId)`（`light=true`・未使用の `recentMatches` 取得を回避、選手名は保持）に渡す（`search` と同じ流儀）。`matches` には組織カラムがないため、`organizationId` 指定時は**選手の所属（`player_organizations`）経由でスコープ**する。判定は**実在選手（id 0/null 以外）が全員当該団体所属、かつ実在所属メンバー1名以上**の `matches` のみ候補化（所属選手ID集合を1クエリで取得して照合し N+1 を回避）。**ゲスト/未登録相手（id 0・null）は所属判定から除外**するため、所属メンバー本人 vs 未登録相手の試合も残る（相手名は `Match.opponentName` で補完）。これで pairings 側と対称になり、同日に複数団体の試合結果があっても他団体の matches-only 候補が混入しない。`organizationId` 未指定（組織非限定）の場合は `matches` を日付のみで取得しスコープしない
- **既定組織スコープ解決（当エンドポイント限定の特例）**: フロントは `organizationId` を渡さず、`OrganizationScopeResolver` は PLAYER 未指定時に `null`（非限定）を返す。そのままでは同日の他団体候補が混入し得るため、`MatchVideoController` は effectiveOrgId が `null` のとき**操作ユーザーの所属団体がちょうど1件ならその団体IDで補完**してスコープする（`resolveDefaultOrganizationIdForCandidates`）。0/複数所属または `currentUserId` が null の場合は `null` のまま（＝非限定。複数所属時の一意解決はアプリ全体の別課題）。この特例は当エンドポイント限定で、他エンドポイントの「PLAYER 未指定→null」挙動は変えない。参加日スコープとは独立しており、非参加ユーザー（撮影担当等）でも所属団体の候補は見られる
- 選手名は `players` からバッチ解決（N+1回避。matches のみスロットの名前解決に使用）。`player1Id`/`player2Id` は正規化後（p1<p2）の生IDをそのまま返す

**POST リクエスト**: `MatchVideoCreateRequest`
```json
{
  "matchDate": "2026-06-12",
  "matchNumber": 1,
  "player1Id": 1,
  "player2Id": 2,
  "videoUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
}
```

**レスポンス**: `MatchVideoDto`
- 選手名（`player1Name` / `player2Name`）は players テーブルから解決
- `matchId` / `winnerId` / `scoreDifference` は同一自然キーの試合結果（`matches`）が存在する場合のみ設定（結果未入力なら null）

**エラー**: 400「YouTubeのURLを入力してください」/ 404「対象の試合が見つかりません」/ 409「この試合には既に動画が登録されています」/ 403（編集・削除の権限なし）

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

#### 対戦履歴一覧（MatchList）での読み・一人取り表示（フロント設計）

対戦履歴一覧画面 `MatchList.jsx` は、`GET /api/matches/player/{id}`（試合）と `GET /api/bye-activities/player/{id}`（抜け番）を併用し、選手が**読み（READING）・一人取り（SOLO_PICK）** を行った回を試合行と同じリストに統合表示する（他の活動種別は対象外）。

- **取得**: 抜け番は `type` 指定なしで全件取得し、フロントで READING / SOLO_PICK のみに絞る。試合一覧（`matches`）・統計と同じ `Promise.all` 内で同時取得し、失敗時は空配列でフォールバック（試合は表示）。
- **マージ/並び**: 試合行と抜け番行を統合し `matchDate DESC, matchNumber DESC` で並べ、該当試合番号の位置に差し込む。
- **会場名**: `bye_activities` は会場を持たないため、同日の試合（`matches`）の `venueName` を借用する。同日に試合がない日は会場名を省略し「N試合目 活動名」と表示する。
- **期間フィルタ候補**: 年/月セレクトの候補（`availableYears` / `availableMonths`）は試合の `matchDate` と抜け番の `sessionDate` の**両方**から生成する（試合がなく抜け番のみの年月にも期間フィルタで到達できるようにするため）。選択中月の補正も試合＋抜け番の合算月で判定し、抜け番のみの月を選んでも試合月へ戻さない。
- **フィルタ連動**: 期間（年/月）フィルタは抜け番行・回数表示の両方に常に連動。結果（勝ち/負け）・対戦相手名検索・級/性別/利き手フィルタが有効なときは抜け番行を非表示にする（対戦相手前提のフィルタのため）。
- **回数表示**: 統計エリアに期間内の「読み n回 ・ 一人取り m回」を活動別に併記（0回は非表示、勝敗統計には不算入）。

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

#### GET /api/practice-sessions/year-month/summary?year={year}&month={month}
**説明**: 年月別練習日サマリー取得（カレンダー画面向け軽量レスポンス）
**権限**: なし

**特記事項**:
- 参加者詳細（試合別参加者リスト、ランク・ロール）まではエンリッチせず、会場名と試合別定員到達状況のみ付与
- 認証済みユーザーがある場合（リクエスト属性 `currentUserId`）は当該プレイヤーの所属団体で絞り込む。`playerId` クエリパラメータは受け付けない
- 月内全セッションの参加者を一括取得して集計するため N+1 にならない

**レスポンス**: `List<PracticeSessionDto>`（サマリー版）

| フィールド | 型 | 説明 |
|-----------|-----|------|
| `id` | Long | 練習セッションID |
| `sessionDate` | LocalDate | 練習日 |
| `totalMatches` | Integer | 総試合数 |
| `venueId` | Long | 会場ID |
| `venueName` | String | 会場名（サマリーAPIで付与） |
| `capacity` | Integer | 定員（試合別の上限人数） |
| `organizationId` | Long | 団体ID |
| `matchCapacityStatuses` | `List<String enum>` | 試合単位の定員到達状況。要素 enum は `AVAILABLE` / `NEARLY_FULL` / `FULL`。`matchCapacityStatuses[i]` が第 `(i+1)` 試合の状態。長さは `min(totalMatches, 9)`。**サマリーAPIのみで返却**され、他のエンドポイント（`getById` / `getByDate` 等）では設定されない。算出不可時は `null` |

**`matchCapacityStatuses` の判定ロジック:**

- 判定に使う `capacity` は `session.capacity` を優先し、`session.capacity == null` のときは紐づく `venue.capacity`（venue 既定 capacity）にフォールバックする。これにより、伝助同期で作成された capacity 未設定セッションでもサマリーAPIがグリッドを返せるようになる（`LotteryService` の `processSession` 既存フォールバックと同じ思想で、表示側でも防御を二重化）。
- 算出スキップ（`matchCapacityStatuses = null`）の条件:
  - フォールバック適用後の有効 `capacity` が `null` または `0` 以下（`session.capacity == null` の場合のみ `venue.capacity` にフォールバックする。`session.capacity = 0` のような明示値は venue 既定値で上書きせず、そのままスキップ判定に流す）
  - `totalMatches == null || totalMatches <= 0 || totalMatches >= 10`
  - 集計中に例外発生
- 上記以外: 試合番号 1〜`totalMatches` の各試合について以下を算出:
  - 実質枠取得人数 `effectiveCount` = `COUNT(WON) + COUNT(PENDING) + COUNT(OFFERED)`（試合番号別）
    - `WAITLISTED` / `DECLINED` / `CANCELLED` / `WAITLIST_DECLINED` はカウントに含めない
  - 残り席数 `remaining` = `capacity - effectiveCount`
  - 状態判定:
    1. `effectiveCount >= capacity` （= `remaining <= 0`）→ `FULL`
    2. `0 < remaining <= 2` → `NEARLY_FULL`
    3. それ以外（= `remaining > 2`）→ `AVAILABLE`

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
**説明**: 月別参加状況取得（アクティブな参加レコードのみ。`CANCELLED`/`DECLINED`/`WAITLIST_DECLINED` は除外し、キャンセル後の再登録判定に使えるようにする）
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
**説明**: セッション再抽選。リクエストボディ省略時は直近実行時の `priorityPlayerIds` を引き継ぐ。`priorityPlayerIds: []` で明示クリア可能。組織スコープを検証し、ADMIN/PLAYER は所属団体のセッション以外を再抽選できない（404 を返す）
**権限**: SUPER_ADMIN, ADMIN
**リクエスト** (任意):
```json
{ "priorityPlayerIds": [10, 20] }
```
**レスポンス**: `LotteryExecution`

#### GET /api/lottery/results?year={year}&month={month}
**説明**: 月別抽選結果取得。ADMIN/PLAYER は自分の所属団体のセッションのみが対象（SUPER_ADMIN は全団体）
**権限**: SUPER_ADMIN, ADMIN, PLAYER
**レスポンス**: `List<LotteryResultDto>`

#### GET /api/lottery/results/{sessionId}
**説明**: セッション別抽選結果取得。ADMIN/PLAYER は所属団体のセッション以外にアクセスすると 403 を返す（SUPER_ADMIN は全団体）
**権限**: SUPER_ADMIN, ADMIN, PLAYER

#### GET /api/lottery/my-results?year={year}&month={month}
**説明**: 自分の抽選結果取得（ログインユーザーの結果のみ）。ADMIN/PLAYER は所属団体のセッションに紐づく結果のみ返す
**権限**: SUPER_ADMIN, ADMIN, PLAYER

#### POST /api/lottery/cancel
**説明**: 参加キャンセル（理由付き・複数対応）。PLAYERは自分の参加のみキャンセル可能かつ過去日のキャンセル不可、ADMIN+は他人分・過去日も可。
受け付けるステータスは `WON`（当選後キャンセル）と `PENDING`（抽選前申込のキャンセル）。`PENDING` の場合は繰り上げ・当日補充フローは発動しない（待機者が存在しないため）。
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

#### GET /api/lottery/is-confirmed?year={year}&month={month}&organizationId={organizationId}
**説明**: 指定年月・団体の抽選が確定済みかどうかを返す（ADMINは自団体に強制）
**権限**: SUPER_ADMIN, ADMIN
**レスポンス**:
```json
{ "confirmed": true }
```

#### GET /api/lottery/notify-status?year={year}&month={month}&organizationId={organizationId}
**説明**: 抽選結果通知の送信済みチェック（ADMINは自団体に強制）。対象月・団体の練習セッションIDを引き当て、`LOTTERY_WAITLISTED` / `LOTTERY_ALL_WON` / `LOTTERY_REMAINING_WON` 通知のうち `referenceId` が該当セッションに紐づくレコード数を返す
**権限**: SUPER_ADMIN, ADMIN
**レスポンス**:
```json
{ "sent": true, "sentCount": 24 }
```

#### POST /api/lottery/notify-results
**説明**: 抽選結果通知の統合送信（アプリ内通知 + LINE通知を一括送信）。ADMINは自団体に強制
**権限**: SUPER_ADMIN, ADMIN
**リクエスト**:
```json
{ "year": 2026, "month": 4, "organizationId": 1 }
```
**レスポンス**:
```json
{ "inAppCount": 24, "lineSent": 20, "lineFailed": 0, "lineSkipped": 4 }
```

#### POST /api/lottery/decline-waitlist
**説明**: キャンセル待ち辞退（セッション単位）。辞退後、後続のキャンセル待ち番号を自動繰り上げ。
**権限**: SUPER_ADMIN, ADMIN, PLAYER（PLAYERは自分のみ。ADMINは自団体セッションのみ — `AdminScopeValidator` で検証、不一致は 403）
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
**権限**: SUPER_ADMIN, ADMIN, PLAYER（PLAYERは自分のみ。ADMINは自団体セッションのみ — `AdminScopeValidator` で検証、不一致は 403）
**リクエスト**:
```json
{ "sessionId": 100, "playerId": 10 }
```
**レスポンス**:
```json
{ "rejoinedCount": 2, "message": "キャンセル待ちに復帰しました（2件）" }
```

#### PUT /api/lottery/admin/edit-participants
**説明**: 管理者による参加者手動編集。WON→CANCELLED へのステータス変更時は通常キャンセル経路（`/api/lottery/cancel`）の `cancelParticipationSuppressed` に委譲し、当日12:00を境界に通常繰り上げ／当日補充フローへ自動分岐する。
**権限**: SUPER_ADMIN, ADMIN
**リクエスト**: `AdminEditParticipantsRequest`

#### POST /api/lottery/confirm
**説明**: 抽選結果を確定し、伝助への一括書き戻しをトリガー。`confirmed_at`/`confirmed_by`/`priority_player_ids`/`seed` を記録。団体単位で確定状態を管理。伝助書き戻しは別トランザクション（REQUIRES_NEW）で実行され、書き戻しが失敗しても抽選確定の DB 更新は維持される
**権限**: SUPER_ADMIN, ADMIN（ADMINは自団体のみ）
**リクエスト**: `LotteryExecutionRequest`
```json
{ "year": 2026, "month": 5, "organizationId": 1, "seed": 12345, "priorityPlayerIds": [10, 20] }
```
**レスポンス**: `ConfirmLotteryResponse`
```json
{
  "execution": { "...": "LotteryExecution の全フィールド" },
  "densukeWriteSucceeded": true,
  "densukeWriteError": null
}
```
- `densukeWriteSucceeded`: 伝助書き戻しが全件成功した場合 true。部分失敗・例外発生時は false
- `densukeWriteError`: 失敗内容の概要（成功時は null）。フロント側ではこの値を確認して伝助同期の再実行を促す

#### POST /api/lottery/preview
**説明**: 抽選プレビュー。抽選アルゴリズムを実行するがDBには保存しない。締め切り前チェック・確定済みチェック・AdminScopeValidation・priorityPlayerIdsバリデーションあり
**権限**: SUPER_ADMIN, ADMIN（ADMINは自団体のみ）
**リクエスト**: `LotteryExecutionRequest`
```json
{ "year": 2026, "month": 5, "organizationId": 1, "priorityPlayerIds": [10, 20] }
```
**レスポンス**: `List<LotteryResultDto>`

#### GET /api/lottery/monthly-applicants
**説明**: 対象月・団体で参加希望を出している選手一覧を取得（優先選手指定UI用）。重複排除・級順ソート済み
**権限**: SUPER_ADMIN, ADMIN（ADMINは自団体のみ）
**クエリパラメータ**: `year`, `month`, `organizationId`
**レスポンス**: `List<MonthlyApplicantDto>` (`playerId`, `name`)

#### POST /api/lottery/notify-waitlisted
**説明**: キャンセル待ち（WAITLISTED）の参加者のみにアプリ内通知 + LINE通知を送信。ADMINは自団体に強制
**権限**: SUPER_ADMIN, ADMIN（ADMINは自団体のみ）
**リクエスト**: `{ year, month, organizationId }`
**レスポンス**: `{ inAppCount, lineSent, lineFailed, lineSkipped }`

#### GET /api/lottery/executions?year={year}&month={month}
**説明**: 抽選実行履歴取得。`confirmedAt` フィールドで確定状態を確認可能。ADMIN/PLAYER は自分の所属団体の履歴のみ返す（SUPER_ADMIN は全団体）
**権限**: SUPER_ADMIN, ADMIN, PLAYER

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

### 4.10 カレンダー購読 API（iCalフィード）

#### GET /ical/calendar/{token}/org/{orgId}.ics
**説明**: 所属団体ごとのiCalフィードを配信。Googleカレンダー等の購読クライアントから直接取得される。
**権限**: 認証なし（推測困難なtokenが事実上の認証）
**404条件**: token無効、論理削除済みプレイヤー、プレイヤーがorgIdに所属していない
**レスポンス**: `Content-Type: text/calendar;charset=UTF-8`、VCALENDAR の X-WR-CALNAME に該当団体の表示名

#### GET /ical/calendar/{token}/guest.ics
**説明**: ゲスト参加（プレイヤーが所属していない団体の練習）専用のiCalフィード
**権限**: 認証なし
**404条件**: token無効、論理削除済みプレイヤー
**レスポンス**: `Content-Type: text/calendar;charset=UTF-8`、VCALENDAR の X-WR-CALNAME は `"ゲスト参加"` 固定

#### GET /api/calendar/feed/info
**説明**: 所属団体ごとのフィードURL一覧 + ゲスト参加URL を取得
**権限**: ALL（自分自身のリソースのみ）
**レスポンス**: `FeedInfoDto`
```json
{
  "organizationFeeds": [
    { "organizationId": 1, "organizationName": "早稲田カルタ会", "displayName": "わすら", "url": "https://.../ical/calendar/{token}/org/1.ics" }
  ],
  "guestFeed": { "url": "https://.../ical/calendar/{token}/guest.ics" }
}
```

#### POST /api/calendar/feed/regenerate
**説明**: iCalフィードトークンを再発行（**所属団体URL・ゲストURL すべてが一斉に無効**）
**権限**: ALL
**レスポンス**: `FeedInfoDto` (再発行後の状態)

#### PATCH /api/calendar/feed/display-names
**説明**: 所属団体ごとの表示名（カレンダー上の団体名）を一括更新
**権限**: ALL
**リクエスト**: `{ "displayNames": { "<organizationId>": "<表示名>" | null } }`
**レスポンス**: `FeedInfoDto` (更新後の状態)

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
**クエリパラメータ**: `organizationId`（任意。SUPER_ADMINが団体別設定を取得する場合に指定。ADMINは自団体に固定）

#### GET /api/system-settings/{key}
**説明**: 設定値取得
**権限**: なし

#### PUT /api/system-settings/{key}
**説明**: 設定値更新
**権限**: SUPER_ADMIN, ADMIN
**リクエスト**:
```json
{
  "value": "3",
  "organizationId": "1"
}
```
`organizationId` は SUPER_ADMIN が団体別設定を更新する場合に必須。ADMINはリクエスト値に関わらず自団体に固定される。

**利用可能な設定キー**:
| キー | 説明 | デフォルト値 |
|------|------|-------------|
| `lottery_deadline_days_before` | 締切日数（月初から何日前） | `0` |
| `lottery_normal_reserve_percent` | 一般枠の最低保証割合（%） | `30` |

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
| 試合一覧 | /matches | 全員 | 試合一覧。各行は CSS Grid 6 列（`grid-cols-[2rem_6.125rem_2.5rem_minmax(0,1fr)_1.5rem_2rem]` で `[日付] [対戦相手名] [勝敗] [会場 N試合目] [メモアイコン] [手N]`）で列揃え。対戦相手名は全角 7 文字分（`text-sm` × 7 = 6.125rem = 98px）固定、会場列が残り幅を受け取る。メモアイコン・お手付きは非表示行でも `invisible` プレースホルダで列幅を確保 |
| 試合登録・編集 | /matches/new, /matches/:id/edit | 全員 | 試合登録・更新 |
| 試合詳細 | /matches/:id | 全員 | 試合詳細表示（試合結果・詳細情報・メモを1つの統合カードで表示。コメント欄は閲覧者種別に応じた表示条件あり。詳細は「7.6 メンター指名・コメントフロー」参照） |
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
| 抽選管理 | /admin/lottery | ADMIN+ | 抽選プレビュー実行→結果確認→確定→通知送信の一連のワークフロー。プレビュー／確定済の両フェーズで LINE 告知用コピーテキスト領域を表示（プレビュー時は警告色）。システム設定へのリンクあり |
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
  - 出欠登録モーダル → 参加登録画面 / キャンセル登録画面（いずれも年月を引き継ぐ）
  - 練習日編集（SUPER_ADMINのみ）

#### 練習関連の導線フロー
```
ホーム画面（/）
  ↓ 「練習記録」クリック
カレンダー画面（/practice）
  ├─ 「出欠登録」ボタン（右下フローティング、過去月のときは非表示）
  │     ↓
  │   出欠登録モーダル（AttendanceRegisterModal）
  │     ├─ 「参加登録」 → /practice/participation?year=YYYY&month=M
  │     │                   ↓ 保存 → SaveProgressOverlay（保存中／完了／エラー）
  │     │                   └→ 「カレンダーに戻る」ボタン押下で /practice へ遷移（データ再取得）
  │     ├─ 「キャンセル登録」 → /practice/cancel?year=YYYY&month=M
  │     │   （当月扱いの月でのみ表示。来月扱いの月では非表示）
  │     │                   ↓ キャンセル実行 → SaveProgressOverlay（キャンセル処理中／完了／エラー）
  │     │                   └→ 「カレンダーに戻る」ボタン押下で /practice へ遷移
  │     └─ 「閉じる」 → モーダルを閉じる（遷移なし）
  │
  ├─ 日付クリック → 選択セッション詳細モーダル表示
  │   └─ 「出欠登録」ボタン（過去日でない場合のみ表示） → 出欠登録モーダル
  │
  └─ 「編集」ボタン（SUPER_ADMINのみ） → 練習日編集画面
```

---

### 5.3 主要画面設計

#### 5.3.-1 共通トップバー（PageHeader コンポーネント）

独自トップバー（NavigationBar 等）を持たない画面では、共通コンポーネント `PageHeader`（`karuta-tracker-ui/src/components/PageHeader.jsx`）が画面最上部に固定表示される。

**責務**:
- `Layout.jsx` のベースナビバー（`bg-[#4a6b5a]` の空の緑バー、`z-40`、ローディング中のフォールバック）の上に `z-50` で重ねて表示し、各画面に「タイトル」と「明示的な戻る導線」を提供する
- 該当画面の本文先頭にあった H1（および付随アイコン）はトップバーに集約され、本文側からは削除される

**Props**:
| 名前 | 型 | 必須 | 説明 |
|---|---|---|---|
| `title` | `string` | ○ | トップバー中央に表示するタイトル（`<h1>` 要素として描画） |
| `backTo` | `string` | ○ | 戻るボタン押下時の遷移先パス（`useNavigate()(backTo)` で明示遷移）|
| `rightActions` | `ReactNode` | × | トップバー右端に配置する追加要素（例: 「すべて削除」「システム設定」） |

**戻り先のグルーピング**（ディープリンク・リロード後も一貫した戻り先を保証するため、`navigate(-1)` ではなく明示的な `backTo` を採用）:
- 設定グリッドから入る画面 → `/settings`（プロフィール、参加練習会、通知設定、メンター管理、カレンダー購読、組み合わせ作成、会場管理、抽選管理、LINEチャネル管理、LINE通知スケジュール）
- 抽選管理から入る画面 → `/admin/lottery`（システム設定）。設定グリッドに「システム設定」のメニュー項目はなく、`LotteryManagement` 画面右上の `rightActions` ボタンから到達する画面のため、戻る先は抽選管理に揃える
- リスト → 詳細・編集 → 親リスト（試合詳細、練習詳細、選手新規/編集、会場新規/編集、札ルール一覧）
- ホーム導線 → `/`（通知一覧、抽選結果、キャンセル待ち状況、繰り上げ参加のご連絡）

**Z-index 重なり順**:
- `PageHeader`（`z-50`、固定トップバー） > `Layout` ベースナビバー（`z-40`、空のフォールバック） > 本文

**動的タイトル / 動的 backTo**:
- 同じコンポーネントが新規/編集で使い回されるページ（`PlayerEdit`, `VenueForm`）は `id` / `isEditMode` から動的に切り替える

**ローディング・エラー画面**:
- `if (loading) return <LoadingScreen />` のような早期 return でも `<><PageHeader ... /><LoadingScreen /></>` で包み、空のベースナビバーが見えないようにする

**詳細仕様・要件**: `docs/features/subpage-topbar-title/requirements.md`

---

#### 5.3.0 ホーム画面（ダッシュボード）
**パス**: `/`

**表示内容**:
- **ナビゲーションバー**: 選手名、プロフィールアイコン → `/profile` に遷移
- **繰り上げオファーバナー**: 未応答の繰り上げ参加通知がある場合に表示。タップで通知一覧に遷移
- **次の練習（NEXT / TODAY）**:
  - 次回参加予定の練習日・時間・会場・参加試合番号
  - 当日の場合は `TODAY` バッジ表示、全ロールに「組み合わせを作成」ボタン
  - 参加者一覧（段位順ソート、自分はハイライト、キャンセル待ちは別セクション）
- **参加率TOP3（団体別フィルタリング）**:
  - 当月の参加率上位3名 + 自分がTOP3に入っていない場合は自分の参加率も表示
  - ユーザーの所属団体（organization）でフィルタリング
  - **1団体所属時**: 団体名ラベルなしで1セクション
  - **複数団体所属時**: 「全体」（全所属団体合算）→ 団体A → 団体B の順で複数セクション表示
  - レスポンス構造: `participationGroups` 配列（各要素に `organizationId`, `organizationName`, `top3`, `myRate`）
  - **参加率の算出**: 分子は有効参加（`WON`/`PENDING`）の試合数のみ（`CANCELLED`/`DECLINED`/`WAITLISTED`/`OFFERED`/`WAITLIST_DECLINED` は除外）。抜け番（非ABSENT、`matchNumber=null`）も参加に含むが、各セッションで予定試合数（`totalMatches`）を上限にキャップしてから合算するため参加率は100%を超えない。分母は当日以前の各セッションの `totalMatches` 合計。算出ロジックは `PracticeParticipantService.buildParticipationRates`

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
- 「組み合わせを作成」ボタン（全ロール、当日のみ） → `/pairings?date={sessionDate}`

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
- **出欠登録ボタン（右下フローティング、過去月は非表示）**: 押下で `AttendanceRegisterModal` を開く。モーダル内で「参加登録」「キャンセル登録」を選択し、それぞれ `?year=YYYY&month=M` 付きで該当ページへ遷移。旧仕様の「参加登録」「参加キャンセル」2フローティング構成は本モーダル統合で「出欠登録」1ボタンに集約された。**カレンダー表示月の判定により FAB の表示／モーダル内のボタン構成が変わる**: 過去月（表示月 < 現在年月）→ FAB 非表示／当月扱い（現在年月、または未来月で抽選確定済みセッションが1つ以上）→ FAB 表示・モーダルに「参加登録」「キャンセル登録」両方表示／来月扱い（未来月で抽選確定済みセッションが0個）→ FAB 表示・モーダルに「参加登録」のみ表示。判定は `pages/practice/utils/attendanceMode.js` の `resolveAttendanceMode` ヘルパーで共通化され、データソースは月変更時に既に取得している `practiceAPI.getPlayerParticipationStatus(playerId, year, month)` のレスポンス内 `lotteryExecuted: { sessionId → boolean }`
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
- 出欠登録ボタン（選択セッション詳細モーダル内、過去日でない場合のみ表示）: 押下で `AttendanceRegisterModal` を開き、参加登録 / キャンセル登録のいずれにも年月を引き継いで遷移可能
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
- **年月ナビゲーション**: 固定ヘッダーに左右矢印ボタン（ChevronLeft / ChevronRight）で月を切り替え。カレンダー画面の出欠登録モーダルから遷移時は `?year=YYYY&month=M` クエリパラメータで初期表示月が引き継がれる（不正値時は現在月にフォールバック、後方互換あり）
- **練習日一覧（テーブル形式）**
  - 日付列
  - 場所列
  - 団体名バッジ: 各セッションの所属団体を略称（例: わすら、北大）で色付きバッジ表示。色は団体の `color` フィールドから取得
  - 試合番号チェックボックス列（1～7）
    - 既存参加状況を反映してチェック済み
    - チェックボックス周囲のラベル領域もタップ対象にし、スマホ幅でも選択しやすいタップ領域を確保
    - **参加人数バッジ**: 各試合の参加者数を表示。定員に対する割合で色分け（赤: 80%以上、橙: 60%、黄: 40%、緑: 40%未満）
  - **抽選ステータス表示**（抽選実行済みセッション）: チェックボックスの代わりにステータスバッジを表示
    - WON（緑）、WAITLISTED（黄・番号付き）、OFFERED（青）、PENDING（灰）、DECLINED/CANCELLED（灰）
  - **既存登録チェック外しの可否（「当月扱い／来月扱い」で切り替え）**: `resolveAttendanceMode(year, month, lotteryExecuted)` の結果に応じて挙動を切り替える
    - **当月扱い**（現在年月、または未来月で抽選確定済みセッションが1つ以上ある月）: 既存登録（`initialParticipations` に含まれる試合）のチェックボックスを一律無効化（解除不可）。既存登録のキャンセルはキャンセル画面（`/practice/cancel`）の理由付きキャンセルに誘導する
    - **来月扱い**（未来月で抽選確定済みセッションが0個の月）: 既存登録もチェックを外せる（API上は未登録に戻す＝理由なしキャンセル）。`registerParticipations` 保存時に差分が反映される
    - **抽選実行済みセッション**: 当月扱い／来月扱いに関わらず全チェックボックスが操作不可（ステータスバッジ表示）
    - **締切後**: 既存仕様どおり、`beforeDeadline=false` のとき既存登録は disabled（来月扱いの月でも維持）
- **SAME_DAYタイプ確認ダイアログ**: SAME_DAYタイプの団体で当日12:00以降、かつ「当日のセッション」の参加状態に初期値からの実際の差分があるときのみ、管理者への連絡確認ダイアログを表示する。同月内の別日セッションだけを変更した保存では当日セッションは触られないためダイアログは出さない。判定ロジックは `karuta-tracker-ui/src/pages/practice/utils/sameDayConfirm.js` の `needsSameDayConfirm` に切り出され、単体テストでカバー
- **保存ボタン**: 押下後、API 呼び出し直前から共通コンポーネント `SaveProgressOverlay` で全画面オーバーレイ（保存中／完了／エラー）を表示する。完了画面の「カレンダーに戻る」ボタンを押下したときのみ `/practice` に遷移する（旧仕様の1秒タイマー自動遷移は廃止）。エラー時は「閉じる」で編集中のチェック状態を維持したまま元画面に戻り再試行できる。Esc キー・背景クリックではオーバーレイは閉じない。同じオーバーレイは PracticeCancelPage のキャンセル実行フロー（旧 `alert` 通知の置き換え）にも使用される

**データフロー**:
1. ページ読み込み時に以下のAPIを並列取得:
   - `GET /api/practice-sessions/year-month` — 月内の練習セッション一覧
   - `GET /api/practice-sessions/participations/player/{playerId}` — 既存参加登録
   - `GET /api/practice-sessions/participations/player/{playerId}/status` — 抽選・締切ステータス（参加状況詳細、抽選実行有無、締切前後）
   - `GET /api/lottery/deadline` — 締切情報の表示用
   - `GET /api/organizations` — 団体名・色・締切タイプ
2. 取得データをもとにテーブルを描画（チェックボックス or ステータスバッジ）
3. チェックボックス操作
4. 保存ボタン → API 呼び出し直前で `SaveProgressOverlay` を `saving` 状態に切替え → `POST /api/practice-sessions/participations`（PLAYERロールは自分のplayerIdのみ操作可能）
5. 成功時: オーバーレイを `success` 状態に切替え（「参加登録を保存しました」と「カレンダーに戻る」ボタン）。失敗時: `error` 状態に切替え、サーバーからのエラーメッセージ（`err.response?.data?.message`）を表示
6. ユーザーが「カレンダーに戻る」を押下 → `/practice`（カレンダー画面）へ遷移（エラー時は「閉じる」で編集中のチェック状態を保持したまま画面に戻り再試行可）
7. カレンダー画面で自動的にデータ再取得

---

#### 5.3.3 対戦組み合わせ
**パス**: `/pairings`

**表示内容**:
- 日付選択
- 試合番号選択（1～7）
- 参加者一覧（チェックボックス）
- 「自動マッチング」ボタン → `POST /api/match-pairings/auto-match`
- 提案されたペア一覧（ドラッグ&ドロップ / タップ選択対応）
  - 選手カード（DraggablePlayerChip）を長押し/クリックでドラッグして入れ替え
  - 選手カードをシングルタップで選択 → 別カード/空き枠/待機/新規ペアゾーンをタップして配置（タップ選択モード）
  - 最近の対戦履歴（日付、何日前）
- 手動調整: 選手カード同士のスワップ、待機リストとの入れ替え
- 新規ペアリング作成ドロップゾーン（待機選手をドロップ/タップして新規行作成、待機選手選択時のみ表示）
- 「組み合わせ確定」ボタン → `POST /api/match-pairings/batch`（片方空欄時は無効化）
- 待機者リスト（DroppableSlot、選手はDraggablePlayerChip）

**タップ選択モードの state 設計**:
- `selectedPlayer`: `{ playerId, playerName, source }` 形式（`source` は `DraggablePlayerChip.data.source` と同形）
- `handleChipClick` / `handleSlotClick` でクリック発火、`executePlacement(dest)` 共通関数で `computeDragResult` 呼出し〜state 更新〜`fetchPairHistory` 発火までを実行
- `handleDragStart` 冒頭で `setSelectedPlayer(null)` を呼び、D&D との状態不整合を防止
- `selectedPlayer` が非 null の時のみ `document` クリックリスナーを張り、チップ/スロット以外のクリックで選択解除（チップ/スロット側は `e.stopPropagation()` で伝播停止）
- 編集モード時のみ有効（`isReadOnly` / `isViewMode` / `hasResult` 時は早期 return）

**アルゴリズム**:
- 過去30日の対戦履歴取得
- スコア = -(100 / 最終対戦日からの日数)
- 初対戦: スコア0（優先）
- 1日前: -100点、2日前: -50点、7日前: -14点
- 貪欲法で最適ペアリング

##### 札ルール一覧（PairingSummary） — 札ルールの日付別永続化

**パス**: `/pairings/summary?date=YYYY-MM-DD`

**目的**: 同一日内であれば対戦組み合わせを何度作り直しても「札ルール一覧」画面に表示される札ルール（一の位／十の位／抜き）が変わらないようにする（LINE 再配信時の整合性確保）。

**localStorage キー設計**:
- キー名: `karuta-tracker:card-rules:<YYYY-MM-DD>`（例: `karuta-tracker:card-rules:2026-06-09`）
- `karuta-tracker:` プレフィックスは既存 localStorage（認証関連）との衝突回避
- 値: `JSON.stringify(cardRules)` — `generateCardRules()` の戻り値配列をそのままシリアライズ

**画面ロード時の処理順**（`useEffect` 内）:
1. `cleanupOldCardRules()` で「クライアント端末ローカルタイムの今日」と一致しない日付の保存値をまとめて削除（過去日にアクセスしても保存しない）
2. 対戦データを `pairingAPI.getByDateAndMatchNumber` で全試合分取得。あわせて `byeActivityAPI.getByDate(date)` で抜け番活動を取得し、`activityType = READING`（読み）のものを「試合番号→読手名」マップ（`readersByMatch`）に集約（取得失敗時は読手なしで継続）
3. `loadCardRules(date)` で復元を試行
4. 復元成功時: `reconcileCardRules(stored, totalMatches)` で試合数差分を吸収（`changed: true` のときのみ `saveCardRules` で上書き）
5. 復元失敗時: `generateCardRules(totalMatches)` で新規生成し `saveCardRules` で保存
6. テキストは最新の対戦データ＋札ルール＋`readersByMatch` から `generateText` で再生成（対戦変更は自動反映）。各試合の `{N}試合目` 行直後に読手がいれば `【読手：○○】`（複数は「、」区切り）を出力

**試合数不一致のフォールバック**（`reconcileCardRules`）:
- 一致: そのまま
- 保存が短い（試合数が増えた）: `generateCardRules(totalMatches, stored)` で末尾追加 → 上書き
- 保存が長い（試合数が減った）: 先頭 `totalMatches` 件のみ表示用に返却し、localStorage 側は保持（試合数が戻れば再利用可能）

**`generateCardRules(totalMatches, prefix = [])` の続行ロジック**:
- `prefix` 空: 現状どおり1試合目から3試合サイクル（1の位→抜き→十の位）でランダム生成
- `prefix` 非空: 末尾ルールの `digits` から `prevUsedDigits` / `prevUnusedDigits` を復元し、`prefix.length` から続きを生成して `prefix.concat(extra)` を返す

**「札を再生成」ボタン**:
- 押下時 `window.confirm('現在の札ルールを上書きして再生成します。よろしいですか？')` を表示
- OK のみ `generateCardRules` で再生成 → `saveCardRules` で上書き
- キャンセル時は何も変化しない（誤タップで初回配信した札ルールを失うリスクを防ぐ）

**例外処理**:
- localStorage パース失敗・配列でない場合: `loadCardRules` が `null` 返却 → 新規生成にフォールバック
- localStorage 利用不可（SecurityError／QuotaExceededError 等）: `saveCardRules` で try-catch 握り、保存スキップ（毎回ランダム生成にフォールバック）

**設計判断**:
- DB ではなく localStorage に保存: 運用は「同一管理者・同一端末・PWA ホーム画面追加」、別端末同期は不要のため、テーブル設計・本番マイグレーション・API 追加のコストに見合わない
- 削除タイミングは画面ロード時のみ: 専用スケジューラやサービスワーカーは導入しない（シンプルさ優先）
- 保存対象は札ルールのみ（テキスト全体は保存しない）: テキストは画面ロード時に最新の対戦データから再生成することで対戦変更が自動反映される

**詳細仕様・要件**: `docs/features/pairing-card-rule-persistence/requirements.md`

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

**全ユーザー共通の表示内容**:
- 月ナビゲーション
- セッション別の抽選結果
  - 当選者リスト（WON）
  - キャンセル待ちリスト（WAITLISTED、番号順）
  - 各参加者のステータス表示（WAITLIST_DECLINED バッジを含む）
- 自分のキャンセル待ちセッションに対する辞退/復帰ボタン

**ADMIN / SUPER_ADMIN 向けの追加表示**:
- 抽選確定済の月にのみ表示する LINE告知用テキスト領域
  - 抽選落ち（キャンセル待ち）のみを整形した文面を初期値として持つ編集可能 textarea
  - コピーボタンで `navigator.clipboard.writeText` を実行（キャンセル待ち0件の月はボタンを無効化）
  - 整形ロジックは `lotteryResultText.js` に切り出し（`buildCopyText` / `hasAnyWaitlisted`）
- SUPER_ADMIN かつ複数団体所属時は団体スコープ切替セレクタを表示
  - 選択された団体IDを `/api/lottery/results` および `/api/lottery/is-confirmed` の両方に渡し、コピー領域と確定状態判定を同じ団体スコープで揃える
  - 団体一覧取得前および切替直後の stale レスポンスを捨てるためのリクエストIDガードを `fetchResults` に持たせる

**備考**: 抽選確定・参加者の手動編集等の主要な管理操作は `/admin/lottery` 側に集約している。本画面で管理者向けに提供するのは、確定済み月の抽選落ちを LINE 告知用に整形・コピーする機能のみで、確定は行わない。なお同等のコピー領域は `/admin/lottery`（抽選管理画面）のプレビュー段階／確定済み段階にも表示され、プレビュー段階のみ警告色で誤配信を抑止する。**セッション単位の再抽選**は専用UIを提供せず、バックエンドAPI `POST /api/lottery/re-execute/{sessionId}`（ADMIN+）のみが稼働している（旧仕様で `/practice` の練習日ポップアップに存在した「再抽選」ボタンはUIから撤去済み）。

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
| PLAYER | 一般選手。基本機能（試合記録、参加登録、閲覧、所属団体の対戦組み合わせ作成・編集）。組み合わせの削除は不可。 |

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
| 対戦組み合わせ | 組み合わせ作成・選手差し替え | ○ | ○（自団体のみ） | ○（所属団体のみ） |
| | 自動マッチング | ○ | ○（自団体のみ） | ○（所属団体のみ） |
| | 組み合わせ削除（個別/一括/結果込みリセット） | ○ | ○（自団体のみ） | × |
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
| カレンダー購読 | URL取得・再発行・表示名カスタマイズ | ○ | ○ | ○ |
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
1. カレンダー画面（/practice）の「出欠登録ボタン」（右下フローティング、または選択セッション詳細モーダル内）をクリック
   → 出欠登録モーダル（AttendanceRegisterModal）が開く
   → モーダル内「参加登録」を選択
   → カレンダー表示中の年月を引き継いで /practice/participation?year=YYYY&month=M へ移動
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
13. SaveProgressOverlay を success 状態へ切替え（「参加登録を保存しました」「カレンダーに戻る」ボタン） → ユーザーが「カレンダーに戻る」を押下 → /practice へ遷移
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
5. 定員内 → 全員WON、定員超過 → 3層優先抽選
   - **層1（管理者指定優先）**: 管理者が指定した選手を優先枠で抽選。定員を超えた場合は優先選手同士で抽選
   - **層2（連続落選救済）**: 同月内の別セッションで落選経験のある選手を救済枠で抽選（normalReservePercent考慮）
   - **層3（一般枠）**: 残り定員に対して通常抽選
   - 当選者: status = WON
   - 落選者: status = WAITLISTED + waitlist_number付与（優先落選 → 救済落選 → 一般落選の順で番号付与）
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

[当日キャンセル→補充フェーズ — WaitlistPromotionService.cancelParticipation() / dispatchSameDayCancelNotifications()]
1. 12:00以降にWON参加者がキャンセル（管理者手動編集 `editParticipants` 経由を含む）
   ↓
2. LotteryDeadlineHelper.isAfterSameDayNoon() で12:00以降判定
   ↓
3. cancelParticipationInternal が SameDayCancelContext 付き AdminWaitlistNotificationData を返却
   ↓
4. 呼び出し元（LotteryController / DensukeImportService / LotteryService.editParticipants）で
   (sessionId, playerId) 単位に集約し、afterCommit で handleSameDayCancelAndRecruitBatch を実行
   - editParticipants は `cancelParticipationSuppressed` に委譲し、通常キャンセル経路と同じ三分岐ロジックに揃える（WON→CANCELLED 直書き経路は廃止）
   ↓
5. キャンセル発生通知（統合版）: 「〇〇さんが今日の1、3試合目をキャンセルしました」
   - 同一セッション×同一プレイヤーの複数試合は1通にまとまる（通知トグル: sameDayCancel）
   - 異なるプレイヤーはプレイヤー単位で別通知
   ↓
6. 空き募集通知（統合版）: sendConsolidatedSameDayVacancyNotification
   - セッション内の複数空き試合を1通のFlex Messageに集約（通知トグル: sameDayVacancy）
   - postback: action=same_day_join&sessionId={id}&matchNumber={num}
   ↓
7. 管理者通知: sendBatchedAdminWaitlistNotifications
   - セッション単位で1通にまとまる

【トランザクション境界の契約】
WaitlistPromotionService の `*Suppressed` 系メソッド（`cancelParticipationSuppressed` /
`respondToOfferDeclineSuppressed` / `expireOfferSuppressed` / `demoteToWaitlistSuppressed`）は
`@Transactional`（伝播 `REQUIRED`）で宣言されているが、個別コミットは保証しない：
- `DensukeImportService` / `LotteryService.editParticipants` などインポート/編集TX配下から
  呼ぶ場合、上流TXに参加して整合性を保つ（失敗時はキャンセル含めロールバック）
- ループ内で1件ずつコミットしたい呼び出し元（`LotteryController#cancelParticipation` など）は
  呼び出し元側に `@Transactional` を付けてはならない。付与するとループ全件が単一TXに化け、
  途中の例外で全件ロールバックされる
- `LotteryControllerCancelTest` にメソッド単位 `@Transactional` 不在のセンチネルテストあり

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

[コメントスレッドの表示条件（試合詳細画面）]
- メンター閲覧時（他選手のページを `?playerId=` で開いた場合）: ACTIVEメンター関係があれば常に表示
- メンティー本人画面: 自分以外の投稿者によるコメントが1件以上ある場合のみ表示（投稿フォーム含めて完全非表示）
  - 判定は `matchCommentsAPI.getComments` のレスポンスを `authorId !== currentPlayer.id` で評価
  - 解除済みメンターからの過去コメントもカウント対象

[メンターコメントフロー（バッチ送信方式）]
1. 試合詳細画面（/matches/:id）で上記表示条件を満たすメンティーまたはメンターがコメント投稿
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

### 7.6.1 試合動画フロー

練習試合の動画（YouTube限定公開）のURLを、試合の自然キー（試合日・試合番号・両選手）と紐付けて管理する「動画台帳」。`matches` / `match_pairings` とはFKを持たず、(match_date, match_number, player1_id, player2_id) の自然キーで対応付くため、**結果未入力（組み合わせのみ）段階でも登録でき、結果入力後は同一キーで自動的に試合詳細にも表示される**（付け替え処理不要）。

```
[動画登録フロー]
1. 撮影者が YouTube アプリから限定公開でアップロード → URLをコピー
   ↓
2. アプリ（試合詳細 or 動画倉庫）で対象試合を選び POST /api/match-videos
   ↓
3. MatchVideoService.register:
   a. YouTube URL検証・動画ID抽出（不正 → 400「YouTubeのURLを入力してください」）
   b. キー正規化（player1Id < player2Id）
   c. 対象試合の存在チェック（matches または match_pairings に同自然キー。
      match_pairings は p1<p2 を保証しないため選手順序不問で照合）
      → どちらにも無ければ 404「対象の試合が見つかりません」
   d. 重複チェック（既に動画あり → 409「この試合には既に動画が登録されています」）
   e. oEmbed API でタイトル取得（接続2秒・読取3秒。失敗時は title=null で続行＝fail-soft）
   f. INSERT（created_by / updated_by = 操作ユーザー）
   ※ LINE通知はタスク9（MATCH_VIDEO_REGISTERED）で追加

[編集・削除フロー]
- PUT /api/match-videos/{id}（URL差し替え）/ DELETE /api/match-videos/{id}（物理削除）
- 権限: 登録者本人（created_by）or ADMIN/SUPER_ADMIN のみ（サービス層で所有者チェック）
- 削除されるのは台帳の紐付けのみ。YouTube上の動画本体は残る

[一覧・検索フロー]
- GET /api/match-videos?date=  : 指定日の動画一覧（当日結果一覧の「動画あり」バッジ用）
- GET /api/match-videos/date-candidates?date=&organizationId= : 指定日の動画登録候補（登録モーダル「日付から」用）
  - 参加日スコープ（hasSessionOnDateForUser）なし: 非参加ユーザー（撮影担当・第三者登録）でも候補を選べる
    （サービスは currentUserId を受け取らないため構造的に担保）
  - 組織スコープあり: OrganizationScopeResolver で effectiveOrgId を解決し
    MatchPairingService.getByDate(date, true, organizationId)（light=true）に渡す（他団体の候補混入を防ぐ）。
    matches は組織カラムが無いため player_organizations の所属選手ID集合でフィルタ
    （実在選手が全員所属 かつ 実在所属メンバー1名以上。ゲスト id=0/null は所属判定から除外し
    相手名は Match.opponentName で補完）→ pairings と対称にスコープ。
    organizationId 未指定時は matches を日付のみ取得しスコープしない。
    既定解決: effectiveOrgId が null かつ単一所属 PLAYER のときのみ所属団体IDで補完（当エンドポイント限定）
  - pairings + matches を自然キー (matchDate, matchNumber, min(p1,p2), max(p1,p2)) で統合・重複排除（matches 優先）
  - 各候補に registered（同自然キーの動画あり）/ hasResult / matchId を付与。選手名はバッチ解決（N+1回避）
- GET /api/match-videos/search : 動画倉庫の検索（選手・年月絞り込み・mine トグル・ページング）
  - mine=true は操作ユーザー自身を対象選手として扱う（playerId より優先）
  - year/month はサービス層で startDate/endDate 範囲に変換してリポジトリへ渡す
  - 並びは matchDate DESC, matchNumber DESC
  - 一覧系は選手名解決・matches照合をバッチ取得（findAllById / findByMatchDateIn）で N+1 回避
  - レスポンスは PagedResponse<MatchVideoDto>（PageImpl 直接シリアライズの不安定さを回避）

[既存の試合APIへの動画付与（MatchDto.video）]
- MatchDto に `video: { id, videoUrl, youtubeVideoId, title } | null` を追加し、
  ①試合詳細（単体取得）と③個人別一覧が既存APIのまま動画有無を取得できるようにする
  （動画なしの試合は video=null。後方互換: 既存フィールドは不変、追加のみ）
  - 単体取得: MatchService.findById → 試合の自然キー (match_date, match_number, p1<p2) で
    MatchVideoRepository.findByMatchDateAndMatchNumberAndPlayers を1回呼び、ヒットすれば video をセット
  - 個人別一覧: MatchService.findPlayerMatchesWithFilters → 対象選手の動画を
    MatchVideoRepository.findByPlayerId で1クエリ取得し、(match_date, match_number, p1, p2) 正規化キーの
    マップを構築して各試合に照合・セット（N+1回避）
  - ②当日結果一覧は別API GET /api/match-videos?date= を使うため、video 付与は ①③ のみに限定する
  - MatchDto.Video.fromEntity(MatchVideo) で MatchVideo → ネストDTO 変換（fromEntity 規約に従う）
```

**関連クラス:**

| クラス | 説明 |
|--------|------|
| `MatchVideo` | entity/ — 動画台帳エンティティ。自然キー + UNIQUE制約、`provider`（'YOUTUBE'固定）、`@PrePersist`/`@PreUpdate` で p1<p2 入れ替え |
| `MatchVideoRepository` | repository/ — 自然キー検索・日付検索・選手検索（p1 OR p2）・倉庫検索（動的条件+ページング） |
| `MatchVideoController` | controller/ — 動画CRUD + 日付別一覧 + 日付別候補 + 倉庫検索（6エンドポイント）。`@RequireRole` 全ロール。`date-candidates` は `OrganizationScopeResolver` で組織スコープ解決 |
| `MatchVideoService` | service/ — 登録・URL差し替え・削除・日付別一覧・日付別候補・倉庫検索。YouTube URL検証/ID抽出、oEmbedタイトル取得（短タイムアウト・fail-soft）、所有者チェック。`getDateCandidates(date, organizationId)` は `MatchPairingService.getByDate`（参加日スコープなし・組織スコープあり）+ matches を自然キーで統合・重複排除（matches 優先）し registered/hasResult/matchId を付与 |
| `MatchVideoDto` | dto/ — `fromEntity(video, p1Name, p2Name, match)`。選手名と matches 照合結果（matchId/winnerId/scoreDifference）を含む |
| `MatchVideoDateCandidateDto` | dto/ — 日付別候補1件（matchDate/matchNumber/player1Id/player1Name/player2Id/player2Name/hasResult/matchId/registered）。複数ソース統合のため Service で組み立てる |
| `MatchVideoCreateRequest` / `MatchVideoUpdateRequest` | dto/ — 登録リクエスト（自然キー+URL）/ 更新リクエスト（URLのみ） |
| `PagedResponse<T>` | dto/ — ページング結果の汎用レスポンス（content/page/size/totalElements/totalPages） |
| `MatchDto.Video` | dto/ — `MatchDto` のネストDTO（id/videoUrl/youtubeVideoId/title）。`fromEntity(MatchVideo)`。動画なしは null |
| `MatchService` | service/ — `MatchDto` への動画付与（単体: 自然キー1回照合 / 個人別一覧: findByPlayerId 1クエリ＋マップ照合でN+1回避）。①試合詳細・③個人別一覧のみ対象 |

**DB変更:**

| 対象 | 変更内容 |
|------|---------|
| `match_videos` テーブル | タスク1で新規作成（自然キー + UNIQUE制約 `uq_match_videos_match`、provider/video_url/youtube_video_id/title） |
| `MatchRepository` | `findByMatchDateIn(dates)` 追加（動画一覧で matches をバッチ照合し N+1 回避） |

### 7.7 隣室予約→会場拡張フロー

```
[ユーザー操作（ADMIN+）]
1. 練習日詳細モーダルで隣室が「空き」の場合、「隣室を予約」ボタンを表示
   ↓
2. 「隣室を予約」ボタンクリック
   ↓
[フロントエンド: PracticeList.jsx]
3. Kaderu 和室(venueId ∈ {3,4,8,11}) → venueReservationProxyAPI.createSession() でプロキシ予約画面を新規タブに表示
   東区民センター 東🌸(venueId=6) → Phase 1 ではプロキシ未対応のため初期状態から「予約完了を報告」ボタンを表示
   （venueResolver / KADERU_VENUE_IDS 判定で分岐）
   ↓
4a. プロキシ画面表示成功時 → 「予約完了を報告」ボタンを表示（manual_pending状態）
4b. プロキシが申込完了を自動検知した場合 → BroadcastChannel 経由で元タブへ通知し、該当セッションを再取得して「会場を拡張」ボタンを表示
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
14. WaitlistPromotionService.promoteWaitlistedAfterCapacityIncrease(sessionId) を呼び出し
   - 既存 OFFERED の offerDeadline を null にクリア（拡張で参加確定）
   - match_number ごとに `(capacity - WON - 既存OFFERED)` 名分だけ、WAITLISTED を waitlist_number 昇順に OFFERED 化（offeredAt=現在時刻、offerDeadline=null）
   - 余り枠を超える WAITLISTED は据え置き（status・waitlist_number そのまま）
   - 全件 dirty=true、最後に renumberRemainingWaitlist で 1..N に再採番
   - 練習編集 (PracticeSessionService.updateSession) で capacity を増加させた場合も同じメソッドが呼ばれる
   ↓
15. レスポンス: 200 OK + 更新後のセッション情報
```

**変更対象テーブル・コード**:

| 対象 | 変更内容 |
|------|---------|
| `practice_sessions` テーブル | `reservation_confirmed_at` カラム追加 |
| `AdjacentRoomService` | `confirmReservation()`, `expandVenue()` メソッド追加 |
| `PracticeSessionController` | `POST /{id}/confirm-reservation`, `POST /{id}/expand-venue` エンドポイント追加（ADMIN+） |
| `PracticeList.jsx` | 隣室予約→予約完了報告→会場拡張の3段階UIフロー。`KADERU_VENUE_IDS` でない venue は「隣室を予約」ボタンをスキップ |
| `AdjacentRoomConfig` | `isKaderuRoom` と独立した `isAdjacentCheckTarget` を導入（かでる4部屋 + 東🌸）、`getNightTimeLabel(venueId)` で会場別時間帯ラベル、ROOM_MAP に東🌸(6)↔かっこう(12) / 東全室(10, 定員18) を追加 |
| `AdjacentRoomNotificationScheduler` | セッションフィルタを `isAdjacentCheckTarget` に切替、通知の時間帯表記を動的化（かでる: 17-21 / 東🌸: 18-21） |
| `scripts/room-checker/sync-higashi-availability-to-db.js` | 東区民センター かっこう の月表示ページから夜間(18-21)空き状況を `room_availability_cache` に UPSERT |
| `.github/workflows/scrape-higashi-availability.yml` | 30分間隔で上記スクレイパを実行（`concurrency.group=higashi-availability-check`） |

**会場予約プロキシ（Phase 1）**:

`PracticeList.jsx` の隣室予約導線は `/api/venue-reservation-proxy/*` に接続済み。旧 `/api/kaderu/*` Controller / Service、旧 React API クライアント、Playwright 版 `open-reserve.js` は削除済み。

| コンポーネント | 役割 |
|---------------|------|
| `VenueReservationProxyController` | `/api/venue-reservation-proxy/session`、`/view`、`/fetch/**` を公開。ADMIN+ の `@RequireRole` と venue proxy 固有例外の `{errorCode, message, venue}` レスポンスを担当 |
| `VenueReservationProxyService` | Controller から呼ばれるファサード。`createSession` / `view` / `fetch` を統括し、会場別 client / config / rewrite strategy を `EnumMap<VenueId, ...>` で dispatch |
| `VenueReservationSessionStore` | `ProxySession` を JVM メモリで管理。token、会場、CookieStore、hiddenFields、申込トレイHTML、完了状態を保持 |
| `VenueReservationClient` | 会場別 HTTP クライアント契約。Phase 1 は `KaderuReservationClient` |
| `VenueReservationHtmlRewriter` | HTMLのURLを `/api/venue-reservation-proxy/fetch/**?token=...` に書き換え、バナーと注入スクリプトを挿入。CSSレスポンスでは `@import` / `url(...)` をCSS自身の上流URL基準でプロキシURLへ書き換える |
| `VenueReservationCompletionDetector` | 会場別 `VenueCompletionStrategy` で申込完了を検知し、`reservation_confirmed_at` を初回検知時刻で固定 |
| `venueReservationProxyAPI` | React 側の API クライアント。`createSession` で `POST /api/venue-reservation-proxy/session` を呼び、`PracticeList.jsx` から利用する |
| `venueResolver` | `PracticeSessionDto` の `venueId` を `KADERU` / `HIGASHI` / `null` に変換する。Phase 1 は Kaderu 会場 ID `[3, 4, 8, 11]` のみを `KADERU` に解決する |
| `PracticeList.jsx` | 「隣室を予約」クリック直後に空タブを確保し、venue 判別、プロキシセッション作成、`viewUrl` 遷移を行う。`BroadcastChannel('venue-reservation-proxy')` の完了通知で該当セッションを再取得して UI を予約済みに更新 |

`fetch` は会場サイトの `Set-Cookie` / `X-Frame-Options` / `Strict-Transport-Security` / `Content-Security-Policy` をユーザーへ返さず、HTMLレスポンスは画面内URLを書き換える。CSSレスポンスは `@import` / `url(...)` を現在の上流CSS URL基準で書き換え、Kaderu の `css/style.css` から読み込まれる分割CSSにも proxy token が付くようにする。完了検知時は `X-VRP-Completed: true` を付与する。

公開 API は `POST /api/venue-reservation-proxy/session`、`GET /api/venue-reservation-proxy/view?token=...`、`ANY /api/venue-reservation-proxy/fetch/**?token=...`。いずれも ADMIN+ のみ利用可能。

Kaderu の Phase 1 実装は `https://k2.p-kashikan.jp/kaderu27/index.php` に対する form 等価 POST で、実サイトの `gotoPage(op)` / `showCalendar(y,m)` / `clickDay(d)` / `setAppStatus(...)` と同じ form 状態を再現する。各応答 HTML から hidden field を `ProxySession.hiddenFields` に保存し、次の POST は保存済み field をベースに必要項目だけを上書きする。日付選択は `op=srch_sst` を維持して `UseYM` / `UseDay` / `UseDate` を更新し、申込トレイ投入は `setAppStatus` の `chk_rsv` 空き再確認後に `op=apply` + `rsv_chk[facilityCode][YYYY/MM/DD][slotIndex]=timeRange` + `requestBtn=申込トレイに入れる` を送信する。申込完了検知は URL / Location の `op=rsv_comp` / `p=rsv_comp`、`op=fix_comp` / `p=fix_comp`、`/complete` と、本文の「申込みを受け付けました」「申込番号」「予約を受付ました」「予約完了」を陽性条件にする。

### 7.8 かでる予約 → 練習日自動登録フロー

北大かるた会（hokudai）とわすらもち会（wasura）の両団体に対応する。ワークフローは hokudai → wasura の順に 2 回 `sync-reservations.js` を呼び出し、各実行は対象団体スコープで独立に処理する（スクリプトは 1 実行 1 団体）。

```
[GitHub Actions: sync-kaderu-reservations.yml]
  cron: */30 * * * *  または  workflow_dispatch
  ↓
  ┌──────────────────────────────────────────────────────────────────┐
  │ Step 1: Sync reservations (hokudai)                              │
  │   env: KADERU_USER_ID / KADERU_PASSWORD ← Secrets               │
  │   cmd: node sync-reservations.js --org hokudai --months 2       │
  ├──────────────────────────────────────────────────────────────────┤
  │ Step 2: Sync reservations (wasura)  (if: always())              │
  │   env: KADERU_USER_ID  ← secrets.WASURA_KADERU_USER_ID          │
  │        KADERU_PASSWORD ← secrets.WASURA_KADERU_PASSWORD         │
  │   cmd: node sync-reservations.js --org wasura --months 2        │
  └──────────────────────────────────────────────────────────────────┘
  ↓ （各 Step の中で以下が走る）
[Node.js: scrape-mypage.js]
1. かでる2・7サイトにPlaywright(headless)でログイン
   - 環境変数: KADERU_USER_ID / KADERU_PASSWORD（workflow 側で団体ごとに切り替え）
   ↓
2. マイページ → 予約申込一覧ページへ遷移
   ↓
3. 当月+翌月のテーブルをパース
   - 利用日時 → 日付 + 時間帯判定
   - 利用施設 → 部屋名抽出（すずらん/はまなす/あかなら/えぞまつ）
   ↓
4. 夜間(17:00-21:00)のみフィルタ → JSON出力
   ↓
[Node.js: sync-reservations.js --org <code>]
5. JSONを受け取り、日付ごとに部屋をグルーピング
   - 「取消」ステータスは除外
   - 隣室ペア判定: すずらん+はまなす→拡張ID:7, あかなら+えぞまつ→拡張ID:9
   ↓
6. DB接続 → organizations テーブルから --org で指定された団体の ID を取得
   - SQL: SELECT id FROM organizations WHERE code = $1  ($1 = orgCode)
   - 該当組織が無ければエラー終了
   ↓
7. 日付ごとに practice_sessions を照合（organization_id スコープ）:
   a. (session_date, organization_id) で存在しない → INSERT（ON CONFLICT DO NOTHING、venue_id, totalMatches, startTime=17:00, endTime=21:00）
   b. 既存 venue_id=NULL → 算出会場で UPDATE（venue_id, capacity 補完）
   c. 単室で既存 + 隣室が予約にある → UPDATE（拡張会場に変更, capacity更新）
   d. それ以外 → スキップ
   ↓
8. 処理結果サマリーを `[<orgCode>]` プレフィックス付きで出力
```

`if: always()` により、hokudai が失敗しても wasura は独立して実行される。いずれかが失敗すれば workflow 全体は失敗扱い（赤バッジ）。`concurrency` グループ `kaderu-reservation-sync` で重複起動を防ぐ。

**関連テーブル**:

| テーブル | 操作 |
|---------|------|
| `organizations` | SELECT（`code = $1` で指定団体の ID 取得。hokudai / wasura のいずれか） |
| `venues` | SELECT（defaultMatchCount, capacity 取得） |
| `practice_sessions` | SELECT / INSERT / UPDATE（`(session_date, organization_id)` スコープ。複合UNIQUEで競合スキップ） |

**スクリプトファイル**:

| ファイル | 説明 |
|---------|------|
| `scripts/room-checker/scrape-mypage.js` | スクレイピング専用（JSON出力）。`KADERU_USER_ID` / `KADERU_PASSWORD` の env を呼び出し側で切り替えて利用 |
| `scripts/room-checker/sync-reservations.js` | DB同期（scrape-mypage.jsを子プロセスで実行）。`--org <code>` 引数で対象団体を指定（必須） |
| `.github/workflows/sync-kaderu-reservations.yml` | 30分cron + 手動トリガー。hokudai → wasura の2ステップ実行 |

**認証情報（GitHub Secrets）**:

| Secret 名 | 用途 |
|----------|------|
| `KADERU_USER_ID` / `KADERU_PASSWORD` | hokudai 用 Kaderu 2.7 アカウント |
| `WASURA_KADERU_USER_ID` / `WASURA_KADERU_PASSWORD` | wasura 用 Kaderu 2.7 アカウント |
| `KADERU_DATABASE_URL` | PostgreSQL 接続URL（両団体共通） |

#### 7.8.1 手動トリガーフロー

cron による30分ごとの自動同期に加え、ADMIN+ が任意のタイミングで同期を起動できる経路。バックエンドが GitHub Actions の `workflow_dispatch` API を叩き、結果をイベントテーブルに記録、scheduler が完了検知して LINE 通知を返す。

```
[Frontend: PracticeForm.jsx (/practice/new, ADMIN+ 限定)]
  ボタン押下 → kaderuSyncAPI.trigger(orgId)
   ↓
[Backend: POST /api/kaderu-sync/trigger]
  KaderuSyncTriggerController
   ↓ (OrganizationScopeResolver で実効 orgId 解決)
  KaderuSyncTriggerService.triggerSync(playerId, orgId)
   1. PENDING 重複チェック (高速 path) → あれば 409 (DuplicateResourceException)
   2. organizations.code 取得 (なければ 404)
   3. KaderuSyncTriggerEvent を PENDING (github_run_id=null) で saveAndFlush
       - DB 側の UNIQUE 部分インデックス uk_kaderu_sync_pending が同時リクエストの
         race を確定的に検知 (DataIntegrityViolationException → 409 に変換)
       - dispatch より「先に」DB を占有することで、loser リクエストは UNIQUE 違反で
         止まり workflow を起動しない
   4. GitHubActionsClient.dispatchWorkflow(
        "sync-kaderu-reservations-manual.yml", "main",
        {org: code, eventId: <event.id>})
       - 環境変数 GITHUB_PAT で Bearer 認証
       - 未設定なら 503 (ResponseStatusException)
       - 失敗なら 500 (RuntimeException) → @Transactional が save を rollback
       - eventId は workflow の run-name に埋め込まれ、scheduler が display_title
         の "[event:<id>]" トークンで run ↔ event を一意に相関させる相関 ID
   ↓
[GitHub Actions: sync-kaderu-reservations-manual.yml]
  workflow_dispatch (inputs.org)
   - concurrency: kaderu-reservation-sync (cron と直列化)
   - if: inputs.org == 'hokudai'/'wasura' で1団体のみ実行
   - node sync-reservations.js --org <code> --months 2
   ↓
[Backend: KaderuSyncStatusPollingScheduler @Scheduled(fixedDelay=30s)]
  KaderuSyncTriggerService.pollPendingEvents()
   for each PENDING event:
     a. triggered_at から30分超過 → FAILED + 失敗通知 (fail-safe)
     b. github_run_id が null → listRecentRuns + display_title の "[event:<id>]"
        トークン照合で一意特定 (取れなければ次回。triggered_at から30分以内のみ捜索)
     c. getWorkflowRun(runId) で status/conclusion 取得
     d. completed && success → COMPLETED 確定
        - fetchWorkflowLogText から「新規作成:X件 / 会場拡張:X件 / スキップ:X件」を
          正規表現で抽出して summary に格納
        - LineNotificationService.sendKaderuSyncCompletedNotification()
     e. completed && (failure|cancelled|timed_out) → FAILED 確定
        - LineNotificationService.sendKaderuSyncFailedNotification()
   ↓
[LINE Messaging API]
  押下者本人 (triggered_by_player_id) の PLAYER チャネル経由で送信
  preference は経由しない (常時送信)
   ↓
[Frontend]
  ユーザーが LINE 通知を受けて画面を手動リロード → 新しい練習日が表示される
  並行して30秒ポーリングが PENDING 解除を検知してボタンを再活性化
```

**関連テーブル**:

| テーブル | 操作 |
|---------|------|
| `kaderu_sync_trigger_events` | INSERT (PENDING) / UPDATE (COMPLETED/FAILED + github_run_id + summary/failure_reason) |
| `organizations` | SELECT (code 解決) |
| `line_message_log` | INSERT (ADMIN_KADERU_SYNC_COMPLETED / ADMIN_KADERU_SYNC_FAILED) |

**ファイル構成**:

| ファイル | 役割 |
|---------|------|
| `.github/workflows/sync-kaderu-reservations-manual.yml` | 手動同期専用 workflow (workflow_dispatch + inputs.org) |
| `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/KaderuSyncTriggerController.java` | POST /api/kaderu-sync/trigger と GET /api/kaderu-sync/status |
| `karuta-tracker/src/main/java/com/karuta/matchtracker/service/KaderuSyncTriggerService.java` | dispatch 起動 + PENDING 巡回処理 |
| `karuta-tracker/src/main/java/com/karuta/matchtracker/service/GitHubActionsClient.java` | GitHub Actions REST API クライアント (Spring RestClient) |
| `karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/KaderuSyncStatusPollingScheduler.java` | 30秒間隔の PENDING 巡回スケジューラー |
| `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/KaderuSyncTriggerEvent.java` | イベントエンティティ (status: PENDING/COMPLETED/FAILED) |
| `karuta-tracker-ui/src/api/kaderuSync.js` | フロント API クライアント |
| `karuta-tracker-ui/src/pages/practice/PracticeForm.jsx` | `/practice/new` ナビバーのボタン + 30秒ポーリング + 1秒タイマー |

**Render 環境変数**:

| 変数名 | 用途 |
|--------|------|
| `GITHUB_PAT` | fine-grained PAT (Actions: Read and write, リポジトリ限定推奨)。未設定時は 503 |
| `GITHUB_REPO` | 対象リポジトリ。デフォルト `poponta2020/match-tracker` |

### 7.9 東区民センター予約 → 練習日自動登録フロー

```
[GitHub Actions: sync-higashi-reservations.yml]
  cron: */30 * * * *  または  workflow_dispatch
  ↓
[Node.js: scrape-higashi-history.js]
1. sapporo-community.jp にPlaywright(headless)でログイン
   - 環境変数: SAPPORO_COMMUNITY_USER_ID / SAPPORO_COMMUNITY_PASSWORD
   ↓
2. メニュー → 申込履歴・結果ページへ遷移
   ↓
3. 履歴テーブル（#ctl00_cphMain_gvView）の全ページをパース
   - 7列行のみデータ行として採用
   - 申込内容に「札幌市東区民センター」を含む行のみ抽出
   - 和暦 → 西暦変換、部屋名正規化（さくら/かっこう/和室全室）
   - 取消済は除外
   ↓
4. 夜間(開始時刻 17:00 以降)のみフィルタ → JSON出力
   ↓
[Node.js: sync-higashi-reservations.js]
5. JSONを受け取り、日付ごとに部屋をグルーピング
   - 和室全室 or (さくら + かっこう) → 東全室(ID:10)
   - さくら のみ → 東🌸(ID:6)
   - かっこう のみ → 警告ログを出しスキップ
   ↓
6. DB接続 → organizations テーブルから hokudai の ID を取得
   ↓
7. 日付ごとに practice_sessions を照合:
   a. 存在しない → INSERT（venue_id, totalMatches, startTime=18:00, endTime=21:00）
   b. 既存 venue_id=NULL → 算出会場で UPDATE（venue_id, capacity 補完）
   c. 既存 venue_id=6 + 算出 10 → UPDATE（東全室に昇格, capacity 更新）
   d. 既存 venue_id=10 → スキップ（ダウングレード無し）
   e. 既存 venue_id=3/4/7/8/9/11（かでる系） → スキップ（1日併存しない前提）
   ↓
8. 処理結果サマリー（created / expanded / skipped 件数）を出力
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
| `scripts/room-checker/scrape-higashi-history.js` | スクレイピング専用（JSON出力） |
| `scripts/room-checker/sync-higashi-reservations.js` | DB同期（scrape-higashi-history.jsを子プロセスで実行） |
| `.github/workflows/sync-higashi-reservations.yml` | 30分cron + 手動トリガー |

**かでる同期との関係**:

- 同一 Render PostgreSQL を共有（secret `KADERU_DATABASE_URL` を流用）
- concurrency group (`higashi-reservation-sync`) が別なので cron 同時起動でも競合しない
- 1日に両センターの予約が併存しない前提（Q13）。既存セッションがかでる系会場の場合、東区民センター同期側からは触らない

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
  - バックエンド（`PracticeSessionService.enrichDtoWithMatchDetails`）は `DECLINED` / `WAITLIST_DECLINED` 以外の全ステータス（`WON` / `PENDING` / `WAITLISTED` / `OFFERED` / `CANCELLED`）を含めて返す
  - 各エントリは `status` フィールドを持ち、フロント側で用途に応じてフィルタする責務を持つ
- **組み合わせ対象 / 抜け番算出ルール**:
  - 団体の運用設定により対象ステータスを切り替える（バックエンド・フロント共通ルール）
    - **抽選あり運用** (`DeadlineType.MONTHLY` かつ締め切りなしモードでない): `WON` のみ
    - **抽選なし運用** (`DeadlineType.SAME_DAY` または `MONTHLY` + `isNoDeadline=true`): `WON` + `PENDING`
  - 判定ヘルパー: `LotteryDeadlineHelper.isLotteryDisabled(organizationId)` が抽選なし運用なら `true`
  - バックエンド `MatchPairingService.loadActiveParticipantIdsForMatch()` は上記ヘルパーで対象ステータスを切り替える
  - フロント側は `PracticeSessionDto.pairingIncludesPending`（バックエンドが `isLotteryDisabled` を反映して返すフラグ）を使い同一ルールで判定する（`PairingGenerator.jsx` / `BulkResultInput.jsx` の `byePlayersLogic.js` / `MatchResultsView.jsx`）
  - 抽選あり運用で `PENDING` を含めると、抽選前の参加希望者まで自動マッチング対象になり抽選結果をバイパスしてしまうため、組織設定に応じた判定が必須

#### 自動マッチングアルゴリズム
- 過去30日の対戦履歴を基にスコアリング
- 日数が経過するほど高スコア（再対戦しやすい）
- 初対戦: スコア0（最優先）
- 同日既存対戦を除外
- シャッフル + 貪欲法で最適ペアリング

#### 直近対戦履歴（recentMatches）生成ロジック
- `MatchPairingService` の `getPairRecentMatches` / `enrichWithRecentMatches` / `autoMatch` の3経路で表示用 `recentMatches` を生成する
- ソース: `MatchPairingテーブル`（ペアリング履歴）+ `Matchテーブル`（試合履歴）を過去30日分マージ
- 同日の他試合（自分の試合番号以外すべて）で組まれたペアは当日日付として `recentMatches` に含める
  - 前方試合番号（例: 試合3編集中の試合1・2）だけでなく、後方試合番号（例: 試合4・5）も検知対象
  - 自分と同じ試合番号のペアは `recentMatches` から除外（編集中の自分自身を警告対象にしない）
- フロントエンドは `recentMatches[0].matchDate === sessionDate` の場合に `⚠今日` を赤字太字で警告表示
- ※ 自動マッチングのペナルティ計算（`calculatePairScore`）と除外判定（`getTodayPairings`）は表示用ロジックとは別責務で、現状「前の試合番号のみ」を見ている（将来的な改修候補）

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
- **新規セッション作成時の capacity 初期化**: `DensukeImportService.findOrCreateSession` で伝助同期から練習日を新規作成する際、解決済み venue がある場合は `venue.capacity` を `practice_sessions.capacity` の初期値として設定する。venue が解決できなかった場合 (`venueId == null`) は capacity を `null` のまま保存し、表示側のフォールバック（`PracticeSessionService.computeMatchCapacityStatuses` の venue 既定値フォールバック）に委ねる
  - **Phase3 3-A6（WAITLISTED + 伝助○）**: 当日12:00 JST以降かつ空き枠あり（WON数 < 定員）の場合、`DensukeImportService.processPhase3Maru` がWAITLISTED→WONに昇格し後続のキャンセル待ち番号を繰り上げ（dirty=false）。12:00前または空き枠なしの場合はdirty=trueにして△で書き戻す（抽選バイパス防止）
  - **練習セッション自動作成時の Venue デフォルト値適用** (`DensukeImportService.findOrCreateSession`): 伝助の会場名と `venues.name` が一致した場合、新規セッションの `totalMatches` は `venue.defaultMatchCount`、`capacity` は `venue.capacity` を採用する。未マッチ時は `totalMatches` を伝助スケジュールの最大試合番号（なければ既定 3）にフォールバックし、`capacity` は null とする。既存セッションでは `venueId` 補完と同時に `capacity` も補完するほか、`venueId` 既設定でも `capacity` のみ null の場合は Venue から逆引きして補完する（管理者が設定済みの `capacity` / `totalMatches` / `venueId` は触らない）。これにより、当日12:00以降の WAITLISTED→WON 自動昇格や空き枠通知が、伝助由来セッションでも正常動作する
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
- **伝助ページ自動作成（DensukePageCreateService）**: アプリ側に登録された練習日（`practice_sessions` × `venues` × `venue_match_schedules`）から densuke.biz にページを新規発行
  - エンドポイント: `POST /api/practice-sessions/densuke/create-page`（ADMIN以上）
  - 送信先: `POST https://www.densuke.biz/create`（`/confirm` はスキップ可能、認証不要）
  - schedule 文字列は「セッションの 1 試合目行は `{M}/{D}({曜}) {会場名} 1試合目`、2 試合目以降は `{N}試合目` のみ」の 2 段形式で改行連結（時刻は含めない）。既存 `DensukeScraper` は `currentDate`/`currentVenue` を前行から引き継ぐため、日付・会場の省略行も正しくパースできる
  - 固定パラメータ: `eventchoice=1`（○△×）、`pw=0`（パスワードなし）
  - レスポンス 302 Location から `cd` と `sd` を抽出、`densuke_urls` に保存
  - バリデーション: ①年月範囲（JST 当月〜+2 ヶ月）、②既存URL重複、③practice_sessions 0件、④venue_match_schedules 不足、⑤会場未登録
  - 排他制御: `densuke_urls` に仮レコードを `saveAndFlush` で先行確保し UNIQUE 制約で同時作成を直列化。ユニーク違反時は 400（「既に登録されています」）を返し densuke.biz への二重 POST を防止
  - 手動 URL 上書き経路（`saveDensukeUrl`）では `densuke_sd` を明示的に NULL クリアし、自動作成済みレコードの sd が残留しないよう保証
  - 作成成功後、`@TransactionalEventListener(AFTER_COMMIT)` 相当の同期で団体所属 PLAYER ロールメンバー（ADMIN/SUPER_ADMIN 除外）に LINE 通知（`DENSUKE_PAGE_CREATED`）を送信
  - テンプレート: `densuke_templates` テーブルで団体ごとにタイトル・説明・連絡先メアドのデフォルト値を保持。作成ダイアログで編集可能
  - 作成後は既存の `DensukeSyncScheduler` が次回サイクル（最長5分）で新URLを自動取り込み
  - 作り直し: `DELETE /api/practice-sessions/densuke-url?year=&month=&organizationId=`（ADMIN以上、自団体のみ）で `densuke_urls` 行を物理削除して作成ロックを解除。densuke.biz 側の旧ページは残るが、アプリからの参照は断たれる。UI では「作り直す」ボタン → 確認ダイアログ → DELETE → 作成モーダル自動オープン、の一連フローで提供
- **メンバー最終変更時刻の取得と drift ログ（observability only / Issue #543, #544, #545）**:
  - `DensukeScraper` がヘッダ各メンバーの `<a title="M/d HH:mm">` 属性をパースして `DensukeData.memberLastChangeTimes`（`Map<String, LocalDateTime>`）に格納（Issue #544）
    - パースは `DensukeScraper.parseDensukeTitleAsDateTime(title, year)`（`^\\s*(\\d{1,2})/(\\d{1,2})\\s+(\\d{1,2}):(\\d{2})\\s*$`）。年は scrape 時引数を採用、年跨ぎはスコープ外
    - `title` が null / 空 / フォーマット不一致 / 不正日付（`2/30`, `13/1`, `25:00`）の場合は map に entry を持たない
  - `DensukeImportService` は Phase1（DB差分検出）と Phase3（伝助→アプリ同期）の状態遷移ログ末尾に、`formatDriftLog()` で生成した drift 情報を付与（Issue #545）
    - 形式: `densukeTitle=<title時刻> detectedAt=<検出時刻(秒丸め)> drift=<分>m`、未取得時は `densukeTitle=(unknown) ... drift=(unknown)`
    - `detectedAt` はインポート 1 回分で固定（インポート開始時刻、`ChronoUnit.SECONDS` で丸め）
  - `warnIfDrifted()` で drift が `DRIFT_WARN_THRESHOLD_MINUTES = 10` 分を超える場合に WARN を出力。`title` 未取得時は WARN 抑制（空 title メンバーでの大量 WARN 防止）
    - 形式: `WARN Densuke change-time drift detected: phase=<Phase> session=<id> match=<n> player=<id> (<name>) densukeTitle=... detectedAt=... driftMinutes=<n>`
  - **DB / API / UI 変更なし** — ログのみで提供。drift 履歴の永続化やフロント表示は将来検討
- **アプリ→伝助 練習日 push 同期（DensukeScheduleWriteService）**: アプリで新規練習日を追加した際に、伝助ページの候補日程欄へ末尾追記で自動同期する機能（追加のみ、削除対象外）。スパイク調査により伝助の `POST /update` で既存スケジュールに末尾追記できることが実証済みで、既存 `densuke_row_ids` のインデックスは破壊されない
  - **トリガー**: ① `PracticeSessionService.createSession` の `afterCommit` フックで `pushNewSchedulesToDensukeAsync` を `@Async` で即時 push。② `DensukeSyncService.syncAll` の最初のステップで `pushAllForCurrentAndNextMonth` をフォロー同期（即時 push 失敗時の自動回復）
  - **無限ループ防止**: `DensukeImportService.findOrCreateSession` は `practiceSessionRepository.save` を直接呼ぶため `createSession` を経由せず、伝助→アプリ取り込み起因の push 再帰は構造上発生しない
  - **並行制御**: `DensukeUrlRepository.findByYearAndMonthAndOrganizationIdForUpdate`（`@Lock(PESSIMISTIC_WRITE)`）で同一 (year, month, organizationId) の行ロックをかけ、並行 push の差分計算ズレを防止
  - **差分計算**: `DensukeScraper.scrape` で伝助の現スケジュール（日付集合）を取得し、アプリ側 `practice_sessions` のうち伝助に存在しない日付のみを抽出。差分なしなら POST せず early return
  - **過去日制約**: 伝助 `/update` は末尾追記しかできないため、伝助の既存最大日付より前の新規日付を push すると `DensukeWriteService.parseAndSaveRowIds` の row id 対応がずれて参加者出欠が別日に書き込まれるデータ破壊リスクがある。差分セッションのうち伝助既存最大日付以前のものは push せず、即時 push 経路のみ管理者へ LINE 通知して手動追加を促す（スケジューラ経路は通知抑制）
  - **スケジュール文字列**: 既存 `DensukePageCreateService.buildScheduleText` を再利用（フォーマット一貫性）。会場未設定・`venue_match_schedules` 不足時の `IllegalStateException` は失敗通知に変換
  - **HTTP 呼び出し**: GET `/list?cd=...` で Cookie と `pageId` を取得 → POST `/update`（`cd`, `id`, `postfix=""`, `schedule`）→ 期待レスポンス HTTP 302。`DensukeWriteService.extractPageId`（package-private に拡張）/ `extractCd` / `extractBase` を共用
  - **失敗時挙動**: 即時 push 失敗時は `LineNotificationService.sendDensukeScheduleSyncFailedNotification` で団体の ADMIN/SUPER_ADMIN に LINE 通知（`ADMIN_DENSUKE_PUSH_FAILED`、preference カラム未追加で常時送信）。スケジューラ経路は WARN ログのみで通知抑制（フラッディング防止）
  - **自己注入**: `@Lazy DensukeScheduleWriteService self` をコンストラクタで受け、`@Async` / `@Transactional` の AOP プロキシを通すため同一 bean 内呼び出しを `self.xxx()` 経由で行う
  - **DB マイグレーション**: 新 enum 値 `ADMIN_DENSUKE_PUSH_FAILED`（25 文字、VARCHAR(30) 内に収まる短縮命名）を `line_message_log_notification_type_check` の CHECK 制約に追加するマイグレーション SQL（`database/add_admin_densuke_push_failed_message_log_check.sql`）を本番 DB に適用する必要あり。テーブル定義の変更（カラム長拡張）は不要
  - **設計判断（DB ロックと外部 HTTP のスコープ）**: `@Transactional` 内で `densuke_urls` 行ロック → 伝助 scrape → POST /update まで実行する設計。ロック粒度は (year, month, organizationId) 単位で限定的、保持時間は HTTP タイムアウト（各 10 秒、合計最大 30 秒）に律速され、`pushAllForCurrentAndNextMonth` は各 URL を順次処理するため DB コネクションプール圧迫リスクは抑えられる。将来パフォーマンスが課題化した場合は advisory lock や keyed lock で HTTP 前にトランザクションを閉じる設計に変更を検討（本 PR は現行方式維持、Codex Round 3 WARNING の現行維持判断）

#### カレンダー購読（iCalフィード）
- プレイヤーごとに発行された `ical_feed_token` を親トークンとし、**所属団体ごと + ゲスト参加** で別々のURLを発行
  - 所属団体: `/ical/calendar/{token}/org/{orgId}.ics`（VCALENDAR.X-WR-CALNAME = 表示名）
  - ゲスト参加: `/ical/calendar/{token}/guest.ics`（X-WR-CALNAME = `ゲスト参加`）
- カレンダー単位を分けることで、Googleカレンダー等の購読側で団体別に色分け可能
- 購読側（Googleカレンダー等）が定期取得（数時間〜半日間隔）
- biweekly でリクエスト時にDB状態を反映したVCALENDARを動的生成（サーバ側スケジューラ不要）
- 削除・キャンセル・ステータス変化（非 `isActive()`）された練習は次回フェッチで自動消去（VEVENT.UID で同一性判定）
- VEVENT.UID: `session-{sessionId}-player-{playerId}@match-tracker`
- 表示名カスタマイズ: `player_organizations.calendar_display_name` で団体ごとに上書き可（ゲスト参加カレンダー内は `Organization.name` 固定）
- 同期対象は `ParticipantStatus.isActive()`（WON / PENDING のみ、CANCELLED / DECLINED / WAITLISTED 等は除外）
- **同期対象期間は全期間（過去・未来とも）**。`PracticeParticipantRepository.findAllParticipationsByPlayer(playerId)` で取得し、サーバ側で日付による絞り込みは行わない。カレンダーアプリで過去の練習を見返す体験（「数年前のこの日はここで練習していた」など）を維持するための設計判断
- 設定画面 `CalendarSubscriptionPage.jsx` は「このページについて」常時表示ボックス・「登録手順を見る」アコーディオン（Google PCブラウザ / Apple iPhone）・各操作の説明サブテキストを含み、外部ドキュメント不要で利用方法が完結する
- VEVENT 時刻はプレイヤーの参加 `match_number` から動的に算出（`IcalCalendarFeedService.buildEvent`）
  - `buildIcsForParticipations` で session 単位に `sessionMatchNumbers`（`match_number != null` のみ集計）と `sessionsWithNullMatchNumber` を構築し、`buildEvent` に `allHaveMatchNumber` フラグとして渡す
  - **条件A**（全参加レコードに `match_number` あり）かつ **条件B**（参加 `match_number` のうち 1 件以上 `VenueMatchSchedule` 登録あり）が両方成立 → 登録済みスケジュールの `min(start)`〜`max(end)` を採用
  - それ以外（`null` 混在、会場スケジュール未登録、`venue_id` 未設定など）→ `PracticeSession.start_time`/`end_time` にフォールバック
  - 両方 `null` なら全日イベント（`VALUE=DATE`）。startTime のみ存在で endTime が `null` の場合は `startTime.plusHours(4)` を補う（フォールバック時のみ発生）
  - UID は時刻を含まないため、Google カレンダー側では同じイベントが「更新」され、重複や消失は発生しない

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
- **カレンダー購読（iCalフィード）**
  - 公開URLによる購読型（OAuth不要、全メンバー利用可）
  - 表示名カスタマイズ（プレイヤー×団体ごと）
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
