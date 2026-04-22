---
status: completed
parent_issue: 489
---

# 抽選機能 管理者指定優先選手 実装手順書

## Issue 対応表

| タスク | Issue |
|-------|-------|
| タスク1: DBマイグレーション + Entity | #490 |
| タスク2: DTO更新 | #491 |
| タスク3: 月次参加希望者API | #492 |
| タスク4: 優先選手バリデーション | #493 |
| タスク5: 抽選アルゴリズム3層化 | #494 |
| タスク6: Controller + AdminScopeValidator | #495 |
| タスク7: 再抽選 + organizationId/seed修正 | #496 |
| タスク8: フロントAPIクライアント | #497 |
| タスク9: UI実装 | #498 |
| タスク10: ドキュメント更新 | #499 |

## 実装タスク

### タスク1: DBマイグレーションと Entity の更新

- [x] 完了
- **概要:** `lottery_executions` テーブルに `priority_player_ids`（JSON文字列）列を追加し、`LotteryExecution` エンティティにフィールドとヘルパーメソッドを追加する。
- **変更対象ファイル:**
  - `database/add_priority_player_ids_to_lottery_executions.sql` — 新規作成。`ALTER TABLE lottery_executions ADD COLUMN priority_player_ids TEXT NULL;`
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/LotteryExecution.java` — `priorityPlayerIdsJson` フィールド追加、`getPriorityPlayerIds() / setPriorityPlayerIds(List<Long>)` ヘルパー追加（`@Transient`、ObjectMapperでJSON変換）
- **依存タスク:** なし
- **対応Issue:** 下記参照

### タスク2: DTO の更新とリクエストバリデーション

- [x] 完了
- **概要:** `LotteryExecutionRequest` に `priorityPlayerIds` フィールドを追加し、再抽選リクエストDTOにも同フィールドを追加する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/LotteryExecutionRequest.java` — `List<Long> priorityPlayerIds`（デフォルト空リスト）を追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/ReLotteryRequest.java`（既存がなければ新規作成） — `List<Long> priorityPlayerIds` フィールド追加
- **依存タスク:** なし
- **対応Issue:** 下記参照

### タスク3: 月次参加希望者一覧取得APIの実装

- [x] 完了
- **概要:** `GET /api/lottery/monthly-applicants` エンドポイントを追加し、対象月・団体で参加希望を出している選手一覧を級順で返す。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryService.java` — `getMonthlyApplicants(int year, int month, Long organizationId)` メソッドを新設。`LotteryParticipant` から重複排除した `playerId` 集合を取得し、`playerRepository.findAllById` で Player を取得、`PlayerSortHelper.playerComparator()` でソート、DTOに変換して返す
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java` — `GET /monthly-applicants` エンドポイントを追加。`@RequireRole({SUPER_ADMIN, ADMIN})`、ADMINは自団体に強制 + `AdminScopeValidator.validateScope` 適用
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/MonthlyApplicantDto.java` — 新規作成。`playerId, name` のみ保持
- **依存タスク:** なし
- **対応Issue:** 下記参照

### タスク4: 優先選手バリデーションロジックの実装

