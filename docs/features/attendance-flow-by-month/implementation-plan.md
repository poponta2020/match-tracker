---
status: completed
---
# attendance-flow-by-month 実装手順書

## 実装タスク

### タスク1: 共通判定ヘルパー `resolveAttendanceMode` の新設
- [x] 完了
- **概要:** 「当月扱い／来月扱い／過去月」を判定する共通ヘルパー関数と単体テストを新規作成する。後続タスクの全画面実装で利用される基盤。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/practice/utils/attendanceMode.js`（新規）— `resolveAttendanceMode(year, month, lotteryExecutedMap, now)` を実装。`{ isCurrentMonth, isPastMonth }` を返す
  - `karuta-tracker-ui/src/pages/practice/utils/attendanceMode.test.js`（新規）— 当月／来月（抽選確定済みあり）／来月（抽選確定済みなし）／過去月の4ケースを網羅
- **依存タスク:** なし
- **対応Issue:** #692

### タスク2: AttendanceRegisterModal の改修
- [x] 完了
- **概要:** モーダルに `isCurrentMonth` props を追加し、来月扱いのときは「キャンセル登録」ボタンを非表示にする。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/components/AttendanceRegisterModal.jsx` — props に `isCurrentMonth: boolean` を追加し、`isCurrentMonth === false` の場合に「キャンセル登録」ボタンを描画しない
  - `karuta-tracker-ui/src/components/AttendanceRegisterModal.test.jsx`（新規または拡張）— `isCurrentMonth` による表示制御を検証
- **依存タスク:** なし
- **対応Issue:** #693

### タスク3: PracticeList に当月扱い判定とFAB制御を実装
- [ ] 完了
- **概要:** カレンダー表示月の `lotteryExecuted` 情報を取得し、`resolveAttendanceMode` で判定。FAB／インラインボタンの過去月非表示、`AttendanceRegisterModal` への `isCurrentMonth` 引き渡しを実装。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/practice/PracticeList.jsx` — 月変更時に `practiceAPI.getPlayerParticipationStatus(currentPlayer.id, year, month)` を呼んで `lotteryExecutedMap` をステート保持。`resolveAttendanceMode` で `isCurrentMonth` / `isPastMonth` を算出。FABとインラインボタンを `!isPastMonth` のときのみ表示。`AttendanceRegisterModal` に `isCurrentMonth` を渡す
- **依存タスク:** タスク1, タスク2
- **対応Issue:** #694

### タスク4: PracticeParticipation のチェック外し挙動を「当月扱い／来月扱い」で切り替え
- [ ] 完了
- **概要:** 既存の `lotteryExecuted` ステートを使って `resolveAttendanceMode` で月単位判定を行い、当月扱い時は既存登録のチェック外しを禁止、来月扱い時は可能とする。抽選確定済みセッションのロックは現状ロジックを維持。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/practice/PracticeParticipation.jsx` — `resolveAttendanceMode` をimport。表示月から `isCurrentMonth` を算出。`isLockedRegistration` のロジックを「当月扱い時は既存登録すべてロック、来月扱い時は現状の締切後ロックのみ」に変更（締切後ロックは来月扱いの月では事実上発生しない）。`toggleMatch` で当月扱い時のチェック外しを抑止
  - `karuta-tracker-ui/src/pages/practice/PracticeParticipation.test.jsx`（新規または拡張）— 当月扱いでチェック外し不可、来月扱いでチェック外し可、抽選確定済みセッションのロックを検証
- **依存タスク:** タスク1
- **対応Issue:** #695

### タスク5: PracticeCancelPage の月ナビ廃止
- [ ] 完了
- **概要:** 月ナビゲーション・YearMonthPicker を削除し、クエリパラメータの年月で固定表示する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/practice/PracticeCancelPage.jsx` — `ChevronLeft` / `ChevronRight` の月ナビボタン、`YearMonthPicker` の表示、`changeMonth` 関数、`showYearMonthPicker` ステート、月変更時リセットの `useEffect` を削除。タイトル下に「○年○月」の固定表示を追加
  - `karuta-tracker-ui/src/pages/practice/PracticeCancelPage.test.jsx`（既存テストを修正）— 月ナビ削除後の挙動、クエリパラメータ月固定の検証。月変更を伴うテストケースは削除または書き換え
- **依存タスク:** なし
- **対応Issue:** #696

### タスク6: ドキュメント更新（SCREEN_LIST.md）
- [ ] 完了
- **概要:** `docs/SCREEN_LIST.md` のPracticeList（項番13）、PracticeParticipation（項番17）、PracticeCancelPage（項番18）の説明を新仕様に合わせて更新する。
- **変更対象ファイル:**
  - `docs/SCREEN_LIST.md` — 項番13・17・18の「説明」列に「カレンダー表示月の抽選確定状態に応じて当月扱い／来月扱いを判定し、参加登録画面のチェック外し挙動とAttendanceRegisterModalのボタン表示を切り替える」旨を追記。項番18には「月ナビ廃止、クエリパラメータ月固定」を追記
- **依存タスク:** タスク2〜5（実装が確定してから記載するため）
- **対応Issue:** #697

### タスク7: 動作確認・回帰確認
- [ ] 完了
- **概要:** 開発サーバを起動し、当月／来月／過去月／混在月の各シナリオを実機（ブラウザ）で確認する。`./gradlew test` と `npm run lint` を通す。
- **変更対象ファイル:**
  - なし（テストと手動確認）
- **手動確認シナリオ:**
  1. 当月カレンダーでFAB→モーダルに両ボタン表示、参加登録画面でチェック外し不可
  2. 来月カレンダー（抽選確定なし）でFAB→モーダルに「参加登録」のみ、参加登録画面でチェック外し可
  3. 来月カレンダー（抽選確定済みが混在）でFAB→モーダルに両ボタン表示、参加登録画面で抽選確定済みセッションのみロック
  4. 過去月カレンダーでFABが非表示
  5. キャンセル画面に直接遷移→月ナビが存在しない、対象月が固定表示
  6. 抽選なし団体（わすらもち会）の当月セッションがチェック外し不可、来月セッションがチェック外し可
- **依存タスク:** タスク1〜6
- **対応Issue:** #698

## 実装順序

1. **タスク1**（共通判定ヘルパー）— 依存なし、基盤
2. **タスク2**（AttendanceRegisterModal）— 依存なし
3. **タスク3**（PracticeList）— タスク1, 2に依存
4. **タスク4**（PracticeParticipation）— タスク1に依存。タスク3と並行可能
5. **タスク5**（PracticeCancelPage）— 依存なし。タスク3, 4と並行可能
6. **タスク6**（ドキュメント更新）— タスク2〜5の実装確定後
7. **タスク7**（動作確認・回帰確認）— タスク1〜6完了後

## 補足

- 既存のテスト構成（Vitest 想定）に合わせ、テストファイル名は `<対象>.test.jsx` または `<対象>.test.js` とする。テスト基盤が未整備のコンポーネントについてはタスク内で最小限のテストを追加する。
- バックエンド変更・DBマイグレーションは発生しないため、本番DB適用作業は不要。
- 既存の `practiceAPI.getPlayerParticipationStatus` を PracticeList から呼ぶことで、月変更時のリクエスト数が1件増える。パフォーマンス影響は軽微と判断するが、必要なら futureSessions が0件の月でリクエストをスキップする最適化を検討。
