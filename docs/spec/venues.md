# 会場管理

> **責務:** 会場マスタ管理・隣室空き確認通知・会場予約プロキシ（Kaderu）の仕様
> **関連画面:** `/venues`, `/venues/new`
> **主要実装:** `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/VenueController.java`, `karuta-tracker/src/main/java/com/karuta/matchtracker/service/VenueService.java`, `karuta-tracker/src/main/java/com/karuta/matchtracker/config/AdjacentRoomConfig.java`, `karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/AdjacentRoomNotificationScheduler.java`, `karuta-tracker/src/main/java/com/karuta/matchtracker/service/AdjacentRoomService.java`, `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/VenueReservationProxyController.java`, `karuta-tracker/src/main/java/com/karuta/matchtracker/service/proxy/VenueReservationProxyService.java`, `karuta-tracker-ui/src/pages/venues/VenueList.jsx`, `karuta-tracker-ui/src/pages/venues/VenueForm.jsx`, `karuta-tracker-ui/src/pages/practice/PracticeList.jsx`

## 機能仕様

### 会場管理

SUPER_ADMIN のみ操作可能。

| フィールド | 型 | 必須 | 説明 |
|---|---|---|---|
| `name` | String(200) | Yes | 会場名（一意） |
| `defaultMatchCount` | Integer | Yes | デフォルト試合数（1〜20） |

**試合スケジュール（VenueMatchSchedule）:**

会場ごとに試合番号と開始・終了時刻を設定可能。Google カレンダー同期で予定の時間帯を決定するのに使用。

| フィールド | 型 | 必須 | 説明 |
|---|---|---|---|
| `matchNumber` | Integer | Yes | 試合番号 |
| `startTime` | LocalTime | Yes | 開始時刻 |
| `endTime` | LocalTime | Yes | 終了時刻 |

### 隣室空き確認通知

#### 目的

定員接近時に隣室（同フロアの隣接和室）の空きを自動チェックし、管理者へ通知して会場拡張の判断を支援する。

#### 対象会場

| 単体会場 | Venue ID | 隣室 | 隣室 Venue ID | 拡張後会場 | 拡張後定員 | 時間帯ラベル |
|----------|----------|------|---------------|------------|-----------|-------------|
| すずらん / はまなす | 3 / 11 | はまなす / すずらん | 11 / 3 | すずらん・はまなす (7) | 24 | 夜間(17-21) |
| あかなら / えぞまつ | 4 / 8 | えぞまつ / あかなら | 8 / 4 | あかなら・えぞまつ (9) | 24 | 夜間(17-21) |
| 東🌸（さくら） | 6 | かっこう | 12 | 東全室 (10) | 18 | 夜間(18-21) |

※ かっこう(12) 単独予約は運用対象外のため、スケジューラーのチェック対象からは除外（Config 上は双方向を定義）。

#### 処理フロー

1. **スクレイピング（GitHub Actions, 30分間隔）**
   - かでる2・7: `scrape-kaderu.yml` → `sync-to-db.js` — ログイン後に4部屋の夜間空き状況を `room_availability_cache` に UPSERT
   - 東区民センター: `scrape-higashi-availability.yml` → `sync-higashi-availability-to-db.js` — ログイン不要で `SsfSvrRoomAvailabilityMonth.aspx` から かっこう の夜間空き状況を当月〜翌月分取得
2. **通知判定（バックエンド `AdjacentRoomNotificationScheduler`, 30分間隔）**
   - 翌日〜40日先の全セッションから `AdjacentRoomConfig.isAdjacentCheckTarget` で対象をフィルタ
   - 全試合のうち最も定員に近い試合で残り4人以下なら通知対象
   - `room_availability_cache` を照会して隣室が空きなら通知送信
   - `adjacent_room_notifications` テーブルで (session_id, remaining_count) の重複送信を防止
3. **通知**: `NotificationType.ADJACENT_ROOM_AVAILABLE`。会場ごとの時間帯ラベル（`AdjacentRoomConfig.getNightTimeLabel`）を含むメッセージ。SUPER_ADMIN全員 + 該当団体の ADMIN に送信

> **空き状態の区分（`available` と `expandable`）**: `room_availability_cache.status` を `AdjacentRoomStatusDto` の2つの真偽値に写像する。
> - `○`（空き）… `available=true` / `expandable=true`。オンライン予約可・空き通知の対象。
> - `●`（要問合せ）… 当日・直近日はかでるがネット予約を締め切るため表示される。`available=false`（**空き通知は送らない**）だが `expandable=true`（電話等で手動確保した前提で管理者が会場拡張できる）。
> - `×`/`-`/`休館`/`不明` … `available=false` / `expandable=false`（拡張不可）。
>
> 空き通知（処理フロー2.）の条件は `available`、会場拡張（`expandVenue`）の可否は `expandable` を用いる。`●` は Kaderu 和室でもオンライン予約（プロキシ）をスキップし、「予約完了を報告 → 会場を拡張」の手動フローで扱う（UI フローは「フロー」節の「隣室予約→会場拡張フロー」参照）。`expandVenue` は `reservationConfirmedAt != null` かつ隣室が `expandable` であることをサーバー側で再検証する。

