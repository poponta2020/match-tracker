---
status: completed
---
# 団体別iCalフィード 要件定義書

## 1. 概要

### 1.1 目的
プレイヤーが購読するカレンダーフィードを「所属団体ごと」「ゲスト参加」に分割し、Googleカレンダー等で**団体ごとに別カレンダー扱い**にすることで、購読側で団体ごとに色分けできるようにする。

### 1.2 背景・動機
- 現在のiCalフィードは1本にプレイヤーの全参加練習が入っており、Googleカレンダー上では同色で表示される
- 「わすら」と「北大」のように複数団体に所属するプレイヤーは、見た目で団体を区別できない
- フィードをカレンダー単位で分けると、Google側の「カレンダー色設定」で団体ごとに色を付けられる

## 2. ユーザーストーリー

### 2.1 対象ユーザー
- 複数団体に所属する match-tracker プレイヤー
- ゲスト参加もする活発なプレイヤー
- 1団体のみ所属のプレイヤーも対象（既存仕様が破壊的に変わるため）

### 2.2 利用シナリオ
1. プレイヤーが設定画面「カレンダー購読」を開く
2. 所属団体ごとに別のURLが表示される（例: わすら / 北大 用に2つ） + ゲスト参加用のURL 1つ
3. それぞれのURLをGoogleカレンダーに「URLで追加」で登録する
4. Googleカレンダー上に「わすら」「北大」「ゲスト参加」と3つの購読カレンダーが並ぶ
5. Googleカレンダーの設定で各カレンダーに別々の色をつける
6. 練習日に色違いで予定が表示され、所属団体が一目でわかる

## 3. 機能要件

### 3.1 画面仕様

#### 設定画面（`CalendarSubscriptionPage.jsx`）

**変更後のレイアウト**：

| セクション | 内容 |
|-----------|------|
| 説明文 | 「所属団体ごと（＋ゲスト参加）にURLが分かれています。Googleカレンダー等で各URLを別カレンダーとして登録すると、団体ごとに色を分けられます。」 |
| 所属団体ごとのカード（団体数分） | カレンダー名（団体の表示名）、URL（読み取り専用）、コピーボタン |
| 「ゲスト参加」カード | カレンダー名「ゲスト参加」、URL、コピーボタン |
| 表示名カスタマイズ | 既存と同じ。所属団体ごとに表示名（プレースホルダー: Organization.name） |
| URL一括再発行ボタン | 押すと全URL（団体別＋ゲスト）が一斉に新URLに切り替わる |

#### バリデーション
- 既存と同じ（表示名 0〜50文字）

### 3.2 ビジネスルール

#### カレンダー単位の分割ルール
- **所属団体ごと**: `PlayerOrganization` のエントリ1つにつき、1つの購読URLを発行
- **ゲスト参加**: ユーザーが所属していない団体の練習を1つにまとめた専用URL
- 1団体所属のプレイヤーでも、団体カレンダーとゲスト参加カレンダー（空かもしれない）の2つが必ず提示される

#### カレンダー名（VCALENDAR `X-WR-CALNAME`）
- 所属団体カレンダー: `PlayerOrganization.calendar_display_name`（NULLなら `Organization.name`）
- ゲスト参加カレンダー: `"ゲスト参加"` 固定

#### イベントタイトル
- 全カレンダー共通: `{表示名}＠{会場名}`（既存と同じ）
- 所属団体カレンダー内: 表示名は `calendar_display_name ?? Organization.name`
- ゲスト参加カレンダー内: 表示名は常に `Organization.name`（カスタマイズ対象外）

#### URL構造
- 所属団体: `GET /ical/calendar/{playerToken}/org/{orgId}.ics`
- ゲスト参加: `GET /ical/calendar/{playerToken}/guest.ics`
- 親トークンは `Player.ical_feed_token`（既存カラムをそのまま流用）

#### トークン再発行
- ボタンは画面に1つ（「全カレンダーURL再発行」）
- 押すと `Player.ical_feed_token` を上書き → 全URLが一斉に新トークンに切り替わる
- 既存購読は無効化（404）、ユーザーは新URLで再登録が必要

#### 同期対象
- 既存と同じ（**全期間（過去・未来とも）**、`ParticipantStatus.isActive()` のみ＝WON/PENDING）
- 各URLのスコープ：
  - 団体URL: `practice_session.organization_id == orgId` かつ「ユーザーが組織所属」のもの
  - ゲストURL: `practice_session.organization_id` が「ユーザーが所属していない団体」のもの

### 3.3 エラーケース

| ケース | 挙動 |
|--------|------|
| 無効なtoken | HTTP 404（既存と同じ） |
| 論理削除済みプレイヤー | HTTP 404 |
| `orgId` がユーザーの所属団体でない | HTTP 404（団体URLとして無効） |
| ゲスト参加対象が0件 | HTTP 200、VCALENDARコンポーネントのみ（VEVENTなし） |
| 所属団体だが参加練習0件 | HTTP 200、VEVENTなし |

