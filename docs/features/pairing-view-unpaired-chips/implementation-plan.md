---
status: completed
---
# 対戦組み合わせ閲覧時に未組み合わせ選手をチップ表示（pairing-view-unpaired-chips）実装手順書

> 純フロント（`karuta-tracker-ui`）のみ。BE/DB/API 不変。要件は同ディレクトリ `requirements.md`（AC は §4）。

## 実装タスク

### タスク1: 閲覧モード・読み取り専用モードで未組み合わせ選手を読み取り専用チップ表示する
- [ ] 完了
- **目的:** 一部だけ組がある試合の閲覧時に、まだどの組にも入っていない参加者（`waitingPlayers`）を「待機中 N名」＋名前チップ（読み取り専用）で表示する。表示可否は純関数に集約する。
- **対応AC:** AC-1, AC-2, AC-3, AC-4, AC-5, AC-6, AC-7
- **主な変更領域:**
  - `karuta-tracker-ui/src/pages/pairings/pairingDisplayLogic.js` — 純関数 `shouldShowViewModeUnpairedSection({ isReadOnly, isViewMode, pairings, waitingPlayers })` を追加。戻り値 `(isReadOnly || isViewMode) && pairings.length > 0 && waitingPlayers.length > 0`。JSDoc に「編集モードガード `!isReadOnly && !isViewMode` の厳密な補集合＝相互排他」「`pairings.length>0` は参加者一覧との二重表示防止に必須」を明記。
  - `karuta-tracker-ui/src/pages/pairings/pairingDisplayLogic.test.js` — 上記関数のユニットテストを追加（AC-1〜5 を網羅する真理値表：view×組あり×待機あり=true / readOnly×組あり×待機あり=true / 組0件=false（両モード）/ 待機0名=false / 編集モード=false）。
  - `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx` — `shouldShowViewModeUnpairedSection` を import。`pairings.length > 0` ブロック内、組み合わせ一覧（各組の行の `.map`）の直後・編集モードの待機中セクション（`!isReadOnly && !isViewMode` ガードの新規ペアドロップゾーン／待機中リスト）より前に、当該純関数 true のとき「待機中 N名」見出し＋ `sortPlayersByRank(waitingPlayers)` を `PlayerChip`（`flex flex-wrap gap-2`、参加者一覧 970–980 行と同スタイル）で描画する。**チップのみ**（活動プルダウン・選手追加ボタン・D&D／タップ・ドロップゾーンは出さない）。見出しは編集モード待機中セクション（1221–1224 行）と同じ `text-[11px] font-bold tracking-wider uppercase text-[#8a8275]` ＋件数の階層表現に揃える。
- **依存タスク:** なし（単一タスク）
- **必要なテスト（テストファースト）:**
  - `pairingDisplayLogic.test.js` に `shouldShowViewModeUnpairedSection` の真理値表テストを追加（AC-1〜5）。
  - 既存の `pairingDisplayLogic.test.js`・pairing 関連ユニットテストが green のままであること（AC-7）。
  - AC-6（実描画：チップ表示・活動プルダウン非表示）は当画面にフルマウント回帰ハーネスが無いため verify（実装後に `/verify` または手動確認）。
- **完了条件:** `npm run test`（該当スイート）green・`npm run lint` green。`shouldShowViewModeUnpairedSection` が本番 JSX とテストの両方から import され、条件式がテスト側に複製されていない。
- **対応Issue:** #1062（親 #1061）

## 実装順序（Wave = 並行実装できるタスクの組）
- Wave 1: タスク1（単一タスク・直列）
