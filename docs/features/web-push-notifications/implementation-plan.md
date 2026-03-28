---
status: completed
---
# Web Push通知 実装手順書

## 実装タスク

### タスク1: DB設計 — push_notification_preferences テーブル作成
- [x] 完了
- **概要:** Web Push通知の種別ごとON/OFF設定を保存するテーブルを作成する。JPA/Hibernateの自動生成で作成されるため、Entityクラスの作成が主な作業。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/PushNotificationPreference.java` — 新規作成。LineNotificationPreference.java をベースに、Web Push用の6種別カラム + enabledカラムを持つEntity
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/PushNotificationPreferenceDto.java` — 新規作成。リクエスト/レスポンス用DTO（fromEntity()メソッド付き）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/PushNotificationPreferenceRepository.java` — 新規作成。findByPlayerId()等のCRUD
- **依存タスク:** なし
- **対応Issue:** #72

### タスク2: バックエンドAPI — Web Push設定のCRUD
- [x] 完了
- **概要:** Web Push通知設定の取得・更新APIエンドポイントを追加する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/PushSubscriptionController.java` — GET /api/push-notification-preferences/{playerId} と PUT /api/push-notification-preferences エンドポイントを追加
- **依存タスク:** タスク1
- **対応Issue:** #73

### タスク3: バックエンド — 通知作成の共通化（createAndPush）
- [x] 完了
- **概要:** NotificationServiceに`createAndPush()`共通メソッドを追加し、アプリ内通知保存 + Web Push設定チェック + 送信を一括で行う仕組みを構築する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/NotificationService.java` — `createAndPush()` メソッド追加、`sendPushIfEnabled()` プライベートメソッド追加。NotificationType → 設定カラムのマッピングロジックを含む（LOTTERY_ALL_WON/LOTTERY_REMAINING_WON/LOTTERY_WAITLISTEDはlotteryResultにマッピング）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PushNotificationService.java` — PushNotificationPreferenceRepositoryの注入は不要（NotificationService側で設定チェックを行い、sendPush()は送信のみに専念）
- **依存タスク:** タスク1
- **対応Issue:** #74

### タスク4: バックエンド — 既存通知作成箇所のリファクタ
- [x] 完了
- **概要:** 既存の通知作成箇所を`createAndPush()`に置き換え、またはWeb Push送信を追加する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/NotificationService.java` — `createOfferNotification()`: 既存のnotificationRepository.save() + pushNotificationService.sendPush()を`createAndPush()`に置き換え。`createOfferExpiredNotification()`: 同様に`createAndPush()`に置き換え。`createLotteryResultNotifications()`: バッチ保存後にプレイヤーごとにWeb Push送信を追加（通知種別に応じたURLマッピング付き）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/LineChannelReclaimScheduler.java` — `warnPlayer()`: 直接のnotificationRepository.save()を`notificationService.createAndPush()`に置き換え。NotificationServiceの注入を追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeImportService.java` — `notifyAdminsOfUnmatchedNames()`: 既存の重複判定ロジックは維持。notificationRepository.save()/saveAll()の後にpushNotificationService.sendPush()呼び出しを追加。PushNotificationServiceとPushNotificationPreferenceRepositoryの注入を追加
- **依存タスク:** タスク3
- **対応Issue:** #75

### タスク5: フロントエンド — Service Worker作成
- [x] 完了
- **概要:** Web Push通知の受信・表示・クリック時遷移を処理するService Workerを作成する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/public/sw.js` — 新規作成。pushイベントでペイロード（title, body, url）を受け取り通知を表示。notificationclickイベントでペイロードのurlに遷移。通知アイコンはicon-512.pngを使用
- **依存タスク:** なし
- **対応Issue:** #76

### タスク6: フロントエンド — APIクライアント拡張
- [x] 完了
- **概要:** Web Push設定の取得・更新APIを呼び出すメソッドをAPIクライアントに追加する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/api/notifications.js` — `getPushPreferences(playerId)` と `updatePushPreferences(data)` メソッドを追加
- **依存タスク:** なし
- **対応Issue:** #77

### タスク7: フロントエンド — 統合通知設定画面の作成
- [x] 完了
- **概要:** 既存のLineSettings.jsxを統合した通知設定画面を新規作成する。Web Pushセクション（有効化/無効化、種別トグル）とLINE通知セクション（既存機能の移植）で構成する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/notifications/NotificationSettings.jsx` — 新規作成。Web Push通知セクション: 有効化ボタン（Service Worker登録 + Push購読 + バックエンド登録）、無効化ボタン（enabled=falseに更新）、種別ごとON/OFFトグル（Web Push有効時のみ表示）、ブラウザ通知非対応・ブロック時のガイドメッセージ、管理者向け種別はADMIN/SUPER_ADMINのみ表示。LINE通知セクション: LineSettings.jsxの内容をそのまま移植（連携状態、ワンタイムコード、種別トグル）
  - `karuta-tracker-ui/src/App.jsx` — `/settings/line` ルートを `/settings/notifications` に変更、importをLineSettingsからNotificationSettingsに変更
  - `karuta-tracker-ui/src/components/NavigationMenu.jsx` — メニューリンクのパスを`/settings/notifications`に変更、ラベルを「通知設定」に変更
- **依存タスク:** タスク5, タスク6
- **対応Issue:** #78

### タスク8: テスト・動作確認
- [ ] 完了
- **概要:** バックエンドのユニットテスト追加と、全体の動作確認を行う。
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/NotificationServiceTest.java` — createAndPush()のテスト追加（設定ONの場合にWeb Push送信されること、設定OFFの場合にスキップされること、設定レコードなしの場合のデフォルト動作）
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/controller/PushSubscriptionControllerTest.java` — 設定API（GET/PUT）のテスト追加
- **依存タスク:** タスク1〜7
- **対応Issue:** #79

## 実装順序
1. タスク1: DB設計（依存なし）
2. タスク5: Service Worker作成（依存なし）— タスク1と並行可能
3. タスク6: APIクライアント拡張（依存なし）— タスク1と並行可能
4. タスク2: バックエンドAPI（タスク1に依存）
5. タスク3: 通知作成の共通化（タスク1に依存）— タスク2と並行可能
6. タスク4: 既存通知作成箇所のリファクタ（タスク3に依存）
7. タスク7: 統合通知設定画面（タスク5, 6に依存）— タスク4と並行可能
8. タスク8: テスト・動作確認（全タスクに依存）
