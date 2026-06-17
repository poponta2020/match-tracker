---
status: completed
---
# match-video（試合動画アクセス機能） 要件定義書

## 1. 概要

### 目的
練習で撮影している試合動画を、対戦記録と紐付けてアプリからアクセスできるようにする。

### 背景・動機
- 練習で試合動画を撮影する運用が始まっており、メンバーから「アプリから見たい」という要望が届いている
- 対戦記録（枚数差・メモ・お手付き）と動画を同じ場所で振り返れるようにする

## 2. ユーザーストーリー

- **対象ユーザー**: ログイン済みの全選手（PLAYER / ADMIN / SUPER_ADMIN）
- **動画の登録者**: 全選手（PLAYER 含む）
- **動画の閲覧者**: ログイン済みの全選手（対戦当事者に限定しない）
- **撮影単位**: 1動画 = 1試合、**1試合につき動画1本まで**
- **想定ボリューム**: 月10〜30本、1本60〜90分、1080p（H.264）
- **保存期間**: 無期限保存
- **利用シナリオ**:
  1. 撮影者が練習後、YouTube アプリから動画を限定公開でアップロードする
  2. 撮影者（または誰か）がアプリで当該試合に動画URLを登録する
  3. 対戦当事者にLINE通知が届く
  4. 選手は試合詳細・当日結果一覧・個人別一覧・動画倉庫のいずれからでも動画にたどり着き、アプリ内で再生して振り返り・研究を行う

### 動画の保存方式（確定）

**YouTube（限定公開）+ URL登録方式** を採用する。

- 撮影者が YouTube アプリから限定公開でアップロードし、そのURLをアプリの試合に登録する
- アプリは登録されたURLを使って導線表示・埋め込み再生を行う
- **動画ファイル本体はアプリのサーバーには一切置かない**。アプリのDBが持つのは「URLと試合の紐付け（台帳）」のみ

### 運用フロー（アップロードから登録まで）

```
[スマホで撮影] → [YouTubeへアップ（YouTubeアプリ・手動）] → [URLをコピー] → [アプリに貼り付け]
     ↓                    ↓                                      ↓
 カメラロール        動画本体はYouTubeに保存                アプリは「URLと試合の紐付け」
                   （限定公開・無料・無制限）                 だけをDBに保存
```

撮影者の操作（練習後、1本あたり2〜3分の手作業）:
1. スマホで試合を撮影
2. YouTube アプリで**会の共有チャンネル**にログインし、「＋」→「動画をアップロード」→ 公開範囲を**限定公開**にしてアップロード（Wi-Fi推奨。アップロード処理はYouTubeアプリがバックグラウンドで実施）
3. アップロード完了後、「共有」→「リンクをコピー」
4. 本アプリの試合詳細 or 動画倉庫から「動画を追加」→ 対象試合を選んでURL貼り付け → 登録完了

### YouTubeアカウント運用（確定: 会の共有チャンネル方式）

- 会で Google アカウントを1つ用意し、撮影担当者がそのアカウントで YouTube にアップロードする
- 全動画が1チャンネルに集約され、**退会者が出ても動画が消えない**。動画本体の整理・削除も管理者が一括で行える
- 15分超の動画アップロードに必要な電話番号確認（無料・初回のみ）も共有アカウント1回で済む
- アカウントの認証情報は管理者間で安全に共有・管理する

### 管理の役割分担

| 管理対象 | 場所 | できる人 |
|---|---|---|
| 動画ファイル本体（YouTube上の削除・公開設定変更） | YouTube | 共有チャンネルにログインできる撮影担当者・管理者 |
| 試合との紐付け（登録・URL差し替え・紐付け削除） | 本アプリ | 登録者本人＋管理者 |
| 検索・閲覧 | 本アプリ（動画倉庫） | 全選手 |

**注意**: アプリ上で「削除」しても消えるのは紐付けのみで、YouTube上の動画本体は残る（本体の削除は共有チャンネル側で行う）。

