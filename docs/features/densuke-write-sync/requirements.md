---
status: completed
---

# 伝助双方向同期（densuke-write-sync）要件定義書

## 1. 概要

### 目的
アプリ（Match Tracker）から伝助への書き込みを実装し、アプリと伝助を双方向に同期させる。

### 背景・動機
現在、出欠管理は「伝助 → アプリ」の一方向同期（読み取り専用）のみ。出欠管理のアプリへの移行期において、選手がどちらで入力してもよいよう双方向同期が必要。また、抽選・キャンセル・繰り上げ等のアプリ側の処理結果も伝助に反映したい。

---

## 2. ユーザーストーリー

### 対象ユーザー
- **選手（PLAYER）**：アプリまたは伝助のどちらでも出欠入力できる
- **管理者（ADMIN/SUPER_ADMIN）**：アプリでの抽選・キャンセル・繰り上げ処理が自動的に伝助に反映される

### ユーザーの目的
- 選手は使い慣れた方（アプリでも伝助でも）で出欠を入力できる
- 管理者は伝助の手動更新作業から解放される
- 抽選後の当選・キャンセル・繰り上げが伝助にも反映され、会員が伝助を見ても現状が把握できる

### 利用シナリオ
1. 選手Aがアプリで参加希望（PENDING）→ 60秒以内に伝助にも○が書き込まれる
2. 選手Bが伝助で○を入力 → 60秒以内にアプリにも参加希望（PENDING）として登録される
3. 管理者がアプリで抽選実行 → 当選者は伝助に○、キャンセル待ちは△が自動反映
4. 当選者Cがキャンセル → アプリでCANCELLED → 伝助に×が自動反映
5. 繰り上げ対象Dが応答待ち（OFFERED）→ 伝助に△が反映

---

## 3. 機能要件

### 3.1 ステータスマッピング（アプリ → 伝助）

| アプリ（ParticipantStatus） | 伝助 | 伝助の値 |
|---|---|---|
| `PENDING`（参加希望） | ○ | 3 |
| `WON`（当選） | ○ | 3 |
| `WAITLISTED`（キャンセル待ち） | △ | 2 |
| `OFFERED`（繰り上げ応答待ち） | △ | 2 |
| `CANCELLED`（当選後キャンセル） | × | 1 |
| `DECLINED`（繰り上げ辞退） | × | 1 |
| `WAITLIST_DECLINED`（キャンセル待ち辞退） | × | 1 |
| アプリ未登録（伝助にも未登録） | 書き込みなし | - |

### 3.2 同期タイミング
- **60秒ごと**のバッチ処理（既存スケジューラーに統合）
- 同一サイクル内の実行順序：**① アプリ→伝助（書き込み）→ ② 伝助→アプリ（読み取り）**

### 3.3 競合解決（dirtyフラグ）
`practice_participants` に `dirty BOOLEAN` カラムを追加して「最後に誰が変更したか」を追跡する。

| 操作 | dirtyの変化 |
|---|---|
| 選手/管理者がアプリで操作 | → `true` |
| アプリ→伝助への書き込み成功 | → `false` |
| 伝助→アプリ同期で追加/更新 | → `false` |

**競合時の動作：**
- **アプリ→伝助（書き込み）**：`dirty=true` の参加者のみ伝助に書き込む
- **伝助→アプリ（削除判定）**：`dirty=true` の参加者は削除しない（アプリ側の変更を優先）
  - `dirty=false` かつ伝助に存在しない → 従来通り削除
  - `dirty=true` かつ伝助に存在しない → 削除しない（次サイクルで①が書き込む）

**60秒以内に双方で逆操作した場合：アプリ側が優先**（実用上ほぼ発生しない）

### 3.4 伝助メンバー管理

#### 初回登録（アプリの選手が伝助にいない場合）
- アプリの選手が参加登録（`dirty=true`）かつ当月の伝助にメンバーIDが未登録の場合、**自動で伝助に新規メンバーとして追加**する
- メンバーIDを `densuke_member_mappings` テーブルに保存

