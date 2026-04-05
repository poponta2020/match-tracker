---
status: completed
---
# 対戦記録機能 改修実装手順書

## 実装タスク

### タスク1: upsertPersonalNote の null クリア対応
- [ ] 完了
- **概要:** 既存の MatchPersonalNote レコードがある場合、personalNotes/otetsukiCount が両方 null でもクリア処理を実行するよう修正
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchService.java` — `upsertPersonalNote()` メソッド（L715-729）の早期リターン条件を変更。既存レコードの有無を先にチェックし、既存レコードがある場合は null でも更新を実行する
- **依存タスク:** なし
- **テスト方針:** 既存テストが通ることを確認。null クリアのケースをテストで追加
- **対応Issue:** #294

### タスク2: getOpponentPlayer の N+1 解消
- [ ] 完了
- **概要:** `findPlayerMatchesWithFilters()` と `getPlayerStatisticsByRank()` で対戦相手を1件ずつ取得している N+1 問題を、一括取得に変更
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchService.java` — 以下の変更:
    1. `findPlayerMatchesWithFilters()` (L103-153): フィルタリング前に全対戦相手IDを収集 → `playerRepository.findAllById()` で一括取得 → `Map<Long, Player>` に変換 → フィルタリングループ内では Map から参照
    2. `getPlayerStatisticsByRank()` (L227-315): 同様に対戦相手を一括取得してから級別統計を計算
    3. `getOpponentPlayer(MatchDto, Long)` (L158-173): Map を受け取るオーバーロードを追加、または既存メソッドをリファクタリング
- **依存タスク:** なし
- **テスト方針:** 既存テストが通ることを確認。フィルタリング結果が変わらないことを検証
- **対応Issue:** #295

### タスク3: デッドコード削除
- [ ] 完了
- **概要:** バックエンドに対応するエンドポイントが存在しない `getByDateRange()` を削除
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/api/matches.js` — `getByDateRange()` メソッド（L33-36）を削除
- **依存タスク:** なし
- **対応Issue:** #296

### タスク4: scoreDifference の「不明」表示対応
- [ ] 完了
- **概要:** scoreDifference = 0 を「枚数差不明」として扱い、フロントエンド全画面で表示を「不明」に変更
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/MatchForm.jsx` — 枚数差セレクトボックス（L802-806）で value=0 の option テキストを `不明` に変更
  - `karuta-tracker-ui/src/pages/matches/MatchList.jsx` — `getResultDisplay()` (L267-269) で scoreDifference === 0 の場合 `〇不明` / `×不明` を表示
  - `karuta-tracker-ui/src/pages/matches/MatchDetail.jsx` — (L161-165) scoreDifference === 0 の場合 `不明` を表示
  - `karuta-tracker-ui/src/pages/matches/MatchResultsView.jsx` — (L551-552) scoreDifference === 0 の場合 `不明` を表示
  - `karuta-tracker-ui/src/pages/matches/BulkResultInput.jsx` — 枚数差セレクトボックス（L566-568）で value=0 の option テキストを `不明` に変更
- **依存タスク:** なし
- **対応Issue:** #297

### タスク5: hasSessionOnDateForUser のレイヤー違反修正
- [ ] 完了
- **概要:** MatchController の private メソッド `hasSessionOnDateForUser()` を PracticeSessionService に移動し、Controller から Service 経由で呼び出すよう変更
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeSessionService.java` — `hasSessionOnDateForUser(LocalDate date, Long userId)` public メソッドを追加。ロジックは MatchController から移植（OrganizationService と PracticeSessionRepository は既に注入済み）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/MatchController.java` — 以下の変更:
    1. `PracticeSessionService` を依存に追加
    2. `hasSessionOnDateForUser()` private メソッドを削除
    3. 呼び出し箇所を `practiceSessionService.hasSessionOnDateForUser()` に変更
    4. `OrganizationService` と `PracticeSessionRepository` の直接依存（L30-31）を削除
- **依存タスク:** なし
- **テスト方針:** 既存テストが通ることを確認
- **対応Issue:** #298

### タスク6: 仕様書の更新
- [ ] 完了
- **概要:** scoreDifference の範囲記述を「0〜25」に修正し、0 = 不明の説明を追加
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` — scoreDifference の範囲を「0〜25」に修正、0 は「枚数差不明」を意味する旨を追記
  - `docs/DESIGN.md` — 必要に応じて設計書も更新
  - `docs/SCREEN_LIST.md` — 一括入力画面のアクセス権限を「全ロール」に更新（先の変更分）
- **依存タスク:** タスク4
- **対応Issue:** #299

## 実装順序

タスク1〜5 は相互に依存しないため、任意の順序で実装可能。

推奨順序:
1. タスク3（デッドコード削除 — 最小変更、リスク低）
2. タスク1（upsertPersonalNote null クリア — バックエンド小改修）
3. タスク2（N+1 解消 — バックエンドリファクタリング）
4. タスク5（レイヤー違反修正 — バックエンドリファクタリング）
5. タスク4（scoreDifference 不明表示 — フロントエンド5ファイル変更）
6. タスク6（仕様書更新 — 最後に実施）
