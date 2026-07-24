---
name: impl-pairing-cancel-alert-save-errors
description: pairing-cancel-alert-and-save-errors 実装
type: project
---

pairing-cancel-alert-and-save-errors 実装完了(2026-07-24、純FE・全main直・PR未作成、branch push済、worktree=C:/tmp/impl-pairing-cancel-alert-and-save-errors)。親#1168・子#1169/#1170。

**タスク1(キャンセル者アラート・a5a1edb6)**: pairingDisplayLogicに collectCancelledNames/shouldTriggerCancelAlert 追加。PairingGeneratorに acknowledgedCancelMatches(useRef Set)・cancelAlert state・発火useEffect(deps=[pairings,isViewMode,isReadOnly,matchNumber])・単一OKモーダル・handleCancelAlertOk(既存enterEditModeForExisting=materializeCancelledSlots再利用)。**hasResult組はアラート対象外**(結果が正)。同一マウント中は一度OKで再発火せず(AC-6)。

**タスク2(保存ボタン常時押下+未設定エラー・71deed5c)**: pairingLockLogicに findIncompleteOpponentNames/buildIncompleteOpponentError 追加。保存ボタンdisabledを`loading||hasBlockingIncompletePair`→`loading`のみ。handleSave冒頭でbuildIncompleteOpponentError→setSaveError→return(hasNothingToSaveより前)。cancelledEmptied除外はhasBlockingIncompletePairが内包。

**advisor完了前チェックで重大指摘→修正(5359369f)**: 変更2のエラー表示位置をdesign宿題にしてdesign-screenスキップ→共通errorバナー(編集エリア先頭=上部)に既定化していた。保存ボタンは最下部で、組数が多いとエラーが見切れ「押しても無反応」に見える=変更2の目的を損なう。→ **saveError専用stateに分離しフッター(保存ボタン直上)に描画**。未設定/保存対象なし/サーバー失敗の3つとも saveError へ。編集開始(enterEditModeForExisting/runAutoMatch/empty-edit)・キャンセルでクリア。

**テスト**: 純粋関数直接検証+小レプリカ(既存パターン)。加えて**実PairingGeneratorをrenderする回帰テスト1件**(apiClient.get をURLで分岐モック→片側未設定組を初期投入→対戦編集→保存押下→フッターにエラー・POST未実行・err.closest(space-y-2).contains(saveBtn))。pairings 323件green・3回反復で非フレーク。FE全878件は`--no-file-parallelism`でgreen(並列時1件フレークは既知のswipe系[[reference_frontend_swipe_tests_flaky_parallel]]・本変更無関係)。lint0err。

**罠/学び**: design-screenスキップ時、design宿題(表示位置等)を「既存流用」で流すとUX目的を外しうる→advisor完了前チェックが捕捉。worktreeはnode_modules junction(PowerShell New-Item -ItemType Junction。mklinkはGitBashパス構文で失敗)。
