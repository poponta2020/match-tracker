---
name: ship-pr1127-line-chat-auto-relogin
description: LINEチャット予約ワーカーの24h自動クリックスルー再ログイン＋30日SSO先回りアラートを出荷（PR #1127、親#1115子#1116-1118）
type: project
category: ship
tags: [line, chat-reserve, worker, relogin, sso, playwright, notification, fail-safe]
---

# PR #1127 出荷記録 — line-chat-auto-relogin

- PR: https://github.com/poponta2020/match-tracker/pull/1127
- 親 Issue: #1115（子 #1116-1118。PR 本文の closing keyword で自動クローズ）
- 要件書: `docs/features/line-chat-auto-relogin/requirements.md`
- 前提: [[ship_pr1096_line_chat_reserve_broadcast]] の運用不成立（24hセッション切れで初回自動配信失敗）を直す
- 出荷日: 2026-07-20

## 何を変えたか（worker + backend の2層・DB/スキーマ変更なし）

- **A. 24hセッション自己回復（worker）**: 認証壁を検出したサイクルでのみ、同一 browser context のメモリ保持SSO Cookie で「LINE account」→「Log in」の2クリック再ログインを1回試行し、成功したら当該タスクを1回リトライ（`OamChatPage.relogin()`）。
- **B. 30日SSO先回りアラート**: `__is_login_sso` 失効が閾値（`SSO_WARNING_THRESHOLD_DAYS`・既定3日）以内で管理者へ既存 `sendChatReserveAlert` をリレー（`POST /api/line-chat-worker/session-warning` → `warnSessionExpiring`・distinct org・状態は持たない）。30日SSOの自動延長は不可（実測 `SSO_ABSOLUTE`）＝約月1回の手動ログインは残る。
- **フェイルセーフ**: relogin が SUCCEEDED のときだけ1回リトライ（RESERVING claim 非再送）。SSO_EXPIRED/ERROR/無効時は元の `FAILED`＋既存フォールバックpush を維持（最悪でも今より悪化しない）。1サイクル最大1回・SSO警告は1日1回。
- **最重要バグ回避（advisor検出）**: `relogin()` 先頭で `editorMissingAfterOpen=false` をクリア（`openChat` と対称・AC-13）。transient wall 由来の再ログイン後、再run が stale フラグで誤フォールバックpush（課金）＋偽アラートを誘発するのを防ぐ。
- 新env2件を docker-compose の explicit `environment:` へ配線（未配線だとコンテナに届かない）。

## auto-review（Codex CLI・effort high）

**R1-R5 で pass 収束（R5 = blockers 0 / should_fix 0、累計約243k/500k）。偽陽性ゼロ＝全指摘が本物のフェイルセーフ穴。** 詳細は harness memory [[auto_review_round_pr1127]]。

- R1: SSO警告残日数 `floor`→`ceil`（窓が1日早く開く境界）
- R2: relogin 最終host帰着を権威化（AC-13同種の穴が2ボタン目に残存）／警告API `daysRemaining` null→400（欠落を「まもなく失効」に誤発報しない）
- R3 (blocker): 「LINE account」クリック後の遷移待ちレース（旧画面で「Log in」取り逃し→正常SSOで誤失敗）→ waitForNextReloginState
- R4 (blocker): 応答喪失時の通知ストーム（成功後throttle→5分毎再送）→ POST前に試行記録（AC-6）
- **偽陽性ゼロは「意図された設計8項目」をレビュープロンプトに先回り明記したのが効いた**（[[ship_pr1114_auth_tokenization]] と同じ）。codex は中立cwd＋stdin で実行（[[auto_review_round_pr1102]] の再帰偽pass 回避）。

## テスト・AC

- worker vitest 55 green（`classifyReloginOutcome` 純関数・relogin リトライ統合・SSO throttle/境界/失敗時）・tsc・lint 0／backend 全スイート green（`warnSessionExpiring` distinct org・controller null→400）
- **auto-test の AC-1〜10・13 は緑。AC-11/12・AC-5実push のみ OPEN**（実DOMクリックスルー・cookie 読みは全てモック越しで実挙動未検証）

## 出荷後の要注意（最重要）

- **merge = 非活性**。稼働中の旧ワーカーには無影響（`request` の text()化も worker 側変更）。新backend EP は新ワーカーが叩くまで inert。
- **VM redeploy で活性化＆その場で verify**: `cd ~/line-chat-worker && git pull → docker compose up -d --build（または restart）`（RUNBOOK準拠・kagetra 非巻き込みのため compose/project 明示）。ここで AC-11（chat.line.biz 帰着＋新 `__Host-chat-ses` 発行）・AC-12（SSO失効時のフォールバック）・AC-5（実push）を目視。
- 新env は `${VAR:-default}` 配線ゆえ VM の `.env` 改変不要。同名cookie複数ドメイン時 `.find` は先頭を拾う点を実読み時に確認。
