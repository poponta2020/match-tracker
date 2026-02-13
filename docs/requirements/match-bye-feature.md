# 奇数参加者対応（不戦記録機能）要件定義書

## 1. 概要

### 1.1 目的
奇数人数の練習会において、対戦に参加できなかった選手（不戦者）の記録を保存・表示する機能を実装する。

### 1.2 背景
- 練習会の参加者が奇数の場合、1人が対戦できずに「抜け」となる
- 現状では抜けた選手の記録が残らず、後から確認できない
- 抜けた選手が何をしていたか（一人取り、読みなど）の情報も記録したい

### 1.3 用語定義
| 用語 | 説明 |
|------|------|
| 不戦者 | 奇数参加時に対戦相手がいなかった選手 |
| 抜け | 対戦せずに休憩していた状態 |
| 一人取り | 一人で札を取る練習をしていた状態 |
| 読み | 読み手として試合の進行を担当していた状態 |

---

## 2. 現状分析

### 2.1 既存システムの状態

#### データベース
- `match_pairings`: 対戦組み合わせを保存（2人1組）
- `matches`: 試合結果を保存（勝者、枚数差など）
- 不戦者を記録するテーブルは存在しない

#### フロントエンド
- `PairingGenerator.jsx`: 奇数時に `waitingPlayers` として余った選手を画面表示
- ペアリング保存時、待機選手の情報はDBに保存されない
- `MatchResultsView.jsx`: 対戦ペアのみ表示、不戦者は表示されない

### 2.2 課題
1. 不戦者が誰だったか、後から確認できない
2. 不戦者が何をしていたか（抜け/一人取り/読み）の記録がない
3. 参加人数として不戦者がカウントされない

---

## 3. 機能要件

### 3.1 機能一覧

| ID | 機能名 | 説明 | 優先度 |
|----|--------|------|--------|
| F01 | 不戦記録の保存 | 各試合番号ごとに不戦者と活動種別をDBに保存 | 高 |
| F02 | 活動種別の選択 | 「抜け」「一人取り」「読み」の3種類から選択 | 高 |
| F03 | 不戦記録の表示 | 試合結果閲覧画面で対戦組み合わせの下に不戦者を表示 | 高 |
| F04 | 不戦記録の編集 | 管理者は活動種別を変更可能 | 中 |
| F05 | 自動保存連携 | ペアリング保存時に待機選手を自動的に不戦記録として保存 | 高 |
| F06 | 参加人数への反映 | 不戦者を含めた参加人数を表示 | 中 |

### 3.2 活動種別の定義

| 種別 | 説明 | デフォルト |
|------|------|-----------|
| 抜け | 対戦せずに休憩・見学 | ○（デフォルト値） |
| 一人取り | 一人で札を取る練習 | |
| 読み | 読み手として試合進行を担当 | |

### 3.3 画面要件

#### 3.3.1 試合結果閲覧画面 (`MatchResultsView.jsx`)

**変更内容**:
- 対戦組み合わせリストの下に「不戦者」セクションを追加
- 不戦者がいる場合のみセクションを表示

**表示項目**:
- 不戦者名
- 活動種別（バッジ形式で表示）
- 活動種別ごとのアイコン
  - 抜け: `Coffee` アイコン
  - 一人取り: `User` アイコン
  - 読み: `BookOpen` アイコン

**UI例**:
```
┌─────────────────────────────────────┐
│ 第1試合 (6名参加)                    │
├─────────────────────────────────────┤
│ [選手A] ── 3枚差 ── [選手B]         │
│ [選手C] ── 5枚差 ── [選手D]         │
├─────────────────────────────────────┤
│ 📋 不戦者                            │
│ ┌─────────────────────────────────┐ │
│ │ ☕ 選手E  [抜け]                 │ │
│ └─────────────────────────────────┘ │
└─────────────────────────────────────┘
```

