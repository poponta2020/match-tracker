# 試合動画

> **責務:** 試合動画の登録・閲覧の仕様
> **関連画面:** `/videos`（`VideoLibrary.jsx`。動画倉庫＝一覧・検索・登録導線）／動画再生・登録モーダルは `/matches`・`/matches/results/:sessionId?`・`/matches/:id` にも組み込み（`VideoPlayerModal.jsx`・`VideoRegisterModal.jsx`）
> **主要実装:**
> - バックエンド: `controller/MatchVideoController.java`, `service/MatchVideoService.java`, `repository/MatchVideoRepository.java`, `entity/MatchVideo.java`, `dto/MatchVideoDto.java`, `dto/MatchVideoDateCandidateDto.java`, `dto/MatchVideoCreateRequest.java`, `dto/MatchVideoUpdateRequest.java`, `dto/PagedResponse.java`, `util/OrganizationScopeResolver.java`（すべて `karuta-tracker/src/main/java/com/karuta/matchtracker/` 配下）。既存の試合APIへの動画付与は `dto/MatchDto.java`（`MatchDto.Video` ネストDTO）・`service/MatchService.java`・`repository/MatchRepository.java`（`findByMatchDateIn`）が担当
> - フロントエンド: `karuta-tracker-ui/src/pages/videos/VideoLibrary.jsx`, `karuta-tracker-ui/src/components/VideoPlayerModal.jsx`, `karuta-tracker-ui/src/components/VideoRegisterModal.jsx`

## フロー

### 試合動画フロー

練習試合の動画（YouTube限定公開）のURLを、試合の自然キー（試合日・試合番号・両選手）と紐付けて管理する「動画台帳」。`matches` / `match_pairings` とはFKを持たず、(match_date, match_number, player1_id, player2_id) の自然キーで対応付くため、**結果未入力（組み合わせのみ）段階でも登録でき、結果入力後は同一キーで自動的に試合詳細にも表示される**（付け替え処理不要）。

