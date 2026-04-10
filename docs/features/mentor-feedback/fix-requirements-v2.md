---
status: completed
audit_source: ユーザー直接リクエスト
selected_items: [1]
---
# メンターフィードバック LINE通知バッチ送信 改修要件定義書

## 1. 改修概要

- **対象機能**: メンターフィードバック コメントスレッドのLINE通知（`MatchCommentService` → `LineNotificationService`）
- **改修の背景**: コメントを見やすさのために複数に分けて書く運用をしているが、現状はコメント投稿のたびに即座にLINE通知が送信される。コメントをまだ書き途中のタイミングで相手に通知が飛んでしまい、全て書き終えてからまとめて通知したいというニーズがある。
- **改修スコープ**: バックエンド（通知ロジック変更、API追加、DB変更）+ フロントエンド（送信ボタン追加）

## 2. 改修内容

### 2.1 即時LINE通知の廃止
- **現状の問題**: `MatchCommentService.createComment()`（60行目）でコメント投稿のたびに `lineNotificationService.sendMentorCommentNotification()` を即時呼び出ししている。コメントを複数回に分けて書くと、書き途中のタイミングで逐一通知が飛ぶ。
- **修正方針**: `createComment()` からLINE通知呼び出しを削除する。コメントはDBに保存されるが、LINE通知は保留状態となる。
- **修正後のあるべき姿**: コメント投稿時にはLINE通知が送信されず、ユーザーが明示的に「送信」ボタンを押すまで通知は保留される。
- **対象方向**: メンター→メンティー、メンティー→メンター の両方向

### 2.2 「LINE通知を送信」ボタンの追加
- **現状の問題**: 通知の送信タイミングをユーザーが制御する手段がない。
- **修正方針**: コメントスレッド画面に「LINE通知を送信」ボタンを追加する。
  - 未通知のコメント（自分が書いたもの）がある場合のみボタンを表示
  - ボタン押下で、該当試合・該当メンティーの未通知コメントをまとめてFlex Messageで送信
  - 送信成功後、ボタンは非表示に（全コメントが通知済みになるため）
  - 送信件数をボタンテキストに表示（例:「LINE通知を送信（3件）」）
- **修正後のあるべき姿**: コメントを書き終えたタイミングでユーザーが任意にLINE通知を送信でき、相手は1つのFlex Messageで全コメントをまとめて受信する。

### 2.3 Flex Messageによる一括通知
- **現状の問題**: 現在はプレーンテキストで「○○さんがフィードバックコメントを投稿しました:\n（50文字プレビュー）」という1件ずつの通知。
- **修正方針**: 未通知コメントをまとめて1つのFlex Message（bubble型）で送信する。
- **Flex Message構成**:
  - **ヘッダー**: 背景色付き、「フィードバックコメント」タイトル
  - **ボディ**:
    - 送信者名（「○○さんからのフィードバック」）
    - 試合情報（日付、対戦相手）
    - コメント一覧（各コメントの本文を区切り線で区切って表示）
  - **altText**（Flex非対応端末向け）: 「○○さんがフィードバックコメントを○件投稿しました」
- **修正後のあるべき姿**: 受信者はLINEで1つの整理されたカード型メッセージを受け取り、全コメントを一覧で確認できる。

## 3. 技術設計

### 3.1 DB変更

#### `match_comments` テーブルにカラム追加

| 追加カラム | 型 | デフォルト | 説明 |
|-----------|---|----------|------|
| `line_notified` | BOOLEAN | false | LINE通知送信済みフラグ |

- 新規コメント作成時: `false`（未通知）
- 通知送信ボタン押下時: `true` に更新
- **マイグレーション**: 既存コメントは全て `true`（既に即時通知済みのため）

### 3.2 API変更

#### 新規エンドポイント

| エンドポイント | メソッド | 説明 |
|---------------|---------|------|
| `/api/matches/{matchId}/comments/notify` | POST | 未通知コメントをまとめてLINE通知を送信 |

