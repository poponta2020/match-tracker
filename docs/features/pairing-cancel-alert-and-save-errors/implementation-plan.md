---
status: completed
---
# pairing-cancel-alert-and-save-errors 実装手順書

対象: 純フロントエンド（`karuta-tracker-ui`）。バックエンド無改修・スキーマ変更なし・migration なし。
2タスクはいずれも [PairingGenerator.jsx](../../../karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx) を触るため**直列**（同一ファイル＝並行不可）。

## 実装タスク

### タスク1: キャンセル者アラート（変更1）
- [ ] 完了
- **目的:** 閲覧モードで表示中の試合にキャンセル済み参加者がいるとき、単一 OK ボタンのアラートを出し、OK で現在の試合を編集モード化（キャンセル者を「空き」に実体化）する。
- **対応AC:** AC-1, AC-2, AC-3, AC-4, AC-5, AC-6, AC-7
- **対応Issue:** #1169（親 #1168）
- **主な変更領域:**
  - `karuta-tracker-ui/src/pages/pairings/pairingDisplayLogic.js`（純粋関数を追加）
    - `collectCancelledNames(pairings)`: 各組から**結果未入力（`!hasResult`）**の組のキャンセル者名（`player1Cancelled→player1Name` / `player2Cancelled→player2Name`）を配列で返す。両方キャンセルは両名を含む。
    - `shouldTriggerCancelAlert({ isViewMode, isReadOnly, pairings, acknowledged })`: `isViewMode && !isReadOnly && !acknowledged && collectCancelledNames(pairings).length > 0`。
  - `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx`
    - `acknowledgedCancelMatches = useRef(new Set())`（同一マウント中の再発火防止。日付は固定なのでマウント単位でよい）。
    - `cancelAlert` state（表示制御＋凍結した名前配列）。
    - 発火 `useEffect`（deps: `pairings, isViewMode, isReadOnly, matchNumber`）: `shouldTriggerCancelAlert` が true かつ `!acknowledgedCancelMatches.current.has(matchNumber)` なら `setCancelAlert({ names })`。
    - アラートモーダル JSX（既存の「選手追加モーダル」＝ styled modal を踏襲。**単一 OK ボタンのみ**・閉じる/キャンセルは出さない）。文言: 「{matchNumber}試合目に参加予定の{names.join('・')}は練習をキャンセルしています。対戦組み合わせから外してよろしいですか？」
    - `handleCancelAlertOk`: `acknowledgedCancelMatches.current.add(matchNumber)` → `setCancelAlert(null)` → 既存の **`enterEditModeForExisting()` を呼ぶ**（＝`materializeCancelledSlots`）。
- **依存タスク:** なし
- **必要なテスト:**
  - `collectCancelledNames`: 片方/両方/なし/結果入力済み除外（AC-2, AC-5）。
  - `shouldTriggerCancelAlert`: view+cancel+未確認=true、非view/読み取り専用/確認済み/cancelなし=false（AC-1, AC-5, AC-6）。
  - モーダルの小レプリカ render で OK 押下 → onOk spy 呼び出し（AC-3 の配線）。materialize 自体は既存 `materializeCancelledSlots` テストでカバー済み（AC-3, AC-4）。
- **完了条件:** 上記テスト green・lint 0 error・`npm run test` green。

### タスク2: 保存ボタン常時押下＋未設定エラー明示（変更2）
- [ ] 完了
- **目的:** 「確定して保存」を `loading` 以外では常に押せるようにし、対戦相手が未設定の組があれば押下後に**未設定選手名を挙げた**エラーを表示して保存を止める。
- **対応AC:** AC-8, AC-9, AC-10, AC-11, AC-12, AC-13
- **対応Issue:** #1170（親 #1168）
- **主な変更領域:**
  - `karuta-tracker-ui/src/pages/pairings/pairingLockLogic.js`（純粋関数を追加）
    - `findIncompleteOpponentNames(pairings)`: `hasBlockingIncompletePair` と**同一の除外条件**（`!hasResult && !locked && !cancelledEmptied` で片側だけ埋まった組）のうち、埋まっている側の選手名を配列で返す（両側 null の組は名前なしなので含めない）。
  - `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx`
    - 「確定して保存」ボタンの `disabled` を `loading || hasBlockingIncompletePair(pairings)` → **`loading` のみ**に変更（AC-8）。
    - `handleSave` 冒頭で `hasBlockingIncompletePair(pairings)` が true なら、`findIncompleteOpponentNames` で名前を取得しエラー文言をセットして `return`（保存しない）。名前が取れない場合（両側 null のみ）は選手名なしの一般文言にフォールバック。この判定は **`hasNothingToSave` チェックより前**に置く（より具体的で行動可能なメッセージを優先）。
    - 文言: 「{names.join('・')}の対戦相手が未設定のため保存できません。対戦相手を選ぶか、待機中の枠に移動してください。」
- **依存タスク:** タスク1（同一ファイル `PairingGenerator.jsx` を触るため直列。ロジック上の依存はない）
- **必要なテスト:**
  - `findIncompleteOpponentNames`: 片側だけ埋まった1件→その名前、複数→全列挙（AC-9, AC-10）、`cancelledEmptied` 除外（AC-11・AC-12）、`hasResult`/`locked` 除外、全組完成→空配列（AC-13）。
  - 保存ボタン `disabled` が `loading` のみに依存すること（未完成組ありでも押せる）を小レプリカ render で確認（AC-8）。
  - `handleSave` のエラー分岐（未設定名の文言・保存未実行）：既存の createBatch モック方針に合わせて確認できる範囲で（AC-9）。
- **完了条件:** 上記テスト green・lint 0 error・`npm run test` green。

## 実装順序（Wave）
- Wave 1: タスク1（キャンセル者アラート）
- Wave 2: タスク2（保存ボタン＋エラー。タスク1 と同一ファイルのため直列）

## ドキュメント更新（実装と同一コミット）
- `docs/SCREEN_LIST.md`: `/pairings`（PairingGenerator）の説明に「キャンセル者アラート」「保存ボタンは常時押下可＋未設定エラー明示」を追記（画面挙動の変更）。
- 本 `docs/features/pairing-cancel-alert-and-save-errors/`（要件・本手順書）が変更履歴の正典。
