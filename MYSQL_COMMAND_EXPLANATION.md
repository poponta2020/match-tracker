# MySQLコマンドの詳細説明

## 基本的なコマンド構造

```bash
mysql [オプション] [データベース名]
```

---

## 1. 基本的な接続コマンド

### コマンド例
```bash
mysql -u root -proot karuta_tracker
```

### 各部分の説明

#### `mysql`
- **役割**: MySQLクライアントプログラムを起動
- **説明**: ターミナルからMySQLサーバーに接続するためのコマンド

#### `-u root`
- **役割**: 接続するユーザー名を指定
- **`-u`**: "user"の略。ユーザー名を指定するオプション
- **`root`**: 接続するユーザー名（この場合は管理者ユーザー）
- **説明**: MySQLに「rootユーザーとして接続する」という意味

#### `-proot`
- **役割**: パスワードを指定
- **`-p`**: "password"の略。パスワードを指定するオプション
- **`root`**: パスワードの値
- **重要**: `-p`と`root`の間にスペースを入れない（`-p root`ではなく`-proot`）
- **説明**: 「パスワードはrootです」という意味

#### `karuta_tracker`
- **役割**: 接続後に使用するデータベース名を指定
- **説明**: 接続と同時に`karuta_tracker`データベースを選択（`USE karuta_tracker;`と同じ）

---

## 2. パスワードを安全に入力する方法

### コマンド例
```bash
mysql -u root -p karuta_tracker
```

### 各部分の説明

#### `-p`（スペースあり）
- **役割**: パスワードを対話的に入力する
- **説明**: コマンド実行後、パスワードの入力を求められます
- **メリット**: パスワードがコマンド履歴に残らない（セキュリティ上安全）
- **使い方**: 
  ```
  Enter password: [ここでrootと入力してEnter]
  ```

---

## 3. SQLコマンドを直接実行する方法

### コマンド例
```bash
mysql -u root -proot karuta_tracker -e "SHOW TABLES;"
```

### 各部分の説明

#### `-e "SHOW TABLES;"`
- **役割**: SQLコマンドを直接実行する
- **`-e`**: "execute"の略。SQL文を実行するオプション
- **`"SHOW TABLES;"`**: 実行するSQLコマンド（ダブルクォートで囲む）
- **説明**: 接続→SQL実行→結果表示→切断を自動で行う
- **メリット**: インタラクティブモードに入らずに結果を取得できる

---

## 4. よく使うオプション

### `-h` (host)
```bash
mysql -h localhost -u root -proot
```
- **役割**: 接続先のホスト名を指定
- **`localhost`**: ローカルマシン（省略時もlocalhostがデフォルト）
- **説明**: リモートサーバーに接続する場合に使用

### `-P` (port)
```bash
mysql -P 3306 -u root -proot
```
- **役割**: 接続先のポート番号を指定
- **`3306`**: MySQLのデフォルトポート番号
- **説明**: デフォルトポート以外を使用している場合に指定

### `--default-character-set`
```bash
mysql --default-character-set=utf8mb4 -u root -proot
```
- **役割**: 文字コードを指定
- **`utf8mb4`**: UTF-8（日本語対応）
- **説明**: 日本語を扱う場合に重要

---

## 5. 実践的なコマンド例と説明

### 例1: データベース一覧を表示
```bash
mysql -u root -proot -e "SHOW DATABASES;"
```
**実行内容**:
1. `mysql` - MySQLクライアントを起動
2. `-u root` - rootユーザーで接続
3. `-proot` - パスワードrootで認証
4. `-e "SHOW DATABASES;"` - SQLコマンドを実行してデータベース一覧を表示
5. 結果表示後、自動的に切断

### 例2: テーブル構造を確認
```bash
mysql -u root -proot karuta_tracker -e "DESCRIBE players;"
```
**実行内容**:
1. `mysql` - MySQLクライアントを起動
2. `-u root` - rootユーザーで接続
3. `-proot` - パスワードrootで認証
4. `karuta_tracker` - このデータベースを選択
5. `-e "DESCRIBE players;"` - playersテーブルの構造（カラム情報）を表示
6. 結果表示後、自動的に切断

