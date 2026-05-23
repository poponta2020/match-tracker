---
status: completed
---
# 対戦一覧の会場・試合番号表示 要件定義書

## 1. 概要

対戦結果一覧画面（`/matches`, `/matches?playerId=...`）と試合詳細画面（`/matches/:id`）の各行・各画面に「会場」と「〇試合目（試合番号）」を追加表示する機能。

現状は日付（M/D）、対戦相手名、メモアイコン、お手付き数、勝敗・点差を1行で表示している。これに会場名と試合番号を加えることで、過去の対戦を振り返るときに「あの試合はどこでやったか」を思い出しやすくする。

## 2. ユーザーストーリー

### 対象ユーザー
- 全選手（PLAYER ロール）
- 他選手の対戦一覧を閲覧するユーザー（メンター含む）

### ユーザーの目的
- 過去の対戦リストを見るときに「あの試合はどこでやったか」を思い出したい
- 試合番号がわかることで、同日内の試合順を一目で把握できる

### 利用シナリオ
- 自分の対戦一覧（`/matches`）を眺めて、過去の試合を振り返るとき
- 他選手の対戦一覧（`/matches?playerId=...`）を眺めて、相手の対戦履歴を把握するとき
- メンターがメンティーの対戦履歴を確認するとき

### 表示範囲
- 自分・他選手の対戦一覧、両方で同じ見た目で表示する
- 試合詳細画面（MatchDetail）にも会場名・試合番号を表示する（試合番号は既存表示あり、会場名のみ新規追加）

## 3. 機能要件

### 3.1 表示書式

各対戦行の先頭部分を以下の書式に変更する。

**通常時（会場情報あり）:**
```
5/23 あかなら・すずらん(2)
```
- 日付（M/D）・会場名・`(試合番号)` をスペース区切りで連結
- 会場名は括弧なしで表示
- 試合番号は会場名の直後に `(N)` の形で数字のみ
- 全体を現状の日付ラベルと同じスタイル（薄字・小さい字 = `text-xs text-[#9ca3af]`）で表示

**会場名が長く1行に収まらない場合:**
- 会場名を末尾「...」で省略する（truncate）
- 対戦相手名のスペースを優先する

**会場情報が取得できない場合（古いデータで backfill 不可・PracticeSession が削除された等）:**
```
5/23 (2)
```
- 会場名部分をスキップし、日付と試合番号のみ表示
- 試合番号は常に表示される

### 3.2 表示位置

- **対戦結果一覧画面 (MatchList.jsx, `/matches`)**
  - 各行の左端（現状の日付部分）を新しい書式に置換
  - 1行レイアウトを維持（2段にはしない）
  - 文字スタイル: 現状の日付と同じ `text-xs text-[#9ca3af]`
- **試合詳細画面 (MatchDetail.jsx, `/matches/:id`)**
  - 「詳細情報」セクションの grid に「会場」カードを追加
  - 既存の「試合日」「試合番号」と並ぶ第3要素として配置
  - 試合番号は既存表示（"第N試合"）を維持

### 3.3 試合番号の意味

- `Match.matchNumber`（その日の第N試合）をそのまま表示
- フロントエンド側での計算は行わない

## 4. 技術設計

### 4.1 DB設計

#### `matches` テーブルへのカラム追加

| カラム名 | 型 | NULL | デフォルト | 説明 |
|---------|---|------|----------|------|
| `venue_id` | BIGINT | YES | NULL | 試合が行われた会場の ID。`venues.id` を参照 |

- FK 制約: `venue_id` → `venues.id`（ON DELETE SET NULL）
- インデックス: `idx_matches_venue` on `(venue_id)`

#### マイグレーション SQL

新規ファイル: `database/add_venue_id_to_matches.sql`

