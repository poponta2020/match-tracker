---
status: completed
---
# 東区民センター予約同期 要件定義書（ドラフト）

## 1. 概要

### 目的
札幌市東区民センターの予約マイページ（sapporo-community.jp）から予約状況をスクレイピングし、アプリの `practice_sessions` に自動登録する。結果として、既存の伝助ページ作成・書き込みパイプラインに自動で乗る。

### 背景・動機
Kaderu 2.7（道立女性プラザ）で既に同様の自動同期パイプラインを稼働させている。東区民センターでも同じことを実現したい（機能の移植・横展開）。

## 2. ユーザーストーリー

### 対象ユーザー
- 組織: `hokudai`（Kaderu と同じ団体）
- 実行主体: GitHub Actions スケジュール（ユーザー操作は不要 / 自動）
- 間接的な受益者: 練習参加者（伝助ページが自動で作成・更新される）

### ユーザーの目的
- 東区民センターで取った予約を、手作業で練習日・伝助に反映する手間をなくす
- Kaderu と運用を揃える

### 利用シナリオ
1. 運営者が sapporo-community.jp 上で東区民センターの予約を取る（人間が手動）
2. GitHub Actions の定期ジョブが予約履歴をスクレイプ
3. `practice_sessions` に練習日として自動登録（会場・時刻を含む）
4. 既存の伝助ページ作成ジョブ（`DensukePageCreateService`）が走り、伝助ページが自動生成
5. 以降、既存の伝助書き込み・読み取り同期パイプラインが通常通り動作

## 3. 機能要件

### 3.1 同期対象データ
- sapporo-community.jp のマイページ「申込履歴・結果」に表示される予約のうち、以下を満たすもの。
  - **施設名**: 申込内容に `札幌市東区民センター` を含む
  - **部屋**: `さくら（和室）` / `かっこう（和室）` / `和室全室`
  - **ステータス**: `予約済` または `利用済`（`取消済` は除外）
  - **時間帯**: 夜間のみ（例: `18:00～21:00`, `17:00～21:00` のような夜間区分）※昼間予約は対象外

### 3.2 同期頻度・期間
- **頻度**: GitHub Actions cron で 30 分おきに実行（Kaderu と同じ）
- **対象期間**: 当月 + 翌月の予約（`--months 2`、Kaderu と同じ）

### 3.3 会場決定ルール（日付単位で集約）

| 予約されている部屋 | 登録会場 | venue_id |
|---|---|---|
| `さくら（和室）` のみ | `東🌸` | 6 |
| `さくら（和室）` + `かっこう（和室）` | `東全室` | 10 |
| `和室全室` | `東全室` | 10 |
| `かっこう（和室）` のみ | （運用対象外として完全スキップ、ログ出力のみ） | - |

### 3.4 practice_sessions 書き込みルール
- **対象組織**: `hokudai` 固定
- **時刻**: `start_time=18:00`, `end_time=21:00` 固定（東区民センターの夜間枠想定）
- **試合数**: `venues.default_match_count` を使用（東🌸=2、東全室=2）
- **定員**: `venues.capacity` を使用（東🌸=14、東全室=18）
- **新規作成**: `INSERT ... ON CONFLICT (session_date, organization_id) DO NOTHING`
- **既存がある場合の拡張**: Kaderu と同じ仕様
  - 既存セッションが `東🌸 (6)` で予約側が `東全室 (10)` → `東全室` に昇格 UPDATE
  - 既存セッションが `東全室 (10)` → 触らない
  - 既存セッションが Kaderu 系会場など → 触らない（1日に両センターの予約が併存するケースはユーザー運用で発生しない前提 = Q13）
- **venue_id が NULL の既存セッション**: 予約から算出した会場で補完 UPDATE（Kaderu と同じ）

### 3.5 取消（`取消済`）の扱い
- Kaderu と同じ挙動に揃える: 取消済はフィルタで除外するのみ。既存の `practice_sessions` レコードには一切触らない。
- 予約取消後の既存セッションは、運営者が手動で削除する運用とする。

