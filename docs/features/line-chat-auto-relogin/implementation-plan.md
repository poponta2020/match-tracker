---
status: completed
---
# line-chat-auto-relogin 実装手順書

要件: [requirements.md](./requirements.md)。二層運用 = **A. 24hセッションの自動クリックスルー再ログイン**（ワーカー）＋ **B. 30日SSO失効の先回りアラート**（ワーカー検知→既存 `sendChatReserveAlert`）。

## 技術メモ（調査で確定した事実）
- ワーカーは単一 browser context/page を全サイクル再利用（`index.ts`）。SSO Cookie をメモリ保持。各サイクル末尾で `context.storageState({path})` をファイルへ書き戻す（＝再ログイン結果は自動永続）。
- 認証壁は `reserveMessage`/`cancelReservation` の `detectAuthWall`（`OK` 以外）で検出→ `authExpiredOutcome()`（`status=FAILED`, `errorCode=LINE_AUTH_EXPIRED`, `abortCycle=true`）→ `runCycle` がサイクル打ち切り。
- クリックスルー導線（実証済）: `chat.line.biz/<accountPath>` → `account.line.biz/login` の「LINE account」→ `access.line.me` の「Log in」→ `chat.line.biz` 帰着＝新 `__Host-chat-ses`。password欄/reCAPTCHAチャレンジは**出たら即失敗**（クリックは2ボタンのみ）。
- 反応型の管理者アラートは既存: `LineChatReservationService.applyWorkerResult`（`:299-303`）が FAILED/MANUAL_REVIEW_REQUIRED で `alert(groupId, …)`→`sendChatReserveAlert(orgId, msg)` を発火。フォールバックpushは `CardDivisionBroadcastService.processGroupBroadcast` の gate `PROCEED`（FAILED/PENDING/未成立）で発火。**どちらも壊さない**。
- 再利用ヘルパー: `LineNotificationService.sendChatReserveAlert(Long organizationId, String message)`（`@Async`・void・SUPER_ADMIN全員＋当該orgのADMINへADMINチャネルpush）。
- ワーカーAPIは `X-Service-Token`（`ServiceTokenInterceptor`）。`@RequireRole` は付けない（既存 `LineChatWorkerController` に倣う）。

## 実装タスク

### タスク1: ワーカーのクリックスルー再ログイン機構（page-object）
- [x] 完了
- **目的:** 認証壁時に同 context 内で「LINE account」→「Log in」の2クリックで新セッションを張り直す `relogin()` を実装する。**ナビ後はボタン有無でなく `isOnAuthSurface()`（host）で分岐**：chat.line.biz に居れば（＝`editorMissingAfterOpen` 由来の transient wall・セッション有効）**クリックせず `SUCCEEDED`**（リトライで room 再オープン）／認証面に居るときだけ2クリックし、そこで password欄/reCAPTCHAチャレンジ検出・期待ボタン不在なら**入力・突破せず `SSO_EXPIRED`**。
- **対応AC:** AC-3, AC-11, AC-12, AC-13
- **主な変更領域:** `line-chat-worker/src/line/pages/OamChatPage.ts`（`relogin()` 追加。既存 `isOnAuthSurface()`/`AUTH_HOSTS` を再利用）・`line-chat-worker/src/line/pages/ChatPage.ts`（IF 追加）・`line-chat-worker/src/detect/authState.ts`（`ReloginResult = "SUCCEEDED" | "SSO_EXPIRED" | "ERROR"` 型追加）
- **⚠ 最重要（advisor指摘の実バグ回避）:** 「期待ボタン不在＝SSO失効」と判定してよいのは**認証面に居るときだけ**。transient wall（chat.line.biz上でボタン無し）を SSO失効と誤判定すると健全セッションで誤フォールバック（67通課金）＋偽アラートを誘発する。host 分岐で `SUCCEEDED` に倒すこと。
- **依存タスク:** なし
- **必要なテスト:** 判定純ロジック（DOMシグナル→SSO_EXPIRED/SUCCEEDED分類）を可能な限り関数抽出して unit test。実DOMの完走は AC-11（VM PoC）で担保。既存 `OamChatPage` テスト方針（T7）に倣う。
- **完了条件:** tsc/lint green・抽出ロジックの unit test green・`relogin()` が ChatPage IF に載る。
- **対応Issue:** #1116

