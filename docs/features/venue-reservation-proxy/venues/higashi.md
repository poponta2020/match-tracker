# 会場別仕様: 東区民センター (Phase 2)

[venue-reservation-proxy](../requirements.md) の Phase 2 対応会場。**Phase 1 では実装しない**。

実装の入力情報は [higashi-reservation-flow-exploration/findings.md](../../higashi-reservation-flow-exploration/findings.md) (Playwright で13ステップ踏破した分析レポート) を参照。本ドキュメントは findings.md の要約 + Phase 2 実装時に確定すべき項目の整理。

## 1. 会場メタ情報

| 項目 | 値 |
|-----|-----|
| `VenueId` enum | `HIGASHI` |
| 表示名 (`VenueConfig.displayName`) | 東区民センター |
| ベースURL (`VenueConfig.baseUrl`) | `https://www.sapporo-community.jp` |
| 技術スタック | ASP.NET WebForms (`.aspx` + `__doPostBack` JS) |
| WAF / CDN | **Imperva Incapsula** (`incap_ses_*` cookie) |
| サービス提供時間 | 8:45〜23:00 JST (時間外は `OutsideServiceTime.html` にリダイレクト) |
| セッション識別 cookie | `ASP.NET_SessionId` (HttpOnly 推定) + `incap_ses_*` |
| ログイン認証 | 利用者ID (7桁) + パスワード |
| 認証情報の環境変数 | `SAPPORO_COMMUNITY_USER_ID` / `SAPPORO_COMMUNITY_PASSWORD` (既存・Phase 2 で利用) |
| Phase 1 時点の `enabled` | `false` (リクエストすると `VENUE_NOT_SUPPORTED`) |

## 2. URL 名前空間

```
/UserWebApp/Form/*   未ログイン可 / 検索系 (Logout-suffix ボタンあり)
/UserWebApp/Foau/*   ログイン必須 / 申込系 (Foau = Form Of Authorized User 推定)
/UserWebApp/Html/*   エラーページ (NoneSessionInfo.html / OutsideServiceTime.html / HttpClientError.html)
```

リバースプロキシ実装時、`/Foau/` 配下へのアクセスは必ずログイン完了後でなければ `NoneSessionInfo.html` に飛ばされる。

## 3. ナビゲーションフロー (申込トレイ + 確認画面まで)

[findings.md §2](../../higashi-reservation-flow-exploration/findings.md) の探索結果による13ステップ。

| # | URL | 操作 |
|---|-----|------|
| 01 | `Form/UserLogin.aspx` | ID/PW 入力 |
| 02 | `Form/UserMenu.aspx` | (ログイン成功 → リダイレクト) |
| 03 | 同上 | 「空室検索 (ログイン済)」ボタン |
| 04 | `Form/SvrSelSearchType.aspx` | 「施設から検索」 |
| 05 | `Form/SvrSelFacilities.aspx` | 施設 ddl 選択 (AutoPostBack) |
| 06 | 同上 (postback) | 部屋 ddl 選択 (AutoPostBack) |
| 07 | 同上 (postback) | 「部屋単位月表示」ボタン |
| 08 | `Form/SsfSvrRoomAvailabilityMonth.aspx` | 対象月まで「翌月」繰り返し |
| 09 | 同上 | 対象日の「○」セル内 `<a onclick="newLinkClick(...)">` クリック |
| 10 | `Foau/SsfrApplyForUseEntry.aspx` | **利用申込フォーム入力** (時間区分 / 氏名 / TEL / メール / 人数 / 目的) |
| 11 | 同上 (postback) | 「利用申込確認」ボタン |
| 12 | `Foau/SsfrApplyForUseEntryConfirmation.aspx` | **利用申込確認 = 申込確定直前** |
| 13 | 同上 | 申込確定ボタン (`#ctl00_cphMain_btnReg` value="はい") |

**Phase 2 のゴール**: ステップ12 (利用申込確認) まで自動到達し、ユーザーが「はい」を手動押下するまでがプロキシの役割。

## 4. ASP.NET WebForms 固有の実装事項

### 4.1 hidden field (毎リクエスト echo 必須)

