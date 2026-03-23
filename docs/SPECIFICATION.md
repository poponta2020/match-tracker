# かるた対戦記録管理システム — 仕様書

> 最終更新: 2026-03-23
> 本書はリバースエンジニアリングにより作成

---

## 1. システム概要

### 1.1 目的

特定のかるた会（サークル）における練習の運営・対戦記録を一元管理するWebアプリケーション。
練習日の出欠管理、対戦組み合わせの生成、試合結果の記録・統計分析を行い、練習の質と運営効率を向上させる。

### 1.2 対象ユーザー

かるた会のメンバー全員が利用する。ロールに応じて利用可能な機能が異なる。

### 1.3 技術スタック

| 層 | 技術 |
|---|---|
| バックエンド | Java 21 / Spring Boot 3.4.1 / Spring Data JPA |
| フロントエンド | React 19 / Vite 7 / Tailwind CSS 3 |
| データベース | PostgreSQL 16 |
| ビルドツール | Gradle (backend) / npm (frontend) |
| デプロイ | Docker / Docker Compose / Render.com |

### 1.4 主要ライブラリ

- **Lombok** — コード生成（Getter/Setter/Builder等）
- **Jsoup** — 伝助HTMLスクレイピング
- **Google Calendar API v3** — カレンダー同期
- **Axios** — HTTP通信
- **React Router v7** — ルーティング
- **Recharts** — グラフ描画
- **Lucide React** — アイコン
- **Web Push API** — ブラウザプッシュ通知（VAPID認証）

---

## 2. ユーザー管理と認証

### 2.1 ロール定義

| ロール | 権限 |
|---|---|
| `SUPER_ADMIN` | 全機能。選手の作成・削除・ロール変更、練習日管理、会場管理 |
| `ADMIN` | 組み合わせ作成、一括結果入力、伝助連携、基本機能 |
| `PLAYER` | 自分の試合記録、出欠登録、閲覧系機能 |

新規登録時のデフォルトロールは `PLAYER`。

### 2.2 認証方式（プロトタイプ仕様）

現在の認証はプロトタイプ段階であり、将来的にJWT等のトークン認証に置き換え予定。

- **ログイン**: 選手名（`name`）+ パスワードで認証
- **パスワード**: BCryptでハッシュ化して保存
- **セッション管理**: ログイン成功時にプレイヤー情報を `localStorage` に保存。トークンは `dummy-token` を使用
- **権限チェック**: リクエストヘッダー `X-User-Role` にロールを付与し、バックエンドの `RoleCheckInterceptor` が `@RequireRole` アノテーションで制御

### 2.3 選手プロパティ

| フィールド | 型 | 必須 | 説明 |
|---|---|---|---|
| `name` | String(100) | Yes | ログインID兼表示名。一意制約 |
| `password` | String(255) | Yes | BCryptハッシュ。最低8文字 |
| `gender` | Enum | Yes | `男性` / `女性` / `その他` |
| `dominantHand` | Enum | Yes | `右` / `左` / `両` |
| `danRank` | Enum | No | `無段`〜`八段` |
| `kyuRank` | Enum | No | `E級`〜`A級` |
| `karutaClub` | String(200) | No | 所属かるた会 |
| `remarks` | Text | No | 備考 |
| `role` | Enum | Yes | デフォルト `PLAYER` |

削除は論理削除（`deleted_at` にタイムスタンプを設定）。

---

## 3. 機能仕様

### 3.1 ホーム画面（ダッシュボード）

ログイン後のメイン画面。以下の情報をまとめて表示する。

| セクション | 内容 |
|---|---|
| ナビゲーションバー | 選手名表示、ハンバーガーメニュー（プロフィール、管理メニュー、Googleカレンダー同期、ログアウト） |
| 繰り上げオファーバナー | 未応答の繰り上げ参加通知がある場合に表示。タップで通知一覧に遷移 |
| 次の練習 | 次回参加予定の練習日・時間・会場・参加試合番号。当日の場合は `TODAY` バッジ表示 |
| 組み合わせ作成リンク | ADMIN以上のみ。次の練習日の組み合わせ作成画面へのショートカット |
| 今月のアクティビティ | 当月の練習参加回数・対戦数をカード形式で表示 |
| 次回の参加者 | 次の練習の参加者一覧（段位順ソート、自分はハイライト） |
| 最近の試合 | 直近5試合の対戦相手・日付・勝敗を表示 |

コールドスタート対応として、3秒以上ローディングが続くと「サーバーを起動中...」メッセージを表示。
タブフォーカス復帰時にデータを自動リフレッシュ。

### 3.2 練習日管理

#### 3.2.1 練習日（PracticeSession）

1日の練習を表すエンティティ。1日に1つのセッションが対応する（日付に一意制約）。

| フィールド | 型 | 必須 | 説明 |
|---|---|---|---|
| `sessionDate` | LocalDate | Yes | 練習日（一意） |
| `totalMatches` | Integer | Yes | その日の試合数 |
| `venueId` | Long | No | 会場ID |
| `notes` | Text | No | 備考 |
| `startTime` | LocalTime | No | 開始時刻 |
| `endTime` | LocalTime | No | 終了時刻 |
| `capacity` | Integer | No | 定員 |

#### 3.2.2 練習参加管理（PracticeParticipant）

