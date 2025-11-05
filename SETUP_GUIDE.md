# かるた結果集計アプリ - セットアップ記録

## 実施日
2025-11-05

## プロジェクト概要

**プロジェクト名**: Match Tracker (かるた結果集計システム)

**技術スタック**:
- Java 25.0.1 LTS
- Spring Boot 3.4.1
- Gradle 9.2.0
- MySQL/MariaDB
- Thymeleaf
- Bootstrap
- Claude API (統計分析用)

---

## 1. 環境セットアップ

### 1.1 SDKMAN のインストール

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
```

**インストール済みバージョン**: SDKMAN 5.20.0

### 1.2 Java のインストール

```bash
sdk install java
```

**インストール済みバージョン**: Java 25.0.1 LTS (Temurin)

### 1.3 Gradle のインストール

```bash
sdk install gradle
```

**インストール済みバージョン**: Gradle 9.2.0

---

## 2. Spring Boot プロジェクト生成

### 2.1 Spring Initializr で生成

以下の設定でプロジェクトを生成：

- **Type**: Gradle Project
- **Language**: Java
- **Spring Boot**: 3.4.1
- **Group**: com.karuta
- **Artifact**: match-tracker
- **Package Name**: com.karuta.matchtracker
- **Java Version**: 21

### 2.2 依存関係

- Spring Web
- Spring Data JPA
- MySQL Driver
- Thymeleaf
- Spring Boot DevTools
- Lombok

---

## 3. データベース設定

### 3.1 application.properties

`src/main/resources/application.properties` に以下を設定：

```properties
spring.application.name=match-tracker

# MySQL Database Configuration
spring.datasource.url=jdbc:mysql://localhost:3306/karuta_tracker?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=Asia/Tokyo
spring.datasource.username=root
spring.datasource.password=
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# JPA/Hibernate Configuration
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect

# Thymeleaf Configuration
spring.thymeleaf.cache=false
spring.thymeleaf.prefix=classpath:/templates/
spring.thymeleaf.suffix=.html

# Server Configuration
server.port=8080

# Logging Configuration
logging.level.com.karuta.matchtracker=DEBUG
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
```

---

## 4. エンティティクラスの実装

### 4.1 プロジェクト構造

```
src/main/java/com/karuta/matchtracker/
├── entity/
│   ├── Player.java
│   └── Match.java
├── repository/
│   └── PlayerRepository.java
├── service/
├── controller/
└── dto/
```

### 4.2 Player エンティティ

**ファイル**: `entity/Player.java`

**フィールド**:
- `id` (Long): 主キー
- `name` (String): 選手名
- `email` (String): メールアドレス
- `phoneNumber` (String): 電話番号
- `notes` (TEXT): メモ
- `createdAt` (LocalDateTime): 作成日時
- `updatedAt` (LocalDateTime): 更新日時

**特徴**:
- Lombok を使用してボイラープレートコードを削減
- `@PrePersist` と `@PreUpdate` でタイムスタンプを自動設定

### 4.3 Match エンティティ

**ファイル**: `entity/Match.java`

**フィールド**:
- `id` (Long): 主キー
- `player1` (Player): 選手1への参照
- `player2` (Player): 選手2への参照
- `matchDate` (LocalDate): 試合日
- `location` (String): 場所
- `player1Score` (Integer): 選手1のスコア
- `player2Score` (Integer): 選手2のスコア
- `winner` (Player): 勝者への参照
- `matchType` (String): 試合タイプ（練習試合、公式戦など）
- `notes` (TEXT): メモ
- `createdAt` (LocalDateTime): 作成日時
- `updatedAt` (LocalDateTime): 更新日時

**特徴**:
- スコアに基づいて勝者を自動判定（`@PrePersist`、`@PreUpdate`）
- `@ManyToOne` で Player エンティティと関連付け
- LAZY フェッチで性能を最適化

### 4.4 PlayerRepository

**ファイル**: `repository/PlayerRepository.java`

**主要メソッド**:
- `findByNameContaining(String name)`: 名前で検索
- `findByEmail(String email)`: メールアドレスで検索
- `findAllByOrderByNameAsc()`: 名前順で全選手取得
- `countAllPlayers()`: 選手総数を取得

---

## 5. 実装予定の機能

### 5.1 画面一覧

1. **対戦結果一覧・検索**
   - 全試合の一覧表示
   - 日付、選手名、試合タイプでフィルタリング
   - ページネーション

2. **対戦結果入力**
   - 新規試合の登録
   - 試合情報の編集・削除

3. **個人の結果一覧**
   - 特定選手の全試合履歴
   - 勝率、平均スコアなどの統計

4. **統計情報**
   - 全体の統計（総試合数、選手数など）
   - ランキング表示
   - AI による分析結果（Claude API 使用）

### 5.2 次のステップ

- [ ] MatchRepository の実装
- [ ] Service レイヤーの実装
- [ ] Controller の実装
- [ ] Thymeleaf テンプレートの作成
- [ ] Bootstrap によるスタイリング
- [ ] Claude API 連携サービスの実装

---

## 6. プロジェクトの起動方法

### 6.1 MySQL の起動

```bash
# MySQL サーバーが起動していることを確認
sudo service mysql start
```

### 6.2 アプリケーションのビルドと起動

```bash
cd /home/poponta/match-tracker/karuta-tracker

# ビルド
source "$HOME/.sdkman/bin/sdkman-init.sh"
./gradlew build

# 起動
./gradlew bootRun
```

### 6.3 アクセス

ブラウザで以下にアクセス：
```
http://localhost:8080
```

---

## 7. 開発時の注意点

1. **データベースの初期設定**
   - `application.properties` のパスワードを適切に設定
   - MySQL が起動していることを確認

2. **Lombok の設定**
   - IDE に Lombok プラグインをインストール
   - Annotation Processing を有効化

3. **Git 管理**
   - `.gitignore` に以下を追加することを推奨：
     - `application-local.properties`（個人設定用）
     - Claude API キー

---

## 8. 参考リンク

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Thymeleaf Documentation](https://www.thymeleaf.org/documentation.html)
- [Bootstrap Documentation](https://getbootstrap.com/docs/)
- [Anthropic Claude API](https://docs.anthropic.com/)

---

## 9. プロジェクト構成図

```
match-tracker/
├── README.md
├── SETUP_GUIDE.md (このファイル)
├── CHANGELOG.md
└── karuta-tracker/
    ├── build.gradle
    ├── settings.gradle
    ├── gradlew
    ├── gradlew.bat
    └── src/
        ├── main/
        │   ├── java/com/karuta/matchtracker/
        │   │   ├── MatchTrackerApplication.java
        │   │   ├── entity/
        │   │   │   ├── Player.java
        │   │   │   └── Match.java
        │   │   ├── repository/
        │   │   │   ├── PlayerRepository.java
        │   │   │   └── MatchRepository.java (予定)
        │   │   ├── service/
        │   │   ├── controller/
        │   │   └── dto/
        │   └── resources/
        │       ├── application.properties
        │       ├── templates/
        │       └── static/
        └── test/
```
