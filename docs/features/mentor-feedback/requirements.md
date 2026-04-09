---
status: completed
---
# メンターフィードバック機能 要件定義書

## 1. 概要
- **目的:** メンター（先輩）がメンティー（後輩）の試合メモを閲覧・コメントできるようにし、試合の振り返りとフィードバックを効率化する
- **背景:** 現在は後輩のアカウントに直接ログインしてメモを確認・追記しており、セキュリティ・利便性の両面で課題がある

## 2. ユーザーストーリー
- **対象ユーザー:** 北大かるた会のメンター（先輩）とメンティー（後輩）
- **メンティーの目的:** 試合の反省を書き、メンターからフィードバックをもらう
- **メンターの目的:** メンティーの試合メモを確認し、コメントでフィードバックを返す
- **メンター指名:** メンティー（後輩）側から先輩を指名する（勝手に他人のメモを見れないようにするため）
- **承認:** メンターに指名された先輩は承認が必要（メンター管理画面で承認）
- **解除:** 双方どちらからでも承認不要で即時解除可能
- **人数制限:** なし（メンターは何人のメンティーでも持てる、メンティーも複数メンター可）
- **組織制限:** 同じ組織のメンバーのみメンターに指名可能

## 3. 機能要件

### 3.1 メンター管理画面
- 設定画面内に「メンター管理」セクションを設ける
- **メンティー側の操作:**
  - 同じ組織のプレイヤー一覧からメンターを指名
  - 指名中（承認待ち）の状態が確認できる
  - 既存のメンター関係を解除できる
- **メンター側の操作:**
  - メンター指名の承認/拒否ができる
  - 自分のメンティー一覧が確認できる（名前タップで試合一覧へ遷移）
  - メンター関係を解除できる

### 3.2 メンターによるメンティー試合閲覧
- メンター管理画面のメンティー一覧から、メンティー名タップで既存の試合一覧画面（`/matches?playerId={menteeId}`）に遷移
- 試合一覧にメモの有無・お手つきの有無が表示される（メンター関係がある場合のみ）
- 試合詳細画面ではメンティーのメモ・お手つき回数が閲覧可能
- メンターは閲覧とコメント投稿のみ。試合結果の編集等の操作ボタンは非表示

### 3.3 コメントスレッド機能
- 試合詳細画面で、メンティーのメモの下にコメントスレッドが表示される
- メンター・メンティー双方がコメントを投稿できる
- メンターが複数いる場合、1つの試合に1つのスレッドで全員が見える
- コメントには投稿者名・投稿日時が表示される（チャットのようなイメージ）
- コメントの編集・削除が可能（投稿者本人のみ）
- メンター関係解除後: メンターはコメントが見えなくなる。メンティーは引き続き閲覧可能

### 3.4 LINE通知
- メンターがコメントを投稿 → メンティーにLINE通知
- メンティーがコメントを投稿 → メンターにLINE通知
- 通知のON/OFFは通知設定画面で切り替え可能
- 既存のLINE通知基盤を利用

### 3.5 メンター指名時の通知
- LINE通知はなし（口頭で行うため、頻度も低い）

## 4. 技術設計

### 4.1 API設計

#### メンター関係管理
| エンドポイント | メソッド | 説明 |
|---------------|---------|------|
| `/api/mentor-relationships` | POST | メンター指名（メンティーが実行） |
| `/api/mentor-relationships/{id}/approve` | PUT | 承認（メンターが実行） |
| `/api/mentor-relationships/{id}/reject` | PUT | 拒否（メンターが実行） |
| `/api/mentor-relationships/{id}` | DELETE | 解除（双方可） |
| `/api/mentor-relationships/my-mentors` | GET | 自分のメンター一覧 |
| `/api/mentor-relationships/my-mentees` | GET | 自分のメンティー一覧 |
| `/api/mentor-relationships/pending` | GET | 承認待ち一覧（メンター側） |

#### コメント
| エンドポイント | メソッド | 説明 |
|---------------|---------|------|
| `/api/matches/{matchId}/comments?menteeId={id}` | GET | コメント一覧 |
| `/api/matches/{matchId}/comments` | POST | コメント投稿 |
| `/api/matches/{matchId}/comments/{commentId}` | PUT | コメント編集 |
| `/api/matches/{matchId}/comments/{commentId}` | DELETE | コメント削除（論理削除） |

#### 既存API拡張
| エンドポイント | 変更内容 |
|---------------|---------|
| `GET /api/matches/player/{playerId}` | メンター関係がある場合、メンティーのメモ・お手つき情報も返す |
| `GET /api/matches/{id}` | メンター関係がある場合、メンティーのメモ・お手つき情報も返す |

### 4.2 DB設計

#### 新規テーブル: `mentor_relationships`
| カラム | 型 | 制約 | 説明 |
|--------|---|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| mentor_id | BIGINT | FK→players, NOT NULL | メンター（先輩） |
| mentee_id | BIGINT | FK→players, NOT NULL | メンティー（後輩） |
| organization_id | BIGINT | FK→organizations, NOT NULL | 所属組織 |
| status | VARCHAR(20) | NOT NULL | PENDING / ACTIVE / REJECTED |
| created_at | TIMESTAMP | NOT NULL | 指名日時 |
| updated_at | TIMESTAMP | NOT NULL | |

- UNIQUE制約: (mentor_id, mentee_id, organization_id)
- 解除時は物理削除

