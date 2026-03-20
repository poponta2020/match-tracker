# キャッシュ導入前提整理: データ構造と画面別データ利用マップ

最終更新: 2026-03-20

## 1. この資料の目的

この資料は、このアプリにキャッシュ機能を導入する前提として、現状のデータ構造とデータ利用経路を整理したものです。以下を対象にしています。

- バックエンドの主要テーブル/エンティティ構造
- Service 層での DTO 変換・集計・表示用加工
- フロントエンドの画面/主要コンポーネントごとの取得データと加工内容
- キャッシュ候補、依存関係、無効化時の注意点

補足:

- DB スキーマの一次情報は `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/` 配下の JPA Entity。
- 画面・ルーティングの一次情報は `karuta-tracker-ui/src/App.jsx`。
- 画面ごとの API 利用と加工内容は `karuta-tracker-ui/src/pages/` 配下の実装から整理。

## 2. システム全体のデータの流れ

基本の流れは以下です。

1. React 画面が `src/api/*.js` 経由で REST API を呼ぶ
2. Controller が Service を呼ぶ
3. Service が Repository 経由で Entity を取得/更新する
4. Service が DTO に変換し、表示用の名前/集計/補足情報を付与する
5. フロントエンドがさらに画面表示用のソート/絞り込み/集約を行う

キャッシュ設計では、次の 3 層を分けて考える必要があります。

- `DB/Entity キャッシュ`: マスタや参照系一覧
- `Service 結果キャッシュ`: DTO に加工済みのレスポンス
- `画面内キャッシュ`: React の `useRef` / state による一時保持

## 3. バックエンド: DB 構造の整理

### 3.1 主要テーブル一覧

| テーブル | エンティティ | 用途 |
|---|---|---|
| `players` | `Player` | 選手マスタ、認証、権限、論理削除 |
| `matches` | `Match` | 試合結果 |
| `practice_sessions` | `PracticeSession` | 練習日、会場、試合数、時間、定員 |
| `practice_participants` | `PracticeParticipant` | 練習参加情報、試合番号ごとの参加 |
| `venues` | `Venue` | 会場マスタ |
| `venue_match_schedules` | `VenueMatchSchedule` | 会場ごとの試合時間割 |
| `match_pairings` | `MatchPairing` | 練習日の組み合わせ |
| `player_profiles` | `PlayerProfile` | 選手プロフィール履歴 |
| `google_calendar_events` | `GoogleCalendarEvent` | 練習日と Google Calendar イベントの対応 |
| `densuke_urls` | `DensukeUrl` | 月別の伝助 URL |

### 3.2 テーブル別の主要カラム

#### `players`

用途:

- ログイン対象の選手情報
- 画面表示名、級位/段位、ロール判定の基礎データ

主要カラム:

| カラム | 内容 |
|---|---|
| `id` | 選手ID |
| `name` | 選手名。ログインIDとしても使用 |
| `password` | パスワードハッシュ |
| `gender` | 性別 |
| `dominant_hand` | 利き手 |
| `dan_rank` | 段位 |
| `kyu_rank` | 級位 |
| `karuta_club` | 所属かるた会 |
| `remarks` | 備考 |
| `role` | `SUPER_ADMIN` / `ADMIN` / `PLAYER` |
| `last_login_at` | 最終ログイン日時 |
| `deleted_at` | 論理削除日時 |
| `created_at` / `updated_at` | 監査項目 |

キャッシュ観点:

- 選手一覧、選手詳細、名前引き当ての基礎データ
- 名前変更、級変更、論理削除、ロール変更が多くの画面に波及

#### `matches`

用途:

- 試合結果の保存
- 試合履歴、日付別結果、級別統計、自動組み合わせ時の履歴参照

主要カラム:

| カラム | 内容 |
|---|---|
| `id` | 試合ID |
| `match_date` | 対戦日 |
| `match_number` | その日の第何試合目か |
| `player1_id` / `player2_id` | 対戦選手ID |
| `winner_id` | 勝者ID。未登録相手/引き分けでは `0L` を使う経路あり |
| `score_difference` | 枚数差 |
| `player1_kyu_rank` / `player2_kyu_rank` | 対戦時点の級位スナップショット |
| `opponent_name` | 未登録選手との対戦時の相手名 |
| `notes` | コメント |
| `created_by` / `updated_by` | 作成者/更新者 |
| `created_at` / `updated_at` | 監査項目 |

