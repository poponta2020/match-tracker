# メンター

> **責務:** メンター（指導者）とメンティー（被指導者）の関係管理、および試合詳細画面でのメンター⇔メンティー間フィードバックコメントのやり取り
> **関連画面:** `/settings/mentor`（メンター管理）、`/matches/:id`（試合詳細内のコメントスレッド）
> **主要実装:** `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/MentorRelationship.java`、`karuta-tracker/src/main/java/com/karuta/matchtracker/entity/MatchComment.java`、`karuta-tracker/src/main/java/com/karuta/matchtracker/controller/MentorRelationshipController.java`、`karuta-tracker/src/main/java/com/karuta/matchtracker/controller/MatchCommentController.java`、`karuta-tracker/src/main/java/com/karuta/matchtracker/service/MentorRelationshipService.java`、`karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchCommentService.java`、`karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java`、`karuta-tracker-ui/src/pages/mentor/MentorManagement.jsx`、`karuta-tracker-ui/src/pages/matches/MatchCommentThread.jsx`

## 機能仕様

### 概要

メンター（指導者）とメンティー（被指導者）の関係を管理し、試合詳細画面でメンター⇔メンティー間のフィードバックコメントをやり取りできる機能。

### メンター関係の管理

**メンター指名フロー:**
1. メンティーが `/settings/mentor` で所属組織とメンターを選択して指名
2. メンター関係が `PENDING` ステータスで作成される
3. メンター側の「承認待ちリクエスト」に表示される
4. メンターが承認 → `ACTIVE`（指導開始）
5. メンターが拒否 → `REJECTED`（メンティーは再指名可能）

**関係の特性:**
- メンター関係は団体（organization）単位。同じ人でも異なる団体では別の関係
- メンター・メンティー双方が関係を解除（DELETE）可能
- 自分自身をメンターに指名することは不可
- `REJECTED` 後の再指名を許可（ステータスを `PENDING` に更新）
- ユニーク制約: `(mentor_id, mentee_id, organization_id)`

**画面構成（`/settings/mentor`）:**
- **承認待ちリクエスト**: メンターとして受け取った PENDING リクエスト一覧（承認/拒否ボタン）
- **マイメンター**: メンティーとして指名したメンター一覧（指名フォーム、解除ボタン、ステータス表示）
- **マイメンティー**: メンターとしての指導対象者一覧（メンティーの試合履歴へのナビゲーション、解除ボタン）

### メンターコメント（フィードバック）

試合詳細画面（`/matches/:id`）にコメントスレッドを表示。メンティー本人またはACTIVEメンター関係を持つメンターのみアクセス可能。

**コメントスレッドの表示条件（試合詳細画面）:**
- **メンター閲覧時**（`isOtherPlayer=true`）: ACTIVEメンター関係があれば常に表示
- **メンティー本人閲覧時**（`isOtherPlayer=false`）: 自分以外の投稿者によるコメントが**1件以上ある場合のみ表示**（投稿フォーム含めて完全非表示。判定は `matchCommentsAPI.getComments` のレスポンスの `authorId !== currentPlayer.id` で行い、解除済みメンターからの過去コメントもカウント対象とする）

**コメント機能:**
- スレッド形式でコメントを表示（チャット風UI）
- 自分のコメントは右側に緑背景、他人のコメントは左側にグレー背景
- 投稿者本人のみ編集・削除可能
- 論理削除対応（`deleted_at`）
- コメント入力中（textarea にフォーカス時）はボトムナビゲーションを非表示にし、誤タップによる画面遷移を防止（ナビゲーション構造については docs/SCREEN_LIST.md 参照）

**LINE通知連携（バッチ送信方式）:**
- 通知種別: `MENTOR_COMMENT`
- コメント投稿時にはLINE通知を即時送信しない（`line_notified = false` で保留）
- ユーザーがコメントスレッド画面の「LINE通知を送信（N件）」ボタンを押した時点で、未通知コメントをまとめて1つのFlex Messageで送信
- メンティーがボタン押下 → すべてのACTIVEメンターにFlex Message送信
- メンターがボタン押下 → メンティーにFlex Message送信
- 送信成功時のみ `line_notified = true` に更新（失敗時は再送可能）
- ユーザーは通知設定画面で `mentor_comment` の ON/OFF を制御可能（デフォルト: ON）

### メンティーメモ更新時のメンターLINE通知

メンティーが試合結果の個人メモを新規入力・更新した際、全ACTIVEメンターにLINE Flex Messageで即時通知する。

