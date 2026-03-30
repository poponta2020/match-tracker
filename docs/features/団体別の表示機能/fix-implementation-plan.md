---
status: completed
---
# 団体別の表示機能 改修実装手順書

## 実装タスク

### タスク1: OrganizationController の認可チェック追加
- [x] 完了
- **概要:** `GET/PUT /api/organizations/players/{playerId}` に自ユーザー検証を追加。`currentUserId` と `playerId` の一致チェック、SUPER_ADMIN は他ユーザーも許可
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/OrganizationController.java` — `HttpServletRequest` 追加、`currentUserId` 取得、自己一致チェックロジック追加
- **依存タスク:** なし
- **対応Issue:** #148

### タスク2: UpdatePlayerOrganizationsRequest DTO 作成
- [x] 完了
- **概要:** `PUT /api/organizations/players/{playerId}` のリクエストボディを専用DTOに変更
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/UpdatePlayerOrganizationsRequest.java` — 新規作成（`List<Long> organizationIds` フィールド）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/OrganizationController.java` — `@RequestBody Map<String, List<Long>>` を `@RequestBody UpdatePlayerOrganizationsRequest` に変更
- **依存タスク:** タスク1
- **対応Issue:** #149

### タスク3: 未使用エンドポイント・メソッドの削除
- [x] 完了
- **概要:** フロントエンドから未使用の3エンドポイント（全件取得・upcoming・range）と関連する Service/Repository メソッドを削除
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/PracticeSessionController.java` — `getAllSessions`, `getUpcomingSessions`, `getSessionsInRange` メソッド削除
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeSessionService.java` — `findAllSessions`, `findAllSessionsByPlayer`, `findUpcomingSessions`, `findSessionsInRange` メソッド削除
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/PracticeSessionRepository.java` — `findAllOrderBySessionDateDesc`, `findByOrganizationIdInOrderBySessionDateDesc`, `findByDateRange`, `findByOrganizationIdInAndDateRange` メソッド削除
  - `karuta-tracker-ui/src/api/practices.js` — `getAll`, `getUpcoming` メソッド削除
- **依存タスク:** なし
- **対応Issue:** #150

### タスク4: findNextParticipation に団体フィルタ追加
- [x] 完了
- **概要:** ホーム画面の「次の参加予定」をユーザーの参加団体に限定。`findUpcomingSessions` を団体フィルタ版に置換し、不要になった Repository メソッドも削除
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeSessionService.java` — `findNextParticipation` 内で `getPlayerOrganizationIds` を取得し `findUpcomingSessionsByOrganizationIdIn` を使用。`findUpcomingSessionsByPlayer` メソッドも削除可（Controller から呼ばれていない場合）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/PracticeSessionRepository.java` — `findUpcomingSessions` メソッド削除（タスク3でController参照削除済み、本タスクでService参照も削除されるため）
- **依存タスク:** タスク3（Controller から `findUpcomingSessions` の参照が削除されている必要がある）
- **対応Issue:** #151

### タスク5: GET /api/practice-sessions/dates に団体フィルタ追加
- [x] 完了
- **概要:** 試合結果ビューの日付候補をユーザーの参加団体に限定
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/PracticeSessionController.java` — `getSessionDates` に `HttpServletRequest` パラメータ追加、`currentUserId` を Service に渡す
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeSessionService.java` — `findSessionDates` メソッドを修正し `playerId` パラメータを追加。`playerId` がある場合は `findSessionDatesByOrganizationIdIn` を使用
- **依存タスク:** なし
- **対応Issue:** #152

### タスク6: getOrgUnderlineColor のハードコード解消
- [x] 完了
- **概要:** カレンダーの団体カラーアンダーラインをDBの `color` フィールドから取得するように変更
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/practice/PracticeList.jsx` — `getOrgUnderlineColor` 関数を `orgMap[organizationId]?.color` を返すシンプルな実装に変更
- **依存タスク:** なし
- **対応Issue:** #153

## 実装順序

1. **タスク1** — OrganizationController 認可チェック追加（依存なし）
2. **タスク2** — DTO 作成 + Controller 型変更（タスク1に依存）
3. **タスク3** — 未使用エンドポイント削除（依存なし、タスク1と並行可）
4. **タスク4** — findNextParticipation 団体フィルタ追加（タスク3に依存）
5. **タスク5** — dates エンドポイント団体フィルタ追加（依存なし、タスク3と並行可）
6. **タスク6** — フロントエンド色ハードコード解消（依存なし、いつでも可）

並列実行可能なグループ:
- グループA: タスク1 → タスク2
- グループB: タスク3 → タスク4
- グループC: タスク5
- グループD: タスク6

グループA〜Dは互いに独立しており並行実施可能。
