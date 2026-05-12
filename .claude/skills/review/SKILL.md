---
name: review
description: クロスレビュー自動ループの起点となるスキル。PR差分から Codex CLI で評価 → 結果ファイル（CRITICAL/WARNING/INFO形式）を得る → 指摘ありなら /fix を、指摘なしなら /ship を自動連鎖呼び出しする。レビューを依頼したいとき、/reviewで使用する。
user-invocable: true
allowed-tools: Bash, Read, Write, Glob, Grep, Skill
argument-hint: [PR番号（任意。省略時は現在のブランチのPRを検出）]
---

# /review - クロスレビュー用プロンプト生成

現在のブランチ（またはPR番号 $ARGUMENTS）に対するレビュー依頼プロンプトを生成します。
**別ブランチのPRでも、ブランチ切り替え不要で実行できます。**

## 手順

### 0. 作業ブランチの確認

スキル実行前に、メインの作業ディレクトリが `main` ブランチであることを確認する。

```bash
git branch --show-current
```

- **`main` の場合** → 次のステップへ進む
- **`main` 以外の場合** → 自動で `main` に切り替える:

```bash
git checkout main
```

切り替え後、ユーザーに「`main` ブランチに切り替えました」と通知して続行する。

**例外:** このスキルが `/implement`、`/quickfix`、`/bug-report`、`/fix` から自動呼び出しされた場合、引数にPR番号が渡されているため、ブランチ確認は不要（スキップしてよい）。

1. PR番号を特定する
   - 引数が指定されていればそれを使う: `$ARGUMENTS`
   - なければ `gh pr view --json number -q '.number'` で現在のブランチのPRを検出

2. レビュー回数を決定する
   - `scripts/review/output/` 内の `review-prompt-pr{番号}-*.md` ファイル数をカウントする
   - 次のレビュー回数 = カウント + 1

3. PR情報を取得する
   - `gh pr view {PR番号} --json url,title,headRefName,baseRefName` でPR情報を取得
   - **headRefName** からブランチ名を取得する（現在のブランチに依存しない）

4. 差分を取得する
   - PR情報から取得した**ブランチ名**を使い、`git diff main...{ブランチ名}` で差分を取得する
   - ※ 現在のブランチとPR対象ブランチが同じ場合も、この方法で統一する
   - ローカルにブランチがない場合は `git fetch origin {ブランチ名}` してから `git diff main...origin/{ブランチ名}` を使う

5. `scripts/review/review-template.md` を読み込み、以下のプレースホルダーを置換する:
   - `{{PR_URL}}` → PRのURL
   - `{{PR_TITLE}}` → PRのタイトル
   - `{{PR_NUMBER}}` → PR番号（数字のみ）
   - `{{REVIEW_ROUND}}` → レビュー回数（数字のみ）
   - `{{BRANCH}}` → ブランチ名
   - `{{DIFF}}` → PR差分
   - `{{BASE_BRANCH}}` → ベースブランチ名（`baseRefName`から取得）
   - `{{RESULT_FILE}}` → レビュー結果ファイルのパス（`scripts/review/output/review-result-pr{番号}-{レビュー回数}.md`）

6. 生成結果を `scripts/review/output/review-prompt-pr{番号}-{レビュー回数}.md` に保存する

7. レビュー結果の受け皿ファイルを空で作成する
   - `scripts/review/output/review-result-pr{番号}-{レビュー回数}.md` を空ファイルとして作成する（Codex がここに結果を書き込む）

8. Codex CLI を起動してレビューを自動実行する

   **前提**: `codex` コマンドが PATH にあり、ChatGPT または OpenAI API キーで認証済みであること。
   - 未インストール: `npm install -g @openai/codex`
   - 未認証: `codex login`

   実行コマンド:

   ```bash
   cat scripts/review/output/review-prompt-pr{番号}-{レビュー回数}.md \
     | codex exec --sandbox workspace-write -
   ```

   - `codex exec` は非対話モードで実行される
   - `--sandbox workspace-write` でリポジトリ配下へのファイル書き込みを許可（プロンプト末尾の指示通り `{{RESULT_FILE}}` に書き込む）
   - **Bash 呼び出し時の timeout は 600000ms（10分）を指定すること**。大規模 PR では数分かかる
   - stderr に進捗ログ、stdout に最終メッセージが流れる

9. レビュー結果を検証する

   ```bash
   test -s scripts/review/output/review-result-pr{番号}-{レビュー回数}.md
   ```

   - **非空で存在する場合** → 次へ進む
   - **空 or 存在しない場合** → Codex が指示通り書き込めなかった可能性。stderr のログを確認してユーザーに状況を報告し、中断する（自動で再実行はしない）

10. レビュー結果の簡易サマリーを表示する
    - 結果ファイルから CRITICAL / WARNING / INFO の件数をカウントして表示
    - 「総合評価」（APPROVE / REQUEST_CHANGES / COMMENT）を抽出して表示
    - レビュー結果ファイルのパスも併せて表示

11. 自動連鎖呼び出し（auto-loop）

    レビュー結果と現在のレビュー回数（step 2 で算出した値）に基づき、次のスキルを自動実行する。

    **判定ロジック**:

    | 条件 | 次のアクション |
    |---|---|
    | CRITICAL=0 かつ WARNING=0 | `/ship {PR番号}` を Skill tool で呼び出す |
    | CRITICAL≥1 または WARNING≥1（かつレビュー回数 ≤ 5） | `/fix {PR番号}` を Skill tool で呼び出す |
    | CRITICAL≥1 または WARNING≥1（かつレビュー回数 > 5） | **自動継続せず停止**してユーザーに報告 |

    **報告メッセージ**:

    - **/ship に進む場合**:
      ```
      指摘なし（CRITICAL: 0, WARNING: 0, INFO: Z）。`/ship {PR番号}` を自動実行します。
      結果ファイル: scripts/review/output/review-result-pr{番号}-{回数}.md
      ```
    - **/fix に進む場合**:
      ```
      指摘 N 件 (CRITICAL: X, WARNING: Y, INFO: Z)。`/fix {PR番号}` を自動実行します。
      結果ファイル: scripts/review/output/review-result-pr{番号}-{回数}.md
      ```
    - **最大反復到達時（停止）**:
      ```
      Review round が 5 を超えました。Codex の指摘が収束していないため、自動ループを停止します。
      最後のレビュー結果: scripts/review/output/review-result-pr{番号}-{回数}.md
      指摘内容を確認のうえ、手動で対応してください。
      ```

    **件数のパースに失敗した場合**: 「レビュー結果のフォーマット解析に失敗しました」とユーザーに報告し、自動継続せず停止する（誤った自動実行を避けるため）。
