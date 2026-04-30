---
status: completed
audit_source: ユーザー直接要望（監査レポートなし）
selected_items: []
---

# 練習出欠登録ボタン統合 改修要件定義書

## 1. 改修概要

### 対象機能
練習予定ページ（`/practice`）のカレンダー画面における出欠登録 UI

### 改修の背景
- カレンダー画面に「参加登録」「参加キャンセル」の 2 ボタンが並立しており、ユーザーから見たアクションの粒度がそろっていない（出欠を変える操作）にもかかわらず別ボタンに分かれている。
- 「出欠登録」という単一のエントリーポイントに集約し、ポップアップで「参加登録 / キャンセル登録」を選ばせる方が直感的。
- さらに、現状の「参加キャンセル」ボタンはカレンダー表示中の月を遷移先に渡していないため、5 月のカレンダーから参加キャンセルを開いても現在月（例: 4 月）が表示されてしまう。
- 加えて、参加登録ページ（`PracticeParticipation.jsx`）はクエリパラメータ `?year&month` を URL 上では受け取るものの、コンポーネント内では `useState(new Date())` で初期化しており、クエリパラメータを実質無視している（**既存バグ**）。

### 改修スコープ
1. カレンダー画面のフローティング 2 ボタン（右下「参加登録」/ 左下「参加キャンセル」）と、選択セッション詳細部のインライン「参加登録」ボタンを「出欠登録」ボタン 1 つに統合。
2. 出欠登録ボタン押下で「参加登録 / キャンセル登録」を選ぶモーダルを表示。
3. モーダルからの遷移時、カレンダーで表示中の `year` / `month` をクエリパラメータで参加登録ページとキャンセル登録ページの両方に渡す。
4. 参加登録ページ・キャンセル登録ページ側でクエリパラメータを読み取り、初期表示月をそれに合わせる。

---

## 2. 改修内容

### 2.1 出欠登録ボタンへの統合（`PracticeList.jsx`）

#### 現状
- 行 1012–1020: 右下フローティング「参加登録」（常時表示）
- 行 1000–1009: 左下フローティング「参加キャンセル」（`hasCancellable` が true のときのみ表示）
- 行 951–956: 選択セッション詳細部のインライン「参加登録」（過去日でない場合のみ表示）

#### 修正方針
- 上記 3 箇所のボタンを、いずれも「出欠登録」ボタンに置き換える。
- フローティングボタンは右下 1 つに統一（左下のキャンセル専用フローティングは廃止）。
- 「出欠登録」ボタン押下で出欠登録モーダル（後述）を表示する。

#### 修正後のあるべき姿
- カレンダー画面のフローティング出欠登録ボタンは常時 1 つ（右下）。
- 選択セッション詳細部の出欠登録ボタンは、過去日でない場合のみ表示（既存の表示条件は踏襲）。
- 「出欠登録」ボタンを押すとモーダルが開き、ユーザーが「参加登録」「キャンセル登録」のいずれかを選択できる。

### 2.2 出欠登録モーダルの新規作成

#### 修正方針
- 新規コンポーネント `AttendanceRegisterModal.jsx` を `karuta-tracker-ui/src/components/` 配下に作成。
- props:
  - `isOpen: boolean`
  - `onClose: () => void`
  - `year: number`
  - `month: number`
- 中身:
  - タイトル: 「出欠登録」
  - サブテキスト: 「YYYY年M月の出欠登録を行います。」（カレンダー表示月を明示）
  - ボタン 2 つ: 「参加登録」「キャンセル登録」
  - 閉じるボタン（× もしくはキャンセル）
- ボタン押下時の挙動:
  - 「参加登録」: `navigate(/practice/participation?year=YYYY&month=M)` してモーダルを閉じる
  - 「キャンセル登録」: `navigate(/practice/cancel?year=YYYY&month=M)` してモーダルを閉じる
- 既存の `MatchParticipantsEditModal.jsx` のスタイル/構造を参考に統一感を出す。

#### 修正後のあるべき姿
- カレンダー画面で「出欠登録」ボタンを押すとオーバーレイ付きモーダルが中央表示され、ユーザーが「参加登録」「キャンセル登録」を選択できる。
- 選択したアクションのページに、カレンダーで表示中の年月がクエリパラメータとして引き継がれる。

### 2.3 キャンセル登録ページの月引き継ぎ対応（`PracticeCancelPage.jsx`）

#### 現状
- 567 行のファイル。`currentDate` を `useState(new Date())` で初期化しており、URL クエリパラメータを参照していない。
- 既存遷移は `navigate('/practice/cancel')`（クエリなし）。

#### 修正方針
- `useSearchParams` を import して、`year` / `month` クエリパラメータを読み取る。
- `currentDate` の初期値を、クエリパラメータが指定されていればそれを使用、なければ `new Date()` を使用する形に変更。
- パラメータが不正値（範囲外、数値でない等）の場合は安全に `new Date()` にフォールバック。

#### 修正後のあるべき姿
- `/practice/cancel?year=2026&month=5` にアクセスすると、初期表示が 2026 年 5 月になる。
- パラメータなしでアクセスした場合は従来どおり現在月を表示。

### 2.4 参加登録ページの月引き継ぎ対応（`PracticeParticipation.jsx`）【既存バグ修正】

#### 現状
- 494 行のファイル。`currentDate` を `useState(new Date())` で初期化しており、URL クエリパラメータを参照していない。
- 呼び出し元（`PracticeList.jsx` の `goToParticipation`）は `?year=YYYY&month=M` を渡しているが、ページ側で読まれていない（**既存バグ**）。