### 3.6 エラー・境界条件
- サイト側一時エラー（`OutsideServiceTime.html` / `HttpClientError.html`）は失敗として扱い、次回 cron で再試行
- 過去日付はスキップ（Kaderu と同じ）
- `さくら` + `かっこう` + `和室全室` が同日に混在するケースは（現実には発生しない前提だが）`東全室` として扱う

## 4. 技術設計

### 4.1 全体アーキテクチャ

```
GitHub Actions (cron */30 * * * *)
   └─ node sync-higashi-reservations.js --months 2
        ├─ 子プロセス: node scrape-higashi-history.js --months 2
        │    └─ Playwright (chromium) で sapporo-community.jp にログイン
        │       → 申込履歴画面 (UserHistory.aspx) からDOM抽出
        │       → stdout に夜間予約 JSON を出力
        └─ pg で Render PostgreSQL に接続し practice_sessions を UPSERT
              → 以降、バックエンド側の既存パイプライン（伝助ページ作成・書き込み）が自動で走る
```

既存 Kaderu パイプライン（`sync-reservations.js` + `scrape-mypage.js`）の兄弟ファイルとして追加。
バックエンド（Spring Boot）側は**変更なし**。

### 4.2 追加ファイル

| ファイル | 内容 |
|---|---|
| `scripts/room-checker/scrape-higashi-history.js` | sapporo-community.jp 申込履歴スクレイパー |
| `scripts/room-checker/sync-higashi-reservations.js` | スクレイパー起動 + practice_sessions 同期 |
| `.github/workflows/sync-higashi-reservations.yml` | GitHub Actions ワークフロー（cron 30分） |
| `docs/features/higashi-community-center-sync/requirements.md` | 本ファイル |
| `docs/features/higashi-community-center-sync/implementation-plan.md` | 実装手順書 |

### 4.3 スクレイパー仕様 (`scrape-higashi-history.js`)

**入力**
- CLI: `--months <N>` (デフォルト 2)
- 環境変数: `SAPPORO_COMMUNITY_USER_ID`, `SAPPORO_COMMUNITY_PASSWORD`

**画面遷移フロー**（調査レポート §4 準拠）
1. `UserLogin.aspx` へ遷移
2. `#ctl00_cphMain_tbUserno` / `#ctl00_cphMain_tbPassword` に資格情報を入力
3. `#ctl00_cphMain_btnReg` クリック → メニュー画面
4. `#ctl00_cphMain_WucImgBtnHistory_imgbtnMain`（申込履歴・結果）クリック → `UserHistory.aspx`
5. 一覧テーブル `#ctl00_cphMain_gvView` から全ページを走査（`javascript:__doPostBack(..., 'Page$N')` でページング）

**行抽出ルール**
- `td` が7列の行のみデータ行として扱う（3列目/5列目が時刻フォーマット `^\d{1,2}:\d{2}$` で確認）
- `申込内容` に `札幌市東区民センター` を含む行のみ採用
- `状態=取消済` の行は除外（出力JSONに含めない）
- 和暦 `令和YY年MM月DD日（曜）` → 西暦 `YYYY-MM-DD` に変換
- 部屋名を `さくら` / `かっこう` / `和室全室` に正規化（`（和室）` は除去）
- **夜間フィルタ**: 開始時刻が 17:00 以降（Kaderu と同じく "夜間のみ" 出力）

**出力**（stdout に JSON 配列）
```json
[
  {
    "date": "2026-05-21",
    "room": "さくら",
    "status": "予約済",
    "startTime": "18:00",
    "endTime": "21:00",
    "rawContent": "札幌市東区民センターさくら（和室） 利用申込"
  }
]
```

**エラー処理**
- ログイン失敗・`OutsideServiceTime.html` / `HttpClientError.html` 検知で `process.exit(1)`
- リトライ無し（Kaderu と同じく次回 cron 再試行）
- `console.error` にのみ進捗ログを出し、stdout は JSON 専用に維持

