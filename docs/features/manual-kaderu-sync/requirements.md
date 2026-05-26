---
status: completed
---
# Kaderu予約取り込み手動トリガー 要件定義書

## 1. 概要

### 目的
練習日登録画面 (`/practice/new`、ADMIN+ 限定) に「Kaderuから取り込み」ボタンを追加し、ADMIN+ ユーザーが任意のタイミングで Kaderu 予約取り込み workflow を手動起動できるようにする。同期の成功/失敗結果はLINE通知で押下者本人に届け、押下者は通知を受けて画面を手動リロードする運用とする。

### 背景・動機
- 現状、Kaderu 予約は GitHub Actions の cron (`*/30 * * * *`) で自動同期されている
- かでる側で新規予約が成立してから最大30分間は練習日一覧に反映されない
- 管理者が「すぐに反映させたい」局面（例: 直前の枠取り後すぐに伝助連携や参加管理を始めたい）で待ち時間が発生する
- workflow に `workflow_dispatch` は既に設定されているため、アプリ内から GitHub API 経由で同 workflow を起動するUIを設ければ、運用工数を削減できる

## 2. ユーザーストーリー

### 対象ユーザー
- **ADMIN（hokudai / wasura）:** 自団体の Kaderu 予約を即時反映させたい
- **SUPER_ADMIN:** 任意の団体の同期を即時起動できる必要がある
- **PLAYER:** この機能のUIは見えず、影響を受けない

### 利用シナリオ
1. hokudai の ADMIN がかでるサイトで新規予約を入れた直後、match-tracker の `/practice/new` を開く
2. 上部ナビバーに「Kaderuから取り込み」ボタンが見える
3. ボタンを押下する → トースト「Kaderu同期を開始しました。完了後にLINEでお知らせします（5分後を目安に画面をリロードしてください）」
4. ボタンは「同期中…」表示でdisableされる
5. 2〜5分後、押下者本人のLINEに「Kaderu予約取り込みが完了しました（団体: hokudai）。新規:3 拡張:1 スキップ:5」が届く
6. ユーザーが `/practice/new` をリロード → 新しい練習日が表示される
7. 万が一 workflow が失敗した場合は、LINEで「Kaderu予約取り込みに失敗しました」と通知が届き、ボタンは再度押下可能になる

## 3. 機能要件

### 3.1 画面仕様

#### 3.1.1 ボタン表示位置
- `PracticeForm.jsx` の上部固定ナビゲーションバー内
- 月ピッカー（中央）の右側、月送り右矢印より右に配置
- ADMIN / SUPER_ADMIN にのみ表示。PLAYER には非表示

#### 3.1.2 ボタンの状態
| 状態 | 表示 | 振る舞い |
|------|------|---------|
| 通常 | `Kaderuから取り込み` | 押下可能 |
| 同期中（PENDING） | `同期中… (mm:ss 経過)` | 押下不可（disabled） |

- 経過時間表示は1秒ごとに更新（クライアント側タイマー）
- PENDING 状態は `GET /api/kaderu-sync/status` で取得し、ページ表示中は **30秒に1回ポーリング**して状態を更新

#### 3.1.3 押下時の動作
1. `POST /api/kaderu-sync/trigger` を呼び出す
2. 成功時: トースト「Kaderu同期を開始しました。完了後にLINEでお知らせします（5分後を目安に画面をリロードしてください）」を表示
3. ボタンを「同期中…」表示にしてdisableする
4. ステータスポーリングを開始（30秒間隔）
5. 失敗時（409 Conflict: 既に PENDING あり / 403 Forbidden / 500 等）: トーストでエラー表示、ボタンは元の状態に戻す

#### 3.1.4 リロードについて
- 自動リロードは行わない
- LINE通知を受けたユーザーが自身で画面をリロードする運用

### 3.2 LINE通知仕様

#### 3.2.1 通知対象
- 押下した本人 1 名のみ
- `LineNotificationPreference` の opt-in/opt-out フラグは新設しない（ユーザー本人の明示的な操作に対するレスポンスのため、preference 上の有効/無効に関わらず常に送る）

