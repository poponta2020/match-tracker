# 機能監査レポート: 抽選結果作成機能

- **調査日**: 2026-04-23
- **対象機能**: ADMIN / SUPER_ADMIN が月次の練習参加希望に対して抽選を実行し、結果を確定するまでの一連のフロー
- **監査種別**: 現状の実装と仕様書の整合性確認、バグ/負債/セキュリティ/パフォーマンスの棚卸し

---

## 1. 機能概要

主な操作ステップ:

1. 締め切り後、管理者が `/admin/lottery` 画面で対象月・団体・優先選手を指定
2. 「抽選実行（プレビュー）」で `POST /api/lottery/preview` を叩き、DB保存なしのプレビューを表示（シード値が返る）
3. プレビューを確認したら「結果を確定する」で `POST /api/lottery/confirm` を叩き、プレビューと同じシードで抽選を再実行しつつ DB に保存、`lottery_executions.confirmed_at / confirmed_by` を記録、伝助への一括書き戻し（WON→○ / WAITLISTED→△ / その他→×）をトリガーする
4. 確定後、「全員通知」または「キャンセル待ちのみ通知」でアプリ内＋LINE通知を送信

抽選アルゴリズムは3層優先（管理者指定優先 > 連続落選救済 > 一般）+ 連鎖落選（前試合落選者は次試合で低優先扱い）。セッション再抽選は `POST /api/lottery/re-execute/{sessionId}`。自動抽選スケジューラ（`LotteryScheduler`）は現在コメントアウトされ **手動実行のみ** で運用されている。

## 2. 関連ファイル一覧

### バックエンド

| ファイル | 役割 |
|---------|------|
| `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java` | `/api/lottery/**` エンドポイント（preview / execute / confirm / re-execute / monthly-applicants / notify-* など全18エンドポイント） |
| `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryService.java` | 抽選ロジック（processSession / processMatch / previewLottery / executeAndConfirmLottery / reExecuteLottery / validatePriorityPlayerIds / buildLotteryResult / editParticipants / confirmLottery（旧）） |
| `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryDeadlineHelper.java` | 締め切り判定（isBeforeDeadline / isNoDeadline など） |
| `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryQueryService.java` | `isLotteryConfirmed()` の参照専用サービス（循環依存回避） |
| `karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/LotteryScheduler.java` | 自動抽選スケジューラ（`@Scheduled`・`@EventListener` ともコメントアウトされ無効） |
| `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/LotteryExecutionRequest.java` | 実行/プレビュー/確定リクエストDTO（year/month/organizationId/seed/priorityPlayerIds） |
| `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/LotteryResultDto.java` | 抽選結果レスポンスDTO |
| `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/LotteryExecution.java` | 抽選実行履歴エンティティ（priorityPlayerIds は JSON 文字列カラムに手動 (de)serialize） |
| `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/LotteryExecutionRepository.java` | 抽選実行履歴リポジトリ |
| `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/PracticeParticipantRepository.java` | `findMonthlyLoserPlayerIds`, `findBySessionIdAndStatus` など（L325-328） |
| `karuta-tracker/src/test/java/com/karuta/matchtracker/service/LotteryServiceTest.java` | processMatch の3層優先 + reExecute 引き継ぎテスト（6件） |

### フロントエンド

| ファイル | 役割 |
|---------|------|
| `karuta-tracker-ui/src/pages/lottery/LotteryManagement.jsx` | 抽選管理画面（プレビュー→確定→通知の主フロー） |
| `karuta-tracker-ui/src/pages/lottery/LotteryResults.jsx` | 抽選結果閲覧画面（全ロール共通。SUPER_ADMINのみ「結果を確定する」ボタン表示） |
| `karuta-tracker-ui/src/api/lottery.js` | 抽選関連APIクライアント |
| `karuta-tracker-ui/src/App.jsx` | ルーティング（L103: `/lottery/results`, L133: `/admin/lottery`） |
| `karuta-tracker-ui/src/pages/SettingsPage.jsx` | 抽選管理画面への導線（L136） |

