---
status: completed
---
# ロック以外を再シャッフル 実装手順書

> 要件: `docs/features/pairing-reshuffle-except-locked/requirements.md`（AC は §4）
> 方針: バックエンドの `autoMatch()` は既にロック除外＋残り最適化を実装済み。ロックの真を DB から
> クライアント状態へ移せるよう `auto-match` 契約を後方互換で拡張し、フロントに再シャッフル導線を足す。

## 技術設計サマリー

**契約拡張（後方互換）:** `POST /api/match-pairings/auto-match` の `AutoMatchingRequest` に
`List<LockedPairInput> lockedPairs`（各 `{player1Id, player2Id}`、nullable）を追加する。
- `lockedPairs == null`（既存の新規作成フロー）: 従来どおり DB の `hasResult` / `locked` から保持組を導出（挙動不変）。
- `lockedPairs != null`（再シャッフル）: **結果入力済み（`hasResult`）は常に DB から保護**（結果整合の防衛）。**手動ロックはクライアントの `lockedPairs` を正**とする（DB の `locked` フラグは参照しない＝ローカルで解除した組は再シャッフル対象、未保存でロックした組は保持）。`lockedPairs` の組が DB に行を持たない（未保存）場合も players を保持対象に加える。
- 保持組の players を `lockedPlayerIds` に集約し除外 → 残り（非ロック組の選手＋待機者）を既存アルゴリズム（過去30日履歴・貪欲法・同日ペア除外）で再シャッフル。保持組は `lockedPairings` として返す（DB に match があれば winner/score を enrich）。

**フロント:** 現在の画面状態から保持組 `lockedPairs = pairings.filter(hasResult||locked)` を算出して送信し、レスポンスは既存 `handleAutoMatch` と同じ整形（`lockedPairings` を先頭＋新規 `pairings` を後ろ）で反映する。

## 実装タスク

### タスク1: バックエンド — auto-match 契約拡張（lockedPairs 尊重）
- [x] 完了
- **目的:** クライアントの現在ロック状態を尊重して「ロック以外を再シャッフル」を可能にする（未保存ロック含む）。結果入力済みは常に保護。
- **対応AC:** AC-3, AC-4, AC-8, AC-9, AC-R2
- **主な変更領域:**
  - `dto/AutoMatchingRequest.java` — `List<LockedPairInput> lockedPairs` 追加（nested static class `LockedPairInput{Long player1Id; Long player2Id;}`、nullable）
  - `service/MatchPairingService.java#autoMatch()` — 保持組の導出を「result-lock は常に DB／manual-lock は lockedPairs 非null時はそれを正・null時は従来の DB `locked`」に分岐。lockedPairs の未保存組（DB 行なし）も players を保持し、**lockedPairs の player ID を `allPlayerIds` に加えて playerMap の不足を補完**（未保存組の名前解決に必須）
- **依存タスク:** なし
- **必要なテスト（テストファースト）:** `MatchPairingServiceTest`
  - lockedPairs で指定した組が保持され、その選手が再シャッフル対象から除外される
  - result-locked 組は lockedPairs に含まれなくても保護される（AC-R2）
  - lockedPairs に DB 行の無い（未保存）組を渡しても保持される（AC-4）
  - `lockedPairs == null` は従来どおり DB `locked` を保護（回帰）
  - 再シャッフル対象が奇数 → 1名 waiting（AC-8）
  - **再シャッフル前に待機者だった選手が、再シャッフル後にペアに組まれうる**（待機者が再プールされる。AC-9）
- **完了条件:** 上記テスト green、`./gradlew test` green
- **対応Issue:** #1030

### タスク2: フロント — 再シャッフル導線（動的文言・確認ダイアログ）
- [x] 完了
- **目的:** 組み合わせがある編集可能状態で「再シャッフル／ロックされた組以外をシャッフル」ボタンを提供する。
- **対応AC:** AC-1, AC-2, AC-3, AC-4, AC-5, AC-6, AC-7, AC-R1, AC-R4
- **主な変更領域:**
  - `api/pairings.js` — `autoMatch` は既に body をそのまま送るため、呼び出し側で `lockedPairs` を含めるだけ（変更最小）
  - `pages/pairings/pairingDisplayLogic.js` — 純粋関数追加: `shouldShowReshuffleButton({isReadOnly, isViewMode, pairings})`（`!isReadOnly && !isViewMode && pairings.length>0`）、`reshuffleButtonLabel(pairings)`（ロック（`hasResult||locked`）≥1→「ロックされた組以外をシャッフル」／0→「再シャッフル」）
  - `pages/pairings/pairingLockLogic.js` — 純粋関数追加: `computeLockedPairsInput(pairings)`（`filter(p => (p.hasResult||p.locked) && p.player1Id && p.player2Id).map({player1Id,player2Id})`。両選手が揃った組のみ＝null player id を送らないガード、`buildSaveRequests` と同方針）
  - `pages/pairings/PairingGenerator.jsx` — `handleAutoMatch` を共通化して `lockedPairs` を受け取れるようにし、`handleReshuffle`（`window.confirm` → `computeLockedPairsInput` を渡して autoMatch → 既存整形で反映）を追加。再シャッフルボタンを組み合わせ表示ヘッダ付近（編集可能時）に配置（既存「対戦編集」「全削除」のトーンを踏襲）
- **依存タスク:** タスク1（API 契約）
- **必要なテスト（テストファースト）:**
  - `pairingDisplayLogic.test.js` — `shouldShowReshuffleButton` の可視条件、`reshuffleButtonLabel` のロック有無による文言切替（AC-1, AC-2）
  - `pairingLockLogic` テスト — `computeLockedPairsInput`
  - `PairingGenerator.integration.test.jsx` — 組み合わせありでボタン表示、ロック時に文言切替、確認ダイアログ→再シャッフルで送信 body に lockedPairs が入る／ロック組が残る（AC-3〜7 の UI 側）
- **完了条件:** 上記テスト green、`npm run test`・`npm run lint` green
- **対応Issue:** #1031

### タスク3: ドキュメント in-place 更新（同一PR）
- [x] 完了
- **目的:** 正典ドキュメントを実装に追随させる（DoD D2）。
- **対応AC:** —（DoD ゲート）
- **主な変更領域:**
  - `docs/spec/matching.md` — 「画面の表示モード」節の *「ロック済みの組はあるが未組の参加者が残っている場合は『全削除』または手動配置で対応する」* を、再シャッフル導線に更新。自動マッチング／API 節に `lockedPairs` 契約拡張を追記
  - `docs/SCREEN_LIST.md` #19 `/pairings` — 主要子コンポーネント/説明に再シャッフルボタン（動的文言・確認ダイアログ）を追記
- **依存タスク:** タスク1・2
- **完了条件:** 変更が実装と一致、gate-dod D2 パス
- **対応Issue:** #1032

## 実装順序
1. タスク1（バックエンド契約拡張、依存なし）
2. タスク2（フロント導線、タスク1 に依存）
3. タスク3（ドキュメント、タスク1・2 に依存。同一PR・同一コミット圏）

## 補足（DB・互換）
- **DB スキーマ変更なし**（既存 `match_pairings.locked` / `matches` で判定）。本番 migration 不要。
- **後方互換:** `lockedPairs` は nullable。既存の新規作成フロー・既存呼び出しは従来挙動を維持する。
