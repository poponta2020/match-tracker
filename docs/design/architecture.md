# アーキテクチャ

> **責務:** システム構成・レイヤー・認証認可・権限・API共通仕様・デプロイの設計

## 技術スタック

| 層 | 技術 |
|---|---|
| バックエンド | Java 21 / Spring Boot 3.4.1 / Spring Data JPA |
| フロントエンド | React 19 / Vite 7 / Tailwind CSS 3 |
| データベース | PostgreSQL 16 |
| ビルドツール | Gradle (backend) / npm (frontend) |
| デプロイ | Docker / Docker Compose / Render.com |

**バックエンド**:
- Java 21
- Spring Boot 3.4.1
- Spring Data JPA
- Hibernate（PostgreSQLDialect）
- PostgreSQL 16（Render.com ホスティング）
- Gradle
- Jsoup 1.17.2（伝助HTMLスクレイピング）
- biweekly 0.6.7（iCalフィード生成）
- Web Push（nl.martijndwars:web-push:5.1.1 + Bouncy Castle）
- Testcontainers（テスト用PostgreSQL）
- Apache HttpClient 4.5（会場予約プロキシの会場サイト中継）

**フロントエンド**:
- React 19
- React Router v7
- Axios
- Tailwind CSS
- Vite
- Recharts（統計グラフ）
- Lucide React（アイコン）

## 主要ライブラリ

- **Lombok** — コード生成（Getter/Setter/Builder等）
- **Jsoup** — 伝助HTMLスクレイピング
- **Apache HttpClient 4.5** — 会場予約プロキシの会場サイト中継
- **biweekly** — iCalフィード生成（カレンダー購読）
- **Axios** — HTTP通信
- **React Router v7** — ルーティング
- **Recharts** — グラフ描画
- **Lucide React** — アイコン
- **Web Push API** — ブラウザプッシュ通知（VAPID認証）
- **LINE Messaging API** — LINE通知（Push/Reply API、Webhook署名検証）

## システム構成

```
[Browser] ←→ [React SPA (Port 5173)]
              ↓ HTTP
              [Spring Boot API (Port 8080)]
              ↓ JDBC
              [PostgreSQL 16 (Render.com)]
```

## レイヤー構成

```
Controller Layer (REST API)
    ↓
Service Layer (ビジネスロジック)
    ↓
Repository Layer (データアクセス)
    ↓
Entity Layer (JPA Entity)
```

## 認証・認可

**現在の実装**:
- 簡易的なヘッダーベース認証（`X-User-Role`, `X-User-Id`）
- パスワード平文比較
- `@RequireRole` アノテーション + `RoleCheckInterceptor`（ロール検証 + ユーザーID伝播）
- `AdminScopeValidator`（`util/AdminScopeValidator.java`）— ADMINの団体スコープ検証ユーティリティ。ADMINが自団体以外のリソースを操作しようとした場合に `ForbiddenException` をスロー。各Controllerから共通利用
- 対戦組み合わせ書き込みAPI（`MatchPairingController`）のスコープ検証は `validateScopeByDate` / `validateScopeByPairingId` で実施。SUPER_ADMIN はスコープなし、ADMIN は `adminOrganizationId` で照合、PLAYER は `OrganizationService.getPlayerOrganizationIds(currentUserId)` で取得した所属団体IDリストに対象セッション／ペアリングの組織IDが含まれているかを照合する。PLAYER に開放している書き込み操作は、作成・一括作成・自動マッチング・選手差し替え・ロック/解除に加え、**全削除（`deleteByDateAndMatchNumber`）・結果込みリセット（`resetWithResult`）**も含む（いずれも上記スコープ検証を通過する）
- 練習日への参加者追加 API（`PracticeSessionController.addParticipantToMatch`、`POST /api/practice-sessions/date/{date}/matches/{matchNumber}/participants/{playerId}`）も PLAYER に開放。スコープ検証は `PracticeSessionService.checkScopeByDate(date, role, adminOrganizationId, currentUserId)` で実施し、`MatchPairingController.validateScopeByDate` ＋ `resolveOrganizationIdForScopedWrite` と同一の考え方（SUPER_ADMIN はスコープなし、ADMIN は自団体セッションの存在で照合、PLAYER は所属団体のセッションが対象日付に存在するかで照合、不一致は 403）。**同メソッドは検証だけでなく書き込み対象の `organizationId` を一意に確定して返し、`PracticeParticipantService.addParticipantToMatch(date, matchNumber, playerId, organizationId)` がその団体スコープ（`findBySessionDateAndOrganizationId`）でセッションを特定する**（検証と実更新の対象セッションを一致させ、同日に複数団体のセッションがある場合に別団体セッションへ書き込む対象ずれを防ぐ）。PLAYER で同日に複数の所属団体のセッションがあり一意に定まらない場合は 403。SUPER_ADMIN（`organizationId=null`）は従来どおり日付のみで特定する。同メソッドはセッションIDベースの `checkAdminScope`（ADMIN専用の他エンドポイント用）とは別物
- 選手起点の最近ペアリング取得 `GET /api/match-pairings/player/{playerId}`（`MatchPairingService.getRecentByPlayerId` / `MatchPairingRepository.findRecentByPlayerId`）は、`@RequireRole` 全ロールの参照系で団体スコープを適用しない（閲覧は全選手可。選手別履歴の参照という用途が getByDate 等の組織限定取得と異なるため）。`(player1Id = :playerId OR player2Id = :playerId)` で `sessionDate DESC, matchNumber DESC` 順・`Pageable` で直近30件に制限し、選手名は `collectPlayerNames` で一括解決（N+1回避）。動画倉庫の登録モーダル「選手起点」で結果未入力（`match_pairings` のみ）の試合も選択肢に含めるための軽量レスポンス（`recentMatches`・試合結果は付与しない）
- フロントエンド `RoleRoute`（`components/RoleRoute.jsx`）— ルートレベルのロール保護コンポーネント。`PrivateRoute`（ログインチェック）の内側で使用し、権限不足時はホームにリダイレクト
- `PrivateRoute` は未認証時に `/login` へリダイレクトする際、遷移元の `location`（パス＋クエリパラメータ）を `state.from` に保持する。`Login` はログイン成功後に `state.from` があれば元URLへ復帰する（LINEリッチメニュー等の外部導線で未ログイン時に正しく復帰するため）

**TODO**:
- Spring Security + JWT導入
- BCryptパスワードハッシュ化
- セッション管理

## 権限設計

| ロール | 説明 |
|-------|------|
| SUPER_ADMIN | 最上位管理者。全機能アクセス可能。選手登録・削除、練習日作成・削除、ロール変更、抽選実行可能。 |
| ADMIN | 管理者。対戦組み合わせ作成・削除、セッション再抽選可能。練習日作成・削除は不可。 |
| PLAYER | 一般選手。基本機能（試合記録、参加登録、閲覧、所属団体の対戦組み合わせ作成・編集）。組み合わせの削除は不可。 |

### 権限マトリックス

