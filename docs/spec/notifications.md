# 通知・LINE・Push

> **責務:** アプリ内通知・Web Push・LINE通知連携の仕様
>
> **関連画面:** `/notifications`（通知一覧）、`/settings/notifications`（通知設定）、`/admin/line/channels`（LINEチャネル管理）、`/admin/line/schedule`（LINE通知スケジュール設定）
>
> **主要実装:**
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/NotificationController.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/PushSubscriptionController.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LineUserController.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LineAdminController.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LineWebhookController.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/NotificationDto.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/PushSubscriptionRequest.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/PushNotificationPreferenceDto.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/LineNotificationPreferenceDto.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/WaitlistPromotionService.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/LineReminderScheduler.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/LineChannelReclaimScheduler.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/LineMessageCountSyncScheduler.java`
> - `karuta-tracker-ui/src/pages/notifications/NotificationList.jsx`
> - `karuta-tracker-ui/src/pages/notifications/NotificationSettings.jsx`
> - `karuta-tracker-ui/src/pages/line/LineChannelAdmin.jsx`
> - `karuta-tracker-ui/src/pages/line/LineScheduleAdmin.jsx`

## 機能仕様

### LINE通知連携

#### 概要

LINE Messaging APIを用いて、練習・抽選に関する各種通知をユーザーのLINEに送信する。
チャネルプールを**選手用（PLAYER）**と**管理者用（ADMIN）**に分離し、通知の種類に応じて適切な用途のチャネルから送信する。
管理者は選手用と管理者用の2つのLINEを登録でき、通知を別々のトークで受け取れる。一般選手は選手用のみ。
ユーザー1人につき用途ごとにLINE公式アカウント（Messaging APIチャネル）を1つ割り当て、月200通の無料枠内で運用する。

#### 通知種別

| 通知種別 | トリガー方式 | 説明 |
|---------|------------|------|
| 抽選結果 | 管理者手動送信 | 抽選実行後、管理者が送信ボタンを押下。プレイヤーごとにまとめ送信（全当選→テキスト1通、一部落選→イントロ+セッション別Flex+クロージング、全落選→イントロ+セッション別Flex）。セッション別FlexにはLINE上でキャンセル待ち辞退ボタン付き |
| キャンセル待ち連絡 | イベント発火型（自動） | キャンセル発生→繰り上げ対象者へFlex Messageで自動送信（「参加する」「辞退する」ボタン付き）。定員未達（キャンセル待ちなし）の場合は通知なし |
| 管理者向けキャンセル待ち状況 | イベント発火型（自動） | キャンセル・降格・オファー辞退等の発生時、管理者にFlex Messageで通知。同一セッション×同一トリガー×同一プレイヤーの複数試合は1通にまとめて送信。トリガー種別に応じたヘッダーテキスト・イベント文言を表示 |
| オファー期限切れ | イベント発火型（自動） | オファー期限到達時に自動送信 |
| 対戦組み合わせ | 管理者手動送信 | 組み合わせ確定後、管理者が送信ボタンを押下 |
| 参加予定リマインダー | スケジュール型 | 練習日のN日前に自動送信（管理者設定可） |
| 締め切りリマインダー | スケジュール型 | 締め切りのN日前に自動送信（管理者設定可）。**未実装**（PracticeSessionに締め切り日フィールドが未追加のため、スケジューラの構造のみ存在） |
| 当日参加者確定 | スケジュール型（自動） | 毎日12:00 JST、OFFERED参加者をDECLINEDに一括変更（dirty=true設定＋空き枠発生時は補充フロー自動トリガー）後、WON参加者 + SUPER_ADMIN（`admin_same_day_confirmation` がONの場合）にメンバーリストFlex Message送信 |
| 当日キャンセル通知 | イベント発火型（自動） | 12:00以降のキャンセル時、WON参加者にキャンセル発生を通知 |
| 当日空き募集 | イベント発火型（自動） | 12:00以降のキャンセル時、非WON参加者に空き枠募集Flex Message（「参加する」ボタン付き）を送信 |
| 当日先着参加通知 | イベント発火型（自動） | 先着参加確定時、参加者本人への確認通知 + WON参加者への枠状況通知 |
| メンティーメモ更新 | イベント発火型（自動） | メンティーが個人メモを更新（内容変更時のみ）→ 全ACTIVEメンターにFlex Messageで自動送信。通知設定 `mentor_comment` トグルで制御 |
| 試合動画登録 | イベント発火型（自動） | 試合動画の新規登録時、対戦当事者のうち登録者本人を除く選手にテキストで自動送信（登録者が第三者なら両選手、当事者なら相手のみ）。URL差し替えでは通知しない。本文に登録者名・試合日・対戦カード・アプリリンク（結果入力済みなら `/matches/{matchId}`、未入力なら `/videos`）を含む。通知設定 `match_video_registered`（デフォルトON）トグルで制御。種別 `MATCH_VIDEO_REGISTERED`（選手用チャネル） |

**通知の振り分けルール:**
- `ADMIN_` プレフィックスの通知種別（当日確認まとめ、キャンセル待ち状況通知） → 管理者用チャネル経由で送信
- それ以外の通知種別 → 選手用チャネル経由で送信
- 該当チャネルが未リンクの場合はスキップ（エラーにしない）

#### アカウント紐付けフロー

1. ユーザーが設定画面で「LINE通知を有効にする」をタップ
2. システムがAVAILABLEなチャネルを割り当て、ワンタイムコード（英数字8桁、有効期限10分）を発行
3. 友だち追加URLとコードを画面に表示
4. ユーザーがLINEアプリで友だち追加 → Botが「連携コードを入力してください」と返信
5. ユーザーがLINE上でコードを送信 → システムがコード検証 → line_user_id保存 → LINKED状態
6. 連携完了時、当月・翌月に確定済みの抽選結果がある場合、そのユーザーの結果をLINEで自動送信する（送信失敗時も連携は成功）

#### チャネル管理

- チャネルは事前にLINE Developers Consoleで手動作成し、DBに登録（用途: PLAYER または ADMIN）
- チャネル管理画面では「選手用」「管理者用」タブで用途別に一覧・登録
- ステータス: AVAILABLE → ASSIGNED → LINKED（または DISABLED）
- 月間送信数カウント（上限200通/月、LINE APIから1時間ごとに自動同期）
- 未使用チャネル自動回収（90日未ログイン → 警告 → 7日猶予後回収）

#### スケジューラ

| スケジューラ | 実行タイミング | 処理 |
|------------|-------------|------|
| LineReminderScheduler | 毎日AM8:00 | 参加予定/締め切りリマインダー送信 |
| LineChannelReclaimScheduler | 毎日AM3:00 | 未使用チャネル回収 |
| LineMessageCountSyncScheduler | 毎時30分 | LINE APIから月間送信数を同期 |

#### LINE上での繰り上げ応答

キャンセル待ち繰り上げ通知はFlex Message（Bubble）で送信され、LINE上で直接応答できる。

##### 統合オファー通知

同一セッション内で同一プレイヤーに対して複数試合のオファーが発生した場合、1通の統合Flexメッセージにまとめて送信する。

- **初回オファー（複数試合）**: ヘッダー（「繰り上げ参加のお知らせ」）＋ボディ（練習日・会場・定員・キャンセル者名・応答期限）＋フッター（個別参加ボタン×N＋「すべての試合に参加」＋「辞退する」）
- **初回オファー（1試合のみ）**: 上記と同様だが「すべての試合に参加」ボタンは非表示
- **部分参加後の残りオファー**: ヘッダー（「残りの試合のオファー」）のみで、ボディのセッション情報は省略。残り1試合なら「すべての試合に参加」は非表示
- **応答期限**: 複数オファーの期限が異なる場合、最も遅い期限を統一表示
- **ボタン配色**: 個別参加=緑(#27AE60)、すべて参加=青(#2E86C1)、辞退=赤(#E74C3C)

##### postbackアクション

| アクション | パラメータ | 動作 |
|-----------|-----------|------|
| `waitlist_accept` | `participantId` | 個別試合の参加承諾 |
| `waitlist_accept_all` | `sessionId`, `playerId` | 同一セッション内の全OFFEREDを一括承諾 |
| `waitlist_decline_all` | `sessionId`, `playerId` | 同一セッション内の全OFFEREDを一括辞退 |

- 「辞退する」ボタンは常に一括辞退（`waitlist_decline_all`）として動作
- 全アクションに確認ダイアログを挟む（`CONFIRMABLE_ACTIONS`）

##### 部分参加後フロー

1. プレイヤーが個別参加ボタン（例:「1試合目に参加」）を押す
2. 該当試合をWONに変更
3. 同一セッション×同一プレイヤーの残りOFFEREDを検索
4. 残りがあれば「残りの試合のオファー」Flexメッセージを送信
5. プレイヤーがさらに個別参加→残りがあれば再度通知（繰り返し）

##### その他

- **応答方式**: postbackアクション → Webhookで受信 → `WaitlistPromotionService.respondToOffer()` / `respondToOfferAll()` を呼び出し
- **Webアプリとの連携**: LINE・Webアプリ共に同一のサービスメソッドを呼ぶため、DBの状態は常に一貫。片方で応答済みの場合、もう片方では「処理済み」と表示
- **セキュリティ**: postback受信時にLINE userId → プレイヤー紐付けを検証し、他人のオファーに応答不可
- **Webアプリからの応答確認通知**: Webアプリから繰り上げオファーに応答（承諾/辞退）した場合、LINEにも確認メッセージを送信する
  - 承諾時: `"{日付}の練習 試合{番号}の繰り上げ参加を承諾しました。"`
  - 辞退時: `"{日付}の練習 試合{番号}の繰り上げ参加を辞退しました。"`
  - 通知種別は WAITLIST_OFFER を使用（繰り上げ通知と同一の通知設定に従う）

#### リッチメニュー

PLAYERチャネルにリッチメニューを設定し、LINEトーク画面から主要機能に素早くアクセスできる。

**メニュー構成（3列x2行）:**

| 上段左 | 上段中 | 上段右 |
|--------|--------|--------|
| ロゴ（操作なし） | 今日の練習参加者を確認する（URI） | キャンセル待ち状況を見る（postback） |

| 下段左 | 下段中 | 下段右 |
|--------|--------|--------|
| 通知設定（URI） | アプリを開く（URI） | 当日参加申込（postback） |

**各ボタンの動作:**
- **今日の参加者**: `https://match-tracker-eight-gilt.vercel.app/practice?openToday=true` へ遷移し、当日の練習セッションがあればポップアップを自動表示する。セッション切れ等で未ログインの場合はログイン後に元URLへ復帰する
- **キャンセル待ち状況**: 自分のWAITLISTED/OFFEREDエントリ一覧をFlex Messageで返信。0件なら「現在キャンセル待ちはありません」。情報表示のみ（アクションボタンなし）
- **当日参加申込**: 当日の空き試合一覧をFlex Messageで返信し「参加する」ボタンで既存の`same_day_join`フローに合流。申込不可時は「現在参加申込できる試合はありません」
  - 申込可能条件: 当日・空きあり・キャンセル待ちなし → 時間制限なし / 当日・空きあり・キャンセル待ちあり → 12時JST以降のみ
