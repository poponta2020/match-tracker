---
name: fix
description: review スキルが書いたレビュー結果ファイルを読み込み、CRITICAL/WARNING/INFOの指摘に基づいてPRブランチのworktreeで修正するスキル。レビュー指摘修正、"$fix <PR番号>"、"/fix <PR番号>" の依頼で使用する。
---

# Fix

レビュー結果ファイルを読み込み、指摘事項に基づいてコードを修正する。
別ブランチのPRでも、git worktreeを使ってブランチ切り替えなしで修正する。

**ユーザーとの対話はすべて日本語で行うこと。**

## 基本方針

- メイン作業ツリー（例: `c:\Users\user\match-tracker`）では原則として実装ファイルを編集しない。
- 修正作業はPRのheadブランチに対応する専用worktreeで行う。
- 既存worktreeがあれば再利用する。なければ `C:\tmp\fix-pr<PR番号>` を標準パスとして作成する。
- 以降のファイル読み書き、テスト、git操作は必ず worktree 配下で行う。
- 例外として、レビュープロンプト生成時の `scripts/review/output/` はメイン作業ツリー側に作成してよい（gitignore対象のレビュー連携ファイルのみ）。実装ファイルはメイン作業ツリーで編集しない。
- 同じファイルを別セッション・別worktreeで同時に編集していそうな場合は、実装前にユーザーへ確認する。
- commit、push、再レビュー用プロンプト生成は、変更内容とテスト結果をユーザーに示してから進める。

## Step 0: PR番号を特定する

ユーザーの依頼にPR番号が含まれていればそれを使う。
PR番号がなければ、`gh pr view --json number -q '.number'` で現在のブランチのPRを検出する。
PR番号を特定できない場合は、PR番号をユーザーに確認する。

## Step 1: レビュー結果ファイルを読み込む

`scripts/review/output/review-result-pr{番号}-*.md` のうち、レビュー回数が最大のファイルを読む。

レビュー回数がユーザーに指定されている場合は、`scripts/review/output/review-result-pr{番号}-{回数}.md` を読む。

該当ファイルが存在しない場合:

- `scripts/review/output/` にある関連ファイルを確認する。
- レビュー結果ファイルが空の場合は、レビューがまだ書き込まれていないことを伝える。
- 必要に応じて `$review <PR番号>` でレビュー実行、または `$review-prompt <PR番号>` でプロンプト生成を案内する。

## Step 2: レビュー指摘を分析する

レビュー結果から以下を分類する。

- **CRITICAL**: 必ず修正する。
- **WARNING**: 原則修正する。修正しない場合は理由を説明する。
- **INFO**: 任意。対応するかどうか判断し、対応しない場合は理由を説明する。

レビュー結果が `APPROVE` かつ指摘なしの場合は、修正不要であることを報告して終了する。

## Step 3: PR情報と作業worktreeを特定する

PRのheadブランチを取得する。

```powershell
gh pr view <PR番号> --json number,url,title,headRefName,baseRefName
```

既存worktreeを確認する。

```powershell
git worktree list
```

判定:

- **PR headブランチに対応するworktreeがある場合**: そのworktreeを使う。作業前に `git -C <worktree> fetch origin` と `git -C <worktree> pull --ff-only` を行ってよいかユーザーに確認する。
- **対応worktreeがない場合**: `C:\tmp\fix-pr<PR番号>` を作成候補として提示し、作成してよいかユーザーに確認する。
- **同じブランチを別セッションが編集中に見える場合**: 状況をユーザーに報告し、続行方針を確認する。

新規worktree作成の標準手順:

```powershell
New-Item -ItemType Directory -Force C:\tmp
git fetch origin <headRefName>
git branch --list "<headRefName>"
git worktree add "C:\tmp\fix-pr<PR番号>" -b "<headRefName>" "origin/<headRefName>"
```

ローカルに同名ブランチが既にある場合は、新規作成せず、そのブランチを使ってworktreeを追加する。

```powershell
git worktree add "C:\tmp\fix-pr<PR番号>" "<headRefName>"
```

この操作が失敗した場合は、エラー内容を読み、ブランチが既に別worktreeで使用中か確認する。推測で別ブランチを作らず、ユーザーに確認する。

## Step 4: 影響範囲を調査する

レビュー指摘の対象ファイルについて、AGENTS.md の「影響範囲の調査義務」に従い、以下を調査する。

- 変更対象を呼び出している箇所（上流）
- 変更対象が呼び出している箇所（下流）
- フロントエンドとバックエンド間のAPI連携への影響
- DBスキーマ変更の必要性
- 共通コンポーネントやユーティリティへの影響
- ドキュメント更新の必要性

調査はworktree内のコードを基準に行う。

## Step 5: 修正方針の提示と確認

**ここで必ずユーザーに確認を取ること。実装に着手してはならない。**

以下の形式で修正方針を提示する。

