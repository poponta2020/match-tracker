# データベース設計 — テーブル定義（1/3: players 〜 venue_match_schedules）

> [docs/design/db.md](./db.md) の「テーブル定義」の分割ファイル。責務・ER図・初期データは db.md を参照。続きは [db-tables-2.md](./db-tables-2.md)・[db-tables-3.md](./db-tables-3.md)。

## テーブル定義

> 型・制約表記は既定で旧SPECIFICATION（2026-07-10分割前。原文は git 履歴参照）の記載を採用し、旧DESIGN との相違は各テーブル直後の「⚠要確認」にまとめる。SPEC に無く DESIGN のみに存在するカラムはその旨を都度注記する。`id` 列の `PK, AUTO`（SPEC表記）と `PK, AUTO_INCREMENT`（DESIGN表記）はいずれも自動採番される主キーの同義表記のため、個別の⚠要確認には含めず本文は SPEC 表記 `PK, AUTO` に統一する。DESIGN 側の `ENUM` / `DATETIME` は MySQL 時代の型表記の残存であり、本番は PostgreSQL（`VARCHAR` 系 / `TIMESTAMP`）。値域・NULL制約・長さなど実質的な差異がある列のみ個別に⚠要確認を付す。

#### players（選手マスタ）

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | 選手ID |
| name | VARCHAR(100) | NOT NULL, UNIQUE | 選手名（ログインID） |
| password | VARCHAR(255) | NOT NULL | BCryptハッシュ |
| gender | VARCHAR(255) | NOT NULL | 男性/女性/その他 |
| dominant_hand | VARCHAR(255) | NOT NULL | 右/左/両 |
| dan_rank | VARCHAR(255) | — | 段位（無段～八段） |
| kyu_rank | VARCHAR(255) | — | 級位（E級～A級） |
| karuta_club | VARCHAR(200) | — | 所属会 |
| remarks | TEXT | — | 備考 |
| role | VARCHAR(255) | NOT NULL, DEFAULT 'PLAYER' | ロール（SUPER_ADMIN/ADMIN/PLAYER） |
| require_password_change | BOOLEAN | NOT NULL, DEFAULT FALSE | パスワード変更要求フラグ |
| admin_organization_id | BIGINT | FK → organizations.id | ADMINの所属団体ID（PLAYER/SUPER_ADMINはNULL）（出典: DESIGN のみ） |
| last_login_at | TIMESTAMP | — | 最終ログイン日時 |
| deleted_at | TIMESTAMP | — | 論理削除 |
| created_at | TIMESTAMP | NOT NULL | — |
| updated_at | TIMESTAMP | NOT NULL | — |
| ical_feed_token | VARCHAR(64) | NOT NULL, UNIQUE | iCalフィードURL用の推測困難な48文字トークン（pgcrypto + hex）（出典: DESIGN 付録「iCalフィード用カラム」） |

（本番 introspect 照合済み: gender/dominant_hand/dan_rank/kyu_rank/role は VARCHAR(255)、last_login_at/deleted_at/created_at/updated_at は TIMESTAMP。DESIGN の ENUM/DATETIME 表記は MySQL 時代の残滓と判明したため⚠は解消）

インデックス: `players_pkey(id)`, `ukpblmuavgrnr991e41662asko` UNIQUE (name), `idx_name_active(name, deleted_at)`, `idx_deleted_at(deleted_at)`, `idx_players_ical_feed_token` UNIQUE (ical_feed_token)（出典: DESIGN 付録）

---

#### organizations（団体マスタ）（出典: DESIGN のみ）

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | 団体ID |
| code | VARCHAR(50) | NOT NULL, UNIQUE | 団体コード（wasura, hokudai） |
| name | VARCHAR(200) | NOT NULL | 団体名 |
| color | VARCHAR(10) | NOT NULL | テーマカラー（例: #22c55e） |
| deadline_type | VARCHAR(20) | NOT NULL | 締め切りタイプ（SAME_DAY / MONTHLY） |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 作成日時 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 更新日時 |

