# 伝助連携

> **責務:** 伝助（Densuke）出欠表との双方向同期・スクレイピング・削除検知の仕様
>
> **関連画面:** `/admin/densuke`（伝助管理画面）、`/practices`（カレンダー）、`/practices/:id`（練習詳細）、`/practices/participation`（出欠登録）
>
> **主要実装:**
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeScraper.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeImportService.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeWriteService.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeSyncService.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/DensukeSyncScheduler.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukePageCreateService.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeScheduleWriteService.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeDeletionDetectionService.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeDeletionCandidateService.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeDeletionGuard.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/DensukeDeletionCandidateController.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeParticipantService.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/WaitlistPromotionService.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeSessionService.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryService.java`

## 機能仕様

### 概要

伝助はかるた会の出欠調整に広く使われている外部Webサービス。本アプリと伝助の双方向同期を実現しており、アプリ側で登録・変更した出欠情報を自動的に伝助へ書き戻す。

### 仕組み

1. **URL登録**: ADMIN以上が団体×月ごとに伝助のURLを登録（`densuke_urls` テーブルに `year`, `month`, `organization_id`, `url` を保存）。ADMINは自団体のURLのみ操作可能
2. **スクレイピング**: Jsoupで伝助のHTML（`table.listtbl`）をパースし、日付・試合番号・参加者を抽出
3. **データ取り込み**:
   - 練習セッションが存在しない日付は自動作成
     - 伝助の会場名がアプリの `venues.name` と一致する場合、新規セッションの `totalMatches` に `venue.defaultMatchCount`、`capacity` に `venue.capacity` を採用する
     - 会場名がマッチしない場合は `totalMatches` を伝助スケジュールの最大試合番号（なければ既定 3）にフォールバックし、`capacity` は null とする
   - 既存セッションへの補完: `venueId` が未設定で会場名マッチ時に `venueId` と `capacity` を同時補完。`venueId` 既設定でも `capacity` のみ null の場合は Venue から逆引きして補完する（管理者が設定済みの `capacity` / `totalMatches` / `venueId` は上書きしない）
   - 参加者名をアプリの選手名と突合し、一致すれば参加登録
   - 伝助から消えた参加者は `dirty=false`（伝助側から追加された）場合のみDBから削除。`dirty=true`（アプリ側で操作済み）の場合はスキップ
   - 未登録の名前は `unmatchedNames` としてレスポンスに含め、一括登録→再同期のフローを提供
   - 未登録者が検出された場合、管理者にアプリ内通知（`DENSUKE_UNMATCHED_NAMES`）を送信。ADMINは自団体の未登録者のみ、SUPER_ADMINは全団体分を通知
4. **操作者記録**: `importFromDensuke` は `createdBy` パラメータで操作者を記録。スケジューラー実行時は `SYSTEM_USER_ID=0L` を使用

### 自動同期

`DensukeSyncScheduler` が `DensukeSyncService.syncAll()` を呼び出してバックグラウンドで動作:
- **間隔**: 5分ごと（初回は30秒後に開始）
- **対象**: 当月 + 翌月の全団体のURL（団体ごとにループ処理）
- **処理順序**: ① 残存 `dirty=true` 参加者の書き込み（フォールバック）→ ② 伝助→アプリへの読み取り
  - 書き込みを先に行うことで、アプリ側の変更が伝助に反映された後に読み取りが実行される

### イベント駆動書き込み

アプリ側で参加者の状態が変更された際、`DensukeSyncService.triggerWriteAsync()` を非同期で呼び出し、伝助へ即時書き込みを行う。5分スケジューラーの書き込みはフォールバックとして機能し、即時書き込みが失敗した場合のリカバリを担う。

**トリガー箇所:**
- `PracticeParticipantService`: 参加者追加（`addParticipantToMatch`）、参加者削除（`removeParticipantFromMatch`）、抽選結果確定（`setMatchParticipants`）、参加登録（`registerParticipations`）
- `PracticeSessionService`: セッション更新（`updateSession`）
- `WaitlistPromotionService`: 繰り上げオファー応答（`respondToOffer`）、キャンセル待ち辞退（`declineWaitlistBySession`）

### アプリ→伝助への書き込み（DensukeWriteService）

アプリ側で操作した出欠情報を伝助へ反映する機能。`dirty` フラグで変更を追跡する。

**dirty フラグ:**
- `practice_participants.dirty = true`: アプリ側で操作された（書き込み対象）
- `practice_participants.dirty = false`: 伝助から取り込まれた（書き込みスキップ）
- 新規参加登録・ステータス変更（`PracticeParticipantService`, `WaitlistPromotionService`, `LotteryService`）で `dirty=true` を設定
- 伝助からの取り込み時（`DensukeImportService`）は `dirty=false` で保存

**書き込み処理フロー（通常同期）:**
1. `dirty=true` かつ `matchNumber IS NOT NULL` の参加者を取得（`findDirtyForDensukeSync`。BYEエントリを除外）
2. プレイヤー×URLでグループ化
3. 各グループに対して：
   a. `densuke_member_id` を取得（未キャッシュの場合は伝助に `POST insert` でメンバー登録し、`densuke_member_mappings` に保存）
   b. `join-{id}` フィールドIDを取得（未キャッシュの場合は伝助の編集フォームをパースし `densuke_row_ids` に保存）
   c. dirtyな参加者が存在するセッション×試合のみをformDataに含める（未入力保護: アプリに未登録のマスやdirtyでないマスは送信しない）
   d. ステータスに応じた値（3=○/2=△/1=×）を決定して `POST regist` で送信
   e. 成功したら送信対象のみ `dirty=false` に更新、失敗はエラーリストに記録
4. 書き込み状況（最終実行日時・最終成功日時・エラー・書き込み待ち件数）を `DensukeWriteStatusDto` で保持

**書き込み処理フロー（抽選確定同期）:**
- `writeAllForLotteryConfirmation` で全マッピング済みプレイヤーを対象に書き込む（dirtyフィルタなし）
- アクティブステータス（WON/WAITLISTED/OFFERED/PENDING）のみ書き戻し、CANCELLED/DECLINED/未登録はスキップ（伝助の既存値を維持）

### 伝助ページ自動作成（DensukePageCreateService）

アプリ側に登録された練習日データから densuke.biz に出欠ページを**新規発行**する機能。ADMIN 以上が伝助管理画面の「伝助ページ作成」ボタンから手動実行する。

**ユースケース:**
- 月初に管理者が翌月の練習日を一通り登録し終えたタイミングで、メンバー周知用の伝助ページを 1 クリックで作成
- 従来は伝助側に手動でページを作成し、日付・会場・試合枠を手入力していたが、アプリのマスタデータ（`practice_sessions` / `venues` / `venue_match_schedules`）をそのまま流用して自動化する

**使い方:**
1. `/admin/densuke` で対象年月を選択（当月＋未来2ヶ月まで）
2. 団体カードの「伝助ページ作成」ボタン → 作成モーダル表示
3. テンプレートから読み込んだタイトル・説明・連絡先メアドを必要に応じて編集
4. 「作成」ボタンで POST `/api/practice-sessions/densuke/create-page` 実行

**処理フロー:**
1. バリデーション: 既存URL重複 / 練習日 0 件 / 会場未登録 / `venue_match_schedules` の不足をチェック、いずれかあれば `IllegalStateException`
2. テンプレート (`densuke_templates`) 取得 + オーバーライド適用 + プレースホルダー置換 (`{year}` / `{month}` / `{organization_name}`)
3. schedule 文字列組み立て: 各セッション（日付×会場）の先頭行は `{M}/{D}({曜}) {会場名} 1試合目`、2 試合目以降は `{N}試合目` 単独で改行連結（時刻は含めない）
4. 年月範囲チェック: JST 基準で当月〜+2 ヶ月以外は `IllegalStateException`（UI の `canCreatePage` と同等の制約を API 側にも適用）
5. `densuke_urls` に仮レコードを `saveAndFlush` で先行確保（UNIQUE 制約による同時作成の直列化）。ユニーク違反時は「既に登録されています」で 400 を返し、densuke.biz への二重 POST を防止
6. densuke.biz に `POST https://www.densuke.biz/create` を送信（UTF-8, `application/x-www-form-urlencoded`, `eventchoice=1` 固定）
7. 302 レスポンスの `Location` ヘッダーから `cd` と `sd` を正規表現で抽出
8. 仮レコードを実 URL / `sd` で更新
9. トランザクションコミット後に LINE 通知発火

