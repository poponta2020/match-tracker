# 開発環境セットアップガイド

このドキュメントは、別のPCでKaruta Tracker Match Trackerの開発環境を構築するための手順書です。

## 目次

1. [必要な環境・ツール](#必要な環境ツール)
2. [初期セットアップ手順](#初期セットアップ手順)
3. [開発モードでの起動方法](#開発モードでの起動方法)
4. [Dockerを使った起動方法](#dockerを使った起動方法)
5. [トラブルシューティング](#トラブルシューティング)
6. [プロジェクト構成](#プロジェクト構成)

---

## 必要な環境・ツール

### 必須ソフトウェア

| ソフトウェア | バージョン | 用途 |
|------------|----------|------|
| **Git** | 最新版 | ソースコード管理 |
| **Java JDK** | 21以上 | バックエンド開発・実行 |
| **Node.js** | 18以上（推奨: 20以上） | フロントエンド開発・実行 |
| **npm** | 9以上 | Node.jsパッケージ管理 |
| **Docker Desktop** | 最新版 | コンテナ実行環境 |
| **Docker Compose** | 最新版（Docker Desktopに含まれる） | 複数コンテナの管理 |

### 現在の開発環境（参考）

```
Java: OpenJDK 21.0.8
Node.js: v24.11.0
npm: 11.6.1
Docker: 28.5.1
OS: Windows
```

### 推奨ツール

- **エディタ**: VS Code, IntelliJ IDEA, Cursor など
- **Git GUI**: GitHub Desktop, SourceTree など（お好みで）
- **API テストツール**: Postman, Thunder Client (VS Code拡張) など

---

## 初期セットアップ手順

### 1. ソフトウェアのインストール

#### 1.1 Java JDK 21のインストール

**Windowsの場合:**
1. [Microsoft Build of OpenJDK](https://learn.microsoft.com/ja-jp/java/openjdk/download)または[Oracle JDK](https://www.oracle.com/java/technologies/downloads/)からJDK 21をダウンロード
2. インストーラーを実行
3. 環境変数`JAVA_HOME`を設定（通常は自動設定されます）
4. 確認コマンド:
```bash
java -version
```

**macOS/Linuxの場合:**
```bash
# macOS (Homebrew使用)
brew install openjdk@21

# Ubuntu/Debian
sudo apt install openjdk-21-jdk

# 確認
java -version
```

#### 1.2 Node.js & npmのインストール

1. [Node.js公式サイト](https://nodejs.org/)からLTS版をダウンロード
2. インストーラーを実行（npmも一緒にインストールされます）
3. 確認コマンド:
```bash
node --version
npm --version
```

#### 1.3 Docker Desktopのインストール

1. [Docker Desktop公式サイト](https://www.docker.com/products/docker-desktop/)からダウンロード
2. インストーラーを実行
3. Docker Desktopを起動
4. 確認コマンド:
```bash
docker --version
docker-compose --version
```

### 2. リポジトリのクローン

```bash
# SSHの場合（推奨）
git clone git@github.com:your-username/match-tracker.git

# HTTPSの場合
git clone https://github.com/your-username/match-tracker.git

# プロジェクトディレクトリに移動
cd match-tracker
```

### 3. 環境設定ファイルの作成

#### 3.1 フロントエンド（karuta-tracker-ui）

`karuta-tracker-ui/.env`ファイルを作成:

```env
VITE_API_BASE_URL=http://localhost:8080/api
```

#### 3.2 バックエンド（karuta-tracker）

バックエンドの設定ファイルは既にGitリポジトリに含まれているため、追加の作業は不要です。

**設定ファイルの場所:**
- `karuta-tracker/src/main/resources/application.properties` - デフォルト設定
- `karuta-tracker/src/main/resources/application-dev.yml` - 開発環境用（Docker MySQLを使用）
- `karuta-tracker/src/main/resources/application-docker.yml` - Docker環境用

---

## 開発モードでの起動方法

ローカル環境で開発する場合の手順です。この方法では、バックエンドとフロントエンドを個別に起動し、MySQLはDockerで起動します。

### 1. MySQLコンテナの起動

```bash
# プロジェクトルートディレクトリで実行
docker-compose up -d mysql

# 起動確認（HEALTHYになるまで待つ）
docker ps
```

### 2. バックエンドの起動

```bash
# karuta-trackerディレクトリに移動
cd karuta-tracker

# Gradleを使って起動（devプロファイル使用）
./gradlew bootRun --args='--spring.profiles.active=dev'

# または、プロファイル指定なしで起動（デフォルト設定）
./gradlew bootRun
```

**起動確認:**
- ブラウザで http://localhost:8080/actuator/health にアクセス
- `{"status":"UP"}`と表示されればOK

### 3. フロントエンドの起動

**別のターミナルを開いて:**

```bash
# karuta-tracker-uiディレクトリに移動
cd karuta-tracker-ui

# 初回のみ: 依存パッケージをインストール
npm install

# 開発サーバー起動
npm run dev
```

**起動確認:**
- ブラウザで http://localhost:5173 にアクセス（ポート番号は表示されたものを使用）
- アプリケーションが表示されればOK

---

## Dockerを使った起動方法

バックエンドとフロントエンドをDockerコンテナで起動する方法です。本番環境に近い状態でテストできます。

### すべてのサービスを一括起動

```bash
# プロジェクトルートディレクトリで実行
docker-compose up -d

# ログを確認
docker-compose logs -f

# 起動確認
docker ps
```

起動するサービス:
- **mysql**: MySQLデータベース（ポート3306）
- **app**: Spring Bootバックエンド（ポート8080）

### 個別にサービスを操作

```bash
# MySQLのみ起動
docker-compose up -d mysql

# アプリのみ起動
docker-compose up -d app

# すべて停止
docker-compose down

# ボリュームも削除して完全リセット
docker-compose down -v
```

### フロントエンドの起動（Docker使用時も同じ）

```bash
cd karuta-tracker-ui
npm run dev
```

**注意:** 現在、フロントエンドのDockerコンテナ化は行っていないため、開発サーバーで起動してください。

---

## トラブルシューティング

### よくある問題と解決方法

#### 1. ポートが既に使用されている

**症状:**
```
Port 8080 is already in use
Port 3306 is already in use
```

**解決方法:**

**Windowsの場合:**
```bash
# ポートを使用しているプロセスを確認
netstat -ano | findstr :8080
netstat -ano | findstr :3306

# プロセスIDを確認して強制終了
taskkill /F /PID <プロセスID>
```

**macOS/Linuxの場合:**
```bash
# ポートを使用しているプロセスを確認
lsof -i :8080
lsof -i :3306

# プロセスを終了
kill -9 <PID>
```

#### 2. Dockerコンテナが起動しない

**MySQLコンテナが起動しない場合:**

```bash
# ログを確認
docker-compose logs mysql

# コンテナを完全に削除して再作成
docker-compose down -v
docker-compose up -d mysql
```

**アプリコンテナが起動しない場合:**

```bash
# ログを確認
docker-compose logs app

# イメージを再ビルド
docker-compose build app
docker-compose up -d app
```

#### 3. データベース接続エラー

**症状:**
```
Communications link failure
Unable to connect to database
```

**解決方法:**

1. MySQLコンテナが起動しているか確認:
```bash
docker ps | grep mysql
```

2. MySQLコンテナのヘルスチェック確認:
```bash
docker inspect karuta-tracker-mysql | grep Status
```

3. データベース接続情報を確認:
   - ホスト: `localhost`（ローカル開発）または`mysql`（Docker環境）
   - ポート: `3306`
   - データベース名: `karuta_tracker`
   - ユーザー名: `karuta`
   - パスワード: `karuta123`

4. MySQLに直接接続してテスト:
```bash
docker exec -it karuta-tracker-mysql mysql -ukaruta -pkaruta123 karuta_tracker
```

#### 4. npm install がエラーになる

**症状:**
```
npm ERR! code EACCES
npm ERR! permission denied
```

**解決方法:**

```bash
# node_modules と package-lock.json を削除
rm -rf node_modules package-lock.json

# 再インストール
npm install

# Windowsの場合、管理者権限で実行するか、キャッシュをクリア
npm cache clean --force
npm install
```

#### 5. Gradle ビルドが失敗する

**症状:**
```
Could not resolve dependencies
Build failed
```

**解決方法:**

```bash
# Gradleキャッシュをクリーン
./gradlew clean

# 依存関係を再取得
./gradlew build --refresh-dependencies

# Gradleラッパーを再生成（最終手段）
gradle wrapper
```

#### 6. フロントエンドからバックエンドに接続できない

**症状:**
```
Network Error
Failed to fetch
CORS error
```

**解決方法:**

1. `.env`ファイルの`VITE_API_BASE_URL`を確認
2. バックエンドが起動しているか確認（http://localhost:8080/actuator/health）
3. ブラウザの開発者ツールでネットワークタブを確認
4. フロントエンドを再起動:
```bash
npm run dev
```

---

## プロジェクト構成

```
match-tracker/
├── docker-compose.yml          # Dockerコンテナオーケストレーション
├── DEVELOPMENT_SETUP.md        # このファイル
├── README.md                   # プロジェクト概要
│
├── karuta-tracker/             # バックエンド（Spring Boot）
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/          # Javaソースコード
│   │   │   │   └── com/karuta/matchtracker/
│   │   │   │       ├── controller/     # REST APIコントローラー
│   │   │   │       ├── service/        # ビジネスロジック
│   │   │   │       ├── repository/     # データアクセス層
│   │   │   │       ├── entity/         # エンティティ（DBモデル）
│   │   │   │       ├── dto/            # データ転送オブジェクト
│   │   │   │       └── exception/      # カスタム例外
│   │   │   └── resources/
│   │   │       ├── application.properties           # デフォルト設定
│   │   │       ├── application-dev.yml              # 開発環境設定
│   │   │       └── application-docker.yml           # Docker環境設定
│   │   └── test/              # テストコード
│   ├── build.gradle           # Gradle依存関係定義
│   ├── Dockerfile             # Dockerイメージビルド定義
│   └── gradlew                # Gradleラッパー（Unix）
│   └── gradlew.bat            # Gradleラッパー（Windows）
│
└── karuta-tracker-ui/         # フロントエンド（React + Vite）
    ├── src/
    │   ├── components/        # Reactコンポーネント
    │   ├── pages/             # ページコンポーネント
    │   ├── services/          # API通信サービス
    │   ├── App.jsx            # メインアプリケーション
    │   └── main.jsx           # エントリーポイント
    ├── public/                # 静的ファイル
    ├── package.json           # npm依存関係定義
    ├── vite.config.js         # Vite設定
    ├── .env                   # 環境変数（要作成）
    └── index.html             # HTMLエントリーポイント
```

---

## データベース構成

### 接続情報

#### 開発環境（ローカルからDockerのMySQL）

| 項目 | 値 |
|-----|---|
| ホスト | `localhost` |
| ポート | `3306` |
| データベース名 | `karuta_tracker` |
| ユーザー名（アプリ用） | `karuta` |
| パスワード | `karuta123` |
| rootパスワード | `root` |

#### Docker環境（コンテナ間通信）

| 項目 | 値 |
|-----|---|
| ホスト | `mysql` |
| ポート | `3306` |
| データベース名 | `karuta_tracker` |
| ユーザー名 | `karuta` |
| パスワード | `karuta123` |

### データベース初期化

データベースは初回起動時に自動で作成されます。テーブルはSpring BootのJPA（Hibernate）が自動生成します（`ddl-auto: update`設定）。

データベースを完全にリセットする場合:

```bash
# Dockerボリュームを削除
docker-compose down -v

# 再起動
docker-compose up -d mysql
```

---

## 開発の流れ

### 1. 機能開発の基本フロー

1. **ブランチ作成**
```bash
git checkout develop
git pull
git checkout -b feature/新機能名
```

2. **開発環境起動**
```bash
# MySQL起動
docker-compose up -d mysql

# バックエンド起動（別ターミナル）
cd karuta-tracker
./gradlew bootRun

# フロントエンド起動（別ターミナル）
cd karuta-tracker-ui
npm run dev
```

3. **コーディング**
   - バックエンド: `karuta-tracker/src/main/java/...`
   - フロントエンド: `karuta-tracker-ui/src/...`

4. **テスト**
```bash
# バックエンドテスト
cd karuta-tracker
./gradlew test

# フロントエンド（現在テストは未実装）
cd karuta-tracker-ui
npm run lint
```

5. **コミット & プッシュ**
```bash
git add .
git commit -m "feat: 新機能の説明"
git push origin feature/新機能名
```

### 2. コード規約

- **Java**: Spring Bootの標準規約に従う
- **JavaScript/React**: ESLintの設定に従う（`npm run lint`で確認）
- **コミットメッセージ**:
  - `feat:` 新機能
  - `fix:` バグ修正
  - `docs:` ドキュメント
  - `refactor:` リファクタリング
  - `test:` テスト追加

---

## 便利なコマンド集

### Docker関連

```bash
# すべてのコンテナを起動
docker-compose up -d

# ログをリアルタイム表示
docker-compose logs -f

# 特定サービスのログ表示
docker-compose logs -f mysql
docker-compose logs -f app

# コンテナの状態確認
docker-compose ps

# コンテナに入る
docker exec -it karuta-tracker-mysql bash
docker exec -it karuta-tracker-app bash

# MySQLに直接接続
docker exec -it karuta-tracker-mysql mysql -ukaruta -pkaruta123 karuta_tracker

# すべて停止
docker-compose down

# データも削除して完全リセット
docker-compose down -v
```

### Gradle関連

```bash
# アプリケーション起動
./gradlew bootRun

# テスト実行
./gradlew test

# ビルド（JARファイル作成）
./gradlew build

# クリーン（ビルド成果物削除）
./gradlew clean

# 依存関係の確認
./gradlew dependencies

# タスク一覧表示
./gradlew tasks
```

### npm関連

```bash
# 依存パッケージインストール
npm install

# 開発サーバー起動
npm run dev

# 本番ビルド
npm run build

# ビルド結果をプレビュー
npm run preview

# コード検証
npm run lint
```

---

## さらなる情報

### API ドキュメント

バックエンドが起動している状態で以下にアクセス:
- ヘルスチェック: http://localhost:8080/actuator/health
- 全エンドポイント: http://localhost:8080/actuator

### 技術スタック

**バックエンド:**
- Spring Boot 3.x
- Java 21
- Hibernate (JPA)
- MySQL 8.0
- Gradle

**フロントエンド:**
- React 19
- Vite
- React Router
- Axios
- Tailwind CSS
- Recharts

**インフラ:**
- Docker & Docker Compose

---

## サポート

問題が解決しない場合は、以下を確認してください:

1. このドキュメントの[トラブルシューティング](#トラブルシューティング)セクション
2. Dockerコンテナのログ: `docker-compose logs -f`
3. バックエンドのログ: `karuta-tracker/`ディレクトリでGradleの出力を確認
4. フロントエンドのブラウザコンソール: 開発者ツールで確認

---

**最終更新日**: 2025-12-03
