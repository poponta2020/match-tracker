# 予約同期

> **責務:** 外部予約システム（かでる2・7／東区民センター）のスクレイピングによる練習日自動登録連携の仕様
> **関連画面:** `/practice/new`
> **主要実装:** `karuta-tracker/src/main/java/com/karuta/matchtracker/service/KaderuSyncTriggerService.java`, `karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/KaderuSyncStatusPollingScheduler.java`, `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/KaderuSyncTriggerController.java`, `karuta-tracker/src/main/java/com/karuta/matchtracker/service/GitHubActionsClient.java`, `karuta-tracker-ui/src/pages/practice/PracticeForm.jsx`, `karuta-tracker-ui/src/api/kaderuSync.js`, `scripts/room-checker/scrape-mypage.js`, `scripts/room-checker/sync-reservations.js`, `scripts/room-checker/scrape-higashi-history.js`, `scripts/room-checker/sync-higashi-reservations.js`

## 機能仕様

### かでる2・7 予約同期

#### 概要

かでる2・7（北海道立道民活動センター）のマイページ「予約申込一覧」をスクレイピングし、予約済みの日付・部屋から練習日（`practice_sessions`）を自動登録する。北海道大学かるた会（hokudai）とわすらもち会（wasura）の両団体に対応し、団体ごとに別の Kaderu アカウントでログインして、対応する `organization_id` 付きで `practice_sessions` に登録する。

#### 対象

- **団体**: 北海道大学かるた会（`organizations.code = 'hokudai'`）、わすらもち会（`organizations.code = 'wasura'`）
- **部屋**: すずらん(ID:3)、はまなす(ID:11)、あかなら(ID:4)、えぞまつ(ID:8)（両団体共通）
- **時間帯**: 夜間（17:00〜21:00）のみ。午前・午後の予約は無視
- **createdBy**: `SYSTEM_USER_ID=0`（伝助同期と同じ慣習）

処理フローの詳細は「フロー」節の「かでる予約 → 練習日自動登録フロー」を参照。

#### 実行方式

| 方式 | 仕組み | トリガー |
|------|--------|---------|
| 定期実行（30分間隔） | GitHub Actions cron (`*/30 * * * *`) | 自動 |
| 手動実行 | GitHub Actions `workflow_dispatch` または `node sync-reservations.js --org <code>` | 手動 |

workflow は hokudai → wasura の順に 2 ステップ実行する。2 ステップ目は `if: always()` を付与しており、hokudai が失敗しても wasura は独立して実行される。いずれかのステップが失敗すると workflow 全体は失敗扱いとなる。`concurrency` グループ `kaderu-reservation-sync` で重複起動を防止する。

#### スクリプト構成

| ファイル | 役割 |
|---------|------|
| `scripts/room-checker/scrape-mypage.js` | マイページ予約一覧をスクレイピングしJSONで出力（`KADERU_USER_ID` / `KADERU_PASSWORD` の env を呼び出し側で切り替えて利用） |
| `scripts/room-checker/sync-reservations.js` | スクレイピング結果をDBに反映（重複スキップ・隣室拡張）。`--org <code>` 引数で対象団体を指定する（必須） |
| `.github/workflows/sync-kaderu-reservations.yml` | GitHub Actions ワークフロー（hokudai → wasura の2ステップ実行） |

**関連テーブル**:

| テーブル | 操作 |
|---------|------|
| `organizations` | SELECT（`code = $1` で指定団体の ID 取得。hokudai / wasura のいずれか） |
| `venues` | SELECT（defaultMatchCount, capacity 取得） |
| `practice_sessions` | SELECT / INSERT / UPDATE（`(session_date, organization_id)` スコープ。複合UNIQUEで競合スキップ） |

#### 認証情報（GitHub Secrets）

