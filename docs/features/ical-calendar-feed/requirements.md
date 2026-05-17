---
status: completed
---
# iCalカレンダーフィード 要件定義書

## 1. 概要

### 1.1 目的
プレイヤーが自分の参加する練習日を、Googleカレンダー等の外部カレンダーアプリで自動表示・自動更新できるようにする。

### 1.2 背景・動機
- 既存のGoogle Calendar OAuth同期は、Google Cloud Consoleの「テストユーザー」制限（最大100件）により事前登録メールアドレスでしか動かず、全メンバーには展開できない
- また都度手動で同期ボタンを押す方式であり、1日1回の自動同期も実装されていない
- iCalフィード方式に切り替えることで、Google審査・テストユーザー制限を回避し、購読側（Googleカレンダー等）が自動で定期取得（数時間ごと）してくれる構造にする
- 結果として全メンバーが利用可能・実質的な自動同期を達成できる

## 2. ユーザーストーリー

### 2.1 対象ユーザー
match-trackerに登録された全プレイヤー

### 2.2 利用シナリオ
1. プレイヤーが設定画面で「カレンダー購読URL」を取得しコピーする
2. Googleカレンダーで「他のカレンダー → URLで追加」にそのURLを貼り付ける
3. 自分のGoogleカレンダーに購読カレンダーが追加され、未来の参加練習が「`{団体名}＠{会場名}`」というタイトルで表示される
4. 練習の追加・キャンセルなどがあった場合、購読側の自動取得タイミングで反映される（数時間ラグあり）
5. URLが漏れたなどの理由で再発行したい場合、設定画面の「URL再発行」ボタンで無効化＋新URL発行できる
6. 設定画面で所属団体ごとに表示名（団体名部分）をカスタマイズできる

## 3. 機能要件

### 3.1 画面仕様

#### 設定画面（`SettingsPage.jsx`）

**新規セクション「カレンダー購読」を追加**：

| UI要素 | 内容 |
|--------|------|
| URL表示 | 読み取り専用テキストフィールド（`https://<host>/ical/calendar/{token}.ics`） |
| コピーボタン | URLをクリップボードへコピー |
| 再発行ボタン | 確認ダイアログ：「再発行すると現在のURLは無効になり、Googleカレンダー側で再登録が必要です。続行しますか？」→ 確定で新URL発行 |
| 表示名カスタマイズ | 所属団体ごとに入力欄。プレースホルダーに `Organization.name` を表示。空欄なら未設定扱い |
| 説明文 | 「このURLをGoogleカレンダーの『他のカレンダー → URLで追加』で登録すると、未来の参加練習が自動表示されます。」 |

