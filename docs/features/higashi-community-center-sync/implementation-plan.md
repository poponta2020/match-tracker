---
status: completed
---
# 東区民センター予約同期 実装手順書

## 実装タスク

### タスク1: スクレイパー実装 (`scrape-higashi-history.js`)
- [x] 完了
- **対応Issue:** #473
- **概要:** sapporo-community.jp のマイページにログインし、申込履歴画面から東区民センターの夜間予約を抽出、stdout に JSON 出力する Playwright スクリプトを実装する。
- **変更対象ファイル:**
  - `scripts/room-checker/scrape-higashi-history.js` — 新規作成
- **実装詳細:**
  - CLI 引数: `--months <N>`（デフォルト 2）
  - 環境変数: `SAPPORO_COMMUNITY_USER_ID`, `SAPPORO_COMMUNITY_PASSWORD`
  - 画面遷移: `UserLogin.aspx` → ID/PW 入力 → `#ctl00_cphMain_btnReg` クリック → メニュー → `#ctl00_cphMain_WucImgBtnHistory_imgbtnMain`（申込履歴）クリック → `UserHistory.aspx`
  - テーブル `#ctl00_cphMain_gvView` から全ページを走査（`javascript:__doPostBack('ctl00$cphMain$gvView', 'Page$N')` でページング）
  - 行抽出ルール:
    - `td` 7列の行のみ（3列目/5列目が時刻フォーマット `^\d{1,2}:\d{2}$`）
    - `申込内容` に `札幌市東区民センター` を含む行のみ採用
    - `状態=取消済` は除外
    - 和暦 `令和YY年MM月DD日（曜）` → 西暦 `YYYY-MM-DD` に変換
    - 部屋名を `さくら` / `かっこう` / `和室全室` に正規化
    - 夜間フィルタ: 開始時刻 17:00 以降のみ出力
  - 出力 JSON スキーマ: `{date, room, status, startTime, endTime, rawContent}[]`
  - エラー処理: ログイン失敗・`OutsideServiceTime.html` / `HttpClientError.html` 検知で `process.exit(1)`。リトライ無し。
  - `console.error` に進捗ログ、`console.log` は JSON 専用。
- **依存タスク:** なし

### タスク2: 同期スクリプト実装 (`sync-higashi-reservations.js`)
- [x] 完了
- **対応Issue:** #474
- **概要:** スクレイパーを子プロセス実行して予約JSONを取得し、`practice_sessions` に UPSERT する Node.js スクリプトを実装する。Kaderu の `sync-reservations.js` を参考にする。
- **変更対象ファイル:**
  - `scripts/room-checker/sync-higashi-reservations.js` — 新規作成
- **実装詳細:**
  - CLI 引数: `--months <N>`（デフォルト 2）, `--dry-run`
  - 環境変数: スクレイパー用 + `DATABASE_URL`（または `DB_URL` + `DB_USERNAME` + `DB_PASSWORD`）
  - 部屋 → venue_id マッピング:
    - `さくら` のみ → `東🌸 (venue_id=6)`
    - `さくら + かっこう` 同日 → `東全室 (venue_id=10)`
    - `和室全室` → `東全室 (venue_id=10)`
    - `かっこう` のみ → 警告ログを出してスキップ
  - 対象組織: `organizations.code = 'hokudai'` から organization_id を動的取得
  - `venues` テーブルから `id IN (6, 10)` で `default_match_count`, `capacity` を取得
  - 各日付の処理:
    - 過去日付はスキップ
    - 既存セッション無し → `INSERT ... ON CONFLICT (session_date, organization_id) DO NOTHING`
      - `start_time='18:00'`, `end_time='21:00'` 固定
      - `total_matches=venues.default_match_count`, `capacity=venues.capacity`
      - `created_by=updated_by=0`（SYSTEM_USER_ID）
    - 既存あり + `venue_id=NULL` → 算出会場で UPDATE
    - 既存あり + `venue_id=6` + 算出 `10` → `10` に昇格 UPDATE
    - 既存あり + `venue_id=10` → 触らない（ダウングレード無し）
    - 既存あり + `venue_id` が Kaderu 系 (3,4,7,8,9,11) → 触らない（1日併存しない前提）
  - 結果サマリー: `{created, expanded, skipped}` 件数と詳細ログを `console.log` に出力