```sql
-- 1) カラム追加
ALTER TABLE matches ADD COLUMN venue_id BIGINT NULL;
ALTER TABLE matches ADD CONSTRAINT fk_matches_venue
    FOREIGN KEY (venue_id) REFERENCES venues(id) ON DELETE SET NULL;
CREATE INDEX idx_matches_venue ON matches(venue_id);

-- 2) backfill: 試合参加者（player1 / player2）が同日・同試合番号に active 参加した
--    practice_sessions の venue_id を集約し、一意であれば採用
--    match_number IS NULL は legacy データ（全試合参加）を拾うために含める
UPDATE matches m SET venue_id = subq.venue_id
FROM (
  SELECT m.id AS match_id, MIN(ps.venue_id) AS venue_id
  FROM matches m
  JOIN practice_participants pp ON pp.player_id IN (m.player1_id, m.player2_id)
  JOIN practice_sessions ps ON ps.id = pp.session_id
  WHERE ps.session_date = m.match_date
    AND (pp.match_number = m.match_number OR pp.match_number IS NULL)
    AND ps.venue_id IS NOT NULL
    AND pp.status IN ('WON', 'PENDING')
    AND m.venue_id IS NULL
  GROUP BY m.id
  HAVING COUNT(DISTINCT ps.venue_id) = 1
) subq
WHERE m.id = subq.match_id;

-- 3) backfill: 同日の練習会場が一意であれば採用（複数会場が混在する日は NULL のまま）
UPDATE matches m SET venue_id = subq.venue_id
FROM (
  SELECT ps.session_date, MIN(ps.venue_id) AS venue_id
  FROM practice_sessions ps
  WHERE ps.venue_id IS NOT NULL
  GROUP BY ps.session_date
  HAVING COUNT(DISTINCT ps.venue_id) = 1
) subq
WHERE m.match_date = subq.session_date
  AND m.venue_id IS NULL;
```

### 4.2 venue_id 決定ロジック（新規登録時）

`MatchService` の create / upsert メソッド内で、Match エンティティ生成後 `matchRepository.save()` 直前に以下のロジックで venue_id を解決する。

**優先順位:**
1. **試合参加者（簡易登録は `request.playerId`、詳細登録は `player1Id` / `player2Id`）が同日・同試合番号に active 参加（`status IN ('WON','PENDING')`）した practice_session の venue_id を集約**
   - 参加者全員の venue を集めて重複排除し、結果が一意であれば採用
   - 結果が複数（参加者が別会場に参加）であれば、誤割り当てを避けるため次のフォールバックに進む
   - `createdBy` ではなく試合参加者を基準にするのは、ADMIN が代理登録するケースで管理者の参加会場が誤って入るリスクを排除するため
   - `match_number` でも絞るのは、同日複数会場で選手が両方に参加している場合に、対象試合と無関係の参加会場が混ざって venue_id が一意決定できなくなるのを防ぐため。`match_number IS NULL`（全試合参加を意味する legacy データ）も対象に含める
   - キャンセル系（`CANCELLED` / `WAITLISTED` / `OFFERED` / `DECLINED` / `WAITLIST_DECLINED`）は実際に試合をしていない参加履歴のため除外
2. **同日に練習が行われた venue_id（同日のすべての practice_sessions の venue_id が一意であれば採用）**
   - 同日に複数会場で練習が行われていた場合は採用せず NULL のまま（誤った会場を割り当てるリスクを回避）
   - 「同日複数会場」は現状想定されない運用だが、将来的な安全性のためにセーフティを設ける
3. **該当なし → venue_id = NULL**（表示時は「5/23 (2)」になる）

実装は `MatchService` 内のプライベートヘルパーメソッド（例: `resolveVenueId(LocalDate matchDate, Long createdBy)`）に切り出し、create系メソッドから呼び出す。

### 4.3 API設計

#### MatchDto への追加フィールド

```java
private Long venueId;       // 会場ID（NULL可）
private String venueName;   // 会場名（NULL可、表示用）
```

- `venueName` は `MatchService` の enrichment メソッド（`enrichMatchesWithPlayerNames` 等）で venue 取得時に設定
- N+1 を避けるため、複数の Match を取得するメソッドではバッチで venue を取得

#### MatchCreateRequest

- **変更なし**。クライアントは venue を明示指定しない（バックエンドで自動決定）

#### 影響する API エンドポイント（venueId/venueName が追加される）