| name | 用途 | サイズ目安 |
|------|-----|----------|
| `__VIEWSTATE` | ページ状態 (Base64) | 800〜2400 bytes |
| `__VIEWSTATEGENERATOR` | VS の世代識別 | 8 bytes |
| `__EVENTVALIDATION` | submit 検証 | 150〜1200 bytes |
| `__EVENTTARGET` | postback の発生源コントロール ID | 数十 bytes |
| `__EVENTARGUMENT` | postback の引数 | 数十 bytes |
| `__PREVIOUSPAGE` | 前画面の識別 | 129〜151 bytes |
| `hdnfacilityNo` 等 | ビジネス hidden | 数 bytes |

**`HigashiReservationClient` の実装方針**:

```java
// 擬似コード
Document doc = Jsoup.parse(htmlResponse);
Map<String, String> hiddenFields = new LinkedHashMap<>();
for (Element input : doc.select("form input[type=hidden]")) {
    hiddenFields.put(input.attr("name"), input.attr("value"));
}
hiddenFields.put("ctl00$cphMain$btnReg", "はい");   // ボタン押下相当
// → POST with URL-encoded form body
```

`ProxySession.hiddenFields` (Map) に最新値をキャッシュし、次の POST で全 echo する。

### 4.2 AutoPostBack コントロール

以下の ddl は値変更で `__doPostBack` を発火する:

- 施設 ddl (`#ctl00_cphMain_WucFacilitySelect_ddlFacilities`)
- 部屋 ddl (`#ctl00_cphMain_wucRoomSelect_ddlRooms`)
- 利用目的 ddl (`#ctl00_cphMain_ddlIntendedUse`)

時間区分チェックボックスは `onclick="SetCheck();"` のクライアント側処理のみで postback は発生しない。

リバースプロキシ経由でも 1操作=1HTTP round-trip の順序で送る (Playwright 探索時は `waitForLoadState` で自動処理されていた挙動を Java で明示的に実装)。

### 4.3 注入スクリプトに追加すべき会場固有ロジック (`HigashiRewriteStrategy.injectScript()`)

ユーザーブラウザでプロキシ経由表示される画面で:

- `__doPostBack(target, arg)` が呼ばれたら、フォームに `__EVENTTARGET=target` `__EVENTARGUMENT=arg` を set してから submit する標準挙動はそのまま動く想定 (ASP.NET 標準スクリプトをサーバから配信される形)
- `<a onclick="newLinkClick(2,'27',4)">` のようなカスタム JS ハンドラがある場合、これらが正しく `__doPostBack` に変換されるよう確認

実機検証で問題が出たらフックを追加。

## 5. Cookie / セッション管理

### 5.1 観察された cookie

```
incap_ses_896_3316468  (Imperva Incapsula、ブラウザ JS から可視)
ASP.NET_SessionId      (HttpOnly 推定。JS からは不可視、HTTP クライアントは透過に保持)
```

### 5.2 実装方針

- `ProxySession.cookies` (CookieStore) にすべての cookie を保持。Apache HttpClient の `CookieStore` で `HttpOnly` も含めて自動管理
- ユーザーブラウザ側には会場サイトの cookie を一切露出させない (`Set-Cookie` ヘッダはレスポンスから除去)
- `incap_ses_*` の寿命は不明。長時間アクセスなしでセッション切れる可能性あり

### 5.3 Incapsula WAF への配慮

- User-Agent を通常ブラウザ (Chrome) に偽装
- レート制限を意識した最小頻度運用 (月数回程度)
- Render の固定IPに対して恒常的にプロキシを走らせると BAN リスクが累積するため、**連続リトライ** や **バッチ処理でのループアクセス** は実装しない

## 6. エラーコード体系

