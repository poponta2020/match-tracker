#!/usr/bin/env bash
#
# 旧 Render PostgreSQL 削除スクリプト
#
# 必要な環境変数:
#   RENDER_API_KEY
#   RENDER_GH_PAT
#   OLD_RENDER_PG_ID            ... 削除対象 ID（空ならスキップ）
#   OLD_RENDER_PG_DELETE_AFTER  ... ISO 8601、これより未来なら何もしない
#   LINE_CHANNEL_ACCESS_TOKEN, LINE_NOTIFY_USER_IDS
#   GITHUB_REPOSITORY
# 任意:
#   FORCE=true  ... DELETE_AFTER を無視して即削除
#
set -euo pipefail

FORCE="${FORCE:-false}"
RENDER_API="https://api.render.com/v1"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

log()  { echo "[$(date -u +%H:%M:%S)] $*"; }
warn() { echo "[$(date -u +%H:%M:%S)] WARN: $*" >&2; }
err()  { echo "[$(date -u +%H:%M:%S)] ERROR: $*" >&2; }

notify() {
  "$SCRIPT_DIR/notify-line.sh" "$1" "$2" "$3" || warn "LINE通知失敗"
}

render_api() {
  local method="$1"; local path="$2"
  curl -sS -X "$method" -H "Authorization: Bearer $RENDER_API_KEY" \
    -H "Accept: application/json" "$RENDER_API$path"
}

OLD_ID="${OLD_RENDER_PG_ID:-}"
OLD_AT="${OLD_RENDER_PG_DELETE_AFTER:-}"

if [[ -z "$OLD_ID" ]]; then
  log "OLD_RENDER_PG_ID が未設定。削除対象なし。"
  exit 0
fi

if [[ "$FORCE" != "true" ]]; then
  if [[ -z "$OLD_AT" ]]; then
    log "OLD_RENDER_PG_DELETE_AFTER が未設定。スキップ。"
    exit 0
  fi
  NOW=$(date -u +%s)
  TARGET=$(date -u -d "$OLD_AT" +%s)
  if [[ "$NOW" -lt "$TARGET" ]]; then
    log "削除予定時刻 $OLD_AT までまだ時間があります（残り $(( (TARGET-NOW)/3600 )) 時間）"
    exit 0
  fi
fi

# 削除実行
log "旧DBを削除: $OLD_ID"
RESP_CODE=$(curl -sS -o /tmp/del-resp.json -w "%{http_code}" \
  -X DELETE -H "Authorization: Bearer $RENDER_API_KEY" \
  "$RENDER_API/postgres/$OLD_ID" || echo "000")

case "$RESP_CODE" in
  204|404)
    log "削除成功 (HTTP $RESP_CODE)"
    ;;
  *)
    err "削除失敗 (HTTP $RESP_CODE): $(cat /tmp/del-resp.json 2>/dev/null || true)"
    notify failure "旧Render DB削除失敗" "旧DB($OLD_ID) の削除APIが HTTP $RESP_CODE を返しました。手動確認してください。"
    exit 1
    ;;
esac

# Secret を空にしてループ脱出
clear_secret() {
  local name="$1"
  GH_TOKEN="$RENDER_GH_PAT" gh secret delete "$name" --repo "$GITHUB_REPOSITORY" 2>/dev/null || true
  log "  $name cleared"
}
clear_secret "OLD_RENDER_PG_ID"
clear_secret "OLD_RENDER_PG_DELETE_AFTER"

notify success "旧Render DB削除完了" "旧DB($OLD_ID) を削除しました。"
log "完了"
