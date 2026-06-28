# Render ログ取得ツール

本番（Render）の `karuta-tracker-api` のログを Render REST API 経由で取得するスクリプト。
主にバグ調査（`/bug-report` の調査ステップ）で本番のスタックトレース・エラーを確認するために使う。

## 認証情報

秘密情報は **`CLAUDE.local.md`（gitignore対象）** の「Render API」セクションに置く。
スクリプトはそこから読み取るため、スクリプト自体に鍵は含まれない（= コミット可）。

```
RENDER_API_KEY=rnd_xxxxxxxxxxxxxxxx     # Render Dashboard → Account Settings → API Keys で発行
RENDER_OWNER_ID=tea-xxxxxxxx or usr-... # ワークスペースID
RENDER_SERVICE_ID=srv-xxxxxxxx          # karuta-tracker-api のサービスID
```

`RENDER_OWNER_ID` / `RENDER_SERVICE_ID` は API キーがあれば `/v1/services` から解決できる。

## 使い方

```powershell
# 直近6時間の error ログ
powershell -ExecutionPolicy Bypass -File scripts/render-logs/Get-RenderLogs.ps1 -Hours 6 -Level error

# 例外クラス名で本文検索（24時間・最大5ページ遡る）
powershell -ExecutionPolicy Bypass -File scripts/render-logs/Get-RenderLogs.ps1 -Text "NullPointerException" -Hours 24 -MaxPages 5

# 500 を返したリクエストログ
powershell -ExecutionPolicy Bypass -File scripts/render-logs/Get-RenderLogs.ps1 -StatusCode 500 -Type request -Hours 24

# 時刻範囲を明示（ISO8601 / UTC）
powershell -ExecutionPolicy Bypass -File scripts/render-logs/Get-RenderLogs.ps1 -Start 2026-06-28T00:00:00Z -End 2026-06-28T03:00:00Z

# 生 JSON で取得（後段処理用）
powershell -ExecutionPolicy Bypass -File scripts/render-logs/Get-RenderLogs.ps1 -Hours 1 -Json
```

### 主なオプション

| オプション | 説明 | 既定 |
|---|---|---|
| `-Hours` | 何時間前まで遡るか（`-Start`/`-End` 指定時は無視） | 6 |
| `-Start` / `-End` | ISO8601(UTC) で時刻範囲を明示 | End=現在 |
| `-Text` | 本文の部分一致（複数可） | - |
| `-Level` | ログレベル（error / warning / info …） | - |
| `-Type` | app / request / build | - |
| `-StatusCode` | HTTP ステータス（500 等） | - |
| `-Method` / `-Path` | HTTP メソッド / パス | - |
| `-Limit` | 1ページ件数（1-100） | 100 |
| `-MaxPages` | 最大ページ数（遡る量） | 1 |
| `-Json` | 生 JSON 出力 | off |

## 注意

- `karuta-tracker-api` は **free プラン**。アイドルでスピンダウン中はログが薄く、保持期間も短い。該当ログが無い場合は `-Hours` を広げる。
- 取得したログをそのまま Issue / PR / チャットに貼るときは、**APIキーや個人情報を含めない**こと。
- Render Logs API リファレンス: https://api-docs.render.com/reference/list-logs
