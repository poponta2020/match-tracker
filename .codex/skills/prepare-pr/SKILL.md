---
name: prepare-pr
description: 実装・コミット・push済みのブランチからPRを作成し、review-prompt生成へつなげるスキル。PR作成準備、"$prepare-pr <ブランチ名>"、"/prepare-pr <ブランチ名>" の依頼で使用する。
---

# Prepare PR

`implement`、`quickfix`、`bug-report` などで実装・コミット・push済みのブランチからPRを作成し、完了後に `review-prompt` 相当の手順でクロスレビュー用プロンプトを生成する。

**ユーザーとの対話はすべて日本語で行うこと。**

## 基本方針

- このスキルは原則として実装ファイルを編集しない。
- PR作成という外部副作用があるため、PRタイトル・本文・対象ブランチを提示してからユーザーの明示確認を取る。
- メイン作業ツリーのブランチを切り替えない。必要な確認は `git -C <worktree>` またはブランチ名指定で行う。
- レビュープロンプト生成時の `scripts/review/output/` はメイン作業ツリー側に作成してよい（gitignore対象のレビュー連携ファイルのみ）。

## Step 0: 対象ブランチの特定

### ブランチ名が指定されている場合

ユーザーの依頼で指定されたブランチ名を使う。

### ブランチ名が指定されていない場合

以下を確認し、PR作成候補を一覧表示してユーザーに選択してもらう。

```powershell
git worktree list
git branch -r --no-merged origin/main
```

候補の例:

```text
PR作成候補:
1. feature/lottery-priority-players - worktree: C:\tmp\impl-lottery-priority-players
2. fix/cancel-button-not-responding - worktree: C:\tmp\fix-cancel-button-not-responding
どのブランチでPRを作成しますか？
```

候補がない場合は、「PR作成候補のブランチが見つかりません。先に `$implement` または `$quickfix` で実装・pushしてください。」と伝える。

## Step 1: 状態の確認

対象ブランチについて、以下を確認する。

```powershell
git fetch origin main
git log --oneline origin/main..origin/<ブランチ名>
gh pr list --head <ブランチ名> --json number,url,title,state
git branch -r --list "origin/<ブランチ名>"
```

収集した情報をもとに分類する。

| 状態 | 条件 | 対応 |
|------|------|------|
| push済み、PRなし | リモートにブランチがありPRが存在しない | PR作成へ進む |
| PR作成済み | `gh pr list` でPRが見つかる | PR URLを表示し、review-prompt生成へ進むか確認 |
| ブランチがリモートにない | `origin/<ブランチ名>` が存在しない | エラー。先にpushが必要 |
| mainとの差分なし | `git log origin/main..origin/<ブランチ名>` が空 | エラー。PR化する差分がない |

異常状態があれば、PR作成せずにユーザーへ報告する。

## Step 2: PRタイトルと本文を作成する

以下を読み、PRタイトルと本文案を作る。

- `git log --oneline origin/main..origin/<ブランチ名>`
- 必要に応じて `git diff --stat origin/main...origin/<ブランチ名>`
- 関連する `docs/features/<機能名>/requirements.md`
- 関連する `docs/features/<機能名>/implementation-plan.md`
- 改修の場合は `fix-requirements.md` / `fix-implementation-plan.md`

PR本文は以下の形式にする。

```markdown
## Summary
- <変更内容の箇条書き>

## Related Issues
- <関連IssueがあればURLまたは番号>

## Test plan
- [x] <実行済みテスト>
- [ ] 動作確認
```

テスト実行状況が分からない場合は、`Test plan` に「未確認」と書かず、まずユーザーに確認するか、実装ログ・コミット・実装手順書から実施済みテストを確認する。

## Step 3: PR作成前確認

PR作成前に、ユーザーへ以下を提示して確認する。

```markdown
## PR作成確認

**対象ブランチ:** `<ブランチ名>`
**base:** `main`
**PRタイトル案:** `<タイトル>`

**PR本文案:**
<本文>

この内容でPRを作成してよいですか？
```

ユーザーの明示承認を得るまで `gh pr create` は実行しない。

## Step 4: PR作成

承認後、PRを作成する。

```powershell
gh pr create --base main --head <ブランチ名> --title "<タイトル>" --body "<PR本文>"
```

作成後、PR番号とURLを取得する。

```powershell
gh pr view --head <ブランチ名> --json number,url,title
```

すでにPRが存在していた場合は、PR作成は行わず、既存PRの番号とURLを使う。

## Step 5: レビュープロンプト生成

PR作成後または既存PR確認後、`review-prompt` スキル相当の手順でレビュープロンプトを生成する。
レビュー連携ファイルを見つけやすくするため、原則としてメイン作業ツリー（例: `c:\Users\user\match-tracker`）側の `scripts/review/output/` に生成する。

```powershell
cd "c:\Users\user\match-tracker"
.\scripts\review\generate-review-prompt.sh <PR番号>
```

Windowsで `.sh` が直接実行できない場合は、Git Bash等で実行するか、同等の処理を手作業で行う。

生成後、以下を確認する。

- `scripts/review/output/review-prompt-pr{番号}-{回数}.md`
- `scripts/review/output/review-result-pr{番号}-{回数}.md`

## Step 6: 完了報告

ユーザーに以下を報告する。

- PR URL
- PR番号
- 対象ブランチ
- 生成されたレビュープロンプトファイルのパス
- レビュー結果の書き込み先ファイルのパス
- 次の手順: `$review <PR番号>` でCodexレビュー、または生成プロンプトを別AIに渡す
