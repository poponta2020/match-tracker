---
status: completed
---
# 伝助ページ自動作成機能（densuke-page-creator） 要件定義書（ドラフト）

## 1. 概要

### 目的
アプリに登録されている練習会場（venues / venue_match_schedules）と練習日程（practice_sessions）を元に、指定された年月の出欠表ページを densuke.biz 上に自動作成する。

### 背景・動機
- 現在は管理者が手動で densuke.biz 上に月次の出欠ページを作成し、練習日・会場・試合枠を手入力している
- アプリにはすでに練習日・会場・試合時間割が登録されており、それを元データに伝助ページを組み立てれば、管理者の手作業を大幅に削減できる
- 既存の `DensukeImportService` / `DensukeWriteService` は「cd が既知の月次ページ」を読み書きする前提なので、「ページそのものの新規発行」機能は現状ゼロ

### 初期確定事項（Q&A 結果）
| 項目 | 確定内容 |
|---|---|
| 作成単位 | 団体 × 年月（既存 `densuke_urls` と同粒度） |
| 作成トリガー | 管理画面から手動。年月選択ドロップダウン + 作成ボタン |
| テンプレート | 団体ごとに事前登録可能。作成時の UI でも編集可能 |
| テンプレート項目 | タイトル、説明／備考、集計締切、その他 densuke.biz 側の必須項目。**主催者名・連絡先は不要** |
| 試合枠数 | `practice_sessions.total_matches` を優先。未設定なら `venues.default_match_count` |
| 試合ごとの開始・終了時刻 | `venue_match_schedules` の `(venue_id, match_number)` → `start_time` / `end_time` |
| 既存 `densuke_urls` がある場合の扱い | 既存 URL を残して新規作成をスキップ（上書きしない） |
| 対象会場 | 全練習日（かでる2・7以外のクラ館・東🌸なども含めてまとめて送信） |
| 機能名 | `densuke-page-creator` |

### 最大の未確定事項（実装着手前に解析必須）
- **densuke.biz の新規ページ作成フォーム構造が未解析**
  - POST エンドポイント（URL パス・HTTP メソッド）
  - フォームフィールド名と必須項目
  - 作成には管理者ログインが必要か（必要なら認証情報の保管方針も要検討）
  - 作成完了時に `cd`（サイトコード）がどう返ってくるか
- → ヒアリング完了後、実装タスクの最初に「ユーザーがブラウザ DevTools で実際に伝助ページ作成操作を行い、通信ログを共有」する手順を組む

## 2. ユーザーストーリー

### 対象ユーザー
- **SUPER_ADMIN + ADMIN**（既存の伝助関連 API と同じ権限ポリシー）

### ユーザーの目的
- アプリ側で練習日・会場・試合枠の情報を管理している上で、月初のメンバー周知に使う「伝助ページ」を手動作成する手間を無くす
- アプリ内の練習データを真のマスタとし、伝助ページはそれを反映した「出欠収集の窓口」として機能させる

### 利用シナリオ（典型フロー）
1. 管理者が当月～翌月の練習日を `practice_sessions` に一通り登録し終える
2. 伝助管理画面を開き、対象年月を選択して「伝助ページ作成」ボタンを押す
3. 作成ダイアログでテンプレート（タイトル／説明／締切など）の初期値を確認、必要があれば編集
4. 確定ボタンで densuke.biz に新規ページが作成され、発行された `cd` が `densuke_urls` に保存される
5. 管理者はその伝助 URL をメンバーに周知（コピー or 既存の共有機能）
6. 以降は既存の `DensukeSyncScheduler`（5 分間隔）が自動的にそのページを読み込み、メンバーの出欠登録がアプリ側に反映される

### 成功の定義
- **必須:** 指定月の全練習日×試合枠が、伝助ページ上の日程として並んでいる
- **必須（暗黙）:** 伝助ページが作成され、`cd` が発行される／発行された URL が `densuke_urls` に保存される
- **対象外:** 団体メンバーの伝助ページへの初期登録は**行わない**。メンバー登録はアプリ側または伝助側でのユーザー自身の操作に任せる

