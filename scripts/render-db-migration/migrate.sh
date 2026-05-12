#!/usr/bin/env bash
#
# Render PostgreSQL 自動マイグレーションスクリプト
#
# 必要な環境変数:
#   RENDER_API_KEY, RENDER_OWNER_ID, RENDER_SERVICE_ID, RENDER_PG_ID
#   RENDER_GH_PAT
#   PG_HOST, PG_USER, PG_PASSWORD, PG_DATABASE
#   LINE_CHANNEL_ACCESS_TOKEN, LINE_NOTIFY_USER_IDS
#   GITHUB_REPOSITORY
# 任意:
#   DRY_RUN=true   ... pg_dump 以降を実行せず判定のみ
#   FORCE=true     ... 25日ガードを無視して強制実行
#   MIN_AGE_DAYS=25 ... ガードの閾値 (デフォルト 25)
#
set -euo pipefail

# Render PostgreSQL は SSL 必須。libpq のデフォルト sslmode=prefer だとフォールバックして
# 接続が切られる場合があるので、全 psql/pg_dump に明示的に require を強制。
export PGSSLMODE=require

DRY_RUN="${DRY_RUN:-false}"
FORCE="${FORCE:-false}"
MIN_AGE_DAYS="${MIN_AGE_DAYS:-25}"
DELETE_AFTER_DAYS="${DELETE_AFTER_DAYS:-14}"

RENDER_API="https://api.render.com/v1"
NEW_DB_NAME="karuta-tracker-db-$(date -u +%Y%m%d)"
NEW_DB_DATABASE="karuta_tracker"
NEW_DB_USER="karuta"
NEW_DB_REGION="oregon"
NEW_DB_VERSION="18"
NEW_DB_PLAN="free"

DUMP_FILE="${RUNNER_TEMP:-/tmp}/db-dump-$(date -u +%Y%m%d-%H%M%S).sql"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

log()  { echo "[$(date -u +%H:%M:%S)] $*"; }
warn() { echo "[$(date -u +%H:%M:%S)] WARN: $*" >&2; }
err()  { echo "[$(date -u +%H:%M:%S)] ERROR: $*" >&2; }

# bash の `trap ERR` は明示的な `exit 1` では発火しないため、EXIT trap でカバーする。
# 失敗時に cleanup（or resume_old_pg のみ）を確実に呼ぶ。
on_exit() {
  local code=$?
  set +e
  if [[ $code -ne 0 ]]; then
    warn "exit code=$code, running cleanup if available"
    if declare -f cleanup > /dev/null 2>&1; then
      cleanup
    elif declare -f resume_old_pg > /dev/null 2>&1; then
      resume_old_pg
    fi
  fi
}
trap on_exit EXIT

notify() {
  local status="$1"; local title="$2"; local body="$3"
  "$SCRIPT_DIR/notify-line.sh" "$status" "$title" "$body" || warn "LINE通知失敗（処理は継続）"
}

render_api() {
  local method="$1"; local path="$2"; local payload="${3:-}"
  local args=(-sS -X "$method" -H "Authorization: Bearer $RENDER_API_KEY" -H "Accept: application/json")
  if [[ -n "$payload" ]]; then
    args+=(-H "Content-Type: application/json" --data "$payload")
  fi
  curl "${args[@]}" "$RENDER_API$path"
}

