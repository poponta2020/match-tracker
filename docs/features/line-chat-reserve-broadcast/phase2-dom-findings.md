# Phase 2 ローカルPoC 実DOM調査結果（chat.line.biz / LINE Official Account Manager チャット）

タスク7の実DOM調査（2026-07-17）。テストOA「好きな札分け発表ドラゴン10」(@668kuqdk)・テストグループ「テスト(3)」で実走確認。
`line-chat-worker/src/line/pages/OamChatPage.ts` のセレクタはこの結果に基づいて実装する。

## 0. ブラウザ／UA・認証

- **UAゲート**: chat.line.biz は `Claude/…Electron/…`（アプリ内Browser）を「サポートされていません」で弾く。**Playwright Chromium は headless(既定UA `HeadlessChrome/…`)でもゲート通過**（UA上書き不要）。ワーカーの `headless:true` はそのまま可。
- **ログイン**: `chat.line.biz/account/@<id>` 未認証時は `account.line.biz/login` → `access.line.me/oauth2` を経て `chat.line.biz/U<accountId>` へ着地。storageState（cookie）で以後は無認証で入れる。
- **アカウントURL構造**: ログイン後のOAチャットのルートは `https://chat.line.biz/U186be0f2e2986d8fb703ec0e897bc608`。この `U186…` は **OA固有のアカウントパス**。ルームURLは `https://chat.line.biz/U<accountId>/chat/C<groupId>`。
  - **`chatRoomId` = `C<groupId>`**（LINEグループID・`C`始まり。例 `C3e4c2c596a2c80848900698429414ca3`）。
  - **`U<accountId>` は WorkerTask に無い** → ワーカーの env 設定として持つ（v1は単一OA）。例 `LINE_OAM_ACCOUNT_PATH=U186…`。

## 1. openChat / verifyTargetChat

- **openChat**: ルームURL `https://chat.line.biz/<accountPath>/chat/<chatRoomId>` へ**直接ナビゲート可能**（一覧クリック不要・確認済み）。SPA描画に ~6-7秒待つ。
- **verifyTargetChat**:
  - ヘッダーに `heading[level=4]` = グループ名（例「テスト」）＋隣に人数 `(3)`。→ `getByRole("heading",{level:4})` のテキストが `chatRoomName` と一致するか。
  - URL に `chatRoomId` を含むか（`page.url().includes(chatRoomId)`）。
  - 両方一致で OK、不一致は `TARGET_CHAT_MISMATCH`。

## 2. detectAuthWall

- **positive判定のみ**（重要・advisor指摘）: `reserveMessage` は `openChat` の**前**に `detectAuthWall` を呼び、`index.ts` は1つの `page` を全サイクルで再利用する。初回は `about:blank`（host=""）。「chat.line.biz 以外＝壁」にすると初回が必ず誤爆し LINE_AUTH_EXPIRED を永久返却する。→ **認証面に居ることを積極確認したときだけ壁と判定**する: host ∈ {`account.line.biz`, `access.line.me`} など。`about:blank`・`chat.line.biz` は OK。
- 保険: room を開いた後に `#editor` が一定時間出ない場合も壁とみなす（openChat 後の再判定）。
- **実測（storageStateなし・Test B）**: room へナビ → `account.line.biz/login` に着地・body は「LINEヤフーBusiness ID」ログイン画面・`#editor` 不在。→ 上記判定でLOGIN_REQUIREDに落ちることを確認（AC-7の面を実証）。
- 2FA/CAPTCHA/本人確認の精密判別は行わず、いずれも「認証面に飛ばされた＝壁」として中止・報告する（要件どおり突破しない）。

## 3. inputMessage（複数行の忠実性＝§6の未解決論点 → 解決）

- 入力欄: **`<textarea-ex id="editor" aria-label="メッセージを入力" placeholder="Enterで送信 / Shift + Enterで改行">`**（カスタム要素）。
- **Enter=送信 / Shift+Enter=改行**。→ 本文を行分割し、行間で `keyboard.press("Shift+Enter")`、行内は `keyboard.type(line)`。
- **確認済み**: この方式で `editor.value` に `\n` 区切りで正しく保持された（`"…札分けA組: 1,2,3\n札分けB組: 4,5,6"`）。**Enter は絶対に押さない**（即送信されるため）。
- ロケータ: `page.locator("#editor")`（`aria-label="メッセージを入力"` でも可）。入力前に `.click()` でフォーカス。

