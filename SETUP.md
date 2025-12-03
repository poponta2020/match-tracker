# Match Tracker 開発環境構築ガイド

このドキュメントは、別のPCでMatch Trackerアプリケーションの開発環境を構築するための手順書です。

## 目次
1. [必要な環境・ツール](#必要な環境ツール)
2. [事前準備](#事前準備)
3. [環境構築手順](#環境構築手順)
4. [初回起動](#初回起動)
5. [トラブルシューティング](#トラブルシューティング)

---

## 必要な環境・ツール

### 必須ソフトウェア

| ソフトウェア | バージョン | 用途 |
|------------|----------|------|
| **Git** | 最新版 | ソースコード管理 |
| **Docker Desktop** | 最新版 | MySQLコンテナ実行環境 |
| **Java (OpenJDK)** | 21以上 | バックエンド実行環境 |
| **Node.js** | 18以上推奨 (現在: 24.11.0) | フロントエンド実行環境 |
| **npm** | 8以上 (現在: 11.6.1) | パッケージ管理 |

### 推奨ツール

| ツール | 用途 |
|-------|------|
| **VSCode** または **Cursor** | コードエディタ |
| **Postman** または **Thunder Client** | API動作確認 |

---

## 事前準備

### 1. Gitのインストール

**Windows:**
```bash
# https://git-scm.com/download/win からインストーラーをダウンロード
# インストール後、ターミナルで確認
git --version
```

**Mac:**
```bash
# Homebrew経由でインストール
brew install git

# 確認
git --version
```

### 2. Docker Desktopのインストール

**Windows/Mac共通:**
1. https://www.docker.com/products/docker-desktop/ からダウンロード
2. インストーラーを実行
3. Docker Desktopを起動し、ログイン（無料アカウント作成が必要）
4. 確認:
```bash
docker --version
docker-compose --version
```

### 3. Java (OpenJDK) 21のインストール

**Windows:**
```bash
# Microsoft Build of OpenJDKを推奨
# https://learn.microsoft.com/ja-jp/java/openjdk/download からダウンロード
# インストール後、確認
java -version
```

**Mac:**
```bash
# Homebrew経由でインストール
brew install openjdk@21

# パスを通す
echo 'export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc

# 確認
java -version
```

**期待される出力:**
```
openjdk version "21.x.x"
```

### 4. Node.jsとnpmのインストール

**Windows/Mac共通:**
```bash
# Node.js公式サイトからLTS版をダウンロード
# https://nodejs.org/

# または、nvmを使用（推奨）
# Windows: https://github.com/coreybutler/nvm-windows
# Mac: brew install nvm

# nvmを使用する場合
nvm install 20
nvm use 20

# 確認
node -v
npm -v
```

---

## 環境構築手順

### ステップ1: リポジトリのクローン

```bash
# GitHubからクローン
git clone https://github.com/poponta2020/match-tracker.git
cd match-tracker

# developブランチに切り替え
git checkout develop
```

### ステップ2: Docker環境のセットアップ

```bash
# Docker Desktopが起動していることを確認してから実行

# MySQLコンテナを起動
docker-compose up -d mysql

# コンテナが正常に起動したか確認
docker ps

# 期待される出力:
# CONTAINER ID   IMAGE       COMMAND                  PORTS                    NAMES
# xxxxxxxxxxxx   mysql:8.0   "docker-entrypoint.s…"   0.0.0.0:3306->3306/tcp   karuta-tracker-mysql
```

**重要:** MySQLコンテナの起動には30秒〜1分程度かかります。以下のコマンドでヘルスチェックを確認してください。

```bash
# ヘルスチェック確認
docker logs karuta-tracker-mysql

# "ready for connections" が表示されればOK
```

### ステップ3: バックエンド（Spring Boot）のセットアップ

```bash
# バックエンドディレクトリに移動
cd karuta-tracker

# Gradleでビルド（初回は依存関係のダウンロードに時間がかかります）
# Windows
.\gradlew build

# Mac/Linux
./gradlew build

# アプリケーションを起動
# Windows
.\gradlew bootRun

# Mac/Linux
./gradlew bootRun
```

**起動確認:**
- ブラウザで http://localhost:8080/api/players にアクセス
- `[]` または選手データのJSON配列が表示されればOK

**注意:** 初回起動時、データベーステーブルが自動作成されます（`spring.jpa.hibernate.ddl-auto=update` 設定により）。

### ステップ4: フロントエンド（React + Vite）のセットアップ

**新しいターミナルウィンドウを開いて実行:**

```bash
# プロジェクトルートから
cd karuta-tracker-ui

# 依存関係をインストール
npm install

# .envファイルの確認（既に存在するはず）
cat .env
# 内容: VITE_API_BASE_URL=http://localhost:8080/api

# 開発サーバーを起動
npm run dev
```

**起動確認:**
- ターミナルに表示されるURL（通常 http://localhost:5173）をブラウザで開く
- ログイン画面が表示されればOK

---

## 初回起動

### 1. 全サービスが起動していることを確認

以下の3つが起動している必要があります:

| サービス | URL | 確認方法 |
|---------|-----|---------|
| **MySQL** | localhost:3306 | `docker ps` でコンテナ確認 |
| **バックエンド** | http://localhost:8080 | `curl http://localhost:8080/api/players` |
| **フロントエンド** | http://localhost:5173 | ブラウザでアクセス |

### 2. 初期データの投入（必要に応じて）

初回起動時はデータベースが空です。以下の手順で初期ユーザーを作成できます:

**方法1: APIを使って直接作成**

```bash
# 管理者ユーザーを作成
curl -X POST http://localhost:8080/api/players \
  -H "Content-Type: application/json" \
  -d '{
    "name": "管理者",
    "password": "admin123",
    "role": "SUPER_ADMIN",
    "gender": "OTHER"
  }'
```

**方法2: フロントエンドから作成**

1. http://localhost:5173 にアクセス
2. 「新規登録」をクリック
3. 必要事項を入力してユーザー作成
4. 初回作成ユーザーは管理者権限が必要な場合、データベースを直接編集

### 3. データベースへの直接アクセス（必要な場合）

```bash
# MySQLコンテナに接続
docker exec -it karuta-tracker-mysql mysql -uroot -proot karuta_tracker

# MySQL内で確認
SHOW TABLES;
SELECT * FROM players;
exit;
```

---

## 開発時の起動手順（2回目以降）

### 1. Docker（MySQL）の起動
```bash
# プロジェクトルートで実行
docker-compose up -d mysql

# または、Docker Desktopアプリからkaruta-tracker-mysqlコンテナを起動
```

### 2. バックエンドの起動
```bash
cd karuta-tracker
./gradlew bootRun  # Windowsは .\gradlew bootRun
```

### 3. フロントエンドの起動
```bash
cd karuta-tracker-ui
npm run dev
```

---

## トラブルシューティング

### 問題1: Dockerコンテナが起動しない

**症状:** `docker-compose up -d mysql` でエラー

**原因と対処:**
- Docker Desktopが起動していない → Docker Desktopを起動
- ポート3306が使用中 → 既存のMySQLを停止するか、docker-compose.ymlのポート番号を変更（例: "3307:3306"）

**ポート使用状況の確認:**
```bash
# Windows
netstat -ano | findstr :3306

# Mac/Linux
lsof -i :3306
```

### 問題2: バックエンドが起動しない

**症状1:** `Could not connect to database`

**対処:**
```bash
# MySQLコンテナの状態を確認
docker ps
docker logs karuta-tracker-mysql

# コンテナが起動していない場合は再起動
docker-compose restart mysql
```

**症状2:** `Port 8080 is already in use`

**対処:**
```bash
# Windows: ポート8080を使用しているプロセスを確認
netstat -ano | findstr :8080
# 表示されたPIDのプロセスを終了
taskkill /PID <PID> /F

# Mac/Linux
lsof -ti :8080 | xargs kill -9
```

**症状3:** `Java version mismatch`

**対処:**
```bash
# Javaバージョンを確認
java -version

# Java 21以上でない場合は、Java 21をインストールし、JAVA_HOMEを設定
```

### 問題3: フロントエンドが起動しない

**症状1:** `npm install` でエラー

**対処:**
```bash
# node_modulesとpackage-lock.jsonを削除して再インストール
rm -rf node_modules package-lock.json
npm install
```

**症状2:** `Cannot connect to backend`

**対処:**
- バックエンドが起動しているか確認: `curl http://localhost:8080/api/players`
- `.env` ファイルの確認: `VITE_API_BASE_URL=http://localhost:8080/api` が設定されているか

**症状3:** `Port 5173 is already in use`

**対処:**
```bash
# 別のポートで起動
npm run dev -- --port 5174
```

### 問題4: データベーステーブルが作成されない

**対処:**
```bash
# application.propertiesの設定を確認
# karuta-tracker/src/main/resources/application.properties

# 以下の設定があるか確認
# spring.jpa.hibernate.ddl-auto=update

# バックエンドを再起動
```

### 問題5: Gitクローン時に認証エラー

**対処:**
```bash
# SSHキーを設定していない場合は、HTTPSでクローン
git clone https://github.com/poponta2020/match-tracker.git

# または、GitHubでPersonal Access Token (PAT)を作成して使用
```

---

## 設定ファイルの確認リスト

環境構築後、以下のファイルが正しく配置されているか確認してください:

- ✅ `docker-compose.yml` - Dockerコンテナ設定
- ✅ `karuta-tracker/src/main/resources/application.properties` - バックエンド設定
- ✅ `karuta-tracker-ui/.env` - フロントエンド環境変数
- ✅ `karuta-tracker/build.gradle` - Gradle設定
- ✅ `karuta-tracker-ui/package.json` - npm依存関係

---

## 環境変数とデータベース接続情報

### MySQLコンテナ設定（docker-compose.yml）

```yaml
MYSQL_ROOT_PASSWORD: root
MYSQL_DATABASE: karuta_tracker
MYSQL_USER: karuta
MYSQL_PASSWORD: karuta123
```

### バックエンド接続情報（application.properties）

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/karuta_tracker?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=Asia/Tokyo
spring.datasource.username=root
spring.datasource.password=root
```

### フロントエンド設定（.env）

```
VITE_API_BASE_URL=http://localhost:8080/api
```

---

## よくある質問

### Q1: WSL2を使用している場合の注意点は？

**A:** Docker DesktopでWSL2統合を有効にしてください。設定 > Resources > WSL Integration で使用するディストリビューションを有効化します。

### Q2: M1/M2 Mac使用時の注意点は？

**A:** Docker DesktopがApple Siliconに対応しているため、特別な設定は不要です。ただし、初回のイメージダウンロードに時間がかかる場合があります。

### Q3: 既存のMySQLと競合する場合は？

**A:** docker-compose.ymlのポート設定を変更します:
```yaml
ports:
  - "3307:3306"  # ホスト側を3307に変更
```
同時に、application.propertiesも更新:
```properties
spring.datasource.url=jdbc:mysql://localhost:3307/karuta_tracker...
```

### Q4: プロダクション環境へのデプロイは？

**A:** 現在の設定は開発環境用です。本番環境では以下を変更してください:
- データベースパスワードの変更
- `spring.jpa.hibernate.ddl-auto=validate` に変更（自動テーブル作成を無効化）
- HTTPS対応
- CORS設定の見直し

---

## サポート

問題が解決しない場合は、以下の情報を含めてIssueを作成してください:
- OS種別とバージョン
- 各ツールのバージョン（`java -version`, `node -v`, `docker --version`）
- エラーメッセージの全文
- 実行したコマンド履歴

GitHub Issues: https://github.com/poponta2020/match-tracker/issues

---

## 開発環境の停止

作業終了時は以下のコマンドで環境を停止できます:

```bash
# フロントエンド: Ctrl+C で停止

# バックエンド: Ctrl+C で停止

# Docker（MySQL）を停止
docker-compose down

# データを保持したまま停止（推奨）
docker-compose stop

# データを含めて完全削除（注意！）
docker-compose down -v
```

---

**最終更新日:** 2025-12-03
**対象バージョン:** Match Tracker v0.0.1
