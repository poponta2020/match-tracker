---
status: completed
---
# 伝助マルチ団体対応 要件定義書

## 1. 概要

### 目的
伝助（densuke.biz）との連携機能を、わすらもち会だけでなく北大かるた会でも利用可能にする。各月に団体ごとの伝助URLを登録し、双方向同期・対戦組み合わせ・結果入力など、わすらもち会で実現している全機能を北大かるた会でも同等に使えるようにする。

### 背景・動機
- わすらもち会と北大かるた会はそれぞれ別の伝助を使って練習参加管理をしている
- 現在のシステムは月に1つの伝助URLしか登録できない構造（`densuke_urls` テーブルの `(year, month)` ユニーク制約）
- 北大かるた会の練習管理もアプリに統合し、手動管理の手間を削減したい

## 2. ユーザーストーリー

### 対象ユーザー
- **SUPER_ADMIN:** 全団体の伝助URLを管理可能
- **ADMIN:** 自団体の伝助URLのみ管理可能
- **PLAYER:** 所属団体の練習情報を閲覧・参加登録

### ユーザーの目的
- 北大かるた会でも伝助の出欠情報を自動で取り込み、対戦組み合わせ・結果入力を行いたい
- わすらもち会と北大かるた会の練習情報を1つのアプリで統合管理したい

### 利用シナリオ
1. 北大かるた会のADMINが伝助管理画面を開く → 自団体のURL入力欄のみ表示される
2. 伝助URLを入力して保存 → 団体別に同期実行ボタンを押す
3. 伝助から参加情報が取り込まれ、練習セッションが団体に紐づいて作成される
4. 対戦組み合わせ・結果入力も団体別にフィルタされて表示される
5. SUPER_ADMINは伝助管理画面で両団体のURL入力欄が並んで表示される

## 3. 機能要件

### 3.1 画面仕様

#### 伝助管理画面（DensukeManagement）
- ログインユーザーが管理可能な団体ごとに、以下のブロックを並べて表示する：
  - 団体名ヘッダー（団体カラー表示）
  - URL入力欄 + 「URL保存」ボタン
  - 「同期実行」ボタン（団体ごとに個別実行）
  - 書き込みステータス表示（書き込み待ち件数、最終書き込み試行/成功日時、エラー）
- **ADMIN:** 自団体（`adminOrganizationId`）のブロックのみ表示
- **SUPER_ADMIN:** 全団体のブロックを表示

#### 練習一覧（PracticeList）
- 既存のまま（ユーザーの所属団体でフィルタ済み）
- 両団体に所属するユーザーは同じカレンダー上に両団体の練習日が表示される

#### 対戦組み合わせ（PairingGenerator）
- ユーザーの所属団体でフィルタを追加
- 同じ日に両団体の練習がある場合、所属団体のセッションのみ表示

#### 結果入力（BulkResultInput / MatchResultsView）
- ユーザーの所属団体でフィルタを追加
- 同じ日に両団体の練習がある場合、所属団体のセッションのみ表示

### 3.2 ビジネスルール

#### 伝助同期
- 60秒ごとの自動同期スケジューラーで、登録されている全団体の伝助URLを順に処理する
- 読み込み（伝助→アプリ）：団体ごとの伝助URLからスクレイピングし、該当団体のセッションとして作成
- 書き込み（アプリ→伝助）：`dirty=true` の参加者を該当団体の伝助に書き戻す
- 手動同期は団体ごとに個別実行

#### 未登録者通知
- 伝助に未登録の名前があった場合：
  - **ADMIN:** 自団体の未登録者のみ通知
  - **SUPER_ADMIN:** 全団体の未登録者を通知
- 通知手段はアプリ内通知 + Web Push（既存と同じ）

#### 抽選処理
- `LotteryService.executeLottery()` に団体フィルタを追加し、団体別にセッションを取得して抽選実行
- 抽選結果通知も団体別にフィルタ

#### 権限チェック
- ADMINは自団体の伝助URLのみCRUD可能
- SUPER_ADMINは全団体の伝助URLをCRUD可能

## 4. 技術設計

### 4.1 API設計

#### 既存API変更

| エンドポイント | 変更内容 |
|---------------|---------|
| `GET /api/practice-sessions/densuke-url` | `organizationId` クエリパラメータ追加（必須） |
| `PUT /api/practice-sessions/densuke-url` | リクエストボディに `organizationId` 追加、ADMIN権限チェック |
| `POST /api/practice-sessions/sync-densuke` | `organizationId` クエリパラメータ追加、ADMIN権限チェック |
| `GET /api/practice-sessions/densuke-write-status` | `organizationId` クエリパラメータ追加（団体別ステータス返却） |

#### 対戦・結果API変更

| エンドポイント | 変更内容 |
|---------------|---------|
| `GET /api/match-pairings/by-date` | ユーザーの所属団体でフィルタ |
| `GET /api/matches/by-date` | ユーザーの所属団体でフィルタ |

### 4.2 DB設計

#### densuke_urls テーブル変更

