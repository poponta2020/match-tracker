---
status: completed
issue: 864
---
# 対戦変更導線の統合（組み合わせ作成画面へ一本化） 改修実装手順書

## 実装タスク

### タスク1: PairingGenerator の戻り先を動的化
- [x] 完了
- **概要:** `from` クエリパラメータがあればそれを「戻る」先に使う。無ければ従来どおり `/settings`。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx` — `const backTo = searchParams.get('from') || '/settings';` を定義し、loading 時（L720）と本体（L731）の `<PageHeader backTo=... />` に適用
- **依存タスク:** なし
- **対応Issue:** #864

### タスク2: BulkResultInput の「対戦変更」ボタン遷移化＋確認ダイアログ＋編集モード削除
- [x] 完了
- **概要:** ボタンを editMode トグルから `/pairings` 遷移に変更。未保存入力があれば `window.confirm` で確認。不要になった画面内編集モード関連コードを削除。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/BulkResultInput.jsx`
    - onClick を `navigate(`/pairings?date=${session.sessionDate}&matchNumber=${currentMatchNumber}&from=${encodeURIComponent(`/matches/bulk-input/${sessionId}`)}`)` に変更
    - `changedMatches.size > 0` のとき遷移前に `window.confirm(...)`、キャンセルなら遷移しない
    - ボタン表示を「対戦変更」固定に（トグル/「完了」廃止）
    - 削除: `editMode` / `selectingPairing` / `updatingPairing` / `participants` state、`handlePlayerChange` / `getAvailablePlayers`、選手選択リスト UI、editMode 分岐（ヒント文・選手タップ・抜け番/保存ボタン表示条件）、未使用 import（`X` 等）
    - 維持: 参加者データ取得（`allParticipants` → `computeByePlayers`）による抜け番算出
- **依存タスク:** なし（タスク1と独立だが、UX 上はセットで意味を持つ）
- **対応Issue:** #864

### タスク3: ドキュメント更新
- [x] 完了
- **概要:** 画面挙動の変更（結果入力画面の「対戦変更」が組み合わせ作成画面への遷移に統合）を仕様書類に反映（CLAUDE.md ルール）。
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` / `docs/SCREEN_LIST.md` / `docs/DESIGN.md` — 該当箇所に反映
- **依存タスク:** タスク1, タスク2
- **対応Issue:** #864

## 実装順序
1. タスク1（戻り先動的化・独立）
2. タスク2（遷移化＋確認ダイアログ＋編集モード削除）
3. タスク3（ドキュメント更新）