#### 運用上の注意
- 限定公開のため「URLを知っていれば会員外でも視聴可能」という流出リスクは許容する（運用でURLを外部共有しない）

## 3. 機能要件

### 3.1 画面仕様

#### ① 試合結果詳細画面（`/matches/:id`・MatchDetail.jsx）
- 統合カードの下に「試合動画」セクションを追加
  - 動画あり: YouTube埋め込みプレイヤー（iframe）で再生可能。倍速・シーク・全画面はYouTubeプレイヤー標準機能。「YouTubeで開く」リンクを併設
  - 動画あり かつ（登録者本人 or ADMIN+）: 「編集（URL差し替え）」「削除」ボタン表示
  - 動画なし: 「動画を追加」ボタン（全選手）→ URL入力モーダルで登録

#### ② 当日結果一覧画面（`/matches/results/:sessionId?`・MatchResultsView.jsx）
- 動画がある組に「動画あり」バッジ（🎥アイコン等）を表示
  - 結果入力済みの組: タップで試合詳細へ遷移
  - 結果未入力の組: タップで再生モーダルを開く（試合詳細画面が存在しないため）

#### ③ 個人別対戦一覧画面（`/matches?playerId=`・MatchList.jsx）
- 動画がある行に動画アイコンを表示（既存6列グリッドに1列追加。メモアイコンと同様、非表示行でも列幅を確保し列揃えを維持）
- タップで試合詳細へ遷移
- 自分・他選手どちらの一覧でも表示（閲覧は全選手可のため）

#### ④ 動画倉庫画面（新規 `/videos`・VideoLibrary.jsx）
- 設定画面（`/settings`）のメニューグリッドに「動画倉庫」を追加（全ロール表示）
- 表示形式: **サムネイル付きリスト**（YouTubeサムネイル + 試合日・試合番号・対戦カード・結果（入力済みの場合）・動画タイトル）
- 並び順: 試合日の新しい順、ページング（無期限蓄積のため）
- 検索・絞り込み:
  - **選手検索**（選手名で、その選手が対戦者に含まれる動画を絞り込み）
  - **年月絞り込み**
  - **「自分が関わる動画」トグル**（自分が対戦者の動画のみ）
- 一覧タップ → 再生モーダル（モーダル内に対戦情報 + 結果入力済みの場合は「試合詳細を見る」リンク）
- 「動画を登録」ボタン → 登録モーダル。起点は2種:
  - **日付起点**: 日付選択 → その日の組み合わせ＋試合の一覧から対象を選択 → URL貼り付け
  - **選手起点**: 選手検索 → その選手の最近の試合（組み合わせ含む）から対象を選択 → URL貼り付け

#### 入力・バリデーション
- 動画URL: YouTube URL形式のみ受け付ける（`youtube.com/watch?v=`、`youtu.be/`、`m.youtube.com/watch`、`youtube.com/shorts/`）。動画ID（11文字）を抽出して保存
- 動画タイトル: 登録時に YouTube oEmbed API から自動取得（取得失敗時は空のまま登録続行）

### 3.2 ビジネスルール

| # | ルール |
|---|---|
| 1 | 1試合につき動画1本まで（DB UNIQUE制約で担保）。差し替えは編集（URL更新）で行う |
| 2 | **結果未入力の試合（組み合わせのみ存在）にも動画を登録できる**。結果入力後は自動的に試合詳細にも表示される |
| 3 | 登録: 全選手可。編集・削除: 登録者本人 + ADMIN/SUPER_ADMIN のみ |
| 4 | 閲覧・再生: ログイン済みの全選手 |
| 5 | 動画登録時、対戦当事者にLINE通知（登録者自身が当事者の場合は相手のみ）。通知設定でOFF可能（デフォルトON） |
| 6 | 試合（結果）が削除・編集されても動画レコードは自動削除されない（動画倉庫に残り、手動削除可能） |

