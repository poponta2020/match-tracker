# 会場別仕様: かでる2・7

[venue-reservation-proxy](../requirements.md) の Phase 1 対応会場。

## 1. 会場メタ情報

| 項目 | 値 |
|-----|-----|
| `VenueId` enum | `KADERU` |
| 表示名 (`VenueConfig.displayName`) | かでる2・7 |
| ベースURL (`VenueConfig.baseUrl`) | `https://k2.p-kashikan.jp` |
| エントリポイント | `/kaderu27/index.php` |
| 技術スタック | PHP |
| WAF / CDN | なし (推定) |
| セッション識別 cookie | `PHPSESSID` |
| ログイン認証 | 利用者ID + パスワード |
| 認証情報の環境変数 | `KADERU_USER_ID` / `KADERU_PASSWORD` |
| 1アカウント並行ログイン | 不可 (会場側仕様。実運用上は月20回低頻度のため問題なし) |

## 2. ナビゲーションフロー (申込トレイまで)

`KaderuReservationClient.prepareReservationTray(ProxySession)` が順次実行する6ステップ。

| # | ステップ | 概要 |
|---|---------|-----|
| 1 | ログイン | `GET /kaderu27/index.php` で `PHPSESSID` を取得し、`p=my_page`, `loginID`, `loginPwd`, `loginBtn=ログイン` を `POST /kaderu27/index.php` に送信。本文に「マイページ」または「ログアウト」があれば成功 |
| 2 | マイページ遷移 | `p=my_page` を `POST /kaderu27/index.php` に送信 |
| 3 | 空き状況ページ遷移 | `p=srch_sst`, `UseYM=YYYYMM` を `POST /kaderu27/index.php` に送信。本文に対象部屋名が含まれることを確認 |
| 4 | 月合わせ | `p=srch_sst`, `UseYear=YYYY`, `UseMonth=MM` を `POST /kaderu27/index.php` に送信 |
| 5 | 日付クリック | `p=date_select`, `UseDate=YYYYMMDD` を `POST /kaderu27/index.php` に送信。本文に対象部屋名が含まれることを確認 |
| 6 | スロット選択 + 申込トレイへ | `p=date_select`, `setAppStatus=1`, `facilityCode`, `useDate=YYYY/MM/DD`, `slotIndex`, `timeRange` を送信後、`p=rsv_search`, `requestBtn=申込トレイに入れる` を送信。本文に「申込トレイ」があれば成功 |

**省略するステップ**: 旧 Playwright 実装にあった「スロット状態確認」の DOM verification は行わない。会場側がスロット選択時に返すエラー文言で `NOT_AVAILABLE` を判定する。

### 2.1 部屋コード / 時間帯

| 部屋名 | `facilityCode` |
|---|---|
| すずらん | `001|018|01|2|2|0` |
| はまなす | `001|018|02|3|2|0` |
| あかなら | `001|017|02|3|2|0` |
| えぞまつ | `001|017|01|2|2|0` |

| `slotIndex` | `timeRange` | 時間帯 |
|---|---|---|
| 0 | `09001200` | 午前 |
| 1 | `13001600` | 午後 |
| 2 | `17002100` | 夜間 |

## 3. エラーコード体系

`VenueReservationProxyException.errorCode` で利用するもの。

| errorCode | 発生条件 |
|-----------|---------|
| `LOGIN_FAILED` | ログインフォーム POST のレスポンスがログイン画面に戻る / エラー文言を含む |
| `ROOM_NOT_FOUND` | 部屋名が空き状況ページの DOM に見つからない |
| `NOT_AVAILABLE` | スロット選択時に会場側がエラーを返す (キャッシュ古く実は埋まっていた等) |
| `TRAY_NAVIGATION_FAILED` | ステップ1〜5 で予期しない応答 |
| `TIMEOUT` | 個別 HTTP リクエストが `request-timeout-seconds` を超過 |
| `SCRIPT_ERROR` | 想定外の I/O エラー、session venue 不一致、プロキシ内部エラー |

`NOT_AVAILABLE` は、スロット選択後の HTML に「既に予約されています」「予約できません」「空きがありません」のいずれかが含まれる場合に発生する。

## 4. HTML 書き換え戦略 (KaderuRewriteStrategy)

### 4.1 URL 書き換え対象
標準の `VenueReservationHtmlRewriter` のコア処理 (`<a href>` `<form action>` `<img src>` `<link href>` `<script src>`) で十分。Kaderu 固有の書き換えは原則不要。

### 4.2 注入スクリプトへの追記
Kaderu は通常の form POST + JavaScript 程度なので、コア注入スクリプト (Location/fetch/XHR フック) のみで動作する。`KaderuRewriteStrategy.injectScript()` は空文字の no-op。

### 4.3 hidden field の特記事項
Kaderu の form は hidden field が少ない (0-2個程度。CSRF token 等)。標準の `<input type=hidden>` をそのまま echo すれば足りる。

## 5. 申込完了検知 (KaderuCompletionStrategy)

### 5.1 URL 条件
- リクエスト URL または `Location` ヘッダに `p=rsv_comp` を含む
- リクエスト URL または `Location` ヘッダに `p=fix_comp` を含む
- リクエスト URL または `Location` ヘッダに `/complete` を含む

### 5.2 HTML 文言条件
- 「申込みを受け付けました」
- 「申込番号」
- 「予約を受付ました」
- 「予約完了」

### 5.3 検知時の動作
- `practice_sessions.reservation_confirmed_at` を `JstDateTimeUtil.now()` で更新
- `reservation_confirmed_at` に既存値がある場合は上書きしない
- `ProxySession.completed = true` にセットして次のクリーンアップで削除対象に
- `fetch` レスポンスに `X-VRP-Completed: true` を付与
- 注入バナーが `X-VRP-Completed: true` を検知し、BroadcastChannel `venue-reservation-proxy` に `{type: 'reservation-completed', practiceSessionId, venue: 'KADERU', token}` をポスト

## 6. PracticeList の venue 判別 (venueResolver.js)

```js
export const KADERU_VENUE_IDS = new Set([3, 4, 8, 11]);
export const HIGASHI_VENUE_IDS = new Set();
```

`practiceSession.venueId` または `practiceSession.venue_id` が `KADERU_VENUE_IDS` に含まれれば `KADERU` を返す。`HIGASHI_VENUE_IDS` は Phase 2 で設定する。

## 7. 既知の制約 / 運用上の留意点

- 利用頻度月20回程度の前提で Render IP からのアクセスを許容 (BAN リスクは低頻度運用で吸収)
- ToS / robots.txt の確認は別途実施予定
- 1アカウント1セッションのため、複数管理者が同時に予約フローを開始すると後勝ちになる (実運用上問題ない想定)
