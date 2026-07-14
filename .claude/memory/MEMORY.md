# Memory Index

（devflow パイプラインの記録がここに蓄積される。1メモリ=1ファイル、この索引に1行ずつ追加する）

## Project
- [process_lean_review_pipeline_v080.md](process_lean_review_pipeline_v080.md) - devflow v0.8.0〜v0.9.0でレビュー軽量化（AC適合・追加code-review・/verify・マージ前CI待ち除外、pass即終了）。PR#1033実測が根拠。再装着条件の記録あり
- [project_ai_dev_optimization.md](project_ai_dev_optimization.md) - AI開発最適化の要件定義完了（親Issue #1010・タスク8件・3リポジトリ横断）。docsドメイン分割+profile §docsレジストリ+gate-dod docsチェックの設計判断を記録
- [impl_ai_dev_optimization.md](impl_ai_dev_optimization.md) - AI開発最適化の実装記録（タスク別追記）。gate-dod D2実装済。git-bashのPATHはC:/形式を解決しない教訓あり

## Fix Review
- [fixreview_pr1007_pointer_events.md](fixreview_pr1007_pointer_events.md) - PR#1007: fixed+transform分離パターンで非表示時のpointer-eventsを外し忘れる回帰。以後同パターン使用時は要注意

## Auto Review
- [autoreview_pr1028.md](autoreview_pr1028.md) - PR#1028（/pairings試合番号タブ整形、Issue#1024-1027）round1でCodex pass・AC適合pass。追加code-review非該当。フォント再計測指摘はweb font未使用でmoot
- [autoreview_pr1009_round1.md](autoreview_pr1009_round1.md) - PR#1009（伝助キャンセル待ち先頭以外の昇格修正、Issue#1008）round1でCodex pass・AC適合pass、追加code-review非該当
- [autoreview_pr1022.md](autoreview_pr1022.md) - PR#1022（フロントエンド既存lintエラー46件解消、Issue#1019）2ラウンドでCodex pass・AC適合pass。追加code-review(high)で3件反映（eslint設定の根本対応・no-irregular-whitespace検査範囲維持・disableコメント修正）

## Ship
- [ship_pr1039_ensure_org_rollback_only.md](ship_pr1039_ensure_org_rollback_only.md) - PR#1039出荷（自動所属の並列競合rollback-only 500、Issue#1037）。ON CONFLICT DO NOTHINGで例外の発生自体を除去（#1035=setRollbackOnly/#1038=REQUIRES_NEWと使い分け根拠あり）。#1034発の同型横展開これで全件完了
- [ship_pr1038_densuke_mapping_tx_abort.md](ship_pr1038_densuke_mapping_tx_abort.md) - PR#1038出荷（伝助マッピング一意制約違反1件でバッチ全体破棄=25P02、Issue#1036）。INSERTをDensukeMemberMappingWriter(REQUIRES_NEW別Bean)に隔離。冪等マスターデータゆえ先行コミット無害（#1035と真逆の判断根拠に注意）。JDKプロキシspyにdoCallRealMethod不可の教訓
- [ship_pr1035_adjacent_room_rollback_only.md](ship_pr1035_adjacent_room_rollback_only.md) - PR#1035出荷（隣室通知スケジューラーのrollback-only ERROR修正、Issue#1034）。exists事前チェック+setRollbackOnlyバックストップ。モックTxテンプレートはrollback-only検査を再現できない教訓。同型バグ2件（OrganizationService/DensukeWriteService）を別タスク切り出し
- [ship_pr1033_reshuffle_except_locked.md](ship_pr1033_reshuffle_except_locked.md) - PR#1033出荷（/pairingsロック以外を再シャッフル・auto-match lockedPairs後方互換拡張、Issue#1029-1032クローズ）。Codex3R収束/AC pass/code-review clean/本番read-only verify。DoD gate A1/A2はdevflow版不整合(profile v0.7.0 path::形式をcached≤0.5.0が非対応)で機械FAIL→実体green確認しユーザー承認で出荷
- [ship_pr1028_pairings_match_tab_bar.md](ship_pr1028_pairings_match_tab_bar.md) - PR#1028出荷完了（/pairings試合番号タブ整形・左寄せ+滑るcreamハイライト、Issue#1024-1027クローズ）。DoD全PASS。gate C1はmain側codex結果JSON要コピー・D1はプロジェクトローカルmemory参照の運用メモあり
- [ship_pr1007_bottom_nav_ios_scroll.md](ship_pr1007_bottom_nav_ios_scroll.md) - PR#1007出荷完了。iOS Safariのfixed+transform既知バグ修正。DoDのlintは既存負債のため--skip-dod（フォローアップIssue #1019切り出し済み）
- [ship_pr1022_frontend_lint_debt.md](ship_pr1022_frontend_lint_debt.md) - PR#1022出荷完了（Issue#1019）。既存lintエラー46件解消。DoD全項目PASS。worktree/gate-dodのファイル所在ずれ・プロセスロックの教訓あり
- [ship_pr1023_pairings_decard_ui.md](ship_pr1023_pairings_decard_ui.md) - PR#1023出荷（/pairings 脱カードUIリデザイン・design-screen主導・純UI+意図的差分3点）。Codex 2x pass/AC fail→修正/code-reviewでdocs正典更新。ライブ目視のみ未実施(Vercelプレビュー可)。会場名フォールバック純粋関数化(pairingHeader.js)
