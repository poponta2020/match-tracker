---
status: approved
issue: 1036
---
# バグ改修要件: 伝助メンバーマッピングの一意制約違反1件で書き込みバッチ全体が破棄される

## 再現手順
1. `densuke_member_mappings` に (densuke_url_id=U, densuke_member_id=M) が別トランザクションで登録済みの状態を作る
2. `@Transactional` バッチ内で事前チェックがすり抜ける状況（TOCTOU: チェック後・INSERT 前に並行登録）で `saveMemberMapping(U, playerId, M, name)` を実行する
3. 同一トランザクション内で後続の DB 操作を行い、コミットする

→ 期待は「衝突1件のみスキップされバッチの他の結果はコミットされる」だが、実際は 25P02 で後続 DB 操作がすべて失敗し、コミットが `UnexpectedRollbackException` となってバッチ全体が破棄される。

## 根本原因
`karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeWriteService.java` の `saveMemberMapping`（685行付近の `save()`）。

- `DensukeMemberMapping` は IDENTITY 採番のため `save()` 時点で即 INSERT が発行される
- PostgreSQL は制約違反が発生した時点で**現トランザクション全体を abort 状態**（25P02: current transaction is aborted）にする
- そのため catch 節（692-703行）の TOCTOU 救済（`findByDensukeUrlIdAndDensukeMemberId` での再取得）は abort 済みトランザクション上で実行されて必ず失敗し、例外が呼び出し元へ伝播する
- さらに `SimpleJpaRepository.save` のプロキシ境界を例外が通過した時点で外側トランザクションが rollback-only にマークされ、バッチ内の後続 DB 操作（他プレイヤーのマッピング・row_id 保存・`dirty=false` 更新）も 25P02 で全滅、外側コミットは `UnexpectedRollbackException` になる

影響経路:
- `writeToDensuke`（スケジューラ・30分毎）/ `writeToDensukeForOrganization`（手動同期）: バッチ全体の DB 効果が破棄される。伝助への HTTP 書き込みは実行済みのため `dirty=true` が残り続け、次回実行でも同じ衝突→ERROR を繰り返す
- `writeAllForLotteryConfirmation`（REQUIRES_NEW）: 書き戻し全体が失敗扱い。抽選確定 DB 自体は `LotteryService` 側の TransactionTemplate 先行コミット + catch(Exception) で保護されている（executeAndConfirmLottery / confirmLottery 両経路とも確認済み）

`AdjacentRoomNotificationScheduler` の同型バグ（Issue #1034 / PR #1035）と同根。

## 修正方針
マッピングの INSERT を新設の小さな Bean（`DensukeMemberMappingWriter`）経由の `@Transactional(propagation = REQUIRES_NEW)` で隔離する。

- 一意制約違反は**内側トランザクションのみ**を abort し、外側バッチのトランザクション（別コネクション）は健全なまま残る
- これにより catch 節の TOCTOU 救済（再取得→同一プレイヤーなら成功扱い）が設計どおり機能するようになる
- マッピングは冪等なマスターデータであり、外側バッチが後で rollback しても先行コミットされたマッピングが残ることに害はない（次回は事前チェックで同一プレイヤー成功扱いになるだけ）
- 事前チェック（`findByDensukeUrlIdAndDensukeMemberId`）は既存実装のまま外側トランザクションで行う（衝突の大半はここで捕捉され、REQUIRES_NEW は TOCTOU 時のみ例外を吸収するバックストップ）
- 別 Bean にするのは Spring の self-invocation ではプロキシを通らず `@Transactional` が効かないため

exists 事前チェック方式（PR #1035）でないのは、本メソッドには事前チェックが既に存在し、残っている穴が「チェック後の並行登録で INSERT 自体が失敗するケース」そのものであるため。

## Acceptance Criteria
| ID | 条件 | 検証手段 |
|----|------|------|
| AC-1 | `@Transactional` バッチ内で `saveMemberMapping` が一意制約違反（TOCTOU）に遭遇しても、(a) 同一トランザクションの後続 DB 操作が失敗しない、(b) 外側トランザクションのコミットが成功し衝突以外のバッチ内書き込みが永続化される、(c) 同一プレイヤーなら true／別プレイヤーなら false の既存契約を維持する（実 PostgreSQL で検証） | auto-test（回帰テスト: TestContainers 統合 `DensukeMemberMappingTxIntegrationTest`） |
| AC-2 | `saveMemberMapping` の既存4分岐（事前チェックで同一/別プレイヤー検出、TOCTOU 後に同一/別プレイヤー判明）の契約がユニットテストで維持される | auto-test（`DensukeWriteServiceTest`） |
| AC-3 | 既存テスト（`./gradlew test`）がすべて成功する（デグレードなし） | auto-test |

## Non-goals
- `OrganizationService.ensurePlayerBelongsToOrganization` の同型バグ（別タスクで対応）
- `AdjacentRoomNotificationScheduler` の同型バグ（PR #1035 で対応済み）
- 別プレイヤー競合時のエラーメッセージ改善・周辺リファクタ
- `writeToDensukeInternal` / `writeAllForLotteryConfirmation` のバッチ構造自体の見直し

## 影響範囲
- 変更: `DensukeWriteService.saveMemberMapping`（insert 呼び出しを Writer 経由に差し替え。戻り値契約・呼び出し元 3 箇所の挙動は不変）
- 新規: `service/DensukeMemberMappingWriter.java`（REQUIRES_NEW の insert 専用 Bean）
- テスト: `DensukeWriteServiceTest`（saveMemberMapping 系 5 件を Writer モックに更新）、`DensukeMemberMappingTxIntegrationTest`（新規回帰テスト）
- 呼び出し元（`writePlayerToDensuke` / `insertMember` 経由の `writeToDensuke` / `writeToDensukeForOrganization` / `writeAllForLotteryConfirmation`）への API 変更なし
- DB スキーマ変更なし（migration 不要）
- FE への影響なし
- docs: `docs/spec/densuke.md` のメンバーマッピング記述にトランザクション隔離の一文を追記
