# CLAUDE.md

競技かるたの対戦記録管理アプリ（Match Tracker）。Java Spring Boot バックエンド + React フロントエンドのモノレポ。
開発スキル・エージェントは **devflow プラグイン**（poponta2020/claude-devflow）で提供される。プロジェクト固有設定は [.claude/project-profile.md](.claude/project-profile.md) が正典。

## 構成

- `karuta-tracker/` — Spring Boot 3.4.1 / Java 21 / Gradle。controller → service → repository → entity のレイヤード構成。DTO は `fromEntity()`、ロール制御は `@RequireRole`（SUPER_ADMIN / ADMIN / PLAYER、localStorage + ダミートークンのプロトタイプ認証）
- `karuta-tracker-ui/` — React 19 / Vite / Tailwind v3。`api/`（Axios）・`pages/`・`components/`・`context/`（AuthContext）
- `database/` — SQL スキーマ・マイグレーション。論理削除は `deleted_at`
- DB はローカル・本番とも Render PostgreSQL。接続情報は `CLAUDE.local.md`（gitignore 対象）。デプロイは Render.com、CI は GitHub Actions（JUnit + Jacoco 60%）

## 改修時のナビゲーション（全域 grep の前に docs を起点にする）

1. UI・画面の改修 → まず [docs/design/design.md](docs/design/design.md)（脱AIスロップ・デザイン正典。色/タイポ/レイアウト・固有 Don'ts を**参照・厳守**）→ [docs/SCREEN_LIST.md](docs/SCREEN_LIST.md) で画面→コンポーネントを特定
2. 機能・API・フローの改修 → [docs/SPECIFICATION.md](docs/SPECIFICATION.md)（ハブ）→ 該当 `docs/spec/<ドメイン>.md`（冒頭に主要実装パスあり）
3. DB スキーマ → `docs/design/db.md`／アーキテクチャ・権限 → `docs/design/architecture.md`
4. 類似の過去改修を探す → [docs/features/INDEX.md](docs/features/INDEX.md)

## DBマイグレーション適用ルール（最重要）

**`database/` 配下に SQL を追加・変更したら、必ず本番 DB（Render PostgreSQL）にも適用する。**（過去に PR #500 で適用漏れ→本番500エラー Issue #518 の実害あり）

1. entity（`@Column`）変更とマイグレーション SQL は**同じ PR に含める**
2. PR 作成時に「本番 DB 適用が必要」とユーザーに明示する
3. 適用は `CLAUDE.local.md` の接続情報で `psql -f database/<SQL>` → `\d <table>` で反映確認
4. 「次のデプロイで一緒に」と先送りしない（必ず忘れる）

## 開発フロー

```
[設計セッション: /model opus]
/grill-me → /define-feature（要件承認後は技術計画→implementation-plan→Issue までノンストップ）
           ⇄ /design-screen（UI がある場合の収束ループ）
[実行セッション: opusplan + advisor（既定設定）]
/implement（起動=実装GO）→ /prepare-pr → /auto-review-loop（Codex⇄/fix、pass で即終了）→ /ship（DoDゲート）
※ devflow v0.8.0〜v0.9.0 で AC適合・追加/code-review・/verify・マージ前のCI待ちを標準ループから除外（時間対効果の実測による。支障が出たら再装着。マージ後にCIが赤なら追修正）
```

- 成果物は `docs/features/<slug>/`（requirements.md / design-spec.md / implementation-plan.md）
- 小さな修正は `/quickfix`、バグは `/bug-report`、既存機能監査は `/audit-feature` → `/fix-feature`
- アプリ起動は `/startapp`（プロジェクトスキル）

## 開発ルール

1. **承認ポイントは要件確定の1箇所のみ**。実装開始の GO は `/implement` の起動そのもの
2. **認識合わせ**: 不明点・あやふやな点が1つでもあれば実装前に必ず質問する。推測・仮定で進めない。ユーザーの指示がベストプラクティスに反する場合は懸念と代替案を提示して確認する（最終判断はユーザー）
3. **影響範囲の調査義務**: 変更前に上流（呼び出し元）・下流（呼び出し先）・FE⇔BE 連携への影響を調査する。影響が広い場合は着手前に報告する
4. **ドキュメント更新**: 機能追加・変更時は profile の `## docs`（docs レジストリ）に従い、該当する正典ファイル（`docs/spec/<ドメイン>.md` 等）を実装と同じコミットで in-place 更新する。更新漏れは DoD の D2 チェックが検出する
5. **memory記録**: 設計判断/バグ修正/完了/フィードバック時に `.claude/memory/` へ必ず記録（MEMORY.md 索引も更新）
6. **1PR = 1機能**。ついでリファクタ禁止。Phase 外の要望は memory に記録して混ぜない

## モデル・委譲

- セッション既定は `opusplan`（Plan=Opus / 実行=Sonnet）+ Advisor=Opus。要件定義・設計は `/model opus` の設計セッションで行う
- サブエージェント委譲は**コンテキスト隔離・作業独立性**で判断（正典: devflow の implement スキル）。調査は機械的列挙=Explore(haiku)／判断込み=Explore(sonnet)／核心は main 自読