| Secret 名 | 用途 |
|----------|------|
| `KADERU_USER_ID` | hokudai 用 Kaderu 2.7 利用者ID |
| `KADERU_PASSWORD` | hokudai 用 Kaderu 2.7 パスワード |
| `WASURA_KADERU_USER_ID` | wasura 用 Kaderu 2.7 利用者ID |
| `WASURA_KADERU_PASSWORD` | wasura 用 Kaderu 2.7 パスワード |
| `KADERU_DATABASE_URL` | PostgreSQL 接続URL（両団体共通） |

#### 手動トリガー

cron による30分ごとの同期に加え、ADMIN+ ユーザーが任意のタイミングで Kaderu 同期 workflow を起動できる仕組みを提供する。Kaderu 側で新規予約が成立してから最大30分間は練習日一覧に反映されないため、「すぐに反映させたい」局面の待ち時間を解消する用途。

**起動 UI**

- 練習日登録画面（`/practice/new`）の上部ナビバーに、団体ごとに小ボタン（例: `Kaderu: hokudai`, `Kaderu: wasura`）を配置
- ADMIN は自団体のボタンのみ、SUPER_ADMIN は全団体のボタンが表示される
- PLAYER には非表示（`/practice/new` 自体が ADMIN+ 限定なので二重ガード）

起動〜完了通知までの詳細な処理シーケンスは「フロー」節の「かでる予約 → 練習日自動登録フロー」内の手動トリガーフローを参照。

**LINE 通知の例**

- 完了: `Kaderu予約取り込みが完了しました\n（団体: hokudai）\n結果: 新規 3件 / 拡張 1件 / スキップ 5件`
- 失敗: `Kaderu予約取り込みに失敗しました\n（団体: hokudai）\n理由: workflow failure（GitHub Actions ログを確認してください）`

**重複起動防止**

- 同一団体で `status=PENDING` の `kaderu_sync_trigger_events` が存在する間、`POST /trigger` は 409 Conflict を返す
- GitHub Actions 側の `concurrency: kaderu-reservation-sync` グループにより、cron 起動と手動起動の重複も自動的に直列化される

**Fail-safe タイムアウト**

- `triggered_at` から30分経過しても workflow が `completed` にならない PENDING は、scheduler が自動で `FAILED` に確定し失敗通知を送る

**運用要件**

- Render 環境変数 `GITHUB_PAT` に fine-grained PAT（Actions: Read and write、対象リポジトリ `poponta2020/match-tracker` 限定、推奨 expiration 90日）を設定する
- 未設定時は `POST /trigger` が 503 Service Unavailable を返す
- リポジトリ指定は環境変数 `GITHUB_REPO`（デフォルト `poponta2020/match-tracker`）で上書き可能

**関連テーブル**:

| テーブル | 操作 |
|---------|------|
| `kaderu_sync_trigger_events` | INSERT (PENDING) / UPDATE (COMPLETED/FAILED + github_run_id + summary/failure_reason) |
| `organizations` | SELECT (code 解決) |
| `line_message_log` | INSERT (ADMIN_KADERU_SYNC_COMPLETED / ADMIN_KADERU_SYNC_FAILED) |

**ファイル構成**:

| ファイル | 役割 |
|---|---|
| `.github/workflows/sync-kaderu-reservations-manual.yml` | 手動同期専用 workflow (workflow_dispatch + inputs.org) |
| `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/KaderuSyncTriggerController.java` | POST /api/kaderu-sync/trigger と GET /api/kaderu-sync/status |
| `karuta-tracker/src/main/java/com/karuta/matchtracker/service/KaderuSyncTriggerService.java` | dispatch 起動 + PENDING 巡回処理 |
| `karuta-tracker/src/main/java/com/karuta/matchtracker/service/GitHubActionsClient.java` | GitHub Actions REST API クライアント (Spring RestClient) |
| `karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/KaderuSyncStatusPollingScheduler.java` | 30秒間隔の PENDING 巡回スケジューラー |
| `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/KaderuSyncTriggerEvent.java` | イベントエンティティ (status: PENDING/COMPLETED/FAILED) |
| `karuta-tracker-ui/src/api/kaderuSync.js` | フロント API クライアント |
| `karuta-tracker-ui/src/pages/practice/PracticeForm.jsx` | `/practice/new` ナビバーのボタン + 30秒ポーリング + 1秒タイマー |

