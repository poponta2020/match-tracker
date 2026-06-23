---
status: completed
---
# 対戦結果「指導」ステータス 要件定義書

## 1. 概要

### 目的
対戦結果に「勝ち / 負け」に加えて **「指導」** という属性を持たせ、上級者が初心者に取らせ方を教えながら行う試合を区別して記録・表示できるようにする。

### 背景・動機
- 普段の練習では通常の対戦のほか、上級者が初心者に教えながら行う「指導」試合がある。
- 指導試合の有無を記録し、各プレイヤーの「指導した回数」「指導された回数」を把握したい。
- ※当初は「指導試合の勝敗を勝率統計に含めたくない」という背景だったが、検討の結果、**指導試合の勝敗も通常どおり統計（勝数・負数・勝率・試合数）に計上する**方針に決定（ユーザー判断）。指導フラグは「表示の差し替え」と「指導回数/被指導回数の集計」に用いる。

---

## 2. ユーザーストーリー

### 対象ユーザー
- 対戦結果を入力する PLAYER / ADMIN / SUPER_ADMIN
- 自分の成績を確認するプレイヤー

### ユーザーの目的
- 指導試合を「指導」として記録したい（通常の勝敗記録と同じフローで）。
- 自分が指導した回数・指導された回数を統計画面で把握したい。

### 利用シナリオ
1. 練習会で上級者Aが初心者Bに教えながら試合を行う。
2. 結果入力時、まず勝ち/負けを選ぶ（教えた側Aが勝ち＝指導した側、Bが負け＝指導された側）。
3. 取り札枚数ピッカーで、数字ではなく末尾の「指導」を選択する。
4. 記録後、一覧・詳細・結果表示画面では「指導」と表示される。
5. 統計画面では通常の勝敗にも計上されつつ、総合統計の下に「指導回数」「被指導回数」が（値があれば）表示される。

---

## 3. 機能要件

### 3.1 画面仕様

#### 改修対象画面（確定）
| 画面 | ルート | 改修内容 |
|------|--------|----------|
| 一括入力 | `/matches/bulk-input/:sessionId` | 枚数ピッカー末尾に「指導」追加。指導試合は両者黒・中央「指導」表示 |
| 結果表示 | `/matches/results/:sessionId?` | 指導試合は勝者の緑色付けをせず、両者黒色、中央の枚数位置に「指導」と表示 |
| 新規/編集 | `/matches/new`, `/matches/:id/edit`（MatchForm） | 勝ち/負け選択は既存どおり。枚数ピッカー末尾に「指導」追加 |
| 一覧 | `/matches`（MatchList） | 指導試合は結果欄を「指導」表示（色付け・〇×なし）。統計の下に指導回数/被指導回数を表示 |
| 詳細 | `/matches/:id`（MatchDetail） | 指導試合は両者黒・「指導」表示 |

#### 入力フロー（MatchForm / BulkResultInput）
- 勝ち/負けの選択は**既存フローのまま**（指導の判定に必須）。
- 取り札枚数ピッカー（現状 0〜25 の `<select>`）の **末尾（25の次）に「指導」** を追加する。
- 「指導」を選択すると:
  - その試合は指導試合（`isLesson = true`）として扱う。
  - 取り札枚数差（`scoreDifference`）は**記録しない（null）**。
  - 勝ち選択側＝指導した側、負け選択側＝指導された側。
- 引き分けと指導の組み合わせは存在しない（指導は必ず勝ち/負けを選んだ上で選択）。

#### 表示仕様（指導試合）
- results / 一覧 / 詳細 のいずれも:
  - 勝者の緑色付け・〇×マークは**行わない**（両プレイヤー名とも通常の黒色）。
  - 枚数差を表示する中央位置に「**指導**」と表示する。

#### 統計表示（MatchList のみ）
- 既存の 試合数 / 勝数 / 負数 / 勝率 は**指導試合も通常どおり計上**（数値の扱いは変更なし）。
- **総合統計の下に**、以下を**値が1以上の場合のみ**表示:
  - 指導回数（= 指導試合で勝ち＝指導した側だった試合数）
  - 被指導回数（= 指導試合で負け＝指導された側だった試合数）
- 級別（A〜E級）統計には指導回数/被指導回数は表示しない。

### 3.2 ビジネスルール
- 指導試合は登録済みプレイヤー同士の試合のみ対象（未登録相手の簡易入力フローは対象外）。
- 指導試合は勝敗（winner_id）を必ず持つ（引き分け不可）。
- 指導試合は `scoreDifference` を持たない（null）。
- 指導フラグは既存メンター管理機能（`mentor_relationships`）とは**独立**（連携しない）。

### 3.3 エラーケース・例外処理
- 指導選択時に勝ち/負けが未選択 → 既存の勝敗必須バリデーションでエラー。
- 通常試合（指導でない）で `scoreDifference` 未選択 → 既存の未選択警告どおり。
- 既存の指導でない試合は `isLesson = false`、`scoreDifference` は従来どおり必須。

---

## 4. 技術設計

### 4.1 DB設計
**`matches` テーブルに以下を追加（マイグレーション必須）:**
- `is_lesson BOOLEAN NOT NULL DEFAULT FALSE` — 指導試合フラグ
- `score_difference` を **NULL 許容** に変更（指導試合では null）。既存の CHECK 制約（`score_difference` の範囲制約）は **null を許容する形に修正**（`score_difference IS NULL OR (範囲)`）。

> ⚠️ **本番DB適用必須**: `database/` にマイグレーションSQLを追加し、本番（Render PostgreSQL）にも `psql` で適用する（CLAUDE.md のDBマイグレーションルール厳守）。entity変更とSQLは同一PRに含める。

### 4.2 API設計
既存エンドポイントを拡張（新規エンドポイントは追加しない）:

