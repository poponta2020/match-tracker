---
status: completed
---
# LINE操作確認ダイアログ 要件定義書

## 1. 概要

### 目的
LINEボットから実行可能なすべてのボタン操作に対し、確認ダイアログ（確認用Flex Message）を挟むことで、押し間違いによる意図しない操作を防止する。

### 背景・動機
現在、LINEのFlexメッセージに含まれるボタンを押すと即座に処理が実行される。押し間違いが発生した場合に取り消しができないため、確認ステップを追加して安全性を高める。

## 2. ユーザーストーリー

### 対象ユーザー
LINE連携済みの全ユーザー（プレイヤー・管理者問わず、LINEのボタン操作を行うすべてのユーザー）

### ユーザーの目的
ボタンの押し間違いによる誤操作を防ぎたい。

### 利用シナリオ
1. ユーザーがLINEで受信したFlexメッセージのボタン（例:「承諾する」）を押す
2. 即座に処理は実行されず、確認用Flex Messageがreplyで返される
3. 確認Flexには操作内容の詳細（日付・会場名・試合番号）と「確定」「キャンセル」ボタンが表示される
4. 「確定」を押すと本来の処理が実行される
5. 「キャンセル」を押すと「操作をキャンセルしました」とテキストで返され、処理は実行されない

## 3. 機能要件

### 3.1 対象操作一覧

| # | 操作 | 現在のアクション | 確認文言 |
|---|------|-----------------|---------|
| 1 | 繰り上げ参加の承諾 | `waitlist_accept` | 「{M}月{D}日（{会場名}）{N}試合目の繰り上げ参加を承諾します。よろしいですか？」 |
| 2 | 繰り上げ参加の辞退 | `waitlist_decline` | 「{M}月{D}日（{会場名}）{N}試合目の繰り上げ参加を辞退します。よろしいですか？」 |
| 3 | キャンセル待ち一括辞退 | `waitlist_decline_session` | 「{M}月{D}日（{会場名}）のキャンセル待ちをすべて辞退します。よろしいですか？」 |
| 4 | 当日参加 | `same_day_join` | 「{M}月{D}日（{会場名}）{N}試合目に当日参加します。よろしいですか？」 |

### 3.2 確認フロー

```
ユーザーがボタン押下（例: action=waitlist_accept&participantId=123）
  ↓
サーバー: 処理を実行せず、確認トークンを発行（DB保存）
  ↓
サーバー: 確認用Flex Messageをreplyで返す
  - 操作内容の詳細表示（DBから日付・会場名・試合番号を取得）
  - 「確定」ボタン: action=confirm_waitlist_accept&participantId=123&token=xxx
  - 「キャンセル」ボタン: action=cancel_confirm&token=xxx
  ↓
【確定を押した場合】
  サーバー: トークン検証（存在・未使用・有効期限内）→ 本来の処理を実行 → 結果をreply
【キャンセルを押した場合】
  サーバー: 「操作をキャンセルしました」とreply
```

### 3.3 確認用Flex Messageの仕様

- **形式**: Flex Message（Bubble）
- **ボタン**: 「確定」「キャンセル」の2ボタン
- **表示内容**: 操作対象の詳細情報（日付・会場名・試合番号）+ 確認文言
- **情報取得**: postbackデータのIDからDBを参照して日付・会場名・試合番号を取得

### 3.4 確認トークンの仕様

| 項目 | 仕様 |
|------|------|
| 形式 | UUID（36文字） |
| 有効期限 | 発行から5分 |
| 冪等性 | `used_at` で管理。使用済みトークンは再使用不可 |
| クリーンアップ | 新トークン発行時に期限切れトークンをすべてDELETE |

### 3.5 エラーケース

| ケース | 応答メッセージ |
|--------|--------------|
| トークンが見つからない | 「この確認は期限切れです。もう一度操作してください。」 |
| トークンが使用済み | 「この確認は期限切れです。もう一度操作してください。」 |
| トークンが有効期限切れ | 「この確認は期限切れです。もう一度操作してください。」 |
| 元の操作自体が期限切れ（例: 繰り上げオファー期限超過） | 既存のエラーハンドリングに従う |
| キャンセル押下 | 「操作をキャンセルしました」 |

### 3.6 ビジネスルール

- 確認トークンの有効期限（5分）内であっても、元の操作の期限（例: 繰り上げオファーの期限）が切れていれば処理は失敗する（既存のバリデーションが機能する）
- 確認Flexの「確定」ボタンを複数回押しても、2回目以降は「この確認は期限切れです。もう一度操作してください。」と返る（冪等性保証）
- 「もう一度操作してください」= 元のFlexのボタンを再度押して確認フローをやり直す

## 4. 技術設計

### 4.1 API設計

API変更なし。すべてLINE Webhook内のpostback処理の変更。

### 4.2 DB設計

#### 新規テーブル: `line_confirmation_token`

