---
status: completed
---

# 伝助との双方向同期機能 改修実装手順書

## 実装タスク

### タスク1: `GET /densuke-url` に認証追加
- [x] 完了
- **概要:** `PracticeSessionController.getDensukeUrl()` に `@RequireRole({Role.PLAYER, Role.ADMIN, Role.SUPER_ADMIN})` を付与し、未認証アクセスを防ぐ
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/PracticeSessionController.java` — `getDensukeUrl` メソッドに `@RequireRole` アノテーションを追加
- **依存タスク:** なし
- **対応Issue:** #112

---

### タスク2: 手動同期エンドポイントに書き込みフェーズ追加
- [x] 完了
- **概要:** `POST /sync-densuke` の先頭で `densukeWriteService.writeToDensuke()` を呼び出し、自動同期と同じ「書き込み→読み取り」順に変更する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/PracticeSessionController.java` — `syncDensuke` メソッドに `writeToDensuke()` 呼び出しを追加
- **依存タスク:** なし
- **対応Issue:** #113

---

### タスク3: `DensukeUrl` 操作をServiceに委譲 + URLドメイン検証
- [x] 完了
- **概要:** `PracticeSessionService` に `getDensukeUrl` / `saveDensukeUrl` メソッドを追加し、Controller の直接リポジトリ参照を削除する。`saveDensukeUrl` で `densuke.biz` ドメイン検証も同時に実装する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeSessionService.java` — `getDensukeUrl(int year, int month)` および `saveDensukeUrl(int year, int month, String url)` を追加（URLドメイン検証含む）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/PracticeSessionController.java` — `densukeUrlRepository` フィールドを削除し、`getDensukeUrl` / `saveDensukeUrl` / `syncDensuke` / `registerAndSyncDensuke` 内の `densukeUrlRepository` 直接呼び出しを Service 経由に変更
- **依存タスク:** タスク1・タスク2（同一ファイルへの変更のため、タスク1・2完了後に着手）
- **対応Issue:** #114

---

### タスク4: `.dirty(true)` を明示
- [x] 完了
- **概要:** `PracticeParticipantService` 内の全 `PracticeParticipant.builder()` 呼び出し（7箇所）に `.dirty(true)` を明示的に追記する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeParticipantService.java` — L69, L162, L169, L190, L224, L232, L317 の各 `builder()` に `.dirty(true)` を追記
- **依存タスク:** なし
- **対応Issue:** #115

---

### タスク5: シングルインスタンス前提コメントと join-id マッピング前提コメントの追記
- [x] 完了
- **概要:** `DensukeWriteService` の2箇所にコメントを追記する。①メモリ保持フィールドにシングルインスタンス前提コメント、②`parseAndSaveRowIds()` に join-id マッピングの前提条件とHTML構造変更リスクのコメント
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeWriteService.java` — L42〜L46 のメモリフィールドにコメント追記、`parseAndSaveRowIds()` メソッドにコメント追記
- **依存タスク:** なし
- **対応Issue:** #116

---

### タスク6: `playerRepository.findAll()` のキャッシュ導入
- [x] 完了
- **概要:** Spring Boot Cache + Caffeine を導入し、`importFromDensuke()` 内の `playerRepository.findAll()` を60秒TTLでキャッシュする。プレイヤー作成・更新・削除時にキャッシュを破棄する
- **変更対象ファイル:**
  - `karuta-tracker/build.gradle` — `spring-boot-starter-cache` および `com.github.ben-manes.caffeine:caffeine` を追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/KarutaTrackerApplication.java`（または新規 `CacheConfig.java`） — `@EnableCaching` を追加
  - `karuta-tracker/src/main/resources/application.properties`（または `application.yml`） — Caffeine の TTL 設定（`spring.cache.caffeine.spec=expireAfterWrite=60s`）を追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeImportService.java` — `playerRepository.findAll()` 呼び出しをキャッシュ対応のヘルパーメソッドに切り出し、`@Cacheable("players")` を付与
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PlayerService.java` — プレイヤー作成・更新・削除メソッドに `@CacheEvict(value = "players", allEntries = true)` を付与
- **依存タスク:** なし
- **対応Issue:** #117

---

## 実装順序

1. **タスク1** — `@RequireRole` 追加（1行の変更、最小リスク）
2. **タスク2** — 手動同期に書き込みフェーズ追加
3. **タスク4** — `.dirty(true)` 明示（機械的変更、リスクなし）
4. **タスク5** — コメント追記（コード変更なし、リスクなし）
5. **タスク3** — Service委譲 + ドメイン検証（最大の変更、タスク1・2完了後）
6. **タスク6** — キャッシュ導入（依存追加を伴う変更）