- **通知設定**: `https://match-tracker-eight-gilt.vercel.app/settings/notifications` へ遷移
- **アプリを開く**: `https://match-tracker-eight-gilt.vercel.app/` へ遷移

**一括設定API**: `POST /api/admin/line/rich-menu/setup`（SUPER_ADMIN、multipart/form-dataで画像アップロード）で全PLAYERチャネルにリッチメニューを一括設定

#### セキュリティ

- Webhook署名検証（HMAC-SHA256、チャネルごとのchannel_secret）
- postbackイベント処理時のプレイヤー本人確認（LINE userId ↔ participantId の紐付け検証）
- Push API専用（Broadcast APIは使用禁止）
- 認証情報（channel_secret, channel_access_token）は現状DBに平文保存。将来的にAES-256-GCM暗号化を予定

## 画面

### 通知一覧

**パス**: `/notifications`

**表示内容**:
- 通知一覧（新しい順）
  - 通知タイトル
  - 通知本文
  - 種別アイコン（当選/落選/繰り上げ等）
  - 既読/未読状態
  - 作成日時
- クリックで既読にする
- ヘッダーに未読バッジ表示

## API

### 通知

`/api/notifications`

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `/?playerId=` | ALL | 通知一覧取得（PLAYERは自分のみ） |
| GET | `/unread-count?playerId=` | ALL | 未読通知数（PLAYERは自分のみ） |
| PUT | `/{id}/read` | ALL | 通知既読化（所有者チェックあり） |
| DELETE | `/?playerId=` | ALL | 通知一括削除・論理削除（PLAYERは自分のみ） |

