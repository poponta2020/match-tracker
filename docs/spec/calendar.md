# カレンダー購読

> **責務:** iCal フィードによるカレンダー購読の仕様
>
> **関連画面:** `/settings/calendar`（カレンダー購読）
>
> **主要実装:**
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/IcalCalendarFeedController.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/IcalCalendarSettingsController.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/IcalCalendarFeedService.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/FeedInfoDto.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/PlayerOrganization.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/Organization.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/ParticipantStatus.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/PracticeSession.java`
> - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/VenueMatchSchedule.java`
> - `karuta-tracker-ui/src/pages/CalendarSubscriptionPage.jsx`
> - `karuta-tracker-ui/src/api/icalCalendar.js`

## 機能仕様

### 概要

選手ごとに「所属団体ごと + ゲスト参加」でフィードURLを発行し、Googleカレンダー・Apple Calendar・Outlook などの外部カレンダーから購読する一方向の同期。**カレンダー単位を団体別に分けることで、購読側で団体ごとに色分けできる**。購読側が定期取得（数時間〜半日間隔）するため、追加・キャンセルの反映には数時間のラグがあるが、サーバ側スケジューラ不要で実質自動同期される。

### URL構造

- 所属団体ごと: `GET /ical/calendar/{token}/org/{orgId}.ics`
- ゲスト参加（所属していない団体の練習）: `GET /ical/calendar/{token}/guest.ics`
- 親トークンは `players.ical_feed_token`（プレイヤー1人に1つ）。複数URLを同じ親トークンで発行する

### 認証

- 公開エンドポイント（認証なし）。`players.ical_feed_token`（推測困難なランダム48文字）が事実上の認証
- 設定画面用の3エンドポイント（`/api/calendar/feed/...`）は通常の認証（X-User-Id ヘッダ）必須
- 漏洩時はユーザー自身が設定画面で「URL再発行」ボタン → 旧トークン即時無効化（**所属団体URL・ゲストURLすべてが一斉に切り替わる**）

### フィード生成ロジック

共通:
1. リクエストの token から Player を検索（論理削除済みは404）
2. `findAllParticipationsByPlayer(playerId)` で**全期間（過去・未来とも）**の参加練習を取得（カレンダーアプリで過去を見返す体験を維持するため）
3. `ParticipantStatus.isActive()`（WON / PENDING のみ）でフィルタ

所属団体フィード (`/org/{orgId}.ics`):
4. プレイヤーが orgId に所属していなければ 404
5. `session.organizationId == orgId` でさらにフィルタ
6. **VCALENDAR の X-WR-CALNAME** に該当団体の表示名（`PlayerOrganization.calendar_display_name` ?? `Organization.name`）

ゲストフィード (`/guest.ics`):
4. プレイヤーが所属する組織ID集合に**含まれない**練習のみ抽出
5. **VCALENDAR の X-WR-CALNAME** に `"ゲスト参加"` 固定

各 VEVENT は両フィード共通:
- **UID**: `session-{sessionId}-player-{playerId}@match-tracker`（決定的、再生成時に同UIDで上書きされる）
- **タイトル**: `{表示名}＠{会場名}` 表示名は `PlayerOrganization.calendar_display_name` を優先、未設定 or 未所属（ゲスト参加）なら `Organization.name`
- **時刻**: プレイヤーが**実際に参加する試合の時間帯**に絞り込む（下記「時刻決定アルゴリズム」参照）
- **タイムゾーン**: Asia/Tokyo（全日イベントは TZ非依存の DateTimeComponents で構築）

#### 時刻決定アルゴリズム

1 つの `PracticeSession` に対するプレイヤーの参加レコード集合（`isActive()` フィルタ後）から、以下の手順で `startTime`/`endTime` を決める。

- **条件A**: 当該 session の全参加レコードに `match_number` が入っている（`null` が 1 件もない）
- **条件B**: `session.venue_id` が設定されており、参加 `match_number` のうち少なくとも 1 件分の `VenueMatchSchedule` が登録されている

| パターン | startTime / endTime |
|---------|---------------------|
| 条件A・B 両方成立 | 参加 `match_number` のうち `VenueMatchSchedule` が登録されている番号で `min(start_time)` 〜 `max(end_time)` |
| 条件A or B が不成立 | `PracticeSession.start_time` / `end_time` にフォールバック（旧来挙動） |
| 上記の startTime/endTime が両方 `null` | 全日イベント（`VALUE=DATE`） |
| startTime のみ存在、endTime が `null`（フォールバック時のみ発生） | `endTime = startTime.plusHours(4)` |

例: 練習が会場 A で 13:00〜17:00（試合1〜6）、プレイヤーが試合 3〜6 のみに参加登録 → イベント時刻は **14:00〜17:00**（試合3 開始〜試合6 終了）になる。全試合参加なら 13:00〜17:00。

**補足:**
- **混在ケース**（同じ session で `match_number` ありレコードと `null` レコードが混在）: `null` が 1 件でもあれば条件A 不成立 → session 全体時刻を採用（`match_number=null` は「全試合参加」の表現として使われる運用を壊さないため）
- **部分スケジュール**（参加 `match_number` の一部しか `VenueMatchSchedule` が無い）: 登録済みの分だけで `min`/`max` を計算（範囲は狭くなる可能性あり）
- **会場スケジュール未登録**（条件B 不成立）: session 全体時刻にフォールバック
- **バッファ時間**: 前後の受付・撤収時間は加味しない（試合の開始〜終了時刻ぴったり）
- **UID 不変**: イベント時刻が変わっても UID は同一なので、Google カレンダー等では同じイベントが「更新」される（重複登録にはならない）

