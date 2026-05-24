---
status: completed
---

# 伝助インポート時の Venue デフォルト値適用 改修実装手順書

## 実装タスク

### タスク1: DensukeImportService.findOrCreateSession の改修

- [x] 完了
- **概要:** `findOrCreateSession()` を改修し、伝助→アプリ同期時に Venue の `defaultMatchCount` / `capacity` を `practice_sessions` に反映する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeImportService.java` (L805-L858)
    - **新規セッション作成時:**
      - 会場名マッチ時: `totalMatches` に `venue.getDefaultMatchCount()` を採用、`capacity` に `venue.getCapacity()` を採用
      - 会場名マッチしない時: `totalMatches` は既存ロジック維持（伝助の最大試合番号 → 3）、`capacity` は null
      - `result.getDetails()` のログメッセージに定員情報を追加
    - **既存セッション処理時:**
      - ケースA: `venueId` が null かつ会場名マッチ時 → `venueId` 補完と同時に、`capacity` が null なら `venue.getCapacity()` も補完
      - ケースB: `venueId` が既設定かつ `capacity` が null の時 → `venueId` から Venue を引き当てて `capacity` を補完（Venue 名マップから ID で検索）
      - `totalMatches` および既設定の `venueId` は触らない
- **依存タスク:** なし
- **対応Issue:** #778
- **注意:**
  - Venue から ID で引き当てる必要があるため、`venueNameMap.values().stream().filter(...)` で検索するか、`venueRepository.findById()` を呼ぶか、メソッド冒頭で `Map<Long, Venue> venueByIdMap` を別途構築する。N回呼ばれるメソッドなので、可能ならメソッド冒頭で id→venue マップを構築するか、`findOrCreateSession` の呼び出し側で渡す。
  - `practiceSessionRepository.save(session)` の呼び出し回数を増やさないよう、`changed` フラグで一括 save に統合する。

---

### タスク2: DensukeImportService の単体テスト追加

- [ ] 完了
- **概要:** タスク1で追加した Venue 値反映ロジックに対する単体テストを追加する。
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/DensukeImportServiceTest.java`
    - **追加するテストケース:**
      1. `testImportAppliesVenueDefaultsOnNewSession`: 新規セッション作成時、会場名マッチで `totalMatches=venue.defaultMatchCount` および `capacity=venue.capacity` が設定されることを検証
      2. `testImportFallsBackToScheduleMatchCountWhenVenueUnmatched`: 会場名未マッチ時、`totalMatches` は伝助スケジュール由来の値、`capacity` は null になることを検証
      3. `testImportFillsCapacityForExistingSessionWhenVenueMatched`: 既存セッション (venueId=null, capacity=null) が、会場名マッチで両方とも補完されることを検証
      4. `testImportFillsCapacityForExistingSessionWhenVenueIdAlreadySet`: 既存セッション (venueId 設定済み, capacity=null) が、capacity のみ補完されることを検証（ケースB）
      5. `testImportDoesNotOverwriteExistingCapacity`: 既存セッションの `capacity` が設定済みの場合、Venue の値で上書きされないことを検証
      6. `testImportDoesNotOverwriteTotalMatchesOnExistingSession`: 既存セッションの `totalMatches` が、Venue の `defaultMatchCount` と異なる値であっても変更されないことを検証
- **依存タスク:** タスク1
- **対応Issue:** #779
- **注意:**
  - 既存テストの mock 構成（`VenueRepository`, `PracticeSessionRepository` 等）を踏襲する
  - `Venue.builder().capacity(N).defaultMatchCount(M).build()` のセットアップが必要
  - 既存テストの動作を壊さないか確認するため、改修後に全テスト実行する

---

### タスク3: ドキュメント更新

- [ ] 完了
- **概要:** `CLAUDE.md` のドキュメント更新ルールに従い、仕様書・設計書を更新する。
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` — 伝助同期セクションに「会場名一致時に Venue の `defaultMatchCount` / `capacity` が自動適用される」旨を追記
  - `docs/DESIGN.md` — `DensukeImportService.findOrCreateSession` の挙動説明を更新
  - `docs/伝助双方向同期.md` — 練習日追加時の自動同期挙動を追記（既存セッション補完も含む）
- **依存タスク:** タスク1
- **対応Issue:** #780
- **注意:**
  - 既存の伝助関連記述を破壊しないよう、追記中心に行う
  - SCREEN_LIST.md は画面変更がないため更新不要

---

## 実装順序

1. **タスク1**: プロダクションコード改修（`DensukeImportService.findOrCreateSession`）
2. **タスク2**: 単体テスト追加（タスク1の実装内容を検証）
3. **タスク3**: ドキュメント更新（CLAUDE.md のルールに従い、実装コードと同じコミットに含める）

タスク1完了後にタスク2を実施し、テストが全てパスすることを確認してからタスク3に進む。タスク1・2・3は全て同じPRに含める（CLAUDE.md「ドキュメントの更新は実装コードと同じコミットに含める」ルール準拠）。

## DB マイグレーション

- **不要** — 既存カラム `practice_sessions.capacity`, `venues.capacity`, `venues.default_match_count` のみを利用するため、スキーマ変更なし。
- `CLAUDE.md` の「DBマイグレーション適用ルール」は本改修では適用外。

## レビュー観点

- 既存セッションの `capacity` 上書き防止が確実に効いていること（null 判定）
- 既存セッションの `totalMatches` および `venueId` が触られないこと
- 会場名未マッチ時の従来動作が壊れていないこと
- `practiceSessionRepository.save()` の呼び出し回数が不必要に増えていないこと
- 既存テスト（`DensukeImportServiceTest`, `DensukeImportServiceMonthFilterTest`, `DensukeImportServicePhaseCoverageTest`, `DensukeImportServiceDriftLogTest`）が全てパスすること
