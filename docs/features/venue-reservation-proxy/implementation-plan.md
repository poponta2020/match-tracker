---
status: completed
---
# venue-reservation-proxy 実装手順書

## 概要

本実装は **2フェーズ構成** で進める:

- **Phase 1**: 会場非依存の抽象化レイヤー (interface / strategy) を最初から組み込みつつ、かでる2・7 (KADERU) の実装まで完了させて MVP リリースする。子Issue #524〜#537 (タスク1〜14) に対応。
- **Phase 2**: 東区民センター (HIGASHI) を別ブランチで追加実装する。Phase 1 で抽象化が正しく効いていれば、会場別 strategy / Config / Client の追加と DI 登録、フロントの venue 判別拡張、E2E 検証で完了する。詳細タスクは Phase 2 着手時に別途 Issue 化。

## Phase 1: 会場抽象化 + Kaderu MVP

### Issue 番号と本実装計画の対応

子Issue #524〜#537 はタイトル・本文ともに旧 kaderu 単独実装の記述で残っているが (案C 採用)、各タスクは Phase 1 の venue 抽象化を含めた現実装計画の内容で進める。Issue 本文と本計画書で記載が異なる場合、**本計画書の記述が優先**。

### 実装タスク

#### タスク1: 既存Kaderu関連コードの未使用確認と削除前調査
- [x] 完了
- **対応Issue:** #524 (旧名: 既存Kaderu関連コードの未使用確認と削除前調査)
- **調査結果:** Issue #524 のコメント参照。要点:
  - Backend (Controller / Service / Test) と `scripts/room-checker/open-reserve.js`、`application.properties` の `kaderu.*` 5項目は削除可
  - `karuta-tracker-ui/src/api/kaderu.js` は Task 10/11 で参照外し後に削除
  - **Playwright 依存 (`scripts/room-checker/package.json`) は higashi 系スクリプトが使用中のため削除不可** — Task 12 の該当 bullet を訂正
  - `KADERU_USER_ID` / `KADERU_PASSWORD` は新システムで再利用するため Render から削除しない
  - `venueResolver.js` の `KADERU_VENUE_IDS` は既存値 `[3, 4, 8, 11]` を踏襲
- **概要:** 削除予定の既存Kaderu関連コード (Controller / Service / open-reserve.js / kaderu.js 等) が他箇所から参照されていないことを grep で確認し、削除可能性を検証する。参照が残っていれば削除前に依存を外す計画を立てる。
- **変更対象ファイル:** なし (調査のみ。結果をタスク内コメントとして記録)
- **調査対象:**
  - `KaderuReservationController` / `KaderuReservationService` の参照箇所
  - `scripts/room-checker/open-reserve.js` の呼び出し元
  - `karuta-tracker-ui/src/api/kaderu.js` の import 箇所
  - `kaderuAPI.openReserve` の呼び出し箇所
  - `package.json` の Playwright 依存を他のどのスクリプトが使っているか
  - `KADERU_ENABLED` / `KADERU_SCRIPT_PATH` / `KADERU_NODE_COMMAND` 環境変数の参照箇所
- **依存タスク:** なし
- **完了条件:** 削除対象ファイル/設定/依存がすべて「削除しても他機能に影響しない」ことを確認した旨のレポート

#### タスク2: Jsoup 依存の追加
- [ ] 完了
- **対応Issue:** #525 (旧名: Jsoup 依存の追加)
- **概要:** バックエンドで HTML パース/書き換えに使用する Jsoup を Gradle 依存に追加する。
- **変更対象ファイル:**
  - `karuta-tracker/build.gradle` — `implementation 'org.jsoup:jsoup:1.17.2'` 等を追加
- **依存タスク:** なし
- **完了条件:** `./gradlew build` が成功する