重要な実装上の性質:

- `player1_id < player2_id` を Entity 側で保証
- 簡易登録では未登録相手に対して `player2_id = 0L` などのダミー値を使う
- 級別統計では、基本的に `player*_kyu_rank` を優先して参照する

キャッシュ観点:

- 日付、選手、試合番号、対戦ペアの複数キーで参照される
- 更新が試合一覧、統計、練習日完了数、組み合わせ履歴表示へ波及

#### `practice_sessions`

用途:

- 練習日管理の中心

主要カラム:

| カラム | 内容 |
|---|---|
| `id` | 練習日ID |
| `session_date` | 練習日。UNIQUE |
| `total_matches` | 予定試合数 |
| `venue_id` | 会場ID |
| `notes` | メモ |
| `start_time` / `end_time` | 開始/終了時刻 |
| `capacity` | 定員 |
| `created_by` / `updated_by` | 作成者/更新者 |
| `created_at` / `updated_at` | 監査項目 |

キャッシュ観点:

- 日付キーでの参照が多い
- `session_date` 変更は ID ベースと日付ベースの両キャッシュに影響

#### `practice_participants`

用途:

- 練習日の参加者管理
- 試合番号単位の参加有無管理

主要カラム:

| カラム | 内容 |
|---|---|
| `id` | 参加レコードID |
| `session_id` | 練習日ID |
| `player_id` | 選手ID |
| `match_number` | 参加試合番号。`null` は後方互換で全試合参加扱い |
| `created_at` / `updated_at` | 監査項目 |

制約:

- `session_id + player_id + match_number` は一意

キャッシュ観点:

- 参加マップ、次回参加、試合別参加者一覧、会場画面の人数表示に影響

#### `venues`

用途:

- 会場マスタ

主要カラム:

| カラム | 内容 |
|---|---|
| `id` | 会場ID |
| `name` | 会場名 |
| `default_match_count` | 既定試合数 |
| `created_at` / `updated_at` | 監査項目 |

#### `venue_match_schedules`

用途:

- 会場ごとの試合時間割

主要カラム:

| カラム | 内容 |
|---|---|
| `id` | 時間割ID |
| `venue_id` | 会場ID |
| `match_number` | 試合番号 |
| `start_time` / `end_time` | 開始/終了時刻 |

制約:

- `venue_id + match_number` は一意

#### `match_pairings`

用途:

- 練習日ごとの対戦組み合わせ

主要カラム:

| カラム | 内容 |
|---|---|
| `id` | 組み合わせID |
| `session_date` | 練習日 |
| `match_number` | 試合番号 |
| `player1_id` / `player2_id` | 組み合わせ選手 |
| `created_by` | 作成者 |
| `created_at` / `updated_at` | 監査項目 |

キャッシュ観点:

- `session_date`、`session_date + match_number` の 2 系統で頻繁に参照される
- 結果入力画面、結果閲覧画面、組み合わせ作成画面に同時に影響

#### `player_profiles`

用途:

- 選手プロフィール履歴

主要カラム:

| カラム | 内容 |
|---|---|
| `id` | プロフィール履歴ID |
| `player_id` | 選手ID |
| `karuta_club` | 所属 |
| `grade` | 級 |
| `dan` | 段 |
| `valid_from` / `valid_to` | 有効期間 |
| `created_at` / `updated_at` | 監査項目 |

#### `google_calendar_events`

用途:

- Google Calendar 同期の冪等制御

主要カラム:

| カラム | 内容 |
|---|---|
| `id` | レコードID |
| `player_id` | 選手ID |
| `session_id` | 練習日ID |
| `google_event_id` | Google 側イベントID |
| `synced_session_updated_at` | 同期時点の練習日更新日時 |
| `created_at` / `updated_at` | 監査項目 |

#### `densuke_urls`

用途:

- 月別の伝助 URL 管理

主要カラム:

| カラム | 内容 |
|---|---|
| `id` | レコードID |
| `year` / `month` | 対象年月 |
| `url` | 伝助 URL |
| `created_at` / `updated_at` | 監査項目 |

## 4. バックエンド: Service 層での加工ポイント

### 4.1 `MatchService`

主な責務:

- 試合 CRUD
- 選手視点の試合履歴生成
- 日付別試合一覧
- 級別統計/勝率集計

主な加工:

- `MatchDto.fromEntity()` は基本項目のみ詰める
- `enrichMatchesWithPlayerNames()` で `player1Name` / `player2Name` / `winnerName` を付与
- `enrichMatchesWithPlayerPerspective()` で閲覧者視点の `opponentName` / `result` を付与
- `setPlayerKyuRanks()` で試合保存時に対戦時級位をスナップショット保存
- `getPlayerStatisticsByRank()` では期間・性別・利き手で絞り込みした後、級別集計を計算

キャッシュ候補:

- `選手別試合一覧`
- `日付別試合一覧`
- `選手別統計`
- `選手別級別統計`
- `選手ID + 日付 + 試合番号` の単一試合取得

無効化トリガ:

- `matches` の作成/更新/削除
- `players.name` / `players.kyu_rank` の変更

### 4.2 `PracticeSessionService`

主な責務:

- 練習日 CRUD
- 練習日詳細/一覧/サマリー取得
- 参加者登録
- 次回参加、参加率 TOP3、参加マップ取得

主な加工:

- `findSessionSummariesByYearMonth()` は軽量レスポンスで、会場名だけ付与
- `enrichSessionWithParticipants()` は以下を付与
  - 会場名
  - 会場時間割
  - 参加者 `participants`
  - `participantCount`
  - `completedMatches`
  - 試合番号ごとの `matchParticipantCounts`
  - 試合番号ごとの `matchParticipants`
- `enrichSessionsWithParticipants()` は一覧取得時の N+1 を避けるために一括取得・一括集計している
- `findNextParticipation()` は、次回参加予定とそのセッションの参加者一覧まで返す

キャッシュ候補:

- `年月別セッションサマリー`
- `日付別セッション詳細`
- `参加マップ`
- `次回参加`
- `練習日一覧`

無効化トリガ:

- `practice_sessions` の作成/更新/削除
- `practice_participants` の追加/削除/更新
- `matches` の作成/削除による `completedMatches` 変化
- `venues` / `venue_match_schedules` の変更
- `players.name` / `players.kyu_rank` / `players.role` の変更

### 4.3 `MatchPairingService`

主な責務:

- 組み合わせ CRUD
- 自動マッチング
- 直近対戦履歴付与

主な加工:

- `getByDate(sessionDate, light)` で `light=false` の場合は `recentMatches` を付与
- `getByDateAndMatchNumber()` は常に `recentMatches` を付与
- `autoMatch()` は過去 30 日の組み合わせ/試合履歴と当日既存対戦を見てスコアリング
- `getPairRecentMatches()` は過去 90 日の組み合わせ/試合履歴を統合して返す
- `updatePlayer()` は旧ペアに対応する試合結果を削除する

キャッシュ候補:

- `日付別組み合わせ一覧`
- `日付 + 試合番号別組み合わせ`
- `ペア履歴`

無効化トリガ:

- `match_pairings` の作成/更新/削除
- `matches` の追加/削除
- `players.name` の変更

### 4.4 `VenueService`

主な責務:

- 会場 CRUD
- 会場ごとの時間割付与

主な加工:

- `getAllVenues()` / `getVenueById()` で `VenueDto` に `schedules` を付与

キャッシュ候補:

- 会場一覧
- 会場詳細

無効化トリガ:

- `venues` の作成/更新/削除
- `venue_match_schedules` の変更

## 5. フロントエンド: API 層の整理

主要 API モジュール:

| API オブジェクト | 主な用途 |
|---|---|
| `playerAPI` | 選手一覧、詳細、更新、ログイン |
| `matchAPI` | 試合一覧、詳細、登録、更新、統計 |
| `practiceAPI` | 練習日、参加登録、次回参加、参加率、伝助 |
| `pairingAPI` | 組み合わせ取得、保存、自動生成、履歴 |
| `venueAPI` | 会場 CRUD |
| `calendarAPI` | Google Calendar 同期 |

