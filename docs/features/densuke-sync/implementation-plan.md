---
status: completed
---
# 伝助双方向同期 実装手順書

## 実装タスク

### タスク1: DB マイグレーション + LotteryExecution エンティティ拡張
- [x] 完了
- **概要:** 抽選確定フローの基盤。`lottery_executions` テーブルに `confirmed_at`, `confirmed_by` を追加し、エンティティに反映
- **変更対象ファイル:**
  - `database/` — ALTER TABLE マイグレーションSQL追加
  - `karuta-tracker/src/.../entity/LotteryExecution.java` — `confirmedAt` (LocalDateTime), `confirmedBy` (Long) フィールド追加
- **依存タスク:** なし
- **対応Issue:** #191

### タスク2: DensukeScraper — memberNames の公開
- [x] 完了
- **概要:** スクレイピング結果に全メンバー名リストを含める。×/空白の判定に使用
- **変更対象ファイル:**
  - `karuta-tracker/src/.../service/DensukeScraper.java`
    - `DensukeData` クラスに `List<String> memberNames` フィールド追加（行25-27付近）
    - `scrape()` メソッド内で `data.setMemberNames(memberNames)` を追加（行80付近）
- **依存タスク:** なし
- **対応Issue:** #192

### タスク3: DensukeWriteService — toJoinValue 変更 + 一括書き戻しメソッド
- [x] 完了
- **概要:** 未登録者の書き戻し値を空白→×に変更。抽選確定時の一括書き戻しメソッドを追加
- **変更対象ファイル:**
  - `karuta-tracker/src/.../service/DensukeWriteService.java`
    - `toJoinValue()` (行587-594): `null` の場合の戻り値を `0` → `1` に変更
    - 新メソッド `writeAllForLotteryConfirmation(Long organizationId, int year, int month)`: 伝助マッピングがある全プレイヤーについて dirty に関係なく書き戻し。完了後に全レコードの dirty=false に更新
- **依存タスク:** なし
- **対応Issue:** #193

### タスク4: PracticeParticipantService — 論理削除化
- [x] 完了
- **概要:** 参加取消時の物理削除を論理削除（CANCELLED + dirty=true）に変更
- **変更対象ファイル:**
  - `karuta-tracker/src/.../service/PracticeParticipantService.java`
    - `registerParticipations()` 内の `deleteByPlayerIdAndSessionIds()` 呼び出し（行154, 193付近）→ ステータスを CANCELLED + dirty=true に更新する処理に変更
    - `setMatchParticipants()` 内の `deleteBySessionIdAndMatchNumber()` 呼び出し（行69付近）→ 同様に論理削除化
  - `karuta-tracker/src/.../repository/PracticeParticipantRepository.java` — 論理削除用の更新メソッド追加（既存の物理削除メソッドは残す）
- **注意:** 論理削除後のレコードが重複チェック（`existsBySessionIdAndPlayerIdAndMatchNumber`）に引っかからないよう、チェック条件に `status != CANCELLED` 等を追加する必要あり
- **依存タスク:** なし
- **対応Issue:** #194

### タスク5: WaitlistPromotionService — 伝助連携用メソッド追加
- [x] 完了
- **概要:** 伝助からの操作に対応する新メソッドを追加
- **変更対象ファイル:**
  - `karuta-tracker/src/.../service/WaitlistPromotionService.java`
    - 新メソッド `demoteToWaitlist(Long participantId)`: WON → WAITLISTED（最後尾）に変更し、WON枠を開放して `promoteNextWaitlisted()` を呼ぶ（3-B2 用）
    - 3-C6（OFFERED + 伝助×）: 既存の `respondToOffer(participantId, false)` を再利用可能。新メソッド不要
    - 3-A8（OFFERED + 伝助○）: 既存の `respondToOffer(participantId, true)` を再利用可能。ただしオファー期限チェックを含むため、期限内判定を事前に行う
- **依存タスク:** なし
- **対応Issue:** #195

