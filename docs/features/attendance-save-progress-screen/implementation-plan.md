---
status: completed
---
# attendance-save-progress-screen 実装手順書

## 実装タスク

### タスク1: SaveProgressOverlay 共通コンポーネントの新規作成
- [x] 完了
- **概要:** 全画面オーバーレイで4状態（idle / saving / success / error）を切り替える共通コンポーネント `SaveProgressOverlay` を新規作成する。Loader2／CheckCircle2／AlertCircle アイコン、Tailwind スタイル、props（state, savingMessage, successMessage, errorMessage, errorDetail, onSuccessConfirm, onErrorClose）を実装する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/components/SaveProgressOverlay.jsx` — 新規作成。要件定義書 4.3.1 の実装イメージに従う。
- **依存タスク:** なし
- **対応Issue:** #702

### タスク2: SaveProgressOverlay のユニットテスト追加
- [x] 完了
- **概要:** `SaveProgressOverlay` の4状態それぞれの描画、ボタンクリックでハンドラが呼ばれること、errorDetail の有無での表示切替を検証するテストを追加する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/components/SaveProgressOverlay.test.jsx` — 新規作成。Vitest + @testing-library/react を使い、各 state での描画とハンドラ呼び出しを検証。
- **依存タスク:** タスク1
- **対応Issue:** #703

### タスク3: PracticeParticipation の保存フローに SaveProgressOverlay を組み込む
- [x] 完了
- **概要:** PracticeParticipation.jsx で、保存ボタン押下時のオーバーレイ表示・成功時の手動遷移・失敗時のエラー表示を実装する。既存の `success` ステート、setTimeout による自動遷移、保存失敗時の `setError` を削除し、`overlayState` + `overlayErrorDetail` に置き換える。初期データ取得失敗時の `error` 表示は維持する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/practice/PracticeParticipation.jsx` — 要件定義書 4.3.2 に従って変更。
    - `useState` 追加: `overlayState`, `overlayErrorDetail`
    - `useState` 削除: `success` / `setSuccess`
    - `handleSave` 内の状態遷移を更新（saving / success / error）
    - 既存の緑バナー（`{success && ...}`）削除
    - 保存エラー用の `setError('保存に失敗しました')` 削除（初期取得用 `setError` は保持）
    - コンポーネント末尾に `<SaveProgressOverlay>` を配置
- **依存タスク:** タスク1
- **対応Issue:** #704

### タスク4: PracticeCancelPage のキャンセルフローに SaveProgressOverlay を組み込む
- [x] 完了
- **概要:** PracticeCancelPage.jsx で、キャンセル実行時のオーバーレイ表示・成功時の手動遷移・失敗時のエラー表示を実装する。既存の `alert('キャンセル処理が完了しました')`、直後の `navigate('/practice')`、保存失敗時の `setError` を削除し、`overlayState` + `overlayErrorDetail` に置き換える。SAME_DAY 確認ダイアログと `window.confirm` による試合キャンセル確認は現状維持。初期取得失敗時の `error` 表示は維持。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/practice/PracticeCancelPage.jsx` — 要件定義書 4.3.3 に従って変更。
    - `useState` 追加: `overlayState`, `overlayErrorDetail`
    - `handleCancel` 内の状態遷移を更新
    - `alert(...)` および直後の `navigate('/practice')` を削除
    - キャンセル失敗用の `setError(...)` 削除（初期取得用は保持）
    - コンポーネント末尾に `<SaveProgressOverlay>` を配置
- **依存タスク:** タスク1
- **対応Issue:** #705

### タスク5: ドキュメント更新（SCREEN_LIST.md / SPECIFICATION.md）
- [x] 完了
- **概要:** PracticeParticipation・PracticeCancelPage の画面説明に「保存／キャンセル実行時はオーバーレイで進捗を表示し、完了後にボタン押下でカレンダーへ戻る」と追記する。
- **変更対象ファイル:**
  - `docs/SCREEN_LIST.md` — 該当画面（参加登録画面・キャンセル画面）の項目に保存フローの新挙動を追記。
  - `docs/SPECIFICATION.md` — 出欠登録／キャンセル機能の節に保存進捗UIの仕様を追記。
- **依存タスク:** タスク3, タスク4
- **対応Issue:** #706

### タスク6: 動作確認（dev サーバーでの手動E2E）
- [ ] 完了
- **概要:** dev サーバーを立ち上げ、参加登録画面とキャンセル画面の両方で以下を手動確認する。
  - 参加登録：通常保存／SAME_DAY 確認経由の保存／API エラー時／エラー閉じてリトライ／カレンダーに戻るボタン
  - キャンセル登録：通常キャンセル／SAME_DAY 確認経由／API エラー時／エラー閉じてリトライ／カレンダーに戻るボタン
  - オーバーレイ表示中に背景がクリックできないことを確認
- **変更対象ファイル:** なし（動作確認のみ）
- **依存タスク:** タスク3, タスク4
- **対応Issue:** #707

## 実装順序

1. **タスク1**: SaveProgressOverlay の新規作成（依存なし）
2. **タスク2**: SaveProgressOverlay のテスト（タスク1に依存）
3. **タスク3 / タスク4**: PracticeParticipation / PracticeCancelPage の改修（いずれもタスク1に依存。タスク3とタスク4は互いに独立なので並行可能）
4. **タスク5**: ドキュメント更新（タスク3・4が完了してから）
5. **タスク6**: 手動 E2E 動作確認（タスク3・4が完了してから）