画面で多用される問い合わせキー:

- `playerAPI.getAll()`
- `practiceAPI.getByDate(date)`
- `practiceAPI.getSessionSummaries(year, month)`
- `practiceAPI.getPlayerParticipations(playerId, year, month)`
- `pairingAPI.getByDate(date)`
- `matchAPI.getByPlayerId(playerId, params)`
- `matchAPI.getStatisticsByRank(playerId, params)`
- `GET /matches?date=YYYY-MM-DD`

## 6. フロントエンド: 画面別データ利用マップ

### 6.1 全体ルート

主要ルートは `karuta-tracker-ui/src/App.jsx` に定義されている。

| ルート | 画面 |
|---|---|
| `/` | `Home` / `Landing` |
| `/login`, `/register` | 認証画面 |
| `/matches` | `MatchList` |
| `/matches/new`, `/matches/:id/edit` | `MatchForm` |
| `/matches/:id` | `MatchDetail` |
| `/matches/bulk-input/:sessionId` | `BulkResultInput` |
| `/matches/results/:sessionId?` | `MatchResultsView` |
| `/practice` | `PracticeList` |
| `/practice/new`, `/practice/:id/edit` | `PracticeForm` |
| `/practice/:id` | `PracticeDetail` |
| `/practice/participation` | `PracticeParticipation` |
| `/pairings` | `PairingGenerator` |
| `/pairings/summary` | `PairingSummary` |
| `/players` ほか | 選手管理系 |
| `/profile`, `/profile/edit` | 自分のプロフィール |
| `/venues` ほか | 会場管理 |

### 6.2 画面別詳細

#### `Home`

主な取得:

- `practiceAPI.getNextParticipation(currentPlayer.id)`
- `practiceAPI.getPlayerParticipations(currentPlayer.id, year, month)`
- `matchAPI.getMatchCount(currentPlayer.id, startOfMonth, endOfMonth)`
- `practiceAPI.getParticipationRateTop3(year, month)`
- 任意操作で `calendarAPI.sync(accessToken, playerId)`

画面側の加工:

- 月間参加回数は `participationMap` のキー数を使って算出
- 月間試合数は軽量 API の数値をそのまま利用
- 参加率 TOP3 は `rate * 100` を `%` 表示
- 次回練習の参加者は `sortPlayersByRank()` でソート
- window focus 復帰時に再取得

キャッシュ価値:

- 高い。ホームは少数 API を毎回まとめて呼んでいる

#### `MatchList`

主な取得:

- 遅延で `playerAPI.getAll()`
- 他人表示時のみ `playerAPI.getById(targetPlayerId)`
- `matchAPI.getByPlayerId(targetPlayerId, matchParams)`
- `matchAPI.getStatisticsByRank(targetPlayerId, statsParams)`

画面側の加工:

- 試合一覧を日付降順でソート
- 年一覧/年内の月一覧を試合日から算出
- 年月フィルタ、結果フィルタ、相手名検索をクライアント側で適用
- 他選手検索は `allPlayers` から部分一致検索

キャッシュ価値:

- 高い。対象選手とフィルタ条件の組み合わせで再利用できる

注意:

- `getByPlayerId` と `getStatisticsByRank` は同時に使われるため、同一タグでの無効化が望ましい

#### `MatchForm`

主な取得:

- 初期表示時に `playerAPI.getAll()`
- `practiceAPI.getByDate(today)`
- 編集時は `matchAPI.getById(id)`
- 新規時は `practiceAPI.getPlayerParticipations(currentPlayer.id, year, month)`
- `pairingAPI.getByDate(formData.matchDate)`
- 各試合番号について `matchAPI.getByPlayerDateAndMatchNumber(currentPlayer.id, matchDate, num)`

画面側の加工:

- 自分を除外した選手一覧を `players` に保持
- 試合番号ごとの既存試合/組み合わせを以下の `useRef` にキャッシュ
  - `matchDataCache`
  - `pairingCache`
  - `allPairingsCache`
