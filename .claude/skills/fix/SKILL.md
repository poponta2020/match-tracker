---
name: fix
description: レビュー結果ファイルを読み込み、指摘事項に基づいてコードを修正するスキル。CRITICAL/WARNING/INFOを分類して対応し、修正後に自動で/reviewを再呼び出しする。レビュー指摘の修正時に使用する。
disable-model-invocation: true
user-invocable: true
allowed-tools: Read, Edit, Write, Bash, Grep, Glob, Agent, Skill
argument-hint: [PR番号（任意。省略時は現在のブランチのPRを検出）]
---

# /fix - レビュー指摘に基づく修正依頼

レビュー結果を読み込み、指摘事項に基づいてコードを修正します。
**別ブランチのPRでも、git worktreeを使ってブランチ切り替え不要で修正できます。**

## 手順

1. PR番号を特定する
   - 引数が指定されていればそれを使う: `$ARGUMENTS`
   - なければ `gh pr view --json number -q '.number'` で現在のブランチのPRを検出

2. 最新のレビュー結果ファイルを読み込む
   - `scripts/review/output/review-result-pr{番号}-*.md` のうち、最大番号のファイルを読む
   - 該当ファイルが存在しない場合はエラーメッセージを表示

3. レビュー指摘を分析する
   - CRITICAL / WARNING / INFO に分類された指摘を把握する

4. **作業ディレクトリを決定する**
   - `gh pr view {PR番号} --json headRefName -q '.headRefName'` でPR対象ブランチを取得する
   - **現在のブランチとPR対象ブランチが同じ場合** → 通常通りカレントディレクトリで作業する
   - **異なる場合** → git worktreeを使って一時作業ディレクトリを作成する:
     1. `git fetch origin {ブランチ名}`
     2. `git worktree add /tmp/fix-pr{番号} {ブランチ名}` で一時worktreeを作成
     3. 以降の修正作業はすべて `/tmp/fix-pr{番号}/` 配下で行う

5. 修正を実施する
   - **CRITICAL** の指摘は必ず修正する
   - **WARNING** の指摘は原則修正する。修正しない場合は理由を説明する
   - **INFO** の指摘は任意。対応するかどうか判断する
   - 各指摘の「ファイル」「問題」「修正案」を参考に修正する
   - **worktreeで作業している場合、ファイルパスはworktreeのルートからの相対パスに読み替えること**

6. テストを実行する
   - バックエンドの変更がある場合: `cd {作業ディレクトリ}/karuta-tracker && ./gradlew test`
   - フロントエンドの変更がある場合: `cd {作業ディレクトリ}/karuta-tracker-ui && npm run lint`

7. **worktreeで作業した場合、commit + pushする**
   - 修正ファイルを `git -C /tmp/fix-pr{番号} add` でステージ
   - `git -C /tmp/fix-pr{番号} commit` でコミット（Co-Authored-By を付与）
   - `git -C /tmp/fix-pr{番号} push origin {ブランチ名}` でリモートに反映
   - `git worktree remove /tmp/fix-pr{番号}` でworktreeを削除

8. 修正サマリーを出力する
   ```
   ## 修正サマリー
   ### 対応した指摘
   - [CRITICAL] 指摘タイトル → 修正内容
   ### 対応しなかった指摘（あれば）
   - [INFO] 指摘タイトル → 理由
   ### テスト結果
   - 既存テスト: PASS / FAIL
   ### 作業方式
   - worktree使用: Yes / No
   ```

9. 修正サマリーをユーザーに表示した後、自動で `/review {PR番号}` スキルを呼び出して再レビュープロンプトを生成する
   - worktreeで作業した場合は「commit + push済みです」と伝えてから `/review` を呼び出す
