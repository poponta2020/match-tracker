---
name: ship-pr1035-adjacent-room-rollback-only
description: 隣室通知スケジューラーの rollback-only ERROR（Issue #1034）を exists 事前チェック＋setRollbackOnly バックストップで修正し出荷
type: ship
---

# PR #1035: fix(adjacent-room) 通知済み段階の再実行で rollback-only エラーが出る問題を修正

- PR: https://github.com/poponta2020/match-tracker/pull/1035（Closes #1034）
- Requirements: docs/bugs/1034-adjacent-room-rollback-only/requirements.md
- コミット: 2cac2442（本修正＋回帰テスト）、e92f131c（レビュー対応）

## 根本原因
`AdjacentRoomNotificationScheduler#processSession` が重複通知防止を「同一トランザクション内で save の一意制約違反（DataIntegrityViolationException）を catch して握りつぶす」方式で実装していた。IDENTITY 採番のため save() 時点で INSERT が実行され、失敗時に Hibernate がトランザクションを rollback-only にマーク → catch して return 0 しても TransactionTemplate のコミットで UnexpectedRollbackException が発生し、残り人数が変わらない 30 分ごとの実行のたびに ERROR ログが出ていた（本番 2026-07-13、session 1000）。

## 修正
- `existsBySessionIdAndRemainingCount` による事前チェックでスキップ（隣室照会より前に置きホットパスのクエリも短絡）
- 一意制約 + save/flush + catch は並列競合（TOCTOU）バックストップとして残し、catch 時は `TransactionStatus#setRollbackOnly()` でローカル rollback-only を立て、コミット試行を回避（ローカル rollback-only はコミット処理で global より先に評価され silent rollback になる）
- REQUIRES_NEW 分離は不採用: マーカー insert と通知送信は同一トランザクションで不可分（f92976b6 の通知永久欠落 CRITICAL を再導入するため）

## 回帰テスト
- `AdjacentRoomNotificationSchedulerIntegrationTest`（TestContainers 実 PostgreSQL）: 通知済み段階の再実行・TOCTOU 競合（@MockitoSpyBean で事前チェックのみすり抜け）の両経路で ERROR ログが出ないことを実トランザクションで検証。修正前／setRollbackOnly 除去 mutation で本番と同一エラーで fail することを確認済み
- 教訓: モックの TransactionTemplate はコミット時の rollback-only 検査を再現できず、この種のバグを検出できない

## 横展開（別タスク切り出し済み）
- OrganizationService#ensurePlayerBelongsToOrganization — 同型の握りつぶし（タスクチップ化）
- DensukeWriteService#saveMemberMapping — 同型＋PG abort 状態で 25P02 → バッチ全滅の潜在バグ（タスクチップ化）