#### エラーケース
- 既に動画が登録済みの試合への登録 → 「この試合には既に動画が登録されています」（409）
- YouTube URL形式でない → 「YouTubeのURLを入力してください」（400）
- 存在しない試合・組み合わせへの登録（自然キー不一致）→ 「対象の試合が見つかりません」（404）
- 編集・削除の権限なし → 403
- oEmbedタイトル取得失敗 → エラーにせず、タイトル空で登録続行

## 4. 技術設計

### 4.1 データモデル方針（コード調査に基づく）

`matches` と `match_pairings` はFKを持たず、**(日付, 試合番号, player1_id, player2_id) の自然キーで対応付く**構造（`matches` には `uq_matches_date_number_players` UNIQUE制約あり、player1_id < player2_id をアプリ層で保証）。

動画は独立テーブル `match_videos` とし、同じ自然キーで紐付ける:
- 結果未入力（ペアリングのみ）段階でも登録可能
- 結果入力後は同一キーで自動的に試合詳細にも表示される（付け替え処理不要）
- 1試合1本制約は自然キーのUNIQUE制約で担保
- `matches`/`match_pairings` と同様に物理削除運用
- `provider` カラム（'YOUTUBE' 固定）を持たせ、将来 R2 等の直接アップロード方式を追加可能にする

### 4.2 DB設計

#### 新規テーブル `match_videos`（`database/create_match_videos.sql`・**本番DB適用必須**）

```sql
CREATE TABLE IF NOT EXISTS match_videos (
    id BIGSERIAL PRIMARY KEY,
    match_date DATE NOT NULL,
    match_number INTEGER NOT NULL,
    player1_id BIGINT NOT NULL REFERENCES players(id) ON DELETE RESTRICT,
    player2_id BIGINT NOT NULL REFERENCES players(id) ON DELETE RESTRICT,
    provider VARCHAR(20) NOT NULL DEFAULT 'YOUTUBE',
    video_url TEXT NOT NULL,
    youtube_video_id VARCHAR(20),
    title VARCHAR(255),
    created_by BIGINT NOT NULL REFERENCES players(id) ON DELETE RESTRICT,
    updated_by BIGINT NOT NULL REFERENCES players(id) ON DELETE RESTRICT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_match_videos_match UNIQUE (match_date, match_number, player1_id, player2_id)
);
CREATE INDEX IF NOT EXISTS idx_match_videos_player1 ON match_videos(player1_id);
CREATE INDEX IF NOT EXISTS idx_match_videos_player2 ON match_videos(player2_id);
```

- 日付絞り込みはUNIQUE制約の先頭カラム（match_date）でインデックス代用
- CI用 MySQL スキーマ（`phase1_schema.sql`）への追記も実装時に確認

#### LINE通知設定（`database/add_match_video_registered_notification.sql`・**本番DB適用必須**）

```sql
ALTER TABLE line_notification_preferences
    ADD COLUMN IF NOT EXISTS match_video_registered BOOLEAN NOT NULL DEFAULT TRUE;

-- line_message_log の notification_type CHECK制約に 'MATCH_VIDEO_REGISTERED' を追加
-- （既存の add_mentor_comment_notification.sql と同じ DROP→ADD パターン）
```

### 4.3 API設計

| メソッド | URL | 説明 | 権限 |
|---|---|---|---|
| POST | `/api/match-videos` | 動画登録 | 全ロール |
| PUT | `/api/match-videos/{id}` | URL差し替え | 登録者本人 or ADMIN+ |
| DELETE | `/api/match-videos/{id}` | 削除 | 登録者本人 or ADMIN+ |
| GET | `/api/match-videos?date=YYYY-MM-DD` | 指定日の動画一覧（②のバッジ表示用） | 全ロール |
| GET | `/api/match-videos/search?playerId=&year=&month=&mine=&page=&size=` | 動画倉庫の検索・ページング | 全ロール |

