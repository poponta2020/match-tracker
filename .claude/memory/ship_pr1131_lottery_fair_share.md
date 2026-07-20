---
name: ship-pr1131-lottery-fair-share
description: 抽選を2ルール方式（その日の一巡保証＋直近30日の重み付き抽選）へ全面変更し出荷（PR #1131、親 #1119・子 #1120-1125）
type: project
category: ship
tags: [lottery, fairness, percentile, rolling-window, org-scope, determinism, auto-review]
---

# PR #1131 出荷記録 — 抽選の公平化（一巡保証＋直近30日の重み付き抽選）

- PR: https://github.com/poponta2020/match-tracker/pull/1131
- 親 Issue: #1119（子 #1120-1125。PR 本文の closing keyword で自動クローズ）
- 要件書: `docs/features/lottery-fair-share-rotation/requirements.md`
- 出荷日: 2026-07-20
- **前セッションの worktree 実装を resume**（タスク1/3/4 が実装済・BEテスト green・未コミットの状態を、委譲成果物と同じ厳密さで受け入れレビュー→コミット）

## 何を変えたか

`LotteryService.processMatch` の cascade（連鎖落選）・二値救済（monthlyLosers）・30%一般枠・キャンセル待ち番号の前試合引き継ぎを撤廃し、**2層構造**へ。**DB スキーマ変更なし・migration 不要**（設定は既存 KV `system_settings`）。

- **ルール1（確定）**: `todayTaken` 最小の候補から座らせる → 1日に全落ちが出ない（一巡保証）
- **ルール2（確率・同点時のみ）**: `recentTaken`（窓 `[d-30, d)`）が少ないほど当たりやすい重み付き抽選。重み `1/(min(recent,cap)+1)`、cap は候補の p パーセンタイル（新設定 `lottery_weight_cap_percentile`・デフォルト30・nearest-rank＋最小値一致時+1ガード）
- `LotteryFairShareTracker`（純ロジック新設）を execute/preview/reExecute の3経路で同一ベースライン・単一シード共有で使い、preview=確定の再現性（AC-R3）を担保
- 集計は暦月リセットから直近30日ローリングへ。管理者指定優先は最上位維持
- FE: SystemSettings.jsx から「一般枠の最低保証割合」カードを削除し、パーセンタイルカード＋「抽選の仕組み」説明を追加
- 死んだ `findMonthlyLoserPlayerIds` クエリ・旧 `getLotteryNormalReservePercent` を撤去

## auto-review（Codex CLI・effort high）

**R1-R3 で pass 収束（R3 = blockers 0 / should_fix 0、nit 1 のみ）。累計 191,375 tok。** 中立cwd＋stdin＋スキル禁止ガードで codex 偽pass回避。

- **R1**: FE should_fix 2件（本物）。パーセンタイル保存値の読取未検証で画面表示と実効値が乖離／説明文言のフラット化割合が逆（p=30 は実際は約上位70%フラット化）
- **R2**: blocker 1＋should_fix 1。**blocker を discriminating fact で分割** — 「同一日複数セッションで todayTaken 混入」は UNIQUE制約 `uk_session_date_organization`（同一団体同一日は不可）で**偽陽性**、「org横断 recentTaken」は org=null（SUPER_ADMIN全団体一括）パス限定で実在 → **封じ込め修正 `buildTrackersByOrg`**（org=null時に団体別トラッカーを構築。tracker本体・AC本体テストは不変・AC-R3維持）。should_fix=`parseInt` が "45abc"→45 と緩い→BE `Integer.parseInt` 準拠の厳密整数判定へ
- **R3**: PASS。前ラウンド確定事実をプロンプトに追記した結果 R2論点は再燃せず。nit=実装手順書のロード範囲表記のみ、修正して収束

## テスト・AC

- BE: 抽選関連テスト green（LotteryServiceTest に AC-1/2/6/7/R3＋org=null分離テスト、LotteryFairShareTrackerTest、SystemSettingServiceTest）。全 AC auto-test
- FE: `npm run lint` 0 err、SystemSettings 18テスト green（AC-9/10/13/14）
- **未検証**: 実動作確認（verify）は標準フロー外。抽選管理画面での実プレビュー/確定の顔ぶれ変化・パーセンタイル設定の反映は本番で目視推奨

## 教訓

- **blocker も鵜呑みにせず discriminating fact を探す**: unique制約1つで claim2 を偽陽性・claim1 を到達可能パスに限定。severity は blocker でなく should_fix（多団体所属選手の公平性ニュアンス、データ破壊ではない）
- **封じ込め＞シグネチャ改変**: 「同一団体同一日は不可」の不変条件があるので tracker全体を (orgId,playerId) キーに書き換える重い修正は不要。分割点は orchestration層（`buildTrackersByOrg`）だけ → 核心ロジックとAC本体テストを触らずに済む
- **resume の green 鮮度確認法**: 変更ソースの mtime がテスト完了時刻より前なら、その green は今からコミットする木を反映＝再実行不要
