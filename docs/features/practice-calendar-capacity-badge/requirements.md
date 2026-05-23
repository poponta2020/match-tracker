---
status: completed
---
# 練習カレンダー 定員状況バッジ 要件定義書

## 1. 概要

### 目的
練習カレンダー画面（`PracticeList.jsx`）で、各日の練習セッションが「埋まりかけ」か「満員」かを一目で把握できるようにする。

### 背景・動機
- 現状のカレンダーは「日付」「会場名」と、自分の参加状況（confirmed / waitlisted）に応じたセル背景色のみを表示している。
- 申込前のユーザーが「この日はもう埋まっているか／まだ空きがあるか」をカレンダー上で判断できず、毎回セルをタップしてモーダルを開かないと分からない。
- カレンダーの段階で空き状況のシグナルを返すことで、申込判断のための無駄なタップ回数を減らす。

## 2. ユーザーストーリー

### 対象ユーザー
- カレンダー画面を閲覧するすべてのプレイヤー
- 管理者・スーパー管理者も同じ表示で閲覧

### ユーザーの目的
- 「今月どこに空きがあるか」「もう申し込んでも当選しなさそうな日はどこか」をカレンダー画面でざっくり把握する

### 利用シナリオ
1. プレイヤーがアプリを開きカレンダー画面を表示する
2. 全部の練習に空きがある日（多くの場合）は今まで通り会場名のみが見える
3. どこか1試合でも埋まりかけている日には「残わずか」のバッジが表示されている
4. 全試合が埋まっている日には「満員」のバッジが表示されている
5. プレイヤーはセルをタップしなくても、申込判断や代替日の検討ができる

## 3. 機能要件

### 3.1 画面仕様

#### 表示対象画面
- [PracticeList.jsx](karuta-tracker-ui/src/pages/practice/PracticeList.jsx) のカレンダー本体（`<td>` 内）

#### バッジ仕様
| 状態 | バッジ文言 | 配色 |
|------|-----------|------|
| 空きあり (`AVAILABLE`) | （バッジなし） | — |
| 残わずか (`NEARLY_FULL`) | `残わずか` | 黄系背景（例: `bg-yellow-100 text-yellow-800`） |
| 満員 (`FULL`) | `満員` | 赤系背景（例: `bg-red-100 text-red-700`） |

- バッジは小さなテキストバッジ（例: `text-[10px] px-1.5 py-0.5 rounded`）
- アイコンは使用しない

#### 配置
- セル内の **会場名の下** に中央揃えで表示
- 会場名と同じ Flex/Block の縦並びに追加するレイアウトで、セルの絶対配置は使わない
- 既存の「日付」「会場名」のレイアウトと干渉しないようにする

#### 表示対象日
- **過去日も含めてすべての日付** で表示する
- 過去日もキャパシティ判定ロジックを適用する

#### 同日複数セッション
- 同一日に複数団体のセッションがある場合は、**セル全体で1つだけ** バッジを表示する
- 表示する状態は最も重いものを採用: `FULL` > `NEARLY_FULL` > `AVAILABLE`
- 例: 日本郷土館（FULL）と北郷（NEARLY_FULL）が同日にある場合 → 「満員」のみ表示

### 3.2 ビジネスルール

#### 判定ロジック

「実質枠取得人数」を以下で定義する:

```
effectiveCount(試合) = COUNT(WON) + COUNT(PENDING) + COUNT(OFFERED)
```

- `WAITLISTED`, `DECLINED`, `CANCELLED`, `WAITLIST_DECLINED` は枠取得カウントに含めない
- `PENDING` を含めるのは、抽選なし運用（`pairingIncludesPending = true`）でも「実質枠を取っている」とみなすため
- `OFFERED`（繰り上げ通知応答待ち）は名目上枠を確保しているのでカウントに含める

セッションの `capacityStatus` は次の優先順位で判定する:

1. **対象外（`AVAILABLE` 扱い）**: `capacity == null` または `capacity <= 0` のセッション
2. **対象外（`AVAILABLE` 扱い）**: そのセッションに紐づく試合（`PracticeParticipant.matchNumber` の `1〜totalMatches`）が1つもない場合
3. **`FULL`**: そのセッションの **全試合** で `effectiveCount(試合) >= capacity`
4. **`NEARLY_FULL`**: そのセッションの **いずれか1試合以上** で `effectiveCount(試合) >= capacity`
5. **`AVAILABLE`**: 上記以外

