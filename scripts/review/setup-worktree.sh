#!/bin/bash
# setup-worktree.sh
# PR番号に対応するgit worktreeを作成する
#
# Usage:
#   ./scripts/review/setup-worktree.sh <PR番号>
#
# 作成先: リポジトリルート/worktrees/pr-{番号}/

set -e

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORKTREE_BASE="$REPO_ROOT/worktrees"

# PR番号の取得
if [ -z "$1" ]; then
    echo "ERROR: PR番号を引数で指定してください。"
    echo "Usage: $0 <PR番号>"
    exit 1
fi

PR_NUMBER="$1"
WORKTREE_DIR="$WORKTREE_BASE/pr-${PR_NUMBER}"

# PRのブランチ名を取得
BRANCH=$(gh pr view "$PR_NUMBER" --json headRefName -q '.headRefName' 2>/dev/null || echo "")
if [ -z "$BRANCH" ]; then
    echo "ERROR: PR #${PR_NUMBER} が見つかりません。"
    exit 1
fi

# 既にworktreeが存在する場合
if [ -d "$WORKTREE_DIR" ]; then
    echo "INFO: worktree は既に存在します: $WORKTREE_DIR"
    echo "ブランチ: $BRANCH"
    exit 0
fi

# worktreeディレクトリの親を作成
mkdir -p "$WORKTREE_BASE"

# リモートの最新を取得
echo "リモートの最新を取得中..."
git fetch origin "$BRANCH"

# worktreeを作成
echo "worktree を作成中: $WORKTREE_DIR (ブランチ: $BRANCH)"
git worktree add "$WORKTREE_DIR" "$BRANCH"

echo ""
echo "=== worktree 作成完了 ==="
echo "パス: $WORKTREE_DIR"
echo "ブランチ: $BRANCH"
echo "PR: #${PR_NUMBER}"
echo ""
echo "次のステップ:"
echo "  Codex の場合: このディレクトリで作業してください"
echo "  Claude Code の場合: /fix ${PR_NUMBER} を実行してください"
