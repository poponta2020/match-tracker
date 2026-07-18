# 選手・認証・団体

> **責務:** 選手アカウント・ロール・認証・団体管理・選手プロフィール履歴の仕様
> **関連画面:** `/players/new`（管理者による選手直接作成）、`/settings/organizations`（参加団体の選択）、`/profile/edit?changePassword=true`（パスワード変更強制時のリダイレクト先）
> **主要実装:**
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/PlayerController.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/PlayerProfileController.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/InviteTokenController.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/OrganizationController.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PlayerService.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PlayerProfileService.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/InviteTokenService.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/OrganizationService.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/Player.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/PlayerOrganization.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/PlayerProfile.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/Organization.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/InviteToken.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/PlayerDto.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/PlayerCreateRequest.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/PlayerUpdateRequest.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/PlayerBulkUpdateRequest.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/LoginResponse.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/InviteTokenResponse.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/PublicRegisterRequest.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/OrganizationDto.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/interceptor/RoleCheckInterceptor.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/util/AdminScopeValidator.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/util/OrganizationScopeResolver.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/MatchPairingController.java`（団体スコープ検証の一部を保持。詳細は docs/spec/matching.md 参照）
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeSessionService.java`（`checkScopeByDate`。詳細は docs/spec/practice-sessions.md 参照）
> - `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx`（詳細は docs/spec/matching.md 参照）
> - `karuta-tracker-ui/src/pages/matches/BulkResultInput.jsx`（詳細は docs/spec/matches.md 参照）

## 機能仕様

### ロール定義

| ロール | 権限 |
|---|---|
| `SUPER_ADMIN` | 全機能・全団体横断。選手の作成・削除・ロール変更、練習日管理、会場管理 |
| `ADMIN` | 自団体の練習日管理、組み合わせ作成、一括結果入力、伝助連携。1団体にのみ紐づく |
| `PLAYER` | 自分の試合記録、出欠登録、閲覧系機能。参加する練習会を自分で選択 |

新規登録時のデフォルトロールは `PLAYER`。

### 選手登録方式

選手の登録には2つの方法がある。

1. **管理者による直接作成**: SUPER_ADMIN が `/players/new` から選手を作成（パスワード設定含む）
2. **招待リンクによるセルフ登録**: ADMIN以上が招待トークンを発行し、URLを共有。新規参加者がそのURLから自分で登録
   - **グループ招待（MULTI_USE）**: 有効期限72時間内なら何人でも登録可能。グループへの一括案内向け
   - **個人招待（SINGLE_USE）**: 1回限り使用可能。特定個人への招待向け
   - 招待トークンは団体に紐づき、登録時にその団体の参加設定が自動で初期値となる

### 認証方式

認証はサーバが発行・検証するトークンで行う。クライアントの自己申告（かつての `X-User-Role` / `X-User-Id` ヘッダー）は**一切参照しない**。

- **ログイン**: 選手名（`name`）+ パスワードで認証し、成功時にトークンを発行する
- **パスワード**: BCrypt ハッシュで保存・照合（最低8文字）。平文は保存しない。書き込み経路（選手作成・選手更新・招待登録・伝助の自動登録）はすべてサービス層の `PasswordEncoder` を通す
- **パスワード変更強制**: `require_password_change` フラグが `true` の場合、ログイン後に `/profile/edit?changePassword=true` へリダイレクトし、パスワード変更を強制する。変更完了後にフラグは自動的に `false` にリセットされる
- **セッション管理**: ログイン成功時にプレイヤー情報（`organizationIds` を含む）を `localStorage` に保存し、トークンは `authToken` に保存する。以降の全リクエストに `Authorization: Bearer <token>` を付与する
- **トークン**: 32バイトの乱数（hex 64文字）の不透明トークン。DB（`auth_tokens`）には SHA-256 ハッシュのみを保存し、生トークンは保存しない。有効期限は発行から約1年
- **失効**: パスワード変更・選手の論理削除でその選手の全トークンを失効させる。ログアウト（`POST /api/players/logout`）は当該トークンのみを失効させる
- **認証の既定（deny by default）**: `/api/**` は既定で認証必須。下記の公開エンドポイントのみ未認証で通る。`@RequireRole` の有無は**認可判定にのみ**使い、認証要否には使わない（アノテーションの付け忘れで穴が開かない構造にするため）
- **認可**: `@RequireRole` アノテーションを `RoleCheckInterceptor` が検査する。認証されていなければ **401**、認証済みだが権限が足りなければ **403** を返す
- フロントエンドは 401 を受けると `localStorage` をクリアして `/login` へ遷移する

