---
name: ship_pr1083
description: 札分けの全体LINE一斉配信(card-division-group-broadcast)の出荷記録 PR #1083
metadata:
  type: ship
---

# 出荷: 札分けの全体LINE一斉配信 — PR #1083

- **PR**: [#1083](https://github.com/poponta2020/match-tracker/pull/1083) — feat: 札分けの全体LINE一斉配信（card-division-group-broadcast）
- **親Issue**: #1074（子 #1075-1082）
- **実装**: T1-T7＋レビュー修正。BE(Testcontainers-Postgres統合+単体)・FE Vitest・lint 全green。CI green。
- **本番適用**: schema migration（新表2種・新カラム2種・idx_lbs_dedupe・idx_lbg_org_unique）は**適用済み**。
  bot 10体のGROUP転用seed（`database/seed_hokudai_broadcast_bots.sql`）は**デプロイ後の手動実行**（旧コードのenum非対応回避）。手順=`docs/features/card-division-group-broadcast/setup-runbook.md`。
- **auto-review-loop**: 7ラウンド（全high・累計~626kトークン）。実findング6件修正（R3 DISABLED再有効化・R5 unassign境界破りは実バグ）、偽陽性5件を事実確認で棄却（MySQL CI/月次リセット/会場曜日/起動失敗/sent_at NOT NULL＝@PrePersistで補完済のため違反不可）。詳細は session memory [[auto_review_round_pr1083]]。
- **DoD**: A1/A2/B1/D2 PASS。C1はCodex-highが大差分(4000行)にformal passを出さず needs_changes(偽陽性 blocker)継続のため、実findング完全解決を根拠に判断でship。
