# karuta-tracker（Spring Boot バックエンド）

Spring Boot 3.4.1 / Java 21 / Gradle。パッケージルート: `src/main/java/com/karuta/matchtracker/`

## レイヤー構成（ショートカット禁止）

controller → service → repository → entity。横断: dto / config / scheduler / interceptor / annotation / exception / util / monitoring / service/proxy（会場予約プロキシ）

## 命名規約（ファイルパスは規約から推測できる）

- エンドポイント `/api/<resource>` → `controller/<Resource>Controller.java` → `service/<Resource>Service.java` → `repository/<Resource>Repository.java` → `entity/<Resource>.java`
- DTO: `dto/<Resource>Dto.java`（Entity→DTO は DTO 側の静的 `fromEntity()`）。リクエストは `<Resource>CreateRequest` / `<Resource>UpdateRequest`
- 定期実行: `scheduler/<Xxx>Scheduler.java`

## 規約

- 新規エンドポイントには `@RequireRole`（SUPER_ADMIN / ADMIN / PLAYER）を必ず付与（`interceptor/RoleCheckInterceptor` が検査）
- entity の `@Column` 変更は `database/` の migration SQL と同一 PR + 本番適用が必須（ルート CLAUDE.md の最重要ルール）
- 論理削除は `deleted_at`。物理 DELETE 禁止
- テスト: `./gradlew test`（単一クラス: `--tests "com.karuta.matchtracker.service.XxxTest"`）

機能→実装ファイルの対応は `docs/spec/<ドメイン>.md` 冒頭の「主要実装」を参照。テーブル定義は `docs/design/db.md` が正典。
