---
status: completed
---
# 団体別iCalフィード 実装手順書

## 実装タスク

### タスク1: DTO の整備（FeedInfoDto 改修 + 新規DTO 作成 + 旧DTO 削除）
- [x] 完了
- **概要:** レスポンス用DTOを新フォーマットに合わせて整える。`FeedInfoDto` を `organizationFeeds` + `guestFeed` 形式に変更、`OrganizationFeedDto` と `GuestFeedDto` を新規作成、不要になった `CalendarOrganizationDto` を削除。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/FeedInfoDto.java` — フィールドを `List<OrganizationFeedDto> organizationFeeds` と `GuestFeedDto guestFeed` に変更
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/OrganizationFeedDto.java` — 新規作成 `{ organizationId, organizationName, displayName, url }`
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/GuestFeedDto.java` — 新規作成 `{ url }`
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/CalendarOrganizationDto.java` — 削除
- **依存タスク:** なし
- **対応Issue:** #664
- **完了条件:** `./gradlew compileJava --no-daemon` が成功する（次タスクで Service 改修するまで一時的に Service がコンパイル失敗するため、本タスク単独ではコンパイル不可。タスク2と合わせて確認する）

---

### タスク2: IcalCalendarFeedService 改修（メソッド削除・追加・getFeedInfo改修）
- [x] 完了
- **概要:** Service層のロジックを新仕様に合わせて改修。`generateIcsForToken` を削除し、`generateIcsForOrgFeed` / `generateIcsForGuestFeed` を新規追加。`getFeedInfo` のレスポンス組み立てを新DTO形式に変更。共通処理を private ヘルパーに切り出す。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/IcalCalendarFeedService.java` —
    - `generateIcsForToken(String)` を削除
    - `generateIcsForOrgFeed(String token, Long orgId)` 追加（プレイヤーが orgId に所属していなければ ResourceNotFoundException）
    - `generateIcsForGuestFeed(String token)` 追加
    - `getFeedInfo(Long playerId)` を改修（organizationFeeds + guestFeed を組み立てて返す）
    - URL組み立てヘルパー: `buildOrgFeedUrl(token, orgId)`, `buildGuestFeedUrl(token)`
    - 共通の VCALENDAR 構築ロジックを private ヘルパーに抽出（カレンダー名と参加練習リストを受け取る形）
    - `regenerateTokenForPlayer` / `updateDisplayNames` はシグネチャ変更なし
- **依存タスク:** タスク1
- **対応Issue:** #665
- **完了条件:** `./gradlew compileJava --no-daemon` 成功

---

### タスク3: IcalCalendarFeedController 改修（公開エンドポイント変更）
- [x] 完了
- **概要:** 公開Controllerのエンドポイントを新URL構造に切り替え。旧 `GET /ical/calendar/{token}.ics` を削除、`/org/{orgId}.ics` と `/guest.ics` の2エンドポイントを追加。設定Controllerは変更なし（レスポンス形式が変わるが Service が新形式で返すので透過的）。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/IcalCalendarFeedController.java` —
    - `getFeed(String token)` を削除
    - `getOrgFeed(String token, Long orgId)` 追加: `GET /ical/calendar/{token}/org/{orgId}.ics`
    - `getGuestFeed(String token)` 追加: `GET /ical/calendar/{token}/guest.ics`
    - 戻り値の Content-Type は既存と同じ `text/calendar;charset=UTF-8`
    - ResourceNotFoundException → 404 のハンドリングは既存と同様
- **依存タスク:** タスク2
- **対応Issue:** #666
- **完了条件:** ローカルで `curl http://localhost:8080/ical/calendar/<token>/org/<orgId>.ics` が ics を返し、無効なtoken/orgIdで404になる

---

