---
status: completed
---
# iCalカレンダーフィード 実装手順書

## 実装タスク

### タスク1: DBマイグレーションSQL作成と本番適用
- [x] 完了
- **概要:** `players`・`player_organizations` テーブルに列追加、`google_calendar_events` テーブル削除、既存プレイヤー全員に `ical_feed_token` を一括生成。マイグレーションSQLを `database/` 配下に追加し、Render PostgreSQLにも適用する。
- **変更対象ファイル:**
  - `database/<次の番号>_add_ical_feed.sql` — 新規作成
    - `ALTER TABLE players ADD COLUMN ical_feed_token VARCHAR(64);`
    - 既存プレイヤーに `encode(gen_random_bytes(24), 'hex')` で値を生成
    - `CREATE UNIQUE INDEX idx_players_ical_feed_token ON players(ical_feed_token);`
    - `ALTER TABLE players ALTER COLUMN ical_feed_token SET NOT NULL;`
    - `ALTER TABLE player_organizations ADD COLUMN calendar_display_name VARCHAR(50);`
    - `DROP TABLE google_calendar_events;`（ただし削除はタスク10と同時に行う方が安全 → このタスクでは ADD COLUMN のみ実施）
- **依存タスク:** なし
- **対応Issue:** #651
- **完了条件:** SQLが本番DBに適用済み、`\d players` と `\d player_organizations` で列追加が確認できる

---

### タスク2: build.gradle 依存ライブラリ追加（biweekly）
- [x] 完了
- **概要:** iCal形式生成用に `biweekly` ライブラリを追加。Google Calendar API系の削除はタスク10で実施。
- **変更対象ファイル:**
  - `karuta-tracker/build.gradle` — `implementation 'net.sf.biweekly:biweekly:0.6.7'` を追加
- **依存タスク:** なし
- **対応Issue:** #652
- **完了条件:** `./gradlew build` が成功する

---

### タスク3: Player / PlayerOrganization エンティティ拡張
- [x] 完了
- **概要:** エンティティに新列をマッピングし、Repository にフィードtoken検索メソッドを追加。新規プレイヤー作成時のtoken自動生成も実装。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/Player.java` — `icalFeedToken` フィールド追加、`@PrePersist` メソッドで未設定時に自動生成
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/PlayerOrganization.java` — `calendarDisplayName` フィールド追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/PlayerRepository.java` — `Optional<Player> findByIcalFeedToken(String token)` 追加
- **依存タスク:** タスク1
- **対応Issue:** #653
- **完了条件:** `./gradlew compileJava` 成功、既存テストが壊れていない

---

### タスク4: IcalCalendarFeedService 実装
- [x] 完了
- **概要:** フィード生成・token管理・表示名管理のビジネスロジックを実装。既存`GoogleCalendarSyncService`の時刻算出ロジックは流用する（参考にしてコピペでもよい）。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/IcalCalendarFeedService.java` — 新規作成
    - `String generateIcsForToken(String token)` — トークンからPlayer検索→未来参加練習取得（CANCELLED除外）→biweeklyでVCALENDAR構築→テキスト返却
    - `String regenerateTokenForPlayer(Long playerId)` — 新トークン生成して上書き
    - `FeedInfoDto getFeedInfo(Long playerId)` — URL + 所属組織・表示名一覧
    - `FeedInfoDto updateDisplayNames(Long playerId, Map<Long, String> displayNames)` — 表示名一括更新
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/FeedInfoDto.java` — 新規作成
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/CalendarOrganizationDto.java` — 新規作成
- **依存タスク:** タスク2, タスク3
- **対応Issue:** #654
- **完了条件:** `./gradlew compileJava` 成功、メソッドのユニットテストが通る

---

### タスク5: IcalCalendarFeedController（公開エンドポイント）実装
- [x] 完了
- **概要:** 認証不要の公開エンドポイント `GET /ical/calendar/{token}.ics` を実装。インターセプターによる認証チェックを除外する必要あり。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/IcalCalendarFeedController.java` — 新規作成
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/interceptor/` 配下 — 認証インターセプターで `/ical/calendar/**` を除外設定
  - もしくは Spring Security 設定がある場合は同様に除外
- **依存タスク:** タスク4
- **対応Issue:** #655
- **完了条件:** ローカル起動で `curl http://localhost:8080/ical/calendar/<token>.ics` がicsテキストを返す。tokenが無効なら404

---

