---
status: completed
---
# LINE リッチメニュー 実装手順書

## 実装タスク

### タスク1: LineMessagingService に Rich Menu API メソッド追加
- [x] 完了
- **概要:** LINE Messaging API の Rich Menu 関連エンドポイントを呼び出すメソッドを追加する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineMessagingService.java` — 以下の3メソッドを追加:
    - `createRichMenu(String channelAccessToken, Map<String, Object> richMenuJson)` → `String richMenuId` を返却。`POST https://api.line.me/v2/bot/richmenu`
    - `uploadRichMenuImage(String channelAccessToken, String richMenuId, byte[] imageData, String contentType)` → `boolean`。`POST https://api-data.line.me/v2/bot/richmenu/{richMenuId}/content`（注: データAPIは `api-data.line.me`）
    - `setDefaultRichMenu(String channelAccessToken, String richMenuId)` → `boolean`。`POST https://api.line.me/v2/bot/user/all/richmenu/{richMenuId}`
- **依存タスク:** なし
- **対応Issue:** #253

### タスク2: リッチメニュー一括設定エンドポイント追加
- [x] 完了
- **概要:** 管理者がPLAYERチャネル全体にリッチメニューを一括設定するAPIエンドポイントを追加する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LineAdminController.java` — `POST /api/admin/line/rich-menu/setup` エンドポイント追加
    - `@RequireRole(Role.SUPER_ADMIN)`
    - `@RequestParam("image") MultipartFile image` で画像を受け取る
    - リッチメニューJSON定義を構築（5ボタン: 3つのpostback + 2つのURI action）
    - `lineChannelService` 経由で全PLAYERチャネルを取得
    - チャネルごとに `createRichMenu` → `uploadRichMenuImage` → `setDefaultRichMenu` を実行
    - 結果を `{ successCount, failureCount, failures }` で返却
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/RichMenuSetupResponse.java` — レスポンスDTO新規作成
    - `int successCount`
    - `int failureCount`
    - `List<String> failures`（失敗したチャネル名のリスト）
- **依存タスク:** タスク1
- **対応Issue:** #254

### タスク3: キャンセル待ち状況確認の照会ハンドラー実装
- [x] 完了
- **概要:** リッチメニューから「キャンセル待ち状況確認」が押された時の postback 処理と Flex Message ビルダーを実装する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LineWebhookController.java` — `handlePostback()` 内に `check_waitlist_status` の分岐追加、`handleCheckWaitlistStatus()` メソッド追加
    - `CONFIRMABLE_ACTIONS` には含めない（確認ダイアログ不要）
    - playerId から `PracticeParticipantRepository` で WAITLISTED + OFFERED のエントリを取得
    - セッション情報・会場名を付与して Flex Message で返信
    - 0件の場合はテキスト返信「現在キャンセル待ちはありません」
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java` — `buildWaitlistStatusFlex()` メソッド追加
    - 練習日・会場名・試合番号・キャンセル待ち順位を一覧表示
    - OFFERED の場合は回答期限も表示
    - 既存の Flex Message スタイル（ヘッダー色・フォント等）に合わせる
- **依存タスク:** なし
- **対応Issue:** #255

### タスク4: 今日の参加者表示の照会ハンドラー実装
- [x] 完了
- **概要:** リッチメニューから「今日の参加者」が押された時の postback 処理を実装する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LineWebhookController.java` — `handlePostback()` 内に `check_today_participants` の分岐追加、`handleCheckTodayParticipants()` メソッド追加
    - 当日のセッションを `PracticeSessionRepository` から取得（`findBySessionDate(today)`）
    - セッションなし → テキスト返信「今日の練習はありません」
    - セッションあり → WON メンバーを取得し、既存の `buildSameDayConfirmationFlex()` を呼び出して Flex Message で返信
    - 自分が WON でなくても閲覧可能（playerId による絞り込みはしない）
- **依存タスク:** なし
- **対応Issue:** #256

### タスク5: 当日参加申込の照会ハンドラー実装
- [x] 完了
- **概要:** リッチメニューから「当日参加申込」が押された時の postback 処理と Flex Message ビルダーを実装する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LineWebhookController.java` — `handlePostback()` 内に `check_same_day_join` の分岐追加、`handleCheckSameDayJoin()` メソッド追加
    - 当日のセッションを取得
    - セッションなし → テキスト返信「現在参加申込できる試合はありません」
    - セッションあり → 試合ごとに以下を判定:
      - `vacancy = capacity - count(WON)`
      - `hasWaitlist = count(WAITLISTED in this match) > 0`
      - 申込可能 = 空きあり AND (waitlistなし OR `JstDateTimeUtil.now()` が12時以降)
    - 申込可能な試合が0 → テキスト返信「現在参加申込できる試合はありません」
    - 申込可能な試合あり → `buildSameDayJoinFlex()` で Flex Message 返信
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java` — `buildSameDayJoinFlex()` メソッド追加
    - 申込可能な試合一覧を表示
    - 各試合に「参加する」ボタン（`action=same_day_join&sessionId=X&matchNumber=Y` の postback）
    - 既存の `same_day_join` フローに合流するので、確認ダイアログ → 実行の流れはそのまま使える
- **依存タスク:** なし
- **対応Issue:** #257

### タスク6: テスト
- [x] 完了
- **概要:** 追加したメソッド・エンドポイントのユニットテストを作成する
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/LineMessagingServiceTest.java` — Rich Menu API メソッドのテスト（モック）
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/controller/LineWebhookControllerTest.java` — 照会型 postback のテスト
    - `check_waitlist_status`: 0件/複数件のケース
    - `check_today_participants`: 練習なし/ありのケース
    - `check_same_day_join`: 申込不可（当日以外, 空きなし, 12時前+waitlistあり）/ 申込可のケース
- **依存タスク:** タスク1, タスク3, タスク4, タスク5
- **対応Issue:** #258

## 実装順序
1. タスク1: LineMessagingService に Rich Menu API メソッド追加（依存なし）
2. タスク3: キャンセル待ち状況確認ハンドラー（依存なし、タスク1と並行可）
3. タスク4: 今日の参加者表示ハンドラー（依存なし、並行可）
4. タスク5: 当日参加申込ハンドラー（依存なし、並行可）
5. タスク2: リッチメニュー一括設定エンドポイント（タスク1に依存）
6. タスク6: テスト（タスク1, 3, 4, 5に依存）