### 東区民センター予約同期

#### 概要

札幌市東区民センターの予約マイページ（sapporo-community.jp）の「申込履歴・結果」をスクレイピングし、予約済みの日付・部屋から練習日（`practice_sessions`）を自動登録する。登録された練習日は既存の伝助ページ作成・書き込みパイプラインに自動で乗る。

#### 対象

- **団体**: 北海道大学かるた会（`organizations.code = 'hokudai'`）
- **部屋**: さくら（和室）、かっこう（和室）、和室全室
- **時間帯**: 夜間（18:00〜21:00）固定。開始時刻 17:00 以降の予約のみ対象
- **ステータス**: `予約済` / `利用済`（`取消済` は取り込み除外。**取消済になった予約に対応する既存 practice_sessions の自動削除は行わず、運営者が手動で削除する運用**。経緯: docs/features/higashi-community-center-sync/requirements.md）
- **createdBy**: `SYSTEM_USER_ID=0`（かでる同期と同じ慣習）

処理フローの詳細は「フロー」節の「東区民センター予約 → 練習日自動登録フロー」を参照。

#### 実行方式

| 方式 | 仕組み | トリガー |
|------|--------|---------|
| 定期実行（30分間隔） | GitHub Actions cron (`*/30 * * * *`) | 自動 |
| 手動実行 | GitHub Actions `workflow_dispatch` または `node sync-higashi-reservations.js` | 手動 |

同一 Render PostgreSQL を共有（secret `KADERU_DATABASE_URL` を流用）。concurrency group (`higashi-reservation-sync`) が別なので cron 同時起動でも競合しない。1日に両センターの予約が併存しない前提（Q13）。既存セッションがかでる系会場の場合、東区民センター同期側からは触らない。

#### スクリプト構成

| ファイル | 役割 |
|---------|------|
| `scripts/room-checker/scrape-higashi-history.js` | 申込履歴をスクレイピングしJSONで出力 |
| `scripts/room-checker/sync-higashi-reservations.js` | スクレイピング結果をDBに反映（新規作成・会場昇格） |
| `.github/workflows/sync-higashi-reservations.yml` | GitHub Actions ワークフロー |

**関連テーブル**:

| テーブル | 操作 |
|---------|------|
| `organizations` | SELECT（hokudai の ID 取得） |
| `venues` | SELECT（defaultMatchCount, capacity 取得） |
| `practice_sessions` | SELECT / INSERT / UPDATE |

## フロー

### かでる予約 → 練習日自動登録フロー

北大かるた会（hokudai）とわすらもち会（wasura）の両団体に対応する。ワークフローは hokudai → wasura の順に 2 回 `sync-reservations.js` を呼び出し、各実行は対象団体スコープで独立に処理する（スクリプトは 1 実行 1 団体）。