### DBスキーマ

| テーブル / ファイル | 役割 |
|-------------------|------|
| `lottery_executions` | 抽選実行履歴（target_year / target_month / execution_type / session_id / executed_by / executed_at / status / details(TEXT) / confirmed_at / confirmed_by / organization_id / priority_player_ids(TEXT)） |
| `database/add_lottery_confirmation.sql` | confirmed_at / confirmed_by カラム追加 |
| `database/add_organization_id_to_lottery_executions.sql` | organization_id 追加 + インデックス |
| `database/add_priority_player_ids_to_lottery_executions.sql` | priority_player_ids 追加 |

## 3. 処理フロー（プレビュー→確定）

1. **フロントエンド (`LotteryManagement.jsx` L46-85)**: 画面表示時、`lotteryAPI.getMonthlyApplicants` で対象月・団体の参加希望者を取得し、優先選手ボタンUIを表示。`sessionStorage` に優先選手選択を保存・復元
2. **APIリクエスト (preview)**: `POST /api/lottery/preview` `{year, month, organizationId, priorityPlayerIds}` を送信（L118-132）
3. **Controller (`LotteryController.previewLottery` L100-135)**:
   - `@RequireRole({SUPER_ADMIN, ADMIN})` + `AdminScopeValidator.validateScope()` で自団体強制
   - `lotteryDeadlineHelper.isBeforeDeadline` で締め切り前は403
   - `lotteryService.isLotteryConfirmed` で既に確定済みなら403
   - `lotteryService.validatePriorityPlayerIds` で他団体・参加希望なしを拒否
   - `lotteryService.previewLottery` を呼び、`{results, seed}` を返却
4. **Service (`LotteryService.previewLottery` L493-541)**: `@Transactional(readOnly=true)` で `processSession` を `saveResults=false` で実行し、インメモリの参加者ステータスから DTO を組み立て。新規シード（`new Random().nextLong()`）を返す
5. **processMatch 3層アルゴリズム (L238-479)**:
   1. 前試合落選者（cascade）とその他に分離
   2. remaining を `adminPriorityPlayers` / `monthlyLosers` (rescue) / general に分類
   3. admin優先→rescue+general（`normalReservePercent` で一般枠最低保証）→cascade の順に winner を埋める
   4. 落選者にキャンセル待ち番号（優先度: admin > rescue > general > cascade、連続試合では前試合の順番を引き継ぐ）
6. **レスポンス → フロントエンド**: `LotteryResultDto[]` と `seed` を `setPreviewResults / setLotterySeed` に保存、phase を `preview` に遷移
7. **APIリクエスト (confirm)**: 「結果を確定する」ボタンで `POST /api/lottery/confirm` `{year, month, organizationId, seed, priorityPlayerIds}`
8. **Controller (`LotteryController.confirmLottery` L729-770)**: 同様のバリデーション + seed 必須チェック → `lotteryService.executeAndConfirmLottery`
9. **Service (`executeAndConfirmLottery` L151-176)**: `executeLottery(…, seed, priorityPlayerIds)` で **DBに保存して** 抽選実行（同じ seed のためプレビューと同じ結果を再現）→ `confirmed_at / confirmed_by` をセット → `densukeWriteService.writeAllForLotteryConfirmation` をトリガー（失敗は warn ログのみ）
10. **通知送信**: 確定後に `POST /api/lottery/notify-results` または `/notify-waitlisted` を呼び、`NotificationService.createLotteryResultNotifications`（アプリ内）と `LineNotificationService.sendLotteryResults`（LINE）を一括送信

## 4. 仕様書との差異