### 失敗時の挙動
- 伝助との通信が途中で失敗した場合: **エラー表示のみ**
- 伝助側に中途半端なページが残っても、アプリからは削除・ロールバックは試みない。管理者が必要に応じて伝助上で手動削除する

### 作成後のフォローアップ
- 作成直後の即時同期は**行わない**
- 既存の `DensukeSyncScheduler` が次回サイクル（最長5分）で新しい URL を読み込むのを待つ方式に任せる

### メンバーへの通知
- 作成成功後、その団体に所属する **PLAYER ロールのメンバー全員**に、LINE で「練習日程が公開された」旨を通知する
- 通知対象から除外: ADMIN ロール、SUPER_ADMIN ロール（=管理者は既に知っているため不要）
- 通知チャネル: **LINE のみ**（Push・アプリ内通知は送らない）
- LINE 連携していないメンバーには通知が届かない（仕様上の許容）
- 通知送信の失敗はログのみ。作成 API 自体の成功/失敗には影響させない

## 3. 機能要件

### 3.1 画面仕様

#### 作成画面の配置
- 既存の `DensukeManagement.jsx`（伝助管理画面）内の月別 URL カードに「伝助ページ作成」ボタンを追加
- クリックで作成モーダルを表示
- モーダル内容:
  - 対象年月の表示（カードの年月をそのまま使用）
  - テンプレート初期値が入った編集可能なフォーム（タイトル／説明／締切 等）
  - 「作成」「キャンセル」ボタン

#### テンプレート編集画面
- 団体設定画面に新タブ「伝助テンプレート」を追加
- 団体ごとに1レコードのテンプレートを編集・保存

#### 年月選択範囲
- 当月＋未来 2 ヶ月まで（過去月は作成不可）
- 基本的な運用は「翌月分」を作成するユースケースを想定

### 3.2 ビジネスルール

#### 作成スキップ条件
- 対象年月・団体の `densuke_urls` レコードが既に存在する場合は**作成をスキップ**（上書きしない）
- UI 上は該当カードの「作成」ボタンを非活性化 or 表示させない

#### 対象練習日
- 指定年月・指定団体の `practice_sessions` 全件を対象にする
- 会場の種別によらず、かでる以外（クラ館・東🌸など）も全て含めて送信

#### 試合枠の決定ロジック
- 試合数: `practice_sessions.total_matches` を優先。未設定時は `venues.default_match_count`
- 試合ごとの開始・終了時刻: `venue_match_schedules` の `(venue_id, match_number)` → `start_time` / `end_time`

### 3.3 バリデーション・エラーケース

| ケース | 挙動 |
|---|---|
| 対象年月の `practice_sessions` が 0 件 | **エラーで作成中断**。「指定月に練習日が登録されていません」表示 |
| 練習日の会場に `venue_match_schedules` が不足（`total_matches` 未満のレコード数） | **エラーで作成中断**。該当会場を明示してメッセージ表示 |
| 対象年月・団体の `densuke_urls` が既存 | 「既に作成済みです」表示（作成ボタン自体を表示させない実装）|
| densuke.biz との通信失敗（作成POST エラー、ネットワーク/タイムアウト） | エラー表示のみ。ロールバックは試みない |
| densuke.biz 作成後、日程行書き込みで失敗 | エラー表示。伝助側に中途半端なページが残った場合は管理者が手動削除 |

### 3.4 フィードバック

| 状態 | UI |
|---|---|
| 進行中 | モーダル内スピナー、ボタン無効化 |
| 完了 | トーストで伝助 URL 表示、モーダル自動クローズ、月別 URL カード更新（新 URL 反映） |
| 失敗 | モーダル内エラーメッセージ表示、ボタン有効化（再試行可能）|

### 3.5 通知

