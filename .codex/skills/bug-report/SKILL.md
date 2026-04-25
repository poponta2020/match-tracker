---
name: bug-report
description: バグ発見時にGitHub Issueを作成し、原因調査・影響範囲分析・修正・記録・PR作成まで追跡するスキル。バグ対応を記録したい、"$bug-report <概要>"、"/bug-report <概要>" の依頼で使用する。
---

# Bug Report

バグの発見から修正・記録までを一貫して行う。GitHub Issueでバグ対応を追跡可能にし、必要に応じてworktreeで修正してPRとレビュープロンプト生成まで進める。

**ユーザーとの対話はすべて日本語で行うこと。**

## 基本方針

- GitHub Issue作成、Issueコメント、commit、push、PR作成は外部副作用があるため、実行前にユーザーの明示確認を取る。
- メイン作業ツリー（例: `c:\Users\user\match-tracker`）では原則として実装ファイルを編集しない。
- 修正作業は必ず専用の git worktree で行う。
- Worktreeは `C:\tmp\fix-<summary>` を標準パスにする。`C:\tmp` が存在しなければ作成する。
- 以降のファイル読み書き、テスト、git操作は必ず worktree 配下で行う。
- 例外として、レビュープロンプト生成時の `scripts/review/output/` はメイン作業ツリー側に作成してよい（gitignore対象のレビュー連携ファイルのみ）。実装ファイルはメイン作業ツリーで編集しない。
- 同じファイルを別セッション・別worktreeで同時に編集していそうな場合は、実装前にユーザーへ確認する。
- 不明点がある場合は、実装やIssue作成に入る前に確認する。

## Step 0: バグ内容のヒアリング

ユーザーの依頼または会話のコンテキストからバグの内容を把握する。

以下の情報を収集する。不足があればユーザーに質問する。

- **バグの概要**: 何が起きているか
- **再現手順**: どうすれば再現できるか（分かる範囲で）
- **期待される動作**: 本来どうなるべきか
- **実際の動作**: 実際にはどうなっているか
- **対象機能/画面/API**: 分かる範囲で
- **発生頻度/影響度**: 分かる範囲で

会話の流れでバグが発見された場合、そのコンテキストから上記を整理し、ユーザーに確認を取る。

## Step 1: GitHub Issue案を作成する

Issue作成前に、タイトルと本文案をユーザーに提示する。

```markdown
## Issue作成確認

**タイトル案:** `<簡潔なバグタイトル>`
**ラベル:** `bug`

**本文案:**
## バグの概要
<概要>

## 再現手順
<手順>

## 期待される動作
<期待動作>

## 実際の動作
<実際の動作>

この内容でGitHub Issueを作成してよいですか？
```

ユーザーの明示承認を得るまで `gh issue create` は実行しない。

## Step 2: GitHub Issueを作成する

承認後、Issueを作成する。

```powershell
gh issue create --title "<簡潔なバグタイトル>" --label "bug" --body "<Issue本文>"
```

作成されたIssue番号とURLを記録し、ユーザーに伝える。

既に該当Issueが存在する場合は、新規作成せず既存Issueを使ってよいかユーザーに確認する。

## Step 3: 原因調査・影響範囲分析

コードベースを調査し、原因と影響範囲を特定する。

調査項目:

1. 関連するファイル・コンポーネント・エンドポイントを特定する。
2. `rg` / `rg --files` を使ってコードを読み、原因を突き止める。
3. AGENTS.md の「影響範囲の調査義務」に従い、上流・下流の依存関係を確認する。
4. フロントエンドとバックエンド間のAPI連携への影響を確認する。
5. DBスキーマ変更の必要性を確認する。
6. 共通コンポーネントやユーティリティへの影響を確認する。
7. ドキュメント更新が必要か確認する。

バグが以下に関わる場合は、セキュリティ観点の自己レビューを必ず行う。

- 認証・認可（`@RequireRole`、インターセプター、トークン処理）
- 入力バリデーション不足
- SQLインジェクションやXSSリスク
- APIエンドポイントの不正アクセス
- 機密データの露出

## Step 4: 修正規模の判断

調査結果をもとに、修正規模を判断しユーザーに提示する。

