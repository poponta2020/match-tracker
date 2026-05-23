---
status: completed
---
# 練習カレンダー 試合別ステータスグリッド 要件定義書

## 1. 概要

### 目的
練習カレンダー画面（`PracticeList.jsx`）の各日付セルの下半分に **試合ごとの空き状況** を ○△× の小さな記号でグリッド表示し、「どの試合がまだ空いているか／満員か」を一目で把握できるようにする。

### 背景・動機
- 既存機能 `practice-calendar-capacity-badge` は「残わずか／満員」のテキストバッジを **セッション単位** で表示するのみ。
- 「どの試合が満員でどの試合は空きがあるか」をカレンダー上で判別できず、セルをタップしてモーダルを開かないと分からない。
- 試合別の空き情報をカレンダーで一目可視化することで、「○試合目だけ空いてる」「△だけど滑り込めるか」といった試合単位の判断を、モーダルを開かずに行えるようにする。

### 既存機能との関係
- 本機能は既存の `practice-calendar-capacity-badge` を **完全に置き換える** 再設計である。
- 既存の「残わずか／満員」テキストバッジは削除する。
- バックエンドの `PracticeSessionDto.capacityStatus`（セッション単位の enum）は廃止し、`matchCapacityStatuses`（試合単位の配列）に置き換える。

## 2. ユーザーストーリー

### 対象ユーザー
- カレンダー画面を閲覧するすべてのプレイヤー
- 管理者・スーパー管理者も同じ表示で閲覧

### ユーザーの目的
- 「今月どの日のどの試合に空きがあるか」をカレンダー画面でざっくり把握する
- 「○試合目だけ空いてる」「△の試合に滑り込めるか」といった試合単位の判断を、モーダルを開かずに行う

### 利用シナリオ
1. プレイヤーがアプリを開きカレンダー画面を表示する
2. 各日付セルの下半分に試合別の ○△× グリッドが表示されている
3. 「明日の3試合目はまだ ○ だから申し込もう」「来週水曜は △ ばかりだから諦めよう」といった判断ができる
4. セルをタップすると従来通りモーダルで詳細を確認できる

## 3. 機能要件

### 3.1 画面仕様

#### 表示対象画面
- [PracticeList.jsx](karuta-tracker-ui/src/pages/practice/PracticeList.jsx) のカレンダー本体（`<td>` 内）

#### セルレイアウト
- セル全体の高さは現状の `h-20`（80px）を **維持** する。会場名・記号はコンパクトに収める。
- セルは視覚的に上下2セクションで構成する（**境界線は引かず、余白で分離**）:
  - 上半分: 日付番号 + 会場名（既存の表示）
  - 下半分: 試合別ステータスグリッド
- グリッドは会場名の下、十分な余白を空けて配置する。

#### 試合別ステータスグリッド
| 状態 | 記号 | 色 |
|------|------|-----|
| 空きあり (`AVAILABLE`) | ○ | 緑 |
| 残わずか (`NEARLY_FULL`) | △ | オレンジ（または黄系） |
| 満員 (`FULL`) | × | 赤 |

- 記号は **小さい文字**（例: `text-[9px]` 程度。実装時に視認性で微調整）
- グリッドは **3列固定**、行は最大3行（= 最大9試合）
- 試合番号順に **左詰め・行優先（row-major）** で配置:
  - 例: 3試合 → 行1: `○ ○ ×`（行2・3 なし）
  - 例: 4試合 → 行1: `○ ○ ○`、行2: `×`（左端、空きスロットは詰めない）
  - 例: 7試合 → 行1: `○ ○ ○`、行2: `△ △ ×`、行3: `×`
- グリッドはセル内で **水平方向は中央寄せ**（行内は左詰め）
- グリッドの各行は等間隔、各列も等間隔（記号は monospace 風に揃える）

#### 表示しないケース（グリッドを出さない条件）
以下のいずれかに該当する場合、グリッドそのものを描画しない（会場名のみのセルになる）:

1. その日に **練習セッションが2件以上ある**（同日複数団体）
2. セッションの `capacity` が `null` または `0` 以下
3. セッションの `totalMatches` が `null` または `0` 以下
4. `matchCapacityStatuses` が `null`（バックエンド側で算出不可だった場合）
5. `totalMatches >= 10`（3×3 グリッドに収まらないため非表示）

#### 過去日の扱い
- 過去日も現在・未来と同じ表示ルールを適用する（過去日もグリッドを表示）。

#### 参加状況色との関係
- 既存の「自分の参加状況による背景色」（`confirmed` = 緑系背景 / `waitlisted` = 黄系背景）は **保持** するが、グリッドの記号が読みやすいよう **既存より一段薄く** する（具体的なクラス変更はデザイン実装時に確定）。
- 罫線（`border-2 border-[#a3c4ad]` 等）は維持。