### タスク2: バックエンド: SSO失効の先回りアラートAPI
- [x] 完了
- **目的:** ワーカーからの「SSO失効が近い」通知を受け、有効な配信グループの各団体の管理者へ既存 `sendChatReserveAlert` で LINE 通知する新エンドポイントを追加する。
- **対応AC:** AC-5（backend側）
- **主な変更領域:** `karuta-tracker` — `controller/LineChatWorkerController.java`（`POST /api/line-chat-worker/session-warning` 追加・`@RequireRole` 無し）・`service/LineChatReservationService.java`（or 適切なサービスに `warnSessionExpiring(int daysRemaining)`：`lineBroadcastGroupRepository.findByEnabledTrue()` の distinct org へ `sendChatReserveAlert(orgId, "[チャット予約] ワーカーのLINEセッション(SSO)が約N日後に失効します。手動再ログインしてください。")`）・`dto/`（リクエスト DTO）
- **依存タスク:** なし（worker ファイルと非重複）
- **必要なテスト:** サービス/コントローラ test（有効グループの org へアラートが発火する・有効グループ0件なら何もしない・不正/未認証は既存 interceptor 準拠）。`@WebMvcTest` で interceptor も効く点は既存パターンに倣う。
- **完了条件:** `./gradlew test` green・新エンドポイントがサービストークン認証下で 200 を返す。
- **対応Issue:** #1117

### タスク3: ワーカーのループ統合（再ログイン・リトライ＋SSO先回り警告）
- [x] 完了
- **目的:** メインループに (a) 認証壁時の `relogin()` 1回試行＋タスク1回リトライ（bounding）、(b) 各サイクルでのSSO Cookie期限監視→閾値内なら1日1回 backend へ警告POST、を組み込む。
- **対応AC:** AC-1, AC-2, AC-4, AC-5（worker側トリガー）, AC-6, AC-7, AC-8, AC-9, AC-10, AC-13
- **主な変更領域:** `line-chat-worker/src/index.ts`（orchestration リトライ＋SSO期限read＋throttle）・`line-chat-worker/src/config/index.ts`（`SSO_WARNING_THRESHOLD_DAYS` 既定3・任意 `AUTO_RELOGIN_ENABLED` 既定true）・`line-chat-worker/src/appApi/client.ts`（`postSessionWarning(daysRemaining)`）・各 `*.test.ts`
  - **(a) リトライ**: `processTask` で usecase が auth-expired（`errorCode=LINE_AUTH_EXPIRED`）を返し、当サイクルで未再ログインなら `po.relogin()`。SUCCEEDED→当該 usecase を1回だけ再実行し**その outcome を報告**（PENDING の RESERVING claim は再送しない）。それ以外→元の auth-expired outcome をそのまま報告（＝既存 FAILED＋フォールバック維持）。再ログインは**1サイクル最大1回**（cycle スコープのフラグを `runCycle`→`processTask` に渡す）。
  - **(b) 先回り警告**: サイクル末尾で `context` の `__is_login_sso` 失効を読み、`daysRemaining <= SSO_WARNING_THRESHOLD_DAYS` かつ「当日未送信」なら `api.postSessionWarning(daysRemaining)`。throttle は in-memory の「最終警告日(JST)」。期限は毎回実 Cookie から読む（ハードコードしない）＝restartで新storageStateの新期限に自己追従。
- **依存タスク:** タスク1（`relogin()` IF）・タスク2（警告APIの契約）
- **必要なテスト:** `index.test.ts` を mock ChatPage/mock client で拡張：AC-1（relogin成功→RESERVEDを報告）・AC-2（成功時FAILED報告しない）・AC-4（relogin=SSO_EXPIRED→FAILED＋abortCycle維持）・AC-13（relogin=SUCCEEDED(transient wall)→リトライ成功・FAILED報告なし）・AC-6（同日2回目は警告POSTしない）・AC-7（threshold判定が渡した期限に従う）・AC-8（1サイクル再ログイン最大1回・リトライ最大1回）・AC-9（壁なし通常時は挙動不変）。
- **完了条件:** worker の全 vitest green・tsc/lint green（AC-10）。
- **対応Issue:** #1118

### docs 更新（各タスクの実装コミットに含める・D2ゲート対象）
- `line-chat-worker/RUNBOOK.md`: 自動クリックスルー再ログインの挙動・新 env（`SSO_WARNING_THRESHOLD_DAYS`）・**約月1回の手動ログインは依然必要（30日SSOは自動延長不可）** を追記（タスク3）。
- `docs/spec/notifications.md`: LINEチャット予約ワーカーの認証壁ハンドリング（自己再ログイン＋先回りアラート）を in-place 反映（タスク2/3のうち該当を触る側）。

## 実装順序（Wave）
- **Wave 1**: タスク1（worker page-object）∥ タスク2（backend endpoint） — 変更領域が非重複・並行可
- **Wave 2**: タスク3（worker ループ統合） — タスク1・2に依存（`relogin()` IF＋警告API契約）

## 検証（実装後）
- AC-11/AC-12/AC-5(実push): VM の使い捨て context で `relogin()` 実走（本番storageStateのコピー・非破壊）＋ SSO失効模擬。既存テスト資材 `~/line-chat-worker/*.mjs` を流用。**ライブのワーカーには storageState を書き戻さない**。
- 本番反映は別途 `git pull`→イメージ再ビルド→`docker compose restart line-chat-worker`（RUNBOOK 準拠・kagetra 非巻き込みのため compose/project 明示）。
