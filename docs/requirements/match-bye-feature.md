# 抜け番活動記録機能 要件定義書

## 1. 概要

### 1.1 目的
奇数人数の練習会において、対戦に参加できなかった選手（抜け番）の活動内容を試合番号ごとに記録・表示・集計できるようにする。

### 1.2 背景
- 練習会の参加者が奇数の場合、1人が対戦できずに「抜け番」となる
- 現状では抜け番の選手の記録が残らず、後から確認できない
- 抜け番の選手が何をしていたか（読み、一人取り、見学など）の情報を記録・集計したい

### 1.3 用語定義

| 用語 | 説明 |
|------|------|
| 抜け番 | 奇数参加時に対戦相手がいなかった選手 |
| 読み | 読み手として試合の進行を担当していた状態 |
| 一人取り | 一人で札を取る練習をしていた状態 |
| 見学 | 他の選手の試合を見学していた状態 |
| 見学対応 | 見学者への説明・案内を担当していた状態 |

---

## 2. 現状分析

### 2.1 既存システムの状態

#### データベース
- `match_pairings`: 対戦組み合わせを保存（2人1組）
- `matches`: 試合結果を保存（勝者、枚数差など）
- `practice_participants`: 抜け番は `match_number = NULL` で登録されている
- 抜け番の活動内容を記録するテーブルは存在しない

#### フロントエンド
- `PairingGenerator.jsx`: 奇数時に `waitingPlayers` として余った選手を画面表示
- `MatchResultsView.jsx`: 対戦ペアのみ表示、抜け番は表示されない
- `MatchForm.jsx`: 対戦結果のみ入力可能、抜け番の活動入力UIなし
- `BulkResultInput.jsx`: 管理者一括入力画面、抜け番の活動入力UIなし

### 2.2 課題
1. 抜け番が誰だったか、結果閲覧画面から確認できない
2. 抜け番の選手が何をしていたかの記録がない
3. 「誰が何回読みをしたか」等の集計ができない

---

## 3. 機能要件

### 3.1 機能一覧

| ID | 機能名 | 説明 | 優先度 |
|----|--------|------|--------|
| F01 | 抜け番活動の保存 | 各試合番号ごとに抜け番選手の活動種別をDBに保存 | 高 |
| F02 | 活動種別の選択 | 5種類（読み/一人取り/見学/見学対応/その他）から選択 | 高 |
| F03 | 自由テキスト入力 | 「その他」選択時のみ自由記述テキストを入力可能 | 高 |
| F04 | 本人による入力 | MatchForm画面から抜け番の本人が活動を入力 | 高 |
| F05 | 管理者による一括入力 | BulkResultInput画面で管理者が抜け番の活動を入力 | 高 |
| F06 | 結果閲覧画面での表示 | MatchResultsView画面で対戦結果と合わせて抜け番の活動を表示 | 高 |
| F07 | 自動保存連携 | ペアリング保存時に待機選手を自動的に抜け番記録として保存 | 高 |
| F08 | 選手別活動履歴の取得 | 集計用に選手別の活動履歴をAPIで取得可能 | 中 |
| F09 | 参加人数への反映 | 抜け番を含めた参加人数を表示 | 中 |

### 3.2 活動種別の定義

| Enum値 | 表示名 | 説明 |
|--------|--------|------|
| READING | 読み | 読み手として試合進行を担当 |
| SOLO_PICK | 一人取り | 一人で札を取る練習 |
| OBSERVING | 見学 | 他の選手の試合を見学 |
| ASSIST_OBSERVING | 見学対応 | 見学者への説明・案内を担当 |
| OTHER | その他 | 上記以外の活動（自由テキストで補足） |

### 3.3 画面要件

#### 3.3.1 対戦結果入力画面 — 本人入力 (`MatchForm.jsx`)

**変更内容**:
- 抜け番の試合番号タブを選択した際、通常の対戦入力フォームの代わりに活動種別選択UIを表示
- 5つの活動種別をボタンまたはラジオボタンで表示
- 「その他」選択時のみ自由テキスト入力欄を表示
- 保存/更新は既存の対戦結果と同様のUXで行う

**UI例**:
```
┌─────────────────────────────────────┐
│ [第1試合] [第2試合] [第3試合▼]       │
├─────────────────────────────────────┤
│ この試合は抜け番です                  │
│                                      │
│ 活動内容を選択してください:           │
│                                      │
│ ○ 読み                              │
│ ○ 一人取り                          │
│ ○ 見学                              │
│ ○ 見学対応                          │
│ ○ その他                            │
│   └ [自由テキスト入力欄]            │
│                                      │
│         [保存]  [キャンセル]          │
└─────────────────────────────────────┘
```

#### 3.3.2 対戦結果一括入力画面 — 管理者入力 (`BulkResultInput.jsx`)