**通知:**
- 団体所属 PLAYER ロールのメンバー全員に LINE 通知（`DENSUKE_PAGE_CREATED` 種別）
- ADMIN / SUPER_ADMIN は通知対象外（作成者なので不要）
- タイトル: `{month}月の練習日程が出ました`、本文には伝助 URL を含める
- `line_notification_preferences.densuke_page_created` が `FALSE` のメンバーはスキップ
- 通知送信失敗は作成 API の成功に影響させない（警告ログのみ）

**テンプレート管理:**
- `GET / PUT /api/densuke-templates/{organizationId}`（ADMIN以上）
- 団体ごとに 1 レコード。未登録団体にはデフォルト値（タイトル = `{year}年{month}月 練習出欠`）を返却
- 伝助管理画面の「テンプレート編集」ボタンからモーダルで編集

**作り直し（再作成）:**
- ADMIN 以上が伝助管理画面の「作り直す」ボタンから実行（既存の URL が登録済みかつ当月〜+2ヶ月の範囲で表示）
- `DELETE /api/practice-sessions/densuke-url?year=&month=&organizationId=` で `densuke_urls` 行を物理削除 → 同月同団体の作成ロックが外れる → 続けて「伝助ページ作成」モーダルが自動オープン
- **densuke.biz 側の旧ページは削除しない**（匿名作成のため削除エンドポイント未知）。アプリ側から参照されなくなるだけで、旧 URL にアクセスすれば旧ページ自体は閲覧可能
- 自動作成レコード（`densuke_sd` あり）/ 手動登録レコード（`densuke_sd` NULL）どちらも同じ扱いで削除可能

