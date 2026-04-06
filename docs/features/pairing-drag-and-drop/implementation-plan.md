---
status: completed
---
# 組み合わせドラッグ&ドロップ 実装手順書

## 実装タスク

### タスク1: @dnd-kit パッケージのインストール
- [x] 完了
- **概要:** ドラッグ&ドロップに必要なライブラリを追加する
- **変更対象ファイル:**
  - `karuta-tracker-ui/package.json` — `@dnd-kit/core`, `@dnd-kit/utilities` を dependencies に追加
- **依存タスク:** なし
- **対応Issue:** #321

### タスク2: DraggablePlayerChip コンポーネントの作成
- [x] 完了
- **概要:** `useDraggable` フックでラップした選手カードコンポーネントを新規作成する
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/pairings/DraggablePlayerChip.jsx` — 新規作成
- **詳細仕様:**
  - `useDraggable` を使用し、`id` にはユニークな文字列（例: `pairing-0-player1`, `waiting-123`）を設定
  - `data` プロパティに `{ playerId, playerName, kyuRank, source }` を持たせる
    - `source`: `{ type: 'pairing', pairingIndex, position: 1|2 }` または `{ type: 'waiting' }`
  - 内部で既存の `PlayerChip` を使用してレンダリング
  - ドラッグ中は `opacity: 0.4` で元の位置を薄く表示（`isDragging` を使用）
  - `disabled` プロパティで閲覧モード時にドラッグ無効化
- **依存タスク:** タスク1
- **対応Issue:** #322

### タスク3: DroppableSlot コンポーネントの作成
- [x] 完了
- **概要:** `useDroppable` フックでラップしたドロップ受け付け領域コンポーネントを新規作成する
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/pairings/DroppableSlot.jsx` — 新規作成
- **詳細仕様:**
  - `useDroppable` を使用し、`id` にはユニークな文字列（例: `slot-pairing-0-player1`, `slot-new-pairing`, `slot-waiting-list`）を設定
  - `data` プロパティに `{ slotType, pairingIndex }` を持たせる
    - `slotType`: `"pairing-player1"`, `"pairing-player2"`, `"new-pairing"`, `"waiting-list"`
  - `children` をそのままレンダリング（内部に `DraggablePlayerChip` や空欄表示を配置）
- **依存タスク:** タスク1
- **対応Issue:** #323

### タスク4: PairingGenerator.jsx にDndContextを統合
- [x] 完了
- **概要:** メインコンポーネントに `DndContext`, センサー設定, `DragOverlay`, `onDragEnd` ハンドラを組み込む
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx` — 大幅変更
- **詳細仕様:**
  - **import追加:**
    - `@dnd-kit/core` から `DndContext`, `DragOverlay`, `PointerSensor`, `TouchSensor`, `useSensor`, `useSensors` をインポート
    - `DraggablePlayerChip`, `DroppableSlot` をインポート
  - **import削除:**
    - `Trash2`, `Plus` を lucide-react の import から削除（不要になるため）
  - **センサー設定:**
    - `PointerSensor`: `activationConstraint: { distance: 8 }`（クリックとの誤判定防止）
    - `TouchSensor`: `activationConstraint: { delay: 200, tolerance: 5 }`（長押し200msでドラッグ開始）
  - **state追加:**
    - `activeDragItem` — ドラッグ中の選手情報（`DragOverlay` 表示用）。`null` or `{ playerId, playerName, kyuRank }`
  - **onDragStart ハンドラ:**
    - `activeDragItem` に選手情報をセット
  - **onDragEnd ハンドラ:**
    - `active.data.current` と `over.data.current` から source/destination を判定
    - 以下のケースを処理:
      1. **ペアリング枠 → ペアリング枠:** 2選手のスワップ（既存 `handleSwapPlayer` のロジックを流用）
      2. **待機リスト → ペアリング枠:** 待機選手とペアリング選手の入れ替え
      3. **ペアリング枠 → 待機リスト:** 選手を待機に移動、ペアリング枠を空欄に
      4. **待機リスト → 新規作成ゾーン:** 新しいペアリング行を作成、片方に選手を配置
      5. **ペアリング枠（片方空欄の行）→ 待機リスト:** 選手を待機に戻し、行を自動削除
    - 変更後に `setHasUnsavedChanges(true)`, `saveDraft()`, `fetchPairHistory()` を呼ぶ（既存と同様）
    - `activeDragItem` を `null` にリセット
  - **onDragCancel ハンドラ:**
    - `activeDragItem` を `null` にリセット
  - **DragOverlay:**
    - `activeDragItem` が存在する場合、半透明の `PlayerChip` を表示（`opacity: 0.8`）
  - **保存ボタンの無効化条件追加:**
    - `pairings.some(p => !p.player1Id || !p.player2Id)` の場合、保存ボタンを `disabled` にする
- **依存タスク:** タスク2, タスク3
- **対応Issue:** #324

### タスク5: 編集モードのペアリング行をD&D対応UIに置換
- [x] 完了
- **概要:** 編集モード時の `<select>` ドロップダウンを `DraggablePlayerChip` + `DroppableSlot` に置き換える
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx` — 編集モード描画部分（約L832〜L902）
- **詳細仕様:**
  - **削除するUI:**
    - 左側 `<select>` (L834〜L856)
    - 右側 `<select>` (L861〜L883)
    - ゴミ箱ボタン `<button onClick={handleRemovePair}>` (L895〜L901)
  - **追加するUI（各ペアリング行）:**
    ```
    <DroppableSlot id="slot-pairing-{index}-player1" data={{ slotType: 'pairing-player1', pairingIndex: index }}>
      {pairing.player1Id ? (
        <DraggablePlayerChip id="pairing-{index}-player1" data={{ playerId, playerName, kyuRank, source: { type: 'pairing', pairingIndex: index, position: 1 } }} />
      ) : (
        <空欄表示 — 点線ボーダーで「ここにドロップ」的な表示>
      )}
    </DroppableSlot>
    <span>vs</span>
    <DroppableSlot id="slot-pairing-{index}-player2" data={{ slotType: 'pairing-player2', pairingIndex: index }}>
      {pairing.player2Id ? (
        <DraggablePlayerChip ... />
      ) : (
        <空欄表示>
      )}
    </DroppableSlot>
    <対戦履歴表示（現状維持）>
    ```
  - 対戦履歴表示（日付 or「初」）はそのまま残す
