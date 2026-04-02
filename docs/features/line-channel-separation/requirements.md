---
status: completed
---
# LINE チャネル用途分離 要件定義書（ドラフト）

## 1. 概要
- **目的:** 管理者向け通知と選手向け通知を別々のLINEボットアカウントから送信することで、通知の視認性を向上させる
- **背景:** 管理者かつ選手のユーザーは、管理者通知と選手通知が同一のLINEトークに混在し、見づらい状態になっている

## 2. ユーザーストーリー

### 対象ユーザー
- **一般選手（PLAYER）:** 選手用LINE 1つのみ登録（現行通り）
- **管理者（ADMIN / SUPER_ADMIN）:** 管理者用LINEと選手用LINEの2つを登録可能。どちらか一方のみの登録も許容

### ユーザーの目的
- 管理者は、管理業務に関する通知と練習参加者としての通知を別々のLINEトークで受け取りたい
- 通知の種類に応じて適切なトークを確認するだけで済むようにしたい

### 利用シナリオ
1. **管理者が2つのLINEを登録する場合:**
   - 設定画面に「選手用LINE」と「管理者用LINE」の2つのセクションが表示される
   - それぞれ独立に有効化・友達登録・コードリンクを行う（現行フローを2回実施）
   - 選手用LINEには選手向け通知、管理者用LINEには管理者向け通知が届く

2. **管理者が選手用LINEのみ登録する場合:**
   - 選手向け通知のみ受信。管理者通知は受信しない

3. **管理者が管理者用LINEのみ登録する場合:**
   - 管理者通知のみ受信。選手向け通知は受信しない

4. **一般選手の場合:**
   - 現行通り、選手用LINEのみ表示・登録可能。変更なし

### 通知の振り分け
| 通知種別 | 送信先 |
|---------|--------|
| 抽選結果（LOTTERY_RESULT） | 選手用LINE |
| キャンセル待ち繰上げ（WAITLIST_OFFER） | 選手用LINE |
| 繰上げ期限切れ（OFFER_EXPIRED） | 選手用LINE |
| 対戦組合せ（MATCH_PAIRING） | 選手用LINE |
| 練習リマインダー（PRACTICE_REMINDER） | 選手用LINE |
| 締切リマインダー（DEADLINE_REMINDER） | 選手用LINE |
| 当日確認（SAME_DAY_CONFIRMATION） | 選手用LINE |
| 当日キャンセル（SAME_DAY_CANCEL） | 選手用LINE |
| 当日空き通知（SAME_DAY_VACANCY） | 選手用LINE |
| キャンセル待ち順位変動（ADMIN_WAITLIST_UPDATE） | 管理者用LINE |
| 当日確認まとめ（ADMIN_SAME_DAY_CONFIRMATION） | 管理者用LINE |

### 設計方針
- **案A採用:** LINEチャネルプールを「管理者用（ADMIN）」と「選手用（PLAYER）」に分離
- `line_channels` テーブルに `channel_type` カラムを追加してチャネルの用途を区別する

## 3. 機能要件

### 3.1 画面仕様

#### LINE通知設定画面（LineSettings）

**一般選手（PLAYER）の場合:**
- 現行と同一。変更なし

**管理者（ADMIN / SUPER_ADMIN）の場合:**
- 2つのセクションを表示：
  ```
  LINE通知設定
  ├── 選手用LINE
  │   ├── [有効にする] / ステータス表示
  │   ├── QRコード / 友達追加URL
  │   ├── ワンタイムコード
  │   └── 通知設定（選手向け通知種別のON/OFF）
  │
  └── 管理者用LINE
      ├── [有効にする] / ステータス表示
      ├── QRコード / 友達追加URL
      ├── ワンタイムコード
      └── 通知設定（管理者向け通知種別のON/OFF）
  ```
- 各セクションは独立して有効化・無効化できる
- どちらか一方のみの登録も可

#### LINEチャネル管理画面（LineChannelAdmin）

- タブで「選手用」「管理者用」を切替（デフォルトは「選手用」タブ）
  ```
  LINEチャネル管理
  [選手用] [管理者用]   ← タブ切替

  チャネル一覧（選択中のタブに応じた一覧）
  | チャネル名 | ステータス | 月間送信数 | 割当先 |

  [+ チャネル追加]  ← 追加時は現在選択中のタブの用途が自動設定
  ```
- チャネル追加時、選択中のタブの用途（PLAYER/ADMIN）が自動的にセットされる
- 一覧は選択中のタブの用途でフィルタリングされる

### 3.2 ビジネスルール

