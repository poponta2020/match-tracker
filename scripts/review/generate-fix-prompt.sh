#!/bin/bash
# generate-fix-prompt.sh
# レビュー結果から修正依頼プロンプトを生成する
#
# Usage:
#   ./scripts/review/generate-fix-prompt.sh [PR番号]
#   レビュー結果は scripts/review/output/review-result-pr{番号}.md に保存されている前提

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TEMPLATE="$SCRIPT_DIR/fix-template.md"
OUTPUT_DIR="$SCRIPT_DIR/output"

# PR番号の取得
if [ -n "$1" ]; then
    PR_NUMBER="$1"
else
    PR_NUMBER=$(gh pr view --json number -q '.number' 2>/dev/null || echo "")
    if [ -z "$PR_NUMBER" ]; then
        echo "ERROR: PR番号を引数で指定してください。"
        echo "Usage: $0 [PR番号]"
        exit 1
    fi
fi

REVIEW_RESULT_FILE="$OUTPUT_DIR/review-result-pr${PR_NUMBER}.md"

if [ ! -f "$REVIEW_RESULT_FILE" ]; then
    echo "ERROR: レビュー結果ファイルが見つかりません: $REVIEW_RESULT_FILE"
    echo ""
    echo "レビュー結果を以下のファイルに保存してから再実行してください:"
    echo "  $REVIEW_RESULT_FILE"
    exit 1
fi

echo "PR #${PR_NUMBER} の修正依頼プロンプトを生成中..."

# PR情報の取得
PR_URL=$(gh pr view "$PR_NUMBER" --json url -q '.url')
BRANCH=$(gh pr view "$PR_NUMBER" --json headRefName -q '.headRefName')

# レビュー結果の読み込み
REVIEW_RESULT=$(cat "$REVIEW_RESULT_FILE")

# テンプレートの読み込みと置換
OUTPUT="$OUTPUT_DIR/fix-prompt-pr${PR_NUMBER}.md"

sed \
    -e "s|{{PR_URL}}|${PR_URL}|g" \
    -e "s|{{BRANCH}}|${BRANCH}|g" \
    "$TEMPLATE" > "$OUTPUT.tmp"

# {{REVIEW_RESULT}} の置換
awk -v result="$REVIEW_RESULT" '{
    if ($0 ~ /\{\{REVIEW_RESULT\}\}/) {
        print result
    } else {
        print $0
    }
}' "$OUTPUT.tmp" > "$OUTPUT"

rm -f "$OUTPUT.tmp"

echo ""
echo "=== 修正依頼プロンプト生成完了 ==="
echo "出力先: $OUTPUT"
echo ""
echo "次のステップ:"
echo "  1. $OUTPUT の内容を実装担当AI（Claude Code or Codex）に貼り付けてください"
echo "  2. 修正が完了したら、再度PRにpushしてください"
echo "  3. 再レビューが必要な場合: ./scripts/review/generate-review-prompt.sh ${PR_NUMBER}"
