# 他かるた会への配布可能性調査（2026-07-18、2026-07-20 更新）

別のかるた会が本リポジトリをクローンし、**別の Render + 別の DB** で自前運用する場合のネック調査。
SaaS マルチテナント化ではなく **fork-and-deploy** を前提とする。

---

## 更新履歴

### 2026-07-20 更新（認証まわりの全面刷新を反映）

初版（07-18）以降、**S章のセキュリティ指摘がほぼ解消された**。認証モデルがヘッダー自己申告から
サーバ発行トークン＋deny by default に作り替えられた（PR #1104 / #1112 / #1114）。差分:

| 指摘 | 初版（07-18） | 現在（07-20） |
|---|---|---|
| **S-1** 未認証破壊エンドポイント `POST /api/seed/all` | 重大・未対応 | ✅ **解消**。`DataSeedController` ごと削除（PR #1104 / `89a8ab0b`） |
| **S-2** パスワード平文保存・平文比較 | 未対応 | ✅ **解消**。BCrypt 化＋`PasswordPolicy` チョークポイント（PR #1114 / `ec127b84`） |
| （新）ヘッダー自己申告認証 | `X-User-Role` 詐称で誰でも全操作可 | ✅ **解消**。Bearer トークンのみを根拠に、`/api/**` は deny by default（`c9e059b8`）。未認証で通すのは `PUBLIC_ENDPOINTS` 許可リストのみ |
| （新）他の未認証状態変更EP6件 | （初版では未列挙） | ✅ **解消**。`PUT /api/players/{id}` ほか6件に認可付与（PR #1112 / `86bfa77c`） |
| **S-3** LINE 認証情報が平文（`LINE_ENCRYPTION_KEY` 死に設定） | 未対応 | ⚠️ **未解消**（後述） |

**A章・D章・E章のブロッカーは基本的に据え置き**（Vercel URL 直書き・hokudai 決め打ち・README 欠如はいずれも現存）。
ただし認証刷新に伴い**新規環境ブートストラップの前提が2点変わった**ので A-1 / A-2 を改訂した（`auth_tokens` テーブル追加・初期パスワード投入の扱い）。

### 2026-07-20 追加対応（A-1 を実処理）

**A-1（最大の残ブロッカー＝空DBからのスキーマ構築手段が無い）を解消した。**

- 稼働中の本番（PostgreSQL 18.4）から `pg_dump --schema-only --no-owner --no-privileges` で統合スキーマを生成し、**`database/schema.sql`** として確定・コミット（43テーブル・247制約・71索引、データ/PII は一切なし）
- Windows は NAT64 で直接 pg_dump 不可のため、**Oracle VM 上の使い捨て `postgres:18` コンテナ**で実行（Render 本番とも kagetra とも隔離）
- pg_dump 18 の psql 専用メタコマンド `\restrict` / `\unrestrict` は移植性のため除去（psql 以外の SQL ランナーでも流せるように）
- **空DBへの試験適用を実施**（同じく使い捨てコンテナ）: `ON_ERROR_STOP=1` でエラー0・43テーブル生成・FK `fk_auth_tokens_player` まで確認。**「誰も空DBから構築したことがない」状態を実際に構築して解消**した

→ 残る起動ブロッカーは無し。あとは A-2（初期データ投入 SQL の用意）・A-3/A-4/D-2 の設定/ブランディング整備のみ。

---

## 結論（3行、07-20 時点）

1. **起動ブロッカーは解消済み。** 最大の障壁だった「空DBからのスキーマ構築手段が無い」問題は、本番から生成した **`database/schema.sql`**（空DB適用を実検証済み）で解決（A-1）。残るは初期データ投入 SQL の整備（A-2）と設定/ブランディングの差し替え（A-3/A-4/D-2）。
2. **セキュリティ面は配布に耐える水準になった**（07-18 版の最優先課題 S-1 / S-2 は解消済み）。残るは S-3（LINE 認証情報の平文保存）のみで、これは LINE を使わなければ無関係。
3. **方針は「新規DB・独立インスタンス」で確定**（2026-07-18）。既存インスタンスへの3団体目相乗りは、org スコープ由来のバグ再発リスクが高く**不採用**（G章）。

---

