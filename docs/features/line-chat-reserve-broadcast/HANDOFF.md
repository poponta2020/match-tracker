# line-chat-reserve-broadcast 引き継ぎ書（2026-07-20 時点）

> **【2026-07-20 更新】§4の24hセッション問題は解決済み** → **クリックスルー自動再ログイン**（「LINE account」→「Log in」の2クリック・password/CAPTCHA無しで24hセッション再発行）を **PR #1127 で実装・出荷（main マージ済み）**。ただし30日SSOは自動延長不可（`SSO_ABSOLUTE`）なので約月1回の手動ログインは残り、SSO期限の先回りアラートで人に促す（feature `docs/features/line-chat-auto-relogin/`・memory `ship_pr1127_line_chat_auto_relogin.md`／`feature_line_chat_auto_relogin.md`）。
> **§5 の後始末: 一時cron `10 16 19 7 *` 削除【済】／予約 id=1 → CANCELLED【済】／PR #1102（検出レース根治）は auto-relogin 出荷後に統制マージ＝OamChatPage 競合を解決し**#1102 も main マージ済み【済 2026-07-20 14:40】**。**
> **VM 再デプロイ【済 2026-07-20 23:48 JST】**: 別セッションが #1127 を 21:55 に VM デプロイ済みだったため、本セッションで **#1102 を上乗せデプロイ**（scp で src 丸ごと置換→`docker compose up -d --build`）。稼働コンテナは #1127＋#1102 の両機能搭載・`restartCount=0`・サイクル完走を確認。**手動ログイン不要**で稼働中（storageState の SSO は ~8/17 有効＝次の配信でワーカーが自動クリックスルーで chat セッションを自己回復）。手動フルログインは SSO 失効前（~8/14 に先回りアラート）に1回。**＝ line-chat-reserve-broadcast/auto-relogin 一式は本番稼働で完了。**

札分けの全体LINE配信を「チャット予約送信」で無料枠を消費せず行う機能。本番投入まで進んだが
**初回自動配信（7/20）はセッション切れで失敗**し、24hセッション問題の**恒久対策の方針が確定**（上記）。実装は未着手。

> 秘匿値（VMのSSH鍵・ホスト、DB接続情報、各種トークン）は **`CLAUDE.local.md`（gitignore対象）** が唯一の正典。本書には書かない。
> DB直クエリは `C:\tmp\dbtool\Q.java`（JDBC+IPv4でNAT64回避、PowerShellから実行、`-Dstdout.encoding=UTF-8` 必須、SQLはBOM無しで書く）。

---

## 1. 一言でいうと（現在地）

- 機能・実装・本番投入設定は**完了済み**。VMのワーカーは**常駐稼働中**。
- しかし **chat.line.biz のログインセッションは「ログインから絶対24時間」で失効**し、アクセスでは延命できない（実測）。
- そのため **7/19 20:00 の前日バッチ時点で既にセッションが死んでおり、初回予約は `LINE_AUTH_EXPIRED` で失敗**した。
- **7/20 のフォールバックpush（67名へ有料枠消費）はユーザー判断で手動停止**した（予約を `MANUAL_REVIEW_REQUIRED` にして gate で抑止 → `SKIPPED`・0通・枠0/200 を一次情報で確認済み）。今日の札分けは**ユーザーが手動送信**。
- **⛔ 最大のブロッカー = 24時間セッション問題。これが解けるまで自動配信は成立しない。** 方針決定待ち（§4）。

---

## 2. 本番の現在状態（実測値・そのまま使える）

**LINE / OA**
- 配信OA: `@773ifizy`「好きな札分け発表ドラゴン」／アカウントパス `U16c48919ba75082b834935062155dbba`
- 本番グループ: `令和8年度北海道大学かるたサークル`（67名）
- **OAMチャットルームID（予約送信用）**: `C432cd420dfface41b6201aaca8fd15f3`
- **webhookグループID（フォールバックpush用・別物）**: `Cd6632033fa170a4ac57a887ac599901a`
- **セッション: 死んでいる**（`__Host-chat-ses` は 7/19 15:58 失効済み）。次に自動予約するなら**再ログイン必須**。

