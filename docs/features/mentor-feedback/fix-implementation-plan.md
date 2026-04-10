---
status: completed
---
# メンターフィードバック コメントUI改修 実装手順書

## 実装タスク

### タスク1: MatchCommentThread を LINE風チャットUIに改修
- [x] 完了
- **概要:** コメントスレッドコンポーネントのレイアウト・入力欄・操作体系をLINE風に全面改修する。1ファイル内の変更で完結する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/MatchCommentThread.jsx` — 以下の4点を改修
    1. コンテナをflex column + 固定高さに変更し、メッセージエリアの独立スクロール + 入力欄下部固定を実現
    2. `<input type="text">` を `<textarea>` に変更し、自動リサイズ（最大4行）を実装
    3. Enter送信 / Shift+Enter改行のキーハンドリングを追加
    4. メッセージエリアの背景色をチャット風ベージュ (`#f0ebe4`) に変更
- **依存タスク:** なし
- **対応Issue:** #413

#### 実装詳細

**1. レイアウト変更**
- 外側コンテナ: `p-4` → `flex flex-col h-[28rem]`（padding除去、flex column化）
- ヘッダー部: `<h3>` を `p-3 border-b` の独立divに分離
- エラー表示: ヘッダー直下に配置
- メッセージエリア: `max-h-96 overflow-y-auto mb-4` → `flex-1 overflow-y-auto min-h-0 p-4 bg-[#f0ebe4]`
- 入力フォーム: `p-3 border-t` の独立divで包む

**2. textarea自動リサイズ**
- `textareaRef = useRef(null)` を追加
- onChange時:
  ```
  textarea.style.height = 'auto';
  textarea.style.height = Math.min(textarea.scrollHeight, 96) + 'px';
  ```
- 送信成功後に `textarea.style.height = 'auto'` でリセット

**3. Enter送信 / Shift+Enter改行**
- onKeyDownハンドラ:
  ```
  if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
    e.preventDefault();
    handleSubmit(e);
  }
  ```
- `isComposing` チェックでIME変換確定時のEnterを無視（日本語入力対応）

**4. 背景色**
- メッセージエリアに `bg-[#f0ebe4]` を適用

## 実装順序
1. タスク1（単一タスク、依存なし）
