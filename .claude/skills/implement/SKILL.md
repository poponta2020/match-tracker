---
name: implement
description: /define-featureで作成した要件定義書・実装手順書を読み取り、タスクを1つずつ実装して進捗を記録するスキル。機能の実装を進めたいとき、実装タスクに着手したいときに使用する。
disable-model-invocation: true
user-invocable: true
allowed-tools: Read, Edit, Write, Bash, Grep, Glob, Agent, Skill
argument-hint: [機能名（任意。省略時は一覧から選択）]
---

# 実装スキル

`/define-feature` で作成された要件定義書・実装手順書を読み取り、1回の呼び出しで1タスクを実装し、進捗を記録する。

**ユーザーとの対話はすべて日本語で行うこと。**

## Step 0: 作業ブランチの確認

スキル実行前に、メインの作業ディレクトリが `main` ブランチであることを確認する。
worktreeで隔離作業を行うため、`main` にいることは必須条件。

```bash
git branch --show-current
```

- **`main` の場合** → 次のステップへ進む
- **`main` 以外の場合** → 自動で `main` に切り替える:

```bash
git checkout main
```

切り替え後、ユーザーに「`main` ブランチに切り替えました」と通知して続行する。

---

## Step 1: 対象機能の特定

### 引数ありの場合
`$ARGUMENTS` で指定された機能名に対応する `docs/features/<機能名>/` ディレクトリを探す。
見つからない場合はユーザーにエラーを伝える。

### 引数なしの場合
`docs/features/` 配下のディレクトリを走査し、`implementation-plan.md` に未完了タスク（`- [ ]`）が残っている機能を一覧表示する。

```
進行中の機能:
1. <機能名A> — 残りタスク: 3/5
2. <機能名B> — 残りタスク: 1/4
どの機能の実装を進めますか？
```

ユーザーに選択させる。進行中の機能がない場合は「未完了の機能はありません。`/define-feature` で新しい機能の要件定義を行ってください。」と伝える。

## Step 2: 資料の読み込み

以下のファイルを読み込む：
- `docs/features/<機能名>/requirements.md` — 要件定義書
- `docs/features/<機能名>/implementation-plan.md` — 実装手順書

要件定義書から機能の全体像を把握し、実装手順書から現在の進捗とタスク一覧を確認する。

## Step 3: タスクの選択

`implementation-plan.md` の未完了タスク（`- [ ] 完了` のもの）を抽出する。

### 依存関係の確認
各タスクの「依存タスク」を確認し、依存先が全て完了しているタスクのみを着手可能とする。

### タスク選択
- 着手可能タスクが **1つ** → そのタスクを自動選択し、ユーザーに伝える
- 着手可能タスクが **複数** → リスト表示してユーザーに選択させる

```
着手可能なタスク:
1. タスク3: フロントエンド画面実装
2. タスク4: バリデーション追加
どのタスクに着手しますか？
```

- 着手可能タスクが **0個**（依存タスクが未完了）→ 状況をユーザーに説明する

## Step 4: 実装方針の提示

選択したタスクについて、以下をユーザーに提示する：
- タスクの概要（実装手順書から）
- 変更対象ファイル（実装手順書から）
- 具体的な実装方針（要件定義書 + コードベース調査に基づく）

ユーザーの確認を得てから実装に進む。

## Step 5: 影響範囲の調査

CLAUDE.mdの「影響範囲の調査義務」に従い、以下を実施する：
1. 変更対象ファイルの上流・下流の依存関係を確認
2. フロントエンド ⇔ バックエンド間の連携への影響を確認
3. 共通コンポーネント・ユーティリティへの影響を確認

問題が見つかった場合はユーザーに報告し、方針を調整する。

## Step 6: Worktreeの作成（並行作業のための隔離環境）

**実装に着手する前に**、worktreeを使って隔離された作業環境を作成する。
これにより、他のセッション（`/quickfix`, `/bug-report`, `/fix` 等）と競合せずに実装を進められる。

1. 機能名からケバブケースの英語サマリーを生成する（例: `lottery-feature`）

2. **既存worktreeの確認**:

```bash
git worktree list
```

3. 以下の優先順位で判定する:

   **a) パス `/tmp/impl-<summary>` のworktreeが既に存在する場合**（前回タスクから保持されている）:
   - そのworktreeをそのまま再利用する
   - 最新のリモート変更を取り込む:
   ```bash
   git -C /tmp/impl-<summary> fetch origin
   git -C /tmp/impl-<summary> pull --ff-only
   ```

   **b) worktreeは存在しないが、リモートに `feature/<summary>` ブランチが存在する場合**（前回 `/ship` で削除された後の再作成など）:
   ```bash
   git fetch origin feature/<summary>
   git worktree add /tmp/impl-<summary> origin/feature/<summary>
   ```

   **c) worktreeもブランチも存在しない場合**（初回）:
   ```bash
   git fetch origin main
   git worktree add /tmp/impl-<summary> -b feature/<summary> origin/main
   ```