#### 3.3.2 ペアリング生成画面 (`PairingGenerator.jsx`)

**変更内容**:
- 待機選手セクションに活動種別の選択UIを追加
- 保存時に待機選手を不戦記録として自動保存

**UI例**:
```
┌─────────────────────────────────────┐
│ 待機中の選手                         │
├─────────────────────────────────────┤
│ 選手E                                │
│ 活動種別: [抜け ▼]                   │
│   ├ 抜け                             │
│   ├ 一人取り                         │
│   └ 読み                             │
└─────────────────────────────────────┘
```

---

## 4. データベース設計

### 4.1 新規テーブル: `match_byes`

```sql
CREATE TABLE match_byes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主キー',
    session_date DATE NOT NULL COMMENT '練習日',
    match_number INT NOT NULL COMMENT '試合番号',
    player_id BIGINT NOT NULL COMMENT '不戦選手のID',
    activity_type ENUM('抜け', '一人取り', '読み') NOT NULL DEFAULT '抜け' COMMENT '活動種別',
    created_by BIGINT NOT NULL COMMENT '作成者ID',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '作成日時',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新日時',

    -- 外部キー制約
    CONSTRAINT fk_match_byes_player
        FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE RESTRICT,
    CONSTRAINT fk_match_byes_created_by
        FOREIGN KEY (created_by) REFERENCES players(id) ON DELETE RESTRICT,

    -- ユニーク制約（同一試合で同一選手は1レコードのみ）
    CONSTRAINT uk_match_byes_unique
        UNIQUE (session_date, match_number, player_id),

    -- インデックス
    INDEX idx_match_byes_date (session_date),
    INDEX idx_match_byes_date_match (session_date, match_number),
    INDEX idx_match_byes_player (player_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='不戦記録テーブル';
```

### 4.2 ER図（関連テーブル）

```
players (選手)
    │
    ├──< match_pairings (対戦組み合わせ)
    │       - player1_id
    │       - player2_id
    │
    ├──< matches (試合結果)
    │       - player1_id
    │       - player2_id
    │       - winner_id
    │
    └──< match_byes (不戦記録) ← 新規追加
            - player_id
            - activity_type
```

---

## 5. API設計

### 5.1 エンドポイント一覧

| メソッド | エンドポイント | 説明 |
|---------|---------------|------|
| GET | `/api/match-byes/date` | 指定日の不戦記録を取得 |
| GET | `/api/match-byes/date-and-match` | 指定日・試合番号の不戦記録を取得 |
| POST | `/api/match-byes` | 不戦記録を作成 |
| POST | `/api/match-byes/batch` | 不戦記録を一括作成 |
| PUT | `/api/match-byes/{id}` | 不戦記録を更新（活動種別変更） |
| DELETE | `/api/match-byes/{id}` | 不戦記録を削除 |
| DELETE | `/api/match-byes/date-and-match` | 指定日・試合番号の不戦記録を削除 |

### 5.2 リクエスト・レスポンス仕様

#### GET `/api/match-byes/date`

**リクエストパラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| date | string | ○ | 練習日（YYYY-MM-DD形式） |

**レスポンス例**:
```json
[
  {
    "id": 1,
    "sessionDate": "2026-01-14",
    "matchNumber": 1,
    "playerId": 5,
    "playerName": "田中太郎",
    "activityType": "抜け",
    "createdBy": 1,
    "createdAt": "2026-01-14T10:00:00",
    "updatedAt": "2026-01-14T10:00:00"
  }
]
```

#### POST `/api/match-byes/batch`

**リクエストパラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| date | string | ○ | 練習日（YYYY-MM-DD形式） |
| matchNumber | int | ○ | 試合番号 |

**リクエストボディ**:
```json
[
  {
    "playerId": 5,
    "activityType": "抜け"
  }
]
```

#### PUT `/api/match-byes/{id}`

**リクエストボディ**:
```json
{
  "activityType": "一人取り"
}
```

