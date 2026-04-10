---
status: completed
---
# メンターフィードバック機能 実装手順書

## 実装タスク

### タスク1: DBマイグレーション
- [x] 完了
- **概要:** 新規テーブル2つ（`mentor_relationships`, `match_comments`）の作成と、既存テーブル（`line_notification_preferences`）へのカラム追加
- **変更対象ファイル:**
  - `database/add_mentor_relationships.sql` — 新規作成。`mentor_relationships` テーブル定義
  - `database/add_match_comments.sql` — 新規作成。`match_comments` テーブル定義
  - `database/add_mentor_comment_notification.sql` — 新規作成。`line_notification_preferences` に `mentor_comment` カラム追加
- **依存タスク:** なし
- **対応Issue:** #393

### タスク2: メンター関係バックエンド（Entity / Repository / Service / Controller / DTO）
- [x] 完了
- **概要:** メンター関係の CRUD + 承認/拒否/解除のバックエンドAPI実装
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/MentorRelationship.java` — 新規作成
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/MentorRelationshipRepository.java` — 新規作成
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/MentorRelationshipService.java` — 新規作成
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/MentorRelationshipController.java` — 新規作成
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/MentorRelationshipDto.java` — 新規作成
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/MentorRelationshipCreateRequest.java` — 新規作成
- **依存タスク:** タスク1
- **対応Issue:** #394

### タスク3: コメントバックエンド（Entity / Repository / Service / Controller / DTO）
- [x] 完了
- **概要:** コメントスレッドの CRUD + LINE通知連携のバックエンドAPI実装
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/MatchComment.java` — 新規作成
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/MatchCommentRepository.java` — 新規作成
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchCommentService.java` — 新規作成
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/MatchCommentController.java` — 新規作成
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/MatchCommentDto.java` — 新規作成
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/MatchCommentCreateRequest.java` — 新規作成
- **依存タスク:** タスク1, タスク2（メンター関係の権限チェックに使用）
- **対応Issue:** #395

### タスク4: 既存API拡張（メンティーのメモ閲覧対応）
- [ ] 完了
- **概要:** メンター関係がある場合、メンティーの試合一覧・詳細でメモ・お手つき情報を返すよう既存APIを拡張
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchService.java` — `enrichDtosWithPersonalNotes()` をメンター対応に拡張
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/MatchDto.java` — メンティーのメモ用フィールド追加（`menteePersonalNotes`, `menteeOtetsukiCount`）
- **依存タスク:** タスク2
- **対応Issue:** #396

### タスク5: LINE通知拡張（メンターコメント通知）
- [ ] 完了
- **概要:** コメント投稿時のLINE通知送信 + 通知設定対応
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/LineNotificationPreference.java` — `mentorComment` フィールド追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/LineNotificationPreferenceDto.java` — 同上
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/LineMessageLog.java` — `NotificationType` に `MENTOR_COMMENT` 追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java` — メンターコメント通知メソッド追加
- **依存タスク:** タスク1, タスク3
- **対応Issue:** #397

### タスク6: フロントエンド — メンター管理画面
- [ ] 完了
- **概要:** 設定画面にメンター管理セクションを追加。メンター指名・承認・拒否・解除・メンティー一覧の表示
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/mentor/MentorManagement.jsx` — 新規作成。メンター管理画面
  - `karuta-tracker-ui/src/api/mentorRelationship.js` — 新規作成。メンター関係API クライアント
  - `karuta-tracker-ui/src/pages/SettingsPage.jsx` — メンター管理へのリンク追加
  - `karuta-tracker-ui/src/App.jsx` — メンター管理画面のルート追加
- **依存タスク:** タスク2
- **対応Issue:** #398

### タスク7: フロントエンド — メンティー試合一覧のメモ表示対応
- [ ] 完了
- **概要:** メンターがメンティーの試合一覧を見た際に、メモ有無・お手つき有無が表示されるよう拡張
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/MatchList.jsx` — メンター関係がある場合のメモ有無表示
  - `karuta-tracker-ui/src/api/matches.js` — 必要に応じてAPI呼び出しパラメータ調整
- **依存タスク:** タスク4, タスク6
- **対応Issue:** #399

### タスク8: フロントエンド — コメントスレッドコンポーネント
- [ ] 完了
- **概要:** 試合詳細画面にコメントスレッドを追加。投稿・編集・削除機能
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/MatchCommentThread.jsx` — 新規作成。コメントスレッドコンポーネント
  - `karuta-tracker-ui/src/api/matchComments.js` — 新規作成。コメントAPI クライアント
  - `karuta-tracker-ui/src/pages/matches/MatchDetail.jsx` — コメントスレッドの埋め込み + メンティーメモ表示
- **依存タスク:** タスク3, タスク4
- **対応Issue:** #400

### タスク9: フロントエンド — 通知設定画面拡張
- [ ] 完了
- **概要:** 通知設定画面にメンターコメント通知のON/OFF追加
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/notifications/NotificationSettings.jsx` — メンターコメント通知設定追加
- **依存タスク:** タスク5
- **対応Issue:** #401

## 実装順序
1. タスク1: DBマイグレーション（依存なし）
2. タスク2: メンター関係バックエンド（タスク1に依存）
3. タスク3: コメントバックエンド（タスク1, 2に依存）
4. タスク4: 既存API拡張（タスク2に依存）
5. タスク5: LINE通知拡張（タスク1, 3に依存）
6. タスク6: フロントエンド — メンター管理画面（タスク2に依存）
7. タスク7: フロントエンド — メンティー試合一覧メモ表示（タスク4, 6に依存）
8. タスク8: フロントエンド — コメントスレッド（タスク3, 4に依存）
9. タスク9: フロントエンド — 通知設定拡張（タスク5に依存）