### 4.4 同期スクリプト仕様 (`sync-higashi-reservations.js`)

**入力**
- CLI: `--months <N>` (デフォルト 2), `--dry-run`
- 環境変数: `SAPPORO_COMMUNITY_USER_ID`, `SAPPORO_COMMUNITY_PASSWORD`, `DATABASE_URL`

**定数**
```js
const ROOM_VENUE_MAP = {
  さくら: 6,       // 東🌸 (default_match_count=2, capacity=14)
  かっこう: null,  // 単独は運用対象外
  和室全室: 10,    // 東全室 (default_match_count=2, capacity=18)
};
const SAKURA_VENUE_ID = 6;        // 東🌸
const ALL_ROOM_VENUE_ID = 10;     // 東全室
```

**処理フロー**
1. `scrape-higashi-history.js` を子プロセス実行し JSON を取得
2. 日付ごとに部屋をグルーピング → 会場決定
   - `和室全室` 含む → `東全室 (10)`
   - `さくら` + `かっこう` 同日 → `東全室 (10)`
   - `さくら` のみ → `東🌸 (6)`
   - `かっこう` のみ → 警告ログ出力してスキップ
3. `hokudai` organization_id を取得
4. 各日付について `practice_sessions` を UPSERT:
   - 過去日付はスキップ
   - 既存セッション無し → `INSERT ... ON CONFLICT (session_date, organization_id) DO NOTHING`
     - `start_time=18:00`, `end_time=21:00` 固定
     - `total_matches=venues.default_match_count`, `capacity=venues.capacity`
     - `created_by=updated_by=0`（SYSTEM_USER_ID）
   - 既存あり + `venue_id=NULL` → 算出会場で UPDATE（venue_id + capacity）
   - 既存あり + `venue_id=東🌸(6)` + 算出 `東全室(10)` → `東全室` に昇格 UPDATE
   - 既存あり + `venue_id=東全室(10)` → 触らない（ダウングレード無し）
   - 既存あり + venue_id が Kaderu 系 (3,4,7,8,9,11) → 触らない（1日に併存しない前提 = Q13）
5. 実行結果サマリーを `console.log` に出力（created/expanded/skipped 件数）

**取消の扱い**
- スクレイパー段階で `取消済` が除外されるため、本スクリプトは取消を意識しない
- 既存セッションに対して削除処理は行わない

### 4.5 GitHub Actions ワークフロー (`sync-higashi-reservations.yml`)

`sync-kaderu-reservations.yml` を複製し、以下を変更:
- `name`: `Sync Higashi Community Center Reservations to Practice Sessions`
- `concurrency.group`: `higashi-reservation-sync`
- 実行コマンド: `node sync-higashi-reservations.js --months 2`
- 環境変数:
  - `SAPPORO_COMMUNITY_USER_ID`: `${{ secrets.SAPPORO_COMMUNITY_USER_ID }}` (新規)
  - `SAPPORO_COMMUNITY_PASSWORD`: `${{ secrets.SAPPORO_COMMUNITY_PASSWORD }}` (新規)
  - `DATABASE_URL`: `${{ secrets.KADERU_DATABASE_URL }}` (既存を流用 = Q18)

cron は Kaderu と同じ `*/30 * * * *`。両ジョブは独立した concurrency group なので競合しない。

### 4.6 新規 GitHub Secrets（ユーザー作業）

実装後、ユーザーが以下を GitHub リポジトリの Secrets に登録する:
- `SAPPORO_COMMUNITY_USER_ID`
- `SAPPORO_COMMUNITY_PASSWORD`

既存 `KADERU_DATABASE_URL` / Playwright セットアップはそのまま流用。

### 4.7 DB スキーマ変更
**なし**。既存 `practice_sessions` / `venues` テーブルをそのまま使用。
（venues マスタには既に `id=6 東🌸` / `id=10 東全室` が登録済み。）