- 試合番号切替時は API を呼ばず、キャッシュからフォームへ反映
- ペアリングがある場合は相手選手を自動設定
- ペアリングがない場合はその試合番号の参加候補から選択
- 参加未登録時は参加登録ダイアログを表示し、月間参加マップを読み込んで保存

キャッシュ価値:

- 高い。既に画面ローカルでキャッシュ実装済み

注意:

- `practiceAPI.registerParticipations()` 実行後は参加系キャッシュの無効化が必要

#### `MatchResultsView`

主な取得:

- 初回に `practiceAPI.getDates(fromDate)`
- 日付ごとに並列で
  - `practiceAPI.getByDate(date)`
  - `GET /match-pairings/date?light=true`
  - `GET /matches?date=...`

画面側の加工:

- `fetchedMonths` で月単位の練習日取得を重複抑制
- `availableDates` をマージして降順ソート
- 組み合わせと試合結果を `matchNumber + pair` で突き合わせて完了判定
- 日付移動 UI は `availableDates` の添字で前後移動

キャッシュ価値:

- 非常に高い。`日付別セッション詳細`、`日付別組み合わせ(light)`、`日付別試合一覧` を同時に読む画面

#### `BulkResultInput`

主な取得:

- `GET /practice-sessions/:sessionId`
- `pairingAPI.getByDate(sessionDate)`
- `GET /matches?date=sessionDate`
- `practiceAPI.getParticipants(sessionId)`

画面側の加工:

- ペアリングと試合結果を `matchNumber-player1Id-player2Id` の正規化キーで辞書化
- 試合完了状態を一括判定
- 保存時は `matchAPI.createDetailed()` / `matchAPI.updateDetailed()` を組み合わせて一括送信

キャッシュ価値:

- 高い。`MatchResultsView` と同じ日付系データを共有できる

#### `PracticeList`

主な取得:

- `practiceAPI.getSessionSummaries(year, month)`
- `practiceAPI.getPlayerParticipations(currentPlayer.id, year, month)`
- セルクリック時に `practiceAPI.getById(session.id)`

画面側の加工:

- カレンダーグリッドをクライアント生成
- 練習日サマリーを日付セルへマッピング
- 自分の参加状況を `full / partial / none` に変換
- 詳細モーダルでは `matchParticipants` を展開し、必要に応じて全試合を初期展開
- 更新ボタンで一覧と参加情報を再取得

キャッシュ価値:

- 非常に高い。月次の一覧系データとして使い回しやすい

#### `PracticeParticipation`

主な取得:

- `practiceAPI.getByYearMonth(year, month)`
- `practiceAPI.getPlayerParticipations(currentPlayer.id, year, month)`

画面側の加工:

- セッションを日付昇順でソート
- `participations` を画面編集用 state に保持
- 過去日を除外して未来セッションのみ表示
- 会場名を `abbreviateVenue()` で短縮
- 参加人数バッジ色を `count / capacity` で決定
- 保存時に `{ sessionId, matchNumber }[]` に正規化して送信

キャッシュ価値:

- 高い。PracticeList と同じ月次参加系データを共有できる

#### `PairingGenerator`

主な取得:

- `practiceAPI.getByDate(sessionDate)`
- `pairingAPI.getByDate(sessionDate)`
- 遅延で `playerAPI.getAll()`
- 自動組み合わせで `pairingAPI.autoMatch()`
- 組み合わせ保存で `pairingAPI.createBatch()`
- ペア履歴で `pairingAPI.getPairHistory()`
- 参加者追加で `practiceAPI.addParticipantToMatch()`

画面側の加工:

- 初回に日付単位のセッションと全組み合わせを一括取得
- `pairingsCache` に試合番号ごとの組み合わせを保持
- `unsavedDraft` に未保存編集中データを保持
- `matchExistsMap` に試合番号ごとの組み合わせ存在有無を保持
- `currentSession.matchParticipants` から試合番号ごとの参加者をフィルタ
- 待機者リストと組み合わせリストを相互変換
- 選手入れ替え後に直近対戦履歴を再取得

キャッシュ価値:

- 非常に高い。日付単位での組み合わせ系データの中心画面

注意:

- `practiceAPI.addParticipantToMatch()` 後はセッション詳細と組み合わせ画面のキャッシュ再同期が必要

