#!/bin/bash
# ship-worktree.sh
# worktree内の変更をコミット＆push＆PRマージ＆worktree削除する
#
# Usage:
#   ./scripts/review/ship-worktree.sh <PR番号> [コミットメッセージ]
#
# コミットメッセージを省略した場合、PRタイトルから自動生成する

set -e

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORKTREE_BASE="$REPO_ROOT/worktrees"

# PR番号の取得
if [ -z "$1" ]; then
    echo "ERROR: PR番号を引数で指定してください。"
    echo "Usage: $0 <PR番号> [コミットメッセージ]"
    exit 1
fi

PR_NUMBER="$1"
COMMIT_MSG="$2"
WORKTREE_DIR="$WORKTREE_BASE/pr-${PR_NUMBER}"

# worktreeの存在確認
if [ ! -d "$WORKTREE_DIR" ]; then
    echo "ERROR: worktree が見つかりません: $WORKTREE_DIR"
    echo "先に setup-worktree.sh ${PR_NUMBER} を実行してください。"
    exit 1
fi

# PR情報の取得
PR_TITLE=$(gh pr view "$PR_NUMBER" --json title -q '.title' 2>/dev/null || echo "")
PR_URL=$(gh pr view "$PR_NUMBER" --json url -q '.url' 2>/dev/null || echo "")

# worktree内で変更を確認
cd "$WORKTREE_DIR"

if [ -z "$(git status --porcelain)" ]; then
    echo "INFO: コミットする変更がありません。pushとマージのみ実行します。"
else
    # コミットメッセージの生成
    if [ -z "$COMMIT_MSG" ]; then
        COMMIT_MSG="fix: レビュー指摘対応 (PR #${PR_NUMBER})"
    fi

    # 変更をステージ（review output と .claude 設定は除外）
    git add -A
    git reset -- scripts/review/output/ .claude/settings.json .claude/settings.local.json 2>/dev/null || true

    # ステージされた変更があるか再確認
    if [ -z "$(git diff --cached --name-only)" ]; then
        echo "INFO: ステージされた変更がありません。pushとマージのみ実行します。"
    else
        echo "コミット中..."
        git commit -m "${COMMIT_MSG}

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
        echo "コミット完了。"
    fi
fi

# push
echo "push 中..."
git push

# マージ
echo "PR #${PR_NUMBER} をマージ中..."
gh pr merge "$PR_NUMBER" --merge

echo ""
echo "=== ship 完了 ==="
echo "PR: ${PR_URL}"
echo ""

# worktreeの削除
cd "$REPO_ROOT"
echo "worktree を削除中..."
git worktree remove "$WORKTREE_DIR"
echo "worktree 削除完了: $WORKTREE_DIR"

# mainに切り替えて最新を取得
echo "main ブランチに切り替え中..."
git checkout main
git pull
echo ""
echo "完了しました。"
