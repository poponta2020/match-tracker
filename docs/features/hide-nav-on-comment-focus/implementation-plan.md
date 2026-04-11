---
status: in-progress
---
# コメント入力時のボトムナビ非表示 実装手順書

## 実装タスク

### タスク1: BottomNavContext の作成
- [x] 完了
- **概要:** ボトムナビの表示状態を管理する React Context とカスタムフックを新規作成する
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/context/BottomNavContext.jsx` — 新規作成。`BottomNavProvider`（`isVisible` state管理）と `useBottomNav` フック（`{ isVisible, setVisible }` を返す）を実装
- **依存タスク:** なし
- **対応Issue:** #434

### タスク2: App.jsx に Provider を追加
- [ ] 完了
- **概要:** アプリ全体を `BottomNavProvider` でラップし、どのコンポーネントからでも Context にアクセスできるようにする
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/App.jsx` — `BottomNavProvider` を import し、既存の JSX ツリーの外側にラップ
- **依存タスク:** タスク1
- **対応Issue:** #435

### タスク3: Layout.jsx のボトムナビにスライドアニメーションを追加
- [ ] 完了
- **概要:** ボトムナビの表示/非表示を Context の `isVisible` に連動させ、CSS transition でスライドアニメーションを実装する
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/components/Layout.jsx` — `useBottomNav()` を呼び出し、`<nav>` 要素に `transition-transform duration-300` と `isVisible ? 'translate-y-0' : 'translate-y-full'` を条件付与
- **依存タスク:** タスク1
- **対応Issue:** #436

### タスク4: MatchCommentThread.jsx に focus/blur ハンドラを追加
- [ ] 完了
- **概要:** コメント入力の textarea にフォーカス/ブラーハンドラを追加し、ボトムナビの表示状態を制御する。チラつき防止の遅延処理と unmount 時のリセットも実装する
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/MatchCommentThread.jsx` — 以下を実装:
    - `useBottomNav()` から `setVisible` を取得
    - `useRef` で blur 遅延用の timerRef を管理
    - `handleNavFocus`: `clearTimeout(timerRef)` → `setVisible(false)`
    - `handleNavBlur`: `timerRef = setTimeout(() => setVisible(true), 100)`
    - 新規コメント textarea（245行目付近）に `onFocus={handleNavFocus}` `onBlur={handleNavBlur}` を追加
    - 編集 textarea（173行目付近）に `onFocus={handleNavFocus}` `onBlur={handleNavBlur}` を追加
    - `useEffect` cleanup で `setVisible(true)` + `clearTimeout(timerRef)` を実行
- **依存タスク:** タスク1
- **対応Issue:** #437

## 実装順序
1. タスク1（依存なし — Context 新規作成）
2. タスク2（タスク1に依存 — Provider ラップ）
3. タスク3（タスク1に依存 — Layout 変更、タスク2と並行可能）
4. タスク4（タスク1に依存 — MatchCommentThread 変更、タスク2と並行可能）
