---
name: ship
description: レビュー完了後にPRをマージし、リモート/ローカルブランチ、worktree、レビュー関連ファイル、必要に応じて親Issueを整理するスキル。PR出荷、"$ship <PR番号>"、"/ship <PR番号>" の依頼で使用する。
---

# Ship

レビュー完了後のPRをマージし、ブランチ削除、worktreeクリーンアップ、レビュー関連ファイル削除、必要に応じた親Issueクローズまで行う。
PR番号を指定すれば、ブランチ切り替えなしで操作できる。

**ユーザーとの対話はすべて日本語で行うこと。**

## 基本方針

- このスキルはPRのマージと削除系操作を含むため、各破壊的操作の前にユーザーの明示確認を取る。
- メイン作業ツリーのブランチを勝手に切り替えない。必要な確認はPR番号、ブランチ名、`git -C <worktree>` を使う。
- 未コミット変更があるworktreeは削除しない。
- `git worktree remove` やローカルブランチ削除の前に、対象パスと対象ブランチを必ず表示して確認する。
- レビュー関連ファイル削除は `scripts/review/output/review-prompt-pr<番号>-*.md` と `scripts/review/output/review-result-pr<番号>-*.md` のみに限定する。
- DBマイグレーションSQLがPRに含まれる場合は、マージ前に本番DB適用要否をユーザーに明示する。

## Step 0: PR番号を特定する

ユーザーの依頼にPR番号が含まれていればそれを使う。
PR番号がなければ、`gh pr view --json number -q '.number'` で現在のブランチのPRを検出する。
PR番号を特定できない場合は、PR番号をユーザーに確認する。

## Step 1: PR情報を取得する

PR情報を取得する。

```powershell
gh pr view <PR番号> --json number,url,title,state,mergeable,isDraft,headRefName,baseRefName,body,commits,statusCheckRollup
```

確認する項目:

- PRが `OPEN` であること
- Draftではないこと
- base branch が想定通り（通常 `main`）であること
- head branch が `main` ではないこと
- mergeable 状態
- CI/check の結果
- PR本文やコミットに関連Issueがあるか
- `database/*.sql` の変更が含まれているか

差分概要も確認する。

```powershell
gh pr diff <PR番号> --name-only
```

## Step 2: レビュー完了状態を確認する

以下を確認する。

- 最新の `scripts/review/output/review-result-pr<番号>-*.md` が存在するか
- 最新レビュー結果が `APPROVE` 相当か
- 未対応の CRITICAL / WARNING が残っていないか
- GitHub上のレビュー状態に未解決の requested changes がないか

必要に応じて:

```powershell
gh pr reviews <PR番号>
gh pr checks <PR番号>
```

レビュー未完了または未対応指摘がある場合は、マージせずユーザーに報告する。

## Step 3: 作業worktreeと未コミット差分を確認する

PRのheadブランチに対応するworktreeを確認する。

```powershell
git worktree list
```

対応worktreeがある場合:

```powershell
git -C "<worktreeパス>" status --short
git -C "<worktreeパス>" log --oneline -5
```

未コミット変更がある場合:

- 変更内容をユーザーに報告する。
- 勝手にコミット・破棄・削除しない。
- `ship` を中断し、先に `$fix` などで整理するよう案内する。

対応worktreeがない場合は、ローカル作業なしとして扱う。

## Step 4: マージ前確認

マージ前に、ユーザーへ以下を提示して確認する。

```markdown
## Ship確認

**PR:** #<PR番号> <PRタイトル>
**URL:** <PR URL>
**branch:** `<headRefName>` → `<baseRefName>`
**状態:** OPEN / Draftではない / mergeable
**CI:** PASS / FAIL / 未確認
**レビュー:** APPROVE / 未確認 / 未対応指摘あり
**DBマイグレーション:** あり / なし
**削除予定:**
- リモートブランチ: `<headRefName>`
- ローカルworktree: `<worktreeパス>`（存在する場合）
- レビュー関連ファイル: `scripts/review/output/review-*pr<番号>-*.md`
**Issueクローズ候補:** <Issue番号またはなし>

このPRをマージして出荷してよいですか？
```

ユーザーの明示承認を得るまでマージしない。

## Step 5: PRをマージする

承認後、PRをマージする。

```powershell
gh pr merge <PR番号> --merge --delete-branch
```

`--delete-branch` により、GitHub上のリモートブランチも削除される。
マージ方式を変える必要がある場合（squash/rebaseなど）は、実行前にユーザーに確認する。

マージ後、メイン作業ツリーが `main` にいる場合のみ、ローカルmainを更新してよいか確認したうえで更新する。

```powershell
git fetch origin main
git merge origin/main --ff-only
```

メイン作業ツリーが `main` 以外の場合は、勝手に切り替えず、更新をスキップして報告する。

## Step 6: ローカルブランチとworktreeを整理する

PRのheadブランチが `main` でないことを再確認する。

```powershell
git worktree list
git branch --list "<headRefName>"
```

対応worktreeがある場合、削除前に以下を確認する。

- worktreeパスが明確である
- worktreeパスが `C:\tmp\` 配下、またはユーザーが明示した作業用worktreeパスである
- `git -C "<worktreeパス>" status --short` が空である
- 対象ブランチがPRの `headRefName` である

削除前にユーザーへ確認する。

```markdown
## Worktree削除確認

以下のworktreeを削除してよいですか？

- path: `<worktreeパス>`
- branch: `<headRefName>`
- status: clean
```

承認後に削除する。

```powershell
git worktree remove "<worktreeパス>"
```

ローカルブランチが残っている場合は、削除前にユーザーへ確認する。

```powershell
git branch -d "<headRefName>"
```

削除できない場合は、エラー内容を報告する。`git branch -D` はユーザーが明示的に承認した場合のみ使用する。

## Step 7: 親Issueをクローズする

PR本文やコミットメッセージから関連Issue番号を確認する。
特に `[Feature]` や `[Fix]` プレフィックスの親Issueがある場合は、クローズ候補として扱う。

```powershell
gh issue view <Issue番号> --json number,title,state,url
```

未クローズであれば、クローズ前にユーザーへ確認する。

```markdown
## Issueクローズ確認

以下のIssueをクローズしてよいですか？

- #<Issue番号> <タイトル>
- <URL>
```

承認後にクローズする。

```powershell
gh issue close <Issue番号>
```

該当するIssueがなければスキップする。

## Step 8: レビュー関連ファイルを削除する

削除対象を限定して確認する。

```powershell
Get-ChildItem scripts\review\output -Filter "review-prompt-pr<PR番号>-*.md"
Get-ChildItem scripts\review\output -Filter "review-result-pr<PR番号>-*.md"
```

削除前にユーザーへ確認する。

```markdown
## レビュー関連ファイル削除確認

以下のファイルを削除してよいですか？

- `scripts/review/output/review-prompt-pr<PR番号>-*.md`
- `scripts/review/output/review-result-pr<PR番号>-*.md`
```

承認後、対象ファイルだけを削除する。

```powershell
Remove-Item -LiteralPath <対象ファイルパス>
```

ワイルドカードを直接 `Remove-Item` に渡さず、削除対象を列挙してから `-LiteralPath` で削除する。

## Step 9: 完了報告

最後に以下を報告する。

- マージしたPR URL
- マージ結果
- 削除したリモートブランチ
- 削除したローカルworktree / ローカルブランチ
- クローズしたIssue
- 削除したレビュー関連ファイル
- ローカルmain更新の有無
