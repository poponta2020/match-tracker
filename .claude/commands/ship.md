# /ship - コミット＆push＆マージ

レビュー完了後の変更をコミットしてpushし、PRをマージする。
**PR番号を指定すれば、ブランチ切り替え不要で操作できます。**

## 手順

1. PR番号を特定する
   - 引数が指定されていればそれを使う: `$ARGUMENTS`
   - なければ `gh pr view --json number -q '.number'` で現在のブランチのPRを検出

2. PR情報を取得する
   - `gh pr view {PR番号} --json number,url,title,headRefName` でPR情報を取得
   - **headRefName** からブランチ名を取得する

3. 作業方式を判定する
   - **現在のブランチとPR対象ブランチが同じ場合** → カレントディレクトリで作業
   - **異なる場合** → リモートブランチベースで操作（後述）

4. 変更の確認とコミット

   **【現在のブランチで作業する場合】**
   - `git status` で未ステージの変更を確認
   - `git diff HEAD` で差分を確認
   - 変更がなければスキップしてpush + mergeへ進む
   - `.claude/settings.json`、`.claude/settings.local.json`、`scripts/review/output/` はコミット対象外
   - PR関連の実装ファイル・テストファイル・SQLファイルのみをステージ
   - `git log --oneline -5` でコミットスタイルを確認
   - prefix: fix / feat / refactor / test / docs / chore から適切なものを選択
   - `git add` → `git commit`（Co-Authored-By を付与）→ `git push`

   **【別ブランチの場合】**
   - ローカルに未コミットの変更はないはずなので（`/fix`がworktreeでcommit+push済み）、コミットステップはスキップ
   - 未pushのコミットがある場合: `git push origin {ブランチ名}`

5. PRをマージする
   - `gh pr merge {PR番号} --merge` でマージを実行
   - マージ後、ローカルの `main` を更新する: `git fetch origin main && git merge origin/main --ff-only` （mainにいる場合のみ）

6. レビュー関連資料を削除する
   - `scripts/review/output/review-prompt-pr{番号}-*.md` を削除する
   - `scripts/review/output/review-result-pr{番号}-*.md` を削除する

7. 完了したらPR URLとマージ結果を報告する