biweekly ライブラリで VCALENDAR にまとめてテキスト返却。

### 表示名カスタマイズ

- プレイヤー×団体ごとに表示名を設定可能（`PlayerOrganization.calendar_display_name`、0〜50文字）
- 1人のユーザーが複数団体に所属する場合に「わすら＠すずらん」「北大＠クラ館」のように使い分けできる
- ゲスト参加フィード内のイベントタイトルは `Organization.name` 固定（カスタマイズ対象外。`PlayerOrganization` が存在しないため）

### 画面UI（設定 → カレンダー購読）

設定画面の「カレンダー購読」（`/settings/calendar`、`CalendarSubscriptionPage.jsx`）は以下の構成。**外部ドキュメントに頼らず画面内だけで使い方を完結できる**ことを目的とする:

1. **「このページについて」常時表示ボックス**（画面冒頭、青系背景でわずかに目立たせる）
   - この画面が何のための画面か（自動表示の仕組み）
   - 使い方3ステップ（URLコピー → カレンダーアプリで貼り付け → 以降は自動更新）
   - カレンダーに表示されるもの（今日以降の参加予定の練習のみ、キャンセル済みは非表示、所属団体ごと＋ゲスト参加で別URLになり団体別に色分けできる）
2. **フィードURLカード**（所属団体ごと + ゲスト参加）
   - URL読み取り専用入力 + コピーボタン
   - URL欄の下にサブテキスト「このURLをコピーして、下の『登録手順を見る』のとおりカレンダーアプリに貼り付けてください」を常時表示（コピー成功時のみ2秒間「コピーしました」に切り替え）
3. **「登録手順を見る」アコーディオン**（折りたたみ式、初期は全て閉じる、同時に1つだけ開く運用）
   - 「Google カレンダー（PCブラウザ）」: `calendar.google.com` → 左メニュー「他のカレンダー」+ → 「URLで追加」 → 貼り付けの番号付き4ステップ。スマホアプリからは追加不可・PCで一度追加すれば同期される旨の注記つき
   - 「Apple カレンダー（iPhone）」: 設定アプリ → カレンダー → アカウント追加 → その他 → 照会するカレンダーを追加 → URL貼り付け → 保存の番号付き5ステップ
   - 文字テキストのみ（スクリーンショットは含めない）
4. **所属団体ごとの表示名カスタマイズ** — カレンダー上のイベントタイトルが `{表示名}＠{会場名}` 形式（例: 「わすら＠すずらん」）になることを説明文に明記し、空欄なら団体名が使われる旨を併記
5. **URL再発行ボタン** — `window.confirm` で「全URLが一斉に無効・再登録が必要」と警告

## API

公開エンドポイント（認証なし。token が事実上の認証）:

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `/ical/calendar/{token}/org/{orgId}.ics` | — | 所属団体ごとのiCalフィード（プレイヤーがorgIdに所属していなければ404） |
| GET | `/ical/calendar/{token}/guest.ics` | — | ゲスト参加（所属外団体の練習）のiCalフィード |

設定画面用エンドポイント (`/api/calendar/feed`、認証必須):

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `/info` | ALL | 所属団体ごとのフィードURL一覧 + ゲスト参加URL |
| POST | `/regenerate` | ALL | feed_tokenを再発行（**全URLが一斉に無効**） |
| PATCH | `/display-names` | ALL | 所属団体ごとの表示名（calendar_display_name）を一括更新 |

### GET /ical/calendar/{token}/org/{orgId}.ics
**説明**: 所属団体ごとのiCalフィードを配信。Googleカレンダー等の購読クライアントから直接取得される。
**権限**: 認証なし（推測困難なtokenが事実上の認証）
**404条件**: token無効、論理削除済みプレイヤー、プレイヤーがorgIdに所属していない
**レスポンス**: `Content-Type: text/calendar;charset=UTF-8`、VCALENDAR の X-WR-CALNAME に該当団体の表示名

### GET /ical/calendar/{token}/guest.ics
**説明**: ゲスト参加（プレイヤーが所属していない団体の練習）専用のiCalフィード
**権限**: 認証なし
**404条件**: token無効、論理削除済みプレイヤー
**レスポンス**: `Content-Type: text/calendar;charset=UTF-8`、VCALENDAR の X-WR-CALNAME は `"ゲスト参加"` 固定

### GET /api/calendar/feed/info
**説明**: 所属団体ごとのフィードURL一覧 + ゲスト参加URL を取得
**権限**: ALL（自分自身のリソースのみ）
**レスポンス**: `FeedInfoDto`
```json
{
  "organizationFeeds": [
    { "organizationId": 1, "organizationName": "早稲田カルタ会", "displayName": "わすら", "url": "https://.../ical/calendar/{token}/org/1.ics" }
  ],
  "guestFeed": { "url": "https://.../ical/calendar/{token}/guest.ics" }
}
```

### POST /api/calendar/feed/regenerate
**説明**: iCalフィードトークンを再発行（**所属団体URL・ゲストURL すべてが一斉に無効**）
**権限**: ALL
**レスポンス**: `FeedInfoDto` (再発行後の状態)

### PATCH /api/calendar/feed/display-names
**説明**: 所属団体ごとの表示名（カレンダー上の団体名）を一括更新
**権限**: ALL
**リクエスト**: `{ "displayNames": { "<organizationId>": "<表示名>" | null } }`
**レスポンス**: `FeedInfoDto` (更新後の状態)