#### GET /api/notifications?playerId={playerId}
**説明**: 通知一覧取得
**権限**: なし
**レスポンス**: `List<NotificationDto>`

#### GET /api/notifications/unread-count?playerId={playerId}
**説明**: 未読通知数取得
**権限**: なし
**レスポンス**: `{"count": 3}`

#### PUT /api/notifications/{id}/read
**説明**: 通知を既読にする
**権限**: なし

#### DELETE /api/notifications?playerId={playerId}
**説明**: 通知を一括削除（論理削除）
**権限**: なし
**レスポンス**: `{"deleted": 5}`

### Push購読

`/api/push-subscriptions`

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `/vapid-public-key` | ALL | VAPID公開鍵取得 |
| POST | `/` | ALL | Push購読登録 |
| DELETE | `/` | ALL | Push購読解除 |
| GET | `/preferences/{playerId}` | ALL | Web Push通知設定取得 |
| PUT | `/preferences` | ALL | Web Push通知設定更新 |

#### GET /api/push-subscriptions/vapid-public-key
**説明**: VAPID公開鍵を取得
**権限**: なし
**レスポンス**: `{"publicKey": "BPxxx..."}`

#### POST /api/push-subscriptions
**説明**: Push購読を登録
**権限**: なし
**リクエスト**: `PushSubscriptionRequest`
```json
{
  "playerId": 1,
  "endpoint": "https://fcm.googleapis.com/...",
  "p256dhKey": "BPxxx...",
  "authKey": "xxx..."
}
```

