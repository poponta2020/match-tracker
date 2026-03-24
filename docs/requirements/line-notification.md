# LINE通知機能 要件定義書

## 改訂履歴

| 版 | 日付 | 内容 |
|----|------|------|
| 1.0 | 2026-03-24 | 初版作成 |

---

## 1. 概要

LINE Messaging APIを用いて、練習・抽選に関する各種通知をユーザーのLINEに送信する機能を導入する。

### 1.1 背景・目的

- 既存のアプリ内通知・Web Push通知に加え、LINEという普段使いのメッセージングアプリへの通知チャネルを追加することで、ユーザーへの到達率を向上させる
- キャンセル待ち連絡やリマインダーなど、見逃すと不利益が生じる通知をLINEで確実に届ける

### 1.2 方針

- LINE Messaging APIの無料プラン（コミュニケーションプラン: 月200通）を利用する
- ユーザー1人につきLINE公式アカウント（Messaging APIチャネル）を1つ割り当てることで、月200通の上限を実質ユーザー単位に分散する
- チャネルは事前に一括作成し、DBで割り当て・利用状況を管理する

---

## 2. 用語定義

| 用語 | 定義 |
|------|------|
| チャネル | LINE Developers Consoleで作成するMessaging APIチャネル。1つのLINE公式アカウントに対応 |
| チャネルID | チャネルを一意に識別するID（LINE Developers Consoleで発行） |
| チャネルシークレット | Webhook署名検証に使用する秘密鍵 |
| チャネルアクセストークン | APIリクエスト認証に使用するトークン（長期有効トークンを使用） |
| 友だち追加 | ユーザーがLINE上で公式アカウントを友だちに追加する操作 |
| LINE userId | ユーザーが友だち追加した際にWebhook経由で取得できる、LINE内部のユーザー識別子 |
| Push送信 | 特定のLINE userIdを指定してメッセージを送信するAPI呼び出し |

---

## 3. 通知種別

### 3.1 一覧

| # | 通知種別 | トリガー方式 | 説明 |
|---|---------|------------|------|
| 1 | 抽選結果 | 管理者手動送信 | 抽選実行後、管理者が送信ボタンを押した時点でLINE通知を送信 |
| 2 | キャンセル待ち連絡 | イベント発火型（自動） | キャンセル発生→繰り上げ対象者へのオファー時に自動送信 |
| 3 | オファー期限切れ | イベント発火型（自動） | オファー期限到達時に自動送信 |
| 4 | 対戦組み合わせ | 管理者手動送信 | 組み合わせ確定後、管理者が送信ボタンを押した時点でLINE通知を送信 |
| 5 | 参加予定リマインダー | スケジュール型 | 練習日のN日前に自動送信。送信日数・回数は管理者が設定 |
| 6 | 締め切りリマインダー | スケジュール型 | スケジュール登録締め切りのN日前に自動送信。送信日数・回数は管理者が設定 |

### 3.2 トリガー方式の定義

#### 管理者手動送信

- 管理者（SUPER_ADMIN / ADMIN）が管理画面上の送信ボタンを押すことで発火
- 抽選結果: 抽選実行画面に「LINE通知送信」ボタンを追加
- 対戦組み合わせ: 組み合わせ画面に「LINE通知送信」ボタンを追加
- 送信対象: 該当セッションの参加者のうち、LINE連携済み かつ 当該通知種別をONにしているユーザー

#### イベント発火型（自動）

- システム内のイベント発生をトリガーに自動送信
- キャンセル待ち連絡: `WaitlistPromotionService.promoteNextWaitlisted()` 実行時
- オファー期限切れ: `OfferExpiryScheduler` によるオファー期限切れ処理時
- 送信対象: 該当ユーザーがLINE連携済み かつ 当該通知種別をONにしている場合

#### スケジュール型

- 管理者が設定した条件に基づき、スケジューラが自動送信
- 設定項目:
  - **送信タイミング**: 対象日の何日前に送信するか（例: 3日前、1日前）
  - **送信回数**: 最大何回送信するか（例: 2回 → 3日前と1日前に1回ずつ）
- デフォルト値を設定し、管理者が変更可能とする

### 3.3 メッセージ内容