### 4.8 バックエンド（Spring Boot）変更
**なし**。`practice_sessions` への INSERT/UPDATE のみで、既存の伝助ページ作成・書き込みパイプラインに自動で乗る。

## 5. 影響範囲

### 5.1 変更が必要な既存ファイル
- **なし**（既存ファイルの変更は伴わない。全て新規追加で完結）

### 5.2 既存機能への影響
- **Kaderu 同期**: 独立した concurrency group・別ワークフローなので影響なし
- **伝助ページ作成（`DensukePageCreateService`）**: 新規 `practice_sessions` を検出して自動で伝助ページを作成する（従来通りの挙動。これが本機能の狙い）
- **伝助書き込み（`DensukeWriteService`）**: 同様に自動で出欠を反映する（従来通りの挙動）
- **Render.com バックエンド**: 起動/停止や設定変更なし
- **既存 `practice_sessions` レコード**: 過去分・Kaderu 由来レコードには触れない

### 5.3 データ整合性
- Q13 の前提「同日に Kaderu と東区民センター両方の予約は取らない」が崩れると、Kaderu 由来セッションが東区民センター側同期でスキップされて伝助に反映されない可能性がある
  → 運用上の注意として実装手順書にメモ。発生時はログで検知可能
- 同一 venue_id が同日に重複する懸念なし（`(session_date, organization_id)` UNIQUE 制約で保護）

### 5.4 セキュリティ
- 資格情報は GitHub Secrets に保存、コードには平文で書かない
- スクレイパー自身は stdout に JSON 出力するが、資格情報はログ出力しない（`scrape-mypage.js` と同じ規律）

## 6. 設計判断の根拠

### 6.1 なぜ Spring Boot 側ではなく Node.js + GitHub Actions か
- 既存 Kaderu 実装と構成を揃えるため。Playwright の導入・保守を1箇所（`scripts/room-checker/`）に集約できる。
- Spring Boot コンテナ（Render.com）に Playwright を組み込むよりデプロイ・メモリ負担が軽い。

### 6.2 なぜ取消（`取消済`）を自動削除しないか
- 既存 Kaderu の設計ポリシーに揃えるため（運用一貫性）。
- 同日に参加者・試合記録が既に登録されている場合のデータ消失・FK違反リスクを避けるため。
- 運営者による手動削除で十分間に合う頻度という判断（取消はレアケース）。

### 6.3 なぜ `KADERU_DATABASE_URL` secret を流用するか
- Kaderu 同期・東区民センター同期ともに同じ Render PostgreSQL に書くため、secret を増やす理由がない。
- 将来分離する必要が出たら、その時点で共通名に昇格させれば良い。

### 6.4 なぜ venue マスタを変更しないか
- 既存 `venues` テーブルに `id=6 東🌸` / `id=10 東全室` が既に存在しているため。
- 将来「東区民センター他室（例: 洋室）」を使う運用が発生したら、その時点で venues に追加しマッピングを拡張する。

---

## 確定事項（ヒアリング済）

- **機能名**: `higashi-community-center-sync`
- **対象組織**: `hokudai` 固定（Kaderu と同じ）
- **実行方式**: 自動スケジュール実行のみ（UI 無し）
- **資格情報**: `SAPPORO_COMMUNITY_USER_ID` / `SAPPORO_COMMUNITY_PASSWORD` を環境変数で運用
- **部屋 → 会場マッピング（案）**:
  - `さくら（和室）` のみ → venue_id=6 `東🌸`
  - `さくら（和室）` + `かっこう（和室）` → venue_id=10 `東全室`
  - `和室全室` → venue_id=10 `東全室`
  - `かっこう（和室）` のみ → 運用対象外（未確定：どう扱うか）
- **アーキテクチャ**: Kaderu と兄弟構成。GitHub Actions + Node.js + Playwright + pg で PostgreSQL を直接UPSERT。
