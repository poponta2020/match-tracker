---
status: completed
---
# LINE リッチメニュー 要件定義書

## 1. 概要
- **目的**: PLAYERチャネルにリッチメニューを設定し、ユーザーがLINEトーク画面から主要機能に素早くアクセスできるようにする
- **背景**: 現在、情報確認や操作のためにはWebアプリへの遷移が必要。LINEトーク画面にメニューを設置することで、よく使う機能へのアクセスを簡便化する
- **対象チャネル**: PLAYERチャネル（約50チャネル）に一括設定。ADMINチャネルは今回スコープ外

## 2. ユーザーストーリー
- **対象ユーザー**: PLAYERチャネルにLINK済みの全プレイヤー
- **ユーザーの目的**: LINEトーク画面から、キャンセル待ち状況・今日の参加者・空き枠への参加申込などを素早く行いたい
- **利用シナリオ**:
  - 抽選後に「自分のキャンセル待ち何番？」と確認する
  - 練習日当日に「今日のメンバー誰だっけ？」と確認する
  - 当日に空きが出た時、LINEから直接参加申込する
  - アプリのホーム画面や通知設定に素早く遷移する

### メニュー項目（5つ）
1. **キャンセル待ち状況確認** — 自分のキャンセル待ちエントリ一覧を表示
2. **今日の参加者表示** — 当日の練習参加メンバー一覧を表示
3. **当日参加申込** — 当日、空きがある試合に参加申込
4. **アプリへ** — Webアプリのホーム画面に遷移
5. **通知設定** — Webアプリの通知設定画面に遷移

## 3. 機能要件

### 3.1 リッチメニュー共通仕様
- リッチメニューは静的な画像であり、ボタンの見た目を動的に変更することはできない
- 各ボタン押下時の状態判定はサーバー側で行い、条件を満たさない場合はテキストメッセージで通知する
- 約50のPLAYERチャネルに一括で同一リッチメニューを設定する
- リッチメニュー画像はユーザーが別途用意する（2500x1686px または 2500x843px）

### 3.2 キャンセル待ち状況確認
- **トリガー**: リッチメニューのボタン押下（postback）
- **表示内容**: 自分のWAITLISTED・OFFEREDエントリ一覧をFlex Messageで表示
  - 練習日・会場名
  - 試合番号
  - キャンセル待ち順位（例: キャンセル待ち 2番）
  - OFFEREDの場合は回答期限を表示
- **情報表示のみ**: OFFEREDのエントリに対して「参加する」「辞退する」ボタンはつけない（繰り上げオファーの通知Flexで対応済みのため）
- **0件の場合**: 「現在キャンセル待ちはありません」とテキストメッセージで返信

### 3.3 今日の参加者表示
- **トリガー**: リッチメニューのボタン押下（postback）
- **表示内容**: 当日の練習のWON（参加確定）メンバー一覧をFlex Messageで表示
  - 既存の `buildSameDayConfirmationFlex()` と同じ形式（段位順・試合別・2列表示）
- **自分がWONでない日でも閲覧可能**
- **当日に練習がない場合**: 「今日の練習はありません」とテキストメッセージで返信

### 3.4 当日参加申込
- **トリガー**: リッチメニューのボタン押下（postback）
- **申込可能条件**:

| 条件 | 申込可否 |
|------|---------|
| 当日・空きあり・キャンセル待ちなし | **可**（時間制限なし） |
| 当日・空きあり・キャンセル待ちあり | 12時（JST）以降のみ可 |
| 当日・空きなし | 不可 |
| 当日以外 | 不可 |

- **申込可能な場合**: 空きがある試合一覧をFlex Messageで表示し、各試合に「参加する」ボタンをつける（既存の `same_day_join` postbackアクションを流用）
- **申込不可の場合**: 「現在参加申込できる試合はありません」とテキストメッセージで返信

### 3.5 アプリへ
- **トリガー**: リッチメニューのボタン押下（URI action）
- **遷移先**: `https://match-tracker-eight-gilt.vercel.app/`

### 3.6 通知設定
- **トリガー**: リッチメニューのボタン押下（URI action）
- **遷移先**: `https://match-tracker-eight-gilt.vercel.app/settings/notifications`

## 4. 技術設計

### 4.1 API設計

#### リッチメニュー一括設定API（新規）
- **エンドポイント**: `POST /api/admin/line/rich-menu/setup`
- **認証**: `@RequireRole(Role.SUPER_ADMIN)`
- **リクエスト**: `multipart/form-data`
  - `image`: リッチメニュー画像ファイル（PNG/JPEG、2500x1686px or 2500x843px）
- **処理フロー**:
  1. リッチメニューJSON定義を構築（5ボタンのレイアウト + アクション定義）
  2. 全PLAYERチャネルを取得（`findAllByChannelType(PLAYER)`）
  3. チャネルごとに以下を実行:
     a. `POST /v2/bot/richmenu` — リッチメニュー作成
     b. `POST /v2/bot/richmenu/{richMenuId}/content` — 画像アップロード
     c. `POST /v2/bot/user/all/richmenu/{richMenuId}` — デフォルトメニューに設定
  4. 結果を返却（成功数・失敗数・失敗チャネル一覧）
- **レスポンス**: `{ "successCount": 48, "failureCount": 2, "failures": [...] }`

#### リッチメニューのアクション定義
| ボタン | アクション種別 | データ / URL |
|--------|--------------|-------------|
| キャンセル待ち状況確認 | postback | `action=check_waitlist_status` |
| 今日の参加者表示 | postback | `action=check_today_participants` |
| 当日参加申込 | postback | `action=check_same_day_join` |
| アプリへ | uri | `https://match-tracker-eight-gilt.vercel.app/` |
| 通知設定 | uri | `https://match-tracker-eight-gilt.vercel.app/settings/notifications` |