- POST リクエスト: `{ matchDate, matchNumber, player1Id, player2Id, videoUrl }`
- 登録処理フロー: URL検証・videoId抽出 → キー正規化（p1<p2）→ 対象試合の存在チェック（`matches` または `match_pairings` に同キーが存在）→ 重複チェック → oEmbedタイトル取得（fail-soft）→ INSERT → LINE通知
- レスポンス DTO `MatchVideoDto`: `{ id, matchDate, matchNumber, player1Id, player1Name, player2Id, player2Name, videoUrl, youtubeVideoId, title, createdBy, createdAt, matchId(入力済みの場合), winnerId, scoreDifference }`
- **MatchDto 拡張**: `video: { id, videoUrl, youtubeVideoId, title } | null` を追加（①試合詳細と③個人別一覧が既存APIのまま動画有無を取得できる。一覧はバッチ解決でN+1回避）

### 4.4 バックエンド設計（`com.karuta.matchtracker`）

| 種別 | クラス | 内容 |
|---|---|---|
| entity | `MatchVideo` | 新規。`Match` と同じ流儀（plain Long ID、`JstDateTimeUtil`、`@PrePersist` で p1<p2 入れ替え） |
| repository | `MatchVideoRepository` | 自然キー検索・日付検索・選手検索（p1 OR p2）・倉庫検索（動的条件+ページング） |
| dto | `MatchVideoDto` / `MatchVideoCreateRequest` / `MatchVideoUpdateRequest` | `fromEntity()` 静的メソッド規約に従う |
| service | `MatchVideoService` | 登録・更新・削除・検索。oEmbedタイトル取得（タイムアウト短め・fail-soft）。権限チェック |
| controller | `MatchVideoController` | `@RequireRole` 全ロール。編集削除の所有者チェックはservice層 |
| service | `LineNotificationService` | `sendMatchVideoRegisteredNotification()` 追加（当事者宛・登録者除く・設定ON者のみ） |
| entity | `LineMessageLog` | notification_type に `MATCH_VIDEO_REGISTERED` 追加 |
| entity | `LineNotificationPreference` | `matchVideoRegistered`（デフォルトTRUE）カラム追加 |
| service | `MatchService` | `MatchDto` への video 付与（単体・一覧バッチ） |

### 4.5 フロントエンド設計（`karuta-tracker-ui/src`）

| 種別 | ファイル | 内容 |
|---|---|---|
| 新規 | `api/matchVideos.js` | APIクライアント（register / update / remove / getByDate / search） |
| 新規 | `components/VideoPlayerModal.jsx` | 共通再生モーダル（youtube-nocookie iframe埋め込み・対戦情報・YouTubeで開く・試合詳細リンク） |
| 新規 | `components/VideoRegisterModal.jsx` | 登録/編集モーダル（試合詳細からは対象固定、倉庫からは日付/選手起点の試合選択ステップ付き） |
| 新規 | `pages/videos/VideoLibrary.jsx` | 動画倉庫画面（検索・サムネイルリスト・ページング・登録導線） |
| 変更 | `pages/matches/MatchDetail.jsx` | 動画セクション追加（埋め込み・追加/編集/削除） |
| 変更 | `pages/matches/MatchResultsView.jsx` | 動画ありバッジ + タップ分岐（詳細遷移 / 再生モーダル） |
| 変更 | `pages/matches/MatchList.jsx` | 動画アイコン列追加（7列グリッド化、列揃え維持） |
| 変更 | `pages/SettingsPage.jsx` | gridItems に「動画倉庫」追加（全ロール） |
| 変更 | `App.jsx` | `/videos` ルート追加（PrivateRoute + Layout） |
| 変更 | `pages/notifications/NotificationSettings.jsx` | 「試合動画登録通知」トグル追加 |