#### タスク3: 共通骨組み (Config / SessionStore / DTO / VenueId) 実装
- [ ] 完了
- **対応Issue:** #526 (旧名: DTO・Config・SessionStore 骨組み実装)
- **概要:** プロキシセッション管理の会場非依存な骨組み (ProxySession・ConcurrentHashMap ストア・設定クラス・DTO・VenueId enum) を作成する。タイムアウトクリーンアップの @Scheduled ジョブもここで実装。
- **変更対象ファイル (新規):**
  - `service/proxy/VenueId.java` — enum `{ KADERU, HIGASHI }`
  - `service/proxy/ProxySession.java` — `token`, `venue`, `practiceSessionId`, `roomName`, `date`, `slotIndex`, `cookies` (CookieStore), `hiddenFields` (Map), `createdAt`, `lastAccessedAt`, `completed`, `cachedTrayHtml`
  - `service/proxy/VenueReservationSessionStore.java` — `ConcurrentHashMap<String, ProxySession>` + `createSession`/`get`/`touch`/`remove` + `@Scheduled(fixedDelayString)` でクリーンアップ
  - `config/VenueReservationProxyConfig.java` — `@ConfigurationProperties("venue-reservation-proxy")` で `enabled`, `sessionTimeoutMinutes`, `cleanupIntervalMinutes`, `requestTimeoutSeconds`, `venues.{kaderu,higashi}.{enabled,baseUrl,userId,password}` を読み込み
  - `dto/CreateVenueProxySessionRequest.java` — `venue`, `practiceSessionId`, `roomName`, `date`, `slotIndex`
  - `dto/CreateVenueProxySessionResponse.java` — `proxyToken`, `viewUrl`, `venue`
