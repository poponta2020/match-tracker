#!/bin/bash
# generate-review-prompt.sh
# PRの差分からレビュー依頼プロンプトを生成する
#
# Usage:
#   ./scripts/review/generate-review-prompt.sh [PR番号]
#   PR番号を省略すると現在のブランチのPRを自動検出

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TEMPLATE="$SCRIPT_DIR/review-template.md"
OUTPUT_DIR="$SCRIPT_DIR/output"
mkdir -p "$OUTPUT_DIR"

# PR番号の取得
if [ -n "$1" ]; then
    PR_NUMBER="$1"
else
    PR_NUMBER=$(gh pr view --json number -q '.number' 2>/dev/null || echo "")
    if [ -z "$PR_NUMBER" ]; then
        echo "ERROR: 現在のブランチにPRが見つかりません。PR番号を引数で指定してください。"
        echo "Usage: $0 [PR番号]"
        exit 1
    fi
fi

# レビュー回数を決定（既存のレビュープロンプトファイル数 + 1）
REVIEW_ROUND=$(ls "$OUTPUT_DIR"/review-prompt-pr${PR_NUMBER}-*.md 2>/dev/null | wc -l | tr -d ' ')
REVIEW_ROUND=$((REVIEW_ROUND + 1))

echo "PR #${PR_NUMBER} のレビュープロンプトを生成中...（${REVIEW_ROUND}回目）"

# PR情報の取得
PR_URL=$(gh pr view "$PR_NUMBER" --json url -q '.url')
PR_TITLE=$(gh pr view "$PR_NUMBER" --json title -q '.title')
BRANCH=$(gh pr view "$PR_NUMBER" --json headRefName -q '.headRefName')
BASE_BRANCH=$(gh pr view "$PR_NUMBER" --json baseRefName -q '.baseRefName')

# ファイル名にレビュー回数を含める
OUTPUT="$OUTPUT_DIR/review-prompt-pr${PR_NUMBER}-${REVIEW_ROUND}.md"
RESULT_FILE="scripts/review/output/review-result-pr${PR_NUMBER}-${REVIEW_ROUND}.md"

# 差分をファイルに保存（変数経由だと特殊文字が破損するため）
DIFF_FILE=$(mktemp)
trap 'rm -f "$OUTPUT.tmp" "$DIFF_FILE"' EXIT
gh pr diff "$PR_NUMBER" > "$DIFF_FILE"

sed \
    -e "s|{{PR_URL}}|${PR_URL}|g" \
    -e "s|{{PR_TITLE}}|${PR_TITLE}|g" \
    -e "s|{{PR_NUMBER}}|${PR_NUMBER}|g" \
    -e "s|{{REVIEW_ROUND}}|${REVIEW_ROUND}|g" \
    -e "s|{{BRANCH}}|${BRANCH}|g" \
    -e "s|{{BASE_BRANCH}}|${BASE_BRANCH}|g" \
    -e "s|{{RESULT_FILE}}|${RESULT_FILE}|g" \
    "$TEMPLATE" > "$OUTPUT.tmp"

# {{DIFF}} の置換（差分をファイルから読み込み、特殊文字を安全に埋め込む）
awk -v difffile="$DIFF_FILE" '{
    if ($0 ~ /\{\{DIFF\}\}/) {
        while ((getline line < difffile) > 0) print line
        close(difffile)
    } else {
        print $0
    }
}' "$OUTPUT.tmp" > "$OUTPUT"

rm -f "$OUTPUT.tmp" "$DIFF_FILE"

# レビュー結果の受け皿ファイルを空で作成
touch "$OUTPUT_DIR/review-result-pr${PR_NUMBER}-${REVIEW_ROUND}.md"

echo ""
echo "=== レビュープロンプト生成完了 ==="
echo "出力先: $OUTPUT"
echo "レビュー回数: ${REVIEW_ROUND}回目"
echo ""
echo "次のステップ:"
echo "  1. $OUTPUT の内容をレビュー担当AI（Codex or Claude Code）に貼り付けてください"
echo "  2. レビュー結果が ${RESULT_FILE} に書き込まれます"
echo "  3. /fix ${PR_NUMBER} で修正依頼できます"
