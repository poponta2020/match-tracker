# 会場別仕様: かでる2・7

[venue-reservation-proxy](../requirements.md) の Phase 1 対応会場。

## 1. 会場メタ情報

| 項目 | 値 |
|-----|-----|
| `VenueId` enum | `KADERU` |
| 表示名 (`VenueConfig.displayName`) | かでる2・7 |
| ベースURL (`VenueConfig.baseUrl`) | `https://k2.p-kashikan.jp` |
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
| 1 | ログイン | `POST /` のログインフォーム送信 |
| 2 | マイページ遷移 | ログイン直後のリダイレクト先 |
| 3 | 空き状況ページ遷移 | 月表示画面まで遷移 |
| 4 | 月合わせ (必要時) | 対象日付の月までページング |
| 5 | 日付クリック | 該当セルの POST (`p=date_select` 相当) |
| 6 | スロット選択 + 申込トレイへ | `setAppStatus` 相当 → `requestBtn` 相当の連続 POST |

**省略するステップ**: 既存 [open-reserve.js:142-184](scripts/room-checker/open-reserve.js#L142-L184) の「スロット状態確認」DOM verification (1-2秒短縮)。

**参考実装**: [scripts/room-checker/open-reserve.js](scripts/room-checker/open-reserve.js) — Playwright 版を Apache HttpClient で再実装する。

## 3. エラーコード体系

`VenueReservationProxyException.errorCode` で利用するもの。

| errorCode | 発生条件 |
|-----------|---------|
| `LOGIN_FAILED` | ログインフォーム POST のレスポンスがログイン画面に戻る / エラー文言を含む |
| `ROOM_NOT_FOUND` | 部屋名が空き状況ページの DOM に見つからない |
| `NOT_AVAILABLE` | スロット選択時に会場側がエラーを返す (キャッシュ古く実は埋まっていた等) |
| `TRAY_NAVIGATION_FAILED` | ステップ1〜5 で予期しない応答 |
| `TIMEOUT` | 個別 HTTP リクエストが `request-timeout-seconds` を超過 |

詳細なエラー判定ロジックは既存 [KaderuReservationService.java](karuta-tracker/src/main/java/com/karuta/matchtracker/service/KaderuReservationService.java) のコード体系を参照。

## 4. HTML 書き換え戦略 (KaderuRewriteStrategy)

### 4.1 URL 書き換え対象
標準の `VenueReservationHtmlRewriter` のコア処理 (`<a href>` `<form action>` `<img src>` `<link href>` `<script src>`) で十分。Kaderu 固有の書き換えは原則不要。

### 4.2 注入スクリプトへの追記
Kaderu は通常の form POST + JavaScript 程度なので、コア注入スクリプト (Location/fetch/XHR フック) のみで動作する見込み。`KaderuRewriteStrategy.injectScript()` は空文字 or no-op で開始し、実機検証で問題が見つかれば追記する。

### 4.3 hidden field の特記事項
Kaderu の form は hidden field が少ない (0-2個程度。CSRF token 等)。標準の `<input type=hidden>` をそのまま echo すれば足りる。

## 5. 申込完了検知 (KaderuCompletionStrategy)

### 5.1 URL 条件 (暫定)
- パスや query に `rsv_comp` / `complete` 等のパターンを含む
- **正確なパターンは Phase 1 実装時の実機検証で確定する**

### 5.2 HTML 文言条件 (暫定)
- 「申込みを受け付けました」
- 「申込番号」
- **正確な文言は Phase 1 実装時の実機検証で確定する**

### 5.3 検知時の動作
- `practice_sessions.reservation_confirmed_at` を `now()` で更新
- `ProxySession.completed = true` にセットして次のクリーンアップで削除対象に
- BroadcastChannel `venue-reservation-proxy` に `{type: 'reservation-completed', practiceSessionId, venue: 'KADERU'}` をポスト

## 6. PracticeList の venue 判別 (venueResolver.js)

```js
const KADERU_VENUE_IDS = [
  // venue マスタの id を実装時に確定 (例: 1, 5, ...)
];
```

`practice_session.venue_id` がこの配列に含まれれば `KADERU` を返す。実 venue_id は実装時に管理画面 / DB から確認して埋める。

## 7. 既知の制約 / 運用上の留意点

- 利用頻度月20回程度の前提で Render IP からのアクセスを許容 (BAN リスクは低頻度運用で吸収)
- ToS / robots.txt の確認は別途実施予定
- 1アカウント1セッションのため、複数管理者が同時に予約フローを開始すると後勝ちになる (実運用上問題ない想定)