**設計上の固定値:**
- `eventchoice = 1`（○△×）— 既存 `DensukeScraper` の判定ロジック（`col3`=○, `col2`=△）と整合
- `pw = 0`（パスワードなし）— 運用上パスワード不要

**セキュリティ / 副作用:**
- densuke.biz への POST は認証不要（匿名作成）
- `densuke_sd`（編集用シークレット）を保存するが、将来の編集・削除 API 実装時に使用予定
- 作成後は既存の `DensukeSyncScheduler` が次回サイクル（最長 5 分）で新 URL を自動取り込みし、以降の双方向同期へ合流

**未入力保護:**
- 伝助上で「未入力」のまま残しているマスをアプリの同期が×に上書きしないよう保護する
- 通常同期ではdirty行のみ送信し、アプリに未登録のマスは送信しない
- BYE（matchNumber=null）エントリは伝助の行に対応しないため、常に `dirty=false` で管理し同期対象から除外する
- `softDeleteByPlayerIdAndSessionIds` は `matchNumber IS NOT NULL` 条件でBYEを除外する
- `updateSession` の削除ループでもBYE（matchNumber=null）を除外する

**ステータスマッピング:**
| ParticipantStatus | 伝助値 | 表示 |
|---|---|---|
| PENDING / WON | 3 | ○（参加） |
| WAITLISTED / OFFERED | 2 | △（未定） |
| CANCELLED / DECLINED / WAITLIST_DECLINED | 1 | ×（不参加） |
| （未登録） | 送信しない | — |

**キャッシュテーブル:**
- `densuke_member_mappings`: プレイヤー×URLごとの伝助メンバーID（`mi` パラメータ）をキャッシュ
- `densuke_row_ids`: URL×日付×試合番号ごとの `join-{id}` フィールドIDをキャッシュ