各選手の参加登録を試合番号単位で管理する。抽選システムのステータス管理も担う。

| フィールド | 型 | 必須 | 説明 |
|---|---|---|---|
| `sessionId` | Long | Yes | 練習日ID |
| `playerId` | Long | Yes | 選手ID |
| `matchNumber` | Integer | No | 参加する試合番号（1〜7）。NULLの場合は全試合参加 |
| `status` | ParticipantStatus | Yes | 参加ステータス（デフォルト: `WON`）。`PENDING`/`WON`/`WAITLISTED`/`OFFERED`/`DECLINED`/`CANCELLED` |
| `waitlistNumber` | Integer | No | キャンセル待ち番号（WAITLISTED時のみ） |
| `lotteryId` | Long | No | 紐づく抽選実行ID |
| `offeredAt` | LocalDateTime | No | 繰り上げ通知日時 |
| `offerDeadline` | LocalDateTime | No | 繰り上げ応答期限 |
| `respondedAt` | LocalDateTime | No | 繰り上げ応答日時 |

一意制約: `(sessionId, playerId, matchNumber)`

#### 3.2.3 出欠登録画面

- 月単位のカレンダー表示で練習日を一覧
- 各練習日の各試合番号ごとに参加チェックを切り替え
- 月をまたぐナビゲーション機能
- 一括保存
- **抽選済みセッション**: チェックボックスの代わりにステータスバッジ（当選/待ち/応答待 等）を表示。当選者は「取消」ボタンでキャンセル可能

### 3.3 対戦組み合わせ

#### 3.3.1 組み合わせ作成（PairingGenerator）

ADMIN以上が利用可能。練習日・試合番号ごとに対戦ペアを作成する。

**手動組み合わせ:**
- 参加者一覧からドラッグ or 選択してペアを作成
- ペア選択時にリアルタイムで直近90日の対戦履歴を表示
- 同じ選手同士のペアは不可

**自動マッチング:**
- 参加者IDリストを入力として最適なペアリングを生成
- アルゴリズム:
  1. 過去30日のペアリング履歴（`match_pairings` テーブル）と対戦履歴（`matches` テーブル）を統合取得
  2. 同日の前の試合で既に組まれたペアを除外
  3. 各候補ペアに対してスコアを計算（直近対戦からの日数が遠いほど高スコア）
  4. 貪欲法で最高スコアのペアから順に確定
  5. 奇数人数の場合は1名が待機者となる
- スコア計算: `-(100.0 / 最終対戦からの日数)`。初対戦は `0`、同日対戦は `-1000`

**一括保存:**
- 試合番号ごとに全ペアを一括保存（既存の組み合わせは置き換え）
- 未保存の変更がある場合は他の試合番号に切り替え時に警告

#### 3.3.2 組み合わせ（MatchPairing）

| フィールド | 型 | 必須 | 説明 |
|---|---|---|---|
| `sessionDate` | LocalDate | Yes | 練習日 |
| `matchNumber` | Integer | Yes | 試合番号 |
| `player1Id` | Long | Yes | 選手1 ID |
| `player2Id` | Long | Yes | 選手2 ID |
| `createdBy` | Long | Yes | 作成者ID |

#### 3.3.3 組み合わせサマリー（PairingSummary）

百人一首の札番号（01〜00）に基づく試合の進行ルールを表示する。

- 3試合サイクルで札の使い方を決定:
  - **第1試合**: 1の位 — 10種から5種を選択
  - **第2試合**: 抜き — 第1試合の残り5種から3種を選択、1枚を抜く
  - **第3試合**: 十の位 — 残り7種から5種を選択
- テキストをクリップボードにコピー可能

### 3.4 対戦結果管理

#### 3.4.1 対戦記録（Match）

1対1の対戦結果を記録するエンティティ。

| フィールド | 型 | 必須 | 説明 |
|---|---|---|---|
| `matchDate` | LocalDate | Yes | 対戦日 |
| `matchNumber` | Integer | Yes | その日の第N試合目 |
| `player1Id` | Long | Yes | 選手1 ID（常に player1Id < player2Id） |
| `player2Id` | Long | Yes | 選手2 ID |
| `winnerId` | Long | Yes | 勝者ID |
| `scoreDifference` | Integer | Yes | 枚数差（1〜25） |
| `opponentName` | String(100) | No | 未登録の対戦相手名（簡易入力用） |
| `notes` | Text | No | コメント |
| `createdBy` | Long | Yes | 登録者ID |
| `updatedBy` | Long | Yes | 更新者ID |

**ビジネスルール:**
- `player1Id < player2Id` はエンティティの `@PrePersist` / `@PreUpdate` で自動保証（必要に応じてスワップ）
- これにより同じペアの対戦を一意に検索可能

#### 3.4.2 試合結果入力方式

**簡易入力（個人向け）:**
- 対戦日、試合番号、対戦相手名（テキスト入力）、勝敗、枚数差を入力
- システム未登録の選手との対戦も記録可能（`opponentName` フィールド使用）

**詳細入力（一括入力）:**
- ADMIN以上が練習日単位で全ペアの結果を一括入力
- 組み合わせ済みのペアが表示され、勝者と枚数差を入力するだけ
- ペアの変更（選手の差し替え）も同画面で可能

#### 3.4.3 試合結果閲覧