#### ファイル構成

| ファイル | 役割 |
|---------|------|
| `karuta-tracker/src/main/java/com/karuta/matchtracker/config/AdjacentRoomConfig.java` | 隣室ペア・拡張定義・時間帯ラベル・対象判定 |
| `karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/AdjacentRoomNotificationScheduler.java` | 定期実行の通知判定 |
| `karuta-tracker/src/main/java/com/karuta/matchtracker/service/AdjacentRoomService.java` | 隣室空き状況取得、予約確認、会場拡張（ADMIN+） |
| `scripts/room-checker/sync-to-db.js` | かでる側のスクレイパ |
| `scripts/room-checker/sync-higashi-availability-to-db.js` | 東区民センター側のスクレイパ |
| `.github/workflows/scrape-kaderu.yml` | かでる側 30分ワークフロー |
| `.github/workflows/scrape-higashi-availability.yml` | 東区民センター側 30分ワークフロー |

### 会場予約プロキシ

#### 目的

隣室予約時に、管理者のブラウザをアプリの `/api/venue-reservation-proxy/*` 経由で会場サイトへ中継し、予約申込トレイ表示から申込完了検知までを自ドメイン内で扱う。Phase 1 は Kaderu (`KADERU`) のみ対応し、バックエンド Controller / Service 層、フロントエンド API クライアント / venue 判別ユーティリティ、`PracticeList.jsx` の隣室予約導線接続まで実装済み。

#### サービス層の責務

- `createSession`: 対象 venue の有効化設定を確認し、`VenueReservationSessionStore` に `ProxySession` を作成した上で、会場別 `VenueReservationClient.prepareReservationTray` により申込トレイHTMLを取得する
- `view`: キャッシュ済み申込トレイHTMLを `VenueReservationHtmlRewriter` と会場別 `VenueRewriteStrategy` で書き換えて返す
- `fetch`: ブラウザからの会場サイト操作を会場別 `VenueReservationClient.fetch` に中継し、HTMLレスポンスは書き換え、CSSレスポンスは `@import` / `url(...)` を現在の上流CSS URL基準でプロキシURLへ書き換える。会場サイトの `Set-Cookie` / `X-Frame-Options` / `Strict-Transport-Security` / `Content-Security-Policy` はユーザーへ返さない。Kaderu の `css/style.css` から読み込まれる分割CSSにも proxy token が付くようにする
- 完了検知: `VenueReservationCompletionDetector` が陽性判定した場合、`X-VRP-Completed: true` を返し、既存の `practice_sessions.reservation_confirmed_at` に JST 現在時刻を記録する。既に値がある場合は上書きせず、最初の検知・手動報告時刻を保持する

#### 会場別拡張

Spring DI では `Map<String, T>` が bean 名キーになるため、会場別 client / config / rewrite strategy は `List<T>` で受け取り、コンストラクタで `EnumMap<VenueId, T>` を構築する。Phase 1 は `KADERU` のみ有効、`HIGASHI` は設定上 `enabled=false` とし、Phase 2 で会場別 client / strategy を追加する。

#### API

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| POST | `/api/venue-reservation-proxy/session` | ADMIN+ | `CreateVenueProxySessionRequest` を受け取り、申込トレイHTMLを準備して `proxyToken` / `viewUrl` を返す |
| GET | `/api/venue-reservation-proxy/view?token=` | ADMIN+ | キャッシュ済み申込トレイHTMLをプロキシ用に書き換えて `text/html` で返す |
| ANY | `/api/venue-reservation-proxy/fetch/**?token=` | ADMIN+ | 会場サイトへの任意HTTPリクエストを中継し、HTMLなら画面内URL、CSSなら `@import` / `url(...)` を書き換えて返す |

`VenueReservationProxyException` は Controller 内の例外ハンドラで `{errorCode, message, venue}` に変換する。`VENUE_NOT_SUPPORTED` 等の利用者操作・会場状態由来のエラーは 400、`SCRIPT_ERROR` は 500 を返す。

旧 `POST /api/kaderu/open-reserve` / `POST /api/kaderu/open-reserve-by-venue` と、Node.js + Playwright 版 `open-reserve.js` を起動する旧サービスは削除済み。Kaderu 認証情報は `venue-reservation-proxy.venues.kaderu.user-id/password` で引き続き利用する。

