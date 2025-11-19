# かるたトラッカー システム設計書

最終更新日: 2025-11-19

## 目次
1. [システム概要](#1-システム概要)
2. [アーキテクチャ](#2-アーキテクチャ)
3. [データベース設計](#3-データベース設計)
4. [API設計](#4-api設計)
5. [画面設計](#5-画面設計)
   - 5.1 [画面一覧](#51-画面一覧)
   - 5.2 [画面遷移と導線](#52-画面遷移と導線2025-11-19更新)
   - 5.3 [主要画面設計](#53-主要画面設計)
6. [権限設計](#6-権限設計)
7. [主要機能フロー](#7-主要機能フロー)
8. [未実装機能・TODO](#8-未実装機能todo)
9. [補足事項](#9-補足事項)

---

## 1. システム概要

### 1.1 目的
競技かるたの練習・試合記録を管理し、対戦組み合わせの自動生成や統計分析を支援するWebアプリケーション。

### 1.2 主要機能
- 選手管理（登録・更新・削除・ロール管理）
- 試合記録（登録・更新・削除・統計）
- 練習日管理（SUPER_ADMIN専用）
- **練習参加登録（試合ごと・月単位一括登録）**
- **練習参加状況可視化（カレンダーバッジ・試合別ハイライト）**
- 対戦組み合わせ自動生成（過去対戦履歴考慮）
- 選手統計情報

### 1.3 技術スタック
**バックエンド**:
- Java 21
- Spring Boot 3.4.1
- Spring Data JPA
- Hibernate
- MySQL 8.0.44
- Gradle

**フロントエンド**:
- React 18
- React Router v6
- Axios
- Tailwind CSS
- Vite

---

## 2. アーキテクチャ

### 2.1 システム構成
```
[Browser] ←→ [React SPA (Port 5176)]
              ↓ HTTP
              [Spring Boot API (Port 8080)]
              ↓ JDBC
              [MySQL (Port 3306)]
```

### 2.2 レイヤー構成
```
Controller Layer (REST API)
    ↓
Service Layer (ビジネスロジック)
    ↓
Repository Layer (データアクセス)
    ↓
Entity Layer (JPA Entity)
```

### 2.3 認証・認可
**現在の実装**:
- 簡易的なヘッダーベース認証（`X-User-Role`）
- パスワード平文比較
- `@RequireRole` アノテーション + `RoleCheckInterceptor`

**TODO**:
- Spring Security + JWT導入
- BCryptパスワードハッシュ化
- セッション管理

---

## 3. データベース設計

### 3.1 ER図
```
[players] 1───多 [matches] 多───1 [players]
    │
    │1
    │
    多
[practice_participants] 多───1 [practice_sessions]
    │
    │多
    │
    1
[match_pairings]
    │
    │多
    │
    1
[player_profiles]
```

### 3.2 テーブル定義

#### players（選手マスタ）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 選手ID |
| name | VARCHAR(100) | NOT NULL, UNIQUE | 選手名（ログインID） |
| password | VARCHAR(255) | NOT NULL | パスワード |
| gender | ENUM | | 性別（男性/女性/その他） |
| dominant_hand | ENUM | | 利き手（右/左/両） |
| dan_rank | ENUM | | 段位（無段～八段） |
| kyu_rank | ENUM | | 級位（E級～A級） |
| karuta_club | VARCHAR(200) | | 所属かるた会 |
| remarks | TEXT | | 備考 |
| role | ENUM | NOT NULL, DEFAULT 'PLAYER' | ロール（SUPER_ADMIN/ADMIN/PLAYER） |
| deleted_at | DATETIME | | 論理削除フラグ |
| created_at | DATETIME | NOT NULL | 作成日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

**インデックス**:
- `idx_name_active` (name, deleted_at)
- `idx_deleted_at` (deleted_at)

---

#### matches（対戦結果）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 試合ID |
| match_date | DATE | NOT NULL | 試合日 |
| match_number | INT | NOT NULL | 試合番号（その日の何試合目か） |
| player1_id | BIGINT | NOT NULL, FK | 選手1ID（player1_id < player2_id を保証） |
| player2_id | BIGINT | NOT NULL, FK | 選手2ID |
| winner_id | BIGINT | NOT NULL, FK | 勝者ID |
| score_difference | INT | NOT NULL | 枚数差（1～50） |
| opponent_name | VARCHAR(100) | | 未登録選手名（簡易登録用） |
| notes | TEXT | | コメント |
| created_by | BIGINT | NOT NULL | 登録者ID |
| updated_by | BIGINT | NOT NULL | 更新者ID |
| created_at | DATETIME | NOT NULL | 登録日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

**インデックス**:
- `idx_matches_date` (match_date)
- `idx_matches_date_player1` (match_date, player1_id)
- `idx_matches_date_player2` (match_date, player2_id)
- `idx_matches_winner` (winner_id)
- `idx_matches_date_match_number` (match_date, match_number)

**特殊ロジック**:
- `@PrePersist`/`@PreUpdate`で player1_id < player2_id を自動保証

---

#### practice_sessions（練習日情報）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 練習日ID |
| session_date | DATE | NOT NULL, UNIQUE | 練習日 |
| total_matches | INT | NOT NULL | 予定試合数 |
| location | VARCHAR(200) | | 練習場所 |
| notes | TEXT | | メモ・備考 |
| start_time | TIME | | 開始時刻 |
| end_time | TIME | | 終了時刻 |
| capacity | INT | | 定員 |
| created_by | BIGINT | | 登録者ID |
| updated_by | BIGINT | | 更新者ID |
| created_at | DATETIME | NOT NULL | 登録日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

**インデックス**:
- `idx_session_date` (session_date)

---

#### practice_participants（練習参加者）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 参加記録ID |
| session_id | BIGINT | NOT NULL, FK | 練習セッションID |
| player_id | BIGINT | NOT NULL, FK | 参加選手ID |
| match_number | INT | | 参加試合番号（1～7、NULLは全試合） |
| created_at | DATETIME | NOT NULL | 登録日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

**制約**:
- UNIQUE (session_id, player_id, match_number)

**インデックス**:
- `idx_participant_session` (session_id)
- `idx_participant_player` (player_id)

---

#### match_pairings（対戦組み合わせ）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 組み合わせID |
| session_date | DATE | NOT NULL | 練習日 |
| match_number | INT | NOT NULL | 試合番号 |
| player1_id | BIGINT | NOT NULL, FK | 選手1ID |
| player2_id | BIGINT | NOT NULL, FK | 選手2ID |
| created_by | BIGINT | NOT NULL | 登録者ID |
| created_at | DATETIME | NOT NULL | 登録日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

---

#### player_profiles（選手情報履歴）
| カラム名 | 型 | 制約 | 説明 |
|---------|-----|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | プロフィールID |
| player_id | BIGINT | NOT NULL, FK | 選手ID |
| karuta_club | VARCHAR(200) | NOT NULL | 所属かるた会 |
| grade | ENUM | NOT NULL | 級位（A～E） |
| dan | ENUM | NOT NULL | 段位（無～八） |
| valid_from | DATE | NOT NULL | 有効開始日 |
| valid_to | DATE | | 有効終了日（NULL=現在有効） |
| created_at | DATETIME | NOT NULL | 登録日時 |
| updated_at | DATETIME | NOT NULL | 更新日時 |

**インデックス**:
- `idx_player_date` (player_id, valid_from, valid_to)
- `idx_valid_to` (valid_to)

---

## 4. API設計

### 4.1 共通仕様

**ベースURL**: `http://localhost:8080/api`

**リクエストヘッダー**:
- `Content-Type: application/json`
- `Authorization: Bearer {token}` (TODO: JWT実装後)
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

---

### 4.2 選手API

#### POST /api/players/login
**説明**: ログイン
**権限**: なし
**リクエスト**:
```json
{
  "name": "田中太郎",
  "password": "password123"
}
```
**レスポンス**: `LoginResponse`
```json
{
  "id": 1,
  "name": "田中太郎",
  "gender": "男性",
  "role": "PLAYER",
  "dominantHand": "右",
  "danRank": "三段",
  "kyuRank": "A級",
  "karutaClub": "東京かるた会"
}
```

#### GET /api/players
**説明**: 全アクティブ選手取得
**権限**: なし
**レスポンス**: `List<PlayerDto>`

#### POST /api/players
**説明**: 選手登録
**権限**: SUPER_ADMIN
**リクエスト**: `PlayerCreateRequest`
```json
{
  "name": "田中太郎",
  "password": "password123",
  "gender": "男性",
  "dominantHand": "右",
  "danRank": "三段",
  "kyuRank": "A級",
  "karutaClub": "東京かるた会",
  "remarks": "備考"
}
```

#### PUT /api/players/{id}
**説明**: 選手情報更新
**権限**: なし
**リクエスト**: `PlayerUpdateRequest`（全フィールドオプショナル）

#### DELETE /api/players/{id}
**説明**: 選手削除（論理削除）
**権限**: SUPER_ADMIN

#### PUT /api/players/{id}/role
**説明**: ロール変更
**権限**: SUPER_ADMIN
**リクエスト**:
```json
{
  "role": "ADMIN"
}
```

---

### 4.3 試合記録API

#### POST /api/matches
**説明**: 簡易試合登録（対戦相手名）
**権限**: なし
**リクエスト**: `MatchSimpleCreateRequest`
```json
{
  "matchDate": "2025-11-17",
  "matchNumber": 1,
  "playerId": 1,
  "opponentName": "佐藤花子",
  "result": "勝ち",
  "scoreDifference": 5,
  "notes": "コメント"
}
```

#### POST /api/matches/detailed
**説明**: 詳細試合登録（両選手ID）
**権限**: なし
**リクエスト**: `MatchCreateRequest`
```json
{
  "matchDate": "2025-11-17",
  "matchNumber": 1,
  "player1Id": 1,
  "player2Id": 2,
  "winnerId": 1,
  "scoreDifference": 5
}
```

#### GET /api/matches?date={date}
**説明**: 日付別試合一覧
**権限**: なし
**レスポンス**: `List<MatchDto>`

#### GET /api/matches/player/{playerId}
**説明**: 選手の試合履歴
**権限**: なし

#### GET /api/matches/player/{playerId}/statistics
**説明**: 選手統計
**権限**: なし
**レスポンス**: `MatchStatisticsDto`
```json
{
  "playerId": 1,
  "playerName": "田中太郎",
  "totalMatches": 100,
  "wins": 65,
  "winRate": 0.65
}
```

---

### 4.4 練習日API

#### POST /api/practice-sessions
**説明**: 練習日作成
**権限**: SUPER_ADMIN
**リクエスト**: `PracticeSessionCreateRequest`
```json
{
  "sessionDate": "2025-11-20",
  "totalMatches": 7,
  "location": "市民館",
  "notes": "備考",
  "startTime": "13:00",
  "endTime": "17:00",
  "capacity": 20,
  "participantIds": [1, 2, 3]
}
```

**特記事項（2025-11-19実装）**:
- `participantIds` で指定された参加者は全試合に自動参加
- 例: 3名の参加者、7試合 → 21レコード作成（3 × 7）
- 各参加者について `matchNumber` 1～7 の `PracticeParticipant` レコードを作成
- ユーザーは後から参加登録画面で試合を調整可能

#### GET /api/practice-sessions
**説明**: 全練習日取得（降順）
**権限**: なし
**レスポンス**: `List<PracticeSessionDto>`
```json
[
  {
    "id": 10,
    "sessionDate": "2025-11-20",
    "totalMatches": 7,
    "location": "市民館",
    "participantCount": 15,
    "completedMatches": 3,
    "matchParticipantCounts": {
      "1": 10,
      "2": 12,
      "3": 8
    },
    "matchParticipants": {
      "1": ["田中太郎", "佐藤花子", ...],
      "2": [...],
      ...
    }
  }
]
```

#### GET /api/practice-sessions/year-month?year={year}&month={month}
**説明**: 年月別練習日取得
**権限**: なし

#### PUT /api/practice-sessions/{id}
**説明**: 練習日更新
**権限**: SUPER_ADMIN

**特記事項（2025-11-19実装）**:
- 更新時も `participantIds` で指定された参加者は全試合に自動参加
- 既存の参加記録は削除され、新しい参加記録が作成される

#### DELETE /api/practice-sessions/{id}
**説明**: 練習日削除
**権限**: SUPER_ADMIN

---

### 4.5 練習参加登録API

#### POST /api/practice-sessions/participations
**説明**: 一括参加登録（月単位）
**権限**: なし
**リクエスト**: `PracticeParticipationRequest`
```json
{
  "playerId": 1,
  "year": 2025,
  "month": 11,
  "participations": [
    {"sessionId": 10, "matchNumber": 1},
    {"sessionId": 10, "matchNumber": 2},
    {"sessionId": 11, "matchNumber": 1}
  ]
}
```

#### GET /api/practice-sessions/participations/player/{playerId}?year={year}&month={month}
**説明**: 月別参加状況取得
**権限**: なし
**レスポンス**:
```json
{
  "10": [1, 2],
  "11": [1]
}
```

**用途**: カレンダー画面で自分の参加状況をバッジ表示するために使用
- ●（緑色）: 全試合参加
- ◐（黄色）: 部分参加（一部の試合のみ）
- バッジなし: 未参加

---

### 4.6 対戦組み合わせAPI

#### POST /api/match-pairings/auto-match
**説明**: 自動マッチング
**権限**: SUPER_ADMIN, ADMIN
**リクエスト**: `AutoMatchingRequest`
```json
{
  "sessionDate": "2025-11-20",
  "matchNumber": 1,
  "participantIds": [1, 2, 3, 4, 5]
}
```
**レスポンス**: `AutoMatchingResult`
```json
{
  "pairings": [
    {
      "player1Id": 1,
      "player1Name": "田中太郎",
      "player2Id": 2,
      "player2Name": "佐藤花子",
      "score": -14.28,
      "recentMatches": [
        {"date": "2025-11-13", "daysAgo": 7}
      ]
    }
  ],
  "waitingPlayers": [
    {"id": 5, "name": "山田太郎"}
  ]
}
```

#### POST /api/match-pairings/batch?date={date}&matchNumber={matchNumber}
**説明**: 一括組み合わせ作成
**権限**: SUPER_ADMIN, ADMIN
**リクエスト**:
```json
[
  {"player1Id": 1, "player2Id": 2},
  {"player1Id": 3, "player2Id": 4}
]
```

#### GET /api/match-pairings/date?date={date}
**説明**: 日付別組み合わせ取得
**権限**: なし

#### DELETE /api/match-pairings/date-and-match?date={date}&matchNumber={matchNumber}
**説明**: 組み合わせ削除
**権限**: SUPER_ADMIN, ADMIN

---

## 5. 画面設計

### 5.1 画面一覧

| 画面名 | パス | 権限 | 説明 |
|-------|------|------|------|
| ログイン | /login | なし | ログイン画面 |
| ダッシュボード | / | 全員 | ホーム画面 |
| **練習日一覧** | **/practice** | **全員** | **カレンダー形式練習日一覧（参加状況バッジ表示）** |
| 練習日登録・編集 | /practice/new, /practice/:id/edit | SUPER_ADMIN | 練習日作成・更新 |
| **練習参加登録** | **/practice/participation** | **全員** | **月単位参加登録（保存後カレンダーに自動遷移）** |
| 試合一覧 | /matches | 全員 | 試合一覧 |
| 試合登録・編集 | /matches/new, /matches/:id/edit | 全員 | 試合登録・更新 |
| 対戦組み合わせ | /pairings | 全員 | 組み合わせ生成・表示 |
| 選手一覧 | /players | 全員 | 選手一覧 |
| 選手詳細 | /players/:id | 全員 | 選手詳細・統計 |
| 選手登録・編集 | /players/new, /players/:id/edit | SUPER_ADMIN | 選手作成・更新 |

---

### 5.2 画面遷移と導線（2025-11-19更新）

#### ホーム画面からの導線
ホーム画面（`/`）には以下のクイックアクション:
- **試合記録**: `/matches/new` へ遷移（試合登録）
- **練習記録**: `/practice` へ遷移（**カレンダー画面**）← 2025-11-19変更
- **組み合わせ**: `/pairings/generate` へ遷移（対戦組み合わせ生成）

**重要な変更点**:
- 以前は「練習記録」が個別の練習日詳細画面に遷移していた
- 現在は**カレンダー画面（`/practice`）を練習関連機能の中心ハブとして設計**
- カレンダー画面から以下に遷移可能:
  - 参加登録画面（年月を引き継ぐ）
  - 練習日編集（SUPER_ADMINのみ）

#### 練習関連の導線フロー
```
ホーム画面（/）
  ↓ 「練習記録」クリック
カレンダー画面（/practice）
  ├─ 「参加登録」ボタン → 参加登録画面（/practice/participation）
  │                        ↓ 保存後1秒待機
  │                        └→ カレンダー画面に自動戻り（データ再取得）
  │
  ├─ 日付クリック → モーダル表示
  │   └─ 「参加登録」ボタン → 参加登録画面（年月引き継ぎ）
  │
  └─ 「編集」ボタン（SUPER_ADMINのみ） → 練習日編集画面
```

---

### 5.3 主要画面設計

#### 5.3.1 練習日一覧（カレンダー形式）
**パス**: `/practice`

**表示内容**:
- **月選択（前月/次月ボタン）**
- **カレンダーグリッド（日曜～土曜）**
- **参加状況バッジ（2025-11-19追加）**
  - ●（緑色）: 全試合参加
  - ◐（黄色）: 部分参加（一部の試合のみ）
  - バッジなし: 未参加
- **各日付に練習日がある場合、背景色変更＋場所名省略表示（「市民」など）**
- **今日の日付をハイライト**
- **参加登録ボタン（カレンダー上部）**: 年月を引き継いで参加登録画面に遷移
- **日付クリック→モーダルポップアップ**

**モーダル内容**:
- 📅 日付（曜日付き）
- 📍 場所
- 🎯 試合別参加者（アコーディオン形式）
  - ▶ X試合目 (Y名) ← クリックで展開
  - ▼ X試合目 (Y名) ← 展開時
    - **自分が参加する試合は緑色でハイライト（2025-11-19追加）**
    - 参加者リスト（箇条書き）
- 📝 備考
- **📝 参加登録ボタン（モーダル内）**: 年月を引き継いで参加登録画面に遷移
- 編集・削除ボタン（SUPER_ADMIN のみ表示）

**実装ポイント**:
- `useState` で `expandedMatches` 管理
- `useState` で `myParticipations` 管理（自分の参加状況）
- `matchParticipants` データから試合別参加者表示
- `useAuth` で現在ログイン中の選手情報を取得
- 画面フォーカス時に自動再取得（`window.addEventListener('focus')`）
- スマホ最適化（クリーンなデザイン）

**データフロー**:
1. `practiceAPI.getAll()` で全セッション取得
2. `practiceAPI.getPlayerParticipations(currentPlayer.id, year, month)` で自分の参加状況取得
3. セッションごとに参加状況を判定（全試合/部分/未参加）
4. カレンダーセルにバッジ表示
5. モーダル内でアコーディオンを緑色表示

---

#### 5.3.2 練習参加登録
**パス**: `/practice/participation`

**表示内容**:
- **年月選択（セレクトボックス）**: カレンダー画面から遷移時は年月が引き継がれる
- **練習日一覧（テーブル形式）**
  - 日付列
  - 場所列
  - 試合番号チェックボックス列（1～7）
    - 既存参加状況を反映してチェック済み
- **保存ボタン**: 成功後、1秒待ってカレンダー画面に自動遷移（2025-11-19追加）

**データフロー**:
1. 年月選択 → `GET /api/practice-sessions/year-month`
2. `GET /api/practice-sessions/participations/player/{playerId}` で既存参加取得
3. チェックボックス操作
4. 保存ボタン → `POST /api/practice-sessions/participations`
5. **成功メッセージ表示（1秒間）**
6. **`/practice` へ自動ナビゲート**
7. **カレンダー画面で自動的にデータ再取得**
8. **最新の参加状況が反映される**

**導線**:
- カレンダー画面の上部ボタンから遷移
- カレンダー画面のモーダル内ボタンから遷移
- いずれも年月を引き継ぐ

---

#### 5.3.3 対戦組み合わせ
**パス**: `/pairings`

**表示内容**:
- 日付選択
- 試合番号選択（1～7）
- 参加者一覧（チェックボックス）
- 「自動マッチング」ボタン → `POST /api/match-pairings/auto-match`
- 提案されたペア一覧
  - 選手1 vs 選手2
  - 最近の対戦履歴（日付、何日前）
  - スコア表示
- 手動調整可能（ドラッグ＆ドロップ等）
- 「組み合わせ確定」ボタン → `POST /api/match-pairings/batch`
- 待機者リスト

**アルゴリズム**:
- 過去30日の対戦履歴取得
- スコア = -(100 / 最終対戦日からの日数)
- 初対戦: スコア0（優先）
- 1日前: -100点、2日前: -50点、7日前: -14点
- 貪欲法で最適ペアリング

---

## 6. 権限設計

### 6.1 ロール定義

| ロール | 説明 |
|-------|------|
| SUPER_ADMIN | 最上位管理者。全機能アクセス可能。選手登録・削除、練習日作成・削除、ロール変更可能。 |
| ADMIN | 管理者。対戦組み合わせ作成・削除可能。練習日作成・削除は不可。 |
| PLAYER | 一般選手。基本機能（試合記録、参加登録、閲覧）のみ。 |

### 6.2 権限マトリックス

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
| 対戦組み合わせ | 組み合わせ作成・削除 | ○ | ○ | × |
| | 自動マッチング | ○ | ○ | × |
| | 組み合わせ閲覧 | ○ | ○ | ○ |

### 6.3 実装方法

**バックエンド**:
- `@RequireRole` アノテーションをコントローラーメソッドに付与
- `RoleCheckInterceptor` がリクエストヘッダー `X-User-Role` を検証
- 権限不足の場合は `403 Forbidden`

**フロントエンド**:
- `AuthContext` で `currentPlayer.role` を管理
- Axiosインターセプターで全リクエストに `X-User-Role` ヘッダー追加
- `isSuperAdmin()`, `isAdmin()` などのヘルパー関数で条件付き表示

---

## 7. 主要機能フロー

### 7.1 練習参加登録フロー（2025-11-19更新）

```
[ユーザー操作]
1. カレンダー画面（/practice）の「参加登録ボタン」クリック
   または モーダル内の「参加登録ボタン」クリック
   → 年月を引き継いで /practice/participation へ移動
   ↓
2. 年月選択（例: 2025年11月）※カレンダーから遷移時は自動設定
   ↓
[フロントエンド]
3. GET /api/practice-sessions/year-month?year=2025&month=11
   ← 練習日一覧（id, sessionDate, location, totalMatches）
   ↓
4. GET /api/practice-sessions/participations/player/{playerId}?year=2025&month=11
   ← 既存参加状況 {sessionId: [matchNumbers]}
   ↓
5. テーブル表示
   - 各練習日に試合番号（1～totalMatches）のチェックボックス
   - 既存参加状況を反映してチェック済み
   ↓
[ユーザー操作]
6. チェックボックス操作（試合ごとに参加/不参加選択）
   ↓
7. 「保存する」ボタンクリック
   ↓
[フロントエンド]
8. POST /api/practice-sessions/participations
   Request Body: {
     playerId: 1,
     year: 2025,
     month: 11,
     participations: [
       {sessionId: 10, matchNumber: 1},
       {sessionId: 10, matchNumber: 2},
       {sessionId: 11, matchNumber: 1}
     ]
   }
   ↓
[バックエンド: PracticeSessionService]
9. その月の全セッションIDを取得
   ↓
10. 既存参加記録を一括削除
    DELETE FROM practice_participants
    WHERE player_id = ? AND session_id IN (10, 11, ...)
   ↓
11. 新規参加記録を一括登録
    INSERT INTO practice_participants (session_id, player_id, match_number, ...)
   ↓
12. レスポンス: 201 Created
   ↓
[フロントエンド]
13. 成功メッセージ表示「参加登録を保存しました」
   ↓
14. 1秒待機（ユーザーが成功メッセージを確認する時間）
   ↓
15. /practice（カレンダー画面）へ自動ナビゲート
   ↓
[カレンダー画面]
16. useEffect で画面フォーカス検知
   ↓
17. fetchSessions() と fetchMyParticipations() を実行
   ↓
18. 最新の参加状況がカレンダーに反映
    - ●（緑色）: 全試合参加
    - ◐（黄色）: 部分参加
    - バッジなし: 未参加
```

---

### 7.2 自動マッチングフロー

```
[ユーザー操作]
1. /pairings へ移動
   ↓
2. 日付・試合番号選択（例: 2025-11-20, 1試合目）
   ↓
[フロントエンド]
3. GET /api/practice-sessions/{id}/participants
   ← 参加者一覧
   ↓
4. 参加者チェックボックス表示（デフォルト全員選択）
   ↓
[ユーザー操作]
5. 参加者選択調整
   ↓
6. 「自動マッチング」ボタンクリック
   ↓
[フロントエンド]
7. POST /api/match-pairings/auto-match
   Request Body: {
     sessionDate: "2025-11-20",
     matchNumber: 1,
     participantIds: [1, 2, 3, 4, 5]
   }
   ↓
[バックエンド: MatchPairingService.autoMatch()]
8. 過去30日の対戦履歴を取得
   SELECT ... FROM matches
   WHERE (player1_id IN (...) OR player2_id IN (...))
     AND match_date >= '2025-10-21'
   ↓
9. ペアごとの最終対戦日マップ作成
   Map<PairKey, List<LocalDate>> historyMap
   ↓
10. 同日既存対戦を取得（除外用）
    SELECT ... FROM matches
    WHERE match_date = '2025-11-20'
      AND match_number < 1
   ↓
11. 参加者をシャッフル
   Collections.shuffle(availablePlayers)
   ↓
12. ペアリングループ
   while (availablePlayers.size() >= 2):
     - 全未ペア選手の組み合わせでスコア計算
     - calculatePairScore(p1, p2, historyMap, sessionDate)
       score = -(100 / 最終対戦日からの日数)
       初対戦: score = 0
     - 最高スコアのペアを選択
     - pairings.add({player1, player2, score, recentMatches})
     - availablePlayers から2人削除
   ↓
13. 待機者リスト生成（残った選手）
   waitingPlayers = availablePlayers
   ↓
14. レスポンス: AutoMatchingResult
   {
     pairings: [
       {player1Id, player1Name, player2Id, player2Name, score, recentMatches: [{date, daysAgo}]},
       ...
     ],
     waitingPlayers: [{id, name}, ...]
   }
   ↓
[フロントエンド]
15. 提案されたペア一覧表示
    - 選手1 vs 選手2
    - 最近の対戦履歴（日付、何日前）
    - スコア（低い方が優先度高い）
   ↓
16. 待機者リスト表示
   ↓
[ユーザー操作]
17. 手動調整（ペア入れ替え、追加・削除）
   ↓
18. 「組み合わせ確定」ボタンクリック
   ↓
[フロントエンド]
19. POST /api/match-pairings/batch?date=2025-11-20&matchNumber=1
    Request Body: [
      {player1Id: 1, player2Id: 2},
      {player1Id: 3, player2Id: 4}
    ]
   ↓
[バックエンド: MatchPairingService.createBatch()]
20. 既存組み合わせ削除
    DELETE FROM match_pairings
    WHERE session_date = '2025-11-20' AND match_number = 1
   ↓
21. 新規組み合わせ一括登録
    INSERT INTO match_pairings (session_date, match_number, player1_id, player2_id, ...)
   ↓
22. レスポンス: [MatchPairingDto, ...]
   ↓
[フロントエンド]
23. 成功メッセージ表示
```

---

### 7.3 試合記録登録フロー（簡易登録）

```
[ユーザー操作]
1. /matches/new へ移動
   ↓
2. フォーム入力
   - 試合日: 2025-11-20
   - 試合番号: 1
   - 対戦相手名: 佐藤花子（未登録選手）
   - 結果: 勝ち
   - 札差: 5
   - コメント: よい試合でした
   ↓
3. 「登録」ボタンクリック
   ↓
[フロントエンド]
4. POST /api/matches
   Request Body: {
     matchDate: "2025-11-20",
     matchNumber: 1,
     playerId: 1,
     opponentName: "佐藤花子",
     result: "勝ち",
     scoreDifference: 5,
     notes: "よい試合でした"
   }
   ↓
[バックエンド: MatchService.createMatchSimple()]
5. 重複チェック
   existsByPlayerIdAndMatchDateAndMatchNumber(playerId, matchDate, matchNumber)
   → 既に同日同試合番号で登録済みならエラー
   ↓
6. Match エンティティ構築
   - player1Id = playerId (1)
   - player2Id = 0L（ダミーID）
   - winnerId = result に基づいて設定
     - "勝ち" → playerId
     - "負け" → 0L
     - "引き分け" → null（未実装想定）
   - opponentName = "佐藤花子"
   - scoreDifference = 5
   ↓
7. 保存
   INSERT INTO matches (player1_id, player2_id, winner_id, opponent_name, ...)
   ↓
8. 選手名付与（enrichMatchesWithPlayerNames）
   - Player情報取得 → MatchDto変換
   ↓
9. レスポンス: 201 Created, MatchDto
   ↓
[フロントエンド]
10. 成功メッセージ表示 → /matches へリダイレクト
```

---

## 8. 未実装機能・TODO

### 8.1 優先度: 高

#### セキュリティ
- [ ] **Spring Security + JWT認証** 導入
  - 現在の簡易ヘッダー認証（`X-User-Role`）を本格化
  - JWTトークン発行・検証
  - リフレッシュトークン
- [ ] **BCryptパスワードハッシュ化**
  - 現在は平文比較
  - `PasswordEncoder` 導入

#### API
- [ ] **選手プロフィールAPI** 実装
  - `GET /api/players/{id}/profiles/current`
  - `GET /api/players/{id}/profiles`
  - `POST /api/players/{id}/profiles`
- [ ] **ページネーション** 導入
  - 試合一覧、選手一覧で大量データ対応

### 8.2 優先度: 中

#### 機能追加
- [ ] **統計ページ** 実装
  - 選手別勝率ランキング
  - 月別試合数推移
  - 対戦相手別成績
- [ ] **試合結果CSVエクスポート**
- [ ] **練習参加率レポート**
- [ ] **通知機能**
  - 練習日前日リマインダー
  - 組み合わせ確定通知

#### UI/UX改善
- [ ] **レスポンシブデザイン** 最適化
  - タブレット対応
  - スマホ横画面対応
- [ ] **ダークモード** 対応
- [ ] **ローディング状態** 改善
  - スケルトンスクリーン導入

### 8.3 優先度: 低

#### 拡張機能
- [ ] **画像アップロード**
  - 選手プロフィール画像
  - 練習風景写真
- [ ] **試合動画アップロード** （将来）
- [ ] **SNSシェア機能**
- [ ] **多言語対応** （英語等）

#### その他
- [ ] **E2Eテスト** 導入（Cypress、Playwright等）
- [ ] **CI/CD** パイプライン構築
- [ ] **Docker Compose** 本番環境設定
- [ ] **監視・ログ** システム（Prometheus、Grafana等）

---

## 9. 補足事項

### 9.1 設計上の重要ポイント

#### player1_id < player2_id 制約
- `Match` エンティティで `@PrePersist`/`@PreUpdate` により自動保証
- データベースインデックスの効率化
- クエリ簡略化（`WHERE (player1_id = ? AND player2_id = ?) OR (player1_id = ? AND player2_id = ?)` 不要）

#### 論理削除
- `Player.deletedAt` により選手を論理削除
- 削除済み選手は更新・ログイン不可
- 過去の試合記録は保持（データ整合性）

#### 試合ごと参加登録
- `PracticeParticipant.matchNumber` により、各練習日の各試合（1～7）ごとに参加登録可能
- フロントエンドで試合ごとのチェックボックスを実装
- `PracticeSessionDto.matchParticipants` で試合ごとの参加者名リスト提供

#### 自動マッチングアルゴリズム
- 過去30日の対戦履歴を基にスコアリング
- 日数が経過するほど高スコア（再対戦しやすい）
- 初対戦: スコア0（最優先）
- 同日既存対戦を除外
- シャッフル + 貪欲法で最適ペアリング

#### 管理者登録時の自動試合参加（2025-11-19追加）
- 管理者が練習日に参加者を登録すると、その参加者は全試合に自動参加
- 理由: 運用の簡便性を優先（初期設定の手間を削減）
- ユーザーは後から参加登録画面で試合ごとに調整可能
- 設計思想: 柔軟性と初期設定の簡便性を両立

---

### 9.2 開発環境セットアップ

#### 前提条件
- Java 21
- Node.js 18+
- MySQL 8.0+

#### バックエンド起動
```bash
cd karuta-tracker
./gradlew bootRun
```
→ `http://localhost:8080`

#### フロントエンド起動
```bash
cd karuta-tracker-ui
npm install
npm run dev
```
→ `http://localhost:5176`

#### データベース初期化
```sql
CREATE DATABASE karuta_tracker;
-- スキーマは Hibernate が自動生成（spring.jpa.hibernate.ddl-auto=update）
```

---

### 9.3 リリースノート

#### v1.1.0（2025-11-19）
- **練習参加状況可視化機能**
  - カレンダーに参加状況バッジ表示（●全試合参加、◐部分参加）
  - モーダルで自分が参加する試合を緑色ハイライト
  - 試合別参加者リストの表示
- **UI/UX改善**
  - カレンダー上部に参加登録ボタン配置
  - モーダル内に参加登録ボタン配置
  - 参加登録保存後、自動的にカレンダー画面に戻る
  - データ自動再取得機能（画面フォーカス時）
- **管理者機能強化**
  - 管理者が練習日に参加者を登録すると全試合に自動参加
- **バグ修正**
  - 試合別参加者が空配列だった問題を解消
  - playerMapから直接参加者名を取得するように修正

#### v1.0.0（2025-11-17）
- 初回リリース
- 選手管理、試合記録、練習日管理、練習参加登録、対戦組み合わせ自動生成機能実装
- ロールベース権限管理（SUPER_ADMIN/ADMIN/PLAYER）
- カレンダー形式練習日一覧
- 試合ごと参加登録機能
- 自動マッチングアルゴリズム（過去対戦履歴考慮）

---

## 付録

### A. 用語集
- **選手**: システム登録済みのかるた競技者
- **試合**: 2人の選手（または選手と未登録相手）の対戦記録
- **練習日**: かるた練習会の日程
- **練習参加**: 選手が特定の練習日・試合番号に参加すること
- **対戦組み合わせ**: 特定の練習日・試合番号における選手ペアリング
- **自動マッチング**: 過去対戦履歴を考慮した最適ペアリング提案

### B. データベース初期データ

#### デフォルトスーパー管理者
```sql
INSERT INTO players (name, password, gender, role, created_at, updated_at)
VALUES ('土居悠太', 'password123', '男性', 'SUPER_ADMIN', NOW(), NOW());
```

---

以上