| # | 仕様書の記載 | 実装の実態 | 差異の内容 |
|---|------------|----------|----------|
| 1 | `DESIGN.md` L1521 / `SPECIFICATION.md` L536: `lottery_executions.seed BIGINT` を持つ | entity `LotteryExecution` にも `lottery_executions` テーブルにも seed カラムが存在しない | **仕様書の誤り or 未実装**。seed は リクエストパラメータとして引き回されるだけで永続化されない（再抽選時に同一結果を再現できない） |
| 2 | `SPECIFICATION.md` L667: ナビゲーションに「抽選結果」リンクを追加 | 確認（App.jsx L103） | OK |
| 3 | `SPECIFICATION.md` L587: 「自動抽選スケジューラ」「毎日0:00（月末日のみ実行）」 | `LotteryScheduler` の `@Scheduled` と `@EventListener` はともにコメントアウト（L40-41, L60）「自動抽選は現在無効（手動実行のみ）」 | **仕様書・実装の解離**。自動抽選は無効化されているが、仕様書には稼働中として記載 |
| 4 | `SPECIFICATION.md` L1848: `POST /execute` 「手動抽選実行」 | 実装あり（L141-185）、ただし現在はプレビュー→確定フロー (`/preview` + `/confirm`) が正規のフロー。`/execute` は UI から呼ばれていない | 整合はしているが `/execute` は実質的な dead endpoint |
| 5 | `SPECIFICATION.md` L1860: `POST /same-day-join` | 実装あり（L761-790） | OK |
| 6 | `SPECIFICATION.md` L1849 「priorityPlayerIds省略可」 | `LotteryExecutionRequest` L41-42 で `@Builder.Default private List<Long> priorityPlayerIds = new ArrayList<>()` により省略可 | OK |

**仕様書に未記載の実装**:

- `executeAndConfirmLottery` という「実行+確定」を同一トランザクションで行うメソッド（`/confirm` API の内部実装）。仕様書では execute と confirm が別API扱いだが、実装上の `/confirm` は「プレビュー時と同じ seed で再計算 + 即確定」である
- `LotteryService.confirmLottery(year, month, confirmedBy, organizationId)` という年月ベースの旧 confirm メソッドが残存しているが、呼び出し元が存在しない（**dead code**）

**実装されていない仕様**:

- `lottery_executions.seed` カラム

## 5. 評価・懸念事項

### 5a. ロジックの正確性

#### A1. `LotteryResults.jsx` の「結果を確定する」ボタンが本番で必ず失敗する（バグ）

`LotteryResults.jsx` L50-65 の `handleConfirmLottery`:

```js
const organizationId = results.length > 0 ? results[0].organizationId : null;
await lotteryAPI.confirm(currentDate.year, currentDate.month, organizationId);
```

問題点:

- `LotteryResultDto` (`LotteryResultDto.java`) に **`organizationId` フィールドは存在しない** ため、`results[0].organizationId` は常に `undefined`
- `lotteryAPI.confirm(year, month, organizationId, seed, priorityPlayerIds=[])` のシグネチャに対し seed が渡されない
- Controller L785-788 で `if (seed == null) throw new IllegalStateException("シード値が指定されていません。プレビューを先に実行してください。")` により **常に 500 エラー**

このボタンは `isSuperAdmin && !confirmed` の条件で表示される（L166）ため、SUPER_ADMIN が抽選結果閲覧画面から確定しようとすると必ず失敗する。正規フローは `LotteryManagement.jsx` のプレビュー→確定だが、UI上「結果を確定する」ボタンが2画面にあるのが混乱のもと。

#### A2. `executeAndConfirmLottery` 内の `setPriorityPlayerIds` が冗長

`LotteryService.executeAndConfirmLottery` (L155-168):

```java
LotteryExecution execution = executeLottery(year, month, executedBy, ExecutionType.MANUAL, organizationId, seed, priorityPlayerIds);
if (execution.getStatus() != ExecutionStatus.SUCCESS) { return execution; }
execution.setPriorityPlayerIds(priorityPlayerIds);  // ← L126 で既にセット済み
execution.setConfirmedAt(JstDateTimeUtil.now());
```

