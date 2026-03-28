---
status: completed
---
# 団体管理 要件定義書（ドラフト）

## 1. 概要

### 目的
競技かるたの対戦記録管理アプリ（Match Tracker）を、わすらもち会と北海道大学かるた会の2団体が利用できるようにする。各団体は異なる締め切り・参加管理ルールを持つため、団体ごとに独立した運用を可能にしつつ、両方に参加するユーザーにはシームレスな体験を提供する。

### 背景・動機
- 将来的にわすらもち会と北海道大学かるた会の2団体が本アプリを使用する
- 片方にしか参加しない人にはもう片方の練習日・締切連絡等を表示したくない（UX向上）
- 2団体は締め切り制度が全く異なるため、団体ごとに異なるルールを適用する必要がある

## 2. ユーザーストーリー

### 対象ユーザー
- **わすらもち会のみに参加する人** — わすらもち会の練習日・締切のみ表示される
- **北大かるた会のみに参加する人** — 北大かるた会の練習日・締切のみ表示される
- **両方に参加する人** — 両方の練習日が表示される（直近のものが優先表示、日程が被れば両方表示）
- **ADMIN（団体ごと）** — 所属団体の練習日・参加者を管理。他団体の練習日は編集不可
- **SUPER_ADMIN** — 全団体横断の管理権限

### ユーザーの目的
- 自分が参加する練習会の情報だけを見たい
- 不要な通知・締切情報に煩わされたくない

### 利用シナリオ

#### 新規ユーザー登録
- 団体別の登録URL（例: `/register?org=wasura`, `/register?org=hokudai`）が存在する
- URLに応じて「参加する練習会」の初期値が設定される
- 登録後はユーザー自身が設定画面で自由に参加練習会を追加・解除できる
- 団体別登録URLはADMINが発行する

#### 参加練習会の管理
- ユーザーは設定画面（「団体登録」のような画面）から参加する練習会を選択する
- 「所属」ではなく「参加する練習会を選ぶ」という概念
- どちらも選択していない状態は存在しない（登録時に必ず1つ以上選択）

#### 管理者の団体紐づけ
- SUPER_ADMINがADMINをどの団体の管理者にするか設定する
- 1人のADMINは1つの団体にのみ紐づく（兼任なし）
- ADMINは自団体の練習日のみ編集可能
- ADMINが他団体の練習会にも登録している場合は、一般ユーザーとして閲覧可能

### 既存ユーザーの移行
- 現在のユーザーは全員「わすらもち会の練習に参加する人」として移行
- 北大かるた会のみに参加する人は現時点で存在しない

## 3. 機能要件

### 3.1 画面仕様

#### 練習日一覧（カレンダー）
- ユーザーが登録している団体の練習日のみ表示
- 両方登録している場合は混在した一覧で表示
- カレンダー上では色で団体を区別（わすらもち会=緑、北大かるた会=赤）
- ラベルは不要。詳細画面にどちらの団体の練習か軽く記載する

#### 参加登録・キャンセル
- 月単位で練習日一覧が表示され、参加したい日・試合を選ぶ（両団体共通）
- 試合ごとの定員管理（両団体共通）

#### 団体登録設定画面
- ユーザーが参加する練習会（わすらもち会 / 北大かるた会）を選択する画面
- 最低1つは選択必須

#### 団体別登録URL
- ADMINが団体ごとの新規ユーザー登録URLを発行できる
- URLに応じて参加練習会の初期値が設定された状態で登録画面が開く

### 3.2 ビジネスルール

#### わすらもち会の締め切りルール
- **締め切り**: 練習当日12:00
- **締め切り前**: 自由に参加登録・キャンセル可能
- **締め切り後（当日12:00以降）**: 確認ダイアログを表示
  - メッセージ例:「12時以降の参加登録・キャンセルは管理者への連絡が必須です。連絡しましたか？」
  - 「はい」押下 → システム上は通常通り処理が完了する（自動通知等は不要、自己申告ベース）
  - 「いいえ」押下 → 処理を中断
  - 理由: 12:00に対戦組み合わせを出すため、それ以降の無断変更を防止
