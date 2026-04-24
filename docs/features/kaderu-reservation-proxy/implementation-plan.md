---
status: completed
---
# kaderu-reservation-proxy 実装手順書

## 実装タスク

### タスク1: 既存Kaderu関連コードの未使用確認と削除前調査
- [ ] 完了
- **概要:** 削除予定の既存Kaderu関連コード（Controller/Service/open-reserve.js/kaderu.js 等）が他箇所から参照されていないことを grep で確認し、削除可能性を検証する。参照が残っていれば削除前に依存を外す計画を立てる。
- **変更対象ファイル:**
  - なし（調査のみ。結果をタスク内コメントとして記録）
- **調査対象:**
  - `KaderuReservationController` / `KaderuReservationService` の参照箇所
  - `scripts/room-checker/open-reserve.js` の呼び出し元
  - `karuta-tracker-ui/src/api/kaderu.js` の import 箇所
  - `kaderuAPI.openReserve` の呼び出し箇所
  - `package.json` の Playwright 依存を他のどのスクリプトが使っているか
  - `KADERU_ENABLED` / `KADERU_SCRIPT_PATH` / `KADERU_NODE_COMMAND` 環境変数の参照箇所
- **依存タスク:** なし
- **対応Issue:** #524
- **完了条件:** 削除対象ファイル/設定/依存がすべて「削除しても他機能に影響しない」ことを確認した旨のレポート

### タスク2: Jsoup 依存の追加
- [ ] 完了
- **概要:** バックエンドで HTML パース/書き換えに使用する Jsoup を Gradle 依存に追加する。
- **変更対象ファイル:**
  - `karuta-tracker/build.gradle` — `implementation 'org.jsoup:jsoup:1.17.2'` 等を追加
- **依存タスク:** なし
- **対応Issue:** #525
- **完了条件:** `./gradlew build` が成功する

### タスク3: DTO・Config・SessionStore 骨組み実装
- [ ] 完了
- **概要:** プロキシセッション管理の骨組み（ProxySession オブジェクト・ConcurrentHashMap ストア・設定クラス・DTO）を作成する。タイムアウトクリーンアップの @Scheduled ジョブもここで実装。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/config/KaderuProxyConfig.java` — 新規作成。`@ConfigurationProperties("kaderu-proxy")` で `enabled`, `userId`, `password`, `sessionTimeoutMinutes`, `cleanupIntervalMinutes`, `requestTimeoutSeconds` を読み込み
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/KaderuProxySessionStore.java` — 新規作成。`ConcurrentHashMap<String, ProxySession>`、`createSession`, `get`, `touch`, `remove`、`@Scheduled(fixedDelayString)` でクリーンアップ
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/ProxySession.java` — 新規作成（または SessionStore のネストクラス）。token, practiceSessionId, roomName, date, slotIndex, CookieStore, createdAt, lastAccessedAt, completed, cachedTrayHtml フィールド
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/CreateProxySessionRequest.java` — 新規作成
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/CreateProxySessionResponse.java` — 新規作成
  - `karuta-tracker/src/main/resources/application.yml` — `kaderu-proxy:` セクション追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/MatchTrackerApplication.java` — `@EnableScheduling` 確認（既にあれば不要）