### タスク6: LotteryService + LotteryController — 抽選確定フロー
- [x] 完了
- **概要:** 抽選結果の確定APIを追加。確定時に一括書き戻しをトリガー
- **変更対象ファイル:**
  - `karuta-tracker/src/.../service/LotteryService.java`
    - 新メソッド `confirmLottery(int year, int month, Long confirmedBy, Long organizationId)`: LotteryExecution の confirmedAt/confirmedBy を設定し、`DensukeWriteService.writeAllForLotteryConfirmation()` を呼び出し
  - `karuta-tracker/src/.../controller/LotteryController.java`
    - 新エンドポイント `POST /api/lottery/confirm` (SUPER_ADMIN 権限)
  - `karuta-tracker/src/.../dto/` — 確認用リクエスト/レスポンス DTO 追加
- **依存タスク:** タスク1, タスク3
- **対応Issue:** #196

### タスク7: DensukeImportService — フェーズ別インポートロジック
- [x] 完了
- **概要:** 伝助→アプリのインポートをフェーズ別に分岐。○/△/×の全パターンを処理。本機能の中核
- **変更対象ファイル:**
  - `karuta-tracker/src/.../service/DensukeImportService.java` — ほぼ全面書き換え
    - `importFromDensuke()` にフェーズ判定を追加（LotteryDeadlineHelper + LotteryExecutionRepository の confirmedAt を使用）
    - フェーズ1: ○ → PENDING 作成 / not-○ → 既存削除 or 無視。書き戻し不要
    - フェーズ2: 全てスキップ
    - フェーズ3: ○/△/× の全マトリクス（3-A1〜3-C9）を実装
      - `memberNames` を使って×/空白の人を判定
      - dirty=true のレコードは一切スキップ
      - 繰り上げ処理は `WaitlistPromotionService` のメソッドを呼び出し
    - SAME_DAY型: フェーズ1（WON/WAITLISTED）/ フェーズ3 の2分岐
- **依存タスク:** タスク1, タスク2, タスク4, タスク5
- **対応Issue:** #197

### タスク8: DensukeSyncService — フェーズ判定統合
- [ ] 完了
- **概要:** 同期フローにフェーズ判定を組み込み、フェーズ2ではインポートをスキップ
- **変更対象ファイル:**
  - `karuta-tracker/src/.../service/DensukeSyncService.java`
    - `syncForOrganization()` / `syncAll()`: フェーズ2（締切後・抽選確定前）のセッションではインポートをスキップ
    - LotteryDeadlineHelper と LotteryExecutionRepository を注入してフェーズ判定
- **注意:** フェーズ判定はセッション（日付）単位ではなく月単位で行う。同一月内の全セッションが同じフェーズ
- **依存タスク:** タスク7
- **対応Issue:** #198

### タスク9: フロントエンド — 抽選確定ボタン
- [ ] 完了
- **概要:** 抽選結果画面に「確定」ボタンを追加。確定済みかどうかの表示
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/lottery/` — 抽選結果画面に確定ボタン追加
  - `karuta-tracker-ui/src/api/` — confirm API 呼び出し追加
- **依存タスク:** タスク6
- **対応Issue:** #199

## 実装順序

```
タスク1（DB + Entity）─┐
タスク2（Scraper）─────┤
タスク3（WriteService）─┼─→ タスク6（確定フロー）─→ タスク9（フロントエンド）
タスク4（論理削除）─────┤
タスク5（Promotion）───┘
         ↓
    タスク7（ImportService）
         ↓
    タスク8（SyncService）
```

1. **タスク1〜5**（並列可能）: 基盤となる変更。互いに依存しない
2. **タスク6**: タスク1, 3 に依存。確定フローの実装
3. **タスク7**: タスク1, 2, 4, 5 に依存。中核ロジック
4. **タスク8**: タスク7 に依存。統合
5. **タスク9**: タスク6 に依存。UI
