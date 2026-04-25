---
status: completed
---
# 東区民センター予約フロー探索 — 分析レポート

## 1. 探索概要

| 項目 | 値 |
|-----|-----|
| 探索実施日時 | 2026-04-24 20:24 JST |
| 探索対象 | 札幌市東区民センター かっこう（和室）夜間 2026-04-27 |
| 探索スクリプト | [`scripts/room-checker/explore-higashi-reservation.js`](../../../scripts/room-checker/explore-higashi-reservation.js) |
| 認証 | 登録利用者ID (7桁) + パスワード |
| 生データ | `scripts/room-checker/exploration-output/higashi-reservation-20260424-202431/`（`.gitignore` 対象） |
| 終了理由 | `final-submit-detected` (申込確定ボタン検出で自動停止、クリックなし) |
| 実際の申込 | 発生なし |

探索ゴールである「**申込確定ボタン押下で本申込が確定する、その1つ前の画面**」に到達し、安全に停止した。

## 2. 画面遷移フロー (13 ステップ)

| # | URL | title | 操作 |
|---|-----|-------|------|
| 01 | `Form/UserLogin.aspx` | ログイン | ID/PW 入力 |
| 02 | `Form/UserMenu.aspx` | メインメニュー | (ログイン成功 → リダイレクト) |
| 03 | 同上 | 同上 | 「空室検索（ログイン済）」ボタン |
| 04 | `Form/SvrSelSearchType.aspx` | 空き状況検索 | 「施設から検索」 |
| 05 | `Form/SvrSelFacilities.aspx` | 空き状況検索 | 施設 ddl 選択 (AutoPostBack) |
| 06 | 同上 (postback) | 同上 | 部屋 ddl 選択 (AutoPostBack) |
| 07 | 同上 (postback) | 同上 | 「部屋単位月表示」ボタン |
| 08 | `Form/SsfSvrRoomAvailabilityMonth.aspx` | 空き状況検索 | 対象月まで「翌月」繰り返し |
| 09 | 同上 | 同上 | 対象日の「○」セル内 `<a onclick="newLinkClick(2,'27',4)">` クリック |
| 10 | `Foau/SsfrApplyForUseEntry.aspx` | **利用申込 (フォーム入力)** | 時間区分 / 氏名 / TEL / メール / 人数 / 目的 入力 |
| 11 | 同上 (postback) | 同上 | 「利用申込確認」ボタン (`#ctl00_cphMain_btnReg`) |
| 12 | `Foau/SsfrApplyForUseEntryConfirmation.aspx` | **利用申込確認 (=申込確定直前)** | — |
| 13 | 同上 | 同上 | **申込確定ボタン検出 → 停止** (`#ctl00_cphMain_btnReg` value="はい") |

### 重要な URL 名前空間規則

```
/UserWebApp/Form/*   — 未ログイン可 / 検索系 (Logout-suffix ボタンあり)
/UserWebApp/Foau/*   — ログイン必須 / 申込系 (Foau = Form Of Authorized User 推定)
/UserWebApp/Html/*   — エラーページ (NoneSessionInfo.html / OutsideServiceTime.html / HttpClientError.html)
```

リバースプロキシ実装時は `/Foau/` へのアクセス前に必ずログイン完了を保証する必要がある。

## 3. ViewState / EventValidation の技術分析

### 3.1 サイズと変化パターン

各ステップの `__VIEWSTATE` / `__EVENTVALIDATION` の長さ (bytes) と、前ステップからのハッシュ変化:

| # | URL (短縮) | VS長 | VS変化 | EV長 | EV変化 |
|---|----------|-----:|:-----:|----:|:-----:|
| 01 | UserLogin | 832 | — | 244 | — |
| 02 | UserMenu | 1540 | ✅ | 284 | ✅ |
| 03 | UserMenu (再取得) | 1540 | — | 284 | — |
| 04 | SvrSelSearchType | 1336 | ✅ | 156 | ✅ |
| 05 | SvrSelFacilities (初期) | 1352 | ✅ | 692 | ✅ |
| 06 | SvrSelFacilities (施設選択後) | 1832 | ✅ | 1012 | ✅ |
| 07 | SvrSelFacilities (部屋選択後) | 1832 | — | 1012 | — |
| 08 | SsfSvrRoomAvailabilityMonth | 892 | ✅ | 1204 | ✅ |
| 09 | 同上 (再取得) | 892 | — | 1204 | — |
| 10 | SsfrApplyForUseEntry | 2376 | ✅ | 1032 | ✅ |
| 11 | 同上 (postback後) | 2376 | ✅(SHA) | 1032 | ✅(SHA) |
| 12 | SsfrApplyForUseEntryConfirmation | 2288 | ✅ | 200 | ✅ |