- [x] 完了
- **概要:** `priorityPlayerIds` が「参加希望なし選手」「他団体選手」を含む場合に明示的にエラーを返すバリデーション関数を追加する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryService.java` — `validatePriorityPlayerIds(List<Long> ids, int year, int month, Long organizationId)` メソッドを新設。対象月の `LotteryParticipant` の `playerId` 集合と突き合わせ、未参加のIDは `IllegalArgumentException`、`Player.organizationId != organizationId` は `SecurityException`（Controller層で403へマップ）
- **依存タスク:** タスク3
- **対応Issue:** 下記参照

### タスク5: 抽選アルゴリズムの3層分類化

- [x] 完了
- **概要:** `processMatch` を管理者指定優先 > 連続落選救済 > 一般の3層分類に変更する。キャンセル待ち順も3層順（優先落選→救済落選→一般落選）にする。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryService.java`
    - `previewLottery` / `executeLottery` / `executeAndConfirmLottery` のシグネチャに `List<Long> priorityPlayerIds` を追加
    - `processSession` / `processMatch` に `Set<Long> adminPriorityPlayers` を伝搬
    - `processMatch` の内部分類ロジックを3層に変更（管理者指定優先の抽選 → 連続落選救済（一般枠最低保証考慮）→ 一般枠）
    - 落選者のキャンセル待ち追加順を「管理者優先落選 → 救済落選 → 一般落選」に変更
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/LotteryServiceTest.java` — 以下のテストケースを追加：
    - 管理者優先指定なしの場合、従来通りの動作となる
    - 管理者優先指定ありで定員内の場合、全員当選する
    - 管理者優先指定ありで定員超過の場合、優先選手同士で抽選される
    - 優先選手が落選した場合、キャンセル待ちの最上位に入る
    - 優先選手が希望していないセッションでは優先扱いにならない
- **依存タスク:** タスク1, タスク2
- **対応Issue:** 下記参照

### タスク6: Controller の preview/confirm を優先選手対応に変更

- [x] 完了
- **概要:** `previewLottery` / `confirmLottery`（`executeAndConfirmLottery`）でリクエストの `priorityPlayerIds` を受け取り、バリデーション + Serviceへの伝搬を行う。`previewLottery` には `AdminScopeValidator.validateScope` を新たに追加する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java`
    - `previewLottery` に `AdminScopeValidator.validateScope` を追加（ADMIN が他団体IDを送った場合に403）
    - `previewLottery` / `confirmLottery` で `request.getPriorityPlayerIds()` を取得 → `lotteryService.validatePriorityPlayerIds(...)` → Service呼び出しに引き渡し
    - `confirmLottery` で確定時に `LotteryExecution.setPriorityPlayerIds(ids)` を保存
    - `SecurityException` は 403 に、`IllegalArgumentException` は 400 にマップされる既存のExceptionHandlerで対応可能か確認。未対応なら個別ハンドリング追加
- **依存タスク:** タスク4, タスク5
- **対応Issue:** 下記参照

### タスク7: 再抽選の優先選手対応

- [ ] 完了
- **概要:** `reExecuteLottery` で初回の `priorityPlayerIds` を引き継ぎ、リクエストで上書き指定された場合はそれを使う。再抽選履歴にも記録する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryService.java`
    - `reExecuteLottery(sessionId, priorityPlayerIds)` のようにoptional引数を受け付ける
    - 省略時は初回の `LotteryExecution.priorityPlayerIds` を取得して流用
    - 指定時はバリデーション実行後に流用
    - 再抽選の `LotteryExecution` レコードにも `priorityPlayerIds` を保存
    - 併せて監査レポートで指摘されていた `organizationId` / `seed` の保存漏れも同時に解消する（再抽選記録の整合性確保）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java`
    - `reExecuteLottery` エンドポイントでリクエストボディの `priorityPlayerIds` を受け取り Service に渡す
- **依存タスク:** タスク5
- **対応Issue:** 下記参照

### タスク8: フロントエンドAPIクライアントの更新

- [ ] 完了
- **概要:** `api/lottery.js` に `getMonthlyApplicants` を追加し、`preview` / `confirm` / `reExecute` のシグネチャを `priorityPlayerIds` を含むように拡張する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/api/lottery.js`
    - `getMonthlyApplicants(year, month, organizationId)` 追加
    - `preview(year, month, organizationId, priorityPlayerIds)` に4引数目追加（optional）
    - `confirm(year, month, organizationId, seed, priorityPlayerIds)` に5引数目追加（optional）
    - `reExecute(sessionId, priorityPlayerIds)` に2引数目追加（optional）
- **依存タスク:** タスク3, タスク6, タスク7
- **対応Issue:** 下記参照

### タスク9: LotteryManagement 画面に優先選手指定UIを追加

- [ ] 完了
- **概要:** 抽選管理画面に「参加希望者一覧（優先選手指定）」セクションを追加する。チップUIで選手名を表示、クリックで選択切り替え、選択済み人数を表示、sessionStorageに保存。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/lottery/LotteryManagement.jsx`
    - 新規 state: `applicants`, `priorityPlayerIds`
    - `year / month / selectedOrgId` の変更 useEffect で `lotteryAPI.getMonthlyApplicants` を呼ぶ + `sessionStorage` から当該キーの選択状態を復元
    - `priorityPlayerIds` 変更時に `sessionStorage.setItem` で保存
    - 「参加希望者一覧」セクションのJSXを追加（チップ、選択トグル、選択済み人数表示）
    - `phase === 'confirmed'` のときはチップを disabled にする
    - プレビューボタン押下時に `priorityPlayerIds` をAPIリクエストに含める
    - 確定ボタン押下時に `priorityPlayerIds` をAPIリクエストに含める + 確定成功時に `sessionStorage.removeItem(key)`
  - `karuta-tracker-ui/src/pages/lottery/LotteryManagement.jsx` 内のスタイリングは既存の選手関連チップがあれば流用、なければ Tailwind で `bg-blue-100 border border-blue-400` (選択中) / `bg-gray-100 border border-gray-300` (未選択) のシンプルな形で作成
