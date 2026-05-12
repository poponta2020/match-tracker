# Render DB 自動マイグレーション 初期セットアップ手順

Render 無料 PostgreSQL の30日制限を回避するため、月次で「新DB作成 → データ移行 → 環境変数差し替え → 旧DB削除」を GitHub Actions で自動実行する。本書はその初回セットアップ手順をまとめる。

通常運用（自動マイグレーションが動き始めた後の挙動・障害対応）は `docs/operations/render-database-migration.md` を参照。

---

## 全体像

```
[毎日 0:00 JST]
  └─ .github/workflows/migrate-render-db.yml が起動
       └─ 現DB が作成から 25 日未満なら exit 0 (skip)
       └─ 25 日以上なら：
            1. pg_dump で現DBをバックアップ
            2. Render API で新 PostgreSQL (free, v18) を作成
            3. 新DBが Available になるまでポーリング
            4. psql で新DBにリストア
            5. リストア検証（テーブルごとの件数一致）
            6. Render API で web service の DB_URL/USERNAME/PASSWORD を新DBへ差し替え
            7. デプロイトリガー → /ping ヘルスチェック
            8. 失敗時は env vars をロールバック
            9. 成功時は新DB情報を GitHub Secrets に書き戻す
           10. 旧DB情報を `OLD_*` Secrets に退避（14日後の削除予約）
           11. LINE Messaging API で実行結果を通知

[毎日 0:30 JST]
  └─ .github/workflows/cleanup-old-render-db.yml が起動
       └─ OLD_RENDER_PG_DELETE_AFTER が現在時刻以前なら旧DB削除
```

---

## 必要なシークレット一覧

すべて GitHub Secrets に登録する（リポジトリ Settings → Secrets and variables → Actions）。

### 手動で発行・登録するもの（初回のみ）

| Secret名 | 用途 | 取得元 |
|---|---|---|
| `RENDER_API_KEY` | Render API 全般 | Render Dashboard → Account Settings → API Keys |
| `RENDER_OWNER_ID` | 新DB作成時の所有者指定 | Render API（後述コマンド） |
| `RENDER_SERVICE_ID` | karuta-tracker-api の Service ID | Render API（後述コマンド） |
| `RENDER_GH_PAT` | Actions から Secrets を更新するための PAT | GitHub Settings → Developer settings → Personal access tokens |
| `LINE_CHANNEL_ACCESS_TOKEN` | LINE Messaging API でプッシュ送信 | LINE Developers Console |
| `LINE_NOTIFY_USER_IDS` | 通知先 LINE userId（カンマ区切り） | DB の `players` テーブル or LINE 友だち追加時のログ |

### 自動で更新されるもの（初回登録後、ワークフローが書き換える）

| Secret名 | 用途 |
|---|---|
| `RENDER_PG_ID` | 現DB の Postgres リソース ID（25日経過判定に使用） |
| `PG_HOST` / `PG_USER` / `PG_PASSWORD` / `PG_DATABASE` | 現DB の接続情報 |
| `OLD_RENDER_PG_ID` | 削除予約中の旧DB ID |
| `OLD_RENDER_PG_DELETE_AFTER` | 旧DB削除予定日時（ISO 8601） |

初回は `CLAUDE.local.md` の値を元に手動登録する。

---

## 1. Render API Key の取得

1. https://dashboard.render.com/u/settings#api-keys を開く
2. **Create API Key** をクリック
3. 名前を `github-actions-db-migration` などにして発行
4. 表示された API Key をコピー（再表示不可なので必ず控える）

---

## 2. Render の OwnerId と ServiceId の取得

Render API を直接叩いて取得する。API Key を環境変数に入れて以下を実行：

```bash
export RENDER_API_KEY='<手順1で発行したキー>'

# OwnerId 一覧
curl -s -H "Authorization: Bearer $RENDER_API_KEY" \
  https://api.render.com/v1/owners | jq '.[].owner | {id, name, type}'

# Service ID 一覧
curl -s -H "Authorization: Bearer $RENDER_API_KEY" \
  "https://api.render.com/v1/services?type=web_service&name=karuta-tracker-api" \
  | jq '.[].service | {id, name}'

# 現在の PostgreSQL リソース ID
curl -s -H "Authorization: Bearer $RENDER_API_KEY" \
  https://api.render.com/v1/postgres | jq '.[].postgres | {id, name, status, createdAt}'
```

それぞれ次の Secret に登録する：
- `RENDER_OWNER_ID` ← owner ID
- `RENDER_SERVICE_ID` ← `karuta-tracker-api` の service ID
- `RENDER_PG_ID` ← 現在の Postgres ID

---

## 3. GitHub PAT の取得

Actions から Secrets を書き換えるために必要。

1. https://github.com/settings/tokens?type=beta を開く
2. **Generate new token (fine-grained)** をクリック
3. 設定：
   - **Token name**: `match-tracker-db-migration`
   - **Expiration**: 1 year（有効期限が切れたら再発行が必要）
   - **Repository access**: Only select repositories → `poponta2020/match-tracker`
   - **Repository permissions**:
     - `Secrets`: **Read and write**
     - `Actions`: Read（任意）
     - `Metadata`: Read（必須・自動付与）
4. **Generate token** → 表示されたトークンをコピー

`RENDER_GH_PAT` Secret に登録する。

---

## 4. LINE Messaging API トークンの取得

既に `LineMessagingService` が動いているなら、同じチャネルのアクセストークンを使う。

### 既存 channel を使う場合

`karuta-tracker` の Render 環境変数または `application*.yml` で設定済みの LINE channel access token を `LINE_CHANNEL_ACCESS_TOKEN` Secret にコピーする。