### 軽微と判断する目安

- 変更ファイルが1〜2個
- 修正箇所が明確で限定的
- 他機能への影響がほぼない
- DBスキーマ変更がない

### 大規模と判断する目安

- 変更ファイルが3個以上
- 複数レイヤー（controller/service/repository/frontend等）にまたがる
- 他機能への副作用がありうる
- DBスキーマの変更が必要
- 仕様や業務フローの再確認が必要

判断結果、原因、影響範囲、修正方針案を提示し、最終判断はユーザーに委ねる。

```markdown
## 調査結果

**原因:** <原因の説明>

**影響範囲:**
- <影響箇所>

**修正規模:** 軽微 / 大規模

**修正方針案:**
- <方針>

**ドキュメント更新:** 必要 / 不要

この方針で進めてよいですか？
```

## Step 5: Issueへ調査結果を記録する

Issueコメントとして原因と修正方針を記録する。
コメント投稿前にユーザーへ本文案を提示し、承認を得る。

軽微な場合:

```markdown
## 原因
<原因の説明>

## 影響範囲
<影響範囲>

## 修正方針
<修正方針>
```

大規模な場合:

```markdown
## 修正手順書

### 原因
<原因の詳細>

### 修正方針
<どのようなアプローチで修正するか>

### 修正ステップ
- [ ] ステップ1: <内容>
- [ ] ステップ2: <内容>
- [ ] ステップ3: <内容>

### 影響範囲
<影響を受けるファイル・機能>

### 確認事項
<修正後に確認すべきこと>
```

承認後に投稿する。

```powershell
gh issue comment <Issue番号> --body "<コメント本文>"
```

## Step 6: Worktreeを作成する

バグ内容からケバブケースの英語サマリーを生成する。

例:

- `null-pointer-in-match-service`
- `duplicate-notification-send`
- `lottery-preview-auth-missing`

標準のworktreeパスとブランチ名:

```text
worktree: C:\tmp\fix-<summary>
branch: fix/<summary>
```

既存worktreeと既存ブランチを確認する。

```powershell
git worktree list
git branch --list "fix/<summary>"
git ls-remote --heads origin "fix/<summary>"
```

判定:

- **同じworktreeが存在する場合**: そのworktreeを再利用してよいかユーザーに確認する。
- **同名ブランチが存在する場合**: 既存作業と衝突する可能性をユーザーに報告し、続行方針を確認する。
- **存在しない場合**: `origin/main` から新規worktreeを作成する。

作成前にユーザーへ確認する。

```markdown
## Worktree作成確認

**worktree:** `C:\tmp\fix-<summary>`
**branch:** `fix/<summary>`
**base:** `origin/main`

このworktreeを作成して修正を進めてよいですか？
```

承認後に作成する。

```powershell
New-Item -ItemType Directory -Force C:\tmp
git fetch origin main
git worktree add "C:\tmp\fix-<summary>" -b "fix/<summary>" origin/main
```

以降のすべてのファイル操作は `C:\tmp\fix-<summary>` 配下に限定する。

## Step 7: 修正を実装する

承認された方針に沿って、worktree内で修正する。

ルール:

- 方針にない変更を勝手に加えない。
- 既存の設計・命名・実装パターンに合わせる。
- 新規ファイル作成は必要最小限にする。
- DBスキーマ変更がある場合は、Entity等のコード変更と対応する `database/*.sql` を同じ作業に含める。
- 変更中に想定外の影響や不明点が出たら、実装を止めてユーザーに確認する。

大規模修正の場合は、手順書のステップ単位で進める。
各ステップ完了時にIssueコメントを更新する場合は、コメント本文を提示してユーザーの承認を得る。

## Step 8: テスト実行

変更内容に応じて、worktree内でテストを実行する。

バックエンドに変更がある場合:

```powershell
cd "C:\tmp\fix-<summary>\karuta-tracker"
.\gradlew test
```

フロントエンドに変更がある場合:

```powershell
cd "C:\tmp\fix-<summary>\karuta-tracker-ui"
npm run lint
npm run build
```

必要に応じて、修正箇所に対応する単体テストや限定テストを追加実行する。