#### 通知内容
- **タイトル:** `{month}月の練習日程が出ました`（例: `5月の練習日程が出ました`）
- **本文:**
  ```
  {団体名}の{year}年{month}月の練習出欠ページが作成されました。
  以下のリンクから出欠を登録してください:
  {伝助URL}
  ```
- **プレースホルダー:** `{year}`, `{month}`, `{organization_name}`, `{伝助URL}` を Java 側で置換

#### 通知対象
- 指定団体の `player_organizations` に所属する Player のうち、`role = PLAYER` のアクティブメンバー
- LINE 連携済み & `LineNotificationPreference.densukePageCreated = true` のメンバーにのみ送信

#### ON/OFF 設定
- `line_notification_preferences` テーブルに `densuke_page_created BOOLEAN NOT NULL DEFAULT TRUE` 列を追加
- LINE 通知設定画面（既存）に本項目の ON/OFF トグルを追加
- デフォルトは ON

#### 送信タイミング・エラー方針
- 作成 API の処理完了後、トランザクションコミット後に LINE 送信を開始
- 個別メンバーへの送信失敗はログに残すのみ（他メンバーへの送信は継続）
- LINE送信が全員失敗しても作成 API のレスポンスは成功として返す

## 4. 技術設計

### 4.1 API 設計

| Method | Path | 権限 | 用途 |
|---|---|---|---|
| POST | `/api/practice-sessions/densuke/create-page` | ADMIN, SUPER_ADMIN | 伝助ページ作成 |
| GET  | `/api/densuke-templates/{organizationId}` | ADMIN, SUPER_ADMIN | テンプレート取得 |
| PUT  | `/api/densuke-templates/{organizationId}` | ADMIN, SUPER_ADMIN | テンプレート更新 |

#### POST `/api/practice-sessions/densuke/create-page`
**Request:**
```json
{
  "year": 2026,
  "month": 5,
  "organizationId": 1,
  "overrides": {
    "title": "...",           // 任意。未指定ならテンプレート値
    "description": "...",     // 任意
    "contactEmail": "..."     // 任意
  }
}
```

**Response (成功):**
```json
{
  "cd": "abc123",
  "url": "https://densuke.biz/list?cd=abc123",
  "createdDateCount": 8,
  "createdMatchSlotCount": 40
}
```

**Response (失敗):** HTTP 4xx/5xx + メッセージ（バリデーション失敗／伝助通信失敗などケースごと）

### 4.2 DB 設計

#### 既存テーブル変更: `line_notification_preferences`

```sql
ALTER TABLE line_notification_preferences
    ADD COLUMN densuke_page_created BOOLEAN NOT NULL DEFAULT TRUE;
```

#### 新規テーブル: `densuke_templates`

```sql
CREATE TABLE densuke_templates (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL UNIQUE REFERENCES organizations(id),
    title_template VARCHAR(200) NOT NULL,   -- プレースホルダー対応 ({year}, {month}, {organization_name})
    description TEXT,
    contact_email VARCHAR(255),             -- 任意。伝助側フォームで主催者メアド等に使う想定
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

- `organization_id` が UNIQUE（1団体1テンプレート）
- プレースホルダー: `{year}`, `{month}`, `{organization_name}` を Java 側で置換
- 締切カラムは持たない（伝助側のデフォルトに任せる方針）

#### 既存テーブルの変更
- `densuke_urls` テーブル自体はスキーマ変更なし（新規作成した `cd` / `url` / `year` / `month` / `organization_id` を保存するだけ）

### 4.3 バックエンド設計

#### 新規クラス構成
```
service/
├── DensukePageCreateService.java         # 伝助ページ作成の中核
│   ├── createPage(year, month, orgId, overrides, userId)
│   ├── buildTitleFromTemplate(template, year, month, org)
│   ├── postCreatePageRequest(...) — densuke.biz へ新規作成 POST
│   └── postScheduleRows(cd, practiceSessions)  — 日程行書き込み
├── DensukeTemplateService.java           # テンプレート CRUD
│   ├── getTemplate(orgId)
│   └── updateTemplate(orgId, request)

