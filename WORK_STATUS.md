# 作業進捗状況

**最終更新**: 2025-11-05

---

## 📌 現在の状況

### ✅ 完了した作業

1. **環境セットアップ**
   - SDKMAN 5.20.0 インストール
   - Java 25.0.1 LTS インストール
   - Gradle 9.2.0 インストール

2. **Spring Boot プロジェクト生成**
   - Spring Boot 3.4.1
   - Gradle プロジェクト
   - 依存関係: Web, JPA, MySQL, Thymeleaf, DevTools, Lombok

3. **データベース設定**
   - `application.properties` に MySQL 接続設定を追加
   - データベース名: `karuta_tracker`

4. **初期エンティティ作成（※見直し予定）**
   - `Player.java` - 選手情報
   - `Match.java` - 対戦結果
   - `PlayerRepository.java` - プレイヤーのリポジトリ

---

## 🚧 現在進行中の作業

### データベース設計の見直し

ユーザーから詳細なデータ項目とRDB定義を受け取り、現在のエンティティ設計との整合性を確認中。

---

## 💬 議論中の内容

### ユーザーが求めるデータ項目

#### 対戦結果に関する項目
- 対戦日（○日の○試合目まで追える）
- 対戦者名
- 勝敗結果
- 枚数差
- 対戦時間
- コメント
- AI分析結果

#### 個人別成績
- 総対戦数
- 勝利数
- 敗北数
- 勝率
- 勝利時平均枚数
- 敗北時平均枚数
- 最高連勝数
- 最高連敗数
- 対戦相手別成績
- 月別成績
- 年別成績
- AI分析傾向

### ユーザーが提示したRDB定義

#### 対戦結果テーブル
- id (PK)
- 対戦日
- 対戦者名
- 勝敗結果
- 枚数差
- コメント
- AI分析結果
- 作成日時、更新日時
- 作成者、更新者

#### 個人成績テーブル
- id (PK)
- 対戦者名
- 総対戦数、勝利数、敗北数、勝率
- 勝利時平均枚数、敗北時平均枚数
- 最高連勝数、最高連敗数
- 対戦相手別成績 (JSON)
- 月別成績 (JSON)
- 年別成績 (JSON)
- AI分析傾向 (JSON)
- 作成日時、更新日時
- 作成者、更新者

---

## 🎯 Claude の分析と提案

### 主な改善提案

#### 1. プレイヤーマスタテーブルの作成
**理由**:
- 対戦者名を文字列で保存すると表記ゆれで同一人物が別人扱いになる
- IDで管理することで一意性を保証

**提案内容**:
```
players テーブル
- id (PK)
- name
- email (任意)
- phone_number (任意)
- notes
- created_at, updated_at
```

#### 2. 対戦結果テーブルの修正
**問題点**:
- かるたは1対1なので、両プレイヤーの情報が必要
- 対戦時間がテーブル定義に含まれていない
- 「○日の○試合目」を追うフィールドがない

**提案内容**:
```
matches テーブル
- id (PK)
- match_date (対戦日)
- match_number (その日の何試合目か) ★追加
- player1_id (FK) ★変更
- player2_id (FK) ★追加
- player1_score
- player2_score
- winner_id (FK) ★自動判定
- match_duration (対戦時間・分) ★追加
- location (場所)
- match_type (試合タイプ)
- notes (コメント)
- ai_analysis (AI分析結果)
- created_at, updated_at
```

#### 3. 個人成績テーブルは不要（動的集計を推奨）
**理由**:
- データの二重管理になり整合性維持が困難
- 対戦結果から計算可能な値を保存するのは冗長
- JSON形式のデータは検索・集計が困難

**提案**:
- 個人成績は対戦結果テーブルから **Serviceレイヤーで動的に集計**
- 統計情報は必要な時にSQLで計算
- パフォーマンスが問題になったら後でキャッシュテーブル追加を検討

**集計方法（例）**:
- 総対戦数: `player1_id` または `player2_id` でカウント
- 勝利数: `winner_id` でカウント
- 勝率: 勝利数 ÷ 総対戦数
- 月別成績: `match_date` で月ごとに GROUP BY