**変更内容**:
- 各試合番号タブ内のペアリング一覧の下に「抜け番」セクションを追加
- 抜け番選手名 + 活動種別ドロップダウンを横並びで表示
- 「その他」選択時のみテキスト入力欄を追加表示
- 既存の一括保存フローに含めて保存

**UI例**:
```
┌─────────────────────────────────────┐
│ 第1試合                              │
├─────────────────────────────────────┤
│ [選手A] ○  枚数差  × [選手B]        │
│ [選手C] ○  枚数差  × [選手D]        │
├─────────────────────────────────────┤
│ 抜け番:                              │
│ 選手E  [読み ▼]                      │
│   ├ 読み                             │
│   ├ 一人取り                         │
│   ├ 見学                             │
│   ├ 見学対応                         │
│   └ その他                           │
│     └ [自由テキスト入力欄]           │
└─────────────────────────────────────┘
```

#### 3.3.3 試合結果閲覧画面 (`MatchResultsView.jsx`)

**変更内容**:
- 各試合番号の対戦結果一覧の下に、抜け番の活動を表示
- 抜け番がいる場合のみセクションを表示

**表示項目**:
- 抜け番選手名
- 活動種別（バッジ形式で表示）
- 「その他」の場合は自由テキストも表示
- 活動種別ごとのアイコン
  - 読み: `BookOpen` アイコン
  - 一人取り: `User` アイコン
  - 見学: `Eye` アイコン
  - 見学対応: `Users` アイコン
  - その他: `MoreHorizontal` アイコン

**UI例**:
```
┌─────────────────────────────────────┐
│ 第1試合                              │
├─────────────────────────────────────┤
│ [選手A] ── 3枚差 ── [選手B]         │
│ [選手C] ── 5枚差 ── [選手D]         │
├─────────────────────────────────────┤
│ 📋 抜け番                            │
│ ┌─────────────────────────────────┐ │
│ │ 📖 選手E  [読み]                │ │
│ └─────────────────────────────────┘ │
└─────────────────────────────────────┘
```

#### 3.3.4 ペアリング生成画面 (`PairingGenerator.jsx`)

**変更内容**:
- 待機選手セクションに活動種別の選択UIを追加
- 保存時に待機選手を抜け番記録として自動保存

**UI例**:
```
┌─────────────────────────────────────┐
│ 待機中の選手                         │
├─────────────────────────────────────┤
│ 選手E                                │
│ 活動種別: [読み ▼]                   │
│   ├ 読み                             │
│   ├ 一人取り                         │
│   ├ 見学                             │
│   ├ 見学対応                         │
│   └ その他                           │
│     └ [自由テキスト入力欄]           │
└─────────────────────────────────────┘
```

---

## 4. データベース設計

### 4.1 新規テーブル: `bye_activities`

```sql
CREATE TABLE bye_activities (
    id BIGSERIAL PRIMARY KEY,
    session_date DATE NOT NULL,
    match_number INT NOT NULL,
    player_id BIGINT NOT NULL,
    activity_type VARCHAR(20) NOT NULL,
    free_text VARCHAR(255),
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 外部キー制約
    CONSTRAINT fk_bye_activities_player
        FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bye_activities_created_by
        FOREIGN KEY (created_by) REFERENCES players(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bye_activities_updated_by
        FOREIGN KEY (updated_by) REFERENCES players(id) ON DELETE RESTRICT,

    -- ユニーク制約（同一試合で同一選手は1レコードのみ）
    CONSTRAINT uk_bye_activities_unique
        UNIQUE (session_date, match_number, player_id)
);

-- インデックス
CREATE INDEX idx_bye_activities_date ON bye_activities (session_date);
CREATE INDEX idx_bye_activities_date_match ON bye_activities (session_date, match_number);
CREATE INDEX idx_bye_activities_player ON bye_activities (player_id);
```

**補足**:
- `activity_type`: アプリケーション側のEnum(`READING`, `SOLO_PICK`, `OBSERVING`, `ASSIST_OBSERVING`, `OTHER`)で管理
- `free_text`: `activity_type = 'OTHER'` の場合のみ使用。それ以外の場合はNULL
- PostgreSQL構文（本番DB: Render PostgreSQL）

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
    └──< bye_activities (抜け番活動記録) ← 新規追加
            - player_id
            - activity_type
            - free_text