#### アプリ→伝助 練習日同期（DensukeScheduleWriteService）

アプリで新規練習日を追加した際に、対応する伝助ページの候補日程欄へ自動で末尾追記する機能。
既存日程・既存参加者データ・既存 `densuke_row_ids` のインデックスは破壊されない（伝助の
`POST /update` は末尾追記であることをスパイク調査で実証済み — `docs/features/densuke-schedule-write-sync/spike-findings.md` 参照）。
削除は対象外（手動運用）。

**同期トリガー（2 系統）:**
1. **イベント駆動（即時 push）**: `PracticeSessionService.createSession` の `afterCommit` フックで
   `DensukeScheduleWriteService.pushNewSchedulesToDensukeAsync(year, month, organizationId)` を
   `@Async` で fire-and-forget 実行。即時 UX 向上のため、追加直後に伝助ページが更新される
2. **スケジューラ（フォロー同期）**: 5 分スケジューラ `DensukeSyncService.syncAll()` の最初のステップで
   `pushAllForCurrentAndNextMonth()` を実行。即時 push が失敗していた場合の自動回復を担う

**push 対象判定:**
- 対象 (year, month, organizationId) の `densuke_urls` レコードがなければ early return（伝助ページ未作成のケース）
- 行ロック (`@Lock(PESSIMISTIC_WRITE)` の `findByYearAndMonthAndOrganizationIdForUpdate`) で取得し、並行 push の差分計算ズレを防ぐ
- `DensukeImportService.findOrCreateSession` 経由のセッション作成では発火しない
  （無限ループ防止 — `findOrCreateSession` は `practiceSessionRepository.save` を直接呼び `PracticeSessionService.createSession` を通らないため、afterCommit フックが入らない）
- **過去日制約**: 伝助 `POST /update` は候補日程を**末尾追記**しかできず、伝助の既存最大日付より前の新規日付を
  push すると、伝助 DOM 出現順とアプリ側日付昇順がずれて `DensukeWriteService.parseAndSaveRowIds` の row id
  対応が誤り、参加者出欠が別日に書き込まれるデータ破壊リスクがある。よって**伝助の既存最大日付以前の新規セッション
  は push せず、即時 push 経路では管理者へ LINE 通知**して伝助ページの管理画面から手動追加を促す。
  スケジューラ経路はフラッディング防止で通知抑制（WARN ログのみ）。

**差分計算:**
- `DensukeScraper.scrape(url, year)` で伝助の現スケジュールを取得し、日付集合を得る
- アプリの `practice_sessions` のうち伝助に存在しない日付のセッションのみを抽出
- 差分が無ければ POST せず early return
- **伝助側で全行削除された日付の誤 push 防止**: 伝助スクレイピング結果に存在しない日付でも、
  (a) `densuke_row_ids` に当該日付宛ての書き込み実績キャッシュがある、または (b) 当該日付に
  `DensukeDeletionCandidate`（PENDING/APPROVED）が既に存在する場合は「新規」から除外し push しない。
  除外しないと、日付単位で全行削除された場合に `DensukeDeletionDetectionService` が検知する前に
  本機能が日程を再作成してしまい、「検知して承認するまでデータを変更しない」という削除検知フローの
  前提を壊す（後述の「伝助側削除検知・承認（DensukeDeletionCandidate）」参照）。ただし、Densuke へ一度も書き込み実績が無い（`densuke_row_ids` 未生成）
  かつ検知未実行の新規セッションが直後に全行削除された極めて狭いタイミングは対象外（既知の限定事項）

**スケジュール文字列:**
- `DensukePageCreateService.buildScheduleText(newSessions, venueMap, scheduleMap)` を再利用（フォーマット一貫性確保）
- 新規セッションが会場未設定・`venue_match_schedules` 不足の場合は `IllegalStateException` を捕捉して失敗通知に変換

