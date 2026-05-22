---
status: completed
---
# attendance-save-progress-screen 要件定義書（ドラフト）

## 1. 概要

### 目的
参加登録／キャンセル登録の保存操作を行った際、保存処理中であることを視覚的に明確に示し、保存完了後はユーザーが自分のタイミングでカレンダー画面に戻れるようにする。

### 背景・動機
- 現状の参加登録画面（PracticeParticipation）は、「保存」押下後にボタンが disabled になり「保存中...」ラベルになるだけで、画面の見た目はほぼ変わらない。完了後は1秒間の成功メッセージを表示した直後に自動で `/practice` に遷移するため、ユーザーは「本当に保存できたか」を落ち着いて確認できない。
- キャンセル画面（PracticeCancelPage）は、完了時にブラウザの `alert("キャンセル処理が完了しました")` を出した後そのまま `/practice` に遷移する。alert は唐突で、件数や対象月を確認する余地がない。
- ユーザーは保存処理の進行と完了を明確に把握し、自分のタイミングで次の画面に移りたい。
- 親機能 [[attendance-flow-by-month]] で動線が「カレンダー → 出欠登録モーダル → 参加登録／キャンセル登録 → 保存 → カレンダーに戻る」と定式化された上で、その最後の「保存 → カレンダー」の遷移体験を整える位置付け。

## 2. ユーザーストーリー

### 対象ユーザー
- 一般選手（PLAYER）
- 管理者（ADMIN / SUPER_ADMIN）
- ロールによる挙動の違いはなし

### ユーザーの目的
- 保存処理が進行中であることを画面上で明確に把握したい
- 保存完了を自分の目で確認してから、納得したタイミングでカレンダー画面に戻りたい
- 失敗した場合は失敗を認識し、編集中の内容を保持したまま元の画面に戻りたい

### 利用シナリオ
1. **参加登録の保存**
   - 参加登録画面で複数試合にチェック → 「保存する」押下 → 全画面オーバーレイ「保存中...」表示 → API 完了後、完了画面「保存しました」＋「カレンダーに戻る」ボタン表示 → ボタン押下で `/practice` 遷移
2. **キャンセル登録の実行**
   - キャンセル画面で試合・理由選択 → 「キャンセル実行」押下 → 確認 alert で「OK」 → 全画面オーバーレイ「キャンセル処理中...」表示 → 完了画面「キャンセルしました」＋「カレンダーに戻る」ボタン表示 → ボタン押下で `/practice` 遷移
3. **SAME_DAY 当日12:00以降の二段階確認**
   - 参加登録／キャンセルともに既存の SAME_DAY 確認ダイアログは現状維持で先に表示。ダイアログで「はい」が押された後に、API 呼び出しと同時にオーバーレイ表示。
4. **保存中のエラー**
   - API がエラーを返した場合、オーバーレイ内のテキストが「保存に失敗しました（エラー内容）」＋「閉じる」ボタンに切り替わる。「閉じる」押下でオーバーレイが消え、元の編集画面に戻る。入力中のチェック状態・理由選択は保持。

### 仕様の合意事項
- オーバーレイの中身（保存中／完了／エラー）は **同一の全画面オーバーレイ内でステート切り替え** で表現する（モーダル開閉や画面遷移は行わない）。
- 完了画面には保存件数・試合一覧などの詳細は表示しない。「参加登録を保存しました」「キャンセル処理が完了しました」程度のシンプルなメッセージのみ。
- 保存中のテキストは「保存中...」（参加登録）／「キャンセル処理中...」（キャンセル）で使い分ける。
- 完了画面のボタンは「カレンダーに戻る」のみ。引き続き登録などの追加ボタンは不要。
- オーバーレイ表示中のブラウザ「戻る」「リロード」「タブを閉じる」操作は `beforeunload` で防がず、ブラウザ任せにする（API 自体はサーバー側で完了するため、ユーザーが再度カレンダーで状態を確認すれば良い）。
- 遷移先は `/practice`（カレンダー画面）。クエリパラメータでの年月引き継ぎや `navigate(-1)` は使わない。

## 3. 機能要件

### 3.1 画面仕様

#### 3.1.1 SaveProgressOverlay の状態とUI

SaveProgressOverlay は単一の `overlayState` ステートで以下4状態を管理する。`idle` 以外はすべて全画面オーバーレイとして表示される（`fixed inset-0 z-50`）。