#### 3.2.2 通知種別とメッセージ
| `LineNotificationType` | 送信条件 | メッセージ例 |
|------------------------|----------|--------------|
| `ADMIN_KADERU_SYNC_COMPLETED` | workflow の conclusion が `success` で終了 | `Kaderu予約取り込みが完了しました\n（団体: hokudai）\n結果: 新規 3件 / 拡張 1件 / スキップ 5件` |
| `ADMIN_KADERU_SYNC_FAILED` | workflow の conclusion が `failure` / `cancelled` / `timed_out` で終了 | `Kaderu予約取り込みに失敗しました\n（団体: hokudai）\n理由: workflow failure（GitHub Actions ログを確認してください）` |

- チャネル種別: PLAYER チャネル（押下者本人の LINE userId 宛）
- 月次 200通制限は通常通り適用される

### 3.3 ビジネスルール

#### 3.3.1 同期対象団体の解決
- ADMIN: ログイン中の `adminOrganizationId` を使用（リクエストでの上書き不可）
- SUPER_ADMIN: リクエストボディで `organizationId` を必須指定
- 解決された `organizationId` から `organizations.code` を取得し、workflow の `--org` inputs に渡す

#### 3.3.2 重複起動防止
- 同一団体で `status=PENDING` の `kaderu_sync_trigger_event` が存在する間、`POST /api/kaderu-sync/trigger` は 409 Conflict を返す
- ボタンは PENDING の存在を `GET /status` で検知して disabled にする
- GitHub Actions 側の `concurrency: kaderu-reservation-sync` は維持し、cron と手動の重複起動も防がれる

#### 3.3.3 PENDING タイムアウト
- スケジューラーが30秒ごとに PENDING を巡回し、GitHub API で workflow run の status を取得
- workflow run が `completed` になったら `COMPLETED` / `FAILED` に確定
- `triggered_at` から30分経過しても `completed` にならない場合、`FAILED` に確定し失敗通知を送る（fail-safe）

#### 3.3.4 エラーケース
| ケース | 振る舞い |
|--------|---------|
| GitHub API 呼び出し失敗（PAT切れ等） | trigger エンドポイントが 500 を返す。イベントは作成しない。フロントはトーストでエラー表示 |
| dispatch は成功したが run_id が取得できない | PENDING イベントは作成。次回ポーリング時にリトライ |
| workflow が実行されたが script で例外（DB接続失敗等） | conclusion が failure になる → `FAILED` + 失敗通知 |
| ユーザーが PENDING 中にログアウト | 通知は LINE に届くため影響なし |

## 4. 技術設計

### 4.1 API設計

#### `POST /api/kaderu-sync/trigger`
- 認可: `@RequireRole({"ADMIN", "SUPER_ADMIN"})`
- リクエストボディ:
  ```json
  { "organizationId": 2 }
  ```
  - ADMIN は省略可（自動で `adminOrganizationId` を使用）
  - SUPER_ADMIN は必須
- レスポンス (201 Created):
  ```json
  {
    "id": 123,
    "organizationId": 2,
    "organizationCode": "wasura",
    "triggeredAt": "2026-05-24T15:00:00",
    "status": "PENDING",
    "githubRunId": 12345678901
  }
  ```
- エラーレスポンス:
  - 409 Conflict: `{ "message": "同一団体の同期が既に実行中です" }`
  - 403 Forbidden: `{ "message": "他団体の同期は実行できません" }`
  - 500 Internal Server Error: `{ "message": "GitHub Actionsの起動に失敗しました" }`

#### `GET /api/kaderu-sync/status`
- 認可: `@RequireRole({"ADMIN", "SUPER_ADMIN"})`
- クエリパラメータ:
  - `organizationId` (ADMIN は無視, SUPER_ADMIN は必須)
- レスポンス (200 OK):
  ```json
  {
    "pendingEvent": {
      "id": 123,
      "organizationId": 2,
      "organizationCode": "wasura",
      "triggeredAt": "2026-05-24T15:00:00",
      "triggeredByPlayerId": 7,
      "elapsedSeconds": 92
    }
  }
  ```
  - PENDING がなければ `{ "pendingEvent": null }`

### 4.2 DB設計