- 通知送信時、通知種別に応じて適切な用途のチャネルを使い分ける
  - `ADMIN_` プレフィックスの通知種別 → 管理者用チャネル経由で送信
  - それ以外 → 選手用チャネル経由で送信
- 管理者用チャネルが未リンクの管理者には、管理者向け通知は送信しない（スキップ）
- 選手用チャネルが未リンクの管理者には、選手向け通知は送信しない（スキップ）
- Webhookの処理フローはチャネル用途によらず共通（ウェルカムメッセージ・コードリンクの仕組みは同一）
- 既存チャネルはすべて `channel_type = PLAYER` としてマイグレーション
- 通知プリファレンスのテーブル構造は現行のまま。送信時にチャネル種別に応じて参照するフィールドを切り替える

### 3.3 エラーケース
- 管理者用チャネルプールが空（AVAILABLE な管理者用チャネルがない）の場合 → 「管理者用のLINEチャネルが不足しています」エラー
- 一般選手が管理者用LINEを有効化しようとした場合 → Service層でロールチェックし拒否

## 4. 技術設計

### 4.1 API設計

#### ユーザー向けAPI（LineUserController）

既存エンドポイントをパスパラメータ方式に拡張。

| メソッド | エンドポイント | 説明 | 備考 |
|---------|--------------|------|------|
| POST | `/api/line/{channelType}/enable` | LINE通知有効化 | channelType: PLAYER / ADMIN |
| DELETE | `/api/line/{channelType}/disable` | LINE通知無効化 | |
| POST | `/api/line/{channelType}/reissue-code` | ワンタイムコード再発行 | |
| GET | `/api/line/{channelType}/status` | リンクステータス取得 | |
| GET | `/api/line/preferences` | 通知プリファレンス取得 | チャネル種別不要（1レコードに全フラグ） |
| PUT | `/api/line/preferences` | 通知プリファレンス更新 | チャネル種別不要 |

- `channelType=ADMIN` の場合、Service層でユーザーのロールがADMIN/SUPER_ADMINであることをチェック
- `channelType=PLAYER` の場合、ロールチェック不要（全ユーザー利用可）

#### 管理者向けAPI（LineAdminController）

| メソッド | エンドポイント | 説明 | 備考 |
|---------|--------------|------|------|
| GET | `/api/admin/line/channels` | チャネル一覧取得 | クエリパラメータ `?channelType=PLAYER` / `ADMIN` でフィルタ。未指定時は全件 |
| POST | `/api/admin/line/channels` | チャネル作成 | リクエストボディに `channelType` フィールド追加（デフォルト: PLAYER） |
| POST | `/api/admin/line/channels/import` | CSVインポート | `channelType` 未指定時は PLAYER |
| POST | `/api/admin/line/channels/migrate-webhook-urls` | Webhook URL一括移行 | SUPER_ADMIN限定。全チャネルのWebhook URLをLINEチャネルIDベースに移行 |

#### Webhook API（LineWebhookController）

- `POST /api/line/webhook/{lineChannelId}` — パスパラメータはLINEチャネルID（DB内部IDではない）
- lineChannelId からチャネルを特定し、そのチャネルの `channel_type` に応じた処理は既存フローのまま
- postback 処理で `lineUserId` からアサインメントを特定する箇所は、`lineChannelId` も条件に加えて正確に特定する

### 4.2 DB設計

#### `line_channels` テーブル — カラム追加

| カラム名 | 型 | デフォルト | 説明 |
|---------|---|---------|------|
| `channel_type` | VARCHAR(10) | 'PLAYER' | チャネル用途。'PLAYER' or 'ADMIN' |

- インデックス追加: `idx_line_channel_type` on `(channel_type)`
- 既存レコードのマイグレーション: 全レコードに `channel_type = 'PLAYER'` を設定

#### `line_channel_assignments` テーブル — カラム追加

| カラム名 | 型 | デフォルト | 説明 |
|---------|---|---------|------|
| `channel_type` | VARCHAR(10) | 'PLAYER' | チャネル用途（非正規化）。'PLAYER' or 'ADMIN' |

- インデックス追加: `idx_lca_player_type` on `(player_id, channel_type)`
- 既存レコードのマイグレーション: 全レコードに `channel_type = 'PLAYER'` を設定
- 1プレイヤーにつき、用途ごとにアクティブなアサインメントは最大1件

### 4.3 フロントエンド設計

