---
name: ship-pr1097-chat-reserve-send-time-floor
description: PR#1097 チャット予約送信の scheduledSendAt を10分境界にfloorする修正の出荷記録
type: ship
---

# ship PR #1097 — チャット予約の送信予定時刻を10分境界にfloor

- **PR**: [#1097](https://github.com/poponta2020/match-tracker/pull/1097) fix(line-chat-reserve): 送信予定時刻を10分境界にfloor(LINE OAM step=600スナップ対策)
- **日付**: 2026-07-18
- **親Issue**: #1084（`Refs` のみ・T8 VM観測が残るため未クローズ）

## 内容
チャット予約の `scheduledSendAt` が非10分境界（例 09:45）だと、LINE OAM の予約時刻入力（native `<input type="time" step="600">`）が値を無言で10分単位へスナップし（実測 09:45→09:50）、ワーカーの `verifyScheduledEntry` が意図時刻とバナー表示の不一致を検出して MANUAL_REVIEW_REQUIRED へ劣化する（サイレント誤送信は防げるが要確認扱いで配信品質が落ちる）。

- `CardDivisionScheduleResolver.resolveScheduledSendAt` の算出結果を10分境界へ **floor**（早める方向・遅らせない）。秒/ナノ秒も0化。
- floor は `resolveScheduledSendAt` のみ。push配信のウィンドウ判定（`CardDivisionBroadcastScheduler.isInBroadcastWindow`）は生の `resolveFirstMatchStartTime` を使い `resolveScheduledSendAt` を呼ばないため **push挙動は不変**。
- テスト新設 `CardDivisionScheduleResolverTest`（6ケース: :15/:45→floor、境界不変、8:00不変、秒0化、resolveFirstMatchStartTimeは生値=push退行ガード）。
- docs: `docs/spec/notifications.md`・`docs/features/line-chat-reserve-broadcast/requirements.md` §3.2 に floor 規則を追記。根拠は `phase2-dom-findings.md` §4/§10（main未追跡=PR外）。

## レビュー（auto-review-loop）
**1ラウンドで verdict=pass 収束**（effort=medium・Codex累計 24,965 tok / 上限500k）。blocker/should_fix/nit すべて0。Codex が push経路不変の設計意図と両解決経路（session.startTime / venue_match_schedules）のテストを good_point として確認。

## AC / 検証
- auto-test: `CardDivisionScheduleResolverTest`（新設）＋ `LineChatReservationServiceTest` / `CardDivisionBroadcastSchedulerTest` / `CardDivisionReminderSchedulerTest` すべて green。
- CI: test（Java CI）・Vercel すべて SUCCESS（mergeStateStatus=CLEAN）。
- DBマイグレーション: **不要**（スキーマ変更なし）。既存予約データ移行も不要（feature未稼働・live RESERVED行なし）。

## デプロイ影響
純ロジック修正。マージ後 Render 自動デプロイで予約スケジューラが floored な送信予定時刻を生成。push側は不変。設計判断の詳細は harness memory `impl_chat_reserve_send_time_floor`。
