---
status: approved
issue: 1037
---
# バグ改修要件: ensurePlayerBelongsToOrganization の並列競合で呼び出し元Txが UnexpectedRollbackException → 500

## 再現手順

1. 未所属の選手について、同一 (playerId, organizationId) で `ensurePlayerBelongsToOrganization` を通る2つのリクエストを並列実行する（例: 練習参加登録 POST の二重送信）
2. 両方が `existsByPlayerIdAndOrganizationId` チェックを false で通過する（TOCTOU）
3. 後着の INSERT が `player_organizations` の UNIQUE (player_id, organization_id) に違反 → `DataIntegrityViolationException` を catch で握りつぶすが、トランザクションは既に rollback-only
4. 呼び出し元（参加登録等）のコミット時に `UnexpectedRollbackException` で 500、業務処理全体が silent rollback

## 根本原因

`karuta-tracker/src/main/java/com/karuta/matchtracker/service/OrganizationService.java` の `ensurePlayerBelongsToOrganization`（154-174行）:

- `PlayerOrganization.id` が `GenerationType.IDENTITY` のため、`save()` 呼び出し時点で即 INSERT が実行される
- 一意制約違反の `DataIntegrityViolationException` が `SimpleJpaRepository.save()` の `@Transactional` プロキシ（参加トランザクション）を通過した時点で、**呼び出し元トランザクションが rollback-only にマークされる**（Hibernate 側も同様にマークする）
- サービス側の catch で例外を握りつぶして正常続行しても rollback-only は解除できず、呼び出し元のコミットが `UnexpectedRollbackException` になる

影響を受ける呼び出し元（すべて `@Transactional` 内から呼ぶ）:
- `PracticeParticipantService.registerParticipations`（ユーザー向け参加登録。実発火リスクが最も高い: 二重送信・二重タップ）
- `PlayerService`（一括更新の団体追加）
- `DensukeImportService`（伝助経由の新規選手登録）

本番DB introspect 済み（2026-07-14）: `player_organizations` に `UNIQUE (player_id, organization_id)` が実在（`player_organizations_player_id_organization_id_key` と Hibernate 生成 `uk1uy39udwlygl2sxe5h5iexcbi` の2本、FKなし）。

## 修正方針

**例外を発生させない原子的挿入に置き換える**（例外ハンドリングの改善ではなく、例外の発生自体を除去する）:

1. `PlayerOrganizationRepository` に native query を追加:
   `INSERT INTO player_organizations (player_id, organization_id, created_at) VALUES (...) ON CONFLICT (player_id, organization_id) DO NOTHING`（`@Modifying`、戻り値 = 挿入行数）
   - PostgreSQL 専用構文だが、本番・ローカル・テスト（Testcontainers）すべて PostgreSQL で問題なし。`database/migration_organization.sql` でも既に `ON CONFLICT` を使用しており、コードベースの前例あり
   - `created_at` は `@PrePersist` を経由しないため `JstDateTimeUtil.now()` をパラメータで渡す（既存挙動と同一のJST基準）
2. `ensurePlayerBelongsToOrganization` の try/catch を廃止し、上記メソッドの戻り値で分岐:
   - 1（挿入成功）→ 従来どおり `createDefaultNotificationPreferences` を実行
   - 0（競合に負けた/既存）→ 通知設定はスキップして正常 return（従来の catch 節と同じ意図の冪等動作。ただし今回はトランザクションが汚染されない）
3. 事前の `existsByPlayerIdAndOrganizationId` チェックは fast path として維持（所属済みの大多数ケースで INSERT 試行を避ける）

採用しなかった代替案:
- `REQUIRES_NEW` での挿入分離（DensukeWriteService の先例）: 追加コネクション占有・呼び出し元ロールバック時に所属だけ残る半端な状態が生じるため不採用。ON CONFLICT 方式は呼び出し元Txと運命共同体のまま例外だけを除去できる
- AdjacentRoomNotificationScheduler 型の「Tx境界外バックストップ catch」: ensure はより大きな業務Txの一部として呼ばれるため境界外 catch を置く場所がなく不適

## Acceptance Criteria

| ID | 条件 | 検証手段 |
|----|------|------|
| AC-1 | exists チェック通過後に他リクエストが同一 (player_id, organization_id) を登録済みの状態（TOCTOU 競合）で `ensurePlayerBelongsToOrganization` を呼んでも、呼び出し元トランザクションが `UnexpectedRollbackException` にならず正常コミットされ、行は1件のまま | auto-test（回帰テスト・実PG/Testcontainers。修正前に fail することを確認） |
| AC-2 | 既存テスト（`./gradlew test`）・FE lint がすべて成功する（デグレードなし） | auto-test |
| AC-3 | 非競合時の既存挙動が不変: 新規所属時は `player_organizations` 追加+通知設定（push/LINE）デフォルト作成、所属済みなら no-op | auto-test（実PG統合テスト + 既存ユニットテスト更新） |

## Non-goals

- `updatePlayerOrganizations` の TOCTOU（DIVE を握りつぶしておらず例外が正常伝播して従来型ロールバックになるため、本バグ=silent rollback とは別性質）
- `DensukeWriteService.saveMemberMapping` の同型の DIVE 握りつぶし（別経路。別 Issue で扱う）
- 通知設定テーブル（push/LINE preference）側の並列競合対策（各 INSERT 前に存在チェックあり・ユーザー影響が未確認）
- 周辺リファクタ

## 影響範囲

- `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/PlayerOrganizationRepository.java` — native insert メソッド追加（既存メソッドは不変）
- `karuta-tracker/src/main/java/com/karuta/matchtracker/service/OrganizationService.java` — `ensurePlayerBelongsToOrganization` のみ変更。他メソッド（`updatePlayerOrganizations` 等）は不変
- 呼び出し元3箇所（PracticeParticipantService / PlayerService / DensukeImportService）はシグネチャ・意味論不変のため無修正
- FE への影響なし（API 契約不変）。DB スキーマ変更なし（既存の一意制約を利用するのみ）
- テスト: `OrganizationServiceTest`（ユニット）の save モックを insert メソッドのモックに更新 + 統合回帰テストを新規追加
