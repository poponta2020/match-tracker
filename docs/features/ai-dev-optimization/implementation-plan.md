---
status: completed
---
# AI開発最適化（ai-dev-optimization）実装手順書

要件: [requirements.md](requirements.md)（AC は §4）。3リポジトリ横断・P→R→K の3ブロック実行。
ブロックK は kagetra セッションで実行し、本書と要件定義書を絶対パス（`C:\Users\popon\match-tracker\docs\features\ai-dev-optimization\`）で参照する。

## 技術計画の決定事項（requirements §6 申し送り論点の解決）

### 1. match-tracker のドメイン分割（12ファイル + design 2ファイル）

`docs/spec/` 配下（旧 SPEC §3/§4/§7、旧 DESIGN §4/§5.3/§7 を統合）:

| ファイル | 収容する旧セクション（代表） |
|---|---|
| spec/players-auth.md | 団体管理・選手・認証・ロール・プロフィール履歴・招待トークン（SPEC §2, §3.0, §3.8, §7.2系 / DESIGN §4.1.1, §4.2系, §6） |
| spec/practice-sessions.md | 練習日管理・ホーム・練習参加登録（SPEC §3.1, §3.2, §7.6 / DESIGN §4.5, §4.6, §7.1） |
| spec/matching.md | 対戦組み合わせ（SPEC §3.3, §7.5 / DESIGN §4.7, §7.2） |
| spec/matches.md | 対戦結果・試合動画・取り札記録・抜け番（SPEC §3.4, §3.20, §7.3系, §7.4 / DESIGN §4.3系, §4.4, §7.3, §7.6.1, §7.10） |
| spec/stats.md | 統計機能（SPEC §3.5） |
| spec/lottery.md | 抽選・当日キャンセル補充（SPEC §3.7, §7.11 / DESIGN §4.9, §7.4, §7.5） |
| spec/mentor.md | メンター機能・コメント（SPEC §3.9, §7.17, §7.18 / DESIGN §4.16, §4.17, §7.6） |
| spec/venues-booking.md | 会場管理・かでる同期・東区民同期・隣室通知・予約プロキシ（SPEC §3.6, §4.4〜§4.7, §7.9 / DESIGN §4.8, §7.7〜§7.9） |
| spec/densuke.md | 伝助連携・削除候補（SPEC §4.1, §7.7系） |
| spec/notifications.md | LINE通知・通知・Push・LINE管理（SPEC §4.3, §7.12〜§7.15 / DESIGN §4.10, §4.11, §4.13, §4.14） |
| spec/calendar.md | カレンダー購読 iCal（SPEC §4.2, §7.10 / DESIGN §4.10(重複番号の後者)） |
| spec/backlog.md | 未実装・TODO の一本化（SPEC §9 + DESIGN §8） |
| design/architecture.md | アーキテクチャ・レイヤー・認証認可実装・権限マトリックス・横断設計ポイント・デプロイ・開発環境（DESIGN §2, §6, §9.1, §9.2 + SPEC §1.3, §1.4, §8, §7.16 / システム設定 §4.15） |
| design/db.md | ER図+テーブル一覧+テーブル定義（DESIGN §3 + SPEC §6 を照合統合、付録B 初期データ含む）。**500行超の場合は design/db-<領域>.md に分割し db.md を索引とする（AC-R2/R4 の判定対象は design/db*.md）** |

- 旧 SPEC §5（画面一覧とルーティング・ナビゲーション構造）と旧 DESIGN §5.1/§5.2 → **SCREEN_LIST.md に統合**（画面のSSOT）。DESIGN §5.3 主要画面設計 → 各ドメインファイルの「関連画面」節へ分配
- 旧 DESIGN §9.3 リリースノート → docs/design/release-notes.md へ退避（以後追記しない。履歴は features/ と git が正）
- 旧 DESIGN 付録A 用語集 → docs/spec/glossary.md
- ハブ: SPECIFICATION.md = システム概要+ドメイン一覧表（リンク+1行責務）、DESIGN.md = アーキテクチャ概要+architecture/db/ドメインへのリンク。**各200行以下・行番号なし・連番なし**
- 各ドメインファイル冒頭の定型メタブロック: 責務1行／関連画面（SCREEN_LIST の画面名）／主要実装パス（ファイルパス粒度・行番号なし）

### 2. gate-dod パスパターン（profile §docs の機械可読ブロック）

`<!-- devflow:docs -->` ブロックを新設（devflow:commands と同じ awk 抽出方式）:

```sh
DEVFLOW_SRC_PATTERNS=("karuta-tracker/src/" "karuta-tracker-ui/src/" "database/")
DEVFLOW_DOCS_PATTERNS=("docs/" "CLAUDE.md" "karuta-tracker/CLAUDE.md" "karuta-tracker-ui/CLAUDE.md")
```

kagetra 用: SRC=("apps/web/src/" "apps/mail-worker/src/" "packages/shared/src/") DOCS=("docs/" "CLAUDE.md" "apps/web/CLAUDE.md")。
判定は `gh pr diff <PR> --name-only` の前方一致。src マッチあり & docs マッチなし & PR 本文に `Docs: no-change-needed` なし → FAIL。ブロック未定義なら SKIP。

### 3. kagetra のドメイン区分け（K5 実行時に kagetra セッションで最終確定）

候補: events-attendance（イベント・出欠・アーカイブ）／tournaments-results（大会・結果取込 result-import）／players（選手・自己同定）／stats（成績 senseki）／schedule／auth-admin（認証・招待・LINE連携・管理）／mail-worker（メール取込・AI振り分け）／notifications（LINE broadcast）。App Router 構造と 28 スラッグから過不足を確認して確定する。

### 4. features/INDEX.md フォーマット（追記が壊れにくい形式）

末尾追記型の行独立リスト（表は使わない）。ヘッダに規約コメントを置く:

```markdown
# Features Index
<!-- 規約: 1スラッグ=1行。/define-feature が作成時に、/ship が出荷時に末尾へ追記・更新する。並べ替え禁止 -->
- `<slug>` — <1行説明>（主要領域: <パッケージ/ディレクトリ>）[shipped: PR #N]
```

### 5. K2 の移動先

`scripts/diagnostics/` へ一括移動し、`.gitignore` に `scripts/diagnostics/` を追加。「使い捨て診断スクリプトは scripts/diagnostics/ に作る（apps/ 直下禁止）」を kagetra CLAUDE.md と profile §conventions に明記。

---

## 実装タスク

### タスク1: プラグイン同期 + gate-dod docs チェック実装（ブロックP）
- [x] 完了
- **目的:** 未push 4コミットの push とバージョン機構の確認後、gate-dod.sh に docs 更新漏れチェック（D2）を実装する
- **対応AC:** AC-P1, AC-P2, AC-P3
- **主な変更領域:** claude-devflow リポジトリ（scripts/gate-dod.sh）。作業場所は C:\Users\popon\.claude\plugins\marketplaces\claude-devflow
- **依存タスク:** なし
- **必要なテスト:** モック profile（devflow:docs ブロックあり/なし）と実 PR 相当の差分リストで FAIL / PASS / SKIP / オプトアウトの4分岐をシナリオ実行して確認（テストハーネスは新設しない）
- **完了条件:** 4分岐のシナリオ確認が全て期待どおり。既存チェック（A/B/C/D1）の出力が不変
- **対応Issue:** #1011

### タスク2: スキル文言改修 + 新バージョンリリース（ブロックP）
- [ ] 完了
- **目的:** quickfix のハードコード除去（profile §docs 参照化）、implement への docs 更新ステップ追加、define-feature/ship への features INDEX 追記ステップ追加、新バージョンとしてリリースし利用側の版を更新する
- **対応AC:** AC-P4, AC-P5, AC-P6
- **主な変更領域:** claude-devflow の skills/quickfix, skills/implement, skills/define-feature, skills/ship の SKILL.md、README/GUIDE、バージョン表記。cache 側の利用バージョン更新
- **依存タスク:** タスク1
- **必要なテスト:** grep で SPECIFICATION/SCREEN_LIST/DESIGN のハードコード0件を確認（AC-P4）。match-tracker で /dod 相当（gate-dod.sh 直実行）の回帰確認（AC-P6）
- **完了条件:** AC-P4 の grep が0件。新バージョンが本セッション環境で有効化され、gate-dod.sh が従来型 PR で従来判定を返す
- **対応Issue:** #1012

### タスク3: docs 分割の対応表 + 照合スクリプト（ブロックR）
- [x] 完了
- **目的:** 旧 SPECIFICATION/DESIGN の全見出し（#〜###）を抽出し「旧見出し→新配置ファイル」対応表を確定、対応の過不足を機械照合するスクリプトを用意する（分割作業の安全網）
- **対応AC:** AC-R3
- **主な変更領域:** docs/features/ai-dev-optimization/migration-map.md（対応表）、scripts/docs-migration/（照合スクリプト。出荷後も AC 再検証に使えるよう PR に含める）
- **依存タスク:** なし（ブロックPと並行可）
- **必要なテスト:** 照合スクリプト自体の動作確認（意図的に1見出し欠落させて検出できること）
- **完了条件:** 対応表が旧2ファイルの全見出しを網羅（照合スクリプトで欠落0を確認できる状態）
- **対応Issue:** #1013

### タスク4: ドメインファイル移植 + db.md 統合（ブロックR）
- [x] 完了
- **目的:** 対応表に従い docs/spec/*.md（12ファイル）と docs/design/architecture.md, db.md, release-notes.md を作成。db は SPEC §6 と DESIGN §3 を照合統合し、players/matches を本番 PostgreSQL introspect（c:\tmp\dbtool\Q.java 使用）と突き合わせてドリフト解消
- **対応AC:** AC-R2, AC-R3, AC-R4, AC-R5
- **主な変更領域:** docs/spec/（新規）、docs/design/（新規）。移植は内容の再配置のみ（リライト禁止。連番見出し→名前見出しの変換と重複統合は行う）
- **依存タスク:** タスク3
- **必要なテスト:** 照合スクリプト pass（欠落0）、各ファイル500行以下の機械確認、db 定義と introspect の突合結果
- **完了条件:** AC-R2/R3/R4/R5 の判定が全て pass
- **対応Issue:** #1014

### タスク5: ハブ化 + SCREEN_LIST 統合 + AGENTS.md 追随（ブロックR）
- [x] 完了
- **目的:** SPECIFICATION.md / DESIGN.md を200行以下のハブに書き換え、旧 SPEC §5・DESIGN §5.1/§5.2 を SCREEN_LIST.md へ統合、AGENTS.md 内の docs 参照を新構造に更新する
- **対応AC:** AC-R1
- **主な変更領域:** docs/SPECIFICATION.md, docs/DESIGN.md, docs/SCREEN_LIST.md, AGENTS.md
- **依存タスク:** タスク4
- **必要なテスト:** ハブ200行以下・全ドメインファイルへのリンク存在・リンク切れなしの機械確認。照合スクリプト再実行（ハブ化で欠落が出ていないこと）
- **完了条件:** AC-R1 pass。docs/ 全体でリンク切れ0
- **対応Issue:** #1015

### タスク6: レジストリ + ルーティング + ネスト CLAUDE.md + INDEX（ブロックR）
- [x] 完了
- **目的:** project-profile §docs をレジストリ化（決定事項2の devflow:docs ブロック含む）、CLAUDE.md にルーティング指示追加、karuta-tracker/CLAUDE.md と karuta-tracker-ui/CLAUDE.md 新設、docs/features/INDEX.md 生成（全スラッグ）
- **対応AC:** AC-R6, AC-R7, AC-R8, AC-R9, AC-P1〜P3 の実地有効化
- **主な変更領域:** .claude/project-profile.md, CLAUDE.md, karuta-tracker/CLAUDE.md（新規）, karuta-tracker-ui/CLAUDE.md（新規）, docs/features/INDEX.md（新規）
- **依存タスク:** タスク2（レジストリ形式）、タスク5（新構造の確定）
- **必要なテスト:** INDEX スラッグ網羅の機械照合、ネスト CLAUDE.md 各50行以下、gate-dod.sh を本リポジトリで実行し docs チェックが機能すること
- **完了条件:** AC-R6/R7/R8/R9 pass
- **対応Issue:** #1016

### タスク7: kagetra 衛生タスク一式（ブロックK・kagetra セッション）
- [ ] 完了
- **目的:** CLAUDE.md stale 修正（Lightsail→Oracle Cloud、apps/api スケルトン+BFF 注記）、apps/web/_*.mts 105本を scripts/diagnostics/ へ移動+gitignore+再発防止ルール明記、apps/web/CLAUDE.md 新設、memory 索引差分解消（130 vs 113）
- **対応AC:** AC-K1, AC-K2, AC-K3, AC-K4
- **主な変更領域:** kagetra_new の CLAUDE.md, .gitignore, scripts/diagnostics/（新規）, apps/web/CLAUDE.md（新規）, .claude/memory/MEMORY.md
- **依存タスク:** なし（ブロックP完了後ならいつでも。タスク8と同セッション推奨）
- **必要なテスト:** AC-K1〜K4 の grep/カウントによる機械確認
- **完了条件:** AC-K1/K2/K3/K4 pass
- **対応Issue:** #1017

### タスク8: kagetra 全体仕様書 + INDEX + レジストリ（ブロックK・kagetra セッション）
- [ ] 完了
- **目的:** ドメイン区分け（決定事項3）を確定し、コードベースからのリバースエンジニアリングで全体仕様書（ハブ SPECIFICATION.md ≤200行 + docs/spec/<ドメイン>.md 各≤500行 + docs/design/db.md ← packages/shared の Drizzle スキーマから生成）をフル作成。docs/features/INDEX.md（28スラッグ）と profile §docs レジストリ（devflow:docs ブロック含む）も整備
- **対応AC:** AC-K5, AC-K6, AC-K7, AC-K8
- **主な変更領域:** kagetra_new の docs/SPECIFICATION.md（新規ハブ）, docs/spec/（新規）, docs/design/db.md（新規）, docs/features/INDEX.md（新規）, .claude/project-profile.md
- **依存タスク:** タスク2（レジストリ形式）、タスク7（同セッションで先行推奨）
- **必要なテスト:** 行数上限・スラッグ網羅の機械確認。主要3ドメイン（結果取込・イベント出欠・選手管理）の記述をコードと突合するサンプルレビュー（AC-K6・manual）
- **完了条件:** AC-K5/K7 pass、AC-K6/K8 のレビュー完了
- **対応Issue:** #1018

## 実装順序

1. タスク1（P: ゲート実装）→ 2. タスク2（P: スキル改修+リリース）
2. タスク3（R: 対応表。P と並行可）→ 4. タスク4（R: 移植）→ 5. タスク5（R: ハブ化）→ 6. タスク6（R: レジストリ+ルーティング）
3. タスク7（K: 衛生）→ 8. タスク8（K: 仕様書）— kagetra セッションで実行
4. 全タスク後: AC-R10（ナビゲーション実測・manual）と AC-P6（回帰）の最終確認

## 全タスク後の検証（/implement Step 12 相当）

- AC-R10: 新セッションで代表シナリオ2件（UI系: 「対戦結果一覧の表示を変えたい」、API系: 「抽選の当選ロジックを確認したい」等）を実測し、全域走査なしに docs 起点2〜3ホップで正しい実装ファイルへ到達することをユーザーと確認
- AC-P6: 従来型 PR での DoD ゲート回帰確認
