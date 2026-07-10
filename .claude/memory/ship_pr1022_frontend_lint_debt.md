---
name: ship-pr1022-frontend-lint-debt
description: PR#1022（フロントエンド既存lintエラー46件解消、Issue#1019）の出荷記録
metadata:
  type: project
---

## バグの概要
`karuta-tracker-ui`の`npm run lint`が既存の46エラー・13警告でFAILし、DoDゲート（gate-dod.sh A2）が常にFAILしていた。PR #1007出荷時に`--skip-dod`でバイパスし、フォローアップIssue #1019として切り出されていた。

## 発端
ユーザーが「DoDスキルが既存の50何件かで失敗すると聞いた」という伝聞を持ち込み、/bug-report で調査した結果、Issue #1019（未着手）の内容と一致すると判明。新規Issue作成はせず#1019をそのまま使用。

## 深刻度
中規模（16ファイル・複数カテゴリ跨ぎ）。うち`PracticeForm.jsx`はReactのrules-of-hooks違反（`/practice/new`と`/practice/:id/edit`を1コンポーネントで兼用し、早期returnの後にフックを呼んでいた）という実質的なバグを含んでいた。

## 根本原因・適用した修正
6カテゴリ（no-unused-vars, no-undef, react-refresh/only-export-components, no-irregular-whitespace, no-useless-catch, react-hooks/rules-of-hooks）に分類して対応。詳細は[docs/bugs/1019-frontend-lint-debt/requirements.md](../../docs/bugs/1019-frontend-lint-debt/requirements.md)参照。
- `PracticeForm.jsx`: 新規登録モードを`PracticeNewForm`という別コンポーネントに切り出し、挙動を変えずにフックルール違反を解消
- 警告13件（react-hooks/exhaustive-deps）は挙動変更リスクがあるためNon-goalsとしIssue #1021に切り出し

## auto-review-loop
2ラウンドでCodex pass・AC適合pass。大型差分のため追加code-review（high effort）を実施し3件の指摘（eslint設定の根本対応漏れ、no-irregular-whitespaceの検査範囲過剰縮小、disableコメントの影響範囲誤記載）を反映。詳細: [autoreview_pr1022.md](autoreview_pr1022.md)

## 教訓
- worktreeでauto-review-loopを回す際、Codex結果(`scripts/review/output/`)とmemoryファイルはmain repo側にしか無くworktreeには存在しないため、`gate-dod.sh`をworktree側で実行するとC1/D1がFALSE FAILする。実行場所とファイルの所在を揃える必要がある
- worktree削除時、内部で起動したまま残っていたVite dev serverプロセス（TaskStopで停止したはずが実プロセスは生存）がディレクトリをロックしremoveが失敗した。プロセスをkillしてから再削除で解消
- worktree/main repoの一時ファイル削除は対象ディレクトリに追跡対象ファイルが同居していないか確認してから行うこと（`scripts/`や`.claude/memory/`を`rm -rf`し誤って既存の追跡ファイルを消しかけた。`git restore`で復旧）

## DoDゲート
全項目PASS（`--skip-dod`不要）。A1テストで既知のflakyテスト（`MatchResultsView.swipe.test.jsx`、並列実行時のみ・本PRとは無関係）が一度FAILしたが単体実行では常にPASSすることを確認済み。再実行でgreen化

## Issue・PR
- Issue #1019: https://github.com/poponta2020/match-tracker/issues/1019 （マージ時に自動クローズ）
- フォローアップ Issue #1021（react-hooks/exhaustive-deps警告13件の解消）: https://github.com/poponta2020/match-tracker/issues/1021
- PR #1022: https://github.com/poponta2020/match-tracker/pull/1022 （マージ済み）