## 4. setScheduledDateTime（モーダルを開いて日時を入れるところまで。確定はしない）

- 予約モーダルを開く: 送信ボタン横の分割ドロップダウン **`a[aria-label="oa.chat.button.scheduledmessages"]`**（i18nキー未翻訳のまま・安定）を click → モーダル「送信日時を設定」が開く。
- ラジオ3択（`name="schedule"`）:
  - `#date1` value=`TOMORROW`（「明日の9:00」・既定）
  - `#date2` value=`NEXT_MONDAY`（「月曜日の9:00」）
  - `#date3` value=`CUSTOM`（任意日時）← これを選ぶ
- **CUSTOM選択**: 隠しradioへの `.check()`/直接clickは効かない。**`page.locator("#date3").dispatchEvent("click")`** で React が拾い、date/time が有効化（確認済み）。
- 日時入力（CUSTOM選択で `disabled` 解除）:
  - 日付: **native `<input type="date" min=今日 max=+約4ヶ月>`** → `fill("YYYY-MM-DD")`
  - 時刻: **native `<input type="time" step="600">`** → `fill("HH:mm")`
  - **制約: 時刻は10分単位（step=600秒）／TZ=JST(UTC+09:00)**。→ `scheduledSendAt` の分は10の倍数である必要。**30分前・8:00は10分境界だが、1試合目が例:10:15開始→9:45は非境界**。
  - **実測（09:45投入）**: `fill("09:45")` → 時刻入力が**エラー無く 09:50 に自動スナップ**（バナー「2026/07/18 09:50に送信されます」）。＝**非10分の時刻は勝手に10分へ丸められ、意図と最大数分ずれる**。verifyScheduledEntry が期待(09:45)とバナー(09:50)の不一致を検出し `MANUAL_REVIEW_REQUIRED` になる（サイレント誤送信はしない安全側）。
  - **対策方針**: **BE側で `scheduledSendAt` を10分境界に floor する**のが本筋（PR#2=ワーカーの範囲外・別対応）。ワーカーは意図時刻を忠実に入れ、ズレたら verify で弾く（現状の usecase で担保）。
- ここでは **`設定` を押さない**（＝ dry-run はこの状態のスクショで完了）。

## 5. confirmReservation

- モーダルの **`button "設定"`（exact）** を click → 予約確定。

## 6. verifyScheduledEntry（「送信予定」表示の照合）

- 確定後、入力欄の上に **`alert: メッセージは<YYYY/MM/DD> <HH:MM>に送信されます。`** バナーが出る（+ `button "option"` = ⋮）。
- **再ナビ後も永続**（サーバ側確定・確認済み）。→ verify は「バナーのテキストに期待日時 `YYYY/MM/DD HH:MM` が含まれるか」で MATCHED 判定。
- **本文はバナーに出ない**。バナーは日時のみ。本文は「直前に自分が入力した本文」＝構成上一致（他アクター無し）。厳密照合が要るなら ⋮→「内容を編集」で本文を読めるが、実装面積とのトレードオフ（下記の設計判断参照）。
- 一定時間内にバナーが出なければ `TIMEOUT` → usecase 側で `MANUAL_REVIEW_REQUIRED`（自動再試行しない）。

## 7. findDuplicateReservation

- ルームを開いた時点で既にバナー `…に送信されます` が存在するか。存在し、その日時が対象 `scheduledSendAt` と一致 → 重複（`DUPLICATE_RESERVATION_FOUND`）。
- **1ルーム1予約が基本挙動**（バナーは単一・⋮も単一メッセージ対象）。複数可否は未検証だが、対象日時テキストでの一致判定にすれば複数でも安全。

## 8. deleteReservation

- ⋮ `button "option"` → メニュー `button "削除"` → 確認モーダル「予約メッセージを削除／この予約メッセージを削除しますか？」→ **`button "削除"`（赤・confirm）**。
- 削除後、バナー・`option` ボタンが消える（`banner=0 optionBtn=0` 確認済み）→ `DELETED`。
- 開いた時点でバナーが無ければ `NOT_FOUND`（追加予約はしない）。
- 注意: メニューの「削除」と確認の「削除」で `getByRole("button",{name:"削除"})` が2回出る。メニュークリック→メニュー閉→確認モーダルの削除、の順で1つずつ扱う。