```
[GitHub Actions: sync-kaderu-reservations.yml]
  cron: */30 * * * *  または  workflow_dispatch
  ↓
  ┌──────────────────────────────────────────────────────────────────┐
  │ Step 1: Sync reservations (hokudai)                              │
  │   env: KADERU_USER_ID / KADERU_PASSWORD ← Secrets               │
  │   cmd: node sync-reservations.js --org hokudai --months 2       │
  ├──────────────────────────────────────────────────────────────────┤
  │ Step 2: Sync reservations (wasura)  (if: always())              │
  │   env: KADERU_USER_ID  ← secrets.WASURA_KADERU_USER_ID          │
  │        KADERU_PASSWORD ← secrets.WASURA_KADERU_PASSWORD         │
  │   cmd: node sync-reservations.js --org wasura --months 2        │
  └──────────────────────────────────────────────────────────────────┘
  ↓ （各 Step の中で以下が走る）
[Node.js: scrape-mypage.js]
1. かでる2・7サイトにPlaywright(headless)でログイン
   - 環境変数: KADERU_USER_ID / KADERU_PASSWORD（workflow 側で団体ごとに切り替え）
   ↓
2. マイページ → 予約申込一覧ページへ遷移
   ↓
3. 当月+翌月のテーブルをパース
   - 利用日時 → 日付 + 時間帯判定
   - 利用施設 → 部屋名抽出（すずらん/はまなす/あかなら/えぞまつ）
   ↓
4. 夜間(17:00-21:00)のみフィルタ → JSON出力
   ↓
[Node.js: sync-reservations.js --org <code>]
5. JSONを受け取り、日付ごとに部屋をグルーピング
   - 「取消」ステータスは除外
   - 隣室ペア判定: すずらん+はまなす→拡張ID:7, あかなら+えぞまつ→拡張ID:9
   ↓
6. DB接続 → organizations テーブルから --org で指定された団体の ID を取得
   - SQL: SELECT id FROM organizations WHERE code = $1  ($1 = orgCode)
   - 該当組織が無ければエラー終了
   ↓
7. 日付ごとに practice_sessions を照合（organization_id スコープ）:
   a. (session_date, organization_id) で存在しない → INSERT（ON CONFLICT DO NOTHING、venue_id, totalMatches, startTime=17:00, endTime=21:00）
   b. 既存 venue_id=NULL → 算出会場で UPDATE（venue_id, capacity 補完）
   c. 単室で既存 + 隣室が予約にある → UPDATE（拡張会場に変更, capacity更新）
   d. それ以外 → スキップ
   ↓
8. 処理結果サマリーを `[<orgCode>]` プレフィックス付きで出力
```

`if: always()` により、hokudai が失敗しても wasura は独立して実行される。いずれかが失敗すれば workflow 全体は失敗扱い（赤バッジ）。`concurrency` グループ `kaderu-reservation-sync` で重複起動を防ぐ。

#### 手動トリガーフロー

cron による30分ごとの自動同期に加え、ADMIN+ が任意のタイミングで同期を起動できる経路。バックエンドが GitHub Actions の `workflow_dispatch` API を叩き、結果をイベントテーブルに記録、scheduler が完了検知して LINE 通知を返す。

