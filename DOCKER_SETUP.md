# Docker環境セットアップガイド

## 前提条件

- Docker Desktop for Windows がインストールされていること
- WSL2が有効化されていること（推奨）

### Docker Desktopのインストール

1. [Docker Desktop for Windows](https://www.docker.com/products/docker-desktop/) をダウンロード
2. インストーラーを実行
3. 再起動後、Docker Desktopを起動

## 使用方法

### パターン1: MySQLのみDocker起動（推奨 - 開発時）

IDEでアプリケーションをデバッグしながら、MySQLだけDockerで起動する方法です。

```bash
# MySQLコンテナを起動
docker-compose -f docker-compose-dev.yml up -d

# MySQLが起動したか確認
docker-compose -f docker-compose-dev.yml ps

# IntelliJ IDEAやVS Codeでアプリケーションを起動
# プロファイル: dev
# または、Gradleで起動
cd karuta-tracker
./gradlew bootRun --args='--spring.profiles.active=dev'

# 終了時: MySQLコンテナを停止
docker-compose -f docker-compose-dev.yml down
```

接続情報:
- URL: `jdbc:mysql://localhost:3306/karuta_tracker`
- Username: `karuta`
- Password: `karuta123`

### パターン2: アプリケーション全体をDocker起動

アプリケーションとMySQLの両方をDockerで起動する方法です。

```bash
# 全コンテナを起動（初回はビルドに時間がかかります）
docker-compose up -d --build

# ログを確認
docker-compose logs -f app

# コンテナの状態確認
docker-compose ps

# 終了時: 全コンテナを停止
docker-compose down

# データも削除する場合
docker-compose down -v
```

アプリケーションへのアクセス:
- URL: http://localhost:8080
- Health Check: http://localhost:8080/actuator/health

### パターン3: 統合テストの実行

Testcontainersが自動的にMySQLコンテナを起動してテストを実行します。

```bash
cd karuta-tracker

# 統合テストのみ実行
./gradlew test --tests "*IntegrationTest"

# 全テスト実行
./gradlew test
```

## コマンド一覧

### Docker Compose

```bash
# コンテナ起動
docker-compose up -d

# コンテナ停止
docker-compose stop

# コンテナ削除
docker-compose down

# ボリューム含めて削除
docker-compose down -v

# ログ確認
docker-compose logs -f [service-name]

# コンテナの状態確認
docker-compose ps

# コンテナに入る
docker-compose exec mysql bash
docker-compose exec app sh
```

### MySQL操作

```bash
# MySQLコンテナに接続
docker-compose exec mysql mysql -u karuta -pkaruta123 karuta_tracker

# データベース一覧
SHOW DATABASES;

# テーブル一覧
USE karuta_tracker;
SHOW TABLES;

# データ確認
SELECT * FROM players;
```

## トラブルシューティング

### ポート3306が既に使用されている

```bash
# 既存のMySQLを停止
net stop MySQL80

# または、docker-compose.ymlのポートを変更
ports:
  - "3307:3306"  # ホスト側のポートを3307に変更
```

### コンテナが起動しない

```bash
# ログを確認
docker-compose logs

# コンテナを再作成
docker-compose down
docker-compose up -d --force-recreate

# Dockerをリセット
docker system prune -a
```

### ビルドエラーが発生する

```bash
# Gradleキャッシュをクリア
cd karuta-tracker
./gradlew clean

# Dockerイメージを再ビルド
docker-compose build --no-cache
```

### データベース接続エラー

```bash
# MySQLが起動しているか確認
docker-compose ps

# MySQLのヘルスチェック
docker-compose exec mysql mysqladmin ping -h localhost -u root -proot

# 接続テスト
docker-compose exec mysql mysql -u karuta -pkaruta123 -e "SELECT 1"
```

## ディレクトリ構造

```
match-tracker/
├── docker-compose.yml          # 本番相当の環境（アプリ+MySQL）
├── docker-compose-dev.yml      # 開発環境（MySQLのみ）
├── DOCKER_SETUP.md            # このファイル
└── karuta-tracker/
    ├── Dockerfile              # アプリケーションのDockerfile
    ├── .dockerignore           # Docker除外ファイル
    └── src/main/resources/
        ├── application.yml            # デフォルト設定
        ├── application-dev.yml        # 開発環境設定
        ├── application-docker.yml     # Docker環境設定
        └── application-test.properties # テスト環境設定
```

## 開発フロー推奨

1. **初回セットアップ**
   ```bash
   docker-compose -f docker-compose-dev.yml up -d
   ```

2. **日常の開発**
   - IDEでアプリケーションを起動（プロファイル: dev）
   - コード変更 → 自動リロード
   - MySQLはバックグラウンドで稼働

3. **統合テスト実行**
   ```bash
   ./gradlew test --tests "*IntegrationTest"
   ```

4. **本番相当環境での確認**
   ```bash
   docker-compose up -d --build
   ```

5. **終了時**
   ```bash
   docker-compose -f docker-compose-dev.yml down
   ```

## 次のステップ

- [ ] Docker Desktopをインストール
- [ ] `docker-compose -f docker-compose-dev.yml up -d` でMySQLを起動
- [ ] IntelliJ IDEAで `application-dev.yml` を使用してアプリケーションを起動
- [ ] ブラウザで http://localhost:8080/actuator/health にアクセスして確認
- [ ] 統合テストを実行: `./gradlew test --tests "*IntegrationTest"`