| state | 表示内容 | ユーザー操作 |
|---|---|---|
| `idle` | 非表示 | — |
| `saving` | 中央に lucide-react `Loader2` アイコン（回転）＋テキスト | Esc・背景クリック・閉じるボタン操作いずれも **不可**（オーバーレイ自体がクリックを吸収する） |
| `success` | 中央に lucide-react `CheckCircle2`（緑）＋「保存しました」「キャンセル処理が完了しました」テキスト＋「カレンダーに戻る」ボタン | 「カレンダーに戻る」ボタン押下のみ受け付ける（Esc・背景クリックでは閉じない） |
| `error` | 中央に lucide-react `AlertCircle`（赤）＋失敗メッセージ＋エラー詳細（あれば）＋「閉じる」ボタン | 「閉じる」ボタン押下のみ受け付ける（Esc・背景クリックでは閉じない） |

オーバーレイ表示中は背景画面（チェックボックスや保存ボタンなど）への操作は届かない。

#### 3.1.2 PracticeParticipation（参加登録画面）

- 既存の保存フロー：保存ボタン押下 → SAME_DAY 当日12:00以降の場合は確認ダイアログ → 「はい」押下 → API 呼び出し → 成功時 1秒後 `/practice` 遷移、失敗時インライン赤エラー
- 新フロー：
  - SAME_DAY 確認ダイアログ自体は **現状維持**（独自の `showSameDayConfirm` ステートによるダイアログ）
  - SAME_DAY 確認の「はい」または通常時の保存ボタン押下後、API 呼び出し直前に `setOverlayState('saving')` で `SaveProgressOverlay` を表示
  - 表示テキスト：**「保存中...」**
  - API 成功 → `setOverlayState('success')`、表示テキスト「参加登録を保存しました」
  - API 失敗 → `setOverlayState('error')`、失敗テキスト「保存に失敗しました」＋サーバーからのエラーメッセージ
  - 既存の `setSuccess('参加登録を保存しました')` / `setTimeout(() => navigate('/practice'), 1000)` / `success` ステートと表示部分は **削除**
  - 既存の `setError('保存に失敗しました')` / `error` ステートと、それを表示する赤いインラインエラーバナーは **削除**（オーバーレイの error 状態に一本化）
  - 完了画面の「カレンダーに戻る」押下で `navigate('/practice')`
  - エラー画面の「閉じる」押下で `setOverlayState('idle')`、編集中のチェック状態・participations ステートは保持

#### 3.1.3 PracticeCancelPage（キャンセル画面）

- 既存の保存フロー：キャンセル実行押下 → SAME_DAY 当日12:00以降は確認ダイアログ → 「はい」押下 → 通常の試合キャンセル確認 `window.confirm` → API 呼び出し → 成功時 `alert("キャンセル処理が完了しました")` → `navigate('/practice')`、失敗時インライン赤エラー
- 新フロー：
  - SAME_DAY 確認ダイアログは現状維持
  - 通常のキャンセル確認 `window.confirm` も現状維持（複数件キャンセルの最終確認）
  - すべての確認を通った後、API 呼び出し直前に `setOverlayState('saving')` で `SaveProgressOverlay` を表示
  - 表示テキスト：**「キャンセル処理中...」**
  - API 成功 → `setOverlayState('success')`、表示テキスト「キャンセル処理が完了しました」
  - API 失敗 → `setOverlayState('error')`、失敗テキスト「キャンセルに失敗しました」＋サーバーからのエラーメッセージ（`err.response?.data?.message`）
  - 既存の `alert("キャンセル処理が完了しました")` と直後の `navigate('/practice')` は **削除**
  - 既存の `setError(err.response?.data?.message || 'キャンセルに失敗しました')` と、それを表示する赤いインラインエラーは **削除**（オーバーレイの error 状態に一本化）
  - 完了画面の「カレンダーに戻る」押下で `navigate('/practice')`
  - エラー画面の「閉じる」押下で `setOverlayState('idle')`、編集中の選択状態（selectedMatches, cancelReason, cancelReasonDetail）は保持

### 3.2 ビジネスルール

