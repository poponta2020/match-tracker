# /review - クロスレビュー用プロンプト生成

現在のブランチ（またはPR番号 $ARGUMENTS）に対するレビュー依頼プロンプトを生成します。

## 手順

1. PR番号を特定する
   - 引数が指定されていればそれを使う: `$ARGUMENTS`
   - なければ `gh pr view --json number -q '.number'` で現在のブランチのPRを検出

2. PR情報を取得する
   - `gh pr view {PR番号} --json url,title,headRefName` でPR情報を取得

3. 差分を取得する
   - `gh pr diff {PR番号}` でPRの差分を取得

4. `scripts/review/review-template.md` を読み込み、以下のプレースホルダーを置換する:
   - `{{PR_URL}}` → PRのURL
   - `{{PR_TITLE}}` → PRのタイトル
   - `{{BRANCH}}` → ブランチ名
   - `{{DIFF}}` → PR差分

5. 生成結果を `scripts/review/output/review-prompt-pr{番号}.md` に保存する

6. レビュー結果の受け皿ファイルを空で作成する
   - `scripts/review/output/review-result-pr{番号}.md` を空ファイルとして作成する（レビュー担当がここに結果を貼り付ける）

7. ユーザーに以下を案内する:
   - 生成されたプロンプトファイルのパス
   - 「このファイルの内容をレビュー担当AI（Codex or Claude Code）に貼り付けてください」
   - 「レビュー結果は `scripts/review/output/review-result-pr{番号}.md` に保存してください」
   - 「保存後、`/fix` でこのAIに修正依頼できます」