#### 未認証で通る公開エンドポイント

これ以外の `/api/**` はすべて 401 になる。

| エンドポイント | 理由 |
|---|---|
| `POST /api/players/login` | ログイン（認証前） |
| `GET /api/invite-tokens/validate/{token}` | 招待リンクの検証（認証前） |
| `POST /api/invite-tokens/register` | 招待リンクからの公開登録（認証前） |
| `GET /api/organizations` | 招待登録画面が未ログインで呼ぶ（**完全一致**。`/api/organizations/players/{id}` は含まない） |
| `GET /api/venue-reservation-proxy/view`・`/fetch/**` | capability トークンで保護済み |
| `POST /api/line/webhook/{lineChannelId}` | LINE 署名検証で保護済み（インターセプタ対象外） |
| `/api/line-chat-worker/**` | サービストークンで保護済み（インターセプタ対象外） |
| `/ping`・`/ical/**` | `/api/**` の外のため対象外（iCal はフィードトークンで保護） |

> **認可の粒度は別問題**: 本方式が保証するのは「ログインしていること」と「ロールを偽装できないこと」まで。ログイン済みの会員が他人の `playerId` を指定して他人のデータを参照できる問題（`GET /api/home?playerId=` 等）は未解決で、別 Issue で扱う。

### 選手プロパティ

| フィールド | 型 | 必須 | 説明 |
|---|---|---|---|
| `name` | String(100) | Yes | ログインID兼表示名。一意制約 |
| `password` | String(255) | Yes | BCryptハッシュ。最低8文字 |
| `gender` | Enum | Yes | `男性` / `女性` / `その他` |
| `dominantHand` | Enum | Yes | `右` / `左` / `両` |
| `danRank` | Enum | No | `無段`〜`八段` |
| `kyuRank` | Enum | No | `E級`〜`A級`。未設定（NULL）の場合、画面上では「初心者」と表示 |
| `karutaClub` | String(200) | No | 所属かるた会 |
| `remarks` | Text | No | 備考 |
| `role` | Enum | Yes | デフォルト `PLAYER` |
| `requirePasswordChange` | Boolean | No | パスワード変更要求フラグ（デフォルト `false`）。伝助からの自動登録時に `true` に設定 |
| `adminOrganizationId` | Long | No | ADMINロールのプレイヤーの所属団体ID。PLAYER/SUPER_ADMINはNULL |

削除は論理削除（`deleted_at` にタイムスタンプを設定）。

### 団体管理

#### 団体定義

本システムは2団体固定で運用する。

| コード | 団体名 | テーマカラー | 締め切りタイプ |
|---|---|---|---|
| `wasura` | わすらもち会 | 緑 `#22c55e` | SAME_DAY（練習当日12:00） |
| `hokudai` | 北海道大学かるた会 | 赤 `#ef4444` | MONTHLY（前月N日） |

#### 参加練習会の管理

- ユーザーは設定画面（`/settings/organizations`）から参加する練習会を選択する
- 最低1つは必須。「所属」ではなく「参加する練習会を選ぶ」概念
- 登録していない団体の練習日・通知は表示されない
- 参加団体の取得・変更APIは本人またはSUPER_ADMINのみ実行可能（他人の参加団体は操作不可）
- **自動所属**: 練習への参加登録時、その練習が属する団体に未所属であれば自動的に所属させる（通知設定のデフォルトレコードも作成）。伝助経由で新規選手を登録した場合も、その団体に自動所属する。退会は手動操作のみ

#### 締め切りルール

**わすらもち会（SAME_DAY）:**
- 練習当日12:00が締切。抽選なし（先着順）
- 当日12:00以降に「当日のセッション」へ実際の変更（参加チェックの増減）を伴う保存を行う場合のみ確認ダイアログ表示（管理者への連絡確認、自己申告ベース）。同月内の別日だけを変更する保存では当日セッションが触られないためダイアログは出さない
- 定員到達で即キャンセル待ち。参加登録時ステータスは即WON/WAITLISTED（PENDINGなし）

**北海道大学かるた会（MONTHLY）:**
- 前月N日（設定可能）が締切。抽選あり
- 締切後は空きあれば登録可、キャンセルはキャンセル専用画面から
- 抽選後の新規登録者はキャンセル待ちの最後尾