- **定員管理**: 先着順。定員到達時点で即キャンセル待ち
- **抽選**: なし
- **参加登録時のステータス**: 空きあり → 即`WON` / 定員超過 → 即`WAITLISTED`（`PENDING`は使わない）
- **キャンセル待ち繰り上げ**: キャンセル発生時に待機者に通知（既存の仕組みと同じ）

#### 北大かるた会の締め切りルール
- **締め切り**: 前月N日（団体設定で指定）
- **締め切り前**: 自由に参加登録・キャンセル可能
- **締め切り後**: 現在の実装と同じ
  - 空きがあれば参加登録は可能
  - キャンセルはキャンセル専用画面から可能
  - 理由: 締切の意味を保つ＋安易なキャンセルの心理的ハードルを上げる
- **抽選**: 締切日に実行（月単位、試合ごと）
- **定員管理**: 抽選で当落決定。抽選後の新規登録者はキャンセル待ちの最後尾に並ぶ
- **キャンセル待ち繰り上げ**: 当選者キャンセル時に待機者に通知（既存の仕組みと同じ）

#### 対戦組み合わせ
- 両団体ともADMINが手動作成
- わすらもち会: 当日12:00に作成
- 北大かるた会: 練習直前に作成

#### 通知
- 団体ごとに分離。ユーザーが登録していない団体の通知は送信しない
- **通知設定は団体別**: Webプッシュ・LINE通知ともに、種別ごとのON/OFFを団体単位で設定可能
  - グローバルの有効/無効切り替えは団体横断で1つ
  - 種別ごとのトグル（抽選結果、キャンセル待ち繰り上げ等）は団体別
  - 1団体のみ登録の場合は団体名セクションを非表示にし、現行とほぼ同じ見た目
  - わすらもち会は抽選がないため「抽選結果」トグルは非表示
- **管理者向け通知（チャネル回収警告、伝助未登録者）**: ADMINは自団体の通知のみ、SUPER_ADMINは全団体
- **デフォルト**: 新しい団体に登録した時、その団体の通知設定はデフォルト全ON
- LINE通知は既存のユーザー単位のチャンネルから送信
- Webプッシュ通知はブラウザのPush APIを使用

#### システム設定
- 団体ごとに独立した設定値を保持
- 締め切りタイプ（当日時刻ベース / 前月N日ベース）は団体設定として保持

#### 管理者権限
- ADMIN: 自団体の練習日・参加者の編集権限のみ
- SUPER_ADMIN: 全団体横断の管理権限（既存通り）

## 4. 技術設計

### 4.1 DB設計

#### 新規テーブル

##### `organizations` テーブル
| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| code | VARCHAR(50) | UNIQUE, NOT NULL | 団体コード（`wasura`, `hokudai`） |
| name | VARCHAR(200) | NOT NULL | 団体名 |
| color | VARCHAR(10) | NOT NULL | テーマカラー（`#22c55e`, `#ef4444`） |
| deadline_type | ENUM('SAME_DAY', 'MONTHLY') | NOT NULL | 締め切りタイプ |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | |
| updated_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP ON UPDATE | |

初期データ:
- `(1, 'wasura', 'わすらもち会', '#22c55e', 'SAME_DAY')`
- `(2, 'hokudai', '北海道大学かるた会', '#ef4444', 'MONTHLY')`

##### `player_organizations` テーブル
| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INCREMENT | |
| player_id | BIGINT | FK → players.id, NOT NULL | |
| organization_id | BIGINT | FK → organizations.id, NOT NULL | |
| created_at | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | |

- UNIQUE制約: `(player_id, organization_id)`

#### 既存テーブルの変更

##### `practice_sessions` テーブル
- `organization_id` (BIGINT, FK → organizations.id, NOT NULL) を追加