| # | 通知種別 | メッセージ例 |
|---|---------|------------|
| 1 | 抽選結果（当選） | 「4月5日の練習 試合1に当選しました」 |
| 1 | 抽選結果（落選） | 「4月5日の練習 試合1: キャンセル待ち2番です」 |
| 2 | キャンセル待ち連絡 | 「4月5日の練習 試合1に空きが出ました。参加しますか？（期限: 4/4 10:00）」 |
| 3 | オファー期限切れ | 「4月5日の練習 試合1の繰り上げ参加の期限が切れました」 |
| 4 | 対戦組み合わせ | 「4月5日の練習の対戦組み合わせが確定しました」 |
| 5 | 参加予定リマインダー | 「明日4月5日は練習日です。準備をお忘れなく！」 |
| 6 | 締め切りリマインダー | 「4月分のスケジュール登録締め切りは3月31日です。登録はお済みですか？」 |

※ メッセージ内容は既存の`NotificationService`のメッセージと整合させる。具体的な文言は実装時に調整。

---

## 4. LINEチャネル管理

### 4.1 チャネルのライフサイクル

```
[事前準備(手動)]         [運用(自動)]                    [回収(自動)]
      │                      │                              │
 LINE Developers        ユーザーがLINE通知を             長期未ログイン検知
 Consoleでチャネル       有効化                               │
 を一括作成                  │                          警告通知(アプリ内)
      │                 未使用チャネルを割り当て               │
 認証情報をDBに              │                          猶予期間経過
 登録                   QRコード/URL表示                     │
      │                      │                          割り当て解除
 Webhook URL設定        ユーザーが友だち追加              チャネル→AVAILABLE
                             │
                        Webhook: follow受信
                             │
                        line_user_id保存
                        → LINKED状態
```

### 4.2 チャネルステータス

| ステータス | 説明 |
|-----------|------|
| AVAILABLE | 未割り当て。新規ユーザーに割り当て可能 |
| ASSIGNED | ユーザーに割り当て済みだが、友だち追加未完了 |
| LINKED | ユーザーが友だち追加済み。通知送信可能 |
| DISABLED | 無効化。管理者が手動で無効にしたチャネル |

### 4.3 事前準備（手動作業）

以下はシステム運用開始前に手動で行う：

1. LINE Developers Consoleでチャネルを必要数作成
2. 各チャネルのWebhook URLを設定: `https://{domain}/api/line/webhook/{channelId}`
3. 各チャネルの認証情報（チャネルID、シークレット、アクセストークン）をDBに登録
   - CSVインポートまたは管理画面からの一括登録を提供

### 4.4 月間送信数の管理

- チャネルごとに月間送信数をカウントする
- 月初にカウントをリセットする（スケジューラで自動実行）
- 上限（200通）に近づいた場合、送信前にチェックし、上限超過時は送信をスキップしてログに記録する
- スケジュール型の通知よりイベント発火型・手動送信の通知を優先する（重要度が高いため）

---

## 5. ユーザーフロー

### 5.1 LINE通知の有効化

```
ユーザー: 設定画面で「LINE通知を有効にする」をタップ
    │
システム: AVAILABLEなチャネルを1つ割り当て（→ ASSIGNED）
    │
システム: 割り当てたチャネルの友だち追加QRコード/URLを表示
    │
ユーザー: LINEアプリで友だち追加
    │
LINE: Webhook (followイベント) を送信
    │
システム: line_user_id を保存（→ LINKED）
    │
システム: 設定画面に「LINE通知: 有効」と表示
```

### 5.2 LINE通知の無効化

```
ユーザー: 設定画面で「LINE通知を無効にする」をタップ
    │
システム: 割り当て解除（→ チャネルをAVAILABLEに戻す）
    │
システム: line_user_idをクリア
    │
※ ユーザーがLINE上で友だちを削除する必要はない（送信しないだけ）
```

### 5.3 通知種別ごとのON/OFF設定

```
ユーザー: 設定画面でトグルスイッチを操作
    │
    ├─ 抽選結果           [ON/OFF]
    ├─ キャンセル待ち連絡   [ON/OFF]
    ├─ オファー期限切れ     [ON/OFF]
    ├─ 対戦組み合わせ      [ON/OFF]
    ├─ 参加予定リマインダー  [ON/OFF]
    └─ 締め切りリマインダー  [ON/OFF]
    │
システム: line_notification_preferences テーブルを更新
```