`executeLottery` の内部（L126）で既に `execution.setPriorityPlayerIds(priorityPlayerIds)` を呼んで save しているため、2回目の setPriorityPlayerIds は冗長。バグではないが無駄。

#### A3. シード永続化なし → 再現不能

`executeLottery` のシードは引数で受け取るが DB に保存しない（`LotteryExecution` entity に seed カラム無し）。`ReLotteryDetails` の details JSON にはシードが含まれるが、`LotteryDetails`（通常実行）には含まれない。確定済み抽選の結果を後から検証・再計算したい場合、シードは `details` 未記録のため原理的に再現不能。監査性の観点で要検討。

#### A4. `densukeWriteService.writeAllForLotteryConfirmation` の失敗が catch で呑まれる

`executeAndConfirmLottery` L168-175:

```java
try { densukeWriteService.writeAllForLotteryConfirmation(organizationId, year, month); }
catch (Exception e) { log.error("Failed to write all to densuke after lottery confirmation: {}", e.getMessage(), e); }
```

伝助書き戻しが失敗してもトランザクションは確定する。「確定＋書き戻しは一体」という仕様上の意図（`SPECIFICATION.md` L627）と微妙に解離している。「DB確定は成功」「伝助書き戻しだけ失敗」の状態になりうるが、フロント側の表示は「書き戻しが実行されました」で固定（`LotteryManagement.jsx` L399）なのでユーザーは失敗を知ることができない。

#### A5. `findMonthlyLoserPlayerIds` の仕様境界

`PracticeParticipantRepository.java` L320-325:

```sql
AND ps.sessionDate < (SELECT ps2.sessionDate FROM PracticeSession ps2 WHERE ps2.id = :currentSessionId)
```

- 「厳密未満」なので **同じ日の別セッションで落選した人は rescue 対象に含まれない**
- organization_id でフィルタしていない → 別団体で落選した人が rescue 対象に混入する可能性（ただし、別団体の落選者がこの団体のセッションに参加希望を出しているケースは稀）

#### A6. `processSession` 実行中の `session.setCapacity` 副作用

`reExecuteLottery` L843-868 は、元の session 定員を保持した後 `session.setCapacity(originalCapacity - promotedInMatch)` で一時変更 → 処理後に `session.setCapacity(originalCapacity)` で戻す。エンティティをインプレースで書き換えているため、同一トランザクション内で他の処理が `session.getCapacity` を見ると**一時的に誤った値**になる。また例外時に `originalCapacity` に戻らないパスがある（`processMatch` 内で例外→try-catchで拾われる）。

#### A7. `processSession` 内で `venueRepository.findById` により capacity を設定し、entity を save していない

`LotteryService.processSession` L182-185:

```java
if (session.getCapacity() == null && session.getVenueId() != null) {
    venueRepository.findById(session.getVenueId())
            .ifPresent(venue -> session.setCapacity(venue.getCapacity()));
}
```

インメモリでは設定するが、`practiceSessionRepository.save(session)` を呼んでいないので DB 上の session.capacity は null のまま。プレビューと本実行で一致はするが、将来的に「capacity が null なら venue からフォールバック」のロジックが別所に散在すると不整合の温床になる。

### 5b. 設計の妥当性

#### B1. `LotteryController` が肥大化

824行。`/api/lottery` 配下に抽選そのもの（execute/confirm/preview/re-execute）、結果取得（results/my-results）、キャンセル・繰り上げ（cancel/respond-offer/decline-waitlist など）、通知（notify-*）、当日参加（same-day-join）など**異質な責務が同居**している。本監査テーマの「抽選結果作成」は execute/preview/confirm/re-execute だけなので、別Controller（`LotteryAdminController` / `WaitlistController` 等）への分離を検討すべき。

#### B2. `LotteryService` も 1121行 + LotteryQueryService 分離済みだが更に分離余地