#### クリック挙動
- 現状のまま、セル全体のタップで該当日のモーダルを開く。グリッド記号自体に個別のクリック挙動は付けない。

### 3.2 ビジネスルール

#### 試合別ステータスの判定ロジック

各試合について、以下を計算する:

```
effectiveCount(試合) = COUNT(WON) + COUNT(PENDING) + COUNT(OFFERED)
remaining(試合) = capacity - effectiveCount(試合)
```

- `WAITLISTED`, `DECLINED`, `CANCELLED`, `WAITLIST_DECLINED` は枠取得カウントに含めない（既存の判定と同じ）
- `PENDING` を含めるのは、抽選なし運用（`pairingIncludesPending = true`）でも「実質枠を取っている」とみなすため
- `OFFERED`（繰り上げ通知応答待ち）は名目上枠を確保しているのでカウントに含める

各試合のステータスは以下の優先順位で判定する:

1. **`FULL`**: `effectiveCount >= capacity` （= remaining ≤ 0）
2. **`NEARLY_FULL`**: `0 < remaining <= 2` （= 残り席数が 1〜2）
3. **`AVAILABLE`**: それ以外（= remaining > 2）

判定対象とする試合番号は `1` から `min(totalMatches, 9)` まで。

#### 参加者ゼロ試合の扱い
- ある試合番号に `PracticeParticipant` のレコードが1件もない場合、`effectiveCount = 0`。
- `capacity` が設定されていれば、remaining = capacity → `AVAILABLE`（○）として表示する。

#### 同日複数セッションの扱い
- セルの `daySessions.length > 1` のとき、グリッドは表示しない（ごちゃつき回避）。
- 会場名は従来通り全セッション分を縦に並べて表示する。

#### エラー・例外処理
- バックエンドの集計でエラーが起きた場合は `matchCapacityStatuses = null` にフォールバックし、カレンダー表示は阻害しない（グリッドを出さないだけ）。
- フロント側は `matchCapacityStatuses` が `null`／配列長0／不正値（既知 enum 値以外）のとき、グリッドを表示しない。

## 4. 技術設計

### 4.1 API設計

#### 変更対象エンドポイント
`GET /api/practice-sessions/year-month/summary`

#### `PracticeSessionDto` レスポンス変更

**削除フィールド:**
- `capacityStatus: CapacityStatus`（セッション単位の enum、既存）

**追加フィールド:**

| フィールド | 型 | 説明 |
|-----------|-----|------|
| `matchCapacityStatuses` | `List<CapacityStatus>` （要素 enum: `AVAILABLE` / `NEARLY_FULL` / `FULL`） | 試合単位の定員到達状況。`matchCapacityStatuses[i]` が第 `i+1` 試合の状態。長さは `min(totalMatches, 9)` |

**`matchCapacityStatuses` が `null` になる条件:**
- `capacity == null || capacity <= 0`
- `totalMatches == null || totalMatches <= 0`
- `totalMatches >= 10`
- 算出中に例外発生

これらの場合、フロント側はグリッドを描画しない。

### 4.2 DB設計
- **変更なし**
- 既存の `practice_sessions.capacity`、`practice_sessions.total_matches`、`practice_participants.status`、`practice_participants.match_number` のみで判定可能。

### 4.3 バックエンド設計

#### 変更ファイル
| ファイル | 変更内容 |
|---------|----------|
| `dto/PracticeSessionDto.java` | `capacityStatus` フィールドを削除し、`matchCapacityStatuses: List<CapacityStatus>` を追加。内部 enum `CapacityStatus` は値そのまま（`AVAILABLE` / `NEARLY_FULL` / `FULL`）で維持。 |
| `service/PracticeSessionService.java` | `findSessionSummariesByYearMonth` 内の `capacityStatus` 算出ロジックを、試合単位の `matchCapacityStatuses` 算出に書き換える。 |
| `service/PracticeSessionServiceTest.java` | 既存の capacity 関連テストを試合単位用に書き換える。 |

#### 処理フロー (`findSessionSummariesByYearMonth` 内)

1. 既存処理: 月内セッション一覧取得、会場名マップ取得
2. 試合別ステータス算出:
   - 全セッションIDを集めて `practiceParticipantRepository.findBySessionIdIn(sessionIds)` で一括取得（N+1回避、既存パターン踏襲）
   - セッション別 × 試合番号別 × ステータス別の集計マップを構築
   - 各セッションについて以下を実施:
     - `capacity == null || capacity <= 0` → `matchCapacityStatuses = null`、次へ
     - `totalMatches == null || totalMatches <= 0 || totalMatches >= 10` → `matchCapacityStatuses = null`、次へ
     - 第 1 試合〜第 `totalMatches` 試合まで、各試合について `effectiveCount = won + pending + offered` を計算
     - `remaining = capacity - effectiveCount` で以下のステータスを決定:
       - `effectiveCount >= capacity` → `FULL`
       - `0 < remaining <= 2` → `NEARLY_FULL`
       - それ以外 → `AVAILABLE`
   - DTOに `matchCapacityStatuses` をセット

