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

# 最新のレビュー結果ファイルを検出（最大番号のもの）
REVIEW_RESULT_FILE=$(ls "$OUTPUT_DIR"/review-result-pr${PR_NUMBER}-*.md 2>/dev/null | sort -t'-' -k4 -n | tail -1)

if [ -z "$REVIEW_RESULT_FILE" ]; then
    echo "ERROR: レビュー結果ファイルが見つかりません"
    echo "  検索パターン: $OUTPUT_DIR/review-result-pr${PR_NUMBER}-*.md"
    echo ""
    echo "先に /review ${PR_NUMBER} を実行してレビューを受けてください。"
    exit 1
fi

# レビュー結果が空でないか確認
if [ ! -s "$REVIEW_RESULT_FILE" ]; then
    echo "ERROR: レビュー結果ファイルが空です: $REVIEW_RESULT_FILE"
    echo ""
    echo "レビュー担当AIがこのファイルに結果を書き込んでから再実行してください。"
    exit 1
fi

# レビュー回数をファイル名から取得
REVIEW_ROUND=$(echo "$REVIEW_RESULT_FILE" | grep -oP 'review-result-pr\d+-\K\d+')

echo "PR #${PR_NUMBER} の修正依頼プロンプトを生成中...（レビュー${REVIEW_ROUND}回目の指摘対応）"

# PR情報の取得
PR_URL=$(gh pr view "$PR_NUMBER" --json url -q '.url')
BRANCH=$(gh pr view "$PR_NUMBER" --json headRefName -q '.headRefName')

# レビュー結果をファイルに保存（変数経由だと特殊文字が破損するため）
RESULT_TMP=$(mktemp)
trap 'rm -f "$OUTPUT.tmp" "$RESULT_TMP"' EXIT
cp "$REVIEW_RESULT_FILE" "$RESULT_TMP"

# テンプレートの読み込みと置換
OUTPUT="$OUTPUT_DIR/fix-prompt-pr${PR_NUMBER}-${REVIEW_ROUND}.md"

sed \
    -e "s|{{PR_URL}}|${PR_URL}|g" \
    -e "s|{{BRANCH}}|${BRANCH}|g" \
    "$TEMPLATE" > "$OUTPUT.tmp"

# {{REVIEW_RESULT}} の置換（ファイルから読み込み、特殊文字を安全に埋め込む）
awk -v resultfile="$RESULT_TMP" '{
    if ($0 ~ /\{\{REVIEW_RESULT\}\}/) {
        while ((getline line < resultfile) > 0) print line
        close(resultfile)
    } else {
        print $0
    }
}' "$OUTPUT.tmp" > "$OUTPUT"

rm -f "$OUTPUT.tmp" "$RESULT_TMP"

echo ""
echo "=== 修正依頼プロンプト生成完了 ==="
echo "出力先: $OUTPUT"
echo "レビュー結果: $REVIEW_RESULT_FILE"
echo ""
echo "次のステップ:"
echo "  1. $OUTPUT の内容を実装担当AI（Claude Code or Codex）に貼り付けてください"
echo "  2. または /fix ${PR_NUMBER} で直接修正できます"