- **依存タスク:** タスク4
- **対応Issue:** #325

### タスク6: 待機リストをD&D対応UIに置換
- [x] 完了
- **概要:** 待機リストの選手を `DraggablePlayerChip` 化し、待機リスト全体を `DroppableSlot` にする
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx` — 待機リスト描画部分（約L908〜L978）
- **詳細仕様:**
  - **削除するUI:**
    - 「組み合わせを追加」ボタン (L915〜L923)
    - ヒントテキスト「※各組み合わせのドロップダウンから選手を入れ替えることができます」(L971〜L973)
  - **変更するUI:**
    - 待機リスト全体を `DroppableSlot`（`id="slot-waiting-list"`, `slotType: "waiting-list"`）でラップ
    - 各待機選手の `PlayerChip` を `DraggablePlayerChip` に変更
      - `id="waiting-{player.id}"`, `source: { type: 'waiting' }`
    - 活動種別のドロップダウン（`<select>` for activityType）はそのまま残す — これはD&Dと無関係
- **依存タスク:** タスク4
- **対応Issue:** #326

### タスク7: 新規ペアリング作成ドロップゾーンの追加
- [x] 完了
- **概要:** 組み合わせリスト下部に新しい組み合わせを作成するためのドロップゾーンを追加する
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx` — 組み合わせリスト直後（約L906の後）
- **詳細仕様:**
  - 編集モード時かつ待機選手が1名以上いる場合に表示
  - `DroppableSlot`（`id="slot-new-pairing"`, `slotType: "new-pairing"`）を使用
  - 見た目: 点線ボーダーの枠、テキスト「ここにドロップして新しい組み合わせを作成」、淡い背景色
  - ドラッグ中（`activeDragItem` が存在し、かつソースが待機リストの場合）に目立つスタイルに変化させると分かりやすい
- **依存タスク:** タスク4
- **対応Issue:** #327

### タスク8: 不要コードの削除・クリーンアップ
- [x] 完了
- **概要:** ドロップダウン移行に伴い不要になったコード・関数を削除する
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx` — 不要コード削除
- **詳細仕様:**
  - `handleSwapPlayer` 関数 — `onDragEnd` に統合されるため削除（ロジックは onDragEnd 内に移植済み）
  - `handleAddPairing` 関数 — 新規作成ドロップゾーンに置き換え済みのため削除
  - `handleRemovePair` 関数 — ゴミ箱ボタン廃止のため削除
  - 未使用の import（`Trash2`, `Plus`）の削除
- **依存タスク:** タスク5, タスク6, タスク7
- **対応Issue:** #328

## 実装順序
1. タスク1: パッケージインストール（依存なし）
2. タスク2 + タスク3: コンポーネント作成（並行可能、タスク1に依存）
3. タスク4: DndContext統合（タスク2, 3に依存）
4. タスク5 + タスク6 + タスク7: UI置換（並行可能、タスク4に依存）
5. タスク8: クリーンアップ（タスク5, 6, 7に依存）