### 4.2 DB設計
- **新規テーブル・カラムの追加なし**
- 既存テーブルのみで対応可能

### 4.3 バックエンド設計

#### LineMessagingService（既存ファイルに追加）
Rich Menu API呼び出し用メソッドを追加:
- `createRichMenu(String channelAccessToken, Map<String, Object> richMenuJson)` → richMenuId
- `uploadRichMenuImage(String channelAccessToken, String richMenuId, byte[] imageData, String contentType)` → boolean
- `setDefaultRichMenu(String channelAccessToken, String richMenuId)` → boolean

#### LineAdminController（既存ファイルに追加）
- `POST /api/admin/line/rich-menu/setup` エンドポイント追加

#### LineWebhookController（既存ファイルに追加）
`handlePostback()` に照会型アクションの分岐を追加:
```
case "check_waitlist_status" → handleCheckWaitlistStatus()
case "check_today_participants" → handleCheckTodayParticipants()
case "check_same_day_join" → handleCheckSameDayJoin()
```
これらは確認ダイアログ不要（`CONFIRMABLE_ACTIONS` に含めない）

#### 各ハンドラーの処理フロー

**handleCheckWaitlistStatus:**
1. playerId から WAITLISTED + OFFERED のエントリを取得（既存の waitlist-status API ロジック流用）
2. 0件 → テキスト返信「現在キャンセル待ちはありません」
3. 1件以上 → Flex Message で一覧表示（新規 `buildWaitlistStatusFlex()` メソッド）

**handleCheckTodayParticipants:**
1. 当日のセッションを取得
2. セッションなし → テキスト返信「今日の練習はありません」
3. セッションあり → 各セッションのWONメンバーを取得し Flex Message 表示（既存 `buildSameDayConfirmationFlex()` を流用）

**handleCheckSameDayJoin:**
1. 当日のセッションを取得
2. セッションなし → テキスト返信「現在参加申込できる試合はありません」
3. セッションあり → 試合ごとに空き判定:
   - `vacancy = capacity - wonCount`
   - `hasWaitlist = count(WAITLISTED in this session's match) > 0`
   - 空きあり かつ (waitlistなし OR 12時以降) → 申込可能
4. 申込可能な試合が0 → テキスト返信「現在参加申込できる試合はありません」
5. 申込可能な試合あり → Flex Message で試合一覧 + 「参加する」ボタン表示（新規 `buildSameDayJoinFlex()` メソッド）

#### LineNotificationService（既存ファイルに追加）
新規Flex Messageビルダー:
- `buildWaitlistStatusFlex()` — キャンセル待ち状況一覧
- `buildSameDayJoinFlex()` — 当日参加可能試合一覧（各試合に `same_day_join` postbackボタン付き）

### 4.4 フロントエンド設計
- **変更なし** — リッチメニュー設定は管理者APIのcurl実行またはPostmanで行う。管理画面UIは今回スコープ外

## 5. 影響範囲

### 変更対象ファイル
| 変更対象 | 変更内容 | 既存機能への影響 |
|---------|---------|----------------|
| `LineMessagingService.java` | Rich Menu API メソッド3つ追加 | なし（新規メソッドのみ） |
| `LineAdminController.java` | `/rich-menu/setup` エンドポイント追加 | なし（新規エンドポイントのみ） |
| `LineWebhookController.java` | postback分岐に照会アクション3つ追加 | なし（既存のpostback処理に変更なし） |
| `LineNotificationService.java` | Flex Messageビルダー2つ追加 | なし（新規メソッドのみ） |

### 既存機能への影響
- **既存のpostback処理**: 変更なし。新アクション名は既存と重複しない
- **確認ダイアログ**: 照会型アクションは `CONFIRMABLE_ACTIONS` に含めないので影響なし
- **DBスキーマ**: 変更なし
- **フロントエンド**: 変更なし

### 注意点
- **当日参加申込の条件判定**: `handleCheckSameDayJoin` に「キャンセル待ちの有無で12時制限を切り替える」という新しいロジックが入る。これは既存の `handleSameDayJoin`（実際の参加処理）には影響しない。あくまでリッチメニューからの「申込可能かどうかの表示判定」のみ

## 6. 設計判断の根拠

### リッチメニューの一括設定方式
- **判断**: 管理者APIエンドポイント方式を採用
- **理由**: 今後チャネルが追加された際にも再利用可能。使い捨てスクリプトでは再実行時の手間がかかる

### 照会型アクションの確認ダイアログ
- **判断**: 確認ダイアログなしで即座に結果を返す
- **理由**: 読み取り専用の照会操作であり、副作用がないため不要

### キャンセル連絡ボタンの除外
- **判断**: リッチメニューにキャンセルボタンを設置しない
- **理由**: キャンセルへの心理的ハードルが下がり、気軽にキャンセルする人が増えるリスクがある

### OFFEREDエントリへのアクションボタン
- **判断**: キャンセル待ち状況確認では情報表示のみとし、「参加する」「辞退する」ボタンはつけない
- **理由**: 繰り上げオファーの通知Flex Messageで既にボタンが提供されており、重複を避ける

### フロントエンド管理画面の見送り
- **判断**: リッチメニュー設定のためのWeb管理画面UIは今回作成しない
- **理由**: 一括設定は初回 + チャネル追加時のみで頻度が低く、curl/Postmanで十分対応可能