4. **以降のすべてのファイル操作（Read, Edit, Write, Bash）は `/tmp/impl-<summary>/` 配下のパスで行うこと**
   - 例: `/tmp/impl-<summary>/karuta-tracker/src/main/java/...`
   - メインの作業ディレクトリには一切触れない

## Step 7: 実装

要件定義書と実装手順書に基づいて、**worktree内で**タスクを実装する。
CLAUDE.mdのルールに従い、不明点があればユーザーに確認を取る。
**パスは必ず `/tmp/impl-<summary>/` プレフィックスを使うこと。**

タスクの種類に応じて、以下の専門エージェントに委譲する：

- **Spring Boot / バックエンド実装**（Controller / Service / Repository / Entity / DTO の追加・変更、Spring Security、スケジューラー等）→ `spring-boot-engineer` エージェントを使う
- **React / フロントエンド実装**（ページコンポーネント、状態管理、パフォーマンス最適化等）→ `react-specialist` エージェントを使う
- **DB設計・クエリ最適化**（スキーマ変更、JPQLチューニング、インデックス設計等）→ `postgres-pro` エージェントを使う
- **複数レイヤーにまたがるタスク**（バックエンド + フロントエンド両方）→ Claude 本体が担当し、必要に応じて各エージェントを部分的に活用する

**エージェントに委譲する際は、worktreeパス（`/tmp/impl-<summary>/`）を作業ディレクトリとして必ず伝えること。**

## Step 8: コミットとPush

worktree内で変更をコミットし、リモートにpushする。
コミットメッセージには対応する子Issue番号を含め、Issueを自動クローズする。

```bash
git -C /tmp/impl-<summary> add <対象ファイル>
git -C /tmp/impl-<summary> commit -m "$(cat <<'EOF'
<変更内容の要約>

Fixes #<子Issue番号>

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
EOF
)"
git -C /tmp/impl-<summary> push -u origin feature/<summary>
```

## Step 9: 進捗の更新

### implementation-plan.md の更新
**worktree内の** `docs/features/<機能名>/implementation-plan.md` で該当タスクのチェックボックスを完了にする：
`- [ ] 完了` → `- [x] 完了`

更新をコミット+pushする（Step 8のコミットに含めてもよい）。

### 親GitHub Issueの更新
親Issueのタスク一覧で、完了した子Issueのチェックを更新する：

```bash
# 親Issueの本文を取得し、該当タスクのチェックを更新
gh issue edit <親Issue番号> --body "<更新後の本文>"
```

## Step 10: PR作成（全タスク完了時のみ）

`implementation-plan.md` の全タスクが完了しているか確認する。

- **未完了タスクが残っている場合** → このステップをスキップしてStep 12へ進む
- **全タスクが完了した場合** → 以下の手順でPRを作成する

### 10a. 既存PRの確認

```bash
gh pr list --head feature/<summary> --json number,url
```

既にPRが存在する場合はPR作成をスキップし、Step 10c へ進む。

### 10b. PR作成

```bash
gh pr create --base main --head feature/<summary> --title "feat: <機能名の英語サマリー>" --body "$(cat <<'EOF'
## Summary
<変更内容の箇条書き（全タスクの要約）>

## Related Issues
<親IssueのURL>

## Test plan
- [ ] 動作確認

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

### 10c. レビュープロンプト生成

PR作成後（または既存PR検出後）、自動的に `/review <PR番号>` スキルを呼び出してレビュープロンプトを生成する。

## Step 11: Worktreeの保持

**Worktreeは削除しない。** 次のタスク着手時や `/fix`（レビュー指摘修正）でも同じworktreeを再利用する。
`/ship` でPRがマージされた際にworktreeも自動クリーンアップされる。

## Step 12: 完了報告

ユーザーに以下を報告する：

### 通常の場合（未完了タスクが残っている）
- 実装したタスクの内容サマリー
- コミットハッシュ
- クローズされた子Issue
- 残りタスク数と次に着手可能なタスク
- Worktreeの場所（`/tmp/impl-<summary>/`）

### 全タスク完了の場合
- 実装したタスクの内容サマリー
- コミットハッシュ
- 親IssueのURL（クローズは `/ship` でマージ時に行う）
- PR URL
- Worktreeの場所（`/tmp/impl-<summary>/`）
- 生成されたレビュープロンプトファイルのパス
- 「このファイルの内容をレビュー担当AI（Codex or Claude Code）に貼り付けてください」
