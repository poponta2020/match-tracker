# データベース環境構築ガイド（AI向け詳細版）

このドキュメントは、他の環境でMatch Trackerアプリケーションの開発環境を構築するための完全なガイドです。
生成AIが読み取り、自動的に環境を構築できるよう、すべての設定値と手順を明記しています。

---

## 📋 目次

1. [環境概要](#環境概要)
2. [必須ソフトウェアとバージョン](#必須ソフトウェアとバージョン)
3. [データベース設定詳細](#データベース設定詳細)
4. [環境構築手順](#環境構築手順)
5. [接続設定ファイル](#接続設定ファイル)
6. [トラブルシューティング](#トラブルシューティング)
7. [検証手順](#検証手順)

---

## 環境概要

### アーキテクチャ
```
[フロントエンド: React + Vite]
        ↓ HTTP (localhost:5173)
[バックエンド: Spring Boot]
        ↓ JDBC (localhost:3306)
[データベース: MySQL 8.0 (Docker)]
```

### ポート使用状況
| サービス | ポート | プロトコル | 備考 |
|---------|--------|-----------|------|
| フロントエンド | 5173 | HTTP | Vite開発サーバー |
| バックエンド | 8080 | HTTP | Spring Boot |
| MySQL | 3306 | TCP | Dockerコンテナ |

---

## 必須ソフトウェアとバージョン

### 1. Java Development Kit (JDK)
```yaml
名前: OpenJDK
バージョン: 21.0.8 LTS
ビルド: Microsoft-11933218
ダウンロード: https://learn.microsoft.com/ja-jp/java/openjdk/download
確認コマンド: java -version
期待される出力: "openjdk version \"21.0.8\""
```

### 2. Node.js & npm
```yaml
Node.js バージョン: v24.11.0
npm バージョン: 11.6.1
ダウンロード: https://nodejs.org/
確認コマンド:
  - node --version
  - npm --version
期待される出力:
  - "v24.11.0" (または v20以上)
  - "11.6.1" (または 10以上)
```

### 3. Docker Desktop
```yaml
バージョン: 28.5.1 (またはそれ以上)
ビルド: e180ab8
ダウンロード: https://www.docker.com/products/docker-desktop
OS要件:
  - Windows: Windows 10/11 Pro, Enterprise, Education (Hyper-V対応)
  - macOS: macOS 10.15以降
  - Linux: カーネル 3.10以降
確認コマンド: docker --version
期待される出力: "Docker version 28.5.1"
重要: Docker Desktopを起動してから作業を開始すること
```

### 4. Git
```yaml
バージョン: 2.x以上
ダウンロード: https://git-scm.com/
確認コマンド: git --version
```

---

## データベース設定詳細

### MySQL Docker コンテナ設定

#### コンテナ基本情報
```yaml
コンテナ名: karuta-tracker-mysql
イメージ: mysql:8.0
イメージID: sha256:f37951fc3753a6a22d6c7bf6978c5e5fefcf6f31814d98c582524f98eae52b21
ベースイメージ: oraclelinux:9-slim
作成日: 2025-10-22
バージョン: 8.0.44
```

#### 環境変数（重要）
```yaml
MYSQL_ROOT_PASSWORD: root
MYSQL_DATABASE: karuta_tracker
MYSQL_USER: karuta
MYSQL_PASSWORD: karuta123
TZ: Asia/Tokyo
```

#### ポートマッピング
```yaml
ホスト側: 3306
コンテナ側: 3306
バインドアドレス: 0.0.0.0 (全インターフェース)
IPv6対応: あり
```

#### 文字コード設定
```yaml
character-set-server: utf8mb4
collation-server: utf8mb4_unicode_ci
default-authentication-plugin: mysql_native_password
```

#### ボリュームマウント
```yaml
データ永続化:
  - タイプ: Docker Volume
  - 名前: match-tracker_mysql_data
  - マウント先: /var/lib/mysql
  - 用途: データベースファイルの永続化

初期化スクリプト:
  - タイプ: Bind Mount
  - ソース: ./init-scripts
  - マウント先: /docker-entrypoint-initdb.d
  - 用途: 初回起動時のSQL実行
  - 注意: 現在は空ディレクトリ（初期化スクリプトなし）
```

#### ヘルスチェック設定
```yaml
コマンド: mysqladmin ping -h localhost -u root -p$MYSQL_ROOT_PASSWORD
インターバル: 10秒
タイムアウト: 5秒
リトライ回数: 5回
ステータス確認: docker ps で "healthy" 表示を確認
```

#### ネットワーク設定
```yaml
ネットワーク名: match-tracker_karuta-network
ドライバ: bridge
IPアドレス: 172.18.0.2/16
ゲートウェイ: 172.18.0.1
エイリアス:
  - karuta-tracker-mysql
  - mysql
DNSによる名前解決: 可能
```

---

## 環境構築手順

### ステップ1: リポジトリのクローン

```bash
# リポジトリをクローン
git clone https://github.com/poponta2020/match-tracker.git
cd match-tracker

# developブランチに切り替え（最新の開発版）
git checkout develop
```

### ステップ2: Docker環境のセットアップ

#### 2.1 Docker Desktopの起動確認
```bash
# Dockerが起動しているか確認
docker ps

# エラーが出る場合は、Docker Desktopを手動で起動
# Windows: スタートメニューから "Docker Desktop" を起動
# macOS: アプリケーションフォルダから起動
# 起動完了まで1-2分待機
```

#### 2.2 MySQLコンテナの起動
```bash
# プロジェクトルートで実行
docker-compose up -d mysql

# 起動確認（healthyになるまで待つ）
docker ps

# 期待される出力例:
# CONTAINER ID   IMAGE       STATUS                    NAMES
# xxxxxxxxxx     mysql:8.0   Up X seconds (healthy)   karuta-tracker-mysql
```

#### 2.3 コンテナログの確認（オプション）
```bash
# MySQLの起動ログを確認
docker logs karuta-tracker-mysql

# 以下のメッセージが表示されればOK:
# "mysqld: ready for connections"
```

### ステップ3: バックエンドのセットアップ

#### 3.1 データベース接続設定の確認
ファイル: `karuta-tracker/src/main/resources/application.properties`

```properties
# この設定が正しいことを確認
spring.datasource.url=jdbc:mysql://localhost:3306/karuta_tracker?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=Asia/Tokyo
spring.datasource.username=root
spring.datasource.password=root
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# データベース自動作成設定
spring.jpa.hibernate.ddl-auto=update
```

**重要ポイント:**
- `username=root` と `password=root` はMySQLの**rootユーザー**を使用
- `karuta`ユーザーは使用しない（rootで統一）
- `createDatabaseIfNotExist=true` により初回起動時に自動でDBが作成される

#### 3.2 Gradleビルドとアプリケーション起動
```bash
# karuta-trackerディレクトリに移動
cd karuta-tracker

# 初回ビルド（依存関係ダウンロード）
./gradlew build

# アプリケーション起動
./gradlew bootRun

# 起動成功のログを確認:
# "Started MatchTrackerApplication in X.XXX seconds"
```

**起動確認:**
```bash
# 別ターミナルで確認
curl http://localhost:8080/api/players

# または
# ブラウザで http://localhost:8080/api/players にアクセス
# 空の配列 [] が返ればOK
```

### ステップ4: フロントエンドのセットアップ

#### 4.1 依存関係のインストール
```bash
# プロジェクトルートに戻る
cd ..

# karuta-tracker-uiディレクトリに移動
cd karuta-tracker-ui

# npm依存関係をインストール
npm install

# インストール完了後、以下のパッケージが含まれていることを確認:
# - react: ^19.1.1
# - react-dom: ^19.1.1
# - react-router-dom: ^7.9.5
# - axios: ^1.13.2
# - vite: ^6.3.1
```

#### 4.2 開発サーバーの起動
```bash
# 開発サーバー起動
npm run dev

# 起動成功のログ:
# "VITE vX.X.X  ready in XXX ms"
# "➜  Local:   http://localhost:5173/"
```

#### 4.3 アクセス確認
ブラウザで http://localhost:5173 を開く

期待される画面:
- ログイン画面が表示される
- エラーメッセージが表示されない

---

## 接続設定ファイル

### 1. docker-compose.yml（プロジェクトルート）

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    container_name: karuta-tracker-mysql
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: karuta_tracker
      MYSQL_USER: karuta
      MYSQL_PASSWORD: karuta123
      TZ: Asia/Tokyo
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql
      - ./init-scripts:/docker-entrypoint-initdb.d
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_unicode_ci
      - --default-authentication-plugin=mysql_native_password
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-u", "root", "-p$$MYSQL_ROOT_PASSWORD"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - karuta-network

volumes:
  mysql_data:
    driver: local

networks:
  karuta-network:
    driver: bridge
```

### 2. application.properties（バックエンド設定）

場所: `karuta-tracker/src/main/resources/application.properties`

```properties
spring.application.name=match-tracker

# MySQL Database Configuration
spring.datasource.url=jdbc:mysql://localhost:3306/karuta_tracker?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=Asia/Tokyo
spring.datasource.username=root
spring.datasource.password=root
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# JPA/Hibernate Configuration
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect

# Server Configuration
server.port=8080

# Logging Configuration
logging.level.com.karuta.matchtracker=DEBUG
logging.level.org.hibernate.SQL=DEBUG
```

### 3. vite.config.js（フロントエンド設定）

場所: `karuta-tracker-ui/vite.config.js`

```javascript
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
```

---

## トラブルシューティング

### 問題1: Dockerコンテナが起動しない

#### エラー: "Cannot connect to the Docker daemon"
**原因:** Docker Desktopが起動していない

**解決方法:**
1. Docker Desktopアプリケーションを手動で起動
2. システムトレイ/メニューバーにDockerアイコンが表示されるまで待つ
3. `docker ps` コマンドで確認

#### エラー: "port 3306 is already in use"
**原因:** 既にポート3306を使用しているプロセスが存在

**解決方法（Windows）:**
```powershell
# ポート使用状況を確認
netstat -ano | findstr :3306

# プロセスIDを確認し、必要に応じて停止
# 例: PID 12345 の場合
taskkill /PID 12345 /F

# または、既存のMySQLサービスを停止
net stop MySQL
```

**解決方法（macOS/Linux）:**
```bash
# ポート使用状況を確認
lsof -i :3306

# プロセスを停止
kill -9 <PID>
```

### 問題2: バックエンドがデータベースに接続できない

#### エラー: "Communications link failure"
**原因:** MySQLコンテナが起動していない、またはhealthyになっていない

**解決方法:**
```bash
# コンテナ状態を確認
docker ps -a

# healthyでない場合は再起動
docker-compose restart mysql

# ログを確認
docker logs karuta-tracker-mysql
```

#### エラー: "Access denied for user 'root'@'localhost'"
**原因:** パスワードが間違っている

**解決方法:**
1. `application.properties` を確認
   - `spring.datasource.username=root`
   - `spring.datasource.password=root`
2. docker-compose.ymlと一致しているか確認
   - `MYSQL_ROOT_PASSWORD: root`

### 問題3: フロントエンドが起動しない

#### エラー: "EADDRINUSE: address already in use :::5173"
**原因:** ポート5173が既に使用中

**解決方法:**
```bash
# 既存のプロセスを確認・停止
# Windows
netstat -ano | findstr :5173
taskkill /PID <PID> /F

# macOS/Linux
lsof -i :5173
kill -9 <PID>
```

#### エラー: npm install時のパッケージエラー
**原因:** Node.jsバージョンが古い、またはnpmキャッシュの問題

**解決方法:**
```bash
# Node.jsバージョン確認（20.x以上必要）
node --version

# npmキャッシュをクリア
npm cache clean --force

# 再インストール
rm -rf node_modules package-lock.json
npm install
```

### 問題4: データベースが初期化されない

#### 症状: テーブルが作成されない

**原因:** `ddl-auto=update` は既存のテーブルを変更しない

**解決方法（データリセットが必要な場合）:**
```bash
# コンテナとボリュームを完全削除
docker-compose down -v

# 再度起動（新しいボリュームで起動）
docker-compose up -d mysql

# バックエンドを起動すると自動でテーブルが作成される
cd karuta-tracker
./gradlew bootRun
```

---

## 検証手順

### 1. データベース接続の検証

```bash
# MySQLコンテナに接続
docker exec -it karuta-tracker-mysql mysql -uroot -proot karuta_tracker

# SQLクエリで確認
SHOW DATABASES;
USE karuta_tracker;
SHOW TABLES;
EXIT;
```

期待される結果:
- `karuta_tracker` データベースが存在
- 起動後はテーブルが自動作成される

### 2. バックエンドAPIの検証

```bash
# 選手一覧API
curl http://localhost:8080/api/players

# 練習セッション一覧API
curl http://localhost:8080/api/practice-sessions

# ヘルスチェック
curl http://localhost:8080/actuator/health
```

### 3. フロントエンドの検証

1. ブラウザで http://localhost:5173 を開く
2. ログイン画面が表示されることを確認
3. F12でデベロッパーツールを開き、コンソールエラーがないことを確認
4. Networkタブで `/api/` へのリクエストが正常に送信されていることを確認

### 4. 全体動作の検証

#### 初回ログイン（テストユーザー作成）
デフォルトではユーザーが存在しないため、直接DBに作成するか、アプリケーション内で新規登録機能を使用

**データベースから直接ユーザーを作成:**
```sql
-- MySQLコンテナに接続
docker exec -it karuta-tracker-mysql mysql -uroot -proot karuta_tracker

-- テストユーザー挿入（パスワード: "password"）
INSERT INTO players (name, password, gender, dominant_hand, role, created_at, updated_at)
VALUES ('土居悠太', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '男性', '右', 'SUPER_ADMIN', NOW(), NOW());
```

その後、以下でログイン:
- ユーザー名: `土居悠太`
- パスワード: `password`

---

## データベーススキーマ情報

### テーブル構造（自動生成）

Spring Bootの `ddl-auto=update` により自動生成されるテーブル:

#### players（選手）
| 列名 | 型 | NULL | キー | 備考 |
|-----|---|------|-----|------|
| id | BIGINT | NO | PRI | AUTO_INCREMENT |
| name | VARCHAR(255) | NO | UNI | ログイン名 |
| password | VARCHAR(255) | NO | | BCryptハッシュ |
| gender | ENUM | YES | | 男性/女性/その他 |
| dominant_hand | ENUM | YES | | 右/左/両 |
| dan_rank | ENUM | YES | | 段位 |
| kyu_rank | ENUM | YES | | 級位 |
| karuta_club | VARCHAR(255) | YES | | 所属かるた会 |
| remarks | TEXT | YES | | 備考 |
| role | ENUM | NO | | SUPER_ADMIN/ADMIN/PLAYER |
| deleted_at | DATETIME | YES | | 論理削除 |
| created_at | DATETIME | NO | | |
| updated_at | DATETIME | NO | | |

#### matches（試合結果）
| 列名 | 型 | NULL | キー | 備考 |
|-----|---|------|-----|------|
| id | BIGINT | NO | PRI | AUTO_INCREMENT |
| match_date | DATE | NO | | 試合日 |
| match_number | INT | NO | | 試合番号 |
| player1_id | BIGINT | NO | FK | 選手1 |
| player2_id | BIGINT | NO | FK | 選手2 |
| winner_id | BIGINT | NO | FK | 勝者 |
| score_difference | INT | NO | | 枚数差 |
| opponent_name | VARCHAR(255) | YES | | 未登録選手用 |
| notes | TEXT | YES | | コメント |
| created_by | BIGINT | YES | FK | 作成者 |
| updated_by | BIGINT | YES | FK | 更新者 |
| created_at | DATETIME | NO | | |
| updated_at | DATETIME | NO | | |

#### practice_sessions（練習セッション）
| 列名 | 型 | NULL | キー | 備考 |
|-----|---|------|-----|------|
| id | BIGINT | NO | PRI | AUTO_INCREMENT |
| session_date | DATE | NO | UNI | 練習日 |
| total_matches | INT | NO | | 総試合数 |
| venue_id | BIGINT | YES | FK | 会場 |
| notes | TEXT | YES | | 備考 |
| created_by | BIGINT | YES | FK | 作成者 |
| updated_by | BIGINT | YES | FK | 更新者 |
| created_at | DATETIME | NO | | |
| updated_at | DATETIME | NO | | |

その他のテーブル:
- `match_pairings`: 対戦組み合わせ
- `practice_participants`: 練習参加者
- `venues`: 会場マスタ
- `venue_schedules`: 会場時間割

---

## まとめチェックリスト

環境構築が完了したら、以下をチェック:

- [ ] Docker Desktop が起動している
- [ ] `docker ps` で karuta-tracker-mysql が healthy
- [ ] `curl http://localhost:3306` でMySQLに接続できる（エラーでもOK、応答があればOK）
- [ ] `curl http://localhost:8080/api/players` で空配列 `[]` が返る
- [ ] ブラウザで http://localhost:5173 にアクセスできる
- [ ] ログイン画面が表示される
- [ ] コンソールにエラーがない

すべてチェックできたら、環境構築完了です！

---

## サポート情報

### 関連ドキュメント
- [DEVELOPMENT_SETUP.md](./DEVELOPMENT_SETUP.md) - 全般的な開発環境セットアップ
- [claude.md](./claude.md) - 変数・メソッド名管理ルール
- [README.md](./README.md) - プロジェクト概要

### トラブル時の連絡先
GitHubリポジトリのIssuesに報告してください:
https://github.com/poponta2020/match-tracker/issues

---

**最終更新日:** 2025-12-24
**作成者:** Claude Sonnet 4.5
**対象バージョン:** develop ブランチ