- 埋め込み再生: `https://www.youtube-nocookie.com/embed/{videoId}`
- サムネイル: `https://i.ytimg.com/vi/{videoId}/mqdefault.jpg`（追加コストなし・APIキー不要）

## 5. 影響範囲

### 変更が必要な既存ファイル
4.4 / 4.5 の「変更」行を参照。

### 既存機能への影響評価

| 項目 | 影響 |
|---|---|
| `matches` / `match_pairings` テーブル | 変更なし（新テーブル追加のみ） |
| MatchDto 拡張 | レスポンスへのフィールド追加のみ（後方互換） |
| MatchList の列追加 | 精密な列幅指定（SCREEN_LIST記載）の変更を伴うため、全行の列揃え崩れに注意 |
| 試合の編集・削除 | 動画は自動では消えない/付いてこない。日付・試合番号・対戦相手を編集すると自然キーが変わり紐付けが外れる（動画は旧キーのまま倉庫に残る → 削除して再登録で対応）。エッジケースとして許容 |
| ペアリング再生成 | 同上（動画は倉庫に残る） |
| ゲスト選手（システム未登録の対戦相手）の試合 | player2 がプレースホルダー扱いになるため、実装時に挙動確認（動画登録自体は可能とする想定） |
| LINE通知基盤 | 種別1つ追加のみ（既存通知に影響なし） |
| DBマイグレーション | **2本のSQLを本番DB（Render PostgreSQL）に適用必須**（CLAUDE.md のDBマイグレーション適用ルールに従う） |

## 6. 設計判断の根拠

### 保存方式: YouTube URL登録方式の採用理由（2026-06 決定）

「無料は絶対条件」が決定打。月10〜30本 × 90分 × 1080p（約4GB/本）× 無期限保存の条件で無料を満たすのは YouTube のみ。

検討した代替案と除外理由:

| 案 | 除外理由 |
|---|---|
| Cloudflare R2 直接アップロード | 無料枠10GB＝動画2〜3本分で枯渇。以降 $0.015/GB/月（1年後約$14/月）の蓄積型コスト |
| Bunny Stream | 無料枠なし。ストレージ+配信従量で月$15〜25規模 |
| Cloudflare Stream | 分単位課金（$5/1,000分/月）で90分動画では1年後に月$100超。長尺に不向き |
| YouTube Data API による自動アップロード | 1本1,600ユニット消費・標準枠1日6本まで。監査未通過アプリのアップロードは強制非公開になるため限定公開にできない。手動アップ+URL貼り付けのみが現実的 |

将来の拡張余地: `match_videos.provider` 種別により、予算が付いた場合に R2 等の直接アップロード方式を追加できる（Phase 2 構想）。

トレードオフとして受容した事項:
- 限定公開URLの流出リスク（ログイン保護なし）
- アップロードがアプリ外の手動2ステップ（YouTubeアプリでアップ → URLコピペ）

### 動画の紐付け: 自然キー方式の採用理由

「結果未入力の試合にも動画を登録したい」という要件に対し、`match_id` FK方式では未入力時に紐付け先が存在しない。`matches` と `match_pairings` が既に (日付, 試合番号, 選手ペア) の自然キーで対応付く設計であることを利用し、`match_videos` も同じ自然キーで持つことで、未入力→入力済みの状態遷移時にデータ移行・付け替え処理が不要になる。

### その他の判断

- **1試合1本制約**: 自然キーのDB UNIQUE制約で担保（アプリ層チェックに依存しない）
- **試合削除時に動画を残す**: 動画はYouTube上の資産への参照であり、試合レコード操作で暗黙に消えると復元困難。明示削除のみとする
- **物理削除**: `matches`/`match_pairings` と同じ運用に合わせる（論理削除 `deleted_at` は players 等の一部テーブルのみの慣習）
- **タイトル自動取得に oEmbed を採用**: APIキー・クォータ不要、限定公開動画でも取得可能。URL有効性の簡易チェックを兼ねる
