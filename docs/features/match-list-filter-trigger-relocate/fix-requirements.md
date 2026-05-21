---
status: completed
audit_source: ユーザー直接指示（監査レポートなし）
selected_items:
  - 上部「年月」テキストのフィルタートリガー化
  - 適用中フィルタの件数バッジ表示
  - 右下FABの削除（コメントアウト保留）
---

# 対戦結果一覧 フィルタートリガー再配置 改修要件定義書

## 1. 改修概要

### 対象機能
- **画面:** `/matches`（自分または他選手の対戦結果一覧）
- **対象ファイル:**
  - [karuta-tracker-ui/src/pages/matches/MatchList.jsx](../../../karuta-tracker-ui/src/pages/matches/MatchList.jsx)
  - [karuta-tracker-ui/src/components/FilterBottomSheet.jsx](../../../karuta-tracker-ui/src/components/FilterBottomSheet.jsx)

### 改修の背景
- 現状、右下のFAB（フィルターアイコン）から `FilterBottomSheet` を呼び出して絞り込みを行っているが、画面上部に表示されている「YYYY年 M月」テキストとは導線が分かれており、操作の一貫性が低い。
- 上部の「YYYY年 M月」が単なる表示テキストになっており、押せること（変更できること）が分かりにくい。
- FABが右下にあるため、上部の年月表示と絞り込み操作の関係が直感的でない。

### 改修スコープ
1. 上部ナビゲーションバーの「YYYY年 M月」（または「YYYY年」「全期間」）をクリック可能にして、`FilterBottomSheet` を呼び出すトリガーに変更する。
2. クリック可能であることを示すために、`ChevronDown` アイコン・テキスト下線・タップ時の状態変化（Tailwind 状態クラス）を追加する。
3. 年月以外のフィルタ（級・性別・利き手・対戦相手検索・結果）が適用されているとき、適用件数バッジを表示する。
4. 右下のFAB を削除する（コメントアウトで保留）。

## 2. 改修内容

### 2.1 上部「年月」テキストのトリガー化