#### 4. その他の検討事項
- **枚数差**: 計算で求められる（player1_score - player2_score）ので、フィールド不要
- **作成者・更新者**: ユーザー管理機能が要件に含まれていないので、現時点では不要の可能性

---

## ❓ 次回確認すべき事項

### ユーザーへの5つの質問

1. **プレイヤーマスタテーブルの作成**に賛成ですか？
   - YES → プレイヤーをIDで管理
   - NO → 対戦者名を文字列で保存（表記ゆれのリスクあり）

2. **個人成績テーブルを削除**して動的集計にすることに賛成ですか？
   - YES → 対戦結果から統計を計算（整合性◎、初期実装が楽）
   - NO → 個人成績テーブルを保持（パフォーマンス◎、保守が複雑）

3. **作成者・更新者フィールド**は必要ですか？
   - ユーザー管理機能は今回の要件に含まれていない
   - 将来の拡張性のために残すかどうか？

4. **対戦時間の単位**は何ですか？
   - 分？秒？時:分:秒形式？

5. **枚数差フィールド**は必要ですか？
   - 計算で求められる（player1_score - player2_score）
   - それでもDBに保存したい？

---

## 📝 次にすべきタスク

### STEP 1: ユーザーから5つの確認事項への回答を得る

上記の質問に回答してもらい、最終的なテーブル設計を確定する。

### STEP 2: データベース設計の確定と実装

確認が取れたら：

1. **既存エンティティの修正または削除**
   - `Player.java`
   - `Match.java`
   - `PlayerRepository.java`

2. **新しいエンティティの作成**
   - 確定した設計に基づいて作成
   - 個人成績テーブルが必要な場合は `PlayerStats.java` も作成

3. **Repositoryの実装**
   - `PlayerRepository.java`
   - `MatchRepository.java`
   - (必要なら) `PlayerStatsRepository.java`

4. **統計集計用のカスタムクエリ追加**
   - 個人成績の動的集計用メソッド
   - 月別・年別成績の集計メソッド

### STEP 3: Serviceレイヤーの実装

- `PlayerService.java`
- `MatchService.java`
- `StatisticsService.java`（統計情報の計算）
- `ClaudeApiService.java`（AI分析）

### STEP 4: Controllerの実装

- `MatchController.java`（対戦一覧・入力）
- `PlayerController.java`（個人結果）
- `StatisticsController.java`（統計情報）

### STEP 5: Viewの実装（Thymeleaf）

- 対戦結果一覧・検索画面
- 対戦結果入力画面
- 個人の結果一覧画面
- 統計情報画面

### STEP 6: スタイリング（Bootstrap）

### STEP 7: Claude API連携の実装

---

## 📂 現在のプロジェクト構造

```
match-tracker/
├── README.md
├── SETUP_GUIDE.md
├── WORK_STATUS.md (このファイル)
├── CHANGELOG.md
└── karuta-tracker/
    ├── build.gradle
    ├── settings.gradle
    └── src/main/
        ├── java/com/karuta/matchtracker/
        │   ├── MatchTrackerApplication.java
        │   ├── entity/
        │   │   ├── Player.java (※見直し予定)
        │   │   └── Match.java (※見直し予定)
        │   ├── repository/
        │   │   └── PlayerRepository.java (※見直し予定)
        │   ├── service/ (未実装)
        │   ├── controller/ (未実装)
        │   └── dto/ (未実装)
        └── resources/
            ├── application.properties (設定済み)
            ├── templates/ (空)
            └── static/ (空)
```

---

## 🔄 作業再開時の手順

1. このファイル（`WORK_STATUS.md`）を確認
2. 「次にすべきタスク」のSTEP 1から開始
3. 5つの確認事項への回答を得る
4. テーブル設計を確定させる
5. 実装に進む

---

## 📞 質問や不明点があれば

作業再開時に、以下のように声をかけてください：

「作業を再開します。WORK_STATUS.mdの内容を確認して、データ定義の確認から始めましょう」

または

「前回の続きから始めたいです」

これで、スムーズに作業を再開できます！