#### パフォーマンス
- 月内の全セッション参加者を1クエリで取得するため、N+1にはならない（既存方式踏襲）
- 既存の `capacityStatus` 算出よりも要素数（試合数 ≤ 9）が増えるが、メモリ・CPU影響は無視できる範囲

### 4.4 フロントエンド設計

#### 変更ファイル
| ファイル | 変更内容 |
|---------|----------|
| `pages/practice/PracticeList.jsx` | 既存「残わずか／満員」バッジ描画ロジックを削除し、試合別ステータスグリッド描画ロジックに置き換える。参加状況背景色を一段薄める。 |
| `pages/practice/PracticeList.capacityBadge.test.jsx` | 既存バッジ用テストを削除（または同名でグリッド用テストに置き換える）。 |
| `pages/practice/PracticeList.matchStatusGrid.test.jsx`（新規） | 試合別ステータスグリッドの描画ケースを網羅するテストを追加。 |

#### 処理フロー

セル描画ロジック（`<td>` 内）に以下を追加:

1. `daySessions` を取得
2. `daySessions.length === 1` かつそのセッションの `matchCapacityStatuses` が配列で長さ ≥ 1 のとき、下半分にグリッドを描画
3. それ以外（複数セッション／グリッド条件外）はグリッド非表示
4. グリッドの描画ルール:
   - 3列固定の grid (`grid grid-cols-3`) で `matchCapacityStatuses` を順に並べる
   - 左詰め、行優先
   - 各記号は色付き small text（`text-[9px]` 程度、`text-green-600` / `text-orange-500` / `text-red-600` など）

#### スタイル例（実装時に微調整）

```jsx
{daySessions.length === 1 && (() => {
  const statuses = daySessions[0].matchCapacityStatuses;
  if (!Array.isArray(statuses) || statuses.length === 0) return null;
  const symbolFor = (s) => {
    if (s === 'FULL') return { ch: '×', cls: 'text-red-600' };
    if (s === 'NEARLY_FULL') return { ch: '△', cls: 'text-orange-500' };
    return { ch: '○', cls: 'text-green-600' };
  };
  return (
    <div className="mt-1 grid grid-cols-3 gap-0.5 text-[9px] leading-none justify-items-center">
      {statuses.map((s, i) => {
        const { ch, cls } = symbolFor(s);
        return <span key={i} className={`${cls} font-bold`}>{ch}</span>;
      })}
    </div>
  );
})()}
```

#### 参加状況色の弱化
- `cellBg = 'bg-[#dce5de]'`（confirmed）→ より薄い緑、または `bg-opacity-50` 等で透過
- `cellBg = 'bg-[#fef9ed]'`（waitlisted）→ 既に薄いが、必要なら更に薄める
- 具体的なトーン調整は実装時に視認性テストで確定する

#### 状態管理
- 既存の `sessions` ステートをそのまま使用。追加ステートは不要。
- 既存の参加者編集モーダル保存後（`handleSaveMatchParticipants`）の `fetchSessions()` 呼び出しもそのまま機能する（`matchCapacityStatuses` がサマリーAPI由来のため）。

### 4.5 テスト方針

#### バックエンド (`PracticeSessionServiceTest`)
- `findSessionSummariesByYearMonth` の `matchCapacityStatuses` 計算テストを追加:
  - capacity null / 0 / 負 → `null`
  - totalMatches null / 0 / 負 / 10以上 → `null`
  - 全試合で空き多数 → `[AVAILABLE, AVAILABLE, ...]`
  - 一部試合で remaining ≤ 2 → 該当試合のみ `NEARLY_FULL`
  - 一部試合で remaining = 0 → 該当試合のみ `FULL`
  - WAITLISTED / CANCELLED は effectiveCount に含めないこと
  - PENDING / OFFERED は含めること
  - 参加者ゼロの試合は `AVAILABLE`
  - totalMatches > 配列長で揃わない場合がないこと（長さ = totalMatches）
- 既存の `capacityStatus` テストは削除する

#### フロントエンド
- `PracticeList.matchStatusGrid.test.jsx` を追加（または既存 `PracticeList.capacityBadge.test.jsx` を置き換え）:
  - 単一セッションで `[AVAILABLE, NEARLY_FULL, FULL]` → ○ △ × が左詰めで描画
  - 7試合の場合の 3+3+1 行レイアウト
  - 同日2セッション → グリッド非表示
  - `matchCapacityStatuses` が null → グリッド非表示
  - capacity 未設定セッション → グリッド非表示