- 日付セレクターで過去の練習日を選択（直近30日分の練習日を表示）
- 試合番号ごとにタブ切り替え
- 各ペアの結果（勝者・枚数差）を表示
- 結果編集・一括入力画面へのリンク

### 3.5 統計機能

#### 3.5.1 選手別統計（実装済み — PlayerDetail画面内）

| 指標 | 計算方法 |
|---|---|
| 総対戦数 | 選手が参加した全試合の件数 |
| 勝利数 | `winnerId` が自分のIDに一致する件数 |
| 勝率 | (勝利数 / 総対戦数) × 100、小数第1位で丸め |

#### 3.5.2 級別統計（実装済み）

対戦相手の `kyuRank`（A級〜E級）ごとに勝率を算出。
以下のフィルタを適用可能:

- 対戦相手の性別
- 対戦相手の利き手
- 期間（開始日〜終了日）

#### 3.5.3 統計専用ページ

**ステータス: 未実装（プレースホルダーのみ）**

`/statistics` ルートは存在するが「実装中...」と表示されるのみ。

### 3.6 会場管理

SUPER_ADMIN のみ操作可能。

| フィールド | 型 | 必須 | 説明 |
|---|---|---|---|
| `name` | String(200) | Yes | 会場名（一意） |
| `defaultMatchCount` | Integer | Yes | デフォルト試合数（1〜20） |

**試合スケジュール（VenueMatchSchedule）:**

会場ごとに試合番号と開始・終了時刻を設定可能。Google カレンダー同期で予定の時間帯を決定するのに使用。

| フィールド | 型 | 必須 | 説明 |
|---|---|---|---|
| `matchNumber` | Integer | Yes | 試合番号 |
| `startTime` | LocalTime | Yes | 開始時刻 |
| `endTime` | LocalTime | Yes | 終了時刻 |

### 3.7 練習参加抽選システム

練習参加者が定員を超えた場合に抽選を行い、公平に参加者を決定するシステム。
詳細な要件定義は [docs/requirements/lottery-system.md](requirements/lottery-system.md) を参照。

#### 3.7.1 抽選の流れ

1. **締め切り前**: 翌月分の練習に対し、プレイヤーは試合ごとに参加希望を登録（ステータス: `PENDING`）
2. **自動抽選**: 前月末日0:00に `LotteryScheduler` が翌月全セッションの抽選を実行
3. **結果確定**: 定員超過の試合では当選（`WON`）・キャンセル待ち（`WAITLISTED`、番号付き）に振り分け
4. **キャンセル→繰り上げ**: 当選者がキャンセルするとキャンセル待ち1番に通知。応答期限内に承諾/辞退

#### 3.7.2 抽選アルゴリズムの特徴

- **連鎖落選**: 同セッション内で先行試合に落選した人は、後続の定員超過試合でも自動落選
- **優先当選**: 同月内の別セッションで落選経験がある人は、次の抽選で当選が保証される
- **応答期限**: min(通知から24時間, 練習日前日23:59) の早い方

#### 3.7.3 抽選関連エンティティ

**LotteryExecution（抽選実行履歴）:**

| フィールド | 型 | 説明 |
|---|---|---|
| `targetYear` / `targetMonth` | int | 対象年月 |
| `sessionId` | Long | 対象セッションID（再抽選時） |
| `executionType` | Enum | `AUTO` / `MANUAL` / `MANUAL_RELOTTERY` |
| `status` | Enum | `SUCCESS` / `FAILED` / `PARTIAL` |
| `executedAt` | LocalDateTime | 実行日時 |
| `details` | Text | 処理詳細 |

**Notification（アプリ内通知）:**

| フィールド | 型 | 説明 |
|---|---|---|
| `playerId` | Long | 通知先プレイヤー |
| `type` | Enum | `LOTTERY_WON` / `LOTTERY_WAITLISTED` / `WAITLIST_OFFER` / `OFFER_EXPIRING` / `OFFER_EXPIRED` |
| `title` / `message` | String | 通知内容 |
| `referenceId` | Long | 参照先ID（参加者レコードID等） |
| `isRead` | Boolean | 既読フラグ |

**PushSubscription（Web Push購読）:**

| フィールド | 型 | 説明 |
|---|---|---|
| `playerId` | Long | プレイヤーID |
| `endpoint` | String | Push APIエンドポイント |
| `p256dhKey` / `authKey` | String | 暗号化キー |

#### 3.7.4 抽選関連画面

| パス | 画面 | 説明 |
|---|---|---|
| `/lottery/results` | 抽選結果確認 | 月別の全セッション・全試合の抽選結果一覧 |
| `/lottery/waitlist` | キャンセル待ち状況 | 自分のキャンセル待ち一覧（番号・ステータス表示） |
| `/lottery/offer-response` | 繰り上げ参加承認 | 「参加する」「参加しない」を選択 |
| `/notifications` | 通知一覧 | 全通知の時系列表示（未読/既読管理、タップで遷移） |

#### 3.7.5 スケジューラ

| スケジューラ | タイミング | 処理 |
|---|---|---|
| `LotteryScheduler` | 毎日0:00（月末日のみ実行）| 翌月分の自動抽選。起動時にリトライチェック |
| `OfferExpiryScheduler` | 5分間隔 | 応答期限切れのOFFERED → DECLINED遷移、次のキャンセル待ちへ通知 |