#### `PairingSummary`

主な取得:

- `practiceAPI.getByDate(date)`
- 全試合番号について `pairingAPI.getByDateAndMatchNumber(date, i + 1)`

画面側の加工:

- API で取得した組み合わせから `matchData` を構築
- `generateCardRules()` で札ルールをランダム生成
- `generateText()` で LINE 送信用テキストを生成

キャッシュ価値:

- 組み合わせデータ自体は高い
- 札ルールや生成テキストはクライアント生成でランダム要素があるため、サーバキャッシュ対象には向かない

#### `PlayerList`

主な取得:

- `playerAPI.getAll()`

画面側の加工:

- `sortPlayersByRank()` で並び替え
- 名前検索
- ロール表示、最終ログイン相対表示

キャッシュ価値:

- 非常に高い。多画面で共通利用される選手マスタの代表画面

#### `Profile` / `ProfileEdit` / `PlayerDetail` / `PlayerEdit`

主な取得:

- `playerAPI.getById(id)`
- `playerAPI.update(id, data)`
- `ProfileEdit` では本人確認として `playerAPI.login()` を再利用

画面側の加工:

- 画面表示向けの整形が中心で、重い集計はほぼない

キャッシュ価値:

- 中程度。単票キャッシュとしては有効

#### `PracticeForm`

主な取得:

- `venueAPI.getAll()`
- `practiceAPI.getById(id)` または `practiceAPI.create/update()`
- 作成フローで `practiceAPI.getSessionSummaries(year, month)`

画面側の加工:

- 会場一覧から選択肢を構築
- 編集時は既存練習日データをフォームへ展開

キャッシュ価値:

- 会場一覧、月次セッションサマリーに依存

#### `VenueList` / `VenueForm`

主な取得:

- `GET /venues`
- `GET /venues/:id`
- `POST /venues`
- `PUT /venues/:id`
- `DELETE /venues/:id`

画面側の加工:

- 会場と時間割をそのままフォームや一覧に展開

キャッシュ価値:

- 高い。会場一覧は変化頻度が低い

## 7. 既に存在する画面内キャッシュ/重複抑制

現状、グローバルなデータ取得ライブラリは導入されておらず、各画面が個別にローカルキャッシュを持っています。

主な例:

| 画面 | 実装 |
|---|---|
| `MatchForm` | `matchDataCache`, `pairingCache`, `allPairingsCache` |
| `MatchResultsView` | `fetchedMonths` |
| `PairingGenerator` | `pairingsCache`, `unsavedDraft`, `matchExistsMap` |
| `MatchList` | `playersLoadedRef`, `fetchingMatchesRef` |
| `PracticeList` | `fetchingRef` |

示唆:

- 既に「同じキーのデータを短時間で再利用したい」という要件は各画面に存在している
- キャッシュ導入時は、画面内 `useRef` の責務をグローバルキャッシュへ寄せられる可能性が高い

## 8. キャッシュ候補と無効化マップ

### 8.1 優先度が高いキャッシュ候補

| キャッシュ候補 | 主な利用画面 | 想定キー |
|---|---|---|
| 選手一覧 | `MatchForm`, `MatchList`, `PairingGenerator`, `PlayerList` | `players/all` |
| 会場一覧 | `PracticeForm`, `VenueList`, `VenueForm` | `venues/all` |
| 日付別練習日詳細 | `MatchForm`, `MatchResultsView`, `PairingGenerator`, `PairingSummary` | `practice/date/{date}` |
| 年月別練習日サマリー | `PracticeList`, `PracticeForm` | `practice/summary/{year}-{month}` |
| 月次参加マップ | `Home`, `PracticeList`, `PracticeParticipation`, `MatchForm` | `participations/{playerId}/{year}-{month}` |
| 日付別組み合わせ | `PairingGenerator`, `BulkResultInput`, `MatchResultsView` | `pairings/date/{date}` |
| 日付+試合番号別組み合わせ | `PairingSummary`, `PairingGenerator` | `pairings/date/{date}/match/{matchNumber}` |
| 日付別試合一覧 | `MatchResultsView`, `BulkResultInput` | `matches/date/{date}` |
| 選手別試合一覧 | `MatchList` | `matches/player/{playerId}/{filters}` |
| 選手別級別統計 | `MatchList` | `stats/rank/{playerId}/{filters}` |
| 次回参加 | `Home` | `next-participation/{playerId}` |

