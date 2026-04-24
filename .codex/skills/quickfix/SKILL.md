---
name: quickfix
description: 小さなバグ修正・軽微な変更を原因調査からPR作成まで一気通貫で進めるスキル。軽微な修正、"$quickfix <修正内容>"、"/quickfix <修正内容>" の依頼で使用する。
---

# Quickfix

小さなバグ修正・軽微な変更を、原因調査、影響範囲調査、修正方針確認、worktreeでの実装、テスト、コミット、PR作成まで一気通貫で進める。

**ユーザーとの対話はすべて日本語で行うこと。**

## 基本方針

- メイン作業ツリー（例: `c:\Users\user\match-tracker`）では原則として編集しない。
- 修正作業は必ず専用の git worktree で行う。
- Worktreeは `C:\tmp\fix-<summary>` を標準パスにする。`C:\tmp` が存在しなければ作成する。
- 以降のファイル読み書き、テスト、git操作は必ず worktree 配下で行う。
- 例外として、レビュープロンプト生成時の `scripts/review/output/` はメイン作業ツリー側に作成してよい（gitignore対象のレビュー連携ファイルのみ）。実装ファイルはメイン作業ツリーで編集しない。
- 同じファイルを別セッション・別worktreeで同時に編集していそうな場合は、実装前にユーザーへ確認する。
- commit、push、PR作成は副作用が強いため、それぞれ実行前にユーザーの明示確認を取る。

## 実行手順

### Step 0: 修正内容の把握

ユーザーの依頼または会話のコンテキストから、修正内容を把握する。
情報が不足している場合は、実装やファイル編集に入る前に質問する。

以下を明確にする。

- 何が起きているか
- 期待される動作
- 実際の動作
- 対象画面・API・機能
- 再現条件（分かる範囲）

### Step 1: 原因調査

修正内容を元に、以下を調査する。

- 関連するファイル・コンポーネント・エンドポイントを特定する
- `rg` / `rg --files` を使ってコードを読み、原因を突き止める
- バックエンド (`karuta-tracker/`) とフロントエンド (`karuta-tracker-ui/`) の両方を必要に応じて確認する
- DBスキーマやマイグレーションが関わる可能性がある場合は `database/` も確認する

### Step 2: 影響範囲調査

AGENTS.md の「影響範囲の調査義務」に従い、以下を調査する。

- 変更対象を呼び出している箇所（上流）
- 変更対象が呼び出している箇所（下流）
- フロントエンドとバックエンド間の API 連携への影響
- DB スキーマの変更が必要か
- 共通コンポーネントやユーティリティへの影響
- ドキュメント更新が必要か

### Step 3: 修正方針の提示と確認

**ここで必ずユーザーに確認を取ること。実装に着手してはならない。**

以下の形式で修正方針を提示する。

```markdown
## 修正方針

**原因:** <原因の説明>

**修正内容:**
- `<変更ファイル1>`: <何をどう変えるか>
- `<変更ファイル2>`: <何をどう変えるか>

**影響範囲:** <影響の有無と内容>

**ドキュメント更新:** 必要 / 不要

**作業worktree:** `C:\tmp\fix-<summary>`

**確認予定:**
- <実行するテストやlint>
```

ユーザーの承認を得てから次に進む。

### Step 4: Worktreeの作成

修正内容からケバブケースの英語サマリーを生成する。

例:
- `cancel-button-not-responding`
- `lottery-date-validation`
- `notification-duplicate-send`

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

作成コマンド:

```powershell
New-Item -ItemType Directory -Force C:\tmp
git fetch origin main
git worktree add "C:\tmp\fix-<summary>" -b "fix/<summary>" origin/main
```

以降のすべてのファイル操作は `C:\tmp\fix-<summary>` 配下に限定する。

### Step 5: 修正の実装

承認された方針に沿って、worktree内で修正を実装する。

ルール:

- 方針にない変更を勝手に加えない。
- 既存の設計・命名・実装パターンに合わせる。
- 新規ファイル作成は必要最小限にする。
- すべてのパスは `C:\tmp\fix-<summary>\...` または `workdir: C:\tmp\fix-<summary>` として扱う。
- 変更中に想定外の影響や不明点が出たら、実装を止めてユーザーに確認する。

### Step 6: テスト実行

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

テストが失敗した場合:

- 失敗内容を読み、原因を調査する。
- 修正方針の範囲内で直せる場合は修正して再実行する。
- 方針外の追加修正が必要な場合は、ユーザーに確認してから進める。

修正内容がセキュリティに関わる場合（認証・認可・入力バリデーション・APIエンドポイント等）は、追加でセキュリティ観点の自己レビューを行う。

### Step 7: ドキュメント更新

修正内容が以下のドキュメントに影響する場合は、worktree内で更新する。

- `docs/SPECIFICATION.md` - 仕様書
- `docs/SCREEN_LIST.md` - 画面一覧
- `docs/DESIGN.md` - 設計書

UIの見た目・動作、API仕様、DB仕様、機能仕様に変更がなければ、更新不要として理由を記録する。

### Step 8: コミット前確認

変更とテスト結果をまとめ、コミット前にユーザーへ確認する。

```markdown
## コミット前確認

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

コミット例:

```powershell
git -C "C:\tmp\fix-<summary>" status --short
git -C "C:\tmp\fix-<summary>" add <対象ファイル>
git -C "C:\tmp\fix-<summary>" commit -m "<変更内容の日本語要約>"
```

関連ファイルをすべて1つのコミットに含める。ドキュメント更新がある場合も同じコミットに含める。

### Step 9: Push・PR作成前確認

push と PR 作成の前に、ユーザーへ確認する。

```markdown
## Push / PR作成確認

**ブランチ:** `fix/<summary>`
**コミット:** `<commit hash>`
**PRタイトル案:** `fix: <修正タイトル>`

pushしてPRを作成してよいですか？
```

ユーザーの明示承認を得てから実行する。

Push:

```powershell
git -C "C:\tmp\fix-<summary>" push -u origin "fix/<summary>"
```

PR作成:

```powershell
gh pr create --base main --head "fix/<summary>" --title "fix: <修正タイトル>" --body "<PR本文>"
```

PR本文には以下を含める。

```markdown
## Summary
- <修正内容の箇条書き>

## Test plan
- [x] <実行したテスト>
- [ ] 動作確認
```

### Step 10: レビュープロンプト生成

PR作成後、`review-prompt` スキル相当の手順でレビュープロンプトを生成する。
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

### Step 11: Worktreeの保持

Worktreeは削除しない。PRがマージされるまで `C:\tmp\fix-<summary>` を保持する。
レビュー指摘修正でも同じworktreeを再利用する。

### Step 12: 完了報告

最後に以下を簡潔に報告する。

- 何を修正したか
- 変更したファイル一覧
- テスト結果
- コミットハッシュ
- PR URL
- Worktreeの場所（`C:\tmp\fix-<summary>`）
- 生成されたレビュープロンプトファイルのパス