`editParticipants`（L977-1053）は **管理者による参加者手動編集** であり抽選ロジックではない。繰り上げフローも含まれているため、`ParticipantAdminService` のような別サービスへの分離候補。

#### B3. `LotteryExecutionRequest` に jsr303 バリデーションが `year` / `month` のみ

seed（`Long`）と `priorityPlayerIds`（`List<Long>`）にバリデーション無し。priorityPlayerIds の要素数上限が無いため、`[1,2,...,10000]` のような異常ペイロードを送られると全量に対し `playerOrganizationRepository.existsByPlayerIdAndOrganizationId` が N 回呼ばれる。上限値（例 200）+ `@NotNull`ではなく `@NotNull @Valid List<@Positive Long>` 等にするとより堅い。

#### B4. 旧 `confirmLottery`（dead code）

`LotteryService.confirmLottery(year, month, confirmedBy, organizationId)` L1065-1108 は L729 の Controller からは呼ばれていない（Controller は `executeAndConfirmLottery` を呼ぶ）。削除候補。

#### B5. `LotteryExecution.getPriorityPlayerIds()` の JSON 解析エラーを silent swallow

```java
} catch (Exception e) { return Collections.emptyList(); }
```

Jackson が corrupted データを返した場合に監査情報が闇に消える。少なくとも `log.warn` したい。

### 5c. セキュリティ

#### C1. `@RequireRole` は適切

`/preview`, `/execute`, `/confirm`, `/re-execute`, `/monthly-applicants`, `/admin/edit-participants`, `/notify-*` はすべて ADMIN+ で保護。問題なし。

#### C2. AdminScopeValidator の適用漏れなし

`/preview`, `/execute`, `/confirm`, `/re-execute` のいずれも `ADMIN` ロール時は `adminOrgId` に強制される。`/re-execute/{sessionId}` は sessionId 経由で `validateAdminScopeBySessionId` 呼び出し。問題なし。

#### C3. `/notify-results` (L647-680) の ADMIN スコープ強制後、`lineNotificationService.sendLotteryResults(year, month)` を呼んでいる

このメソッドに **organizationId が渡されていない**。LineNotificationService 側で全団体送信になっていれば、ADMIN が自団体の「通知送信」ボタンを押すと他団体も含めた全員に LINE 送信されるリスクがある。監査対象外だが要確認。

#### C4. `/executions` (L770-776) が PLAYER にも開放

`@RequireRole({SUPER_ADMIN, ADMIN, PLAYER})` + organizationId フィルタ無しで `findByTargetYearAndTargetMonth` を返す。`priorityPlayerIds` カラムも含まれるので、**PLAYER が別団体の「誰が優先選手指定されたか」まで閲覧できる**。情報漏洩のリスクあり。

#### C5. `LotteryExecution` エンティティをそのまま JSON レスポンスで返している

`/execute`, `/confirm`, `/re-execute`, `/executions`。`confirmedBy`, `executedBy`, `details` JSON など内部情報がそのまま露出。レスポンス用 DTO への変換が望ましい。

### 5d. パフォーマンス

#### D1. `getMyLotteryResults` の N+1

`LotteryController.getMyLotteryResults` L237-257 で `for (PracticeSession session : sessions)` の中で `practiceParticipantRepository.existsBySessionIdAndPlayerId` を呼び、さらに `lotteryService.buildLotteryResult(session)` を呼ぶ。`buildLotteryResult` は内部で `findBySessionIdOrderByMatchAndStatus` + `playerRepository.findAllById` + `venueRepository.findById` を毎回実行するため、セッション数が N なら少なくとも **4N 回のクエリ**。

#### D2. `buildLotteryResult` の venue/player 取得が毎回

同 L800-805 で `playerRepository.findAllById(playerIds)` と `venueRepository.findById` が毎セッション実行される。月別API (`/results`) では N セッション分ループするので N+1 相当。選手/会場をまとめて先にロードしてキャッシュすべき。

