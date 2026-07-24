---
name: feature-def-pairing-cancel-alert-save-errors
description: pairing-cancel-alert-and-save-errors 要件定義
type: project
---

対戦組み合わせ編集(/pairings)のUX改善2点を要件定義＋Issue発行(2026-07-24、親#1168子#1169-1170、slug=pairing-cancel-alert-and-save-errors、design-screenはユーザー判断でスキップ、実装着手GO済み)。

**変更1=キャンセル者アラート**: 閲覧モードで表示中の試合にキャンセル済み参加者がいると単一OKボタンのアラートを出す。OK=現在の試合を編集モード化(既存 enterEditModeForExisting/materializeCancelledSlots 再利用でキャンセル者を「空き」実体化)。**スコープは試合単位**(OKの作用が現在の試合に閉じるため。依頼文の「〜試合目と〜試合目」集約は per-match に調整・承認済み)。**全試合一括自動保存(選択肢B)は不採用**。PLAYER/ADMIN 同一挙動(現状/pairingsはFEロール判定なし・BE createBatch等もPLAYER許可のプロトタイプ認証)。同一マウント中は一度OKした試合で再発火しない(acknowledged Set)。

**変更2=保存ボタン常時押下+未設定エラー**: 「確定して保存」disabledを`loading||hasBlockingIncompletePair`→`loading`のみに。handleSave冒頭でhasBlockingIncompletePair時にfindIncompleteOpponentNames(新設・同一除外条件で埋まってる側の名前列挙)で「〇〇の対戦相手が未設定のため保存できません。対戦相手を選ぶか、待機中の枠に移動してください」をsetErrorしてreturn(hasNothingToSaveより前)。cancelledEmptied空き組は対象外(既存hasBlockingIncompletePairが除外内包)。

**方式**: read-time非破壊維持(match_pairings不変・スキーマ変更なし・migration不要)・**純FE・BE無改修**。テストは純粋関数切り出し(collectCancelledNames/shouldTriggerCancelAlert/findIncompleteOpponentNames)+小レプリカrender(既存 PairingGenerator.integration.test.jsx パターン)。AC=auto-test13/verify1(AC-7 ロール同一)。2タスク直列(同一ファイルPairingGenerator.jsx)。