**HTTP 呼び出し:**
- GET `/list?cd=...` で Cookie と `pageId`（`<input name="id">` の value）を取得（`DensukeWriteService.extractPageId` を package-private で共用）
- POST `/update` (`cd`, `id`, `postfix=""`, `schedule`) を `application/x-www-form-urlencoded` で送信
- 期待レスポンスは HTTP 302 Found（`Location` は `edit?cd=...&pw=...`）。302 以外は失敗扱い

**失敗時の挙動:**
- 即時 push 経路（`pushNewSchedulesToDensukeAsync` → `pushNewSchedulesToDensuke`）の失敗:
  - HTTP 4xx/5xx・IOException・`pageId` 未検出・`buildScheduleText` の `IllegalStateException` を捕捉
  - `LineNotificationService.sendDensukeScheduleSyncFailedNotification(organizationId, errorMessage)` で
    団体の ADMIN / SUPER_ADMIN へ LINE 通知（`ADMIN_DENSUKE_PUSH_FAILED`）
  - 通知設定 ON/OFF は持たない（管理者向け重要通知のため常時送信）
- スケジューラ経路（`pushSilently`）の失敗:
  - WARN ログのみで管理者通知は発火しない（フラッディング防止）

**スケジューラ実行順序（`DensukeSyncService.syncAll`）:**
1. **スケジュール push** （`pushAllForCurrentAndNextMonth`）— 本機能
2. 参加者書き込み（`writeToDensuke`、既存）
3. 伝助→アプリ取り込み（`syncForMonth` × 当月・翌月、既存）

**DB マイグレーション:**
- 既存テーブル `densuke_urls` / `practice_sessions` / `venues` / `venue_match_schedules` への変更は不要
- ただし新 enum 値 `ADMIN_DENSUKE_PUSH_FAILED` は `line_message_log_notification_type_check` の CHECK 制約に追加する必要がある（`database/add_admin_densuke_push_failed_message_log_check.sql` を本番 DB に適用）。enum 名は VARCHAR(30) 内に収まる短縮形で命名しているため、カラム長拡張は不要

### 伝助側削除検知・承認（DensukeDeletionCandidate）

伝助側で試合の行（日付×試合番号）が削除された場合に自動検知し、管理者が明示的に承認するまでは
アプリ側のデータを一切変更しない機能。伝助側の行削除により `DensukeWriteService` の行数不一致
チェック（前述の「アプリ→伝助への書き込み（DensukeWriteService）」参照）が解消せず `ADMIN_DENSUKE_ROWID_ISSUE` 通知が繰り返し送信され続ける問題への対応。

**ユースケース:**
- 管理者が誤って（または意図的に）伝助側で試合行を削除した際、どの試合（日付・試合番号）が
  削除されたのかをアプリ側で特定し、承認操作 1 つで対応する出欠エントリを削除できるようにする
- 承認するまでは検知のみ行い、選手側にも「伝助側で削除されました」と可視化することで、
  管理者が確認する前に選手が混乱しないようにする

**検知処理（`DensukeDeletionDetectionService`）:**
- `DensukeImportService.importFromDensuke` が当月・翌月の同期サイクルで既に取得済みのスクレイピング
  結果を再利用し、既存の参加者同期ロジックとは独立した追加チェックとして実行する（既存ロジックへの影響を避ける）
- 対象団体・対象月の `PracticeSession.totalMatches` から機械的に導かれる期待値（1〜totalMatches）と、
  伝助スクレイピング結果に実在する (日付, 試合番号) を突き合わせ、期待値にあって実在しない組を
  新規の削除候補（`densuke_deletion_candidates`, status=PENDING）として記録する
- 承認済み（APPROVED）の組は「欠番」として再検知の対象から除外する（同じ組を再度 PENDING にしない）
- 未承認（PENDING）の組が伝助側で復活した場合は、削除候補を自動的に解消する（再オープンはしない単純化方針）

**承認・却下（`DensukeDeletionCandidateService`）:**
- 承認（`approve`）: 該当 (session_date, matchNumber) の `PracticeParticipant`（出欠エントリ）のみを削除する。
  `PracticeSession.totalMatches` と既存の対戦結果（`Match` エンティティ）には一切触れない（欠番方式）
