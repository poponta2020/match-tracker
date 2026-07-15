---
name: ship_pr1067_card_division_today_tomorrow
description: 札分け確認v2(今日＋明日表示)を出荷(2026-07-15、PR #1067、フロント単一タスク・BE無改修)
metadata:
  node_type: memory
  type: ship
---

card-division-line-reminder の **v2改修（今日＋明日表示）** を出荷（2026-07-15、**PR #1067**）。親#1064・子#1065（マージ時クローズ）。要件は `docs/features/card-division-line-reminder/requirements.md` §4.1 (AC-14〜21)。

**内容**: 札分け確認画面（`/settings/card-division`）を各練習会ブロックで「今日＋明日」の2日分常時表示に。明日の日付は今日レスポンスの `date`（BEがJST解決）を TZ非依存の純関数 `addOneIsoDay` で +1（FEの `new Date()` 不使用＝AC-17）。日別コピーボタン、明日ブロックは常時「暫定」注記。**BE無改修**（既存 `GET /api/card-division` の `date` パラメータ流用）。

**変更ファイル**: `karuta-tracker-ui/src/pages/settings/CardDivision.jsx`・`CardDivision.test.jsx`・`utils/date.js`（`addOneIsoDay` を react-refresh lint 都合で切出し）・`utils/date.test.js`・`docs/SCREEN_LIST.md` §8.10。worktree `C:/tmp/impl-card-division-today-tomorrow`（別slug＝v1マージ済リモートbr残存の枝分かれ罠回避）。

**auto-review**: 1R pass（Codex high・blockers/should_fix/nits 全0・36.5k tokens）。679 FEテスト green（swipeフレークは `--no-file-parallelism` で確定）・lint 0エラー・BE差分ゼロ。AC-18(暫定注記)はadvisor指摘でセッション有無非依存の常時表示に修正済。

**出荷時の環境事故**: 並行セッション（別チャットのdev server + PR #1066出荷）が共有 `karuta-tracker-ui/node_modules` を破壊→worktree独自 `npm ci` で隔離してテスト実行。DoDゲートの lint は本体node_modules依存のため `npm install` で本体も復旧。詳細は auto-memory の同名記録参照。
