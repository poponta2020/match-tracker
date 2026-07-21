---
name: ship_pr1165_design_md_anti_slop
description: PR#1165出荷（脱AIスロップ・デザイン正典design.md新設＋Home脱カード・washiリデザイン、純FE、親#1155子#1156-1159）。auto-review 1R pass。実装詳細は[[impl_design_md_anti_slop]]
metadata:
  type: ship
---

PR #1165 出荷（脱AIスロップ・デザイン正典 `docs/design/design.md` 新設＋Home 脱カード・washi リデザイン）。純 FE（BE/API/DB/認証・スキーマ変更なし＝visual-only + design-spec §6 の意図的例外6件）。slug=design-md-anti-slop。親 #1155・子 #1156-1159（PR 本文 closing keyword でマージ時 auto-close）。実装詳細は [[impl_design_md_anti_slop]]。

## 変更（4タスク＋レビュー後 polish 3件）
- **design.md 新設**（Google標準8節＋YAML front matter トークン。washi 語彙＝TorifudaRecord.css 由来・参照仕様で tailwind 未配線。原則「脱スロップ≠ミニマリズム」・固有スロップ名指し禁止・孤児赤primary/旧マテリアル色はレガシー）。
- **Home 脱カード・washi**（`Home.jsx` 全面書換）: 深緑ヒーロー `#33503f` 常時表示・単一デザイン、参加率を行の背景バーで表現、和紙繊維テクスチャ背景、NavigationMenu/PlayerChip/Trophy/繰り上げバナー撤去し人数「N名」集約。`Home.test.jsx` 6ケース新設。
- **和紙タイル**: `scripts/washi-tile/generate-washi-tile.mjs`（seed固定・決定論）＋`src/assets/washi-fiber-tile.png`（460×460 seamless・実行時乱数canvasなし）。
- **正典配線**: project-profile(design-system/docs)・ルートCLAUDE.md・ui/CLAUDE.md に design.md 参照。`SCREEN_LIST.md` の Home 行追随。
- **レビュー後 polish（ユーザー往復）**: ①トップバー撤去の完遂＝Layout 固定フォールバック緑バーを `-mt-16 relative z-40` で覆いヒーロー最上部占有(d881d9b1) ②団体名を明朝16px navy #1A3654 に強調・試合数据え置き(f7c707c6) ③月見出し「N月の参加率」太字(3330029e)。

## auto-review（[[auto_review_round_pr1165]] は 1R pass のため記録省略・本 ship 記録に集約）
- **1R pass**（effort=medium・blockers/should_fix/nits ゼロ・偽陽性ゼロ・47,165/500,000 tokens）。
- effort=medium 判断: DIFF_LINES=1457 だが大半が docs 5ファイル＋Home.jsx 視覚書換＋test＋一回限りスクリプト。認証/DB/API/通知ロジックなし・全コード低リスクFEパス。[[auto_review_round_pr1145]]「docs/test膨張の大差分はhigh過大→medium妥当」踏襲。
- 偽陽性ゼロの要因: **意図設計8項目（トークン非配線＝フォーク#3、@napi-rs/canvas非依存化、NavigationMenu残置、-mt-16 z-40被覆、端末ローカルTZ既存パターン等）を Codex プロンプトに先回り明記**（[[auto_review_round_pr1114]]/[[auto_review_round_pr1127]] 同型）。中立cwd(scratchpad)+stdin([[auto_review_round_pr1102]]踏襲)で偽passも回避。

## テスト・検証
- FE 810 テスト green（Home.test.jsx 6件含む）・lint 0 error。
- design.md front matter を js-yaml でパース検証（**hex 全 quote＝null化なし**・8見出し確認）。
- Home 実機検証（本番backend不要のCORS mock+worktree vite+localStorage認証注入・旧同一DTO契約）: computed geometry で全状態・深緑ヒーロー最上部 y=0・水平オーバーフロー無し・バー/配色を確認。**Browser pane の screenshot API はハング**（read_page/js_tool即応=描画正常のinfra不具合）→geometry代替。

## DoD / マージ
- CI: Vercel 2件 pass（本プロジェクトは PR/push の test CI を手動化済み＝test.yml 非自動）。C1=codex pass・D2=docs更新あり。
- **罠**: 実装中の検証用 mock(:8080)/vite(:5173) は /show-app・/startapp と衝突するので出荷前に kill 必須。