| メソッド | エンドポイント | 変更 |
|---------|---------------|------|
| POST | `/matches/detailed` | リクエストに `isLesson` 追加、`scoreDifference` を null 許容 |
| PUT | `/matches/{id}/detailed` | クエリ/リクエストに `isLesson` 追加、`scoreDifference` null 許容 |
| GET | `/matches/...`（取得系） | レスポンス `MatchDto` に `isLesson` 追加 |
| GET | `/matches/player/{id}/statistics-by-rank` | レスポンスの総合（total）に `lessonGivenCount` / `lessonReceivedCount` 追加 |

### 4.3 バックエンド設計
- **Match エンティティ**: `isLesson`（`@Column(name="is_lesson")`）追加。`scoreDifference` を nullable に。
- **MatchDto**: `isLesson` 追加、`fromEntity()` でマッピング。
- **MatchCreateRequest**: `isLesson` 追加、`scoreDifference` の `@Min/@Max` を null 許容に調整。
- **MatchService**: `createMatch` / `updateMatch` で `isLesson` を永続化、指導時は `scoreDifference = null`。`determineResult` は**変更しない**（勝敗判定は winner_id ベースのまま＝指導も通常計上）。
- **MatchRepository**: 集計クエリ追加
  - 指導回数: `is_lesson = true AND winner_id = :playerId`
  - 被指導回数: `is_lesson = true AND (player1_id = :playerId OR player2_id = :playerId) AND winner_id <> :playerId AND winner_id IS NOT NULL`
- **統計DTO**: `statistics-by-rank` の総合に `lessonGivenCount` / `lessonReceivedCount` を追加。既存の試合数・勝数・負数・勝率の計算ロジックは変更しない（指導も通常計上）。

### 4.4 フロントエンド設計
- **api/matches.js**: `createDetailed` / `updateDetailed`（および必要なら create/update）に `isLesson` を渡す。
- **BulkResultInput.jsx**: 枚数 `<select>` 末尾に「指導」option 追加。選択時 `isLesson=true, scoreDifference=null`。指導試合は両者黒・中央「指導」表示。送信に `isLesson` を含める。
- **MatchForm.jsx**: 枚数 `<select>` 末尾に「指導」option 追加。勝ち/負けラジオは既存どおり必須。指導時 `isLesson=true, scoreDifference=null`。
- **MatchResultsView.jsx**: `isLesson` の場合、両者黒・〇×/緑なし・中央に「指導」表示。
- **MatchList.jsx**:
  - 結果欄: `isLesson` の場合「指導」表示（色付け・マークなし）。
  - 統計セクション: 総合の下に `lessonGivenCount` / `lessonReceivedCount` を値>0 のときのみ表示。
- **MatchDetail.jsx**: `getResultMark()` / `getResultTextColor()` に指導ケース追加（両者黒・「指導」表示）。

---

## 5. 影響範囲

### 変更が必要な既存ファイル
**バックエンド:**
- `entity/Match.java` — `isLesson` 追加、`scoreDifference` nullable
- `dto/MatchDto.java` — `isLesson` 追加
- `dto/MatchCreateRequest.java` — `isLesson` 追加、バリデーション調整
- `service/MatchService.java` — 永続化・統計集計
- `repository/MatchRepository.java` — 指導/被指導カウントクエリ
- `dto/MatchStatisticsDto.java` または statistics-by-rank の総合DTO — `lessonGivenCount` / `lessonReceivedCount`
- `controller/MatchController.java` — `/matches/{id}/detailed` の `isLesson` 受け取り
- `database/`（新規SQL）— `is_lesson` 追加・`score_difference` null許容・CHECK修正 → **本番適用必須**

**フロントエンド:**
- `api/matches.js`
- `pages/matches/BulkResultInput.jsx`
- `pages/matches/MatchForm.jsx`
- `pages/matches/MatchResultsView.jsx`
- `pages/matches/MatchList.jsx`
- `pages/matches/MatchDetail.jsx`

**ドキュメント:**
- `docs/SPECIFICATION.md` / `docs/SCREEN_LIST.md` / `docs/DESIGN.md`（実装と同コミットで更新）

### 既存機能への影響
- 既存試合は `is_lesson = false`（デフォルト）として扱われ、表示・統計は従来どおり。
- `scoreDifference` の null 許容化により、null を参照する箇所（表示・計算）で NPE が出ないことを確認する必要あり。
- 枚数差未選択バリデーション（BulkResultInput）は「指導選択時は枚数差不要」とする分岐が必要。
- 簡易入力（未登録相手）・メンター管理・iCal 等は対象外（影響なし想定だが、`MatchDto` への `isLesson` 追加が他参照箇所を壊さないか確認）。

### API・DBスキーマ互換性
- `is_lesson` は `DEFAULT FALSE` のため既存行・既存APIと後方互換。
- `score_difference` の null 許容化は拡大方向の変更で互換性あり。

---

## 6. 設計判断の根拠
- **winner_id 流用 + is_lesson フラグ**: 既存の勝敗入力フローをそのまま活かせ、影響範囲が最小。勝ち側=指導した側という自然なマッピングが可能（ユーザー要望と一致）。
- **指導も通常統計に計上**: ユーザーが整合性（試合数=勝+負）を優先し、確認の上で決定。`determineResult` を変えないことで統計ロジックの変更を最小化。
- **指導回数/被指導回数は総合のみ**: 級別表示は不要というユーザー判断。実装量を抑える。
- **登録済み相手のみ対応**: 未登録相手の簡易入力は統計反映の扱いが複雑になるため対象外。
- **メンター機能と独立**: 指導回数は試合単位のフラグから集計し、formalなメンター関係に依存させない（ユーザー判断）。