- **API 呼び出し直前にオーバーレイを表示**する。SAME_DAY 確認や `window.confirm` などのプリチェックは現状の挙動を維持し、それらを通過してから API を叩く瞬間にオーバーレイを表示する。
- **二重リクエスト防止**：オーバーレイ表示中は背景の保存ボタンが見えないわけではない（半透明オーバーレイの下に存在する）が、クリックは届かないため二重リクエストは発生しない。既存の `saving` ステートの disabled 制御は冗長になるが、安全側として残す。
- **完了後の遷移はユーザーのボタン押下のみ**で行う。タイマーによる自動遷移は **行わない**。
- **エラー時の入力保持**：エラーの「閉じる」を押すと、編集中のチェック状態（PracticeParticipation の `participations`）や選択状態（PracticeCancelPage の `selectedMatches`, `cancelReason`, `cancelReasonDetail`, `selectedDate`, `selectedSession`）は **完全に保持**する。これにより再試行が容易になる。
- **ブラウザの戻る／リロード／タブを閉じる操作**は `beforeunload` で防がない。ブラウザ任せとし、サーバー側で完了している保存は次回カレンダー表示時に反映される。
- **オーバーレイは Esc キー・背景クリックでは閉じない**。明示的にボタンを押させる。これは「保存中の誤閉じ防止」「完了をユーザーに確実に認識させる」ための制約。
- **エラーメッセージ**：`err.response?.data?.message` があればそれを使用し、なければデフォルト文（「保存に失敗しました」「キャンセルに失敗しました」）を使う。HTTPステータスコードなどの技術詳細はユーザー向けには表示しない（コンソールに `console.error` は残す）。

### 3.3 エラーケース・境界条件

- **ネットワークタイムアウト／オフライン**：axios のデフォルト挙動でエラーが返るので、`overlayState='error'` に切り替わり、「閉じる」ボタンで編集画面に戻る。
- **API 4xx/5xx エラー**：上記と同じく `error` 状態。`err.response?.data?.message` を表示。
- **保存対象が空の場合**：既存のロジック（`if (selectedMatches.length === 0) return;` 等）は維持。空ならそもそも API を呼ばずオーバーレイも出さない。
- **ボタン連打**：「カレンダーに戻る」連打は同一パスへの `navigate` で React Router 側で吸収。「閉じる」連打は `setOverlayState('idle')` を複数回呼ぶだけで害なし。
- **既存の SAME_DAY 確認ダイアログ表示中**：オーバーレイは表示されないので影響なし。

## 4. 技術設計

### 4.1 API設計

**追加・変更なし**。既存の以下のAPIをそのまま利用する。
- `practiceAPI.registerParticipations(data)` （参加登録）
- `lotteryAPI.cancelMultiple(participantIds, cancelReason, cancelReasonDetail)` （キャンセル）

### 4.2 DB設計

**追加・変更なし**。

### 4.3 フロントエンド設計

#### 4.3.1 新規共通コンポーネント `SaveProgressOverlay`

**ファイル:** `karuta-tracker-ui/src/components/SaveProgressOverlay.jsx`

**Props:**

| prop | 型 | 必須 | 説明 |
|---|---|---|---|
| `state` | `'idle' \| 'saving' \| 'success' \| 'error'` | ○ | オーバーレイの状態。`idle` のときは `null` を返し非表示 |
| `savingMessage` | `string` | ○ | `state==='saving'` のときに表示するテキスト（例：「保存中...」「キャンセル処理中...」） |
| `successMessage` | `string` | ○ | `state==='success'` のときに表示するテキスト（例：「参加登録を保存しました」） |
| `errorMessage` | `string` | ○ | `state==='error'` のときの見出しテキスト（例：「保存に失敗しました」） |
| `errorDetail` | `string` | × | エラー詳細（サーバーからの `err.response?.data?.message`）。空文字なら非表示 |
| `onSuccessConfirm` | `() => void` | ○ | 完了画面の「カレンダーに戻る」押下ハンドラ |
| `onErrorClose` | `() => void` | ○ | エラー画面の「閉じる」押下ハンドラ |

**実装イメージ:**