entity/
└── DensukeTemplate.java

repository/
└── DensukeTemplateRepository.java

controller/
├── PracticeSessionController (既存に POST /densuke/create-page を追加)
└── DensukeTemplateController (新規)

dto/
├── DensukePageCreateRequest.java
├── DensukePageCreateResponse.java
├── DensukeTemplateDto.java
└── DensukeTemplateUpdateRequest.java
```

#### 処理フロー（`createPage`）
1. バリデーション:
   - 指定月の `practice_sessions` を取得 → 0 件なら例外
   - 関連 `venue_match_schedules` の整合性チェック（`total_matches` 分の行があるか）→ 不整合なら例外
   - 既存 `densuke_urls` レコード有無チェック → 既存なら例外
2. テンプレート取得（`DensukeTemplateRepository`）。`overrides` で上書き
3. densuke.biz に POST で新規ページ作成 → `cd` を取得（★ 実フォーム解析後に確定）
4. `cd` を元に日程行書き込み（日付 × 試合番号 × 時刻 × 会場）
5. `densuke_urls` に新 URL を保存
6. トランザクションコミット後 (`TransactionSynchronizationManager#registerSynchronization` か `@TransactionalEventListener(phase = AFTER_COMMIT)`) に LINE 通知を発火
7. レスポンス返却

#### LINE 通知の実装方針
- `LineNotificationService` に `sendDensukePageCreatedNotification(Long organizationId, int year, int month, String densukeUrl)` を追加
- 内部で対象団体の PLAYER を列挙し、個別に `sendToPlayer(playerId, LineNotificationType.DENSUKE_PAGE_CREATED, message)` を呼び出す
- `LineNotificationType` enum に `DENSUKE_PAGE_CREATED` を追加
- preference チェックは既存の `sendToPlayer` 内部で実施される前提（現実装に合わせる）

### 4.4 フロントエンド設計

#### 新規／変更コンポーネント
```
pages/densuke/
├── DensukeManagement.jsx                 # 既存。月別 URL カードに「伝助ページ作成」ボタン追加
└── DensukePageCreateModal.jsx            # 新規。作成ダイアログ本体

pages/settings/
├── OrganizationSettings.jsx              # 既存。タブに「伝助テンプレート」を追加
└── DensukeTemplateTab.jsx                # 新規。テンプレート編集タブの中身

api/
├── practices.js                          # createDensukePage() 追加
└── densukeTemplates.js                   # 新規
```

#### 状態管理
- モーダル内の編集値は `useState` のローカル状態
- 作成中はローディングフラグでボタン無効化
- 完了時にトースト表示→親画面の月別 URL 一覧を再取得

### 4.5 densuke.biz 側の仕様（確定済み、2026-04-17）

詳細は [densuke-form-spec.md](./densuke-form-spec.md) 参照。要点:

#### エンドポイント
- **URL:** `POST https://www.densuke.biz/create`
- **Content-Type:** `application/x-www-form-urlencoded` (UTF-8)
- **認証:** 不要（匿名作成可能）
- **/confirm の事前 POST は不要** — `/create` に直接 POST でOK（ワンショット）

#### 必須ヘッダー
- `User-Agent`: 通常のブラウザ UA
- `Referer`: `https://www.densuke.biz/confirm`
- `Origin`: `https://www.densuke.biz`

#### フォームフィールド（固定値と可変値）
| フィールド | 送信値 |
|---|---|
| `eventname` | テンプレートから組み立てたタイトル（プレースホルダー置換後）|
| `schedule` | 練習日×試合枠を改行区切りで連結した文字列（詳細下記）|
| `explain` | テンプレートの説明文 |
| `email` | テンプレートの `contact_email`（空文字可）|
| `pw` | **`0` で固定**（パスワードなし）|
| `password` | 空文字 |
| `eventchoice` | **`1` で固定**（`○△×`、既存 DensukeScraper の判定ロジックと整合）|
| `postfix` | 空文字 |