| 機能カテゴリ | 機能 | SUPER_ADMIN | ADMIN | PLAYER |
|------------|------|------------|-------|--------|
| 認証 | ログイン | ○ | ○ | ○ |
| 選手管理 | 選手登録 | ○ | × | × |
| | 選手削除 | ○ | × | × |
| | ロール変更 | ○ | × | × |
| | 選手更新 | ○ | ○ | ○ |
| | 選手一覧・詳細 | ○ | ○ | ○ |
| 試合記録 | 試合登録・更新・削除 | ○ | ○ | ○ |
| | 試合一覧・詳細・統計 | ○ | ○ | ○ |
| 練習日 | 練習日作成・更新・削除 | ○ | × | × |
| | 練習日一覧・詳細 | ○ | ○ | ○ |
| 練習参加 | 参加登録 | ○ | ○ | ○ |
| | 参加状況閲覧 | ○ | ○ | ○ |
| 対戦組み合わせ | 組み合わせ作成・選手差し替え | ○ | ○（自団体のみ） | ○（所属団体のみ） |
| | 自動マッチング | ○ | ○（自団体のみ） | ○（所属団体のみ） |
| | 組み合わせ削除（個別/一括/結果込みリセット） | ○ | ○（自団体のみ） | × |
| | 組み合わせロック/解除（手動ロック） | ○ | ○（自団体のみ） | ○（所属団体のみ） |
| | 組み合わせ閲覧 | ○ | ○ | ○ |
| 会場管理 | 会場CRUD | ○ | ○ | ○ |
| 抽選 | 月別抽選実行 | ○ | ○（自団体のみ） | × |
| | 抽選結果確定 | ○ | ○（自団体のみ） | × |
| | セッション再抽選 | ○ | ○ | × |
| | 参加者手動編集 | ○ | ○ | × |
| | 抽選結果閲覧 | ○ | ○ | ○ |
| | キャンセル・繰り上げ応答 | ○ | ○ | ○ |
| 通知 | 通知閲覧・既読 | ○ | ○ | ○ |
| メンター | メンター指名・承認・拒否・解除 | ○ | ○ | ○ |
| | メンターコメント投稿・編集・削除 | ○ | ○ | ○ |
| カレンダー購読 | URL取得・再発行・表示名カスタマイズ | ○ | ○ | ○ |
| Web Push | 購読管理 | ○ | ○ | ○ |
| LINE通知 | LINE連携有効化/無効化 | ○ | ○ | ○ |
| | 通知種別ON/OFF設定 | ○ | ○ | ○ |
| | チャネル管理（登録・無効化・強制解除） | ○ | × | × |
| | 抽選結果/組み合わせLINE一括送信 | ○ | ○ | × |
| | スケジュール型通知設定 | ○ | ○ | × |

### 権限の実装方法

**バックエンド**:
- `@RequireRole` アノテーションをコントローラーメソッドに付与
- `RoleCheckInterceptor` がリクエストヘッダー `X-User-Role` と `X-User-Id` を検証
- `@RequireRole` 付きエンドポイントでは `X-User-Id` 必須（リクエスト属性 `currentUserId` / `currentUserRole` にセット）
- 権限不足の場合は `403 Forbidden`
- ADMIN時は `RoleCheckInterceptor` が `adminOrganizationId` をリクエスト属性にセット
- `AdminScopeValidator.validateScope()` でADMINの団体スコープを統一的に検証（練習日・組み合わせ・抽選・LINE送信・抜け番・伝助・システム設定）

**フロントエンド**:
- `AuthContext` で `currentPlayer.role` を管理
- `BottomNavContext` でボトムナビゲーションの表示/非表示を管理（`isVisible` state、デフォルト `true`）。`App.jsx` で `BottomNavProvider` として全体をラップ。`Layout.jsx` が `isVisible` を参照してスライドアニメーション（`translate-y-0` ⇔ `translate-y-full`）で切り替え。position:fixedの要素自体にtransformを直接適用するとiOS Safariでfixedが解除されスクロールに追随してしまうため、fixed指定用の外側`<nav>`（transformなし）とスライドアニメーション用の内側`<div>`（transform）に分離。非表示時はスクリーンリーダー向けに`aria-hidden`、キーボード操作向けに各リンクの`tabIndex`を`-1`に、クリック/タップ判定は内側divの`pointer-events`切り替えで制御（外側の静的なfixed領域が常時クリックを奪わないよう`pointer-events-none`固定）
- Axiosインターセプターで全リクエストに `X-User-Role` ヘッダー追加
- `isSuperAdmin()`, `isAdmin()` などのヘルパー関数で条件付き表示
- `RoleRoute` コンポーネントで管理者専用ルートを保護（`/admin/*`, `/players/*`, `/practice/new` 等）

## API共通仕様

- ベースURL: `http://localhost:8080/api`
- ベースパス: `/api`
- レスポンス形式: JSON
- タイムゾーン: 全ての日時処理は `JstDateTimeUtil`（`util/JstDateTimeUtil.java`）を使用してJST（Asia/Tokyo）で統一。`LocalDate.now()` / `LocalDateTime.now()` は直接使用せず、`JstDateTimeUtil.today()` / `JstDateTimeUtil.now()` を使用する。
- 認証: `X-User-Id` / `X-User-Role` ヘッダー（プロトタイプ）
- CORS: `app.cors.allowed-origins` プロパティで設定
- リバースプロキシ配下デプロイ: `server.forward-headers-strategy=framework` を有効化し、`X-Forwarded-Proto` / `X-Forwarded-Host` / `X-Forwarded-Port` を解釈する。これがないと TLS 終端サービス (Render 等) 配下で `request.getScheme()` などが内部値を返し、会場予約プロキシ画面の same-origin form POST が CORS で誤拒否される。

**リクエストヘッダー**:
- `Content-Type: application/json`
- `X-User-Role: {SUPER_ADMIN|ADMIN|PLAYER}` (現在の簡易実装)

**レスポンス形式**:
```json
// 成功時
{
  "id": 1,
  "name": "田中太郎",
  ...
}

// エラー時
{
  "message": "エラーメッセージ",
  "timestamp": "2025-11-17T12:00:00"
}
```

⚠要確認: エラーレスポンスの形式について、SPECでは `{ "message": "エラーメッセージ" }`（`timestamp` フィールドなし）、DESIGNでは上記のとおり `timestamp` フィールドありと記載が食い違う。

**HTTPステータスコード**:
- 200: OK
- 201: Created
- 204: No Content
- 400: Bad Request
- 401: Unauthorized
- 403: Forbidden
- 404: Not Found
- 409: Conflict
- 500: Internal Server Error

## システム設定API (`/api/system-settings`)

### GET /api/system-settings
**説明**: 全設定取得
**権限**: SUPER_ADMIN, ADMIN
**クエリパラメータ**: `organizationId`（任意。SUPER_ADMINが団体別設定を取得する場合に指定。ADMINは自団体に固定）

### GET /api/system-settings/{key}
**説明**: 設定値取得
**権限**: なし

### PUT /api/system-settings/{key}
**説明**: 設定値更新
**権限**: SUPER_ADMIN, ADMIN
**リクエスト**:
```json
{
  "value": "3",
  "organizationId": "1"
}
```
`organizationId` は SUPER_ADMIN が団体別設定を更新する場合に必須。ADMINはリクエスト値に関わらず自団体に固定される。

**利用可能な設定キー**:
| キー | 説明 | デフォルト値 |
|------|------|-------------|
| `lottery_deadline_days_before` | 締切日数（月初から何日前） | `0` |
| `lottery_normal_reserve_percent` | 一般枠の最低保証割合（%） | `30` |

## ヘルスチェック

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `/ping` | Public | `{"status": "ok"}` |

## デプロイ構成

### 環境プロファイル

| プロファイル | 用途 | DB |
|---|---|---|
| default | ローカル開発 | PostgreSQL (localhost) |
| docker | Docker Compose | PostgreSQL (コンテナ) |
| render | 本番 (Render.com) | PostgreSQL (外部) |
| dev | 開発（デバッグログ有効） | PostgreSQL |
| test | テスト | Testcontainers |

