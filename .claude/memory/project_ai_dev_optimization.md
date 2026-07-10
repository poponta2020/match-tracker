---
name: project-ai-dev-optimization
description: AI開発最適化（docs構造・ナビゲーション・書き込み規律・DoDゲート）の要件定義完了。3リポジトリ横断・親Issue #1010
type: project
category: feature-definition
tags: [ai-dev-optimization]
---

# AI開発最適化 要件定義（2026-07-10 承認）

## 主要な設計判断と理由

- **「読み方ガイド」案は棄却し、ドメイン分割（ハブ≤200行 + docs/spec/<ドメイン>.md 各100〜500行 + docs/design/db.md）を採用**。理由: AIによる数千行ファイルの継続編集は一意マッチ失敗の実証済み脆弱点で、行番号TOCは行ドリフトで有害。既に SPEC/DESIGN で番号破綻・players 定義ドリフト（admin_organization_id 有無の不一致）が発生していた
- **docs 書き込みの実態**: ship/implement に docs 更新指示はなく gate-dod にもチェックなし。quickfix にリポジトリ固有ファイル名がプラグインへハードコード。→ profile §docs レジストリ化+gate-dod docs チェック（FAIL+PR本文 `Docs: no-change-needed（理由）` オプトアウト、レジストリ未定義は SKIP）で構造化
- **docs/features/ は変更履歴レイヤーとして維持**（OpenSpec の changes/ 相当）。本体 docs への changelog 追記は禁止、見出しの連番は廃止（名前見出し+ファイルパス粒度参照・行番号禁止・コード断片コピー禁止）
- **kagetra-new は全体仕様書をドメイン分割形式でフル新規作成**（リバースエンジニアリング。match-tracker 移行のリファレンス実装を兼ねる）
- **スコープは1機能・3ブロック（P:プラグイン→R:match-tracker→K:kagetra）**。Issue は match-tracker に集約、PR は各リポジトリ
- プラグイン制約: 正規クローン C:\Users\popon\.claude\plugins\marketplaces\claude-devflow が origin より4コミット先行（v0.2.0〜v0.4.0 未push）かつ実行中は cache の 0.2.0 → 改修前に push・改修は新バージョン・利用版更新までが完了条件

## Acceptance Criteria 要約

全22件（auto-test 13 / verify 5 / manual 6）。本丸は AC-R10「代表改修シナリオ2件で新セッションの AI が全域走査なしに docs 起点2〜3ホップで実装ファイルに到達」（ナビゲーション実測）と AC-R5「db 定義が本番 PostgreSQL introspect と一致」。

## Issue / タスク

- 親: #1010
- 子: #1011（P:ゲート）→ #1012（P:スキル+リリース）／#1013（R:対応表）→ #1014（R:移植+db統合）→ #1015（R:ハブ化）→ #1016（R:レジストリ+ルーティング）／#1017（K:衛生）→ #1018（K:仕様書）
- タスク8件。P→R→K 順、R ブロック内は 3→4→5→6 の直列。K は kagetra セッションで実行（要件定義書を絶対パス参照）
- 成果物: docs/features/ai-dev-optimization/{requirements.md, implementation-plan.md}