### タスク6: IcalCalendarSettingsController（認証必須）実装
- [x] 完了
- **概要:** 設定画面用の3エンドポイントを実装。`@RequireRole(PLAYER)` 等で認証を要求。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/IcalCalendarSettingsController.java` — 新規作成
    - `GET /api/calendar/feed/info`
    - `POST /api/calendar/feed/regenerate`
    - `PATCH /api/calendar/feed/display-names`
- **依存タスク:** タスク4
- **対応Issue:** #656
- **完了条件:** ローカル起動で各エンドポイントを認証付きで叩いて期待動作を確認

---

### タスク7: バックエンド ユニットテスト追加
- [ ] 完了
- **概要:** 主要ロジック（feed生成・CANCELLED除外・表示名カスタマイズ・token再発行・時刻算出・UID一意性）のテストを追加。
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/IcalCalendarFeedServiceTest.java` — 新規作成
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/controller/IcalCalendarFeedControllerTest.java` — 新規作成（IntegrationTest）
- **依存タスク:** タスク5, タスク6
- **対応Issue:** #657
- **完了条件:** `./gradlew test` が成功、Jacocoカバレッジが既存しきい値60%を割らない

---

### タスク8: フロントエンド API client 追加
- [x] 完了
- **概要:** `icalCalendar.js` に3つのAPI呼び出し関数を実装。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/api/icalCalendar.js` — 新規作成
    - `getFeedInfo()` → GET `/api/calendar/feed/info`
    - `regenerateFeed()` → POST `/api/calendar/feed/regenerate`
    - `updateDisplayNames(displayNames)` → PATCH `/api/calendar/feed/display-names`
- **依存タスク:** タスク6
- **対応Issue:** #658
- **完了条件:** `npm run build` が成功

---

### タスク9: SettingsPage.jsx の新UI実装
- [x] 完了
- **概要:** 既存のGoogle Calendar OAuth同期UIを削除し、新しい「カレンダー購読」セクションを実装。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/SettingsPage.jsx` —
    - 削除：`calSyncing` / `calSyncMessage` / `calSyncError` state、`handleCalendarSync` 関数、関連JSX、Google OAuth初期化コード
    - 追加：`feedUrl` / `organizations` / `displayNames` / `regenerating` state、`useEffect` で `getFeedInfo()` 実行、URL表示・コピー・再発行確認ダイアログ・表示名カスタマイズ入力
- **依存タスク:** タスク8
- **対応Issue:** #659
- **完了条件:** ローカル起動でブラウザから設定画面を開き、URL表示・コピー・再発行・表示名更新が動作する

---

### タスク10: 既存Google Calendar OAuth関連の削除
- [ ] 完了
- **概要:** バックエンド・フロントエンド・依存・DBテーブルから、既存OAuth方式の痕跡を一括削除。
- **変更対象ファイル:**
  - **バックエンド削除:**
    - `service/GoogleCalendarSyncService.java`
    - `controller/GoogleCalendarController.java`
    - `entity/GoogleCalendarEvent.java`
    - `repository/GoogleCalendarEventRepository.java`
    - 関連DTO（`CalendarSyncRequest` 等あれば）
    - 関連テスト
  - **バックエンド依存削除:**
    - `karuta-tracker/build.gradle` — `google-api-client`, `google-api-services-calendar`, `google-auth-library-oauth2-http` 等を削除
  - **フロントエンド削除:**
    - `karuta-tracker-ui/src/api/calendar.js` — 削除
    - `karuta-tracker-ui/index.html` — Google GIS CDN（`<script src="https://accounts.google.com/gsi/client" ...>`）削除
  - **DB削除:**
    - `database/<次の番号+1>_drop_google_calendar_events.sql` — 新規作成、`DROP TABLE google_calendar_events;`
    - 本番DBへの適用も実施
- **依存タスク:** タスク9（新方式の動作確認後に既存削除）
- **対応Issue:** #660
- **完了条件:** `./gradlew build` と `npm run build` 成功、`google_calendar_events` テーブルが本番DBから削除されている

---

### タスク11: ドキュメント更新
- [ ] 完了
- **概要:** CLAUDE.mdルールに基づき、機能追加・変更を3つの公式ドキュメントに反映。
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` — Google Calendar機能の記述を新方式（iCalフィード）に書き換え
  - `docs/SCREEN_LIST.md` — 設定画面の「カレンダー購読」セクション追加、既存「Google Calendar同期」削除
  - `docs/DESIGN.md` — 同期方式の設計（公開フィード方式、表示名カスタマイズ、ゲスト参加扱い等）を反映
- **依存タスク:** タスク10
- **対応Issue:** #661
- **完了条件:** 3ドキュメントに新方式の記述があり、旧方式の記述が残っていない

---

## 実装順序

```
タスク1（DBマイグレーション ADD COLUMN）
  ↓
タスク2（依存追加: biweekly）
  ↓
タスク3（Entity / Repository 拡張）
  ↓
タスク4（Service実装）
  ↓
  ├─ タスク5（公開Controller）
  └─ タスク6（認証Controller）
       ↓
タスク7（バックエンドテスト）
タスク8（フロントAPI client）
  ↓
タスク9（SettingsPage UI実装）
  ↓
  ★ ローカル動作確認 ★
  ↓
タスク10（既存OAuth関連削除 + DROP TABLE）
  ↓
タスク11（ドキュメント更新）
```

### PR分割の方針
- タスク1: PR-1（DBマイグレーション、本番適用）
- タスク2〜タスク7: PR-2（バックエンド新機能、テスト含む）
- タスク8〜タスク9: PR-3（フロントエンド新UI）
- タスク10: PR-4（既存OAuth削除、テーブル削除）
- タスク11: PR-5（ドキュメント更新）

もしくはタスク10とタスク11を同PRにまとめてもよい。