## S. 最優先：配布以前に現本番が抱える問題

> **07-20 時点の総括**: このS章の初版指摘（S-1 / S-2）と、初版で列挙しきれていなかった認証全般の穴は、
> auth-tokenization 一連の改修（PR #1104 / #1112 / #1114）で**解消済み**。以下 S-1 / S-2 は記録として残すが対応不要。
> 未解消は S-3（LINE 認証情報の平文保存）のみ。

### S-1 ✅【解消済み】未認証で誰でも叩けた破壊的エンドポイント

> **2026-07-20: 解消。`DataSeedController` はファイルごと削除された（PR #1104 / commit `89a8ab0b`）。**
> 実測で `karuta-tracker/src/main/java/.../controller/DataSeedController.java` の不在を確認。
> 加えて認証モデル自体が deny by default になったため、仮に同種のEPが残っていても未認証では通らない。

（初版の記録）`POST /api/seed/all` が `@RequireRole` も `@Profile` ガードも無く**完全に未認証**で、
`matchRepository.deleteAll()` 等の全データ消去＋全選手パスワードの `pppppppp` 上書きを誰でも実行できた。

### S-2 ✅【解消済み】パスワードが平文保存・平文比較

> **2026-07-20: 解消。BCrypt 化された（PR #1114 / commit `ec127b84`）。**
> `service/PlayerService.java:357-358` は `passwordEncoder.matches(...)` で照合。書き込み経路は
> `PasswordPolicy.encode(...)`（サービス層チョークポイント、UTF-8 72バイト上限）に一本化され、
> `PasswordWriteSiteArchitectureTest` が全書き込み地点を機械的に固定している。
> 本番の平文パスワードは起動時 `PasswordHashMigrationRunner` が一括変換済み（一方通行・ロールバック不可）。

（初版の記録）`PlayerService` が平文でパスワードを保存・比較しており、`BCrypt`/`PasswordEncoder` の利用がゼロだった。

### S-3 ⚠️【未解消】LINE の認証情報が平文（`LINE_ENCRYPTION_KEY` は死んだ設定）

- `application.properties:69` に `line.encryption-key=${LINE_ENCRYPTION_KEY:}` があるが、**このプロパティを読むコードが1行も存在しない**（07-20 再確認：依然ゼロ）。暗号化クラスもゼロ
- `entity/LineChannel.java:37,41` のコメントは「（暗号化保存）」だが実際は `channel_secret` / `channel_access_token` が**平文で DB 保存**
- `docs/requirements/line-notification.md:587-589` は AES-256-GCM を必須と書いており、**仕様と実装が乖離**
- **影響は限定的**: LINE を使わない運用（C章）なら `line_channels` は空で無関係。使う場合のみ、DB のアクセス制御でカバーするか暗号化を実装する判断が要る

---

## A. ブロッカー（これが無いと起動しない）

### A-1 ✅【解消済み】空DBからスキーマを作る手段が無い

> **2026-07-20: 解消。`database/schema.sql` を生成・コミットし、空DBへの適用を実検証した**（詳細は上の「2026-07-20 追加対応」）。
> 以下は問題の記録として残す。

- マイグレーションフレームワーク（Flyway / Liquibase）**不採用**
- `database/` は68ファイルがフラットに配置。**README・manifest・番号付け・適用順の記録が一切無い**（`CREATE TABLE` 18本 / `ALTER TABLE` 46本）
- **コア系テーブルの PostgreSQL 用 `CREATE TABLE` が存在しない。** 実測で確認：`players` `matches` `venues` `practice_sessions` はゼロ件、`organizations` のみ `migration_organization.sql` にある
  - 例: `database/add_venue_id_to_matches.sql:5` は `ALTER TABLE matches` するが `matches` の CREATE が無い。7行目は `REFERENCES venues(id)` を張るが `venues` の CREATE も無い → **空DBでは1行目で即エラー**
- 統合スキーマ候補3つはいずれも使えない：`phase1_schema.sql` は **MySQL構文**（`ENUM`/`AUTO_INCREMENT`/`ENGINE=InnoDB`）で自ら「正本ではない」と明記、残り2つは MySQL ダンプ
- `docs/design/db.md` は「テーブル定義の正典」と宣言されているが **ER図と散文のみで DDL は0件**

