---
status: completed
---

# 伝助双方向同期（densuke-write-sync）実装手順書

## 実装タスク

### タスク1: DBマイグレーション
- [ ] 完了
- **概要:** 新規テーブル2件の作成と、`practice_participants` への `dirty` カラム追加
- **変更対象ファイル:**
  - `database/` 配下に新規SQLファイル追加（3ファイル）
    - `add_dirty_to_practice_participants.sql` — `dirty BOOLEAN NOT NULL DEFAULT TRUE` を追加
    - `create_densuke_member_mappings.sql` — densuke_member_mappings テーブル作成
    - `create_densuke_row_ids.sql` — densuke_row_ids テーブル作成
- **依存タスク:** なし
- **対応Issue:** #102

---

### タスク2: Entity・Repository の追加
- [ ] 完了
- **概要:** 新規テーブル2件に対応するEntityとRepositoryを作成。既存の `PracticeParticipant` に `dirty` フィールドを追加
- **変更対象ファイル:**
  - `entity/DensukeMemberMapping.java` — 新規作成（id, densukeUrlId, playerId, densukeMemberId, createdAt）
  - `entity/DensukeRowId.java` — 新規作成（id, densukeUrlId, densukeRowId, sessionDate, matchNumber, createdAt）
  - `repository/DensukeMemberMappingRepository.java` — 新規作成（findByDensukeUrlIdAndPlayerId 等）
  - `repository/DensukeRowIdRepository.java` — 新規作成（findByDensukeUrlIdAndSessionDateAndMatchNumber 等）
  - `entity/PracticeParticipant.java` — `dirty` フィールド追加
- **依存タスク:** タスク1
- **対応Issue:** #103

---

### タスク3: PracticeParticipantService への dirty フラグ設定
- [ ] 完了
- **概要:** 選手・管理者がアプリ側で操作した際に `dirty=true` を設定する。対象は参加登録・キャンセル・ステータス変更の全操作
- **変更対象ファイル:**
  - `service/PracticeParticipantService.java` — 参加登録・削除・ステータス変更の各メソッドで `dirty=true` を設定
- **依存タスク:** タスク2
- **対応Issue:** #104

---

### タスク4: DensukeImportService の dirty フラグ対応
- [ ] 完了
- **概要:** 伝助→アプリの削除判定に dirty フラグ条件を追加。`dirty=true` の参加者は削除しない。伝助から追加した参加者は `dirty=false` で保存
- **変更対象ファイル:**
  - `service/DensukeImportService.java`
    - 削除処理（importFromDensuke内）： `dirty=false` の場合のみ削除する条件を追加
    - 追加処理（importFromDensuke内）： 新規追加時に `dirty=false` を設定
- **依存タスク:** タスク2
- **対応Issue:** #105

---

### タスク5: DensukeWriteService の新規作成
- [ ] 完了
- **概要:** アプリ→伝助への書き込みを担うサービス。dirty=true の参加者を対象に、伝助へのHTTPリクエストで出欠を書き込む
- **変更対象ファイル:**
  - `service/DensukeWriteService.java` — 新規作成。処理フロー：
    1. dirty=true の参加者を取得（当月・翌月でDensuke URLが登録されているセッションのみ）
    2. プレイヤー×URLでグループ化
    3. 各グループに対して：
       a. densuke_member_id を取得（なければ POST insert で自動作成し保存）
       b. densuke_row_ids を取得（なければ POST list?mi={mi} で編集フォームを取得・パース・保存）
       c. 当該プレイヤーの全セッションのステータスを取得し join-{id} の値（3/2/1/0）を決定
       d. POST regist で全 join-{id} 値を送信
       e. 成功したら dirty=false に更新、失敗したらエラーをリストに追加
    4. 書き込み状況（最終実行日時・エラー）をフィールドに保持
  - `dto/DensukeWriteStatusDto.java` — 新規作成（lastAttemptAt, lastSuccessAt, errors, pendingCount）
- **依存タスク:** タスク2, タスク3
- **対応Issue:** #106

---

### タスク6: DensukeSyncScheduler の処理順変更
- [ ] 完了
- **概要:** スケジューラーに DensukeWriteService を組み込み、「①書き込み→②読み取り」の順序で実行するよう変更
- **変更対象ファイル:**
  - `scheduler/DensukeSyncScheduler.java`
    - DensukeWriteService を DI で注入
    - scheduledSync メソッドで DensukeWriteService.writeToDesuke() を DensukeImportService より先に呼び出す
- **依存タスク:** タスク4, タスク5
- **対応Issue:** #107

---

### タスク7: API エンドポイント追加と管理画面への表示
- [ ] 完了
- **概要:** 書き込み状況を返すエンドポイントを追加し、DensukeManagement.jsx に表示する
- **変更対象ファイル:**
  - `controller/PracticeSessionController.java` — `GET /api/practice-sessions/densuke-write-status` を追加（ADMIN以上）
  - `karuta-tracker-ui/src/api/practices.js` — `getDensukeWriteStatus(year, month)` を追加
  - `karuta-tracker-ui/src/pages/densuke/DensukeManagement.jsx`
    - 書き込み状況セクションを追加（最終書き込み試行日時・成功日時・エラーメッセージ・書き込み待ち件数）
    - ページ表示時・手動同期後に `getDensukeWriteStatus` を呼び出して状態を表示
- **依存タスク:** タスク5
- **対応Issue:** #108

---

### タスク8: テスト追加
- [ ] 完了
- **概要:** 各新規クラスと変更クラスのテストを追加
- **変更対象ファイル:**
  - `test/.../service/DensukeWriteServiceTest.java` — 新規作成
    - dirty=true の参加者のみ書き込まれること
    - dirty=false の参加者はスキップされること
    - メンバーIDがない場合に自動作成されること
    - 書き込み失敗時に dirty=true が維持されること
  - `test/.../service/DensukeImportServiceTest.java` — 既存に追加
    - dirty=true の参加者は削除されないこと
    - dirty=false の参加者は削除されること
    - Densuke追加時に dirty=false が設定されること
- **依存タスク:** タスク3, タスク4, タスク5
- **対応Issue:** #109

---

## 実装順序

```
タスク1（DBマイグレーション）
  ↓
タスク2（Entity・Repository）
  ↓
タスク3（PracticeParticipantService dirty設定） ─── タスク4（DensukeImportService dirty対応）
  ↓                                                         ↓
タスク5（DensukeWriteService）─────────────────────────────┘
  ↓
タスク6（DensukeSyncScheduler順序変更）
  │
  ├─ タスク7（APIエンドポイント・管理画面）
  └─ タスク8（テスト）
```

タスク3・4は独立して並行作業可能。タスク7・8もタスク5・6が完了次第、並行作業可能。
