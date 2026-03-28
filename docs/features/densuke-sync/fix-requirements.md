---
status: completed
audit_source: 会話内レポート（2026-03-29）
selected_items: [1, 2, 3, 4, 5, 6, 7, 8]
---

# 伝助との双方向同期機能 改修要件定義書

## 1. 改修概要

- **対象機能:** 伝助との双方向同期機能（アプリ ↔ 伝助 出欠同期）
- **改修の背景:** `/audit-feature` による監査（2026-03-29）で検出されたセキュリティ・アーキテクチャ・品質上の問題に対応する
- **改修スコープ:** 監査レポートの推奨アクション全8項目

---

## 2. 改修内容

### 2.1 項目1: `GET /densuke-url` に認証追加（高優先度・セキュリティ）

**現状の問題:**
`PracticeSessionController.getDensukeUrl()` に `@RequireRole` が付与されておらず、未認証ユーザーでも年月を指定すれば伝助URLを取得できる。

**修正方針:**
`@RequireRole({Role.PLAYER, Role.ADMIN, Role.SUPER_ADMIN})` を付与し、認証済みユーザーのみアクセス可能にする。

**修正後のあるべき姿:**
全ロール（PLAYER以上）でアクセス可能だが、未認証は401を返す。フロントエンドの呼び出し元（DensukeManagement.jsx）はADMIN/SUPER_ADMINのみのため影響なし。

---

### 2.2 項目2: 手動同期エンドポイントに書き込みフェーズ追加（高優先度・機能改善）

**現状の問題:**
`POST /sync-densuke` は読み取り（伝助→アプリ）のみを行い、書き込み（アプリ→伝助）を行わない。自動同期（スケジューラー）は「書き込み→読み取り」の順に実行されるため、手動同期の挙動が異なり管理者の期待と乖離する。

**修正方針:**
`syncDensuke` メソッドの先頭で `densukeWriteService.writeToDensuke()` を呼び出し、自動同期と同じ「書き込み→読み取り」の順に変更する。

**修正後のあるべき姿:**
手動同期ボタンを押した際も、dirty な参加者が伝助に書き込まれたうえで読み取りが実行される。

---

### 2.3 項目3: `DensukeUrl` 操作をServiceに委譲（中優先度・アーキテクチャ）

**現状の問題:**
`PracticeSessionController` が `DensukeUrlRepository` を直接 `@Autowired` して使用している（L35, L437, L474, L494, L519）。Controller がリポジトリを直接操作することはレイヤードアーキテクチャの設計方針に反する。

**修正方針:**
`PracticeSessionService` に `getDensukeUrl(int year, int month)` と `saveDensukeUrl(int year, int month, String url)` を追加し、Controller から委譲する。Controller の `densukeUrlRepository` フィールドを削除する。

**修正後のあるべき姿:**
Controller は Service 経由でのみ DensukeUrl を操作する。リポジトリはレイヤードアーキテクチャに従い Service からのみ参照される。

---

### 2.4 項目4: 伝助URLのドメイン検証（中優先度・セキュリティ）

**現状の問題:**
`saveDensukeUrl` は URL が空でないことのみチェックし、ドメインを検証しない。任意のURLを登録するとスケジューラーが定期的にそのURLへHTTPリクエストを送信するSSRFリスクがある。

**修正方針:**
Service の `saveDensukeUrl` 内で URL が `https://densuke.biz/` で始まることを検証し、不正な場合は `IllegalArgumentException` をスローする。Controller でキャッチして400を返す。

**修正後のあるべき姿:**
`densuke.biz` ドメイン以外のURLは登録不可。

---

### 2.5 項目5: 書き込み状態のシングルインスタンス前提を明示（中優先度・設計文書化）

**現状の問題:**
`DensukeWriteService` の書き込み状況（`lastAttemptAt` 等）はインスタンス変数として `volatile` で保持されている。スケールアウト時に各インスタンスの状態が独立するが、その前提がコードに明記されていない。

**修正方針:**
メモリ保持フィールドにコメントを追記し、「シングルインスタンス前提。スケールアウト時はDB/Redisへの永続化が必要」と明示する。実装の変更は行わない。

**修正後のあるべき姿:**
設計上の制約がコードから明確に読み取れる。

---

### 2.6 項目6: `.dirty(true)` の明示（低優先度・品質改善）

**現状の問題:**
`PracticeParticipantService` の複数の `PracticeParticipant.builder()` 呼び出しで `.dirty(true)` を明示しておらず、Lombok `@Builder.Default` のデフォルト値に暗黙的に依存している。デフォルト値が変更された場合のリスクと読み手への誤解リスクがある。

**修正方針:**
`PracticeParticipantService` 内の全 `PracticeParticipant.builder()` 呼び出しに `.dirty(true)` を明示的に追記する。

**修正後のあるべき姿:**
参加者作成時の dirty フラグ設定が意図的であることがコードから明確に読み取れる。