**根本原因**: 本番スキーマは初代DBから `pg_dump`/`restore` で受け継がれてきた（`scripts/render-db-migration/migrate.sh` は月次ローテーション用）。**誰も空DBからの構築を一度も実行していない。**

> **07-20 追記**: 認証刷新で `auth_tokens` テーブルが増えた（`database/add_auth_tokens.sql`）。新規環境で組むスキーマにはこれも含める必要がある。
> なお同ファイルの `:21-23` には**「新規環境では同じFK制約が2本できてしまうのでインライン `REFERENCES` を書かない」**という注意書きが明示されている。
> これは**新規環境での構築を実際に意識し始めた証跡**であり良い兆候だが、裏を返せば **`database/*.sql` を手で順に流す方式は、こうした新規環境固有の罠を1本ずつ踏む**ということでもある。
> やはり下記 `pg_dump --schema-only`（本番で実際に動いている確定形をそのまま写す）が安全。

#### 解決策（推奨）: 現本番から `pg_dump --schema-only` で統合スキーマを作る

**このリポジトリに欠けている「統合スキーマ」は、稼働中の本番DBから1コマンドで生成できる。**

```sh
pg_dump --schema-only --no-owner --no-privileges "$DATABASE_URL" > database/schema.sql
```

- **データを一切含まない**ので個人情報の混入が無い（E-1 の `backup.sql` とは別物）
- CHECK制約・複合ユニーク・INDEX・列の型がすべて**実際に本番で動いている通り**に出る
- 68ファイルの適用順を復元する必要が無くなる
- 生成物をリポジトリにコミットすれば、以後は新規構築の正典になる（`docs/design/db.md` が果たせていない役割）

**注意（本環境固有）**: Windows から Render の Postgres ホストへは NAT64/IPv6 の問題で psql/pg_dump が繋がらない既知の罠がある。
Render のダッシュボードのシェル、または Linux 環境（Oracle VM 等）から実行するのが確実。

#### 代替策（本番に到達できない場合）: `ddl-auto=update` で生成 ※未検証

`render` プロファイルは `ddl-auto: none` だが、ベースの `application.properties:40` は `ddl-auto=update`。
初回のみ env `SPRING_JPA_HIBERNATE_DDL_AUTO=update` で起動 → Hibernate にテーブルを生成させる → シード投入 → `none` に戻す。

**この経路は本調査では実行していない（机上の推論）**。既知の欠損として、
**CHECK制約・複合ユニーク制約は作られず**、また手書きマイグレーションで育ってきた列と
エンティティの `@Column` 定義が食い違っていれば**型のドリフトが起きうる**。
（部分ユニークINDEX3本は `DataInitializer` が起動時に自動作成、`matches` には本番でも元々CHECK制約が無いため、その2点の影響は限定的）
採用する場合は**必ず自分で1回通して検証すること。**

### A-2 初期データ投入の手順・SQLが存在しない（鶏卵問題）

- 起動時シーダー `config/DataInitializer.java` が入れるのは `LineNotificationScheduleSetting` のみ。**organization も SUPER_ADMIN も作らない**
- 最初の SUPER_ADMIN を作る PostgreSQL 用 INSERT は**リポジトリのどこにも無い**
- （07-20: かつてブートストラップに流用できなかった `POST /api/seed/all` は削除済み。無関係になった）
- `scripts/seed_data.sql` は**完全に陳腐化**（存在しないテーブル `practice_participations` / `match_results`、存在しない列 `players.username` を参照）

→ 手動 INSERT が必要な最低4行: `organizations` / `players`(role=SUPER_ADMIN) / `player_organizations` / `venues`。

> **07-20 追記（BCrypt 化後もブートストラップは平文でよい）**: S-2 で BCrypt 化されたが、初期 SUPER_ADMIN の
> パスワードは**平文のまま INSERT してよい**。`PasswordHashMigrationRunner`（`config/PasswordHashMigrationRunner.java:31-32`）が
> 起動時に平文行を検出して BCrypt へ一括変換し、そのコメントは**「新しい環境（他団体への配布・ステージング）で平文の
> 初期データから立ち上げる場合に同じ保証が要るため（このランナーを）残す」と明記している**。
> つまり「初期データは平文で書き、初回起動で自動ハッシュ化」が**設計として正規に想定された手順**になった。
> （BCrypt ハッシュを手で生成して INSERT しても可。どちらでもよい）