#### D3. `validatePriorityPlayerIds` の N クエリ

`LotteryService.validatePriorityPlayerIds` L598-614 で `ids.stream().filter(id -> !playerOrganizationRepository.existsByPlayerIdAndOrganizationId(id, organizationId))` → ids の件数分クエリ。`existsByPlayerIdInAndOrganizationId(List<Long> ids, Long orgId)` のような一括取得に変えるとクエリ1本で済む。

#### D4. `isLotteryConfirmed` が orgId 指定時にクエリ2本

`LotteryQueryService.isLotteryConfirmed` L23-42 で orgId 指定時は「団体指定レコード」と「organization_id IS NULL のレコード」両方をチェック。正しい挙動だが、preview と confirm の両方で呼ばれるため抽選1回の操作で 4回クエリ。許容範囲だが改善余地あり。

#### D5. プレビューと確定で抽選アルゴリズムを2回実行

`/preview` でシードを発行してインメモリ実行、`/confirm` で同じシードを渡して本実行。同じ処理が2回走る（フロントのUI設計上の必然だがコストはかかる）。プレビュー結果を Redis 等にキャッシュして確定時に再利用するなどの最適化は将来課題。

### 5e. フロントエンド⇔バックエンド整合性

#### E1. LotteryResults.jsx の「結果を確定する」ボタンが確実に壊れている（前述 A1）← **最重要**

#### E2. `lotteryAPI.execute(year, month)` 使用箇所なし

`lottery.js` L4-6 で定義されているが、フロント内で呼ぶコードは存在しない（Grep でヒットせず）。実質的な dead code。

#### E3. `lotteryAPI.notifyResults(year, month)` が organizationId を渡さない

`lottery.js` L65-66:

```js
notifyResults: (year, month) => apiClient.post('/lottery/notify-results', { year, month }),
```

一方 `notifyWaitlisted` は organizationId を渡す（L89）。Controller 側の `/notify-results` は ADMIN なら httpRequest から orgId を取得するため結果的には動くが、SUPER_ADMIN が全団体対象で送る意図で叩くと全団体が対象になるのでテストケース要確認。

#### E4. プレビュー結果の `results[n].capacity` が `Integer` と `int` の混在

`LotteryResultDto` の session 単位 `capacity` は `Integer`（nullable）、match 単位 `capacity` は `int`（primitive）。フロントで `session.capacity` を null チェック（L319）しているが `match.capacity` はチェックなし。Service L701 で `session.getCapacity() != null ? session.getCapacity() : Integer.MAX_VALUE` としているので定員未設定セッションでは `matchResults[*].capacity = Integer.MAX_VALUE` が返るが、フロントはこれを `定員: 2147483647名` と表示する可能性（実際は session.capacity 側を表示しているので顕在化していないがリスク）。

#### E5. プレビュー中に優先選手を変更すると `idle` に戻してプレビュー結果をクリアする（L99-104）

良い設計だが、`previousMatchWaitlistOrder` の挙動含め再プレビューが必須。特に問題なし。

## 6. 総合評価

抽選アルゴリズム本体（`LotteryService.processSession` / `processMatch` 周辺）はよく整理されており、3層優先（管理者 > 救済 > 一般）と連鎖落選対策（`cascadeCandidates`）、連続試合のキャンセル待ち順番引き継ぎなど、**要求仕様に対する実装品質は高い**。 `@Transactional` 境界、idempotent な preview/confirm のシード設計、AdminScopeValidator による団体スコープ検証も適切。

一方で **以下の実害級バグ 1 件と設計負債が残っている**:

