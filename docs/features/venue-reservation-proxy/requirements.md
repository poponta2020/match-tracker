---
status: completed
---
# venue-reservation-proxy 要件定義書

## 1. 概要

### 目的
本番環境（Render）でも、練習管理者がスマホから対応会場の隣室予約申込トレイ画面まで到達できるようにする会場非依存のリバースプロキシ機能を提供する。

### 背景・動機
現状の [KaderuReservationService](karuta-tracker/src/main/java/com/karuta/matchtracker/service/KaderuReservationService.java) はサーバー上で Playwright を起動してブラウザを開く設計のため、本番（Render）では `kaderu.enabled=false` で動作せず、ユーザーのスマホで「隣室を予約」ボタンが実質機能していない。

加えて将来的には**東区民センター**でも同種の機能が必要であり、[higashi-reservation-flow-exploration の findings.md](../higashi-reservation-flow-exploration/findings.md) §10 の比較で「**会場非依存に再設計してから kaderu MVP を作る統合方式 (案A) が合計工数最小**」と結論された。

本機能は次の2点を同時に満たす:

1. バックエンドが対応会場へのリバースプロキシとして動作し、ユーザーのブラウザは自ドメイン (`/api/venue-reservation-proxy/*`) 経由で会場サイトを操作する
2. **会場固有部分を strategy/interface で隔離**し、Phase 2 で東区民センターを追加する際の工数を 0.4x まで圧縮する設計を最初から組み込む

### スコープ

| フェーズ | 対応会場 | 状態 |
|---------|--------|-----|
| **Phase 1 (本要件)** | かでる2・7 (`k2.p-kashikan.jp`) | 本要件で実装 |
| **Phase 2 (別途)** | 東区民センター (`sapporo-community.jp`) | 設計のみ本要件で言及。実装は別ブランチ |

Phase 1 完了時点で:
- venue 抽象化レイヤー (`VenueReservationClient` interface 等) は完成し、Phase 2 で会場別実装を追加するだけで済む状態にする
- ただし `HigashiReservationClient` 等の会場別実装は Phase 1 では作らない (interface 雛形のみ)

## 2. ユーザーストーリー

### 対象ユーザー
- SUPER_ADMIN / ADMIN ロールの管理者のみ
- PLAYER ロールには本機能を公開しない

### ユーザーの目的
競技かるた練習会で隣室が必要になった際、スマホからアプリ経由で対応会場の予約申込トレイ画面まで到達し、利用目的を入力して申込みを完了させる。

### 利用シナリオ (Phase 1: かでる2・7)
1. 管理者がスマホで [PracticeList.jsx](karuta-tracker-ui/src/pages/practice/PracticeList.jsx) のセッション詳細モーダルを開く
2. 隣室空き状況が「○」(空きあり) と表示されているのを確認
3. 「隣室を予約」ボタンを押す
4. 元のタブにスピナー表示「かでる2・7に接続中...」（5-10秒）
5. バックエンドがログイン〜申込トレイまで自動遷移完了
6. 新しいタブで「かでる2・7」の申込トレイ画面が即座に開く
7. ユーザーが利用目的を手動入力し、「申込み」ボタンを手動で押下する
8. プロキシが申込完了画面到達を自動検知し `reservationConfirmedAt` を記録する
9. ダイアログ「予約完了したのでアプリに戻ります」→ OK押下でアプリのカレンダー画面に遷移する
10. 元のアプリタブ (開いていれば) も BroadcastChannel 経由で即時に予約完了状態に反映される

### Phase 2 シナリオ (将来)
東区民センターも同等のフローで動作する。venue 判別はサーバー側に委ね、フロントは `practiceSessionId + roomName + ...` を渡すだけ。詳細は [venues/higashi.md](venues/higashi.md) を参照。

### 重要制約
- **申込ボタン押下そのものは絶対に自動化しない**。誤申込発生時の取り返しがつかないため、ユーザーの明示的な手動操作によってのみ申込が完了する。利用目的の事前入力・自動submitは機能に含めない。
- ゴールは「申込トレイ画面まで到達」のみ。以降はユーザーの手動操作。