#### `schedule` 文字列の組み立てフォーマット ⚠️

既存 [`DensukeScraper`](../../karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeScraper.java) の正規表現と整合させるため、以下の形式を厳守:

- 各セッション（日付 × 会場）の **1試合目行**: `{M}/{D}({曜日}) {会場名} 1試合目`
- 同じセッションの **2試合目以降**: `{N}試合目` のみ
- **時刻は含めない**（混入すると `VENUE_PATTERN` が時刻込み文字列を会場名として拾うため）

`DensukeScraper` は `currentDate` / `currentVenue` を前行から引き継ぐので、2試合目以降で日付・会場を省略しても同期は成立する。

各練習日について `total_matches`（未設定時は `venues.default_match_count`）の回数ループ。`venue_match_schedules` は「`total_matches` 分のレコードがあるか」という整合性チェックのためにロードするのみで、`start_time` は densuke 送信文字列には含めない。

**例（1日3試合 × 2日）:**
```
4/20(月) すずらん 1試合目
2試合目
3試合目
4/22(水) はまなす 1試合目
2試合目
```

#### レスポンス
- **HTTP 302 Found**
- **Location ヘッダー:** `complete?cd=<16文字>&sd=<13文字>`
- `cd` を `Location` から正規表現で抽出 → `https://densuke.biz/list?cd={cd}` として `densuke_urls` に保存
- `sd` は将来の編集・削除 API で必要になる可能性があるため、**`densuke_urls` テーブルに `sd` カラムを追加して保存する**

#### DB スキーマ追加（4.2 の補強）
`densuke_urls` テーブルに編集用シークレット `sd` カラムを追加:
```sql
ALTER TABLE densuke_urls ADD COLUMN densuke_sd VARCHAR(32);
```
- 手動登録 URL は NULL のまま（既存データと互換）
- 自動作成時のみ値が入る

#### Java 実装方針
- `java.net.http.HttpClient` または Spring `RestTemplate` + `URLEncoder.encode(value, StandardCharsets.UTF_8)` で UTF-8 エンコードを行う
- `HttpClient.Builder.followRedirects(NEVER)` で 302 を手動処理し、Location から cd を取得
- タイムアウト: 10〜30 秒程度（既存 `DensukeScraper` の 10 秒と揃えるか長めに）

#### contact_email の扱い
- 伝助側の `email` フィールドに送信。主催者の控えメール送付先として使われる（伝助ページ上には表示されない）
- 空でも正常に作成可能なので、`densuke_templates.contact_email` が NULL/空の団体はそのまま空で送信

## 5. 影響範囲

### 5.1 新規作成するファイル

#### バックエンド
| パス | 役割 |
|---|---|
| `database/create_densuke_templates.sql` | テンプレートテーブルのマイグレーション |
| `database/add_densuke_page_created_line_preference.sql` | LINE 通知設定列の追加 |
| `entity/DensukeTemplate.java` | テンプレートエンティティ |
| `repository/DensukeTemplateRepository.java` | テンプレート Repository |
| `service/DensukePageCreateService.java` | ページ作成サービス |
| `service/DensukeTemplateService.java` | テンプレート CRUD サービス |
| `controller/DensukeTemplateController.java` | テンプレート CRUD Controller |
| `dto/DensukePageCreateRequest.java` | ページ作成リクエスト DTO |
| `dto/DensukePageCreateResponse.java` | ページ作成レスポンス DTO |
| `dto/DensukeTemplateDto.java` | テンプレート DTO |
| `dto/DensukeTemplateUpdateRequest.java` | テンプレート更新リクエスト DTO |