require_env() {
  local missing=()
  for v in RENDER_API_KEY RENDER_OWNER_ID RENDER_SERVICE_ID RENDER_PG_ID RENDER_GH_PAT \
           PG_HOST PG_USER PG_PASSWORD PG_DATABASE \
           LINE_CHANNEL_ACCESS_TOKEN LINE_NOTIFY_USER_IDS GITHUB_REPOSITORY; do
    if [[ -z "${!v:-}" ]]; then missing+=("$v"); fi
  done
  if [[ ${#missing[@]} -gt 0 ]]; then
    err "必須環境変数が未設定: ${missing[*]}"
    exit 2
  fi
}

#=== 1. 事前チェック =========================================================
require_env

log "現DB情報を取得: pgId=$RENDER_PG_ID"
CURRENT_PG_JSON=$(render_api GET "/postgres/$RENDER_PG_ID")
# レスポンス形式が `{...}` と `{"postgres": {...}}` の両方ある可能性に備えて両対応
CURRENT_CREATED_AT=$(echo "$CURRENT_PG_JSON" | jq -r '.createdAt // .postgres.createdAt // empty')

if [[ -z "$CURRENT_CREATED_AT" ]]; then
  err "現DBの createdAt 取得失敗。Render API レスポンス: $CURRENT_PG_JSON"
  notify failure "Render DB 自動マイグレーション失敗" "現DBの情報取得に失敗しました。RENDER_PG_ID が正しいか確認してください。"
  exit 1
fi

# 経過日数の判定
CREATED_EPOCH=$(date -u -d "$CURRENT_CREATED_AT" +%s)
NOW_EPOCH=$(date -u +%s)
AGE_DAYS=$(( (NOW_EPOCH - CREATED_EPOCH) / 86400 ))

log "現DB作成日: $CURRENT_CREATED_AT (経過 $AGE_DAYS 日)"

if [[ "$FORCE" != "true" && "$AGE_DAYS" -lt "$MIN_AGE_DAYS" ]]; then
  log "経過日数 $AGE_DAYS < $MIN_AGE_DAYS 日のため、マイグレーションをスキップします"
  exit 0
fi

if [[ "$DRY_RUN" == "true" ]]; then
  log "DRY_RUN=true のため、ここで終了します（実マイグレーションは実行しません）"
  exit 0
fi

#=== 2. pg_dump =============================================================
log "現DBから pg_dump を取得します"
PGPASSWORD="$PG_PASSWORD" pg_dump \
  -h "$PG_HOST" -U "$PG_USER" -d "$PG_DATABASE" \
  --no-owner --no-privileges --format=plain \
  > "$DUMP_FILE"

DUMP_SIZE=$(stat -c%s "$DUMP_FILE")
log "ダンプ取得完了: $DUMP_FILE ($DUMP_SIZE bytes)"

if [[ "$DUMP_SIZE" -lt 1024 ]]; then
  err "ダンプサイズが異常に小さい ($DUMP_SIZE bytes)"
  notify failure "Render DB 自動マイグレーション失敗" "pg_dump の出力が小さすぎます (${DUMP_SIZE} bytes)。"
  exit 1
fi

# 件数比較用に source DB の行数を取得（psql で直接 count → ダンプ依存なし）
declare -A SRC_COUNTS
for table in players matches practice_sessions practice_participants match_pairings venues; do
  SRC_COUNTS[$table]=$(PGPASSWORD="$PG_PASSWORD" psql \
    -h "$PG_HOST" -U "$PG_USER" -d "$PG_DATABASE" \
    -tAc "SELECT COUNT(*) FROM $table;")
  log "  source.$table = ${SRC_COUNTS[$table]} 行"
done

#=== 2.5 旧DB を suspend（Free tier の "1 active DB" 制約を回避） ===========
# 旧DBを suspend してから新DBを作成する。失敗時は cleanup で resume してサービス復旧。
log "旧DB を suspend します（Free tier 制約のため）"
SUSPEND_HTTP=$(curl -sS -o /tmp/suspend.out -w "%{http_code}" \
  -X POST -H "Authorization: Bearer $RENDER_API_KEY" \
  "$RENDER_API/postgres/$RENDER_PG_ID/suspend")
if [[ "$SUSPEND_HTTP" != "202" && "$SUSPEND_HTTP" != "200" ]]; then
  err "旧DB suspend 失敗: HTTP $SUSPEND_HTTP, $(cat /tmp/suspend.out 2>/dev/null)"
  notify failure "Render DB 自動マイグレーション失敗" "旧DB の suspend に失敗しました（マイグレーション中止）。"
  exit 1
fi
SUSPENDED_OLD_PG=true

resume_old_pg() {
  if [[ "${SUSPENDED_OLD_PG:-false}" != "true" ]]; then return 0; fi
  warn "旧DB を resume してサービス復旧します"
  curl -sS -X POST -H "Authorization: Bearer $RENDER_API_KEY" \
    "$RENDER_API/postgres/$RENDER_PG_ID/resume" >/dev/null || true
}

# suspend 反映待ち
sleep 10
SUSPEND_STATUS=$(render_api GET "/postgres/$RENDER_PG_ID" | jq -r '.status // .postgres.status // empty')
log "旧DB status: $SUSPEND_STATUS"

#=== 3. 新DB作成 ============================================================
log "新DBを作成します: name=$NEW_DB_NAME"

CREATE_PAYLOAD=$(jq -n \
  --arg name "$NEW_DB_NAME" \
  --arg ownerId "$RENDER_OWNER_ID" \
  --arg databaseName "$NEW_DB_DATABASE" \
  --arg databaseUser "$NEW_DB_USER" \
  --arg region "$NEW_DB_REGION" \
  --arg version "$NEW_DB_VERSION" \
  --arg plan "$NEW_DB_PLAN" \
  '{
    name: $name,
    ownerId: $ownerId,
    databaseName: $databaseName,
    databaseUser: $databaseUser,
    region: $region,
    version: $version,
    plan: $plan,
    ipAllowList: [{ cidrBlock: "0.0.0.0/0", description: "everywhere" }]
  }')

NEW_PG_JSON=$(render_api POST "/postgres" "$CREATE_PAYLOAD")
NEW_PG_ID=$(echo "$NEW_PG_JSON" | jq -r '.id // .postgres.id // empty')

if [[ -z "$NEW_PG_ID" ]]; then
  err "新DB作成失敗。レスポンス: $NEW_PG_JSON"
  notify failure "Render DB 自動マイグレーション失敗" "新DB作成APIが ID を返しませんでした。旧DB を resume してロールバックします。"
  resume_old_pg
  exit 1
fi
log "新DB作成リクエスト受理: id=$NEW_PG_ID"

# rollback 用フラグ
ROLLBACK_NEW_PG=true
cleanup() {
  set +e
  if [[ "${ROLLBACK_NEW_PG:-false}" == "true" && -n "${NEW_PG_ID:-}" ]]; then
    warn "失敗のため作成済み新DBを削除: $NEW_PG_ID"
    render_api DELETE "/postgres/$NEW_PG_ID" >/dev/null || true
    sleep 30  # 削除反映待ち（次の resume が成功するように）
  fi
  resume_old_pg
}
trap cleanup ERR

#=== 4. 新DB が available になるまでポーリング ===============================
log "新DBが available になるまで待機します（最大 20 分）"
DEADLINE=$(( $(date -u +%s) + 1200 ))
while :; do
  STATUS_JSON=$(render_api GET "/postgres/$NEW_PG_ID")
  STATUS=$(echo "$STATUS_JSON" | jq -r '.status // .postgres.status // empty')
  log "  status=$STATUS"
  if [[ "$STATUS" == "available" ]]; then
    log "新DBが available。接続安定化のため 60 秒待機"
    sleep 60
    break
  fi
  if [[ "$STATUS" == "suspended" || "$STATUS" == "deleting" || "$STATUS" == "unknown" ]]; then
    err "新DBが予期しない status になりました: $STATUS"
    notify failure "Render DB 自動マイグレーション失敗" "新DBの status が $STATUS になりました。"
    exit 1
  fi
  if [[ $(date -u +%s) -gt $DEADLINE ]]; then
    err "新DBが20分以内に available になりませんでした"
    notify failure "Render DB 自動マイグレーション失敗" "新DB($NEW_PG_ID) が available にならないままタイムアウト。"
    exit 1
  fi
  sleep 20
done

#=== 5. 新DB接続情報取得 =====================================================
log "新DB接続情報を取得します"
CONN_JSON=$(render_api GET "/postgres/$NEW_PG_ID/connection-info")
NEW_PG_PASSWORD=$(echo "$CONN_JSON" | jq -r '.password // empty')
NEW_PG_EXT_URL=$(echo "$CONN_JSON" | jq -r '.externalConnectionString // empty')
NEW_PG_INT_URL=$(echo "$CONN_JSON" | jq -r '.internalConnectionString // empty')

if [[ -z "$NEW_PG_PASSWORD" || -z "$NEW_PG_EXT_URL" ]]; then
  err "新DB接続情報取得失敗。レスポンス: $CONN_JSON"
  notify failure "Render DB 自動マイグレーション失敗" "新DB接続情報の取得に失敗しました。"
  exit 1
fi

# external URL から host と database 名を抽出
# 形式: postgresql://user:pass@host[:port]/database  ← port が含まれる場合もあるので除外
NEW_PG_HOST=$(echo "$NEW_PG_EXT_URL" | sed -E 's|^postgresql://[^@]+@([^:/]+).*$|\1|')
NEW_PG_DATABASE=$(echo "$NEW_PG_EXT_URL" | sed -E 's|^postgresql://[^@]+@[^/]+/([^?]+).*$|\1|')

if [[ -z "$NEW_PG_HOST" || -z "$NEW_PG_DATABASE" ]]; then
  err "新DB external URL のパース失敗: $NEW_PG_EXT_URL"
  notify failure "Render DB 自動マイグレーション失敗" "新DBの接続URLパースに失敗しました。"
  cleanup
  exit 1
fi

# internal URL のホスト部（Renderサービス間アクセス用、ドメインなし）
NEW_PG_INT_HOST=$(echo "$NEW_PG_INT_URL" | sed -E 's|^postgresql://[^@]+@([^:/]+).*$|\1|')

log "新DB: host=$NEW_PG_HOST  internalHost=$NEW_PG_INT_HOST  db=$NEW_PG_DATABASE"

#=== 6. リストア =============================================================
# Available になった直後でも SSL ハンドシェイクが安定しないことがあるので、
# リストア前に簡単な接続テストでリトライ。
log "新DBへの SSL 接続テスト（最大 5 回 × 30 秒）"
CONN_OK=false
for i in 1 2 3 4 5; do
  if PGPASSWORD="$NEW_PG_PASSWORD" psql \
    -h "$NEW_PG_HOST" -U "$NEW_DB_USER" -d "$NEW_PG_DATABASE" \
    -c "SELECT 1;" > /tmp/conn-test.log 2>&1; then
    log "  接続テスト成功 (attempt $i)"
    CONN_OK=true
    break
  fi
  log "  接続テスト失敗 (attempt $i): $(tail -1 /tmp/conn-test.log)"
  sleep 30
done

if [[ "$CONN_OK" != "true" ]]; then
  err "新DBへの接続テストが5回失敗"
  notify failure "Render DB 自動マイグレーション失敗" "新DBへの接続が安定しません（SSL ハンドシェイク不安定）。"
  exit 1
fi

log "新DBへリストア実行"
PGPASSWORD="$NEW_PG_PASSWORD" psql \
  -h "$NEW_PG_HOST" -U "$NEW_DB_USER" -d "$NEW_PG_DATABASE" \
  -v ON_ERROR_STOP=1 \
  -f "$DUMP_FILE" \
  > /tmp/restore.log 2>&1 || {
    err "リストア失敗。tail of restore log:"
    tail -50 /tmp/restore.log >&2
    notify failure "Render DB 自動マイグレーション失敗" "新DBへのリストアに失敗しました。"
    exit 1
  }
log "リストア完了"

#=== 7. リストア検証 =========================================================
log "リストア結果を検証します"
verify_count() {
  local table="$1"; local expected="$2"
  local actual
  actual=$(PGPASSWORD="$NEW_PG_PASSWORD" psql \
    -h "$NEW_PG_HOST" -U "$NEW_DB_USER" -d "$NEW_PG_DATABASE" \
    -tAc "SELECT COUNT(*) FROM $table;")
  if [[ "$actual" != "$expected" ]]; then
    err "  $table: 期待値 $expected ≠ 実際 $actual"
    return 1
  fi
  log "  $table: $actual 行 (一致)"
}

VERIFY_FAILED=false
for table in "${!SRC_COUNTS[@]}"; do
  verify_count "$table" "${SRC_COUNTS[$table]}" || VERIFY_FAILED=true
done

if [[ "$VERIFY_FAILED" == "true" ]]; then
  notify failure "Render DB 自動マイグレーション失敗" "リストア後のレコード件数が一致しません。"
  exit 1
fi

#=== 8. ロールバック用に現 env vars を保存 ===================================
log "現在の env vars をロールバック用に取得"
OLD_ENV_RAW=$(render_api GET "/services/$RENDER_SERVICE_ID/env-vars")
# レスポンスが [{"envVar": {...}}, ...] と [{"key":..,"value":..}, ...] のどちらでも処理できるよう正規化
OLD_ENV_NORM=$(echo "$OLD_ENV_RAW" | jq '[.[] | if .envVar then .envVar else . end | {key, value}]')
OLD_ENV_COUNT=$(echo "$OLD_ENV_NORM" | jq 'length')
log "現env vars 件数: $OLD_ENV_COUNT"

if [[ "$OLD_ENV_COUNT" == "0" || "$OLD_ENV_COUNT" == "null" ]]; then
  err "env vars 取得失敗。raw: $OLD_ENV_RAW"
  notify failure "Render DB 自動マイグレーション失敗" "web service の env vars 取得に失敗しました。"
  exit 1
fi

#=== 9. env vars 更新 ========================================================
NEW_DB_URL_JDBC="jdbc:postgresql://$NEW_PG_INT_HOST:5432/$NEW_PG_DATABASE"

log "web service の env vars を新DB情報で置き換えます"

NEW_ENV_PAYLOAD=$(echo "$OLD_ENV_NORM" | jq \
  --arg url "$NEW_DB_URL_JDBC" \
  --arg user "$NEW_DB_USER" \
  --arg pass "$NEW_PG_PASSWORD" \
  '[.[] | if .key == "DB_URL" then .value = $url
          elif .key == "DB_USERNAME" then .value = $user
          elif .key == "DB_PASSWORD" then .value = $pass
          else . end]')

UPDATE_RESP=$(render_api PUT "/services/$RENDER_SERVICE_ID/env-vars" "$NEW_ENV_PAYLOAD")
log "env vars 更新レスポンス受信"

# 失敗したら env vars を元に戻す
ROLLBACK_ENV=true
rollback_env() {
  if [[ "${ROLLBACK_ENV:-false}" != "true" ]]; then return 0; fi
  warn "env vars をロールバックします"
  render_api PUT "/services/$RENDER_SERVICE_ID/env-vars" "$OLD_ENV_NORM" >/dev/null || true
  render_api POST "/services/$RENDER_SERVICE_ID/deploys" '{"clearCache":"do_not_clear"}' >/dev/null || true
}
cleanup() {
  set +e
  rollback_env
  if [[ "${ROLLBACK_NEW_PG:-false}" == "true" && -n "${NEW_PG_ID:-}" ]]; then
    warn "失敗のため作成済み新DBを削除: $NEW_PG_ID"
    render_api DELETE "/postgres/$NEW_PG_ID" >/dev/null || true
    sleep 30  # 削除反映待ち
  fi
  resume_old_pg
}
trap cleanup ERR

#=== 10. デプロイトリガー ===================================================
log "再デプロイをトリガーします"
DEPLOY_RESP=$(render_api POST "/services/$RENDER_SERVICE_ID/deploys" '{"clearCache":"do_not_clear"}')
DEPLOY_ID=$(echo "$DEPLOY_RESP" | jq -r '.id // .deploy.id // empty')

if [[ -z "$DEPLOY_ID" ]]; then
  err "デプロイ ID 取得失敗: $DEPLOY_RESP"
  notify failure "Render DB 自動マイグレーション失敗" "デプロイトリガーが失敗しました。"
  exit 1
fi
log "デプロイ開始: id=$DEPLOY_ID"

#=== 11. デプロイ完了を待つ =================================================
DEADLINE=$(( $(date -u +%s) + 1500 ))   # 25分
while :; do
  D_JSON=$(render_api GET "/services/$RENDER_SERVICE_ID/deploys/$DEPLOY_ID")
  D_STATUS=$(echo "$D_JSON" | jq -r '.status // .deploy.status // empty')
  log "  deploy.status=$D_STATUS"
  case "$D_STATUS" in
    live) break ;;
    build_failed|deploy_failed|update_failed|pre_deploy_failed|canceled)
      err "デプロイ失敗: $D_STATUS"
      notify failure "Render DB 自動マイグレーション失敗" "再デプロイが失敗しました (status=$D_STATUS)。env varsをロールバックします。"
      exit 1
      ;;
  esac
  if [[ $(date -u +%s) -gt $DEADLINE ]]; then
    err "デプロイが25分以内に完了しませんでした"
    notify failure "Render DB 自動マイグレーション失敗" "デプロイがタイムアウトしました。"
    exit 1
  fi
  sleep 20
done
log "デプロイ完了 (live)"

#=== 12. /ping ヘルスチェック ===============================================
SERVICE_INFO=$(render_api GET "/services/$RENDER_SERVICE_ID")
SERVICE_URL=$(echo "$SERVICE_INFO" | jq -r '
  .serviceDetails.url
  // .serviceDetails.externalUrl
  // .service.serviceDetails.url
  // .service.serviceDetails.externalUrl
  // empty
')
if [[ -n "$SERVICE_URL" ]]; then
  log "ヘルスチェック: $SERVICE_URL/ping"
  for i in 1 2 3 4 5; do
    HTTP_CODE=$(curl -sS -o /dev/null -w "%{http_code}" "$SERVICE_URL/ping" || echo "000")
    if [[ "$HTTP_CODE" == "200" ]]; then
      log "  /ping = 200 OK"
      break
    fi
    log "  attempt $i: /ping = $HTTP_CODE, retrying in 15s"
    if [[ "$i" == "5" ]]; then
      err "ヘルスチェック失敗 (最終 HTTP $HTTP_CODE)"
      notify failure "Render DB 自動マイグレーション失敗" "/ping が 200 を返しません ($HTTP_CODE)。"
      exit 1
    fi
    sleep 15
  done
else
  warn "service URL が取得できないためヘルスチェックをスキップ"
fi

#=== 13. ここまで来たらロールバック不要 =====================================
ROLLBACK_NEW_PG=false
ROLLBACK_ENV=false
trap - ERR

#=== 14. GitHub Secrets を新DB情報で更新 ====================================
log "GitHub Secrets を新DB情報で更新します"
set_secret() {
  local name="$1"; local value="$2"
  # gh CLI のバージョンによって `--body-file -` が無いので、引数なしで stdin から読む。
  printf '%s' "$value" | GH_TOKEN="$RENDER_GH_PAT" gh secret set "$name" --repo "$GITHUB_REPOSITORY"
  log "  $name updated"
}

# 旧DB情報を退避（cleanup ワークフローが14日後に削除する）
DELETE_AFTER=$(date -u -d "+$DELETE_AFTER_DAYS days" +%Y-%m-%dT%H:%M:%SZ)
set_secret "OLD_RENDER_PG_ID"           "$RENDER_PG_ID"
set_secret "OLD_RENDER_PG_DELETE_AFTER" "$DELETE_AFTER"

# 新DB情報を「現DB」として書き戻し
set_secret "RENDER_PG_ID" "$NEW_PG_ID"
set_secret "PG_HOST"      "$NEW_PG_HOST"
set_secret "PG_USER"      "$NEW_DB_USER"
set_secret "PG_PASSWORD"  "$NEW_PG_PASSWORD"
set_secret "PG_DATABASE"  "$NEW_PG_DATABASE"

#=== 15. 成功通知 ===========================================================
SUMMARY=$(cat <<EOF
Render PostgreSQL 自動マイグレーション完了

新DB:
  name: $NEW_DB_NAME
  id:   $NEW_PG_ID
  host: $NEW_PG_HOST
  db:   $NEW_PG_DATABASE
  user: $NEW_DB_USER

旧DB（suspended のまま放置、14日後に cleanup ワークフローが削除）:
  id:   $RENDER_PG_ID
  作成: $CURRENT_CREATED_AT (経過 $AGE_DAYS 日)
  削除予定: $DELETE_AFTER

ローカル開発の CLAUDE.local.md を更新してください:
DB_URL=jdbc:postgresql://$NEW_PG_HOST/$NEW_PG_DATABASE
DB_USERNAME=$NEW_DB_USER
DB_PASSWORD=$NEW_PG_PASSWORD
EOF
)

notify success "Render DB 自動マイグレーション成功" "$SUMMARY"
log "全工程完了"