インデックス: `organizations_pkey(id)`, `organizations_code_key` UNIQUE (code)

---

#### player_organizations（ユーザー×団体紐づけ）（出典: DESIGN のみ）

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | ID |
| player_id | BIGINT | NOT NULL | 選手ID |
| organization_id | BIGINT | NOT NULL | 団体ID |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 作成日時 |
| calendar_display_name | VARCHAR(50) | NULLable | カレンダー上の団体表示名カスタマイズ（NULL なら Organization.name を使う）（出典: DESIGN 付録「iCalフィード用カラム」） |

ユニーク制約: `player_organizations_player_id_organization_id_key` (player_id, organization_id)

自動所属: 練習参加登録時・伝助経由の新規選手登録時に、該当団体へ未所属であれば `OrganizationService.ensurePlayerBelongsToOrganization()` により自動追加（通知設定のデフォルトレコードも同時作成）。退会は手動操作のみ。

---

#### matches（対戦結果）

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| match_date | DATE | NOT NULL | 対戦日 |
| match_number | INT | NOT NULL | 試合番号 |
| player1_id | BIGINT | NOT NULL, FK | 選手1（ID小さい方） |
| player2_id | BIGINT | NOT NULL, FK | 選手2（ID大きい方） |
| winner_id | BIGINT | NOT NULL, FK | 勝者ID |
| score_difference | INT | — | 枚数差（0〜25）。指導試合では NULL |
| is_lesson | BOOLEAN | NOT NULL DEFAULT FALSE | 指導試合フラグ（true=指導試合。勝者=指導した側、敗者=指導された側） |
| opponent_name | VARCHAR(100) | — | 未登録相手名（簡易登録用） |
| player1_kyu_rank | VARCHAR(10) | — | 対戦時点の選手1の級位スナップショット（出典: introspect から追加。旧ドキュメント未記載） |
| player2_kyu_rank | VARCHAR(10) | — | 対戦時点の選手2の級位スナップショット（出典: introspect から追加。旧ドキュメント未記載） |
| notes | TEXT | — | コメント（出典: SPEC のみ） |
| venue_id | BIGINT | FK(venues.id) ON DELETE SET NULL | 試合が行われた会場ID（NULL可。古いデータで backfill 不可・PracticeSession 削除済みの場合は NULL）（出典: DESIGN のみ） |
| created_by | BIGINT | NOT NULL | 登録者 |
| updated_by | BIGINT | NOT NULL | 更新者 |
| created_at | TIMESTAMP | NOT NULL | — |
| updated_at | TIMESTAMP | NOT NULL | — |

（本番 introspect 照合済み: created_at/updated_at は TIMESTAMP。⚠解消）

インデックス: `matches_pkey(id)`, `idx_matches_date`, `idx_matches_date_player1`, `idx_matches_date_player2`, `idx_matches_winner`, `idx_matches_date_match_number`, `idx_matches_venue(venue_id)`, `uq_matches_date_number_players(match_date, match_number, player1_id, player2_id)` UNIQUE（出典: DESIGN のみ）

特殊ロジック（出典: DESIGN のみ）:
- `@PrePersist`/`@PreUpdate`で player1_id < player2_id を自動保証
- `venue_id` は新規登録時に `MatchService.resolveVenueId()` で自動決定:
  1. 試合参加者（簡易登録は `request.playerId`、詳細登録は `player1_id` / `player2_id`）が同日・同試合番号に active 参加（`status IN ('WON','PENDING')`）した `practice_sessions` の `venue_id` を集約し、一意であれば採用（`pp.match_number IS NULL` の legacy データも含む）
  2. 同日の `practice_sessions` の `venue_id` が一意であればその値（複数会場が混在する日は NULL のまま、誤割り当てを回避）
  3. いずれにも該当しなければ NULL