#### 修正方針
- 2.3 と同じ方針で `useSearchParams` を導入。
- `currentDate` の初期値にクエリパラメータを反映。
- 不正値時のフォールバックも 2.3 と同様。

#### 修正後のあるべき姿
- カレンダーから「出欠登録」→「参加登録」と遷移した際、参加登録ページの初期月がカレンダーの表示月と一致する。

---

## 3. 技術設計

### 3.1 API変更
なし（フロントエンドのみの改修）。

### 3.2 DB変更
なし。

### 3.3 フロントエンド変更

#### 新規ファイル
- `karuta-tracker-ui/src/components/AttendanceRegisterModal.jsx`
  - 上記 2.2 のモーダルコンポーネント

#### 変更ファイル

**`karuta-tracker-ui/src/pages/practice/PracticeList.jsx`**
- 出欠登録モーダル開閉用の state を追加: `const [isAttendanceModalOpen, setIsAttendanceModalOpen] = useState(false)`
- `goToParticipation` 関数を削除、または出欠登録モーダルを開く関数 `openAttendanceModal` に置き換え。
- 行 1012–1020 の右下フローティングボタンのテキストを「参加登録」→「出欠登録」、`onClick` を `openAttendanceModal` に変更。アイコンは `CalendarCheck` のまま、または出欠を表す適切なアイコンに置換可（要確認）。
- 行 1000–1009 の左下フローティング「参加キャンセル」ボタンを削除。`hasCancellable` 判定で条件分岐していた部分も併せて削除。
- 行 951–956 の選択セッション詳細部インライン「参加登録」ボタンを「出欠登録」に変更し、`onClick` を `openAttendanceModal` に。
- JSX の末尾に `<AttendanceRegisterModal>` をマウント。`year` / `month` には `currentDate` から計算した値を渡す。

**`karuta-tracker-ui/src/pages/practice/PracticeParticipation.jsx`**
- 1 行目に `useSearchParams` を `react-router-dom` から import。
- 12 行目付近の `useState(new Date())` を、クエリパラメータ由来の初期値を渡すように変更。
- 初期値計算ロジック（共通化候補）:
  ```js
  const getInitialDate = (yearParam, monthParam) => {
    const y = Number(yearParam);
    const m = Number(monthParam);
    if (Number.isInteger(y) && Number.isInteger(m) && m >= 1 && m <= 12) {
      return new Date(y, m - 1, 1);
    }
    return new Date();
  };
  ```

**`karuta-tracker-ui/src/pages/practice/PracticeCancelPage.jsx`**
- 同上の方針で `useSearchParams` を導入し、`currentDate` 初期値をクエリパラメータ由来に変更。

#### 共通化方針
`getInitialDate` 相当の小ヘルパーは、両ページで同等の処理になるため `karuta-tracker-ui/src/pages/practice/utils/dateFromQuery.js`（仮）として切り出し、両ページから import するのを推奨。ただし規模が小さいので各ファイルに置く判断もあり（実装時に確定）。

### 3.4 バックエンド変更
なし。

---

## 4. 影響範囲

### 影響を受ける既存機能
- **練習予定カレンダー画面（`/practice`）**: フローティングボタンの数とラベル、選択セッション詳細部のボタンラベルが変わる。直接的な操作フローが変わるためユーザー側に影響がある（操作回数が 1 クリック増える）。
- **参加登録ページ（`/practice/participation`）**: クエリパラメータ尊重に変更。今までクエリパラメータを無視して常に現在月を表示していたため、カレンダーから遷移したユーザーの体験が変わる（バグ修正）。クエリなしでアクセスした場合の挙動は変わらない。
- **キャンセル登録ページ（`/practice/cancel`）**: クエリパラメータ受け入れを追加。直接 URL アクセスや既存の他導線では従来通り現在月表示。

### 共通コンポーネント・ユーティリティへの影響
- `MatchParticipantsEditModal.jsx`（既存モーダル）はそのまま残す。新規 `AttendanceRegisterModal.jsx` は独立したコンポーネントで干渉なし。

### 破壊的変更の有無
- API、DB スキーマの変更なし。
- URL ルーティング自体は変更なし（既存の `/practice/participation` `/practice/cancel` を継続利用）。
- クエリパラメータの**追加**のみで、既存の URL（クエリなし）も引き続き動作する後方互換あり。

### 既存テストへの影響
- `karuta-tracker-ui/src/pages/practice/AdjacentRoomFlow.test.jsx` が PracticeList 関連テストの可能性あり。テスト対象に「参加登録」ボタンを直接アサートしていれば、ラベル変更で失敗する。実装時に確認・修正が必要。

---

## 5. 設計判断の根拠

- **モーダル新規 vs 既存モーダル流用**: 既存の `MatchParticipantsEditModal.jsx` は対戦参加者編集用で目的が異なるため、別コンポーネントとして新設する。スタイリングのみ参考にする。
- **キャンセル専用フローティングボタンの廃止**: 出欠登録ボタンに統合したため、左下のキャンセル専用ボタンは冗長になり廃止する。`hasCancellable` 判定は出欠登録モーダル内のキャンセル登録ボタンの活性/非活性などには使わず、ボタン押下後の遷移先（キャンセル登録ページ）側で対象セッションの有無を扱う既存ロジックに任せる。
- **クエリパラメータのフォールバック**: 不正値や未指定時は `new Date()`（現在日時）にフォールバックすることで、既存の直接アクセスやブックマーク URL の挙動を壊さない。
- **既存バグの修正範囲**: 参加登録ページのクエリパラメータ無視は今回の改修で初めて顕在化する（モーダルから遷移する全ケースで影響が出る）ため、本改修と同 PR で修正するのが自然。