### A-3 フロントの API URL が「環境変数だけでは」切り替わらない箇所

- `VITE_API_BASE_URL` は Vite の**ビルド時埋め込み**。実行時注入では効かない。フォールバックは `src/api/client.js:3` と `src/pages/practice/PracticeList.jsx:22` に重複した `http://localhost:8080/api`
- `.env.example` が**リポジトリ全体に1つも無い**
- **`controller/LineAdminController.java:225,232,235` に元クラブの Vercel URL がハードコード**（`https://match-tracker-eight-gilt.vercel.app`）。リッチメニューのボタンが**他会のサイトを指したままになる。ソース修正必須**
- `render.yaml` はバックエンド1サービスのみ。**フロントは Vercel 前提**（`karuta-tracker-ui/vercel.json` あり）だが、Vercel 側の設定手順書は存在しない

### A-4 セットアップ手順書が無い

- ルート `README.md` は**機能一覧3行が文字化け（UTF-8/CP932混在）＋ Claude Code の使い方**のみ。ビルド・起動・デプロイに一切触れていない
- `karuta-tracker-ui/README.md` は Vite の初期テンプレートのまま
- **手順書が `CLAUDE.local.md`（gitignore対象）を参照している箇所が8+**（`docs/design/architecture.md:268-270` ほか）。クローン先には来ないので**手順が行き止まりになる**

---

## B. 再設定で済むもの（コード改修は不要）

### B-1 環境変数：`render.yaml` の宣言7個に対し、実際は18個必要

未設定時は**起動失敗ではなくサイレントな機能停止**として出るため気づきにくい。

| 変数 | 未設定時 |
|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | render プロファイルは既定値無し → **起動失敗** |
| `CORS_ALLOWED_ORIGINS` | localhost のみ許可 → **本番SPAからAPI全滅** |
| `APP_BASE_URL` | localhost → iCal 購読不可 |
| `APP_FRONTEND_URL` | localhost → LINE通知のリンクが機能しない |
| `VAPID_PUBLIC_KEY` / `_PRIVATE_KEY` | **起動は成功、Web Push が無言で全停止**（warn ログのみ） |
| `VAPID_SUBJECT` | 既定 `mailto:admin@example.com` のまま |
| `LINE_CHAT_WORKER_TOKEN` | ワーカーAPI が全401（fail-closed。設計としては良い） |
| `GITHUB_PAT` / `GITHUB_REPO` | **既定値が `poponta2020/match-tracker`＝他会のリポジトリを叩く。要変更** |
| `KADERU_*` / `SAPPORO_COMMUNITY_*` | 札幌の会場予約用。他地域では不要 |
| `VITE_API_BASE_URL`（フロント／ビルド時） | localhost → **本番で全API失敗** |

新クラブが自分で発行するもの: DB認証 / VAPID鍵（`npx web-push generate-vapid-keys`・唯一ドキュメント有り）/ LINEチャネル一式。

### B-2 丸ごと不要な GitHub Actions とシークレット

`migrate-render-db` / `cleanup-old-render-db`（Render無料DBの30日制限回避）、`codex-review`（`OPENAI_API_KEY`）、会場同期4種。
有料プランや Supabase/Neon を使うならワークフローごと削除でよい。

---

## C. LINE 連携：**無しで運用できる**（結合は疎）

規模は大きい（バックエンド57ファイル・約9,130行、`LineNotificationService.java` 単体で3,631行、フロント8ファイル、計約12,000行）が、**切り離しは容易**。

- **実質的なスイッチは「データの有無」**。`line_channels` に行を入れなければ `LineNotificationService.resolveChannel()`（:1090-1123）が `null` を返し、全送信が `SKIPPED` になる。例外もHTTP通信も発生しない
- コア側も多層防御済み。`MatchService.java:1136-1149` は `afterCommit` + 非同期 + try/catch でトランザクションに影響しない
- `LINE_ENCRYPTION_KEY` 未設定は**何も起きない**（S-3 の通り誰も読んでいないため）
- **団体ごと/グローバルの LINE ON/OFF フラグは存在しない**（`line.enabled` 相当はゼロヒット）

