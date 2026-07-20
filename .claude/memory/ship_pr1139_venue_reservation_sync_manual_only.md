---
name: ship-pr1139-venue-reservation-sync-manual-only
description: かでる/東区民の予約→練習日sync定期cron(*/30)停止＋手動WFに東区民ステップ追加で手動ボタンのみ化を出荷（PR #1139、親#1138）
type: project
category: ship
tags: [github-actions, workflow, kaderu, higashi, reservation-sync, practice-sessions, cron, manual-trigger]
---

# PR #1139 出荷記録 — venue-reservation-sync-manual-only

- PR: https://github.com/poponta2020/match-tracker/pull/1139
- 親 Issue: #1138（PR 本文の closing keyword で自動クローズ）
- 要件書: `docs/features/venue-reservation-sync-manual-only/requirements.md`
- 背景: 会場連携軽量化3機能の③（設計は harness memory [[feature_venue_sync_lightweight_plan]]）。「常時反映」の正体＝予約 sync の30分毎 cron 2本を止め、既に実装済みの手動ボタン（練習日登録画面）に一本化する
- 出荷日: 2026-07-20

## 何を変えたか（純 GHA workflow YAML + docs・Java/JS 無改変）

- `.github/workflows/sync-kaderu-reservations.yml` / `sync-higashi-reservations.yml`: `schedule:`（`*/30 * * * *`）を削除、`workflow_dispatch` は保守/フォールバック用に維持
- `.github/workflows/sync-kaderu-reservations-manual.yml`: 既存 wasura ステップの後ろに東区民ステップを追加。`if: inputs.org == 'hokudai'`（東区民は北大会場ゆえ hokudai のみ）、`node sync-higashi-reservations.js --months 2`、env=`SAPPORO_COMMUNITY_USER_ID/PASSWORD`＋`DATABASE_URL: KADERU_DATABASE_URL`。→ hokudai 押下でかでる＋東区民を1ボタン取込
- docs: `docs/spec/venue-reservations.md` の実行方式表・フロー図・手動トリガー節を「定期→手動のみ」に in-place 更新、`docs/features/INDEX.md` にエントリ追記

## 設計の肝

- **東区民ステップに `always()` は意図的に付けない**。GHA は bare `if:` を `success() && <expr>` に暗黙ラップ→直前の hokudai(かでる) ステップ失敗時は東区民 step が skip される。これは「失敗通知が出たのに一部だけ反映済み」を避ける正しい挙動（always() だと kaderu 失敗＋higashi 成功でも run 全体は失敗扱いで失敗通知が誤解を生む）。意図を YAML コメントに明記。
- **cron 完全停止・セーフティネットなし**（要件§7・ユーザー明示要望「押した時だけ」）。押し忘れが問題化したら後から日1回 cron を足せる。
- **concurrency グループ `kaderu-reservation-sync` は不変**（Non-goal）。東区民 step は同グループ内で走る。直接 `sync-higashi-reservations.yml`（別グループ）と並走しうるが `sync-higashi-reservations.js` は idempotent upsert で二重起動安全。

## auto-review（Codex CLI・effort medium）

**2R で R2 pass 収束**（累計 約50k/500k tok、偽陽性ゼロ）。R1 should_fix 1件＝**自 docs 内の「唯一の起点」矛盾**（手動ボタンを唯一の起点と書いたが各 workflow の `workflow_dispatch` を維持＝実行方式表にも「直接手動起動」記載）→「主経路」に緩め直接 dispatch が保守用に残る旨を明記して解消。R2 の nit（先在「自動登録フロー」見出し）は scope-creep として棄却。中立cwd+stdin+意図設計7項目先回りで [[ship_pr1127_line_chat_auto_relogin]] を踏襲。詳細は harness memory `auto_review_round_pr1139`。

## テスト・AC

- 純 GHA YAML=自動テスト対象 surface なし（profile の test scope は karuta-tracker/database/UI のみ、`.github/`・`docs/` 非該当）。AC は manual/verify 中心（要件の性質上不可避）。
- AC-1/2/3/5 は差分 inspection で充足。**AC-4（hokudai/wasura の実ボタン押下 verify）はデプロイ後の実 Actions 起動が必要でセッション内実行不可＝ユーザー verify 事項**。
- DoD: A1=PASS（CI委譲）・A2/A3=SKIP・B1 CI=PASS・C1 レビュー=PASS（r2 pass）・D1 memory=PASS・D2 docs=PASS。

## 出荷後の要注意

- **AC-4 の実起動 verify が残る**: hokudai 押下→かでる＋東区民の run が走り練習日が取り込まれること／wasura 押下→東区民ステップが skip されることを Actions ログで確認。定期 cron が発火しないことも Actions 実行履歴で確認（AC-5）。
- 予約→練習日 sync は**押すまで反映されない**運用に変わった（仕様どおり）。運営者への周知が必要なら別途。
- 会場連携軽量化の残り2機能（①隣室確認スロットル slug `adjacent-room-check-lightweight`／②取込通知宛先拡大 slug `kaderu-sync-notify-admins`）は別 PR。②は別セッションが並行実装中。