- 却下（`reject`）: データは変更せず、削除候補を解消するのみ（選手向け表示も通常表示に戻る）
- ADMIN は自団体の削除候補のみ操作可能。クライアント指定の `organizationId` は信用せず、
  削除候補自身が持つ所属団体を正としてスコープ検証する

**書き込み側の欠番除外（`DensukeWriteService.buildScheduleOrder`）:**
- PENDING・APPROVED いずれの状態の削除候補がある (date, matchNumber) も、伝助への書き込み時の
  スケジュール生成から除外する（REJECTED は除外しない）
- 検知時点（PENDING）から既に除外するため、「伝助フォームの行数」と「アプリの予定数」は検知直後から
  一致し、`ADMIN_DENSUKE_ROWID_ISSUE` の行数不一致（本機能が解決したい元の5分ごとの繰り返し通知）
  はそもそも発生しなくなる。個別の重複抑制ロジックは不要
- 却下（REJECTED）した場合は除外対象から外れる。データも行数不一致も変わらないため、
  `ADMIN_DENSUKE_ROWID_ISSUE` は通常どおり報告され続ける（未解決の問題として管理者に見える状態を維持）
- `DensukeWriteService.buildRowIdsByKey` も同じ除外集合を使い、PENDING・APPROVED な
  (date, matchNumber) の `densuke_row_ids` キャッシュはプリフェッチしない。除外しないと、
  削除された行に紐づく stale な row_id が dirty な参加者の書き込みに使われ、伝助に実際は
  反映されていないのに `dirty=false`（同期済み扱い）になってしまうデータ不整合が起きる

**通知:**
- 新規検知時のみ、団体の ADMIN / SUPER_ADMIN へ LINE 通知（`ADMIN_DENSUKE_DELETE_DETECTED`）
- 同一の削除候補について、承認/却下されるまで再通知はしない（初回検知時の1回のみ）
- 通知設定 ON/OFF は持たない（管理者向け重要通知のため常時送信）

**選手向け可視化:**
- `PracticeSessionDto.densukeDeletionCandidateMatchNumbers`（PENDING・APPROVED の両方）を、
  練習日サマリー（カレンダー）・詳細取得の両方に付与する。承認後も `totalMatches` は変更しない
  欠番方式のため、APPROVED も表示対象から外さない（外すと通常の空き枠に見えてしまう）
- カレンダー画面（`/practices`）: 試合状況グリッドの該当試合番号を灰色×で表示
- 練習詳細（`/practices/:id`）・出欠登録（`/practices/participation`）: 該当試合番号に
  「伝助で削除されました」バッジ/表示を出し、チェックボックス操作を無効化する
- 却下時は通常表示に戻る（データは変更されないため）
- バックエンド側にも二重ガードを設ける: APPROVED な (団体, 練習日, 試合番号) への新規参加登録・
  再有効化はフロントの表示に関わらず拒否する。承認済み欠番が通常枠として再登録され、伝助書き込み時の
  行数不一致を再発させることを防ぐ。判定ロジックは共有コンポーネント `DensukeDeletionGuard` に切り出し、
  以下の選手が直接触れる経路に適用（単一対象は例外、バッチ処理は当該試合番号のみスキップして他は継続）:
  - `PracticeParticipantService.setMatchParticipants` / `addParticipantToMatch`（例外）/
    `registerParticipations`（バッチ、該当キーのみ拒否）
  - `WaitlistPromotionService.handleSameDayJoin`（例外）/ `handleSameDayJoinAll`（バッチ、スキップ）/
    `rejoinWaitlistBySession`（バッチ、スキップ）
  - **既知の限定事項**: 管理者専用の編集経路（`LotteryService.editParticipants` の参加者追加・
    ステータス変更、`LotteryService.reExecuteLottery` の再抽選、`PracticeSessionService.createSession` /
    `updateSession` の参加者一括登録）には本ガードを適用していない。管理者が承認済み欠番と知った上で
    あえて操作するケースは残るため、必要に応じて別Issueで対応する
  - `DensukeImportService` のフェーズ処理（伝助→アプリ取り込み）は対象外で問題ない。承認済み欠番は
    伝助側に行自体が存在しないため、スクレイピング結果に該当 (date, matchNumber) のエントリが
    現れず、取り込みロジックが実行される前提が成立しない