#### LineSettings.jsx の変更
- AuthContext からユーザーロールを取得
- ADMIN/SUPER_ADMIN の場合、「選手用LINE」「管理者用LINE」の2セクションを描画
- 各セクションは既存のLINE設定UIをコンポーネント化して再利用
- API呼び出し時に `channelType` パスパラメータを付与
- 通知プリファレンスは1つのAPIで取得し、セクションに応じて表示するフラグを振り分け
  - 選手用セクション: lotteryResult, waitlistOffer, offerExpired, matchPairing, practiceReminder, deadlineReminder, sameDayConfirmation, sameDayCancel, sameDayVacancy
  - 管理者用セクション: adminWaitlistUpdate, adminSameDayConfirmation

#### LineChannelAdmin.jsx の変更
- タブUI追加（「選手用」「管理者用」、デフォルト: 選手用）
- チャネル一覧取得時に `?channelType=<選択中タブ>` クエリパラメータを付与
- チャネル追加ダイアログで `channelType` を選択中タブの値で自動セット

#### line.js（APIクライアント）の変更
- `enableLine(channelType)`, `disableLine(channelType)`, `reissueCode(channelType)`, `getLineStatus(channelType)` にパラメータ追加
- `getChannels(channelType)` にクエリパラメータ対応追加
- `createChannel(data)` のリクエストボディに `channelType` フィールド追加

### 4.4 バックエンド設計

#### Enum 追加
- `ChannelType { PLAYER, ADMIN }` — チャネル用途を表す列挙型

#### Entity 変更
- `LineChannel` — `channelType` フィールド追加（`ChannelType` enum）
- `LineChannelAssignment` — `channelType` フィールド追加（同 enum）

#### Repository 変更
- `LineChannelRepository`
  - `findByStatusAndChannelType(ChannelStatus, ChannelType)` 追加 — 用途別の空きチャネル検索
  - `findAllByChannelType(ChannelType)` 追加 — 用途別の一覧取得
- `LineChannelAssignmentRepository`
  - `findByPlayerIdAndChannelTypeAndStatusIn(Long, ChannelType, List<AssignmentStatus>)` 追加 — 用途別のアクティブアサインメント検索
  - `findByLineChannelIdAndStatus(Long, AssignmentStatus)` — 既存のまま（channelId で特定済み）
  - 既存の `findActiveByPlayerId()` は LIMIT 1 を除去し、結果が複数返る前提に変更

#### Service 変更
- `LineChannelService`
  - `assignChannel(Long playerId, ChannelType channelType)` — channelType に応じたプールから割当。ADMIN の場合はロールチェック実施
  - `releaseChannel(Long playerId, ChannelType channelType)` — channelType 指定でリリース
  - `linkChannel(Long channelId, String lineUserId)` — 変更なし（channelId で特定済み）
  - `getAllChannels(ChannelType channelType)` — 用途別フィルタ対応（null 時は全件）
- `LineNotificationService`
  - `resolveChannel(Long playerId, LineNotificationType, String)` — 通知種別から送信先チャネル種別を判定し、該当チャネル種別のアサインメントを取得
  - `LineNotificationType` enum に `getRequiredChannelType()` メソッド追加 — ADMIN_ プレフィックスなら ADMIN、それ以外は PLAYER を返す
- `LineLinkingService`
  - `reissueCode(Long playerId, ChannelType channelType)` — channelType 指定で対象アサインメントを特定

#### Controller 変更
- `LineUserController` — 各エンドポイントに `@PathVariable ChannelType channelType` 追加
- `LineAdminController` — チャネル一覧に `@RequestParam(required = false) ChannelType channelType` 追加、作成に `channelType` フィールド対応
- `LineWebhookController` — postback 処理で `lineUserId` + `channelId` でアサインメントを特定するように修正

#### DTO 変更
- `LineChannelDto` — `channelType` フィールド追加
- `LineChannelCreateRequest` — `channelType` フィールド追加（デフォルト: PLAYER）
- `LineEnableResponse` — 変更なし（レスポンス内容は同一）
- `LineStatusResponse` — 変更なし

## 5. 影響範囲

### 5.1 変更が必要な既存ファイル

#### バックエンド（優先度順）

