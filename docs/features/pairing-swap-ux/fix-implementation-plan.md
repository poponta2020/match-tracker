---
status: completed
---
# 対戦組み合わせ画面 選手入替UX改修 実装手順書

## 実装タスク

### タスク1: DraggablePlayerChip のタップ選択対応

- [x] 完了
- **概要:** `DraggablePlayerChip` に `onClick` ハンドラと `isSelected` prop を追加し、選択状態の視覚表現を実装する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/pairings/DraggablePlayerChip.jsx`
    - `onClick`, `isSelected` prop を追加
    - `isSelected` の時のみ `bg-[#4a6b5a] text-white border-2 border-[#2d4a3e]` を適用
    - `onClick` ハンドラ内で `e.stopPropagation()` を呼び、document 側の解除リスナーと競合しないようにする
- **依存タスク:** なし
- **完了条件:**
  - props 追加後、既存の D&D 動作に影響がないこと（ドラッグで選手入替できる）
  - `isSelected={true}` で枠線・背景色が変わること
- **対応Issue:** #480

### タスク2: DroppableSlot のタップ選択対応

- [x] 完了
- **概要:** `DroppableSlot` に `onClick` prop を追加し、選択モード中のスロットタップを受け付ける。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/pairings/DroppableSlot.jsx`
    - `onClick` prop を追加
    - ルート div で `onClick` を受け、内部で `e.stopPropagation()` を呼ぶ
    - 既存の `isDragActive` 判定はそのまま（呼び出し側で `selectedPlayer` と合成する）
- **依存タスク:** なし
- **完了条件:**
  - `onClick` を渡すとスロット全体のタップで発火する
  - 既存の D&D ドロップ挙動に影響がないこと
- **対応Issue:** #481

### タスク3: PairingGenerator へのタップ選択モード統合

- [ ] 完了
- **概要:** `selectedPlayer` state を追加し、チップ/スロットのタップハンドラを実装して既存 `computeDragResult` で状態更新する。D&D との共存、画面他領域タップでの解除も実装する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx`
    - `const [selectedPlayer, setSelectedPlayer] = useState(null)` を追加
    - `handleChipClick(playerId, playerName, source)` を実装（選択開始 / 解除 / スワップ判定）
    - `handleSlotClick(slotData)` を実装（空き枠・待機・新規ペア対応）
    - `executePlacement(dest)` 共通関数で `computeDragResult` 呼出し〜state 更新〜`fetchPairHistory` 発火までを実装
    - `handleDragStart` 冒頭で `setSelectedPlayer(null)` を呼び D&D との整合性を確保
    - `useEffect` で `selectedPlayer` が非 null の時だけ `document` に click リスナーを張り、解除
    - JSX 側: 全 `DraggablePlayerChip` の `onClick` に `handleChipClick`, `isSelected` に対応する bool を渡す
    - JSX 側: 全 `DroppableSlot` の `onClick` に `handleSlotClick`, `isDragActive` を `!!activeDragItem || !!selectedPlayer` に変更
    - JSX 側: 新規ペア作成ゾーンの表示条件と active スタイル条件に `selectedPlayer?.source?.type === 'waiting'` を OR で追加
    - ロック済みペア・閲覧モード・`isReadOnly` 時はハンドラで早期 return
- **依存タスク:** タスク1 (#480), タスク2 (#481)
- **完了条件:**
  - 編集モード時にチップタップで選択できる
  - 選択中に別チップ/空き枠/待機/新規ペアゾーンをタップで正しく状態遷移する
  - 画面他領域タップで選択解除される
  - ドラッグ開始時に選択が解除される
  - ロック済みペア・閲覧モードではタップ選択が発動しない
- **対応Issue:** #482

### タスク4: Integration テストの追加

- [ ] 完了
- **概要:** タップ選択モードの動作を統合テストでカバーする。既存の `pairingDragLogic.test.js` はロジック変更なしのため対象外。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/pairings/PairingGenerator.integration.test.jsx`
    - テスト追加: チップタップで選択状態（視覚表現）になる
    - テスト追加: 選択中に別チップタップでスワップされる
    - テスト追加: 選択中に空き枠タップで移動される
    - テスト追加: 選択中に待機エリアタップで待機に移動される
    - テスト追加: 選択中に新規ペア作成ゾーンタップで新ペアが作成される
    - テスト追加: 同じチップ再タップで選択解除される
    - テスト追加: 閲覧モード時はチップタップが発動しない
    - テスト追加: ロック済みペアのチップタップが発動しない
- **依存タスク:** タスク3 (#482)
- **完了条件:**
  - 追加したテストがすべてパスする
  - 既存の integration テストもすべてパスする
  - `npm run lint` でエラーが出ない
- **対応Issue:** #483

### タスク5: ドキュメント更新

- [ ] 完了
- **概要:** 仕様書・設計書・画面一覧にタップ選択モードの記述を追加する（`CLAUDE.md` のドキュメント更新ルールに準拠）。
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` — 対戦組み合わせ画面の操作仕様にタップ選択モードを追記
  - `docs/DESIGN.md` — `PairingGenerator` の UI/state 設計にタップ選択モード追加
  - `docs/SCREEN_LIST.md` — 対戦組み合わせ画面の操作方法にタップ選択モードを追記
- **依存タスク:** タスク1 (#480)〜タスク4 (#483)
- **完了条件:** 3 ドキュメントに改修内容が反映されている
- **対応Issue:** #484

## 実装順序

1. **タスク1** — DraggablePlayerChip 拡張（依存なし）
2. **タスク2** — DroppableSlot 拡張（依存なし、タスク1と並行可）
3. **タスク3** — PairingGenerator 統合（タスク1, 2 に依存）
4. **タスク4** — Integration テスト追加（タスク3 に依存）
5. **タスク5** — ドキュメント更新（タスク1〜4 完了後）
