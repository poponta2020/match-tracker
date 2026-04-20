---
status: completed
audit_source: ユーザー直接フィードバック
selected_items: [タップ選択モード追加]
---
# 対戦組み合わせ画面 選手入替UX改修 要件定義書

## 1. 改修概要

- **対象機能:** 対戦組み合わせ画面（PairingGenerator）の選手入替 UI
- **改修の背景:** ユーザーフィードバック「対戦組み合わせ画面の選手入替用のUIがもう少しモダンにならないか、なんか少しやりづらい」
- **改修スコープ:** タップ選択 → タップ配置モードの追加（既存 D&D は変更なし、レイアウト・見た目は現状維持）
- **対象端末:** スマホのみ（PC は対象外）

## 2. 改修内容

### 2.1 タップ選択 → タップ配置モードの追加

- **現状の問題:** 選手入替は長押し 200ms 発動の D&D のみ。スマホで狙ったチップを掴むのにストレスがあり、「やりづらい」と感じる
- **修正方針:** 既存の D&D は維持したまま、シングルタップで選手を選択 → 別のスロットをタップで配置/スワップする代替モードを追加する
- **修正後のあるべき姿:** 長押しでドラッグしたい人、サクッとタップで済ませたい人、両方が自分に合った操作で選手入替できる

### 2.2 タップ選択モードの動作仕様

1. 編集モード時のみ有効（ロック済みペア・閲覧モード・`isReadOnly` 時は無効）
2. 選手チップをシングルタップ → 選択状態（視覚的に枠線・背景色で表現）
3. 選択中に次のいずれかをタップすると、対応する状態遷移を実行:
   - 別の選手チップ → **スワップ**
   - 空き枠（dashed box `空き`）→ 空き枠へ**移動**
   - 待機エリア（`waiting-list`）→ 待機リストへ**移動**
   - 新規ペア作成ゾーン（`new-pairing`、待機選手選択時のみ表示）→ 新しいペア行を作成
4. もう一度同じチップをタップ → 選択解除
5. 画面他領域（チップ・スロット以外）タップで選択解除
6. 状態遷移ロジックは既存の `computeDragResult`（`pairingDragLogic.js`）をそのまま流用

### 2.3 スコープ外（現状維持）

- チップのサイズ・タップ領域拡大
- ドラッグハンドルアイコンの追加
- 待機列の活動プルダウンの UI 改修
- ドロップ先ハイライトの強化
- PC での操作改善

### 2.4 デフォルト仕様

- **選択中の見た目:** チップに 2px の濃い枠線 + 背景色を少し濃く（例: `bg-[#4a6b5a] text-white border-2 border-[#2d4a3e]`）
- **ドラッグハンドル:** 追加しない
- **選択解除トリガー:** 同チップ再タップ or 画面他領域タップ

## 3. 技術設計

### 3.1 変更ファイル

| ファイル | 変更内容 |
|---|---|
| `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx` | `selectedPlayer` state 追加・タップハンドラ追加・スロット/チップへ onClick 配線 |
| `karuta-tracker-ui/src/pages/pairings/DraggablePlayerChip.jsx` | `onClick` と `isSelected` prop 追加、選択中スタイル適用 |
| `karuta-tracker-ui/src/pages/pairings/DroppableSlot.jsx` | `onClick` prop 追加、`isDragActive` の判定を選択モードにも対応 |

### 3.2 新規 state

```js
// PairingGenerator.jsx
const [selectedPlayer, setSelectedPlayer] = useState(null);
// 構造: { playerId, playerName, source }
//   source は DraggablePlayerChip.data.source と同形
//   例: { type: 'pairing', pairingIndex: 2, position: 1 }
//   例: { type: 'waiting' }
```

### 3.3 チップタップハンドラの挙動

1. `isReadOnly || isViewMode || hasResult` のチップは無視（D&D と同じ保護）
2. `selectedPlayer` が null → そのチップを選択状態に
3. `selectedPlayer.playerId === 今タップしたチップ.playerId` → 選択解除
4. それ以外 → 既存 `computeDragResult` を呼び出してスワップ
   - `dest` はタップしたチップの `source` から導出（`pairing-player1/2` or `waiting-list`）

