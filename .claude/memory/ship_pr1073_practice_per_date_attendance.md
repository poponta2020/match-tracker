---
name: ship-pr1073-practice-per-date-attendance
description: カレンダー日付ポップアップの出欠登録を1日分の参加＋理由付きキャンセル画面に変更（親#1068/子#1069-1071）を出荷。auto-review 4R、C1はdocument override
type: ship
---

# PR #1073: feat(practice) 1日分の出欠登録画面（参加＋理由付きキャンセル）

- PR: https://github.com/poponta2020/match-tracker/pull/1073
- Requirements: docs/features/practice-per-date-attendance/requirements.md
- 親Issue #1068 / 子 #1069(純ロジック) #1070(画面) #1071(導線)
- フロントのみ（新規BE・DB・マイグレーションなし。既存 registerParticipations / cancelMultiple 流用）

## 変更概要
- `/practice` セッション詳細ポップアップ「出欠登録」→ 新画面 `/practice/attendance?sessionId=<id>`（AttendanceRegisterModal 非経由）。FAB は「出欠一括登録」に改名（月まとめ導線は不変）。
- 純ロジック `utils/attendanceScreen.js`（`buildMonthParticipationsPayload`=全置換ペイロード／`resolveAttendanceSections`=参加/キャンセル/読み取り専用の排他3分割）＋ `PracticeSessionAttendance.jsx`。
- コミット: c31897d2(T1) 1a2c7a63(T2) fd4f0160(T3) fd6a5ce7(AC-10test) ＋ auto-review 修正 5dd853cc 4c6473b1 ce57d984。

## 核心リスクと防御（consolidation＝既存2画面挙動のunion。回帰防止が主眼）
- **seed失敗ブロック**: 全置換ペイロードの土台 `getPlayerParticipations` は `.catch` せず、失敗時は保存ボタンに到達させない（空seed保存で他日データ消失を防ぐ）。
- **payload = PracticeParticipation.handleSave と byte一致**: 月マップをseedに対象セッションのみ差し替え、他日を保持。
- 満員(`matchParticipantCounts>=capacity`)でもチェック可・伝助削除×・抽選確定済みステータス表示・当月/来月区別維持。

## auto-review-loop（Codex effort=high、差分~2100行、4ラウンド、累計~460k/500k）
- **R1（実バグ・高価値）**: 締切後(`beforeDeadline=false`)の来月扱いで既存参加を理由なしトグル解除→ backend `registerAfterDeadline` は payload省略分を削除しない(追加のみ)ためサイレントno-op。`beforeDeadline` を `resolveAttendanceSections` に通し締切後は理由付きキャンセルへ回す(`PracticeParticipation.isLockedRegistration` と同判定)。
- **R2（休眠TZ）**: `new Date('YYYY-MM-DD')` UTC解釈で負オフセットTZの月初セッションが前月にずれ全置換seedが別月に。`parseIsoDateLocal`(ローカルコンストラクタ)で導出。
- **R3**: R3-2=当日12時判定を `toISOString()`(UTC)→ローカル日付比較へ統一。**R3-1=false positive不採用**（締切後全置換ペイロードは handleSave と byte一致・backend が existsActive(PENDING) を skip＝一次情報確認済）。
- **R4=収束点（両方 parity で不採用）**: blocker=seed/version並列取得の理論的サブ秒レース（PracticeParticipation も同`Promise.all`、409が load→save全域を保護、修正はseed失敗ガード経路を触る悪手）／should_fix=totalMatches>7の7cap（PracticeParticipation も7cap、片側だけ直すと同一データで挙動乖離）。**advisor判定=Ship, fix neither**。high Codex は大差分に理論/休眠/偽陽性を無限産出、価値ピークはR1。停止ルール適用。

## DoD C1 の document override
- gate-dod C1 は最新 Codex 結果(r4)の `verdict != pass` で機械的 FAIL。R4指摘は上記のとおり検証済み false-positive/parity で、コード修正すると PracticeParticipation と挙動乖離するため不採用が正。ユーザー承認(「documentして出荷」)のもと C1 を override して出荷。根拠は本記録＋各commit body＋[[auto_review_round_pr1073]]（personal memory 側）。
- 詳細な実装memoは harness auto-memory 側 `impl_practice_per_date_attendance` にも記録。

## テスト・出荷
- フロント全体 718 test green・lint 0 error。B1 CI green・D2 docs(SCREEN_LIST #18-2 / practice-attendance.md)更新済。
