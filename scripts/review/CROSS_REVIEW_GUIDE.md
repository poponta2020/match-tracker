# Cross Review ワークフロー ガイド

Claude Code（実装担当）と Codex CLI（レビュー担当）を使った相互レビュー開発フローです。`/review` を一度起動するだけで、Codex 評価 → /fix 修正 → 再 /review → ... → /ship までを **完全自動ループ** で実行します。

## 概要（完全自動ループ）

```
┌──────────────┐     PR作成
│  実装担当 AI    │ ───────────────┐
│ (Claude Code)  │                │
└──────────────┘                │
                                  ▼
       ┌────────────── ユーザー: /review {PR番号} を1回実行 ─────────────┐
       │                                                              │
       ▼                                                              │
┌──────────────────────┐                                              │
│     /review 実行      │ ① プロンプト生成                              │
│                      │ ② codex exec で自動評価                       │
│                      │ ③ 結果ファイル保存                            │
│                      │ ④ CRITICAL/WARNING 件数を判定                 │
└──────────┬───────────┘                                              │
           │                                                          │
   ┌───────┴────────┐                                                 │
   │ 指摘あり？        │                                                 │
   └───┬────────┬───┘                                                 │
       │ Yes    │ No                                                   │
       ▼        ▼                                                      │
┌─────────────┐  ┌─────────────┐                                       │
│ /fix 自動起動 │  │ /ship 自動起動│ ──→ マージ・ブランチ削除・Issue クローズ  │
│ 修正→test→push│  └─────────────┘                                     │
└──────┬──────┘                                                       │
       │                                                              │
       └──── /fix が終わると /review を再実行（ループ） ──────────────────┘

  安全装置: review round が 5 を超えたら自動ループを停止
```

## 必要要件

| ツール | バージョン | インストール |
|---|---|---|
| Node.js | 18.18 以上 | OS の package manager 等 |
| Codex CLI | 0.130 以上 | `npm install -g @openai/codex` |
| ChatGPT サブスク or OpenAI API キー | - | `codex login` で認証 |

`codex --version` と `codex login status` の両方が通ることを事前に確認。

## ファイル構成

```
scripts/review/
├── CROSS_REVIEW_GUIDE.md     ← このファイル
├── review-template.md        ← レビュープロンプトのテンプレート
├── fix-template.md           ← 修正依頼プロンプトのテンプレート
├── generate-review-prompt.sh ← レビュープロンプト生成スクリプト
├── generate-fix-prompt.sh    ← 修正依頼プロンプト生成スクリプト
└── output/                   ← 生成されたプロンプト・レビュー結果（自動作成）
    ├── review-prompt-pr{N}-{R}.md  ← /review が生成
    ├── review-result-pr{N}-{R}.md  ← Codex が書き込む
    └── fix-prompt-pr{N}.md
```

## 使い方（Claude Code 実装 → Codex レビュー、完全自動）

### ステップ 1: 実装

通常通り実装し、PR を作成します。

```
（Claude Code で実装）
→ git commit & push
→ gh pr create
```

### ステップ 2: 自動ループ起動

```
/review          ← 現在のブランチのPRを自動検出
/review 42       ← PR #42 を指定
```

これ1回でループ全体が走ります。内部の流れ:

1. **/review**: プロンプト生成 → `codex exec` で評価 → 結果を `review-result-pr{N}-{R}.md` に保存
2. **判定**: CRITICAL+WARNING の合計が0なら ⇒ 4. へ。指摘ありなら 3. へ
3. **/fix（自動起動）**: worktree (`/tmp/fix-pr{N}/`) で修正 → test → push → /review を再起動 → 1. に戻る
4. **/ship（自動起動）**: コミット未ステージ分があればコミット・push → PR マージ → 親 Issue クローズ → ローカルブランチ削除 → worktree 削除 → 完了

### 安全装置

- **最大反復回数: 5 回**。Codex の指摘が5ラウンドで収束しなかった場合、自動ループを停止し、最後の結果ファイルを案内
- **パース失敗時の停止**: Codex の出力が想定フォーマット（CRITICAL/WARNING件数を含む）でなかった場合、自動継続せず停止
- **Codex 認証/インストールの欠落**: `/review` のステップ8で明示的にエラー

## 役割を入れ替えたい場合（Codex 実装 → Claude Code レビュー）

`/review` は Codex を呼び出す前提で組まれているため、Claude Code をレビュー担当にするフローは現在自動化していません。必要なら以下を手動で行います:

1. `./scripts/review/generate-review-prompt.sh {PR番号}` でプロンプト生成（Codex 自動呼び出しを行わない）
2. 生成されたプロンプトをこのセッションの Claude Code に貼り付けてレビュー依頼
3. レビュー結果を `review-result-pr{N}-{R}.md` に手動保存
4. `/fix {PR番号}` で修正

## レビュー観点

`review-template.md` には以下の観点が含まれています:

1. **コードの正しさ** - ロジックバグ、エッジケース
2. **設計・アーキテクチャ** - レイヤード構成の遵守
3. **セキュリティ** - OWASP Top 10、認可チェック
4. **テストの十分さ** - カバレッジ、正常系/異常系
5. **その他** - 不要コード、パフォーマンス

## 追加の観点でレビューしたいとき

DB マイグレや認可など、特定観点を強くレビューしたい場合、`/review` の後に手動で `codex` を再呼び出ししても良いです:

```bash
codex exec --sandbox workspace-write \
  "PR #42 のレース条件と @RequireRole 抜けに絞ってレビューしてください" \
  >> scripts/review/output/review-result-pr42-1.md
```

これで第二観点レビューを既存結果ファイルに追記できます。

## 注意事項

- `scripts/review/output/` は `.gitignore` 対象（成果物はローカル限定）
- 差分が大きすぎると Codex のコンテキスト上限を超える可能性があります。その場合は PR を分割してください
- `codex exec` は ChatGPT サブスクの usage limit を消費します（Free プランでも動作）
- Codex CLI が未インストール / ログイン切れの場合、`/review` のステップ 8 で明確にエラーが出ます
