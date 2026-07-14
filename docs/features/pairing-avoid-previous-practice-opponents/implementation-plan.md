---
status: completed
---
# 自動シャッフルで前回練習日の対戦相手も避ける 実装手順書

> 対象要件: [requirements.md](requirements.md)（AC は §4）。純バックエンド・UI/API/DBスキーマ変更なし。

## 技術設計（確定）

**変更点は `MatchPairingService.autoMatch` のスコアリングのみ。公開契約・DTO・エンドポイントは不変。**

1. **前回練習日ペア集合の算出（新規 private メソッド `findPreviousPracticePairKeys`）**
   - 入力: `sessionDate`, `effectiveOrganizationId`。
   - `effectiveOrganizationId` が null（＝SUPER_ADMIN かつ当日セッションから団体を解決できない）なら空集合を返す（＝ペナルティなし）。
   - `practiceSessionRepository.findPastSessionDatesByOrganizationId(orgId, sessionDate, PageRequest.of(0, MAX_PREV_LOOKBACK))` で**団体の過去セッション日を降順**に取得（最大 `MAX_PREV_LOOKBACK`＝20件でクエリ数を上限保証）。
   - 降順に walk し、各日 `d` について団体スコープのペアを収集:
     - ペア: `filterPairingsBySession(matchPairingRepository.findBySessionDateOrderByMatchNumber(d), getSessionAllPlayerIds(d, orgId), true)`
     - 結果: `filterMatchesBySession(matchRepository.findByMatchDateOrderByMatchNumber(d), getSessionAllPlayerIds(d, orgId), true)`
     - 両者の `getPairKey(p1,p2)` を集合に追加。
   - **集合が空でない最初の日**＝「前回練習日」。その集合を返して walk 終了（＝対戦なしの日は自然にスキップ・AC-4）。全件空なら空集合。

2. **実効団体IDの解決（`autoMatch` 冒頭）**
   - `Long effectiveOrganizationId = organizationId;`
   - null なら `practiceSessionRepository.findBySessionDate(sessionDate).map(PracticeSession::getOrganizationId).orElse(null)`（現行 `loadActiveParticipantIdsForMatch` L1131 と同じ縮退挙動。同日複数団体の横断処理は追加しない）。

3. **スコアペナルティの適用（`calculatePairScore` シグネチャ拡張）**
   - 引数に `Set<String> previousPracticePairKeys` を追加（**呼び出し元は L978 の1箇所のみ**・確認済み）。
   - 既存の日数ベーススコアを `base` に算出後、`previousPracticePairKeys.contains(getPairKey(p1,p2))` なら `base += PREVIOUS_PRACTICE_PENALTY` を加算して返す。
   - 定数 `private static final double PREVIOUS_PRACTICE_PENALTY = -1000.0;`（非・前回ペアのスコアは最悪 −100 なので、必ず下位・かつ有限＝最後の手段として選択可能）。
   - 同日ペアは従来どおり `todayMatches` で**スコア前に完全除外**されるため二重扱いは起きない。

4. **配線**: `autoMatch` の貪欲ループ前で `Set<String> previousPracticePairKeys = findPreviousPracticePairKeys(sessionDate, effectiveOrganizationId);` を1回算出し、L978 の呼び出しに渡す。

5. **リポジトリ追加（1本のみ・読み取り専用）**
   - `PracticeSessionRepository`:
     ```java
     @Query("SELECT ps.sessionDate FROM PracticeSession ps WHERE ps.organizationId = :orgId AND ps.sessionDate < :date ORDER BY ps.sessionDate DESC")
     List<LocalDate> findPastSessionDatesByOrganizationId(@Param("orgId") Long orgId, @Param("date") LocalDate date, Pageable pageable);
     ```

6. **ドキュメント更新（同一コミット）**: `docs/spec/matching.md` の「自動マッチング」アルゴリズム（§54-65）・「アルゴリズム」要約（§168-173）・スコア優先順位に「前回練習日ペアの強い回避（同一団体・ソフトペナルティ・グレースフル劣化）」を追記。

## 実装タスク

### タスク1: 前回練習日回避ロジックの実装 ＋ 決定的コアテスト ＋ spec更新
- [x] 完了
- **目的:** 自動シャッフルで同一団体の前回練習日ペアに強いペナルティを加え、他に相手がいる限り再形成しない。相手が尽きた場合は待機者を増やさず組む。
- **対応AC:** AC-1, AC-2, AC-5, AC-9
- **主な変更領域:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchPairingService.java`（`autoMatch`・`calculatePairScore`・新規 `findPreviousPracticePairKeys`・定数 `PREVIOUS_PRACTICE_PENALTY`）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/PracticeSessionRepository.java`（finder 1本追加）
  - `docs/spec/matching.md`（アルゴリズム記述の更新）
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/MatchPairingServiceTest.java`（新規テスト）
- **依存タスク:** なし
- **必要なテスト（テストファースト・shuffle不変な性質で検証）:**
  - AC-1（回避）: 参加者3名・`findPastSessionDatesByOrganizationId` が前回日を返し、その日ペア集合＝{1-2}。結果 `pairings` の唯一の組が (1,2) でない（1-3 か 2-3）。1ラウンドで全体最高スコアを選ぶため決定的。
  - AC-2（グレースフル劣化）: 参加者4名・前回ペア集合＝{1-2,3-4,1-3,2-4,1-4,2-3}（全消化）。`pairings.hasSize(2)` かつ `waitingPlayers` 空。
  - AC-5（回帰）: 同日（前試合番号）ペアが従来どおり候補から除外される既存テストが green。
- **完了条件:** 上記テスト green・`./gradlew test --tests "*MatchPairingServiceTest*"` 通過・型/コンパイル通過。
- **対応Issue:** #1042

### タスク2: 団体スコープ・エッジ・回帰テストの網羅
- [x] 完了
- **目的:** 団体スコープ・遡り・多人数時の非退行など残りACをテストで固定する。
- **対応AC:** AC-3, AC-4, AC-6, AC-7, AC-8, AC-9
- **主な変更領域:** `karuta-tracker/src/test/java/com/karuta/matchtracker/service/MatchPairingServiceTest.java`（既存 `MatchPairingIntegrationTest` に統合テストを足すかは実装時判断）
- **依存タスク:** タスク1（同一ファイル `MatchPairingServiceTest` を触るため直列）
- **必要なテスト:**
  - AC-3: `findPastSessionDatesByOrganizationId` が渡された `organizationId` で呼ばれること（Mockito verify）＋別団体の日ペアが集合に混ざらないこと（`getSessionAllPlayerIds(d, org)` フィルタで除外）。
  - AC-4: 過去日リスト降順 `[D-1(対戦なし), D-8(1-2対戦あり)]` で、D-1 がスキップされ D-8 の 1-2 がペナルティ対象になる。
  - AC-6/AC-7: 既存のロック保護・待機者算出・団体スコープ・初対戦最優先テストが green（回帰）。
  - AC-8: 参加者6名・前回ペア集合＝{1-2} で、`pairings.hasSize(3)`・`waitingPlayers` 空（多人数時に待機者が増えない）。
- **完了条件:** 全 `MatchPairingServiceTest` green・`./gradlew test` 全体 green・lint/typecheck 通過。
- **対応Issue:** #1043

## 実装順序（Wave）
- Wave 1: タスク1
- Wave 2: タスク2（タスク1に依存・同一テストファイル）

※ 2タスクとも `MatchPairingService` / `MatchPairingServiceTest` を触るため**直列**（並行不可）。
