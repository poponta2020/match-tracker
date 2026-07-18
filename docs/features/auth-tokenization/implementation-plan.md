---
status: completed
---
# 認証のトークン化 実装手順書

## 技術設計の確定事項

### トークン方式: 不透明ランダムトークン＋DB 永続
- 生成: `SecureRandom` 32バイト → hex 64文字（既存 `Player.icalFeedToken` と同じ生成様式に揃える）
- 保存: **DB には SHA-256 ハッシュのみを保存**し、生トークンは保存しない（DB 漏洩時にトークンを再利用されないため）
- BCrypt はトークンには使わない。毎リクエストの照合には重すぎ、256bit エントロピーの乱数には総当たり耐性が元々あるため。BCrypt はパスワードのみに使う
- 署名付きトークン（JWT）を採らない理由: 失効要件（AC-12/13/14）があり、かつ有効期限が約1年と長いため、サーバ側で状態を持つ方式でないと救済手段が無くなる。現行インターセプタは保護 EP ごとに既に `playerRepository.findById` を実行しているため、DB 参照の追加コストは限定的

### テーブル `auth_tokens`
| 列 | 型 | 備考 |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `player_id` | BIGINT NOT NULL | 選手単位の一括失効に使う。索引を張る |
| `token_hash` | VARCHAR(64) NOT NULL UNIQUE | SHA-256 hex。検索キー |
| `issued_at` | TIMESTAMP NOT NULL | |
| `expires_at` | TIMESTAMP NOT NULL | 発行 +1年 |
| `revoked_at` | TIMESTAMP NULL | NULL = 有効 |

検証手順: 生トークン → SHA-256 → `findByTokenHash` → `revoked_at IS NULL` かつ `expires_at > now()` → `player`（`deleted_at IS NULL`）を解決 → リクエスト属性へ

### deny by default の実装
`WebConfig` で `/api/**` に認証インターセプタを登録し、インターセプタ内に **(HTTPメソッド, パスパターン) の公開許可リスト**を持たせる。`@RequireRole` の有無は**ロール判定にのみ**使い、認証要否には使わない。

許可リスト（これ以外の `/api/**` は 401）:
- `POST /api/players/login`
- `GET /api/invite-tokens/validate/**`
- `POST /api/invite-tokens/register`
- `GET /api/organizations`（**完全一致**。`GET /api/organizations/players/{id}` を巻き込まないこと）
- `GET /api/venue-reservation-proxy/view`、`ANY /api/venue-reservation-proxy/fetch/**`（capability トークンで保護済み）
- `/api/line/webhook/**`、`/api/line-chat-worker/**` は既存の `excludePathPatterns` を維持（インターセプタ自体が動かない）
- `/ping`、`/ical/**` は `/api/**` 外のため元々対象外

### パスワードのハッシュ化
- `org.springframework.security:spring-security-crypto` を追加（`spring-boot-starter-security` は入れない。フィルタチェーンが自動で有効化され既存構成を壊すため）
- 移行は冪等な `ApplicationRunner`。`^\$2[aby]\$` に**マッチしない** `players.password` を BCrypt 化する。トランザクションで all-or-nothing とし、失敗時は起動を失敗させる（半端な移行状態で稼働させない）
- `players.password` は既に VARCHAR(255) のため**スキーマ変更は不要**（BCrypt ハッシュは60文字）

### リクエスト属性の契約（維持）
`currentUserId` / `currentUserRole` / `adminOrganizationId` の3属性は現行のまま維持し、**供給元をヘッダーからトークンに差し替えるだけ**にする。これにより 23ファイル・185箇所の下流を無改修に保つ。

---

## 実装タスク

