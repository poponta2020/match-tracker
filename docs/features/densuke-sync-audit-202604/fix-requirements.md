---
status: completed
audit_source: 会話内レポート（2026-04-02）
selected_items: [1, 2, 3]
---

# 伝助同期・キャンセル待ち機能 改修要件定義書（2026-04-02 監査対応）

## 1. 改修概要

- **対象機能:** 伝助アプリ双方向同期機能およびキャンセル待ち・繰り上げ周りの一連の処理
- **改修の背景:** `/audit-feature` による監査（2026-04-02）で検出されたテストの実装乖離およびトランザクション設計の問題に対応する
- **改修スコープ:** 監査レポートの推奨アクションのうち優先度 中以上の3項目

---

## 2. 改修内容

### 2.1 項目1: WaitlistPromotionServiceTest のアサーション修正（高優先度）

- **現状の問題:** `handleSameDayJoin_noVacancy` テスト（WaitlistPromotionServiceTest.java:422）が `"先を越されました"` を期待しているが、実装（WaitlistPromotionService.java:196）は `"定員に達してしまいました..."` をスローする。テスト実行時に失敗する。
- **修正方針:** テスト側のアサーション文字列を `"定員に達してしまいました"` に修正する。実装のエラーメッセージが正しい表現であり、テストを合わせる。
- **修正後のあるべき姿:** テストが実装のエラーメッセージと一致し、正常にパスする。

### 2.2 項目2: DensukeImportServiceTest のフェーズ判定mock修正（高優先度）

- **現状の問題:** 複数のテスト（testImportCreatesSessionAndRegistersParticipants, testImportUsesExistingSession, testImportSkipsLotteryExecutedSession 等）が `lotteryExecutionRepository.findTopBySessionIdOrderByExecutedAtDesc()` をmockしているが、現在の `importFromDensuke()` はこのメソッドを直接呼ばず、`determinePhase()` 内で `lotteryDeadlineHelper` と `lotteryService` を使用する。テストが古いコードパスに依存しており、フェーズ判定が正しく検証されていない。
- **修正方針:**
  - `lotteryExecutionRepository.findTopBySessionIdOrderByExecutedAtDesc()` のmockを全テストから削除
  - Phase1前提のテストには `lotteryDeadlineHelper.getDeadlineType(1L)` → `DeadlineType.MONTHLY` および `lotteryDeadlineHelper.isBeforeDeadline(year, month, 1L)` → `true` のmockを追加
  - `testImportSkipsLotteryExecutedSession` は「Phase2スキップ」を正しく検証するよう書き直す（`isBeforeDeadline` → false, `isLotteryConfirmed` → false でPhase2判定）
- **修正後のあるべき姿:** 全テストが現行の `determinePhase()` ロジックに基づくmockを使用し、各フェーズの分岐が正しくテストされる。

### 2.3 項目3: DensukeSyncService の @Transactional スコープ分割（中優先度）

- **現状の問題:** `syncForOrganization()` が `@Transactional` で宣言されており、内部の `writeToDensuke()`（外部HTTP通信含む）と `importFromDensuke()`（外部スクレイピング含む）が1トランザクション内で実行される。外部HTTPリクエスト中にDBコネクションが長時間保持される。
- **修正方針:** `syncForOrganization()` から `@Transactional` アノテーションを削除する。`writeToDensuke()` (L72) と `importFromDensuke()` (L59) はそれぞれ自前で `@Transactional` を持っており、各メソッドの原子性は維持される。
- **リスク評価:** write成功後にimportが失敗した場合に「書き込みは反映されたがインポートされていない」状態が生じ得るが、`syncAll()` でも既に同条件で動作しており、次回の5分間隔同期で自動回復する。ユーザー確認済み。
- **修正後のあるべき姿:** 外部HTTP通信中にDBコネクションが保持されなくなり、コネクションプール枯渇リスクが低減する。

---

## 3. 技術設計

### 3.1 API変更
なし

### 3.2 DB変更
なし

### 3.3 フロントエンド変更
なし

### 3.4 バックエンド変更

| ファイル | 変更内容 |
|---------|---------|
| `WaitlistPromotionServiceTest.java` | L422: アサーション文字列修正 |
| `DensukeImportServiceTest.java` | L92, L121, L143, L166, L194, L234, L260, L301, L328, L455, L509: `lotteryExecutionRepository` mock → `lotteryDeadlineHelper` / `lotteryService` mockに置換 |
| `DensukeSyncService.java` | L31: `@Transactional` アノテーション削除 |

---

## 4. 影響範囲

- **項目1・2:** テストコードのみの変更。プロダクションコードへの影響なし。
- **項目3:** `syncForOrganization()` は `PracticeSessionController.syncDensuke()` からのみ呼び出される手動同期エンドポイント。`syncAll()`（5分間隔スケジューラー）は変更なし。トランザクション分離後も各サービスメソッドの `@Transactional` により原子性は維持される。
- **破壊的変更:** なし

---

## 5. 設計判断の根拠

- **テスト修正を実装修正より優先:** 実装のエラーメッセージ・フェーズ判定ロジックが正しく、テストが古い実装に基づいているため、テストを実装に合わせる。
- **@Transactional削除の安全性:** `syncAll()` が同じパターン（@Transactionalなし）で本番稼働中であり、実績がある。write→import間の部分失敗は5分間隔同期で自動回復する。
