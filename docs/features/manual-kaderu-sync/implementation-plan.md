---
status: completed
---
# Kaderu予約取り込み手動トリガー 実装手順書

## 実装タスク

### タスク1: DB マイグレーション + Entity / Repository / enum 追加
- [x] 完了
- **概要:** `kaderu_sync_trigger_events` テーブルを作成し、Entity / Repository / LINE通知 enum を追加する
- **変更対象ファイル:**
  - `database/create_kaderu_sync_trigger_events.sql` （新規）
    - `kaderu_sync_trigger_events` テーブル定義（要件書 4.2 参照）+ 2インデックス
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/KaderuSyncTriggerEvent.java` （新規）
    - `@Entity` `@Table(name = "kaderu_sync_trigger_events")`
    - enum `SyncStatus { PENDING, COMPLETED, FAILED }`
    - フィールド: id, organization (ManyToOne), triggeredByPlayerId, triggeredAt, status, githubRunId, completedAt, summary, failureReason, createdAt, updatedAt
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/KaderuSyncTriggerEventRepository.java` （新規）
    - `findFirstByOrganizationIdAndStatusOrderByTriggeredAtDesc(Long, SyncStatus)`
    - `findAllByStatus(SyncStatus)`
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/LineMessageLog.java`
    - enum `LineNotificationType` に `KADERU_SYNC_COMPLETED` / `KADERU_SYNC_FAILED` を追加
- **本番DB適用:** PR作成時にユーザーへ依頼（CLAUDE.md「DBマイグレーション適用ルール」遵守）
- **依存タスク:** なし
- **対応Issue:** #803

### タスク2: GitHub Actions Client + Workflow 新設
- [x] 完了
- **概要:** GitHub Actions API を叩く Spring 用クライアントと、手動同期専用 workflow を追加する
- **変更対象ファイル:**
  - `.github/workflows/sync-kaderu-reservations-manual.yml` （新規）
    - `workflow_dispatch` with `inputs.org` (choice: hokudai/wasura)
    - hokudai/wasura のステップを `if: inputs.org == '...'` で振り分け
    - `concurrency: kaderu-reservation-sync` を継承
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/GitHubActionsClient.java` （新規）
    - Spring `RestClient` ベース
    - `dispatchWorkflow(String workflowFileName, String ref, Map<String,Object> inputs)`: `POST /repos/{owner}/{repo}/actions/workflows/{file}/dispatches`
    - `listRecentRuns(String workflowFileName, Instant createdAfter)`: dispatch直後の run_id 解決用
    - `getWorkflowRun(long runId)`: status / conclusion を取得
    - `fetchWorkflowLogText(long runId)`: ログ取得（summary 抽出用、失敗時はnullを返す）
    - 環境変数 `GITHUB_PAT` を `Authorization: Bearer <token>` に付与。未設定時はメソッド呼び出し時に例外
    - 環境変数 `GITHUB_REPO` でリポジトリ指定（デフォルト `poponta2020/match-tracker`）
- **依存タスク:** なし（タスク1と並列可）
- **対応Issue:** #804

### タスク3: バックエンド Service + Controller + DTO
- [x] 完了
- **概要:** トリガー API・ステータス API のサービスとコントローラを実装する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/KaderuSyncTriggerRequest.java` （新規）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/KaderuSyncTriggerEventDto.java` （新規）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/KaderuSyncStatusResponse.java` （新規）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/KaderuSyncTriggerService.java` （新規）
    - `triggerSync(playerId, organizationId)`:
      1. PENDING の重複チェック（あれば `ConflictException` 投げる）
      2. `organizations.code` を取得
      3. `GitHubActionsClient.dispatchWorkflow("sync-kaderu-reservations-manual.yml", "main", Map.of("org", code))`
      4. `listRecentRuns` で `triggered_at` 以降の run_id を取得（取れなければ null で保存）
      5. `KaderuSyncTriggerEvent` を PENDING で保存
    - `getStatus(organizationId)`: PENDING を返す（経過秒数を計算してDTOに詰める）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/KaderuSyncTriggerController.java` （新規）
    - `POST /api/kaderu-sync/trigger` — `@RequireRole({"ADMIN", "SUPER_ADMIN"})`
      - リクエストから `organizationId` 取得 → `OrganizationScopeResolver` で実効 organizationId 解決
      - `KaderuSyncTriggerService.triggerSync` 呼び出し
      - 201 で DTO 返却
    - `GET /api/kaderu-sync/status` — 同認可
      - クエリ `organizationId` から実効 ID 解決 → サービス呼び出し
- **依存タスク:** タスク1 (#803), タスク2 (#804)
- **対応Issue:** #805

### タスク4: ポーリング Scheduler + LINE通知メソッド追加
- [x] 完了
- **概要:** 30秒間隔で PENDING を巡回し、workflow 完了を検知してイベント確定 + LINE通知を行う
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/KaderuSyncStatusPollingScheduler.java` （新規）
    - `@Scheduled(fixedDelay = 30_000)` 毎に PENDING を巡回
    - 各イベントについて:
      1. `githubRunId` が null なら `listRecentRuns` で補完
      2. `getWorkflowRun` で status / conclusion を取得
      3. `completed && success` → `COMPLETED` + `sendKaderuSyncCompletedNotification`
      4. `completed && (failure|cancelled|timed_out)` → `FAILED` + `sendKaderuSyncFailedNotification`
      5. `triggered_at + 30分` 超過の PENDING → `FAILED` 確定（fail-safe）
    - summary 抽出: `fetchWorkflowLogText` の結果を `新規作成: X件 / 会場拡張: X件 / スキップ: X件` の正規表現でパース。失敗時は null
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java`
    - `sendKaderuSyncCompletedNotification(Long playerId, String orgCode, String summary)` を追加
    - `sendKaderuSyncFailedNotification(Long playerId, String orgCode, String failureReason)` を追加
    - 既存の `sendDensukePageCreatedNotification` と同じ構造で実装（preference チェックは行わない）
- **依存タスク:** タスク1 (#803), タスク2 (#804), タスク3 (#805)
- **対応Issue:** #806

### タスク5: フロントエンド（API client + PracticeList ボタン）
- [x] 完了
- **概要:** API クライアントと PracticeList のボタン・ポーリングを実装する
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/api/kaderuSync.js` （新規）
    - `trigger(organizationId)` / `getStatus(organizationId)` の2メソッド
  - `karuta-tracker-ui/src/api/index.js`
    - `kaderuSyncAPI` を re-export
  - `karuta-tracker-ui/src/pages/practice/PracticeList.jsx`
    - import: `kaderuSyncAPI`, `isAdmin`, `isSuperAdmin`（既にimport済み）, トースト
    - 状態追加: `kaderuSyncPending`, `kaderuSyncElapsedSec`
    - useEffect: 30秒間隔で status をポーリング（ADMIN+ かつ画面表示中のみ）
    - useEffect: PENDING 中、1秒間隔で elapsed を増分
    - ナビバーの月送り右矢印の右側にボタンを配置（ADMIN+ のみ）
    - ボタン押下: `trigger` 呼び出し → 成功時トースト表示、ポーリング即時実行、失敗時にエラートースト