#### 月ごとのURLローテーション
- 伝助は月ごとに別URLが登録される（`densuke_urls` テーブル）
- メンバーIDは URL単位で管理する（同じ選手でも月が違えば別のメンバーIDになる）

### 3.5 抽選後の挙動
- 抽選済みセッションも **アプリ→伝助の書き込みは継続する**（WON=○、WAITLISTED=△、CANCELLED=×）
- 抽選済みセッションへの **伝助→アプリの書き込みはスキップ**（既存動作を維持）

### 3.6 エラー表示（管理画面）
- `DensukeManagement.jsx` に「伝助への書き込み状況」を追加表示する
  - 最終書き込み試行日時
  - 最終書き込み成功日時
  - 直近のエラーメッセージ（あれば）
  - 書き込み待ち（dirty=true）の参加者数

### 3.7 エラー時の挙動
- 書き込みに失敗した参加者は `dirty=true` のまま残す → 次の60秒サイクルで自動リトライ
- 管理者への通知は行わない（管理画面でエラー状況を確認できれば十分）

---

## 4. 技術設計

### 4.1 API設計

#### 新規エンドポイント

| エンドポイント | メソッド | 権限 | 説明 |
|---|---|---|---|
| `/api/practice-sessions/densuke-write-status` | GET | ADMIN+ | 書き込み状況（最終実行日時・エラー・dirty件数）を返す |

#### レスポンス（DensukeWriteStatusDto）
```json
{
  "lastAttemptAt": "2026-03-29T12:00:00",
  "lastSuccessAt": "2026-03-29T11:59:00",
  "errors": ["選手ID=123: 伝助への接続に失敗しました"],
  "pendingCount": 2
}
```

### 4.2 DB設計

#### 新規テーブル①：`densuke_member_mappings`
```sql
CREATE TABLE densuke_member_mappings (
  id                BIGSERIAL PRIMARY KEY,
  densuke_url_id    BIGINT NOT NULL REFERENCES densuke_urls(id),
  player_id         BIGINT NOT NULL REFERENCES players(id),
  densuke_member_id VARCHAR(50) NOT NULL,
  created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
  UNIQUE (densuke_url_id, player_id)
);
```

#### 新規テーブル②：`densuke_row_ids`
伝助の各行（日付×試合番号）の `join-{id}` を保存する。

```sql
CREATE TABLE densuke_row_ids (
  id               BIGSERIAL PRIMARY KEY,
  densuke_url_id   BIGINT NOT NULL REFERENCES densuke_urls(id),
  densuke_row_id   VARCHAR(50) NOT NULL,
  session_date     DATE NOT NULL,
  match_number     INT NOT NULL,
  created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
  UNIQUE (densuke_url_id, session_date, match_number)
);
```

行IDの取得方法：書き込み時に対象メンバーの編集フォーム（POST `list?cd=...` with `mi`）を取得して `join-{id}` フィールドをパースし、DBに保存（初回のみ、以降はDBから再利用）

#### 既存テーブル変更：`practice_participants`
```sql
ALTER TABLE practice_participants
  ADD COLUMN dirty BOOLEAN NOT NULL DEFAULT TRUE;
```

- デフォルト `TRUE`：既存データは全て「アプリ側未書き込み」扱いとして初期化
- 既存データへの影響：初回同期サイクルで全員分を伝助に書き込む（URLが登録されている月のみ）

### 4.3 バックエンド設計

#### 新規クラス

| クラス | パッケージ | 役割 |
|---|---|---|
| `DensukeMemberMapping` | `entity/` | densuke_member_mappingsエンティティ |
| `DensukeRowId` | `entity/` | densuke_row_idsエンティティ |
| `DensukeMemberMappingRepository` | `repository/` | densuke_member_mappingsのCRUD |
| `DensukeRowIdRepository` | `repository/` | densuke_row_idsのCRUD |
| `DensukeWriteService` | `service/` | アプリ→伝助の書き込みロジック |
| `DensukeWriteStatusDto` | `dto/` | 書き込み状況レスポンスDTO |

#### DensukeWriteService の処理フロー

