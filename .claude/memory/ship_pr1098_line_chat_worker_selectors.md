---
name: ship-pr1098-line-chat-worker-selectors
description: "PR#1098出荷=LINEチャット予約タスク7(chat.line.biz実DOMセレクタ確定＋ローカルPoC)。OamChatPage全実装・PoC PASS・worker+docsのみBE/DB無改修。auto-review 4R収束(全high)"
metadata:
  node_type: memory
  type: ship
---

PR #1098 出荷（LINEチャット予約送信化 **タスク7=Phase2ローカルPoC**、親#1084・対応#1092）。
`feature/line-chat-reserve-broadcast-phase2`。**line-chat-worker + docs のみ・karuta-tracker(BE)/フロント/DB 無改修**。PR#1096(タスク1-6)の続き。

## 内容
chat.line.biz の実DOMを Phase2 で実測し `OamChatPage` の全メソッドを実装、テストグループで実走PoC PASS。
- **セレクタ**: ルームURL `chat.line.biz/<accountPath>/chat/<chatRoomId>` 直ナビ／入力 `#editor`(Shift+Enterで改行=`\n`保持・Enterは即送信)／予約は `a[aria-label="oa.chat.button.scheduledmessages"]`→モーダル「送信日時を設定」→CUSTOMラジオ`#date3`(隠しradioゆえ`dispatchEvent("click")`)→native `input[type=date]`(YYYY-MM-DD)/`input[type=time]`(HH:mm)→`設定`／確定検証は `alert:メッセージは YYYY/MM/DD HH:mm に送信されます` バナーの日時一致／削除は ⋮→削除→確認モーダル削除。
- **config**: `LINE_OAM_ACCOUNT_PATH`(per-OA定数)を必須env追加。docker-compose.yml・RUNBOOK・.env全経路に配線。
- **datetime**: native入力/バナー照合用ヘルパ＋テスト。**scripts**: `create-auth-state`(chat.line.biz対象＋ログイン自動検知)、`poc-run`(実走PoC)、`auth-check`(セッション有効性診断)。
- **実測知見**: UAゲートは Playwright headless 通過(in-app Electron不可)。送信時刻は10分単位で非境界はスナップ→verifyがMISMATCH検出(サイレント誤送信なし)。BE側10分floorは **PR#1097で別途出荷済**。
- **検証**: ローカルPoC=dry-run/RESERVED/DUPLICATE/CANCELLED PASS。ヘッドレス認証(storageStateのみ)=AUTH_OK。lint/tsc/32テスト green。実配信確認はユーザー判断で不要(往復PASSで十分)。

## auto-review
4ラウンドで収束（全 effort=high・LINE配信系＋差分>800行/12ファイル）。累計約248k/500kトークン。
- R1 should_fix: openChat後に#editor不在なら認証壁扱いに（findings記載の保険が未実装＝doc/impl齟齬）→`editorMissingAfterOpen`実装。
- R2 should_fix: deleteReservation の削除確認が固定1.5秒待ち→`banner.waitFor({state:"hidden"})`化。
- R3 **blocker**: 必須化した `LINE_OAM_ACCOUNT_PATH` が docker-compose.yml 未配線→`missing required env`で起動不能→environment注入＋RUNBOOK追記。**差分外ファイル(PR#1由来)への影響をCodexが指摘した好例**。
- R4 pass。

## 教訓
- 新required env(requireEnv)を足したら **config だけでなく docker-compose.yml/RUNBOOK/.env の全経路に配線**（片方だけはデプロイで起動不能）。
- ドキュメント(findings)に書いた安全策は実装にも落とす（R1のdoc/impl齟齬）。
- Playwrightの「削除/消失」確認は固定sleep+countでなく `waitFor(hidden/detached)`（非同期反映に強い）。
- Bash背景タスクは cwd 継承が不安定→絶対パス必須／孤立Playwright Chromiumは path*ms-playwright*でStop-Process安全掃除／push はTLS/Connection reset頻発でリトライ要。
- 詳細な実DOMマップは `docs/features/line-chat-reserve-broadcast/phase2-dom-findings.md`（正典）。
