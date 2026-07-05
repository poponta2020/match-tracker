# モデル階層委譲(Model Delegation)

> main 会話(Opus/Fable 等)をオーケストレーターとして維持し、委譲可能な葉ステップだけを下位モデル(Sonnet)のサブエージェントへ流すための正典。機能開発フロー骨格の設計は [feature-flow.md](feature-flow.md)。kagetra_new プロジェクトでの先行導入(2026-07-04)を移植し、match-tracker 向けに書き換えたもの。

## 原則

1. **main のモデルは切り替えない。** プロンプトキャッシュはモデル別なので、main を途中で安いモデルに切り替えると全履歴を非キャッシュで読み直す。「main は固定・安い作業はサブエージェントへ切り出す」が基本形
2. **判断は上・作業は下。** 要件定義・設計判断・計画・裁定・本番操作・DB マイグレーション適用は main。仕様が確定した作業だけを下位へ
3. **モデルは常に明示指定。** 指定なしのサブエージェントは main と同モデルで走る。Agent tool 呼び出し時に `model` を必ず渡す

## ルーティング表

| 層 | モデル | 対象タスク | 機構 |
|---|---|---|---|
| ワーカー | Sonnet | 実装手順書で完全仕様化された単一実装タスク(バックエンド/フロントエンド問わず) | Agent tool: `subagent_type: task-implementer`(`.claude/agents/task-implementer.md`、model: sonnet / effort: high 埋め込み済み) |
| オーケストレーター | main(Opus/Fable 等) | 要件定義・設計判断・計画、曖昧なデバッグ、跨層リファクタ、DB マイグレーション、認可の新設、委譲結果の受け入れ確認、コミット/PR/ship、本番操作 | main 会話(スキル骨格) |

**スカウト層(Haiku による read-heavy 調査委譲)は現時点で未配線。** 導入する場合は本ドキュメントとルーティング表を更新すること。

**レビューは従来どおり `/review`(Codex 連携)が担う。** モデル委譲はレビュー工程には介入しない。

## 既存の専門特化エージェントとの関係(重要)

`spring-boot-engineer` / `react-specialist` / `postgres-pro` / `security-auditor` / `test-automator` の5エージェントは `.claude/agents/` に定義済み(全て `model: opus`)。

- `security-auditor` / `test-automator` は `/bug-report`・`/quickfix` から従来どおり利用する(セキュリティ関連調査・テスト失敗時の分析)。**このドキュメントの対象外**
- `spring-boot-engineer` / `react-specialist` / `postgres-pro` は **`task-implementer` 導入に伴い `/implement` からの呼び出しを廃止した**。技術領域(Spring Boot/React/DB)による機械的な振り分けをやめ、`/implement` は4条件判定 → `task-implementer` 一本化に統一する。エージェント定義自体は削除せず残してあるので、将来的にアーキテクチャ判断が要る大規模タスク向けに再配線する余地は残す(現状は未配線)

## 委譲可否の判定(4条件すべて満たす場合のみ委譲)

1. **仕様完全性**: 要件定義書+実装手順書でタスクが完全に記述されている(変更対象ファイル・完了条件が明確)
2. **設計判断の余地なし**: API の形・データモデル・UI 挙動の解釈が確定済み
3. **検証手段あり**: テスト・lint・ビルドで失敗を機械的に検知できる
4. **高リスク領域を含まない**: `database/` 配下の SQL マイグレーション追加・変更、`@RequireRole` 等の認可ロジックの新設・変更、本番(Render)操作を含まない

1つでも欠けたら main が直接実装する。迷ったら main。

条件4を特に厳格にする理由: 過去に PR #500 で `priority_player_ids` カラム追加 SQL を書いたが本番適用を忘れ、本番500エラー(Issue #518)が発生した実績があるため([CLAUDE.md](../../CLAUDE.md) 参照)。マイグレーションは常に main が扱う。

## 委譲の作法

| 失敗パターン | この運用での対策 |
|---|---|
| 仕様不足の丸投げ → ワーカーが「もっともらしい推測」で誤った方向へ | タスク仕様は**要約せず全文**をプロンプトに貼る。task-implementer には「迷ったら停止して報告」規約を埋め込み済み(自分で判断させない) |
| ワーカーの effort 不足で品質劣化 | task-implementer は effort: high 固定 |
| 能力ミスマッチが「成功」の顔で静かに通る | 委譲後に main が必ず `git diff` をレビューし検証コマンドを自分でも再実行してからコミット。後段に CI + レビュー(`/review`)の網 |

エスカレーション: ワーカーが停止報告を返したら、その論点は main が引き取って判断・実装する。同じタスクを下位モデルに再委譲してリトライしない。

## 運用ルール

- **小径修正は委譲しない**(目安: 数ファイル・数十行以下)。サブエージェント起動と文脈再構築のオーバーヘッドの方が高くつく。`/quickfix`・`/bug-report` の修正実装が main 直なのはこのため
- **ワーカーの並列実行は worktree を分ける**。同一 worktree では1タスクずつ直列(`/implement` は元々直列ループ)
- **テストファーストを brief で担保**: 実装手順書にテストが無いタスクを委譲するときは、main がテスト要件を決めて brief に含める
- **品質フィードバックループ**: 受け入れ確認 NG が同種タスクで2回続いたら、その種別を委譲対象から外して本ドキュメントを更新する

### 各スキルの委譲対応(配線状況)

| スキル | 委譲 |
|---|---|
| `/implement` | Step 7 で4条件判定 → `task-implementer`(配線済み) |
| `/quickfix` | 修正実装は小径のため main 直(配線済み)。テスト失敗時は `test-automator`、セキュリティ関連は `security-auditor` を利用(既存配線、変更なし) |
| `/bug-report` | 原因調査・修正は main 直。セキュリティ関連調査は `security-auditor` を利用(既存配線、変更なし) |
| `/fix`(レビュー指摘修正) | main 直。レビュー指摘の文脈が濃く小径のため委譲しない |
| `/fix-feature` | `/define-feature`・`/implement` に準ずる |
| `/define-feature`・`/design-screen`・`/prepare-pr`・`/ship`・`/dispatch`・`/tech-explore`・`/audit-feature` | 委譲なし(要件定義・視覚判断・side-effect・レビュー裁定・ルーティングは main) |

## match-tracker 固有の高リスク領域(委譲禁止・main 直の対象)

- `database/` 配下の SQL 追加・変更(マイグレーション)とその本番適用
- `@RequireRole` の新設・変更、`interceptor/` の認証チェックロジック
- Render 本番環境への操作(再起動・環境変数変更・ログ取得を除く直接操作)
- `CLAUDE.local.md` に記載の接続情報・APIキーを扱う作業

> このドキュメントは `/implement` の SKILL.md および `.claude/agents/task-implementer.md` から参照される正典。ルーティングを変えるときはこの1ファイルを直す。
