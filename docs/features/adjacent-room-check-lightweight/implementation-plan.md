---
status: completed
---
# 隣室確認の軽量化 実装手順書

前提: requirements.md completed / design-screen はユーザー指示でスキップ。
方針: **A（throttle）と B（東区民 rework）を分離**。3タスク直列（同一ファイル共有・DTO 依存のため並行不可）。A は単独 PR に切り出し可能。

## 概念の整理（AdjacentRoomConfig に集約）
- `ADJACENT_CHECK_TARGET_VENUE_IDS = {3,11,4,8,6}` … **変更しない**（DTO・拡張先マッピングの供給。`isAdjacentCheckTarget`）
- `ADJACENT_NOTIFICATION_TARGET_VENUE_IDS = {3,11,4,8}` … **新設**（かでる4室のみ。自動通知の対象。`isAdjacentNotificationTarget`）
- 手動拡張会場（東🌸）= `check-target \ notification-target` = `{6}` … `isManualExpansionVenue(v) = isAdjacentCheckTarget(v) && !isAdjacentNotificationTarget(v)`

## 実装タスク

### タスク1: かでる隣室確認スロットル（A）
- [ ] 完了
- **目的:** かでる空きスクレイプ＋隣室通知スケジューラを 30分毎 → 1時間毎＋JST夜間(1-5時)スキップに間引く。挙動は頻度のみ変更。
- **対応AC:** AC-A1, AC-A2, AC-R2（かでる通知ロジック不変の回帰）
- **主な変更領域:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/AdjacentRoomNotificationScheduler.java`: `@Scheduled` cron `"0 */30 * * * *"` → `"0 0 0,6-23 * * *"`（zone は Asia/Tokyo 据え置き）
  - `.github/workflows/scrape-kaderu.yml`: `cron: '*/30 * * * *'` → `cron: '0 0-15,21-23 * * *'`（`workflow_dispatch` 維持）
  - docs cadence 更新: `docs/features/adjacent-room-check/requirements.md`・`higashi-adjacent-room-check/requirements.md` の「30分間隔」記述を新頻度に、各 `## 変更履歴` に追記
- **依存タスク:** なし
- **対応Issue:** #1134
- **必要なテスト:** `AdjacentRoomNotificationSchedulerTest`（または新 `AdjacentRoomScheduleCronTest`）に、`checkCapacityAndNotify` の `@Scheduled` cron をリフレクションで取得→`org.springframework.scheduling.support.CronExpression.parse` で解釈し、Asia/Tokyo 基準で **1〜5時台に発火せず 0,6…23時に毎時発火**することを assert（AC-A1）。scrape-kaderu.yml は manual 確認（AC-A2）。
- **完了条件:** 新 cron テスト green・既存 `AdjacentRoomNotificationScheduler` 系テスト green・build green

### タスク2: 東区民の自動確認廃止＋会場拡張の手動化（B・BE）
- [ ] 完了
- **目的:** 東🌸(venue 6) を自動通知・空きスクレイプから外し、`expandVenue` を venue 6 のみスクレイプ非依存の手動拡張にする。かでる経路は不変。
- **対応AC:** AC-B1, AC-B2, AC-B3(BE), AC-B4, AC-R1（かでる拡張の回帰）, AC-R3
- **主な変更領域:**
  - `config/AdjacentRoomConfig.java`: `ADJACENT_NOTIFICATION_TARGET_VENUE_IDS`＋`isAdjacentNotificationTarget`＋`isManualExpansionVenue` を追加（`ADJACENT_CHECK_TARGET_VENUE_IDS` は不変）
  - `scheduler/AdjacentRoomNotificationScheduler.java`: フィルタ `isAdjacentCheckTarget` → `isAdjacentNotificationTarget`（venue 6 を自動通知から除外）
  - `service/AdjacentRoomService.java` `expandVenue`: `isManualExpansionVenue(venueId)` の場合は `reservationConfirmedAt` チェックと `expandable` チェックをスキップ（拡張先ID取得〜save〜キャンセル待ち繰上げの共有ロジックは温存）。かでる経路は byte-unchanged
  - `dto/AdjacentRoomStatusDto.java`: `manualExpansion` (boolean) を追加。`getAdjacentRoomAvailability` で `isManualExpansionVenue(venueId)` をセット（FE の分岐用。expandedVenueName/Capacity は従来どおり供給）
  - `.github/workflows/scrape-higashi-availability.yml`: `schedule:` ブロック削除（`workflow_dispatch` 維持）
  - docs: `docs/features/higashi-adjacent-room-check/requirements.md` に「自動確認廃止・拡張は手動化」を反映＋変更履歴
- **依存タスク:** タスク1（同一ファイル `AdjacentRoomNotificationScheduler.java` を触るため順序制約）
- **対応Issue:** #1135
- **必要なテスト:**
  - `AdjacentRoomConfigTest`: `isAdjacentNotificationTarget`（venue 6 は false, かでる4室 true）・`isManualExpansionVenue`（venue 6 true, かでる false）を追加
  - `AdjacentRoomServiceTest`: venue 6 で空き「不明」でも `expandVenue` が成功し定員更新＋繰上げが走るケースを追加（AC-B3/B4）。既存 `expandVenue_*`（かでる）は空き検証を要求し続けることを維持（AC-R1、`expandVenue_adjacentRoomUnknown` 等）。`getAdjacentRoomAvailability` の DTO に `manualExpansion` が入ることを assert
  - `AdjacentRoomNotificationScheduler{Test,IntegrationTest}`: venue 6 セッションが通知されないことを確認するケース追加／既存の venue 6 前提テストがあれば新仕様に改修（AC-B1）
- **完了条件:** 上記テスト green・既存 BE テスト green・build green

### タスク3: 東🌸拡張 UI の手動化（B・FE）
- [ ] 完了
- **目的:** `PracticeList.jsx` で東🌸(manualExpansion)セッションは隣室 status チップと空き依存ゲートを外し、管理者に「会場を拡張（東全室に）」を常時表示。押下で確認ダイアログ→`expandVenue`。かでるの隣室 UI は不変。
- **対応AC:** AC-B3(FE/verify)
- **主な変更領域:**
  - `karuta-tracker-ui/src/pages/practice/PracticeList.jsx`: 隣室ブロック（~896-949）で `adjacentRoomStatus.manualExpansion` を分岐。true なら status チップ非表示・`expandable` に依存せず `isSuperAdmin||isAdmin` で「会場を拡張（{expandedVenueName}に）」ボタン → `handleExpandVenue`。確認ダイアログ文言に「かっこうを予約済みですか？」を追加。false（かでる）は現状維持
- **依存タスク:** タスク2（DTO の `manualExpansion` フラグに依存）
- **対応Issue:** #1136
- **必要なテスト:** `AdjacentRoomFlow.test.jsx` に、venue 6（manualExpansion=true・空き不明）で拡張ボタンが出て `expandVenue` が呼ばれるケースを追加。かでる（manualExpansion=false）の既存分岐が不変であることを維持
- **完了条件:** FE テスト green・`npm run lint` 0 error・`npm run test` green

## 実装順序（Wave）
- Wave 1: タスク1
- Wave 2: タスク2（タスク1 と同一 scheduler ファイル → 直列）
- Wave 3: タスク3（タスク2 の DTO フラグに依存 → 直列）
- ※ 並行できるタスクなし。A（タスク1）は単独 PR に切り出し可能（risk profile が B と逆）。