**API:**
| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `/api/densuke-deletion-candidates?organizationId=` | ADMIN+ | 団体別の未承認削除候補一覧取得 |
| POST | `/api/densuke-deletion-candidates/{id}/approve` | ADMIN+ | 削除候補を承認（出欠エントリ削除） |
| POST | `/api/densuke-deletion-candidates/{id}/reject` | ADMIN+ | 削除候補を却下（データ変更なし） |

**DB マイグレーション:**
- 新規テーブル `densuke_deletion_candidates`（`database/create_densuke_deletion_candidates.sql`）
- 新 enum 値 `ADMIN_DENSUKE_DELETE_DETECTED` を `line_message_log_notification_type_check`
  の CHECK 制約に追加（`database/add_admin_densuke_delete_detected_message_log_check.sql`）

### スクレイピング詳細

伝助のHTMLテーブル構造:
- **ヘッダー行（1行目）**: 列4以降が参加者名
- **データ行（2行目以降）**: 列0がラベル（日付・会場・試合番号を含む）、列4以降が各参加者の出欠
  - CSSクラス `col3` = 参加（○）
  - CSSクラス `col2` + テキスト `△` = 未定
- **日付パターン**: `3/14(金)` 形式
- **試合番号パターン**: `1試合目` 形式
- **会場名パターン**: 日付と試合番号の間のテキスト

### 参加者名の正規化（`DensukeScraper.normalizeMemberName()`）

伝助からスクレイピング／インポートする参加者名を、既存DB選手名との照合精度を高め重複登録を防ぐために正規化する。照合・自動登録・書き戻しがこの単一関数を共有するため、全経路へ対称的に適用される。名前全体に次の3段階を適用する。

1. **不可視文字の除去（名前全体）**: Unicode の `FORMAT`(Cf) カテゴリ全般（双方向制御 LRM/RLM/LRE/PDF 等、ゼロ幅スペース・接合子 ZWSP/ZWNJ/ZWJ、BOM、Word Joiner、Soft Hyphen 等）とバリエーションセレクター（U+FE00–U+FE0F / U+E0100–U+E01EF）を、先頭・途中・末尾を問わず除去する。伝助からのペーストで U+202A 等が混入した重複登録（Issue #671）を救済する。
2. **空白文字の除去（名前全体）**: あらゆる空白文字（半角 U+0020 / 全角 U+3000 / NBSP U+00A0 / タブ U+0009 等。`Character.isWhitespace` または `Character.isSpaceChar` に該当する全コードポイント）を、**先頭・途中・末尾すべての位置**から除去する。例: `星野　和夏`（途中に全角空白）→ `星野和夏`。名前途中の空白による別人登録（本番 #159）を防ぐ。空白の有無のみで区別される別人は存在しない前提。
3. **先頭絵文字の除去**: 上記除去後、名前の先頭に残る絵文字（Unicode の `OTHER_SYMBOL`・`MATH_SYMBOL`・`MODIFIER_SYMBOL` カテゴリ）を除去する。例: `🔰田中` → `田中`、`🌟鈴木` → `鈴木`。

- 照合時はDB選手名・伝助名の**双方**を正規化して突合するため、絵文字・不可視文字・空白の有無による表記ゆれは吸収される。未登録名の自動登録・重複チェック（`findByNameAndActive`）も正規化後の名前で行う。
- `null` / 空文字はそのまま返す（`null` → `null`、`""` → `""`）。
- 既存DBに絵文字・空白付き等で登録されているプレイヤーのレコード自体は変更されない（照合時に正規化して突合するため再登録は不要）。