---

### 2.7 項目7: `playerRepository.findAll()` キャッシュ（低優先度・パフォーマンス）

**現状の問題:**
`DensukeImportService.importFromDensuke()` が毎回 `playerRepository.findAll()` を実行しており、スケジューラーが毎分呼び出すため、プレイヤー数増加時の負荷になる。

**修正方針:**
`build.gradle` に `spring-boot-starter-cache` と `caffeine` を追加し、Spring Cache を有効化する。プレイヤー一覧取得を `@Cacheable` でラップし、60秒TTLのキャッシュを設定する。プレイヤーの作成・更新・削除時に `@CacheEvict` でキャッシュを破棄する。

**修正後のあるべき姿:**
60秒以内の同一スケジューラー実行ではDBアクセスが発生しない。

---

### 2.8 項目8: `parseAndSaveRowIds()` の前提条件をコメントで文書化（低優先度・保守性）

**現状の問題:**
`DensukeWriteService.parseAndSaveRowIds()` は、伝助HTMLの `join-{id}` 出現順序とアプリ側の日付順が一致するという前提でマッピングを行っているが、この前提がコードに記述されていない。伝助HTML構造変更時に無音でスキップされるリスクがある。

**修正方針:**
`parseAndSaveRowIds()` に前提条件・リスク・HTML構造変更時の影響をJavadocまたはインラインコメントで記述する。

**修正後のあるべき姿:**
障害発生時のデバッグに必要な情報がコードに記述されている。

---

## 3. 技術設計

### 3.1 API変更

| エンドポイント | 変更内容 |
|--------------|---------|
| `GET /api/practice-sessions/densuke-url` | `@RequireRole({PLAYER, ADMIN, SUPER_ADMIN})` を追加 |
| `PUT /api/practice-sessions/densuke-url` | ドメイン検証ロジックを Service 側に追加（エンドポイント自体の変更なし） |
| `POST /api/practice-sessions/sync-densuke` | 書き込みフェーズ（`writeToDensuke()`）を先頭に追加 |

### 3.2 DB変更

なし

### 3.3 フロントエンド変更

なし（APIレスポンスの互換性は維持される）

### 3.4 バックエンド変更

| ファイル | 変更内容 |
|---------|---------|
| `build.gradle` | `spring-boot-starter-cache`・`caffeine` 依存を追加 |
| `PracticeSessionController.java` | `getDensukeUrl` に `@RequireRole` 追加、`DensukeUrl` 操作を Service 経由に変更、`densukeUrlRepository` フィールド削除、`syncDensuke` に書き込みフェーズ追加 |
| `PracticeSessionService.java` | `getDensukeUrl(year, month)` / `saveDensukeUrl(year, month, url)` を追加（URLドメイン検証含む） |
| `DensukeWriteService.java` | メモリフィールドにシングルインスタンス前提コメントを追記、`parseAndSaveRowIds()` に前提条件コメントを追記 |
| `PracticeParticipantService.java` | 全 `builder()` 呼び出しに `.dirty(true)` を明示 |
| `DensukeImportService.java` | `playerRepository.findAll()` を `@Cacheable` でキャッシュ化 |
| `PlayerService.java` | プレイヤー作成・更新・削除時に `@CacheEvict` を追加 |
| `KarutaTrackerApplication.java` または CacheConfig | `@EnableCaching` を追加 |

---

## 4. 影響範囲

### 4.1 影響を受ける既存機能

| 機能 | 影響内容 |
|-----|---------|
| 伝助管理画面（DensukeManagement.jsx） | `GET /densuke-url` に認証が追加されるが、呼び出し元は既に認証済みADMINのみのため動作変更なし |
| 手動同期 | 書き込みフェーズが追加されるため、実行時間がわずかに増加する可能性がある |
| プレイヤー一覧取得（全機能） | `PlayerService` にキャッシュが追加されるが、既存の動作は変わらない |

### 4.2 破壊的変更

なし（APIレスポンス形式・DBスキーマは変更なし）

---

## 5. 設計判断の根拠

| 判断 | 理由 |
|-----|------|
| 項目2: 書き込みフェーズ追加（UIラベル変更ではなく） | 管理者が意図的に手動同期を実行した場合、dirty な参加者が伝助に反映されないのは仕様上の欠陥であるため、動作を自動同期と揃える |
| 項目5: 現状維持＋コメント追記（DB永続化ではなく） | 現状シングルインスタンス運用であり、設計の複雑性を増やすリスクより、制約の明示化で十分と判断 |
| 項目7: Spring Cache + Caffeine | Spring Boot 標準の仕組みであり、侵略的な変更なしに既存コードに適用できる。他のキャッシュ実装（手動フィールド管理等）より保守性が高い |
| 項目4: `densuke.biz` ドメインのみ許可 | 他のSSRF対策（allowlist等）は過剰。伝助との同期が唯一の用途であり、ドメイン固定が最もシンプル |