#### 新規テーブル: `kaderu_sync_trigger_events`
```sql
CREATE TABLE kaderu_sync_trigger_events (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    triggered_by_player_id BIGINT NOT NULL REFERENCES players(id),
    triggered_at TIMESTAMP NOT NULL,
    status VARCHAR(16) NOT NULL,  -- 'PENDING' | 'COMPLETED' | 'FAILED'
    github_run_id BIGINT,
    completed_at TIMESTAMP,
    summary TEXT,
    failure_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 同一団体の PENDING を高速検索
CREATE INDEX idx_kaderu_sync_pending
    ON kaderu_sync_trigger_events (organization_id, status)
    WHERE status = 'PENDING';

-- スケジューラーが PENDING を巡回するため
CREATE INDEX idx_kaderu_sync_status_triggered
    ON kaderu_sync_trigger_events (status, triggered_at);
```

#### `LineMessageLog.LineNotificationType` enum 拡張
- `ADMIN_KADERU_SYNC_COMPLETED` を追加
- `ADMIN_KADERU_SYNC_FAILED` を追加

### 4.3 フロントエンド設計

#### 新規API client: `karuta-tracker-ui/src/api/kaderuSync.js`
```js
import client from './client';
export const kaderuSyncAPI = {
  trigger: (organizationId) => client.post('/kaderu-sync/trigger', { organizationId }),
  getStatus: (organizationId) => client.get('/kaderu-sync/status', { params: { organizationId } }),
};
```

#### `PracticeForm.jsx` への追加
- 状態:
  - `kaderuSyncPendingEvent`: 現在の PENDING イベント or null
  - `kaderuSyncElapsedSec`: 経過秒数（1秒タイマーで増分）
- フック:
  - `useEffect`: 30秒間隔で `kaderuSyncAPI.getStatus()` をポーリング
  - `useEffect`: PENDING がある間、1秒ごとに `kaderuSyncElapsedSec` を増分
- 表示:
  - `isAdmin(currentPlayer)` または `isSuperAdmin(currentPlayer)` のときのみボタンをレンダリング
  - ナビバーの月送り右矢印の右側に配置
- 押下時: `kaderuSyncAPI.trigger()` → 成功時にトースト表示＋ポーリング即時起動

### 4.4 バックエンド設計

#### 新規 Entity: `KaderuSyncTriggerEvent`
- `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/KaderuSyncTriggerEvent.java`
- フィールド: id, organization, triggeredByPlayerId, triggeredAt, status (enum), githubRunId, completedAt, summary, failureReason, createdAt, updatedAt
- enum `SyncStatus { PENDING, COMPLETED, FAILED }`

#### 新規 Repository: `KaderuSyncTriggerEventRepository`
```java
Optional<KaderuSyncTriggerEvent> findFirstByOrganizationIdAndStatusOrderByTriggeredAtDesc(
    Long organizationId, SyncStatus status);

List<KaderuSyncTriggerEvent> findAllByStatus(SyncStatus status);
```

#### 新規 DTO: `KaderuSyncTriggerEventDto`, `KaderuSyncStatusResponse`

#### 新規 Service: `KaderuSyncTriggerService`
- `triggerSync(playerId, organizationId)`: 重複チェック → GitHub Actions API dispatch → イベント作成
- `getStatus(organizationId)`: PENDING イベントを返す

#### 新規 Service: `GitHubActionsClient`
- Spring `RestClient` を使う
- `dispatchWorkflow(workflowFileName, ref, inputs)`: `POST /repos/{owner}/{repo}/actions/workflows/{file}/dispatches`
- `listRecentRuns(workflowFileName, createdAfter, eventType)`: dispatch直後の run_id 解決用
- `getWorkflowRun(runId)`: status / conclusion を取得
- 認証: 環境変数 `GITHUB_PAT` を `Authorization: Bearer <token>` で付与

#### 新規 Scheduler: `KaderuSyncStatusPollingScheduler`
- `@Scheduled(fixedDelay = 30_000)`
- 全 PENDING イベントを取得
- 各イベントについて:
  1. `githubRunId` が null なら、`listRecentRuns` から `triggered_at` 以降の run を探して埋める
  2. `getWorkflowRun(githubRunId)` で status/conclusion を取得
  3. `status=completed && conclusion=success` → `COMPLETED` に確定、`ADMIN_KADERU_SYNC_COMPLETED` 通知送信、summary を script のログから抽出（後述）
  4. `status=completed && conclusion in [failure, cancelled, timed_out]` → `FAILED` に確定、`ADMIN_KADERU_SYNC_FAILED` 通知送信
  5. `triggered_at + 30分` を超えた PENDING → `FAILED` に確定（fail-safe）

