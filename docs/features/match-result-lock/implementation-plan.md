---
status: completed
---
# 結果入力済み対戦のロック機能 実装手順書

## 実装タスク

### タスク1: MatchPairingDto にロック関連フィールドを追加
- [x] 完了
- **概要:** ペアリングDTOに結果入力済み情報を持たせる
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/MatchPairingDto.java` — `hasResult`(boolean), `winnerName`(String), `scoreDifference`(Integer), `matchId`(Long) フィールド追加
- **依存タスク:** なし
- **対応Issue:** #330

### タスク2: MatchRepository / MatchPairingRepository にクエリ追加
- [x] 完了
- **概要:** ロック判定やペアリング存在チェックに必要なクエリを追加
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/MatchRepository.java` — 指定日・試合番号の全matchをまとめて取得するクエリ追加（既存の `findByMatchDateAndMatchNumber` で対応可能だが、ペアリング一覧へのマッピング用にそのまま利用）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/MatchPairingRepository.java` — `findBySessionDateAndMatchNumberAndPlayers(sessionDate, matchNumber, player1Id, player2Id)` クエリ追加（ペアリング存在チェック用）
- **依存タスク:** なし
- **対応Issue:** #331

### タスク3: MatchPairingService のロック対応（コアロジック）
- [x] 完了
- **概要:** ペアリング取得時の結果情報エンリッチメント、バッチ作成時のロック保持、自動マッチングのロック除外、リセット処理を実装
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchPairingService.java`
    - `getByDate()`, `getByDateAndMatchNumber()` — DTOに `hasResult`, `winnerName`, `scoreDifference`, `matchId` を付加する `enrichWithMatchResults()` メソッド追加。`findByMatchDateAndMatchNumber` で該当日・試合番号の全matchを取得し、各ペアリングのプレイヤーペアと照合してマッピング
    - `createBatch()` — 既存の110行目 `deleteBySessionDateAndMatchNumber` の前にロック済みペアリング（対応するmatchが存在するもの）を取得。ロック済みペアリングは削除対象から除外し、新規ペアリングにもロック済みプレイヤーが含まれないようフィルタリング
    - `autoMatch()` — 282行目 `loadWonParticipantIdsForMatch` の結果から、ロック済みプレイヤーIDを除外。ロック済みペアリングを `AutoMatchingResult` の `lockedPairings` として返却
    - 新メソッド `resetWithResult(Long pairingId)` — ペアリングIDからペアリングを取得し、対応するmatch（同日・同試合番号・同プレイヤーペア）を検索して両方削除。削除した結果情報（勝者名・スコア差）を返却
- **依存タスク:** タスク1, タスク2
- **対応Issue:** #332

### タスク4: MatchService にペアリング自動生成ロジック追加
- [x] 完了
- **概要:** 結果作成・更新時にmatch_pairingsを自動生成/更新する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchService.java`
    - `createMatch()` (393行目〜) — 保存後、`player2Id != 0L`（両プレイヤーが登録済み）の場合のみ、`MatchPairingRepository` で同日・同試合番号・同プレイヤーペアのペアリングが存在しなければ自動生成。`created_by` は `request.getCreatedBy()` を使用
    - `createMatchSimple()` (321行目〜) — `player2Id = 0L`（未登録対戦相手）のため、ペアリング自動生成は行わない（変更なし）
    - `updateMatch()` (452行目〜) — プレイヤー変更なし（勝者・スコアのみ変更）のため、ペアリング操作は不要（変更なし）
    - `updateMatchSimple()` (484行目〜) — 対戦相手名のみ変更の可能性があり、`player2Id = 0L` のため、ペアリング操作は不要（変更なし）
    - 注: `createMatch()` のupsert処理（既存matchの更新）時はペアリングが既に存在する可能性が高いため、存在チェックでスキップ
- **依存タスク:** タスク2
- **対応Issue:** #333

### タスク5: AutoMatchingResult にロック済みペアリング情報を追加
- [x] 完了
- **概要:** 自動マッチングの結果にロック済みペアリング一覧を含める
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/AutoMatchingResult.java` — `lockedPairings` フィールド（`List<PairingSuggestion>` 型）を追加。フロントエンドがロック済みペアを表示に残すために使用
- **依存タスク:** なし
- **対応Issue:** #334