- `created_by` ではなく試合参加者を基準にするのは、ADMIN 代理登録時に管理者の参加 venue が誤って入るのを防ぐため
- `match_number` でも絞るのは、同日複数会場で選手が両方に参加している場合に、対象試合と無関係の参加会場が混ざって venue_id が一意決定できないのを防ぐため
- 更新時は `venue_id` を変更しない（不変）

---

#### match_personal_notes（個人メモ・お手付き記録）（出典: DESIGN のみ）

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | レコードID |
| match_id | BIGINT | NOT NULL, FK(matches.id, CASCADE) | 対象試合ID |
| player_id | BIGINT | NOT NULL, FK(players.id, RESTRICT) | 記録者ID |
| notes | TEXT | — | 個人メモ |
| otetsuki_count | INT | CHECK(0〜20) | お手付き回数（nullは未入力） |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 登録日時 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 更新日時 |

（本番 introspect 照合済み: created_at/updated_at は TIMESTAMP。⚠解消。CHECK制約は introspect の対象外のため未照合のまま残す）

制約:
- UNIQUE: `uq_match_personal_notes` (match_id, player_id)
- CHECK: `otetsuki_count >= 0 AND otetsuki_count <= 20`

インデックス: `match_personal_notes_pkey(id)`, `idx_match_personal_notes_player` (player_id, match_id)

---

#### match_pairings（対戦組み合わせ）

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| session_date | DATE | NOT NULL | 練習日 |
| match_number | INT | NOT NULL | 試合番号 |
| player1_id | BIGINT | NOT NULL, FK | 選手1 |
| player2_id | BIGINT | NOT NULL, FK | 選手2 |
| locked | BOOLEAN | NOT NULL, DEFAULT FALSE | 手動ロックフラグ（結果未入力でも自動組み合わせ・回戦削除から保護。一括保存ではリクエストの `locked` を反映して削除→再作成し永続化する。`add_locked_to_match_pairings.sql`）（出典: DESIGN のみ） |
| created_by | BIGINT | NOT NULL | 作成者 |
| created_at | TIMESTAMP | NOT NULL | — |
| updated_at | TIMESTAMP | NOT NULL | — |

（本番 introspect 照合済み: created_at/updated_at は TIMESTAMP。⚠解消）

インデックス: `match_pairings_pkey(id)`
ユニークインデックス（出典: DESIGN のみ）: `uq_match_pairings_date_number_players (session_date, match_number, LEAST(player1_id, player2_id), GREATEST(player1_id, player2_id))`（順不同ペアの関数ユニークインデックス。同日・同試合番号・同ペアの重複登録を防止。`match_pairings` は `matches` と異なり `player1_id < player2_id` を正規化しないため LEAST/GREATEST で順不同に正規化する。Hibernate では関数インデックスを管理できないため Render PostgreSQL へ手動適用。Issue #900）

対戦相手キャンセルの反映（read-time・スキーマ変更なし、pairing-cancelled-opponent。出典: DESIGN のみ）: 参加者キャンセル（`practice_participants.status=CANCELLED`）の組み合わせ表示への反映は `match_pairings` を一切変更せず、取得API（`MatchPairingService.getByDate` / `getByDateAndMatchNumber` → `enrichWithCancellation`）が `(その日のセッション, 選手, 試合番号)` の CANCELLED を引き当てて DTO に `player1Cancelled` / `player2Cancelled` を付与する read-time 方式。`PracticeParticipantRepository.findBySessionIdIn` で当日セッション群の全参加者を1クエリ取得し、メンバーシップ（誰がどの試合でどのセッションに居るか）と CANCELLED 集合 `(sessionId:playerId:matchNumber)` を導出。各組は**両選手が共に属するセッション（＝その組が行われたセッション）を解決し、そのセッション内のキャンセルだけを反映する**（org非指定で同一選手が同日同試合番号で別団体セッションにも居る場合のクロス団体誤反映を防ぐ）。試合単位判定・`match_number=null` の抜け番マーカーは除外。org指定=当該団体セッション / org非指定=その日の全セッションを対象にする。閲覧モードは取消線＋グレー名＋「キャンセル」タグ、編集モードは当該スロットを空きに（フロント純粋関数 `materializeCancelledSlots`）。両方キャンセルの組は閲覧で非表示・編集で除去。