- **依存タスク:** タスク2 (#525)
- **対応Issue:** #526
- **完了条件:** 単体テストで SessionStore のCRUD・タイムアウト削除が動作する

### タスク4: KaderuProxyClient 実装（ログイン〜申込トレイ到達まで）
- [ ] 完了
- **概要:** Apache HttpClient ベースで kaderu にアクセスし、ログイン→マイページ→空き状況→月合わせ→日付クリック→スロット選択→申込トレイまでを順次実行するクライアントを実装する。既存 [open-reserve.js](scripts/room-checker/open-reserve.js) のロジックをJavaに移植。空き状況verificationはスキップ。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/KaderuProxyClient.java` — 新規作成
    - `prepareReservationTray(ProxySession)` メソッド: ログイン〜申込トレイ遷移の全ステップ実行
    - `fetch(ProxySession, HttpRequest)` メソッド: 任意のリクエストを kaderu に中継（fetch エンドポイント用）
    - CookieStore を ProxySession ごとに分離
    - 各ステップのエラーを識別可能な errorCode で例外化（LOGIN_FAILED / ROOM_NOT_FOUND / NOT_AVAILABLE / TRAY_NAVIGATION_FAILED / TIMEOUT 等）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/KaderuProxyException.java` — 新規作成（errorCode + message）
- **参考実装:**
  - [scripts/room-checker/open-reserve.js](scripts/room-checker/open-reserve.js) — ナビゲーションのステップ構造
  - [KaderuReservationService.java](karuta-tracker/src/main/java/com/karuta/matchtracker/service/KaderuReservationService.java) — エラーコード体系
- **依存タスク:** タスク3 (#526)
- **対応Issue:** #527
- **完了条件:**
  - 単体テスト（WireMock で kaderu をモック）で各ステップの正常系・異常系が通る
  - 手動でローカルから実 kaderu に対して `prepareReservationTray` を呼び、申込トレイ画面のHTMLが取得できる

### タスク5: KaderuHtmlRewriter 実装（HTML書き換え + 注入スクリプト + バナー）
- [ ] 完了
- **概要:** kaderu からの応答HTMLを書き換える。URL書き換え、ヘッダーバナー注入、Location/fetch/XHR フック用スクリプト注入、BroadcastChannel 発信ロジック、申込完了ダイアログロジックを含む。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/KaderuHtmlRewriter.java` — 新規作成
    - `rewrite(String html, String proxyToken)` メソッド
    - Jsoup で `<a href>`, `<form action>`, `<img src>`, `<link href>`, `<script src>`, `<base href>` を `/api/kaderu-proxy/fetch/...` 形式に書き換え
    - インライン `<script>` / `<style>` 内の絶対URL文字列を正規表現で書き換え
    - 先頭に**注入スクリプト**を挿入：`Location.prototype.assign` / `replace`、`window.open`、`fetch`、`XMLHttpRequest.open` のフック
    - 先頭に**ヘッダーバナー HTML/CSS/JS**を挿入：
      - 「← アプリに戻る」ボタン
      - 状態表示エリア
      - BroadcastChannel 発信（`new BroadcastChannel('kaderu-reservation')`）
      - 申込完了時のダイアログ表示と `/practice` 遷移ロジック
  - `karuta-tracker/src/main/resources/static/kaderu-proxy-banner.html` — バナー用のHTMLスニペット（オプション、rewriter内文字列でも可）
  - `karuta-tracker/src/main/resources/static/kaderu-proxy-injector.js` — 注入スクリプト（オプション、rewriter内文字列でも可）
- **依存タスク:** タスク2 (#525)
- **対応Issue:** #528
- **完了条件:**
  - 単体テスト: サンプルHTMLを渡して、URL書き換え・注入スクリプト挿入・バナー挿入が期待通り出力されること
  - 書き換え後HTMLをブラウザで開いてバナーが表示されること（スタブ検証可）

### タスク6: KaderuReservationCompletionDetector 実装
- [ ] 完了
- **概要:** プロキシ経由の各レスポンスを監視し、申込完了画面到達を検知する。URL条件 + HTML文言条件の OR 判定。検知時は `practice_sessions.reservation_confirmed_at` を更新し、`ProxySession.completed = true` にセット。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/KaderuReservationCompletionDetector.java` — 新規作成
    - `boolean isCompletionResponse(String url, String responseBody)` メソッド
    - URL マッチパターン（実機検証で確定、暫定 `?p=rsv_comp` 相当）
    - HTML 文言マッチパターン（例: 「申込みを受け付けました」「申込番号」）
    - 検知時にサーバー側 practice_sessions.reservation_confirmed_at を更新（`PracticeSessionRepository` 経由）
- **依存タスク:** タスク3 (#526)
- **対応Issue:** #529
- **完了条件:** 単体テスト: 完了画面サンプル / 非完了画面サンプルで判定が期待通り動作する

### タスク7: KaderuProxyService 実装（統括ロジック）
- [ ] 完了
- **概要:** Controller から呼ばれるファサード的サービス。`createSession` / `view` / `fetch` の3つのユースケースを統括する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/KaderuProxyService.java` — 新規作成
    - `createSession(CreateProxySessionRequest)` — SessionStoreで新規ProxySession作成、KaderuProxyClient.prepareReservationTrayを実行、成功したらcachedTrayHtmlを保存しtokenを返す
    - `view(String token)` — SessionStoreから取得、KaderuHtmlRewriter.rewriteしてHTML返却
    - `fetch(String token, HttpServletRequest)` — SessionStore.touch、KaderuProxyClient.fetchでkaderuに中継、レスポンスをKaderuReservationCompletionDetectorで判定、HTMLならKaderuHtmlRewriterで書き換え、ヘッダ書き換え、ResponseEntity返却
- **依存タスク:** タスク3 (#526), 4 (#527), 5 (#528), 6 (#529)
- **対応Issue:** #530
- **完了条件:** 単体テストでビジネスロジック分岐がカバーされる

### タスク8: KaderuProxyController 実装
- [ ] 完了
- **概要:** `/api/kaderu-proxy/*` の3エンドポイントを公開する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/KaderuProxyController.java` — 新規作成
    - `POST /api/kaderu-proxy/session` — CreateProxySessionRequest受取、CreateProxySessionResponse返却
    - `GET /api/kaderu-proxy/view` — tokenパラメータ受取、HTML返却
    - `RequestMapping /api/kaderu-proxy/fetch/**` — 全メソッド（GET/POST/PUT/DELETE等）受付、kaderu に中継
    - `@RequireRole({SUPER_ADMIN, ADMIN})` 付与
    - 例外ハンドラで KaderuProxyException を HTTP 400/500 + `{errorCode, message}` に変換
- **依存タスク:** タスク7 (#530)
- **対応Issue:** #531
- **完了条件:**
  - Controller 統合テスト（`@WebMvcTest`）で各エンドポイントが認可・入力バリデーションを通過する
  - E2E: ローカルから実 kaderu に対して `POST /api/kaderu-proxy/session` → `GET /api/kaderu-proxy/view` で申込トレイHTMLが取得できる

### タスク9: フロントエンド API クライアント新規作成
- [ ] 完了
- **概要:** React フロント側のプロキシAPIクライアントを新規作成する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/api/kaderuProxy.js` — 新規作成
    - `createSession({practiceSessionId, roomName, date, slotIndex})` → `POST /api/kaderu-proxy/session`
- **依存タスク:** タスク8 (#531)
- **対応Issue:** #532
- **完了条件:** API呼び出し試験（バックエンドのモック/実機に対して）

### タスク10: PracticeList.jsx の改修
- [ ] 完了
- **概要:** 既存の `handleReserveAdjacentRoom` を新プロキシフローに置き換え、BroadcastChannel 受信ロジックを追加する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/practice/PracticeList.jsx`
    - `kaderuAPI` import を `kaderuProxyAPI` import に変更
    - `handleReserveAdjacentRoom` を新フロー（事前 window.open + createSession + location.href 書き換え）に書き換え
    - useEffect で `new BroadcastChannel('kaderu-reservation')` を開き、`reservation-completed` メッセージ受信時に該当セッションのデータを再取得してUI更新
    - component unmount 時に channel.close()
- **依存タスク:** タスク9 (#532)
- **対応Issue:** #533
- **完了条件:** 手動テストで本番と同一フローの予約操作が完了する

### タスク11: フロントエンドテスト更新
- [ ] 完了
- **概要:** 既存の `AdjacentRoomFlow.test.jsx` を新プロキシフロー用に書き直す。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/practice/AdjacentRoomFlow.test.jsx`
    - `vi.mock('../../api/kaderu', ...)` を `vi.mock('../../api/kaderuProxy', ...)` に置き換え
    - `kaderuAPI.openReserve` テストケースを `kaderuProxyAPI.createSession` 用に書き直し
    - 新フローの成功/失敗ケースを網羅
    - BroadcastChannel 経由の即時反映テストを追加
- **依存タスク:** タスク10 (#533)
- **対応Issue:** #534
- **完了条件:** `npm test` 全テスト通過

### タスク12: 既存Kaderu関連コードの削除
- [ ] 完了
- **概要:** タスク1の調査で「削除可能」と判定された既存コードを実際に削除する。
- **変更対象ファイル（削除）:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/KaderuReservationController.java`
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/KaderuReservationService.java`
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/KaderuReservationServiceTest.java`（もし存在すれば）
  - `karuta-tracker-ui/src/api/kaderu.js`
  - `scripts/room-checker/open-reserve.js`
  - `package.json` の Playwright 依存（他で使っていなければ）
- **変更対象ファイル（編集）:**
  - `karuta-tracker/src/main/resources/application.yml` — `kaderu.*` 設定値（`kaderu.enabled`, `kaderu.user-id`, `kaderu.password`, `kaderu.script-path`, `kaderu.node-command`）を削除
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/controller/PracticeSessionControllerTest.java` — kaderu 関連テストがあれば削除
  - Render環境変数（手動） — `KADERU_ENABLED`, `KADERU_SCRIPT_PATH`, `KADERU_NODE_COMMAND` を削除
- **依存タスク:** タスク10 (#533), 11 (#534)
- **対応Issue:** #535
- **完了条件:** `./gradlew build` と `npm run build` が成功する

### タスク13: ドキュメント更新
- [ ] 完了
- **概要:** CLAUDE.md のルール「実装が完了したら、以下のドキュメントを必ず最新の状態に更新すること」に従い、関連ドキュメントを更新する。
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` — 仕様書：隣室予約機能のフローをプロキシ方式に更新
  - `docs/SCREEN_LIST.md` — 画面一覧：プロキシ画面・ダイアログを追記
  - `docs/DESIGN.md` — 設計書：プロキシコンポーネント構成を追記
- **依存タスク:** タスク12 (#535)
- **対応Issue:** #536
- **完了条件:** 3ドキュメント全てに本機能の内容が反映されている

### タスク14: E2E手動検証 + PR作成
- [ ] 完了
- **概要:** 本番（Render）の環境変数設定を確認し、実機でE2E検証を実施。問題なければPR作成。
- **変更対象ファイル:** なし
- **作業手順:**
  - Render環境変数 `KADERU_USER_ID` / `KADERU_PASSWORD` が設定済みか確認
  - ローカルブランチで E2E 動作確認
    - 「隣室を予約」ボタン → 申込トレイ画面到達 → 利用目的入力 → 申込み → 完了検知 → ダイアログ → カレンダー遷移 → 元タブ即時反映
  - エラーパスの検証（無効なスロット、ログイン失敗、タイムアウト）
  - PR作成・レビュー依頼
- **依存タスク:** タスク13 (#536)
- **対応Issue:** #537
- **完了条件:** PR がマージ可能な状態になる

## 実装順序

```
1 (既存調査)
  ↓
2 (Jsoup依存追加) - 単独
  ↓
3 (骨組み: DTO/Config/SessionStore)
  ↓                  ↓
4 (Client)      5 (HtmlRewriter) 6 (CompletionDetector)
  └────────┬────────┘
           ↓
7 (Service 統括)
  ↓
8 (Controller)
  ↓
9 (フロントAPI)
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

依存関係まとめ：
- タスク2 は単独で先行実行可能
- タスク4, 5, 6 はタスク3完了後に並列実行可能
- タスク7 はタスク4, 5, 6 全てに依存
- 以降は順次
