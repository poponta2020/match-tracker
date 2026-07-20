---
name: ship-pr1141-kaderu-sync-notify-admins
description: かでる取り込み通知の宛先を押下者本人→対象団体ADMIN全員＋全SUPER_ADMINへ拡大を出荷（PR #1141、親#1137）
type: project
category: ship
tags: [line-notification, kaderu, venue-sync, admin-notification, async, backend]
---

# PR #1141 出荷記録 — kaderu-sync-notify-admins

- PR: https://github.com/poponta2020/match-tracker/pull/1141
- Issue: 親 #1137 — PR 本文の closing keyword で自動クローズ
- 要件書: `docs/features/kaderu-sync-notify-admins/requirements.md`
- 背景: 会場連携軽量化3機能の②（取込通知宛先拡大）。設計は harness memory [[impl_kaderu_sync_notify_admins]]
- 出荷日: 2026-07-20

## 何を変えたか（純 BE・単一タスク・main 直実装）

- **`service/LineNotificationService.java`**: `sendKaderuSyncCompletedNotification`/`sendKaderuSyncFailedNotification` のシグネチャに `Long organizationId` を第2引数追加（`triggeredByPlayerId` はログ用に残置）。宛先を「押下者本人1人」→「対象団体の ACTIVE ADMIN 全員 ＋ ACTIVE SUPER_ADMIN 全員（重複排除）」へ拡大。受信者収集は新規 private `resolveKaderuSyncRecipientIds(orgId)`（`findByRoleAndActive(SUPER_ADMIN)` ＋ `findByRoleAndAdminOrganizationIdAndActive(ADMIN, orgId)` を Stream.concat→`map(Player::getId)`→`distinct`）。既存の隣室スケジューラ `getAdminRecipientsForSession` と同型だが session でなく orgId 直渡し。**本文・通知タイプ不変**（AC-3）
- **`service/KaderuSyncTriggerService.java`**: `finalizeCompleted`/`finalizeFailed` の通知呼び出しに `event.getOrganizationId()` を追加（2箇所）
- test: 新規 `LineNotificationServiceKaderuSyncSendTest`（6ケース）＋ 既存 `KaderuSyncTriggerServiceTest` を4引数化
- docs: `docs/spec/venue-reservations.md` の手動トリガーフローの LINE 送信先を更新

## 設計の肝

- **受信者ごと try/catch（要件§6「1人の送信失敗が他に波及しない」）**。当初のメソッド全体1本 try/catch案は advisor が指摘＝1人目の例外で残り全員が未送信になるバグ。ループ内 try/catch＋外側 try/catch（@Async 安全）の二段構え。専用テスト（recipient=100 doThrow→200 は送信継続）で担保
- **spy 自己呼び出しは有効**: `this.sendToPlayer(...)` は Mockito `spy()` が subclass dispatch で intercept（Spring AOP self-invocation と違い効く）。`doReturn(SKIPPED).when(spy).sendToPlayer(...)` でチャネル配線を省いて受信者集合だけ検証。ハーネスは `LineNotificationServiceCardDivisionSendTest` を踏襲
- dedup(distinct) は防御的。SUPER_ADMIN/ADMIN は役割排他で実運用は重複しない＝dedup テストは人工ケース
- 新受信者は全員 ADMIN+ なので既存の ADMIN チャネルルーティング（ADMIN_ プレフィックス）がそのまま効く。未連携 ADMIN は `sendToPlayer` が SKIPPED

## auto-review（Codex CLI・effort high）

**1R pass・偽陽性ゼロ**（116,674/500,000 tok）。意図設計7項目を先回り明記＋中立cwd+stdin踏襲（[[ship_pr1140_adjacent_room_check_lightweight]] / [[ship_pr1127_line_chat_auto_relogin]] の系譜）。nit 1件＝requirements.md §7 の `[[memory-slug]]` 記法をリポジトリ内 doc 読者向けに平文化（pass 収束前に /fix、再レビューなし）。

## テスト・AC

- backend 全テスト green（`./gradlew test`）。`LineNotificationServiceKaderuSyncSendTest` 6ケース green（宛先集合・本文/タイプ不変・他団体除外・重複排除・例外分離・受信者0件）
- DoD: A1=SKIP（CI委譲）・A2 lint=SKIP（FE差分なし）・A3=SKIP・B1 CI=PASS（pending マージ）・C1 レビュー=PASS（r1 pass）・D1 memory=PASS・D2 docs=PASS
- DB マイグレーション不要（スキーマ変更なし）
- **AC は全 auto-test で担保**。実機での LINE 実配信確認（管理者複数人へ届く・本文不変）はデプロイ後のユーザー確認事項

## 出荷過程のインシデント（記録）

- 実装編集を worktree でなくメイン作業ディレクトリに誤って入れ、worktree の `./gradlew test` が旧コードを green にする偽検証が発生。メイン→WT へ移送し実変更に対して再テスト green を確認して復旧。教訓は harness memory [[reference_worktree_edit_wrong_dir_stale_tests]]

## 関連

会場連携軽量化3機能: ①隣室確認軽量化=[[ship_pr1140_adjacent_room_check_lightweight]]、②本 PR、③予約 sync 手動化=[[ship_pr1139_venue_reservation_sync_manual_only]]。いずれも出荷済。