```
[動画登録フロー]
1. 撮影者が YouTube アプリから限定公開でアップロード → URLをコピー
   ↓
2. アプリ（試合詳細 or 動画倉庫）で対象試合を選び POST /api/match-videos
   ↓
3. MatchVideoService.register:
   a. YouTube URL検証・動画ID抽出（不正 → 400「YouTubeのURLを入力してください」）
   b. キー正規化（player1Id < player2Id）
   c. 対象試合の存在チェック（matches または match_pairings に同自然キー。
      match_pairings は p1<p2 を保証しないため選手順序不問で照合）
      → どちらにも無ければ 404「対象の試合が見つかりません」
   d. 重複チェック（既に動画あり → 409「この試合には既に動画が登録されています」）
   e. oEmbed API でタイトル取得（接続2秒・読取3秒。失敗時は title=null で続行＝fail-soft）
   f. INSERT（created_by / updated_by = 操作ユーザー）
   ※ LINE通知はタスク9（MATCH_VIDEO_REGISTERED）で追加

[編集・削除フロー]
- PUT /api/match-videos/{id}（URL差し替え）/ DELETE /api/match-videos/{id}（物理削除）
- 権限: 登録者本人（created_by）or ADMIN/SUPER_ADMIN のみ（サービス層で所有者チェック）
- 削除されるのは台帳の紐付けのみ。YouTube上の動画本体は残る

[一覧・検索フロー]
- GET /api/match-videos?date=  : 指定日の動画一覧（当日結果一覧の「動画あり」バッジ用）
- GET /api/match-videos/date-candidates?date=&organizationId= : 指定日の動画登録候補（登録モーダル「日付から」用）
  - 参加日スコープ（hasSessionOnDateForUser）なし: 非参加ユーザー（撮影担当・第三者登録）でも候補を選べる
    （サービスは currentUserId を受け取らないため構造的に担保）
  - 組織スコープあり: OrganizationScopeResolver で effectiveOrgId を解決し
    MatchPairingService.getByDate(date, true, organizationId)（light=true）に渡す（他団体の候補混入を防ぐ）。
    matches は組織カラムが無いため player_organizations の所属選手ID集合でフィルタ
    （実在選手が全員所属 かつ 実在所属メンバー1名以上。ゲスト id=0/null は所属判定から除外し
    相手名は Match.opponentName で補完）→ pairings と対称にスコープ。
    organizationId 未指定時は matches を日付のみ取得しスコープしない。
    既定解決: effectiveOrgId が null かつ単一所属 PLAYER のときのみ所属団体IDで補完（当エンドポイント限定）
  - pairings + matches を自然キー (matchDate, matchNumber, min(p1,p2), max(p1,p2)) で統合・重複排除（matches 優先）
  - 各候補に registered（同自然キーの動画あり）/ hasResult / matchId を付与。選手名はバッチ解決（N+1回避）
- GET /api/match-videos/search : 動画倉庫の検索（選手・年月絞り込み・mine トグル・ページング）
  - mine=true は操作ユーザー自身を対象選手として扱う（playerId より優先）
  - year/month はサービス層で startDate/endDate 範囲に変換してリポジトリへ渡す
  - 並びは matchDate DESC, matchNumber DESC
  - 一覧系は選手名解決・matches照合をバッチ取得（findAllById / findByMatchDateIn）で N+1 回避
  - レスポンスは PagedResponse<MatchVideoDto>（PageImpl 直接シリアライズの不安定さを回避）

[既存の試合APIへの動画付与（MatchDto.video）]
- MatchDto に `video: { id, videoUrl, youtubeVideoId, title } | null` を追加し、
  ①試合詳細（単体取得）と③個人別一覧が既存APIのまま動画有無を取得できるようにする
  （動画なしの試合は video=null。後方互換: 既存フィールドは不変、追加のみ）
  - 単体取得: MatchService.findById → 試合の自然キー (match_date, match_number, p1<p2) で
    MatchVideoRepository.findByMatchDateAndMatchNumberAndPlayers を1回呼び、ヒットすれば video をセット
  - 個人別一覧: MatchService.findPlayerMatchesWithFilters → 対象選手の動画を
    MatchVideoRepository.findByPlayerId で1クエリ取得し、(match_date, match_number, p1, p2) 正規化キーの
    マップを構築して各試合に照合・セット（N+1回避）
  - ②当日結果一覧は別API GET /api/match-videos?date= を使うため、video 付与は ①③ のみに限定する
  - MatchDto.Video.fromEntity(MatchVideo) で MatchVideo → ネストDTO 変換（fromEntity 規約に従う）
```

**関連クラス:**

| クラス | 説明 |
|--------|------|
| `MatchVideo` | entity/ — 動画台帳エンティティ。自然キー + UNIQUE制約、`provider`（'YOUTUBE'固定）、`@PrePersist`/`@PreUpdate` で p1<p2 入れ替え |
| `MatchVideoRepository` | repository/ — 自然キー検索・日付検索・選手検索（p1 OR p2）・倉庫検索（動的条件+ページング） |
| `MatchVideoController` | controller/ — 動画CRUD + 日付別一覧 + 日付別候補 + 倉庫検索（6エンドポイント）。`@RequireRole` 全ロール。`date-candidates` は `OrganizationScopeResolver` で組織スコープ解決 |
| `MatchVideoService` | service/ — 登録・URL差し替え・削除・日付別一覧・日付別候補・倉庫検索。YouTube URL検証/ID抽出、oEmbedタイトル取得（短タイムアウト・fail-soft）、所有者チェック。`getDateCandidates(date, organizationId)` は `MatchPairingService.getByDate`（参加日スコープなし・組織スコープあり）+ matches を自然キーで統合・重複排除（matches 優先）し registered/hasResult/matchId を付与 |
| `MatchVideoDto` | dto/ — `fromEntity(video, p1Name, p2Name, match)`。選手名と matches 照合結果（matchId/winnerId/scoreDifference）を含む |
| `MatchVideoDateCandidateDto` | dto/ — 日付別候補1件（matchDate/matchNumber/player1Id/player1Name/player2Id/player2Name/hasResult/matchId/registered）。複数ソース統合のため Service で組み立てる |
| `MatchVideoCreateRequest` / `MatchVideoUpdateRequest` | dto/ — 登録リクエスト（自然キー+URL）/ 更新リクエスト（URLのみ） |
| `PagedResponse<T>` | dto/ — ページング結果の汎用レスポンス（content/page/size/totalElements/totalPages） |
| `MatchDto.Video` | dto/ — `MatchDto` のネストDTO（id/videoUrl/youtubeVideoId/title）。`fromEntity(MatchVideo)`。動画なしは null |
| `MatchService` | service/ — `MatchDto` への動画付与（単体: 自然キー1回照合 / 個人別一覧: findByPlayerId 1クエリ＋マップ照合でN+1回避）。①試合詳細・③個人別一覧のみ対象 |