```sql
-- organization_id カラム追加
ALTER TABLE densuke_urls ADD COLUMN organization_id BIGINT REFERENCES organizations(id);

-- 既存データをわすらもち会にマイグレーション
UPDATE densuke_urls SET organization_id = (SELECT id FROM organizations WHERE code = 'wasura');

-- NOT NULL制約追加
ALTER TABLE densuke_urls ALTER COLUMN organization_id SET NOT NULL;

-- ユニーク制約変更
ALTER TABLE densuke_urls DROP CONSTRAINT densuke_urls_year_month_key;
ALTER TABLE densuke_urls ADD CONSTRAINT densuke_urls_year_month_org_key UNIQUE (year, month, organization_id);
```

### 4.3 フロントエンド設計

#### DensukeManagement.jsx
- ユーザーの管理可能団体リストを取得（ADMIN: 自団体のみ、SUPER_ADMIN: 全団体）
- 各団体ごとにURL入力・同期・ステータス表示のブロックをレンダリング
- API呼び出し時に `organizationId` を付与

#### PairingGenerator.jsx / BulkResultInput.jsx / MatchResultsView.jsx
- バックエンドでのフィルタに依存（API側で所属団体フィルタを適用）

#### practices.js（APIクライアント）
- `getDensukeUrl(year, month, organizationId)`
- `saveDensukeUrl(year, month, url, organizationId)`
- `syncDensuke(year, month, organizationId)`
- `getDensukeWriteStatus(organizationId)`

### 4.4 バックエンド設計

#### エンティティ変更
- `DensukeUrl.java` — `organizationId` フィールド追加

#### リポジトリ変更
- `DensukeUrlRepository` — `findByYearAndMonthAndOrganizationId()` 追加
- `PracticeSessionRepository` — `findByYearAndMonthAndOrganizationId()` 追加（抽選用）

#### サービス変更
- `PracticeSessionService` — `saveDensukeUrl()`, `getDensukeUrl()` に `organizationId` パラメータ追加、ADMIN権限チェック
- `DensukeImportService` — 全団体の伝助URLを取得してループ処理、セッション作成時に `organizationId` 設定、未登録者通知を団体別に送信
- `DensukeWriteService` — 全団体の伝助URLを取得してループ処理
- `LotteryService` — `executeLottery()` に `organizationId` パラメータ追加、団体別セッション取得
- `MatchPairingService` / `MatchService` — 日付検索時にユーザー所属団体フィルタ追加

#### コントローラー変更
- `PracticeSessionController` — 伝助関連エンドポイントに `organizationId` パラメータ追加
- `MatchPairingController` — 所属団体フィルタ追加
- `MatchController` — 所属団体フィルタ追加
- `LotteryController` — 抽選結果通知に団体フィルタ追加

## 5. 影響範囲

### 変更が必要な既存ファイル

**バックエンド:**
- `DensukeUrl.java` — `organizationId` カラム追加
- `DensukeUrlRepository.java` — クエリ変更
- `PracticeSessionService.java` — 伝助URL CRUD に団体パラメータ追加
- `PracticeSessionController.java` — API引数変更
- `DensukeImportService.java` — 団体別ループ処理、通知の団体フィルタ
- `DensukeWriteService.java` — 団体別ループ処理
- `DensukeSyncScheduler.java` — 全団体分をループ
- `LotteryService.java` — 団体別セッション取得
- `LotteryController.java` — 抽選結果通知の団体フィルタ
- `MatchPairingController.java` — 所属団体フィルタ
- `MatchController.java` — 所属団体フィルタ
- `PracticeSessionRepository.java` — 団体別クエリ追加

**フロントエンド:**
- `DensukeManagement.jsx` — 団体別UI
- `practices.js` — API呼び出し変更
- `PairingGenerator.jsx` — 団体フィルタ
- `BulkResultInput.jsx` — 団体フィルタ
- `MatchResultsView.jsx` — 団体フィルタ

**DBマイグレーション:**
- `densuke_urls` テーブルへの `organization_id` 追加スクリプト

### 既存機能への影響
- 既存のわすらもち会の伝助連携は、マイグレーションにより `organization_id` が設定されるため、動作に変更なし
- 既存APIの呼び出し元（フロントエンド）は `organizationId` パラメータの追加が必要
- 抽選処理の団体フィルタ追加により、わすらもち会の抽選も正確に団体スコープで実行されるようになる（現状は偶然1団体のみのため問題が顕在化していなかった）

## 6. 設計判断の根拠

### `densuke_urls` に `organization_id` を追加する方式を選択
- 既存のテーブル構造を最小限の変更で拡張できる
- `densuke_member_mappings` と `densuke_row_ids` は `densuke_url_id` を外部キーとしているため、自動的に団体スコープが効く

### 対戦組み合わせ・結果入力のフィルタはバックエンド側で適用
- フロントエンドでのフィルタは既に練習一覧で実績があるが、対戦・結果は日付ベースのAPIのため、バックエンドでユーザーの所属団体フィルタを適用する方が安全

### 未登録者通知を団体スコープに限定
- ADMINは自団体の管理責任のみ持つため、他団体の未登録者を通知する必要がない
- SUPER_ADMINは全体を把握する必要があるため全団体分を通知