### タスク1: トークン基盤
- [x] 完了
- **目的:** トークンの発行・検証・失効を担う土台を作る（上位のどこからも使える状態にする）
- **対応AC:** AC-1（基盤）, AC-12, AC-13, AC-14（基盤）
- **主な変更領域:**
  - `database/add_auth_tokens.sql`（新規）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/AuthToken.java`（新規）
  - `.../repository/AuthTokenRepository.java`（新規）
  - `.../service/AuthTokenService.java`（新規: `issue(player)` / `verify(rawToken)` / `revoke(rawToken)` / `revokeAllForPlayer(playerId)`）
  - `karuta-tracker/build.gradle`（`spring-security-crypto` 追加）
- **依存タスク:** なし
- **必要なテスト:** `AuthTokenServiceTest` — 発行したトークンが検証を通る／期限切れは通らない／失効済みは通らない／選手単位の一括失効／生トークンが DB に保存されていないこと（ハッシュのみ）
- **完了条件:** `./gradlew test` green
- **対応Issue:** #1107

### タスク2: ログイン契約とパスワードのハッシュ化
- [x] 完了
- **目的:** ログインで実トークンを発行し、パスワードを BCrypt 化する。失効フックを繋ぐ
- **対応AC:** AC-5, AC-6, AC-10, AC-11, AC-11b, AC-12, AC-13, AC-14
- **主な変更領域:**
  - `.../service/PlayerService.java`（`login` を BCrypt 照合＋トークン発行に。パスワード変更時・論理削除時に `revokeAllForPlayer`）
  - `.../dto/LoginResponse.java`（`token` フィールド追加。**既存フィールドは削らない**）
  - `.../controller/PlayerController.java`（`POST /api/players/logout` 追加）
  - `.../config/PasswordHashMigrationRunner.java`（新規・冪等）
  - **★パスワードの書き込み経路4つすべて**（1つでも漏らすと該当ユーザーが出荷後にログイン不能）:
    - `.../dto/PlayerCreateRequest.java:49`（選手作成。コメント「実際はハッシュ化が必要」が放置されている）
    - `.../dto/PlayerUpdateRequest.java:43`（選手更新・**パスワード変更フロー**。同上）
    - `.../dto/PublicRegisterRequest.java:44`（招待登録）→ 呼び出し元 `.../service/InviteTokenService.java`
    - `.../service/DensukeImportService.java:977`（伝助自動登録。`"pppppppp"` 固定）
- **依存タスク:** タスク1
- **実装上の注意:** 上記3つの DTO は `toEntity()` 内でパスワードを設定しており DTO からは `PasswordEncoder` に触れない。**ハッシュ化は `toEntity()` の中ではなくサービス層の単一チョークポイントで行う**（DTO に encoder を注入する設計にしない）
- **必要なテスト:** BCrypt 照合の成否／平文パスワードのまま保存されないこと／移行ランナーが平文を変換し既にハッシュ済みは触らないこと（冪等）／**移行前の平文と同じパスワードで移行後もログインできること（AC-11）**／**選手作成・招待登録・伝助自動登録・パスワード変更で保存したパスワードで、再起動を挟まずログインできること（AC-11b。移行ランナーに依存しない往復テスト）**／パスワード変更で既存トークンが失効すること／論理削除で失効すること／ログアウトで当該トークンのみ失効すること
- **なぜ AC-11b が要るか:** 書き込み経路の漏れは**テスト green のまま出荷され得る**（移行済みパスワードだけを検証していると素通りする）。さらに起動時ランナーが次回再起動で平文を拾ってハッシュ化するため「再起動したら直る」断続バグに化ける
- **完了条件:** `./gradlew test` green
- **対応Issue:** #1108

### タスク3: 認証インターセプタのトークン化と deny by default
- [x] 完了
- **目的:** ヘッダー自己申告を廃止し、トークンを唯一の根拠にする。未保護エンドポイントを塞ぐ
- **対応AC:** AC-1, AC-2, AC-3, AC-4, AC-7, AC-8, AC-9, AC-15, AC-16, AC-17, AC-18
- **主な変更領域:**
  - `.../interceptor/RoleCheckInterceptor.java`（全面改修。`X-User-Role` / `X-User-Id` の参照を削除し `Authorization: Bearer` から解決。公開許可リストを実装）
  - `.../config/WebConfig.java`（登録パターンの見直し）
  - `.../controller/OrganizationController.java`（**`getHeader("X-User-Role")` の直読みを属性参照に置換**。契約から外れる唯一の箇所）
  - `karuta-tracker/src/test/**`（17ファイル・270箇所のヘッダー組み立てをテストヘルパー経由に移行。`MatchPairingControllerTest` 62 / `PracticeSessionControllerTest` 36 / `RoleCheckInterceptorTest` 25 で約45%）
  - テストヘルパー新規（選手＋ロールから `Authorization: Bearer` を組み立てる）
- **依存タスク:** タスク1（トークン発行がないとテストが書けない）
- **必要なテスト:**
  - **AC-2: `X-User-Role: SUPER_ADMIN` を付けてもトークン無しなら 401**
  - **AC-3: 有効なトークンと矛盾する `X-User-Role` / `X-User-Id` を同時送信しても、トークン由来の主体で判定される**
  - AC-7: 許可リスト外の `/api/**` が `@RequireRole` の有無によらず未認証で 401（旧・未保護だった書き込み系から代表を選ぶ: `PUT /api/players/{id}`, `DELETE /api/matches/{id}`, `POST /api/matches/{matchId}/comments/notify`）
  - AC-8: 許可リストの各EPが未認証で成功（**特に `GET /api/organizations`**。`GET /api/organizations/players/{id}` は 401 のままであることも併せて確認）
  - **AC-9: `@RequireRole` を付けない新規エンドポイントでも既定で認証必須になる構造テスト**（テスト専用コントローラを立てて検証する。個別列挙では将来の付け忘れを検出できないため）
  - AC-16/17: ロール境界・ADMIN 団体スコープの回帰
- **依存の注意:** インターセプタの変更とテスト移行は**同一タスク内で完結させる**。分けるとビルドが赤のまま次タスクに渡ることになる
- **テスト移行は2軸あることに注意:** ①ヘッダー組み立て → ヘルパー経由（270箇所）に加えて、②**パスワードのフィクスチャ**。リポジトリ経由で平文パスワードの選手を作ってからログインする統合テストは、タスク2の BCrypt 化により赤くなる。ヘッダー由来でない破綻が出ても想定内
- **完了条件:** `./gradlew test` green（既存テスト全通過）
- **対応Issue:** #1109

### タスク4: フロントエンドの配線
- [ ] 完了
- **目的:** 実トークンを保存・送信し、`X-User-*` ヘッダーの送信を止める
- **対応AC:** AC-18（FE）, AC-20
- **主な変更領域:**
  - `karuta-tracker-ui/src/api/client.js`（`X-User-Role` / `X-User-Id` の付与を削除。`Authorization: Bearer` は既存のまま）
  - `karuta-tracker-ui/src/context/AuthContext.jsx`（`'dummy-token'` をログインレスポンスの実トークンに。ログアウトで `POST /api/players/logout` を呼ぶ）
- **依存タスク:** タスク2（`LoginResponse.token` が必要）
- **必要なテスト:** ログイン成功でレスポンスのトークンが `localStorage` に保存されること／リクエストに `X-User-Role` / `X-User-Id` が**含まれない**こと／ログアウトで logout API を呼びトークンを破棄すること
- **注意:** `localStorage` の `currentPlayer.role` は**画面表示の出し分け用に残す**（サーバは参照しない）。消すと `RoleProtectedPage` 等の UI が壊れる
- **完了条件:** `npm run test` green、`npm run lint` エラー0
- **対応Issue:** #1110

### タスク5: ドキュメント更新と本番 DB 適用
- [ ] 完了
- **目的:** 正典を新仕様に更新し、本番へマイグレーションを適用する
- **対応AC:** AC-19
- **主な変更領域:**
  - `docs/spec/players-auth.md`（認証方式セクションを全面更新。**選手プロパティ表の「password: BCryptハッシュ」と本文「平文」の矛盾も是正**。API 表の権限「なし」表記を実態に合わせる）
  - `docs/design/architecture.md`（認証・認可セクション、`X-User-Role` 前提の記述）
  - 本番 Render PostgreSQL へ `database/add_auth_tokens.sql` を適用 → `\d auth_tokens` で反映確認
- **依存タスク:** タスク3, タスク4
- **完了条件:** docs 更新済み、本番 DB に `auth_tokens` が存在することを確認
- **対応Issue:** #1111

## 実装順序（Wave）

- **Wave 1:** タスク1（基盤。以降すべての前提）
- **Wave 2:** タスク2（ログイン契約）
- **Wave 3:** タスク3, タスク4 — **並行実装可**（タスク3は `karuta-tracker/` のみ、タスク4は `karuta-tracker-ui/` のみで変更領域が完全に分離）
- **Wave 4:** タスク5（docs＋本番適用）

## 出荷時の注意

- **本番 DB 適用が必須**（`auth_tokens` テーブル）。PR 作成時にユーザーへ明示する
- パスワードのハッシュ化は起動時ランナーが自動実行する。**デプロイ後に本番 DB で `players.password` が `$2` で始まることを確認する**
- 既存のログイン中ユーザーは全員 1 回だけ再ログインが必要（`dummy-token` が無効になるため）