### 例3: データを検索
```bash
mysql -u root -proot karuta_tracker -e "SELECT id, name FROM players LIMIT 5;"
```
**実行内容**:
1. `mysql` - MySQLクライアントを起動
2. `-u root` - rootユーザーで接続
3. `-proot` - パスワードrootで認証
4. `karuta_tracker` - このデータベースを選択
5. `-e "SELECT id, name FROM players LIMIT 5;"` - playersテーブルからidとnameを5件取得
6. 結果表示後、自動的に切断

### 例4: インタラクティブモード
```bash
mysql -u root -proot karuta_tracker
```
**実行内容**:
1. `mysql` - MySQLクライアントを起動
2. `-u root` - rootユーザーで接続
3. `-proot` - パスワードrootで認証
4. `karuta_tracker` - このデータベースを選択
5. **接続が維持される** - SQLコマンドを続けて実行可能
6. `EXIT;` または `QUIT;` で切断

**インタラクティブモード内で使えるコマンド**:
```sql
SHOW TABLES;              -- テーブル一覧
DESCRIBE players;         -- テーブル構造
SELECT * FROM players;    -- データ取得
EXIT;                     -- 終了
```

---

## 6. 警告メッセージを非表示にする

### 問題
```bash
mysql -u root -proot -e "SHOW TABLES;"
```
実行すると以下の警告が表示される：
```
mysql: [Warning] Using a password on the command line interface can be insecure.
```

### 解決方法1: 標準エラー出力をリダイレクト
```bash
mysql -u root -proot -e "SHOW TABLES;" 2>/dev/null
```
- **`2>/dev/null`**: 標準エラー出力（警告）を捨てる
- **説明**: 警告メッセージを非表示にする

### 解決方法2: grepで除外
```bash
mysql -u root -proot -e "SHOW TABLES;" 2>&1 | grep -v "Warning"
```
- **`2>&1`**: 標準エラー出力を標準出力にマージ
- **`| grep -v "Warning"`**: "Warning"を含む行を除外
- **説明**: 警告メッセージの行だけを非表示にする

---

## 7. コマンドの実行順序

### 例: `mysql -u root -proot karuta_tracker -e "SELECT COUNT(*) FROM players;"`
1. **`mysql`** が起動
2. **`-u root`** でユーザー名を設定
3. **`-proot`** でパスワードを設定
4. MySQLサーバーに接続を試みる
5. **`karuta_tracker`** データベースを選択
6. **`-e "SELECT COUNT(*) FROM players;"`** SQLを実行
7. 結果を表示
8. 接続を切断

---

## 8. よくある間違い

### ❌ 間違い: スペースを入れる
```bash
mysql -u root -p root karuta_tracker
```
- **問題**: `-p root` とすると、`root`がパスワードではなく別の引数として解釈される
- **正解**: `mysql -u root -proot karuta_tracker` または `mysql -u root -p karuta_tracker`

### ❌ 間違い: クォートを忘れる
```bash
mysql -u root -proot karuta_tracker -e SHOW TABLES;
```
- **問題**: スペースを含むSQL文が正しく解釈されない
- **正解**: `mysql -u root -proot karuta_tracker -e "SHOW TABLES;"`

---

## まとめ

| オプション | 意味 | 例 |
|-----------|------|-----|
| `-u` | ユーザー名 | `-u root` |
| `-p` | パスワード | `-proot` または `-p`（対話的） |
| `-h` | ホスト名 | `-h localhost` |
| `-P` | ポート番号 | `-P 3306` |
| `-e` | SQL実行 | `-e "SHOW TABLES;"` |
| （引数） | データベース名 | `karuta_tracker` |

**基本パターン**:
```bash
mysql [接続オプション] [データベース名] [実行オプション]
```