- **依存タスク:** タスク1 (#473)

### タスク3: GitHub Actions ワークフロー追加
- [ ] 完了
- **対応Issue:** #475
- **概要:** 30分おきに同期スクリプトを実行する GitHub Actions ワークフローを追加する。`sync-kaderu-reservations.yml` をベースに作成。
- **変更対象ファイル:**
  - `.github/workflows/sync-higashi-reservations.yml` — 新規作成
- **実装詳細:**
  - `name`: `Sync Higashi Community Center Reservations to Practice Sessions`
  - `on`: `schedule: - cron: '*/30 * * * *'` + `workflow_dispatch`
  - `concurrency.group`: `higashi-reservation-sync`, `cancel-in-progress: false`
  - ジョブ: `ubuntu-latest`, `timeout-minutes: 15`
  - ステップ: checkout → Node.js 20 setup → `npm ci` → `npx playwright install --with-deps chromium` → `node sync-higashi-reservations.js --months 2`
  - 環境変数:
    - `SAPPORO_COMMUNITY_USER_ID`: `${{ secrets.SAPPORO_COMMUNITY_USER_ID }}`
    - `SAPPORO_COMMUNITY_PASSWORD`: `${{ secrets.SAPPORO_COMMUNITY_PASSWORD }}`
    - `DATABASE_URL`: `${{ secrets.KADERU_DATABASE_URL }}` （既存を流用）
- **依存タスク:** タスク2 (#474)

### タスク4: ドキュメント更新
- [ ] 完了
- **対応Issue:** #476
- **概要:** `docs/SPECIFICATION.md` と `docs/DESIGN.md` に東区民センター予約同期のセクションを追加する。
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` — 4.4 かでる2・7 予約同期の後に「4.5 東区民センター予約同期」セクションを追加
  - `docs/DESIGN.md` — 7.8 かでる予約→練習日自動登録フローの後に「7.9 東区民センター予約→練習日自動登録フロー」を追加
  - `docs/SCREEN_LIST.md` — 変更なし（UI 追加なし）
- **実装詳細:**
  - SPECIFICATION.md: 機能概要・対象範囲・処理概要・起動契機・関連ファイル（Kaderu セクションと同形式）
  - DESIGN.md: 処理シーケンス・実装ファイル一覧（Kaderu セクションと同形式）
- **依存タスク:** タスク1 (#473), タスク2 (#474), タスク3 (#475)

### タスク5: GitHub Secrets 登録（ユーザー作業）
- [ ] 完了
- **対応Issue:** #477
- **概要:** GitHub リポジトリに新規 secret を登録する（コミット不要、Web UI 操作）。
- **変更対象ファイル:** なし
- **実装詳細:**
  - リポジトリの Settings → Secrets and variables → Actions → New repository secret
    - `SAPPORO_COMMUNITY_USER_ID`: 東区民センターの利用者ID
    - `SAPPORO_COMMUNITY_PASSWORD`: 東区民センターのパスワード
  - 既存 `KADERU_DATABASE_URL` はそのまま流用（新規追加不要）
- **依存タスク:** タスク3 (#475)

### タスク6: 動作検証（手動トリガー）
- [ ] 完了
- **対応Issue:** #478
- **概要:** GitHub Actions の `workflow_dispatch` で手動実行し、本番DBで期待通り練習日が登録されるか確認する。
- **変更対象ファイル:** なし
- **実装詳細:**
  - 事前に `sapporo-community.jp` 側に検証用の予約があることを確認（無ければテストしない）
  - GitHub Actions UI から `Sync Higashi Community Center Reservations to Practice Sessions` を手動トリガー
  - ログで「スクレイピング件数」「dateVenueMap の内訳」「created / expanded / skipped 件数」を確認
  - Render PostgreSQL で `SELECT * FROM practice_sessions WHERE organization_id=<hokudai> AND venue_id IN (6,10) ORDER BY session_date;` を実行し、期待行が入っていることを確認
  - 問題あれば `workflow_dispatch` 時に `--dry-run` オプション付き派生ワークフローを一時的に追加して検証（任意）
- **依存タスク:** タスク5 (#477)

## 実装順序
1. タスク1: スクレイパー実装（依存なし）
2. タスク2: 同期スクリプト実装（タスク1 に依存）
3. タスク3: GitHub Actions ワークフロー追加（タスク2 に依存）
4. タスク4: ドキュメント更新（タスク1〜3 に依存）
5. タスク5: GitHub Secrets 登録（タスク3 merge 後、ユーザー作業）
6. タスク6: 動作検証（タスク5 完了後）
