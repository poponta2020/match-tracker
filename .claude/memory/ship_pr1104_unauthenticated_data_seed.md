---
name: ship-pr1104-unauthenticated-data-seed
description: 未認証で全データを破壊できた POST /api/seed/all（DataSeedController）を削除して出荷（PR #1104、Issue #1103）
type: project
category: ship
tags: [security, bug-fix, data-seed, require-role]
---

# PR #1104 出荷記録 — 未認証の破壊的シードエンドポイント削除

- PR: https://github.com/poponta2020/match-tracker/pull/1104
- Issue: https://github.com/poponta2020/match-tracker/issues/1103（PR 本文の closing keyword で自動クローズ）
- 要件書: `docs/bugs/1103-unauthenticated-data-seed-endpoint/requirements.md`
- 出典: `docs/audits/third-party-club-deployment-assessment.md` の S-1
- 出荷日: 2026-07-18

## 何を直したか

`DataSeedController.java` をファイルごと削除。`@RequireRole` も `@Profile` も無く、`RoleCheckInterceptor:42-45`（アノテーション無し＝素通り）のため本番で未認証実行できた:

- `POST /api/seed/all` … 全対戦記録・全練習日程を `deleteAll()`、全選手のパスワードを `pppppppp` に上書き
- `POST /api/seed/venue-schedules` … 全会場の試合時間割を `deleteByVenueId()` で消去

## 最重要の判断: `@RequireRole` 付与では塞がらない

`RoleCheckInterceptor:50-67` はロールを **`X-User-Role` ヘッダーの自己申告**でしか判定せず署名・トークン検証が無い。よって `curl -H "X-User-Role: SUPER_ADMIN" -H "X-User-Id: 1"` で素通りする。**このプロトタイプ認証下では「アノテーションを付ける」はネット越しの脅威に対する防御にならない**（今後同種の判断で再利用できる原則）。攻撃面を消す唯一の手段は削除。回帰テストは詐称ヘッダー付きケースも検証している。

## 実測で分かったこと

- 修正前: `/api/seed/venue-schedules` は **200 で完走**、`/api/seed/all` は 500
- **`/api/seed/all` の 500 は安全を意味しない**。`deleteAll()` は try/catch の前段で完了し、コントローラに `@Transactional` が無いためロールバックもされない（削除だけ成立して 500）
- したがって**ステータスコードでは「削除済み」と「実行された上で失敗」を区別できない** → 回帰テストの主検証は「データが無傷であること」、ステータスは `>= 400` の補助検証に留めた

## テスト設計の教訓（再利用価値あり）

1. **AC の「404」は framework 挙動の推測で誤りだった**。このアプリは存在しないルートに **500** を返す（Spring の `NoResourceFoundException` が `GlobalExceptionHandler.java:244` の `@ExceptionHandler(Exception.class)` に捕まる）。**全ルートに影響する既存の欠陥**で本 PR とは無関係 → Non-goals として別対応に切り出し
2. **JPA の削除は永続化コンテキストに溜まってからフラッシュされる**。`@Transactional` な統合テストで jdbcTemplate（素の SQL）で件数を数えると「まだ消えていない」ように見え、**アサーションが空振りする**（実際に一度空振りし、teeth 検証で気付いた）。JPA リポジトリ経由で数えると auto-flush されて正しく検出できる
3. **回帰テストの実効性は必ず実測する**。コントローラを一時的に復活させて red になることを確認した（`venue-schedules` は `expected: 1 but was: 0` でデータ側アサーションが落ちる。`seed/all` は破壊処理の副作用で Hibernate セッションが汚染され red）

## レビュー

auto-review-loop **1ラウンドで verdict=pass**（effort=high、blockers 0 / should_fix 0 / nit 1、累計 26,584 tokens）。nit は要件書「影響範囲」に古い 404 記述が残っていた実在の矛盾で、修正して push（再レビューなし）。

## 付随事項

- **DB スキーマ変更なし＝本番 DB 適用は不要**。ただし**本番デプロイ完了までエンドポイントの露出は続く**
- 他コントローラの `@RequireRole` 欠落（LineUser / MatchComment / MentorRelationship / PlayerProfile / Venue。LineWebhook は署名検証前提で誤検知の可能性）は Non-goals とし、別タスクへ切り出し済み
- Testcontainers を使うため**ローカルでの統合テスト実行には Docker Desktop の起動が必要**