**トリガー条件:**
- メンティーが試合編集画面（`/matches/:id/edit`）で個人メモを更新
- メモ内容が実際に変更された場合のみ通知（スコアのみ変更時は通知なし）
- 通知種別: `MENTEE_MEMO_UPDATE`

**送信先:**
- メンティーの全ACTIVEメンターに送信
- メンターの `mentor_comment` 通知設定がONの場合のみ送信

**通知内容（Flex Message）:**
- メンティー名、試合情報（対戦相手・日付）、更新されたメモ内容

**エラーハンドリング:**
- 通知失敗時もメモ保存は正常に完了する（トランザクションコミット後に非同期送信、try-catch保護）

## フロー

### メンター指名・コメントフロー

```
[メンター指名フロー]
1. メンティーが /settings/mentor でメンター指名
   ↓
2. POST /api/mentor-relationships → PENDING状態で作成
   ↓
3. メンター側の承認待ちリクエストに表示
   ↓
4. メンターが承認（PUT /{id}/approve → ACTIVE）
   または拒否（PUT /{id}/reject → REJECTED）

[コメントスレッドの表示条件（試合詳細画面）]
- メンター閲覧時（他選手のページを `?playerId=` で開いた場合）: ACTIVEメンター関係があれば常に表示
- メンティー本人画面: 自分以外の投稿者によるコメントが1件以上ある場合のみ表示（投稿フォーム含めて完全非表示）
  - 判定は `matchCommentsAPI.getComments` のレスポンスを `authorId !== currentPlayer.id` で評価
  - 解除済みメンターからの過去コメントもカウント対象

[メンターコメントフロー（バッチ送信方式）]
1. 試合詳細画面（/matches/:id）で上記表示条件を満たすメンティーまたはメンターがコメント投稿
   ↓
2. POST /api/matches/{matchId}/comments → コメント作成（line_notified = false）
   ※ LINE通知は即時送信しない
   ↓
3. ユーザーが「LINE通知を送信（N件）」ボタンを押下
   ↓
4. POST /api/matches/{matchId}/comments/notify → 未通知コメントを収集
   ↓
5. Flex Messageを構築（送信者名・試合情報・コメント一覧）
   ↓
6. LINE通知送信（MENTOR_COMMENT）
   - メンティーがボタン押下 → 全ACTIVEメンターにFlex Message送信
   - メンターがボタン押下 → メンティーにFlex Message送信
   ↓
7. 送信成功時のみ line_notified = true に更新（失敗時は再送可能）

[メンティーメモ更新通知フロー]
1. メンティーが試合編集画面（/matches/:id/edit）で個人メモを更新
   ↓
2. PUT /api/matches/{id}/detailed or PUT /api/matches/{id} → メモ内容が変更されたか比較
   ↓
3. 変更あり → トランザクションコミット後に通知処理を実行
   ↓
4. 全ACTIVEメンターを取得 → mentor_comment トグルがONのメンターにFlex Message送信（MENTEE_MEMO_UPDATE）
   ↓
5. 送信結果をline_message_logに記録（通知失敗時もメモ保存は正常完了）
```

**関連クラス:**

| クラス | 説明 |
|--------|------|
| `MentorRelationship` | entity/ — メンター関係エンティティ（PENDING/ACTIVE/REJECTED） |
| `MatchComment` | entity/ — メンターコメントエンティティ（論理削除対応、`line_notified` で通知状態管理） |
| `MentorRelationshipController` | controller/ — メンター関係CRUD（7エンドポイント） |
| `MatchCommentController` | controller/ — コメントCRUD + 通知送信（5エンドポイント） |
| `MentorRelationshipService` | service/ — 指名・承認・拒否・解除のビジネスロジック |
| `MatchCommentService` | service/ — コメント投稿・編集・削除・アクセス権検証・バッチ通知送信 |
| `LineNotificationService` | service/ — MENTOR_COMMENT / MENTEE_MEMO_UPDATE Flex Message構築・送信 |
| `MentorManagement.jsx` | pages/mentor/ — メンター管理画面 |
| `MatchCommentThread.jsx` | pages/matches/ — コメントスレッドUI（textarea の focus/blur で `BottomNavContext.setVisible` を制御。blur 時 100ms 遅延でチラつき防止。unmount 時に `setVisible(true)` でリセット） |

**DB変更:**

| 対象 | 変更内容 |
|------|---------|
| `mentor_relationships` テーブル | 新規作成（mentor_id, mentee_id, organization_id, status） |
| `match_comments` テーブル | 新規作成（match_id, mentee_id, author_id, content, deleted_at） |
| `LineNotificationType` enum | `MENTOR_COMMENT` 追加 |
| `line_notification_preferences` テーブル | `mentor_comment` カラム追加（DEFAULT TRUE） |
| `line_message_log` CHECK制約 | `MENTOR_COMMENT` 追加 |
| `LineNotificationType` enum | `MENTEE_MEMO_UPDATE` 追加 |
| `line_message_log` CHECK制約 | `MENTEE_MEMO_UPDATE` 追加 |