- **依存タスク:** タスク3 (#805)
- **対応Issue:** #807

### タスク6: GitHub PAT 発行 + Render 環境変数登録（運用作業）
- [ ] 完了
- **概要:** PRマージ前にユーザー側で実施する運用作業。PR本文で明示する
- **作業内容:**
  - GitHub fine-grained PAT を発行（要件書 4.6 参照）
    - Name: `match-tracker-kaderu-sync`
    - Expiration: 90 days
    - Repository: `poponta2020/match-tracker` 限定
    - Permission: Actions = Read and write
  - Render dashboard で `karuta-tracker` サービスの Environment に追加:
    - Key: `GITHUB_PAT`
    - Value: 発行したトークン文字列
  - 必要に応じて `GITHUB_REPO=poponta2020/match-tracker` も追加（デフォルト値と同じなら省略可）
- **依存タスク:** タスク2 (#804)（workflow が存在することが前提）
- **対応Issue:** #808

### タスク7: SPECIFICATION.md / DESIGN.md / SCREEN_LIST.md 更新
- [ ] 完了
- **概要:** ドキュメントを最新化する（CLAUDE.md「ドキュメント更新ルール」遵守）
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md`
    - 4.4節（かでる予約同期）に「4.4.7 手動トリガー」を追加 — ボタンの場所・LINE通知・PAT運用の概要
  - `docs/DESIGN.md`
    - 7.8節（Kaderu予約 → 練習日自動登録フロー）の下に「7.8.1 手動トリガーフロー」を追加 — シーケンス図/疑似コードでフロント→Backend→GitHub→Scheduler→LINEの流れを記述
  - `docs/SCREEN_LIST.md`
    - 練習日一覧画面の操作項目に「Kaderuから取り込み」ボタン（ADMIN+）を追加
- **依存タスク:** タスク1〜5 (#803〜#807)
- **対応Issue:** #809

### タスク8: 動作確認
- [ ] 完了
- **概要:** PRマージ後、エンドツーエンドで動作確認する
- **確認内容:**
  - **準備:** GitHub PAT を Render に登録済みであること（タスク6完了）
  - **正常系:**
    - ADMIN ユーザーで `/practice` を開き、ボタンが見えることを確認
    - ボタン押下 → トースト表示、ボタンが `同期中…` でdisabledになること
    - 経過秒数が1秒ごとに更新されること
    - 数分後、`KADERU_SYNC_COMPLETED` LINE通知が押下者本人に届くこと
    - 画面をリロードして、新しい練習日が表示されること
    - ボタンが再度有効になること
  - **重複防止:**
    - ボタン押下後、即座にもう1回押下しようとすると disabled で押せないこと
    - 別タブで `POST /api/kaderu-sync/trigger` を直接叩くと 409 が返ること
  - **異常系:**
    - GITHUB_PAT を一時的に無効化して workflow を起動 → `KADERU_SYNC_FAILED` 通知が届くこと
    - 30分タイムアウトのfail-safe動作確認（PENDING を手動でDB挿入してテストするか、本番運用時に確認）
- **依存タスク:** タスク1〜7 全て + タスク6 のPAT登録完了
- **対応Issue:** #810

## 実装順序
1. **タスク1**（依存なし）— DB マイグレーション + Entity + enum
2. **タスク2**（依存なし、タスク1と並列可）— GitHub Actions Client + Workflow
3. **タスク3**（タスク1, 2に依存）— Service + Controller + DTO
4. **タスク4**（タスク1, 2, 3に依存）— Scheduler + LINE通知メソッド
5. **タスク5**（タスク3に依存）— フロントエンド
6. **タスク7**（タスク1〜5に依存）— ドキュメント更新
7. **PR作成**（タスク1〜5, 7 を1コミット or 複数コミットでまとめる）
8. **タスク6**（PR上で運用作業を明示）→ ユーザーがPAT発行・Render登録 → PRマージ
9. **タスク8**（マージ後）— 動作確認
