---
status: completed
---
# match-video（試合動画アクセス機能） 実装手順書

要件定義書: `docs/features/match-video/requirements.md`
親Issue: [#844](https://github.com/poponta2020/match-tracker/issues/844)

## 実装タスク

### タスク1: DBマイグレーション + MatchVideo エンティティ/リポジトリ
- [x] 完了
- **概要:** 動画台帳テーブル `match_videos` を新設し、エンティティ・リポジトリを実装する。自然キー（match_date, match_number, player1_id, player2_id・p1<p2正規化）+ UNIQUE制約。provider カラム（'YOUTUBE' 固定）を含む
- **変更対象ファイル:**
  - `database/create_match_videos.sql` — 新規。PostgreSQL用テーブル作成SQL（要件定義書 4.2 のDDL）。**本番DB（Render PostgreSQL）適用必須**
  - `database/phase1_schema.sql` — CI用MySQLスキーマに match_videos を追記（CIでの利用有無を実装時に確認し、不要なら見送り）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/MatchVideo.java` — 新規。Match と同じ流儀（plain Long、`JstDateTimeUtil`、`@PrePersist`/`@PreUpdate` で p1<p2 入れ替え）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/MatchVideoRepository.java` — 新規。自然キー検索 / 日付検索 / 選手検索（p1 OR p2）/ 倉庫検索（年月・選手・ページング）
  - `docs/DESIGN.md` — テーブル定義を追記
- **依存タスク:** なし
- **対応Issue:** #845

### タスク2: 動画 CRUD + 検索 API（Service / Controller / DTO）
- [x] 完了
- **概要:** 動画の登録・URL差し替え・削除・日付別一覧・倉庫検索のAPIを実装する。登録フロー: URL検証・videoId抽出 → キー正規化 → 対象試合存在チェック（matches または match_pairings）→ 重複チェック（409）→ oEmbedタイトル取得（fail-soft）→ INSERT。編集・削除は登録者本人 or ADMIN+ のみ（service層で所有者チェック）
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/MatchVideoDto.java` — 新規（`fromEntity()` 規約、選手名解決、入力済みの場合 matchId/結果情報を含む）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/MatchVideoCreateRequest.java` — 新規
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/MatchVideoUpdateRequest.java` — 新規
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchVideoService.java` — 新規（YouTube URL検証/ID抽出ユーティリティ、oEmbed呼び出し（短タイムアウト・失敗時null）、権限チェック）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/MatchVideoController.java` — 新規（`@RequireRole` 全ロール。POST/PUT/DELETE/GET?date=/GET /search）
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/MatchVideoServiceTest.java` — 新規（URL検証・正規化・重複・権限・oEmbed失敗時のフォールバックを網羅。カバレッジ60%基準）
  - `docs/SPECIFICATION.md` — API仕様を追記
  - `docs/DESIGN.md` — クラス構成を追記
- **依存タスク:** タスク1
- **対応Issue:** #846

### タスク3: MatchDto 拡張（video 付与）
- [x] 完了
- **概要:** `MatchDto` に `video: { id, videoUrl, youtubeVideoId, title } | null` を追加し、試合詳細（単体）と試合一覧（バッチ解決でN+1回避）の両方で動画有無を返せるようにする
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/MatchDto.java` — video フィールド追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchService.java` — 単体取得・一覧取得時の video 解決（一覧は対象選手の動画を1クエリで引いてマップ照合）
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/MatchServiceTest.java` — video 付与のテスト追加
  - `docs/DESIGN.md` — DTO変更を追記
- **依存タスク:** タスク1
- **対応Issue:** #847

### タスク4: フロントエンド基盤（APIクライアント + 再生/登録モーダル）
- [x] 完了
- **概要:** 動画APIクライアントと、全画面で使い回す再生モーダル・登録/編集モーダルを実装する。再生は `youtube-nocookie.com/embed/{videoId}` iframe、サムネイルは `i.ytimg.com/vi/{videoId}/mqdefault.jpg`
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/api/matchVideos.js` — 新規（register / update / remove / getByDate / search）
  - `karuta-tracker-ui/src/components/VideoPlayerModal.jsx` — 新規（埋め込みプレイヤー・対戦情報・「YouTubeで開く」・結果入力済みなら「試合詳細を見る」リンク）
  - `karuta-tracker-ui/src/components/VideoRegisterModal.jsx` — 新規（URL入力+バリデーション。このタスクでは「対象試合固定モード」のみ。倉庫用の試合選択ステップはタスク8）
- **依存タスク:** タスク2
- **対応Issue:** #848

### タスク5: ① 試合詳細画面の動画セクション
- [x] 完了
- **概要:** `MatchDetail.jsx` の統合カード下に「試合動画」セクションを追加。動画あり→埋め込み再生+（登録者本人/ADMIN+のみ）編集・削除ボタン、動画なし→「動画を追加」ボタン（VideoRegisterModal・対象固定モード）
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/MatchDetail.jsx` — 動画セクション追加（MatchDto.video を使用）
  - `docs/SCREEN_LIST.md` — 試合詳細の説明更新
- **依存タスク:** タスク3, タスク4
- **対応Issue:** #849

### タスク6: ② 当日結果一覧の「動画あり」バッジ
- [x] 完了
- **概要:** `MatchResultsView.jsx` で `GET /api/match-videos?date=` を取得し、動画がある組にバッジ表示。タップ分岐: 結果入力済み→試合詳細へ遷移 / 未入力→VideoPlayerModal で再生。ペアリングとの照合は (matchNumber, p1<p2正規化済み選手ペア) で行う
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/MatchResultsView.jsx` — バッジ表示・タップ分岐・モーダル組み込み
  - `docs/SCREEN_LIST.md` — 当日結果一覧の説明更新
- **依存タスク:** タスク2, タスク4
- **対応Issue:** #850

### タスク7: ③ 個人別一覧の動画アイコン列
- [x] 完了
- **概要:** `MatchList.jsx` の6列グリッドに動画アイコン列を追加して7列化（非表示行でも列幅を確保し列揃え維持）。動画ありの行のみアイコン表示、タップで試合詳細へ遷移。自分・他選手どちらの閲覧でも表示
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/MatchList.jsx` — グリッド列定義変更 + 動画アイコン（MatchDto.video を使用）
  - `docs/SCREEN_LIST.md` — 一覧のカラム仕様更新（列幅の記載も更新）
- **依存タスク:** タスク3
- **対応Issue:** #851

### タスク8: ④ 動画倉庫画面
- [ ] 完了
- **概要:** 新規画面 `/videos`（VideoLibrary.jsx）。サムネイル付きリスト（新しい順・ページング）、選手検索・年月絞り込み・「自分が関わる動画」トグル。一覧タップ→VideoPlayerModal。「動画を登録」→VideoRegisterModal に試合選択ステップ（日付起点/選手起点）を追加。設定画面メニューとルートも追加
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/videos/VideoLibrary.jsx` — 新規
  - `karuta-tracker-ui/src/components/VideoRegisterModal.jsx` — 試合選択ステップ追加（日付→その日の組み合わせ+試合一覧 / 選手→最近の試合一覧）
  - `karuta-tracker-ui/src/pages/SettingsPage.jsx` — gridItems に「動画倉庫」追加（全ロール）
  - `karuta-tracker-ui/src/App.jsx` — `/videos` ルート追加（PrivateRoute + Layout）
  - `docs/SCREEN_LIST.md` — 動画倉庫画面を追加、設定画面メニュー表更新
- **依存タスク:** タスク2, タスク4
- **対応Issue:** #852

### タスク9: LINE通知（動画登録時に対戦当事者へ）
- [ ] 完了
- **概要:** 動画登録時に対戦当事者（登録者自身を除く）へLINE通知を送る。通知種別 `MATCH_VIDEO_REGISTERED`（PLAYER向け・プレフィックスなし）を追加し、通知設定画面にトグルを追加（デフォルトON）
- **変更対象ファイル:**
  - `database/add_match_video_registered_notification.sql` — 新規。`line_notification_preferences.match_video_registered` カラム追加 + `line_message_log` の notification_type CHECK制約更新（既存 `add_mentor_comment_notification.sql` と同パターン）。**本番DB適用必須**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/LineNotificationPreference.java` — `matchVideoRegistered` 追加（デフォルトTRUE）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/LineMessageLog.java` — notification_type に `MATCH_VIDEO_REGISTERED` 追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java` — `sendMatchVideoRegisteredNotification()` 追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchVideoService.java` — 登録成功時に通知トリガ（通知失敗は登録を巻き戻さない）
  - `karuta-tracker-ui/src/pages/notifications/NotificationSettings.jsx` — 「試合動画登録通知」トグル追加
  - 関連テスト — 通知対象の選定（登録者除外・設定OFF者除外）のテスト
  - `docs/SPECIFICATION.md` — 通知仕様を追記
- **依存タスク:** タスク2
- **対応Issue:** #853

## 実装順序

1. **タスク1**（#845・依存なし・DB基盤）
2. **タスク2**（#846）／ **タスク3**（#847）— タスク1に依存・相互に並行可
3. **タスク4**（#848・タスク2に依存）
4. **タスク5**（#849）／ **タスク6**（#850）／ **タスク7**（#851）— 並行可
5. **タスク8**（#852）
6. **タスク9**（#853・フロント画面タスクとは独立して進行可）

## 運用メモ

- タスク1・タスク9 のマイグレーションSQLは、PRマージ前後に **本番DB（Render PostgreSQL）へ psql で適用必須**（CLAUDE.md のDBマイグレーション適用ルール）。適用後 `\d match_videos` / `\d line_notification_preferences` で確認
- 会のYouTube共有チャンネル（Googleアカウント）の用意・電話番号確認（15分超対応）はアプリ実装と独立した運用準備タスク