## 9. usecase とのマッピング（reserveMessage の分割が dry-run に綺麗に対応）

| usecase 手順 | OamChatPage 実装 |
|---|---|
| detectAuthWall | host 判定＋#editor 存在 |
| openChat / verifyTargetChat | ルームURL直ナビ／heading[4]＋URL照合 |
| findDuplicateReservation | 既存バナー有無＋日時一致 |
| inputMessage | #editor に Shift+Enter 改行で type |
| setScheduledDateTime | モーダル開く→#date3 dispatchEvent→date/time fill（**設定は押さない**） |
| （dry-run） | この状態で screenshot → DRY_RUN_SUCCEEDED |
| confirmReservation | `設定` click |
| verifyScheduledEntry | バナー `…YYYY/MM/DD HH:MM…送信されます` の日時一致 |
| deleteReservation | ⋮→削除→確認削除→バナー消失 |

## 10. 未確定・要フォロー

- **10分丸め**: `scheduledSendAt` が10分非境界のときの扱い（BE丸め or 要確認）。
- **本文の厳密照合**: 現状は日時一致＋構成上の一致。⋮「内容を編集」で本文を読む実装にするかは設計判断。
- **複数予約の可否**: 未検証（1ルーム1予約前提で実装）。
- **datetime.ts**: `formatJstParts` は "YYYY/MM/DD"（バナー照合用）と、native input 用の "YYYY-MM-DD"/"HH:mm" の両方が要る。

## 11. 【最重要】チャットルームID と webhookグループID は別物（2026-07-18 発見）

**同じLINEグループに対して識別子が2系統あり、取り違えると必ず失敗する。**

| 用途 | 呼び名 | 取得元 | 本番全体グループの実値 |
| --- | --- | --- | --- |
| **チャット予約（ワーカー）** | OAMチャットルームID | OAM の URL `chat.line.biz/<acct>/chat/<ここ>` | `C432cd420dfface41b6201aaca8fd15f3` |
| **フォールバックpush** | webhookグループID | Messaging API webhook の `source.groupId` | `Cd6632033fa170a4ac57a887ac599901a` |

- どちらも `C` + 32桁hex で**見た目が完全に同じ**ため、値だけでは判別できない。
- **webhookのIDを OAM のURLに入れると `404 - Not found`**（逆も push 失敗）。この404は「グループが消えた」ではなく**ID体系の取り違え**。
- 格納先: OAM側 → `line_broadcast_group.chat_room_id` ／ webhook側 → `line_channels.line_group_id`。**この2カラムに同じ値を入れてはいけない。**
- したがって **`chat_room_id` は webhook から自動取得できない**。OAM のチャット一覧で当該ルームを開き、URL から採るのが唯一の正規手段（本番投入手順は RUNBOOK §9）。

### OAM チャット一覧にルームが出る条件

**招待（OAの参加）だけではルームは表示されない。** グループ内でメッセージのやり取りが発生して初めて出現する
（LINEの空状態表示「お客さまが公式アカウントにメッセージを送信すると、ここにチャットルームが表示されます」のとおり）。
`GET /api/v1/bots/<acct>?noFilter=true` の `hasChatRoom` で有無を判定できる。
→ 本番投入時は「招待 → グループで誰かが一言発言 → ルーム出現 → URLからID採取」の順になる。

### 参考: セッションで使える OAM 内部API（storageStateのCookieで叩ける）

- `GET /api/v1/bots?limit=100[&next=…]` … 配下OA一覧（`botId`=アカウントパス `U…`／`basicSearchId`=`@…`／`name`）。**Business ID配下の全OAが返る**＝storageStateが全OA共通であることの裏付け。
- `GET /api/v2/bots/<acct>/chats?folderType=ALL&limit=25&prioritizePinnedChat=true` … チャット一覧。
- `manager.line.biz/account/<@id>/setting` … 「グループ・複数人トークへの参加を許可する」の選択状態も同セッションで読める。

### 通数（課金）の注記

グループ内の発言に対する自動応答は `LineWebhookController` の **`sendReplyMessage`（応答メッセージ）**であり、
**無料メッセージ通数にカウントされない**（カウント対象は push / multicast / broadcast）。
本番グループでの疎通確認のために一言発言してもらっても通数は消費しない。
