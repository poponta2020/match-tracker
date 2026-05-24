---
status: completed
audit_source: 会話内ユーザーからの不具合報告（カレンダー画面でわすらもち会のセッションに〇×△が表示されない）
selected_items: [A1, A2]
---

# 練習カレンダー試合別ステータスグリッド 改修要件定義書

## 1. 改修概要

### 対象機能
カレンダー画面の試合別キャパシティステータスグリッド（〇△× の 3 列固定グリッド）
- 関連 PR: [#760](https://github.com/poponta2020/match-tracker/pull/760)
- 元実装: コミット `b94c993 feat(practice-calendar): replace capacity badge with per-match status grid`

### 改修の背景
わすらもち会のセッションでカレンダー画面に〇×△が一切表示されない、というユーザー報告。

調査の結果、以下が判明した:

- バックエンドの `PracticeSessionService.computeMatchCapacityStatuses` は `session.getCapacity() == null` のとき `matchCapacityStatuses` を `null` で返す
- フロント (`PracticeList.jsx:712-740`) は `matchCapacityStatuses` が配列でないと描画しない
- 本番 DB では `practice_sessions.capacity` が NULL のレコードが多数存在:
  - わすらもち会: 44 件中 **33 件 (75%)** が NULL
  - 北海道大学かるた会: 51 件中 12 件 (24%) が NULL
- NULL になっている根本原因は `DensukeImportService.java:845-852` で伝助同期から練習日を自動作成する際、`.capacity(...)` を呼んでいないこと。手動作成フォーム (`PracticeForm.jsx:222-224`) では venue 選択時に venue 既定 capacity が自動で入る仕組みがあるが、伝助同期側に同じロジックが無い
- 一方で `LotteryService.java:223-228` には「`capacity` が NULL なら venue 既定値で埋める」フォールバックが既に存在する。抽選機能は今までも問題なく動いていたが、サマリー API にだけフォールバックがなかったのが本質

### 改修スコープ
監査ではなく不具合報告起点のため、対応項目は会話の中で確定した以下の 2 つ:

- **A1**: 伝助同期で自動作成されるセッションにも venue 既定 capacity を入れる + 既存 NULL 45 件を venue 既定 capacity で UPDATE
- **A2**: サマリー API (`PracticeSessionService.computeMatchCapacityStatuses`) にも `LotteryService` 同様の「capacity NULL → venue 既定値」フォールバックを入れる

---

## 2. 改修内容

### 2.1 DensukeImportService に capacity 設定を追加

**現状の問題:**

`DensukeImportService.java:845-852` で `PracticeSession.builder()` を呼ぶ際、`.capacity(...)` が呼ばれていない。venue 解決済みでも capacity は常に NULL になる。

```java
// 現状
PracticeSession session = PracticeSession.builder()
        .sessionDate(entry.getDate())
        .totalMatches(totalMatches)
        .venueId(venueId)
        .organizationId(organizationId)
        .createdBy(createdBy)
        .updatedBy(createdBy)
        .build();
```

**修正方針:**

venue が解決できた場合（`venueId != null` かつ `venue.getCapacity() != null`）、venue の既定 capacity をビルダーに渡す。venue が解決できなかった場合 (`venueId == null`) は既存挙動どおり capacity は NULL のままにする（後述 2.3 のフォールバックで救う）。

**修正後のあるべき姿:**

伝助同期で新規作成されるセッションは、venue が解決できる限り capacity が venue 既定値で入る。

### 2.2 既存 NULL レコードのバックフィル

**現状の問題:**

本番 DB に capacity NULL のレコードが 45 件残っている。コード修正だけでは過去レコードは埋まらない。

**修正方針:**

`database/backfill_practice_session_capacity_from_venue.sql` を新規作成し、`venue_id` が解決可能かつ `venues.capacity IS NOT NULL` のレコードを一括 UPDATE する。本番 DB 適用は CLAUDE.md の DB マイグレーション適用ルールに従う（マージ前後で psql 経由で実行）。

```sql
UPDATE practice_sessions ps
SET capacity = v.capacity
FROM venues v
WHERE ps.venue_id = v.id
  AND ps.capacity IS NULL
  AND v.capacity IS NOT NULL;
```

**修正後のあるべき姿:**

本番 DB 上のわすらもち会 33 件 + 北大かるた会 12 件 (= 45 件) すべてに venue 既定 capacity が入る。事前確認の通り `capacity IS NULL` のレコードはすべて `venue_id IS NOT NULL` であるため、漏れは出ない。

### 2.3 PracticeSessionService にフォールバックロジック追加

**現状の問題:**

`PracticeSessionService.computeMatchCapacityStatuses` は `session.getCapacity()` だけを見ており、NULL なら即座に `null` を返す。`LotteryService.processSession` には既に venue 既定値フォールバックがあるが、サマリー API には同じロジックがない。

**修正方針:**

`findSessionSummariesByYearMonth` で月内会場を一括取得する箇所を、`venueRepository.findAllById` の戻り値を「会場名マップ」だけでなく「Venue オブジェクトのマップ」として保持する形に変更し、`computeMatchCapacityStatuses` 呼び出し時に venue 既定 capacity を渡す。`computeMatchCapacityStatuses` 側は session capacity が NULL なら venue 既定 capacity にフォールバックする。

**修正後のあるべき姿:**

伝助同期以外の経路（手動 INSERT、将来別の自動同期）で capacity NULL のセッションが入ってきた場合でも、サマリー API はカレンダーに〇×△を表示できる。データの根本対処（A1）と表示側の防御（A2）が二重化される。

---

## 3. 技術設計

### 3.1 API 変更
なし（既存エンドポイントのレスポンス形式は変更しない。`matchCapacityStatuses` が出てくる頻度が増えるだけ）

### 3.2 DB 変更
- スキーマ変更なし（既存 `practice_sessions.capacity` カラムをそのまま使う）
- データ変更あり: `database/backfill_practice_session_capacity_from_venue.sql` で既存 NULL 45 件を UPDATE

### 3.3 フロントエンド変更
なし

### 3.4 バックエンド変更

#### `DensukeImportService.java`
- `createSession` 系のヘルパー（L845 付近）で `PracticeSession.builder()` に `.capacity(...)` を追加
- venue が解決できた場合のみ venue 既定 capacity を入れる。それ以外は NULL のまま

#### `PracticeSessionService.java`
- `findSessionSummariesByYearMonth`:
  - 現状 `venueNameMap (Long → String)` だけ保持しているのを、`Venue` オブジェクトマップ `(Long → Venue)` に変更（または並行して保持）
  - `computeMatchCapacityStatuses` 呼び出し時に venue 既定 capacity を引数として渡す
- `computeMatchCapacityStatuses`:
  - 引数に `Integer venueDefaultCapacity` を追加
  - 既存ロジックの先頭で `Integer capacity = session.getCapacity()` した直後、`if (capacity == null) { capacity = venueDefaultCapacity; }` を入れる
  - その後の `capacity == null || capacity <= 0 → null` 判定はそのまま残す

#### テスト
- `PracticeSessionServiceTest`:
  - 新規ケース: capacity が NULL で venue 既定値があるセッション → `matchCapacityStatuses` が venue 既定値ベースで返る
  - 新規ケース: capacity も venue 既定値も NULL → 既存と同じく `null` を返す
- `DensukeImportServiceTest`（存在する場合）:
  - 新規ケース: 伝助同期でセッション作成時、venue 既定 capacity がセットされる

---

## 4. 影響範囲

### 4.1 影響を受ける既存機能

| 機能 | 影響 | 評価 |
|------|------|------|
| カレンダー画面の〇△×グリッド (`PracticeList.jsx`) | NULL だったセッションでも表示されるようになる | 意図通り |
| 抽選機能 (`LotteryService`) | 既に同等フォールバック有り。DB 側も埋まるので一時 set が空振りするだけ | 影響なし |
| キャンセル待ち昇格 (`WaitlistPromotionService`) | `session.getCapacity()` を見て動作。これまで NULL だったセッションでは適切に動いていなかった可能性あり。今後は venue 既定値で動く | 改善（潜在バグ解消） |
| 隣室予約/会場拡張 (`AdjacentRoomService`) | `expandVenue` で `session.setCapacity(expandedVenue.getCapacity())` で上書きするため capacity を起点に動かない | 影響なし |
| 編集画面 (`PracticeForm.jsx` 編集モード) | 既存セッション編集時に capacity の表示が NULL → 14 に変わる（より正しい値になる） | 軽微・ポジティブ |
| 抽選確定済みセッションの履歴 | capacity NULL → 14 に変わるが、抽選結果自体は `practice_participants.status` で確定済みのため変化しない | 影響なし |

### 4.2 共通コンポーネント・ユーティリティへの影響
なし

### 4.3 API・DB スキーマの互換性
- API: スキーマ変更なし、後方互換
- DB: カラム追加・削除なし、データ UPDATE のみ
- フロント: 変更なし

### 4.4 破壊的変更の有無
なし。DB のデータは「NULL → 値あり」になるだけで、`NOT NULL` 制約等は付け加えない（手動で意図的に NULL を入れたい運用が将来出てきた場合を考慮）。

---

## 5. 設計判断の根拠

### 5.1 なぜ A1 + A2 の両方を採用するか
- A1（DB クリーン化）だけだと、将来別の同期処理が追加された時に同じ事故（capacity 未設定で〇×△が出ない）が再発する余地がある
- A2（サマリー API フォールバック）だけだと、フロントで表示はされるが DB データは NULL のまま残り、編集画面で「14」と空白が混在して運用者を困惑させる
- A1 + A2 で根本対処と防御を二重化する。重複ロジックは LotteryService と PracticeSessionService の 2 箇所だけなので許容範囲

### 5.2 なぜ既存 45 件を全件 UPDATE するか
- わすらもち会の 4 月までの 11 件は手動作成で capacity=14 が入っている。同じ会場（`venue_id=3,4`、いずれも venue 既定 14）の 5 月以降 33 件も同じ運用と推定される
- 全件埋めれば、過去のカレンダーを見たときにも一貫して〇×△が出る
- `venue_id NULL` のレコードは 0 件で、漏れの心配がない

### 5.3 なぜ編集画面の updateSession は触らないか
- 手動作成フォームでは venue 選択時に既定 capacity が入る仕組みが既にあり、編集時にも capacity フィールドが見える
- updateSession に「未指定なら venue 既定値」を入れると、「ユーザーが意図的に capacity を NULL にしたい」ケースとの区別がつかなくなる
- 今回の不具合の影響範囲外であり、デグレ防止のため触らない

### 5.4 なぜ `practice_sessions.capacity NOT NULL` 制約を入れないか
- venue が解決できない（unmatched）ケースで capacity が NULL になる可能性が残る
- 「定員無制限」運用を将来想定する可能性がある（今は無いが、将来オープン練習等で）
- データの整合性ではなく、表示側で適切にフォールバックする設計のほうが柔軟