デフォルト値: 全通知種別ON

---

## 6. 管理者機能

### 6.1 チャネル管理画面

管理者（SUPER_ADMIN）が利用可能。

- チャネル一覧の閲覧（ステータス、割り当てユーザー、月間送信数）
- チャネルの新規登録（個別 / CSV一括インポート）
- チャネルの無効化・有効化
- チャネルの強制割り当て解除

### 6.2 手動通知送信

#### 抽選結果のLINE送信

- 抽選実行画面（既存）に「LINE通知送信」ボタンを追加
- ボタン押下 → 該当月のセッション参加者（WON / WAITLISTED）のうちLINE連携済み・通知ONのユーザーに一括送信
- 送信前に確認ダイアログ: 「{N}人にLINE通知を送信します。よろしいですか？」
- 送信結果（成功数 / 失敗数）を表示

#### 対戦組み合わせのLINE送信

- 組み合わせ画面（既存）に「LINE通知送信」ボタンを追加
- ボタン押下 → 該当セッションの参加者のうちLINE連携済み・通知ONのユーザーに送信
- 同様に確認ダイアログと送信結果表示

### 6.3 スケジュール型通知の設定

管理者（SUPER_ADMIN / ADMIN）が設定画面で以下を管理:

#### 参加予定リマインダー設定

| 設定項目 | 型 | デフォルト値 | 説明 |
|---------|---|------------|------|
| 有効/無効 | Boolean | 有効 | リマインダー機能自体のON/OFF |
| 送信スケジュール | Integer[] | [1] | 練習日の何日前に送信するか（配列で複数指定可） |

例: `[3, 1]` → 3日前と1日前の2回送信

#### 締め切りリマインダー設定

| 設定項目 | 型 | デフォルト値 | 説明 |
|---------|---|------------|------|
| 有効/無効 | Boolean | 有効 | リマインダー機能自体のON/OFF |
| 送信スケジュール | Integer[] | [3, 1] | 締め切りの何日前に送信するか |

---

## 7. 未使用チャネルの自動回収

### 7.1 回収条件

- チャネルが割り当て済み（ASSIGNED または LINKED）のユーザーが**90日間**アプリにログインしていない場合を対象とする
- 90日の閾値は設定で変更可能とする

### 7.2 回収フロー

```
[毎日AM3:00にスケジューラ実行]
    │
    ├─ 90日以上未ログインかつチャネル割り当て済みのユーザーを検索
    │
    ├─ 該当ユーザーにアプリ内通知で警告
    │   「LINE通知の割り当てが7日後に解除されます。
    │    継続利用する場合はアプリにログインしてください」
    │
    ├─ 警告済みかつ猶予期間（7日）経過 かつ 依然未ログイン
    │
    ├─ 割り当て解除
    │   ├─ line_user_id をクリア
    │   ├─ チャネルステータス → AVAILABLE
    │   └─ assignment レコードの status → RECLAIMED
    │
    └─ ログに記録
```

### 7.3 再割り当て

- 回収済みチャネルは新規ユーザーへの割り当てに利用可能
- 前ユーザーの友だち登録がLINE上に残っていても、Push APIはuserIdを指定して送信するため、前ユーザーに通知が届くことはない
- 新ユーザーが友だち追加すると、新しいline_user_idで紐づけが行われる

---

## 8. データベース設計

### 8.1 新規テーブル

#### line_channels（LINEチャネル情報）

| カラム | 型 | NULL | デフォルト | 説明 |
|-------|---|------|----------|------|
| id | BIGSERIAL | NO | - | PK |
| channel_name | VARCHAR(100) | YES | - | 管理用表示名 |
| line_channel_id | VARCHAR(50) | NO | - | LINE発行のチャネルID |
| channel_secret | VARCHAR(255) | NO | - | チャネルシークレット（暗号化保存） |
| channel_access_token | TEXT | NO | - | アクセストークン（暗号化保存） |
| status | VARCHAR(20) | NO | 'AVAILABLE' | AVAILABLE / ASSIGNED / LINKED / DISABLED |
| friend_add_url | TEXT | YES | - | 友だち追加URL |
| qr_code_url | TEXT | YES | - | QRコード画像URL |
| monthly_message_count | INTEGER | NO | 0 | 当月送信数 |
| message_count_reset_at | TIMESTAMP | YES | - | 送信数リセット日時 |
| created_at | TIMESTAMP | NO | NOW() | 作成日時 |
| updated_at | TIMESTAMP | NO | NOW() | 更新日時 |