テストが失敗した場合:

- 失敗内容を読み、原因を調査する。
- 修正方針の範囲内で直せる場合は修正して再実行する。
- 方針外の追加修正が必要な場合は、ユーザーに確認してから進める。

## Step 9: ドキュメント更新

修正内容が以下に影響する場合は、worktree内で更新する。

- `docs/SPECIFICATION.md`
- `docs/SCREEN_LIST.md`
- `docs/DESIGN.md`

UIの見た目・動作、API仕様、DB仕様、機能仕様に変更がなければ、更新不要として理由を記録する。

## Step 10: Issueへ修正内容を記録する

Issueコメントとして、修正内容とテスト結果を記録する。
コメント投稿前に本文案を提示し、ユーザーの承認を得る。

```markdown
## 修正内容
<何をどう修正したか>

## 確認結果
- <実行したテスト>: PASS / FAIL

## ドキュメント更新
更新あり / 更新不要（理由）
```

承認後に投稿する。

```powershell
gh issue comment <Issue番号> --body "<コメント本文>"
```

## Step 11: コミット前確認

変更とテスト結果をまとめ、コミット前にユーザーへ確認する。

```markdown
## コミット前確認

**Issue:** #<Issue番号>

**変更内容:**
- <変更概要>

**変更ファイル:**
- `<ファイルパス>`

**テスト結果:**
- <実行したテスト>: PASS / FAIL / 未実行（理由）

**ドキュメント更新:**
- 更新あり / 更新不要（理由）

この内容でコミットしてよいですか？
```

ユーザーの明示承認を得てからコミットする。

```powershell
git -C "C:\tmp\fix-<summary>" status --short
git -C "C:\tmp\fix-<summary>" add <対象ファイル>
git -C "C:\tmp\fix-<summary>" commit -m "fix: <修正内容の要約>"
```

コミットメッセージ本文には `Fixes #<Issue番号>` を含める。

## Step 12: Push・PR作成前確認

push と PR 作成の前に、ユーザーへ確認する。

```markdown
## Push / PR作成確認

**ブランチ:** `fix/<summary>`
**コミット:** `<commit hash>`
**PRタイトル案:** `fix: <バグタイトル>`
**Issue:** Fixes #<Issue番号>

pushしてPRを作成してよいですか？
```

ユーザーの明示承認を得てから実行する。

Push:

```powershell
git -C "C:\tmp\fix-<summary>" push -u origin "fix/<summary>"
```

既存PRがある場合は作成せず、そのPRを使う。

```powershell
gh pr list --head "fix/<summary>" --json number,url,title
```

PR作成:

```powershell
gh pr create --base main --head "fix/<summary>" --title "fix: <バグタイトル>" --body "<PR本文>"
```

PR本文には以下を含める。

```markdown
## Summary
- <修正内容の箇条書き>

## Bug
Fixes #<Issue番号>

## Test plan
- [x] <実行したテスト>
- [ ] 動作確認
```

## Step 13: レビュープロンプト生成

PR作成後、`review-prompt` スキル相当の手順でレビュープロンプトを生成する。
レビュー連携ファイルを見つけやすくするため、原則としてメイン作業ツリー（例: `c:\Users\user\match-tracker`）側の `scripts/review/output/` に生成する。

```powershell
cd "c:\Users\user\match-tracker"
.\scripts\review\generate-review-prompt.sh <PR番号>
```

Windowsで `.sh` が直接実行できない場合は、Git Bash等で実行するか、同等の処理を手作業で行う。

生成後、以下を確認する。

- `scripts/review/output/review-prompt-pr{番号}-{回数}.md`
- `scripts/review/output/review-result-pr{番号}-{回数}.md`

## Step 14: Worktreeの保持

Worktreeは削除しない。PRがマージされるまで `C:\tmp\fix-<summary>` を保持する。
後続のレビュー指摘修正でも同じworktreeを再利用する。

## Step 15: 完了報告

最後に以下を報告する。

- 修正内容のサマリー
- コミットハッシュ
- Issue URL
- PR URL
- Worktreeの場所（`C:\tmp\fix-<summary>`）
- 生成されたレビュープロンプトファイルのパス