### 3.4 スロットタップハンドラの挙動

- `selectedPlayer` が null なら何もしない
- 空き枠（`空き` の dashed box）、待機エリア、新規ペア作成ゾーンでタップ受付
- ロック済みペア（`hasResult`）へのタップは無視
- そのスロットの `data` を `dest` として `computeDragResult` を呼び出し、既存 state 更新処理と同じ経路で反映

### 3.5 既存 D&D との共存

- ドラッグ開始時（`handleDragStart`）に `setSelectedPlayer(null)` で選択を解除 → state 不整合防止
- シングルタップは D&D の activationConstraint（`distance: 8`、`delay: 200`）を満たさないため、誤発動しない
- `computeDragResult` はそのまま流用（変更なし）

### 3.6 選択中の視覚表現

- DraggablePlayerChip: `isSelected` の時 `bg-[#4a6b5a] text-white border-2 border-[#2d4a3e]`
- DroppableSlot のハイライト条件: `isDragActive={!!activeDragItem || !!selectedPlayer}` として既存の条件に OR で合流（ハイライトの見た目は変更なし）

### 3.7 画面他領域タップでの選択解除

- `selectedPlayer` が非 null の間、`document` に click リスナーを張る
- チップ/スロット側のハンドラで `e.stopPropagation()` を呼んで伝播を止める
- これにより、チップ/スロット以外をタップした時のみ document リスナーが発火し、選択解除される

## 4. 影響範囲

### 4.1 影響を受ける既存機能（デグレチェック観点）

| 既存機能 | 影響 |
|---|---|
| D&D による選手入替 | 共存可能（ドラッグ開始時に選択解除で整合性確保） |
| 自動組み合わせ生成（`handleAutoMatch`）| 影響なし |
| 保存・キャッシュ（`handleSave`）| 影響なし |
| 既存組み合わせ閲覧/編集モード切替 | 影響なし（タップ選択は編集モード時のみ有効） |
| ロック済みペア（`hasResult`）保護 | タップ選択でも同じ条件で無効化（実装で対応） |
| 待機選手の活動プルダウン | 影響なし（別の行要素に配置されているため） |
| `computeDragResult`（状態遷移ロジック） | 流用のみ、コード変更なし |

### 4.2 破壊的変更

- API 変更: なし
- DB スキーマ変更: なし
- 共通コンポーネント（`PlayerChip`）変更: なし（`DraggablePlayerChip` 側で className 上書き）

### 4.3 テスト

- 既存: `pairingDragLogic.test.js`（純粋関数のテスト）、`PairingGenerator.integration.test.jsx`（統合テスト）
- 新規: タップ選択モードの integration テストを `PairingGenerator.integration.test.jsx` に追記

## 5. 設計判断の根拠

### 5.1 D&D を維持してタップ選択モードを「追加」する方針にした理由

- D&D に慣れたユーザーの操作感を壊さない
- 代替操作の追加はユーザーの選択肢を増やすだけで、既存のワークフローに影響しない
- D&D のみ廃止してタップ選択に置き換えた場合、長押しドラッグで素早く複数操作したいユースケースを損なう

### 5.2 `computeDragResult` を流用する理由

- 状態遷移ロジック（スワップ、空き枠挿入、待機移動、新規ペア作成）は D&D とタップ選択で完全に同一
- ロジックを共通化することで、既存の単体テスト（`pairingDragLogic.test.js`）がそのまま品質担保に寄与
- 実装コストとバグ混入リスクを最小化

### 5.3 チップ拡大・ドラッグハンドル・ハイライト強化をスコープ外にした理由

- ユーザーが「タップ選択モード追加で十分」と判断
- 見た目の変更は D&D 挙動への影響が読みづらく、別改修として切り出すほうが安全
- 最小の変更で「やりづらい」を解消することを優先

### 5.4 PC 対応を含めない理由

- 主な利用端末はスマホ（ユーザー確認済み）
- PC では既存 D&D で十分機能している
- PC 対応を含めるとクリック vs D&D の判定ロジックが複雑化する懸念