| ファイル | 変更内容 | 優先度 |
|---------|---------|--------|
| `LineChannel.java` (Entity) | `channelType` フィールド追加 | CRITICAL |
| `LineChannelAssignment.java` (Entity) | `channelType` フィールド追加 | CRITICAL |
| `LineChannelRepository.java` | 用途別検索メソッド追加 | CRITICAL |
| `LineChannelAssignmentRepository.java` | 用途別検索メソッド追加、LIMIT 1 除去 | CRITICAL |
| `LineChannelService.java` | `assignChannel`, `releaseChannel`, `getAllChannels` に channelType 対応 | CRITICAL |
| `LineNotificationService.java` | `resolveChannel()` を通知種別→チャネル種別のルーティングに対応 | CRITICAL |
| `LineLinkingService.java` | `reissueCode()` に channelType 対応 | HIGH |
| `LineUserController.java` | エンドポイントパスに `{channelType}` 追加 | HIGH |
| `LineAdminController.java` | チャネル一覧フィルタ、作成時 channelType 対応 | HIGH |
| `LineWebhookController.java` | postback の lineUserId + channelId でアサインメント特定 | MEDIUM |
| `LineReminderScheduler.java` | 通知送信が `resolveChannel()` 経由なので自動対応 | LOW |
| `LineChannelReclaimScheduler.java` | 複数アサインメントの個別回収に対応 | MEDIUM |
| `LineMonthlyResetScheduler.java` | 変更不要 | NONE |
| `LineChannelDto.java` | `channelType` フィールド追加 | HIGH |
| `LineChannelCreateRequest.java` | `channelType` フィールド追加 | HIGH |
| `LineConfirmationServiceTest.java` | channelType シナリオのテスト追加 | MEDIUM |

#### フロントエンド

| ファイル | 変更内容 | 優先度 |
|---------|---------|--------|
| `line.js` (APIクライアント) | 全ユーザー向けAPI関数に channelType パラメータ追加 | HIGH |
| `LineSettings.jsx` | ロール判定、2セクション表示、API呼び出し変更 | HIGH |
| `LineChannelAdmin.jsx` | タブUI追加、フィルタリング、チャネル作成時の channelType | HIGH |

#### データベース

| 変更 | 内容 |
|------|------|
| マイグレーションSQL | `line_channels` に `channel_type` カラム追加（デフォルト: PLAYER） |
| マイグレーションSQL | `line_channel_assignments` に `channel_type` カラム追加（デフォルト: PLAYER） |
| インデックス追加 | `idx_line_channel_type`, `idx_lca_player_type` |

### 5.2 既存機能への影響

| 影響箇所 | 影響内容 | 対策 |
|---------|---------|------|
| 既存の選手のLINE通知 | API パスが変わる | フロントエンドを同時にデプロイ。既存アサインメントは `channel_type = PLAYER` にマイグレーション |
| 管理者の既存アサインメント | 選手用として扱われる | リリース後、管理者は管理者用LINEを別途有効化する必要がある |
| 通知送信処理 | `resolveChannel()` のロジック変更 | 通知種別→チャネル種別のマッピングを追加。既存の選手向け通知は `PLAYER` チャネルにルーティングされるため動作変更なし |
| Webhook postback | lineUserId だけでなく channelId も条件に加える | 既存の PLAYER チャネルの postback は引き続き動作 |
| チャネル回収 | 管理者は2つのアサインメントを持ちうる | 各アサインメントを個別に回収対象として扱う |
| CSVインポート | channelType 未指定時は PLAYER | 既存のCSVフォーマットとの後方互換性を維持 |

### 5.3 互換性に関する注意

- **APIの破壊的変更:** ユーザー向けエンドポイントのパスが変わるため、フロントエンドとバックエンドは同時にデプロイする必要がある
- **DBスキーマ変更:** カラム追加（デフォルト値あり）のみのため、既存データへの影響なし
- **通知プリファレンス:** テーブル構造は変更なし。フロントエンドの表示振り分けのみ

## 6. 設計判断の根拠

| 判断 | 理由 |
|------|------|
| チャネルプール分離（案A）を採用 | LINEボットアカウント自体が用途別に分かれるため、チャネル側に用途を持たせるのが自然。管理者は少数なのでチャネル利用効率の差は問題にならない |
| `line_channel_assignments` に `channel_type` を非正規化 | 通知送信処理は頻繁に走るため、JOIN なしで高速に検索できる方が有利 |
| パスパラメータ方式（`/api/line/{channelType}/...`）を採用 | RESTful で自然な設計 |
| チャネル一覧APIはクエリパラメータ方式 | 既存エンドポイントの拡張で済み、パラメータ未指定時は全件返却で後方互換を維持 |
| 通知プリファレンスAPIはチャネル種別を分けない | プリファレンスは1レコードに全フラグが入っており、フロントエンドで表示を振り分ける方がシンプル |
| ロールチェックはService層で実施 | LineUserController に `@RequireRole` を部分的に付けるのは不自然。Service層で channelType=ADMIN の場合のみチェック |
| Webhook は channelId + lineUserId でアサインメント特定 | 同一ユーザーが管理者用・選手用の両チャネルを登録する場合、lineUserId だけでは一意にならないため |
| チャネル回収はアサインメント単位で個別に実施 | ユーザー単位で一括回収すると、片方だけ活用しているケースで不便 |
