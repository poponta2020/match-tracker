---
status: completed
---
# 対戦結果「指導」ステータス 実装手順書

## 実装タスク

### タスク1: DBマイグレーション（is_lesson 追加・score_difference null許容）
- [ ] 完了
- **概要:** `matches` テーブルに指導フラグ `is_lesson` を追加し、`score_difference` を NULL 許容に変更。既存の `score_difference` 範囲 CHECK 制約を「NULL または 範囲内」に修正する。
- **変更対象ファイル:**
  - `database/add_is_lesson_to_matches.sql`（新規） — `ALTER TABLE matches ADD COLUMN is_lesson BOOLEAN NOT NULL DEFAULT FALSE;` / `score_difference` を `DROP NOT NULL`（NOT NULL の場合）/ 既存 CHECK 制約を削除し `CHECK (score_difference IS NULL OR (範囲))` を再作成
- **依存タスク:** なし
- **本番適用:** ⚠️ マージ前後で本番DB（Render PostgreSQL）に `psql` で適用必須（CLAUDE.md ルール）。適用後 `\d matches` で反映確認。
- **対応Issue:** #872

### タスク2: バックエンド エンティティ・DTO・リクエスト拡張
- [ ] 完了
- **概要:** Match に `isLesson` を追加し、`scoreDifference` を nullable 化。DTO・リクエストに `isLesson` を反映、バリデーションを調整。
- **変更対象ファイル:**
  - `entity/Match.java` — `isLesson`（`@Column(name="is_lesson")`、デフォルト false）追加、`scoreDifference` を nullable に
  - `dto/MatchDto.java` — `isLesson` フィールド追加、`fromEntity()` でマッピング
  - `dto/MatchCreateRequest.java` — `isLesson` 追加、`scoreDifference` のバリデーション（指導時 null 許容）調整
- **依存タスク:** タスク1（#872）
- **対応Issue:** #873

### タスク3: バックエンド Service・Controller の保存処理拡張
- [ ] 完了
- **概要:** 詳細登録・更新で `isLesson` を永続化。指導時は `scoreDifference = null` とする。勝敗判定（`determineResult`）は変更しない（指導も通常計上）。
- **変更対象ファイル:**
  - `service/MatchService.java` — `createMatch` / `updateMatch` で `isLesson` を保存、指導時 `scoreDifference=null`
  - `controller/MatchController.java` — `POST /matches/detailed` / `PUT /matches/{id}/detailed` で `isLesson` を受け取り
- **依存タスク:** タスク2（#873）
- **対応Issue:** #874

### タスク4: バックエンド 指導回数/被指導回数の集計
- [ ] 完了
- **概要:** 指導回数（指導試合で勝ち＝指導した側）・被指導回数（指導試合で負け＝指導された側）の集計クエリを追加し、statistics-by-rank の総合レスポンスに含める。既存の試合数・勝数・負数・勝率の計算は変更しない。
- **変更対象ファイル:**
  - `repository/MatchRepository.java` — 指導回数 `is_lesson=true AND winner_id=:playerId` / 被指導回数 `is_lesson=true AND (player1_id=:playerId OR player2_id=:playerId) AND winner_id<>:playerId AND winner_id IS NOT NULL`
  - `dto/MatchStatisticsDto.java`（および statistics-by-rank の総合DTO） — `lessonGivenCount` / `lessonReceivedCount` 追加
  - `service/MatchService.java` — `getPlayerStatisticsByRank` の総合に集計値をセット
- **依存タスク:** タスク2（#873）
- **対応Issue:** #875

### タスク5: フロント APIクライアント拡張
- [ ] 完了
- **概要:** 詳細登録・更新APIに `isLesson` を送れるようにする。
- **変更対象ファイル:**
  - `api/matches.js` — `createDetailed` / `updateDetailed`（必要なら create/update）に `isLesson` を追加
- **依存タスク:** タスク3（#874）
- **対応Issue:** #876

### タスク6: フロント 入力UI（ピッカーに「指導」追加）
- [ ] 完了
- **概要:** 枚数ピッカー末尾に「指導」を追加。選択時 `isLesson=true, scoreDifference=null`。勝ち/負け選択は既存どおり必須。枚数差未選択バリデーションは指導選択時にスキップ。
- **変更対象ファイル:**
  - `pages/matches/MatchForm.jsx` — 枚数 `<select>` に「指導」option 追加、選択ハンドリング、送信に `isLesson`
  - `pages/matches/BulkResultInput.jsx` — 枚数 `<select>` に「指導」option 追加、選択ハンドリング、未選択警告の分岐、送信に `isLesson`
- **依存タスク:** タスク5（#876）
- **対応Issue:** #877

### タスク7: フロント 表示（指導試合の両者黒・「指導」表示）
- [ ] 完了
- **概要:** 指導試合は勝者の緑色付け・〇×をやめ、両者黒色・中央（枚数位置）に「指導」と表示する。
- **変更対象ファイル:**
  - `pages/matches/MatchResultsView.jsx` — `isLesson` 時に両者黒・中央「指導」
  - `pages/matches/BulkResultInput.jsx` — 表示部（読み取り時）も指導表示
  - `pages/matches/MatchList.jsx` — 結果欄を指導試合は「指導」表示（色付け・マークなし）
  - `pages/matches/MatchDetail.jsx` — `getResultMark()` / `getResultTextColor()` に指導ケース追加
- **依存タスク:** タスク5（#876）
- **対応Issue:** #878

### タスク8: フロント 統計に指導回数/被指導回数を表示
- [ ] 完了
- **概要:** MatchList の総合統計の下に、指導回数・被指導回数を値>0 のときのみ表示する。
- **変更対象ファイル:**
  - `pages/matches/MatchList.jsx` — `lessonGivenCount` / `lessonReceivedCount` を総合統計セクション下に条件付き表示
- **依存タスク:** タスク4（#875）
- **対応Issue:** #879

### タスク9: テスト
- [ ] 完了
- **概要:** バックエンドの保存・集計ロジックのテストを追加。指導試合が通常統計に計上され、かつ指導回数/被指導回数が正しく集計されることを検証。
- **変更対象ファイル:**
  - `karuta-tracker/src/test/.../MatchServiceTest`（または該当テスト） — 指導試合の作成・更新、統計集計のテスト追加
- **依存タスク:** タスク3（#874）, タスク4（#875）
- **対応Issue:** #880

### タスク10: ドキュメント更新
- [ ] 完了
- **概要:** 仕様・画面・設計ドキュメントに指導ステータスを反映。
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` — 指導ステータスの仕様
  - `docs/SCREEN_LIST.md` — 各画面の指導表示
  - `docs/DESIGN.md` — DB・API・集計の設計
- **依存タスク:** タスク1〜8
- **対応Issue:** #881

## 実装順序
1. タスク1（#872 DBマイグレーション、依存なし）→ 本番適用
2. タスク2（#873 エンティティ・DTO、タスク1依存）
3. タスク3（#874 Service・Controller、タスク2依存）
4. タスク4（#875 集計、タスク2依存）
5. タスク5（#876 APIクライアント、タスク3依存）
6. タスク6（#877 入力UI、タスク5依存）
7. タスク7（#878 表示、タスク5依存）
8. タスク8（#879 統計表示、タスク4依存）
9. タスク9（#880 テスト、タスク3・4依存）
10. タスク10（#881 ドキュメント、タスク1〜8依存）