### 8.2 無効化の考え方

#### `players` 更新時

影響:

- 選手一覧
- 選手詳細
- 試合 DTO の表示名
- 練習日参加者名
- 組み合わせ DTO の選手名
- ホームの次回参加参加者一覧

特に注意:

- `kyu_rank` 変更は統計ロジックの一部に影響するが、試合保存済みの `player*_kyu_rank` は変わらない

#### `matches` 更新時

影響:

- 日付別試合一覧
- 選手別試合一覧
- 級別統計
- 練習日 `completedMatches`
- 結果閲覧画面の完了判定
- 組み合わせ履歴/対戦履歴表示

#### `practice_sessions` 更新時

影響:

- 日付別練習日詳細
- 年月別セッションサマリー
- 次回参加
- 組み合わせ画面の参加者母集団

特に注意:

- `session_date` 変更は日付キーキャッシュの再キー化が必要

#### `practice_participants` 更新時

影響:

- 月次参加マップ
- 練習日詳細の `participants`
- 試合別参加者リスト
- 次回参加
- 組み合わせ画面の参加者リスト

#### `match_pairings` 更新時

影響:

- 日付別組み合わせ
- 日付+試合番号別組み合わせ
- 結果入力画面
- 結果閲覧画面
- ペア履歴表示

#### `venues` / `venue_match_schedules` 更新時

影響:

- 会場一覧/詳細
- 練習日詳細の `venueName` と `venueSchedules`

## 9. キャッシュ設計時に気をつけるべき点

1. 同じ日付データでも「練習日詳細」「組み合わせ」「試合結果」が別 API で管理されている
2. `PracticeSessionDto` は詳細系とサマリー系で返却内容が違うため、同じ `practice session` でもキャッシュ粒度を分ける必要がある
3. `MatchDto` は閲覧者視点によって `opponentName` と `result` が変わるので、閲覧者依存キャッシュになる
4. 組み合わせ系は `light=true` と通常取得で payload が異なる
5. 既に画面内で個別最適のキャッシュが存在するため、導入時は二重キャッシュを避ける設計が必要
6. 参加登録や組み合わせ変更は、単一 API 更新でも複数画面の読取結果に影響する

## 10. キャッシュ導入の最初の単位として適しているもの

初手として比較的安全なのは次の 4 系統です。

1. `playerAPI.getAll()` の共通キャッシュ
2. `venueAPI.getAll()` の共通キャッシュ
3. `practiceAPI.getSessionSummaries(year, month)` の月次キャッシュ
4. `practiceAPI.getByDate(date)` / `pairingAPI.getByDate(date)` / `GET /matches?date=` の日付単位キャッシュ

理由:

- 利用箇所が多い
- 取得条件が明確
- 無効化キーを設計しやすい
- 既存画面内キャッシュを置き換えやすい

## 11. 参照元ファイル

主な参照元:

- `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/`
- `karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchService.java`
- `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeSessionService.java`
- `karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchPairingService.java`
- `karuta-tracker/src/main/java/com/karuta/matchtracker/service/VenueService.java`
- `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/MatchDto.java`
- `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/PracticeSessionDto.java`
- `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/MatchPairingDto.java`
- `karuta-tracker-ui/src/App.jsx`
- `karuta-tracker-ui/src/api/`
- `karuta-tracker-ui/src/pages/Home.jsx`
- `karuta-tracker-ui/src/pages/matches/MatchList.jsx`
- `karuta-tracker-ui/src/pages/matches/MatchForm.jsx`
- `karuta-tracker-ui/src/pages/matches/BulkResultInput.jsx`
- `karuta-tracker-ui/src/pages/matches/MatchResultsView.jsx`
- `karuta-tracker-ui/src/pages/practice/PracticeList.jsx`
- `karuta-tracker-ui/src/pages/practice/PracticeParticipation.jsx`
- `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx`
- `karuta-tracker-ui/src/pages/pairings/PairingSummary.jsx`
