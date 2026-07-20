---
status: completed
---
# LINE認証情報の暗号化 実装手順書

対象: `karuta-tracker`（バックエンド）のみ。DB スキーマ変更なし・フロント改修なし。
**セキュリティ核心（暗号実装）＋稼働中2団体の本番 LINE エンティティに触れるため、両タスクとも main が自実装する**（task-implementer へは委譲しない。CLAUDE.md 委譲基準）。

## 技術設計の確定事項

- **鍵注入方式（最大の技術論点）**: JPA `AttributeConverter` は Hibernate が生成し Spring 管理外のため `@Value` 注入が効かない。→ **静的ホルダ `LineEncryptionKeyHolder` を Spring `@Component`（`LineEncryptionKeyProvider`）が起動時（`@PostConstruct`）に populate** する方式を採る。コンバータはホルダを静的参照する。ユニットテストはホルダに直接テスト鍵をセット/クリアできる（Spring 不要でテスト可能）。
- **鍵の起動時検証**: `line.encryption-key` が**設定されている場合のみ** base64→32バイトを検証し、不正なら起動時 fail-fast（誤設定を早期に検出）。**未設定なら何もしない**（＝LINE 未使用クラブは起動できる。遅延検証の要）。
- **配置パッケージ**: 新規 `com.karuta.matchtracker.converter`（既存コンバータ無し・初導入）。
- **テスト鍵**: 実 DB 永続化テスト（Testcontainers 系）は書き込み時に鍵が要る。→ `src/test/resources/application-test.properties` に固定テスト鍵 `line.encryption-key=<base64 32B>` を追加する（AC-10 の前提）。Mockito でリポジトリをモックするユニットテストは Hibernate を通らずコンバータ非実行のため影響なし。

## 実装タスク

### タスク1: 暗号化コンバータ・鍵プロバイダ・静的ホルダの実装＋ユニットテスト
- [x] 完了
- **対応Issue:** #1128
- **目的:** AES-256-GCM の暗号化/復号ロジックと、`enc:v1:` 接頭辞・レガシー平文パススルー・遅延鍵検証・fail-fast を、DB 非依存の単体として実装し、ユニットテストで挙動を確定する。この段階では `LineChannel` にはまだ適用しない（挙動変化ゼロ）。
- **対応AC:** AC-1, AC-2, AC-3, AC-6, AC-7, AC-8, AC-9
- **主な変更領域:** 新規 `karuta-tracker/src/main/java/com/karuta/matchtracker/converter/` に以下を新設。既存ファイルの変更なし。
  - `LineCredentialCipher` — base64 32B 鍵から AES/GCM/NoPadding。`encrypt(plain) -> "enc:v1:"+base64(IV(12)‖ct‖tag(16))` / `decrypt("enc:v1:...") -> plain`。IV はレコード毎に `SecureRandom`。
  - `LineEncryptionKeyHolder` — `Optional<LineCredentialCipher>` を保持する静的ブリッジ（`set`/`clear`/`current`）。
  - `LineEncryptionKeyProvider`（`@Component`）— `@Value("${line.encryption-key:}")` を読み、非空なら検証＋`LineCredentialCipher` 構築して `LineEncryptionKeyHolder` に登録。空なら未登録のまま（起動は成功）。不正形式なら `@PostConstruct` で fail-fast。
  - `EncryptedStringConverter implements AttributeConverter<String,String>` — 書込: null 素通し／既に `enc:v1:` は二重暗号化しない／それ以外は暗号化（鍵無ければ `IllegalStateException` で fail-fast）。読取: null 素通し／`enc:v1:` 無しはパススルー（鍵不要）／`enc:v1:` 有りは復号（鍵無し・復号失敗は例外）。
- **依存タスク:** なし
- **必要なテスト（テストファースト・積極アサート）:** `LineCredentialCipherTest` / `EncryptedStringConverterTest`
  - 暗号化結果が `enc:v1:` 始まりで平文と異なる（AC-1）
  - 暗号化→復号の往復一致（AC-2）
  - 接頭辞なし平文はパススルー（AC-3）＝鍵ホルダ空でも成功（AC-6 の単体側）
  - 鍵ホルダ空で暗号化書き込み → `IllegalStateException`（AC-7）
  - `enc:v1:` を誤鍵で復号 → 例外（GCM 認証失敗を投げる。null/ゴミを返さない）（AC-8）
  - 32文字 secret の暗号文長 ≤ 255（AC-9）
  - null / 空文字の null 安全
- **完了条件:** 上記ユニットテスト green（`./gradlew test --tests "com.karuta.matchtracker.converter.*"`）。例外メッセージに平文・鍵を含めない。

### タスク2: LineChannel への @Convert 付与＋結合/回帰テスト＋テスト鍵設定
- [x] 完了
- **対応Issue:** #1129
- **目的:** コンバータを `LineChannel` の2フィールドに適用し、実 DB 永続化・webhook 署名検証・push トークン受け渡しが平文契約どおり動くことを結合レベルで担保。既存 LINE テスト群を green に保つ。
- **対応AC:** AC-2（永続化層）, AC-4, AC-5, AC-6（実DB側）, AC-10, AC-11
- **主な変更領域:**
  - `entity/LineChannel.java` — `channelSecret` / `channelAccessToken` に `@Convert(converter = EncryptedStringConverter.class)` を付与（`autoApply=false` 相当＝明示付与）。コメント「（暗号化保存）」が実装と一致する状態にする。
  - `src/test/resources/application-test.properties` — `line.encryption-key=<固定テスト用 base64 32B>` を追加（実 DB 書き込みテストの前提）。
- **依存タスク:** タスク1（コンバータ実体が必要。かつ共有ホットスポット `LineChannel` を触るため直列）
- **必要なテスト（積極アサート）:**
  - リポジトリ往復: `LineChannel` を secret 付きで保存 → 生カラム値が `enc:v1:` で始まる（native query で raw を読む）／`getChannelSecret()` は平文を返す（AC-2 実DB）
  - `verifySignature(復号後secret, body, signature)` が暗号化前と同一結果（AC-4）
  - push/reply 呼び出しに**平文** access_token が渡る（`LineMessagingService` をスパイし `setBearerAuth`/引数が平文であることを検証）（AC-5）
  - 鍵未設定コンテキストでレガシー平文チャネルの読み取りが成功（AC-6 実DB側。別テストプロファイル or ホルダ制御）
  - 既存 LINE 系テスト（webhook/notification/broadcast/repository/scheduler）が green（AC-10）
- **完了条件:** `./gradlew test` green、`npm run lint`（フロント無変更のため既存どおり）green（AC-11）。`git grep` で `channel_secret`/`channel_access_token` を平文で新規保存する経路が増えていないこと。

## 実装順序（Wave）
- Wave 1: タスク1（DB 非依存の暗号ロジック単体）
- Wave 2: タスク2（タスク1 に依存・`LineChannel` 共有ホットスポットを触るため直列）

（並行可能なタスクなし＝直列。security-sensitive のため各タスク完了時に main がテスト green を確認してから次へ）