### 失敗時の挙動
- プロキシ起動エラー / 会場側エラー → エラー理由をアラートで表示
- ログイン失敗 → エラー理由をアラートで表示
- ユーザーが申込ボタンを押さずにブラウザを閉じた → アプリ側は何もしない（無視する。プロキシセッションは15分無操作でタイムアウト破棄）

## 3. 機能要件

### 3.1 画面仕様

#### 3.1.1 「隣室を予約」ボタン (既存)
- **表示条件**: 現状維持
  - 対象会場ID (Phase 1 では Kaderu自動予約API対応会場、Phase 2 で東区民センターを追加)
  - かつ、隣室空き状況が「○」(空きあり)
- **位置**: [PracticeList.jsx:659](karuta-tracker-ui/src/pages/practice/PracticeList.jsx#L659) 付近のセッション詳細モーダル内
- **押下時挙動**:
  1. ボタンクリック直後に `window.open('about:blank', '_blank')` でタブ確保 (ポップアップブロッカー対策)
  2. `POST /api/venue-reservation-proxy/session` を呼び出し (`venue` パラメータはフロント側で session の venue_id から推論)
  3. レスポンスの `viewUrl` (= `/api/venue-reservation-proxy/view?token=...`) に確保済みタブを遷移
  4. 失敗時はタブを閉じてアラート表示
- **venue 推論ロジック**: practice_session の venue_id をクライアント側で参照し、`KADERU` / `HIGASHI` (Phase 2) のどちらに該当するかを判別 (Phase 1 では実質 KADERU 固定)

#### 3.1.2 プロキシ画面 (新規)
プロキシ経由で表示されるHTMLには、**固定ヘッダーバナー**を注入する:

```
┌─────────────────────────────────────────┐
│ ← アプリに戻る  |  {会場名} 予約画面       │  ← 注入バナー
├─────────────────────────────────────────┤
│   [ 会場サイトのHTML内容 ]                │
└─────────────────────────────────────────┘
```

- 「← アプリに戻る」ボタン: 押下すると同じタブでアプリのカレンダー画面 (`/practice`) に遷移
- 会場名はバナー注入時に `VenueConfig.displayName` から動的に決定 (会場非依存)
- 申込完了検知時: バナーの状態が「予約完了を検知しました」に変化

#### 3.1.3 予約完了検知時のダイアログ (新規)
- 申込完了を検知したらプロキシタブ上でダイアログを表示
  - 文言: 「予約完了したのでアプリに戻ります」
  - ボタン: OK (1つだけ)
- OK押下時:
  - プロキシタブが `/practice` (カレンダー画面) に遷移
  - BroadcastChannel で元のアプリタブに完了通知を送信

#### 3.1.4 元のアプリタブでの即時反映
- 元のアプリタブが開いている場合、BroadcastChannel メッセージを受信
- 該当セッションのデータを再取得し、予約完了状態をUIに反映

#### 3.1.5 既存「予約完了を報告」ボタン (現状維持)
- 自動検知漏れに備え、[PracticeList.jsx:644](karuta-tracker-ui/src/pages/practice/PracticeList.jsx#L644) の手動「予約完了を報告」ボタンはそのまま残す

### 3.2 ビジネスルール

#### 3.2.1 予約完了の自動検知
- 会場側応答を解析し、以下の2条件のいずれかで申込完了を検知:
  - **URL条件**: 会場別の完了URLパターン
  - **HTML文言条件**: 会場別の特徴的文言
- 条件は **会場別 strategy** で定義 ([venues/kaderu.md](venues/kaderu.md), [venues/higashi.md](venues/higashi.md))
- いずれか1つで陽性 → 申込完了とみなす
- 検知したらサーバー側で該当セッションの `reservationConfirmedAt` を自動 set

#### 3.2.2 プロキシセッションのタイムアウト
- プロキシが会場サイトのセッション (PHPSESSID / ASP.NET_SessionId 等) をサーバー側で保持
- **最終アクセスから15分無操作でタイムアウト破棄**
- 申込完了検知時は即座に破棄
- 背景ジョブで5分おきに期限切れセッションをクリーンアップ

#### 3.2.3 同時利用
- 月20回×3分程度の低頻度利用を想定し、**同時利用の排他制御は実装しない**
- 万一同一会場アカウントで並行ログインが発生した場合の挙動は会場側の仕様に委ねる

#### 3.2.4 空き状況verificationのスキップ
- 既存 [open-reserve.js:142-184](scripts/room-checker/open-reserve.js#L142-L184) 相当の「スロット状態確認」ステップを省略 (1-2秒短縮)
- 安全性: `RoomAvailabilityCache` で既に「○」確認済みのため通常は問題なし
- 万一キャッシュが古くて実際は「×」の場合、後続の操作時に会場側がエラーを返す → そのエラーをユーザーに表示

#### 3.2.5 エラーハンドリング

| ケース | 挙動 |
|-------|-----|
| プロキシが会場サイトに到達できない | エラー理由をアラート表示 (例: 「{会場名}への接続に失敗しました」) |
| 会場側でログイン失敗 | エラー理由をアラート表示 |
| スロット確保失敗 (キャッシュ古く実は×) | エラー理由をアラート表示 (例: 「スロットが既に埋まっていました」) |
| 会場が500等のエラー応答 | 会場側のエラー画面をそのままユーザーに透過表示 |
| 15分無操作でタイムアウト | セッションが無効になった旨のエラー表示 |
| ユーザーが申込せずタブを閉じた | 何もしない (無視) |
| **未対応会場が指定された** (Phase 1 で HIGASHI 等) | HTTP 400 + `errorCode: VENUE_NOT_SUPPORTED` |

## 4. 技術設計

### 4.1 API設計

すべて `/api/venue-reservation-proxy/*` 配下。venue は request body / query で明示。

#### 4.1.1 `POST /api/venue-reservation-proxy/session`
プロキシセッション作成 + 申込トレイ画面までの自動遷移を実行。

- **認可**: `@RequireRole({SUPER_ADMIN, ADMIN})`
- **リクエストボディ**:
  ```json
  {
    "venue": "KADERU",
    "practiceSessionId": 123,
    "roomName": "はまなす",
    "date": "2026-04-12",
    "slotIndex": 2
  }
  ```
- **`venue` フィールド**: enum `{ KADERU, HIGASHI }` (Phase 1 では `KADERU` のみ受理、`HIGASHI` は HTTP 400 + `VENUE_NOT_SUPPORTED`)
- **レスポンス (成功)**:
  ```json
  {
    "proxyToken": "uuid-v4-string",
    "viewUrl": "/api/venue-reservation-proxy/view?token=uuid-v4-string",
    "venue": "KADERU"
  }
  ```
- **レスポンス (失敗)**: HTTP 400/500 + `{errorCode, message, venue}`
  - 共通: `VENUE_NOT_SUPPORTED` / `LOGIN_FAILED` / `NOT_AVAILABLE` / `ROOM_NOT_FOUND` / `TIMEOUT` / `SCRIPT_ERROR`
  - 会場別の追加コードは [venues/kaderu.md](venues/kaderu.md), [venues/higashi.md](venues/higashi.md) に記載
- **処理時間**: 5-10秒 (会場別の自動遷移ステップ数による)

#### 4.1.2 `GET /api/venue-reservation-proxy/view?token=...`
事前準備済みの申込トレイ画面HTMLを返却 (ヘッダーバナー注入済み)。

- **認可**: `@RequireRole({SUPER_ADMIN, ADMIN})` + proxyToken有効性検証
- **レスポンス**: 書き換え済みHTML (Content-Type: text/html)

#### 4.1.3 `ANY /api/venue-reservation-proxy/fetch/**?token=...`
ユーザーブラウザが画面内の操作で発生させるリクエストを会場サイトに中継。

- **認可**: `@RequireRole({SUPER_ADMIN, ADMIN})` + proxyToken有効性検証
- **メソッド**: GET / POST / PUT / DELETE 等すべて
- **パス**: `/api/venue-reservation-proxy/fetch/` 以降を会場サイトのパスにマッピング (会場別 origin は `VenueConfig.baseUrl`)
- **処理**:
  - サーバー側保管の Cookie を付与して会場サイトにリクエスト
  - レスポンスの `Set-Cookie` をサーバー側 CookieJar に保存 (ユーザーには返さない)
  - HTML なら書き換え + バナー注入、その他 (CSS/JS/画像) も同じ経路で返却
  - `Location` / `Content-Security-Policy` / `X-Frame-Options` ヘッダを書き換え or 削除
  - 申込完了検知ロジックを発火

### 4.2 DB設計

**新規テーブル・カラムなし。** プロキシセッションは JVM メモリで管理。

既存の `practice_sessions.reservation_confirmed_at` カラムに申込完了検知時のタイムスタンプを set する (既存の手動「予約完了を報告」と同じカラム)。

→ **DBマイグレーション不要** ([CLAUDE.md のDBマイグレーションルール](../../../CLAUDE.md) に抵触しない)

### 4.3 フロントエンド設計

#### 4.3.1 新規ファイル
- `karuta-tracker-ui/src/api/venueReservationProxy.js`
  - `createSession({venue, practiceSessionId, roomName, date, slotIndex})` → `POST /api/venue-reservation-proxy/session`
- `karuta-tracker-ui/src/utils/venueResolver.js`
  - `resolveVenue(practiceSession)` → `KADERU` | `HIGASHI` | `null`
  - venue_id ベースでマッピング (Phase 1 は KADERU のみ実装)

#### 4.3.2 既存ファイル変更
- [PracticeList.jsx](karuta-tracker-ui/src/pages/practice/PracticeList.jsx)
  - `handleReserveAdjacentRoom` を新フローに書き換え:
    1. ボタンクリック直後に `window.open('about:blank', '_blank')` でタブ確保
    2. `resolveVenue(practiceSession)` で venue 判別
    3. `venueReservationProxyAPI.createSession({venue, ...})` 呼び出し
    4. 成功したら `placeholderTab.location.href = response.viewUrl`
    5. 失敗 (`VENUE_NOT_SUPPORTED` 等含む) はタブを閉じてアラート表示
  - mount時に `new BroadcastChannel('venue-reservation-proxy')` を開き、`reservation-completed` メッセージで該当セッションを再取得
  - unmount 時に channel.close()
  - 既存 `kaderuAPI.openReserve` 呼び出しは削除

#### 4.3.3 テスト更新
- [AdjacentRoomFlow.test.jsx](karuta-tracker-ui/src/pages/practice/AdjacentRoomFlow.test.jsx)
  - `kaderuAPI` モック → `venueReservationProxyAPI` モックに置き換え
  - 新APIフロー (`createSession` → `window.open`) に対応したテストに書き直し
  - `VENUE_NOT_SUPPORTED` のエラーケース追加 (Phase 2 への布石)

### 4.4 バックエンド設計

#### 4.4.1 新規クラス構成

```
karuta-tracker/src/main/java/com/karuta/matchtracker/
  ├─ controller/
  │   └─ VenueReservationProxyController.java     — プロキシエンドポイント (venue 非依存)
  ├─ service/proxy/
  │   ├─ VenueReservationProxyService.java        — ファサード (venue 非依存)
  │   ├─ VenueReservationSessionStore.java        — セッション管理 (venue 非依存、ConcurrentHashMap)
  │   ├─ VenueReservationHtmlRewriter.java        — HTML書き換え (venue 非依存、strategy 注入)
  │   ├─ VenueReservationCompletionDetector.java  — 完了検知 (venue 非依存、strategy 委譲)
  │   ├─ ProxySession.java                        — セッションオブジェクト (venue field 含む)
  │   ├─ VenueReservationClient.java              — interface (会場別 HTTP クライアント契約)
  │   ├─ VenueReservationProxyException.java      — 共通例外 (errorCode + message + venue)
  │   └─ venue/
  │       ├─ VenueConfig.java                     — interface (baseUrl, displayName, hidden field map 等)
  │       ├─ VenueRewriteStrategy.java            — interface (URL書き換えルール)
  │       ├─ VenueCompletionStrategy.java         — interface (完了判定ロジック)
  │       ├─ kaderu/
  │       │   ├─ KaderuVenueConfig.java
  │       │   ├─ KaderuReservationClient.java     — VenueReservationClient impl
  │       │   ├─ KaderuRewriteStrategy.java
  │       │   └─ KaderuCompletionStrategy.java
  │       └─ higashi/                              — Phase 2 で実装。Phase 1 では interface 雛形のみ
  │           └─ (空 or HigashiVenueConfig.java の TODO 雛形)
  ├─ dto/
  │   ├─ CreateVenueProxySessionRequest.java     — venue field 必須
  │   └─ CreateVenueProxySessionResponse.java
  └─ config/
      └─ VenueReservationProxyConfig.java        — `venue-reservation-proxy.*` 設定
```

#### 4.4.2 ProxySession (メモリ内オブジェクト)
```java
class ProxySession {
    String token;                        // UUID
    VenueId venue;                       // KADERU | HIGASHI (enum)
    Long practiceSessionId;
    String roomName;
    LocalDate date;
    int slotIndex;
    CookieStore cookies;                 // Apache HttpClient CookieStore (会場別)
    Map<String, String> hiddenFields;    // ASP.NET ViewState 等の最新値 (会場により未使用)
    Instant createdAt;
    Instant lastAccessedAt;
    boolean completed;
    String cachedTrayHtml;
}
```

#### 4.4.3 venue 抽象化レイヤー

`VenueReservationClient` interface:

```java
public interface VenueReservationClient {
    VenueId venue();
    void prepareReservationTray(ProxySession session) throws VenueReservationProxyException;
    HttpResponse fetch(ProxySession session, HttpRequest request) throws VenueReservationProxyException;
}
```

`VenueConfig` interface (registry):

```java
public interface VenueConfig {
    VenueId venue();
    String baseUrl();         // https://k2.p-kashikan.jp 等
    String displayName();     // 「かでる2・7」等
    Duration sessionTimeout();
}
```

DI: `Map<VenueId, VenueReservationClient>` を Spring が自動収集。`VenueReservationProxyService` は venue で dispatch。

Phase 1 では `KaderuReservationClient` のみ実装。Phase 2 で `HigashiReservationClient` を追加するだけで `Map` に自動登録される。

#### 4.4.4 HTML書き換え方針 (会場非依存コア)
- **Jsoup** で以下を書き換え:
  - `<a href>` `<form action>` `<img src>` `<link href>` `<script src>`: 絶対URL or 相対URL → `/api/venue-reservation-proxy/fetch/...` に書き換え (会場別 baseUrl は `VenueConfig` から取得)
  - `<base href>` があれば削除 or 自ドメインに変更
- **正規表現** で以下を書き換え:
  - インライン `<script>` 内の文字列URL
  - インライン `<style>` 内の `url(...)`
- 先頭に**注入スクリプト**を挿入 (会場非依存):
  - `Location.assign/replace` / `window.open` / `fetch` / `XMLHttpRequest.open` のフック
  - 会場別の `__doPostBack` 等のフック追加は `VenueRewriteStrategy.injectScript()` で会場別追記
- 先頭に**ヘッダーバナー**を挿入 (会場非依存):
  - 「← アプリに戻る」ボタン
  - `{venue.displayName} 予約画面` のタイトル
  - BroadcastChannel 発信ロジック
  - 申込完了検知時のダイアログロジック

会場別書き換えは `VenueRewriteStrategy` interface で:

```java
public interface VenueRewriteStrategy {
    VenueId venue();
    String rewriteHtml(String html, String proxyToken);   // 会場固有の事前/事後処理
    String injectScript();                                 // 注入スクリプトに追記する会場固有コード
}
```

#### 4.4.5 CookieJar管理
- 各 `ProxySession` が独自の `CookieStore` を持つ (会場非依存)
- ユーザーブラウザには会場サイトの Cookie を露出させない (`Set-Cookie` ヘッダはレスポンスから削除)
- 自ドメインの Cookie は proxyToken のみ (URLパラメータで渡す)

#### 4.4.6 ヘッダ書き換え
- **削除**: `Set-Cookie`, `X-Frame-Options`, `Strict-Transport-Security`
- **書き換え**: `Location` (会場サイト絶対URL → 自ドメイン)、`Content-Security-Policy` (会場サイト参照を自ドメインに置換 or 削除)
- **透過**: `Content-Type`, `Content-Length` (ボディ書き換え後は再計算) 等

#### 4.4.7 申込完了検知 (会場非依存コア + strategy)

```java
public interface VenueCompletionStrategy {
    VenueId venue();
    boolean isCompletion(String requestUrl, String responseLocation, String responseBody);
}
```

- `VenueReservationCompletionDetector` は fetch レスポンス毎に該当 venue の strategy を呼び出す
- 陽性なら `practice_sessions.reservation_confirmed_at` を更新 + `ProxySession.completed = true`

#### 4.4.8 セッションタイムアウト
- `@Scheduled(fixedDelay = 5min)` で `VenueReservationSessionStore` をスキャン
- `lastAccessedAt` が15分以上経過しているものを削除
- `completed = true` のセッションは即座に削除

### 4.5 設定値

#### 4.5.1 application.yml
```yaml
venue-reservation-proxy:
  enabled: true
  session-timeout-minutes: 15
  cleanup-interval-minutes: 5
  request-timeout-seconds: 30
  venues:
    kaderu:
      enabled: true
      base-url: https://k2.p-kashikan.jp
      user-id: ${KADERU_USER_ID:}
      password: ${KADERU_PASSWORD:}
    higashi:
      enabled: false       # Phase 2 で true に
      base-url: https://www.sapporo-community.jp
      user-id: ${SAPPORO_COMMUNITY_USER_ID:}
      password: ${SAPPORO_COMMUNITY_PASSWORD:}
```

`enabled: false` の venue にリクエストが来たら HTTP 400 + `VENUE_NOT_SUPPORTED`。

#### 4.5.2 Render環境変数
- `KADERU_USER_ID` (既存を流用)
- `KADERU_PASSWORD` (既存を流用)
- `SAPPORO_COMMUNITY_USER_ID` (既存、Phase 2 で利用)
- `SAPPORO_COMMUNITY_PASSWORD` (既存、Phase 2 で利用)
- 削除: `KADERU_ENABLED` / `KADERU_SCRIPT_PATH` / `KADERU_NODE_COMMAND`

### 4.6 テスト戦略
- **ユニットテスト**: 会場のモックサーバー (WireMock等) を立てて、HTML書き換え・Cookie管理・申込完了検知を単体テスト
  - `VenueReservationHtmlRewriter` / `VenueReservationCompletionDetector` は会場非依存テスト
  - `KaderuReservationClient` / `KaderuRewriteStrategy` / `KaderuCompletionStrategy` は kaderu固有テスト
- **E2Eテスト**: 実会場サイトへの接続確認はローカル開発時のみ手動実施 (自動CI化しない)

## 5. 影響範囲

### 5.1 新規作成ファイル

#### バックエンド
- `controller/VenueReservationProxyController.java`
- `service/proxy/VenueReservationProxyService.java`
- `service/proxy/VenueReservationSessionStore.java`
- `service/proxy/VenueReservationHtmlRewriter.java`
- `service/proxy/VenueReservationCompletionDetector.java`
- `service/proxy/ProxySession.java`
- `service/proxy/VenueReservationClient.java` (interface)
- `service/proxy/VenueReservationProxyException.java`
- `service/proxy/venue/VenueConfig.java` (interface)
- `service/proxy/venue/VenueRewriteStrategy.java` (interface)
- `service/proxy/venue/VenueCompletionStrategy.java` (interface)
- `service/proxy/venue/kaderu/KaderuVenueConfig.java`
- `service/proxy/venue/kaderu/KaderuReservationClient.java`
- `service/proxy/venue/kaderu/KaderuRewriteStrategy.java`
- `service/proxy/venue/kaderu/KaderuCompletionStrategy.java`
- `dto/CreateVenueProxySessionRequest.java`
- `dto/CreateVenueProxySessionResponse.java`
- `config/VenueReservationProxyConfig.java`
- 対応するテストクラス群

#### フロントエンド
- `karuta-tracker-ui/src/api/venueReservationProxy.js`
- `karuta-tracker-ui/src/utils/venueResolver.js`

#### ドキュメント (要件定義書付随)
- `docs/features/venue-reservation-proxy/venues/kaderu.md`
- `docs/features/venue-reservation-proxy/venues/higashi.md`

### 5.2 変更対象ファイル
- [karuta-tracker-ui/src/pages/practice/PracticeList.jsx](karuta-tracker-ui/src/pages/practice/PracticeList.jsx) — venue 推論 + API 差し替え + BroadcastChannel
- [karuta-tracker-ui/src/pages/practice/AdjacentRoomFlow.test.jsx](karuta-tracker-ui/src/pages/practice/AdjacentRoomFlow.test.jsx) — モック差し替え
- `karuta-tracker/src/main/resources/application.yml` — `venue-reservation-proxy.*` 追加、旧 `kaderu.*` 削除
- `karuta-tracker/build.gradle` — Jsoup 依存追加

### 5.3 削除対象ファイル (未使用確認後)

#### バックエンド
- [KaderuReservationController.java](karuta-tracker/src/main/java/com/karuta/matchtracker/controller/KaderuReservationController.java)
- [KaderuReservationService.java](karuta-tracker/src/main/java/com/karuta/matchtracker/service/KaderuReservationService.java)
- `KaderuReservationServiceTest.java` (該当あれば)
- `PracticeSessionControllerTest.java` 内の kaderu 関連テスト (該当あれば)

#### フロント
- [karuta-tracker-ui/src/api/kaderu.js](karuta-tracker-ui/src/api/kaderu.js)

#### スクリプト
- [scripts/room-checker/open-reserve.js](scripts/room-checker/open-reserve.js)
- `package.json` の Playwright依存 (他で使っていなければ)

### 5.4 既存機能への影響

| 機能 | 影響 | 備考 |
|-----|-----|-----|
| [higashi-adjacent-room-check](docs/features/higashi-adjacent-room-check/) | 軽微 | Phase 2 で有効化後、ボタン表示条件を東区民センターに拡張する想定 |
| [adjacent-room-check](docs/features/adjacent-room-check/) | あり (連携) | 本機能の前段、表示条件「○」連動 |
| [higashi-community-center-sync](docs/features/higashi-community-center-sync/) | なし | スクレイプ系機能とは独立 |
| [higashi-reservation-flow-exploration](docs/features/higashi-reservation-flow-exploration/) | 完了済み | findings.md を Phase 2 設計の入力として利用 |
| セッション詳細モーダルの他機能 | 軽微 | 「隣室を予約」ボタンのハンドラー差し替えのみ |
| `practice_sessions.reservation_confirmed_at` カラム | 書き込みパス追加 | 既存の手動報告と同一カラム |

### 5.5 共通コンポーネント・ユーティリティへの影響
- なし

### 5.6 API互換性
- 新規エンドポイント `/api/venue-reservation-proxy/**` 追加
- 既存 `/api/kaderu/open-reserve` と `/api/kaderu/open-reserve-by-venue` は削除 (フロント側の呼び出しも同時に削除されるので破壊的変更なし)

### 5.7 DBスキーマ互換性
- スキーマ変更なし
- DBマイグレーションSQL不要

## 6. 設計判断の根拠

### 6.1 なぜ最初から venue 抽象化を組み込むか
[higashi-reservation-flow-exploration/findings.md §10.4](../higashi-reservation-flow-exploration/findings.md) の比較表で、3案の合計工数は:

| 選択肢 | 初期工数 | higashi追加工数 | 合計 |
|-------|:------:|:-------------:|:---:|
| **A. 統合設計 (本方針)** | kaderu 1.2x | higashi 0.4x | **1.6x** |
| B. 別々実装 | 1.0x | 1.1x | 2.1x |
| C. kaderu独立→後でリファクタ | 1.0x | 0.8x | 1.8x |

kaderu 未着手の今こそ抽象化を入れる一度の投資で higashi 追加工数が最小化される。Phase 1 の追加コスト 0.2x (interface 設計) で Phase 2 の工数 0.7x を圧縮できる試算。

### 6.2 なぜリバースプロキシ方式を選んだか
事前議論で検討した「リモートブラウザストリーミング」方式と比較:

| 観点 | リバースプロキシ (採用) | リモートブラウザ |
|-----|------|------|
| 月額 | **+$0** | $0〜$25 (プラン次第) |
| 月200円予算クリア | ◎ 確実 | △ 条件付き |
| UX遅延 | なし | 100-200ms |
| スマホIME | ネイティブでそのまま使える | 自作実装必須 |
| 画質 | 完全 | JPEG 10-15FPSで劣化 |
| 利用頻度月60分 | まったく問題なし | まったく問題なし |

利用頻度が月20回×3分程度と低く、「サイト変更耐性」という唯一リバースプロキシが不利な観点が「工数/サイト変更は無視してよい」という前提条件により無効化されたため、プロキシ方式を採用。

### 6.3 なぜプロキシ専用トークンを発行するか
本アプリの認証トークンを直接プロキシで使うと、URLパラメータやログに露出するリスクがある。短命の専用トークンを発行することで露出範囲とリスクを限定する。

### 6.4 なぜJVMメモリ管理を選んだか
- 15分タイムアウトでセッションが自然消滅するため永続化不要
- DB書き込みオーバーヘッドなし
- インスタンス再起動で消えるが、15分で消える性質なので実害なし

### 6.5 なぜ空き状況verificationをスキップするか
`RoomAvailabilityCache` で既に「○」確認済みのため、サーバー側で再度DOM解析してverificationする意味が薄い。1-2秒の短縮効果があり、失敗時も後続ステップで明示的エラーが取れるため安全。

### 6.6 なぜ同時利用排他制御を実装しないか
月20回×3分程度の低頻度利用を想定し、実運用上で同時利用が発生する確率は極めて低い。会場側の1アカウント1セッション制約による影響も同様に実運用上ほぼ発生しない見込み。

### 6.7 なぜBroadcastChannelを採用するか
- 同一オリジン内のタブ間通信に最適
- サーバーを介さず即時反映できる
- WebSocket/SSE のような追加インフラ不要
- 本機能の利用シナリオ (プロキシタブ→元タブ) と完全一致

### 6.8 なぜ申込そのものは自動化しないか
誤申込発生時の取り返しがつかないため、申込ボタン押下・利用目的入力は必ずユーザーの明示的な手動操作によってのみ実行される設計とする。自動化は**絶対に実装しない**。

### 6.9 なぜ Phase 2 (higashi) を別ブランチにするか
- Phase 1 完了で kaderu の実機検証 + 設計妥当性検証が済む
- higashi 固有の未確定要素 (完了画面 URL/文言、Incapsula WAF 挙動) は本番で1回実申込してから埋める必要がある — その実機ステップを Phase 1 の PR に含めると差し戻しリスクが大きい
- 抽象化が正しく効いていれば Phase 2 の差分は会場別 strategy 3 ファイル + Config 1 ファイル + DI 登録程度に収まる想定

### 6.10 既知のリスクと認識
以下のリスクは認識した上で、利用頻度が月20回程度と極めて低いため実運用上の問題は発生しない見込みとして本機能を開発する判断をした:

- 各会場サイトの ToS / robots.txt に関する確認は別途実施予定
- Render のIPからのアクセスに対する会場側のレート制限・BAN のリスク
- 認証情報は Render 環境変数で管理しコードベースには露出させない
- 東区民センターは Imperva Incapsula WAF 配下のため、Phase 2 では特に低頻度運用を維持する

## 7. 会場別仕様書

会場固有の URL / DOM / ナビゲーション / hidden field / 完了検知パターン等は会場別ドキュメントに分離:

- [venues/kaderu.md](venues/kaderu.md) — かでる2・7 (PHP)
- [venues/higashi.md](venues/higashi.md) — 東区民センター (ASP.NET WebForms, Phase 2)

会場別ドキュメントは `VenueConfig` / `VenueReservationClient` impl の実装時の参照先として位置付ける。