##### `system_settings` テーブル
- `organization_id` (BIGINT, FK → organizations.id, NOT NULL) を追加
- ユニーク制約を `(setting_key)` → `(setting_key, organization_id)` に変更

##### `players` テーブル
- `admin_organization_id` (BIGINT, FK → organizations.id, NULL) を追加
- ADMIN のみ値が入る。PLAYER / SUPER_ADMIN は NULL

##### `invite_tokens` テーブル
- `organization_id` (BIGINT, FK → organizations.id, NOT NULL) を追加
- トークン発行時に団体を紐づけ → 登録時の `player_organizations` 初期値に使用

##### `push_notification_preferences` テーブル
- `organization_id` (BIGINT, FK → organizations.id, NOT NULL) を追加
- ユニーク制約を `(player_id)` → `(player_id, organization_id)` に変更
- 団体ごとに独立した通知設定を保持

##### `line_notification_preferences` テーブル
- `organization_id` (BIGINT, FK → organizations.id, NOT NULL) を追加
- ユニーク制約を `(player_id)` → `(player_id, organization_id)` に変更
- 団体ごとに独立した通知設定を保持

### 4.2 API設計

#### 新規エンドポイント
- `GET /api/organizations` — 団体一覧取得
- `GET /api/players/{id}/organizations` — ユーザーの参加団体一覧
- `PUT /api/players/{id}/organizations` — ユーザーの参加団体更新
- `PUT /api/players/{id}/admin-organization` — ADMIN の団体紐づけ変更（SUPER_ADMIN のみ）

#### 既存エンドポイントの変更
- `GET /api/practice-sessions/**` — ログインユーザーの `player_organizations` に基づき自動フィルタ
- `POST /api/practice-sessions` — ADMIN は自動で `admin_organization_id` を設定。SUPER_ADMIN は `organization_id` を指定
- `GET /api/system-settings` — ADMIN は自団体の設定のみ取得。SUPER_ADMIN は団体指定で取得
- `PUT /api/system-settings/{key}` — `organization_id` パラメータを追加
- `POST /api/invite-tokens` — `organization_id` パラメータを追加
- 通知関連API — 送信対象を `player_organizations` でフィルタ

### 4.3 フロントエンド設計

#### 新規コンポーネント
- **団体登録設定画面** — チェックボックスで参加練習会を選択（最低1つ必須）

#### 既存コンポーネントの変更
- **カレンダー** — 団体の色で練習日を色分け表示
- **練習日詳細** — 団体名を軽く表示
- **参加登録** — わすらもち会の場合、当日12:00以降に確認ダイアログを表示
- **新規登録画面（InviteRegister）** — トークンの `organization_id` を取得し `player_organizations` 初期値に使用
- **ADMIN管理画面** — 自団体スコープで自動フィルタ
- **SUPER_ADMIN画面** — 練習日作成時に団体選択UIを追加
- **通知設定画面（NotificationSettings）** — Webプッシュ・LINE通知の種別トグルを団体別に表示。1団体のみ登録の場合はセクションヘッダー非表示。わすらもち会は「抽選結果」トグル非表示

### 4.4 バックエンド設計

#### 新規クラス
- `Organization` (Entity)
- `PlayerOrganization` (Entity)
- `OrganizationRepository`
- `PlayerOrganizationRepository`
- `OrganizationService`
- `OrganizationController`
- `OrganizationDto`

#### 既存クラスの変更
- `LotteryDeadlineHelper` — 団体の `deadline_type` に応じて分岐（SAME_DAY / MONTHLY）
- `PracticeParticipantService` — わすらもち会の場合は `PENDING` を使わず即 `WON` / `WAITLISTED`
- `PracticeSessionService` — `organization_id` による自動フィルタ
- `SystemSettingService` — `organization_id` を考慮した設定値取得・更新
- `InviteTokenService` — `organization_id` の保持と登録時の `player_organizations` 作成
- `LineNotificationService` — 送信対象を `player_organizations` でフィルタ、通知設定を団体別に参照
- `NotificationService` — Webプッシュ送信時に団体別の通知設定を参照
- `PushNotificationService` — 送信前に `player_organizations` でフィルタ
- `LineNotificationPreference` (Entity) — `organization_id` 追加
- `PushNotificationPreference` (Entity) — `organization_id` 追加
- 認証インターセプター — ADMIN の団体スコープチェック