#### フロントエンド補助層

| ファイル | 役割 |
|---|---|
| `karuta-tracker-ui/src/api/venueReservationProxy.js` | `createSession({venue, practiceSessionId, roomName, date, slotIndex})` で `POST /api/venue-reservation-proxy/session` を呼び出す |
| `karuta-tracker-ui/src/utils/venueResolver.js` | `practiceSession.venueId` / `venue_id` を `KADERU` / `HIGASHI` / `null` に解決する。Phase 1 は Kaderu 会場 ID `[3, 4, 8, 11]` のみ対応し、`HIGASHI_VENUE_IDS` は Phase 2 で設定する |

`PracticeList.jsx` はクリック直後に `window.open('about:blank', '_blank')` でタブを確保し、`resolveVenue` → `createSession` → `viewUrl` 遷移の順で申込トレイ画面を開く。成功時は自動検知漏れに備えて既存の「予約完了を報告」ボタンを表示し、プロキシ画面から `BroadcastChannel('venue-reservation-proxy')` で `reservation-completed` を受け取った場合は該当セッションを再取得して `reservationReady` を予約済みに更新する。

#### Kaderu (`KADERU`) Phase 1 実装

Kaderu の Phase 1 実装は `https://k2.p-kashikan.jp/kaderu27/index.php` に対する form 等価 POST で、実サイトの `gotoPage(op)` / `showCalendar(y,m)` / `clickDay(d)` / `setAppStatus(...)` と同じ form 状態を再現する。各応答 HTML から hidden field を `ProxySession.hiddenFields` に保存し、次の POST は保存済み field をベースに必要項目だけを上書きする。日付選択は `op=srch_sst` を維持して `UseYM` / `UseDay` / `UseDate` を更新し、申込トレイ投入は `setAppStatus` の `chk_rsv`（値は `{facilityCode}#{YYYY/MM/DD}#{slotIndex}#{timeRange}` の `#` 区切り）で空きを再確認した後に `op=apply` + `rsv_chk[facilityCode][YYYY/MM/DD][slotIndex]=timeRange` + `requestBtn=申込トレイに入れる` を送信する。申込完了検知は URL / Location の `op=rsv_comp` / `p=rsv_comp`、`op=fix_comp` / `p=fix_comp`、`/complete` と、本文の「申込みを受け付けました」「申込番号」「予約を受付ました」「予約完了」を陽性条件にする。（画面遷移の調査記録: docs/features/venue-reservation-proxy/venues/kaderu.md）

## 画面

### 会場管理

**パス**: `/venues`

**表示内容**:
- 会場一覧（名前、デフォルト試合数）
- 「新規登録」ボタン → `/venues/new`
- 各会場の編集・削除ボタン

**会場登録・編集フォーム**:
- 会場名（必須、ユニーク）
- デフォルト試合数
- 試合時間割（試合番号ごとの開始・終了時刻）

## フロー

### 隣室予約→会場拡張フロー