#### Summary 抽出方針
- 簡易実装: workflow run の logs を GitHub API でダウンロードして `新規作成: X件` 等の行を正規表現でパース
- 取得失敗時はsummaryをnullのまま完了通知を送る（メッセージは「結果: 詳細は GitHub Actions ログを確認してください」）

#### LineNotificationService への追加
- `sendKaderuSyncCompletedNotification(playerId, organizationCode, summary)`
- `sendKaderuSyncFailedNotification(playerId, organizationCode, failureReason)`

### 4.5 Workflow 設計

#### 新規 workflow: `.github/workflows/sync-kaderu-reservations-manual.yml`
```yaml
name: Sync Kaderu Reservations (manual, per-org)

on:
  workflow_dispatch:
    inputs:
      org:
        description: '対象団体コード'
        required: true
        type: choice
        options:
          - hokudai
          - wasura

concurrency:
  group: kaderu-reservation-sync
  cancel-in-progress: false

jobs:
  sync:
    runs-on: ubuntu-latest
    timeout-minutes: 15
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '20' }
      - working-directory: ./scripts/room-checker
        run: npm ci
      - working-directory: ./scripts/room-checker
        run: npx playwright install --with-deps chromium

      - name: Sync (hokudai)
        if: inputs.org == 'hokudai'
        working-directory: ./scripts/room-checker
        env:
          KADERU_USER_ID: ${{ secrets.KADERU_USER_ID }}
          KADERU_PASSWORD: ${{ secrets.KADERU_PASSWORD }}
          DATABASE_URL: ${{ secrets.KADERU_DATABASE_URL }}
        run: node sync-reservations.js --org hokudai --months 2

      - name: Sync (wasura)
        if: inputs.org == 'wasura'
        working-directory: ./scripts/room-checker
        env:
          KADERU_USER_ID: ${{ secrets.WASURA_KADERU_USER_ID }}
          KADERU_PASSWORD: ${{ secrets.WASURA_KADERU_PASSWORD }}
          DATABASE_URL: ${{ secrets.KADERU_DATABASE_URL }}
        run: node sync-reservations.js --org wasura --months 2
```

- 既存の cron workflow `sync-kaderu-reservations.yml` は変更しない（hokudai + wasura 両方を順次実行のまま）

### 4.6 運用作業（GitHub PAT 設定）

#### PAT 種別
- **fine-grained personal access token** を使用（classic より権限スコープが狭く、安全）

#### PAT 作成手順
1. GitHub にログイン → 右上アイコン → **Settings**
2. 左サイドバー最下部 → **Developer settings**
3. **Personal access tokens** → **Fine-grained tokens** → **Generate new token**
4. 各項目を入力:
   - **Token name**: `match-tracker-kaderu-sync`
   - **Resource owner**: `poponta2020`（自分のユーザー名）
   - **Expiration**: 90 days（推奨。期限切れ時の再発行時期を後述のリマインダーで管理）
   - **Repository access**: **Only select repositories** → `poponta2020/match-tracker` を選択
   - **Permissions** → **Repository permissions** → **Actions**: `Read and write`
   - その他の permission は **No access** のまま
5. **Generate token** をクリック → 表示された token 文字列をコピー（**この画面でしか表示されない**）

#### Render への登録
1. Render dashboard → 対象サービス（`karuta-tracker`）
2. **Environment** タブ → **Add Environment Variable**
3. Key: `GITHUB_PAT`、Value: コピーした token
4. **Save** → 自動再デプロイ

#### PAT の有効期限管理
- 期限切れ時は GitHub Actions dispatch が 401 を返し、トリガー機能が動かなくなる
- 期限が切れる前に GitHub の通知 or カレンダーリマインダーで再発行する運用
- PAT再発行手順は本機能のドキュメント (`docs/operations/`) に別途まとめる（implementation 完了後に作成）

## 5. 影響範囲

