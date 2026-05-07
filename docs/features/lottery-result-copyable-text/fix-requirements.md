---
status: completed
audit_source: 会話内ヒアリング（監査レポートではなく機能拡張要望が起点）
selected_items: [プレビュー画面へのコピー領域追加]
---

# 抽選結果コピー可能テキスト プレビュー画面表示 改修要件定義書

## 1. 改修概要

### 対象機能
抽選結果LINE告知用コピーテキスト出力機能（`docs/features/lottery-result-copyable-text/`）

### 改修の背景
現状、LINE告知用のコピー領域は **抽選確定後の `LotteryResults.jsx`（抽選結果画面）にのみ** 表示される。
しかし運用上、抽選確定前に管理者が告知文の内容を事前確認したいケースがある。
本機能を拡張し、**抽選プレビュー段階および確定直後の `LotteryManagement.jsx`** でも同じコピー領域を表示する。

### 改修スコープ
- `LotteryManagement.jsx` の `phase === 'preview'` および `phase === 'confirmed'` の状態で、LINE告知用コピー領域を表示する
- プレビューフェーズでは「未確定の抽選結果である」ことを視覚的に示すため、コピーボタンを警告色（オレンジ系）に変える
- 既存の `LotteryResults.jsx` 側のコピー領域は変更しない

---

## 2. 改修内容

### 2.1 コピー領域の追加表示
- **現状の問題:** プレビュー段階で告知文の内容を確認できず、確定後にしか LINE 用テキストを得られない
- **修正方針:** `LotteryManagement.jsx` のプレビュー結果ブロック内に、`buildCopyText` で生成した文面を表示する textarea とコピーボタンを追加する
- **修正後のあるべき姿:**
  - 管理者が抽選プレビュー実行直後に告知文をチェック・コピー可能
  - 確定後も同画面でそのままコピー可能（一度 `LotteryResults.jsx` に遷移する必要がない）

### 2.2 警告色によるフェーズ識別
- **現状の問題:** （新規追加に伴う設計判断）プレビュー段階のテキストを誤って配信してしまうリスクがある
- **修正方針:** `phase === 'preview'` のときのみコピーボタンを警告色（例: `bg-orange-500 hover:bg-orange-600`）にする。`phase === 'confirmed'` は既存 `LotteryResults.jsx` と同じ青系（`bg-blue-600`）で統一
- **修正後のあるべき姿:** 管理者が「いまコピーしようとしている文面が確定済みか未確定か」を一目で判別できる

---

## 3. 技術設計

### 3.1 API変更
**なし**。既存の `lotteryAPI.preview()` のレスポンス（`previewResults`）を流用する。`previewResults` は `getResults()` と同一スキーマ（`sessionId / sessionDate / venueName / matchResults`）であることを確認済み。

### 3.2 DB変更
**なし**。

### 3.3 フロントエンド変更

#### 変更対象: `karuta-tracker-ui/src/pages/lottery/LotteryManagement.jsx`

**追加 import:**
```js
import { buildCopyText, hasAnyWaitlisted } from './lotteryResultText';
```

**追加 state:**
```js
const [copyText, setCopyText] = useState('');
const [copyFeedback, setCopyFeedback] = useState('');
```

**追加 effect:** `previewResults` が更新されるたびに `buildCopyText` を再計算
```js
useEffect(() => {
  if (previewResults.length > 0) {
    setCopyText(buildCopyText(currentDate.year, currentDate.month, previewResults));
  } else {
    setCopyText('');
  }
}, [previewResults, currentDate.year, currentDate.month]);
```

**追加ハンドラ:** `LotteryResults.jsx` の `handleCopy` と同等のロジックをコピー

**追加UIブロック:** `phase === 'preview' || phase === 'confirmed'` のプレビュー結果ブロック内（既存のセッション別結果マップの下、確定ボタン群の上 or 下）に以下を配置：

```jsx
{(phase === 'preview' || phase === 'confirmed') && previewResults.length > 0 && (
  <div className="mt-8 pt-4 border-t">
    <div className="text-sm font-semibold text-gray-700 mb-2">
      管理者向け: LINE告知用テキスト（抽選落ちのみ）
      {phase === 'preview' && (
        <span className="ml-2 text-xs text-orange-700 font-bold">
          ※ プレビュー（未確定）
        </span>
      )}
    </div>
    <textarea
      value={copyText}
      onChange={(e) => setCopyText(e.target.value)}
      rows={12}
      className="w-full font-mono text-xs border border-gray-300 rounded p-2 whitespace-pre"
    />
    <div className="mt-2 flex items-center gap-3">
      <button
        type="button"
        onClick={handleCopy}
        disabled={!hasAnyWaitlisted(previewResults)}
        className={`px-4 py-1.5 text-white text-sm rounded disabled:opacity-50 disabled:cursor-not-allowed ${
          phase === 'preview'
            ? 'bg-orange-500 hover:bg-orange-600'
            : 'bg-blue-600 hover:bg-blue-700'
        }`}>
        コピー
      </button>
      {copyFeedback && (
        <span className="text-sm text-gray-600">{copyFeedback}</span>
      )}
    </div>
  </div>
)}
```

### 3.4 バックエンド変更
**なし**。

---

## 4. 影響範囲

### 4.1 影響を受ける既存機能
- **`LotteryManagement.jsx`**: プレビュー結果セクションに新規UIが挿入される。既存の確定ボタン・通知送信ボタンの位置関係には影響しないよう、コピー領域はそれらの **後** に配置する
- **`LotteryResults.jsx`**: 変更なし
- **`lotteryResultText.js`**: 変更なし（純関数を再利用するのみ）

### 4.2 共通コンポーネント・ユーティリティへの影響
なし。既存の `buildCopyText` / `hasAnyWaitlisted` を変更せずそのまま使用する。

### 4.3 API・DBスキーマの互換性
破壊的変更なし。

### 4.4 認可・セキュリティへの影響
- `LotteryManagement.jsx` 自体が既に管理者ロール限定の画面（既存ガード）であるため、コピー領域に対する追加の認可制御は不要
- プレビュー段階での誤配信リスクは「警告色＋『プレビュー（未確定）』ラベル」で軽減

---

## 5. 設計判断の根拠

| 判断 | 理由 |
|------|------|
| 既存の `buildCopyText` を変更せずそのまま流用 | `previewResults` と `getResults()` のレスポンスが同一スキーマであり、関数は純関数として既にテストされているため改修不要 |
| プレビュー時のみ警告色（オレンジ） | 確定前のテキストを誤って配信するリスクへの視覚的ガード。確定後は既存 `LotteryResults.jsx` と同じ青系で統一感を保つ |
| コピー領域を `LotteryManagement.jsx` 内に直接埋め込み（共通コンポーネント化しない） | 利用箇所が2画面のみで、UI構造が単純。早期の共通化は YAGNI。将来3箇所目が出たら抽出を検討 |
| `previewResults.length > 0` を表示条件に追加 | プレビュー実行前（`previewResults` が空配列）に空テキストエリアが見えるのを防ぐ |
| テスト追加なし | `buildCopyText` は既存の `lotteryResultText.test.js` でカバー済み。UIへの埋め込みは手動確認で十分 |