重複・ゾンビ組み合わせ対策（`MatchPairingService`、Issue #900。出典: DESIGN のみ）:
- `create`: 同一（日・試合番号・順不同ペア）が既存なら重複作成せず既存を返す（冪等）。
- `createBatch`: リクエスト内の同一ペア（順不同）を初出のみ採用して重複排除し、保護対象（結果入力済み）ペアと同一の新規も除外する。フロントの組み合わせ state に同一ペアが多重蓄積したペイロードでも1行に正規化される。保存時の保護対象は**結果入力済み（`hasResult`）のみ**で、手動ロック組はリクエストの `locked` を反映して削除→再作成する（`MatchPairingCreateRequest.locked`（null=false）を `MatchPairing.locked` に設定。これによりロック＝`locked=true`・解除＝`locked=false` の両方が保存で永続化される）。`auto-match` / `deleteByDateAndMatchNumber` の保護判定は `hasResult OR locked`（`isLockedPairing`）のまま不変。
- `createBatch` / `deleteByDateAndMatchNumber`: 「当日のどのセッションでも両選手が同時に参加していない」かつ結果なしのペア（ゾンビ）も削除する。組織スコープのフィルタ（`filterPairingsBySession`、両選手が同一団体セッション参加者のみ対象）から漏れるペア（両者非参加／片方だけ参加／両者が別セッション・別団体に分かれて参加）は従来は削除されず、再生成のたびに重複が累積し、フロントの一括削除でも消せなかった。ただし**組織スコープ操作では他団体データを破壊しないよう**、両選手とも「自団体セッション参加者」または「当日どのセッションの参加者でもない（誰のものでもない）」ペアに限定する（他団体参加者を含むペアは保護）。SUPER_ADMIN（organizationId=null）は団体横断で掃除する。
- `updatePlayer`: 差し替え後のペアが同日・同試合番号で既存と重複する場合は検証エラー（ユニークインデックス由来の500を回避）。
- `createBatch` の待機者（抜け番）登録（Issue #958）: 待機者は `practice_participants` に `matchNumber=null` の「抜け番（当日参加者）」レコードとして登録される。**非破壊更新**（リクエストの待機者のうち抜け番レコード未保有の選手だけ追加し、既存の抜け番レコードは削除しない・冪等）。待機は本来「試合ごと」だが抜け番レコードは「セッション共有（`matchNumber=null`）」のため、旧実装のように保存ごとにセッション全体の抜け番を全削除→当該試合分のみ再生成すると、別試合の当日参加者（ロスター未登録で抜け番レコードだけを持つ選手）の参加者レコードまで巻き込んで消え、以後その選手を含む保存がセッション参加者検証で 403（`対象セッションの参加者でない選手は…`）になり続けた。不要な抜け番の整理は抜け番活動（`ByeActivity`）の「全ABSENT削除／復元」ライフサイクルに委ねる。なおフロント（`PairingGenerator.handleSave`）は保存失敗時にサーバーのエラーメッセージ（`err.response?.data?.message`）を優先表示し、どの選手が・なぜ弾かれたかを示す。

---

#### bye_activities（抜け番活動記録）

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| session_date | DATE | NOT NULL | 練習日 |
| match_number | INT | NOT NULL | 試合番号 |
| player_id | BIGINT | NOT NULL, FK | 抜け番の選手 |
| activity_type | VARCHAR(255) | NOT NULL | 活動種別（READING/SOLO_PICK/OBSERVING/ASSIST_OBSERVING/OTHER/ABSENT）（本番 introspect 照合済み: 旧記載は VARCHAR(20)） |
| free_text | VARCHAR(255) | — | 「その他」選択時の自由記述 |
| created_by | BIGINT | NOT NULL | 登録者 |
| updated_by | BIGINT | NOT NULL | 更新者 |
| created_at | TIMESTAMP | NOT NULL | — |
| updated_at | TIMESTAMP | NOT NULL | — |
| deleted_at | TIMESTAMP | NULL | 論理削除日時 |

