# アプリ起動ガイド

## 構成概要

```
karuta-tracker/       ... Spring Boot バックエンド (ポート 8080)
karuta-tracker-ui/    ... React フロントエンド (ポート 5173)
MySQL                 ... Docker コンテナで起動 (ポート 3306)
```

---

## 起動方法（開発環境）

### ステップ 1: MySQL を Docker で起動

```bash
cd /home/poponta/match-tracker

# MySQL だけを起動（開発用 compose）
docker compose -f docker-compose-dev.yml up -d

# 起動確認（Healthy になるまで待つ）
docker compose -f docker-compose-dev.yml ps
```

> `karuta-tracker-mysql-dev` コンテナの Status が `healthy` になればOK。
> 初回は30秒〜1分ほどかかる場合があります。

---

### ステップ 2: バックエンド（Spring Boot）を起動

```bash
cd /home/poponta/match-tracker/karuta-tracker

./gradlew bootRun
```

起動確認：`http://localhost:8080/actuator/health` にアクセスして `{"status":"UP"}` が返ればOK。

> **DB接続情報（application.properties）**
> - URL: `jdbc:mysql://localhost:3306/karuta_tracker`
> - ユーザー: `root`
> - パスワード: `root`

---

### ステップ 3: フロントエンド（React）を起動

```bash
cd /home/poponta/match-tracker/karuta-tracker-ui

# 初回のみ: 依存パッケージのインストール
npm install

# 開発サーバー起動
npm run dev
```

起動後、ターミナルに表示される URL（通常 `http://localhost:5173`）をブラウザで開く。

---

## アプリへのアクセス

| サービス | URL |
|----------|-----|
| フロントエンド | http://localhost:5173 |
| バックエンド API | http://localhost:8080/api |

---

## 初回セットアップ：最初のユーザー作成

アプリを開くとログイン画面が表示されます。
初回は「新規登録」からアカウントを作成してください。

**管理者（ADMIN/SUPER_ADMIN）の設定**

登録直後のユーザーはロールが `PLAYER` です。
管理者機能（組み合わせ生成・一括入力）を使うには、MySQL で直接ロールを変更する必要があります。

```bash
# MySQL に接続
docker exec -it karuta-tracker-mysql-dev mysql -u root -proot karuta_tracker

# ロールを SUPER_ADMIN に変更（例: 名前が "admin" のユーザー）
UPDATE players SET role = 'SUPER_ADMIN' WHERE name = 'admin';

# 確認
SELECT id, name, role FROM players;

# 終了
exit
```

---

## 停止方法

```bash
# フロントエンド: Ctrl+C で停止

# バックエンド: Ctrl+C で停止

# MySQL コンテナの停止
cd /home/poponta/match-tracker
docker compose -f docker-compose-dev.yml down

# データも含めて完全削除する場合（注意: DBデータが消えます）
docker compose -f docker-compose-dev.yml down -v
```

---

## トラブルシューティング

### MySQL に接続できない

```bash
# コンテナの状態確認
docker compose -f docker-compose-dev.yml ps

# ログ確認
docker compose -f docker-compose-dev.yml logs mysql
```

### バックエンドが起動しない

```bash
# Java バージョン確認（17以上が必要）
java -version

# ビルドのみ実行してエラーを確認
cd /home/poponta/match-tracker/karuta-tracker
./gradlew build -x test
```

### フロントエンドが起動しない

```bash
# Node バージョン確認
node -v

# node_modules を再インストール
cd /home/poponta/match-tracker/karuta-tracker-ui
rm -rf node_modules
npm install
```

### ポートが既に使用されている

```bash
# 使用中のポートを確認
ss -tlnp | grep 8080
ss -tlnp | grep 5173
ss -tlnp | grep 3306
```

---

## 全サービスをまとめて起動するコマンド（コピペ用）

ターミナルを3つ開いて、それぞれで以下を実行:

**ターミナル 1: MySQL**
```bash
cd /home/poponta/match-tracker && docker compose -f docker-compose-dev.yml up
```

**ターミナル 2: バックエンド**
```bash
cd /home/poponta/match-tracker/karuta-tracker && ./gradlew bootRun
```

**ターミナル 3: フロントエンド**
```bash
cd /home/poponta/match-tracker/karuta-tracker-ui && npm run dev
```