1. **`LotteryResults.jsx` の「結果を確定する」ボタンが必ず 500 エラー（A1）**。SUPER_ADMIN が「抽選結果」画面から確定しようとすると失敗するため、事実上 `/admin/lottery` からしか確定できない。この UI を呼んだユーザーは原因がわからず混乱する
2. **仕様書と実装の乖離**（seed カラム未実装、自動スケジューラが無効化されているのに仕様書は稼働前提）
3. **`LotteryController` と `LotteryService` の責務肥大**、PLAYER に `/executions` を開放している情報漏洩リスク、`/notify-results` への LINE 送信 orgId 漏れの疑い
4. `lotteryAPI.execute` や `LotteryService.confirmLottery(4引数版)` など **dead code の残存**

現状の仕様そのもの（管理者がプレビュー→確定→通知で操作する）は妥当。ただし **「抽選結果」画面からの確定ボタンは削除するか、正しい実装に直す必要がある**。仕様書・スケジューラの整理、PLAYER権限の `/executions` 露出対応、`notify-results` の orgId 明示渡しの見直しが推奨される。

## 7. 推奨アクション

| 優先度 | 内容 | 理由 |
|-------|------|------|
| **高** | `LotteryResults.jsx` の「結果を確定する」ボタン (L166-173) を削除、または正しく `LotteryManagement` に誘導するリンクに置換する | 現状必ず 500 エラー。SUPER_ADMIN が混乱する |
| **高** | `/api/lottery/executions` (Controller L770-776) の `priorityPlayerIds` を PLAYER には返さない、または ADMIN+ のみに制限する | 他団体の優先選手情報が PLAYER から閲覧可能な情報漏洩 |
| **高** | `lotteryAPI.notifyResults` に `organizationId` を追加し、`/notify-results` Controller (L634-679) でも受け取って LINE 送信時に渡す（`lineNotificationService.sendLotteryResults(year, month, orgId)`） | ADMIN が他団体に LINE 送信してしまう潜在バグ |
| 中 | `SPECIFICATION.md` / `DESIGN.md` の `lottery_executions.seed` カラム記載を削除、もしくは実装に合わせて seed 永続化を追加する | 仕様書との整合性。監査性の観点でも seed 永続化は有意義 |
| 中 | `LotteryScheduler` について「自動抽選は無効」という旨を `SPECIFICATION.md` L587 / L613 に明記する | 仕様書の乖離。新メンバーが自動抽選を前提に設計するのを防ぐ |
| 中 | `densukeWriteService.writeAllForLotteryConfirmation` の失敗を握りつぶさず、`LotteryExecution.status` を `PARTIAL` にする、または専用のフィールド `densukeWriteStatus` を追加して UI に伝える | 「確定したのに伝助に反映されていない」事態を管理者が即座に検知できない |
| 中 | `LotteryExecution` エンティティを Controller レスポンスでそのまま返すのを DTO 化する | `executedBy` / `confirmedBy` / `details` JSON などの内部情報露出を防ぐ |
| 中 | `validatePriorityPlayerIds` の N クエリ問題を `existsByPlayerIdInAndOrganizationId` 系で1クエリ化 | パフォーマンス改善（件数大の場合） |
| 低 | `LotteryService.confirmLottery(int, int, Long, Long)` (L1066-) を削除 | dead code |
| 低 | `lotteryAPI.execute` を削除、または UI から呼ばれる計画があるなら仕様書に明記 | dead code |
| 低 | `LotteryController` を抽選管理系・結果閲覧系・キャンセル/繰り上げ系に分割 | 責務整理。824行は可読性・テスト容易性の点で負債 |
| 低 | `LotteryService.editParticipants` を別 Service に切り出し | LotteryService の責務圧縮 |
| 低 | `LotteryExecution.getPriorityPlayerIds` の JSON 解析失敗を `log.warn` する | 監査情報の消失を検知するため |
| 低 | `LotteryExecutionRequest` の `priorityPlayerIds` に件数上限バリデーション追加（例: `@Size(max=200)`） | 異常ペイロード防御 |
| 低 | `previewLottery` / `executeAndConfirmLottery` / `validatePriorityPlayerIds` に対するユニットテストを追加 | 現行テストは processMatch と reExecute inheritance のみ |