**DB変更:**

| 対象 | 変更内容 |
|------|---------|
| `match_videos` テーブル | タスク1で新規作成（自然キー + UNIQUE制約 `uq_match_videos_match`、provider/video_url/youtube_video_id/title） |
| `MatchRepository` | `findByMatchDateIn(dates)` 追加（動画一覧で matches をバッチ照合し N+1 回避） |

## API

### `/api/match-videos`

練習試合の動画（YouTube限定公開）のURLを、試合の自然キー（試合日・試合番号・両選手）と紐付けて管理する「動画台帳」のAPI。`matches` / `match_pairings` とはFKを持たず、(match_date, match_number, player1_id, player2_id) の自然キー（player1_id < player2_id にサービス層で正規化）で対応付く。詳細フローは前述の「試合動画フロー」を参照。

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| POST | `/` | ALL | 動画登録（登録は全選手可） |
| PUT | `/{id}` | ALL（登録者本人 or ADMIN+） | URL差し替え。所有者チェックはサービス層 |
| DELETE | `/{id}` | ALL（登録者本人 or ADMIN+） | 紐付け削除（物理削除）。YouTube上の本体は残る |
| GET | `/?date=` | ALL | 指定日の動画一覧（当日結果一覧の「動画あり」バッジ用） |
| GET | `/date-candidates?date=&organizationId=` | ALL | 指定日の動画登録候補（動画倉庫の登録モーダル「日付から」用）。**参加日スコープなし**・**組織スコープあり（pairings/matches とも対称）**。レスポンス: `List<MatchVideoDateCandidateDto>` |
| GET | `/search?playerId=&year=&month=&mine=&page=&size=` | ALL | 動画倉庫の検索・ページング。レスポンス: `PagedResponse<MatchVideoDto>` |

#### GET `/api/match-videos/date-candidates`
動画倉庫の登録モーダル「日付から」で使用する読み取り専用の候補API。指定日の対戦カードを返す。

- **参加日スコープ（`hasSessionOnDateForUser`）は適用しない**。その日の練習に参加していないユーザー（撮影担当者・第三者登録者・管理者代理登録）でも候補を選べる（動画登録は全選手可の仕様）。サービスは操作ユーザーIDを受け取らない設計のため、非参加でも候補が返ることが構造的に担保される
- **組織スコープは pairings / matches の両方で対称に維持**する。`OrganizationScopeResolver` で effectiveOrgId を解決（`search` と同じ流儀。ADMIN は自団体強制、PLAYER は所属団体のみ、SUPER_ADMIN は任意）する。
    - ペアリングは `MatchPairingService.getByDate(date, light=true, organizationId)` に渡して当該団体のセッション参加者のもののみへ絞り込む（`light=true` で未使用の対戦履歴 `recentMatches` 取得を回避。選手名は light でも保持される）
    - `matches` には組織カラムがないため、`organizationId` 指定時は**選手の所属（`player_organizations`）経由でスコープ**する。判定は**実在選手（id が 0 / null 以外）が全員当該団体に所属し、かつ実在所属メンバーが1名以上**の `matches` のみを候補化する（所属選手ID集合を1クエリで取得して照合し N+1 を回避）。**ゲスト/未登録相手（id 0・null）は所属判定から除外**するため、所属メンバー本人 vs 未登録相手の試合も候補に残る（フロントでは「相手未登録」として選択不可表示、相手名は `Match.opponentName` で補完）。これで pairings 側と対称になり、同日に複数団体の試合結果があっても**他団体の matches-only 候補が混入しない**
    - `organizationId` 未指定（組織非限定）の場合は `matches` を日付のみで取得しスコープしない（アプリ全体の組織未指定時の挙動と一貫）