```jsx
import { Loader2, CheckCircle2, AlertCircle } from 'lucide-react';

const SaveProgressOverlay = ({
  state,
  savingMessage,
  successMessage,
  errorMessage,
  errorDetail = '',
  onSuccessConfirm,
  onErrorClose,
}) => {
  if (state === 'idle') return null;

  return (
    <div
      className="fixed inset-0 z-50 bg-black/40 flex items-center justify-center px-4"
      role="dialog"
      aria-modal="true"
      aria-busy={state === 'saving'}
    >
      <div className="bg-white rounded-lg shadow-lg p-6 max-w-sm w-full">
        {state === 'saving' && (
          <div className="flex flex-col items-center">
            <Loader2 className="w-12 h-12 text-blue-500 animate-spin mb-3" aria-hidden="true" />
            <p className="text-base font-medium text-gray-800">{savingMessage}</p>
          </div>
        )}
        {state === 'success' && (
          <div className="flex flex-col items-center">
            <CheckCircle2 className="w-12 h-12 text-green-500 mb-3" aria-hidden="true" />
            <p className="text-base font-medium text-gray-800 mb-4 text-center">{successMessage}</p>
            <button
              type="button"
              onClick={onSuccessConfirm}
              className="w-full px-4 py-2 bg-blue-500 text-white rounded font-medium hover:bg-blue-600"
            >
              カレンダーに戻る
            </button>
          </div>
        )}
        {state === 'error' && (
          <div className="flex flex-col items-center">
            <AlertCircle className="w-12 h-12 text-red-500 mb-3" aria-hidden="true" />
            <p className="text-base font-medium text-gray-800 mb-2 text-center">{errorMessage}</p>
            {errorDetail && (
              <p className="text-sm text-gray-600 mb-4 text-center break-words">{errorDetail}</p>
            )}
            <button
              type="button"
              onClick={onErrorClose}
              className="w-full px-4 py-2 bg-gray-200 text-gray-800 rounded font-medium hover:bg-gray-300"
            >
              閉じる
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

export default SaveProgressOverlay;
```

**設計上のポイント:**
- 半透明黒の全画面オーバーレイ（`bg-black/40` + `fixed inset-0 z-50`）が背景クリックを吸収するため、背景画面（保存ボタン等）への操作はブロックされる。
- 内側のカードは `max-w-sm` で読みやすく、`px-4` の左右パディングでモバイル幅でも見切れない。
- Esc キーや背景クリックでの閉じる処理は **実装しない**（要件3.2による）。
- `role="dialog"`, `aria-modal="true"`, `aria-busy` でアクセシビリティ対応。

#### 4.3.2 `PracticeParticipation.jsx` の変更

**追加ステート:**
```js
const [overlayState, setOverlayState] = useState('idle'); // 'idle' | 'saving' | 'success' | 'error'
const [overlayErrorDetail, setOverlayErrorDetail] = useState('');
```

**削除ステート:**
```js
const [success, setSuccess] = useState('');  // 削除
```

**`error` ステートは保持**：初期データ取得失敗（line 73 の `setError('データの取得に失敗しました')`）の表示で引き続き使用するため。保存処理での `setError` 呼び出しのみオーバーレイに置き換える。

**`handleSave` の変更:**
- `setError('');` の直後で `setOverlayState('saving');` `setOverlayErrorDetail('');` を実行
- 成功時: `setSuccess('参加登録を保存しました')` と `setTimeout(() => navigate('/practice'), 1000)` を削除し、`setOverlayState('success')` に置き換え
- 失敗時: `setError('保存に失敗しました')` を削除し、`setOverlayErrorDetail(err.response?.data?.message || '')` と `setOverlayState('error')` に置き換え
- `finally { setSaving(false); }` は維持（ボタンの disabled 解除のため）

**JSX 変更:**
- `{success && (...)}` の緑バナー表示を削除（line 294-298）
- `{error && (...)}` の赤バナー表示は **維持**（初期取得エラー用）
- コンポーネント末尾に `<SaveProgressOverlay>` を追加：
  ```jsx
  <SaveProgressOverlay
    state={overlayState}
    savingMessage="保存中..."
    successMessage="参加登録を保存しました"
    errorMessage="保存に失敗しました"
    errorDetail={overlayErrorDetail}
    onSuccessConfirm={() => navigate('/practice')}
    onErrorClose={() => setOverlayState('idle')}
  />
  ```
- 保存ボタンの label `{saving ? '保存中...' : '保存する'}` は維持（オーバーレイ表示前のミリ秒間の挙動として残す）

#### 4.3.3 `PracticeCancelPage.jsx` の変更

**追加ステート:**
```js
const [overlayState, setOverlayState] = useState('idle');
const [overlayErrorDetail, setOverlayErrorDetail] = useState('');
```

**`error` ステートは保持**：初期データ取得失敗（line 65 の `setError('データの取得に失敗しました')`）の表示で引き続き使用。

**`handleCancel` の変更:**
- 既存の `window.confirm(...)` による試合キャンセル確認は維持
- `setCancelling(true); setError('');` の直後で `setOverlayState('saving'); setOverlayErrorDetail('');` を実行
- 成功時: `alert('キャンセル処理が完了しました'); navigate('/practice');` を削除し、`setOverlayState('success')` に置き換え
- 失敗時: `setError(err.response?.data?.message || 'キャンセルに失敗しました')` を削除し、`setOverlayErrorDetail(err.response?.data?.message || '')` と `setOverlayState('error')` に置き換え
- `finally { setCancelling(false); }` は維持

