---
status: completed
---
# kaderu-reservation-proxy 要件定義書

## 1. 概要

### 目的
本番環境（Render）でも、練習管理者がスマホから「かでる2・7」の隣室予約申込トレイ画面まで到達できるようにする。

### 背景・動機
現状の [KaderuReservationService](karuta-tracker/src/main/java/com/karuta/matchtracker/service/KaderuReservationService.java) はサーバー上で Playwright を起動してブラウザを開く設計になっており、ローカル開発時のみ機能する。本番（Render）では `kaderu.enabled=false` でありユーザーのブラウザに予約画面が表示されず、「隣室を予約」ボタンが実質的に動作しない。

本機能により、バックエンドが「かでる2・7」へのリバースプロキシとして動作し、ユーザーのブラウザは自ドメイン (`/api/kaderu-proxy/*`) 経由で「かでる2・7」を操作する。これによりユーザーのスマホ画面で申込トレイ画面まで到達できる。

## 2. ユーザーストーリー

### 対象ユーザー
- SUPER_ADMIN / ADMIN ロールの管理者のみ
- PLAYER ロールには本機能を公開しない

### ユーザーの目的
競技かるた練習会で隣室が必要になった際、スマホからアプリ経由で「かでる2・7」の予約申込トレイ画面まで到達し、利用目的を入力して申込みを完了させる。

### 利用シナリオ
1. 管理者がスマホで [PracticeList.jsx](karuta-tracker-ui/src/pages/practice/PracticeList.jsx) のセッション詳細モーダルを開く
2. 隣室空き状況が「○」(空きあり)と表示されているのを確認
3. 「隣室を予約」ボタンを押す
4. 元のタブにスピナー表示「かでる2・7に接続中...」（5-10秒）
5. バックエンドがログイン〜申込トレイまで自動遷移完了
6. 新しいタブで「かでる2・7」の申込トレイ画面が即座に開く
7. ユーザーが利用目的を手動入力し、「申込み」ボタンを手動で押下する
8. プロキシが申込完了画面到達を自動検知し `reservationConfirmedAt` を記録する
9. ダイアログ「予約完了したのでアプリに戻ります」→ OK押下でアプリのカレンダー画面に遷移する
10. 元のアプリタブ（開いていれば）も BroadcastChannel 経由で即時に予約完了状態に反映される

### 重要制約
- **申込ボタン押下そのものは絶対に自動化しない**。誤申込発生時の取り返しがつかないため、ユーザーの明示的な手動操作によってのみ申込が完了する。利用目的の事前入力・自動submitは機能に含めない。
- ゴールは「申込トレイ画面まで到達」のみ。以降はユーザーの手動操作。

### 失敗時の挙動
- プロキシ起動エラー / kaderu側エラー → エラー理由をアラートで表示
- ログイン失敗 → エラー理由をアラートで表示
- ユーザーが申込ボタンを押さずにブラウザを閉じた → アプリ側は何もしない（無視する。プロキシセッションは15分無操作でタイムアウト破棄）

## 3. 機能要件

### 3.1 画面仕様

#### 3.1.1 「隣室を予約」ボタン（既存）
- 表示条件: **現状維持**
  - 対象会場ID（Kaderu 自動予約API対応の会場）
  - かつ、隣室空き状況が「○」(空きあり)
