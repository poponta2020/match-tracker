# Cross Review ワークフロー ガイド

Claude Code と Codex を使った相互レビュー開発フローです。

## 概要

```
┌─────────────┐     PR作成      ┌──────────────┐
│  実装担当AI   │ ──────────────→ │  /review 実行  │
│ (Claude/Codex)│               │  プロンプト生成  │
└─────────────┘               └──────┬───────┘
       ↑                             │
       │                    コピペして貼り付け
       │                             ↓
┌──────┴──────┐  レビュー結果  ┌──────────────┐
│  /fix 実行   │ ←──────────── │  レビュー担当AI  │
│  修正実施     │    ファイル保存  │ (Codex/Claude) │
└─────────────┘               └──────────────┘
       │
       │ 再push
       ↓
   再レビューが必要なら /review → ループ
```

## ファイル構成

```
scripts/review/
├── CROSS_REVIEW_GUIDE.md     ← このファイル
├── review-template.md        ← レビュープロンプトのテンプレート
├── fix-template.md           ← 修正依頼プロンプトのテンプレート
├── generate-review-prompt.sh ← レビュープロンプト生成スクリプト
├── generate-fix-prompt.sh    ← 修正依頼プロンプト生成スクリプト
└── output/                   ← 生成されたプロンプト（自動作成）
    ├── review-prompt-pr{N}.md
    ├── review-result-pr{N}.md  ← 手動で保存
    └── fix-prompt-pr{N}.md
```

## 使い方

### ステップ 1: 実装（実装担当AI）

通常通り実装を行い、PR を作成します。

```
（Claude Code or Codex で実装）
→ git commit & push
→ gh pr create
```

Claude Code を使っている場合、PR作成後に自動で `/review` の案内が表示されます。

### ステップ 2: レビュープロンプト生成

**方法A: スラッシュコマンド（Claude Code 内）**
```
/review          ← 現在のブランチのPRを自動検出
/review 42       ← PR #42 を指定
```

**方法B: シェルスクリプト**
```bash
./scripts/review/generate-review-prompt.sh       # 自動検出
./scripts/review/generate-review-prompt.sh 42    # PR #42 を指定
```

→ `scripts/review/output/review-prompt-pr{N}.md` が生成されます。

### ステップ 3: レビュー実施（レビュー担当AI）

1. 生成されたプロンプトファイルの内容をコピー
2. レビュー担当AI（Codex or Claude Code）に貼り付け
3. レビュー結果が返ってくる

### ステップ 4: レビュー結果の保存

レビュー結果を以下のファイルに保存します：

```
scripts/review/output/review-result-pr{N}.md
```

### ステップ 5: 修正（実装担当AI）

**方法A: スラッシュコマンド（Claude Code 内）**
```
/fix          ← 現在のブランチのPRを自動検出
/fix 42       ← PR #42 を指定
```

**方法B: シェルスクリプト + 手動コピペ**
```bash
./scripts/review/generate-fix-prompt.sh 42
```
→ `scripts/review/output/fix-prompt-pr{N}.md` が生成されるので、実装担当AIに貼り付け。

### ステップ 6: 再レビュー（必要な場合）

修正をpush後、ステップ 2 に戻って再レビュー。

## 役割の入れ替え

タスクによって実装担当とレビュー担当を自由に入れ替えられます。

| パターン | 実装 | レビュー |
|---------|------|---------|
| A | Claude Code | Codex |
| B | Codex | Claude Code |

決め方の目安：
- **バックエンド中心の変更** → Spring Boot に強い方を実装に
- **フロントエンド中心の変更** → React に強い方を実装に
- **どちらでもよい場合** → 前回と逆にする（視点の偏りを防ぐ）

## レビュー観点

テンプレートには以下の観点が含まれています：

1. **コードの正しさ** - ロジックバグ、エッジケース
2. **設計・アーキテクチャ** - レイヤード構成の遵守
3. **セキュリティ** - OWASP Top 10、認可チェック
4. **テストの十分さ** - カバレッジ、正常系/異常系
5. **その他** - 不要コード、パフォーマンス

## 注意事項

- `scripts/review/output/` は `.gitignore` に追加することを推奨
- 差分が大きすぎる場合、AIのコンテキストウィンドウに収まらない可能性があります。その場合はPRを分割してください
- レビュー結果の保存時、ファイル名を正確に合わせてください（`review-result-pr{N}.md`）