**JSX 変更:**
- `{error && (...)}` の赤バナー表示は **維持**（初期取得エラー用）
- コンポーネント末尾に `<SaveProgressOverlay>` を追加：
  ```jsx
  <SaveProgressOverlay
    state={overlayState}
    savingMessage="キャンセル処理中..."
    successMessage="キャンセル処理が完了しました"
    errorMessage="キャンセルに失敗しました"
    errorDetail={overlayErrorDetail}
    onSuccessConfirm={() => navigate('/practice')}
    onErrorClose={() => setOverlayState('idle')}
  />
  ```

#### 4.3.4 テスト方針

| ファイル | 内容 |
|---|---|
| `components/SaveProgressOverlay.test.jsx`（新規） | 4状態（idle / saving / success / error）の描画、各ボタンクリックでハンドラが呼ばれるか、errorDetail の有無による表示切替 |
| `pages/practice/PracticeParticipation.test.jsx`（既存があれば拡張、なければ新規） | 保存ボタン押下 → saving 表示 → API モックで成功 → success 表示 → 「カレンダーに戻る」押下で `/practice` に navigate、エラー時の error 表示と「閉じる」での復帰 |
| `pages/practice/PracticeCancelPage.test.jsx`（既存テストを拡張） | キャンセル実行 → 既存 confirm を OK → saving → success → ボタンで遷移、エラー時の挙動。`alert` の呼び出しがないことの確認 |

### 4.4 バックエンド設計

**変更なし**。

## 5. 影響範囲

### 5.1 変更ファイル一覧

**フロントエンド（新規）**
- `karuta-tracker-ui/src/components/SaveProgressOverlay.jsx`
- `karuta-tracker-ui/src/components/SaveProgressOverlay.test.jsx`

**フロントエンド（変更）**
- `karuta-tracker-ui/src/pages/practice/PracticeParticipation.jsx`
- `karuta-tracker-ui/src/pages/practice/PracticeCancelPage.jsx`

**フロントエンド（既存テスト調整）**
- `karuta-tracker-ui/src/pages/practice/PracticeCancelPage.test.jsx`：既存テストは UI 統一の検証のみで保存フローに触れていないため、本機能では基本的に追加変更なし。新規にオーバーレイ表示の挙動を検証するテストケースを追加できればなお良い。

**バックエンド** — 変更なし。

**DB** — 変更なし。

**ドキュメント**
- `docs/SCREEN_LIST.md`：PracticeParticipation・PracticeCancelPage の画面説明に「保存／キャンセル実行時はオーバーレイで進捗を表示し、完了後にボタン押下でカレンダーへ戻る」と追記
- `docs/SPECIFICATION.md`：出欠登録／キャンセル機能の節に保存時の挙動を追記

### 5.2 既存機能への影響

- **PracticeParticipation の保存後挙動の変更**：
  - 旧：保存完了 → 1秒間「参加登録を保存しました」緑バナー表示 → 自動で `/practice` へ遷移
  - 新：保存完了 → 全画面オーバーレイで「参加登録を保存しました」表示 → ユーザーが「カレンダーに戻る」を押すと `/practice` へ遷移
  - ユーザー操作が1ステップ増えるが、確認の余地ができる。
- **PracticeCancelPage のキャンセル後挙動の変更**：
  - 旧：API 完了 → ブラウザの `alert('キャンセル処理が完了しました')` → OK 押下で `/practice` へ遷移
  - 新：API 完了 → 全画面オーバーレイで「キャンセル処理が完了しました」＋ボタン表示 → ボタン押下で `/practice` へ遷移
  - `alert` から自前UIへの置き換え。UI 一貫性が向上。
- **保存エラー表示の場所変更**：
  - 旧：両画面ともページ上部の赤バナーに「保存に失敗しました」「（サーバーメッセージ）」を表示
  - 新：全画面オーバーレイのエラー状態で表示
  - **初期データ取得失敗（"データの取得に失敗しました"）の赤バナーは現状維持**。保存エラーのみオーバーレイに移行。
- **`success` ステート（PracticeParticipation）の廃止**：他に参照箇所はないため安全に削除可能。

### 5.3 共通コンポーネント・ユーティリティへの影響