### Docker構成

- `Dockerfile`: Java 21ベース、Gradleビルド
- `docker-compose.yml`: PostgreSQL 16 + Spring Bootアプリケーション
- `docker-compose-dev.yml`: 開発用構成

### Render.com構成

`render.yaml` でIaCデプロイを定義。

## 開発環境セットアップ

### 前提条件
- Java 21
- Node.js 18+
- PostgreSQL 16（Render.comに接続、環境変数必須）

### バックエンド起動
```bash
cd karuta-tracker
DB_URL="<CLAUDE.local.md の DB_URL>" \
DB_USERNAME="<CLAUDE.local.md の DB_USERNAME>" \
DB_PASSWORD="<CLAUDE.local.md の DB_PASSWORD>" \
./gradlew bootRun
```
→ `http://localhost:8080`

### フロントエンド起動
```bash
cd karuta-tracker-ui
npm install
npm run dev
```
→ `http://localhost:5173`

## 設計上の重要ポイント

### player1_id < player2_id 制約
- `Match` エンティティで `@PrePersist`/`@PreUpdate` により自動保証
- データベースインデックスの効率化
- クエリ簡略化

### 論理削除
- `Player.deletedAt` により選手を論理削除
- 削除済み選手は更新・ログイン不可
- 過去の試合記録は保持（データ整合性）
- `ByeActivity.deletedAt` により抜け番活動記録を論理削除
- 一括作成時（`createBatch`）は既存レコードを論理削除してから再作成

### 試合ごと参加登録
- `PracticeParticipant.matchNumber` により、各練習日の各試合ごとに参加登録可能
- フロントエンドで試合ごとのチェックボックスを実装
- `PracticeSessionDto.matchParticipants` で試合ごとの参加者名リスト提供
  - バックエンド（`PracticeSessionService.enrichDtoWithMatchDetails`）は `DECLINED` / `WAITLIST_DECLINED` 以外の全ステータス（`WON` / `PENDING` / `WAITLISTED` / `OFFERED` / `CANCELLED`）を含めて返す
  - 各エントリは `status` フィールドを持ち、フロント側で用途に応じてフィルタする責務を持つ
- **組み合わせ対象 / 抜け番算出ルール**:
  - 団体の運用設定により対象ステータスを切り替える（バックエンド・フロント共通ルール）
    - **抽選あり運用** (`DeadlineType.MONTHLY` かつ締め切りなしモードでない): `WON` のみ
    - **抽選なし運用** (`DeadlineType.SAME_DAY` または `MONTHLY` + `isNoDeadline=true`): `WON` + `PENDING`
  - 判定ヘルパー: `LotteryDeadlineHelper.isLotteryDisabled(organizationId)` が抽選なし運用なら `true`
  - バックエンド `MatchPairingService.loadActiveParticipantIdsForMatch()` は上記ヘルパーで対象ステータスを切り替える
  - フロント側は `PracticeSessionDto.pairingIncludesPending`（バックエンドが `isLotteryDisabled` を反映して返すフラグ）を使い同一ルールで判定する（`PairingGenerator.jsx` / `BulkResultInput.jsx` の `byePlayersLogic.js` / `MatchResultsView.jsx`）
  - 抽選あり運用で `PENDING` を含めると、抽選前の参加希望者まで自動マッチング対象になり抽選結果をバイパスしてしまうため、組織設定に応じた判定が必須

### 自動マッチングアルゴリズム
- 過去30日の対戦履歴を基にスコアリング
- 日数が経過するほど高スコア（再対戦しやすい）
- 初対戦: スコア0（最優先）
- 同日既存対戦を除外
- シャッフル + 貪欲法で最適ペアリング

### 直近対戦履歴（recentMatches）生成ロジック
- `MatchPairingService` の `getPairRecentMatches` / `enrichWithRecentMatches` / `autoMatch` の3経路で表示用 `recentMatches` を生成する
- ソース: `MatchPairingテーブル`（ペアリング履歴）+ `Matchテーブル`（試合履歴）を過去30日分マージ
- 同日の他試合（自分の試合番号以外すべて）で組まれたペアは当日日付として `recentMatches` に含める
  - 前方試合番号（例: 試合3編集中の試合1・2）だけでなく、後方試合番号（例: 試合4・5）も検知対象
  - 自分と同じ試合番号のペアは `recentMatches` から除外（編集中の自分自身を警告対象にしない）
- フロントエンドは `recentMatches[0].matchDate === sessionDate` の場合に `⚠今日` を赤字太字で警告表示
- ※ 自動マッチングのペナルティ計算（`calculatePairScore`）と除外判定（`getTodayPairings`）は表示用ロジックとは別責務で、現状「前の試合番号のみ」を見ている（将来的な改修候補）

### 管理者登録時の自動試合参加
- 管理者が練習日に参加者を登録すると、その参加者は全試合に自動参加
- 理由: 運用の簡便性を優先（初期設定の手間を削減）
- ユーザーは後から参加登録画面で試合ごとに調整可能

### 練習セッション更新時の一意制約違反対策
- 問題: 練習セッション更新時に参加者を変更すると `Duplicate entry` エラー発生
- 原因: `deleteBySessionId()` が即座にDBに反映されず、新規レコード挿入時に既存レコードと衝突
- 解決策: `practiceParticipantRepository.flush()` を `deleteBySessionId()` 直後に実行

### 会場マスタによる練習日管理
- 練習セッションは `venue_id` で会場を参照
- 会場には `defaultMatchCount`（デフォルト試合数）と時間割（`venue_match_schedules`）を設定可能
- 練習日作成時に会場のデフォルト値を適用可能

### 抽選・キャンセル待ちシステム
- 定員（`practice_sessions.capacity`）超過時に自動抽選を実行
- 落選者にはキャンセル待ち番号を付与
- 当選者キャンセル時は自動繰り上げ（応答期限付き）。定員未達（キャンセル待ちなし）の場合は通知なし
- 応答期限切れ時は次の待ち番号に自動繰り上げ
- アプリ内通知 + Web Push で即座に通知

### 伝助連携
- 伝助（出欠管理ツール）との双方向同期
  - **アプリ→伝助（イベント駆動）**: 参加者の状態変更時に `DensukeSyncService.triggerWriteAsync()` を `@Async` で即時実行。`DensukeWriteService` が `dirty=true` かつ `matchNumber IS NOT NULL` の参加者のみを対象に、dirty行に対応するスロットだけを伝助へHTTP POSTで書き込み（未入力保護: アプリに未登録のマスは送信しない）
  - **伝助→アプリ（5分スケジューラー）**: `DensukeSyncScheduler` が5分間隔で `DensukeSyncService.syncAll()` を呼び出し、JsoupによるHTMLスクレイピングで出欠情報を取得