ユニーク制約: `uk_bye_activities_unique (session_date, match_number, player_id) WHERE deleted_at IS NULL`（部分ユニークインデックス。SPECでは制約名の明示なし）
インデックス: `bye_activities_pkey(id)`, `idx_bye_activities_date`, `idx_bye_activities_date_match`, `idx_bye_activities_player`, `idx_bye_activities_deleted_at`

---

#### practice_sessions（練習日情報）

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| session_date | DATE | NOT NULL | 練習日 |
| total_matches | INT | NOT NULL | 試合数（予定試合数） |
| venue_id | BIGINT | — | 会場ID |
| notes | TEXT | — | 備考・メモ |
| start_time | TIME | — | 開始時刻 |
| end_time | TIME | — | 終了時刻 |
| capacity | INT | — | 定員（抽選判定に使用） |
| reservation_confirmed_at | TIMESTAMP | — | 隣室予約確認日時（NULLは未確認） |
| organization_id | BIGINT | NOT NULL, FK | 団体ID（organizations.id） |
| created_by | BIGINT | NOT NULL | — |
| updated_by | BIGINT | NOT NULL | — |
| created_at | TIMESTAMP | NOT NULL | — |
| updated_at | TIMESTAMP | NOT NULL | — |

（本番 introspect 照合済み: reservation_confirmed_at/created_at/updated_at は TIMESTAMP。⚠解消）

ユニーク制約: `uk_session_date_organization (session_date, organization_id)`（DESIGNでは `UNIQUE (session_date, organization_id)` と表記）
インデックス: `practice_sessions_pkey(id)`, `idx_session_date`, `ukm60bbd1y9qs46eirlu5c2udhh` UNIQUE(session_date)（本番のみ・(session_date, organization_id) 複数団体化以前の旧UNIQUE制約が残存していると推測。実質 `uk_session_date_organization` に包含されるため実害なし）

---

#### practice_participants（練習参加者）

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| session_id | BIGINT | NOT NULL | 練習日ID |
| player_id | BIGINT | NOT NULL | 選手ID |
| match_number | INT | — | 参加試合番号（1～7、NULLは全試合） |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'WON' | 参加ステータス（PENDING/WON/WAITLISTED/OFFERED/DECLINED/CANCELLED） |
| waitlist_number | INT | — | キャンセル待ち番号 |
| lottery_id | BIGINT | — | 抽選実行ID |
| cancel_reason | VARCHAR(50) | — | キャンセル理由コード（HEALTH/WORK_SCHOOL/FAMILY/TRANSPORT/OTHER） |
| cancel_reason_detail | TEXT | — | キャンセル理由詳細（OTHER時） |
| cancelled_at | TIMESTAMP | — | キャンセル日時 |
| offered_at | TIMESTAMP | — | 繰り上げ通知日時 |
| offer_deadline | TIMESTAMP | — | 繰り上げ応答期限 |
| responded_at | TIMESTAMP | — | 繰り上げ応答日時 |
| dirty | BOOLEAN | NOT NULL, DEFAULT TRUE | アプリ側操作済みフラグ（true=伝助への書き戻し対象、false=伝助から取り込み済み） |
| created_at | TIMESTAMP | NOT NULL | — |
| updated_at | TIMESTAMP | NOT NULL | — |

（本番 introspect 照合済み: status は VARCHAR(20)、cancelled_at/offered_at/offer_deadline/responded_at/created_at/updated_at は TIMESTAMP。⚠解消）
⚠要確認: status の値域 — DESIGNの ParticipantStatus 列挙型には `WAITLIST_DECLINED`（キャンセル待ち辞退）が含まれるが、SPECのカラム説明の値リストには含まれない（DBのVARCHAR型からは値域を確定できない意味論の差のため要確認のまま残す）

