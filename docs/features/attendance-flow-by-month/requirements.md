---
status: completed
---
# attendance-flow-by-month 要件定義書

## 1. 概要

### 目的
出欠登録／キャンセルの動線を「対象月のセッションの抽選状態」に応じて切り替え、抽選確定済みの予定は理由付きキャンセル（キャンセル画面）に集約しつつ、抽選確定前の月の予定はチェック切替だけで参加・取消を完結できるようにする。

### 背景・動機
- 当月の予定は抽選確定済みのケースが多く、急なキャンセルは履歴として理由を残したい。
- 来月以降の予定は抽選前の調整段階であり、軽い気持ちでチェックON/OFFを切替えたい。
- 現状は「カレンダー上の月」と「画面の振る舞い」が一致しておらず、ユーザーは「参加登録画面でチェックを外した取消」と「キャンセル画面で理由付きキャンセル」を場面で使い分けている。混乱の元になっている。

## 2. ユーザーストーリー

### 対象ユーザー
- 一般選手（ALL ロール）
- 管理者（ADMIN/SUPER_ADMIN）も同じ動線を使う

### ユーザーの目的
- 当月の予定をキャンセルしたい → 理由付きキャンセルを残したい
- 来月以降の予定の出欠を仮で組み替えたい → ワンタップで自由に変更したい

### 利用シナリオ
1. **当月の予定をキャンセル**
   - 「出欠登録」ボタン → モーダルの「キャンセル登録」 → キャンセル画面（当月固定） → 試合選択 → 理由選択 → キャンセル実行
2. **来月の予定の参加申込**
   - 「出欠登録」ボタン → モーダルの「参加登録」 → 参加登録画面（来月扱い） → 試合チェック → 保存
3. **来月の予定をキャンセル**
   - 「出欠登録」ボタン → モーダルの「参加登録」 → 参加登録画面（来月扱い） → 既存チェックを外す → 保存（理由なしで取消扱い）
4. **来月以降だが抽選が確定したセッション**
   - 同上の動線で参加登録画面に入っても、抽選確定済みセッションだけはチェックロックされる
   - キャンセルしたい場合は、カレンダー画面に戻ってモーダルから「キャンセル登録」を選び、キャンセル画面でその月の確定済み試合をキャンセル

## 3. 機能要件

### 3.1 画面仕様

#### 3.1.1 「当月扱い／来月扱い」の判定

カレンダー（PracticeList）で表示中の月、または各画面のクエリパラメータ年月に対して以下のロジックで判定する。

| 表示月の状態 | 月内セッションの抽選状態 | 判定 |
|---|---|---|
| 表示月 == 現在年月 | 問わず | **当月扱い** |
| 表示月 > 現在年月 | 抽選確定済みセッションが1つ以上ある | **当月扱い**（例外適用） |
| 表示月 > 現在年月 | 抽選確定済みセッションが0個 | **来月扱い** |
| 表示月 < 現在年月 | 問わず | **過去月** |

判定ロジックの詳細：
- 「抽選確定済みセッション」= `PlayerParticipationStatusDto.lotteryExecuted` がそのセッションIDについて `true`
- 抽選なし運用団体（わすらもち会など SAME_DAY 運用や締切なし運用）のセッションは抽選を行わないため `lotteryExecuted=false` のままになる
- 当月扱い／来月扱いの判定は「月単位」、個別セッションのロック判定は「セッション単位」（次節）

#### 3.1.2 PracticeList（カレンダー画面）

- **FAB（フローティングアクションボタン）**
  - ラベル：常に「出欠登録」（変更しない）
  - 表示条件：表示月が **過去月でないとき** のみ表示。過去月のときは非表示
  - 押下時：`AttendanceRegisterModal` を開く。引数として `year`, `month` に加えて `isCurrentMonth`（当月扱い／来月扱い）を渡す
- **インラインボタン（セッション詳細部）**
  - ラベル：常に「出欠登録」（変更しない）
  - 表示条件：選択セッションの日付が過去日でないとき（現状維持）
  - 押下時：`AttendanceRegisterModal` を開く（FABと同じ）
- **カレンダー表示月の参加状況取得**
  - 既存の `practiceAPI.getPlayerParticipationStatus(playerId, year, month)` を月変更時に呼び、`lotteryExecuted` の値を保持
  - 「当月扱い」判定はこの値を使い、月単位フラグとして算出する

#### 3.1.3 AttendanceRegisterModal

- props に `isCurrentMonth: boolean` を追加
- 表示ボタン：
  - `isCurrentMonth === true`：「参加登録」「キャンセル登録」両方表示（現状維持）
  - `isCurrentMonth === false`：「参加登録」のみ表示、「キャンセル登録」ボタンは非表示