- **新規セッション作成時の capacity 初期化**: `DensukeImportService.findOrCreateSession` で伝助同期から練習日を新規作成する際、解決済み venue がある場合は `venue.capacity` を `practice_sessions.capacity` の初期値として設定する。venue が解決できなかった場合 (`venueId == null`) は capacity を `null` のまま保存し、表示側のフォールバック（`PracticeSessionService.computeMatchCapacityStatuses` の venue 既定値フォールバック）に委ねる
  - **Phase3 3-A6（WAITLISTED + 伝助○）**: 当日12:00 JST以降かつ**空き枠あり（`WON + OFFERED < 定員`、OFFERED算入）** かつ**対象者が待ち行列先頭（最小 `waitlist_number`）**の場合のみ、`DensukeImportService.processPhase3Maru` がWAITLISTED→WONに昇格し後続のキャンセル待ち番号を繰り上げ（dirty=false）。12:00前・空き枠なし・先頭でない場合はdirty=trueにして△で書き戻す（抽選バイパス／キュー飛ばし防止・B-5で空き判定を他経路と統一）
  - **練習セッション自動作成時の Venue デフォルト値適用** (`DensukeImportService.findOrCreateSession`): 伝助の会場名と `venues.name` が一致した場合、新規セッションの `totalMatches` は `venue.defaultMatchCount`、`capacity` は `venue.capacity` を採用する。未マッチ時は `totalMatches` を伝助スケジュールの最大試合番号（なければ既定 3）にフォールバックし、`capacity` は null とする。既存セッションでは `venueId` 補完と同時に `capacity` も補完するほか、`venueId` 既設定でも `capacity` のみ null の場合は Venue から逆引きして補完する（管理者が設定済みの `capacity` / `totalMatches` / `venueId` は触らない）。これにより、当日12:00以降の WAITLISTED→WON 自動昇格や空き枠通知が、伝助由来セッションでも正常動作する
- **同期フロー集約**: `DensukeSyncService` がスケジューラー・手動同期・イベント駆動書き込みの全フローを統括
  - `syncAll()`: 当月+翌月の全団体を処理（スケジューラー用）
  - `syncForOrganization()`: 指定団体の書き込み→読み取り（Controller・スケジューラー共用）
  - `triggerWriteAsync()`: dirty参加者の即時書き込み（イベント駆動用、`@Async`）
- **参加者削除**: `removeParticipantFromMatch()` は物理削除ではなく論理削除（`status=CANCELLED`, `dirty=true`）で処理。`DensukeWriteService` が「x」（不参加）として伝助に書き戻す。`softDeleteByPlayerIdAndSessionIds` は `matchNumber IS NOT NULL` 条件でBYEを除外
- **セッション更新**: `updateSession()` は差分更新方式。既存参加者の `dirty` フラグを保持し、不要な伝助書き込みを防止。BYE（matchNumber=null）は削除ループから除外
- **未入力保護**: 通常同期ではdirty行のみ送信し、伝助上の未入力マスを×で上書きしない。BYE（matchNumber=null）は伝助の行に対応しないため常に `dirty=false` で管理し同期対象外とする。抽選確定同期（`writeAllForLotteryConfirmation`）は現行の全体書き戻し方針を維持
- 団体×月単位でURL管理（`densuke_urls`テーブル、`organization_id` でマルチ団体対応）
- メンバーID・行IDをキャッシュテーブル（`densuke_member_mappings`, `densuke_row_ids`）に保存
- スケジューラー・手動同期ともに ① 書き込み → ② 読み取り の順で実行。全団体のURLをループ処理
- **セキュリティ**: `PUT /densuke-url` は `https://densuke.biz/` ドメインのみ受付（SSRF対策）
- **認証**: `GET /densuke-url` は PLAYER 以上の認証が必要（未認証アクセス不可）
- **権限**: ADMINは自団体の伝助URLのみ操作可能。SUPER_ADMINは全団体操作可能
- **未登録者通知**: ADMINは自団体の未登録者のみ通知。SUPER_ADMINは全団体分を通知
- **名前正規化（`DensukeScraper.normalizeMemberName()`）**: 伝助スクレイプ名・DB選手名の双方を単一関数で正規化してから突合し、表記ゆれによる重複登録を防ぐ。名前全体に3段階を適用する: ①不可視文字（`FORMAT`(Cf) カテゴリ全般＋バリエーションセレクター U+FE00–U+FE0F / U+E0100–U+E01EF）の除去（Issue #671）、②あらゆる空白（半角/全角/NBSP/タブ。`Character.isWhitespace || Character.isSpaceChar`）を先頭・途中・末尾すべてから除去（例 `星野　和夏`→`星野和夏`、本番 #159 の重複防止）、③先頭の絵文字（`OTHER_SYMBOL`/`MATH_SYMBOL`/`MODIFIER_SYMBOL`）除去（例 `🔰田中`→`田中`）。照合（`DensukeImportService` の playerNameMap キー生成）・自動登録（`registerAndSync`）・書き戻し（`DensukeWriteService` のメンバー突合・新規メンバー名）が同一関数を共有し全経路へ対称適用される。`null`/空文字は不変。空白の有無のみで区別される別人は存在しない前提
- **キャッシュ**: `PlayerService.findAllPlayersRaw()` に Caffeine 60秒 TTL を適用（スケジューラーのDB負荷軽減）
- **伝助ページ自動作成（DensukePageCreateService）**: アプリ側に登録された練習日（`practice_sessions` × `venues` × `venue_match_schedules`）から densuke.biz にページを新規発行
  - エンドポイント: `POST /api/practice-sessions/densuke/create-page`（ADMIN以上）
  - 送信先: `POST https://www.densuke.biz/create`（`/confirm` はスキップ可能、認証不要）
  - schedule 文字列は「セッションの 1 試合目行は `{M}/{D}({曜}) {会場名} 1試合目`、2 試合目以降は `{N}試合目` のみ」の 2 段形式で改行連結（時刻は含めない）。既存 `DensukeScraper` は `currentDate`/`currentVenue` を前行から引き継ぐため、日付・会場の省略行も正しくパースできる
  - 固定パラメータ: `eventchoice=1`（○△×）、`pw=0`（パスワードなし）
  - レスポンス 302 Location から `cd` と `sd` を抽出、`densuke_urls` に保存
  - バリデーション: ①年月範囲（JST 当月〜+2 ヶ月）、②既存URL重複、③practice_sessions 0件、④venue_match_schedules 不足、⑤会場未登録
  - 排他制御: `densuke_urls` に仮レコードを `saveAndFlush` で先行確保し UNIQUE 制約で同時作成を直列化。ユニーク違反時は 400（「既に登録されています」）を返し densuke.biz への二重 POST を防止
  - 手動 URL 上書き経路（`saveDensukeUrl`）では `densuke_sd` を明示的に NULL クリアし、自動作成済みレコードの sd が残留しないよう保証
  - 作成成功後、`@TransactionalEventListener(AFTER_COMMIT)` 相当の同期で団体所属 PLAYER ロールメンバー（ADMIN/SUPER_ADMIN 除外）に LINE 通知（`DENSUKE_PAGE_CREATED`）を送信
  - テンプレート: `densuke_templates` テーブルで団体ごとにタイトル・説明・連絡先メアドのデフォルト値を保持。作成ダイアログで編集可能
  - 作成後は既存の `DensukeSyncScheduler` が次回サイクル（最長5分）で新URLを自動取り込み
  - 作り直し: `DELETE /api/practice-sessions/densuke-url?year=&month=&organizationId=`（ADMIN以上、自団体のみ）で `densuke_urls` 行を物理削除して作成ロックを解除。densuke.biz 側の旧ページは残るが、アプリからの参照は断たれる。UI では「作り直す」ボタン → 確認ダイアログ → DELETE → 作成モーダル自動オープン、の一連フローで提供