```
[Frontend: PracticeForm.jsx (/practice/new, ADMIN+ 限定)]
  ボタン押下 → kaderuSyncAPI.trigger(orgId)
   ↓
[Backend: POST /api/kaderu-sync/trigger]
  KaderuSyncTriggerController
   ↓ (OrganizationScopeResolver で実効 orgId 解決)
  KaderuSyncTriggerService.triggerSync(playerId, orgId)
   1. PENDING 重複チェック (高速 path) → あれば 409 (DuplicateResourceException)
   2. organizations.code 取得 (なければ 404)
   3. KaderuSyncTriggerEvent を PENDING (github_run_id=null) で saveAndFlush
       - DB 側の UNIQUE 部分インデックス uk_kaderu_sync_pending が同時リクエストの
         race を確定的に検知 (DataIntegrityViolationException → 409 に変換)
       - dispatch より「先に」DB を占有することで、loser リクエストは UNIQUE 違反で
         止まり workflow を起動しない
   4. GitHubActionsClient.dispatchWorkflow(
        "sync-kaderu-reservations-manual.yml", "main",
        {org: code, eventId: <event.id>})
       - 環境変数 GITHUB_PAT で Bearer 認証
       - 未設定なら 503 (ResponseStatusException)
       - 失敗なら 500 (RuntimeException) → @Transactional が save を rollback
       - eventId は workflow の run-name に埋め込まれ、scheduler が display_title
         の "[event:<id>]" トークンで run ↔ event を一意に相関させる相関 ID
   ↓
[GitHub Actions: sync-kaderu-reservations-manual.yml]
  workflow_dispatch (inputs.org)
   - concurrency: kaderu-reservation-sync (cron と直列化)
   - if: inputs.org == 'hokudai'/'wasura' で1団体のみ実行
   - node sync-reservations.js --org <code> --months 2
   ↓
[Backend: KaderuSyncStatusPollingScheduler @Scheduled(fixedDelay=30s)]
  KaderuSyncTriggerService.pollPendingEvents()
   for each PENDING event:
     a. triggered_at から30分超過 → FAILED + 失敗通知 (fail-safe)
     b. github_run_id が null → listRecentRuns + display_title の "[event:<id>]"
        トークン照合で一意特定 (取れなければ次回。triggered_at から30分以内のみ捜索)
     c. getWorkflowRun(runId) で status/conclusion 取得
     d. completed && success → COMPLETED 確定
        - fetchWorkflowLogText から「新規作成:X件 / 会場拡張:X件 / スキップ:X件」を
          正規表現で抽出して summary に格納
        - LineNotificationService.sendKaderuSyncCompletedNotification()
     e. completed && (failure|cancelled|timed_out) → FAILED 確定
        - LineNotificationService.sendKaderuSyncFailedNotification()
   ↓
[LINE Messaging API]
  対象団体の ACTIVE な ADMIN 全員 ＋ ACTIVE な SUPER_ADMIN 全員 (重複排除) の
  ADMIN チャネル経由で送信 (取り込み結果を運営全体で共有)
  preference は経由しない (ADMIN 運用通知・常時送信)。1人の送信失敗は他に波及しない
   ↓
[Frontend]
  ユーザーが LINE 通知を受けて画面を手動リロード → 新しい練習日が表示される
  並行して30秒ポーリングが PENDING 解除を検知してボタンを再活性化
```

### 東区民センター予約 → 練習日自動登録フロー

```
[GitHub Actions: sync-higashi-reservations.yml]
  cron: */30 * * * *  または  workflow_dispatch
  ↓
[Node.js: scrape-higashi-history.js]
1. sapporo-community.jp にPlaywright(headless)でログイン
   - 環境変数: SAPPORO_COMMUNITY_USER_ID / SAPPORO_COMMUNITY_PASSWORD
   ↓
2. メニュー → 申込履歴・結果ページへ遷移
   ↓
3. 履歴テーブル（#ctl00_cphMain_gvView）の全ページをパース
   - 7列行のみデータ行として採用
   - 申込内容に「札幌市東区民センター」を含む行のみ抽出
   - 和暦 → 西暦変換、部屋名正規化（さくら/かっこう/和室全室）
   - 取消済は除外
   ↓
4. 夜間(開始時刻 17:00 以降)のみフィルタ → JSON出力
   ↓
[Node.js: sync-higashi-reservations.js]
5. JSONを受け取り、日付ごとに部屋をグルーピング
   - 和室全室 or (さくら + かっこう) → 東全室(ID:10)
   - さくら のみ → 東🌸(ID:6)
   - かっこう のみ → 警告ログを出しスキップ
   ↓
6. DB接続 → organizations テーブルから hokudai の ID を取得
   ↓
7. 日付ごとに practice_sessions を照合:
   a. 存在しない → INSERT（venue_id, totalMatches, startTime=18:00, endTime=21:00）
   b. 既存 venue_id=NULL → 算出会場で UPDATE（venue_id, capacity 補完）
   c. 既存 venue_id=6 + 算出 10 → UPDATE（東全室に昇格, capacity 更新）
   d. 既存 venue_id=10 → スキップ（ダウングレード無し）
   e. 既存 venue_id=3/4/7/8/9/11（かでる系） → スキップ（1日併存しない前提）
   ※ 取消済予約は取り込み対象外。既存セッションの自動削除も行わない（運営者が手動削除）
   ↓
8. 処理結果サマリー（created / expanded / skipped 件数）を出力
```