#### 3.7.6 Densuke同期との整合性

抽選済みセッションの参加者は伝助同期で変更されない。`DensukeImportService` は抽選実行済みセッションを検出するとスキップする。

#### 3.7.7 共通レイアウトの変更

`Layout.jsx` にヘッダーバーを追加:
- ページタイトル表示
- 通知ベルアイコン（未読バッジ付き）→ `/notifications` に遷移
- プロフィールアイコン → `/profile` に遷移

ナビゲーションメニューに「抽選結果」「キャンセル待ち」リンクを追加。

### 3.8 選手プロフィール履歴

段位・級位の変遷を履歴管理する。`valid_from` 〜 `valid_to` の期間で有効なプロフィールを判定。

| フィールド | 型 | 必須 | 説明 |
|---|---|---|---|
| `playerId` | Long | Yes | 対象選手 |
| `grade` | Enum | No | 級位（A〜E） |
| `dan` | Enum | No | 段位（無段〜八段） |
| `karutaClub` | String | No | 所属かるた会 |
| `validFrom` | LocalDate | Yes | 有効開始日 |
| `validTo` | LocalDate | No | 有効終了日。NULLの場合は現在有効 |

---

## 4. 外部連携

### 4.1 伝助（Densuke）連携

#### 4.1.1 概要

伝助はかるた会の出欠調整に広く使われている外部Webサービス。現在は伝助が出欠管理の主体であり、本アプリに参加者データを同期する形で運用している。将来的には本アプリ側で出欠管理を完結させる予定。

#### 4.1.2 仕組み

1. **URL登録**: ADMIN以上が月ごとに伝助のURLを登録（`densuke_urls` テーブルに `year`, `month`, `url` を保存）
2. **スクレイピング**: Jsoupで伝助のHTML（`table.listtbl`）をパースし、日付・試合番号・参加者を抽出
3. **データ取り込み**:
   - 練習セッションが存在しない日付は自動作成
   - 参加者名をアプリの選手名と突合し、一致すれば参加登録
   - 伝助から消えた参加者は自動的にDBからも削除
   - 未登録の名前は `unmatchedNames` としてレスポンスに含め、一括登録→再同期のフローを提供

#### 4.1.3 自動同期

`DensukeSyncScheduler` がバックグラウンドで動作:
- **間隔**: 60秒ごと（初回は30秒後に開始）
- **対象**: 当月 + 翌月のURL
- **処理**: 登録済みURLがあれば自動的にスクレイピング→同期を実行

#### 4.1.4 スクレイピング詳細

伝助のHTMLテーブル構造:
- **ヘッダー行（1行目）**: 列4以降が参加者名
- **データ行（2行目以降）**: 列0がラベル（日付・会場・試合番号を含む）、列4以降が各参加者の出欠
  - CSSクラス `col3` = 参加（○）
  - CSSクラス `col2` + テキスト `△` = 未定
- **日付パターン**: `3/14(金)` 形式
- **試合番号パターン**: `1試合目` 形式
- **会場名パターン**: 日付と試合番号の間のテキスト

### 4.2 Google カレンダー同期

#### 4.2.1 概要

本アプリの練習予定を各選手のGoogleカレンダーに一方向で同期する。

#### 4.2.2 認証フロー

- **ブラウザ**: Google Identity Services (GIS) ライブラリのポップアップ方式
- **モバイル（PWA/ホーム画面追加時）**: GISが動作しないため、OAuth2のリダイレクト方式にフォールバック
  - `sessionStorage` にプレイヤーIDを保存してリダイレクト
  - コールバックでURLハッシュからアクセストークンを取得
- **スコープ**: `https://www.googleapis.com/auth/calendar.events`

#### 4.2.3 同期ロジック

1. 選手が参加予定の今後の練習セッションを取得
2. 各セッションについて:
   - `google_calendar_events` テーブルに既存イベントがあれば更新
   - なければ新規作成
   - アプリから削除されたセッションに対応するGoogleイベントは削除
3. イベントの時間は会場の `VenueMatchSchedule` から決定。スケジュールがなければ終日イベント
4. Google側でイベントが削除されていた場合（404）は再作成

#### 4.2.4 同期結果

レスポンスに含まれる情報:
- 作成件数 / 更新件数 / 削除件数 / 変更なし件数 / エラー件数
- 詳細ログ / エラーメッセージ

---

## 5. 画面一覧とルーティング

### 5.1 公開ページ

| パス | 画面 | 説明 |
|---|---|---|
| `/login` | ログイン | 選手名 + パスワードでログイン |
| `/register` | 新規登録 | アカウント作成（デフォルトロール: PLAYER） |
| `/privacy` | プライバシーポリシー | — |

### 5.2 認証必須ページ

全ページは `PrivateRoute` で保護され、未ログイン時は `/login` にリダイレクト。

