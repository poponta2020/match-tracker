---
status: completed
---
# 対戦一覧の会場・試合番号表示 実装手順書

## 実装タスク

### タスク1: DB マイグレーション SQL 作成 + 本番DB適用
- [x] 完了
- **概要:** `matches` テーブルに `venue_id` カラムを追加し、既存データを 2 段階の優先順位で backfill する SQL を作成。CLAUDE.md の DB マイグレーションルールに従い、本番 Render PostgreSQL にも適用する
- **変更対象ファイル:**
  - `database/add_venue_id_to_matches.sql` — 新規SQL作成（ALTER + FK + INDEX + backfill 2段）
- **本番適用:** PR レビュー時 / マージ前後に `CLAUDE.local.md` の接続情報で psql 実行。適用後 `\d matches` で確認
- **依存タスク:** なし
- **対応Issue:** #740

### タスク2: `Match` エンティティに `venueId` 追加
- [x] 完了
- **概要:** Match エンティティに `@Column(name = "venue_id")` で `venueId: Long` フィールドを追加
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/Match.java` — `venueId` フィールド追加
- **依存タスク:** タスク1（DB スキーマが先）
- **対応Issue:** #741

### タスク3: `MatchDto` に `venueId`/`venueName` 追加
- [x] 完了
- **概要:** MatchDto に `venueId: Long`, `venueName: String` を追加し、`fromEntity()` に `venueId` セット処理を追加（venueName は別途 enrichment）
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/MatchDto.java` — フィールド追加・`fromEntity()` 更新
- **依存タスク:** タスク2
- **対応Issue:** #742

### タスク4: `MatchService.resolveVenueId()` 実装と create/upsert への組み込み
- [x] 完了
- **概要:** PracticeParticipant 経由優先・同日 practice_sessions が一意ならフォールバックで venue_id を決定するヘルパーメソッドを実装。create / upsert 系メソッドから呼ぶ
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchService.java` — `resolveVenueId()` 追加、`createMatchSimple()`/`createMatch()`（新規作成パスのみ）で呼出
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/PracticeParticipantRepository.java` — `findVenueIdsByPlayerIdAndSessionDate()` 追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/PracticeSessionRepository.java` — `findDistinctVenueIdsBySessionDate()` 追加
- **依存タスク:** タスク2、タスク3
- **対応Issue:** #743

### タスク5: `MatchService` の enrichment で `venueName` を解決
- [x] 完了
- **概要:** 一覧・詳細レスポンスに venueName を含めるため、enrich メソッド内でバッチ取得 (N+1 回避)。Venue を `venueRepository.findAllById(...)` で一度に取得し Map にして紐付け
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchService.java` — `enrichMatchesWithPlayerPerspective()` / `enrichMatchesWithPlayerNames()` を拡張
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/VenueRepository.java` — `findAllById(Iterable)` の利用確認（既存 JpaRepository 標準で OK のはず）
- **依存タスク:** タスク3、タスク4
- **対応Issue:** #744

### タスク6: バックエンド単体テスト追加・更新
- [x] 完了
- **概要:** `MatchServiceTest` で venue_id 解決ロジック（PracticeParticipant 経由・PlayerOrganization 経由・該当なし NULL）の3パターンを検証。既存テストの fixture も venueId に対応
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/MatchServiceTest.java` — テスト追加
  - 既存 fixture / DTO 比較がある箇所の確認・修正
- **依存タスク:** タスク4、タスク5
- **対応Issue:** #745

### タスク7: `MatchList.jsx` 表示書式変更
- [x] 完了
- **概要:** 日付ラベルを `5/23 あかなら・すずらん(2)` / 会場不明時は `5/23 (2)` の書式に変更。文字スタイルは現状の `text-xs text-[#9ca3af]` を維持。固定幅 `w-12` を廃止し、`min-w-0 truncate` で長い会場名に対応
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/MatchList.jsx` — 日付ブロックのレンダリング書き換え
- **依存タスク:** タスク5（API レスポンスに venueName が乗ること）
- **対応Issue:** #746

### タスク8: `MatchDetail.jsx` に会場カード追加
- [x] 完了
- **概要:** 「詳細情報」セクションの grid を `md:grid-cols-3` に変更し、「会場」カード（MapPin 等のアイコン + 会場名）を追加。会場情報がない場合は「—」を表示
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/MatchDetail.jsx` — grid 拡張・会場カード追加
- **依存タスク:** タスク5
- **対応Issue:** #747

### タスク9: ドキュメント更新
- [x] 完了
- **概要:** CLAUDE.md のドキュメント更新ルールに従い、`docs/SPECIFICATION.md`、`docs/SCREEN_LIST.md`、`docs/DESIGN.md` を更新
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` — 対戦結果一覧画面・試合詳細画面の項に会場・試合番号表示を追記
  - `docs/SCREEN_LIST.md` — 該当画面の表示項目に会場名・試合番号を追加
  - `docs/DESIGN.md` — Match に venue_id を追加した旨、決定ロジックを追記
- **依存タスク:** タスク7、タスク8（実装内容が確定してから）
- **対応Issue:** #748

## 実装順序

1. **タスク1** — DB マイグレーション SQL 作成 + 本番DB適用（最初に実施）
2. **タスク2** — Match エンティティ拡張
3. **タスク3** — MatchDto 拡張
4. **タスク4** — MatchService.resolveVenueId() 実装
5. **タスク5** — enrichment で venueName 解決
6. **タスク6** — バックエンドテスト
7. **タスク7** — MatchList.jsx 表示書式変更
8. **タスク8** — MatchDetail.jsx に会場追加
9. **タスク9** — ドキュメント更新

タスク2〜6 は並列性が低い（同じファイルを編集）ので順次。タスク7・8 は5の完了後に並列実装可能。

## PR 戦略の提案

全タスクを 1 つの PR にまとめる想定（バックエンド変更とフロント変更が密結合、API レスポンスの変更が両方に必要）。タスク数が多いが、論理的には1つの機能。