### 3.2 観察される挙動

1. **両フィールドは画面遷移 / postback のたびに常に更新される**
   - サーバーが毎回新しい値を返却し、クライアントは受け取った値をそのまま次の POST に echo する責務を負う
   - 同じ URL の再取得（snapshot 取り直し）では値は不変 (step 02→03, 06→07, 08→09)
   - 同じ URL で AutoPostBack を発生させると SHA は変わる（step 10→11 は VS長 は同じ 2376 だが SHA は異なる — サーバー側状態の変化を表現）

2. **暗号化レベル**
   - 内容は Base64 エンコードされた ViewState シリアライズ形式
   - 平文解釈する必要はなく、**値をバイト列としてそのまま echo すればよい**
   - Java 側で扱う場合は `String` として保持し、次の POST の form-encoded body に含めるだけ

3. **`__VIEWSTATEGENERATOR` (8 bytes) / `__PREVIOUSPAGE` (129-151 bytes) / `__EVENTTARGET` / `__EVENTARGUMENT` も常に併送**
   - `__PREVIOUSPAGE` は前画面の識別情報を含む (サイズが 129 / 151 と小さく、URL ハッシュ的な短い符号)
   - 確認画面 (step 12) の `hdnfacilityNo` などビジネスロジック hidden もある

### 3.3 Java 側での再注入可能性

**結論: 可能。**

実装方針:
1. レスポンス HTML を Jsoup で解析
2. `input[type=hidden]` を全抽出 (ASP.NET 標準 6 種 + ビジネス hidden を含めて all)
3. 次の POST 時に、これらをそのまま form-encoded body に含める
4. Button クリックは `name=value` のペアを追加 (例: `ctl00$cphMain$btnReg=はい`)

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

### 3.4 AutoPostBack の挙動

以下のコントロールは値変更で postback を発火する:
- 施設 ddl (`#ctl00_cphMain_WucFacilitySelect_ddlFacilities`) — `onchange` に `__doPostBack` を注入
- 部屋 ddl (`#ctl00_cphMain_wucRoomSelect_ddlRooms`) — 同上
- 利用目的 ddl (`#ctl00_cphMain_ddlIntendedUse`) — 同上 (`javascript:setTimeout('__doPostBack(\'ctl00$cphMain$ddlIntendedUse\',\'\')', 0)`)

時間区分チェックボックス (`cbTimeAm` / `cbTimeNt` 等) は `onclick="SetCheck();"` のクライアント側関数で、postback は**発生しない**。タブ/エンターでユーザー側に渡される。

**リバースプロキシ観点**:
- AutoPostBack の ddl は実質的に「選択→POST→次画面表示」の3段に分かれる
- 1操作=1HTTP round-trip の仮定で設計すれば問題なし (WireMock 等でテストしやすい)

## 4. Cookie / セッション管理

### 4.1 `document.cookie` から観測された cookie

```
incap_ses_896_3316468
```

- `incap_*` = Imperva Incapsula (WAF/CDN) のセッション識別子
- **ASP.NET の `ASP.NET_SessionId` cookie は `document.cookie` で観測できず**
- これは `HttpOnly` 属性が付与されているためと推測される (ブラウザ DevTools の Network タブでは見えるが JS からは見えない)

### 4.2 リバースプロキシ実装での含意

**Cookie Jar はネットワーク層で保持する必要がある**:

- Apache HttpClient / Spring WebClient の `CookieStore` を使用すれば `HttpOnly` cookie も含めて全 cookie を透過的に扱える
- 各 `ProxySession` ごとに独立した `CookieStore` を持たせる (kaderu-reservation-proxy の既存設計と完全に同じ)
- クライアント (ブラウザ) 側には実サイトの cookie を一切露出させない — `Set-Cookie` ヘッダはレスポンスから除去

### 4.3 Incapsula WAF への対策

サイトが Imperva Incapsula で保護されているため、以下のリスクを認識する:

- Bot 検出: User-Agent を通常ブラウザのものに偽装 (Playwright 探索時は Chrome UA を使用して成功)
- レート制限: 同一IPからの高頻度アクセスで BAN の可能性
- セッション持続性: `incap_ses_*` の寿命は不明 (長時間アクセスのないセッションは切れる可能性)

Render の固定IPに対して恒常的にプロキシを走らせると BAN リスクが累積する。実運用頻度は月数回程度のため通常は問題ないが、**連続リトライ** や **バッチ処理でのループアクセス** は避ける設計とする。

