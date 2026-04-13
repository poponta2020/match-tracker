---
status: completed
---
# コメント入力時のボトムナビ非表示 要件定義書

## 1. 概要
- **目的:** コメント入力中に誤ってボトムナビゲーションをタップし、意図しない画面遷移が発生することを防ぐ
- **背景・動機:** 試合詳細画面（`/matches/:id`）のコメント欄でテキスト入力中、ボトムナビが近い位置にあるため誤タップしやすい。入力内容が破棄されてしまうストレスがある

## 2. ユーザーストーリー
- **対象ユーザー:** メンター・メンティー（コメント機能を利用する全ユーザー）
- **ユーザーの目的:** コメント入力中に誤操作で画面遷移しない安心感を得る
- **利用シナリオ:**
  1. 試合詳細画面でコメント入力欄をタップする
  2. ボトムナビがスライドダウンして非表示になる
  3. 安心してコメントを入力・送信する
  4. 入力欄からフォーカスが外れるとボトムナビがスライドアップして再表示される

## 3. 機能要件

### 3.1 画面仕様

#### 対象コンポーネント
- `MatchCommentThread.jsx` 内の2つの textarea
  - 新規コメント投稿用 textarea（245行目付近）
  - 既存コメント編集用 textarea（173行目付近）

#### 動作フロー
1. textarea に `focus` イベント発生 → ボトムナビを非表示にする
2. textarea に `blur` イベント発生 → ボトムナビを再表示する
3. コンポーネントの unmount 時 → ボトムナビを再表示にリセットする

#### アニメーション
- 非表示: 下方向にスライドアウト（`translate-y-full` + `transition`）
- 再表示: 下方向からスライドイン（`translate-y-0` + `transition`）
- トランジション時間: 300ms 程度

#### チラつき防止
- 編集 textarea → 新規コメント textarea のように、textarea 間でフォーカスが移動する場合、blur → focus が連続発火する
- blur 時に約100msの遅延を設け、次の focus でその遅延をキャンセルすることで、ナビがチラつくのを防ぐ

### 3.2 ビジネスルール
- デフォルト状態ではボトムナビは常に表示（既存動作を維持）
- コメント入力欄以外の画面では一切影響しない
- 画面遷移によって MatchCommentThread が unmount された場合、自動的にナビを表示状態にリセットする

## 4. 技術設計

### 4.1 API設計
- API変更なし

### 4.2 DB設計
- DB変更なし

### 4.3 フロントエンド設計

#### 新規ファイル
- `karuta-tracker-ui/src/context/BottomNavContext.jsx`
  - `BottomNavProvider` コンポーネント: `isVisible` state（デフォルト `true`）を管理
  - `useBottomNav` カスタムフック: `{ isVisible, setVisible }` を返す

#### 変更ファイル

**`App.jsx`**
- `BottomNavProvider` で全体をラップ

**`components/Layout.jsx`**
- `useBottomNav()` から `isVisible` を取得
- ボトムナビの `<nav>` 要素に以下のクラスを条件付与:
  - `transition-transform duration-300`
  - `isVisible ? 'translate-y-0' : 'translate-y-full'`

**`pages/matches/MatchCommentThread.jsx`**
- `useBottomNav()` から `setVisible` を取得
- 新規コメント textarea と編集 textarea に `onFocus` / `onBlur` ハンドラを追加
- blur 時に `setTimeout(100ms)` で遅延、focus 時に `clearTimeout` でキャンセル（チラつき防止）
- `useEffect` の cleanup で `setVisible(true)` を呼び、unmount 時にリセット

### 4.4 バックエンド設計
- バックエンド変更なし

## 5. 影響範囲
- **変更が必要な既存ファイル:**
  - `karuta-tracker-ui/src/App.jsx` — Provider追加（ラップのみ）
  - `karuta-tracker-ui/src/components/Layout.jsx` — ボトムナビのアニメーション追加
  - `karuta-tracker-ui/src/pages/matches/MatchCommentThread.jsx` — focus/blurハンドラ追加
- **既存機能への影響:**
  - Context のデフォルト値が `visible: true` のため、他の全画面は影響なし
  - Layout.jsx のボトムナビにトランジション用クラスが追加されるが、デフォルトは `translate-y-0`（現状と同じ位置）なので見た目の変化なし

## 6. 設計判断の根拠
- **React Context を採用した理由:** MatchCommentThread と Layout はコンポーネントツリー上離れており、props のバケツリレーが不自然。Context で疎結合に繋ぐのが最もシンプル
- **CSS transition を採用した理由:** ユーザーの「スッと下に消えてほしい」という要望に応えるため。JS アニメーションライブラリは不要な依存追加になるため避けた
- **blur 遅延方式を採用した理由:** textarea 間のフォーカス移動時のチラつきを防ぐ最もシンプルなパターン
