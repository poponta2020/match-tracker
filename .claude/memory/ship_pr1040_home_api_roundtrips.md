---
name: ship_pr1040_home_api_roundtrips
description: PR#1040出荷（/api/home の参加率グループ構築で月間データを1回だけロード）。ローカル開発の /api/home 15〜24秒の主因だった最大6回のフル再計算を撤廃
type: ship
---

# PR #1040 出荷記録 — /api/home 参加率グループ構築の往復削減

- **PRタイトル**: perf(home): /api/home の参加率グループ構築で月間データを1回だけロードする
- **PR**: https://github.com/poponta2020/match-tracker/pull/1040
- **カテゴリ**: ship / quickfix（home-api-roundtrips）
- **出荷日**: 2026-07-14

## 修正したバグ（症状）

ローカル開発（自宅→Render オレゴンDB、RTT実測 ~170ms）で `/api/home` が 15〜24 秒かかり、ホーム画面遷移だけ極端に重い（`/api/players` は約1秒）。

## 根本原因

`HomeController` がグループ×(top3/myRate) ごとに `computeAllParticipationRates` を最大6回フル実行（2団体所属→3グループ×2）。そのたびに月間セッション・参加者全件（7月実測1,436行 ≈2.1s）・players全件（≈0.5s）を再ロード。`pg_stat_user_tables` 前後差分で1リクエスト≈140往復相当を実証。本番は同一リージョン（RTT ~1ms）のため顕在化せず、環境要因が支配的（ユーザー確認済み）。

## 変更内容

- `PracticeParticipantService.getParticipationGroups` 新設: 月間データ（sessions/participants/団体メンバー/players名）を1回だけロードし、全グループ（全体+各団体）の top3 と myRate をメモリ上で導出
- `buildParticipationRates` の names をパラメータ化（内部 `findAllActive` 撤去）
- `HomeController` は新メソッド1呼び出しに置換。孤児化した団体スコープのオーバーロード4本+private compute 2本を削除
- `PlayerOrganizationRepository.findByOrganizationIdIn` 追加（メンバー取得1クエリ化）
- テスト: 既存2件を新メソッド経由に書き換え + 複数団体グルーピング・「各リポジトリ1回だけロード」検証を追加
- `docs/spec/practice-sessions.md` の実装参照を更新
- レスポンス形状（HomeDto）不変・FE変更なし・DBスキーマ変更なし

## レビュー（auto-review-loop、1R pass のためラウンド記録なし）

- 1ラウンド pass（effort=high: 差分488行の規模ルール）、blockers/should_fix/nits=0、Codex tokens 59,210/500,000
- CI pending のままマージ（v0.9.0 方針: マージ前CI待ちなし）

## コミット

- ff4d71ae perf(home): /api/home の参加率グループ構築で月間データを1回だけロードする