- モーダルのタイトル・サブテキスト・「参加登録」ボタンラベルは変更しない
- 「参加登録」押下時：`/practice/participation?year=Y&month=M` へ遷移
- 「キャンセル登録」押下時：`/practice/cancel?year=Y&month=M` へ遷移

#### 3.1.4 PracticeParticipation（参加登録画面）

タイトル「○年○月 参加登録」は変更しない。動作のみ「当月扱い／来月扱い」で切り替え。

判定基準：クエリパラメータ年月および月内セッションの抽選確定状態（3.1.1 のルール）。

**当月扱い時の挙動**
- 既存登録（保存済み）のチェック外し：**一律禁止**（disabled かつ視覚的にロック）
- 未登録試合のチェック追加：可能（現状維持）
- 抽選確定済みセッション：これまで通り「ステータス表示（当選/待ち等）」固定（チェック不可）
- 締切表示：これまで通り表示

**来月扱い時の挙動**
- 既存登録のチェック外し：**可能**（=理由なしキャンセル、API上は未登録に戻す）
- 未登録試合のチェック追加：可能
- セッション単位のロック：そのセッションの `lotteryExecuted === true` の場合のみ、当該セッション全体を「ステータス表示」に切り替え、チェック不可
- 締切表示：これまで通り表示（ただし来月扱いの月は通常まだ締切前のため目立たない）

**共通**
- 抽選済みセッションの WON/WAITLISTED 等のステータス表示は現状維持
- 保存時の SAME_DAY 12:00以降確認ダイアログは現状維持
- クエリパラメータ `?year=YYYY&month=M` 受領は現状維持
- 「保存する」ボタン：当月扱い時に変更がないとき＝既存どおり非表示、来月扱い時にチェック外しによる変更があるとき＝表示

#### 3.1.5 PracticeCancelPage（キャンセル画面）

- 月ナビゲーション・YearMonthPicker を **廃止**
- タイトル「参加キャンセル」の下／上部に「○年○月」を固定表示
- クエリパラメータ `?year=YYYY&month=M` を受け取り、その月で固定（クエリ未指定時は現在年月をデフォルト）
- カレンダー表示は現状の「キャンセル可能セッション赤系ハイライト」を維持
- 試合選択・理由選択・確認ダイアログは現状維持
- 月変更時のリセット処理は除去（月ナビ廃止に伴い不要）

### 3.2 ビジネスルール

- **「過去月」のFABおよびインラインボタンは非表示**。過去月のキャンセル操作はできない（既存仕様と同じ）。
- **「当月扱い」と「来月扱い」の判定**は3.1.1のルールに従う。判定は表示月の `getPlayerParticipationStatus.lotteryExecuted` の値で決定する。
- **当月扱いの月で参加登録画面に入った場合**、既存登録（保存済み）のチェックは disabled、未登録試合のチェックは追加可能。
- **来月扱いの月で参加登録画面に入った場合**、既存登録のチェックは外せる（理由なし取消）。ただし `lotteryExecuted=true` のセッションは「ステータス表示」固定でチェック不可。
- **AttendanceRegisterModal の「キャンセル登録」ボタンの表示制御**は3.1.3のルールに従う（当月扱いのみ表示）。
- **キャンセル画面の対象月**は遷移元から受け取ったクエリパラメータ年月で固定。月変更はできない。
- **API は既存のものを再利用**：
  - 参加登録／チェック外しによる取消：`practiceAPI.registerParticipations`
  - 理由付きキャンセル：`lotteryAPI.cancelMultiple`

## 4. 技術設計

### 4.1 API設計

**追加・変更なし**。既存APIをそのまま利用する。
- `practiceAPI.getPlayerParticipationStatus(playerId, year, month)`：レスポンスの `lotteryExecuted: Map<Long, Boolean>` を利用
- `practiceAPI.registerParticipations(data)`：チェック追加／チェック外しの一括保存
- `lotteryAPI.cancelMultiple(participantIds, cancelReason, cancelReasonDetail)`：理由付きキャンセル

### 4.2 DB設計

**追加・変更なし**。

### 4.3 フロントエンド設計

#### 4.3.1 共通判定ヘルパーの新設

`karuta-tracker-ui/src/pages/practice/utils/attendanceMode.js`（新規）

