# Render PostgreSQL データベース移行手順書

Renderの無料プランPostgreSQLデータベースは90日間の制限があります。この手順書では、既存のデータを新しいデータベースに移行する方法を説明します。

## 前提条件

- PostgreSQL 18のクライアントツールがインストールされていること
- 既存のデータベースへのアクセス権限があること
- 新しいデータベースを作成済みであること

## 手順

### 1. PostgreSQL 18クライアントツールのインストール

既存のデータベースがPostgreSQL 18の場合、同じバージョンのクライアントツールが必要です。

```bash
# PostgreSQL公式リポジトリを追加
sudo sh -c 'echo "deb http://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" > /etc/apt/sources.list.d/pgdg.list'

# GPGキーを追加
wget --quiet -O - https://www.postgresql.org/media/keys/ACCC4CF8.asc | sudo apt-key add -

# パッケージリストを更新
sudo apt update

# PostgreSQL 18クライアントをインストール
sudo apt install postgresql-client-18 -y
```

### 2. 既存データベースのバックアップ取得

プロジェクトのルートディレクトリで以下のコマンドを実行します。

```bash
cd /path/to/match-tracker

PGPASSWORD=旧DBのパスワード /usr/lib/postgresql/18/bin/pg_dump \
  -h 旧DBのホスト名 \
  -U 旧DBのユーザー名 \
  -d 旧DBのデータベース名 \
  > backup.sql
```

**例:**
```bash
PGPASSWORD=tQMNFG3XxpSo4CpFWipGwhInXMo2uv5N /usr/lib/postgresql/18/bin/pg_dump \
  -h dpg-d6c3rgn5r7bs73an03pg-a.oregon-postgres.render.com \
  -U karuta \
  -d karuta_tracker \
  > backup.sql
```

### 3. バックアップファイルの検証

バックアップが正常に取得できたか確認します。

```bash
# ファイルサイズを確認
ls -lh backup.sql

# バックアップ内容の冒頭を確認
head -20 backup.sql

# 各テーブルのデータ件数を確認
for table in densuke_urls google_calendar_events match_pairings matches player_profiles players practice_participants practice_sessions venue_match_schedules venues; do
  count=$(awk "/COPY public.$table/,/^\\\\\.$/" backup.sql | grep -v "^COPY" | grep -v "^\\\\\.$" | grep -v "^--" | grep -v "^$" | wc -l)
  echo "$table: $count 件のデータ"
done

# 特定の日付のデータを確認（例: 2026-03-17の試合記録）
awk '/COPY public.matches/,/^\\.$/' backup.sql | grep "2026-03-17" | wc -l
```

### 4. Renderで新しいPostgreSQLデータベースを作成