- `GET /api/matches/{id}`
- `GET /api/matches/player/{playerId}`
- `GET /api/matches/player/{playerId}/period`
- `GET /api/matches/player/{playerId}/by-rank`（既に MatchDto を返す場合）

既存クライアントは新フィールドを無視できるため後方互換あり。

### 4.4 バックエンド設計

#### 変更ファイル
| ファイル | 変更内容 |
|--------|---------|
| `entity/Match.java` | `venueId: Long` フィールド追加（`@Column(name = "venue_id")`） |
| `dto/MatchDto.java` | `venueId`, `venueName` フィールド追加。`fromEntity()` の更新 |
| `service/MatchService.java` | `resolveVenueId()` メソッド追加。create / upsert で呼ぶ。enrichment メソッドで venueName を解決 |
| `repository/PracticeSessionRepository.java` | 必要に応じてクエリメソッド追加（例: backfill 用ロジックの実装サポート） |
| `repository/PracticeParticipantRepository.java` | 必要に応じてクエリメソッド追加 |
| `repository/VenueRepository.java` | バッチ取得用クエリの追加（既存に `findAllByIdIn` 等がなければ） |

#### 変更しないファイル
- `dto/MatchCreateRequest.java` — venue 入力は不要
- `controller/MatchController.java` — エンドポイント定義は変えない

### 4.5 フロントエンド設計

#### MatchList.jsx の変更
- 既存の `formatDate()` を使った日付ラベル部分を以下に置換:
  ```jsx
  // venueName と matchNumber を組み合わせて表示
  // venueName あり: `${formatDate(...)} ${venueName}(${matchNumber})`
  // venueName なし: `${formatDate(...)} (${matchNumber})`
  ```
- 文字スタイルは現状維持（`text-xs text-[#9ca3af]`）
- 幅は `w-12` の固定幅から可変に変更（会場名が入るため `truncate` 対応）

#### MatchDetail.jsx の変更
- 「詳細情報」セクションの grid (`grid-cols-1 md:grid-cols-2`) を `grid-cols-1 md:grid-cols-3` に変更し、「会場」カードを追加
- 会場情報がない場合は「—」または「(未設定)」を表示

### 4.6 設計判断の根拠

| 判断 | 採用案 | 理由 |
|-----|------|------|
| Match に venue_id を持つか動的取得か | Match に持つ | クエリがシンプル・高速。N+1 を避けやすい。将来の編集・統計拡張が容易 |
| 表示レイアウト | 1行に詰め込み | ユーザー要望通りシンプル。会場名は truncate で対応 |
| venue_id 決定ロジック | PracticeParticipant → PlayerOrganization のフォールバック | M2M 所属を考慮しつつ、最も精度の高い `参加実績` を優先 |
| backfill | マイグレーション SQL 内で実施 | CLAUDE.md の DB マイグレーションルールに従い、ALTER と同じファイルで完結 |
| MatchDetail への適用 | grid 3 カラム化 | 既存の「試合日」「試合番号」と並ぶ自然な追加 |

## 5. 影響範囲

### 5.1 変更が必要な既存ファイル

#### バックエンド
- `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/Match.java` — `venueId` フィールド追加
- `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/MatchDto.java` — `venueId`, `venueName` フィールド追加・`fromEntity()` 更新
- `karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchService.java` — `resolveVenueId()` 追加・create系で呼出・enrichment 拡張
- `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/PracticeParticipantRepository.java` — クエリ追加（必要に応じて）
- `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/VenueRepository.java` — バッチ取得用（必要に応じて）

#### フロントエンド
- `karuta-tracker-ui/src/pages/matches/MatchList.jsx` — 日付ブロックを書式変更
- `karuta-tracker-ui/src/pages/matches/MatchDetail.jsx` — 詳細情報 grid に会場追加

#### DB
- `database/<番号>_add_venue_id_to_matches.sql` — 新規SQLファイル（ALTER + backfill）

### 5.2 既存機能への影響