- **既定組織スコープ解決（当エンドポイント限定の特例）**: フロントは `organizationId` を渡さず、`OrganizationScopeResolver` は PLAYER 未指定時に `null`（非限定）を返す。そのままでは同日の他団体候補が混入し得るため、`MatchVideoController` は effectiveOrgId が `null` のときに限り**操作ユーザーの所属団体を引き、ちょうど1団体所属ならその団体IDで補完**して当該団体にスコープする（`resolveDefaultOrganizationIdForCandidates`）。動画登録候補は実運用上、操作者の所属団体に限定するのが自然なため。挙動を正確に記すと:
    - **単一所属PLAYER**（所属がちょうど1件）→ その所属団体IDでスコープ
    - **複数所属 / 未所属**、または `currentUserId` が取れない場合 → `null` のまま（＝非限定。複数所属時の一意な団体決定はアプリ全体の別課題に委ねる）
    - ADMIN は `adminOrganizationId`、組織IDを明示指定した PLAYER/SUPER_ADMIN はその団体IDで既にスコープ済みのため、この既定解決は走らない
    - この特例は**当エンドポイント限定**で、他エンドポイントの「PLAYER 未指定→`null`」挙動は変えない。**参加日スコープとは独立**しており、非参加ユーザー（撮影担当等）でも所属団体の候補は見られる
- サーバ側で 組み合わせ（`match_pairings`）+ 試合結果（`matches`）を**自然キー `(matchDate, matchNumber, min(p1,p2), max(p1,p2))` で統合・重複排除**する。同一キーが両方にある場合は **`matches` 優先**（結果情報を保持）
- 各候補に `registered`（同自然キーの `match_videos` が登録済みか）・`hasResult`（同自然キーの `matches` があるか）・`matchId` を付与
- 選手名は `players` からバッチ解決（N+1回避）。`matches` のみのスロットの選手名解決に使う（ペアリング由来は `MatchPairingDto` が既に選手名を保持）
- `player1Id`/`player2Id` は自然キー正規化（player1Id < player2Id）後の**生IDをそのまま**返す（フロントが生IDで「相手未登録(0/null)」を判定するため）
- 並び順: `matchNumber` 昇順

**レスポンス**（`MatchVideoDateCandidateDto` のリスト・`matchNumber` 昇順）:
```json
[
  {
    "matchDate": "2026-06-12",
    "matchNumber": 1,
    "player1Id": 1,
    "player1Name": "山田太郎",
    "player2Id": 2,
    "player2Name": "佐藤花子",
    "hasResult": true,
    "matchId": 100,
    "registered": false
  }
]
```
- `hasResult`/`matchId`: 同自然キーの `matches` が存在する場合のみ true / 非null
- `registered`: 同自然キーの `match_videos` が存在する場合 true（フロントの「登録済み」グレーアウト判定用）

#### POST `/api/match-videos`
**リクエスト**:
```json
{
  "matchDate": "2026-06-12",
  "matchNumber": 1,
  "player1Id": 1,
  "player2Id": 2,
  "videoUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
}
```
**処理フロー**:
1. YouTube URL検証・動画ID（11文字）抽出。受理形式: `youtube.com/watch?v=`、`youtu.be/`、`m.youtube.com/watch`、`youtube.com/shorts/`（www有無・http/https許容）
2. 選手IDバリデーション・キー正規化（player1Id < player2Id）。`player1Id`/`player2Id` は **正の値（`@Positive`）**であること（必須=`@NotNull`）。0（システム未登録ゲストの番兵値）や負値は **400**（`MethodArgumentNotValidException`）。正規化後に **同一選手（player1Id == player2Id）の場合は 400**（`対戦相手が不正です`）。サービス層でも正規化後の `normP1 <= 0` / `normP1 == normP2` を再チェックして二重防御する
3. 対象試合の存在チェック（`matches` または `match_pairings` に同自然キーが存在。match_pairings 側は選手順序不問で照合）
4. 重複チェック（既に動画があれば409）
5. oEmbed API（`https://www.youtube.com/oembed?url=<URL>&format=json`）からタイトル取得。接続2秒・読取3秒のタイムアウト。**取得失敗時は title=null で登録続行（fail-soft）**
6. INSERT（created_by / updated_by = 操作ユーザー）。重複チェック（手順4）通過後〜INSERT間の同時登録で発生した整合性違反のうち、**自然キーUNIQUE制約 `uq_match_videos_match` 由来のもののみ** 409 に変換する（TOCTOU競合の最終防衛）。判定は `DataIntegrityViolationException` の原因チェーン中の Hibernate `ConstraintViolationException#getConstraintName()`（大文字小文字非依存の部分一致、取得不可時はメッセージ）で行う。**FK違反・NOT NULL違反など一意制約以外の整合性エラーは409に変換せずそのまま伝播**させ、本来のステータス（500等）で扱う