```

---

## 5. API設計

### 5.1 エンドポイント一覧

| メソッド | エンドポイント | 説明 | 権限 |
|---------|---------------|------|------|
| GET | `/api/bye-activities?date={date}` | 指定日の抜け番活動を取得 | ログインユーザー |
| GET | `/api/bye-activities?date={date}&matchNumber={n}` | 指定日・試合番号の抜け番活動を取得 | ログインユーザー |
| POST | `/api/bye-activities` | 抜け番活動を作成（本人入力） | ログインユーザー |
| POST | `/api/bye-activities/batch` | 抜け番活動を一括作成 | ADMIN以上 |
| PUT | `/api/bye-activities/{id}` | 抜け番活動を更新 | ログインユーザー（本人）/ ADMIN以上 |
| DELETE | `/api/bye-activities/{id}` | 抜け番活動を削除 | ADMIN以上 |
| GET | `/api/bye-activities/player/{playerId}` | 選手別の活動履歴を取得（集計用） | ログインユーザー |

### 5.2 リクエスト・レスポンス仕様

#### GET `/api/bye-activities?date={date}`

**リクエストパラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| date | string | ○ | 練習日（YYYY-MM-DD形式） |
| matchNumber | int | - | 試合番号（指定時はその試合のみ取得） |

**レスポンス例**:
```json
[
  {
    "id": 1,
    "sessionDate": "2026-03-24",
    "matchNumber": 1,
    "playerId": 5,
    "playerName": "田中太郎",
    "activityType": "READING",
    "activityTypeDisplay": "読み",
    "freeText": null,
    "createdBy": 5,
    "updatedBy": 5,
    "createdAt": "2026-03-24T10:00:00",
    "updatedAt": "2026-03-24T10:00:00"
  },
  {
    "id": 2,
    "sessionDate": "2026-03-24",
    "matchNumber": 3,
    "playerId": 8,
    "playerName": "鈴木花子",
    "activityType": "OTHER",
    "activityTypeDisplay": "その他",
    "freeText": "審判練習",
    "createdBy": 1,
    "updatedBy": 1,
    "createdAt": "2026-03-24T10:30:00",
    "updatedAt": "2026-03-24T10:30:00"
  }
]
```

#### POST `/api/bye-activities`

**リクエストボディ**:
```json
{
  "sessionDate": "2026-03-24",
  "matchNumber": 1,
  "playerId": 5,
  "activityType": "READING",
  "freeText": null
}
```

#### POST `/api/bye-activities/batch`

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
    "activityType": "READING",
    "freeText": null
  },
  {
    "playerId": 8,
    "activityType": "OTHER",
    "freeText": "審判練習"
  }
]
```

#### PUT `/api/bye-activities/{id}`

**リクエストボディ**:
```json
{
  "activityType": "SOLO_PICK",
  "freeText": null
}
```

#### GET `/api/bye-activities/player/{playerId}`

**リクエストパラメータ**:
| パラメータ | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| type | string | - | 活動種別でフィルタ（例: `READING`） |

**レスポンス**: 上記 GET と同形式のリスト

---

## 6. バックエンド実装

### 6.1 新規作成ファイル

| ファイル | 説明 |
|---------|------|
| `entity/ByeActivity.java` | 抜け番活動エンティティ |
| `entity/ActivityType.java` | 活動種別Enum |
| `dto/ByeActivityDto.java` | 抜け番活動DTO |
| `dto/ByeActivityCreateRequest.java` | 作成リクエストDTO |
| `dto/ByeActivityUpdateRequest.java` | 更新リクエストDTO |
| `repository/ByeActivityRepository.java` | リポジトリ |
| `service/ByeActivityService.java` | サービス |
| `controller/ByeActivityController.java` | コントローラー |

### 6.2 エンティティ定義

```java
@Entity
@Table(name = "bye_activities",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_bye_activities_unique",
        columnNames = {"session_date", "match_number", "player_id"}))
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ByeActivity {
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

    @Column(name = "free_text")
    private String freeText;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
```

### 6.3 Enum定義

```java
public enum ActivityType {
    READING("読み"),
    SOLO_PICK("一人取り"),
    OBSERVING("見学"),
    ASSIST_OBSERVING("見学対応"),
    OTHER("その他");

    private final String displayName;

    ActivityType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
```

---

## 7. フロントエンド実装

### 7.1 新規・変更ファイル

| ファイル | 変更種別 | 内容 |
|---------|---------|------|
| `api/byeActivities.js` | 新規 | API呼び出し関数 |
| `pages/matches/MatchForm.jsx` | 変更 | 抜け番の試合番号選択時に活動種別選択UIを表示 |
| `pages/matches/BulkResultInput.jsx` | 変更 | 抜け番セクション追加、一括保存に活動も含める |
| `pages/matches/MatchResultsView.jsx` | 変更 | 抜け番の活動を表示するセクション追加 |
| `pages/pairings/PairingGenerator.jsx` | 変更 | 待機選手に活動種別選択UI追加、保存時に自動登録 |

### 7.2 API モジュール

