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
- [ship_pr1098_line_chat_worker_selectors.md](ship_pr1098_line_chat_worker_selectors.md) - PR#1098出荷（LINEチャット予約タスク7=chat.line.biz実DOMセレクタ確定＋ローカルPoC、親#1084・対応#1092）。OamChatPage全実装(URL直ナビ/#editor Shift+Enter/予約モーダルnative date-time/バナー日時照合/⋮削除)・PoC PASS・worker+docsのみBE/DB無改修。auto-review 4R収束(全high,248k)=R1認証壁保険doc/impl齟齬・R2削除waitFor(hidden)・R3 blocker必須env LINE_OAM_ACCOUNT_PATH docker未配線→R4 pass。UAはPlaywright headless通過。10分floorはPR#1097別出荷済
- [ship_pr1097_chat_reserve_send_time_floor.md](ship_pr1097_chat_reserve_send_time_floor.md) - PR#1097出荷（チャット予約の送信予定時刻を10分境界にfloor、親#1084 Refsのみ・未クローズ）。LINE OAMの時刻入力step=600が非境界値を無言スナップ(09:45→09:50)→verify要確認化の対策。floorはresolveScheduledSendAtのみ=push経路(resolveFirstMatchStartTime)は不変・早める方向。CardDivisionScheduleResolverTest新設6ケース。DB変更なし。auto-review 1R pass(medium,25k)。設計詳細はharness memory impl_chat_reserve_send_time_floor
- [ship_pr1095_admin_member_org_pairing_scope.md](ship_pr1095_admin_member_org_pairing_scope.md) - PR#1095出荷（他団体会員でもあるADMINが会員団体の対戦組み合わせを閲覧・操作できないバグ、Issue#1094）。ADMINをPLAYERと同じ会員団体スコープに統一（閲覧=resolveViewingOrganizationId新設・書込4ヘルパー＋checkScopeByDate統一・個別削除ロールはADMIN+維持）。resolveEffectiveOrganizationIdはKaderuSync/MatchVideoで残置が正。BEのみ・DB/FE無改修。auto-review 1R Codex pass(high)
- [ship_pr1073_practice_per_date_attendance.md](ship_pr1073_practice_per_date_attendance.md) - PR#1073出荷（カレンダー日付ポップアップ出欠登録→1日分の参加＋理由付きキャンセル画面、親#1068/子#1069-1071）。フロントのみ・consolidation。seed失敗ブロック＋payloadはhandleSameとbyte一致＋3分割。auto-review 4R=R1締切後既存トグルのサイレントno-op実バグ/R2 date-only UTC月ずれ/R3-1締切後全置換は偽陽性(existsActive PENDING skip)/R4両parity不採用(advisor:Ship fix neither)。DoD C1はr4 needs_changesで機械FAIL→検証済み偽陽性ゆえユーザー承認でdocument override
- [ship_pr1072_card_division_header_format.md](ship_pr1072_card_division_header_format.md) - PR#1072出荷（札分けテキストのヘッダ整形、Issueなし・quickfix・BEのみ）。CardDivisionTextService.buildTextで①囲みかっこ【】削除②M/D(曜)会場名（半角・曜日はDayOfWeek算出）③タイトル直後に空白行1行④一の位/十の位のあと全角スペース。確認画面/LINE両方が同一生成元。/pairings cardRules.jsは別生成元で無影響（位行クランプは残課題）。auto-review 1R pass(medium,23.6k)
- [impl_remove_shuffle_icon_edit_button.md](impl_remove_shuffle_icon_edit_button.md) - PR#1066出荷（対戦結果画面matches/resultの「編集画面へ移動」ボタンからlucide Shuffle装飾アイコン削除、Issueなし・quickfix）。1ファイル1挿入2削除、遷移/文言不変。lint 0err・MatchResultsView 12テストpass。auto-review 1R pass(low,19.6k)＝trivial高速パス。docs no-change-needed
- [ship_pr1067_card_division_today_tomorrow.md](ship_pr1067_card_division_today_tomorrow.md) - PR#1067出荷（札分け確認を今日＋明日の2日分表示、親#1064/子#1065）。純フロント・BE無改修（既存 GET /api/card-division の date 流用）。明日はBEレスポンスの date を addOneIsoDay(+1・TZ非依存)でアンカー＝FE new Date()不使用(AC-17)。addOneIsoDayはreact-refresh lintでutils/date.jsへ切出し。AC-18暫定注記はadvisor指摘でセッション有無非依存の常時表示に修正。auto-review 1R pass(high,36.5k)。教訓=別slug card-division-today-tomorrowでv1マージ済リモートbrの枝分かれ罠回避／共有node_modulesを並行セッションが破壊→worktree独自npm ciで隔離
- [ship_pr1063_pairing_view_unpaired_chips.md](ship_pr1063_pairing_view_unpaired_chips.md) - PR#1063出荷（/pairings 閲覧時に未組み合わせ選手を待機中チップ表示、親#1061/子#1062）。純フロント単一タスク。核心=waitingPlayersは{id,name}のみで級/段/ロール欠落→advisorが枠線色・Codexが並び順を同根から別観点で検出、ソート前にparticipants補完で両治。auto-review 2R収束(medium,63k)。AC-6はverify未実施
- [ship_pr1060_torifuda_record_dnd_view.md](ship_pr1060_torifuda_record_dnd_view.md) - PR#1060出荷（取り札記録の改修=①D&D配置②決まり字順③象限内動的幅④保存後は試合詳細へ遷移＋読取専用表示、親#1052/子#1053-1057）。フロントのみ・Wave1(task1∥task2)＋task3/4/5直列。@dnd-kitはjsdom e2e不可→computeDrop/trailing-clickガードを純モジュール化、transform未適用だとドラッグ中動かない。auto-review 4R収束(全high)=MatchDetail本人性ガード/stale記録クリアを段階深堀り、kimariji crashはfallback見落としfalse-positive
- [ship_pr1059_pairing_lock_display_fixes.md](ship_pr1059_pairing_lock_display_fixes.md) - PR#1059出荷（/pairingsロック表示3改善＝A通知削除/Bバッジ重複解消/C全削除→対戦をリセット、親#1058）。純フロント・単一タスク直列。shouldShowManualLockBadge=canShowUnlockの厳密な補集合で編集/閲覧を排他。auto-review 1R pass(medium,32k)。教訓=docs更新前に対象文字列の実在をgrep確認・変更文言をテスト全体grepしアサート無しを確認(AC-6ブラインドスポット)
- [ship_pr1051_card_division_line_reminder.md](ship_pr1051_card_division_line_reminder.md) - PR#1051出荷（札分け確認＆LINE通知、親#1045/子#1046-1049）。札組テキストBE一元生成(cardRules.jsゴールデンパリティ)＋購読制デフォルトOFF(per-orgゲート)＋3h前スケジューラ(dedupeKey=sessionId)＋購読部分更新API。本番DB適用済(CHECK25種別)。auto-review 2R収束(R1=clipboardテスト堅牢化)
- [ship_pr1050_pairing_avoid_previous_practice.md](ship_pr1050_pairing_avoid_previous_practice.md) - PR#1050出荷（自動シャッフルで同一団体の前回練習日ペアを強く回避、親#1041/子#1042#1043）。ソフト強ペナルティ=グレースフル劣化。auto-review 2R収束(R1 blocker=matches経路テスト欠落→main直修正)。cross-org除外はbehavioral検証不可の教訓
- [ship_pr1044_torifuda_place_over_chip.md](ship_pr1044_torifuda_place_over_chip.md) - PR#1044出荷（取り札記録の盤面で札選択中は既存チップの上でもそのマスへ配置可、Issueなし）。stopPropagation子がモード操作で親の当たり判定を潰す不具合→子onClickをarm状態で分岐。TorifudaBoard初テスト追加。1R Codex pass(low)
- [ship_pr1040_home_api_roundtrips.md](ship_pr1040_home_api_roundtrips.md) - PR#1040出荷（/api/home参加率グループの月間データ1回ロード化）。ローカル→オレゴンRTT環境で15〜24秒の主因＝最大6回フル再計算を撤廃。pg_stat_user_tables前後差分で往復数を実証した調査手法つき
- [ship_pr1039_ensure_org_rollback_only.md](ship_pr1039_ensure_org_rollback_only.md) - PR#1039出荷（自動所属の並列競合rollback-only 500、Issue#1037）。ON CONFLICT DO NOTHINGで例外の発生自体を除去（#1035=setRollbackOnly/#1038=REQUIRES_NEWと使い分け根拠あり）。#1034発の同型横展開これで全件完了
- [ship_pr1038_densuke_mapping_tx_abort.md](ship_pr1038_densuke_mapping_tx_abort.md) - PR#1038出荷（伝助マッピング一意制約違反1件でバッチ全体破棄=25P02、Issue#1036）。INSERTをDensukeMemberMappingWriter(REQUIRES_NEW別Bean)に隔離。冪等マスターデータゆえ先行コミット無害（#1035と真逆の判断根拠に注意）。JDKプロキシspyにdoCallRealMethod不可の教訓
- [ship_pr1035_adjacent_room_rollback_only.md](ship_pr1035_adjacent_room_rollback_only.md) - PR#1035出荷（隣室通知スケジューラーのrollback-only ERROR修正、Issue#1034）。exists事前チェック+setRollbackOnlyバックストップ。モックTxテンプレートはrollback-only検査を再現できない教訓。同型バグ2件（OrganizationService/DensukeWriteService）を別タスク切り出し
- [ship_pr1033_reshuffle_except_locked.md](ship_pr1033_reshuffle_except_locked.md) - PR#1033出荷（/pairingsロック以外を再シャッフル・auto-match lockedPairs後方互換拡張、Issue#1029-1032クローズ）。Codex3R収束/AC pass/code-review clean/本番read-only verify。DoD gate A1/A2はdevflow版不整合(profile v0.7.0 path::形式をcached≤0.5.0が非対応)で機械FAIL→実体green確認しユーザー承認で出荷
- [ship_pr1028_pairings_match_tab_bar.md](ship_pr1028_pairings_match_tab_bar.md) - PR#1028出荷完了（/pairings試合番号タブ整形・左寄せ+滑るcreamハイライト、Issue#1024-1027クローズ）。DoD全PASS。gate C1はmain側codex結果JSON要コピー・D1はプロジェクトローカルmemory参照の運用メモあり
- [ship_pr1007_bottom_nav_ios_scroll.md](ship_pr1007_bottom_nav_ios_scroll.md) - PR#1007出荷完了。iOS Safariのfixed+transform既知バグ修正。DoDのlintは既存負債のため--skip-dod（フォローアップIssue #1019切り出し済み）
- [ship_pr1022_frontend_lint_debt.md](ship_pr1022_frontend_lint_debt.md) - PR#1022出荷完了（Issue#1019）。既存lintエラー46件解消。DoD全項目PASS。worktree/gate-dodのファイル所在ずれ・プロセスロックの教訓あり
- [ship_pr1023_pairings_decard_ui.md](ship_pr1023_pairings_decard_ui.md) - PR#1023出荷（/pairings 脱カードUIリデザイン・design-screen主導・純UI+意図的差分3点）。Codex 2x pass/AC fail→修正/code-reviewでdocs正典更新。ライブ目視のみ未実施(Vercelプレビュー可)。会場名フォールバック純粋関数化(pairingHeader.js)
