---
status: completed
audit_source: ユーザー直接リクエスト
selected_items: [1, 2, 3, 4]
---
# メンターフィードバック コメントUI改修 要件定義書

## 1. 改修概要

- **対象機能**: メンター・メンティー間コメントスレッド（MatchCommentThread）
- **改修の背景**: 現在のコメントUIは通常のフォーム配置であり、ページスクロール時に入力欄が画面外に消える、入力が1行固定で改行できない等、チャットアプリとしてのUXに課題がある。LINEのようなチャットUIに改修し、メッセージのやり取りをより直感的にする。
- **改修スコープ**: フロントエンドUIのみ（バックエンド・API・DBへの変更なし）

## 2. 改修内容

### 2.1 レイアウトをチャットウィンドウ型に変更
- **現状の問題**: コメント一覧（`max-h-96 overflow-y-auto`）と入力フォームが通常フロー配置。ページ全体をスクロールすると入力欄が画面外に消え、文字を打ちながら過去メッセージを確認できない。
- **修正方針**: コンテナを `flex flex-col` + 固定高さに変更。メッセージエリアを `flex-1 overflow-y-auto min-h-0` で独立スクロール可能にし、入力フォームをコンテナ下部に固定する。
- **修正後のあるべき姿**: 入力欄が常にコメントセクション下部に表示され、メッセージ履歴だけが独立してスクロールする（LINE風）。

### 2.2 入力欄を複数行対応（自動リサイズ）に変更
- **現状の問題**: `<input type="text">` で1行固定。長文や改行付きコメントが入力しにくい。
- **修正方針**: `<textarea>` に変更し、入力内容に応じて自動的に高さを拡張する。最大高さ（約4行分）に達したらtextarea内でスクロール。
- **修正後のあるべき姿**: 入力するたびにテキストエリアが自然に広がり、LINEの入力欄と同じ挙動になる。

### 2.3 送信・改行の操作をLINE準拠に変更
- **現状の問題**: Enterキーでフォーム送信。改行を入力する手段がない。
- **修正方針**: Enterで送信、Shift+Enterで改行に変更。
- **修正後のあるべき姿**: LINEと同じ操作感で、Enterで即送信、改行したい場合はShift+Enterを使用。

### 2.4 メッセージエリアの背景をチャット風に変更
- **現状の問題**: 白背景でチャットアプリらしさがない。
- **修正方針**: メッセージエリアに薄いベージュ系の背景色を適用し、チャットウィンドウの雰囲気を出す。
- **修正後のあるべき姿**: LINEのトーク画面のような背景色でチャット感が向上。

## 3. 技術設計

### 3.1 API変更
なし

### 3.2 DB変更
なし

### 3.3 フロントエンド変更

**変更ファイル**: `karuta-tracker-ui/src/pages/matches/MatchCommentThread.jsx`

#### レイアウト構造の変更
```
変更前:
<div className="bg-white rounded-lg shadow-sm p-4">        // 通常フロー
  <h3>コメント</h3>
  <div className="max-h-96 overflow-y-auto">               // 固定max-height
    {messages}
  </div>
  <form>                                                     // フロー配置
    <input type="text" />
  </form>
</div>

変更後:
<div className="bg-white rounded-lg shadow-sm flex flex-col h-[28rem]">  // flex column + 固定高さ
  <div className="p-3 border-b">                                          // ヘッダー固定
    <h3>コメント</h3>
  </div>
  <div className="flex-1 overflow-y-auto min-h-0 p-4 bg-[#f0ebe4]">    // 独立スクロール + チャット背景
    {messages}
  </div>
  <div className="p-3 border-t">                                          // 入力欄固定
    <form>
      <textarea />  // auto-resize対応
    </form>
  </div>
</div>
```

#### textarea自動リサイズ
- `useRef` でtextarea要素を参照
- `onChange` 時に `scrollHeight` を読み取り `style.height` を動的に設定
- 最大高さ（96px ≈ 4行）を超えたら内部スクロール

#### Enter送信 / Shift+Enter改行
- `onKeyDown` で `e.key === 'Enter' && !e.shiftKey` を検知し `handleSubmit` を呼び出す
- `e.shiftKey` が true の場合はデフォルト動作（改行）を許可

#### 送信後のtextareaリセット
- 送信成功後に `setNewComment('')` と同時に textarea の `style.height` を初期値にリセット

### 3.4 バックエンド変更
なし

## 4. 影響範囲

- **変更ファイル**: `MatchCommentThread.jsx` の1ファイルのみ
- **呼び出し元**: `MatchDetail.jsx` (L281) — propsに変更なし、影響なし
- **API連携**: リクエスト/レスポンス形式に変更なし
- **共通コンポーネント**: 使用していないため影響なし
- **破壊的変更**: なし

## 5. 設計判断の根拠

| 判断 | 理由 |
|------|------|
| コンテナ高さを `h-[28rem]` (448px) に固定 | モバイル・デスクトップ両方で適切なチャットウィンドウサイズ。ページ内の他の情報も見える余裕を残す |
| `<input>` → `<textarea>` + auto-resize | LINEの入力欄と同じ挙動を実現するための標準的な手法 |
| Enter送信 / Shift+Enter改行 | LINEのPC版と同一の操作体系。ユーザーが期待する動作 |
| 背景色 `#f0ebe4` | LINEのトーク画面の背景色に近い暖かみのあるベージュ。既存の緑 (`#4a6b5a`) 吹き出しとの相性も良い |