```js
/**
 * 「当月扱い／来月扱い」の判定ロジックを共通化する。
 *
 * @param {number} year 判定対象の年（クエリパラメータ等）
 * @param {number} month 判定対象の月（1-12）
 * @param {Object<number, boolean>} lotteryExecutedMap セッションIDごとの抽選確定状態
 * @param {Date} [now] 現在時刻（テスト時に注入可能）
 * @returns {{ isCurrentMonth: boolean, isPastMonth: boolean }} 判定結果
 */
export function resolveAttendanceMode(year, month, lotteryExecutedMap, now = new Date()) {
  const currentYear = now.getFullYear();
  const currentMonth = now.getMonth() + 1;

  const targetIndex = year * 12 + (month - 1);
  const nowIndex = currentYear * 12 + (currentMonth - 1);

  if (targetIndex < nowIndex) {
    return { isCurrentMonth: false, isPastMonth: true };
  }
  if (targetIndex === nowIndex) {
    return { isCurrentMonth: true, isPastMonth: false };
  }
  const hasExecutedLottery = Object.values(lotteryExecutedMap || {}).some(Boolean);
  return { isCurrentMonth: hasExecutedLottery, isPastMonth: false };
}
```

#### 4.3.2 各画面・コンポーネントの変更

| ファイル | 変更内容 |
|---|---|
| `pages/practice/PracticeList.jsx` | 表示月変更時に `getPlayerParticipationStatus` で抽選状態を取得し `lotteryExecutedMap` をステート保持。`resolveAttendanceMode` で `isCurrentMonth` / `isPastMonth` を算出。FAB・インラインボタンの「過去月非表示」、`AttendanceRegisterModal` への `isCurrentMonth` 引き渡し。 |
| `components/AttendanceRegisterModal.jsx` | props に `isCurrentMonth: boolean` を追加。`isCurrentMonth === false` のとき「キャンセル登録」ボタンを非表示。 |
| `pages/practice/PracticeParticipation.jsx` | 既存の `lotteryExecuted` ステートと表示月から `resolveAttendanceMode` を呼んで `isCurrentMonth` を算出。`toggleMatch` および `isLockedRegistration` のロジックを更新：<br>・当月扱い：既存登録（`initialParticipations` に含まれる試合）はチェック外し不可（既存の締切後ロックと同じ振る舞いを当月扱い全般に拡張）<br>・来月扱い：既存登録のチェック外し可。ただし `lotteryExecuted[sessionId]` が true のセッションは個別にチェック不可（既存ロジックと同じ）<br>`hasChanges()` の判定は既存どおり。 |
| `pages/practice/PracticeCancelPage.jsx` | 月ナビゲーション（ChevronLeft/Right）と YearMonthPicker を削除。`changeMonth` 関数を削除。`useEffect` の月変更時リセットを削除。タイトル下に「○年○月」固定表示を追加。 |

#### 4.3.3 共通テスト

| ファイル | 変更内容 |
|---|---|
| `pages/practice/utils/attendanceMode.test.js`（新規） | `resolveAttendanceMode` の単体テスト：当月／来月（抽選確定済みあり）／来月（抽選確定済みなし）／過去月のケースを網羅 |
| `pages/practice/PracticeParticipation.test.jsx`（既存があれば拡張、なければ新規） | 当月扱い時のチェック外し不可、来月扱い時のチェック外し可、来月扱い時に抽選確定済みセッションのみロックされることを検証 |
| `pages/practice/PracticeCancelPage.test.jsx`（既存テストを修正） | 月ナビ削除後の挙動、クエリパラメータ月固定の検証 |
| `components/AttendanceRegisterModal.test.jsx`（新規または拡張） | `isCurrentMonth` による「キャンセル登録」ボタン表示制御の検証 |

### 4.4 バックエンド設計

DBマイグレーション・新規API追加はなし。ただしクロスレビューでの指摘を踏まえ、以下のサーバー側変更を行う：

1. **`PlayerParticipationStatusDto` に `hasAnyExecutedLotteryInMonth: Boolean` を追加**
   - 月単位の「当月扱い／来月扱い」判定用フラグ。月次抽選レコード（`session_id=null` の SUCCESS）とセッション単位の再抽選 SUCCESS の両方を考慮
   - 既存の `lotteryExecuted: Map<sessionId, Boolean>` は個別セッションのロック判定に使い続ける（責務を分離）
2. **`PracticeParticipantService.getPlayerParticipationStatusByMonth` の `lotteryExecuted` 算出を SUCCESS 限定に修正**
   - これまでは `findBySessionIdIn` の戻り値を status を見ずに true 化していたが、`status == SUCCESS` のレコードのみ反映するよう厳密化
3. **`PracticeParticipantService.registerParticipations` にサーバー側検証を追加**
   - フロントの `resolveAttendanceMode` と同じ判定（月単位 + セッション単位の SUCCESS 統合）で「当月扱い」を判別
   - 当月扱いの月で月内既存アクティブ参加（CANCELLED/DECLINED/WAITLIST_DECLINED 以外）がリクエストから欠落している場合、`IllegalArgumentException`（HTTP 400）で拒否
   - 理由付きキャンセルは `lotteryAPI.cancelMultiple` に集約するという仕様をAPI直叩きでも守らせるための防御