1. [Render Dashboard](https://dashboard.render.com/) にログイン
2. 右上の **+ New** ボタンをクリック
3. **PostgreSQL** を選択
4. 以下を設定：
   - **Name**: 任意の名前（例: `karuta-tracker-db-new`）
   - **Database**: `karuta_tracker`
   - **User**: `karuta`
   - **Region**: `Oregon (US West)` （既存と同じリージョン推奨）
   - **PostgreSQL Version**: `18`
   - **Instance Type**: **Free**
5. **Create Database** をクリック

### 5. 新しいデータベースの接続情報を取得

データベース作成後、Renderのダッシュボードで以下の情報をコピー：

- **External Database URL** （ローカルからのアクセス用）
- **Internal Database URL** （Renderサービス間のアクセス用）

形式例:
```
External: postgresql://karuta:パスワード@dpg-xxxxx-a.oregon-postgres.render.com/karuta_tracker_b297
Internal: postgresql://karuta:パスワード@dpg-xxxxx-a/karuta_tracker_b297
```

### 6. 新しいデータベースにバックアップをリストア

External Database URLを使用してリストアします。

```bash
PGPASSWORD=新DBのパスワード psql \
  -h 新DBのホスト名 \
  -U 新DBのユーザー名 \
  -d 新DBのデータベース名 \
  -f backup.sql
```

**例:**
```bash
PGPASSWORD=b1FgPgpxsqE83Z1sVoRdes2EdxTAKAal psql \
  -h dpg-d6t1e77kijhs73er5ug0-a.oregon-postgres.render.com \
  -U karuta \
  -d karuta_tracker_b297 \
  -f backup.sql
```

**注意:** 最後に権限関連のエラーが表示されることがありますが、データ自体は正常にリストアされます。

### 7. リストアの確認

データが正しくリストアされたか確認します。

```bash
# 全体のレコード数を確認
PGPASSWORD=新DBのパスワード psql \
  -h 新DBのホスト名 \
  -U 新DBのユーザー名 \
  -d 新DBのデータベース名 \
  -c "SELECT
    (SELECT COUNT(*) FROM players) as players,
    (SELECT COUNT(*) FROM matches) as matches,
    (SELECT COUNT(*) FROM practice_sessions) as sessions,
    (SELECT COUNT(*) FROM practice_participants) as participants;"

# 特定の日付のデータを確認
PGPASSWORD=新DBのパスワード psql \
  -h 新DBのホスト名 \
  -U 新DBのユーザー名 \
  -d 新DBのデータベース名 \
  -c "SELECT COUNT(*) as \"特定日の試合数\" FROM matches WHERE match_date = '2026-03-17';"
```

### 8. ローカル環境の接続情報を更新

`karuta-tracker/.env.local` ファイルを編集します。

```bash
# ローカル開発環境から Render の PostgreSQL に接続する設定
export DB_URL="jdbc:postgresql://新DBのホスト名/新DBのデータベース名"
export DB_USERNAME="新DBのユーザー名"
export DB_PASSWORD="新DBのパスワード"
```

**例:**
```bash
export DB_URL="jdbc:postgresql://dpg-d6t1e77kijhs73er5ug0-a.oregon-postgres.render.com/karuta_tracker_b297"
export DB_USERNAME="karuta"
export DB_PASSWORD="b1FgPgpxsqE83Z1sVoRdes2EdxTAKAal"
```

**重要:** JDBC URLには `?sslmode=require` などのクエリパラメータは**不要**です。

### 9. Renderサービスの環境変数を更新

1. [Render Dashboard](https://dashboard.render.com/) にアクセス
2. **karuta-tracker-api** サービスを開く
3. 左メニューの **Environment** タブをクリック
4. 以下の環境変数を更新（Internal Database URLを使用）：

```
DB_URL = jdbc:postgresql://新DBの内部ホスト名:5432/新DBのデータベース名
DB_USERNAME = 新DBのユーザー名
DB_PASSWORD = 新DBのパスワード
```

**例:**
```
DB_URL = jdbc:postgresql://dpg-d6t1e77kijhs73er5ug0-a:5432/karuta_tracker_b297
DB_USERNAME = karuta
DB_PASSWORD = b1FgPgpxsqE83Z1sVoRdes2EdxTAKAal
```

**重要:**
- **ポート番号 `:5432` を必ず明示的に指定してください**
- Internal Database URLには通常ポート番号が含まれていませんが、JDBC接続では明示的な指定が必要です
- ホスト名は Internal URL のもの（`.oregon-postgres.render.com` が**付かない**形式）を使用

5. **Save Changes** をクリック（自動的に再デプロイが開始されます）

**注意:** 新しく作成したデータベースのInternal接続が有効になるまで、数分～15分程度かかる場合があります。接続タイムアウトエラーが出る場合は、10-15分待ってから再度デプロイしてみてください。

### 10. 動作確認

#### ローカル環境での確認

```bash
cd karuta-tracker
source .env.local
./gradlew bootRun --args='--spring.profiles.active=render'
```

ブラウザで `http://localhost:8080/actuator/health` にアクセスして正常に起動するか確認してください。

#### Render環境での確認

再デプロイ完了後、Renderのサービスページで **Logs** を確認し、エラーがないか確認します。

アプリケーションのURLにアクセスして、データが正しく表示されるか確認してください。

### 11. 古いデータベースの削除（任意）

新しいデータベースで問題なく動作することを数日～1週間確認した後、Renderダッシュボードから古いデータベースを削除できます。

1. Render Dashboardで古いデータベースを開く
2. 右上の **Settings** → **Delete Database** から削除

## トラブルシューティング

### バージョン不一致エラー

```
pg_dump: error: aborting because of server version mismatch
```

このエラーが出た場合は、PostgreSQL 18のクライアントツールをインストールしてください（手順1参照）。

### 接続エラー

#### Internal接続でタイムアウトする場合

**原因1: ポート番号の指定漏れ**

Internal Database URLには通常ポート番号が含まれていませんが、JDBC接続では必ず `:5432` を明示する必要があります。

誤り:
```
jdbc:postgresql://dpg-xxxxx-a/karuta_tracker_b297
```

正解:
```
jdbc:postgresql://dpg-xxxxx-a:5432/karuta_tracker_b297
```

**原因2: DBの準備完了待ち**

新しく作成したデータベースのInternal接続が有効になるまで、数分～15分程度かかる場合があります。

対処法:
- データベースのStatusが **Available** （緑色）になっているか確認
- 10-15分待ってから再度デプロイを試す
- それでもダメな場合は、External URLを使用（同じリージョン内なら十分高速）

**原因3: プロジェクト・リージョンの不一致**

- WebサービスとDBが同じプロジェクト内にあるか確認
- 両方が同じリージョン（例: Oregon US West）にあるか確認

#### その他の確認事項

- ホスト名、ポート番号、ユーザー名、パスワードが正しいか確認
- External URLとInternal URLを適切に使い分けているか確認
  - ローカル/外部からのアクセス: External URL（`.oregon-postgres.render.com` 付き）
  - Renderサービス間のアクセス: Internal URL（ホスト名のみ、ドメインなし）

### リストア時の権限エラー

リストアの最後に以下のようなエラーが出ることがありますが、データは正常にリストアされています：

```
ERROR: permission denied to change default privileges
```

このエラーは無視して問題ありません。

## まとめ

- Renderの無料DBは90日制限があるため、定期的な移行が必要
- pg_dumpとpsqlを使ってデータを完全に移行可能
- External URLとInternal URLの使い分けが重要
- JDBC URLにはクエリパラメータを付けない
- **Internal接続では必ずポート番号 `:5432` を明示する**
- 新しいDBのInternal接続が有効になるまで数分～15分かかる場合がある

## よくある躓きポイント

### Internal接続で接続タイムアウトが発生する

**症状:**
```
HikariPool-1 - Connection is not available, request timed out after 30000ms
The connection attempt failed.
```

**主な原因と解決策:**

1. **ポート番号の指定漏れ（最も多い）**
   - Internal URLには `:5432` が含まれていないため、明示的に追加する必要がある
   - 正: `jdbc:postgresql://dpg-xxxxx-a:5432/database_name`
   - 誤: `jdbc:postgresql://dpg-xxxxx-a/database_name`

2. **DBの準備完了待ち**
   - 新規作成直後のDBはInternal接続の準備に時間がかかる
   - 10-15分待ってから再試行
   - 急ぐ場合はExternal URLを使用

3. **URLの形式間違い**
   - Internal URL: ホスト名のみ（`dpg-xxxxx-a`）
   - External URL: 完全なドメイン（`dpg-xxxxx-a.oregon-postgres.render.com`）
   - 混同しないように注意

---

**最終更新:** 2026-03-18