#### フロントエンド
| パス | 役割 |
|---|---|
| `pages/densuke/DensukePageCreateModal.jsx` | 作成モーダル本体 |
| `pages/settings/DensukeTemplateTab.jsx` | テンプレート編集タブ |
| `api/densukeTemplates.js` | テンプレート API クライアント |

### 5.2 既存ファイルの変更

#### バックエンド
| パス | 変更内容 |
|---|---|
| `controller/PracticeSessionController.java` | `POST /densuke/create-page` エンドポイント追加 |
| `service/LineNotificationService.java` | `sendDensukePageCreatedNotification()` メソッド追加 |
| `entity/LineNotificationPreference.java` | `densukePageCreated` フラグ列を追加 |
| `entity/LineNotificationType.java`（または該当 enum） | `DENSUKE_PAGE_CREATED` 追加 |
| `dto/LineNotificationPreferenceDto.java` 等 | 新フラグ項目の入出力対応 |
| `service/DensukeSyncService.java` | 変更なし（新規作成された `densuke_urls` は既存フローで自動読込） |

#### フロントエンド
| パス | 変更内容 |
|---|---|
| `pages/densuke/DensukeManagement.jsx` | 各団体カードに「伝助ページ作成」ボタン追加、モーダル起動処理 |
| `pages/settings/OrganizationSettings.jsx` | 「伝助テンプレート」タブを追加（`DensukeTemplateTab` を組み込む）|
| LINE 通知設定画面（既存） | `densukePageCreated` の ON/OFF トグルを追加 |
| `api/practices.js` | `createDensukePage()` 追加 |

### 5.3 既存機能への影響

| 既存機能 | 影響 |
|---|---|
| `DensukeSyncScheduler`（5分間隔読み書き） | **影響なし**。新規保存された `densuke_urls` は次回サイクルで通常通り読み込まれる |
| `DensukeScraper` による会場名マッチング | **影響なし**。ただし伝助に書き込む会場名の文字列は Venue マスタと完全一致させる必要がある（マッチングに失敗すると `unmatchedVenues` 通知が発火する）|
| `DensukeWriteService`（既存出欠書き込み） | **影響なし**。新機能は別クラスで実装 |
| 既存の伝助手動 URL 登録（`PUT /densuke-url`） | **共存**。手動登録 or 自動作成、どちらのケースでも同じ `densuke_urls` 行が作られるだけ |
| 既存 `DensukeUrl` のデータ | 既存データに変更なし。新規作成時のみ新レコード追加 |

### 5.4 API・DB の後方互換性

| 項目 | 互換性 |
|---|---|
| 既存 API | 変更なし（追加のみ）|
| `densuke_urls` テーブル | スキーマ変更なし |
| `densuke_templates` テーブル | 新規追加。団体ごとにレコードが無ければデフォルト値を Java 側で供給し動作（未整備団体でも `GET` は 404 ではなくデフォルト値返却にする設計を想定）|

### 5.5 テストへの影響

| レベル | 追加範囲 |
|---|---|
| Unit | `DensukePageCreateServiceTest`, `DensukeTemplateServiceTest`（新規）|
| Controller | `DensukeTemplateControllerTest`（新規）、`PracticeSessionControllerTest` に作成エンドポイントのテスト追加 |
| Integration | densuke.biz への実通信はモック化（既存の `DensukeWriteServiceTest` と同じ方針）|

### 5.6 ドキュメント更新対象（CLAUDE.md ルール）

| ドキュメント | 更新内容 |
|---|---|
| `docs/SPECIFICATION.md` | 伝助ページ自動作成機能の仕様追加 |
| `docs/SCREEN_LIST.md` | 作成モーダル、テンプレート編集タブを追記 |
| `docs/DESIGN.md` | 新規テーブル・クラス構成を追記 |

### 5.7 運用・環境への影響

| 項目 | 影響 |
|---|---|
| 環境変数 | 追加なし（認証情報は不要、メアドは団体ごとに DB 管理）|
| GitHub Actions | 変更なし |
| Render デプロイ | 変更なし（DB マイグレーション `create_densuke_templates.sql` の実行手順は通常通り）|
| Docker | 変更なし |