互換性影響：
- 既存クライアントが「当月扱いの月の参加登録 API リクエストで既存登録を意図的に欠落させる」と HTTP 400 になる。本プロジェクトのフロントエンドは新仕様で送信するため影響なし。

## 5. 影響範囲

### 5.1 変更ファイル一覧

**フロントエンド（変更／新規）**
- `karuta-tracker-ui/src/pages/practice/PracticeList.jsx`（変更）
- `karuta-tracker-ui/src/components/AttendanceRegisterModal.jsx`（変更）
- `karuta-tracker-ui/src/pages/practice/PracticeParticipation.jsx`（変更）
- `karuta-tracker-ui/src/pages/practice/PracticeCancelPage.jsx`（変更）
- `karuta-tracker-ui/src/pages/practice/utils/attendanceMode.js`（新規）
- `karuta-tracker-ui/src/pages/practice/utils/attendanceMode.test.js`（新規）
- 既存テストの調整（PracticeParticipation / PracticeCancelPage / AttendanceRegisterModal 関連）

**バックエンド** — 変更なし。

**DB** — 変更なし。

**ドキュメント**
- `docs/SCREEN_LIST.md`：項番13（PracticeList）、項番17（PracticeParticipation）、項番18（PracticeCancelPage）の説明を更新

### 5.2 既存機能への影響

- **抽選なし団体（わすらもち会）の当月セッション**：これまで「いつでもチェック外し可」だったが、新仕様では「当月扱いのため既存登録のチェック外し不可、キャンセル画面で理由付きキャンセル」となる。挙動が変わる重要な変更点。
- **PracticeCancelPage の月ナビゲーション削除**：これまで複数月のキャンセル操作が1画面で可能だったが、対象月固定となる。クエリパラメータ経由でしか入れないため、ブックマーク／直リンクには注意。
- **PracticeList の月変更時に1リクエスト追加**：`getPlayerParticipationStatus` を月毎に呼ぶ必要があるが、Home/Profile以外で表示されている画面と動作が変わるわけではない（PracticeParticipation で既に呼んでいるAPIを再利用）。
- **AttendanceRegisterModal**：props 追加で呼び出し元（PracticeList）も同時に修正が必要。

### 5.3 共通コンポーネント・ユーティリティへの影響

- `AttendanceRegisterModal` は PracticeList からのみ参照されているため、他画面への波及はなし。
- `attendanceMode.js` ヘルパーを新設し、PracticeList／PracticeParticipation で共通利用する。

### 5.4 API・DBスキーマの互換性

- API変更なし、DB変更なし、互換性問題なし。

## 6. 設計判断の根拠

### 6.1 FAB・モーダル・画面のタイトルを統一する
ユーザーの最終判断：「仮登録という表現はしなくていい、FABの見た目もやっぱ統一して、動きだけ変える」。
ユーザー視点で「画面の見た目が変わると混乱しやすい」「キャンセル登録ボタンの有無＝月の状態の差」だけで体感できれば十分という判断。

### 6.2 判定単位を「月単位」とする
ユーザーの希望：「カレンダーで表示したときはキャンセル登録ボタンも表示」。
判定を月単位（その月に抽選確定済みセッションが1つでもあれば当月扱い）にすることで、モーダル表示や画面振り分けがシンプルになる。

### 6.3 個別セッションのロックは「セッション単位」
来月扱いの月の中に「抽選確定済み（北大かるた会など）」と「抽選なし（わすらもち会）」が混在する可能性がある。ユーザーの希望：「セッション単位で振る舞いを変える」。
これにより、抽選確定済みセッションだけがロックされ、抽選なしセッションは自由にチェック外し可能になる。

### 6.4 抽選なし団体のセッションは「カレンダー月の扱いに従う」
ユーザーの希望：「カレンダー月と同じ扱い（当月=本登録動作、来月=仮登録動作）」。
抽選なし団体だけ常に自由にチェック外し可とすると、月の状態に対するユーザーの予測が破綻するため、当月扱いの月では一律ロックする方針を採用。

### 6.5 「現在年月で判定、ただし例外あり」
判定の本質はユーザーの体感（「今月の予定」「来月の予定」）に近づける必要がある一方、抽選確定済みかどうかでデータの確度が変わるため例外を設ける。
- 通常：現在年月＝当月扱い、それ以外＝来月扱い
- 例外：来月以降でも、その月のセッションに抽選確定済みがあれば当月扱いに切り替える

### 6.6 API・DB変更なし
既存の `PlayerParticipationStatusDto.lotteryExecuted` がすでに必要な情報を返しているため、フロントエンドの判定ロジックだけで実現可能。バックエンドの修正リスクや本番DBマイグレーションのリスクを回避する。
