---
status: completed
---

# 抽選機能 改修実装手順書（v3）

## 実装タスク

### タスク1: `findMonthlyLoserPlayerIds` に組織フィルタを追加
- [x] 完了
- **概要:** 再抽選時に別団体の落選経験者が「優先当選救済枠」に混入しないよう、JPQL に `organizationId` 条件を追加し呼び出し側でも引数を渡す。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/PracticeParticipantRepository.java` — `findMonthlyLoserPlayerIds` のシグネチャに `Long organizationId` 引数を追加し JPQL に `AND ps.organizationId = :organizationId` を追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryService.java` — `reExecuteLottery` 内の呼び出し（L828-829）でセッションの `organizationId` を渡す
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/repository/PracticeParticipantRepositoryTest.java`（必要なら新規作成）— 別団体の落選者が結果に含まれないリグレッションテストを追加
- **依存タスク:** なし
- **対応Issue:** #583

### タスク2: 抽選結果系 API に組織スコープを追加
- [x] 完了
- **概要:** `/lottery/results`, `/my-results`, `/executions` 3 エンドポイントに `organizationId` クエリパラメータを追加し、PLAYER は所属団体・ADMIN は自団体に強制絞り込みする。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java` — 3 エンドポイントの引数追加とスコープ補正ロジックを実装
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/LotteryExecutionRepository.java` — `findByTargetYearAndTargetMonthAndOrganizationId(int, int, Long)` を追加
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/controller/LotteryControllerTest.java`（あれば）— PLAYER の他団体アクセスが除外されるテスト追加
- **依存タスク:** なし
- **対応Issue:** #584

### タスク3: `LotteryResults.jsx` の確定ボタン削除
- [x] 完了
- **概要:** seed なしで `confirm` を呼ぶ dead UI を削除し、`/lottery/results` を閲覧専用に純化する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/lottery/LotteryResults.jsx` — `confirmed` / `lotteryExecuted` state、`fetchConfirmStatus`、`handleConfirmLottery`、確定ボタン JSX、`useEffect` の `fetchConfirmStatus` 呼び出しを削除
- **依存タスク:** なし
- **対応Issue:** #585

### タスク4: 確定時の伝助書き戻し失敗をレスポンスに伝搬
- [x] 完了
- **概要:** `executeAndConfirmLottery` の戻り値を `ConfirmLotteryResponse` DTO に変更し、伝助書き戻し失敗をレスポンスに含める。フロントで失敗時に警告表示。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/ConfirmLotteryResponse.java` — 新規作成（`execution`, `densukeWriteSucceeded`, `densukeWriteError` を持つ DTO）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryService.java` — `executeAndConfirmLottery` を `ConfirmLotteryResponse` 戻り値に変更
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java` — `confirmLottery` エンドポイントの戻り値を `ConfirmLotteryResponse` に変更
  - `karuta-tracker-ui/src/pages/lottery/LotteryManagement.jsx` — `confirm` レスポンスの `densukeWriteSucceeded` を見て失敗時に警告 alert を出す
- **依存タスク:** なし
- **対応Issue:** #586

### タスク5: `LotteryExecution.getPriorityPlayerIds()` に log.warn 追加
- [x] 完了
- **概要:** JSON parse 失敗が silent swallow されている箇所に log.warn を追加。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/LotteryExecution.java` — `@Slf4j` アノテーション追加。`getPriorityPlayerIds`/`setPriorityPlayerIds` の catch 句に `log.warn` を追加
- **依存タスク:** なし
- **対応Issue:** #587

### タスク6: 仕様書の自動抽選スケジューラ記述を実態に合わせる
- [x] 完了
- **概要:** §3.7.1 と §3.7.7 の自動抽選記述に「現在停止中」を明記する。
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` — §3.7.1 の「自動抽選」フロー記述を「現在停止中、手動運用のみ」に更新。§3.7.7 のスケジューラ表で `LotteryScheduler` の行に「現在停止中」を追記
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/LotteryScheduler.java` — クラスコメントに「現在停止中」を補足（既存 L40 のコメントを拡充）
- **依存タスク:** なし
- **対応Issue:** #588

## 実装順序

1. **タスク1**: 単独で完結（バックエンドのみ・テスト含む）
2. **タスク5**: 単独で完結（軽微・log 追加のみ）
3. **タスク6**: 単独で完結（ドキュメント中心）
4. **タスク2**: バックエンド + 軽微なフロント影響
5. **タスク3**: フロントのみ・他タスクと独立
6. **タスク4**: バックエンド DTO 新規 + フロントエンド表示連携（最後にやることで他タスクの影響を吸収しやすい）

実装は **タスク 1 → 5 → 6 → 2 → 3 → 4** の順で進める。各タスク完了ごとに `./gradlew test --tests <該当テストクラス>` で回帰確認する。
