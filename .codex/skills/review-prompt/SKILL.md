---
name: review-prompt
description: PRの差分からクロスレビュー用プロンプトを生成するスキル。レビュー依頼プロンプト作成、"$review-prompt <PR番号>"、"/review-prompt <PR番号>" の依頼で使用する。既存の review スキルは生成済みプロンプトを読んでレビューを実行する用途として残す。
---

# Review Prompt

PRの差分からレビュー依頼プロンプトを生成し、外部レビュー担当AIに渡せる形にする。
既存の `review` スキルは「生成済みレビュー依頼プロンプトを読んでレビュー結果を書く」用途なので、このスキルとは役割を分ける。

## 手順

### 1. PR番号を特定する

- ユーザーの依頼にPR番号が含まれていればそれを使う。
- PR番号がなければ、`gh pr view --json number -q '.number'` で現在のブランチのPRを検出する。
- PR番号を特定できない場合は、PR番号をユーザーに確認する。

### 2. レビュープロンプトを生成する

既存スクリプトを優先して使う。

```bash
./scripts/review/generate-review-prompt.sh <PR番号>
```

PR番号が省略可能な状況では、次のコマンドでもよい。

```bash
./scripts/review/generate-review-prompt.sh
```

スクリプトは以下を行う。

- PR情報を取得する
- PR差分を取得する
- `scripts/review/review-template.md` のプレースホルダーを置換する
- `scripts/review/output/review-prompt-pr{番号}-{回数}.md` を生成する
- `scripts/review/output/review-result-pr{番号}-{回数}.md` を空ファイルとして作成する

### 3. 生成結果を確認する

生成されたプロンプトファイルを読み、以下を確認する。

- PR番号、PR URL、タイトル、ブランチ名が正しい
- 差分が含まれている
- 出力先が `scripts/review/output/review-result-pr{番号}-{回数}.md` になっている
- リポジトリルートが現在の環境に合っている

リポジトリルートなど、テンプレート由来の固定値が現在の環境と異なる場合は、生成されたプロンプトファイル側を現在の値に合わせて修正する。

### 4. 完了を報告する

ユーザーに以下を報告する。

- 生成されたレビュープロンプトファイルのパス
- レビュー結果の書き込み先ファイルのパス
- 「このプロンプトをレビュー担当AIに渡してください」
- 「Codexでレビューする場合は `$review <PR番号>` または `review <PR番号>` と依頼してください」