**DB（本番 Render PostgreSQL）**
- `line_broadcast_group` id=1: `enabled=true`・`chat_room_id=C432cd…`・`chat_room_name=令和8年度北海道大学かるたサークル`
- `line_channels` id=21（@773ifizy）: `channel_type=GROUP`・`broadcast_group_id=1`・`line_group_id=Cd66320…`・`monthly_message_count=0`
- `line_channels` id=20（@668kuqdk ドラゴン10）: `line_group_id=NULL`（誤爆防止でクリア済み）
- `line_chat_reservations` id=1（session 1019・sched 7/20 16:30）: **`status=MANUAL_REVIEW_REQUIRED`**・`error_code=FALLBACK_SUPPRESSED_MANUAL`
  → **これは7/20 pushを止めるための手動セット**。送信時刻は既に過ぎたので無害だが、次セッションで意味を把握しておくこと。

**VM（Oracle・接続は CLAUDE.local.md）**
- `~/line-chat-worker` で docker compose 常駐（`restart: unless-stopped`）。`DRY_RUN=false`・`LINE_OAM_ACCOUNT_PATH=U16c48919…`
- **一時cronあり**: `10 16 19 7 *`（7/19 16:10のセッション確認・既に実行済み＝役目終了）。**次セッションで `crontab -e` から削除**（放置すると毎年7/19に走る）。判定ログは `~/line-chat-worker/session-check.log`。

---

## 3. これまでに出荷済み / 未処理

**出荷済み**
- 本体（アプリ＋ワーカー骨格）PR #1096、実DOMセレクタ確定 PR #1098、10分floor PR #1097
- webhookがグループ発言に応答する問題の修正 **PR #1101（マージ済・本番反映済）**

**未処理（次セッションで扱う）**
- **PR #1102**（予約有無をDOMバナー→予約一覧APIで判定＝レース根治）: **OPEN・CI green・auto-review 4R収束・未マージ・VM未再デプロイ**。今日の失敗（セッション切れ）とは別問題。方針が決まったらマージ＋VM再ビルド。
- **24時間セッション問題の恒久対策**（§4）＝本質。

---

## 4. ⛔ ブロッカー：24時間セッション問題（要ユーザー判断）

**事実**: chat.line.biz の `__Host-chat-ses` はログインから**絶対24時間**で失効。アクセスしても期限は延びない（実測 `DELTA=0分`）。SSO系Cookie（`__is_login_sso`等）は30日あるが、**期限切れ後にアクセスすると `account.line.biz/login` へリダイレクト＝自動再発行されない**（7/19のcronチェックが `NG_RELOGIN_REQUIRED` を実証）。

**含意**: 練習は週2回。**毎回、配信日の当日に人が再ログインしないと予約できない**＝現状のままでは自動運用が成立しない。

**選択肢（次セッションでユーザーと決める）**:
1. **セッション自動更新の仕組みを作る** — ただしLINEの24h絶対失効が壁。パスワード自動投入等は規約・2FA・保守性で要検討。
2. **別の配信方式に切り替える** — 例: 有料枠を割り切ってグループpush運用（card-division-group-broadcast の元設計）に戻す／通数課金を受容。
3. **半自動運用** — 配信日の朝だけ人が再ログインする運用に割り切る（storageState更新の1手順を定型化）。

→ **方針が決まるまで実装を進めない。** 目先の配信は止めてあるので急がない。

---

## 5. 次セッションの着手手順（方針決定後）