### メンバー最終変更時刻の取得と drift ログ（Issue #543 / #544 / #545）

**目的（observability only）:**
伝助上で各メンバーが最後に出欠を変更した時刻と、アプリ側がその変更を検出した時刻の乖離（drift）を可視化し、同期遅延・スケジューラ間隔の影響・不審な大幅遅延ケースを事後解析できるようにする。**DB / API / UI には変更なし**。ログのみで提供する。

**メンバー title 属性のパース:**
- 伝助ヘッダの各メンバーリンク `<a title="M/d HH:mm">` 属性を `DensukeScraper` がパースし、`DensukeData.memberLastChangeTimes`（`Map<String, LocalDateTime>`）に格納する
- 年は scrape 時の指定年（呼び出し元 `DensukeImportService` が渡す）を採用。年跨ぎは現スコープ外
- `title` が `null` / 空文字 / フォーマット不一致 / 不正日付（例: `2/30`, `13/1`, `25:00`）の場合は map に entry を持たない（黙ってスキップ）

**drift ログ形式:**
`DensukeImportService` の Phase1（DB差分検出）/ Phase3（伝助→アプリ同期）の状態遷移ログ末尾に、以下の形式で drift 情報を付与する:
- 取得成功時: `densukeTitle=2026-04-23T12:45 detectedAt=2026-04-23T12:50:56 drift=5m`
- 取得失敗時: `densukeTitle=(unknown) detectedAt=2026-04-23T12:50:56 drift=(unknown)`

`detectedAt` はインポート 1 回分で固定の値（インポート開始時刻、秒単位に丸め）。`drift` は分単位（負値もあり得る — title 時刻 > 検出時刻となるエッジケース）。

**10分超 WARN:**
drift が **10分（`DensukeImportService.DRIFT_WARN_THRESHOLD_MINUTES`）** を超えた場合、状態遷移ログに加えて WARN レベルでサマリーを出力する。これにより監視ツールでの絞り込み・アラート化を可能にする。

形式:
```
WARN Densuke change-time drift detected: phase=Phase3-C2 session=934 match=1 player=20 (山田太郎) densukeTitle=2026-04-22T22:06 detectedAt=2026-04-23T12:50:56 driftMinutes=884
```

`title` 未取得（map に entry 無し）の場合は WARN を抑制する — title が空のメンバーで大量 WARN が出るのを防ぐため。

**スコープ外:**
- DB スキーマ変更（drift 履歴の永続化はしない）
- API レスポンス追加（フロントへ drift 情報を返さない）
- UI 表示（管理画面に drift 列を追加しない）
- アラート通知（ログから外部監視で拾う運用）

## API

### 伝助連携 (`/api/practice-sessions`)

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| POST | `/register-and-sync-densuke` | ADMIN+ | 未登録者一括登録+再同期（`organizationId` 指定） |
| GET | `/densuke-url?year=&month=&organizationId=` | PLAYER+ | 団体別伝助URL取得（認証必須） |
| PUT | `/densuke-url` | ADMIN+ | 伝助URL登録・更新（`organizationId` 必須、ADMINは自団体のみ、`https://densuke.biz/` ドメインのみ受付） |
| POST | `/sync-densuke` | ADMIN+ | 団体×年月指定で伝助同期（`organizationId` 必須、ADMINは自団体のみ、書き込み→読み取りの順に実行） |
| GET | `/densuke-write-status?organizationId=` | ADMIN+ | 団体別の書き込み状況取得（最終実行日時・最終成功日時・エラー・書き込み待ち件数） |

### 伝助削除候補 (`/api/densuke-deletion-candidates`)

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `?organizationId=` | ADMIN+ | 団体別の未承認削除候補一覧取得（ADMINは自団体のみ） |
| POST | `/{id}/approve` | ADMIN+ | 削除候補を承認（該当出欠エントリを削除。候補自身の所属団体でスコープ検証） |
| POST | `/{id}/reject` | ADMIN+ | 削除候補を却下（データ変更なし） |