#### 新規テーブル: `match_comments`
| カラム | 型 | 制約 | 説明 |
|--------|---|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| match_id | BIGINT | FK→matches, NOT NULL | 対象試合 |
| mentee_id | BIGINT | FK→players, NOT NULL | メモの所有者（メンティー） |
| author_id | BIGINT | FK→players, NOT NULL | コメント投稿者 |
| content | TEXT | NOT NULL | コメント本文 |
| created_at | TIMESTAMP | NOT NULL | |
| updated_at | TIMESTAMP | NOT NULL | |
| deleted_at | TIMESTAMP | NULL | 論理削除 |

#### 既存テーブル変更: `line_notification_preferences`
| 追加カラム | 型 | デフォルト | 説明 |
|-----------|---|----------|------|
| mentor_comment | BOOLEAN | true | メンターコメント通知 |

### 4.3 フロントエンド設計

#### 新規コンポーネント
- `MentorManagement.jsx` — メンター管理画面（設定画面から遷移）
  - メンティー用: メンター指名フォーム + 現在のメンター一覧 + 承認待ち状態表示
  - メンター用: 承認待ち一覧（承認/拒否ボタン） + メンティー一覧（名前タップで試合一覧へ）
  - 双方: 解除ボタン
- `MatchCommentThread.jsx` — コメントスレッドコンポーネント（試合詳細画面に埋め込み）
  - コメント一覧表示（投稿者名・日時）
  - コメント投稿フォーム
  - 編集・削除ボタン（投稿者本人のみ表示）

#### 既存コンポーネントの変更
- `SettingsPage.jsx` — メンター管理へのリンクを追加
- `MatchList.jsx` — メンター関係がある場合、メンティーのメモ有無・お手つき有無を表示
- `MatchDetail.jsx` — メンター関係がある場合、メンティーのメモ・お手つき表示 + コメントスレッド表示
- `NotificationSettings.jsx` — メンターコメント通知のON/OFF追加

#### 新規APIクライアント
- `mentorRelationship.js` — メンター関係管理API
- `matchComments.js` — コメントAPI

### 4.4 バックエンド設計

#### 新規クラス
- **Entity:** `MentorRelationship.java`, `MatchComment.java`
- **Repository:** `MentorRelationshipRepository.java`, `MatchCommentRepository.java`
- **Service:** `MentorRelationshipService.java`, `MatchCommentService.java`
- **Controller:** `MentorRelationshipController.java`, `MatchCommentController.java`
- **DTO:** `MentorRelationshipDto.java`, `MatchCommentDto.java`, `MentorRelationshipCreateRequest.java`, `MatchCommentCreateRequest.java`

#### 既存クラスの変更
- `MatchService.java` — `enrichDtosWithPersonalNotes()` をメンター関係に対応拡張
- `MatchDto.java` — メンティーのメモ情報を返すフィールド追加
- `LineNotificationPreference.java` — `mentorComment` フィールド追加
- `LineNotificationPreferenceDto.java` — 同上
- `LineNotificationService.java` — メンターコメント通知メソッド追加
- `LineMessageLog.java` — `NotificationType` に `MENTOR_COMMENT` 追加

## 5. 影響範囲

### 既存機能への影響

| 影響箇所 | 影響内容 | リスク |
|---------|---------|--------|
| `MatchService.enrichDtosWithPersonalNotes()` | メンター関係がある場合にメンティーのメモも返すよう拡張 | 現在の「自分のメモのみ表示」ロジックに手を入れるため、一般ユーザーへの影響に注意 |
| `MatchDto` | メンティーのメモ用フィールド追加 | 既存フィールド(`myPersonalNotes`)とは別に追加。既存APIレスポンスに影響なし |
| `MatchDetail.jsx` | コメントスレッド追加 | メンター関係がない場合は非表示。既存表示に影響なし |
| `MatchList.jsx` | メンティーのメモ有無表示 | `isOtherPlayer` かつメンター関係ありの場合のみ。既存の他プレイヤー閲覧に影響なし |
| `SettingsPage.jsx` | メンター管理リンク追加 | 追加のみ、既存項目に影響なし |
| `NotificationSettings.jsx` | 通知設定項目追加 | 追加のみ |
| `line_notification_preferences` テーブル | カラム追加（`mentor_comment`） | デフォルト `true` で追加。既存データに影響なし |
| `LineMessageLog` NotificationType | ENUM値追加 | 追加のみ |

### DB互換性
- 新規テーブル2つ追加 + 既存テーブル1つにカラム追加（デフォルト値あり）
- 破壊的変更なし

### リスク軽減策
- `enrichDtosWithPersonalNotes()` の拡張では、メンター関係の有無チェックを明確に分離し、既存の「自分のメモ取得」ロジックには手を加えない
- コメントスレッドはメンター関係の存在を前提に表示制御するため、一般ユーザーには一切表示されない

## 6. 設計判断の根拠

| 判断 | 理由 |
|------|------|
| メンティーからの指名制（メンターからではない） | メンターが登録すると、勝手に他人のメモを見れてしまうリスクがあるため |
| コメントスレッド形式（メモ追記ではなく） | 誰がいつ書いたか明確になり、やり取りが追いやすい。反省（メモ）とFB（コメント）が構造的に分離される |
| メンター関係解除後、メンターはコメント非表示 | プライバシー保護。メンティーは自分の試合のコメントなので引き続き閲覧可能 |
| 複数メンター時は共有スレッド | シンプルさ優先。メンター間で指導内容を共有できるメリットもある |
| メンター指名時のLINE通知なし | 口頭で行うため不要。頻度も低い |
| 既存の試合一覧画面を流用 | 新画面を作る必要がなく、既存の `isOtherPlayer` フラグを活用できる |
| 解除時は物理削除 | 解除履歴の管理は不要。再指名は新規レコードで対応 |