- `SaveProgressOverlay.jsx` は新規追加。他画面（伝助等）も将来的に同じパターンを採用しうるが、本機能の範囲では参加登録・キャンセル登録の2画面のみ参照。
- `LoadingScreen.jsx` は変更なし。これは画面遷移時の初期ローディングに使い続ける。
- `lucide-react` の `Loader2`, `CheckCircle2`, `AlertCircle` を新たに使用するが、いずれもプロジェクトで既に利用実績あり（`AlertCircle` は両画面で import 済み）。

### 5.4 API・DBスキーマの互換性

- API変更なし、DB変更なし、互換性問題なし。

### 5.5 動線への影響

- 「カレンダー → 出欠登録モーダル → 参加登録／キャンセル登録 → 保存 → カレンダーに戻る」という親機能 [[attendance-flow-by-month]] の動線は維持。最後の「保存 → カレンダー」が「自動 / alert」から「ボタンによる明示的な遷移」に変わるのみ。
- ユーザー視点では「保存 → 完了確認 → カレンダーへ戻る」の3ステップが視覚的に分かれることで、何が起きたかを把握しやすくなる。

## 6. 設計判断の根拠

### 6.1 オーバーレイを画面遷移にしない理由

専用ページ（例：`/practice/saving`）への遷移ではなく、同じ画面上のオーバーレイで状態を切り替える設計を採用した理由：
- 編集中のステート（チェック状態、選択した試合・理由）をエラー時に保持しやすい。画面遷移するとステート復元の仕組みが必要になる。
- 戻る操作（エラー時の「閉じる」）が単に `setOverlayState('idle')` で済み、navigate を介さない。
- URL が変わらないため、ブラウザ履歴に「保存中ページ」が積まれず履歴が綺麗になる。

### 6.2 単一の `overlayState` ステートを採用する理由

既存の `saving`, `error`, `success` を組み合わせるのではなく、新たに単一の `overlayState` を導入する理由：
- オーバーレイは「保存中 → 完了 or 失敗」と単方向に遷移するため、状態は本質的に排他的。複数 boolean では矛盾した状態（例：saving と success が両方 true）が表現できてしまう。
- 既存の `saving` は「ボタンを disabled にする」用途で残し、`overlayState` は「オーバーレイのUI」用途で持つ、と関心を分離。
- `success` ステート（PracticeParticipation）は他に使用箇所がないため削除可能。`error` ステートは初期取得エラーで引き続き必要なので保持。

### 6.3 ボタンによる明示的な遷移を採用する理由

タイマーによる自動遷移ではなく、ユーザーのボタン操作で `/practice` に戻す理由：
- 現状の1秒タイマー（PracticeParticipation）は短すぎて完了メッセージを読み切れないという課題があった。
- alert（PracticeCancelPage）はモーダルだが OS のスタイルで現れるため UI 一貫性に欠ける。
- ユーザーが自分のタイミングで遷移することで、操作の意図性と安心感が増す。

### 6.4 SAME_DAY 確認ダイアログを維持する理由

参加登録／キャンセル登録ともに、SAME_DAY（当日12:00以降）の確認ダイアログは現状の独自実装を維持する：
- このダイアログは「本当に進めるか」のプリチェックであり、API 呼び出し前に必須の意思確認である。
- オーバーレイは API 呼び出しと一体の進捗UI であり、役割が異なる。
- 一画面上で確認ダイアログ → オーバーレイの順に切り替わる体験は、保存処理の段階性を表現できる。

### 6.5 Esc／背景クリックで閉じない理由

オーバーレイは Esc キーや背景クリックでは閉じない設計とした理由：
- 保存中：API 中断はできない（fetch/axios の中断は実装していない）ため、ユーザーが意図的に閉じてしまうと「裏で保存され続ける」状態になり混乱を生む。
- 完了：自動的に閉じると次の遷移先が曖昧。ユーザーに「戻る」を明示的に押させる。
- エラー：誤操作で閉じて編集中の入力が失われると最悪。明示的な「閉じる」ボタンを押させて意図を確認する。

### 6.6 SaveProgressOverlay を共通コンポーネント化する理由

参加登録・キャンセル登録の2画面でほぼ同じUIを使うため、共通コンポーネント化する：
- 重複コード（オーバーレイJSXと状態遷移ロジック）を回避。
- 将来的に他画面（伝助ページ作成、団体作成など）でも同じパターンが必要になれば再利用可能。
- props でテキストとハンドラを受け取る形のシンプルな設計で十分。