**唯一の落とし穴**: `config/DataInitializer.java:53,90,119` が LINE テーブルの部分UNIQUEインデックスを検証し、失敗時 `IllegalStateException` で**起動を落とす**。スキップフラグは無い。
→ A-1 推奨の `pg_dump --schema-only` 経路ならLINEテーブルも含まれるので通過する。
`ddl-auto: none` のまま LINE系テーブルだけ欠けている状態にすると、**LINEを使わなくても起動失敗する**点に注意。

**`line-chat-worker` は完全に任意**。依存の向きが worker → backend の pull のみ（backend から worker を呼ぶ経路は無い）。デプロイしなければタスクが溜まるだけ。
ただし使う場合は常駐VM + Playwright + LINE OAM のログイン済みセッション（`storage-state.json`）が必要で、**LINE側のDOM変更で壊れる**。`LINE_OAM_ACCOUNT_PATH` は単一OA前提。**新クラブには非推奨。**

### その他の外部依存はすべて安全に無効化できる

| 依存 | 無効化 | 壊れ方 |
|---|---|---|
| Web Push (VAPID) | env未設定でよい | 安全。`enabled=false` で warn のみ、購読UIも出ない |
| 伝助 (densuke) | `densuke_urls` に行を入れない | 安全。スケジューラは対象ゼロで何もしない |
| 会場予約プロキシ | `venue-reservation-proxy.enabled=false` | 安全。`higashi` は既に既定OFF |
| Googleカレンダー(iCal) | フラグ無しだが**外部通信しない** | 安全。自DBからics生成して配る側 |

---

## D. 札幌・北大固有（他会では削るか作り直す）

### D-1 会場の決め打ち — 機能ごと落とすのが現実的

`config/AdjacentRoomConfig.java` が札幌市の会場を **Venue ID 直値**で全面ハードコード：
- `:16-21` かでる2・7 の施設コード、`:23-27` 時間帯区分、`:62` `KADERU_VENUE_IDS = Set.of(3L,11L,4L,8L)`、`:65` 隣室確認対象、`:77-84` 隣室ペアマップ、`:104-108` 拡張後定員 24/24/18
- フロントにも二重化：`karuta-tracker-ui/src/utils/venueResolver.js:4`

**DBのid直値**なので他会では全く別の会場を指す。無効化スイッチは無い（`isAdjacentCheckTarget()` が false になり機能は不発になるが、UI とスケジューラは残る）。

`scripts/room-checker/` は丸ごと札幌市専用（`k2.p-kashikan.jp` / `sapporo-community.jp` / `FACILITY_CODE=103` / `TARGET_ORGANIZATION_CODE="hokudai"` / `FIXED_START_TIME="18:00"`）。**全ファイル破棄が妥当。** 関連 GitHub Actions 6本も同様。

### D-2 フロントに露出する「北大」決め打ち

| 場所 | 内容 |
|---|---|
| `pages/settings/OrganizationSettings.jsx:29` | `filter(o => o.code !== 'hokudai')` → **他会では自団体が設定画面から消える不具合** |
| `pages/practice/PracticeParticipation.jsx:76-79` | 締切情報の取得先を `code === 'hokudai'` で決め打ち |
| 同 `:265-266`, `:346` | 略称マップ `{'wasura':'わすら','hokudai':'北大'}`、表示文字列に `（北大）` 直書き |
| `pages/players/PlayerBulkEdit.jsx:57-58, 151-155, 268-272` | ボタンラベル **「＋北大」「全員に北大を追加」** |

いずれも `code` 不一致で `undefined` になり、**該当UIが黙って壊れる**。
なお**バックエンド Java の実行コードに団体決め打ちは無い**（`hokudai` の出現は全てコメント/Javadoc）。

### D-3 ブランディング・級体系