### タスク6: MatchPairingController にリセットエンドポイント追加
- [x] 完了
- **概要:** ペアリングと対応する結果を同時に削除するリセットAPIを追加
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/MatchPairingController.java` — `DELETE /api/match-pairings/{id}/with-result` エンドポイント追加。`@RequireRole({Role.SUPER_ADMIN, Role.ADMIN})` を適用。レスポンスは削除された結果情報を含むDTO
- **依存タスク:** タスク3
- **対応Issue:** #335

### タスク7: フロントエンド API クライアント更新
- [x] 完了
- **概要:** リセットAPI呼び出しを追加
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/api/pairings.js` — `resetWithResult(id)` メソッド追加（`DELETE /api/match-pairings/${id}/with-result`）
- **依存タスク:** タスク6
- **対応Issue:** #336

### タスク8: PairingGenerator.jsx のUI変更
- [x] 完了
- **概要:** ロック表示、操作制御、リセットボタン、自動組み合わせのロック除外をUIに反映
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx`
    - ペアリング表示部分: `hasResult === true` のペアリングをグレーアウト表示、「結果入力済」バッジ追加、プレイヤー変更・削除ボタンを `disabled` 化
    - リセットボタン追加: 各ロック済みペアリングに「リセット」ボタンを配置。クリック時に確認ダイアログ（勝者名・スコア差を含む）を表示し、承認時に `pairingAPI.resetWithResult(id)` を呼び出し
    - 自動組み合わせ結果の表示: `autoMatch` レスポンスの `lockedPairings` をロック済みとして表示に残す
    - バッチ保存: ロック済みペアリングを送信対象から除外
    - 手動ペア追加時の利用可能プレイヤーリスト: ロック済みプレイヤーを除外
- **依存タスク:** タスク7
- **対応Issue:** #337

### タスク9: BulkResultInput.jsx の変更
- [x] 完了
- **概要:** ペアリング未存在時のメッセージとPairingGeneratorへの遷移ボタンを追加
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/pairings/BulkResultInput.jsx`（正しいパスは `karuta-tracker-ui/src/pages/matches/BulkResultInput.jsx`）
    - ある回戦のペアリングが0件の場合、「対戦組み合わせが作成されていません」メッセージを表示
    - `isAdmin()` または `isSuperAdmin()` の場合、「対戦組み合わせを作成する」ボタンを表示。クリックで `/pairings?date=${sessionDate}&matchNumber=${matchNumber}` に遷移
    - PLAYERの場合はメッセージのみ表示（遷移ボタンなし）
- **依存タスク:** なし
- **対応Issue:** #338

### タスク10: テスト作成
- [x] 完了
- **概要:** ロック機能の主要ロジックに対するテストを作成
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/MatchPairingServiceTest.java` — createBatch時のロック保持テスト、resetWithResultテスト、autoMatch時のロック除外テスト
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/MatchServiceTest.java` — createMatch時のペアリング自動生成テスト
- **依存タスク:** タスク3, タスク4
- **対応Issue:** #339

## 実装順序
1. タスク1（DTO変更 — 依存なし）
2. タスク2（Repository変更 — 依存なし）
3. タスク5（AutoMatchingResult変更 — 依存なし）
4. タスク3（MatchPairingService — タスク1,2に依存）
5. タスク4（MatchService — タスク2に依存）
6. タスク6（Controller — タスク3に依存）
7. タスク7（APIクライアント — タスク6に依存）
8. タスク9（BulkResultInput — 依存なし、並行作業可能）
9. タスク8（PairingGenerator — タスク7に依存）
10. タスク10（テスト — タスク3,4に依存）
