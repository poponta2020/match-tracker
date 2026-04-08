---
status: completed
---
# 隣室空き確認通知 実装手順書

## 実装タスク

### タスク1: DBスキーマ追加（room_availability_cache, adjacent_room_notifications）
- [ ] 完了
- **概要:** スクレイピング結果のキャッシュテーブルと段階的通知の重複防止テーブルを作成する
- **変更対象ファイル:**
  - `database/migrations/add_room_availability_tables.sql` — 新規。2テーブルのCREATE文
- **依存タスク:** なし
- **対応Issue:** #371

### タスク2: 隣室ペア設定（定数クラス）
- [ ] 完了
- **概要:** かでる2・7の和室の隣接関係・拡張先・サイト上の部屋名をマッピングする定数クラスを作成
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/config/AdjacentRoomConfig.java` — 新規。隣室ペア定数・ヘルパーメソッド
- **依存タスク:** なし
- **対応Issue:** #372

### タスク3: RoomAvailabilityCacheエンティティ・リポジトリ
- [ ] 完了
- **概要:** room_availability_cacheテーブルに対応するJPAエンティティとリポジトリを作成
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/RoomAvailabilityCache.java` — 新規
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/RoomAvailabilityCacheRepository.java` — 新規。`findByRoomNameAndTargetDateAndTimeSlot()`等
- **依存タスク:** タスク1
- **対応Issue:** #373

### タスク4: AdjacentRoomNotificationエンティティ・リポジトリ
- [ ] 完了
- **概要:** adjacent_room_notificationsテーブルに対応するJPAエンティティとリポジトリを作成
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/AdjacentRoomNotification.java` — 新規
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/AdjacentRoomNotificationRepository.java` — 新規。`existsBySessionIdAndRemainingCount()`等
- **依存タスク:** タスク1
- **対応Issue:** #374

### タスク5: AdjacentRoomService（隣室空き確認・会場拡張）
- [ ] 完了
- **概要:** 隣室の空き状況取得と会場拡張処理を行うサービスクラスを作成
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/AdjacentRoomService.java` — 新規
    - `getAdjacentRoomAvailability(Long venueId, LocalDate date)` — DBキャッシュから隣室の空き状況を返す
    - `expandVenue(Long sessionId)` — venue_idを拡張後に変更、capacityを拡張後Venueの値に更新
- **依存タスク:** タスク2, タスク3
- **対応Issue:** #375

### タスク6: NotificationType追加・通知サービス拡張
- [ ] 完了
- **概要:** ADJACENT_ROOM_AVAILABLEタイプを追加し、通知サービスのisTypeEnabled()を拡張
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/Notification.java` — NotificationType enumに `ADJACENT_ROOM_AVAILABLE` 追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/NotificationService.java` — `isTypeEnabled()` にADJACENT_ROOM_AVAILABLEのケース追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/PushNotificationPreference.java` — `adjacentRoom` カラム追加
- **依存タスク:** なし
- **対応Issue:** #376

### タスク7: 定員接近チェック スケジューラー
- [ ] 完了
- **概要:** 30分間隔で未来のかでる和室セッションの定員接近をチェックし、隣室が空きなら管理者に段階的に通知するスケジューラー
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/AdjacentRoomNotificationScheduler.java` — 新規
    - 未来のセッションのうちかでる和室(venue_id IN 3,4,8,11)を取得
    - 各セッションの全試合の参加者数を計算、最も定員に近い試合の残り人数を算出
    - 残り4人以下かつ、その残り人数段階で未通知 → room_availability_cacheから隣室空きチェック
    - 空きなら SUPER_ADMIN全員 + 団体ADMINに通知作成
    - adjacent_room_notificationsに記録
- **依存タスク:** タスク2, タスク4, タスク5, タスク6
- **対応Issue:** #377

### タスク8: PracticeSessionDto拡張・API修正（隣室情報付与）
- [ ] 完了
- **概要:** セッション詳細取得時に隣室の空き状況を付与して返す
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/PracticeSessionDto.java` — `adjacentRoomStatus` フィールド追加（隣室名、空き状態、拡張後Venue ID/名前/定員）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeSessionService.java` — セッション詳細取得時にAdjacentRoomServiceを呼び出して隣室情報を付与
- **依存タスク:** タスク5
- **対応Issue:** #378

### タスク9: 会場拡張APIエンドポイント
- [ ] 完了
- **概要:** 管理者がセッションの会場を拡張するための専用エンドポイントを追加
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/PracticeSessionController.java` — `POST /api/practice-sessions/{id}/expand-venue` 追加（@RequireRole ADMIN以上）
- **依存タスク:** タスク5
- **対応Issue:** #379

### タスク10: フロントエンド — カレンダーポップアップに隣室状況・拡張ボタン追加
- [ ] 完了
- **概要:** モーダルヘッダーに隣室空き状況バッジと会場拡張ボタンを追加
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/practice/PracticeList.jsx` — モーダルヘッダー（行512-514付近）に隣室状況表示追加。管理者かつ隣室空きの場合に「会場を拡張」ボタン追加。確認ダイアログ実装
  - `karuta-tracker-ui/src/api/practices.js` — `expandVenue(sessionId)` メソッド追加
- **依存タスク:** タスク8, タスク9
- **対応Issue:** #380

### タスク11: スクレイピングスクリプト（DB書き込み版）
- [ ] 完了
- **概要:** かでる2・7サイトから4部屋の空き状況を取得し、PostgreSQLにUPSERTするNode.jsスクリプト
- **変更対象ファイル:**
  - `scripts/room-checker/sync-to-db.js` — 新規。4部屋×今日〜40日先の夜間スロットを取得しDBに書き込み
  - `scripts/room-checker/package.json` — `pg` パッケージ追加
- **依存タスク:** タスク1
- **対応Issue:** #381

### タスク12: テスト
- [ ] 完了
- **概要:** 主要ロジックのユニットテスト
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/config/AdjacentRoomConfigTest.java` — 新規。隣室マッピングのテスト
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/AdjacentRoomServiceTest.java` — 新規。空き状況取得・会場拡張のテスト
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/scheduler/AdjacentRoomNotificationSchedulerTest.java` — 新規。段階的通知ロジックのテスト
- **依存タスク:** タスク5, タスク7
- **対応Issue:** #382

## 実装順序

1. **タスク1**: DBスキーマ追加（依存なし）
2. **タスク2**: 隣室ペア設定（依存なし）※タスク1と並行可能
3. **タスク11**: スクレイピングスクリプト（タスク1に依存）
4. **タスク3**: RoomAvailabilityCacheエンティティ・リポジトリ（タスク1に依存）
5. **タスク4**: AdjacentRoomNotificationエンティティ・リポジトリ（タスク1に依存）※タスク3と並行可能
6. **タスク6**: NotificationType追加（依存なし）※タスク3-4と並行可能
7. **タスク5**: AdjacentRoomService（タスク2, 3に依存）
8. **タスク7**: スケジューラー（タスク2, 4, 5, 6に依存）
9. **タスク8**: DTO拡張・API修正（タスク5に依存）
10. **タスク9**: 会場拡張APIエンドポイント（タスク5に依存）※タスク8と並行可能
11. **タスク10**: フロントエンド（タスク8, 9に依存）
12. **タスク12**: テスト（タスク5, 7に依存）※タスク10と並行可能