### 5.8 未確定事項が確定した時の追加影響

densuke.biz の実フォーム解析後に判明する可能性のある追加変更:
- フォームが想定より複雑で、テンプレートに列追加が必要になる可能性
- 認証が必要と判明した場合、`densuke_templates` にクレデンシャル列追加 ＋ 暗号化処理が必要
- `cd` の取得方法が HTML 本文パースの場合、`Jsoup` の追加処理が必要

## 6. 設計判断の根拠

### 6.1 作成ロジックを独立クラスにする理由
- 既存 `DensukeWriteService` は 874 行あり「既知ページへの出欠書き込み」特化
- 新機能「ページそのものの発行」は責務が全く異なる（テンプレート解決、プレースホルダー置換、初期日程の書き込みなど）
- 同一クラスに混在させると可読性・テスト容易性が下がるため、`DensukePageCreateService` として分離

### 6.2 テンプレートを団体ごとにする理由
- 団体によってタイトルの書式、説明文、連絡先メアドが異なる
- 既存設計でも `densuke_urls` が団体ごとに管理されており、自然な粒度
- システムワイド1テンプレートだと、将来団体追加時に挙動が壊れるリスク

### 6.3 作成後に即時同期を行わない理由
- 既存 `DensukeSyncScheduler` が 5 分以内に必ず読み込みを実行
- 即時同期を入れると「作成 API のレスポンス時間が長くなる」「同期失敗時のエラーハンドリングが二重で必要」というデメリット
- ユーザー体験として「URL が作られ、5 分待てば反映される」は受容可能

### 6.4 失敗時のロールバックを実装しない理由
- densuke.biz 側に「作成したページの削除 API」があるか不明。現状のコードにも痕跡なし
- 仮に削除手段があっても、伝助との通信が不安定な状況で削除 API も失敗する可能性が高く、結局手動対応になる
- 発生頻度が低いエラーに実装コストを割くより、管理者手動対応を選ぶ方が合理的

### 6.5 テンプレートに締切列を持たない理由
- 伝助側の締切仕様（月単位 or 日単位）がまだ不明
- 過剰設計を避け、伝助デフォルト挙動に任せる
- 将来的に必要と判明したらカラム追加で対応可能

### 6.6 試合枠数を `practice_sessions.total_matches` 優先、`venues.default_match_count` フォールバックにする理由
- 既存コード（[PracticeSessionService.java:329](karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeSessionService.java#L329) など）と挙動を揃える
- 同じロジックが複数箇所にあると不整合リスクが高まるため、既存慣習を踏襲

### 6.7 通知を LINE のみに限定する理由
- 既存の Densuke 関連通知（`DENSUKE_UNMATCHED_NAMES`）は管理者向けで Push のみ
- 本機能は全メンバー向けで、気付かれやすさが最重要
- LINE は能動的にチェックするチャネルのため伝助 URL の周知に適する
- Push のみだとアプリを開かないメンバーに気付かれにくい／アプリ内通知のみでは手動確認が必要
- 将来的に Push を追加したくなった場合でも、既存の `LineNotificationType` / `NotificationType` の仕組みで拡張可能

### 6.8 作成 API 成功／LINE 送信失敗を独立扱いする理由
- LINE 送信は外部 API コール（Messaging API）で失敗要因が多い
- 送信失敗を作成 API 失敗にすると、伝助ページは作成済みなのに作成 API が失敗を返す矛盾が生じる
- 通知は「連絡」であって作成処理本体の結果ではない

### 6.9 対象年月を「当月＋2 ヶ月先まで」に絞る理由
- 主ユースケース「翌月分の作成」に最適化
- 過去月の作成は運用上意味がない（既に練習日が過ぎている）
- 3 ヶ月以上先は練習日登録が無いので意味がない