判定対象とする試合番号は `1` から `session.totalMatches` までの整数。`totalMatches` が null / 0 のセッションは `AVAILABLE` 扱い。

#### エラーケース・例外処理
- バックエンドの集計でエラーが起きた場合は `capacityStatus = null`（または `AVAILABLE`）にフォールバックし、カレンダー表示は阻害しない
- フロントエンドは `capacityStatus` が未定義／不明値のとき、バッジを表示しない（防御的に AVAILABLE 扱い）

## 4. 技術設計

### 4.1 API設計

#### 変更対象エンドポイント
`GET /api/practice-sessions/year-month/summary`

#### レスポンスDTO追加フィールド
`PracticeSessionDto` に以下を追加:

| フィールド | 型 | 説明 |
|-----------|-----|------|
| `capacityStatus` | `String` (enum: `AVAILABLE` / `NEARLY_FULL` / `FULL`) | セッションの定員到達状況。サマリーAPIのみで返却される |

- `capacity == null || capacity <= 0` または試合がないセッションは `AVAILABLE` を返す
- 他のエンドポイント（`getById`, `findByDate*` など）では現状この値を返さない（空文字／null）。今回のスコープはサマリーAPIのみ

### 4.2 DB設計
- **変更なし**
- 既存の `practice_sessions.capacity` と `practice_participants.status` のみで判定可能

### 4.3 バックエンド設計

#### 変更ファイル
| ファイル | 変更内容 |
|---------|----------|
| `dto/PracticeSessionDto.java` | `capacityStatus` フィールドと内部 enum `CapacityStatus { AVAILABLE, NEARLY_FULL, FULL }` を追加 |
| `service/PracticeSessionService.java` | `findSessionSummariesByYearMonth` で参加者を一括取得して集計し、各DTOに `capacityStatus` を設定 |

#### 処理フロー (`findSessionSummariesByYearMonth` 内に追加)
1. 既存処理: 月内セッション一覧取得、会場名マップ取得
2. 追加処理:
   - 全セッションIDを集めて `practiceParticipantRepository.findBySessionIdIn(sessionIds)` で一括取得（N+1回避）
   - セッション別 × 試合番号別 × ステータス別の `Map<Long, Map<Integer, Map<ParticipantStatus, Long>>>` を構築
   - 各セッションについて以下を実施:
     - `capacity` が null / 0 → `AVAILABLE`
     - `totalMatches` が null / 0 → `AVAILABLE`
     - 1〜totalMatches の各試合で `effectiveCount = won + pending + offered` を計算
     - 全試合で `effectiveCount >= capacity` → `FULL`
     - いずれか1試合以上で `effectiveCount >= capacity` → `NEARLY_FULL`
     - それ以外 → `AVAILABLE`
   - DTOに `capacityStatus` をセット

#### パフォーマンス
- 月内の全セッション参加者を1クエリで取得するため、N+1にはならない
- 既存の `enrichSessionsWithParticipants` と同じパターンを踏襲
- 軽量サマリーAPIの想定に沿い、参加者の詳細（名前・ランク）まではエンリッチしない

### 4.4 フロントエンド設計

#### 変更ファイル
| ファイル | 変更内容 |
|---------|----------|
| `pages/practice/PracticeList.jsx` | カレンダーセル描画ロジックに `capacityStatus` バッジ表示を追加 |

#### 処理フロー
1. `getSessionsForDate(day)` の結果から、各セッションの `capacityStatus` を取り出す
2. 同日複数セッションの場合は最も重い状態を選ぶ:
   ```js
   const order = { FULL: 2, NEARLY_FULL: 1, AVAILABLE: 0 };
   const worst = daySessions.reduce((acc, s) => {
     const v = order[s.capacityStatus] ?? 0;
     return v > acc.value ? { value: v, status: s.capacityStatus } : acc;
   }, { value: 0, status: 'AVAILABLE' }).status;
   ```