## API

### メンター関係

`/api/mentor-relationships`

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| POST | `/` | ALL | メンター指名（PENDING状態で作成。自分自身指名不可） |
| PUT | `/{id}/approve` | ALL | メンター関係承認（メンター本人のみ） |
| PUT | `/{id}/reject` | ALL | メンター関係拒否（メンター本人のみ） |
| DELETE | `/{id}` | ALL | メンター関係解除（メンターまたはメンティー本人のみ） |
| GET | `/my-mentors` | ALL | 自分のメンター一覧（ACTIVE + PENDING） |
| GET | `/my-mentees` | ALL | 自分のメンティー一覧（ACTIVEのみ） |
| GET | `/pending` | ALL | 承認待ちリクエスト（自分がメンターのPENDING） |

#### POST /api/mentor-relationships
**説明**: メンター指名（PENDING状態で作成）
**権限**: ALL
**リクエスト**:
```json
{
  "mentorId": 1,
  "organizationId": 1
}
```
**バリデーション**: 自分自身指名不可、同一組み合わせの重複チェック（REJECTED済みは再指名可）

#### PUT /api/mentor-relationships/{id}/approve
**説明**: メンター関係承認（PENDING → ACTIVE）
**権限**: ALL（メンター本人のみ）

#### PUT /api/mentor-relationships/{id}/reject
**説明**: メンター関係拒否（PENDING → REJECTED）
**権限**: ALL（メンター本人のみ）

#### DELETE /api/mentor-relationships/{id}
**説明**: メンター関係解除（物理削除）
**権限**: ALL（メンターまたはメンティー本人のみ）

#### GET /api/mentor-relationships/my-mentors
**説明**: 自分のメンター一覧取得（ACTIVE + PENDING）
**権限**: ALL

#### GET /api/mentor-relationships/my-mentees
**説明**: 自分のメンティー一覧取得（ACTIVEのみ）
**権限**: ALL

#### GET /api/mentor-relationships/pending
**説明**: 承認待ちリクエスト取得（自分がメンターのPENDING）
**権限**: ALL

### メンターコメント

`/api/matches/{matchId}/comments`

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `/?menteeId=` | ALL | コメント一覧取得（メンティー本人またはACTIVEメンターのみ。レスポンスに `lineNotified` フィールドを含む） |
| POST | `/` | ALL | コメント投稿（メンティー本人またはACTIVEメンターのみ。LINE通知は即時送信しない） |
| POST | `/notify?menteeId=` | ALL | 未通知コメントをまとめてLINE Flex Messageで送信。成功時のみ `line_notified=true` に更新 |
| PUT | `/{commentId}` | ALL | コメント編集（投稿者本人のみ） |
| DELETE | `/{commentId}` | ALL | コメント論理削除（投稿者本人のみ） |

#### GET /api/matches/{matchId}/comments?menteeId={menteeId}
**説明**: コメント一覧取得
**権限**: ALL（メンティー本人またはACTIVEメンターのみ）

#### POST /api/matches/{matchId}/comments
**説明**: コメント投稿（LINE通知は即時送信しない。`line_notified = false` で保留）
**権限**: ALL（メンティー本人またはACTIVEメンターのみ）
**リクエスト**:
```json
{
  "menteeId": 1,
  "content": "コメント内容"
}
```

#### POST /api/matches/{matchId}/comments/notify?menteeId={menteeId}
**説明**: 未通知コメントをまとめてLINE Flex Messageで送信
**権限**: ALL（メンティー本人またはACTIVEメンターのみ）
**処理**: 現在のユーザーが書いた未通知コメント（`line_notified = false`）を収集し、1つのFlex Messageにまとめて送信。送信成功時のみ `line_notified = true` に更新。
**レスポンス**:
```json
{
  "notifiedCount": 3,
  "result": "SUCCESS"
}
```
**result値**: `SUCCESS`（全件送信成功）/ `FAILED`（送信失敗）/ `SKIPPED`（通知対象外）

#### PUT /api/matches/{matchId}/comments/{commentId}
**説明**: コメント編集（投稿者本人のみ）
**権限**: ALL
**リクエスト**:
```json
{
  "content": "更新後のコメント内容"
}
```

#### DELETE /api/matches/{matchId}/comments/{commentId}
**説明**: コメント論理削除（投稿者本人のみ）
**権限**: ALL