- **メンバー最終変更時刻の取得と drift ログ（observability only / Issue #543, #544, #545）**:
  - `DensukeScraper` がヘッダ各メンバーの `<a title="M/d HH:mm">` 属性をパースして `DensukeData.memberLastChangeTimes`（`Map<String, LocalDateTime>`）に格納（Issue #544）
    - パースは `DensukeScraper.parseDensukeTitleAsDateTime(title, year)`（`^\\s*(\\d{1,2})/(\\d{1,2})\\s+(\\d{1,2}):(\\d{2})\\s*$`）。年は scrape 時引数を採用、年跨ぎはスコープ外
    - `title` が null / 空 / フォーマット不一致 / 不正日付（`2/30`, `13/1`, `25:00`）の場合は map に entry を持たない
  - `DensukeImportService` は Phase1（DB差分検出）と Phase3（伝助→アプリ同期）の状態遷移ログ末尾に、`formatDriftLog()` で生成した drift 情報を付与（Issue #545）
    - 形式: `densukeTitle=<title時刻> detectedAt=<検出時刻(秒丸め)> drift=<分>m`、未取得時は `densukeTitle=(unknown) ... drift=(unknown)`
    - `detectedAt` はインポート 1 回分で固定（インポート開始時刻、`ChronoUnit.SECONDS` で丸め）
  - `warnIfDrifted()` で drift が `DRIFT_WARN_THRESHOLD_MINUTES = 10` 分を超える場合に WARN を出力。`title` 未取得時は WARN 抑制（空 title メンバーでの大量 WARN 防止）
    - 形式: `WARN Densuke change-time drift detected: phase=<Phase> session=<id> match=<n> player=<id> (<name>) densukeTitle=... detectedAt=... driftMinutes=<n>`
  - **DB / API / UI 変更なし** — ログのみで提供。drift 履歴の永続化やフロント表示は将来検討
- **アプリ→伝助 練習日 push 同期（DensukeScheduleWriteService）**: アプリで新規練習日を追加した際に、伝助ページの候補日程欄へ末尾追記で自動同期する機能（追加のみ、削除対象外）。スパイク調査により伝助の `POST /update` で既存スケジュールに末尾追記できることが実証済みで、既存 `densuke_row_ids` のインデックスは破壊されない
  - **トリガー**: ① `PracticeSessionService.createSession` の `afterCommit` フックで `pushNewSchedulesToDensukeAsync` を `@Async` で即時 push。② `DensukeSyncService.syncAll` の最初のステップで `pushAllForCurrentAndNextMonth` をフォロー同期（即時 push 失敗時の自動回復）
  - **無限ループ防止**: `DensukeImportService.findOrCreateSession` は `practiceSessionRepository.save` を直接呼ぶため `createSession` を経由せず、伝助→アプリ取り込み起因の push 再帰は構造上発生しない
  - **並行制御**: `DensukeUrlRepository.findByYearAndMonthAndOrganizationIdForUpdate`（`@Lock(PESSIMISTIC_WRITE)`）で同一 (year, month, organizationId) の行ロックをかけ、並行 push の差分計算ズレを防止
  - **差分計算**: `DensukeScraper.scrape` で伝助の現スケジュール（日付集合）を取得し、アプリ側 `practice_sessions` のうち伝助に存在しない日付のみを抽出。差分なしなら POST せず early return。ただし `densuke_row_ids` に書き込み実績がある、または `DensukeDeletionCandidate`（PENDING/APPROVED）が既にある日付は「新規」から除外し push しない（伝助側で全行削除された日付を、削除検知が走る前に誤って再作成してしまうのを防ぐ。Codex レビュー Round 4 CRITICAL 対応）
  - **過去日制約**: 伝助 `/update` は末尾追記しかできないため、伝助の既存最大日付より前の新規日付を push すると `DensukeWriteService.parseAndSaveRowIds` の row id 対応がずれて参加者出欠が別日に書き込まれるデータ破壊リスクがある。差分セッションのうち伝助既存最大日付以前のものは push せず、即時 push 経路のみ管理者へ LINE 通知して手動追加を促す（スケジューラ経路は通知抑制）
  - **スケジュール文字列**: 既存 `DensukePageCreateService.buildScheduleText` を再利用（フォーマット一貫性）。会場未設定・`venue_match_schedules` 不足時の `IllegalStateException` は失敗通知に変換
  - **HTTP 呼び出し**: GET `/list?cd=...` で Cookie と `pageId` を取得 → POST `/update`（`cd`, `id`, `postfix=""`, `schedule`）→ 期待レスポンス HTTP 302。`DensukeWriteService.extractPageId`（package-private に拡張）/ `extractCd` / `extractBase` を共用
  - **失敗時挙動**: 即時 push 失敗時は `LineNotificationService.sendDensukeScheduleSyncFailedNotification` で団体の ADMIN/SUPER_ADMIN に LINE 通知（`ADMIN_DENSUKE_PUSH_FAILED`、preference カラム未追加で常時送信）。スケジューラ経路は WARN ログのみで通知抑制（フラッディング防止）
  - **自己注入**: `@Lazy DensukeScheduleWriteService self` をコンストラクタで受け、`@Async` / `@Transactional` の AOP プロキシを通すため同一 bean 内呼び出しを `self.xxx()` 経由で行う
  - **DB マイグレーション**: 新 enum 値 `ADMIN_DENSUKE_PUSH_FAILED`（25 文字、VARCHAR(30) 内に収まる短縮命名）を `line_message_log_notification_type_check` の CHECK 制約に追加するマイグレーション SQL（`database/add_admin_densuke_push_failed_message_log_check.sql`）を本番 DB に適用する必要あり。テーブル定義の変更（カラム長拡張）は不要
  - **設計判断（DB ロックと外部 HTTP のスコープ）**: `@Transactional` 内で `densuke_urls` 行ロック → 伝助 scrape → POST /update まで実行する設計。ロック粒度は (year, month, organizationId) 単位で限定的、保持時間は HTTP タイムアウト（各 10 秒、合計最大 30 秒）に律速され、`pushAllForCurrentAndNextMonth` は各 URL を順次処理するため DB コネクションプール圧迫リスクは抑えられる。将来パフォーマンスが課題化した場合は advisory lock や keyed lock で HTTP 前にトランザクションを閉じる設計に変更を検討（本 PR は現行方式維持、Codex Round 3 WARNING の現行維持判断）