**リクエスト**: `?menteeId={id}`（クエリパラメータ）

**処理フロー**:
1. アクセス権検証（既存の `validateCommentAccess`）
2. 現在のユーザーが書いた未通知コメント（`line_notified = false`、`deleted_at IS NULL`）を取得
3. 未通知コメントが0件の場合は400エラー
4. 試合情報を取得（Flex Message用）
5. 宛先を決定:
   - 送信者がメンティー → 全ACTIVEメンターに送信
   - 送信者がメンター → メンティーに送信
6. Flex Messageを構築・送信
7. 送信したコメントの `line_notified` を `true` に更新
8. レスポンス: `{ "notifiedCount": N, "result": "SUCCESS" / "SKIPPED" / "FAILED" }`

#### 既存エンドポイント変更

| エンドポイント | 変更内容 |
|---------------|---------|
| `GET /api/matches/{matchId}/comments` | レスポンスの `MatchCommentDto` に `lineNotified` フィールドを追加 |

### 3.3 バックエンド変更

| ファイル | 変更内容 |
|---------|---------|
| `MatchComment.java` | `lineNotified` フィールド追加（`@Column(name = "line_notified", nullable = false)`, `@Builder.Default private Boolean lineNotified = false`） |
| `MatchCommentDto.java` | `lineNotified` フィールド追加、`fromEntity()` に反映 |
| `MatchCommentRepository.java` | 未通知コメント検索クエリ追加（`findUnnotifiedByMatchIdAndMenteeIdAndAuthorId`） |
| `MatchCommentService.java` | `createComment()` からLINE通知呼び出しを削除。`sendCommentNotification()` メソッドを新規追加 |
| `MatchCommentController.java` | `POST /notify` エンドポイント追加 |
| `LineNotificationService.java` | `sendMentorCommentFlexNotification()` メソッド追加（Flex Message構築・送信） |

### 3.4 フロントエンド変更

| ファイル | 変更内容 |
|---------|---------|
| `MatchCommentThread.jsx` | 「LINE通知を送信（N件）」ボタンを追加。未通知コメント（自分のもの）がある場合のみ表示。送信成功/失敗のフィードバック表示 |
| `matchComments.js` | `sendNotification(matchId, menteeId)` API関数を追加 |

## 4. 影響範囲

| 影響箇所 | 影響内容 | リスク |
|---------|---------|--------|
| `MatchCommentService.createComment()` | LINE通知呼び出しを削除（**動作変更**） | コメント投稿時にLINE通知が飛ばなくなる。ユーザーがボタンを押し忘れると相手に通知が届かない。これは意図した動作。 |
| `match_comments` テーブル | `line_notified` カラム追加 | 既存データは `true` でマイグレーション。既存機能に影響なし |
| `MatchCommentDto` | `lineNotified` フィールド追加 | 既存レスポンスに追加フィールド。破壊的変更なし |
| 通知設定（`mentor_comment` ON/OFF） | 引き続き有効 | Flex送信時にも `isNotificationEnabled` チェックが走るため、既存の通知設定は尊重される |
| `sendMentorCommentNotification()` | 即時通知メソッドは参照されなくなる | 将来的に削除可能だが、今回は残置（他から呼ばれていないことを確認済み） |

## 5. 設計判断の根拠

| 判断 | 理由 |
|------|------|
| 即時通知の完全廃止（オプション化ではなく） | コメントを分けて書く運用が前提。途中通知は全て不要というユーザー要件 |
| `match_comments` に `line_notified` を追加（別テーブルではなく） | コメント単位で通知状態を管理するのが最もシンプル。別テーブルは過剰な複雑さ |
| Flex Messageの採用 | 複数コメントを1つのカード型メッセージにまとめられ、テキスト羅列より視認性が高い |
| 送信ボタンをコメントスレッド画面に配置 | コメントを書いた流れでそのまま通知を送信できる。メンティーごとの操作 |
| 通知方向は双方向対応 | メンター→メンティー、メンティー→メンター 両方でコメントを分けて書くニーズがある |