```
[ユーザー操作（ADMIN+）]
1. 練習日詳細モーダルで隣室が expandable（`○` 空き ／ `●` 要問合せ）の場合、予約・拡張ボタンを表示（`×`/`不明` 等は非表示）
   - `●`（要問合せ＝当日・直近日でかでるがネット予約締切）→ オンライン予約不可のためプロキシをスキップし、初期状態から「予約完了を報告」ボタンを表示（Kaderu 和室でも同様。以降は手順6へ）
   ↓
2. （`○` の場合）「隣室を予約」ボタンクリック
   ↓
[フロントエンド: PracticeList.jsx]
3. Kaderu 和室(venueId ∈ {3,4,8,11}) かつ `○` → venueReservationProxyAPI.createSession() でプロキシ予約画面を新規タブに表示
   東区民センター 東🌸(venueId=6) → Phase 1 ではプロキシ未対応のため初期状態から「予約完了を報告」ボタンを表示
   隣室が `●`（要問合せ）→ プロキシをスキップし「予約完了を報告」を表示
   （venueResolver / KADERU_VENUE_IDS / adjacentRoomStatus.status 判定で分岐）
   ↓
4a. プロキシ画面表示成功時 → 「予約完了を報告」ボタンを表示（manual_pending状態）
4b. プロキシが申込完了を自動検知した場合 → BroadcastChannel 経由で元タブへ通知し、該当セッションを再取得して「会場を拡張」ボタンを表示
   ↓
[ユーザー操作]
5. かでる2・7サイトで予約を完了
   ↓
6. 「予約完了を報告」ボタンクリック
   ↓
[フロントエンド]
7. POST /api/practice-sessions/{id}/confirm-reservation
   ↓
[バックエンド: AdjacentRoomService.confirmReservation()]
8. reservation_confirmed_at をセット（確認者は updated_by で記録）
   ↓
9. レスポンス: 200 OK + 更新後のセッション情報
   ↓
[フロントエンド]
10. 「会場を拡張」ボタンを表示（reservationReady = true）
   ↓
[ユーザー操作]
11. 「会場を拡張」ボタンクリック → 確認ダイアログ
   ↓
[フロントエンド]
12. POST /api/practice-sessions/{id}/expand-venue
   ↓
[バックエンド: AdjacentRoomService.expandVenue()]
13. reservation_confirmed_at != null かつ隣室が expandable（`○`/`●`）であることを再検証し、会場を拡張後会場に変更、定員を更新
   ↓
14. WaitlistPromotionService.promoteWaitlistedAfterCapacityIncrease(sessionId) を呼び出し（B-1で要承諾に統一）
   - 昇格 OFFERED に通常オファーと同じ応答期限 `calculateOfferDeadline` を付与（auto-confirm=offerDeadline null は廃止）。既存 OFFERED の応答期限一律クリアも廃止（既存OFFEREDは変更しない）
   - match_number ごとに `(capacity - WON - 既存OFFERED)` 名分だけ、WAITLISTED を waitlist_number 昇順に OFFERED 化（offeredAt=現在時刻、offerDeadline=応答期限）
   - 昇格者へオファー通知（アプリ内 `createOfferNotification` ＋ LINE 統合オファー通知）を送信し承諾を促す
   - 応答期限が既に過ぎている場合（当日12:00以降等）は即失効オファーを作らないため昇格しない
   - 余り枠を超える WAITLISTED は据え置き（status・waitlist_number そのまま）
   - 全件 dirty=true、最後に renumberRemainingWaitlist で 1..N に再採番
   - 練習編集 (PracticeSessionService.updateSession) で capacity を増加させた場合も同じメソッドが呼ばれる
   ↓
15. レスポンス: 200 OK + 更新後のセッション情報
```

**変更対象テーブル・コード**:

| 対象 | 変更内容 |
|------|---------|
| `practice_sessions` テーブル | `reservation_confirmed_at` カラム追加 |
| `AdjacentRoomService` | `confirmReservation()`, `expandVenue()` メソッド追加 |
| `PracticeSessionController` | `POST /{id}/confirm-reservation`, `POST /{id}/expand-venue` エンドポイント追加（ADMIN+） |
| `PracticeList.jsx` | 隣室予約→予約完了報告→会場拡張の3段階UIフロー。ボタン表示ゲートは `adjacentRoomStatus.expandable`（`○`/`●`）。`KADERU_VENUE_IDS` でない venue、および隣室が `●`（要問合せ）の場合は「隣室を予約」ボタンをスキップし「予約完了を報告」から開始 |
| `AdjacentRoomConfig` | `isKaderuRoom` と独立した `isAdjacentCheckTarget` を導入（かでる4部屋 + 東🌸）、`getNightTimeLabel(venueId)` で会場別時間帯ラベル、ROOM_MAP に東🌸(6)↔かっこう(12) / 東全室(10, 定員18) を追加 |
| `AdjacentRoomNotificationScheduler` | セッションフィルタを `isAdjacentCheckTarget` に切替、通知の時間帯表記を動的化（かでる: 17-21 / 東🌸: 18-21） |
| `scripts/room-checker/sync-higashi-availability-to-db.js` | 東区民センター かっこう の月表示ページから夜間(18-21)空き状況を `room_availability_cache` に UPSERT |
| `.github/workflows/scrape-higashi-availability.yml` | 30分間隔で上記スクレイパを実行（`concurrency.group=higashi-availability-check`） |

## API

### 会場管理 (`/api/venues`)

#### GET /api/venues
**説明**: 全会場取得
**権限**: なし
**レスポンス**: `List<VenueDto>`

#### GET /api/venues/{id}
**説明**: 会場ID指定取得
**権限**: なし
**レスポンス**: `VenueDto`

#### POST /api/venues
**説明**: 会場新規作成
**権限**: なし
**リクエスト**: `VenueCreateRequest`
```json
{
  "name": "市民館",
  "defaultMatchCount": 7,
  "schedules": [
    {"matchNumber": 1, "startTime": "13:00", "endTime": "13:50"},
    {"matchNumber": 2, "startTime": "14:00", "endTime": "14:50"}
  ]
}
```

#### PUT /api/venues/{id}
**説明**: 会場更新
**権限**: なし
**リクエスト**: `VenueUpdateRequest`

#### DELETE /api/venues/{id}
**説明**: 会場削除
**権限**: なし