```
1. 当月・翌月でDensuke URLが登録されているセッションを取得
2. dirty=true の参加者を取得（セッションIDで絞り込み）
3. dirty=true の参加者をプレイヤー×URLでグループ化
4. 各（プレイヤー, URL）に対して：
   a. densuke_member_mappings を検索
      → なければ POST insert でメンバー作成、miを保存
   b. densuke_row_ids を検索
      → なければ POST list?mi={mi} で編集フォームを取得し join-{id} を抽出・保存
   c. 当該URLの全セッション×このプレイヤーのステータスを取得
   d. join-{id} ごとに値（3/2/1/0）を決定
   e. POST regist に全 join-{id} 値を送信
   f. 成功したら対象参加者の dirty=false に更新
      失敗したらエラーを記録（dirty=true のまま → 次サイクルでリトライ）
5. 書き込み状況（最終実行日時・エラー）をメモリに保持
```

#### 修正クラス

| クラス | 変更内容 |
|---|---|
| `DensukeSyncScheduler` | スケジューラーの処理順を ①DensukeWriteService → ②DensukeImportService に変更 |
| `DensukeImportService` | 削除判定に dirty フラグを追加（dirty=true の参加者は削除しない）。追加時に dirty=false を設定 |
| `PracticeParticipantService` | 参加登録・キャンセル・ステータス変更時に dirty=true を設定 |
| `PracticeSessionController` | `GET /densuke-write-status` エンドポイント追加 |

### 4.4 フロントエンド設計

#### 変更ファイル：`DensukeManagement.jsx`
- 既存の「同期結果」表示エリアの下に「伝助への書き込み状況」セクションを追加
- 表示項目：最終書き込み試行日時、最終書き込み成功日時、エラーメッセージ、書き込み待ち件数
- 既存の `practices.js` に `getDensukeWriteStatus(year, month)` を追加

---

## 5. 影響範囲

### 変更が必要な既存ファイル

| ファイル | 変更内容 |
|---|---|
| `DensukeSyncScheduler.java` | DensukeWriteService の呼び出しを追加（①書き込み → ②読み取りの順に変更） |
| `DensukeImportService.java` | 削除判定に dirty フラグチェックを追加、追加時に dirty=false を設定 |
| `PracticeParticipantService.java` | 全ステータス変更メソッドで dirty=true を設定 |
| `PracticeSessionController.java` | `/densuke-write-status` エンドポイント追加 |
| `DensukeManagement.jsx` | 書き込み状況UIの追加 |
| `practices.js`（APIクライアント） | `getDensukeWriteStatus()` 追加 |

### 既存機能への影響

| 機能 | 影響 |
|---|---|
| 伝助→アプリ同期（既存） | 削除判定ロジックに dirty フラグ条件が追加されるが、動作の基本は変わらない |
| 抽選済みセッションのスキップ（既存） | 変わらず維持（伝助→アプリ方向のみスキップ。アプリ→伝助は継続） |
| 練習参加登録・キャンセル処理（既存） | dirty=true を設定する処理が追加されるが、既存ロジックへの変更なし |
| DB: practice_participants | dirty カラム追加。既存レコードはデフォルト TRUE で初期化 |

---

## 6. 設計判断の根拠

| 判断 | 理由 |
|---|---|
| 同期サイクル内で①書き込み→②読み取りの順にする | ①を先に実行することで、アプリ登録済みの参加者が伝助に存在しない状態で②が走って誤削除されるのを防ぐ |
| dirtyフラグで競合解決 | タイムスタンプ比較よりシンプルで実装ミスが少ない。60秒以内の双方同時操作というエッジケースはアプリ優先で許容 |
| densuke_member_mappingsはURL単位で管理 | 伝助は月ごとに別URLのため、同じ選手でもメンバーIDが月ごとに異なる |
| densuke_row_ids をDBに保存してキャッシュ | 毎回Densuke編集フォームを取得するとHTTPリクエストが増加。初回取得後はDBから再利用 |
| 書き込み失敗は通知せずリトライのみ | dirty=true が残るので次サイクルで自然にリトライされる。管理画面で状況確認できれば十分 |
