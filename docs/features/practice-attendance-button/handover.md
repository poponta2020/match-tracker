---
status: ready-for-review
last_updated: 2026-04-30
---

# 練習出欠登録ボタン統合 — 作業引き継ぎ書

## 現状サマリー

実装は **全タスク完了**、PR 作成済み、レビュー待ち。

- **Feature ブランチ:** `feature/practice-attendance-button`
- **Worktree パス:** `C:/tmp/impl-practice-attendance-button/`
- **PR:** https://github.com/poponta2020/match-tracker/pull/615
- **親 Issue:** https://github.com/poponta2020/match-tracker/issues/609（5 タスクすべてチェック済み）
- **子 Issue:** #610 / #611 / #612 / #613 / #614（PR マージ時にコミットメッセージの `Fixes #xxx` で自動クローズ予定）

## ブランチ状態

`origin/main` から 5 コミット先行、すべて push 済み（worktree クリーン）:

| コミット | 内容 | 対応タスク |
|---|---|---|
| 211002c | `feat(practice): add AttendanceRegisterModal component` | Task 1 (#610) |
| 33f9c3b | `feat(practice): respect year/month query params in PracticeParticipation` | Task 3 (#612) |
| f2dc894 | `feat(practice): respect year/month query params in PracticeCancelPage` | Task 4 (#613) |
| b9fb1c6 | `feat(practice): consolidate attendance buttons into single 出欠登録 button` | Task 2 (#611) |
| 4a70b47 | `docs(practice): reflect attendance button consolidation and modal flow` | Task 5 (#614) |

## 完了した作業

### Task 1: AttendanceRegisterModal 新規作成（#610 完）
- `karuta-tracker-ui/src/components/AttendanceRegisterModal.jsx` を新規作成
- props: `isOpen`, `onClose`, `year`, `month`
- 「参加登録」「キャンセル登録」「閉じる」の 3 ボタン構成
- 「参加登録」→ `/practice/participation?year=YYYY&month=M`、「キャンセル登録」→ `/practice/cancel?year=YYYY&month=M` 遷移後に `onClose()`
- 既存 `MatchParticipantsEditModal.jsx` のスタイルを参考

### Task 2: PracticeList.jsx ボタン統合（#611 完）
- `XCircle` import と `goToParticipation` 関数を削除
- `isAttendanceModalOpen` state と `openAttendanceModal` ハンドラを追加
- カレンダー画面 3 箇所のボタンを「出欠登録」1 種類に統合:
  - 選択セッション詳細部のインラインボタン（過去日でない場合のみ表示）
  - 右下フローティングボタン（常時表示）
  - 左下フローティング「参加キャンセル」ボタンと `hasCancellable` 判定 IIFE は完全削除
- JSX 末尾に `<AttendanceRegisterModal>` をマウント
- `myParticipationStatuses` はカレンダーセル色付けで引き続き使用（残存）

### Task 3: PracticeParticipation.jsx クエリパラメータ対応（#612 完）
- `useSearchParams` を import
- `currentDate` 初期値を `getInitialDateFromQuery(searchParams)` に変更
- 共通ヘルパー `karuta-tracker-ui/src/pages/practice/utils/dateFromQuery.js` を新規作成（`year`/`month` の整数・範囲チェック、不正値時は `new Date()` フォールバック）
- 既存バグ修正（クエリパラメータ無視を解消）

### Task 4: PracticeCancelPage.jsx クエリパラメータ対応（#613 完）
- Task 3 と同方針、`getInitialDateFromQuery` を再利用

### Task 5: ドキュメント更新（#614 完）
- `docs/SPECIFICATION.md`: 新セクション 3.2.3.1（出欠登録モーダル仕様）を追加、3.2.3 と 3.2.4 にクエリパラメータ対応を追記、ルーティング表 1342-1343 行も更新
- `docs/SCREEN_LIST.md`: #13 主要子コンポーネントに `AttendanceRegisterModal` を追加、#17/#18 にクエリパラメータ対応を追記
- `docs/DESIGN.md`: 5.2 練習関連の導線フロー、5.3.1 カレンダー画面、5.3.2 練習参加登録、7.1 練習参加登録フローをモーダル経由に書き換え
- `AdjacentRoomFlow.test.jsx` は「参加登録」「出欠登録」のラベルアサーションを持たず、テスト修正は不要だった

## 検証済み事項

- ESLint: 既存 57 problems（44 errors, 13 warnings）から増減なし（本変更による新規エラー 0）
- Vite production build: 成功（1834 modules、チャンクサイズ警告は既存）
- `AdjacentRoomFlow.test.jsx`: 該当ラベルのアサーションなし、無修正で OK

## 次に取り掛かる作業

### 1. レビューを実施する
レビュープロンプトは生成済み:
- **プロンプト:** `scripts/review/output/review-prompt-pr615-1.md`
- **結果書き込み先:** `scripts/review/output/review-result-pr615-1.md`（空ファイル作成済み）

このプロンプトをレビュー担当 AI（Codex or Claude Code）に貼り付けてレビューを依頼する。

### 2. レビュー結果に応じて対応する
- **指摘ありの場合:** `/fix` で同じ worktree 上で修正に着手（worktree は保持されている）
- **APPROVE の場合:** `/ship` で PR をマージ → worktree 自動クリーンアップ

### 3. 動作確認チェックリスト（マージ前推奨）
`docs/features/practice-attendance-button/fix-implementation-plan.md` の末尾参照:

- [ ] カレンダー画面の右下に「出欠登録」フローティングボタンが 1 つだけ存在する
- [ ] 選択セッション詳細部のボタンが「出欠登録」（過去日でない場合のみ表示）
- [ ] モーダルが中央表示、サブテキストにカレンダー表示中の年月
- [ ] モーダル「参加登録」→ `/practice/participation?year=YYYY&month=M` 遷移、初期月一致
- [ ] モーダル「キャンセル登録」→ `/practice/cancel?year=YYYY&month=M` 遷移、初期月一致
- [ ] モーダル「閉じる」で遷移せず閉じる
- [ ] `/practice/participation` 直接アクセス（クエリなし）で現在月（後方互換）
- [ ] `/practice/cancel` 直接アクセス（クエリなし）で現在月（後方互換）

## 関連バグ報告（参考）

セッション中に発見された別件のバグ報告（本 PR の対象外）:
- 「キャンセル済み参加者がカレンダーに表示されない」バグを別途修正済み（`isPastDeadline` チェックの修正）。本 PR とは別ブランチ。

## 重要パス

- **要件定義書:** `docs/features/practice-attendance-button/fix-requirements.md`
- **実装手順書:** `docs/features/practice-attendance-button/fix-implementation-plan.md`
- **本引き継ぎ書:** `docs/features/practice-attendance-button/handover.md`
- **モーダル本体:** `karuta-tracker-ui/src/components/AttendanceRegisterModal.jsx`
- **クエリ初期化ヘルパー:** `karuta-tracker-ui/src/pages/practice/utils/dateFromQuery.js`
- **カレンダー画面:** `karuta-tracker-ui/src/pages/practice/PracticeList.jsx`
- **参加登録画面:** `karuta-tracker-ui/src/pages/practice/PracticeParticipation.jsx`
- **キャンセル画面:** `karuta-tracker-ui/src/pages/practice/PracticeCancelPage.jsx`

## メイン作業ディレクトリの注意点

`c:/Users/user/match-tracker` は `main` ブランチに保持。本機能とは無関係な以下の未追跡変更があるが、別案件由来なので触らないこと:
- `M  .claude/settings.json`
- `?? docs/features/lottery-result-copyable-text/`
- `?? docs/features/practice-attendance-button/`（worktree とは独立した古いコピーの可能性あり）

`/implement`, `/fix`, `/ship` 等のスキル経由で操作する限り、worktree 側を自動的に対象にするので問題なし。