**レスポンス**: `MatchVideoDto`
- 選手名（`player1Name` / `player2Name`）は players テーブルから解決
- `matchId` / `winnerId` / `scoreDifference` は同一自然キーの試合結果（`matches`）が存在する場合のみ設定（結果未入力なら null）

#### GET `/api/match-videos/search`
**クエリパラメータ**:
- `playerId`: その選手が対戦者（player1 or player2）の動画に絞り込み（任意）
- `year` + `month`: その年月の範囲に絞り込み（year のみなら年全体）（任意）
- `mine`: `true` の場合は操作ユーザー自身を対象選手として扱う（`playerId` より優先）。デフォルト `false`
- `page`: ページ番号（デフォルト0）/ `size`: 1ページ件数（デフォルト20、上限100）
- 並び順: 試合日の新しい順 → 試合番号の降順
- `year`/`month` のバリデーション（`year` 非null時のみ検証）: `month` は 1〜12、`year` は 2000〜2100。範囲外は **400**（`月は1〜12で指定してください` / `年は2000〜2100で指定してください`）。`month` だけ指定して `year` がない場合は `month` を無視（年月絞り込みなし）。これにより `month=13`/`month=0`/極端な `year` が `LocalDate.of` で `DateTimeException`→500 になるのを防ぐ

**レスポンス**（`PagedResponse<MatchVideoDto>`。Spring の `Page` の直接シリアライズは不安定なため専用形式）:
```json
{
  "content": [
    {
      "id": 100,
      "matchDate": "2026-06-12",
      "matchNumber": 1,
      "player1Id": 1,
      "player1Name": "山田太郎",
      "player2Id": 2,
      "player2Name": "佐藤花子",
      "videoUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
      "youtubeVideoId": "dQw4w9WgXcQ",
      "title": "2026/6/12 第1試合",
      "createdBy": 1,
      "createdAt": "2026-06-12T20:00:00",
      "matchId": 50,
      "winnerId": 1,
      "scoreDifference": 5
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```
- `matchId` / `winnerId` / `scoreDifference` は、同一自然キーの試合結果（`matches`）が存在する場合のみ設定（結果未入力なら null）

**エラー**:
- 400: `YouTubeのURLを入力してください`（URL形式外・動画ID長不正）
- 404: `対象の試合が見つかりません`（自然キー不一致）
- 409: `この試合には既に動画が登録されています`（重複登録）
- 403: 編集・削除の権限なし（登録者本人でも ADMIN+ でもない）

**動画登録時のLINE通知（`MATCH_VIDEO_REGISTERED`）**:
- 新規登録（POST）成功時のみ、対戦当事者（player1 / player2）のうち**登録者本人を除く選手**へ選手用チャネルでLINE通知を送信する。URL差し替え（PUT）では通知しない
- 登録者が当事者の場合は相手選手のみ、登録者が第三者（非当事者）の場合は両選手が対象
- 各対象者の通知設定 `match_video_registered`（デフォルト ON）が ON の者のみ送信。LINE未連携・月間上限超過時はスキップ（既存通知と同様）
- 文言: 「○○さんが M月D日の試合（選手1 vs 選手2）に動画を登録しました」＋アプリ該当ページへのリンク。リンク先は結果入力済み（`matchId` あり）なら `/matches/{matchId}`、未入力なら `/videos`。リンクのベースURLはフロント（Vercel）オリジンを指す `app.frontend-url`（環境変数 `APP_FRONTEND_URL`、未設定時は `http://localhost:5173`）を使う。バックエンドオリジンの `app.base-url`（iCalフィード用）とは別物
- 通知送信の失敗（例外含む）は登録処理を巻き戻さない（try-catch で握りつぶしログ出力のみ）