## 5. 影響範囲

### 変更が必要な既存ファイル
- `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/PracticeSessionDto.java`
- `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeSessionService.java`
- `karuta-tracker/src/test/java/com/karuta/matchtracker/service/PracticeSessionServiceTest.java`
- `karuta-tracker-ui/src/pages/practice/PracticeList.jsx`
- `karuta-tracker-ui/src/pages/practice/PracticeList.capacityBadge.test.jsx`（削除 or 名前変更）
- `docs/SPECIFICATION.md`
- `docs/SCREEN_LIST.md`
- `docs/DESIGN.md`

### 既存機能への影響
- **`getSessionSummaries` API の互換性**: `capacityStatus` フィールドを削除し `matchCapacityStatuses` を追加するため **レスポンス形状が変わる**。ただし `capacityStatus` を読んでいるのは `PracticeList.jsx` のみで、同 PR で同時に修正する。他エンドポイント (`getById` 等) は今回スコープ外で `matchCapacityStatuses` を返さない（フィールド未定義 = null）。
- **他エンドポイントへの影響**: `getById` / `getByDate` などの詳細APIには `matchCapacityStatuses` を返さない（必要になったタイミングで別途追加）。
- **DBスキーマ**: 変更なし。マイグレーションSQLなし。
- **既存テスト**: `PracticeSessionServiceTest` 内の capacityStatus 関連ケースを試合単位用に書き換える必要あり。
- **既存フロントテスト**: `PracticeList.capacityBadge.test.jsx` は廃止し新規テストに置き換える。

### ドキュメント更新
- `docs/SPECIFICATION.md`: カレンダー画面の「定員状況バッジ」項目を「試合別ステータスグリッド」仕様に書き換える。
- `docs/SCREEN_LIST.md`: 該当画面の説明を更新（試合別空き状況表示）。
- `docs/DESIGN.md`: API設計の `capacityStatus` → `matchCapacityStatuses` に書き換え。

## 6. 設計判断の根拠

### `capacityStatus`（セッション単位）を廃止し `matchCapacityStatuses`（試合単位）に置き換える理由
- 本機能の目的そのものが「試合単位の可視化」なので、セッション単位の集約値は不要。
- 両フィールドを共存させると DTO が肥大化し、フロントが「どちらを参照するか」迷う温床になる。
- `capacityStatus` の唯一の利用箇所が同 PR で書き換わるため、互換性懸念はゼロ。

### △ の判定を「残り2席以下」（絶対値）にした理由
- 練習会場の capacity は 4〜8 程度が一般的で、残り席数の絶対値で「あと数席」と感じる感覚が直感的。
- パーセンテージ判定（例: 80%以上）だと capacity ごとに △ になる人数が変わり、ユーザー側の感覚と合わない。
- capacity が極端に小さい場合（例: capacity = 2）は corner case として「最初から△扱い」になるが、現実的に発生する設定ではないため許容する。

### 同日複数セッション時にグリッドを非表示にする理由
- カレンダーセルは狭く、複数団体のグリッドを並べるとレイアウトが崩れる。
- 複数団体の場合は会場名（団体色付き）の表示そのものが視認の手がかりになるため、グリッドなしでも導線は維持される。
- 詳細はモーダルで確認できる。

### 10試合以上を非表示にする理由
- 3×3 = 最大9試合に固定。
- 10試合以上の運用は実質皆無で、無理に行追加すると他セルと高さが揃わずレイアウト崩壊を招く。
- 必要になったら後日拡張する。

### 参加者ゼロ試合を ○ にする理由
- まだ申込なし = 空きフル = ○ が直感に合う。
- 「表示しない」「グレー」とすると「データなし」と紛らわしく、新規申込導線として弱くなる。

### 過去日も同じ表示にする理由
- 過去日のグリッドを見て「あの日は○試合目だけ空いてた」と振り返るユースケースがある。
- 過去・未来で表示分岐を入れるとロジックが複雑化し、判定もブレやすい。

### バックエンドで事前計算する理由
- フロント側に判定ロジックを置くと、参加者集計と二重実装になり乖離するリスクがある。
- サマリーAPI の軽量コンセプトを維持しつつ、ペイロード追加は短い配列（最大9要素）で済む。

### 同日複数セッションの判定をフロント側に置く理由
- バックエンドはセッション単体で完結する責務にしたい（日付の集約はそもそも別レイヤー）。
- フロント側で `daySessions.length` を見るのは1行で済み、ロジックが単純。
- バックエンドに集約ロジックを入れると `getSessionSummaries` のレスポンス意味論が変わる（「他にもセッションあるなら null 返す」になり、再利用性が落ちる）。