| パス | 画面 | 必要ロール | 説明 |
|---|---|---|---|
| `/` | ホーム | ALL | ダッシュボード |
| `/matches` | 対戦一覧 | ALL | 自分の対戦履歴 |
| `/matches/new` | 対戦登録 | ALL | 簡易入力フォーム |
| `/matches/:id` | 対戦詳細 | ALL | 個別の対戦結果 |
| `/matches/:id/edit` | 対戦編集 | ALL | 対戦結果の編集 |
| `/matches/bulk-input/:sessionId` | 一括入力 | ADMIN+ | 練習日単位の結果一括入力 |
| `/matches/results/:sessionId?` | 結果閲覧 | ALL | 練習日の全結果を閲覧 |
| `/practice` | 練習日一覧 | ALL | 練習セッション一覧 |
| `/practice/new` | 練習日作成 | SUPER_ADMIN | 新しい練習日の作成 |
| `/practice/:id` | 練習日詳細 | ALL | 練習日の詳細・参加者 |
| `/practice/:id/edit` | 練習日編集 | SUPER_ADMIN | 練習日の編集 |
| `/practice/participation` | 出欠登録 | ALL | 月別カレンダーで参加登録 |
| `/pairings` | 組み合わせ作成 | ADMIN+ | 手動/自動マッチング |
| `/pairings/summary` | 組み合わせサマリー | ADMIN+ | 札のルール表示 |
| `/players` | 選手管理 | SUPER_ADMIN | 選手一覧・作成・削除 |
| `/players/new` | 選手作成 | SUPER_ADMIN | 新規選手登録 |
| `/players/:id` | 選手詳細 | ALL | 選手のプロフィール・戦績 |
| `/players/:id/edit` | 選手編集 | SUPER_ADMIN | 選手情報の編集 |
| `/profile` | プロフィール | ALL | 自分のプロフィール |
| `/profile/edit` | プロフィール編集 | ALL | 自分のプロフィール編集 |
| `/venues` | 会場管理 | SUPER_ADMIN | 会場一覧 |
| `/venues/new` | 会場作成 | SUPER_ADMIN | 新しい会場の登録 |
| `/venues/edit/:id` | 会場編集 | SUPER_ADMIN | 会場情報の編集 |
| `/statistics` | 統計 | ALL | **未実装** |
| `/lottery/results` | 抽選結果 | ALL | 月別抽選結果一覧 |
| `/lottery/waitlist` | キャンセル待ち | ALL | 自分のキャンセル待ち状況 |
| `/lottery/offer-response` | 繰り上げ参加 | ALL | 繰り上げ承認/辞退 |
| `/notifications` | 通知一覧 | ALL | 全通知の時系列表示 |

### 5.3 ナビゲーション構造

**ボトムナビゲーション（モバイルファースト）:**

| アイコン | ラベル | 遷移先 | 全ロール |
|---|---|---|---|
| Home | ホーム | `/` | Yes |
| PlusCircle | 結果入力 | `/matches/new` | Yes |
| ClipboardList | 対戦結果 | `/matches/results` | Yes |
| Calendar | スケジュール | `/practice` | Yes |
| List | 対戦履歴 | `/matches` | Yes |

**ハンバーガーメニュー（ヘッダー）:**
- プロフィール
- 選手管理（SUPER_ADMIN のみ）
- 会場管理（SUPER_ADMIN のみ）
- Googleカレンダー同期
- ログアウト

---

## 6. データモデル

### 6.1 ER図（テキスト表記）

```
players ──< matches (player1Id, player2Id, winnerId)
players ──< practice_participants (playerId)
players ──< player_profiles (playerId)
players ──< match_pairings (player1Id, player2Id)
players ──< google_calendar_events (playerId)
players ──< notifications (playerId)
players ──< push_subscriptions (playerId)

practice_sessions ──< practice_participants (sessionId)
practice_sessions ──< google_calendar_events (sessionId)
practice_sessions ──< lottery_executions (sessionId)

practice_participants ──> lottery_executions (lotteryId)

venues ──< practice_sessions (venueId)
venues ──< venue_match_schedules (venueId)
```

### 6.2 テーブル一覧

#### players

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| name | VARCHAR(100) | NOT NULL, UNIQUE | 選手名 |
| password | VARCHAR(255) | NOT NULL | BCryptハッシュ |
| gender | VARCHAR | NOT NULL | 男性/女性/その他 |
| dominant_hand | VARCHAR | NOT NULL | 右/左/両 |
| dan_rank | VARCHAR | — | 段位 |
| kyu_rank | VARCHAR | — | 級位 |
| karuta_club | VARCHAR(200) | — | 所属会 |
| remarks | TEXT | — | 備考 |
| role | VARCHAR | NOT NULL, DEFAULT 'PLAYER' | ロール |
| deleted_at | TIMESTAMP | — | 論理削除 |
| created_at | TIMESTAMP | NOT NULL | — |
| updated_at | TIMESTAMP | NOT NULL | — |

インデックス: `idx_name_active(name, deleted_at)`, `idx_deleted_at(deleted_at)`

#### matches

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| match_date | DATE | NOT NULL | 対戦日 |
| match_number | INT | NOT NULL | 試合番号 |
| player1_id | BIGINT | NOT NULL | 選手1（ID小さい方） |
| player2_id | BIGINT | NOT NULL | 選手2（ID大きい方） |
| winner_id | BIGINT | NOT NULL | 勝者ID |
| score_difference | INT | NOT NULL | 枚数差（1〜25） |
| opponent_name | VARCHAR(100) | — | 未登録相手名 |
| notes | TEXT | — | コメント |
| created_by | BIGINT | NOT NULL | 登録者 |
| updated_by | BIGINT | NOT NULL | 更新者 |
| created_at | TIMESTAMP | NOT NULL | — |
| updated_at | TIMESTAMP | NOT NULL | — |