**現状（[MatchList.jsx:296-302](../../../karuta-tracker-ui/src/pages/matches/MatchList.jsx#L296-L302)）:**

```jsx
<p className="text-sm text-white/70 mt-0.5">
  {selectedYear && selectedMonth
    ? `${selectedYear}年 ${selectedMonth}月`
    : selectedYear
    ? `${selectedYear}年`
    : '全期間'}
</p>
```

- 単なる `<p>` タグでクリック不可
- 視覚的にも「押せる」要素には見えない

**改修後:**

- `<button>` 要素に変更し、`onClick={() => setIsFilterOpen(true)}` を設定
- 年月テキストの右に `ChevronDown` アイコンを追加（`lucide-react` から import）
- テキスト下に下線を追加（`underline decoration-dotted underline-offset-4` または `border-b border-white/40`）
- タップ時の状態クラス: `active:scale-95 active:bg-white/10`
- ホバー時の状態: `hover:bg-white/10`
- パディングを微調整して、タップターゲットを十分な大きさに（最低 44x44px 相当）

**期待する見た目（イメージ）:**

```
┌───────────────────────────────┐
│ 山田太郎  A級           [🔍] │
│ ───────────────              │
│  2026年 5月 ▼  [フィルタ2件] │
│ ─────────                    │
└───────────────────────────────┘
```

### 2.2 適用中フィルタの件数バッジ

**現状:**
- 年月以外のフィルタ（級・性別・利き手・対戦相手検索・結果）が適用されていても、上部には何も表示されない
- 利用者は実際に絞り込み画面を開くまで他のフィルタが効いているか分からない

**改修後:**

- 以下のフィルタが「アクティブ」かどうかを判定:
  - `filterKyuRank`: 空文字以外でアクティブ
  - `filterGender`: 空文字以外でアクティブ
  - `filterDominantHand`: 空文字以外でアクティブ
  - `searchTerm`: 空文字以外でアクティブ
  - `filterResult`: `'全て'` 以外でアクティブ
- アクティブな件数を集計
- 1件以上の場合、年月ボタンの右に「フィルタ N件」のバッジを表示
- 0件の場合はバッジ非表示
- バッジは年月ボタン外に配置し、ボタンクリックでも独立してフィルター画面を開ける（一体感のため、バッジもボタン領域に含めることも可）
- バッジスタイル: `bg-white/20 text-white text-xs px-2 py-0.5 rounded-full`

### 2.3 右下FABの削除

**現状（[MatchList.jsx:473-480](../../../karuta-tracker-ui/src/pages/matches/MatchList.jsx#L473-L480)）:**

```jsx
{/* フローティングアクションボタン (FAB) */}
<button
  onClick={() => setIsFilterOpen(true)}
  className="fixed right-4 z-20 bg-[#4a6b5a] text-white p-4 rounded-full shadow-lg hover:bg-[#3d5a4c] transition-all hover:shadow-xl"
  style={{ bottom: 'calc(4.5rem + env(safe-area-inset-bottom, 0px))' }}
>
  <Filter className="w-6 h-6" />
</button>
```

**改修後:**
- 上記のFABブロックをコメントアウトして残す（将来の参考用、ユーザー指示による）
- `Filter` アイコンの import は `FilterBottomSheet` 内のヘッダーで使用中のため、`MatchList.jsx` 側の import からは削除する

## 3. 技術設計

### 3.1 API変更
- なし

### 3.2 DB変更
- なし

### 3.3 フロントエンド変更

#### `karuta-tracker-ui/src/pages/matches/MatchList.jsx`

**変更内容:**

1. **import の追加**
   - `lucide-react` から `ChevronDown` を追加 import
   - `Filter` の import を削除（FAB削除に伴い使われなくなる）

2. **適用中フィルタ件数の計算**
   ```jsx
   const activeFilterCount = [
     filterKyuRank,
     filterGender,
     filterDominantHand,
     searchTerm,
     filterResult !== '全て' ? filterResult : '',
   ].filter(Boolean).length;
   ```
   - レンダリング時に再計算（React の通常レンダリングで十分、`useMemo` は不要）

3. **上部ナビゲーションバーの構造変更（[MatchList.jsx:288-303](../../../karuta-tracker-ui/src/pages/matches/MatchList.jsx#L288-L303) 周辺）**
   - 既存の `<h1>` （名前 + 級）はそのまま
   - 既存の `<p>` （年月）を `<button>` に変更
   - `ChevronDown` アイコンを追加
   - 下線スタイルを追加
   - 件数バッジを併記
   - onClick: `setIsFilterOpen(true)`

4. **FAB部分のコメントアウト**
   - 既存の FAB ブロック（473-480行付近）を JSX コメント `{/* ... */}` で囲む

#### `karuta-tracker-ui/src/components/FilterBottomSheet.jsx`
- 変更なし（呼び出し側のトリガーが変わるだけで、ボトムシート自体の動作・props は変更なし）

### 3.4 バックエンド変更
- なし

## 4. 影響範囲

### 影響を受ける既存機能
- **対象画面の操作フロー（`/matches`）:** フィルタを開く動作の起点が右下FAB → 上部年月テキストに変わる。機能自体は変わらない。
- **`FilterBottomSheet` コンポーネント:** 内部の動作・props は変更なし。
- **`FilterBottomSheet` の他画面での利用:** 全文検索で確認した結果、`FilterBottomSheet` を import しているのは `MatchList.jsx` のみ。他画面への影響なし。

### 破壊的変更
- なし（APIもDBスキーマも変更なし）
- 既存のURL・クエリパラメータの仕様も変更なし

### 既存テストへの影響
- `karuta-tracker-ui/src/pages/matches/` 配下のテストファイル: `MatchForm.navigation.test.jsx`, `MatchCommentThread.test.jsx`, `byePlayersLogic.test.js`
- `MatchList.jsx` 自体のテストはない（影響なし）

### 既存ドキュメントへの影響
- `docs/SPECIFICATION.md`, `docs/SCREEN_LIST.md`, `docs/DESIGN.md` に対戦結果一覧画面の絞り込みUIが記載されているかを確認し、必要に応じて更新する

## 5. 設計判断の根拠

- **アイコンに `ChevronDown` を選択した理由:** セレクトボックスやドロップダウンとの視覚的類似性により、「クリックして変更できる」がもっとも直感的に伝わる。`Filter` アイコンは「フィルター機能を呼び出す」というメンタルモデルを必要とするが、`ChevronDown` は「ここから選び直せる」というより直接的なメッセージを伝える。

- **件数バッジを「件数表示」にした理由:** ドットだけだと「何があるか分からない」、フィルタ名列挙だと「画面幅が足りなくなる」。件数表示は中間で、必要十分な情報量。

- **タップ波紋を Tailwind の状態クラスで実装する理由:** Material-UI 風の ripple エフェクトを実装するには別途ライブラリやカスタムCSSが必要。`active:scale-95` + `active:bg-white/10` の組み合わせで、押した瞬間にボタンが少し縮み、背景が一瞬光るような視覚効果が得られる。十分なフィードバック。

- **FAB をコメントアウトで残す:** ユーザーの判断による。一般的なベストプラクティスとしては git 履歴に残るため完全削除が望ましいが、ユーザーが「他画面で似た実装を参照したい」等の意図を持っている可能性を尊重。
