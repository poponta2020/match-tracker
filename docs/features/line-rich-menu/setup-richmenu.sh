#!/bin/bash
# リッチメニュー一括設定スクリプト
# 使い方: bash setup-richmenu.sh <画像ファイルパス>

IMAGE_PATH="${1:-docs/features/line-rich-menu/richmenu.png}"

if [ ! -f "$IMAGE_PATH" ]; then
  echo "Error: Image file not found: $IMAGE_PATH"
  exit 1
fi

# リッチメニューJSON定義（3列x2行）
RICH_MENU_JSON='{
  "size": {"width": 2500, "height": 1686},
  "selected": true,
  "name": "Match Tracker Menu",
  "chatBarText": "Menu",
  "areas": [
    {
      "bounds": {"x": 0, "y": 0, "width": 833, "height": 843},
      "action": {"type": "postback", "data": "action=noop"}
    },
    {
      "bounds": {"x": 833, "y": 0, "width": 833, "height": 843},
      "action": {"type": "postback", "data": "action=check_today_participants"}
    },
    {
      "bounds": {"x": 1666, "y": 0, "width": 834, "height": 843},
      "action": {"type": "postback", "data": "action=check_waitlist_status"}
    },
    {
      "bounds": {"x": 0, "y": 843, "width": 833, "height": 843},
      "action": {"type": "uri", "uri": "https://match-tracker-eight-gilt.vercel.app/matches/results"}
    },
    {
      "bounds": {"x": 833, "y": 843, "width": 833, "height": 843},
      "action": {"type": "uri", "uri": "https://match-tracker-eight-gilt.vercel.app/"}
    },
    {
      "bounds": {"x": 1666, "y": 843, "width": 834, "height": 843},
      "action": {"type": "postback", "data": "action=check_same_day_join"}
    }
  ]
}'

# DB接続情報
DB_HOST="dpg-d6t1e77kijhs73er5ug0-a.oregon-postgres.render.com"
DB_PORT="5432"
DB_NAME="karuta_tracker_b297"
DB_USER="karuta"
DB_PASS="b1FgPgpxsqE83Z1sVoRdes2EdxTAKAal"

# Content-Type判定
CONTENT_TYPE="image/png"
if [[ "$IMAGE_PATH" == *.jpg ]] || [[ "$IMAGE_PATH" == *.jpeg ]]; then
  CONTENT_TYPE="image/jpeg"
fi

echo "=== リッチメニュー一括設定 ==="
echo "画像: $IMAGE_PATH"
echo ""

# PLAYERチャネルのアクセストークンを取得
TOKENS=$(PGPASSWORD="$DB_PASS" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -t -A \
  -c "SELECT channel_name || '|' || channel_access_token FROM line_channels WHERE channel_type = 'PLAYER' AND status != 'DISABLED';")

SUCCESS=0
FAIL=0

while IFS= read -r line; do
  [ -z "$line" ] && continue
  CHANNEL_NAME="${line%%|*}"
  ACCESS_TOKEN="${line#*|}"

  # 1. リッチメニュー作成
  RESULT=$(curl -s -X POST "https://api.line.me/v2/bot/richmenu" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -H "Content-Type: application/json" \
    -d "$RICH_MENU_JSON")

  RICH_MENU_ID=$(echo "$RESULT" | grep -o '"richMenuId":"[^"]*"' | cut -d'"' -f4)

  if [ -z "$RICH_MENU_ID" ]; then
    echo "FAIL: $CHANNEL_NAME (作成失敗: $RESULT)"
    FAIL=$((FAIL + 1))
    continue
  fi

  # 2. 画像アップロード
  UPLOAD_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    "https://api-data.line.me/v2/bot/richmenu/$RICH_MENU_ID/content" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -H "Content-Type: $CONTENT_TYPE" \
    --data-binary "@$IMAGE_PATH")

  if [ "$UPLOAD_STATUS" != "200" ]; then
    echo "FAIL: $CHANNEL_NAME (画像アップロード失敗: $UPLOAD_STATUS)"
    FAIL=$((FAIL + 1))
    continue
  fi

  # 3. デフォルトメニューに設定
  DEFAULT_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    "https://api.line.me/v2/bot/user/all/richmenu/$RICH_MENU_ID" \
    -H "Authorization: Bearer $ACCESS_TOKEN")

  if [ "$DEFAULT_STATUS" != "200" ]; then
    echo "FAIL: $CHANNEL_NAME (デフォルト設定失敗: $DEFAULT_STATUS)"
    FAIL=$((FAIL + 1))
    continue
  fi

  echo "OK: $CHANNEL_NAME"
  SUCCESS=$((SUCCESS + 1))

done <<< "$TOKENS"

echo ""
echo "=== 完了 ==="
echo "成功: $SUCCESS / 失敗: $FAIL"