- **自動運用を続けるなら再ログイン**: ローカルPCでheadedログイン（`line-chat-worker/scripts/create-auth-state.ts`）→ storageState を VM の docker volume `line-chat-worker-data` の `/data/storage-state.json` へ配置 → `sudo docker compose restart line-chat-worker`。**ワーカーは起動時のCookieをメモリ保持するので、ファイル更新後は必ず restart**。
- **PR #1102**: 【済 2026-07-20 14:40 マージ】auto-relogin（#1127）出荷後に統制マージ。OamChatPage.ts/ChatPage.ts/index.test.ts の競合を human 監視下で解決（relogin() 保持＋findDuplicateReservation は #1102 の API判定=DuplicateCheck を採用）。tsc・worker 72テスト・lint 全green を確認後にマージ。
- **VM 再デプロイ**: 【済 2026-07-20 23:48】#1127 は別セッションが 21:55 にデプロイ済み。本セッションで #1102 を上乗せ（VM は git 非管理＝ローカル src を tar→scp で `~/line-chat-worker/src` 丸ごと置換→`sudo docker compose -f ~/line-chat-worker/docker-compose.yml up -d --build`。旧srcは `src.bak-pre1102` に退避）。両機能搭載・restartCount=0・サイクル完走を確認。手動ログイン不要で稼働中。
- **手動停止した予約 id=1 の後始末**: 【済 2026-07-20】`CANCELLED` に更新済（管理画面の「要確認」フラグ解消。sched 16:30 経過済・push リスク無し）。
- **一時cron削除**: 【済 2026-07-20】VM crontab から `10 16 19 7 *`（run-session-check）を削除済（crontab 空）。

---

## 6. 触るとハマる要点（必読）

- **2系統ID**: OAMチャットルームID（`chat_room_id`・予約用）と webhookグループID（`line_group_id`・push用）は**別物**。両方 `C`+32hexで見分け不能。取り違えるとOAMで404／push失敗。詳細 `phase2-dom-findings.md §11`。
- **配信OAを他グループに出し入れしない**: `handleJoin` が `line_group_id` を無条件上書き→`leave` がnullクリアし、**フォールバック宛先が消える**（本番グループに入ったままでも）。実害発生済み。復旧は id=21 の `line_group_id` を `Cd66320…` に再設定。
- **push抑止のレバー**: gate は `PENDING/FAILED→送信` / `RESERVED→無言抑止` / `RESERVING・MANUAL_REVIEW_REQUIRED・CANCEL_PENDING・DRY_RUN_SUCCEEDED→抑止(管理者アラート付)`。**push経路は `group.enabled` を見ない**ので、止めるなら予約statusを非送信状態にする（グループ無効化では止まらない）。pushスケジューラは3分毎・送信ウィンドウ `[sched, first_match_start)`。
- **`DRY_RUN=true` のまま有効化は罠**: 予約が `DRY_RUN_SUCCEEDED` に化けてフォールバックまで抑止＝無言の配信欠落。常駐は `false` が正。
- **Renderデプロイは env変更だけでは走らない**／完了まで約20分。反映確認は deploys API の `status=live`。`api.render.com` は `curl -4` 必須。
- **storageState は Business ID 単位で全OAに通る**ので、OA切替は env `LINE_OAM_ACCOUNT_PATH` 差し替え＋restartだけ（再ログイン不要）。
- **kagetra_new と同居VM**: グローバルな `docker system prune`／無指定 `docker compose down` 禁止。必ずワーカーの compose/project を明示。

---

## 7. 正典ポインタ

- 詳細memory: `.claude/memory/impl_line_chat_reserve_broadcast.md`（時系列の全経緯）／索引 `MEMORY.md`
- 実DOMマップ: `docs/features/line-chat-reserve-broadcast/phase2-dom-findings.md`（§11=2系統ID）
- 運用手順: `line-chat-worker/RUNBOOK.md`（§9=本番投入手順・実施済み値）
- 要件: `docs/features/line-chat-reserve-broadcast/requirements.md`／手順書: `implementation-plan.md`
- 関連PR: #1096 #1097 #1098 #1101(webhook無応答・マージ済) #1102(検出レース根治・OPEN)