インデックス: `idx_matches_date`, `idx_matches_date_player1`, `idx_matches_date_player2`, `idx_matches_winner`, `idx_matches_date_match_number`

#### match_pairings

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| session_date | DATE | NOT NULL | 練習日 |
| match_number | INT | NOT NULL | 試合番号 |
| player1_id | BIGINT | NOT NULL | 選手1 |
| player2_id | BIGINT | NOT NULL | 選手2 |
| created_by | BIGINT | NOT NULL | 作成者 |
| created_at | TIMESTAMP | NOT NULL | — |
| updated_at | TIMESTAMP | NOT NULL | — |

#### practice_sessions

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| session_date | DATE | NOT NULL, UNIQUE | 練習日 |
| total_matches | INT | NOT NULL | 試合数 |
| venue_id | BIGINT | — | 会場ID |
| notes | TEXT | — | 備考 |
| start_time | TIME | — | 開始時刻 |
| end_time | TIME | — | 終了時刻 |
| capacity | INT | — | 定員 |
| created_by | BIGINT | NOT NULL | — |
| updated_by | BIGINT | NOT NULL | — |
| created_at | TIMESTAMP | NOT NULL | — |
| updated_at | TIMESTAMP | NOT NULL | — |

インデックス: `idx_session_date`

#### practice_participants

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| session_id | BIGINT | NOT NULL | 練習日ID |
| player_id | BIGINT | NOT NULL | 選手ID |
| match_number | INT | — | 試合番号（NULLは全試合） |
| status | VARCHAR | NOT NULL, DEFAULT 'WON' | 参加ステータス（PENDING/WON/WAITLISTED/OFFERED/DECLINED/CANCELLED） |
| waitlist_number | INT | — | キャンセル待ち番号 |
| lottery_id | BIGINT | — | 抽選実行ID |
| offered_at | TIMESTAMP | — | 繰り上げ通知日時 |
| offer_deadline | TIMESTAMP | — | 繰り上げ応答期限 |
| responded_at | TIMESTAMP | — | 繰り上げ応答日時 |
| created_at | TIMESTAMP | NOT NULL | — |
| updated_at | TIMESTAMP | NOT NULL | — |

一意制約: `uk_session_player_match(session_id, player_id, match_number)`

#### player_profiles

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| player_id | BIGINT | NOT NULL | 選手ID |
| dan | VARCHAR | — | 段位 |
| grade | VARCHAR | — | 級位 |
| karuta_club | VARCHAR | — | 所属会 |
| valid_from | DATE | NOT NULL | 有効開始日 |
| valid_to | DATE | — | 有効終了日（NULL=現在有効） |
| created_at | TIMESTAMP | NOT NULL | — |
| updated_at | TIMESTAMP | NOT NULL | — |

インデックス: `idx_player_date(player_id, valid_from)`, `idx_valid_to(valid_to)`

#### venues

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| name | VARCHAR(200) | NOT NULL, UNIQUE | 会場名 |
| default_match_count | INT | NOT NULL | デフォルト試合数 |
| created_at | TIMESTAMP | NOT NULL | — |
| updated_at | TIMESTAMP | NOT NULL | — |

#### venue_match_schedules

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| venue_id | BIGINT | NOT NULL | 会場ID |
| match_number | INT | NOT NULL | 試合番号 |
| start_time | TIME | NOT NULL | 開始時刻 |
| end_time | TIME | NOT NULL | 終了時刻 |

#### google_calendar_events

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| player_id | BIGINT | NOT NULL | 選手ID |
| session_id | BIGINT | NOT NULL | 練習日ID |
| google_event_id | VARCHAR | NOT NULL | GoogleイベントID |
| synced_session_updated_at | TIMESTAMP | — | 同期時のセッション更新日時 |

#### densuke_urls

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| year | INT | NOT NULL | 年 |
| month | INT | NOT NULL | 月 |
| url | VARCHAR | NOT NULL | 伝助URL |
| created_at | TIMESTAMP | NOT NULL | — |
| updated_at | TIMESTAMP | NOT NULL | — |

一意制約: `(year, month)`

#### lottery_executions

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| target_year | INT | NOT NULL | 対象年 |
| target_month | INT | NOT NULL | 対象月 |
| session_id | BIGINT | — | 対象セッションID（再抽選時） |
| execution_type | VARCHAR | NOT NULL | AUTO/MANUAL/MANUAL_RELOTTERY |
| executed_by | BIGINT | — | 実行者（自動はNULL） |
| executed_at | TIMESTAMP | NOT NULL | 実行日時 |
| status | VARCHAR | NOT NULL | SUCCESS/FAILED/PARTIAL |
| details | TEXT | — | 処理詳細 |

#### notifications

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| player_id | BIGINT | NOT NULL | 通知先プレイヤー |
| type | VARCHAR | NOT NULL | LOTTERY_WON/LOTTERY_WAITLISTED/WAITLIST_OFFER/OFFER_EXPIRING/OFFER_EXPIRED |
| title | VARCHAR | NOT NULL | 通知タイトル |
| message | TEXT | — | 通知本文 |
| reference_id | BIGINT | — | 参照先ID |
| is_read | BOOLEAN | NOT NULL, DEFAULT FALSE | 既読フラグ |
| created_at | TIMESTAMP | NOT NULL | — |