一意制約: `uk_session_player_match(session_id, player_id, match_number)`（DESIGNでは `UNIQUE (session_id, player_id, match_number)` と表記）
インデックス: `practice_participants_pkey(id)`, `idx_participant_session` (session_id), `idx_participant_player` (player_id)（出典: DESIGN のみ）

ParticipantStatus列挙型（出典: DESIGN のみ）:
- `PENDING` - 参加希望（抽選前）
- `WON` - 当選（参加確定）
- `WAITLISTED` - キャンセル待ち
- `OFFERED` - 繰り上げ通知済み（応答待ち）
- `DECLINED` - 繰り上げ辞退（明示的辞退または応答期限切れ）
- `CANCELLED` - 当選後キャンセル
- `WAITLIST_DECLINED` - キャンセル待ち辞退

---

#### densuke_member_mappings（伝助メンバーIDキャッシュ）

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| densuke_url_id | BIGINT | NOT NULL | 伝助URL ID（FK: densuke_urls.id） |
| player_id | BIGINT | NOT NULL | 選手ID（FK: players.id） |
| densuke_member_id | VARCHAR(50) | NOT NULL | 伝助メンバーID（`mi` パラメータ値） |
| created_at | TIMESTAMP | NOT NULL | — |

（本番 introspect 照合済み: created_at は TIMESTAMP。⚠解消）

一意制約: `(densuke_url_id, player_id)`（`uk5m97uf5l31676nufncoswc4xf`）に加え、`(densuke_url_id, densuke_member_id)`（`uklccn4bn0fa51ck0admo5npf5q` / `uq_densuke_member_mappings_url_member`）も本番に存在（旧ドキュメント未記載・introspectで判明）
インデックス: `densuke_member_mappings_pkey(id)`

---

#### densuke_row_ids（伝助行IDキャッシュ）

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| densuke_url_id | BIGINT | NOT NULL | 伝助URL ID（FK: densuke_urls.id） |
| densuke_row_id | VARCHAR(50) | NOT NULL | `join-{id}` フィールドのID値 |
| session_date | DATE | NOT NULL | 対象日付 |
| match_number | INT | NOT NULL | 対象試合番号 |
| created_at | TIMESTAMP | NOT NULL | — |

（本番 introspect 照合済み: created_at は TIMESTAMP。⚠解消）

一意制約: `(densuke_url_id, session_date, match_number)`（`ukk3m4w4pydaswpretprscpfepa`）
インデックス: `densuke_row_ids_pkey(id)`

---

#### densuke_deletion_candidates（伝助削除候補）（出典: SPEC のみ）

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| densuke_url_id | BIGINT | NOT NULL | 伝助URL ID（FK: densuke_urls.id） |
| organization_id | BIGINT | NOT NULL | 所属団体ID |
| session_date | DATE | NOT NULL | 対象日付 |
| match_number | INT | NOT NULL | 対象試合番号 |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'PENDING' | PENDING / APPROVED / REJECTED |
| detected_at | TIMESTAMP | NOT NULL, DEFAULT now() | 検知日時 |
| notified_at | TIMESTAMP | — | LINE通知送信日時 |
| resolved_at | TIMESTAMP | — | 承認/却下日時 |
| resolved_by | BIGINT | — | 承認/却下した管理者ID（FK: players.id） |

一意制約: `(densuke_url_id, session_date, match_number)`
インデックス: `densuke_deletion_candidates_pkey(id)`, `idx_densuke_deletion_candidates_org_status(organization_id, status)`（出典: introspect から追加）

---

#### densuke_templates（伝助ページ作成テンプレート）（出典: DESIGN のみ）

