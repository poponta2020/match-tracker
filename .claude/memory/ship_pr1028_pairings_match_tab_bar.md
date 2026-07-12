---
name: ship_pr1028_pairings_match_tab_bar
description: PR#1028出荷完了（/pairings試合番号タブ整形・左寄せ+滑るcreamハイライト）。DoD全PASS、#1024-1027クローズ
type: reference
---

**PR #1028** 「組み合わせ画面 試合番号タブの整形（左寄せ・同一フットプリント＋滑る cream ハイライト）」出荷完了。
URL: https://github.com/poponta2020/match-tracker/pull/1028 / マージ commit `e02e02e0`。

- **内容:** `/pairings`（`PairingGenerator.jsx`）の試合番号タブの純UI改修（BE/API/DB 変更なし）。親機能 `pairings-ui-change`(PR#1023) の follow-up。全タブ同一フットプリント（`flex-none`・数字のみ・左寄せ・`overflow-x-auto`）＋実測追従で滑る cream ハイライト（`useLayoutEffect` で offsetLeft/offsetWidth 計測、`bottom-0` で連結パネルへシームレス接続）。「N試合目」はパネル冒頭の静的見出しで保持。
- **マージ:** 成功（`--merge --delete-branch`）。**クローズ Issue: 親 #1024 ＋子 #1025/#1026/#1027**（PR本文 Closes キーワードで自動クローズ）。
- **DoD:** 全項目 PASS（A1テスト・A2lint・B1 CI・C1レビュー verdict=pass・D1 memory）。auto-review-loop は1ラウンド収束（[[autoreview_pr1028]]）。
- **検証:** AC-1〜5 は live-verify 実施（3タブ左寄せ／切替フットプリント不変=tab1→2で全タブ{left,w}変化ゼロを動的計測／indicator が active tab と一致し transition 滑走／10タブ横スクロール・全到達・縦オーバーフロー無し／緑チェック実測）。AC-6/7/8 は既存603テスト＋lint green。

**出荷時の運用メモ（再利用）:**
- auto-review-loop は codex 結果 JSON を **worktree 側** `scripts/review/output/` に書くが、gate-dod.sh の C1 は **main 側** の同パスを見る。ship 前に main へコピーが必要（今回コピーで C1 PASS）。
- gate-dod D1 / devflow の memory は **プロジェクトローカル `match-tracker/.claude/memory/`**（git 追跡）を参照する。ハーネスの auto-memory（`~/.claude/projects/.../memory/`）とは別系統なので、devflow 記録は前者に置くこと。
- worktree 削除は node_modules junction を先に link だけ外し（rmdir 相当・target 保護）、Vite 停止後でも OS ロックが残ると `worktree remove` が Permission denied。ただし `gh pr merge --delete-branch` がリモート＋ローカルブランチを削除し、`git worktree prune` で登録も消えるため、tmp ディレクトリだけロック解放後に消える（無害）。参照: [[feedback_ship_worktree_branch_delete]] / [[reference_worktree_node_modules_junction]] / [[reference_ship_ffmerge_untracked_feature_docs]]。
