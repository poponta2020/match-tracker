---
status: completed
design_required: false
completed_sections: [delta動機, 変更後挙動, 変わらないもの, Acceptance Criteria, 技術制約]
next_section: 技術計画
---
# かでる取り込み通知の宛先拡大 要件定義書

## 1. 概要
かでる予約取り込み（手動同期）の**完了・失敗 LINE 通知**を、現在の「押下者本人のみ」から「**対象団体の全 ADMIN ＋ 全 SUPER_ADMIN**」へ拡大する改修。チャネル（LINE）・メッセージ本文は変えない。純 BE。

## 2. 背景・動機
- 現状 `KaderuSyncTriggerService.finalizeCompleted/finalizeFailed` は `sendKaderuSyncCompletedNotification(triggeredByPlayerId, …)` を呼び、通知は**押した本人1人だけ**に届く。
- 取り込み結果は運営全体で共有したい。管理者・スーパー管理者なら誰でも把握できるようにする。

## 3. 変更後の挙動（delta）
- `sendKaderuSyncCompletedNotification` / `sendKaderuSyncFailedNotification` の宛先を、**対象団体の ACTIVE な ADMIN 全員 ＋ ACTIVE な SUPER_ADMIN 全員**（重複排除）に変更。各人へ LINE 送信。
- 受信者集合は既存の隣室スケジューラ／[LineNotificationService.java:802-803](../../../karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java) と同じパターン（`playerRepository.findByRoleAndActive(SUPER_ADMIN)` ＋ `findByRoleAndAdminOrganizationIdAndActive(ADMIN, orgId)`）で構築。
- メッセージ本文（「Kaderu予約取り込みが完了/失敗しました（団体: X）／結果 or 理由」）は**不変**。
- LINE 未連携の受信者は `sendToPlayer` が SKIPPED（no-op）。preference は従来どおりチェックしない（ADMIN 運用通知）。

## 4. Acceptance Criteria

| ID | 条件 | 検証手段 |
|----|------|------|
| AC-1 | 完了通知が「対象団体の ACTIVE ADMIN 全員 ＋ ACTIVE SUPER_ADMIN 全員」へ送られる（同一人物は1回・重複排除） | auto-test |
| AC-2 | 失敗通知も同じ宛先集合へ送られる | auto-test |
| AC-3 | メッセージ本文（団体コード・結果/理由行）と通知タイプ（`ADMIN_KADERU_SYNC_COMPLETED/FAILED`）が現行と不変 | auto-test |
| AC-4 | 対象団体に紐づかない他団体 ADMIN には送られない（SUPER_ADMIN は全員・org ADMIN は当該 org のみ） | auto-test |
| AC-5 | 既存テスト・build がすべて green | auto-test |

## 5. Non-goals
- 通知チャネルの変更（LINE のまま。アプリ内通知＋Web Push 化はしない）
- メッセージ文言・フォーマットの変更
- 隣室通知や他の LINE 通知の宛先変更
- サマリー内容の変更（取り込み件数の集計ロジックは触らない）

## 6. 技術的制約・契約
- `sendKaderuSyncCompletedNotification` / `sendKaderuSyncFailedNotification` は現状 `organizationCode`（本文用）を受け取るが、受信者解決には `organizationId` が要る。**シグネチャに `organizationId` を追加**し、呼び出し元 `finalizeCompleted/finalizeFailed`（`event.getOrganizationId()` 保有）から渡す。呼び出し2箇所を更新。
- メソッドは `@Async`・例外飲み込み（現行踏襲。1人の送信失敗が他に波及しない）。
- 受信者0件（該当 ADMIN も SUPER_ADMIN も居ない）でも例外にせず no-op。
- 変更対象は `LineNotificationService` の当該2メソッドと `KaderuSyncTriggerService` の呼び出し2箇所のみ。

## 7. 設計判断の根拠
- **LINE 継続**: ADMIN_ 系操作通知は既に LINE 運用で一貫（`ADMIN_` プレフィックスによるチャネルルーティングの経緯より）。チャネル変更は範囲が広がるので避ける。
- **受信者パターン流用**: 既存の SUPER_ADMIN＋org ADMIN 収集パターンをそのまま使い、独自ロジックを増やさない。