#### line_channel_assignments（チャネル割り当て）

| カラム | 型 | NULL | デフォルト | 説明 |
|-------|---|------|----------|------|
| id | BIGSERIAL | NO | - | PK |
| line_channel_id | BIGINT | NO | - | FK → line_channels.id |
| player_id | BIGINT | NO | - | FK → players.id |
| line_user_id | VARCHAR(50) | YES | - | follow時に取得するLINE userId |
| status | VARCHAR(20) | NO | 'PENDING' | PENDING / LINKED / UNLINKED / RECLAIMED |
| assigned_at | TIMESTAMP | NO | NOW() | 割り当て日時 |
| linked_at | TIMESTAMP | YES | - | follow完了（LINKED化）日時 |
| unlinked_at | TIMESTAMP | YES | - | 解除日時 |
| reclaim_warned_at | TIMESTAMP | YES | - | 回収警告通知日時 |
| created_at | TIMESTAMP | NO | NOW() | 作成日時 |

#### line_notification_preferences（通知設定）

| カラム | 型 | NULL | デフォルト | 説明 |
|-------|---|------|----------|------|
| id | BIGSERIAL | NO | - | PK |
| player_id | BIGINT | NO | - | FK → players.id（UNIQUE） |
| lottery_result | BOOLEAN | NO | TRUE | 抽選結果 |
| waitlist_offer | BOOLEAN | NO | TRUE | キャンセル待ち連絡 |
| offer_expired | BOOLEAN | NO | TRUE | オファー期限切れ |
| match_pairing | BOOLEAN | NO | TRUE | 対戦組み合わせ |
| practice_reminder | BOOLEAN | NO | TRUE | 参加予定リマインダー |
| deadline_reminder | BOOLEAN | NO | TRUE | 締め切りリマインダー |
| updated_at | TIMESTAMP | NO | NOW() | 更新日時 |

#### line_notification_schedule_settings（スケジュール型通知設定）

| カラム | 型 | NULL | デフォルト | 説明 |
|-------|---|------|----------|------|
| id | BIGSERIAL | NO | - | PK |
| notification_type | VARCHAR(30) | NO | - | PRACTICE_REMINDER / DEADLINE_REMINDER（UNIQUE） |
| enabled | BOOLEAN | NO | TRUE | 有効/無効 |
| days_before | VARCHAR(50) | NO | - | 送信日数（JSON配列文字列。例: "[3, 1]"） |
| updated_at | TIMESTAMP | NO | NOW() | 更新日時 |
| updated_by | BIGINT | YES | - | 最終更新者のplayer_id |

#### line_message_log（送信ログ）

| カラム | 型 | NULL | デフォルト | 説明 |
|-------|---|------|----------|------|
| id | BIGSERIAL | NO | - | PK |
| line_channel_id | BIGINT | NO | - | FK → line_channels.id |
| player_id | BIGINT | NO | - | FK → players.id |
| notification_type | VARCHAR(30) | NO | - | 通知種別 |
| message_content | TEXT | NO | - | 送信メッセージ内容 |
| status | VARCHAR(20) | NO | - | SUCCESS / FAILED / SKIPPED |
| error_message | TEXT | YES | - | 失敗時のエラー内容 |
| sent_at | TIMESTAMP | NO | NOW() | 送信日時 |

### 8.2 既存テーブルへの変更

なし。既存の`notifications`テーブル（アプリ内通知）やその他のテーブルは変更しない。LINE通知は独立したサブシステムとして構築する。

---

## 9. API設計

### 9.1 Webhookエンドポイント

#### POST /api/line/webhook/{channelId}

LINEプラットフォームからのWebhookを受信する。

