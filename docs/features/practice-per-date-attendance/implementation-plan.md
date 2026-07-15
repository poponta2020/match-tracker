---
status: completed
---
# 1日分の出欠登録画面 実装手順書

> 要件: [requirements.md](./requirements.md)（AC は §4）／デザイン: [design-spec.md](./design-spec.md)（locked）
> **フロントのみ**（新規BE・DB・マイグレーションなし）。既存 `registerParticipations` / `cancelMultiple` と既存ヘルパー・`SaveProgressOverlay` を流用する。

## 技術設計（確定）

- **新ルート:** `/practice/attendance?sessionId=<id>`（`App.jsx`、`/practice` の直後に追加。静的セグメントのため `/practice/:id` より優先）。ガードは `ProtectedPage`（全ロール）。
- **新コンポーネント:** `pages/practice/PracticeSessionAttendance.jsx`（対象セッション1件の参加＋理由付きキャンセル）。
- **エントリ変更:** `pages/practice/PracticeList.jsx` セッション詳細ポップアップ「出欠登録」ボタン → `navigate('/practice/attendance?sessionId=' + selectedSession.id)`（`AttendanceRegisterModal` 経由をやめる）。FAB ラベルを「出欠一括登録」に変更（機能・`AttendanceRegisterModal` は不変）。
- **データ取得（並列・既存API）:** `getById(sessionId)`（会場・団体・`venueSchedules`・`matchParticipants`・`densukeDeletionCandidateMatchNumbers`・`capacity`・`totalMatches`）／`getPlayerParticipations(playerId, year, month)`（月の参加マップ＝全置換ペイロードの土台）／`getPlayerParticipationStatus(playerId, year, month)`（`participations`[sessionId] の status・participantId・waitlistNumber、`version`、`lotteryExecuted`、`hasAnyExecutedLotteryInMonth`）／`organizationAPI.getAll()`（団体カラー）。満員判定は `getSessionSummaries(year, month)` の対象セッション `matchCapacityStatuses` を流用（カレンダーと一致）、無ければ `capacity` と人数から導出。`year`/`month` は対象セッション日付から導出。
- **参加保存:** 月の参加マップに対象セッションのトグル結果を反映して `participations` 配列を組み、`registerParticipations({playerId, year, month, participations, expectedVersion})`。**対象セッション以外の同月参加を必ず含める**（全置換対策）。当日 SAME_DAY は `needsSameDayConfirm` で確認ダイアログ。409 は既存同様に再読込。
- **キャンセル:** 対象セッションの選択試合（WON/PENDING）の `participantId` を集約 → `cancelMultiple(participantIds, reason, detail)`。理由必須・`OTHER` は詳細必須。当日12時以降は追加確認。
- **完了/エラー:** `SaveProgressOverlay`（保存中/完了/エラー）。完了「カレンダーに戻る」→ `/practice`。エラーは状態維持。

## 実装タスク

### タスク1: 出欠画面の純ロジック抽出＋単体テスト
- [x] 完了
- **対応Issue:** #1069
- **目的:** 回帰の要（全置換ペイロード・セクション排他振り分け）を描画から切り離した純関数にし、状態マトリクスを単体テストで固定する。
- **対応AC:** AC-3, AC-5, AC-6, AC-15, AC-16
- **主な変更領域:** `karuta-tracker-ui/src/pages/practice/utils/`（新規 `attendanceScreen.js` 等）。既存 `attendanceMode.js`・`sameDayConfirm.js` は流用（重複実装しない）。
- **依存タスク:** なし
- **必要なテスト（テストファースト）:**
  - `buildMonthParticipationsPayload(monthParticipations, targetSessionId, targetDesiredMatchNumbers)` = 対象セッションを **targetDesiredMatchNumbers（対象セッションの完全な希望アクティブ集合）で差し替え**、他セッションの参加はそのまま保持した `[{sessionId, matchNumber}]` を返す。
    - ⚠ **契約の肝（advisor 指摘）:** `targetDesiredMatchNumbers` は「参加セクションのトグル結果」ではなく **対象セッションの完全な希望集合＝既存アクティブ ∪ 追加 ∖（来月モードの解除）**。当月扱いでは既存登録は参加トグルに出さない設計なので、トグルだけを渡すと既存登録が落ち、バックエンド `validateAttendanceModeCancellation` が400を投げる（来月モードでは黙って全置換で消える）。→ 呼び出し側は月マップの既存登録を **seed** にして差分を適用した集合を渡す。
    - **他日保持テスト（クロスデイ軸）:** 他セッションの参加が payload に残る。
    - **対象セッション内保持テスト（イントラ軸・破綻ケース）:** 当月扱い・第1登録済み・第2を追加 → payload の対象セッションが `[1,2]`（第1が落ちない）。来月扱い・第1登録済み・第2追加 → `[1,2]`、第1をuncheck → `[2]`。
  - `resolveAttendanceSections({ session, isCurrentMonthMode, lotteryExecutedForSession, monthParticipationsForSession, statusesForSession })` = `{ showRegisterSection, registerMatches, showCancelSection, cancelMatches, readonlyStatusMatches }`。状態マトリクス（一部参加/全参加/全未参加/来月扱い/抽選確定済み、満員はregister対象・伝助削除は除外）を網羅。
