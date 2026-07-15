---
name: ship_pr1051_card_division_line_reminder
description: PR#1051出荷（札分け確認＆LINE通知、親Issue#1045/子#1046-1049）。札組テキストBE一元生成＋購読制デフォルトOFF＋3h前スケジューラ
type: ship
---

# PR #1051 出荷記録 — 札分け確認＆LINE通知（card-division-line-reminder）

- **PRタイトル**: feat: 札分け確認＆LINE通知（card-division-line-reminder）
- **PR**: https://github.com/poponta2020/match-tracker/pull/1051
- **カテゴリ**: ship（card-division-line-reminder）
- **出荷日**: 2026-07-15
- **クローズIssue**: #1045（親）・#1046・#1047・#1048・#1049（PR本文 closing keyword でマージ時クローズ）

## 変更内容（4タスク＋購読API）

- **T1 通知種別＋preference**: `LineNotificationType.CARD_DIVISION_REMINDER`（選手用チャネル）＋ `line_notification_preferences.card_division_reminder` カラム。購読制のため既存慣習と逆で **DEFAULT FALSE**。per-org 判定 `isCardDivisionReminderEnabled`（レコード無し＝OFF）。
- **T2 札組テキストのサーバー一元生成**: `cardRules.js` の決定論生成（FNV-1a32/mulberry32/部分Fisher-Yates/3試合サイクル）を `CardDivisionTextService` に Java 移植。`cardRules.js`/`kimariji.js` は**不変**（Non-goal）。`Kimariji.java`（補正値込み複製）。`GET /api/card-division`。
- **T3 3時間前スケジューラ＋送信**: `CardDivisionReminderScheduler`（5分ポーリング、`[開始-3h, 開始)` ウィンドウ、開始時刻＝`venue_match_schedules` match_number=1 →`PracticeSession.startTime`→無ければスキップ）。per-org 購読ゲートで受信者を絞り `sendCardDivisionReminder`（dedupeKey=sessionId・空き枠通知と同型の予約経路）。
- **購読部分更新API**: `GET` に `subscribed` 返却、`PUT /api/card-division/subscription`（`setCardDivisionReminder`＝find-or-create で当該フラグのみ更新。`updatePreferences` 全上書きの「札分けONで他通知が全OFFに潰れる」事故を回避）。
- **T4 設定画面**: 設定グリッド導線＋`/settings/card-division`。参加練習会ごとにテキスト表示・コピー（トグル非依存）・LINE購読トグル（既定OFF・楽観的更新）・未連携案内・0件空表示。`api/cardDivision.js`。
- **本番DB適用済み**: `add_card_division_reminder_preference.sql` を Render PostgreSQL に適用・introspect検証済み。CHECK制約は本番現行24種別＋新種別=25で張り直し（テンプレの ADMIN_DENSUKE_* 4種別欠落を回避）。docs（db-tables-2/notifications/matching/SCREEN_LIST）更新。

## レビュー（auto-review-loop、2R収束・effort=high固定）

- **R1**（tokens 110,228）: needs_changes・blocker 1件＝`CardDivision.test.jsx` の clipboard fallback が空オブジェクトに spy で例外の脆弱性（実環境は user-event v14 setup が clipboard を用意しテストは元々 green）→ fallback に `writeText: vi.fn()` を持たせ堅牢化（commit `c1f8ec43`）。
- **R2**（tokens 118,418, 累計 228,646/500,000）: **pass**・blocker/should_fix/nit ゼロ。@RequireRole・per-org購読ゲート・dedupeKey・購読部分更新APIによる既存通知保護を good points。
- DoD: D1（memory）初回 FAIL（repo `.claude/memory/` に記録未作成）→本記録で解消。他 PASS/SKIP（A3 typecheck 未定義SKIP）。CI `test` は pending のままマージ（v0.9.0: マージ前CI待ちなし。赤なら追修正）。

## 設計・教訓

- migration の CHECK制約テンプレ（`add_match_video_registered_notification.sql`）は20種別で ADMIN_DENSUKE_* 4種別欠落＝古い。**そのままコピーせず本番 introspect で現行25種別を導出**（advisor 検出）。migration適用は新ツール `C:\tmp\dbtool\RunMig.java`（env読込・全文1トランザクション）。
- **デフォルトOFFの穴**: `isNotificationEnabled` は preference 行ゼロのプレイヤーに全種別 true を返す。購読制は必ず per-org `isCardDivisionReminderEnabled(...).orElse(false)` で先に絞る（bare `sendToPlayer` 禁止・不変条件を送信メソッドに明記）。
- PRNG移植はバイト一致必須（`Math.imul`=int乗算、`>>>0`=`& 0xFFFFFFFFL` は /2^32 直前のみ、U+3000、`parseInt||100`）。**ゴールデンは実 cardRules.js を node で採取**（Java-vs-Java にしない）。
- JSDOM の `navigator.clipboard` テストは fallback を空オブジェクトにせず `writeText: vi.fn()` を必ず持たせる（Codex high の定番指摘）。
- 実装詳細メモ（auto-memory）: impl_card_division_line_reminder / auto_review_round_pr1051。

## コミット

- 973482fd feat(line): 通知種別＋購読preference（デフォルトOFF）＋migration（本番適用済）
- 7fe31ea5 feat(card-division): 札組テキストのサーバー一元生成＋取得API
- 39199e44 feat(scheduler): 1試合目3時間前スケジューラ＋LINE送信
- 8ac17c26 feat(card-division): 購読状態(subscribed)＋トグル部分更新API
- 2022526d feat(settings): 札分け確認画面＋設定導線
- 46d01d0c feat(settings): 参加練習会0件時の空表示
- c1f8ec43 test(card-division): clipboard fallback 堅牢化（Codex R1）