### 3.4 既存全体一括フィードの廃止
- `GET /ical/calendar/{token}.ics` は **削除**
- 廃止後は404を返す（リダイレクト等はしない）
- 影響：既存のApple/Googleカレンダー登録は失効するため、ユーザーは新URLで再登録が必要
- 現状ほぼ運用されていないため影響は限定的

## 4. 技術設計

### 4.1 API設計

#### 公開エンドポイント（変更）
| Method | Path | 認証 | 説明 |
|--------|------|------|------|
| ~~GET~~ | ~~`/ical/calendar/{token}.ics`~~ | — | **削除** |
| GET | `/ical/calendar/{token}/org/{orgId}.ics` | なし | 所属団体のiCalフィード |
| GET | `/ical/calendar/{token}/guest.ics` | なし | ゲスト参加のiCalフィード |

#### 認証必須エンドポイント
| Method | Path | 説明 | 変更 |
|--------|------|------|------|
| GET | `/api/calendar/feed/info` | フィードURL一覧+所属団体・表示名 | レスポンス形式変更 |
| POST | `/api/calendar/feed/regenerate` | feed_token を再発行 | 変更なし |
| PATCH | `/api/calendar/feed/display-names` | 表示名を一括更新 | 変更なし |

#### レスポンス例：`GET /api/calendar/feed/info`
```json
{
  "organizationFeeds": [
    {
      "organizationId": 1,
      "organizationName": "早稲田カルタ会",
      "displayName": "わすら",
      "url": "https://example.com/ical/calendar/abc.../org/1.ics"
    },
    {
      "organizationId": 2,
      "organizationName": "北海道大学",
      "displayName": null,
      "url": "https://example.com/ical/calendar/abc.../org/2.ics"
    }
  ],
  "guestFeed": {
    "url": "https://example.com/ical/calendar/abc.../guest.ics"
  }
}
```

### 4.2 DB設計

**新規カラム・テーブルなし**。既存スキーマで対応可能。

- `Player.ical_feed_token`（既存）→ 親トークンとして流用
- `PlayerOrganization.calendar_display_name`（既存）→ そのまま使用
- 不要になったカラムや列なし

### 4.3 フロントエンド設計

#### `CalendarSubscriptionPage.jsx` 変更内容
- **state**:
  - `feedInfo`: `{ organizationFeeds: [...], guestFeed: {...} }` 形式に変更
  - `displayNamesDraft`: 既存維持
  - `regenerating` / `saving` / `error` / `success`: 維持
  - `copyFeedbackId`: コピーしたカードのIDを記録（複数URLそれぞれにフィードバック表示）

- **UI**:
  - 既存の単一URLカード → 「所属団体ごとのカード（複数）」+「ゲスト参加カード」に分割
  - 各カード：カレンダー名（表示名 or Organization.name）、URL（readOnly input + コピーボタン）
  - 表示名カスタマイズフィールド：既存と同じ（団体ごとに1つの入力欄）
  - URL再発行ボタン：1つ（既存と同じ動作、ただし全URLが切り替わる旨を確認ダイアログに明記）

#### `karuta-tracker-ui/src/api/icalCalendar.js`
- 変更なし（エンドポイントとレスポンス形式が変わるだけ、関数シグネチャは同じ）

### 4.4 バックエンド設計

#### `IcalCalendarFeedController.java`
- `getFeed(String token)` → 削除
- `getOrgFeed(String token, Long orgId)` 追加: `GET /ical/calendar/{token}/org/{orgId}.ics`
- `getGuestFeed(String token)` 追加: `GET /ical/calendar/{token}/guest.ics`

#### `IcalCalendarFeedService.java`
- `generateIcsForToken(String token)` → 削除（後方互換性不要）
- `generateIcsForOrgFeed(String token, Long orgId)` 追加
- `generateIcsForGuestFeed(String token)` 追加
- 内部共通処理を private ヘルパーに切り出し：
  - `loadPlayerByToken(String token)`: トークンから Player を引く
  - `buildIcalendarForParticipations(List<PracticeParticipant>, String calendarName, ...)`: VCALENDAR 構築
- `getFeedInfo(Long playerId)` のレスポンスを新形式（organizationFeeds + guestFeed）に変更

#### `FeedInfoDto.java` 変更
- `String url` → 削除
- `List<OrganizationFeedDto> organizationFeeds` 追加
- `GuestFeedDto guestFeed` 追加

#### 新規DTO
- `OrganizationFeedDto`: `{ organizationId, organizationName, displayName, url }`
- `GuestFeedDto`: `{ url }`

