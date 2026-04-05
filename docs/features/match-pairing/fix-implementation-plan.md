---
status: completed
---
# 対戦組み合わせ・対戦変更機能 改修実装手順書

## 実装タスク

### タスク1: createdBy ハードコード修正
- [x] 完了
- **概要:** `MatchPairingController.java` の3箇所で `createdBy = 1L` を `httpRequest.getAttribute("currentUserId")` に置換
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/MatchPairingController.java` — 行93, 115, 133 の `Long createdBy = 1L;` を `Long createdBy = (Long) httpRequest.getAttribute("currentUserId");` に変更。TODOコメントも削除
- **依存タスク:** なし
- **対応Issue:** #285

### タスク2: side パラメータバリデーション追加
- [x] 完了
- **概要:** `MatchPairingService.updatePlayer` に `side` パラメータの明示的バリデーションを追加
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchPairingService.java` — `updatePlayer` メソッドの先頭（行154付近）に `if (!"player1".equals(side) && !"player2".equals(side)) { throw new IllegalArgumentException("sideは'player1'または'player2'を指定してください"); }` を追加
- **依存タスク:** なし
- **対応Issue:** #286

### タスク3: MatchPairingRepository 直接注入のService移動
- [x] 完了
- **概要:** Controller から `MatchPairingRepository` の直接注入を削除し、Service 経由でアクセスするようにする
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchPairingService.java` — `getSessionDateById(Long id)` メソッドを追加（ペアリングIDからセッション日付を返す）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/MatchPairingController.java` — `MatchPairingRepository` のフィールド注入を削除、`validateAdminScopeByPairingId` 内で `matchPairingService.getSessionDateById(id)` を使用するように変更
- **依存タスク:** なし
- **対応Issue:** #287

### タスク4: 対戦履歴参照日数を90日→30日に修正
- [x] 完了
- **概要:** `MATCH_HISTORY_DAYS` を90から30に変更
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchPairingService.java` — 行30 `MATCH_HISTORY_DAYS = 90` → `MATCH_HISTORY_DAYS = 30`
- **依存タスク:** なし
- **対応Issue:** #288

### タスク5: BulkResultInput の getByDate に light=true 追加
- [x] 完了
- **概要:** 対戦結果入力画面での不要な recentMatches データ取得を回避
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/BulkResultInput.jsx` — 行86 `pairingAPI.getByDate(sessionData.sessionDate)` → `pairingAPI.getByDate(sessionData.sessionDate, { light: true })`
- **依存タスク:** なし
- **対応Issue:** #289

### タスク6: PairingGenerator 二重削除の解消
- [x] 完了
- **概要:** 既存編集時のフロントエンド側の明示的削除呼び出しを除去
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx` — 行320-323 の `if (isEditingExisting) { await pairingAPI.deleteByDateAndMatchNumber(sessionDate, matchNumber); }` ブロックを削除
- **依存タスク:** なし
- **対応Issue:** #290

### タスク7: 仕様書・設計書・コメントの修正
- [x] 完了
- **概要:** 仕様書のAPI権限一覧、対戦履歴日数、scoreDifference範囲、設計書のAutoMatchingRequest/batch API仕様を修正
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` — API権限一覧（行1444-1447付近）で `auto-match`, `PUT /{id}/player`, `DELETE /{id}`, `DELETE /date-and-match` の権限を「ALL」→「ADMIN+」に修正。scoreDifference範囲（行337付近）を「1〜25」→「0〜25」に修正
  - `docs/DESIGN.md` — AutoMatchingRequest（行1090付近）から `participantIds` を削除し `sessionDate` + `matchNumber` のみに更新。batch APIリクエスト形式（行1114-1123付近）を `MatchPairingBatchRequest`（pairings + waitingPlayerIds）に更新。対戦履歴日数（行1775付近）を「30日」に統一
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchPairingService.java` — 行293のコメント「過去30日」はそのまま（MATCH_HISTORY_DAYS=30に変更後は正しくなる）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/Match.java` — 行70のコメント「1～50」→「0～25」に修正
- **依存タスク:** タスク4（MATCH_HISTORY_DAYS変更後にコメントが正しくなる）
- **対応Issue:** #291

## 実装順序

1. タスク1: createdBy ハードコード修正（依存なし）
2. タスク2: side パラメータバリデーション追加（依存なし）
3. タスク3: Repository直接注入のService移動（依存なし）
4. タスク4: 対戦履歴参照日数の修正（依存なし）
5. タスク5: BulkResultInput light=true追加（依存なし）
6. タスク6: PairingGenerator 二重削除解消（依存なし）
7. タスク7: 仕様書・設計書・コメント修正（タスク4に依存）

※タスク1〜6は互いに独立しており、並列実施可能。タスク7はタスク4完了後に実施。
