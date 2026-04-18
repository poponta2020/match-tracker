---
name: startapp
description: フロントエンド（Vite）とバックエンド（Spring Boot + Render PostgreSQL）を起動するスキル。開発サーバーを立ち上げたいときに使用する。
disable-model-invocation: true
user-invocable: true
allowed-tools: Bash, Read
---

# /startapp - アプリケーション起動

フロントエンド（Vite）とバックエンド（Spring Boot + Render PostgreSQL）を起動します。

## 手順

### 1. 現在の起動状態を確認

以下の2つを並列で確認する：

- **バックエンド**: `curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/players` を実行し、200が返るか確認
- **フロントエンド**: `curl -s -o /dev/null -w "%{http_code}" http://localhost:5173` を実行し、200が返るか確認

### 2. 起動していなければ起動

#### バックエンド（Spring Boot）
- 作業ディレクトリ: `c:/Users/popon/match-tracker/karuta-tracker`
- **重要**: RenderのPostgreSQLに接続するため、以下の環境変数を必ず設定して起動すること
- 起動コマンド:
```bash
cd c:/Users/popon/match-tracker/karuta-tracker && DB_URL="jdbc:postgresql://dpg-d6t1e77kijhs73er5ug0-a.oregon-postgres.render.com:5432/karuta_tracker_b297" DB_USERNAME="karuta" DB_PASSWORD="9wvobIcnZknsLP5owc9bQDKOWHmiekNE" ./gradlew bootRun 2>&1
```
- `run_in_background: true` で起動する
- 起動完了まで約30〜60秒かかる。30秒後に `curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/players` で確認し、200でなければさらに15秒ごとにリトライ（最大3回）

#### フロントエンド（Vite）
- 作業ディレクトリ: `c:/Users/popon/match-tracker/karuta-tracker-ui`
- 起動コマンド:
```bash
cd c:/Users/popon/match-tracker/karuta-tracker-ui && npm run dev 2>&1
```
- `run_in_background: true` で起動する
- 5秒後に `curl -s -o /dev/null -w "%{http_code}" http://localhost:5173` で確認

### 3. 結果を報告

以下の形式でユーザーに報告する：

```
起動状態:
- フロントエンド: ✅ 起動済み (http://localhost:5173) / ❌ 起動失敗
- バックエンド: ✅ 起動済み (http://localhost:8080) / ❌ 起動失敗
```

起動失敗の場合はエラー内容も伝える（TaskOutputでログを確認）。

## 注意事項

- ポート8080が既に使われている場合（別プロセス）: `netstat -ano | grep LISTENING | grep :8080` でPIDを確認し、ユーザーに報告して判断を仰ぐ
- DB接続情報は環境変数なしで起動すると `localhost:5432` に接続しようとして失敗するので、必ず環境変数を設定すること