（締め切り後の抽選ロジックの詳細は docs/spec/lottery.md 参照）

#### 管理者の団体スコープ

- ADMINは1つの団体にのみ紐づく（SUPER_ADMINが設定）
- ADMINは自団体のリソースのみ操作可能（練習日の作成・編集・削除、組み合わせ削除、抽選再実行・手動編集・結果通知、LINE送信、抜け番活動管理、伝助管理、システム設定）。他団体は一般ユーザーとしての閲覧のみ
- 対戦組み合わせの作成・自動マッチング・選手差し替え・参加者追加・全削除（日付＋試合番号）・結果込みリセットは PLAYER にも開放しており、PLAYER は所属団体（複数可）のセッションに対してのみ操作可能。個別削除（`DELETE /api/match-pairings/{id}`）と参加者1名削除（`removeParticipantFromMatch`・UI未配線）のみ ADMIN+ 専用
- スコープ検証は ADMIN は共通ユーティリティ `AdminScopeValidator`、PLAYER は `MatchPairingController` 内の `validateScopeByDate` / `validateScopeByPairingId`（組み合わせ系）および `PracticeSessionService.checkScopeByDate`（参加者追加）で実施
- **既知の制限**:
  - PLAYER が複数団体に所属し、同日に複数所属団体のセッションが存在する場合、書き込み API の対象団体が一意に決まらないため `ForbiddenException` で拒否される（安全側フォールバック）。`/pairings` / `/matches/bulk-input` から `organizationId` を渡す設計拡張は別 Issue で対応予定
  - 対戦組み合わせ書き込み（`create` / `createBatch` / `updatePlayer` / `auto-match`）および抜け番活動一括（`bye-activities/batch`）の選手スコープ検証は、対象セッションの **全参加者**（`PracticeParticipant.session_id` ベース）で行っており、`PracticeParticipant.matchNumber` や参加ステータス（WON / PENDING / CANCELLED / WAITLISTED）までは見ていない。そのため UI が試合番号・ステータスで除外している選手 ID を PLAYER が直接 API で渡せば、別試合番号にしか割り当たっていない選手や対象外ステータスの選手でも書き込みに含められる。試合番号＋ステータスを含む厳密スコープは後続課題として別 Issue で対応予定（UI 経路では従来通り絞り込み済み）
  - **読み取り系 API の団体スコープ**: PLAYER 開放した `/pairings` / `/matches/bulk-input/:sessionId` 画面が呼ぶ読み取り API（`GET /api/match-pairings/date`、`GET /api/practice-sessions/{id}` 等）は `organizationId` 未指定時に **全団体検索**として動作する。`OrganizationScopeResolver` は PLAYER が未指定の場合 `null`（組織非限定）を返す仕様のため、同日に複数団体のセッションがある日付で PLAYER が `/pairings` を開くと他団体のペアリングも返り得る。また `PracticeSessionController.getSessionById` は `@RequireRole` も団体スコープ検証も持たないため、PLAYER が URL の `sessionId` を他団体のものに変更すれば他団体セッション情報を閲覧できる。これらは書き込み系のスコープ強制と非対称な状態で、書き込み（`resolveOrganizationIdForScopedWrite`）が他団体への書き込みを 403 拒否する一方、読み取りには同等の強制がない。読み取り API への団体スコープ追加（`@RequireRole`、`OrganizationScopeResolver` の PLAYER 未指定時の所属団体一意解決、`PairingGenerator` / `BulkResultInput` からの `organizationId` 一貫伝播）は後続課題として別 Issue で対応予定
- SUPER_ADMINは全団体横断の管理権限

#### 通知の団体分離

- 通知は団体ごとに分離。登録していない団体の通知は送信しない
- Webプッシュ・LINE通知とも、種別ごとのON/OFFを団体単位で設定可能
- グローバルの有効/無効切り替えは団体横断で1つ
- 管理者向け通知: ADMINは自団体のみ、SUPER_ADMINは全団体
- 新団体登録時のデフォルトは全ON

（通知の詳細仕様は docs/spec/notifications.md 参照）

### 選手プロフィール履歴

段位・級位の変遷を履歴管理する。`valid_from` 〜 `valid_to` の期間で有効なプロフィールを判定。