### タスク4: バックエンドテスト改修
- [x] 完了
- **概要:** 既存テストを新仕様に追従。Controller テストは削除エンドポイント分を除去・新エンドポイント分を追加。Service テストは generateIcsForOrgFeed / generateIcsForGuestFeed 各ケースを追加し、旧 generateIcsForToken を呼んでいたテストは新メソッドに振り替え。
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/IcalCalendarFeedServiceTest.java` —
    - 既存の generateIcsForToken_* テスト群を generateIcsForOrgFeed_* に書き換え
    - 新規ケース追加:
      - `generateIcsForOrgFeed_unaffiliatedOrgId_returns404`: プレイヤーが orgId に所属していないと例外
      - `generateIcsForOrgFeed_otherOrgPracticeExcluded`: 別組織の練習は含まれない
      - `generateIcsForGuestFeed_onlyGuestPracticesIncluded`: 所属外の練習だけ含まれる
      - `generateIcsForGuestFeed_noGuestParticipations_returnsEmptyCalendar`: 該当なしで空VCALENDAR
      - `getFeedInfo_returnsOrgFeedsAndGuestFeed`: レスポンスに organizationFeeds 配列 + guestFeed が入る
    - getFeedInfo の既存テストは新DTO形式に合わせて改修
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/controller/IcalCalendarFeedControllerTest.java` —
    - `getFeed_validToken_*` → `getOrgFeed_validToken_*` / `getGuestFeed_validToken_*` に変更
    - 404 ケースを org / guest それぞれで追加
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/controller/IcalCalendarSettingsControllerTest.java` —
    - `FeedInfoDto` のフィールド名変更に追従（コンストラクタ呼び出し箇所）
- **依存タスク:** タスク3
- **対応Issue:** #667
- **完了条件:** `./gradlew test --tests "*IcalCalendar*"` 全成功

---

### タスク5: フロントエンド UI 改修（CalendarSubscriptionPage.jsx）
- [ ] 完了
- **概要:** 設定画面を「所属団体ごとのカード」+「ゲスト参加カード」構造に改修。各カードにカレンダー名・URL・コピーボタン。表示名カスタマイズ入力欄は既存維持（所属団体ごと）。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/CalendarSubscriptionPage.jsx` —
    - state: `feedInfo` のシェイプ変更（`{ organizationFeeds, guestFeed }`）
    - 既存の単一URLカード削除 → 所属団体カード（map）+ ゲスト参加カード
    - `copyFeedback` を `copyFeedbackId` に変更し、カードIDを保持（コピー成功表示を該当カードのみに）
    - 再発行ダイアログのメッセージを「すべてのカレンダーURL（団体別＋ゲスト）が無効になる」と明示
    - URL一括再発行ボタンは既存維持
    - 表示名カスタマイズUIは既存維持（PlayerOrganization ごとに `calendarDisplayName` を入力）
  - `karuta-tracker-ui/src/api/icalCalendar.js` — 変更なし（関数シグネチャ同じ、レスポンス形式だけ変わる）
- **依存タスク:** タスク3（バックエンドAPIが新レスポンス形式を返す状態）
- **対応Issue:** #668
- **完了条件:** `npm run lint` 成功、ローカルでブラウザから設定画面を開いて複数URL表示・コピー・再発行が動く

---

### タスク6: ドキュメント更新
- [ ] 完了
- **概要:** CLAUDE.mdルールに基づき、機能変更を3つの公式ドキュメントに反映。
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` —
    - §4.2 カレンダー購読セクション：URL構造を団体別+ゲスト形式に書き換え
    - §7.10 API表：公開エンドポイント2本に変更
  - `docs/SCREEN_LIST.md` — 設定画面「カレンダー購読」の説明を新仕様に更新（特に変更なしの可能性が高いので確認のみ）
  - `docs/DESIGN.md` —
    - §4.10 API: 公開エンドポイント2本に変更（org / guest）
    - §「カレンダー購読（iCalフィード）」セクション：URL構造・カレンダー単位の説明を追加
- **依存タスク:** タスク5
- **対応Issue:** #669
- **完了条件:** 3ドキュメントに新仕様が反映され、旧URL構造の記述が残っていない

---

## 実装順序

```
タスク1（DTO整備）
  ↓
タスク2（Service改修）
  ↓
タスク3（Controller改修）
  ↓
  ├─ タスク4（バックエンドテスト改修）
  └─ タスク5（フロントエンドUI改修）
       ↓
       ★ ローカル動作確認 ★
       ↓
タスク6（ドキュメント更新）
```

### PR分割の方針
規模が小さくDB変更もないため、**1つのPRにまとめる**方針を推奨。タスク1〜5を1コミット単位（または機能単位の小コミット数本）に集約し、タスク6を最終コミットで仕上げる。