- **完了条件:** 上記単体テスト green。

### タスク2: 1日分出欠登録画面コンポーネント＋ルート追加
- [ ] 完了
- **対応Issue:** #1070
- **目的:** 対象セッション1件で参加トグル保存と理由付きキャンセルを完結する画面を実装する。
- **対応AC:** AC-1, AC-2, AC-4, AC-7, AC-8, AC-9, AC-10, AC-11, AC-15, AC-16, AC-17
- **主な変更領域:** `karuta-tracker-ui/src/pages/practice/PracticeSessionAttendance.jsx`（新規）、`karuta-tracker-ui/src/App.jsx`（`/practice/attendance` ルート追加）。API は `api/practices.js`・`api/lottery.js` の既存関数流用（新規追加なし）。`SaveProgressOverlay`・`resolveAttendanceMode`・`needsSameDayConfirm`・タスク1のヘルパーを使用。
- **依存タスク:** タスク1（ヘルパー）
- **必要なテスト（テストファースト）:**
  - 参加保存で `registerParticipations` に**他日の参加を含む月全体ペイロード**＋`expectedVersion` が渡る（AC-3/10）。
  - キャンセルで対象試合の participantId を集約し `cancelMultiple(理由,詳細)` を呼ぶ。理由未選択で実行不可、`OTHER` 詳細必須（AC-4）。
  - セクション排他表示・満員チェック可・伝助削除×・抽選確定済みステータス表示（AC-15/16/6/7）。
  - 当日12時以降の確認ダイアログ（参加・キャンセル両方）（AC-8）、`SaveProgressOverlay` 完了→`/practice`／エラー保持（AC-9）。
  - 上部バー「M/D(曜) 会場名」・団体カラー表示（AC-17）。
- **完了条件:** コンポーネントテスト green、lint 通過。

### タスク3: PracticeList の導線変更（ポップアップボタン→新画面 / FAB改名）
- [ ] 完了
- **対応Issue:** #1071
- **目的:** カレンダー日付ポップアップの「出欠登録」を新画面へ接続し、FAB を「出欠一括登録」に改名する（月まとめ導線は維持）。
- **対応AC:** AC-1, AC-11, AC-12, AC-13
- **主な変更領域:** `karuta-tracker-ui/src/pages/practice/PracticeList.jsx`（ポップアップ内「出欠登録」ボタンの onClick を navigate に変更／FAB ラベル変更）、および影響する既存テスト（`PracticeList.attendanceMode.test.jsx` 等）。`AttendanceRegisterModal`・`Home.jsx` リンクは不変。
- **依存タスク:** タスク2（遷移先ルートが存在すること）
- **必要なテスト:**
  - ポップアップ「出欠登録」押下で `/practice/attendance?sessionId=<id>` へ navigate（AttendanceRegisterModal を開かない）（AC-1）。過去日は当ボタン非表示（AC-11）。
  - FAB ラベルが「出欠一括登録」で、押下すると従来どおり `AttendanceRegisterModal` が開く（AC-12）。月まとめ導線・Home リンク不変（AC-13、既存テスト維持）。
- **完了条件:** 変更後テスト green、既存 PracticeList 系テスト green、lint 通過。

## ドキュメント更新（実装と同じコミット）
- `docs/SCREEN_LIST.md` の練習管理（4節）に新画面 `/practice/attendance` を追記し、#13 の「出欠登録」ボタン挙動（ポップアップ→新画面 / FAB=出欠一括登録）を更新。
- `docs/spec/practice-attendance.md` に本画面の仕様（セクション排他・満員チェック可・当月/来月差分・全置換ペイロード）を追記し、モーダル節の記述を更新。

## 実装順序（Wave）
- Wave 1: タスク1（純ロジック＋テスト。他と変更領域が重ならない）
- Wave 2: タスク2（タスク1に依存）
- Wave 3: タスク3（タスク2のルートに依存。`PracticeList.jsx` 単独領域）