| errorCode | 発生条件 |
|-----------|---------|
| `LOGIN_FAILED` | ID/PW 誤り、または `OutsideServiceTime.html` にリダイレクトされた (時間外) |
| `OUTSIDE_SERVICE_TIME` | サービス提供時間外 (8:45〜23:00 以外) — `LOGIN_FAILED` と区別する場合 |
| `ROOM_NOT_FOUND` | 施設/部屋 ddl の選択肢に該当が見つからない |
| `NOT_AVAILABLE` | 月表示で「○」だったセルが、確認画面遷移過程で他ユーザーに奪われた |
| `VIEWSTATE_INVALID` | `__VIEWSTATE` 期限切れ (タイムアウト後にリトライした場合等) |
| `WAF_BLOCKED` | Incapsula が Bot として遮断 (HTTP 403 等) |
| `TRAY_NAVIGATION_FAILED` | 13ステップのいずれかで予期しない応答 |
| `TIMEOUT` | 個別 HTTP リクエストが `request-timeout-seconds` を超過 |

## 7. 申込完了検知 (HigashiCompletionStrategy)

### 7.1 完了画面の URL — **未確認 (Phase 2 で実機検証必須)**

[findings.md §11](../../higashi-reservation-flow-exploration/findings.md) の残課題1。探索では安全ガードのため「はい」を押していないため、完了画面は未踏査。

ASP.NET WebForms の命名規則上、以下の候補が想定される (実機で1回申込完了を発生させて確定する):
- `Foau/SsfrApplyForUseEntryComplete.aspx`
- `Foau/SsfrApplyForUseResult.aspx`
- `Foau/SsfrApplyForUseEntryDone.aspx`

### 7.2 完了画面の文言 — **未確認 (Phase 2 で実機検証必須)**

既存 [scrape-higashi-history.js](../../../scripts/room-checker/scrape-higashi-history.js) の申込履歴スクレイピングで観察される完了状態の表記から推定:

- 「申込みを受け付けました」
- 「申込完了」
- 「受付番号」
- (申込履歴側の表記) 状態 = 「予約済」

### 7.3 検知ロジック (Phase 2 で確定)

```java
public boolean isCompletion(String requestUrl, String responseLocation, String responseBody) {
    // URL 条件 (P2-1 で確定)
    if (requestUrl.contains("ApplyForUseEntryComplete") || ...) return true;
    // HTML 文言条件 (P2-1 で確定)
    if (responseBody.contains("申込みを受け付けました")) return true;
    return false;
}
```

## 8. PracticeList の venue 判別 (venueResolver.js)

Phase 2 着手時に以下の `HIGASHI_VENUE_IDS` を実値で埋める:

```js
const HIGASHI_VENUE_IDS = [
  // venue マスタの id を Phase 2 着手時に確定 (例: 6 == 「東🌸」)
];
```

[higashi-adjacent-room-check](../../higashi-adjacent-room-check/) で venue id 6 (東🌸) が対応済みのため、同 id を流用する想定。

## 9. Phase 2 の前に確認すべき残課題 (findings.md §11)

| # | 内容 | 解決手段 |
|---|------|---------|
| 1 | 完了画面の URL と特徴的文言 | 本番で1回実申込を発生させて観察 (P2-1) |
| 2 | セッションタイムアウト時間 | 15分無操作で kaderu 設計と同じか実機で検証 |
| 3 | 同時申込時の挙動 | 月表示で「○」だった日が確認画面遷移中に埋まった場合のエラー応答 |
| 4 | Incapsula WAF のレート制限閾値 | Render IP で運用開始してから経験則で把握 |
| 5 | さくら (042) 単独 / 和室全室 (040) のフロー差異 | 探索は かっこう (041) のみ。さくらでも同じ構造か実機で確認 |

## 10. Phase 2 着手時の作業見積

抽象化が正しく効いている場合の追加コスト ([findings.md §10.4](../../higashi-reservation-flow-exploration/findings.md) の試算 0.4x):

- HigashiVenueConfig: 30 行程度
- HigashiReservationClient: ASP.NET WebForms 対応で 400-600 行 (kaderu の 1.5倍)
- HigashiRewriteStrategy: 50-100 行
- HigashiCompletionStrategy: 30 行 (URL/文言が確定していれば)
- venueResolver.js への HIGASHI_VENUE_IDS 追加: 数行
- application.yml の `enabled: true` 化 + Render 環境変数確認: 設定変更のみ
- 実機 E2E 検証 (P2-1 と兼ねる)

合計: 1〜2人日見込み (P2-1 の実機検証準備時間を除く)。
