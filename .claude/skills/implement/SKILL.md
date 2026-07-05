---
name: implement
description: /define-featureで作成した要件定義書・実装手順書を読み取り、タスクを1つずつ実装して進捗を記録するスキル。機能の実装を進めたいとき、実装タスクに着手したいときに使用する。
disable-model-invocation: true
user-invocable: true
allowed-tools: Read, Edit, Write, Bash, Grep, Glob, Agent, Skill
argument-hint: [機能名（任意。省略時は一覧から選択）]
---

# 実装スキル

`/define-feature` で作成された要件定義書・実装手順書を読み取り、1回の呼び出しで対象機能の**全タスクを連続実装**し、コミット・push・PR作成・`/review` 呼び出しまで自動で行う。

**ユーザーとの対話はすべて日本語で行うこと。**

## 自動化の方針（重要）

ユーザーの作業を最小化するため、以下はユーザーに確認しない：
- 着手可能なタスクが複数ある場合の**選択順序**（自動で `implementation-plan.md` の上から順に選ぶ）
- 各タスクの**着手前の方針提示・確認**（即実装に着手する）
- 1タスク完了後の**「次のタスクに進んでよいか」の確認**（自動で次の着手可能タスクへループする）
- タスク完了ごとの進捗報告（最終完了時にまとめて報告する）

ただし、以下の場合は**必ずユーザーに確認**する（CLAUDE.md「最重要ルール：実装・編集前の認識合わせ」より）：
- 要件定義書・実装手順書を読んでも判断できない**仕様レベルの不明点**が見つかった場合
- 一般的なベストプラクティス・コードベースの既存設計方針と**矛盾する実装**が必要になった場合
- セキュリティ・パフォーマンスに**重大な影響**が出る可能性がある場合

確認した結果ユーザーから回答があったら、その方針に従い**そのまま自動継続**する（再度確認しない）。

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

### タスク選択（自動）
- 着手可能タスクが **1つ以上** → `implementation-plan.md` の**上から順に1つ自動選択**する。ユーザーには「タスクX（タスク名）に着手します」と通知するだけで、選択を待たない
- 着手可能タスクが **0個**（依存タスクが未完了 or 全タスク完了済み）→ 状況をユーザーに説明して停止する

## Step 4: 実装方針の決定（確認待ちなし）

選択したタスクについて、要件定義書・実装手順書とコードベース調査から実装方針を内部的に決定する。
ユーザーには方針を提示せず、そのまま Step 5（影響範囲調査）→ Step 7（実装）へ進む。

ただし、以下に該当する場合のみ、この時点でユーザーに確認する（「自動化の方針」を参照）：
- 要件定義書・実装手順書を読んでも判断できない**仕様レベルの不明点**がある
- 既存設計やベストプラクティスと**矛盾する実装**が必要
- **セキュリティ・パフォーマンスへの重大な影響**が予想される

確認した結果回答があれば、その方針に従って**そのまま自動で実装に進む**（追加確認はしない）。

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

### タスクのモデル委譲（正典: docs/dev/model-delegation.md）

タスクごとに委譲可否を判定し、以下の**4条件をすべて満たす場合は `task-implementer` エージェント（Sonnet）に委譲**する（バックエンド/フロントエンドいずれの領域でも同じ判定基準で扱う）：

1. 実装手順書＋要件定義書でタスクが完全に仕様化されている（変更対象ファイル・完了条件が明確）
2. 設計判断の余地が残っていない（API の形・データモデル・UI 挙動の解釈が確定済み）
3. 検証手段がある（テスト・lint・ビルドで失敗を機械的に検知できる）
4. 高リスク領域を含まない（`database/` 配下の SQL マイグレーション追加・変更、`@RequireRole` 等の認可ロジックの新設・変更、本番操作を含まない）

1つでも欠けるタスク（跨層・曖昧・マイグレーション/認可新設/本番操作含み等）は、従来どおり main（このスキル）が worktree 内で直接実装する。

**委譲時にプロンプトへ必ず含めるもの**: Step 6 で作成した worktree の絶対パス（Windows 形式 `C:/tmp/impl-<summary>/`）／タスク仕様の**全文**（実装手順書・要件定義書の該当部分を要約せず貼る。仕様不足はワーカーの誤った推測に直結する）／**書くべきテスト**（テストファースト。実装手順書にテストが明記されていない場合は main がテスト要件を決めてから委譲する）／実行すべき検証コマンド（`./gradlew test` や `npm run lint`/`npm run build` 等）。

エージェントが「停止して報告」を返したら、その論点は main が引き取って判断・実装する（下位モデルへの再委譲リトライはしない）。

**委譲後の受け入れ確認（main が必ず行う）**: `git -C <worktree> diff` を読んで方針との一致を確認 → 検証コマンドを main 自身でも実行 → メインリポジトリが汚れていないか `git status --short` で一瞥 → 問題なければ Step 8 のコミットへ。

> `spring-boot-engineer` / `react-specialist` / `postgres-pro` エージェントは `task-implementer` 導入に伴い本スキルからの呼び出しを廃止した（定義自体は `.claude/agents/` に残存）。`security-auditor` / `test-automator` は `/bug-report`・`/quickfix` からの利用のみで、本スキルの対象外。

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

### タスクループ（次タスクへの自動継続）

進捗を更新したら、**ユーザーに確認を求めず**に未完了タスクが残っているか再確認する：

- **未完了の着手可能タスクが残っている** → **Step 3 に戻り、次のタスクへ自動的に進む**。ユーザーには `タスクX 完了 → タスクY に着手` のように1行で通知するだけで、確認は待たない
- **全タスク完了** → Step 10（PR作成）へ進む
- **未完了タスクは残っているが依存関係で着手不能**（通常は起きないはず）→ 状況を報告して停止

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

## Step 12: 完了報告（最後にまとめて1回だけ）

すべてのタスクが完了したら（または例外的に停止したら）、ユーザーにまとめて報告する。
**個別タスク完了ごとの詳細報告はしない**（Step 9 のタスクループで1行通知するのみ）。

### 全タスク完了の場合（通常パターン）
- 実装した**全タスクのサマリー一覧**（各タスク名 + コミットハッシュ）
- クローズされた子Issue一覧
- 親IssueのURL（クローズは `/ship` でマージ時に行う）
- **PR URL**
- Worktreeの場所（`/tmp/impl-<summary>/`）
- 生成されたレビュープロンプトファイルのパス
- 「このファイルの内容をレビュー担当AI（Codex or Claude Code）に貼り付けてください」

### 例外的に停止した場合（依存関係で着手不能、仕様の不明点で確認待ち など）
- これまでに実装したタスク一覧
- 残タスク一覧
- 停止理由（依存待ち / 仕様確認待ち など）
- Worktreeの場所（`/tmp/impl-<summary>/`）
