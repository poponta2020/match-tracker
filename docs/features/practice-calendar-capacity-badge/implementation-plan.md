---
status: completed
---
# 練習カレンダー 定員状況バッジ 実装手順書

## 実装タスク

### タスク1: バックエンド DTO に capacityStatus フィールドと enum を追加
- [x] 完了
- **概要:** `PracticeSessionDto` に `capacityStatus` フィールドと内部 enum `CapacityStatus { AVAILABLE, NEARLY_FULL, FULL }` を追加する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/PracticeSessionDto.java` — `CapacityStatus` enum と `private CapacityStatus capacityStatus;` フィールドを追加（Lombok の `@Builder` 経由で組み立て可能にする）
- **依存タスク:** なし
- **対応Issue:** #733
- **完了条件:**
  - 既存フィールドの並びを壊さず追加されている
  - enum は DTO の内部 static class として定義する
  - 既存テストがコンパイル・パスする

### タスク2: バックエンド サービスで capacityStatus を集計
- [ ] 完了
- **概要:** `PracticeSessionService.findSessionSummariesByYearMonth` で月内全セッションの参加者を一括取得し、各セッションの `capacityStatus` を計算して DTO にセットする。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeSessionService.java` — 既存メソッド `findSessionSummariesByYearMonth(int, int, Long)` に集計処理を追加。`practiceParticipantRepository.findBySessionIdIn(sessionIds)` で参加者を一括取得し、セッション × 試合番号 × ステータス でカウント。`effectiveCount = WON + PENDING + OFFERED` を試合ごとに計算し、判定ルールに従い `AVAILABLE / NEARLY_FULL / FULL` を決定して DTO にセット
- **依存タスク:** タスク1
- **対応Issue:** #734
- **完了条件:**
  - `capacity == null || capacity <= 0` のセッションは `AVAILABLE`
  - `totalMatches == null || totalMatches <= 0` のセッションは `AVAILABLE`
  - 1〜`totalMatches` の各試合で `effectiveCount >= capacity` を判定
  - 全試合で達している → `FULL`、いずれか1試合で達している → `NEARLY_FULL`、それ以外 → `AVAILABLE`
  - N+1 クエリにならない（参加者は `findBySessionIdIn` で1回のみ取得）
  - `WAITLISTED / DECLINED / CANCELLED / WAITLIST_DECLINED` はカウントに含めない

### タスク3: バックエンド サービスのユニットテストを追加
- [ ] 完了
- **概要:** `findSessionSummariesByYearMonth` の `capacityStatus` 計算ロジックをカバーするテストを追加する。
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/PracticeSessionServiceTest.java`（既存ファイル）または同 service の専用テストクラス — 以下のケースを追加:
    - capacity null → AVAILABLE
    - capacity 0 → AVAILABLE
    - totalMatches null → AVAILABLE
    - 全試合で空き → AVAILABLE
    - 一部試合で WON+PENDING+OFFERED >= capacity → NEARLY_FULL
    - 全試合で WON+PENDING+OFFERED >= capacity → FULL
    - WAITLISTED / CANCELLED / DECLINED / WAITLIST_DECLINED は effectiveCount に含めない
    - OFFERED は effectiveCount に含める
- **依存タスク:** タスク2
- **対応Issue:** #735
- **完了条件:**
  - 上記7ケース以上のテストがパスする
  - 既存テストがリグレッションしていない

### タスク4: フロントエンド カレンダーセルにバッジ表示を追加
- [ ] 完了
- **概要:** `PracticeList.jsx` のカレンダー描画ロジックで `capacityStatus` バッジを会場名の下に表示する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/practice/PracticeList.jsx` — `<tbody>` のセル描画ロジック（既存の `daySessions.map(...)` 直下）に集約ロジック（`FULL > NEARLY_FULL > AVAILABLE`）とバッジ JSX を追加。
    - 集約ロジック: `daySessions` のうち `capacityStatus` の最も重い値を1つだけ採用
    - バッジ JSX:
      - `NEARLY_FULL` → `残わずか`（`bg-yellow-100 text-yellow-800` 系）
      - `FULL` → `満員`（`bg-red-100 text-red-700` 系）
      - `AVAILABLE` → 何も描画しない
    - スタイル: `text-[10px] leading-tight px-1.5 py-0.5 rounded font-medium inline-block` 程度
    - 配置: 既存の `daySessions.map` 内で会場名のループの **後** に1つだけ追加
- **依存タスク:** タスク2（API レスポンス確認のため、ローカルで動作確認が望ましい）
- **対応Issue:** #736
- **完了条件:**
  - カレンダー画面で実機 / dev server 上でバッジ表示が確認できる
  - 同日複数セッションで最も重い状態だけが表示される
  - capacityStatus が null や未知の値のときバッジが表示されない（防御的に AVAILABLE 扱い）

### タスク5: フロントエンド テストを追加
- [ ] 完了
- **概要:** `PracticeList.jsx` 関連テスト（`PracticeList.attendanceMode.test.jsx` または新規テストファイル）に capacityStatus バッジ表示のテストを追加する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/practice/PracticeList.attendanceMode.test.jsx` または新規 `PracticeList.capacityBadge.test.jsx` — 以下のケースを追加:
    - capacityStatus が AVAILABLE → バッジが描画されない
    - capacityStatus が NEARLY_FULL → 「残わずか」バッジが描画される
    - capacityStatus が FULL → 「満員」バッジが描画される
    - 同日に NEARLY_FULL と FULL の2セッション → 「満員」バッジのみが描画される
    - capacityStatus 未定義 → バッジ描画されない
- **依存タスク:** タスク4
- **対応Issue:** #737
- **完了条件:**
  - 上記5ケース以上のテストがパスする
  - `npm run lint` がパスする

### タスク6: ドキュメント更新
- [ ] 完了
- **概要:** `docs/SPECIFICATION.md`, `docs/SCREEN_LIST.md`, `docs/DESIGN.md` のカレンダー画面およびAPI設計の節に定員状況バッジを追記する。
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` — 練習カレンダー画面の仕様に「定員状況バッジ」の節を追加（判定ロジック・配色・配置）
  - `docs/SCREEN_LIST.md` — 練習カレンダー画面の説明にバッジ表示の項を追加
  - `docs/DESIGN.md` — サマリーAPI（`/practice-sessions/year-month/summary`）のレスポンスに `capacityStatus` を追記
- **依存タスク:** タスク1〜5（実装が確定してから記述する）
- **対応Issue:** #738
- **完了条件:**
  - 3ファイルすべてに該当節が追加されている
  - 実装と内容が一致している

## 実装順序
1. タスク1（依存なし）— DTO 拡張
2. タスク2（タスク1に依存）— サービスでの集計
3. タスク3（タスク2に依存）— バックエンドテスト
4. タスク4（タスク2に依存。タスク3と並行可）— フロントエンドのバッジ表示
5. タスク5（タスク4に依存）— フロントエンドテスト
6. タスク6（タスク1〜5に依存）— ドキュメント更新