```markdown
## レビュー指摘修正方針

**対象PR:** #<PR番号>
**作業worktree:** `<worktreeパス>`

### 対応する指摘
- [CRITICAL] <指摘タイトル> - <修正方針>
- [WARNING] <指摘タイトル> - <修正方針>

### 対応しない指摘
- [INFO] <指摘タイトル> - <対応しない理由>

### 変更予定ファイル
- `<ファイルパス>`: <何をどう変えるか>

### 影響範囲
<影響の有無と内容>

### 確認予定
- <実行するテストやlint>

### ドキュメント更新
必要 / 不要（理由）
```

ユーザーの承認を得てから次に進む。

## Step 6: 修正を実施する

承認された方針に沿って、worktree内で修正を実装する。

ルール:

- CRITICAL は必ず対応する。
- WARNING は原則対応する。
- INFO は方針で合意したものだけ対応する。
- 方針にない変更を勝手に加えない。
- すべてのパスは `worktree` からの相対パスに読み替える。
- 変更中に想定外の影響や不明点が出たら、実装を止めてユーザーに確認する。

## Step 7: テスト実行

変更内容に応じて、worktree内でテストを実行する。

バックエンドに変更がある場合:

```powershell
cd "<worktreeパス>\karuta-tracker"
.\gradlew test
```

フロントエンドに変更がある場合:

```powershell
cd "<worktreeパス>\karuta-tracker-ui"
npm run lint
npm run build
```

必要に応じて、レビュー指摘に対応する単体テストや限定テストを追加実行する。

テストが失敗した場合:

- 失敗内容を読み、原因を調査する。
- 修正方針の範囲内で直せる場合は修正して再実行する。
- 方針外の追加修正が必要な場合は、ユーザーに確認してから進める。

## Step 8: ドキュメント更新

レビュー指摘修正によって、仕様・設計・画面に変更が入る場合は、worktree内で以下を更新する。

- `docs/SPECIFICATION.md`
- `docs/SCREEN_LIST.md`
- `docs/DESIGN.md`

更新不要の場合は、その理由を修正サマリーに残す。

## Step 9: コミット前確認

変更とテスト結果をまとめ、コミット前にユーザーへ確認する。

```markdown
## コミット前確認

**対応した指摘:**
- [CRITICAL] <指摘タイトル> - <修正内容>
- [WARNING] <指摘タイトル> - <修正内容>

**対応しなかった指摘:**
- [INFO] <指摘タイトル> - <理由>

**変更ファイル:**
- `<ファイルパス>`

**テスト結果:**
- <実行したテスト>: PASS / FAIL / 未実行（理由）

**ドキュメント更新:**
- 更新あり / 更新不要（理由）

この内容でコミットしてよいですか？
```

ユーザーの明示承認を得てからコミットする。

コミット例:

```powershell
git -C "<worktreeパス>" status --short
git -C "<worktreeパス>" add <対象ファイル>
git -C "<worktreeパス>" commit -m "fix: レビュー指摘を修正"
```

レビュー出力ファイル、`.claude/`、`.codex/` の設定ファイルなど、修正対象外のファイルはコミットに含めない。

## Step 10: Push前確認

push の前に、ユーザーへ確認する。

```markdown
## Push確認

**PR:** #<PR番号>
**ブランチ:** `<headRefName>`
**コミット:** `<commit hash>`

pushしてよいですか？
```

ユーザーの明示承認を得てから実行する。

```powershell
git -C "<worktreeパス>" push origin "<headRefName>"
```

## Step 11: 再レビュープロンプト生成

push後、`review-prompt` スキル相当の手順で再レビュープロンプトを生成する。
レビュー連携ファイルを見つけやすくするため、原則としてメイン作業ツリー（例: `c:\Users\user\match-tracker`）側の `scripts/review/output/` に生成する。
これは gitignore 対象のレビュー連携ファイルだけを作る例外であり、実装ファイルをメイン作業ツリーで編集してよいという意味ではない。

```powershell
cd "c:\Users\user\match-tracker"
.\scripts\review\generate-review-prompt.sh <PR番号>
```

Windowsで `.sh` が直接実行できない場合は、Git Bash等で実行するか、同等の処理を手作業で行う。

生成後、以下を確認する。

- `scripts/review/output/review-prompt-pr{番号}-{回数}.md`
- `scripts/review/output/review-result-pr{番号}-{回数}.md`

## Step 12: Worktreeの保持

Worktreeは削除しない。PRがマージされるまで保持する。
追加のレビュー指摘修正でも同じworktreeを再利用する。

## Step 13: 完了報告

最後に以下を簡潔に報告する。

- 対応した指摘
- 対応しなかった指摘と理由
- 変更したファイル一覧
- テスト結果
- コミットハッシュ
- push結果
- Worktreeの場所
- 生成されたレビュープロンプトファイルのパス
