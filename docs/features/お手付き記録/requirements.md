---
status: completed
---
# お手付き記録 要件定義書

## 1. 概要

### 目的
個人の結果入力画面（MatchForm）で、自分のお手付き回数と個人メモを記録できるようにする。

### 背景・動機
- 現在の `matches.notes` は試合に対して1つしかなく、共有されるデータである
- お手付き回数を記録する手段がない
- メモとお手付きは入力者固有のプライベートデータとして管理したい
- 将来的にはお手付き回数の集計・分析を行う予定

## 2. ユーザーストーリー

### 対象ユーザー
- 各プレイヤー（PLAYER / ADMIN）が自分自身の試合結果に対して入力する

### ユーザーの目的
- 自分の試合ごとのお手付き回数を記録し、振り返りに役立てたい
- 試合ごとの個人メモ（感想・反省点）をプライベートに記録したい

### 利用シナリオ
1. プレイヤーが `/matches/new` から試合結果を入力する際に、お手付き回数（任意）と個人メモ（任意）を一緒に入力する
2. 入力後、`/matches` の結果一覧で自分の試合にはメモの有無とお手付き回数が表示される
3. `/matches/results/:sessionId` の結果表示画面でも自分のお手付き回数・メモが確認できる
4. `/matches/:id` の試合詳細画面でも自分のお手付き回数が表示される
5. 後から `/matches/:id/edit` で編集できる

## 3. 機能要件

### 3.1 画面仕様

#### MatchForm（`/matches/new`, `/matches/:id/edit`）
- 既存の「メモ」欄はそのままの見た目で残すが、保存先を新テーブルに変更する
- 「メモ」欄の上または下に「お手付き回数」の入力欄を追加する
- お手付き回数の入力: セレクトボックスで 0〜20 を選択可能
- デフォルト: 未選択（null）。プレースホルダーに「選択してください」等を表示
- お手付き回数・メモともに任意入力（未入力で保存可能）

#### MatchList（`/matches`）下部の結果一覧
- 自分の試合の場合のみ:
  - メモの有無を表示（例: メモアイコンの有無）
  - お手付き回数を表示（nullの場合は非表示、0の場合は「0回」と表示）
- 他のプレイヤーの試合ではメモ・お手付き回数は表示しない

#### MatchResultsView（`/matches/results/:sessionId`）
- 自分の試合結果にお手付き回数・メモを表示
- 他プレイヤーのメモ・お手付きは非表示

#### MatchDetail（`/matches/:id`）
- 自分のお手付き回数を表示
- 自分のメモを表示
- 他プレイヤーのメモ・お手付きは非表示

### 3.2 ビジネスルール
- お手付き回数: 0〜20の整数、またはnull（未入力）
- null と 0 は明確に区別する（未入力 vs 0回）
- メモ・お手付きは入力者のプライベートデータであり、他のプレイヤーには一切表示しない
- 管理者一括入力（BulkResultInput）ではお手付き・メモは入力しない
- 試合結果の保存と同時に、個人メモ・お手付きを保存する（同一トランザクション）

### 3.3 エラーケース
- お手付き回数に範囲外の値（-1, 21以上）が入力された場合: バリデーションエラー
- 試合が存在しない状態で個人メモだけ保存しようとした場合: 試合結果と一緒に保存するのでこのケースは発生しない

## 4. 技術設計

### 4.1 API設計

#### 個人メモの保存（試合保存と一体化）
既存のMatch作成/更新APIのリクエストに `otetsukiCount` と `personalNotes` フィールドを追加する。バックエンドで試合保存後に `match_personal_notes` テーブルにupsertする。

**POST /api/matches/detailed（既存の拡張）**
リクエストボディに追加:
```json
{
  "otetsukiCount": 3,
  "personalNotes": "右下段が甘かった"
}
```

**PUT /api/matches/{id}/detailed（既存の拡張）**
同上。

**POST /api/matches（簡易作成、既存の拡張）**
リクエストボディに追加:
```json
{
  "otetsukiCount": 3,
  "notes": "右下段が甘かった"
}
```
※ `notes` は `match_personal_notes.notes` に保存するように変更。

**PUT /api/matches/{id}（簡易更新、既存の拡張）**
同上。

#### 個人メモの取得
既存のMatch取得APIのレスポンスに、リクエストしたプレイヤー（認証ユーザー）の個人メモ・お手付きを含める。

**GET /api/matches/player/{playerId}** 等のレスポンスに追加:
```json
{
  "myOtetsukiCount": 3,
  "myPersonalNotes": "右下段が甘かった"
}
```

※ `my` プレフィックスを付けることで「リクエストユーザー自身のデータ」であることを明示。

