---
name: impl_remove_shuffle_icon_edit_button
description: 対戦結果画面(matches/result)の「編集画面へ移動」ボタンから装飾用Shuffleアイコンを削除して出荷(PR#1066)
metadata:
  type: project
---

対戦結果画面（`matches/result` = MatchResultsView）で未組み合わせ時に表示される「編集画面へ移動」ボタンから、装飾用のシャッフルアイコン（lucide `Shuffle`）を削除した quickfix。

- **変更**: [karuta-tracker-ui/src/pages/matches/MatchResultsView.jsx](karuta-tracker-ui/src/pages/matches/MatchResultsView.jsx) の1ファイル。ボタン内 `<Shuffle className="w-5 h-5" />` を削除し、未使用になった import メンバー `Shuffle` も同時に除去（1 insertion, 2 deletions）。文言「編集画面へ移動」・遷移先 `/pairings?date=...` は不変
- **影響**: 表示のみ。ロジック・遷移・他コンポーネント・FE/BE契約への影響なし
- **検証**: lint 0 errors、MatchResultsView 関連テスト 12 passed。テストは本ボタン/Shuffle を参照していなかった
- **auto-review**: 1R pass（effort=low、blockers/should_fix/nits すべて0、累計 19,619 tokens）。差分<150行・≤4ファイルかつ全変更が低リスクパス（`src/**`、api除く）→ trivial 高速パス low 判定
- **docs**: no-change-needed（装飾アイコン削除のみで画面遷移・機能・仕様に変更なし。SCREEN_LIST 更新不要）
- **PR #1066**: <https://github.com/poponta2020/match-tracker/pull/1066>、コミット 857fa2c6、Issue なし