- **変更対象ファイル (編集):**
  - `karuta-tracker/src/main/resources/application.yml` — `venue-reservation-proxy:` セクション追加 (旧 `kaderu.*` は本タスクではまだ消さない)
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/MatchTrackerApplication.java` — `@EnableScheduling` 確認 (既にあれば不要)
- **依存タスク:** タスク2 (#525)
- **完了条件:** 単体テストで SessionStore の CRUD・タイムアウト削除・複数 venue のセッション分離が動作する

#### タスク4: VenueReservationClient interface + KaderuReservationClient 実装
- [ ] 完了
- **対応Issue:** #527 (旧名: KaderuProxyClient 実装)
- **概要:** 会場別 HTTP クライアントの契約を `VenueReservationClient` interface として定義し、Phase 1 で必要な `KaderuReservationClient` 実装をかでる用に作成する。Apache HttpClient ベースで、ログイン→マイページ→空き状況→月合わせ→日付クリック→スロット選択→申込トレイまでを順次実行する。既存 [open-reserve.js](scripts/room-checker/open-reserve.js) のロジックを Java に移植。空き状況verificationはスキップ。
- **変更対象ファイル (新規):**
  - `service/proxy/VenueReservationClient.java` (interface) — `venue()`, `prepareReservationTray(ProxySession)`, `fetch(ProxySession, HttpRequest)`
  - `service/proxy/VenueReservationProxyException.java` — `errorCode` + `message` + `venue`
  - `service/proxy/venue/VenueConfig.java` (interface) — `venue()`, `baseUrl()`, `displayName()`, `sessionTimeout()`
  - `service/proxy/venue/kaderu/KaderuVenueConfig.java` — VenueConfig impl
  - `service/proxy/venue/kaderu/KaderuReservationClient.java` — VenueReservationClient impl
    - CookieStore を ProxySession ごとに分離
    - エラーコード体系 (LOGIN_FAILED / ROOM_NOT_FOUND / NOT_AVAILABLE / TRAY_NAVIGATION_FAILED / TIMEOUT 等)
- **参考実装:**
  - [scripts/room-checker/open-reserve.js](scripts/room-checker/open-reserve.js) — ナビゲーションのステップ構造
  - [KaderuReservationService.java](karuta-tracker/src/main/java/com/karuta/matchtracker/service/KaderuReservationService.java) — エラーコード体系
  - [venues/kaderu.md](venues/kaderu.md) — Kaderu 固有の URL / DOM / ナビゲーション (ドキュメント整備時に充実化)
- **依存タスク:** タスク3 (#526)
- **完了条件:**
  - 単体テスト (WireMock で kaderu をモック) で各ステップの正常系・異常系が通る
  - 手動でローカルから実 kaderu に対して `prepareReservationTray` を呼び、申込トレイ画面のHTMLが取得できる
- **Phase 2 への布石:** `VenueReservationClient` interface を切ったことで、higashi 実装時は `HigashiReservationClient` を追加するだけで Spring DI が拾う

#### タスク5: VenueReservationHtmlRewriter + VenueRewriteStrategy + KaderuRewriteStrategy 実装
- [ ] 完了
- **対応Issue:** #528 (旧名: KaderuHtmlRewriter 実装)
- **概要:** 会場サイトからの応答 HTML を書き換える会場非依存コアと、会場別 strategy を実装する。URL書き換え、ヘッダーバナー注入、Location/fetch/XHR フック用スクリプト注入、BroadcastChannel 発信ロジック、申込完了ダイアログロジックを含む。
- **変更対象ファイル (新規):**
  - `service/proxy/venue/VenueRewriteStrategy.java` (interface) — `venue()`, `rewriteHtml(html, token)`, `injectScript()`
  - `service/proxy/VenueReservationHtmlRewriter.java` — 会場非依存コア
    - `rewrite(String html, ProxySession session, VenueRewriteStrategy strategy)` メソッド
    - Jsoup で `<a href>`, `<form action>`, `<img src>`, `<link href>`, `<script src>`, `<base href>` を `/api/venue-reservation-proxy/fetch/...` 形式に書き換え (origin は `VenueConfig.baseUrl` から取得)
    - インライン `<script>` / `<style>` 内の絶対URL文字列を正規表現で書き換え
    - 先頭に注入スクリプトを挿入: `Location.prototype.assign` / `replace`、`window.open`、`fetch`、`XMLHttpRequest.open` のフック (会場非依存) + `strategy.injectScript()` の追記分
    - 先頭にヘッダーバナーを挿入: 「← アプリに戻る」ボタン + `{venue.displayName} 予約画面` + BroadcastChannel + 完了ダイアログ
  - `service/proxy/venue/kaderu/KaderuRewriteStrategy.java` — Kaderu 固有の書き換え (PHP 系の form 構造に対する事前/事後処理)
- **変更対象ファイル (オプション):**
  - `karuta-tracker/src/main/resources/static/venue-proxy-banner.html`
  - `karuta-tracker/src/main/resources/static/venue-proxy-injector.js`
- **依存タスク:** タスク2 (#525), タスク3 (#526)
- **完了条件:**
  - 単体テスト: サンプル HTML を渡して、URL書き換え・注入スクリプト挿入・バナー挿入が期待通り出力されること
  - 書き換え後 HTML をブラウザで開いてバナーが表示されること (スタブ検証可)
- **Phase 2 への布石:** higashi では `HigashiRewriteStrategy` で `__doPostBack` のフックを `injectScript()` に追加するだけで対応できる

#### タスク6: VenueReservationCompletionDetector + VenueCompletionStrategy + KaderuCompletionStrategy 実装
- [ ] 完了
- **対応Issue:** #529 (旧名: KaderuReservationCompletionDetector 実装)
- **概要:** プロキシ経由の各レスポンスを監視し、申込完了画面到達を検知する。会場非依存コアが strategy に判定を委譲する設計。検知時は `practice_sessions.reservation_confirmed_at` を更新し、`ProxySession.completed = true` にセット。
- **変更対象ファイル (新規):**
  - `service/proxy/venue/VenueCompletionStrategy.java` (interface) — `venue()`, `isCompletion(requestUrl, responseLocation, responseBody)`
  - `service/proxy/VenueReservationCompletionDetector.java` — 会場非依存コア
    - `boolean detectAndMarkComplete(ProxySession session, String url, HttpResponse response)` メソッド
    - 該当 venue の `VenueCompletionStrategy` を呼び出し、陽性なら `PracticeSessionRepository` 経由で `reservation_confirmed_at` を更新
  - `service/proxy/venue/kaderu/KaderuCompletionStrategy.java` — Kaderu の完了判定
    - URL マッチパターン (実機検証で確定、暫定 `?p=rsv_comp` 相当)
    - HTML 文言マッチパターン (例: 「申込みを受け付けました」「申込番号」)
- **依存タスク:** タスク3 (#526)
- **完了条件:** 単体テスト: 完了画面サンプル / 非完了画面サンプルで判定が期待通り動作する。複数 venue を登録した状態で正しい strategy が呼ばれる

#### タスク7: VenueReservationProxyService 実装 (統括ロジック)
- [ ] 完了
- **対応Issue:** #530 (旧名: KaderuProxyService 実装)
- **概要:** Controller から呼ばれるファサード的サービス。`createSession` / `view` / `fetch` の3つのユースケースを統括する。会場別 client / strategy を `Map<VenueId, ...>` で DI し、リクエストの venue で dispatch する。
- **変更対象ファイル (新規):**
  - `service/proxy/VenueReservationProxyService.java`
    - `createSession(CreateVenueProxySessionRequest)` — venue が `enabled=true` か検証 (false なら `VENUE_NOT_SUPPORTED`)、SessionStore で新規 ProxySession 作成、該当 `VenueReservationClient.prepareReservationTray` を実行、成功したら `cachedTrayHtml` を保存し token を返す
    - `view(String token)` — SessionStore から取得、該当 `VenueRewriteStrategy` + `VenueReservationHtmlRewriter` で書き換えて HTML 返却
    - `fetch(String token, HttpServletRequest)` — SessionStore.touch、該当 `VenueReservationClient.fetch` で会場サイトに中継、レスポンスを `VenueReservationCompletionDetector` で判定、HTML なら `VenueReservationHtmlRewriter` で書き換え、ヘッダ書き換え、ResponseEntity 返却
- **依存タスク:** タスク3 (#526), 4 (#527), 5 (#528), 6 (#529)
- **完了条件:** 単体テストでビジネスロジック分岐 (venue dispatch, enabled=false 拒否, completion 検知時の cleanup 等) がカバーされる

#### タスク8: VenueReservationProxyController 実装
- [ ] 完了
- **対応Issue:** #531 (旧名: KaderuProxyController 実装)
- **概要:** `/api/venue-reservation-proxy/*` の3エンドポイントを公開する。
- **変更対象ファイル (新規):**
  - `controller/VenueReservationProxyController.java`
    - `POST /api/venue-reservation-proxy/session` — CreateVenueProxySessionRequest 受取、CreateVenueProxySessionResponse 返却
    - `GET /api/venue-reservation-proxy/view` — token パラメータ受取、HTML 返却
    - `RequestMapping /api/venue-reservation-proxy/fetch/**` — 全メソッド (GET/POST/PUT/DELETE 等) 受付、会場サイトに中継
    - `@RequireRole({SUPER_ADMIN, ADMIN})` 付与
    - 例外ハンドラで `VenueReservationProxyException` を HTTP 400/500 + `{errorCode, message, venue}` に変換
- **依存タスク:** タスク7 (#530)
- **完了条件:**
  - Controller 統合テスト (`@WebMvcTest`) で各エンドポイントが認可・入力バリデーションを通過する
  - E2E: ローカルから実 kaderu に対して `POST /api/venue-reservation-proxy/session` (`venue: "KADERU"`) → `GET /api/venue-reservation-proxy/view` で申込トレイ HTML が取得できる
  - `venue: "HIGASHI"` で呼ぶと HTTP 400 + `VENUE_NOT_SUPPORTED` が返る (Phase 1 では `enabled=false`)

#### タスク9: フロントエンド API クライアント + venueResolver 新規作成
- [ ] 完了
- **対応Issue:** #532 (旧名: フロントエンド API クライアント新規作成)
- **概要:** React フロント側のプロキシ API クライアントと venue 判別ユーティリティを新規作成する。
- **変更対象ファイル (新規):**
  - `karuta-tracker-ui/src/api/venueReservationProxy.js`
    - `createSession({venue, practiceSessionId, roomName, date, slotIndex})` → `POST /api/venue-reservation-proxy/session`
  - `karuta-tracker-ui/src/utils/venueResolver.js`
    - `resolveVenue(practiceSession)` — venue_id ベースで `KADERU` | `HIGASHI` | `null` を返す (Phase 1 は KADERU のみマッピング)
    - `KADERU_VENUE_IDS` / `HIGASHI_VENUE_IDS` 定数を export
- **依存タスク:** タスク8 (#531)
- **完了条件:** API 呼び出し試験 (バックエンドのモック/実機に対して) と venueResolver の単体テスト

#### タスク10: PracticeList.jsx の改修
- [ ] 完了
- **対応Issue:** #533 (旧名: PracticeList.jsx の改修)
- **概要:** 既存の `handleReserveAdjacentRoom` を新プロキシフローに置き換え、venue 判別ロジックと BroadcastChannel 受信ロジックを追加する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/practice/PracticeList.jsx`
    - `kaderuAPI` import を `venueReservationProxyAPI` import に変更
    - `resolveVenue` import 追加
    - `handleReserveAdjacentRoom` を新フローに書き換え (事前 window.open + venue 判別 + createSession + location.href 書き換え)
    - 未対応 venue の場合は早期 return + アラート表示
    - useEffect で `new BroadcastChannel('venue-reservation-proxy')` を開き、`reservation-completed` メッセージ受信時に該当セッションのデータを再取得して UI 更新
    - component unmount 時に channel.close()
- **依存タスク:** タスク9 (#532)
- **完了条件:** 手動テストで本番と同一フローの予約操作が完了する

#### タスク11: フロントエンドテスト更新
- [ ] 完了
- **対応Issue:** #534 (旧名: フロントエンドテスト更新)
- **概要:** 既存の `AdjacentRoomFlow.test.jsx` を新プロキシフロー用に書き直す。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/practice/AdjacentRoomFlow.test.jsx`
    - `vi.mock('../../api/kaderu', ...)` を `vi.mock('../../api/venueReservationProxy', ...)` に置き換え
    - `kaderuAPI.openReserve` テストケースを `venueReservationProxyAPI.createSession` 用に書き直し
    - 新フローの成功/失敗ケースを網羅 (KADERU 成功 / `VENUE_NOT_SUPPORTED` / `LOGIN_FAILED` 等)
    - BroadcastChannel 経由の即時反映テストを追加
  - `karuta-tracker-ui/src/utils/venueResolver.test.js` (新規)
    - venue_id → VenueId のマッピングを網羅
- **依存タスク:** タスク10 (#533)
- **完了条件:** `npm test` 全テスト通過

#### タスク12: 既存Kaderu関連コードの削除
- [ ] 完了
- **対応Issue:** #535 (旧名: 既存Kaderu関連コードの削除)
- **概要:** タスク1の調査で「削除可能」と判定された既存コードを実際に削除する。
- **変更対象ファイル (削除):**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/KaderuReservationController.java`
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/KaderuReservationService.java`
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/KaderuReservationServiceTest.java` (もし存在すれば)
  - `karuta-tracker-ui/src/api/kaderu.js`
  - `scripts/room-checker/open-reserve.js`
  - ~~`package.json` の Playwright 依存~~ (Task 1 調査で higashi 系スクリプトが使用中と判明したため削除しない)
- **変更対象ファイル (編集):**
  - `karuta-tracker/src/main/resources/application.yml` — `kaderu.*` 設定値 (`kaderu.enabled`, `kaderu.user-id`, `kaderu.password`, `kaderu.script-path`, `kaderu.node-command`) を削除
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/controller/PracticeSessionControllerTest.java` — kaderu 関連テストがあれば削除
  - Render 環境変数 (手動) — `KADERU_ENABLED`, `KADERU_SCRIPT_PATH`, `KADERU_NODE_COMMAND` を削除
- **依存タスク:** タスク10 (#533), 11 (#534)
- **完了条件:** `./gradlew build` と `npm run build` が成功する

#### タスク13: ドキュメント更新
- [ ] 完了
- **対応Issue:** #536 (旧名: ドキュメント更新)
- **概要:** [CLAUDE.md](../../../CLAUDE.md) のルール「実装が完了したら、以下のドキュメントを必ず最新の状態に更新すること」に従い、関連ドキュメントを更新する。
- **変更対象ファイル:**
  - [docs/SPECIFICATION.md](../../SPECIFICATION.md) — 仕様書: 隣室予約機能のフローを venue 抽象化プロキシ方式に更新
  - [docs/SCREEN_LIST.md](../../SCREEN_LIST.md) — 画面一覧: プロキシ画面・ダイアログを追記
  - [docs/DESIGN.md](../../DESIGN.md) — 設計書: venue-reservation-proxy のコンポーネント構成を追記 (interface + strategy パターン)
  - [docs/features/venue-reservation-proxy/venues/kaderu.md](venues/kaderu.md) — 実装で確定した URL / DOM / 完了パターンを反映
- **依存タスク:** タスク12 (#535)
- **完了条件:** 4ドキュメントすべてに本機能の内容が反映されている

#### タスク14: E2E手動検証 + PR作成
- [ ] 完了
- **対応Issue:** #537 (旧名: E2E手動検証 + PR作成)
- **概要:** 本番 (Render) の環境変数設定を確認し、実機で E2E 検証を実施。問題なければ PR 作成。
- **作業手順:**
  - Render 環境変数 `KADERU_USER_ID` / `KADERU_PASSWORD` が設定済みか確認
  - ローカルブランチで E2E 動作確認 (KADERU)
    - 「隣室を予約」ボタン → 申込トレイ画面到達 → 利用目的入力 → 申込み → 完了検知 → ダイアログ → カレンダー遷移 → 元タブ即時反映
  - エラーパスの検証 (無効なスロット、ログイン失敗、タイムアウト、`VENUE_NOT_SUPPORTED`)
  - PR作成・レビュー依頼
- **依存タスク:** タスク13 (#536)
- **完了条件:** PR がマージ可能な状態になる

### 実装順序

```
1 (既存調査)
  ↓
2 (Jsoup依存追加) - 単独
  ↓
3 (骨組み: Config/SessionStore/DTO/VenueId)
  ↓                  ↓                 ↓
4 (Client+interface) 5 (Rewriter+strategy)  6 (Detector+strategy)
  └────────┬────────┘
           ↓
7 (Service 統括 + venue dispatch)
  ↓
8 (Controller)
  ↓
9 (フロントAPI + venueResolver)
  ↓
10 (PracticeList.jsx 改修)
  ↓
11 (フロントテスト更新)
  ↓
12 (既存削除)
  ↓
13 (ドキュメント更新)
  ↓
14 (E2E + PR)
```

依存関係まとめ:
- タスク2 は単独で先行実行可能
- タスク4, 5, 6 はタスク3完了後に並列実行可能
- タスク7 はタスク4, 5, 6 全てに依存
- 以降は順次

## Phase 2: Higashi 拡張 (別ブランチ・Issue は別途切る)

Phase 1 完了後、以下のタスクを別ブランチで実施する。本実装計画の時点では sketch 扱いで、Phase 2 着手時に詳細 Issue 化する。

### Phase 2 想定タスク (sketch)

| # | 内容 | 備考 |
|---|------|-----|
| P2-1 | **東区民センター 完了画面の実機検証** | 本番で1回実申込を発生させ、完了画面の URL と特徴的文言を確定する。 [findings.md §11](../higashi-reservation-flow-exploration/findings.md) の残課題1。スタッフが別用途で申込む際に観察するか、テスト用の申込を1件意図的に発生させる。 |
| P2-2 | **HigashiVenueConfig 実装** | `service/proxy/venue/higashi/HigashiVenueConfig.java`、`venue-reservation-proxy.venues.higashi.enabled = true` 化、`SAPPORO_COMMUNITY_USER_ID` / `SAPPORO_COMMUNITY_PASSWORD` 環境変数の参照 |
| P2-3 | **HigashiReservationClient 実装** | ASP.NET WebForms 対応。`__VIEWSTATE` / `__EVENTVALIDATION` / `__VIEWSTATEGENERATOR` / `__PREVIOUSPAGE` / `__EVENTTARGET` / `__EVENTARGUMENT` の抽出・echo を Jsoup で実装 ([findings.md §3](../higashi-reservation-flow-exploration/findings.md))。AutoPostBack の 1操作=1要求 を順序制御 |
| P2-4 | **HigashiRewriteStrategy 実装** | `injectScript()` で `__doPostBack` JS 呼び出しのフック (form 送信に変換) を会場固有スクリプトとして注入。`<a onclick="newLinkClick(...)">` 等 ASP.NET 由来の JS ハンドラ対応 |
| P2-5 | **HigashiCompletionStrategy 実装** | P2-1 で確定した URL / 文言パターンを実装 |
| P2-6 | **PracticeList 側 venue 判別の拡張** | `venueResolver.js` の `HIGASHI_VENUE_IDS` を実 venue_id で埋める。「隣室を予約」ボタンの表示条件を東区民センターでも有効化 |
| P2-7 | **Incapsula WAF 配慮の検証** | User-Agent 偽装が効いているか確認、レート制限の閾値を把握、長時間運用時のセッション維持挙動を観測 ([findings.md §4.3](../higashi-reservation-flow-exploration/findings.md)) |
| P2-8 | **Phase 2 ドキュメント更新** | `venues/higashi.md` の「未確認」項目を実機検証結果で埋める。SPECIFICATION.md / DESIGN.md に東区民センター対応を追記 |
| P2-9 | **Phase 2 E2E 検証 + PR** | 実機で隣室予約フローを完走させて Phase 2 を MVP リリース |

### Phase 2 着手前の前提条件

- Phase 1 が main にマージ済みで、KADERU の運用実績が一定期間 (例: 1ヶ月) 取れている
- KADERU 運用で見つかった課題 (タイムアウト調整、エラーメッセージ改善等) があれば Phase 1 の延長で解消済み
- 抽象化レイヤーが正しく機能していることが運用で検証できている (会場別実装を 0.4x で追加できる前提が崩れていない)

### Phase 1 抽象化が崩れた場合のリカバリ

Phase 1 リリース後、KADERU で運用しながら「Phase 2 で higashi 実装するには interface を変える必要がある」と判明した場合:

- 軽微な変更 (メソッド追加など): Phase 2 の中で interface 拡張 + Kaderu impl のメソッド追加で対応
- 重大な変更 (interface 全体の見直し): 一度 Phase 1 の interface を refactor する小PR を出してから Phase 2 着手