#### push_subscriptions

| カラム | 型 | 制約 | 説明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | — |
| player_id | BIGINT | NOT NULL | プレイヤーID |
| endpoint | TEXT | NOT NULL | Push APIエンドポイント |
| p256dh_key | VARCHAR | — | 暗号化キー |
| auth_key | VARCHAR | — | 認証キー |
| user_agent | VARCHAR | — | ブラウザ情報 |
| created_at | TIMESTAMP | NOT NULL | — |
| updated_at | TIMESTAMP | NOT NULL | — |

---

## 7. API仕様

### 7.1 共通仕様

- ベースパス: `/api`
- レスポンス形式: JSON
- 認証: `X-User-Id` / `X-User-Role` ヘッダー（プロトタイプ）
- CORS: `app.cors.allowed-origins` プロパティで設定
- エラーレスポンス: `{ "message": "エラーメッセージ" }`

### 7.2 選手管理 (`/api/players`)

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| POST | `/login` | Public | ログイン |
| GET | `/` | ALL | 全アクティブ選手取得 |
| GET | `/{id}` | ALL | ID指定で取得 |
| GET | `/search?name=` | ALL | 名前部分一致検索 |
| GET | `/role/{role}` | ALL | ロール別取得 |
| GET | `/count` | ALL | アクティブ選手数 |
| POST | `/` | SUPER_ADMIN | 選手作成 |
| PUT | `/{id}` | ALL | 選手更新 |
| DELETE | `/{id}` | SUPER_ADMIN | 選手論理削除 |
| PUT | `/{id}/role?role=` | SUPER_ADMIN | ロール変更 |

### 7.3 対戦結果 (`/api/matches`)

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `/?date=` | ALL | 日付別取得 |
| GET | `/exists?date=` | ALL | 指定日に対戦存在確認 |
| GET | `/{id}` | ALL | ID指定で取得 |
| GET | `/player/{id}/date/{date}/match/{num}` | ALL | 選手・日付・試合番号で取得 |
| GET | `/player/{id}` | ALL | 選手の対戦履歴（フィルタ付き） |
| GET | `/player/{id}/period?startDate=&endDate=` | ALL | 期間指定取得 |
| GET | `/player/{id}/period/count?startDate=&endDate=` | ALL | 期間内件数（軽量） |
| GET | `/between?player1Id=&player2Id=` | ALL | 2選手間の対戦履歴 |
| GET | `/player/{id}/statistics` | ALL | 選手統計（勝率等） |
| GET | `/player/{id}/statistics-by-rank` | ALL | 級別統計 |
| POST | `/` | ALL | 簡易登録 |
| POST | `/detailed` | ALL | 詳細登録 |
| PUT | `/{id}` | ALL | 簡易更新 |
| PUT | `/{id}/detailed` | ALL | 詳細更新 |
| DELETE | `/{id}` | ALL | 削除 |

### 7.4 組み合わせ (`/api/match-pairings`)

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `/date?date=&light=` | ALL | 日付別取得 |
| GET | `/date-and-match?date=&matchNumber=` | ALL | 日付+試合番号で取得 |
| GET | `/exists?date=&matchNumber=` | ALL | 存在確認 |
| GET | `/pair-history?player1Id=&player2Id=&sessionDate=` | ALL | ペアの対戦履歴 |
| POST | `/` | ADMIN+ | 単一作成 |
| POST | `/batch?date=&matchNumber=` | ADMIN+ | 一括作成 |
| POST | `/auto-match` | ALL | 自動マッチング |
| PUT | `/{id}/player?newPlayerId=&side=` | ALL | 選手差し替え |
| DELETE | `/{id}` | ALL | 単一削除 |
| DELETE | `/date-and-match?date=&matchNumber=` | ALL | 日付+試合番号の全削除 |

### 7.5 練習日 (`/api/practice-sessions`)

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `/` | ALL | 全セッション取得 |
| GET | `/{id}` | ALL | ID指定で取得 |
| GET | `/date?date=` | ALL | 日付指定で取得（参加者込み） |
| GET | `/range?startDate=&endDate=` | ALL | 期間指定取得 |
| GET | `/year-month?year=&month=` | ALL | 年月指定取得 |
| GET | `/year-month/summary?year=&month=` | ALL | 年月サマリー（軽量） |
| GET | `/upcoming?fromDate=` | ALL | 今後のセッション |
| GET | `/next-participation?playerId=` | ALL | 次の参加予定 |
| GET | `/dates?fromDate=` | ALL | 日付一覧（軽量） |
| GET | `/exists?date=` | ALL | 日付存在確認 |
| GET | `/{id}/participants` | ALL | 参加者一覧 |
| GET | `/participations/player/{id}?year=&month=` | ALL | 月別参加状況 |
| GET | `/participations/player/{id}/status?year=&month=` | ALL | 月別参加状況（抽選ステータス付き） |
| POST | `/` | SUPER_ADMIN | セッション作成 |
| PUT | `/{id}` | SUPER_ADMIN | セッション更新 |
| PUT | `/{id}/total-matches?totalMatches=` | SUPER_ADMIN | 試合数変更 |
| DELETE | `/{id}` | SUPER_ADMIN | セッション削除 |
| POST | `/participations` | ALL | 出欠一括登録 |
| PUT | `/{sid}/matches/{num}/participants` | SUPER_ADMIN | 試合別参加者設定 |
| POST | `/date/{date}/matches/{num}/participants/{pid}` | ADMIN+ | 参加者追加 |
| DELETE | `/{sid}/matches/{num}/participants/{pid}` | ADMIN+ | 参加者削除 |