#### DELETE /api/push-subscriptions?playerId={playerId}&endpoint={endpoint}
**説明**: Push購読を解除
**権限**: なし

#### GET /api/push-subscriptions/preferences/{playerId}
**説明**: Web Push通知設定を取得（レコードなしの場合はデフォルト値を返す）
**権限**: なし
**レスポンス**:
```json
{
  "playerId": 1,
  "enabled": true,
  "lotteryResult": true,
  "waitlistOffer": true,
  "offerExpiring": true,
  "offerExpired": true,
  "channelReclaimWarning": true,
  "densukeUnmatched": true
}
```

#### PUT /api/push-subscriptions/preferences
**説明**: Web Push通知設定を更新（レコードがなければ新規作成）
**権限**: なし
**リクエスト**: `PushNotificationPreferenceDto`
```json
{
  "playerId": 1,
  "enabled": true,
  "lotteryResult": true,
  "waitlistOffer": false,
  "offerExpiring": true,
  "offerExpired": true,
  "channelReclaimWarning": true,
  "densukeUnmatched": true
}
```

### LINE通知

`/api/line`

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| POST | `/enable` | ALL | LINE通知有効化（チャネル割り当て+コード発行） |
| DELETE | `/disable` | ALL | LINE通知無効化（チャネル解放） |
| POST | `/reissue-code` | ALL | ワンタイムコード再発行 |
| GET | `/status?playerId=` | ALL | LINE連携状態取得 |
| GET | `/preferences?playerId=` | ALL | 通知種別設定取得 |
| PUT | `/preferences` | ALL | 通知種別設定更新 |
| POST | `/webhook/{lineChannelId}` | Public | LINEプラットフォームからのWebhook受信 |