#### `CalendarOrganizationDto.java`
- 削除（`OrganizationFeedDto` で代替）

#### 処理フロー：`getOrgFeed(token, orgId)`
1. `playerRepository.findByIcalFeedTokenAndActive(token)` で Player 取得（なければ404）
2. プレイヤーが orgId に所属していなければ404（`playerOrganizationRepository.findByPlayerIdAndOrganizationId` でチェック）
3. `practiceParticipantRepository.findAllParticipationsByPlayer(playerId)` で全期間の参加練習取得
4. `session.organizationId == orgId` のもののみフィルタ
5. `isActive()` でステータスフィルタ
6. VCALENDAR を構築（X-WR-CALNAME に表示名）

#### 処理フロー：`getGuestFeed(token)`
1. Player 取得（なければ404）
2. プレイヤーの所属組織ID一覧を取得（`playerOrganizationRepository.findByPlayerId`）
3. 全参加練習取得 → `isActive()` フィルタ
4. `session.organizationId` が所属組織IDに**含まれない**もののみ抽出
5. VCALENDAR を構築（X-WR-CALNAME = "ゲスト参加"）

## 5. 影響範囲

### 5.1 変更が必要な既存ファイル

**バックエンド**：
- `controller/IcalCalendarFeedController.java` - メソッド削除・追加
- `service/IcalCalendarFeedService.java` - メソッド削除・追加・改修
- `dto/FeedInfoDto.java` - フィールド変更
- `dto/CalendarOrganizationDto.java` - 削除
- `dto/OrganizationFeedDto.java` - 新規作成
- `dto/GuestFeedDto.java` - 新規作成

**フロントエンド**：
- `karuta-tracker-ui/src/pages/CalendarSubscriptionPage.jsx` - state/UI 大幅改修

**テスト**：
- `service/IcalCalendarFeedServiceTest.java` - 既存テスト改修＋新規ケース
- `controller/IcalCalendarFeedControllerTest.java` - メソッド変更
- `controller/IcalCalendarSettingsControllerTest.java` - 既存維持（レスポンス形式の検証は改修）

**ドキュメント**：
- `docs/SPECIFICATION.md` - URL構造・カレンダー仕様の更新
- `docs/SCREEN_LIST.md` - 設定画面の説明更新
- `docs/DESIGN.md` - 同期方式の説明更新
- `docs/features/ical-calendar-feed/requirements.md` - 旧仕様。touch しない（履歴として残す）

### 5.2 既存機能への影響

- **既存の Apple/Google カレンダー購読**：既存URL `/ical/calendar/{token}.ics` が削除されるため失効する
  - 現状本番運用が始まったばかりで、登録済みユーザーは1名程度
  - 新URL（団体別＋ゲスト）に登録し直す必要がある
- **`PlayerOrganization.calendar_display_name`**: そのまま流用、既存設定は保持
- **`Player.ical_feed_token`**: そのまま流用、既存トークン値は無効化しない（ただし古いURLパターンでアクセスすると404）
- **DBマイグレーション**: 不要

### 5.3 共通コンポーネントへの影響
- なし（設定画面の中だけで完結）

## 6. 設計判断の根拠

### 6.1 なぜ団体ごとにカレンダーを分けるか
- Googleカレンダーは「カレンダー単位」でしか色分けできない
- 1つのフィードに全団体の練習が混ざっていると、団体識別が困難
- カレンダー分割により、購読側の標準UIで色分けが可能になる

### 6.2 なぜ親トークン方式（PlayerOrganizationごとに別トークンではない）か
- DB変更が不要でシンプル
- 1団体のトークンが漏れた場合、他団体URLも漏れる可能性は高いが、一括再発行で対処できる
- 「団体ごとに再発行」というきめ細かい運用は現時点で不要（YAGNI）
- 既存ユーザー数が少ないため、過剰設計のコストを払う必要がない

### 6.3 なぜ既存全体一括フィードを削除するか
- 両方提供するとUIが複雑（URL数が多い）
- 「色分けが目的」というユーザー要望に対して、選択肢が多いとミスリードする
- 既存運用ユーザーが少ないため、廃止コストが低い

### 6.4 なぜゲスト参加カレンダーは別カレンダーか
- ゲスト参加は所属外の練習で性質が異なる
- 「自分の正規所属とゲスト参加を区別したい」というニーズは自然
- 1団体のみ所属でゲスト参加するプレイヤーもカレンダー2つで管理できる

### 6.5 なぜゲスト参加カレンダーのイベントタイトルもカスタマイズ不可か
- 所属していない団体に対する表示名は本人が決められない（DB設計的にも `PlayerOrganization` がない）
- カスタマイズUIを増やすと設定画面が煩雑になる
- カレンダー名「ゲスト参加」で区別はつく
