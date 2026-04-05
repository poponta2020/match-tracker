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

echo "PR #${PR_NUMBER} のレビュープロンプトを生成中..."

# PR情報の取得
PR_URL=$(gh pr view "$PR_NUMBER" --json url -q '.url')
PR_TITLE=$(gh pr view "$PR_NUMBER" --json title -q '.title')
BRANCH=$(gh pr view "$PR_NUMBER" --json headRefName -q '.headRefName')

# 差分の取得
DIFF=$(gh pr diff "$PR_NUMBER")

# テンプレートの読み込みと置換
OUTPUT="$OUTPUT_DIR/review-prompt-pr${PR_NUMBER}.md"

sed \
    -e "s|{{PR_URL}}|${PR_URL}|g" \
    -e "s|{{PR_TITLE}}|${PR_TITLE}|g" \
    -e "s|{{BRANCH}}|${BRANCH}|g" \
    "$TEMPLATE" > "$OUTPUT.tmp"

# {{DIFF}} の置換（差分は複数行なのでsedでは難しいためawkを使用）
awk -v diff="$DIFF" '{
    if ($0 ~ /\{\{DIFF\}\}/) {
        print diff
    } else {
        print $0
    }
}' "$OUTPUT.tmp" > "$OUTPUT"

rm -f "$OUTPUT.tmp"

echo ""
echo "=== レビュープロンプト生成完了 ==="
echo "出力先: $OUTPUT"
echo ""
echo "次のステップ:"
echo "  1. $OUTPUT の内容をレビュー担当AI（Codex or Claude Code）に貼り付けてください"
echo "  2. レビュー結果を scripts/review/output/review-result-pr${PR_NUMBER}.md に保存してください"
echo "  3. ./scripts/review/generate-fix-prompt.sh ${PR_NUMBER} で修正依頼プロンプトを生成してください"