**削除するUI**：
- 既存のGoogle Calendar OAuth同期ボタン（[SettingsPage.jsx:131](karuta-tracker-ui/src/pages/SettingsPage.jsx#L131) 付近）と関連state

#### バリデーション
- 表示名カスタマイズ：0〜50文字。空文字保存時はNULL扱い

### 3.2 ビジネスルール

#### feed_token
- 推測困難なランダム文字列（32文字以上、URLセーフ）
- プレイヤー1人につき1つ保持（再発行時に上書き）
- **生成タイミング**：
  - 新規プレイヤー作成時：Entity `@PrePersist` で自動生成
  - 既存プレイヤー：DBマイグレーション内で一括生成（NULL残りなし）

#### 同期対象
- ユーザーが参加する練習で、`session_date >= today` のもの
- ステータス CANCELLED は **除外**（カレンダーに残ってると紛らわしいため）
- ゲスト参加（未所属団体の練習）も含める

#### イベントタイトル
- 形式：`{表示名}＠{会場名}`
- 表示名：`PlayerOrganization.calendar_display_name`（未所属団体 or NULLの場合は `Organization.name`）
- 会場名：`Venue.name`
- 例：「わすら＠すずらん」「北大＠クラ館」

#### イベント時刻
- 既存`GoogleCalendarSyncService`のロジックを踏襲
  - `PracticeSession.start_time`/`end_time` があればそれを使う
  - なければ `VenueMatchSchedule` から minMatch/maxMatch の開始・終了時刻を取得
  - 取れなければ全日イベント
- タイムゾーン：Asia/Tokyo 固定

#### イベント説明（DESCRIPTION）
- 「試合数：{minMatch}〜{maxMatch}試合」程度（既存OAuth方式の踏襲）

#### イベントUID（VEVENT.UID）
- `session-{sessionId}-player-{playerId}@match-tracker` のような決定的な値
- 同じイベントが更新されたら同じUIDで上書きされる（カレンダー側で重複生成されない）

### 3.3 エラーケース

| ケース | 挙動 |
|--------|------|
| token が存在しない | HTTP 404 |
| token に対応するプレイヤーが論理削除済み | HTTP 404 |
| 未来の参加練習が0件 | HTTP 200、VCALENDARコンポーネントのみ（VEVENTなし）を返す |

### 3.4 URL設計
- フィード本体：`GET /ical/calendar/{token}.ics`
  - 認証なし（公開エンドポイント。tokenが事実上の認証）
  - レスポンス：`Content-Type: text/calendar; charset=utf-8`

## 4. 技術設計

### 4.1 API設計

#### 公開エンドポイント
| Method | Path | 認証 | 説明 |
|--------|------|------|------|
| GET | `/ical/calendar/{token}.ics` | なし | iCal形式のフィードを返す |

#### 認証必須エンドポイント
| Method | Path | 説明 |
|--------|------|------|
| GET | `/api/calendar/feed/info` | 自分のフィードURLと所属団体・表示名一覧を返す |
| POST | `/api/calendar/feed/regenerate` | feed_tokenを再発行 |
| PATCH | `/api/calendar/feed/display-names` | 所属団体ごとの表示名を一括更新 |

#### レスポンス例
```json
// GET /api/calendar/feed/info
{
  "url": "https://example.com/ical/calendar/abc123...xyz.ics",
  "organizations": [
    {"organizationId": 1, "organizationName": "早稲田カルタ会", "displayName": "わすら"},
    {"organizationId": 2, "organizationName": "北海道大学", "displayName": null}
  ]
}
```

```json
// PATCH /api/calendar/feed/display-names
// Request
{"displayNames": {"1": "わすら", "2": null}}
// Response: 更新後の状態を info と同じ形式で返す
```

### 4.2 DB設計

#### `players` テーブル（変更）
```sql
ALTER TABLE players ADD COLUMN ical_feed_token VARCHAR(64);
CREATE UNIQUE INDEX idx_players_ical_feed_token ON players(ical_feed_token);
-- 既存プレイヤー全員にランダムなトークンを生成
UPDATE players SET ical_feed_token = encode(gen_random_bytes(24), 'hex') WHERE ical_feed_token IS NULL;
ALTER TABLE players ALTER COLUMN ical_feed_token SET NOT NULL;
```

#### `player_organizations` テーブル（変更）
```sql
ALTER TABLE player_organizations ADD COLUMN calendar_display_name VARCHAR(50);
```

#### 削除するテーブル
```sql
DROP TABLE google_calendar_events;
```

#### マイグレーションファイル
- `database/<次の番号>_add_ical_feed.sql`
- CLAUDE.md ルールに基づき本番DB（Render PostgreSQL）にも適用必須

### 4.3 フロントエンド設計

#### 新規API client：`api/icalCalendar.js`
```javascript
- getFeedInfo()              // GET /api/calendar/feed/info
- regenerateFeed()           // POST /api/calendar/feed/regenerate
- updateDisplayNames(map)    // PATCH /api/calendar/feed/display-names
```

#### `SettingsPage.jsx` 変更内容
- **削除**：`calSyncing` / `calSyncMessage` / `calSyncError` state、`handleCalendarSync` 関数、Google Calendar同期ボタンJSX
- **追加**：`feedUrl` / `organizations` / `displayNames` / `regenerating` state、`useEffect` で初回 `getFeedInfo()` 実行、再発行ボタンと表示名カスタマイズUI

#### `karuta-tracker-ui/index.html`
- Google GIS CDN（`<script src="https://accounts.google.com/gsi/client" ...>`）削除
  - 他で使われていないか念のため確認

#### `karuta-tracker-ui/src/api/calendar.js`
- 削除（新しい `icalCalendar.js` で置き換え）

### 4.4 バックエンド設計

#### 既存エンティティ変更
- `entity/Player.java`：`icalFeedToken` フィールド追加（`@PrePersist` で生成）
- `entity/PlayerOrganization.java`：`calendarDisplayName` フィールド追加

#### 新規Repository メソッド
- `PlayerRepository#findByIcalFeedToken(String token)`

#### 新規Controller
- `controller/IcalCalendarFeedController.java`
  - `@GetMapping("/ical/calendar/{token}.ics")` 公開エンドポイント
- `controller/IcalCalendarSettingsController.java`
  - 認証必須3エンドポイント

#### 新規Service
- `service/IcalCalendarFeedService.java`
  - `generateIcsForToken(String token)` → String（icsテキスト）
  - `getOrCreateTokenForPlayer(Long playerId)` → String
  - `regenerateTokenForPlayer(Long playerId)` → String
  - `getFeedInfo(Long playerId)` → DTO
  - `updateDisplayNames(Long playerId, Map<Long, String>)` → DTO

#### 削除するクラス・ファイル
- `service/GoogleCalendarSyncService.java`
- `controller/GoogleCalendarController.java`
- `entity/GoogleCalendarEvent.java`
- `repository/GoogleCalendarEventRepository.java`
- 関連DTO（`CalendarSyncRequest` 等）
- 関連テスト

#### 依存ライブラリ追加（`build.gradle`）
```gradle
implementation 'net.sf.biweekly:biweekly:0.6.7'
```

#### 削除する依存
- `com.google.api-client:google-api-client`
- `com.google.apis:google-api-services-calendar`
- `com.google.auth:google-auth-library-oauth2-http`
- （他Google Calendar専用のもの。他で使われていないか確認）

#### 処理フロー（フィード取得 `GET /ical/calendar/{token}.ics`）
1. token から `playerRepository.findByIcalFeedToken(token)` でPlayer検索
2. 該当なし or 論理削除済みなら404
3. `practiceParticipantRepository.findUpcomingParticipations(playerId, today)` で参加練習取得
4. ステータス CANCELLED を除外
5. 各参加について：
   - 関連 PracticeSession / Organization / Venue / PlayerOrganization をまとめて取得
   - `displayName = playerOrg.calendarDisplayName ?? org.name`
   - `title = displayName + "＠" + venue.name`
   - 時刻は既存 `GoogleCalendarSyncService` のロジックを踏襲
   - VEVENT を構築（UID: `session-{sid}-player-{pid}@match-tracker`）
6. VCALENDARをbiweicklyでテキスト化、レスポンス返却

## 5. 影響範囲

### 5.1 変更が必要な既存ファイル

**バックエンド**：
- `entity/Player.java` — `ical_feed_token` フィールド追加
- `entity/PlayerOrganization.java` — `calendar_display_name` フィールド追加
- `repository/PlayerRepository.java` — `findByIcalFeedToken` メソッド追加
- `build.gradle` — 依存追加・削除

**フロントエンド**：
- `karuta-tracker-ui/src/pages/SettingsPage.jsx` — 既存Calendar UI削除、新UI追加
- `karuta-tracker-ui/index.html` — GIS CDN削除

**DB**：
- `players` テーブル — カラム追加
- `player_organizations` テーブル — カラム追加
- `google_calendar_events` テーブル — 削除

### 5.2 削除対象ファイル
- バックエンド：`GoogleCalendarSyncService.java`, `GoogleCalendarController.java`, `GoogleCalendarEvent.java`, `GoogleCalendarEventRepository.java`, 関連DTO・テスト
- フロントエンド：`karuta-tracker-ui/src/api/calendar.js`

### 5.3 既存機能への影響
- **OAuth方式のGoogle Calendar同期を使っていたユーザー（テストユーザー登録済の数名）**：購読URLを新たにGoogleカレンダーに登録し直す必要がある。既存の同期データは消えるが、購読カレンダーとして再表示される
- **共通コンポーネント**：影響なし（設定画面の内側のみ変更）
- **他API**：影響なし

### 5.4 ドキュメント更新
- `docs/SPECIFICATION.md` — Google Calendar機能の記述を新方式に書き換え
- `docs/SCREEN_LIST.md` — 設定画面の項目変更を反映
- `docs/DESIGN.md` — 同期方式の設計を新方式に書き換え

## 6. 設計判断の根拠

### 6.1 なぜiCalフィード方式か
- Google Cloud Consoleのテストユーザー制限から完全に解放される（全メンバー利用可能）
- Google審査が不要（制限付きスコープを使わないため）
- Apple Calendar / Outlookも同じURLで対応可能
- サーバ側のスケジューラ実装が不要（購読側が自動取得）

### 6.2 なぜ既存OAuth方式を削除するか
- 2つの方式を維持するとコードとUI両面でメンテコストが二重化
- ユーザーが「どちらを使うべきか」迷う
- 既存OAuth方式は最大100ユーザー上限のためそもそも本格運用に耐えない
- 「OAuthのコードを残す」コストに見合うメリットがない（後で必要になれば git 履歴から復元可能）

### 6.3 なぜ表示名は PlayerOrganization に持たせるか
- 同じユーザーが「わすら」「北大」のように団体ごとに違う表示名を使い分けたい要件
- `player_organizations` は既に多対多のJunctionテーブルとして存在しており、追加列が自然
- 別テーブル化はYAGNI

### 6.4 なぜCANCELLED参加は除外するか
- カレンダーに「参加予定だがキャンセルした練習」が残っているのは紛らわしい
- ユーザーがアプリでキャンセルした時、次のフェッチで自動的にカレンダーから消える方が直感的

### 6.5 なぜtokenは32文字程度か
- 192bit以上のエントロピーを確保（推測攻撃に対して十分）
- URLとして扱える長さに収める

### 6.6 ゲスト参加（未所属団体の練習）の扱い
- カレンダーに含める：参加したのは事実なので隠す理由がない
- 表示名は `Organization.name` 固定：所属していない団体に対する表示名カスタマイズUIを出すと設定画面が煩雑になる。所属したら設定できるようになる
