# Project Profile — Match Tracker

devflow プラグインのスキルが読む、このプロジェクトの唯一の設定ファイル。

## commands

テスト・lint・typecheck コマンド（gate-dod.sh・/fix・/implement が使用。リポジトリルートから実行される）
<!-- devflow:commands -->
```sh
DEVFLOW_TEST_CMDS=("cd karuta-tracker && ./gradlew test" "cd karuta-tracker-ui && npm run test")
DEVFLOW_LINT_CMDS=("cd karuta-tracker-ui && npm run lint")
DEVFLOW_TYPECHECK_CMDS=()
```
<!-- /devflow:commands -->

- E2E は未整備。CI（GitHub Actions: JUnit + Jacoco 60% / room-checker / Vitest）が最終網
- バックエンドの単一テスト実行: `./gradlew test --tests "com.karuta.matchtracker.service.XxxTest"`

## run

`/startapp` プロジェクトスキルを使う（バックエンド: `./gradlew bootRun` + Render PostgreSQL 環境変数、フロント: `npm run dev`）。
ヘルスチェック: バックエンド `http://localhost:8080/api/players`、フロント `http://localhost:5173`。

## worktree

既定（Windows: /c/tmp、Linux: /tmp）。

## branches

- ベースブランチ: `main`。機能は `feature/<slug>` ブランチ + PR
- main への直接コミットは `/ship` の docs・memory 同期コミットのみ許可
- PR マージは `gh pr merge --merge --delete-branch`

## database

- ローカル・本番とも **Render PostgreSQL**（同一インスタンス）。接続情報は `CLAUDE.local.md`（gitignore 対象。無ければ Render ダッシュボードの Connect タブ）
- **`database/*.sql` を追加・変更したら本番 DB への psql 適用が必須**（CLAUDE.md の最重要ルール参照。entity 変更と同一 PR に含める）
- 論理削除パターン: `deleted_at` カラム

## prod-logs

本番ログ取得: `scripts/render-logs/Get-RenderLogs.ps1`（PowerShell。Render API 使用）

## design-system

- claude.ai/design プロジェクト名: **Match Tracker Design System**
- デザイントークン: `karuta-tracker-ui/tailwind.config.js`、`karuta-tracker-ui/src/index.css`

## review-extra

Codex レビュー・code-review に追加するプロジェクト固有観点:
- エンドポイントの `@RequireRole` 設定漏れ（ロール: SUPER_ADMIN / ADMIN / PLAYER）
- 認証は localStorage + ダミートークンの**プロトタイプ段階** — 本人性検証の厳密さについて過剰指摘しない
- `database/*.sql` の変更と entity（`@Column`）変更が同一 PR に揃っているか（片方だけは事故のもと）
- 論理削除（`deleted_at`）パターンの一貫性。物理 DELETE の混入に注意
- FE⇔BE のリクエスト/レスポンス形式の不一致（api/ クライアントと DTO の齟齬）

高リスクパス（レビュー effort を high に上げる対象）:
- `karuta-tracker/src/main/java/**/interceptor/**`、`**/annotation/**`（認証・認可）
- `database/**`（スキーマ変更）
- `**/scheduler/**`（スケジュールタスク・通知系）

## conventions

task-implementer（実装ワーカー）が厳守する実装規約:
- バックエンドは controller → service → repository → entity のレイヤードアーキテクチャを崩さない。層をまたぐショートカット禁止
- Entity ↔ DTO 変換は DTO 側の `fromEntity()` 静的メソッドパターンを踏襲
- 新規エンドポイントには `@RequireRole` を必ず付与（既存の同種エンドポイントに倣う）
- DB スキーマ変更（entity の `@Column` 追加等）が必要と判明したら**停止して報告**（migration SQL と本番適用が絡むため main が担当）
- フロントは既存の `api/` クライアントパターン（client.js 共通設定）と `pages/` 構成に従う
- ユーザー向け文言は日本語
- ドキュメント更新は `## docs`（docs レジストリ）に従い、正典ファイルを実装と同じコミットに含める

## docs

docs レジストリ（/quickfix・/implement・/audit-feature・gate-dod.sh が参照）。

**事実タイプ→正典ファイル（1つの事実は1ファイルにのみ書く）:**
- 機能仕様・フロー・API → `docs/spec/<ドメイン>.md`（索引: `docs/SPECIFICATION.md`。抽選の API 詳細のみ `docs/spec/lottery-api.md`）
- 画面一覧・ルーティング・ナビゲーション・画面遷移 → `docs/SCREEN_LIST.md`
- テーブル定義・ER図・初期データ → `docs/design/db.md` + `db-tables-*.md`（本番 introspect 照合済み。**他ファイルへのカラム定義表の記載禁止**）
- アーキテクチャ・権限・API共通仕様・デプロイ・開発環境 → `docs/design/architecture.md`（索引: `docs/DESIGN.md`）
- 未実装・TODO → `docs/spec/backlog.md`／用語 → `docs/spec/glossary.md`
- 変更履歴 → `docs/features/<slug>/`（索引: `docs/features/INDEX.md`。本体 docs への履歴追記禁止）
- 開発プロセス → `docs/dev/feature-flow.md`

**更新手順:** 該当ドメインファイルを特定 → 見出しを Grep → そのセクションだけ in-place 更新（実装と同じコミットに含める）
**書き込み規律:** 見出しに連番を付けない／実装参照はファイルパス粒度（行番号禁止）／コード断片のコピー禁止

gate-dod.sh の D2 チェック用パスパターン:
<!-- devflow:docs -->
```sh
DEVFLOW_SRC_PATTERNS=("karuta-tracker/src/" "karuta-tracker-ui/src/" "database/")
DEVFLOW_DOCS_PATTERNS=("docs/" "CLAUDE.md" "karuta-tracker/CLAUDE.md" "karuta-tracker-ui/CLAUDE.md")
```
<!-- /devflow:docs -->

## worklog

未運用（`/ship` のワークログ追記はスキップされる）。運用を始める場合はここにパスを書く（例: `docs/worklog.md`）。