| カラム | 型 | 制約 | 説明 |
|--------|---|------|------|
| `id` | BIGSERIAL | PK | |
| `token` | VARCHAR(64) | UNIQUE NOT NULL | UUID |
| `action` | VARCHAR(50) | NOT NULL | 元のアクション名（`waitlist_accept`等） |
| `params` | TEXT | NOT NULL | 元のpostbackパラメータ（JSON形式） |
| `player_id` | BIGINT | NOT NULL | 操作者のプレイヤーID |
| `created_at` | TIMESTAMP | NOT NULL | 作成日時 |
| `expires_at` | TIMESTAMP | NOT NULL | 有効期限（created_at + 5分） |
| `used_at` | TIMESTAMP | NULL | 使用日時（NULLなら未使用） |

### 4.3 バックエンド設計

#### 新規クラス

| クラス | パッケージ | 役割 |
|--------|-----------|------|
| `LineConfirmationToken` | `entity` | テーブルエンティティ |
| `LineConfirmationTokenRepository` | `repository` | CRUD + 期限切れトークン削除 |
| `LineConfirmationService` | `service` | トークン発行・検証・消費・クリーンアップ |

#### 変更クラス

| クラス | 変更内容 |
|--------|---------|
| `LineWebhookController` | `handlePostback()` を変更: 元アクション受信時→確認Flex返却、`confirm_*` 受信時→本来の処理実行、`cancel_confirm` 受信時→キャンセル応答 |
| `LineNotificationService` | 確認用Flex Message構築メソッドを追加（`buildConfirmationFlex()`） |
| `LineMessagingService` | Flex Messageをreplyで送信するメソッドを追加（`sendReplyFlexMessage()`） |

#### 処理フロー

```
handlePostback()
  ├── action が waitlist_accept / waitlist_decline / waitlist_decline_session / same_day_join の場合
  │     ├── DBからセッション情報（日付・会場名・試合番号）を取得
  │     ├── LineConfirmationService.createToken(action, params, playerId)
  │     │     └── 期限切れトークンを全削除 → 新トークン発行・DB保存
  │     ├── LineNotificationService.buildConfirmationFlex(action, sessionInfo, token)
  │     └── sendReplyFlexMessage() で確認Flexを返却
  │
  ├── action が confirm_* の場合
  │     ├── LineConfirmationService.consumeToken(token, playerId)
  │     │     ├── トークン存在チェック
  │     │     ├── used_at NULLチェック（冪等性）
  │     │     ├── expires_at チェック
  │     │     ├── player_id 一致チェック
  │     │     └── used_at を現在時刻で更新
  │     ├── 検証OK → 元の処理を実行（既存ロジックをそのまま呼び出し）
  │     └── 検証NG → 「この確認は期限切れです。もう一度操作してください。」
  │
  └── action が cancel_confirm の場合
        └── 「操作をキャンセルしました」をreply
```

### 4.4 確認アクション名マッピング

| 元アクション | 確認実行アクション | キャンセルアクション |
|---|---|---|
| `waitlist_accept` | `confirm_waitlist_accept` | `cancel_confirm` |
| `waitlist_decline` | `confirm_waitlist_decline` | `cancel_confirm` |
| `waitlist_decline_session` | `confirm_waitlist_decline_session` | `cancel_confirm` |
| `same_day_join` | `confirm_same_day_join` | `cancel_confirm` |

## 5. 影響範囲

### 変更が必要な既存ファイル

| ファイル | 変更内容 |
|---------|---------|
| `LineWebhookController.java` | postback処理の分岐追加 |
| `LineNotificationService.java` | 確認用Flex構築メソッド追加 |
| `LineMessagingService.java` | Flex reply送信メソッド追加 |

### 新規ファイル

| ファイル | 内容 |
|---------|------|
| `LineConfirmationToken.java` | エンティティ |
| `LineConfirmationTokenRepository.java` | リポジトリ |
| `LineConfirmationService.java` | サービス |
| `V*.sql`（マイグレーション） | テーブル作成SQL |

### 既存機能への影響

- **LINE操作**: すべてのボタン操作に確認ステップが追加される（意図通り）
- **既に配信済みのFlexメッセージ**: 古いFlexのボタンを押しても確認Flexが返る（正常動作）
- **Web画面・API**: 影響なし（LINE postback経由のみの変更）
- **スケジューラ**: 影響なし
- **通知配信**: 影響なし（Flex構築・配信処理は変更なし）

## 6. 設計判断の根拠

| 判断 | 理由 |
|------|------|
| 確認方式にFlex Messageを採用 | LINE標準のConfirm Templateより表現力が高く、操作内容の詳細を表示できる |
| DB参照で日付・会場名を取得 | postbackの300バイト制限を気にせず済み、会場名変更時も最新情報を表示できる |
| DBテーブルでトークン管理 | `used_at` フラグで確実に冪等性を担保できる。JWT方式は冪等性保証に工夫が必要 |
| 有効期限5分 | 確認操作に十分な時間。元の操作の期限チェックは既存ロジックで別途担保 |
| インラインクリーンアップ | スケジューラ追加不要でリソース節約。アプリのRAM使用量を増やさない |
| エラーメッセージを統一 | トークン不在・使用済み・期限切れを区別せず同じメッセージにすることで、セキュリティ上の情報漏洩を防ぎつつUXもシンプル |
