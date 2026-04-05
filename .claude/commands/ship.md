# /ship - コミット＆push＆マージ

レビュー完了後の変更をコミットしてpushし、PRをマージする。

## 手順

1. 変更の確認
   - `git status` で未ステージの変更を確認
   - `git diff HEAD` で差分を確認
   - 変更がなければ「コミットする変更がありません」と伝えて終了

2. コミット対象の判断
   - `.claude/settings.json`、`.claude/settings.local.json`、`scripts/review/output/` はコミット対象外
   - PR関連の実装ファイル・テストファイル・SQLファイルのみをステージする

3. コミットメッセージの作成
   - `git log --oneline -5` で直近のコミットスタイルを確認
   - 変更内容を分析して適切なコミットメッセージを作成
   - prefix: fix / feat / refactor / test / docs / chore から適切なものを選択

4. コミット＆push
   - 対象ファイルを `git add` でステージ
   - `git commit` でコミット（Co-Authored-By を付与）
   - `git push` でリモートに反映

5. PRをマージする
   - `gh pr view --json number,url,title` でPR情報を取得
   - `gh pr merge {番号} --merge` でマージを実行
   - マージ後、ローカルを `main` に切り替えて `git pull` する
   - 完了したらPR URLとマージ結果を報告する
