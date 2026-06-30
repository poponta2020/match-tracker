---
status: locked
round: 4
chosen_direction: A（取消線＋gray-400＋既存と同じ右端の丸タグ「キャンセル」）
target_screen: 対戦組み合わせ（/pairings, PairingGenerator）の閲覧モード
mock: C:/tmp/design-screen/pairing-cancelled-opponent/preview.html（アプリ実クラス＋実Tailwind）
---
# 対戦組み合わせ「対戦相手キャンセル」表示 design-spec（確定）

requirements.md と同居。ロジック・判定ルールは [requirements.md](./requirements.md) を参照（**ここでは視覚仕様のみ**。重複させない）。

## 方針（確定）
**リデザインしない。現行の本番 PairingGenerator 閲覧モードのまま**、キャンセル表示だけを足す。
実装は既存の className をそのまま使い、**新規の見た目要素は「キャンセルタグ」だけ**。

## 確定仕様（実装そのまま使える className）

対象：閲覧モード（`isReadOnly || isViewMode`、現状 [PairingGenerator.jsx L1045-1051](../../../karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx)）。

### 行の構造
キャンセルを含む行は、**既存の「結果入力済」行（L1002-1021）と同一構造**にする（タグを名前の間に挟まない＝右端固定で揃える）：

```
<div className="px-3 py-2.5">
  <div className="flex items-center gap-2">
    <div className="flex-1 flex items-center justify-center gap-3">
      <span className="font-medium text-[#374151] text-sm">{name1}</span>
      <span className="text-[#a5b4aa] text-xs">vs</span>
      <span className="font-medium text-gray-400 text-sm line-through">{name2}</span>  ← キャンセル側
    </div>
    {キャンセルタグ}
  </div>
</div>
```

### 各要素
| 要素 | className |
|------|-----------|
| キャンセル選手名 | `font-medium text-gray-400 text-sm line-through`（通常名は `text-[#374151]` のまま） |
| キャンセルタグ | `flex items-center gap-1 text-xs bg-gray-100 text-gray-600 px-2 py-0.5 rounded-full whitespace-nowrap` ＋ 先頭に lucide `Ban`（`w-3 h-3`）＋「キャンセル」 |
| 通常名 / vs | 現状維持 |

### 状態
- 片方キャンセル：上記。左右どちらの選手でも、**取消線が付いた方がキャンセルした人**。タグは常に右端。
- 両方キャンセル：**その行は描画しない**（閲覧モードで非表示）。
- 既存の「結果入力済」（青タグ）・「ロック」（amberタグ）と色で並列に見分く。
- 編集モードでは当該スロットが「空き」表示（既存の空きスロット。本spec対象外）。

### 揃いについて（既知・確定）
タグなしの通常行は全幅で中央寄せ、タグ付き行は `flex-1`（全幅−タグ）で中央寄せ。**これは現行アプリの結果入力済の行と同じ挙動**で、通常行とタグ付き行で「vs」位置がわずかに異なる。現行どおりとして確定（完全整列はしない）。

### モック
- 忠実版（アプリ実クラス＋実Tailwind）：`C:/tmp/design-screen/pairing-cancelled-opponent/preview.html`。
- Claude Design「Match Tracker Design System」/ グループ「対戦組み合わせ (現行画面)」/ `pairing-cancelled-opponent-a.html`（プレビュー用の静的CSS版）。

## 要件への宿題
なし（実装は現行画面に合わせる方針で確定）。
