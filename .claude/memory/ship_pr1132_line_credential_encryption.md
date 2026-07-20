---
name: ship-pr1132-line-credential-encryption
description: LINE認証情報(channel_secret/channel_access_token)のAES-256-GCM暗号化を出荷（PR #1132、親#1130子#1128#1129）
type: project
category: ship
tags: [line, encryption, aes-gcm, jpa-converter, security, credential, deploy-prereq]
---

# PR #1132 出荷記録 — line-credential-encryption

- PR: https://github.com/poponta2020/match-tracker/pull/1132
- 親 Issue: #1130（子 #1128 タスク1 / #1129 タスク2。PR 本文の closing keyword で自動クローズ）
- 要件書: `docs/features/line-credential-encryption/requirements.md`
- 背景: 配布可能性調査 [[audit_third_party_club_deployment]] の S-3。`application.properties` の死んだ `line.encryption-key` と「（暗号化保存）」コメントと実装（平文保存）の乖離を埋める
- 出荷日: 2026-07-20

## 何を変えたか（backend のみ・DB/スキーマ変更なし）

- 新規 `com.karuta.matchtracker.converter` パッケージ:
  - `LineCredentialCipher` — AES/GCM/NoPadding。`enc:v1:` + base64(IV12‖ct‖tag16)、IV はレコード毎に `SecureRandom`、tag 128bit。誤鍵/破損は `AEADBadTagException`→`IllegalStateException`（null/ゴミを返さない）
  - `LineEncryptionKeyHolder` — JPA コンバータ（Hibernate 生成＝Spring 管理外）へ鍵を渡す `static volatile` ブリッジ（set/clear/current）
  - `LineEncryptionKeyProvider`（`@Component`）— `@PostConstruct` で `${line.encryption-key:}` を検証しホルダへ。空=no-op で起動可（LINE 未使用クラブ維持）・不正=fail-fast
  - `EncryptedStringConverter`（`@Convert` 明示付与・autoApply=false）— 書込暗号化 / 既に `enc:v1:` は二重暗号化せず / 読取は接頭辞無=レガシー平文パススルー（鍵不要）・有=復号。null/空は素通し。鍵未設定での暗号化書込は fail-fast
- `LineChannel.channelSecret`/`channelAccessToken` に `@Convert` 付与。カラムは `varchar(255)`/`TEXT` のまま＝**本番 DB マイグレーション不要**
- 呼び出し側17箇所（署名検証・push/reply・quota・group member count）は従来どおり平文を受け取る（getter 契約不変）

## 設計の肝（advisor 2回で確定）

- **鍵注入**: JPA コンバータは Spring 管理外で `@Value` 不可 → 静的ホルダを `@Component @PostConstruct` が populate。
- **テストの罠**: `@DataJpaTest` スライスは `@Component` provider を読み込まない → ホルダ空 → 暗号化書込が fail-fast。∴ `application-test.properties` に鍵を置くだけでは足りない（full-context のみ有効）。解=**Option B: ホルダを触る全テストが `@BeforeEach`/`@AfterEach` で自己管理**。既存 `LineChannelBroadcastRepositoryTest`（唯一の実DB LineChannel 永続化テスト）もこの方式でパッチ。
- **前提検証**: backend テストは `useJUnitPlatform()` のみ＝逐次単一JVM（`maxParallelForks` 無し）→ static mutable ホルダはデータ競合しない。本番に `channel_secret`/`channel_access_token` の raw 読取ゼロ（`@Column` と getter 17箇所のみ、`@Query` は `monthlyMessageCount` の bulk UPDATE のみ）→ ciphertext 素通しのサイレント障害経路なし。

## auto-review（Codex CLI・effort high）

**R1 で pass 収束（blockers 0 / should_fix 0 / nits 0、37.5k/500k tok）。偽陽性ゼロ＝意図設計10項目をレビュープロンプトに先回り明記**（[[ship_pr1114_auth_tokenization]]・[[ship_pr1127_line_chat_auto_relogin]] と同じ）。codex は中立cwd＋stdin で実行（[[auto_review_round_pr1102]] の再帰偽pass 回避）。詳細は harness memory [[impl_line_credential_encryption]]。

## テスト・AC

- 全11 AC を auto-test で担保: Cipher 9 / Converter 7 / Integration 4（実DB=Testcontainers）/ 既存 Broadcast 3、full backend suite BUILD SUCCESSFUL。フロント無改修ゆえ lint スコープ外。
- DoD: A1/A2/A3=SKIP（CI委譲・FEスコープ外・typecheck未定義）・B1 CI=PASS（pending でマージ）・C1 レビュー=PASS・D1 memory=PASS・D2 docs=PASS。

## 出荷後の要注意（最重要・デプロイ前提）

- **`LINE_ENCRYPTION_KEY`（base64 32バイト、`openssl rand -base64 32`）を Render に、次のデプロイ前に設定すること（運営作業）。** `@Convert` 稼働後はエンティティ UPDATE のフラッシュで両列が新IVで再暗号化されるため、稼働中2団体（北大・わすら）の channel write（message-count sync 等）は鍵未設定だと fail-fast する。読取のみの行は passthrough で無害。
- **不正な鍵はアプリ全体を起動 fail-fast** させる（`@PostConstruct` の設計通り）。
- **鍵は全環境同一値・永久に不変**（紛失/変更で暗号化済み行が復号不能。rotation は Non-goal＝[[ship_pr1114_auth_tokenization]] と同じ一方通行ドア）。
- 既存60チャネル（平文）は接頭辞パススルーで共存し、初回 write 時に遅延暗号化される（一括移行は Non-goal）。
- CI（test）が pending のままマージ済み。赤になったら /quickfix で追修正。
