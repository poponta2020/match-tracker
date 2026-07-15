---
status: completed
---
# 対戦組み合わせ ロック表示まわりの改善（pairing-lock-display-fixes）実装手順書

要件: `docs/features/pairing-lock-display-fixes/requirements.md`（AC は §4）

3変更いずれも `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx` を触るため、変更領域が重なる。
**並行不可＝単一タスク・単一 Wave（直列）**。純フロント変更で BE/DB/API 変更なし。

## 実装タスク

### タスク1: /pairings ロック表示・文言の3改善（通知削除・バッジ重複解消・リセット改名）
- [x] 完了
- **目的:** ロック通知の削除（A）、ロック済み組の「ロック」バッジ重複解消（B）、「全削除」→「対戦をリセット」のボタン＋確認ダイアログ文言変更（C）を一括実装する。
- **対応AC:** AC-1, AC-2, AC-3, AC-4, AC-5, AC-6
- **主な変更領域:**
  - `karuta-tracker-ui/src/pages/pairings/pairingLockLogic.js`（変更B: 純関数 `shouldShowManualLockBadge` 追加）
  - `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx`（A/B/C の本番反映）
  - `karuta-tracker-ui/src/pages/pairings/PairingGenerator.integration.test.jsx`（変更B: `ViewRow` ハーネスを新純関数に接続＋ `shouldShowManualLockBadge` 単体テスト追加）
  - `docs/SCREEN_LIST.md`（`/pairings` #19 行のロック表示・「全削除」記述を in-place 更新）
- **依存タスク:** なし
- **必要なテスト（テストファースト・変更Bのみ）:**
  - `shouldShowManualLockBadge` の単体テストを `PairingGenerator.integration.test.jsx`（既存の pairingLockLogic 検証群と同じ場所）に追加:
    - 編集モード（`isReadOnly:false, isViewMode:false`）＋ `{locked:true}` → **false**（解除ボタンのみ・バッジ非表示）
    - 閲覧モード（`isViewMode:true`）＋ `{locked:true}` → **true**（バッジ表示）
    - 読み取り専用（`isReadOnly:true`）＋ `{locked:true}` → **true**
    - 結果入力済み `{locked:true, hasResult:true}` → いずれのモードでも **false**
    - 未ロック `{locked:false}` → **false**
  - `canShowUnlock` との排他（編集モードで一方が true のとき他方が false）を1ケース確認。
  - `ViewRow` ハーネス（閲覧モード相当）は `shouldShowManualLockBadge({isReadOnly:false, isViewMode:true, pairing})` で「ロック」バッジ表示を分岐させ、条件式のコピーをやめる（本番と同じ関数を import）。
- **実装メモ（各変更）:**
  - **A（通知削除）:** `runAutoMatch` 内の「`locked.length > 0` のとき `結果入力済みの/手動ロックの N 組…はロックされています。` を `setNotice` する」ブロックを丸ごと削除する。直前の「参加者数が異なる…」通知（`usedParticipantCount !== participants.length`）は**残す**。`notice` state・`setNotice('')` クリア箇所・通知の描画 JSX（`{notice && ...}`）はそのまま（他通知が使うため削除しない）。
  - **B（バッジ重複解消）:** `pairingLockLogic.js` に
    ```js
    export const shouldShowManualLockBadge = ({ isReadOnly, isViewMode, pairing }) =>
      !!(pairing && pairing.locked && !pairing.hasResult)
      && !canShowUnlock({ isReadOnly, isViewMode, pairing });
    ```
    を追加（`canShowUnlock` は同ファイル内なので直接参照）。本番 JSX のロックバッジ条件 `pairing.locked && !pairing.hasResult` を `shouldShowManualLockBadge({ isReadOnly, isViewMode, pairing })` に置換する。「結果入力済」バッジ・「解除」ボタン・「リセット」ボタンの条件は不変。
  - **C（リセット改名）:** 「全削除」ボタンのラベルを「対戦をリセット」に変更。`handleDeleteExisting` の確認ダイアログ文言を
    - ロックなし: `既存の対戦組み合わせをリセットしますか？`
    - ロックあり: `ロック済みの${lockedCount}組を除く対戦組み合わせをリセットしますか？\n（結果入力済み・手動ロックの組は保持されます。結果入力済みは個別にリセット、手動ロックは解除してください）`
    に変更（`削除` → `リセット`）。処理本体（`deleteByDateAndMatchNumber` 呼び出し等）は不変。
- **完了条件:**
  - `shouldShowManualLockBadge` の単体テストが green。
  - `npm run test`（pairing 関連）と `npm run lint` が green（AC-6）。
  - AC-1・AC-5 は実装後 `/verify` または手動確認（通知非表示・ボタン/ダイアログ文言）。
- **対応Issue:** #1058

## 実装順序（Wave）
- Wave 1: タスク1（単独・直列）