### 変更が必要な既存ファイル
- `karuta-tracker-ui/src/pages/practice/PracticeForm.jsx` — ボタン追加、状態管理、ポーリング
- `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/LineMessageLog.java` — enum 2件追加

### 新規作成ファイル
- バックエンド:
  - `entity/KaderuSyncTriggerEvent.java`
  - `repository/KaderuSyncTriggerEventRepository.java`
  - `dto/KaderuSyncTriggerEventDto.java`, `dto/KaderuSyncStatusResponse.java`, `dto/KaderuSyncTriggerRequest.java`
  - `service/KaderuSyncTriggerService.java`
  - `service/GitHubActionsClient.java`
  - `scheduler/KaderuSyncStatusPollingScheduler.java`
  - `controller/KaderuSyncTriggerController.java`
- DB マイグレーション:
  - `database/create_kaderu_sync_trigger_events.sql`
- フロントエンド:
  - `karuta-tracker-ui/src/api/kaderuSync.js`
- Workflow:
  - `.github/workflows/sync-kaderu-reservations-manual.yml`
- LINE通知:
  - `LineNotificationService.sendKaderuSyncCompletedNotification` / `sendKaderuSyncFailedNotification` メソッド追加

### 既存機能への影響
- **cron による30分ごとの定期同期** (`sync-kaderu-reservations.yml`): 完全に独立。本機能の有無に関わらず動作
- **`densuke-multi-org` 機能**: 影響なし。手動同期で作成されたセッションも同じパイプラインに乗る
- **LINE通知の月次200通制限**: 手動同期は1回押下で最大2通（成功 or 失敗 + 場合により再送）程度なので影響は軽微

### 運用上の影響
- `GITHUB_PAT` が未設定の状態でボタンを押すと 500 エラーになる
  - 対応: バックエンド起動時に `GITHUB_PAT` 未設定なら警告ログ、エンドポイントは 503 を返す実装にする
- PAT の有効期限切れ時はトリガー機能が止まる（cron 同期は影響なし）

## 6. 設計判断の根拠

### バックエンド経由で GitHub Actions を起動する設計
- フロントエンドから直接 GitHub API を叩く案は、PAT がブラウザに露出するため却下
- バックエンド集約により PAT を Render 環境変数（サーバーサイド）に閉じ込め、認可チェック・重複起動防止・イベント記録をまとめて行える

### 専用 workflow を新設する（既存 workflow を拡張しない）
- 既存 `sync-kaderu-reservations.yml` は cron で「両団体を順次同期」する責務
- 手動同期は「指定された 1 団体のみ」を実行するため責務が異なる
- `if` 条件で1つの workflow に押し込むと条件式が複雑化するため、workflow を分けたほうが読みやすい
- `concurrency` グループは同じ (`kaderu-reservation-sync`) を使うことで cron と手動の重複起動を防止

### LINE通知の `LineNotificationPreference` フラグを追加しない
- 押下者本人の明示的なアクションに対するフィードバックなので、opt-out を提供する意義が薄い
- 将来的に必要であれば追加可能（後方互換）

### 「workflow失敗」のみを失敗扱いとする（「変更なし」は失敗扱いしない）
- Kaderu 側に新規予約がなければ「変更なし」が正常動作
- 「同期したのに何も増えない」は cron 同期と同じ状況であり、通知価値が低い
- 一方 workflow conclusion が failure の場合は Kaderu 側のサイト変更・認証失敗・DB接続障害など対応必要な事象なので明示通知が価値高い

### fine-grained PAT を classic より優先
- classic は `repo` スコープを取ると当該ユーザーの全リポジトリに書き込み可能になる
- fine-grained は特定リポジトリ × 特定 permission のみに限定可能
- 90日 expiration は GitHub のデフォルト推奨値で、ローテーション運用しやすい

### ステータスポーリング間隔: 30秒
- workflow 完了は2〜5分程度
- 30秒間隔なら最悪でも30秒のタイムラグでボタンが解放される
- LINE通知も30秒以内に送信される
- ポーリング負荷も軽微（PENDING イベントの件数だけ GitHub API を呼ぶ）

### フロント側のステータスポーリング間隔: 30秒
- バックエンドのポーリング間隔と揃え、不必要な細かい更新を避ける
- 経過時間表示は1秒タイマーで補間（クライアント側で `triggered_at` から計算）