- `karuta-tracker-ui/index.html:9,11` と `public/manifest.json:2-4` — アプリ名が**「柄長」**（シマエナガ＝北海道の鳥）
- `index.html:12` の `google-site-verification` と `public/google536af4756296dc2f.html` — **現運営者のGoogleアカウント紐付け。譲渡時は削除必須**
- 級体系は `utils/rank.js:8` の A〜E級5段階 + `KyuRank` enum + DBのCHECK制約の**3箇所**に固定。全日本かるた協会の標準なので多くの会で通用するが、無級運用の会は3箇所改修が必要
- （07-20: 北大の施設「中央区民センター」「クラーク会館」を直接作成していた `DataSeedController` は S-1 対応で削除済み。この決め打ちは消えた）

### D-4 docs/ は2団体運用前提

`docs/SPECIFICATION.md:10` が冒頭で「わすらもち会と北海道大学かるた会の2団体が利用する」と明言（ハブ文書なので最初に読まれる）。
札幌固有の機能ディレクトリ多数（`higashi-*` 3件、`kaderu-multi-org` 等）。
一方 `docs/spec/` の汎用ドメイン（`matching.md` `lottery.md` `matches.md` `stats.md` `mentor.md`）は団体非依存でそのまま流用可。

---

## E. リポジトリ譲渡に伴う情報漏洩リスク

### E-1 【要注意】`backup.sql` は git 管理外だがフォルダごと渡すと同梱される

- `.gitignore:23` で除外済み、`git log --all` でも**コミット履歴に一度も存在しない**ことを実測確認 → **`git clone` では渡らない**
- しかし実物は作業ディレクトリにあり、**33テーブル分の `COPY` データ付き完全ダンプ**（実測：players 約102行、line_channels 約60行）
- 含まれるもの: **実名の選手個人情報**（氏名・所属・性別・段級位）、**LINE `channel_secret` が平文**（S-3 未解消のため現在も平文）
- 07-20 注記: このファイルは 2026-04-15 時点のスナップショットで、**S-2 の BCrypt 移行より前**。よって password 列は**平文**のまま残っている（現本番は既に BCrypt 化済みだが、この古いダンプには反映されていない）。いずれにせよ**実名 PII と平文 channel_secret を含む点で配布厳禁**であることは変わらない
- **ZIP や共有フォルダで渡す方式は厳禁。** clone 経由に限定すること

### E-2 git 履歴に旧DBパスワードが残存

`.claude/settings.local.json`（現在は追跡対象外、履歴上55コミット）に Render DB のパスワード2件が平文で残存。
**いずれも旧ローテーション済みDB**（現行とホスト名が異なる）のため実害は低いが、履歴ごと渡ることは認識しておくべき。
なお**現行の本番DBパスワード・Render APIキー・ワーカートークンは履歴に存在しない**（実測確認済み）。

### E-3 その他の実値露出

- `PrivacyPolicy.jsx:44,47` / `TermsOfService.jsx:183,186` — **現運営者個人の Gmail** が問い合わせ先としてUIに露出。差し替え必須
- `line-chat-worker/RUNBOOK.md:150-153`、`docs/features/line-chat-reserve-broadcast/phase2-dom-findings.md:10-11,102-103` — LINE OA アカウントパス・配信グループIDの**実値**
- リポジトリ直下に `*.log` / `hs_err_pid*` / `replay_pid*` が数MB混入（gitignore済みだがフォルダ配布時は同梱）

**問題なしと確認できたもの**（誤検知回避のため明記）: `github-com.pem` は公開証明書で無害。`database/karuta_tracker_dump_*.sql` はデータブロックを含まないスキーマのみ。テストfixtureの「北大太郎」は架空名。LINE ユーザーID直書き・`onrender.com` 直書きは0件。

---

## F. 推奨アクション（優先度順、07-20 改訂）

**✅ 完了（07-18→07-20 の間に対応済み）**
- ~~`DataSeedController` の削除／認可付与（S-1）~~ → PR #1104 で削除済み
- ~~BCrypt 化（S-2）~~ → PR #1114 で対応済み
- ~~未認証状態変更EP6件の認可（初版では未列挙）~~ → PR #1112 で対応済み
- ~~`pg_dump --schema-only` で `database/schema.sql` を生成・コミット（A-1）~~ → **2026-07-20 実施・空DB適用検証済み**

**独立インスタンス構築に必須**（G章の方針決定により、以下はすべて実施が前提）