3. `worst` が `NEARLY_FULL` または `FULL` のとき、会場名表示の直下にバッジを描画
4. バッジコンポーネントは `PracticeList.jsx` 内で小さなインライン JSX として実装（汎用化はしない）

#### スタイル例
```jsx
{worst === 'NEARLY_FULL' && (
  <div className="mt-0.5 text-[10px] leading-tight px-1.5 py-0.5 rounded bg-yellow-100 text-yellow-800 font-medium inline-block">
    残わずか
  </div>
)}
{worst === 'FULL' && (
  <div className="mt-0.5 text-[10px] leading-tight px-1.5 py-0.5 rounded bg-red-100 text-red-700 font-medium inline-block">
    満員
  </div>
)}
```

#### 状態管理
- 既存の `sessions` ステートをそのまま使用。追加ステートは不要
- 既存の `cellBg` / `cellBorder`（自分の参加状況による色分け）は維持する

### 4.5 テスト方針
- バックエンド: `PracticeSessionServiceTest` に `findSessionSummariesByYearMonth` の `capacityStatus` 計算テストを追加
  - capacity null / 0 → AVAILABLE
  - 全試合で空き → AVAILABLE
  - 一部試合で WON+PENDING+OFFERED >= capacity → NEARLY_FULL
  - 全試合で WON+PENDING+OFFERED >= capacity → FULL
  - WAITLISTED / CANCELLED はカウントに含めないこと
- フロントエンド: `PracticeList` の既存テストに同日複数セッションでのバッジ優先順位ケースを追加（必要に応じて）

## 5. 影響範囲

### 変更が必要な既存ファイル
- `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/PracticeSessionDto.java`
- `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeSessionService.java`
- `karuta-tracker-ui/src/pages/practice/PracticeList.jsx`
- テストファイル（バックエンド・フロントエンド該当箇所）

### 既存機能への影響
- **`getSessionSummaries` API のレスポンスにフィールド追加**: 既存利用箇所（`PracticeForm.jsx`, `PracticeCancelPage.jsx`, `PracticeList.jsx`）はフィールドを読まないだけなので破壊的影響なし
- **他エンドポイントへの影響**: `getById` / `getByDate` などの詳細APIには `capacityStatus` を返さない（必要になったタイミングで別途追加）
- **DBスキーマ**: 変更なし
- **既存テスト**: `PracticeSessionDto` を組み立てる既存テストでフィールド追加に伴う不具合はないか確認（builder 追加のみ）

### ドキュメント更新
- `docs/SPECIFICATION.md`: カレンダー画面の仕様に「定員状況バッジ」の節を追加
- `docs/SCREEN_LIST.md`: 該当画面の説明に空き状況表示の項を追加
- `docs/DESIGN.md`: API設計（サマリーAPIのレスポンス）に `capacityStatus` を追加

## 6. 設計判断の根拠

### 判定対象に PENDING と OFFERED を含める理由
- **PENDING**: 抽選なし運用（`pairingIncludesPending = true`）では PENDING がそのまま参加確定相当となる。抽選あり運用でも申込フェーズの「予定された定員超え」を可視化したいというユーザー要望に合致する
- **OFFERED**: 繰り上げ通知応答待ちは名目上枠を確保しているため、空き状況の判定上はカウントすべき

### 「満員」判定も WON+PENDING+OFFERED で揃える理由
- 抽選あり／なしの両運用で同じロジックが働き、説明可能性が高い
- WON のみで判定すると抽選なし運用では永久に FULL にならず、表示の意味がなくなる

### バックエンドで事前計算（enum）を返す方針の理由
- フロント側に判定ロジックを置くと、バックエンドの参加者集計と二重実装になりズレる
- サマリーAPIの「軽量」コンセプトを維持しつつ、ペイロード追加は短い文字列1つで済む
- 後から判定ロジックを変更する場合もバックエンドの1箇所修正で完結

### 過去日も表示する理由
- 過去日のカレンダーを遡って「この日は満員だった」と確認したいケースに対応
- 表示するかどうかをフロントで日付判定する複雑さを避け、判定は常時実施

### 同日複数セッションで1つだけ表示する理由
- カレンダーセルは狭く、複数バッジを並べるとレイアウトが崩れる
- 最も重い状態を表示することで「この日は何か埋まっている」シグナルを優先する