### 7.6 伝助連携 (`/api/practice-sessions`)

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| POST | `/import-densuke` | ADMIN+ | 伝助URLからインポート |
| POST | `/register-and-sync-densuke` | ADMIN+ | 未登録者一括登録+再同期 |
| GET | `/densuke-url?year=&month=` | ALL | 伝助URL取得 |
| PUT | `/densuke-url` | ADMIN+ | 伝助URL登録・更新 |
| POST | `/sync-densuke` | ADMIN+ | 年月指定で伝助同期 |

### 7.7 選手プロフィール (`/api/player-profiles`)

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `/current/{playerId}` | ALL | 現在のプロフィール |
| GET | `/at-date/{playerId}?date=` | ALL | 特定日時点のプロフィール |
| GET | `/history/{playerId}` | ALL | プロフィール履歴 |
| POST | `/` | ALL | プロフィール作成 |
| PUT | `/{profileId}/valid-to?validTo=` | ALL | 有効終了日設定 |
| DELETE | `/{profileId}` | ALL | プロフィール削除 |

### 7.8 会場管理 (`/api/venues`)

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `/` | ALL | 全会場取得 |
| GET | `/{id}` | ALL | ID指定で取得 |
| POST | `/` | ALL | 会場作成 |
| PUT | `/{id}` | ALL | 会場更新 |
| DELETE | `/{id}` | ALL | 会場削除 |

### 7.9 Googleカレンダー (`/api/google-calendar`)

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| POST | `/sync` | ALL | カレンダー同期実行 |

### 7.10 抽選 (`/api/lottery`)

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| POST | `/execute` | SUPER_ADMIN | 手動抽選実行（年月指定） |
| POST | `/re-execute/{sessionId}` | ADMIN+ | セッション再抽選 |
| GET | `/results?year=&month=` | ALL | 月別抽選結果取得 |
| GET | `/results/{sessionId}` | ALL | セッション別抽選結果 |
| GET | `/my-results?year=&month=&playerId=` | ALL | 自分の抽選結果 |
| POST | `/cancel` | ALL | 当選キャンセル（participantId指定） |
| POST | `/respond-offer` | ALL | 繰り上げへの応答（participantId, accept） |
| GET | `/waitlist-status?playerId=` | ALL | キャンセル待ち状況 |
| PUT | `/admin/edit-participants` | ADMIN+ | 管理者による手動編集 |
| GET | `/executions?year=&month=` | ALL | 抽選実行履歴 |

### 7.11 通知 (`/api/notifications`)

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `/?playerId=` | ALL | 通知一覧取得 |
| GET | `/unread-count?playerId=` | ALL | 未読通知数 |
| PUT | `/{id}/read` | ALL | 通知既読化 |

### 7.12 Push購読 (`/api/push-subscriptions`)

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `/vapid-public-key` | ALL | VAPID公開鍵取得 |
| POST | `/subscribe` | ALL | Push購読登録 |
| DELETE | `/unsubscribe` | ALL | Push購読解除 |

### 7.13 ヘルスチェック

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `/ping` | Public | `{"status": "ok"}` |

---

## 8. デプロイ構成

### 8.1 環境プロファイル

| プロファイル | 用途 | DB |
|---|---|---|
| default | ローカル開発 | PostgreSQL (localhost) |
| docker | Docker Compose | PostgreSQL (コンテナ) |
| render | 本番 (Render.com) | PostgreSQL (外部) |
| dev | 開発（デバッグログ有効） | PostgreSQL |
| test | テスト | Testcontainers |

### 8.2 Docker構成

- `Dockerfile`: Java 21ベース、Gradleビルド
- `docker-compose.yml`: PostgreSQL 16 + Spring Bootアプリケーション
- `docker-compose-dev.yml`: 開発用構成

### 8.3 Render.com構成

`render.yaml` でIaCデプロイを定義。

---

## 9. 未実装・今後の予定

| 項目 | 状態 | 備考 |
|---|---|---|
| 統計専用ページ (`/statistics`) | 未実装 | プレースホルダーのみ |
| JWT認証 | 未実装 | 現在はダミートークン+ヘッダーベースの権限チェック |
| 本アプリでの出欠管理完結 | 計画中 | 現在は伝助からの同期に依存。利用者の移行完了後に実装予定 |
| Web Push通知のVAPID署名 | 完了 | `nl.martijndwars:web-push:5.1.1` + BouncyCastleによるRFC 8030準拠のVAPID署名付き実装 |
| Service Worker | 未実装 | Push通知受信用。現在はアプリ内通知のみ |
| 通知設定画面 | 未実装 | Push通知の許可/拒否設定UI |
| 管理者用抽選管理画面 | 部分実装 | PracticeListモーダル内に再抽選ボタンあり。専用管理画面は未作成 |