### 新規 channel の場合

1. https://developers.line.biz/console/ にログイン
2. 該当 Provider → Messaging API channel を選択
3. **Messaging API settings** タブ → **Channel access token (long-lived)** を Issue
4. 表示されたトークンを `LINE_CHANNEL_ACCESS_TOKEN` に登録

---

## 5. LINE 通知先 userId の取得

DB 移行通知の宛先となる管理者の LINE userId を控える。複数人ならカンマ区切り。

既存運用に紐付けて取得する：

```bash
PGPASSWORD='<DBパスワード>' psql \
  -h dpg-d7fpnf8sfn5c73d7hkgg-a.oregon-postgres.render.com \
  -U karuta -d karuta_tracker_o7gz \
  -c "SELECT p.name, p.line_user_id
      FROM players p
      WHERE p.role IN ('SUPER_ADMIN', 'ADMIN')
        AND p.line_user_id IS NOT NULL;"
```

得られた `line_user_id` をカンマ区切りで `LINE_NOTIFY_USER_IDS` Secret に登録する。

例: `LINE_NOTIFY_USER_IDS=U1234abcd...,U5678efgh...`

---

## 6. 現DB の接続情報を Secrets に登録

`CLAUDE.local.md` の値を元に登録する：

| Secret | 値 |
|---|---|
| `PG_HOST` | `dpg-d7fpnf8sfn5c73d7hkgg-a.oregon-postgres.render.com` |
| `PG_USER` | `karuta` |
| `PG_PASSWORD` | （`CLAUDE.local.md` の `DB_PASSWORD`） |
| `PG_DATABASE` | `karuta_tracker_o7gz` |

---

## 7. GitHub Secrets への一括登録（コマンド例）

`gh` CLI が手元にあるなら一括で登録できる：

```bash
# 必要に応じて gh auth login で先にログイン

REPO=poponta2020/match-tracker

# 値はそれぞれ実際のものに置き換える
gh secret set RENDER_API_KEY            --repo $REPO --body '<step1の値>'
gh secret set RENDER_OWNER_ID           --repo $REPO --body '<step2のowner ID>'
gh secret set RENDER_SERVICE_ID         --repo $REPO --body '<step2のservice ID>'
gh secret set RENDER_PG_ID              --repo $REPO --body '<step2のpostgres ID>'
gh secret set RENDER_GH_PAT             --repo $REPO --body '<step3のPAT>'
gh secret set LINE_CHANNEL_ACCESS_TOKEN --repo $REPO --body '<step4のtoken>'
gh secret set LINE_NOTIFY_USER_IDS      --repo $REPO --body '<step5のIDカンマ区切り>'
gh secret set PG_HOST                   --repo $REPO --body '<step6の値>'
gh secret set PG_USER                   --repo $REPO --body '<step6の値>'
gh secret set PG_PASSWORD               --repo $REPO --body '<step6の値>'
gh secret set PG_DATABASE               --repo $REPO --body '<step6の値>'
```

登録確認：

```bash
gh secret list --repo $REPO
```

---

## 8. 初回動作確認

### Dry-run（実際にはマイグレーションしない動作テスト）

```bash
gh workflow run migrate-render-db.yml --repo $REPO -f dry_run=true
```

ジョブログで以下が確認できれば OK：
- 現DB の createdAt 取得に成功
- 25日ガードの判定が正しく出力される
- dry_run なので実際の pg_dump 以降は実行されない（早期 exit）

### 強制実行（25日ガードを無視して本番フローを通す）

```bash
gh workflow run migrate-render-db.yml --repo $REPO -f force=true
```

**注意:** これは本番DBを実際に置き換える。動作確認は十分に検証してから。

---

## 9. 通常運用の挙動

セットアップ完了後は完全に放置で動作する：

- **毎日 0:00 JST**: ガード判定のみ。25日未満なら何もせず exit
- **25日経過後の起動**: 実際のマイグレーション実行 → LINE 通知
- **マイグレーション翌日以降の毎日 0:30 JST**: 旧DB の削除予定日時に達していれば削除 → LINE 通知

ユーザーがやること：
1. LINE 通知が来たら成功/失敗を確認
2. 失敗していた場合は Actions ログを確認
3. 成功したら `CLAUDE.local.md` の DB 接続情報を新DBの値に書き換え（ローカル開発用）
   - LINE 通知に新DB の `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` が含まれる

---

## トラブルシューティング

### Actions が起動しない

- `.github/workflows/migrate-render-db.yml` がデフォルトブランチ（main）にマージされているか確認
- Actions が無効化されていないか（Settings → Actions → General）

### Render API 401 エラー

- `RENDER_API_KEY` の有効期限切れ。再発行して Secret を更新

### LINE 通知が届かない

- `LINE_CHANNEL_ACCESS_TOKEN` が long-lived トークンか確認
- `LINE_NOTIFY_USER_IDS` の userId が当該 LINE Bot を友だち追加済みか確認
- ジョブログで `line-api-error` 行を確認

### マイグレーションが失敗してロールバックされた

- 旧DB はまだ生きているので、サービスは継続稼働
- ジョブログで失敗ステップを特定
- 必要なら Actions の `force=true` で再実行
- 旧DB の期限切れが迫っているなら、`docs/operations/render-database-migration.md` の手動手順でフォールバック

### PAT の有効期限切れ

- 1年に1回再発行が必要
- 期限が近づくと GitHub からメール通知が来る
- 期限切れになると Secrets 自動更新が失敗 → 次回マイグレーションで失敗
