---
status: completed
---

# 練習出欠登録ボタン統合 改修実装手順書

## 実装タスク

### タスク1: AttendanceRegisterModal コンポーネントの新規作成
- [x] 完了
- **概要:** 「出欠登録」モーダルを新規作成。表示中の年月をサブテキストに表示し、「参加登録」「キャンセル登録」「閉じる」のボタンを持つ。各ボタン押下時に対応するページへ `?year=YYYY&month=M` 付きで遷移する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/components/AttendanceRegisterModal.jsx`（新規）
    - props: `isOpen`, `onClose`, `year`, `month`
    - 「参加登録」ボタン: `navigate(\`/practice/participation?year=${year}&month=${month}\`)` 後 `onClose()`
    - 「キャンセル登録」ボタン: `navigate(\`/practice/cancel?year=${year}&month=${month}\`)` 後 `onClose()`
    - 既存 `MatchParticipantsEditModal.jsx` のスタイル/構造を参考にする
- **依存タスク:** なし
- **対応Issue:** #610

### タスク2: PracticeList.jsx のボタン統合
- [x] 完了
- **実装メモ:** `XCircle` import と `goToParticipation` 関数を削除し、`openAttendanceModal` に置き換え。`isAttendanceModalOpen` state 追加、`hasCancellable` IIFE と左下フローティングボタンを削除。インライン/フローティング双方のラベルを「出欠登録」に統一し、JSX 末尾に `<AttendanceRegisterModal>` をマウント。
- **概要:** カレンダー画面の 3 つのボタン（右下フローティング「参加登録」/ 左下フローティング「参加キャンセル」/ 選択セッション詳細インライン「参加登録」）を「出欠登録」ボタン 1 種類に統合し、押下時に AttendanceRegisterModal を開く。左下フローティング「参加キャンセル」ボタンと `hasCancellable` 判定ブロックは削除する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/practice/PracticeList.jsx`
    - state 追加: `const [isAttendanceModalOpen, setIsAttendanceModalOpen] = useState(false)`
    - 関数追加 or 置換: `goToParticipation` を `openAttendanceModal = () => setIsAttendanceModalOpen(true)` に置き換え、または併存させて段階的に書き換え（最終的に `goToParticipation` は削除）
    - 行 951–956: 選択セッション詳細インライン「参加登録」ボタン → ラベル「出欠登録」、onClick を `openAttendanceModal` に変更
    - 行 1000–1011: 左下フローティング「参加キャンセル」ボタンと `hasCancellable` 判定ブロックを削除
    - 行 1012–1020: 右下フローティング「参加登録」ボタン → ラベル「出欠登録」、onClick を `openAttendanceModal` に変更
    - JSX 末尾に `<AttendanceRegisterModal isOpen={isAttendanceModalOpen} onClose={() => setIsAttendanceModalOpen(false)} year={currentDate.getFullYear()} month={currentDate.getMonth() + 1} />` をマウント
    - import に `AttendanceRegisterModal` を追加
- **依存タスク:** タスク1
- **対応Issue:** #611

### タスク3: PracticeParticipation.jsx のクエリパラメータ対応（既存バグ修正）
- [x] 完了
- **実装メモ:** 共通ヘルパー `getInitialDateFromQuery` を `karuta-tracker-ui/src/pages/practice/utils/dateFromQuery.js` に新規作成し、`useSearchParams` を渡して初期化に使用する形に変更。タスク4でも同ヘルパーを再利用予定。
- **概要:** 参加登録ページが URL クエリパラメータ `year` / `month` を読み取り、初期表示月に反映するよう修正。不正値時は現在月にフォールバック。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/practice/PracticeParticipation.jsx`
    - import に `useSearchParams`（react-router-dom）を追加
    - 12 行目付近の `const [currentDate, setCurrentDate] = useState(new Date())` を、クエリパラメータ由来の初期値を渡すよう変更
    - ヘルパー（同ファイル内）: `getInitialDate(yearParam, monthParam)` を追加し、整数かつ `1 <= month <= 12` のとき `new Date(year, month - 1, 1)` を、そうでなければ `new Date()` を返す
- **依存タスク:** なし
- **対応Issue:** #612

### タスク4: PracticeCancelPage.jsx のクエリパラメータ対応
- [x] 完了
- **実装メモ:** タスク3で作成した `getInitialDateFromQuery` ヘルパーを再利用。`useSearchParams` を導入し、`currentDate` 初期値をクエリパラメータ由来に変更。
- **概要:** キャンセル登録ページが URL クエリパラメータ `year` / `month` を読み取り、初期表示月に反映するよう修正。タスク3と同じ方針。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/practice/PracticeCancelPage.jsx`
    - import に `useSearchParams`（react-router-dom）を追加
    - `currentDate` 初期値をクエリパラメータ由来に変更
    - ヘルパー `getInitialDate` を追加（タスク3と同実装）
- **依存タスク:** なし
- **対応Issue:** #613

### タスク5: 既存テスト・ドキュメント更新
- [x] 完了
- **実装メモ:** AdjacentRoomFlow.test.jsx は「参加登録」「参加キャンセル」「出欠登録」のラベルアサーションを一切持たず、テスト修正は不要だった。SPECIFICATION.md には新セクション 3.2.3.1（出欠登録モーダル仕様）を追加し、3.2.3 と 3.2.4 にクエリパラメータ対応を追記、ルーティング表 1329-1330 行も更新。SCREEN_LIST.md は #13 の主要子コンポーネントに `AttendanceRegisterModal` を追加し、出欠登録モーダル経由のフローを説明、#17/#18 にクエリパラメータ対応を追記。DESIGN.md は 5.2 練習関連の導線フロー、5.3.1 カレンダー画面、5.3.2 練習参加登録、7.1 練習参加登録フローをモーダル経由に書き換え。
- **概要:** ボタンラベル変更に伴うテスト修正と、`docs/SPECIFICATION.md` / `docs/SCREEN_LIST.md` / `docs/DESIGN.md` への反映。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/practice/AdjacentRoomFlow.test.jsx` — 「参加登録」ボタンを直接アサートしている箇所があれば、新ラベル「出欠登録」とモーダル経由のフローに合わせて修正
  - `docs/SPECIFICATION.md` — 出欠登録ボタンとモーダルの仕様を追記、参加キャンセル UI 変更を反映
  - `docs/SCREEN_LIST.md` — カレンダー画面の UI 変更（ボタン統合・モーダル追加）を反映
  - `docs/DESIGN.md` — モーダル設計・クエリパラメータ仕様を反映
- **依存タスク:** タスク1, 2, 3, 4
- **対応Issue:** #614

## 実装順序
1. **タスク1**（依存なし） — モーダルを単独で作成・確認
2. **タスク3**（依存なし）と **タスク4**（依存なし） — 並行で着手可。クエリパラメータ受け側を先に整える
3. **タスク2**（タスク1 に依存） — モーダルを組み込み、ボタン統合
4. **タスク5**（タスク1〜4 に依存） — 動作確認後にテスト修正・ドキュメント反映

## 動作確認チェックリスト（実装完了時）
- [ ] カレンダー画面の右下に「出欠登録」フローティングボタンが 1 つだけ存在する（左下のキャンセル専用ボタンが消えている）
- [ ] 選択セッション詳細部のボタンが「出欠登録」になっている（過去日でない場合のみ表示）
- [ ] 「出欠登録」ボタン押下でモーダルが中央に表示される
- [ ] モーダルのサブテキストにカレンダー表示中の年月が表示されている
- [ ] モーダルから「参加登録」を選ぶと `/practice/participation?year=YYYY&month=M` に遷移し、初期月がカレンダー表示月に一致する
- [ ] モーダルから「キャンセル登録」を選ぶと `/practice/cancel?year=YYYY&month=M` に遷移し、初期月がカレンダー表示月に一致する
- [ ] モーダルの閉じるボタンで遷移せず閉じられる
- [ ] `/practice/participation`（クエリなし）で直接アクセスすると現在月が表示される（後方互換）
- [ ] `/practice/cancel`（クエリなし）で直接アクセスすると現在月が表示される（後方互換）
- [ ] `npm run lint` が通る
- [ ] `npm run build` が通る