| 機能 | 影響 |
|-----|------|
| 試合登録（フォーム） | フォーム自体は変更なし。バックエンドで venue_id 自動決定 |
| 試合更新（フォーム） | 影響なし（venue_id は不変） |
| 試合一覧の検索・フィルタ | 影響なし（venue は表示のみ） |
| 統計（rankStatistics） | 影響なし（venue 関連の集計は今回スコープ外） |
| ペアリング自動マッチング | 影響なし（MatchService.create を経由するため自動で venue_id がセットされる） |
| Densuke import | 影響なし（MatchService.create を経由する） |
| MatchDetail | 「会場」カード追加（grid 3 カラム化） |
| MatchEdit (編集画面) | 影響なし（venue は表示・編集対象外） |
| メンター閲覧 | 影響なし（同じ MatchDto を使う） |

### 5.3 API・DBスキーマの互換性
- **API**: MatchDto に新フィールドが追加されるが、既存フィールドは保持。既存クライアントは新フィールドを無視できる → 後方互換あり
- **DB**: `venue_id` は NULL 許容で追加。既存データは保持される。backfill 失敗ケース（PracticeSession 無し等）は NULL のまま

### 5.4 テストへの影響

- `MatchServiceTest`: create系メソッドのテストで venue_id が期待値どおりセットされるか追加検証
- `MatchListTest`: 表示書式の変更を確認
- 既存テストの fixture で venue_id を期待値に追加する必要があるかも

### 5.5 本番DB適用

CLAUDE.md の **DBマイグレーション適用ルール** に従う:
1. PR に SQL ファイルが含まれていることを明示
2. 本番DB（Render PostgreSQL）に psql で適用
3. 適用後 `\d matches` で `venue_id` カラム反映を確認
4. Render アプリの再起動が必要か検討

## 6. 設計判断の根拠

| # | 判断ポイント | 採用案 | 検討した代替案 | 採用理由 |
|---|------------|------|--------------|---------|
| 1 | 会場情報の保持方法 | Match に venue_id カラム追加 | PracticeSession 経由で動的取得（join） | クエリがシンプルで高速。N+1 リスク低減。将来の編集・統計拡張に対応しやすい |
| 2 | 表示レイアウト | 1行詰め込み（5/23 会場名(N)） | 2段組レイアウト | ユーザー要望に従い視覚的にコンパクト。会場名 truncate で長さ対応 |
| 3 | 会場名と試合番号の書式 | `5/23 あかなら・すずらん(2)`（括弧は試合番号のみ、スペース区切り） | `5/23 (会場名) N試合目` | ユーザー要望通り。よりコンパクト |
| 4 | 長い会場名の処理 | 末尾「...」で省略 | 対戦相手名を省略 / 2段折返し | 対戦相手の視認性を維持。truncate で自然 |
| 5 | 会場情報なし時の表示 | 「5/23 (2)」（会場のみスキップ） | 完全に何も追加表示しない / プレースホルダー | 試合番号は常に有用な情報。スペース詰めで自然 |
| 6 | venue_id 決定ロジック | 試合参加者（player1 / player2）の同日・同試合番号 active 参加 venue が一意なら採用 → 同日一意 venue にフォールバック | createdBy の参加 venue を採用 / 全ステータス / match_number で絞らない | createdBy 基準は ADMIN 代理登録時に管理者の参加 venue が誤って入る。全ステータスはキャンセル待ち・辞退済みの venue まで拾う。match_number を絞らないと同日複数会場・別試合番号の参加レコードが混ざり venue_id が NULL に落ちるため、(WON/PENDING) かつ同試合番号 (or NULL) に限定する |
| 7 | 既存 Match の backfill | マイグレーション SQL で自動実施 | NULL のまま放置 / 手動運用 | 古いデータも会場が見える方が UX 良い。SQL 1回実行で完結 |
| 8 | MatchCreateRequest の拡張 | 拡張しない（バックエンドで自動推定） | venue_id を必須フィールドに追加 | フォーム改修不要。既存フローへの破壊的変更を回避 |
| 9 | MatchDetail での表示 | 詳細情報 grid を 3 カラム化 | ヘッダ部分に追加 / 別セクション化 | 既存の「試合日」「試合番号」と整合 |
| 10 | API 後方互換 | MatchDto に追加フィールド（既存維持） | 別エンドポイント新設 | クライアント側の段階的移行不要。後方互換あり |