## 5. フォーム構造 — 利用申込画面 (`SsfrApplyForUseEntry.aspx`)

リバースプロキシが `ProxySession.prepareReservationTray()` で最終的に到達させるべき画面。以下のフィールドをユーザーが入力する:

| フィールド | セレクタ | 型 | 制約 |
|-----------|---------|-----|------|
| 時間区分 | `#ctl00_cphMain_wucTimeKbn_cbTime{Am,Pm,Nt,Extra,Lunch,Dinner,All}` | checkbox | 少なくとも1つ必須 (○セルクリック時に対応チェック済み状態で到達) |
| 利用人数 | `#ctl00_cphMain_tbUserNumber` | text (maxlength=3, 半角数字) | 必須 |
| 担当者氏名 | `#ctl00_cphMain_tbContactName` | text | 全角必須、苗字と名の間に全角空白 |
| 電話番号 | `#ctl00_cphMain_tbTelno{1,2,3}` | text x3 | 必須 |
| メール | `#ctl00_cphMain_tbMailAddress` | text | 必須 |
| 利用目的 | `#ctl00_cphMain_ddlIntendedUse` | select (23選択肢) | 必須、AutoPostBack |
| コメント | `#ctl00_cphMain_tbComment` | textarea | 任意 |
| **確認ボタン** | `#ctl00_cphMain_btnReg` | submit (value="利用申込確認") | — |

### 入力バリデーションパターン (JS)

```
errormessage = "利用人数が未入力です。"
errormessage = "利用人数を正しく入力してください。"
errormessage = "利用目的が未選択です。"
errormessage = "担当者氏名は全角で入力してください。"
errormessage = "時間区分が未選択です。"
errormessage = "氏名は苗字と名の間に空白を入力してください。"
```

クライアントサイド validation のみで、サーバー側でも同様のチェックがあるはず (ポストバック後のエラー時に ValidationSummary に表示)。

## 6. 確認画面 (`SsfrApplyForUseEntryConfirmation.aspx`) の構造

### 6.1 ボタン一覧

| id | name | value | 役割 |
|----|------|-------|------|
| `ctl00_cphMain_btnReg` | `ctl00$cphMain$btnReg` | **はい** | **申込を確定する (最終submit)** |
| `ctl00_cphMain_btnReturn` | `ctl00$cphMain$btnReturn` | いいえ | 戻る |
| `ctl00_cphMain_btnBackTimeSelect` | `ctl00$cphMain$btnBackTimeSelect` | 入力内容を修正 | Entry 画面に戻る |
| `ctl00_btnReturn` | `ctl00$btnReturn` | メニューへ戻る | — |
| `ctl00_btnLogout` | `ctl00$btnLogout` | ログアウト | — |

「申込内容でよろしいですか？ はい / いいえ」ダイアログ形式。**`はい` が最終申込ボタン**。

### 6.2 hidden fields

確認画面の `form` には 6 個の ASP.NET hidden (`__EVENTTARGET` / `__EVENTARGUMENT` / `__VIEWSTATE` 2288B / `__VIEWSTATEGENERATOR` 8B / `__PREVIOUSPAGE` 151B / `__EVENTVALIDATION` 200B) に加え、ビジネス hidden `hdnfacilityNo` (3B = 施設コード "103" と推定) を含む計 7 個。

申込確定時の POST body は:
- 6 ASP.NET hidden を echo
- `hdnfacilityNo` を echo
- `ctl00$cphMain$btnReg=はい` (ボタン名・値)

で構成される form-encoded 送信となるはず (本探索では実際には送信していない)。

## 7. 完了画面 (未観測)

探索の安全ガードにより、`はい` をクリックしていないため**完了画面は未踏査**。

既存の申込履歴スクリプト [`scrape-higashi-history.js`](../../../scripts/room-checker/scrape-higashi-history.js) / 履歴画面スクレイピング調査 ([higashi-community-center-scraping-report-20260420.md](../../operations/higashi-community-center-scraping-report-20260420.md)) によれば、申込完了後の一覧には:

- `状態` = `予約済`
- 一覧テーブル `#ctl00_cphMain_gvView`

として表示される。完了画面の URL は推測になるが、ASP.NET WebForms の命名規則上 `SsfrApplyForUseEntryComplete.aspx` か `SsfrApplyForUseResult.aspx` といった類似名の可能性が高い。

**完了検知ロジックの実装時は以下を推奨**:

1. URL マッチ: `/Foau/SsfrApplyForUseEntry(Result|Complete).aspx` のようなパターンに遷移したら完了とみなす
2. HTML 文言マッチ: 「申込みを受け付けました」「申込完了」「受付番号」等の文言を検出
3. Safety-first: どちらか1つでも陽性なら完了として扱う