- **認証**: x-line-signature ヘッダーによる署名検証（チャネルごとのchannel_secretを使用）
- **処理するイベント**:
  - `follow`: line_user_idを保存、割り当てステータスをLINKEDに更新
  - `unfollow`: ユーザーがブロックしたことを記録（割り当ては維持、送信対象から除外）

### 9.2 ユーザー向けAPI

#### POST /api/line/enable

LINE通知を有効化する（チャネル割り当て）。

- **リクエスト**: `{ "playerId": Long }`
- **レスポンス**: `{ "friendAddUrl": String, "qrCodeUrl": String, "status": String }`
- **処理**: AVAILABLEなチャネルを割り当て、友だち追加URLを返す
- **エラー**: 利用可能なチャネルがない場合は503

#### DELETE /api/line/disable

LINE通知を無効化する（チャネル解放）。

- **リクエスト**: `{ "playerId": Long }`
- **レスポンス**: 204 No Content
- **処理**: 割り当て解除、チャネルをAVAILABLEに戻す

#### GET /api/line/status

LINE連携状態を取得する。

- **クエリパラメータ**: `playerId`
- **レスポンス**: `{ "enabled": Boolean, "linked": Boolean, "friendAddUrl": String }`

#### GET /api/line/preferences

通知種別ごとの設定を取得する。

- **クエリパラメータ**: `playerId`
- **レスポンス**: `LineNotificationPreferencesDto`

#### PUT /api/line/preferences

通知種別ごとの設定を更新する。

- **リクエスト**: `LineNotificationPreferencesDto`
- **レスポンス**: 200 OK

### 9.3 管理者向けAPI

#### GET /api/admin/line/channels

チャネル一覧を取得する。

- **RequireRole**: SUPER_ADMIN
- **レスポンス**: チャネル一覧（ステータス、割り当てユーザー、月間送信数）

#### POST /api/admin/line/channels

チャネルを登録する（個別）。

- **RequireRole**: SUPER_ADMIN

#### POST /api/admin/line/channels/import

チャネルを一括登録する（CSV）。

- **RequireRole**: SUPER_ADMIN

#### POST /api/admin/line/send/lottery-result

抽選結果をLINE送信する。

- **RequireRole**: SUPER_ADMIN, ADMIN
- **リクエスト**: `{ "year": Integer, "month": Integer }`
- **レスポンス**: `{ "sentCount": Integer, "failedCount": Integer, "skippedCount": Integer }`

#### POST /api/admin/line/send/match-pairing

対戦組み合わせをLINE送信する。

- **RequireRole**: SUPER_ADMIN, ADMIN
- **リクエスト**: `{ "sessionId": Long }`
- **レスポンス**: `{ "sentCount": Integer, "failedCount": Integer, "skippedCount": Integer }`

#### GET /api/admin/line/schedule-settings

スケジュール型通知の設定を取得する。

- **RequireRole**: SUPER_ADMIN, ADMIN

#### PUT /api/admin/line/schedule-settings

スケジュール型通知の設定を更新する。

- **RequireRole**: SUPER_ADMIN, ADMIN
- **リクエスト**: `{ "notificationType": String, "enabled": Boolean, "daysBefore": [Integer] }`

---

## 10. スケジューラ設計

### 10.1 新規スケジューラ

#### LineReminderScheduler

- **実行タイミング**: 毎日 AM 8:00（ユーザーが確認しやすい時間帯）
- **処理内容**:
  1. `line_notification_schedule_settings`から参加予定リマインダー・締め切りリマインダーの設定を取得
  2. 各設定の`days_before`配列を参照し、本日が送信対象日かを判定
  3. 対象ユーザーを抽出（LINE連携済み かつ 当該通知ONのユーザー）
  4. 重複送信防止: `line_message_log`を参照し、同一通知種別・同一対象について既に送信済みでないかチェック
  5. Push APIで送信、結果をログに記録

#### LineChannelReclaimScheduler

- **実行タイミング**: 毎日 AM 3:00
- **処理内容**: 第7章の回収フローに準拠

#### LineMonthlyResetScheduler

- **実行タイミング**: 毎月1日 AM 0:00
- **処理内容**: 全チャネルの`monthly_message_count`を0にリセット

