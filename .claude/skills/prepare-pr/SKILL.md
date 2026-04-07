---
name: prepare-pr
description: 実装完了後にpush・PR作成を行い、/reviewスキルによるレビューへつなげるスキル。/implement、/quickfix、/bug-report の後に使用する。
user-invocable: true
disable-model-invocation: true
allowed-tools:
  - Bash
  - Read
  - Glob
  - Grep
---

# /prepare-pr - 実装完了後のPR作成

`/implement`、`/quickfix`、`/bug-report` で実装・コミット済みの変更をpushし、PRを作成する。
完了後は `/review` でクロスレビューに進む。

**前提**: 各スキルがfeatureブランチ作成・コミットまで完了していること。

**ユーザーとの対話はすべて日本語で行うこと。**

## Step 1: 現在の状態を収集

以下のコマンドを**並列で**実行する:

```bash
git status --short
git branch --show-current
git log --oneline -10
git rev-list --count origin/main..HEAD 2>/dev/null || echo "0"
gh pr view --json number,url 2>/dev/null || echo "NO_PR"
```

## Step 2: 状態を分類し、異常を検出

収集した情報をもとに、以下のいずれかに分類する:

| 状態 | 条件 | 実行するステップ |
|------|------|-----------------|
| A. featureブランチ上、未push | featureブランチ上でリモートより先行 | push → PR作成 |
| B. push済み、PRなし | リモートと同期済み、PRが存在しない | PR作成のみ |
| C. PR作成済み | `gh pr view` が成功 | PR URLを表示して終了 |

### 異常状態の検出（以下の場合はユーザーに警告して確認を取る）

- **mainブランチ上にコミットがある場合**: 「mainブランチ上に未pushのコミットがあります。featureブランチに移しますか？」と確認し、承認を得たらfeatureブランチを作成してmainを巻き戻す
- **未コミットの変更がある場合**: 「未コミットの変更があります。先にコミットしてから再実行してください。」と伝える
- **変更なし**: 「コミットもPRもありません。先に実装スキルを実行してください。」と伝える

## Step 3: Push

```bash
git push -u origin <ブランチ名>
```

## Step 4: PR作成

1. `git log --oneline origin/main..HEAD` でコミット内容を確認する
2. コミット内容からPRタイトルと本文を生成する

```bash
gh pr create --base main --title "<タイトル>" --body "$(cat <<'EOF'
## Summary
<変更内容の箇条書き>

## Test plan
- [ ] 動作確認

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

## Step 5: 完了報告と次ステップの案内

以下を表示する:
- 作成されたPRのURL
- 実行されたステップの一覧（push / PR作成）
- **「`/review` でレビュープロンプトを生成し、クロスレビューに進められます。」** と案内する