団体ごとに1レコード保持。伝助ページ自動作成時のタイトル・説明・連絡先メアドのデフォルト値を保存する。

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | ID |
| organization_id | BIGINT | NOT NULL, UNIQUE, FK | 団体ID（organizations.id） |
| title_template | VARCHAR(200) | NOT NULL | タイトルテンプレート（プレースホルダー `{year}`、`{month}`、`{organization_name}` を置換） |
| description | TEXT | — | 伝助イベント説明文 |
| contact_email | VARCHAR(255) | — | 主催者連絡先メアド（伝助の `email` フィールドに送信。控えメールの受信先） |
| created_at | TIMESTAMP | NOT NULL, DEFAULT now() | 作成日時 |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT now() | 更新日時 |

（本番 introspect 照合済み: created_at/updated_at は TIMESTAMP。⚠解消）

制約: UNIQUE (organization_id)
インデックス: `densuke_templates_pkey(id)`, `densuke_templates_organization_id_key` UNIQUE (organization_id)

---

#### system_settings（システム設定）

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| setting_key | VARCHAR(100) | NOT NULL | 設定キー |
| setting_value | VARCHAR(255) | NOT NULL | 設定値 |
| organization_id | BIGINT | NOT NULL, FK | 団体ID |
| updated_at | TIMESTAMP | NOT NULL | — |
| updated_by | BIGINT | — | 更新者ID |

（本番 introspect 照合済み: updated_at は TIMESTAMP。⚠解消）

一意制約: `(setting_key, organization_id)`（`uk_system_settings_key_org` / `uk3uukkredspdgpq65rggt9l5m4`。同一内容の索引が2本存在＝命名の重複、実害なし）
インデックス: `system_settings_pkey(id)`

初期データ: `lottery_deadline_days_before` = `0`（締切日数：月初から何日前。0=前月末日の0時）

---

#### player_profiles（選手情報履歴）

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| player_id | BIGINT | NOT NULL | 選手ID |
| dan | VARCHAR(255) | NOT NULL | 段位（無～八） |
| grade | VARCHAR(255) | NOT NULL | 級位（A～E） |
| karuta_club | VARCHAR(200) | NOT NULL | 所属会 |
| valid_from | DATE | NOT NULL | 有効開始日 |
| valid_to | DATE | — | 有効終了日（NULL=現在有効） |
| created_at | TIMESTAMP | NOT NULL | — |
| updated_at | TIMESTAMP | NOT NULL | — |

（本番 introspect 照合済み: dan/grade/karuta_club は VARCHAR かつ NOT NULL（SPECの「制約なし」表記は誤り）、created_at/updated_at は TIMESTAMP。⚠すべて解消）

インデックス: `player_profiles_pkey(id)`, `idx_player_date(player_id, valid_from, valid_to)`（DESIGN表記どおりと判明。SPECの `(player_id, valid_from)` のみの表記は誤り）, `idx_valid_to(valid_to)`

---

#### venues（練習会場マスタ）

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| name | VARCHAR(200) | NOT NULL, UNIQUE | 会場名 |
| default_match_count | INT | NOT NULL | デフォルト試合数 |
| created_at | TIMESTAMP | NOT NULL | — |
| updated_at | TIMESTAMP | NOT NULL | — |
| capacity | INT | — | 定員（introspect から追加。抽選判定に使用の可能性、業務説明は未整備） |

（本番 introspect 照合済み: created_at/updated_at は TIMESTAMP。⚠解消。capacity 列は旧ドキュメント未記載のため追加）

インデックス: `venues_pkey(id)`, `ukc2om8hy3unm9k7dxwddow2rv1` UNIQUE (name)

---

#### venue_match_schedules（会場試合時間割）

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| venue_id | BIGINT | NOT NULL | 会場ID |
| match_number | INT | NOT NULL | 試合番号 |
| start_time | TIME | NOT NULL | 開始時刻 |
| end_time | TIME | NOT NULL | 終了時刻 |

制約: UNIQUE (venue_id, match_number)（出典: DESIGN のみ。SPECには制約記載なし）
インデックス: `venue_match_schedules_pkey(id)`, `uk_venue_match` UNIQUE (venue_id, match_number)

---
