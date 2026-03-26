---
status: completed
---
# 抽選通知まとめ・キャンセル待ち辞退機能 実装手順書

## 実装タスク

### タスク1: ステータス・通知タイプのEnum拡張
- [ ] 完了
- **概要:** 新しいステータスと通知タイプをEnumに追加する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/ParticipantStatus.java` — `WAITLIST_DECLINED` を追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/Notification.java` — `NotificationType` に `LOTTERY_ALL_WON`, `LOTTERY_REMAINING_WON` を追加
- **依存タスク:** なし
- **対応Issue:** #33

### タスク2: キャンセル待ち辞退・復帰のバックエンドロジック
- [ ] 完了
- **概要:** キャンセル待ち辞退（セッション単位）と復帰のサービスメソッド・APIエンドポイントを実装する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/PracticeParticipantRepository.java` — 辞退・復帰・番号繰り上げ用のクエリメソッド追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/WaitlistPromotionService.java` — `declineWaitlistBySession()`, `rejoinWaitlistBySession()`, 番号繰り上げロジック追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java` — `POST /api/lottery/decline-waitlist`, `POST /api/lottery/rejoin-waitlist` エンドポイント追加
- **依存タスク:** タスク1
- **対応Issue:** #34

### タスク3: 通知まとめロジック（アプリ内通知）
- [ ] 完了
- **概要:** `NotificationService.createLotteryResultNotifications()` をプレイヤーごとにグルーピングして通知を作成するロジックに改修する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/NotificationService.java` — `createLotteryResultNotifications()` の全面改修
    - プレイヤーごとにグルーピング
    - 全当選 → `LOTTERY_ALL_WON` 1レコード
    - 一部落選 → セッション別 `LOTTERY_WAITLISTED` + `LOTTERY_REMAINING_WON`
    - 全落選 → セッション別 `LOTTERY_WAITLISTED` のみ
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java` — `notifyResults()` のロジックを新しい通知まとめに合わせて調整
- **依存タスク:** タスク1
- **対応Issue:** #35

### タスク4: 通知まとめロジック（LINE通知）
- [ ] 完了
- **概要:** `LineNotificationService.sendLotteryResults()` をプレイヤーごとにグルーピングして送信するロジックに改修する。セッション別Flex Messageの新規ビルダーを追加。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java`
    - `sendLotteryResults()` の全面改修（プレイヤーごとグルーピング、3パターンの送信）
    - `buildLotteryWaitlistedFlex()` 新規メソッド（セッション単位のキャンセル待ちFlex Message）
    - LINE postback ハンドラにキャンセル待ち辞退アクション追加
- **依存タスク:** タスク1, タスク2
- **対応Issue:** #36

### タスク5: 締切後の新規キャンセル待ち登録
- [ ] 完了
- **概要:** 抽選締切後に参加登録する際、定員超過ならキャンセル待ち（最後尾）、空きがあれば即当選として登録するロジックを追加する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeParticipantService.java` — `registerParticipations()` に締切後分岐ロジック追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryDeadlineHelper.java` — 必要に応じて抽選実行済み判定メソッド追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/LotteryExecutionRepository.java` — 対象月の抽選実行済み判定クエリ追加（必要な場合）
- **依存タスク:** タスク1
- **対応Issue:** #37

### タスク6: フロントエンド - 通知一覧のグルーピング表示
- [ ] 完了
- **概要:** 通知一覧ページで抽選結果通知をグルーピング表示し、キャンセル待ち辞退ボタンを追加する
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/notifications/NotificationList.jsx`
    - 新通知タイプ（`LOTTERY_ALL_WON`, `LOTTERY_REMAINING_WON`）のアイコン・カラー追加
    - `LOTTERY_WAITLISTED` 通知に「キャンセル待ちを辞退する」ボタン追加
    - 確認ダイアログの実装
  - `karuta-tracker-ui/src/api/lottery.js` — `declineWaitlist()`, `rejoinWaitlist()` API関数追加
- **依存タスク:** タスク2, タスク3
- **対応Issue:** #38

### タスク7: フロントエンド - 抽選結果ページの辞退/復帰ボタン
- [ ] 完了
- **概要:** 抽選結果ページに辞退・復帰ボタンと新ステータスバッジを追加する
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/lottery/LotteryResults.jsx`
    - 自分のキャンセル待ち試合に「辞退」ボタン追加（`WAITLISTED` の場合）
    - 辞退済みに「復帰」ボタン追加（`WAITLIST_DECLINED` の場合）
    - `WAITLIST_DECLINED` 用のステータスバッジ追加（グレー「待ち辞退」）
    - 確認ダイアログの実装
- **依存タスク:** タスク2, タスク6
- **対応Issue:** #39

### タスク8: テスト
- [ ] 完了
- **概要:** 新機能・改修箇所のユニットテストを追加する
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/WaitlistPromotionServiceTest.java` — 辞退・復帰・番号繰り上げのテスト
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/NotificationServiceTest.java` — 通知まとめロジックのテスト（全当選/一部落選/全落選）
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/PracticeParticipantServiceTest.java` — 締切後登録のテスト
- **依存タスク:** タスク2, タスク3, タスク5
- **対応Issue:** #40

### タスク9: ドキュメント更新
- [ ] 完了
- **概要:** 仕様書・設計書・画面一覧を更新する（CLAUDE.mdのドキュメント更新ルールに従う）
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` — 通知まとめ・キャンセル待ち辞退機能の仕様追記
  - `docs/DESIGN.md` — 新ステータス・新通知タイプ・新API設計の追記
  - `docs/SCREEN_LIST.md` — 画面変更（通知一覧・抽選結果ページの変更点）追記
- **依存タスク:** タスク1〜タスク8（全タスク完了後）
- **対応Issue:** #41

## 実装順序

1. **タスク1**: ステータス・通知タイプのEnum拡張（依存なし）
2. **タスク2**: キャンセル待ち辞退・復帰のバックエンドロジック（タスク1に依存）
3. **タスク3**: 通知まとめロジック・アプリ内通知（タスク1に依存）
4. **タスク5**: 締切後の新規キャンセル待ち登録（タスク1に依存）
   ※ タスク2, 3, 5 は並行作業可能
5. **タスク4**: 通知まとめロジック・LINE通知（タスク1, 2に依存）
6. **タスク6**: フロントエンド・通知一覧（タスク2, 3に依存）
7. **タスク7**: フロントエンド・抽選結果ページ（タスク2, 6に依存）
8. **タスク8**: テスト（タスク2, 3, 5に依存）
9. **タスク9**: ドキュメント更新（全タスク完了後）