### 4.2 DB設計

#### 新規テーブル: `match_personal_notes`

| カラム名 | 型 | NULL | デフォルト | 説明 |
|---|---|---|---|---|
| id | BIGINT | NO | AUTO_INCREMENT | PK |
| match_id | BIGINT | NO | - | FK → matches(id) |
| player_id | BIGINT | NO | - | FK → players(id) |
| notes | TEXT | YES | NULL | 個人メモ |
| otetsuki_count | INT | YES | NULL | お手付き回数（0〜20） |
| created_at | TIMESTAMP | NO | CURRENT_TIMESTAMP | 作成日時 |
| updated_at | TIMESTAMP | NO | CURRENT_TIMESTAMP ON UPDATE | 更新日時 |

- UNIQUE制約: `(match_id, player_id)`
- CHECK制約: `otetsuki_count >= 0 AND otetsuki_count <= 20`
- INDEX: `idx_match_personal_notes_player (player_id, match_id)`

#### 既存テーブルの変更: `matches`
- `notes` カラムを削除（DROP COLUMN）

### 4.3 フロントエンド設計

#### コンポーネント変更
- **MatchForm.jsx**: formDataに `otetsukiCount` を追加。セレクトボックスUIを追加。`notes` の保存先を変更。
- **MatchList.jsx**: 自分の試合行にメモアイコン・お手付き回数を表示。
- **MatchResultsView.jsx**: 自分の結果表示部にお手付き回数・メモを表示。
- **MatchDetail.jsx**: 自分のお手付き回数・メモを表示。

#### 状態管理
- MatchFormの `formData` に `otetsukiCount: null` を追加
- 既存マッチ編集時: APIレスポンスから `myOtetsukiCount`, `myPersonalNotes` を読み込み

### 4.4 バックエンド設計

#### 新規作成
- **Entity**: `MatchPersonalNote.java`
- **Repository**: `MatchPersonalNoteRepository.java`
- **DTO**: 既存のDTOを拡張（`MatchDto` に `myOtetsukiCount`, `myPersonalNotes` を追加）

#### 既存変更
- **MatchService.java**: 試合作成/更新時に `match_personal_notes` へのupsertを追加
- **MatchController.java**: リクエストから `otetsukiCount`, `personalNotes` を受け取り、サービスに渡す
- **Match.java**: `notes` フィールドを削除
- **MatchCreateRequest.java / MatchSimpleCreateRequest.java**: フィールド追加

## 5. 影響範囲

### 変更が必要な既存ファイル

#### バックエンド
- `Match.java` — `notes` フィールド削除
- `MatchDto.java` — `notes` 削除、`myOtetsukiCount` / `myPersonalNotes` 追加
- `MatchCreateRequest.java` — `otetsukiCount` / `personalNotes` 追加
- `MatchSimpleCreateRequest.java` — `otetsukiCount` 追加、`notes` の扱い変更
- `MatchService.java` — 個人メモのupsertロジック追加、取得時の個人メモ結合
- `MatchController.java` — 認証ユーザー情報の取得・サービスへの受け渡し

#### フロントエンド
- `MatchForm.jsx` — お手付き入力UI追加、保存ロジック変更
- `MatchList.jsx` — 自分の試合にメモ有無・お手付き回数表示
- `MatchResultsView.jsx` — 自分の結果にお手付き回数・メモ表示
- `MatchDetail.jsx` — お手付き回数・メモ表示
- `matches.js`（API） — リクエスト/レスポンスのフィールド対応

#### データベース
- `matches` テーブル — `notes` カラム削除
- 新規テーブル `match_personal_notes` 作成

### 既存機能への影響
- **BulkResultInput**: `notes` フィールドを使っている場合は送信しないように変更が必要。お手付き・メモの入力は追加しない。
- **統計機能**: 現状ではお手付きの集計は不要。将来の拡張のみ。

## 6. 設計判断の根拠

### 新テーブルにした理由
- メモ・お手付きは「プレイヤー×試合」ごとのデータであり、1試合に2レコード（対戦者それぞれ）が必要
- `matches` テーブルに `player1_notes`, `player2_notes` のように持つと、どちらのプレイヤーかの判定が複雑になる
- 独立テーブルにすることで、試合の共有データと個人データを明確に分離できる

### 既存API拡張（新規APIではない）にした理由
- お手付き・メモは試合結果と同時に入力・保存するため、別APIにする必要がない
- フロントエンドの保存ロジックをシンプルに保てる

### matches.notes の即時削除
- 既にメモ機能を使っているユーザーがいないため、移行は不要
- 段階的削除より即時削除の方がコードの複雑さを避けられる