1. 初期データ投入SQL（org / SUPER_ADMIN / player_organizations / venues）を用意（A-2）。初期パスワードは平文でよい（起動時に自動 BCrypt 化。A-2 追記参照）
2. `README.md` を書き直し、`.env.example` を用意（A-4・B-1）
3. `LineAdminController.java:225,232,235` の Vercel URL を `app.frontend-url` 参照に変更（A-3）
4. `PrivacyPolicy.jsx` / `TermsOfService.jsx` の連絡先、`index.html` の google-site-verification と検証ファイル削除（E-3・D-3）
5. `OrganizationSettings.jsx:29` の `!== 'hokudai'` 除去ほかフロント決め打ち4ファイル（D-2）
6. `GITHUB_REPO` 既定値の変更（B-1）

**新クラブ側の運用判断**

7. LINE は「使わない」で始めてよい（C章）。`line-chat-worker` は非推奨
8. 隣室確認・会場予約連携・`scripts/room-checker/`・関連Actions 6本は**機能ごと削除**（D-1）
9. `docs/SPECIFICATION.md:10` から着手して docs の2団体前提を書き換え（D-4）

**残る任意改善（利用者ゼロの今なら移行コスト小）**

10. LINE を使うなら S-3（`channel_secret` 平文保存）の暗号化実装、または DB アクセス制御での代替（＝2つ目のご依頼。下記で方針を提示）

---

## G. 方針決定：新規DB・独立インスタンス（3団体目相乗りは不採用）

**決定（2026-07-18）**: 他会には**独立した Render 環境と新規DB**を立てる。既存インスタンスに3団体目として追加する案は採らない。

### 却下の理由

技術的には multi-org 設計は存在する（`organizations` テーブル、46エンティティ中15が `organization_id` 保持、`SystemSetting` は `(setting_key, organization_id)` の複合ユニーク、`wasura` と `hokudai` が現に並存稼働）。
**しかし「2団体で動いている」ことは「3団体目を足しても壊れない」ことを意味しない。** org スコープの取り回しは**繰り返しバグを出している領域**であり、これが決定的な理由である。

実績として記録に残っているものだけで:

| Issue / commit | 内容 |
|---|---|
| Issue #1094 / `04b3fe68` | 他団体の会員でもある ADMIN が、会員団体の対戦組み合わせを**閲覧・操作できない** |
| Issue #1037 / `d798f638` | 団体への自動所属の並列競合で、呼び出し元Txが `UnexpectedRollbackException` で**500** |
| `f9a84e76` | LINE配信グループの1団体1グループ制約が未強制＋ADMINのスコープ付与漏れ |
| `9a3c1adc` | 団体未所属 ADMIN に対する `listGroups` が fail-open していた |

いずれも**「団体が増える方向」に踏み込んだ時に出た不具合**であり、3団体目は同種の潜在バグを踏み抜く可能性が高い。
しかも**踏み抜いた時の被害が既存2団体に及ぶ**（本番データの同居）。この非対称性が決め手になる。

### 独立環境を選ぶことで得られるもの

- 既存2団体の本番に**一切影響しない**（最大の利点）
- 他会の個人情報を当方が保持しない／当方の個人情報を他会に渡さない（E-1 の懸念が構造的に消える）
- 障害・DB移行・Render無料枠の制約が相互に波及しない
- 他会が独自に改造してもこちらに返ってこない

### 引き換えに引き受けるコスト

A章のブロッカーを**全部自前で解決する必要がある**。相乗り案ならこれらは回避できていた。

1. **空DBからの構築手順の確立（A-1）— 最大の作業**。`pg_dump --schema-only` を打って `database/schema.sql` をコミットするのが最短
2. 初期データ投入SQL の用意（A-2）: `organizations` / `players`(SUPER_ADMIN) / `player_organizations` / `venues`
3. デプロイ手順書と `.env.example`（A-4）
4. `LineAdminController` の Vercel URL 外部化（A-3）
5. フロントの `hokudai` 決め打ち4ファイル修正（D-2）— これは相乗り案でも必要だった作業

### 進め方（重要）

**手順が未検証のまま渡すと、相手は起動すらできない。**
`pg_dump` → 空DBに流す → 初期データ投入 → 起動確認、までを**必ず自分で1回通してから**リポジトリを渡すこと。
この1周が、そのまま A-4 のセットアップ手順書の原稿になる。