---

## 6. バックエンド実装

### 6.1 新規作成ファイル

| ファイル | 説明 |
|---------|------|
| `entity/MatchBye.java` | 不戦記録エンティティ |
| `dto/MatchByeDto.java` | 不戦記録DTO |
| `dto/MatchByeCreateRequest.java` | 作成リクエストDTO |
| `dto/MatchByeUpdateRequest.java` | 更新リクエストDTO |
| `repository/MatchByeRepository.java` | リポジトリ |
| `service/MatchByeService.java` | サービス |
| `controller/MatchByeController.java` | コントローラー |

### 6.2 エンティティ定義

```java
@Entity
@Table(name = "match_byes")
public class MatchBye {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "match_number", nullable = false)
    private Integer matchNumber;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false)
    private ActivityType activityType;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    // ... timestamps

    public enum ActivityType {
        抜け, 一人取り, 読み
    }
}
```

---

## 7. フロントエンド実装

### 7.1 新規・変更ファイル

| ファイル | 変更内容 |
|---------|---------|
| `api/matchByes.js` | 新規作成 - API呼び出し関数 |
| `api/index.js` | 変更 - matchByeAPI のエクスポート追加 |
| `pages/matches/MatchResultsView.jsx` | 変更 - 不戦者表示セクション追加 |
| `pages/pairings/PairingGenerator.jsx` | 変更 - 活動種別選択UI追加、保存処理変更 |

### 7.2 API モジュール

```javascript
// api/matchByes.js
export const matchByeAPI = {
  getByDate: (date) =>
    apiClient.get('/match-byes/date', { params: { date } }),

  getByDateAndMatchNumber: (date, matchNumber) =>
    apiClient.get('/match-byes/date-and-match', { params: { date, matchNumber } }),

  createBatch: (date, matchNumber, byes) =>
    apiClient.post('/match-byes/batch', byes, { params: { date, matchNumber } }),

  update: (id, data) =>
    apiClient.put(`/match-byes/${id}`, data),

  delete: (id) =>
    apiClient.delete(`/match-byes/${id}`),
};
```

---

## 8. 非機能要件

### 8.1 パフォーマンス
- 1日あたり最大50件程度の不戦記録を想定
- 一覧取得は1秒以内にレスポンス

### 8.2 互換性
- 既存のペアリング・試合結果機能に影響を与えない
- 不戦記録がない日でも正常に動作する

### 8.3 セキュリティ
- 不戦記録の作成・編集・削除は管理者権限が必要
- 閲覧はログインユーザー全員が可能

---

## 9. テスト観点

### 9.1 単体テスト
- [ ] MatchByeRepository の各メソッド
- [ ] MatchByeService の各メソッド
- [ ] バリデーション（必須項目、ENUM値）

### 9.2 結合テスト
- [ ] API エンドポイントの正常系・異常系
- [ ] ペアリング保存時の不戦記録自動作成

### 9.3 画面テスト
- [ ] 試合結果閲覧画面での不戦者表示
- [ ] 活動種別の選択・変更
- [ ] 不戦者がいない場合の表示

---

## 10. 実装スケジュール

### フェーズ1: DB・バックエンド
1. DBマイグレーションスクリプト作成
2. Entity, DTO 作成
3. Repository, Service 作成
4. Controller 作成
5. バックエンドテスト

### フェーズ2: フロントエンド
1. API モジュール作成
2. MatchResultsView.jsx 変更
3. PairingGenerator.jsx 変更
4. 画面テスト

---

## 11. 承認

| 役割 | 氏名 | 日付 | 署名 |
|------|------|------|------|
| 作成者 | | | |
| レビュアー | | | |
| 承認者 | | | |

---

## 変更履歴

| バージョン | 日付 | 変更者 | 変更内容 |
|-----------|------|--------|---------|
| 1.0 | 2026-01-14 | | 初版作成 |