### 4.5 データ移行

1. `organizations` テーブル作成 → わすらもち会・北大の2レコード挿入
2. 既存の全 `players` → `player_organizations` にわすらもち会のレコード挿入
3. 既存の全 `practice_sessions` → `organization_id` をわすらもち会に設定
4. 既存の `system_settings` → `organization_id` をわすらもち会に設定（北大用は別途追加）
5. 既存の `invite_tokens` → `organization_id` をわすらもち会に設定
6. 既存の `push_notification_preferences` → `organization_id` をわすらもち会に設定
7. 既存の `line_notification_preferences` → `organization_id` をわすらもち会に設定

## 5. 影響範囲

### 広範囲に影響（要注意）

| 対象 | 影響内容 |
|---|---|
| `practice_sessions` 関連の全API・画面 | `organization_id` フィルタ追加。一覧取得・作成・編集すべてに影響 |
| `system_settings` | キー構造変更（`organization_id` 追加）。全設定値の取得・更新ロジック変更 |
| `LotteryDeadlineHelper` / `LotteryService` | 団体別の締切タイプ分岐。わすらもち会は抽選なし、北大は既存ロジック維持 |
| `PracticeParticipantService` | わすらもち会用の先着順ロジック追加。ステータス遷移が団体で異なる |
| 通知系全般 | 送信対象の団体フィルタリング。Webプッシュ・LINE通知の設定テーブルに `organization_id` 追加 |
| 認証・権限チェック | ADMINの団体スコープ制約追加。インターセプター改修 |

### 影響が小さい

| 対象 | 理由 |
|---|---|
| `matches`（対戦記録） | `practice_session` 経由で間接的に団体に紐づく。直接変更不要。全団体横断で表示 |
| `venues`（会場） | 両団体で共有。変更なし |
| `player_profiles` | 変更なし |
| `bye_activities` | 変更なし |

### DB互換性
- 既存テーブルへのカラム追加（NOT NULL）はデータ移行と同時に行う必要あり
- `system_settings` のユニーク制約変更は既存データ移行後に適用

## 6. 設計判断の根拠

| 判断 | 理由 |
|---|---|
| 2団体固定設計（汎用マルチテナントにしない） | 現時点で3団体目の予定なし。過剰な汎用化を避けシンプルに保つ |
| `player_organizations` 中間テーブルで多対多 | ユーザーが両方の練習会に参加可能なため |
| `players.admin_organization_id` で ADMIN 紐づけ | ADMIN は1団体のみ。中間テーブルの `is_admin` フラグより単純 |
| `system_settings` に `organization_id` 追加 | キーを団体コード入りにする方式より構造的にクリーン |
| バックエンド側で自動フィルタ（フロントから団体指定しない） | フィルタ漏れ防止。フロントエンド改修が少ない |
| 会場（venues）は団体共有 | 同じ会場を両団体が使う実態に合致 |
| わすらもち会は `PENDING` ステータス不使用 | 抽選がないため。空きあり→即`WON`、定員超過→即`WAITLISTED` |
| 締め切りタイプを `organizations.deadline_type` で保持 | 団体固有のルールであり、練習日ごとに変わるものではない |
| 通知設定を団体別にする | 片方の団体の通知だけOFFにしたいケースに対応。グローバルON/OFFは共通で種別トグルが団体別 |
| 管理者通知もADMINは自団体のみ | ADMINは自団体の管理責任のみ。SUPER_ADMINは全団体の通知を受ける |
