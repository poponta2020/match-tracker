# line-chat-worker RUNBOOK

LINE Official Account Manager（OAM）のチャット予約送信を自動化する常駐ワーカーの運用手順。
機能仕様は [`docs/features/line-chat-reserve-broadcast/requirements.md`](../docs/features/line-chat-reserve-broadcast/requirements.md) を参照。

**現状（2026-07時点）**: タスク6（このディレクトリ）はロジック層・雛形Page Object・運用資材のみ。
`src/line/pages/*.ts` の実DOM操作はタスク7（Phase 2 ローカルPoC）で確定する。
それまでワーカーを実運用に投入しない。

## 1. 初回ログイン・storageState 作成（ローカルPC・headed）

1. `line-chat-worker/` で依存関係をインストール（初回のみ）:
   ```
   cd line-chat-worker
   npm install
   ```
2. ローカルPCで headed ブラウザを起動し、手動ログインする:
   ```
   npx tsx scripts/create-auth-state.ts ./storage-state.json
   ```
3. 開いたブラウザで対象OA（例:「北大かるた会お知らせ」）としてLINE Official Account Managerへログインし、
   対象の全体グループチャットが見える状態にする。
4. ターミナルで Enter を押すと `storage-state.json` が書き出される。
5. **このファイルは絶対にコミットしない**（`.gitignore` 済み）。安全なチャネル（SCP等）でVMへ転送する。

## 2. VM配置・デプロイ

1. Oracle Cloud VM（東京・kagetra_new 稼働ホスト）へ SSH 接続する。
2. `line-chat-worker/` 一式（Dockerfile・docker-compose.yml・src・package.json 等）をVM上の作業ディレクトリへ配置する
   （git pull または scp。kagetra_new のディレクトリとは分離する）。
3. `.env`（gitignore対象・VM上でのみ作成）に以下を設定する:
   ```
   APP_BASE_URL=https://<本番アプリのURL>
   LINE_CHAT_WORKER_TOKEN=<十分に長いランダム値。アプリ側 LINE_CHAT_WORKER_TOKEN と一致させる>
   POLL_INTERVAL_MS=300000
   DRY_RUN=false
   ARTIFACT_RETENTION_DAYS=14
   ```
4. 手順1で作成した `storage-state.json` を、docker volume `line-chat-worker-data` の
   `/data/storage-state.json` に配置する（初回起動前にコンテナへコピー、または
   ボリュームをマウントしたコンテナ内から `cp` する）。
5. 起動:
   ```
   docker compose up -d --build
   ```
6. ログを確認し、ポーリングが開始していること・トークンや本文が出力されていないことを確認する:
   ```
   docker compose logs -f line-chat-worker
   ```

## 3. 認証状態（storageState）の更新（再ログイン運用）

ワーカーは `LINE_AUTH_EXPIRED` を検出すると当サイクルを中断し、アプリへ FAILED を報告する
（`ADMIN_` 管理者通知が飛ぶ想定・タスク4）。セッションが失効した場合:

1. 手順1（ローカルPCでの手動ログイン）を再実施し、新しい `storage-state.json` を作成する。
2. 新しいファイルをVMの `/data/storage-state.json` へ上書き配置する。
3. ワーカーコンテナを再起動する:
   ```
   docker compose restart line-chat-worker
   ```

**注意**: ワーカーは実行毎に `storageState` を再エクスポートしてローリング更新するため、
通常運用中は手動更新が不要。手動更新が必要になるのは認証が完全に失効した場合のみ。

## 4. 障害対応

| 症状 | 対応 |
|---|---|
| `LINE_AUTH_EXPIRED` が継続する | 上記「3. 認証状態の更新」を実施 |
| `MANUAL_REVIEW_REQUIRED`（TARGET_CHAT_MISMATCH） | 管理画面で対象グループの `chatRoomId`/`chatRoomName` 登録を確認する。誤りがあれば管理APIで修正し、該当予約を手動で再試行する |
| `MANUAL_REVIEW_REQUIRED`（DUPLICATE_RESERVATION_FOUND） | OAM上で既存の予約内容を目視確認し、重複であれば片方を手動削除する |
| `MANUAL_REVIEW_REQUIRED`（CONFIRM_RESULT_UNKNOWN） | OAM上で当該予約が実際に登録されているか目視確認する（登録済みなら放置可、未登録なら管理画面から再試行） |
| `MANUAL_REVIEW_REQUIRED`（OLD_RESERVATION_NOT_FOUND） | OAM上で旧予約が存在するか確認する。存在しなければ新規予約を手動登録するか、管理画面から再試行して自動再予約させる |
| コンテナが起動しない | `docker compose logs line-chat-worker` を確認。環境変数（`APP_BASE_URL`/`LINE_CHAT_WORKER_TOKEN`）の未設定が典型 |
| アプリAPIへ到達できない | VMからのアウトバウンド疎通・`APP_BASE_URL` の値を確認 |

## 5. アーティファクト（スクリーンショット・Trace）の保持

- 失敗時のスクリーンショット・Playwright Trace は `ARTIFACT_DIR`（既定 `/data/artifacts`）に保存する。
- 保持期間は `ARTIFACT_RETENTION_DAYS`（既定14日）。期限切れの削除は運用者が定期的に確認・削除する
  （v1では自動削除ジョブは未実装。必要になれば cron で `find /data/artifacts -mtime +N -delete` 等を追加する）。
- **本文・Cookie・トークンはログ/stdoutに出力しない設計**（`src/appApi/client.ts`・`src/index.ts` 参照）。
  アーティファクトのスクリーンショットには画面内容（本文含む）が写り得るため、取り扱いに注意する。

## 6. dry-run モード

`DRY_RUN=true` を設定すると、予約確定ボタンを押さずに入力済み画面のスクリーンショットのみ保存する
（`DRY_RUN_SUCCEEDED` を報告）。テストグループでの検証時に使用する（タスク7）。

## 7. 開発・テスト（VM操作を伴わない）

```
cd line-chat-worker
npm install
npm run lint
npm test
```

実ブラウザ操作を伴うテストはCIに含めない（`.github/workflows/line-chat-worker.yml` はユニットテストのみ）。
実DOM調査・実機検証はタスク7（Phase 2 ローカルPoC）で行う。
