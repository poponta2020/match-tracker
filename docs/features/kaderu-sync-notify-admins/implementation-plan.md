---
status: completed
---
# かでる取り込み通知の宛先拡大 実装手順書

純 BE・単一タスク。

## 実装タスク

### タスク1: 完了/失敗通知を対象団体ADMIN＋全SUPER_ADMINへ拡大
- [x] 完了
- **目的:** `sendKaderuSyncCompletedNotification`/`sendKaderuSyncFailedNotification` の宛先を押下者本人→対象団体ADMIN全員＋全SUPER_ADMIN（重複排除）に拡大。本文・タイプは不変。
- **対応AC:** AC-1〜AC-5
- **主な変更領域:**
  - `service/LineNotificationService.java`: 当該2メソッドのシグネチャに `Long organizationId` を追加。受信者を `findByRoleAndActive(SUPER_ADMIN)` ＋ `findByRoleAndAdminOrganizationIdAndActive(ADMIN, organizationId)` で構築→`distinct`→各人に `sendToPlayer(id, type, message)`。本文構築は現行維持
  - `service/KaderuSyncTriggerService.java`: `finalizeCompleted`/`finalizeFailed` の呼び出しに `event.getOrganizationId()` を渡す（2箇所）
- **依存タスク:** なし
- **対応Issue:** #1137
- **必要なテスト:**
  - `LineNotificationServiceTest`（または新 `LineNotificationServiceKaderuSyncSendTest`）: playerRepository の2 finder をモックし、完了/失敗それぞれで対象団体ADMIN＋SUPER_ADMIN全員に `sendToPlayer` が呼ばれること・重複排除・本文/タイプ不変・他団体ADMINに送られないこと（AC-1〜4）を検証
  - 既存の KaderuSync 通知テスト（単一宛先を前提していれば）を新仕様へ改修
- **完了条件:** 上記テスト green・既存テスト green・build green

## 実装順序（Wave）
- Wave 1: タスク1（単一）