- 位置: [PracticeList.jsx:659](karuta-tracker-ui/src/pages/practice/PracticeList.jsx#L659) 付近のセッション詳細モーダル内
- 押下時挙動: 新API `POST /api/kaderu-proxy/session` を呼んだ後、取得した `proxyToken` で `window.open('/api/kaderu-proxy/view?token=...', '_blank')`
- ポップアップブロッカー対策: ボタンクリック直後に `window.open('about:blank', '_blank')` でタブ確保 → API完了後に `location.href` 書き換え

#### 3.1.2 プロキシ画面（新規）
プロキシ経由で表示されるHTMLには、**固定ヘッダーバナー**を注入する：

```
┌─────────────────────────────────────────┐
│ ← アプリに戻る  |  かでる2・7 予約画面      │  ← 注入バナー
├─────────────────────────────────────────┤
│   [ kaderu2・7 のHTML内容 ]              │
└─────────────────────────────────────────┘
```

- 「← アプリに戻る」ボタン: 押下すると同じタブでアプリのカレンダー画面 (`/practice`)に遷移
- 申込完了検知時: バナーの状態が「予約完了を検知しました」に変化

#### 3.1.3 予約完了検知時のダイアログ（新規）
- 申込完了を検知したらプロキシタブ上でダイアログを表示
  - 文言: 「予約完了したのでアプリに戻ります」
  - ボタン: OK（1つだけ）
- OK押下時:
  - プロキシタブが `/practice`（カレンダー画面）に遷移
  - BroadcastChannelで元のアプリタブに完了通知を送信

#### 3.1.4 元のアプリタブでの即時反映
- 元のアプリタブが開いている場合、BroadcastChannel メッセージを受信
- 該当セッションのデータを再取得し、予約完了状態をUIに反映

#### 3.1.5 既存「予約完了を報告」ボタン（現状維持）
- 自動検知漏れに備え、[PracticeList.jsx:644](karuta-tracker-ui/src/pages/practice/PracticeList.jsx#L644) の手動「予約完了を報告」ボタンはそのまま残す

### 3.2 ビジネスルール

#### 3.2.1 予約完了の自動検知
- kaderu側の応答を解析し、以下の2条件のいずれかで申込完了を検知：
  - **URL条件**: 申込完了に遷移する特定URL（`?p=rsv_comp` 的なもの、実装時に実機で確認）
  - **HTML文言条件**: 完了画面に特徴的な文言（例: 「申込みを受け付けました」「申込番号」等）の検出
- いずれか1つで陽性 → 申込完了とみなす
- 検知したらサーバー側で該当セッションの `reservationConfirmedAt` を自動 set

#### 3.2.2 プロキシセッションのタイムアウト
- プロキシがkaderu2・7のセッション（PHPSESSID等）をサーバー側で保持
- **最終アクセスから15分無操作でタイムアウト破棄**
- 申込完了検知時は即座に破棄
- 背景ジョブで5分おきに期限切れセッションをクリーンアップ

#### 3.2.3 同時利用
- 月20回×3分程度の低頻度利用を想定し、**同時利用の排他制御は実装しない**
- 万一同一kaderuアカウントで並行ログインが発生した場合の挙動は kaderu 側の仕様に委ねる

#### 3.2.4 空き状況verificationのスキップ
- [open-reserve.js:142-184](scripts/room-checker/open-reserve.js#L142-L184) 相当の「スロット状態確認」ステップを省略（1-2秒短縮）
- 安全性: `RoomAvailabilityCache` で既に`○`確認済みのため通常は問題なし
- 万一キャッシュが古くて実際は`×`の場合、後続の setAppStatus or requestBtn クリック時に kaderu 側がエラーを返す → そのエラーをユーザーに表示

#### 3.2.5 エラーハンドリング
| ケース | 挙動 |
|-------|-----|
| プロキシがkaderuに到達できない | エラー理由をアラートで表示（例: 「かでる2・7への接続に失敗しました」） |
| kaderu側でログイン失敗 | エラー理由をアラートで表示（例: 「かでる2・7へのログインに失敗しました」） |
| スロット確保失敗（キャッシュ古く実は×） | エラー理由をアラートで表示（例: 「スロットが既に埋まっていました」） |
| kaderuが500等のエラー応答 | kaderu側のエラー画面をそのままユーザーに透過表示 |
| 15分無操作でタイムアウト | セッションが無効になった旨のエラー表示 |
| ユーザーが申込せずタブを閉じた | 何もしない（無視） |

## 4. 技術設計

### 4.1 API設計

#### 4.1.1 `POST /api/kaderu-proxy/session`
プロキシセッション作成 + 申込トレイ画面までの自動遷移を実行。

- **認可**: `@RequireRole({SUPER_ADMIN, ADMIN})`
- **リクエストボディ**:
  ```json
  {
    "practiceSessionId": 123,
    "roomName": "はまなす",
    "date": "2026-04-12",
    "slotIndex": 2
  }
  ```
- **レスポンス（成功）**:
  ```json
  {
    "proxyToken": "uuid-v4-string",
    "viewUrl": "/api/kaderu-proxy/view?token=uuid-v4-string"
  }
  ```
- **レスポンス（失敗）**: HTTP 400/500 + `{errorCode, message}`（LOGIN_FAILED / NOT_AVAILABLE / ROOM_NOT_FOUND / TIMEOUT / SCRIPT_ERROR 等）
- **処理時間**: 5-10秒（kaderuへの6ステップ順次実行）

#### 4.1.2 `GET /api/kaderu-proxy/view?token=...`
事前準備済みの申込トレイ画面HTMLを返却（ヘッダーバナー注入済み）。

- **認可**: `@RequireRole({SUPER_ADMIN, ADMIN})` + proxyToken有効性検証
- **レスポンス**: 書き換え済みHTML（Content-Type: text/html）

#### 4.1.3 `ANY /api/kaderu-proxy/fetch/**?token=...`
ユーザーブラウザが画面内の操作で発生させるリクエストを kaderu に中継。

- **認可**: `@RequireRole({SUPER_ADMIN, ADMIN})` + proxyToken有効性検証
- **メソッド**: GET / POST / PUT / DELETE 等すべて
- **パス**: `/api/kaderu-proxy/fetch/` 以降を kaderu のパスにマッピング
- **処理**:
  - サーバー側保管の Cookie（PHPSESSID等）を付与して kaderu にリクエスト
  - レスポンスの `Set-Cookie` をサーバー側 CookieJar に保存（ユーザーには返さない）
  - HTML なら書き換え + バナー注入、その他（CSS/JS/画像）も同じ経路で返却（Q8推奨の通り全プロキシ経由）
  - `Location` / `Content-Security-Policy` / `X-Frame-Options` ヘッダを書き換え or 削除
  - 申込完了検知ロジックを発火

### 4.2 DB設計

**新規テーブル・カラムなし。** プロキシセッションは JVM メモリで管理。

既存の `practice_sessions.reservation_confirmed_at` カラムに申込完了検知時のタイムスタンプを set する（既存の手動「予約完了を報告」と同じカラム）。

→ **DBマイグレーション不要** ([CLAUDE.md のDBマイグレーションルール](CLAUDE.md) に抵触しない)

### 4.3 フロントエンド設計

#### 4.3.1 新規ファイル
- `karuta-tracker-ui/src/api/kaderuProxy.js`
  - `createSession({practiceSessionId, roomName, date, slotIndex})` → POST /api/kaderu-proxy/session
  
#### 4.3.2 既存ファイル変更
- [PracticeList.jsx](karuta-tracker-ui/src/pages/practice/PracticeList.jsx)
  - `handleReserveAdjacentRoom` を書き換え：
    1. ボタンクリック直後に `window.open('about:blank', '_blank')` でタブ確保（ポップアップブロッカー対策）
    2. `kaderuProxyAPI.createSession(...)` 呼び出し
    3. 成功したら `placeholderTab.location.href = response.viewUrl`
    4. 失敗したら `placeholderTab.close()` + アラート表示
  - コンポーネント mount 時に BroadcastChannel リスナーを登録
    - `{type: 'reservation-completed', practiceSessionId}` 受信時、該当セッションのデータを再取得してUI更新
  - 既存の `kaderuAPI.openReserve` 呼び出しは削除

#### 4.3.3 テスト更新
- [AdjacentRoomFlow.test.jsx](karuta-tracker-ui/src/pages/practice/AdjacentRoomFlow.test.jsx)
  - `kaderuAPI` モック → `kaderuProxyAPI` モックに置き換え
  - 新APIフロー（`createSession` → `window.open`）に対応したテストに書き直し

### 4.4 バックエンド設計

#### 4.4.1 新規クラス構成

```
karuta-tracker/src/main/java/com/karuta/matchtracker/
  ├─ controller/
  │   └─ KaderuProxyController.java      — プロキシエンドポイント
  ├─ service/
  │   ├─ KaderuProxyService.java         — プロキシロジック統括
  │   ├─ KaderuProxySessionStore.java    — セッション管理（ConcurrentHashMap）
  │   ├─ KaderuProxyClient.java          — kaderu への HTTP クライアント
  │   ├─ KaderuHtmlRewriter.java         — HTML書き換え（Jsoup + 正規表現）
  │   └─ KaderuReservationCompletionDetector.java — 申込完了検知ロジック
  ├─ dto/
  │   ├─ CreateProxySessionRequest.java
  │   └─ CreateProxySessionResponse.java
  └─ config/
      └─ KaderuProxyConfig.java          — 設定値
```

#### 4.4.2 ProxySession（メモリ内オブジェクト）
```java
class ProxySession {
    String token;                        // UUID
    Long practiceSessionId;
    String roomName;
    LocalDate date;
    int slotIndex;
    CookieStore kaderuCookies;           // Apache HttpClient CookieStore
    Instant createdAt;
    Instant lastAccessedAt;
    boolean completed;                   // 申込完了検知済みフラグ
}
```

#### 4.4.3 初回遷移フロー（POST /api/kaderu-proxy/session）
1. `ProxySession` を新規生成
2. Apache HttpClient / Spring WebClient で順次実行：
   - ログイン（POST）
   - マイページ遷移
   - 空き状況ページ遷移
   - 月合わせ（必要時）
   - 日付クリック（POST）
   - **空き状況verificationはスキップ**
   - スロット選択（`setAppStatus` 相当のPOST）
   - 申込トレイに入れる（`requestBtn` 相当のPOST）
3. 最終HTMLを `ProxySession.cachedTrayHtml` に保存
4. `token` をレスポンスで返す

#### 4.4.4 HTML書き換え方針
- **Jsoup** で以下を書き換え：
  - `<a href>` `<form action>` `<img src>` `<link href>` `<script src>`: 絶対URL or 相対URL → `/api/kaderu-proxy/fetch/...` に書き換え
  - `<base href>` があれば削除 or 自ドメインに変更
- **正規表現** で以下を書き換え：
  - インライン `<script>` 内の文字列URL
  - インライン `<style>` 内の `url(...)`
- 先頭に**注入スクリプト**を挿入：
  ```javascript
  (function() {
    const KADERU_ORIGIN = 'https://k2.p-kashikan.jp';
    const PROXY_PREFIX = '/api/kaderu-proxy/fetch';
    const rewrite = (u) => typeof u === 'string' 
      ? u.replace(KADERU_ORIGIN, location.origin + PROXY_PREFIX) : u;
    // Location, window.open, fetch, XMLHttpRequest をフック
  })();
  ```
- 先頭に**ヘッダーバナー HTML + スタイル + JS**を挿入：
  - 「← アプリに戻る」ボタン
  - 状態表示エリア
  - BroadcastChannel 発信ロジック
  - 申込完了検知時のダイアログ表示ロジック

#### 4.4.5 CookieJar管理
- 各 `ProxySession` が独自の `CookieStore` を持つ
- ユーザーブラウザには kaderu の Cookie を露出させない（`Set-Cookie` ヘッダはレスポンスから削除）
- 自ドメインの Cookie は proxyToken のみ（URLパラメータで渡す）

#### 4.4.6 ヘッダ書き換え
- **削除**: `Set-Cookie`, `X-Frame-Options`, `Strict-Transport-Security`
- **書き換え**: `Location` (kaderu絶対URL→自ドメイン)、`Content-Security-Policy`（kaderu参照を自ドメインに置換 or 削除）
- **透過**: `Content-Type`, `Content-Length`（ボディ書き換え後は再計算）等

#### 4.4.7 申込完了検知
`KaderuReservationCompletionDetector` が fetch レスポンス毎に以下を評価：
- URL判定: リクエストURL or レスポンスの `Location` ヘッダに特定パターンが含まれるか
- HTML判定: レスポンスボディに特定文言が含まれるか
- いずれか陽性 → 該当 `ProxySession.practiceSessionId` の `reservationConfirmedAt` を更新 + `ProxySession.completed = true`
- 実際のマッチ条件は実装時に kaderu 実機で確認して確定

#### 4.4.8 セッションタイムアウト
- `@Scheduled(fixedDelay = 5min)` で `KaderuProxySessionStore` をスキャン
- `lastAccessedAt` が15分以上経過しているものを削除
- `completed = true` のセッションは即座に削除

### 4.5 設定値

#### 4.5.1 新規 application.yml
```yaml
kaderu-proxy:
  enabled: true                      # 本番で有効化
  user-id: ${KADERU_USER_ID:}        # 既存環境変数を流用
  password: ${KADERU_PASSWORD:}      # 既存環境変数を流用
  session-timeout-minutes: 15
  cleanup-interval-minutes: 5
  request-timeout-seconds: 30
```

#### 4.5.2 Render環境変数
- `KADERU_USER_ID` (既存を流用)
- `KADERU_PASSWORD` (既存を流用)
- 削除: `KADERU_ENABLED` / `KADERU_SCRIPT_PATH` / `KADERU_NODE_COMMAND`

### 4.6 テスト戦略
- **ユニットテスト**: kaderu のモックサーバー（WireMock等）を立てて、HTML書き換え・Cookie管理・申込完了検知を単体テスト
- **E2Eテスト**: 実 kaderu への接続確認はローカル開発時のみ手動実施（自動CI化しない）

## 5. 影響範囲

### 5.1 新規作成ファイル

#### バックエンド
- `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/KaderuProxyController.java`
- `karuta-tracker/src/main/java/com/karuta/matchtracker/service/KaderuProxyService.java`
- `karuta-tracker/src/main/java/com/karuta/matchtracker/service/KaderuProxySessionStore.java`
- `karuta-tracker/src/main/java/com/karuta/matchtracker/service/KaderuProxyClient.java`
- `karuta-tracker/src/main/java/com/karuta/matchtracker/service/KaderuHtmlRewriter.java`
- `karuta-tracker/src/main/java/com/karuta/matchtracker/service/KaderuReservationCompletionDetector.java`
- `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/CreateProxySessionRequest.java`
- `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/CreateProxySessionResponse.java`
- `karuta-tracker/src/main/java/com/karuta/matchtracker/config/KaderuProxyConfig.java`
- 対応するテストクラス群

#### フロントエンド
- `karuta-tracker-ui/src/api/kaderuProxy.js`

### 5.2 変更対象ファイル

- [karuta-tracker-ui/src/pages/practice/PracticeList.jsx](karuta-tracker-ui/src/pages/practice/PracticeList.jsx)
  - `handleReserveAdjacentRoom` のAPIコール変更
  - BroadcastChannel リスナー追加
  - `kaderuAPI` import削除
- [karuta-tracker-ui/src/pages/practice/AdjacentRoomFlow.test.jsx](karuta-tracker-ui/src/pages/practice/AdjacentRoomFlow.test.jsx)
  - モック対象を `kaderuProxyAPI` に置き換え、新フローに対応
- `karuta-tracker/src/main/resources/application.yml`
  - `kaderu-proxy.*` 設定追加、旧 `kaderu.*` 設定削除
- `karuta-tracker/build.gradle`
  - Jsoup 依存追加

### 5.3 削除対象ファイル（未使用確認後）

#### バックエンド
- [karuta-tracker/src/main/java/com/karuta/matchtracker/controller/KaderuReservationController.java](karuta-tracker/src/main/java/com/karuta/matchtracker/controller/KaderuReservationController.java)
- [karuta-tracker/src/main/java/com/karuta/matchtracker/service/KaderuReservationService.java](karuta-tracker/src/main/java/com/karuta/matchtracker/service/KaderuReservationService.java)
- `KaderuReservationServiceTest.java`（該当テスト）
- `PracticeSessionControllerTest.java` 内の kaderu 関連テスト（該当あれば）

#### フロント
- [karuta-tracker-ui/src/api/kaderu.js](karuta-tracker-ui/src/api/kaderu.js)

#### スクリプト
- [scripts/room-checker/open-reserve.js](scripts/room-checker/open-reserve.js)
- `package.json` の Playwright依存（他で使っていなければ）

### 5.4 既存機能への影響

| 機能 | 影響 | 備考 |
|-----|-----|-----|
| [higashi-adjacent-room-check](docs/features/higashi-adjacent-room-check/) | なし | 東区民センターはKaderu対応外、ボタン表示条件で除外済み |
| [adjacent-room-check](docs/features/adjacent-room-check/) | あり（連携） | 本機能の前段、表示条件「○」連動 |
| [higashi-community-center-sync](docs/features/higashi-community-center-sync/) | なし | 同上 |
| セッション詳細モーダルの他機能 | 軽微 | 「隣室を予約」ボタンのハンドラー差し替えのみ |
| `practice_sessions.reservation_confirmed_at` カラム | 書き込みパス追加 | 既存の手動報告と同一カラム |

### 5.5 共通コンポーネント・ユーティリティへの影響
- なし

### 5.6 API互換性
- 新規エンドポイント `/api/kaderu-proxy/**` 追加
- 既存 `/api/kaderu/open-reserve` と `/api/kaderu/open-reserve-by-venue` は削除（フロント側からの呼び出しも同時に削除されるので破壊的変更なし）

### 5.7 DBスキーマ互換性
- スキーマ変更なし
- DBマイグレーションSQL不要

## 6. 設計判断の根拠

### 6.1 なぜリバースプロキシ方式を選んだか
事前議論で検討した「リモートブラウザストリーミング」方式と比較した結果：

| 観点 | リバースプロキシ（採用） | リモートブラウザ |
|-----|------|------|
| 月額 | **+$0** | $0〜$25（プラン次第） |
| 月200円予算クリア | ◎ 確実 | △ 条件付き |
| UX遅延 | なし | 100-200ms |
| スマホIME | ネイティブでそのまま使える | 自作実装必須 |
| 画質 | 完全 | JPEG 10-15FPSで劣化 |
| 利用頻度月60分 | まったく問題なし | まったく問題なし |

利用頻度が月20回×3分程度と低く、「サイト変更耐性」という唯一リバースプロキシが不利な観点が「工数/サイト変更は無視してよい」という前提条件により無効化されたため、プロキシ方式を採用。

### 6.2 なぜプロキシ専用トークンを発行するか
本アプリの認証トークンを直接プロキシで使うと、URLパラメータやログに露出するリスクがある。短命の専用トークンを発行することで露出範囲とリスクを限定する。

### 6.3 なぜJVMメモリ管理を選んだか
- 15分タイムアウトでセッションが自然消滅するため永続化不要
- DB書き込みオーバーヘッドなし
- インスタンス再起動で消えるが、15分で消える性質なので実害なし

### 6.4 なぜ空き状況verificationをスキップするか
`RoomAvailabilityCache` で既に`○`確認済みのため、サーバー側で再度DOM解析してverificationする意味が薄い。1-2秒の短縮効果があり、失敗時も後続ステップで明示的エラーが取れるため安全。

### 6.5 なぜ同時利用排他制御を実装しないか
月20回×3分程度の低頻度利用を想定し、実運用上で同時利用が発生する確率は極めて低い。kaderu側の1アカウント1セッション制約による影響も同様に実運用上ほぼ発生しない見込み。

### 6.6 なぜBroadcastChannelを採用するか
- 同一オリジン内のタブ間通信に最適
- サーバーを介さず即時反映できる
- WebSocket/SSE のような追加インフラ不要
- 本機能の利用シナリオ（プロキシタブ→元タブ）と完全一致

### 6.7 既知のリスクと認識
以下のリスクは認識した上で、利用頻度が月20回程度と極めて低いため実運用上の問題は発生しない見込みとして、本機能を開発する判断をした：

- kaderu2・7のToS / robots.txt に関する確認は別途実施予定
- Render のIPからのアクセスに対する kaderu 側のレート制限・BAN のリスク
- 認証情報（`KADERU_USER_ID` / `KADERU_PASSWORD`）はRender環境変数で管理しコードベースには露出させない

### 6.8 なぜ申込そのものは自動化しないか
誤申込発生時の取り返しがつかないため、申込ボタン押下・利用目的入力は必ずユーザーの明示的な手動操作によってのみ実行される設計とする。自動化は**絶対に実装しない**。