- **伝助側削除検知・承認（DensukeDeletionCandidate）**: 伝助側で試合行（日付×試合番号）が削除された場合を検知し、管理者が明示的に承認するまではアプリ側データを一切変更しない機能。伝助側の行削除で `DensukeWriteService` の行数不一致チェックが解消せず `ADMIN_DENSUKE_ROWID_ISSUE` 通知が5分ごとに繰り返し送信され続ける問題への対応
  - **検知（`DensukeDeletionDetectionService`）**: `DensukeImportService.importFromDensuke` が既に取得済みのスクレイピング結果を再利用し、既存の参加者同期ロジック（Phase1/Phase3）とは独立した追加チェックとして実行（既存ロジックへの影響を避ける設計判断）。対象団体・月の `PracticeSession.totalMatches` から機械的に導かれる期待値（1〜totalMatches）と実際にスクレイピングで存在する (date, matchNumber) を突き合わせ、期待値にあって実在しないものを新規削除候補（`densuke_deletion_candidates`, PENDING）として記録する
  - **欠番方式**: 承認しても `PracticeSession.totalMatches` は変更しない。試合番号の詰め直しをすると既存の対戦結果（`Match` エンティティ・参加者データ）が別の試合番号を指してしまうデータ破壊リスクがあるため、totalMatches は維持したまま該当試合番号を「欠番」として扱う
  - **承認/却下（`DensukeDeletionCandidateService`）**: 承認は該当 (date, matchNumber) の `PracticeParticipant` のみ削除（`Match` には触れない・出欠エントリのみ削除の方針）。却下はデータ変更なし。ADMIN のスコープ検証はクライアント指定の organizationId を信用せず、削除候補自身が持つ organizationId を正として検証（`PracticeSessionService.checkAdminScope` と同じ考え方）
  - **書き込み側の欠番除外**: `DensukeWriteService.buildScheduleOrder` が urlId 単位で PENDING・APPROVED（REJECTED は含まない）な削除候補を除外してからスケジュール（期待行リスト）を生成。検知時点（PENDING）から既に除外するため、`ADMIN_DENSUKE_ROWID_ISSUE` の行数不一致は承認を待たずそもそも発生しなくなり、個別の重複通知抑制ロジックは不要（当初は団体/URL単位の抑制ヒューリスティックを検討したが、無関係な問題まで抑制するリスクがあり、根本原因である不一致自体を起こさない設計に変更した）。却下（REJECTED）時は除外対象から外れ、データも行数不一致も変わらないため通常どおり報告され続ける
  - **row_id プリフェッチ側の欠番除外**: `DensukeWriteService.buildRowIdsByKey`（`writePlayerToDensuke` から呼ばれる row_id プリフェッチ）も `buildScheduleOrder` と同じ除外判定（`findDeletionExcludedDateMatchKeys`）を共有し、PENDING・APPROVED な (date, matchNumber) の `densuke_row_ids` キャッシュを読まない。除外しないと、削除された行に紐づく stale な row_id が `buildRegistFormData` に渡り、伝助に実際は反映されていない dirty な参加者が書き込み成功扱い（`dirty=false`）になってしまうデータ不整合が起きる（Codex レビュー Round 5 CRITICAL 対応）
  - **通知**: 新規検知時のみ `ADMIN_DENSUKE_DELETE_DETECTED` を団体の ADMIN/SUPER_ADMIN に LINE 通知（同一候補は承認/却下されるまで再送しない・preference カラムなしで常時送信）
  - **再検知の抑止・自動解消**: APPROVED・REJECTED いずれの (date, matchNumber) も再検知の対象から除外する（REJECTED を除外しないと、伝助側の行が欠落したまま次回同期が走った際に同じキーで新規PENDINGを再saveしようとしUNIQUE制約違反になる）。PENDING な候補の行が伝助側で復活した場合は自動的に削除候補を解消する（REJECTED の自動再オープンはしない単純化方針）
  - **選手向け可視化**: `PracticeSessionDto.densukeDeletionCandidateMatchNumbers`（PENDING・APPROVED の両方）をカレンダーサマリー・詳細取得の両方に付与し、`PracticeList`（カレンダーの試合状況グリッド）は灰色×、`PracticeDetail`/`PracticeParticipation` は「伝助で削除されました」バッジ/表示で検知時点（承認前）から承認後まで一貫して可視化する（承認後も totalMatches は変更しないため、非表示にすると通常の空き枠に見えてしまう）
  - **バックエンド側の登録ガード**: 表示制御だけでは API 直叩きや表示更新前のタイミングで承認済み欠番に再登録できてしまうため、APPROVED な (団体, 練習日, 試合番号) への新規参加登録・再有効化を拒否する。判定ロジックは `DensukeDeletionGuard`（`@Component`、`DensukeDeletionCandidateRepository` をラップする薄い共有ガード）に切り出し、`PracticeParticipantService`（`setMatchParticipants` / `addParticipantToMatch` / `registerParticipations`）と `WaitlistPromotionService`（`handleSameDayJoin` / `handleSameDayJoinAll` / `rejoinWaitlistBySession`）の計6経路に適用（Codex レビュー Round 6 CRITICAL 対応。当初 `PracticeParticipantService` の3経路のみだったが、選手が同日参加・キャンセル待ち復帰で直接触れる `WaitlistPromotionService` の3経路が漏れていたため拡張した）。単一対象の経路は例外で拒否、バッチ処理（`registerParticipations`／`handleSameDayJoinAll`／`rejoinWaitlistBySession`）は該当 (date, matchNumber) のみスキップし他の登録は継続する
  - **既知の限定事項（管理者専用経路は未対応）**: `LotteryService.editParticipants`（参加者追加・ステータス変更）・`LotteryService.reExecuteLottery`（再抽選）・`PracticeSessionService.createSession` / `updateSession`（参加者一括登録）は ADMIN 専用の編集系エンドポイントで、今回はガード未適用（監査で確認済み）。管理者が承認済み欠番と認識した上で意図的に操作する分には実害は小さいが、伝助書き込み側は当該 (date, matchNumber) を除外し続けるため、アプリDB上は登録済みなのに伝助に反映されない不整合が生じ得る。別Issueでの追加対応を検討する。なお `DensukeImportService` のフェーズ処理（伝助→アプリ取り込み）は対象外で問題ない — 承認済み欠番は伝助側に行自体が存在せずスクレイピング結果にエントリが現れないため、取り込みロジックが実行される前提が成立しない
  - **DB マイグレーション**: 新規テーブル `densuke_deletion_candidates`（`database/create_densuke_deletion_candidates.sql`）。新 enum 値 `ADMIN_DENSUKE_DELETE_DETECTED` を `line_message_log_notification_type_check` の CHECK 制約に追加（`database/add_admin_densuke_delete_detected_message_log_check.sql`）

### コンテナメモリ運用とOOM対策（Issue #953）

- **症状**: 本番（Render free, コンテナ上限 512Mi）が**約5時間ごとに規則的にコンテナレベルの OOM kill**（`server_failed` / `oomKilled` / `memoryLimit=512Mi`）を起こし、約216秒のコールドスタートを挟んで毎回数分ダウンしていた。Render Events API で確認（例: 2026-06-27 16:12 JST）。
- **切り分け**: ヒープは `Dockerfile` で `-Xmx200m` に制限済み。にもかかわらず 512Mi 超過 ⇒ 増加しているのは**ヒープ外（メタスペース / コードキャッシュ / NIO ダイレクトバッファ / スレッドスタック / その他ネイティブ）**。Java の `OutOfMemoryError` ではなく cgroup OOM killer による SIGKILL のため、ログに例外も graceful shutdown も残らない。規則的な間隔は、再起動直後の RSS から徐々に増えて 512 に達する挙動による（後述の NMT 計測で**リークではなく footprint 過大**と確定）。
- **緩和（`karuta-tracker/Dockerfile`）**:
  - ベースイメージを Alpine（musl libc）→ **glibc（`eclipse-temurin:21-jre-jammy`）** に変更。musl は JVM の多スレッド・ネイティブ確保ワークロードで RSS が膨らみ OS へ解放されにくい既知傾向があるため。
  - **`MALLOC_ARENA_MAX=2`** で glibc の per-thread malloc アリーナ断片化を抑制。
  - JVM フラグで**ヒープ外の上限**を明示: `-XX:MaxMetaspaceSize=192m`、`-XX:MaxDirectMemorySize=64m`。明示上限の合計をコンテナ上限の内側（200+192+64=456Mi）に収め、メタスペース/ダイレクトの暴走を cgroup SIGKILL より手前で Java `OutOfMemoryError` として顕在化させ MEM-DIAG ログで追えるようにする。ただし 512Mi は本アプリには元来きつく、コードキャッシュ/スレッドスタック/その他ネイティブ込みでは上限到達前にコンテナ OOM が起きうる（明示上限は万能のガードレールではない）。ネイティブ RSS そのものの縮小は glibc + `MALLOC_ARENA_MAX` が担い、各上限値はデプロイ後の MEM-DIAG 観測で精緻化する。
