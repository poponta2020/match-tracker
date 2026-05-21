---
status: completed
---
# 参加試合に応じたカレンダーイベント時刻 実装手順書

## 実装タスク

### タスク1: IcalCalendarFeedService の時刻決定ロジック改修
- [x] 完了
- **概要:** `buildIcsForParticipations` で session ごとに「match_number=null を含むか」を集計し、`buildEvent` で要件定義書 §3.2 のアルゴリズム（全レコードに match_number ありかつ会場スケジュールが1件以上存在する場合のみ、参加 match_number の min(start)〜max(end) を採用）に書き換える。
- **変更対象ファイル:**
  - [karuta-tracker/src/main/java/com/karuta/matchtracker/service/IcalCalendarFeedService.java](karuta-tracker/src/main/java/com/karuta/matchtracker/service/IcalCalendarFeedService.java) — `buildIcsForParticipations` に `sessionHasNullMatchNumber` Map の集計を追加し、`buildEvent` のシグネチャに `boolean allHaveMatchNumber` を追加して時刻決定ロジックを置換
- **依存タスク:** なし
- **対応Issue:** #675

### タスク2: IcalCalendarFeedServiceTest にユニットテスト追加
- [x] 完了
- **概要:** 要件定義書 §4.4 の7ケースをカバーするテストを追加する。既存テストで新仕様に合致しなくなったケースは期待値を修正する。
- **変更対象ファイル:**
  - [karuta-tracker/src/test/java/com/karuta/matchtracker/service/IcalCalendarFeedServiceTest.java](karuta-tracker/src/test/java/com/karuta/matchtracker/service/IcalCalendarFeedServiceTest.java) — 以下のケースを追加：
    1. 全参加レコードに match_number あり・スケジュール完備 → スケジュール時刻採用
    2. 部分参加（試合3〜6 / 会場は試合1〜6 完備） → 試合3 start〜試合6 end
    3. 全試合参加 → 試合1 start〜試合最終 end（session 全体と一致）
    4. match_number あり/null 混在 → session 全体時刻
    5. 会場 VenueMatchSchedule 未登録 → session 全体時刻
    6. 参加 match_number の一部だけスケジュール登録あり → 登録分の min/max
    7. session.startTime/endTime も null・スケジュール不在 → 全日イベント
- **依存タスク:** タスク1
- **対応Issue:** #676

### タスク3: ドキュメント更新
- [ ] 完了
- **概要:** カレンダー購読仕様の説明を新仕様に合わせて更新する。
- **変更対象ファイル:**
  - [docs/SPECIFICATION.md](docs/SPECIFICATION.md) — カレンダー購読のイベント時刻仕様を「参加試合に応じた時間」に更新
  - [docs/DESIGN.md](docs/DESIGN.md) — iCal フィード生成ロジック（時刻決定）の説明を更新
- **依存タスク:** タスク1
- **対応Issue:** #677

## 実装順序
1. タスク1: IcalCalendarFeedService の時刻決定ロジック改修（依存なし）
2. タスク2: IcalCalendarFeedServiceTest にユニットテスト追加（タスク1に依存）
3. タスク3: ドキュメント更新（タスク1に依存／タスク2と並行可）
