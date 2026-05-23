---
status: completed
---
# 練習カレンダー 試合別ステータスグリッド 実装手順書

## 実装タスク

### タスク1: PracticeSessionDto の置き換え（capacityStatus → matchCapacityStatuses）
- [x] 完了
- **概要:** `PracticeSessionDto` から既存の `capacityStatus: CapacityStatus` フィールドを削除し、`matchCapacityStatuses: List<CapacityStatus>` を追加する。内部 enum `CapacityStatus`（`AVAILABLE` / `NEARLY_FULL` / `FULL`）は値そのままで維持。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/PracticeSessionDto.java` — `capacityStatus` フィールド削除、`matchCapacityStatuses: List<CapacityStatus>` フィールド追加。`@Builder` の都合上、import に `java.util.List` 追加（既存）。
- **依存タスク:** なし
- **対応Issue:** #753

### タスク2: PracticeSessionService の試合別ステータス算出ロジック追加
- [x] 完了
- **概要:** `findSessionSummariesByYearMonth` 内の既存 `capacityStatus` 算出ロジックを、試合単位の `matchCapacityStatuses` 算出に書き換える。集計マップは既存の N+1 回避パターン（`findBySessionIdIn`）を踏襲。
- **判定ロジック:**
  - `capacity == null || capacity <= 0` → `matchCapacityStatuses = null`
  - `totalMatches == null || totalMatches <= 0 || totalMatches >= 10` → `matchCapacityStatuses = null`
  - それ以外: 第 1 〜第 `totalMatches` 試合まで、各試合について `effectiveCount = COUNT(WON) + COUNT(PENDING) + COUNT(OFFERED)` を計算し、
    - `effectiveCount >= capacity` → `FULL`
    - `0 < (capacity - effectiveCount) <= 2` → `NEARLY_FULL`
    - それ以外 → `AVAILABLE`
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeSessionService.java` — `findSessionSummariesByYearMonth` 内の既存 `capacityStatus` 算出ブロックを置き換える。private helper メソッド（例: `computeMatchCapacityStatuses(session, participantsBySession)`）を切り出して可読性を確保。
- **依存タスク:** タスク1
- **対応Issue:** #754

### タスク3: バックエンドテストの書き換え
- [x] 完了
- **概要:** `PracticeSessionServiceTest` の既存 `capacityStatus` 関連テストを、`matchCapacityStatuses` 用に書き換える。
- **追加・更新するテストケース:**
  - capacity null / 0 / 負 → `matchCapacityStatuses = null`
  - totalMatches null / 0 / 負 / 10以上 → `matchCapacityStatuses = null`
  - 全試合で空き多数 → `[AVAILABLE, AVAILABLE, ...]`（長さ = totalMatches）
  - 一部試合で remaining ≤ 2 → 該当インデックスのみ `NEARLY_FULL`
  - 一部試合で remaining = 0 → 該当インデックスのみ `FULL`
  - `WAITLISTED` / `CANCELLED` / `DECLINED` / `WAITLIST_DECLINED` は effectiveCount に含めない
  - `PENDING` / `OFFERED` は effectiveCount に含める
  - 参加者ゼロの試合は `AVAILABLE`
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/PracticeSessionServiceTest.java` — 既存 capacity 関連テストを書き換え、上記ケースを網羅。
- **依存タスク:** タスク2
- **対応Issue:** #755

### タスク4: フロントエンド PracticeList.jsx の表示書き換え
- [x] 完了
- **概要:** 既存の「残わずか／満員」テキストバッジ描画ロジック（`PracticeList.jsx` 内、セル描画 IIFE）を削除し、試合別ステータスグリッド描画に置き換える。同時に参加状況背景色を一段薄める。
- **実装ポイント:**
  - 表示条件: `daySessions.length === 1` && `Array.isArray(matchCapacityStatuses)` && `matchCapacityStatuses.length >= 1`
  - グリッド: `grid grid-cols-3 gap-0.5 text-[9px] leading-none justify-items-center`
  - 記号: `○ / △ / ×`（`text-green-600` / `text-orange-500` / `text-red-600`、`font-bold`）
  - 既存の cellBg（`bg-[#dce5de]` / `bg-[#fef9ed]`）はそのまま使用、グリッドの可読性を確認しながら必要に応じて微調整
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/practice/PracticeList.jsx` — 既存 capacity badge 描画 IIFE をグリッド描画に書き換え。
- **依存タスク:** タスク1（API レスポンス形状変更後でないとフロントだけ先行マージ不可）
- **対応Issue:** #756

### タスク5: フロントエンドテストの置き換え
- [x] 完了
- **概要:** 既存 `PracticeList.capacityBadge.test.jsx` を新規 `PracticeList.matchStatusGrid.test.jsx` で置き換える。
- **追加するテストケース:**
  - 単一セッションで `matchCapacityStatuses = ['AVAILABLE', 'NEARLY_FULL', 'FULL']` → ○ △ × が左詰め描画
  - 7試合 (`[AVAILABLE×3, NEARLY_FULL×2, FULL×2]`) → 3+3+1 の3行レイアウト
  - 同日2セッション → グリッド非表示（記号要素が一つもないこと）
  - `matchCapacityStatuses = null` → グリッド非表示
  - capacity 未設定セッション（`matchCapacityStatuses = null`）→ グリッド非表示
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/practice/PracticeList.capacityBadge.test.jsx` — 削除
  - `karuta-tracker-ui/src/pages/practice/PracticeList.matchStatusGrid.test.jsx` — 新規作成（既存テストの mock データ構造を流用）
- **依存タスク:** タスク4
- **対応Issue:** #757

### タスク6: ドキュメント更新
- [x] 完了
- **概要:** `SPECIFICATION.md`, `SCREEN_LIST.md`, `DESIGN.md` を本機能仕様に書き換える。
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` — 「定員状況バッジ」項目を「試合別ステータスグリッド」に書き換え。記号と判定基準（remaining ≤ 2 → △ 等）を明記。
  - `docs/SCREEN_LIST.md` — 練習カレンダー画面の説明から「残わずか／満員」バッジの記述を削除し、試合別グリッド表示を追記。
  - `docs/DESIGN.md` — `PracticeSessionDto` の `capacityStatus` 説明を削除し、`matchCapacityStatuses: List<CapacityStatus>` を追記。
- **依存タスク:** タスク1〜5（実装完了後）
- **対応Issue:** #758

## 実装順序
1. **タスク1**: DTO 置き換え（依存なし）
2. **タスク2**: Service の試合別算出（タスク1に依存）
3. **タスク3**: バックエンドテスト（タスク2に依存）
4. **タスク4**: フロント表示書き換え（タスク1に依存。実際は2〜3完了後にマージするのが安全）
5. **タスク5**: フロントテスト（タスク4に依存）
6. **タスク6**: ドキュメント更新（全タスク完了後）

タスク1〜3 をバックエンド PR として、タスク4〜5 をフロント PR として分割しても良いが、API レスポンス形状の破壊的変更を含むため **1 PR にまとめてマージ** する方が安全（capacityStatus 削除と matchCapacityStatuses 利用を同時に切り替えるため）。タスク6は同 PR に含める。
