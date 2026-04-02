---
status: completed
---
# LINE チャネル用途分離 実装手順書

## 実装タスク

### タスク1: DB マイグレーション & Entity / Enum 追加
- [x] 完了
- **概要:** `line_channels` と `line_channel_assignments` に `channel_type` カラムを追加し、対応する Entity・Enum を更新する
- **変更対象ファイル:**
  - `database/` — 新規マイグレーションSQL（`channel_type` カラム追加、既存レコードに `PLAYER` 設定、インデックス追加）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/ChannelType.java` — 新規 enum `ChannelType { PLAYER, ADMIN }`
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/LineChannel.java` — `channelType` フィールド追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/LineChannelAssignment.java` — `channelType` フィールド追加
- **依存タスク:** なし
- **対応Issue:**

### タスク2: Repository 層の更新
- [x] 完了
- **概要:** channelType を条件に含む検索メソッドを追加し、既存の単一結果前提のメソッドを修正する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/LineChannelRepository.java` — `findByStatusAndChannelType()`, `findAllByChannelType()` 追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/LineChannelAssignmentRepository.java` — `findByPlayerIdAndChannelTypeAndStatusIn()` 追加、`findActiveByPlayerId()` の LIMIT 1 除去
- **依存タスク:** タスク1
- **対応Issue:**

### タスク3: LineChannelService の更新
- [x] 完了
- **概要:** チャネル割当・解放・一覧取得を channelType 対応にする。ADMIN チャネル要求時のロールチェックを追加する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineChannelService.java` — `assignChannel()`, `releaseChannel()`, `getAllChannels()` に channelType パラメータ追加、ロールチェックロジック追加
- **依存タスク:** タスク2
- **対応Issue:**

### タスク4: LineLinkingService の更新
- [x] 完了
- **概要:** `reissueCode()` を channelType 対応にする
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineLinkingService.java` — `reissueCode(Long playerId, ChannelType channelType)` に変更
- **依存タスク:** タスク2
- **対応Issue:**

### タスク5: LineNotificationService の通知ルーティング対応
- [x] 完了
- **概要:** 通知種別から送信先チャネル種別を判定し、該当チャネルのアサインメントを取得して送信するようにする
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/LineNotificationType.java` — `getRequiredChannelType()` メソッド追加（ADMIN_ プレフィックス → ADMIN、それ以外 → PLAYER）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java` — `resolveChannel()` を channelType 対応に修正。通知種別から `getRequiredChannelType()` でチャネル種別を取得し、`findByPlayerIdAndChannelTypeAndStatusIn()` で該当アサインメントを検索
- **依存タスク:** タスク2
- **対応Issue:**

### タスク6: LineWebhookController の postback 修正
- [x] 完了
- **概要:** postback 処理で lineUserId + channelId の両方でアサインメントを特定するように修正
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LineWebhookController.java` — postback の `findByLineUserIdAndStatus()` を `findByLineUserIdAndLineChannelIdAndStatus()` に変更
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/LineChannelAssignmentRepository.java` — 上記メソッド追加（必要な場合）
- **依存タスク:** タスク2
- **対応Issue:**

### タスク7: LineUserController のエンドポイント変更
- [x] 完了
- **概要:** ユーザー向けLINE APIのパスに `{channelType}` パスパラメータを追加する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LineUserController.java` — 各エンドポイントのパスを `/api/line/{channelType}/...` に変更、`@PathVariable ChannelType channelType` 追加、Service 呼び出しに channelType を渡す
- **依存タスク:** タスク3, タスク4
- **対応Issue:**

### タスク8: LineAdminController のチャネル管理API変更
- [x] 完了
- **概要:** チャネル一覧のフィルタリング対応とチャネル作成時の channelType 対応
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LineAdminController.java` — `getAllChannels()` に `@RequestParam(required = false) ChannelType channelType` 追加、チャネル作成に channelType 対応
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/LineChannelDto.java` — `channelType` フィールド追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/LineChannelCreateRequest.java` — `channelType` フィールド追加（デフォルト: PLAYER）
- **依存タスク:** タスク3
- **対応Issue:**

### タスク9: LineChannelReclaimScheduler の更新
- [x] 完了
- **概要:** 複数アサインメントを持つユーザーのチャネル回収に対応する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/LineChannelReclaimScheduler.java` — 各アサインメントを個別に回収対象として処理するように修正
- **依存タスク:** タスク2
- **対応Issue:**

### タスク10: フロントエンド APIクライアント更新
- [x] 完了
- **概要:** line.js の各API関数に channelType パラメータを追加する
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/api/line.js` — `enableLine(channelType)`, `disableLine(channelType)`, `reissueCode(channelType)`, `getLineStatus(channelType)`, `getChannels(channelType)`, `createChannel(data)` の更新
- **依存タスク:** タスク7, タスク8
- **対応Issue:**

### タスク11: LineSettings.jsx のUI変更
- [x] 完了
- **概要:** 管理者の場合に「選手用LINE」「管理者用LINE」の2セクションを表示し、それぞれ独立して操作できるようにする
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/line/LineSettings.jsx` — ロール判定追加、LINE設定UIをコンポーネント化して2セクション描画、通知プリファレンスの表示振り分け
- **依存タスク:** タスク10
- **対応Issue:**

### タスク12: LineChannelAdmin.jsx のタブUI追加
- [x] 完了
- **概要:** チャネル管理画面に「選手用」「管理者用」タブを追加し、用途別にフィルタリング表示する
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/line/LineChannelAdmin.jsx` — タブUI追加（デフォルト: 選手用）、一覧取得時に channelType クエリパラメータ付与、チャネル追加時に channelType 自動セット
- **依存タスク:** タスク10
- **対応Issue:**

### タスク13: テスト
- [x] 完了
- **概要:** 新機能のユニットテストを追加し、既存テストを修正する
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/LineChannelServiceTest.java` — 新規: channelType 別の割当・解放テスト
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/LineNotificationServiceTest.java` — 新規: 通知ルーティングテスト（ADMIN通知→ADMINチャネル、PLAYER通知→PLAYERチャネル）
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/LineConfirmationServiceTest.java` — 既存テストの修正（必要に応じて）
- **依存タスク:** タスク1〜9
- **対応Issue:**

## 実装順序
1. タスク1: DB マイグレーション & Entity / Enum（依存なし）
2. タスク2: Repository 層（タスク1に依存）
3. タスク3〜6: Service層・Controller修正（タスク2に依存、互いに独立なため並行可能）
   - タスク3: LineChannelService
   - タスク4: LineLinkingService
   - タスク5: LineNotificationService
   - タスク6: LineWebhookController
4. タスク7〜9: Controller・Scheduler（タスク3〜4に依存）
   - タスク7: LineUserController
   - タスク8: LineAdminController
   - タスク9: LineChannelReclaimScheduler
5. タスク10: フロントエンド APIクライアント（タスク7〜8に依存）
6. タスク11〜12: フロントエンド UI（タスク10に依存、互いに独立なため並行可能）
   - タスク11: LineSettings.jsx
   - タスク12: LineChannelAdmin.jsx
7. タスク13: テスト（全タスクに依存）
