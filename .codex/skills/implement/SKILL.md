---
name: implement
description: define-feature または fix-feature で作成した要件定義書・実装手順書を読み取り、1回に1タスクずつworktreeで実装して進捗を記録するスキル。機能実装、"$implement <機能名>"、"/implement <機能名>" の依頼で使用する。
---

# Implement

`define-feature` または `fix-feature` で作成された要件定義書・実装手順書を読み取り、1回の呼び出しで1タスクを実装し、進捗を記録する。

**ユーザーとの対話はすべて日本語で行うこと。**

## 基本方針

- メイン作業ツリー（例: `c:\Users\user\match-tracker`）では原則として編集しない。
- 実装作業は必ず専用の git worktree で行う。
- Worktreeは `C:\tmp\impl-<summary>` を標準パスにする。`C:\tmp` が存在しなければ作成する。
- 以降のファイル読み書き、テスト、git操作は必ず worktree 配下で行う。
- 例外として、レビュープロンプト生成時の `scripts/review/output/` はメイン作業ツリー側に作成してよい（gitignore対象のレビュー連携ファイルのみ）。実装ファイルはメイン作業ツリーで編集しない。
- 同じファイルを別セッション・別worktreeで同時に編集していそうな場合は、実装前にユーザーへ確認する。
- commit、push、PR作成は副作用が強いため、それぞれ実行前にユーザーの明示確認を取る。
- ユーザーが明示的に「並行エージェントで分担して」と依頼した場合のみ、Codexのサブエージェント利用を検討する。その場合も、各担当の変更ファイル範囲とworktreeパスを明確にする。

## Step 0: 対象機能の特定

### 機能名が指定されている場合

ユーザーの依頼で指定された機能名に対応する `docs/features/<機能名>/` ディレクトリを探す。
見つからない場合はユーザーにエラーを伝える。

### 機能名が指定されていない場合

`docs/features/` 配下を走査し、以下のいずれかに未完了タスク（`- [ ]`）が残っている機能を一覧表示する。

- `implementation-plan.md`
- `fix-implementation-plan.md`

```text
進行中の機能:
1. <機能名A> - 残りタスク: 3/5（implementation-plan.md）
2. <機能名B> - 残りタスク: 1/4（fix-implementation-plan.md）
どの機能の実装を進めますか？
```

ユーザーに選択してもらう。進行中の機能がない場合は「未完了の機能はありません。`$define-feature` または `$fix-feature` で要件定義を行ってください。」と伝える。

## Step 1: 資料の読み込み

対象機能ディレクトリから、以下の組み合わせを優先順で読み込む。

### 新機能の場合

- `docs/features/<機能名>/requirements.md`
- `docs/features/<機能名>/implementation-plan.md`

### 改修の場合

- `docs/features/<機能名>/fix-requirements.md`
- `docs/features/<機能名>/fix-implementation-plan.md`

両方ある場合は、未完了タスクが残っている実装手順書を優先する。どちらを使うか曖昧な場合はユーザーに確認する。

要件定義書から機能の全体像を把握し、実装手順書から現在の進捗とタスク一覧を確認する。

## Step 2: タスクの選択

実装手順書の未完了タスク（`- [ ] 完了` または未チェックのタスク）を抽出する。

### 依存関係の確認

各タスクの「依存タスク」を確認し、依存先が全て完了しているタスクのみを着手可能とする。

### タスク選択

- 着手可能タスクが **1つ**: そのタスクを自動選択し、ユーザーに伝える
- 着手可能タスクが **複数**: リスト表示してユーザーに選択してもらう
- 着手可能タスクが **0個**: 依存タスクが未完了であることを説明する

```text
着手可能なタスク:
1. タスク3: フロントエンド画面実装
2. タスク4: バリデーション追加
どのタスクに着手しますか？
```

## Step 3: 実装方針の提示

選択したタスクについて、以下をユーザーに提示する。

- タスクの概要（実装手順書から）
- 変更対象ファイル（実装手順書から）
- 具体的な実装方針（要件定義書 + コードベース調査に基づく）
- 予定するテスト
- ドキュメント更新の要否
- 作業予定worktree（`C:\tmp\impl-<summary>`）

ユーザーの確認を得てから実装に進む。

## Step 4: 影響範囲の調査

AGENTS.md の「影響範囲の調査義務」に従い、以下を実施する。

1. 変更対象ファイルの上流・下流の依存関係を確認する。
2. フロントエンドとバックエンド間のAPI連携への影響を確認する。
3. 共通コンポーネント・ユーティリティへの影響を確認する。
4. DBスキーマの変更が必要か確認する。
5. 仕様書・設計書・画面一覧の更新が必要か確認する。

問題が見つかった場合はユーザーに報告し、方針を調整する。

## Step 5: Worktreeの作成

機能名からケバブケースの英語サマリーを生成する。

例:
- `lottery-priority-players`
- `densuke-sync`
- `admin-notification-redesign`

標準のworktreeパスとブランチ名:

```text
worktree: C:\tmp\impl-<summary>
branch: feature/<summary>
```

既存worktreeと既存ブランチを確認する。

```powershell
git worktree list
git branch --list "feature/<summary>"
git ls-remote --heads origin "feature/<summary>"
```

判定:

- **同じworktreeが存在する場合**: そのworktreeを再利用してよいかユーザーに確認する。
- **同名ブランチが存在する場合**: 既存作業と衝突する可能性をユーザーに報告し、続行方針を確認する。
- **存在しない場合**: `origin/main` から新規worktreeを作成する。

作成コマンド:

```powershell
New-Item -ItemType Directory -Force C:\tmp
git fetch origin main
git worktree add "C:\tmp\impl-<summary>" -b "feature/<summary>" origin/main
```

以降のすべてのファイル操作は `C:\tmp\impl-<summary>` 配下に限定する。

## Step 6: 実装

要件定義書と実装手順書に基づいて、worktree内で選択タスクを実装する。

ルール:

- 方針にない変更を勝手に加えない。
- 既存の設計・命名・実装パターンに合わせる。
- 新規ファイル作成は必要最小限にする。
- すべてのパスは `C:\tmp\impl-<summary>\...` または `workdir: C:\tmp\impl-<summary>` として扱う。
- 変更中に想定外の影響や不明点が出たら、実装を止めてユーザーに確認する。
- DBスキーマ変更がある場合は、Entity等のコード変更と対応する `database/*.sql` を同じ作業に含める。

## Step 7: テスト実行

変更内容に応じて、worktree内でテストを実行する。

バックエンドに変更がある場合:

```powershell
cd "C:\tmp\impl-<summary>\karuta-tracker"
.\gradlew test
```

フロントエンドに変更がある場合:

```powershell
cd "C:\tmp\impl-<summary>\karuta-tracker-ui"
npm run lint
npm run build
```

DBやスクリプトのみの変更でも、影響する最小限の検証コマンドを実行する。

テストが失敗した場合:

- 失敗内容を読み、原因を調査する。
- 実装方針の範囲内で直せる場合は修正して再実行する。
- 方針外の追加修正が必要な場合は、ユーザーに確認してから進める。

## Step 8: ドキュメント更新

新機能追加・既存機能変更・画面追加/変更がある場合は、AGENTS.mdに従って以下をworktree内で更新する。

- `docs/SPECIFICATION.md`
- `docs/SCREEN_LIST.md`
- `docs/DESIGN.md`

更新不要の場合は、その理由を記録する。

## Step 9: 進捗更新

worktree内の実装手順書で、該当タスクのチェックボックスを完了にする。

```text
- [ ] 完了
```

を

```text
- [x] 完了
```

に変更する。

対応Issueがある場合は、必要に応じてIssue本文やコメントで進捗更新する。ただし GitHub Issue の更新前にはユーザーの確認を取る。

## Step 10: コミット前確認

変更とテスト結果をまとめ、コミット前にユーザーへ確認する。

```markdown
## コミット前確認

**実装タスク:** <タスク名>

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
git -C "C:\tmp\impl-<summary>" status --short
git -C "C:\tmp\impl-<summary>" add <対象ファイル>
git -C "C:\tmp\impl-<summary>" commit -m "<変更内容の日本語要約>"
```

関連ファイルをすべて1つのコミットに含める。ドキュメント更新と実装手順書の進捗更新も同じコミットに含める。

## Step 11: Push前確認

push の前に、ユーザーへ確認する。

```markdown
## Push確認

**ブランチ:** `feature/<summary>`
**コミット:** `<commit hash>`

pushしてよいですか？
```

ユーザーの明示承認を得てから実行する。

```powershell
git -C "C:\tmp\impl-<summary>" push -u origin "feature/<summary>"
```

## Step 12: PR作成（全タスク完了時のみ）

実装手順書の全タスクが完了しているか確認する。

- **未完了タスクが残っている場合**: PR作成はスキップし、Step 14へ進む。
- **全タスクが完了している場合**: PR作成前確認に進む。

既存PRの確認:

```powershell
gh pr list --head "feature/<summary>" --json number,url
```

既にPRが存在する場合はPR作成をスキップし、Step 13へ進む。

PR作成前に、ユーザーへ確認する。

```markdown
## PR作成確認

**ブランチ:** `feature/<summary>`
**PRタイトル案:** `feat: <機能名の英語サマリー>`

PRを作成してよいですか？
```

PR本文には以下を含める。

```markdown
## Summary
- <変更内容の箇条書き>

## Related Issues
- <親Issueや子IssueのURL>

## Test plan
- [x] <実行したテスト>
- [ ] 動作確認
```

作成コマンド:

```powershell
gh pr create --base main --head "feature/<summary>" --title "feat: <機能名の英語サマリー>" --body "<PR本文>"
```

## Step 13: レビュープロンプト生成

PR作成後（または既存PR検出後）、`review-prompt` スキル相当の手順でレビュープロンプトを生成する。
レビュー連携ファイルを見つけやすくするため、原則としてメイン作業ツリー（例: `c:\Users\user\match-tracker`）側の `scripts/review/output/` に生成する。
これは gitignore 対象のレビュー連携ファイルだけを作る例外であり、実装ファイルをメイン作業ツリーで編集してよいという意味ではない。

```powershell
cd "c:\Users\user\match-tracker"
.\scripts\review\generate-review-prompt.sh <PR番号>
```

Windowsで `.sh` が直接実行できない場合は、Git Bash等で実行するか、同等の処理を手作業で行う。

## Step 14: Worktreeの保持

Worktreeは削除しない。次のタスク着手時やレビュー指摘修正でも同じworktreeを再利用する。
PRがマージされた後に、別スキルまたはユーザー確認の上でクリーンアップする。

## Step 15: 完了報告

未完了タスクが残っている場合:

- 実装したタスクの内容サマリー
- コミットハッシュ
- push結果
- 残りタスク数と次に着手可能なタスク
- Worktreeの場所（`C:\tmp\impl-<summary>`）

全タスク完了の場合:

- 実装したタスクの内容サマリー
- コミットハッシュ
- PR URL
- Worktreeの場所（`C:\tmp\impl-<summary>`）
- 生成されたレビュープロンプトファイルのパス