⚠要確認: `enable` / `disable` / `reissue-code` / `status` の4エンドポイントについて、SPECでは `/api/line/enable` 等（`channelType` パスパラメータなし）、DESIGNでは `POST /api/line/{channelType}/enable` 等（`channelType`: PLAYER または ADMIN）としており不一致がある。DESIGN側がPLAYER/ADMIN二重チャネルモデル（本ファイル「LINE通知連携」参照）と整合するため、下記の詳細（DESIGN由来）を実装の実体として扱う。

#### POST /api/line/{channelType}/enable
**説明**: LINE通知を有効化する（チャネル割り当て＋ワンタイムコード発行）
**権限**: なし（channelType=ADMIN の場合、Service層でADMIN/SUPER_ADMINロールをチェック）
**パスパラメータ**: `channelType` — PLAYER または ADMIN
**リクエスト**: `{ "playerId": Long }`
**レスポンス**: `{ "friendAddUrl": String, "linkingCode": String, "codeExpiresAt": String, "status": String }`

#### DELETE /api/line/{channelType}/disable
**説明**: LINE通知を無効化する（チャネル解放）
**権限**: なし
**パスパラメータ**: `channelType` — PLAYER または ADMIN
**リクエスト**: `{ "playerId": Long }`

#### POST /api/line/{channelType}/reissue-code
**説明**: ワンタイムコードを再発行する
**権限**: なし
**パスパラメータ**: `channelType` — PLAYER または ADMIN
**リクエスト**: `{ "playerId": Long }`
**レスポンス**: `{ "linkingCode": String, "codeExpiresAt": String }`

#### GET /api/line/{channelType}/status?playerId={playerId}
**説明**: LINE連携状態を取得する（用途別）
**権限**: なし
**パスパラメータ**: `channelType` — PLAYER または ADMIN
**レスポンス**: `{ "enabled": Boolean, "linked": Boolean, "friendAddUrl": String }`

#### GET /api/line/preferences?playerId={playerId}
**説明**: 通知種別ごとの設定を取得する
**権限**: なし

#### PUT /api/line/preferences
**説明**: 通知種別ごとの設定を更新する
**権限**: なし
**リクエスト**: `LineNotificationPreferenceDto`

#### POST /api/line/webhook/{lineChannelId}
**説明**: LINEプラットフォームからのWebhookを受信する
**認証**: x-line-signatureヘッダーによる署名検証
**処理イベント**: follow（コード入力案内返信）、message（コード検証・紐付け）、postback（繰り上げオファー応答）、unfollow（記録のみ）

**postbackイベント処理フロー**:
1. postbackデータをパース（`action=waitlist_accept&participantId=123`）
2. LINE userId → LineChannelAssignment → playerId の紐付けを検証
3. participantIdの所有者がpostback送信者と一致することを確認
4. ステータスがOFFERED以外の場合は「処理済み」を返信
5. `WaitlistPromotionService.respondToOffer()` を呼び出し
6. 結果をReply APIで返信（承諾/辞退の確認メッセージ）

### LINE管理