| フィールド | 型 | 必須 | 説明 |
|---|---|---|---|
| `playerId` | Long | Yes | 対象選手 |
| `grade` | Enum | No | 級位（A〜E） |
| `dan` | Enum | No | 段位（無段〜八段） |
| `karutaClub` | String | No | 所属かるた会 |
| `validFrom` | LocalDate | Yes | 有効開始日 |
| `validTo` | LocalDate | No | 有効終了日。NULLの場合は現在有効 |

## API

### 団体 (`/api/organizations`)

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

### 選手 (`/api/players`)

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| POST | `/login` | Public | ログイン（レスポンスに `organizationIds` を含む） |
| GET | `/` | ALL | 全アクティブ選手取得（各選手に `organizationIds` を含む） |
| GET | `/{id}` | ALL | ID指定で取得 |
| GET | `/search?name=` | ALL | 名前部分一致検索 |
| GET | `/role/{role}` | ALL | ロール別取得 |
| GET | `/count` | ALL | アクティブ選手数 |
| POST | `/` | SUPER_ADMIN | 選手作成 |
| PUT | `/bulk` | ADMIN+ | 複数選手の一括更新（性別・級・段位・かるた会の上書き＋所属練習会の追加）。`/{id}` より優先してマッチ |
| PUT | `/{id}` | ALL | 選手更新 |
| DELETE | `/{id}` | SUPER_ADMIN | 選手論理削除 |
| PUT | `/{id}/role?role=` | SUPER_ADMIN | ロール変更 |

#### POST /api/players/login
**説明**: ログイン。成功時に認証トークンを発行する
**権限**: 不要（公開エンドポイント）
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
  "requirePasswordChange": false,
  "token": "3f2a…（hex 64文字）"
}
```

`token` は以降の全リクエストの `Authorization: Bearer` に載せる。サーバ側では復元できないため、クライアントが保存する。

#### POST /api/players/logout
**説明**: ログアウト。`Authorization` ヘッダーのトークンのみを失効させる（他端末のトークンは維持）
**権限**: 認証必須（ロール不問）
**レスポンス**: `204 No Content`

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
**権限**: `@RequireRole(SUPER_ADMIN, ADMIN)`。対象選手の団体スコープ検証は行わない（トラストベースでADMINも全選手を編集可）
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
- `gender / kyuRank / danRank / karutaClub` は **指定された項目のみ** `players` 列を上書き（`null` は据え置き。級↔段位の整合は単体更新と同様にフロントで算出する）
- `addOrganizationIds` は既存に無い団体のみ `player_organizations` に追加（**追加のみ・冪等**。`(player_id, organization_id)` ユニーク制約で二重登録を防止。削除は提供しない）。追加経路は単体更新・招待登録と共通の `OrganizationService.ensurePlayerBelongsToOrganization` で、団体別の通知設定（push/LINE）も初期化する
- `addOrganizationIds` は保存前に全件の団体存在を検証し、存在しないIDが含まれる場合は更新前に 404（ResourceNotFound）で**全件失敗**（FK の無い `player_organizations` に孤児データを作らない）
- `@Transactional` による all-or-nothing（1件でも失敗すれば全件ロールバック）
- `"/bulk"` は `"/{id}"` より優先してマッチするため単体更新と競合しない

### 招待トークン (`/api/invite-tokens`)

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| POST | `/?type=&createdBy=&organizationId=` | ADMIN+ | 招待トークン生成（MULTI_USE / SINGLE_USE）。`organizationId` は SUPER_ADMIN は必須、ADMIN はサーバ側で自団体に固定される。NULL の場合は 400、存在しない団体IDの場合は 404 を返す |
| GET | `/validate/{token}` | Public | トークン有効性検証 |
| POST | `/register` | Public | トークン付き公開登録 |

**トークン種別**:
- `MULTI_USE`: グループ招待用。有効期限（72時間）内なら何人でも登録可能
- `SINGLE_USE`: 個人招待用。1回限り使用可能（有効期限72時間）

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

### 選手プロフィール (`/api/player-profiles`)

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `/current/{playerId}` | ALL | 現在のプロフィール |
| GET | `/at-date/{playerId}?date=` | ALL | 特定日時点のプロフィール |
| GET | `/history/{playerId}` | ALL | プロフィール履歴 |
| POST | `/` | ALL | プロフィール作成 |
| PUT | `/{profileId}/valid-to?validTo=` | ALL | 有効終了日設定 |
| DELETE | `/{profileId}` | ALL | プロフィール削除 |
