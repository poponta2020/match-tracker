---
status: completed
completed_sections: [ユーザーストーリー, 機能要件, Acceptance Criteria と Non-goals, 技術的制約・契約]
design_required: false
approved: 2026-07-10
---
# AI開発最適化（ai-dev-optimization）要件定義書

対象: match-tracker / kagetra-new / devflow プラグイン（poponta2020/claude-devflow）の3リポジトリ横断。
コーディング機能ではなく「AI駆動開発の基盤（docs構造・ナビゲーション・書き込み規律・ゲート）」の改修。

## 1. 概要

- **目的**: 改修時に AI がリポジトリ全体を探索するムダをなくし、維持されている docs を起点に改修箇所へ直行できるようにする。同時に、docs 自体の品質劣化（見出し番号破綻・二重管理ドリフト）を構造的に止める。
- **背景・動機**（2026-07-10 の調査セッションで確定した事実）:
  - match-tracker の3大ドキュメント（SPECIFICATION 2,968行 / DESIGN 3,668行 / SCREEN_LIST 316行）は同一コミット更新が実践されているが、書き込み規律が未定義のままアドホックに更新されており、既に番号破綻（SPEC §3.9→§3.8→§3.20、DESIGN §4.10重複・§4.12欠番）と実ドリフト（players テーブル定義が SPEC §6.2 と DESIGN §3.2 で不一致 — admin_organization_id の有無、ENUM/DATETIME 等 MySQL 残滓）が発生している
  - docs への書き込みを構造的に担うスキルは存在しない（ship/implement に指示なし、gate-dod.sh に docs チェックなし。quickfix Step 7 に match-tracker 固有の3ファイル名がプラグインへハードコードされているのみ）
  - 2026年のゴールデンスタンダード（外部調査済み・出典は会話ログと memory）: 「lean な CLAUDE.md（ルーティング指示）→ 維持された docs をオンデマンド参照 → 残りは agentic search」。数千行ファイルへの AI 編集は一意マッチ失敗の既知の脆弱点で、1ファイル100〜500行への分割が唯一の実効的緩和策。行番号入り TOC・自動生成網羅マップ・巨大ファイルの @import はアンチパターン

## 2. ユーザーストーリー

- **対象ユーザー**: このリポジトリ群の開発者（1人開発、devflow プラグインによる AI 駆動開発フロー: /define-feature → /implement → /auto-review-loop → /ship）。実質的な「利用者」は毎セッションの Claude Code / Codex。
- **ユーザーの目的**:
  1. 改修依頼のたびに AI が全域 grep・全ファイル読込をする時間・トークンの浪費をなくす
  2. 出荷のたびに docs が壊れていく（番号破綻・ドリフト）流れを止め、docs を信頼できる探索起点として維持する
  3. 仕組みは devflow プラグイン側に汎用化し、match-tracker / kagetra-new の両方（および将来のリポジトリ）で同じ規律が働くようにする
- **利用シナリオ**: 「〇〇画面の△△を直して」という依頼に対し、AI が CLAUDE.md のルーティング指示 → ハブ/SCREEN_LIST → 該当ドメインファイル → 実装ファイルパス、と2〜3ホップで改修箇所に到達する。出荷時は該当ドメインファイル1つ（+必要なら db.md / SCREEN_LIST の1行）だけを in-place 更新し、DoD ゲートが docs 更新漏れを機械検出する。

## 3. 機能要件

### 3.1 変更対象の地図（画面インベントリに相当。UI なしのため対象リポジトリ×成果物で記載）

**ブロックP: devflow プラグイン（汎用機構）**
- P1: quickfix Step 7 のハードコード（SPECIFICATION 等3ファイル名）を project-profile §docs レジストリ参照に変更
- P2: gate-dod.sh に「docs 更新漏れチェック」を追加
- P3: implement スキルに docs 更新ステップを明記（profile §docs レジストリに従い該当ドメインファイルを in-place 更新）
- P4: features INDEX の自動維持 — /define-feature（新規スラッグ作成時）と /ship（出荷時）に docs/features/INDEX.md への1行追記を組み込む