`/api/admin/line`

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `/channels` | SUPER_ADMIN | チャネル一覧取得 |
| POST | `/channels` | SUPER_ADMIN | チャネル登録（個別） |
| POST | `/channels/import` | SUPER_ADMIN | チャネル一括登録 |
| PUT | `/channels/{id}/disable` | SUPER_ADMIN | チャネル無効化 |
| PUT | `/channels/{id}/enable` | SUPER_ADMIN | チャネル有効化 |
| DELETE | `/channels/{id}/assignment` | SUPER_ADMIN | チャネル強制割り当て解除 |
| POST | `/send/match-pairing` | ADMIN+ | 対戦組み合わせLINE送信 |
| POST | `/channels/migrate-webhook-urls` | SUPER_ADMIN | 全チャネルのWebhook URLをLINEチャネルIDベースに一括移行 |
| POST | `/rich-menu/setup` | SUPER_ADMIN | PLAYERチャネル全体にリッチメニューを一括設定（multipart/form-data） |
| GET | `/schedule-settings` | ADMIN+ | スケジュール設定取得 |
| PUT | `/schedule-settings` | ADMIN+ | スケジュール設定更新 |

> **注記:** 抽選結果LINE一括送信は `POST /api/lottery/notify-results` に統合済み（アプリ内通知と同時送信）。

（`schedule-settings` の2行は元 SPECIFICATION.md 上で表の続きが `> 注記` ブロックで分断され見出しなしの孤立行になっていたため、本ファイルでは同一テーブルへ連結して収録した。)

#### GET /api/admin/line/channels?channelType={channelType}
**説明**: チャネル一覧を取得する（用途別フィルタ対応）
**権限**: SUPER_ADMIN
**クエリパラメータ**: `channelType`（任意）— PLAYER / ADMIN。未指定時は全件返却

#### POST /api/admin/line/channels
**説明**: チャネルを登録する（個別）。リクエストボディに `channelType` を含める（デフォルト: PLAYER）
**権限**: SUPER_ADMIN

#### POST /api/admin/line/channels/import
**説明**: チャネルを一括登録する（JSON配列）
**権限**: SUPER_ADMIN

#### PUT /api/admin/line/channels/{channelId}/disable
**説明**: チャネルを無効化する
**権限**: SUPER_ADMIN

#### PUT /api/admin/line/channels/{channelId}/enable
**説明**: チャネルを有効化する
**権限**: SUPER_ADMIN

#### DELETE /api/admin/line/channels/{channelId}/assignment
**説明**: チャネルの強制割り当て解除
**権限**: SUPER_ADMIN

#### ~~POST /api/admin/line/send/lottery-result~~ （廃止）
**説明**: `POST /api/lottery/notify-results` に統合。アプリ内通知とLINE通知を一括送信する。

#### POST /api/admin/line/send/match-pairing
**説明**: 対戦組み合わせをLINE送信する
**権限**: SUPER_ADMIN, ADMIN
**リクエスト**: `{ "sessionId": Long }`
**レスポンス**: `{ "sentCount": Integer, "failedCount": Integer, "skippedCount": Integer }`

#### GET /api/admin/line/schedule-settings
**説明**: スケジュール型通知の設定を取得する
**権限**: SUPER_ADMIN, ADMIN

#### PUT /api/admin/line/schedule-settings
**説明**: スケジュール型通知の設定を更新する
**権限**: SUPER_ADMIN, ADMIN
**リクエスト**: `{ "notificationType": String, "enabled": Boolean, "daysBefore": [Integer] }`

#### POST /api/admin/line/channels/migrate-webhook-urls
**説明**: 全チャネルのWebhook URLをLINEチャネルIDベースに一括移行する。LINE Messaging APIを呼び出し、各チャネルのWebhook URLを `/api/line/webhook/{lineChannelId}` 形式に更新する。
**権限**: SUPER_ADMIN
**レスポンス**: `{ "successCount": Integer, "failedCount": Integer, "skippedCount": Integer }`

#### POST /api/admin/line/rich-menu/setup
**説明**: PLAYERチャネル全体にリッチメニューを一括設定する。画像をアップロードし、全PLAYERチャネルにリッチメニューを作成・画像設定・デフォルト適用する。
**権限**: SUPER_ADMIN
**リクエスト**: `multipart/form-data` — `image`: リッチメニュー画像（PNG/JPEG、2500x1686px）
**レスポンス**: `{ "successCount": Integer, "failureCount": Integer, "failures": [String] }`