- **可観測化（`monitoring/MemoryDiagnosticsLogger`）**: MXBean（`MemoryMXBean` / `MemoryPoolMXBean` / `BufferPoolMXBean` / `ThreadMXBean`）と Linux `/proc/self/status` の VmRSS から **5分間隔**（`DensukeSyncScheduler` と同周期で相関を取る）で `MEM-DIAG` 行を出力する。
  - 形式: `MEM-DIAG rss=<MB> heap=<used>/<max>MB nonHeap=<MB> metaspace=<MB> codeCache=<MB> directBuf=<used>/<cap>MB mapped=<MB> threads=<n>`
  - **NMT サマリによる内訳特定（実施済み→撤去）**: 緩和後も約5時間周期の OOM が継続したため、一時的に `-XX:NativeMemoryTracking=summary` ＋ `DiagnosticCommandMBean`（`jcmd VM.native_memory summary` 相当）で NMT サマリを `NMT-DUMP` 行として出力し、ネイティブの内訳を取得した。**内訳確定後、計測オーバーヘッド（約6MB）削減のため撤去済み**（下記「根本原因」参照）。
  - 読み出し: `scripts/render-logs/Get-RenderLogs.ps1 -Text "MEM-DIAG" -Hours <n>` で本番ログから取得し、どの領域が増加しているかを特定 → 真因に的を絞った修正につなげる。観測専用のため例外は握りつぶし本番動作に影響しない。
- **根本原因（NMT 計測で確定）**: 起動直後で既に **RSS ~449MB / 512Mi**。NMT Total committed ~396MB の内訳は **Java Heap 189MB（`-Xmx200m` をほぼ全コミット。だが実使用は ~113MB＝約75MBが未使用コミット）**、**クラスメタデータ ~103MB（クラス 21,689 個＝Spring/Hibernate/Jackson/httpclient/bouncycastle/web-push/biweekly/jsoup 等の蓄積）**、Code 43→70MB、Symbol 35MB、その他＋`RSS − committed` の glibc 諸経費 ~53MB。**OOM はリークではなく、肥大した正規 footprint（~449MB）が JIT/glibc 保持で ~505MB まで上がり 512 を越えるため**。機能・依存の蓄積で常駐が 512 に到達した時点から発生している。
- **根本対策（`karuta-tracker/Dockerfile`）**: NMT が「ヒープは 189MB コミットだが実使用 ~113MB」と実証したことに基づき、**ヒープを実測に右サイズ化**: `-Xmx 200m → 150m`（過剰コミット ~40MB を回収）＋ **`-XX:ReservedCodeCacheSize=64m`**（~70MB へ伸びる尾を抑制、JIT flush で吸収）。これで定常 RSS が ~505→~450MB に下がり、マージンが 7MB→約60MB になる。計測用 NMT は撤去。構造的な重さの主因（クラス 21,689 個＝メタ ~103MB）の削減は依存削減の大規模対応のため、回避するなら Render 有料プラン（RAM 増）が確実。

### カレンダー購読（iCalフィード）
- プレイヤーごとに発行された `ical_feed_token` を親トークンとし、**所属団体ごと + ゲスト参加** で別々のURLを発行
  - 所属団体: `/ical/calendar/{token}/org/{orgId}.ics`（VCALENDAR.X-WR-CALNAME = 表示名）
  - ゲスト参加: `/ical/calendar/{token}/guest.ics`（X-WR-CALNAME = `ゲスト参加`）
- カレンダー単位を分けることで、Googleカレンダー等の購読側で団体別に色分け可能
- 購読側（Googleカレンダー等）が定期取得（数時間〜半日間隔）
- biweekly でリクエスト時にDB状態を反映したVCALENDARを動的生成（サーバ側スケジューラ不要）
- 削除・キャンセル・ステータス変化（非 `isActive()`）された練習は次回フェッチで自動消去（VEVENT.UID で同一性判定）
- VEVENT.UID: `session-{sessionId}-player-{playerId}@match-tracker`
- 表示名カスタマイズ: `player_organizations.calendar_display_name` で団体ごとに上書き可（ゲスト参加カレンダー内は `Organization.name` 固定）
- 同期対象は `ParticipantStatus.isActive()`（WON / PENDING のみ、CANCELLED / DECLINED / WAITLISTED 等は除外）
- **同期対象期間は全期間（過去・未来とも）**。`PracticeParticipantRepository.findAllParticipationsByPlayer(playerId)` で取得し、サーバ側で日付による絞り込みは行わない。カレンダーアプリで過去の練習を見返す体験（「数年前のこの日はここで練習していた」など）を維持するための設計判断
- 設定画面 `CalendarSubscriptionPage.jsx` は「このページについて」常時表示ボックス・「登録手順を見る」アコーディオン（Google PCブラウザ / Apple iPhone）・各操作の説明サブテキストを含み、外部ドキュメント不要で利用方法が完結する
- VEVENT 時刻はプレイヤーの参加 `match_number` から動的に算出（`IcalCalendarFeedService.buildEvent`）
  - `buildIcsForParticipations` で session 単位に `sessionMatchNumbers`（`match_number != null` のみ集計）と `sessionsWithNullMatchNumber` を構築し、`buildEvent` に `allHaveMatchNumber` フラグとして渡す
  - **条件A**（全参加レコードに `match_number` あり）かつ **条件B**（参加 `match_number` のうち 1 件以上 `VenueMatchSchedule` 登録あり）が両方成立 → 登録済みスケジュールの `min(start)`〜`max(end)` を採用
  - それ以外（`null` 混在、会場スケジュール未登録、`venue_id` 未設定など）→ `PracticeSession.start_time`/`end_time` にフォールバック
  - 両方 `null` なら全日イベント（`VALUE=DATE`）。startTime のみ存在で endTime が `null` の場合は `startTime.plusHours(4)` を補う（フォールバック時のみ発生）
  - UID は時刻を含まないため、Google カレンダー側では同じイベントが「更新」され、重複や消失は発生しない

### 試合番号のスワイプ移動（フロント設計）

結果一覧（`MatchResultsView`）・一括入力（`BulkResultInput`）・個人入力（`MatchForm`）の3画面で、画面上部の試合番号タブを左右スワイプでも切り替えられる。タブUIは存続し、スワイプは追加の操作手段。

- **ジェスチャ判定ロジック `swipeGesture.js`（純粋関数）:** UIから切り離して単体テスト可能にする方針（`byePlayersLogic.js` / `pairingDragLogic.js` と同様）。
  - `isHorizontalSwipe(dx, dy, activationPx=10)`: 横移動が活性化閾値を超え、かつ横移動 > 縦移動なら true（縦スクロール・タップと区別）
  - `resolveSwipe({ dx, containerWidth, commitRatio=0.25, velocity, flickVelocity=0.5 })`: 移動量がコンテナ幅×25%以上、またはフリック速度以上で確定し方向（`'prev'|'next'`）を返す。閾値未満は `null`。座標系は `dx<0`（左スワイプ）=次へ、`dx>0`（右スワイプ）=前へ
  - `clampOffset(dx, { atFirst, atLast })`: 端方向の動きを 0 に抑制した表示用オフセット（端で止まる）
