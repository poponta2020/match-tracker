---
name: ship-pr1140-adjacent-room-check-lightweight
description: 隣室確認軽量化(かでる cron スロットル＋東区民の自動確認廃止・会場拡張の手動化)を出荷（PR #1140、親#1133 子#1134-1136）
type: project
category: ship
tags: [scheduler, cron, github-actions, adjacent-room, kaderu, higashi, practice-sessions, notification, venue-expansion]
---

# PR #1140 出荷記録 — adjacent-room-check-lightweight

- PR: https://github.com/poponta2020/match-tracker/pull/1140
- Issue: 親 #1133 / 子 #1134（A スロットル）#1135（B BE）#1136（B FE）— PR 本文の closing keyword で自動クローズ
- 要件書: `docs/features/adjacent-room-check-lightweight/requirements.md`
- 背景: 会場連携軽量化3機能の①（設計は harness memory [[feature_adjacent_room_check_lightweight]]）。隣室空き確認まわりを間引き・整理
- 出荷日: 2026-07-20

## 何を変えたか（3タスク直列・全 main 直実装）

- **A スロットル（#1134）**: `AdjacentRoomNotificationScheduler` cron `0 */30 * * * *`→`0 0 0,6-23 * * *`（Asia/Tokyo）、`scrape-kaderu.yml` `*/30 * * * *`→`0 0-15,21-23 * * *`（UTC）。**JST 1〜5時台スキップ = UTC 16〜20スキップ**、両者とも毎時0分・1日19回。cron 検証は新 `AdjacentRoomScheduleCronTest`（`@Scheduled` 値を reflection 取得→`CronExpression.parse`→JST発火時集合を assert）
- **B BE（#1135）**: `AdjacentRoomConfig` に `ADJACENT_NOTIFICATION_TARGET_VENUE_IDS={3,11,4,8}`＋`isAdjacentNotificationTarget`／`isManualExpansionVenue`。**`ADJACENT_CHECK_TARGET`（venue 6 含む）は不変**。scheduler フィルタを notification-target に切替（東🌸を自動通知除外）。`expandVenue` は `isManualExpansionVenue`（東🌸のみ）で `reservationConfirmedAt`＋`expandable` 検証をスキップ（かでる経路は if でくくり byte-unchanged）。`AdjacentRoomStatusDto.manualExpansion` 追加。`scrape-higashi-availability.yml` の `schedule:` 削除
- **B FE（#1136）**: `PracticeList.jsx` 隣室ブロックで `manualExpansion` を**最上位分岐**（venue 6 は expandable=false で既存ゲート素通り→無描画を回避）。東🌸は status チップ・空きゲートを外し「会場を拡張（東全室に）」常時表示→「かっこうを予約済みですか？」確認。`handleExpandVenue` に manualExpansion 引数

## 設計の肝

- **3概念の分離**: check-target（不変=DTO・拡張先マッピング供給源）／notification-target（かでる4室=自動通知）／manual-expansion（東🌸のみ=空き検証なし手動拡張）。venue 6 を check-target に残すことで確認ダイアログの拡張先名・定員供給を壊さず自動通知だけ外す最小侵襲
- **手動拡張がスクレイプ非依存**: 「予約してから押す」運用に合致し、「予約すると次回スクレイプで×→expandable=false→拡張ブロック」の罠を解消
- **manualExpansion 配線**: `getAdjacentRoomAvailability` で設定→`PracticeSessionService`（getById/summaries）が `setAdjacentRoomStatus` で session DTO に載せて FE 到達（着手前 grep 確認済み）

## auto-review（Codex CLI・effort high）

**1R pass・偽陽性ゼロ**（103,095/500,000 tok）。意図設計8項目を先回り明記＋中立cwd+stdin踏襲（[[ship_pr1127_line_chat_auto_relogin]] / [[ship_pr1132_line_credential_encryption]] の系譜）。詳細は harness memory `impl_adjacent_room_check_lightweight`。

## テスト・AC

- BE 全テスト green（`./gradlew test`。cron/config/service/scheduler の新規＋既存回帰、TestContainers 統合含む）。FE 64ファイル754テスト green（`AdjacentRoomFlow.test.jsx` に東🌸手動拡張ケース追加）。lint 0 error
- **Mockito strict-stub の罠**: venue6 通知テスト反転時、フィルタで即 drop→`findByDateRange` 以外の stub が全て `UnnecessaryStubbingException`→stub を findByDateRange のみに絞った
- DoD: A1=SKIP（CI委譲）・A2 lint=PASS・A3=SKIP・B1 CI=PASS（pending マージ）・C1 レビュー=PASS（r1 pass）・D1 memory=PASS・D2 docs=PASS
- DB マイグレーション不要（スキーマ変更なし）

## 出荷後の要注意（manual/verify 事項）

- **AC-A2/B2（GHA cron 実挙動）**: マージ後、`scrape-kaderu.yml` が新 cron で発火し `workflow_dispatch` が残ること／`scrape-higashi-availability.yml` が定期発火しない（`workflow_dispatch` のみ）ことを Actions 実行履歴で確認
- **AC-B3(FE) verify**: 東🌸(venue 6) セッションを管理者で開く→隣室 status チップが出ず「会場を拡張（東全室に）」が空き状況に関わらず表示→押下で確認ダイアログ→拡張後に定員18・キャンセル待ち繰上げ
- 会場連携軽量化3機能: ①=本 PR、②取込通知宛先拡大 slug `kaderu-sync-notify-admins`、③予約 sync 手動化=[[ship_pr1139_venue_reservation_sync_manual_only]] 出荷済
