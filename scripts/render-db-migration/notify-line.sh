#!/usr/bin/env bash
#
# LINE Messaging API push helper.
#
# Usage: notify-line.sh <success|failure|info> <title> <body>
#
# 必要な環境変数:
#   LINE_CHANNEL_ACCESS_TOKEN
#   LINE_NOTIFY_USER_IDS    ... カンマ区切り
#
set -euo pipefail

STATUS="${1:-info}"
TITLE="${2:-}"
BODY="${3:-}"

if [[ -z "${LINE_CHANNEL_ACCESS_TOKEN:-}" || -z "${LINE_NOTIFY_USER_IDS:-}" ]]; then
  echo "LINE 通知用 env が未設定のためスキップ" >&2
  exit 0
fi

case "$STATUS" in
  success) PREFIX="[OK]" ;;
  failure) PREFIX="[NG]" ;;
  *)       PREFIX="[INFO]" ;;
esac

MESSAGE="$PREFIX $TITLE"
if [[ -n "$BODY" ]]; then
  MESSAGE="$MESSAGE

$BODY"
fi

# LINE の text メッセージ上限 5000 文字
if [[ ${#MESSAGE} -gt 4900 ]]; then
  MESSAGE="${MESSAGE:0:4900}
... (truncated)"
fi

IFS=',' read -ra USER_IDS <<< "$LINE_NOTIFY_USER_IDS"

EXIT_CODE=0
for raw in "${USER_IDS[@]}"; do
  uid="$(echo "$raw" | tr -d '[:space:]')"
  [[ -z "$uid" ]] && continue

  PAYLOAD=$(jq -n --arg to "$uid" --arg text "$MESSAGE" \
    '{to:$to, messages:[{type:"text", text:$text}]}')

  HTTP_CODE=$(curl -sS -o /tmp/line-resp.json -w "%{http_code}" \
    -X POST https://api.line.me/v2/bot/message/push \
    -H "Authorization: Bearer $LINE_CHANNEL_ACCESS_TOKEN" \
    -H "Content-Type: application/json" \
    --data "$PAYLOAD" || echo "000")

  if [[ "$HTTP_CODE" != "200" ]]; then
    echo "line-api-error uid=$uid http=$HTTP_CODE body=$(cat /tmp/line-resp.json 2>/dev/null || true)" >&2
    EXIT_CODE=1
  fi
done

exit $EXIT_CODE
