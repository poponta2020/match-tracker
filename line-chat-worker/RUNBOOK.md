# line-chat-worker RUNBOOK

LINE Official Account Manager（OAM）のチャット予約送信を自動化する常駐ワーカーの運用手順。
機能仕様は [`docs/features/line-chat-reserve-broadcast/requirements.md`](../docs/features/line-chat-reserve-broadcast/requirements.md) を参照。

**現状（2026-07-18時点）**: タスク7（Phase 2 ローカルPoC）で `src/line/pages/OamChatPage.ts` の実DOM
セレクタを確定・実走検証済み。実DOM調査の根拠は
[`docs/features/line-chat-reserve-broadcast/phase2-dom-findings.md`](../docs/features/line-chat-reserve-broadcast/phase2-dom-findings.md)。

**タスク8a（VMデプロイ＋smoke）完了**: Oracle VM の `~/line-chat-worker` で
常駐稼働中（`restart: unless-stopped`）。**Smoke A**（トークン無し=401／不正=403／正当=200）と
**Smoke B**（VMのIPからテストグループへ dry-run → `DRY_RUN_SUCCEEDED`）が PASS。
**2週間の事前観測は廃止**し「smoke＋本番初回の目視確認」に差し替え済み（requirements.md §7・変更履歴）。

**タスク8b（本番投入）は未実施**。現在 `line_broadcast_group` は `enabled=f`・`chat_room_id=null` のため
**予約は1件も生成されない安全な停止状態**。本番投入は下記「9. 本番投入手順」に従うこと。

**確定した主な挙動（詳細は phase2-dom-findings.md）**:
- ルームURL `https://chat.line.biz/<accountPath>/chat/<chatRoomId>` へ直接ナビ可能。`chatRoomId` は `C…`（LINEグループID）。
- 本文の改行は Shift+Enter（Enter は即送信）。予約は「送信ボタン横の分割ドロップダウン→送信日時を設定モーダル→設定」。
- 予約確定後は「メッセージは YYYY/MM/DD HH:mm に送信されます」バナーで検証。削除は ⋮→削除→確認削除。
- **時刻は10分単位**（step=600）。非境界の分は入力側でスナップされ、verify が MISMATCH を検出（サイレント誤送信はしない）。→ **BE側で送信時刻を10分境界に丸める対応が別途必要**。
- chat.line.biz は Playwright Chromium(headless) のUAは通す（アプリ内Electronブラウザは非対応）。

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
   LINE_OAM_ACCOUNT_PATH=<ログイン後URL https://chat.line.biz/U... の U... 部分（対象OA固有）>
   POLL_INTERVAL_MS=300000
   DRY_RUN=false
   ARTIFACT_RETENTION_DAYS=14
   ```
   - `LINE_OAM_ACCOUNT_PATH` は、ログイン済みブラウザで chat.line.biz を開いた時のURL `https://chat.line.biz/U186…` の `U186…` 部分。ルームURLの組み立てに使う（WorkerTask には含まれない per-OA 定数）。
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
| コンテナが起動しない | `docker compose logs line-chat-worker` を確認。環境変数（`APP_BASE_URL`/`LINE_CHAT_WORKER_TOKEN`/`LINE_OAM_ACCOUNT_PATH`）の未設定が典型（`missing required env: ...` で fatal） |
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

## 8. ローカルPoC実走（実チャット・テストグループで実DOMコードパスを検証）

実ワーカーコード（`OamChatPage` + usecases）を合成 WorkerTask でテストグループに対して駆動し、
dry-run→本予約→重複検出→取消 を一気に検証する（アプリAPI・本番DBを介さない）。

```
cd line-chat-worker
# 1) storageState を作成（未作成なら）
npx tsx scripts/create-auth-state.ts ./storage-state.json
# 2) PoC 実走（テストグループの識別情報を env で渡す）
HEADED=1 \
  LINE_OAM_ACCOUNT_PATH=U186... \
  POC_CHAT_ROOM_ID=C3e4... \
  POC_CHAT_ROOM_NAME=テスト \
  npx tsx scripts/poc-run.ts
```

- `POC_CHAT_ROOM_ID` は対象グループを一度開いた時のURL `.../chat/C…` の `C…` 部分。
- 既定の予約日時は「翌日08:00 JST」（当日発火しない・10分境界）。`POC_SCHEDULED_SEND_AT` で上書き可。
- `HEADED=1` で可視ブラウザ。スクショは `poc-artifacts/`（`*.png` は gitignore）。
- 期待出力: `POC_RESULT: PASS`（dry-run=DRY_RUN_SUCCEEDED / 本予約=RESERVED / 重複=DUPLICATE_RESERVATION_FOUND / 取消=CANCELLED）。
- **実際の配信確認**（「指定時刻に届く」）は、近い10分境界を `POC_SCHEDULED_SEND_AT` に指定して取消せずに置き、テストグループで受信を確認する。

## 9. 本番投入手順（タスク8b・未実施）

現在は `line_broadcast_group` が `enabled=f`・`chat_room_id=null` のため予約が生成されない**安全な停止状態**。
以下を順に実施して初めて本番配信が動き出す。**途中で止めても配信欠落は起きない**（既存pushフォールバックが拾う）。

1. **本番OAを決める**: 全体グループ（約70名）に参加させる公式アカウントを1体決め、必要ならリネームする。
   - **storageState は LINE Business ID 単位で、配下の全OAに同一セッションで入れることを実測済み**（2026-07-18）。
     したがってOAを変えても**再ログインは不要**で、`.env` の `LINE_OAM_ACCOUNT_PATH` 差し替えだけでよい。
   - アカウントパスは、そのOAのチャットを開いた時のURL `https://chat.line.biz/U…` の `U…` 部分。
2. **OAを本番全体グループへ招待**し、そのルームを開いて URL `…/chat/C…` の `C…`（=`chatRoomId`）を控える。
3. **VMの `.env` を更新**（SSH接続情報＝ホスト・ユーザー・鍵は `CLAUDE.local.md` の「Oracle Cloud VM」節を参照）:
   ```
   cd ~/line-chat-worker && sed -i 's/^LINE_OAM_ACCOUNT_PATH=.*/LINE_OAM_ACCOUNT_PATH=<本番OAのU…>/' .env
   ```
4. **DBに本番グループを登録して有効化**（`line_broadcast_group` id=1 が北海道大学かるた会 全体グループ）:
   ```sql
   UPDATE line_broadcast_group
      SET chat_room_id = '<C…>', chat_room_name = '<OAM上のグループ表示名と完全一致させる>',
          enabled = true, updated_at = now()
    WHERE id = 1;
   ```
   - `chat_room_name` は**ワーカーが `heading[level=4]` と厳密一致で照合**する（不一致は `TARGET_CHAT_MISMATCH` で停止）。
     OAM画面に出る名前をそのまま入れること（人数表記 `(70)` は含めない）。
5. **ワーカー再起動**: `sudo docker compose restart line-chat-worker`
6. **初回1〜2回は目視確認**（最重要ゲート）: 前日20:00バッチ→ワーカー予約後、OAM上で
   「メッセージは YYYY/MM/DD HH:mm に送信されます」バナーの**日時と本文**を人が確認する。
   問題なければ以後は完全自動。
7. 異常時は「4. 障害対応」を参照。予約状態は管理画面（全体LINE配信管理の予約状況セクション）でも確認できる。