---

## 11. 既存機能との統合ポイント

LINE通知は既存のアプリ内通知・Web Pushとは独立したサブシステムとして動作する。以下の既存処理にLINE送信の呼び出しを追加する。

| 統合箇所 | 既存処理 | 追加内容 |
|---------|---------|---------|
| `WaitlistPromotionService.promoteNextWaitlisted()` | アプリ内通知 + Web Push | LINE送信（キャンセル待ち連絡）を追加 |
| `WaitlistPromotionService.expireOffer()` | アプリ内通知 | LINE送信（オファー期限切れ）を追加 |
| 抽選実行画面（フロントエンド） | 抽選実行ボタン | 「LINE通知送信」ボタンを追加 |
| 組み合わせ画面（フロントエンド） | 組み合わせ表示 | 「LINE通知送信」ボタンを追加 |

※ 手動送信型（抽選結果・対戦組み合わせ）は既存処理には組み込まず、独立したAPIエンドポイントとして実装する。管理者が明示的に送信ボタンを押した場合のみ発火する。

---

## 12. 画面設計

### 12.1 新規画面

| 画面名 | パス | 対象ロール | 概要 |
|-------|-----|----------|------|
| LINE通知設定 | /settings/line | 全ロール | LINE連携の有効化/無効化、通知種別ON/OFF |
| LINEチャネル管理 | /admin/line/channels | SUPER_ADMIN | チャネル一覧・登録・状態管理 |
| LINE通知スケジュール設定 | /admin/line/schedule | SUPER_ADMIN, ADMIN | スケジュール型通知の送信日数設定 |

### 12.2 既存画面への追加

| 画面 | 追加内容 |
|------|---------|
| 抽選実行画面 | 「LINE通知送信」ボタン |
| 対戦組み合わせ画面 | 「LINE通知送信」ボタン |
| ナビゲーション / 設定画面 | LINE通知設定へのリンク |

---

## 13. セキュリティ

### 13.1 認証情報の保護

- `channel_secret`および`channel_access_token`はDBに暗号化して保存する
- 暗号化方式: AES-256-GCM（アプリケーションレベル暗号化）
- 暗号化キーは環境変数で管理（`LINE_ENCRYPTION_KEY`）

### 13.2 Webhook署名検証

- すべてのWebhookリクエストに対し、`x-line-signature`ヘッダーの署名を検証する
- 署名はチャネルごとの`channel_secret`を用いたHMAC-SHA256で計算
- 検証失敗時は400を返す

### 13.3 Broadcast APIの使用禁止

- Push API（個別送信）のみを使用し、Broadcast API（全友だち送信）は使用しない
- これにより、チャネル再割り当て時に前ユーザーへ通知が届くリスクを排除する

---

## 14. 設定・環境変数

| 環境変数 | 説明 | 必須 |
|---------|------|-----|
| LINE_ENCRYPTION_KEY | チャネル認証情報の暗号化キー | Yes |

※ 各チャネルのchannel_id / channel_secret / channel_access_tokenはDB管理のため環境変数には含めない。

---

## 15. 通知種別の拡張性

将来的に通知種別が追加される可能性を考慮し、以下の設計方針とする：

- 通知種別はEnum（`LineNotificationType`）で管理する
- `line_notification_preferences`に新カラムを追加するだけで新種別に対応可能
- 送信ロジックは`LineNotificationService`に集約し、種別ごとの送信可否チェック・メッセージ生成を一元管理する
- 新しい通知種別の追加は、Enum追加 + preferencesカラム追加 + メッセージテンプレート追加で完結する

---

## 16. 制約・前提条件

| 項目 | 内容 |
|------|------|
| LINEチャネル数 | 有限。事前に手動で作成・登録が必要 |
| 月間送信上限 | チャネルあたり200通/月（無料プラン） |
| 友だち追加 | ユーザーが自分でLINE上で操作する必要がある |
| Webhook URL | チャネルごとにLINE Developers Consoleで設定が必要 |
| Push API専用 | Broadcast / Multicast APIは使用しない |
| 通知はLINE側で非保証 | ユーザーがブロックした場合、メッセージはサイレントに破棄される（送信数にはカウントされない） |