- **指追従カルーセル `MatchCarousel.jsx`（共通コンポーネント）:** 結果一覧・一括入力で共用。props は `totalMatches` / `currentMatchNumber` / `onChange(matchNumber)` / `renderPanel(matchNumber)` / `swipeAreaRef`（任意）。
  - 現在±1のパネルを描画（端ではその方向のパネルを描画しない）。現在パネルは通常フローで高さの基準とし、前後パネルは絶対配置で高さに影響させない（パネルごとの高さ差による余白を防ぐ）
  - `touchmove` で `preventDefault` するため `passive:false` のネイティブリスナーを使用し、`touchAction: 'pan-y'` で縦スクロールと両立。横スワイプと判定したときのみ追従（translateX）
  - **スワイプ検出面の拡張:** 任意 props `swipeAreaRef` を受け取り、指定時はその要素（各ページのルート＝共通ヘッダー/フッターを除くコンテンツ全域）に touch リスナーを張る（未指定時は従来どおりカルーセル本体 `viewport`。テストはこのフォールバックで従来通り動作）。`onTouchStart` で `e.target.closest('[data-swipe-ignore]')` に該当するタッチ（固定ヘッダー由来）は無視。確定閾値の幅は検出面ではなく**パネル（`viewport`）幅基準**に固定し、指追従の見た目を保つ
  - 離したときに `resolveSwipe` で確定方向を判定。確定なら約0.2秒スライドさせてから `onChange(現在±1)` を呼びオフセットを0に戻す（新パネルが同じ画面位置に来るため見た目は連続）。端ではその方向へは確定しない
- **MatchForm（スライドイン＋警告）:** 1試合分のフォームしか保持しない構造のため指追従カルーセルは使わず、スワイプ検出（`swipeGesture.js`）→確定判定→スライドイン（約0.2秒）。
  - `isDirty` 状態を追加。ユーザー操作（結果・枚数差・お手付き・メモ・対戦相手選択・抜け番活動・手動抜け番切替）で `true`、確認後の試合切替で `false`。`applyMatchData` では立てない
  - 共通ガード `requestMatchNumberChange(num, { fromSwipe })`: `isDirty` なら確認ダイアログ→OKで切替／キャンセルで据え置き。dirtyでなければ即切替。**既存タブ onClick とスワイプ確定の両方をこのガードに通す**。`fromSwipe` のときのみスライドインアニメ。編集モードは対象外
  - **スワイプ検出面:** `onTouchStart`/`onTouchEnd` をルート div（`min-h-screen` 全域）に張り、共通ヘッダー/フッターを除くコンテンツ全域でスワイプを受け付ける。`handleContentTouchStart` で `data-swipe-ignore`（固定ヘッダー）由来のタッチは無視。スライドイン用の `contentRef`（幅計測・アニメ対象）は据え置き
- **タブ自動スクロール `tabScroll.js`:** `scrollActiveTabIntoView(tabBarEl)` がアクティブタブ（`data-active="true"`）を横スクロールするタブバー内に収まるよう **タブバー自身の `scrollLeft` だけ** を調整する（ページの縦スクロールには影響しない）。3画面とも `currentMatchNumber`/`formData.matchNumber` 変化時に呼ぶ
- **操作ヒント表示:** スワイプ可能な画面では、スワイプで試合を切り替えられることをコンテンツ上部に控えめな案内テキスト『‹ スワイプで試合を切替 ›』で常時表示する（`text-xs`・淡色・中央寄せ）。表示条件は結果一覧/一括入力が `totalMatches > 1`、個人入力が `!isEdit && getTabMatchNumbers().length > 1`（1試合のみ・編集モードは非表示にして誤誘導を防ぐ）。色は各画面の既存トーンに合わせる（結果一覧/個人入力 `#9ca3af`、一括入力は既存ヒントと揃え `#9b8a7e`）。テスト: 各 `*.swipe.test.jsx` に表示/非表示の軽量テストを追加
- バックエンド・DB・APIの変更はなし（フロントエンドの表示状態のみ変更）

### 初期表示試合番号のデフォルト（フロント設計）

結果一覧（`MatchResultsView`）・一括入力（`BulkResultInput`）の初期表示試合番号を、現在時刻（端末ローカル）と入力状況から決定する。従来の1固定（`useState(1)`）を、初回データ取得完了後に1回だけ上書きする方式。**フロント完結でバックエンド・DB・APIの変更なし**（試合番号ごとの時刻 = 取得済み `PracticeSessionDto.venueSchedules`、ペアリング・結果 = `pairingAPI.getByDate` / `matchAPI.getByDate` で取得済み）。

- **決定ロジック `defaultMatchNumber.js`（純粋関数）:** UIから切り離して単体テスト可能にする方針（`swipeGesture.js` / `byePlayersLogic.js` と同様）。猶予 `GRACE_MINUTES = 15`（固定）。
  - `toMinutes(timeStr)`: `"HH:mm[:ss]"` を0時からの分数に変換（型不正・パース不能は `null`）
  - `isToday(sessionDate, now)`: `sessionDate`（`"YYYY-MM-DD"`）が `now` の端末ローカル日付と一致するか
  - `timeBasedDefaultMatchNumber(venueSchedules, now)`: `endTime` を持つ番号を昇順走査し、`now < endTime + 15分` を満たす最小の `matchNumber` を返す。該当なしは `1`（最終試合の終了+15分を超過 → 1に戻す）
  - `getCompletedMatchNumbers({ pairings, matches, totalMatches })`: 全入力済み（その番号の全ペアリングに対応する `Match` が存在）の試合番号配列。選手IDは `min`/`max` 正規化で突合（保存時に `player1Id < player2Id` へ正規化されるため）。ペアリング0件の番号は対象外（既存 `isMatchCompleted` と同一判定）
  - `defaultForResultsView({ urlMatchNumber, venueSchedules, sessionDate, now })`: `urlMatchNumber` 指定を最優先 → 当日かつスケジュールありで時刻ベース → `1`
  - `defaultForBulkInput({ completedMatchNumbers, totalMatches, venueSchedules, sessionDate, now })`: 入力済みありなら `min(max(completed)+1, totalMatches)` を最優先 → なければ時刻ベース → `1`
- **`MatchResultsView.jsx`:** `useSearchParams` で `matchNumber` クエリを読み取り、初回データ取得（`fetchInitial`）内でセッション確定後に `defaultForResultsView` の結果で `currentMatchNumber` を設定。URL値は `parseInt` 後 `1〜totalMatches` の範囲内のみ採用（範囲外・非数値は `null` 扱いで無視）。`useEffect` 依存配列に `matchNumberParam`・`location.key` を含めることで、保存後遷移など新しい `?matchNumber=` での再ナビゲート時にも再適用される。初回ガードは `initialFetchDone` ref。
- **`BulkResultInput.jsx`:** 初回データ取得（`fetchData`、依存配列 `[sessionId]` のため `sessionId` ごとに1回のみ実行）でセッション・ペアリング・既存結果が揃った時点に `getCompletedMatchNumbers` → `defaultForBulkInput` で `currentMatchNumber` を設定。以降のユーザーのタブ／スワイプ切替は上書きしない。保存成功後の遷移を `navigate('/matches/results/:sessionId?date=<session.sessionDate>&matchNumber=<currentMatchNumber>')` とし、保存元セッションの日付と入力中の試合番号を一覧画面へ引き継ぐ（一覧は `sessionId` でなく日付でセッションを解決するため、`date` を付けないと過去日・未来日の保存で当日が開く）。
- **テスト:** `defaultMatchNumber.test.js`（純粋関数の境界）、`MatchResultsView.defaultTab.test.jsx` / `BulkResultInput.defaultTab.test.jsx`（URL指定優先・時刻ベース・過去日1固定・一括入力の入力済み制約・保存後遷移の `?matchNumber=` 付与）。
- バックエンド・DB・APIの変更はなし