**ブロックR: match-tracker（再構成）**
- R1: 3大ドキュメントの再構成 — SPECIFICATION.md / DESIGN.md を200行以下のハブに縮退し、本文を docs/spec/<ドメイン>.md（機能仕様+フロー+API+関連画面名+実装ファイルパス統合、各100〜500行）と docs/design/db.md（ER図+テーブル定義の唯一の置き場）へ分割。SCREEN_LIST.md は画面のSSOTとして現状維持。docs/features/ は変更履歴レイヤーとして現状維持。AGENTS.md（Codex 用）内の docs 参照も追随更新
- R2: 既知ドリフトの修正 — players テーブル定義の不一致を本番 PostgreSQL の introspect 結果を正として db.md で一本化・解消
- R3: CLAUDE.md にルーティング指示（3〜5行）を追加（UI改修→SCREEN_LIST、機能・API→ハブ→ドメインファイル、過去改修→features/INDEX.md）
- R4: ネスト CLAUDE.md 新設 — karuta-tracker/CLAUDE.md（レイヤー構成・DTO fromEntity・@RequireRole・命名規約）、karuta-tracker-ui/CLAUDE.md（pages/api/components 構成・命名規約）。各50行以下
- R5: docs/features/INDEX.md 新設（既存90スラッグ+本件: スラッグ+1行説明+主要変更領域）
- R6: project-profile §docs を「docs レジストリ」に昇格（§3.2 の仕様）

**ブロックK: kagetra-new（適用+新規仕様書）**
- K1: CLAUDE.md の stale 修正 — 本番環境 Lightsail→Oracle Cloud、「apps/api は現状スケルトン。API 実処理は apps/web/src/app/api/（BFF）」の明記
- K2: apps/web/_*.mts 診断スクリプト105本を scripts/ 配下（例: scripts/diagnostics/）へ一括移動+gitignore。「診断スクリプトを apps/ 直下に作らない」ルールを CLAUDE.md/profile に明記
- K3: apps/web/CLAUDE.md 新設（App Router 構成・lib/ モジュール・components 規約）。50行以下
- K4: memory 索引の差分解消（実ファイル130 vs 索引113 → 差分0に）
- K5: 全体仕様書のフル新規作成 — 最初からドメイン分割形式（ハブ SPECIFICATION.md ≤200行 + docs/spec/<ドメイン>.md 各≤500行 + docs/design/db.md）。コードベースからのリバースエンジニアリングで作成し、以後は出荷時 in-place 更新の規律に乗せる
- K6: docs/features/INDEX.md 新設（既存28スラッグ）
- K7: project-profile §docs のレジストリ化

**実行順序**: P → R → K（P のゲート・スキル改修が R/K の運用前提。K は kagetra セッションで実行し、本要件定義書を絶対パスで参照する）

### 3.2 ビジネスルール（docs レジストリ仕様・書き込み規律・ゲート仕様）

**docs レジストリ（project-profile §docs）が持つべき内容**:
1. 事実タイプ→正典ファイルの対応表（例: 画面→SCREEN_LIST.md、テーブル定義→docs/design/db.md、機能仕様・フロー・API→docs/spec/<ドメイン>.md、変更履歴→docs/features/<slug>/）
2. 更新手順（該当ドメインファイルを特定→見出しを Grep→そのセクションだけ in-place 更新）
3. 書き込み規律: 連番見出しの新設禁止（安定した名前見出しを使う）／実装参照はファイルパス粒度（行番号を書かない）／コード断片のコピー禁止／本文への changelog 追記禁止（履歴は docs/features/ と git に任せる）
4. gate-dod 用のパスパターン定義（src と見なすパスパターン、docs と見なすパスパターン）

**gate-dod docs チェックの仕様（FAIL+明示オプトアウト）**:
- PR 差分に src パターンのファイルが含まれ、かつ docs パターンの差分が 0 → **FAIL**（SHIP 不可）
- ただし PR 本文に `Docs: no-change-needed（理由）` の行があれば PASS（SKIP 表示・理由をゲート結果に転記）
- profile に §docs レジストリ（パスパターン定義）が無いリポジトリでは SKIP（既存 run_cmds の「未定義なら SKIP」思想と同一）

**ドメイン分割の規律（R1/K5 共通）**:
- ハブ（SPECIFICATION.md / DESIGN.md）は200行以下。「ドメイン名+1行の責務説明+リンク」のみで、行番号は書かない
- ドメインファイルは1ファイル100〜500行。超過しそうなら分割（例: 会場管理と予約連携を分ける）
- 各ドメインファイルの冒頭に定型メタブロック（責務1行・関連画面名・主要実装パス列挙）
- 1つの事実は1ファイルにのみ存在（SSOT）。他ファイルからは参照リンクのみ

### 3.3 エラーケース・境界条件

- 分割時のコンテンツ欠落 → AC-R3 の機械照合で検出（旧見出し→新配置の対応表を生成し、全見出しの対応を照合）
- gate-dod の誤 FAIL（純リファクタ等） → オプトアウト行で意図的省略のみ許可。オプトアウト行があるのに docs 変更もある場合は通常判定（オプトアウトは無視）
- プラグイン改修と利用側リポジトリのバージョン不整合 → §6 の制約（リリース手順）で担保
- kagetra リバースエンジニアリングの誤記述 → AC-K6 のサンプル照合レビューで抑止（全数保証はしない。以後の出荷時 in-place 更新で漸進修正する方針）