- **依存タスク:** タスク8
- **対応Issue:** 下記参照

### タスク10: ドキュメント更新

- [ ] 完了
- **概要:** `docs/SPECIFICATION.md`, `docs/SCREEN_LIST.md`, `docs/DESIGN.md` に本機能の内容を反映する。
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` — 抽選機能の仕様に「管理者指定優先選手」の項目追加、API仕様表に `GET /monthly-applicants` 追加、`preview`/`confirm`/`re-execute` のリクエスト項目更新、DBスキーマ表に `lottery_executions.priority_player_ids` 追加
  - `docs/SCREEN_LIST.md` — LotteryManagement画面の機能一覧に「参加希望者一覧・優先選手指定」を追記
  - `docs/DESIGN.md` — 抽選アルゴリズムの章に3層優先順位ロジックの記載追加、監査ログ（LotteryExecution.priorityPlayerIds）の扱いを追記
- **依存タスク:** タスク1〜タスク9
- **対応Issue:** 下記参照

---

## 実装順序

1. タスク1（DB・Entity）
2. タスク2（DTO） ※タスク1と並行可
3. タスク3（月次参加希望者API） ※タスク1・2と並行可
4. タスク4（バリデーション） — タスク3に依存
5. タスク5（抽選アルゴリズム3層化） — タスク1・2に依存
6. タスク6（Controller preview/confirm 対応） — タスク4・5に依存
7. タスク7（再抽選対応） — タスク5に依存（タスク6と並行可）
8. タスク8（API Client） — タスク3・6・7に依存
9. タスク9（UI実装） — タスク8に依存
10. タスク10（ドキュメント更新） — 全タスクに依存

---

## テスト方針

- **バックエンド単体テスト:** タスク5 のロジック変更について、従来の挙動を壊していないことを確認するテストを追加（`priorityPlayerIds` が空の場合は既存テストと同じ結果になる）
- **バックエンド結合テスト:** preview→confirm→re-execute の一連のフローで `priorityPlayerIds` が正しく引き継がれる・上書きできることを確認
- **フロントエンド手動テスト:**
  - 年月・団体を切り替えて参加希望者一覧が更新されること
  - チップ選択が sessionStorage に保存され、再プレビューで維持されること
  - 確定後に sessionStorage がクリアされること
  - 確定済み状態でチップが disabled になること
  - 画面リロードで state がリセットされても sessionStorage から復元されること
  - ADMIN で他団体IDを送ろうとしても UI上はそもそも送れないこと（SUPER_ADMINと挙動が違うので確認）

---

## リスクと緩和策

| リスク | 緩和策 |
|-------|-------|
| 抽選アルゴリズムの変更で既存の動作にバグが混入 | `priorityPlayerIds` が空の場合は従来通りの動作になることを単体テストで担保 |
| JSON列への保存時のシリアライズエラー | エンティティのヘルパーメソッドで ObjectMapper を使い、try-catch でJSON破損時は空リストにフォールバック |
| `previewLottery` への `validateScope` 追加で既存UIが壊れる | 既存UIは常に自団体IDしか送らないので実害なし。ADMIN の UI で `organizationId` を送っているかコードレビューで確認 |
| 参加希望者が多い場合の画面パフォーマンス | 初期版は一覧表示のみ。問題があれば後日ページング対応 |