実機で1回申込完了を発生させれば URL と文言は確定できるが、**現時点では未確認**であることを要件定義書に明記し、実装時に本番 kaderu テンプレートとは別に確認フェーズを設ける。

## 8. かでる (`k2.p-kashikan.jp`) との技術比較

### 8.1 技術スタックの違い

| 観点 | かでる | 東区民センター |
|------|-------|---------------|
| サーバー基盤 | PHP | ASP.NET WebForms |
| セッション識別 | `PHPSESSID` cookie | `ASP.NET_SessionId` cookie (HttpOnly 推定) + ViewState |
| CDN/WAF | なし (推定) | Imperva Incapsula (`incap_ses_*`) |
| form 送信 | 通常 POST (action/method 固定) | `__doPostBack` JS 関数経由 (全フォームが自己ポスト) |
| hidden field 数 | 少 (0-2個) | 多 (5-7個、ASP.NET 標準 + ビジネス) |
| 1操作の POST サイズ | 数百 bytes | 数 KB (ViewState 800-2400B + EventValidation 150-1200B) |
| AutoPostBack | なし | 施設/部屋/目的の ddl で発火 (1操作=1要求) |

### 8.2 プロキシ実装上の共通点

- **Cookie Jar の必要性**: 両方ともサーバー側 session を cookie で維持
- **ログイン → 検索 → 日付選択 → 申込 → 確認 → 完了 のマクロフロー構造は同じ**
- **HTML 書き換え + ヘッダバナー注入の必要性は同じ**
- **BroadcastChannel + 完了検知 + `practice_sessions.reservation_confirmed_at` 更新の仕組みは共通**

### 8.3 プロキシ実装上の差異

**かでる実装で不要 / 東区民センター実装で必要になる追加要素**:

1. **ViewState / EventValidation 抽出・再注入**
   - `fetch(...)` で受け取った HTML から hidden を全抽出して次の POST に含める
   - Jsoup 1回 parse で OK
2. **__doPostBack セマンティクスの翻訳**
   - プロキシ経由のブラウザ操作で `<a onclick="newLinkClick(2,'27',4)">` のような JS が走ったとき、注入スクリプトで `__doPostBack` 呼び出しをフックして form 送信に変換
   - kaderu-reservation-proxy の既存 HTML Rewriter に「ASP.NET postback 対応」フックを追加する形で実装可能
3. **AutoPostBack の 1操作=1要求 パターン**
   - 施設 ddl / 部屋 ddl は連続して選択すると 2 回の postback が発生する
   - プロキシ経由でも同じ順序で送る必要がある (Playwright は自動的に waitForLoadState で処理していたのを、Java 側で明示的に順序制御)

**これらは kaderu-reservation-proxy の既存設計では想定されていない機能**だが、アーキテクチャの拡張 (サブクラス / strategy) で追加可能。

## 9. リバースプロキシ実装可否の結論

**YES — 技術的に実現可能。**

ただし kaderu-reservation-proxy の現行設計より実装複雑度は 1.3-1.5 倍。追加で必要な要素:

1. **ASP.NET WebForms アダプタ層** (hidden field 抽出・POST body 構築・postback 翻訳)
2. **Incapsula WAF 対策** (User-Agent 偽装・レート制限配慮)
3. **完了検知の実機検証** (URL / 文言パターンは未確定)

危険性は kaderu と同等 (月数回利用であれば BAN リスクは無視できる水準)。

## 10. kaderu-reservation-proxy の実装計画への提言

### 10.1 現状

[kaderu-reservation-proxy/implementation-plan.md](../kaderu-reservation-proxy/implementation-plan.md) は 14 タスク構成だが**全タスク未着手** (0/14)。
kaderu 単体で実装を進める前提で設計されており、**会場別のアダプタ層は存在しない**。

### 10.2 推奨: venue-reservation-proxy への統合リファクタ

kaderu-reservation-proxy の実装着手前の今こそ、統合設計に切り替える機会。

**提案する再構成**:

```
karuta-tracker/src/main/java/com/karuta/matchtracker/service/proxy/
  ├─ VenueReservationProxyService     — Controller ファサード (現 KaderuProxyService 相当)
  ├─ VenueReservationSessionStore     — セッション Store (会場を問わない)
  ├─ VenueReservationHtmlRewriter     — HTML 書き換え / バナー注入 (会場を問わない)
  ├─ VenueReservationCompletionDetector — 完了検知 (会場ごとの判定パターンを strategy)
  ├─ VenueReservationClient           — interface (prepareReservationTray / fetch)
  │   ├─ KaderuReservationClient       — PHP 向け実装
  │   └─ HigashiReservationClient      — ASP.NET 向け実装 (ViewState ハンドラ含む)
  └─ venue/
      ├─ KaderuVenueConfig             — kaderu 特有の URL / selector
      └─ HigashiVenueConfig            — higashi 特有の URL / selector / ViewState field map
```