## 4. Acceptance Criteria

| ID | 条件（客観的に判定できる文） | 検証手段 |
|----|------|------|
| AC-P1 | profile に §docs レジストリ（パスパターン）が無いリポジトリで gate-dod.sh の docs チェックが SKIP になる | verify |
| AC-P2 | src 変更あり・docs 差分ゼロ・オプトアウトなしの PR で gate-dod.sh が FAIL を返す | verify |
| AC-P3 | 同条件で PR 本文に `Docs: no-change-needed` 行があれば PASS（SKIP 表示）になる | verify |
| AC-P4 | quickfix の SKILL.md にリポジトリ固有 docs ファイル名（SPECIFICATION/SCREEN_LIST/DESIGN）のハードコードが存在しない | auto-test |
| AC-P5 | implement に profile §docs 準拠の docs 更新ステップ、define-feature/ship に features INDEX 追記ステップが記載されている | manual |
| AC-P6 | 改修後プラグインで従来型 PR（docs 変更を含む）の DoD ゲートが従来どおり PASS する（回帰なし） | verify |
| AC-R1 | match-tracker の SPECIFICATION.md / DESIGN.md が各200行以下のハブで、全ドメインファイルへのリンクを持つ | auto-test |
| AC-R2 | docs/spec/*.md と docs/design/db.md が存在し、各ファイル500行以下である | auto-test |
| AC-R3 | 旧 SPECIFICATION/DESIGN の全セクション見出しが「旧見出し→新配置」対応表で追跡でき、機械照合が pass する（欠落ゼロ） | auto-test |
| AC-R4 | カラム定義表が docs/design/db.md 以外の docs に存在しない | auto-test |
| AC-R5 | db.md の players / matches テーブル定義が本番 PostgreSQL の introspect 結果と一致する | verify |
| AC-R6 | CLAUDE.md にルーティング指示があり、全体が200行以下のまま | manual |
| AC-R7 | karuta-tracker/CLAUDE.md と karuta-tracker-ui/CLAUDE.md が存在し各50行以下 | auto-test |
| AC-R8 | docs/features/INDEX.md が features/ 配下の全スラッグディレクトリを網羅している | auto-test |
| AC-R9 | project-profile §docs が §3.2 のレジストリ仕様（対応表・更新手順・書き込み規律・パスパターン）を満たす | manual |
| AC-R10 | 代表改修シナリオ2件（UI 系1件・API/ロジック系1件）で、新セッションの AI が全域走査なしに docs 起点2〜3ホップで正しい実装ファイルを特定する | manual |
| AC-K1 | kagetra CLAUDE.md に「Lightsail」が存在せず、Oracle Cloud と BFF（apps/api スケルトン）注記がある | auto-test |
| AC-K2 | apps/web 直下に _*.mts が0本。移動先に格納され、以後の生成先ルールが CLAUDE.md/profile に記載されている | auto-test |
| AC-K3 | apps/web/CLAUDE.md が存在し50行以下 | auto-test |
| AC-K4 | .claude/memory/ の実ファイルと MEMORY.md 索引の差分が0 | auto-test |
| AC-K5 | kagetra に全体仕様書（ハブ≤200行 + docs/spec/<ドメイン>.md 各≤500行 + docs/design/db.md）が存在する | auto-test |
| AC-K6 | 主要3ドメイン（試合結果取込・イベント/出欠・選手管理を想定）の仕様記述がコードの実挙動と一致することをレビューで確認 | manual |
| AC-K7 | kagetra docs/features/INDEX.md が全スラッグを網羅している | auto-test |
| AC-K8 | kagetra project-profile §docs がレジストリ仕様を満たす | manual |

manual は AC-P5 / AC-R6 / AC-R9 / AC-R10 / AC-K6 / AC-K8 の6件（うち R10・K6 がユーザー確認の本丸。他はレビュー時確認）。

## 5. Non-goals

- CI でのドキュメントドリフト自動検出（claude-code-action 等）— 次段の改善候補として memory 記録に留める
- llms.txt の導入、AGENTS.md 標準（クロスベンダー標準）への移行（既存 AGENTS.md の参照更新は R1 に含むが、標準化対応はしない）
- ベクトルインデックス・RAG・コードマップ自動生成の導入
- docs の内容自体の網羅性向上・リライト（構造の変更のみ。K5 の kagetra 仕様書新規作成を除く）
- kagetra apps/api（Hono スケルトン）の実装方針変更
- devflow プラグインへのテスト基盤（bash テストハーネス）の新設 — ゲート検証はシナリオ verify で行う
- match-tracker / kagetra の docs 以外のリファクタリング

## 6. 技術的制約・契約

- **互換性**: SPECIFICATION.md / DESIGN.md のファイル名は削除せずハブとして残す（audit-feature 等の既存スキル・既存リンクを壊さない）。docs/features/ の既存スラッグ構造は変更しない
- **汎用性**: devflow プラグインは複数リポジトリ共通。リポジトリ固有情報（docs ファイル名・パスパターン）はスキル/スクリプトにハードコードせず profile §docs から読む
- **プラグインのリリース手順**: 正規クローンは C:\Users\popon\.claude\plugins\marketplaces\claude-devflow（origin=poponta2020/claude-devflow）。現在 main が origin より4コミット先行（v0.2.0〜v0.4.0 の開発履歴が未push）かつ実行中バージョンは cache の 0.2.0。改修前に (1) 既存4コミットを push、(2) 改修は新バージョンとしてコミット、(3) 両リポジトリの利用バージョン（cache）更新までを完了条件とする
- **同一コミットルールの維持**: match-tracker「docs は実装と同じコミット」（CLAUDE.md ルール4）は維持。本機能はその実効性を高める方向のみ
- **コンテンツ保全**: docs 分割は情報の再配置であり削除ではない。旧2ファイルの全セクションが新構造のどこかに対応すること（AC-R3 の機械照合が契約）
- **本番DB照合**: テーブル定義の一本化時は本番 PostgreSQL の introspect を正とする（過去教訓: 本番 matches に CHECK 制約なし・MySQL スキーマとの乖離あり）。接続は CLAUDE.local.md の JDBC ツール（c:\tmp\dbtool\Q.java、IPv4 強制）を使用
- **クロスリポジトリ実行**: ブロックK は kagetra セッションで実行する。本要件定義書は C:\Users\popon\match-tracker\docs\features\ai-dev-optimization\requirements.md を絶対パスで参照
- **Issue 管理**: 親 Issue・子 Issue とも match-tracker リポジトリに集約（進捗の一元管理）。PR は各リポジトリ（match-tracker / kagetra_new / claude-devflow）で作成
- **未解決の技術論点（技術計画フェーズへの申し送り）**:
  1. match-tracker のドメイン分割の具体的な区分け（候補: 練習日管理／対戦組み合わせ／対戦結果・動画・取り札／統計／会場・予約連携（かでる・東区民・隣室・プロキシ）／抽選／メンター／選手・認証・団体／伝助連携／LINE・通知・Push／カレンダー購読 — 見出しマップから確定させる）
  2. gate-dod の src/docs パスパターンの具体値（両リポジトリの profile に定義する内容）
  3. kagetra のドメイン区分け（App Router 構造と 28 スラッグから導出）
  4. features/INDEX.md のフォーマット（ship 追記が最も壊しにくい形式 — 追記型の表 or リスト）
  5. K2 の移動先ディレクトリ名と gitignore パターンの確定

## 7. 設計判断の根拠

- **「読み方ガイド」ではなくドメイン分割を採用**: AI による数千行ファイルの継続編集は一意マッチ失敗・重複挿入の実証済み脆弱点であり、行番号 TOC は行ドリフトでむしろ有害。分割は Anthropic 公式の progressive disclosure・OpenSpec の二層モデル（specs=現在形 in-place / changes=追記型履歴）・実測研究（人手キュレーション起点 docs でトークン−16.6%・時間−28.6%）と整合。本リポジトリで既に観測された番号破綻・ドリフトが「書く側」の構造問題の直接証拠
- **docs/features/ を変更履歴レイヤーとして維持**: OpenSpec の changes/ に相当する既存資産（match-tracker 90 / kagetra 28 スラッグ）であり、既に正しい形。本体 docs への changelog 追記を禁止することで二層が完成する
- **見出し番号の廃止**: AI が壊し続けているのは連番そのもの。安定した名前見出し+ファイルパス粒度の参照に統一
- **ゲートは FAIL+明示オプトアウト**: devflow の「DoD は全項目機械判定・FAIL なら出荷不可」の設計思想に合わせる。純リファクタは『Docs: no-change-needed（理由）』の明示を要求し、意図的省略と更新漏れを機械的に区別する
- **kagetra 仕様書はフル作成**: 直近30日335コミットと活発で、data-quality 等の暗黙知が memory に偏在。移行先と同型（ドメイン分割）で最初から作ることで、match-tracker の移行のリファレンス実装を兼ねる
- **スコープは1機能・3ブロック**: レジストリ仕様・書き込み規律という共通契約を1つの要件定義書で固定し、二重管理を避ける。実行はリポジトリ境界で3ブロックに分け、/implement の worktree 前提と両立させる