```javascript
// api/byeActivities.js
import apiClient from './client';

export const byeActivityAPI = {
  getByDate: (date) =>
    apiClient.get('/bye-activities', { params: { date } }),

  getByDateAndMatch: (date, matchNumber) =>
    apiClient.get('/bye-activities', { params: { date, matchNumber } }),

  create: (data) =>
    apiClient.post('/bye-activities', data),

  createBatch: (date, matchNumber, activities) =>
    apiClient.post('/bye-activities/batch', activities, { params: { date, matchNumber } }),

  update: (id, data) =>
    apiClient.put(`/bye-activities/${id}`, data),

  delete: (id) =>
    apiClient.delete(`/bye-activities/${id}`),

  getByPlayer: (playerId, type) =>
    apiClient.get(`/bye-activities/player/${playerId}`, { params: { type } }),
};
```

---

## 8. 非機能要件

### 8.1 パフォーマンス
- 1日あたり最大50件程度の抜け番記録を想定
- 一覧取得は1秒以内にレスポンス

### 8.2 互換性
- 既存のペアリング・試合結果機能に影響を与えない
- 抜け番記録がない日でも正常に動作する
- 活動の入力は任意（未入力でもエラーにしない）

### 8.4 抜け番判定ロジックの制約
MatchFormでの抜け番判定は「自分のペアリングがない + その試合番号に他のペアリングが存在する」で行う。
以下の制約がある：
- 組み合わせ未作成の試合では抜け番と判定されない（通常の新規入力画面が表示される）
- 組み合わせ削除後は抜け番判定が解除される（ただしDB上の抜け番活動記録は残る）
- 全員がペアリング未割当ての場合（全員抜け番）は検出不可（ペアリングが0件のため）
- BulkResultInputでは `matchParticipants` とペアリングの差分から抜け番を算出するため、上記の制限はない

### 8.3 セキュリティ
- 閲覧: ログインユーザー全員
- 本人入力（POST/PUT）: 自分自身の抜け番活動のみ
- 一括入力・削除: ADMIN以上の権限が必要

---

## 9. テスト観点

### 9.1 単体テスト
- [ ] ByeActivityRepository の各メソッド
- [ ] ByeActivityService の各メソッド
- [ ] バリデーション（必須項目、Enum値、free_textの条件付き必須）

### 9.2 結合テスト
- [ ] API エンドポイントの正常系・異常系
- [ ] ペアリング保存時の抜け番活動自動作成
- [ ] 「その他」選択時のみfree_textが保存されること
- [ ] 権限チェック（本人/管理者の入力・更新・削除）

### 9.3 画面テスト
- [ ] MatchForm: 抜け番の試合番号タブで活動種別選択UIが表示されること
- [ ] MatchForm: 「その他」選択時のみ自由テキスト入力欄が表示されること
- [ ] BulkResultInput: 抜け番セクションが表示されること
- [ ] MatchResultsView: 抜け番の活動がバッジ形式で表示されること
- [ ] PairingGenerator: 待機選手の活動種別が選択・保存できること
- [ ] 抜け番がいない場合にセクションが表示されないこと

---

## 10. 実装スケジュール

### フェーズ1: DB・バックエンド
1. DBマイグレーションスクリプト作成（`bye_activities` テーブル）
2. Entity（`ByeActivity.java`）、Enum（`ActivityType.java`）作成
3. DTO（`ByeActivityDto`, `ByeActivityCreateRequest`, `ByeActivityUpdateRequest`）作成
4. Repository（`ByeActivityRepository.java`）作成
5. Service（`ByeActivityService.java`）作成
6. Controller（`ByeActivityController.java`）作成

### フェーズ2: フロントエンド
1. APIモジュール作成（`byeActivities.js`）
2. MatchForm.jsx 変更（本人入力UI）
3. BulkResultInput.jsx 変更（管理者一括入力UI）
4. MatchResultsView.jsx 変更（抜け番活動表示）
5. PairingGenerator.jsx 変更（待機選手に活動種別選択追加）

---

## 11. 対象外（今回のスコープ外）

- 集計ダッシュボード画面の新設（APIのみ用意し、画面は将来対応）
- LINE通知への抜け番活動情報の追加

---

## 12. 承認

| 役割 | 氏名 | 日付 | 署名 |
|------|------|------|------|
| 作成者 | | | |
| レビュアー | | | |
| 承認者 | | | |

---

## 変更履歴

| バージョン | 日付 | 変更内容 |
|-----------|------|---------|
| 1.0 | 2026-01-14 | 初版作成（活動種別3種: 抜け/一人取り/読み） |
| 2.0 | 2026-03-26 | 大幅改訂: 活動種別を5種に拡張（読み/一人取り/見学/見学対応/その他）、「その他」自由テキスト対応、本人入力（MatchForm）追加、管理者一括入力（BulkResultInput）追加、集計用API追加、テーブル名を `bye_activities` に変更、PostgreSQL対応 |