### 10.3 現行 14 タスクへの影響

| # | 現タスク | 統合前提での変更 |
|---|---------|-----------------|
| 1 | 既存 Kaderu 関連コードの未使用確認 | 影響小 (そのまま) |
| 2 | Jsoup 依存追加 | 影響小 |
| 3 | DTO/Config/SessionStore 骨組み | **Venue非依存に設計** (venue名 enum を request/session に追加) |
| 4 | KaderuProxyClient 実装 | **`VenueReservationClient` interface + KaderuReservationClient 実装**に分割 |
| 5 | KaderuHtmlRewriter | **`VenueReservationHtmlRewriter` として venue 非依存化**。会場別の書き換えルールは strategy |
| 6 | KaderuReservationCompletionDetector | **venue 別 strategy を DI** |
| 7-8 | KaderuProxyService / Controller | **`/api/venue-reservation-proxy/*` として再命名** + request に `venue` パラメータ追加 |
| 9 | フロントAPIクライアント | 同上 |
| 10 | PracticeList.jsx | venue を推論してリクエストに添付 (venue_id から kaderu/higashi を判別) |
| 11 | フロントテスト | 影響小 |
| 12 | 既存 Kaderu コード削除 | 影響小 |
| 13 | ドキュメント更新 | 本機能追加で書き直し範囲拡大 |
| 14 | E2E 検証 | kaderu で一度完成させてから、higashi は別ブランチで追加する段階式を推奨 |

### 10.4 結論: 選択肢の比較

| 選択肢 | 初期工数 | 後の higashi 実装工数 | 合計 | リスク |
|-------|:-------:|:-------------------:|:----:|-------|
| **A. 統合設計で進める** (推奨) | kaderu: 1.2x | higashi: 0.4x | **1.6x** | 設計時間の増 |
| **B. kaderu 独立 → higashi 新規実装** | kaderu: 1.0x | higashi: 1.1x (別サービスクラス丸ごと) | **2.1x** | コード重複・機能差分が拡大 |
| **C. kaderu のまま進めて higashi は後で判断** | kaderu: 1.0x | higashi: 0.8x (リファクタ後に分岐) | **1.8x** | リファクタ時の regression リスク |

選択肢 **A** を推奨。kaderu 実装開始前に設計を調整する一度の投資で、higashi 追加時の工数を大きく削減できる。

## 11. 残課題

探索では確認できなかった、今後の実機確認が必要な項目:

1. **完了画面の URL と特徴的文言** — 本番で1回実申込を行って確認が必要 (スタッフが別用途で申込む際に観察する)
2. **セッションタイムアウト時間** — 15分無操作で kaderu 設計と同じかは未検証
3. **同時申込時の挙動** — 月表示で表示上空きだった日が、確認画面に進む過程で他ユーザーに奪われた場合のエラー応答
4. **Incapsula WAF のレート制限閾値** — Render IP で運用開始してから徐々に経験を積む
5. **さくら (042) 単独 or 和室全室 (040) のフロー差異** — 本探索は かっこう (041) のみ。予約対象として重要度の高い さくら でも同様のフローになるか要確認 (構造は同じはずだが、UI細部で差がある可能性)

## 12. 添付 (生データ)

探索スクリプトが生成した raw data (コミット対象外):

```
scripts/room-checker/exploration-output/higashi-reservation-20260424-202431/
  ├─ step-01-login-page.{html,png,json}
  ├─ step-02-login-submitted.{html,png,json}
  ├─ step-03-menu-after-login.{html,png,json}
  ├─ step-04-vacant-rooms-search-top.{html,png,json}
  ├─ step-05-facility-search-page.{html,png,json}
  ├─ step-06-facility-selected.{html,png,json}
  ├─ step-07-room-selected.{html,png,json}
  ├─ step-08-month-view.{html,png,json}
  ├─ step-09-target-month-view.{html,png,json}
  ├─ step-10-after-cell-click.{html,png,json}
  ├─ step-11-form-filled.{html,png,json}
  ├─ step-12-after-apply-confirm-click.{html,png,json}
  ├─ step-13-final-submit-visible.{html,png,json}
  └─ summary.json
```
