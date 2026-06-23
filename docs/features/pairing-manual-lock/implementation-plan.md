---
status: completed
---
# 対戦組み合わせ手動ロック（pairing-manual-lock）実装手順書

## 前提・全体方針
- 既存の結果ロック（`hasResult`）の保護ロジックを「`hasResult OR locked`」へ一般化して再利用する。
- 機能は概ね1PRで完結する見込みだが、追跡のためタスク分割する。**DBマイグレーションと entity 変更は同一PRに含める**（CLAUDE.md 最重要ルール）。
- ロックバッジ文言: 手動ロック=「🔒 ロック」 / 結果ロック=既存「結果入力済」（据え置き）。
- lock/unlock は別エンドポイント2本（`PATCH /{id}/lock`・`/unlock`）。

---

## 実装タスク

### タスク1: DBマイグレーション + Entity/DTO に `locked` 追加（基盤）
- [x] 完了
- **概要:** `match_pairings` に `locked` カラムを追加し、entity / DTO に反映する。後続タスクの土台。
- **変更対象ファイル:**
  - `database/add_locked_to_match_pairings.sql`（新規）— `ALTER TABLE match_pairings ADD COLUMN locked BOOLEAN NOT NULL DEFAULT FALSE;`
  - `karuta-tracker/.../entity/MatchPairing.java` — `@Column(name = "locked", nullable = false) @Builder.Default private Boolean locked = false;`
  - `karuta-tracker/.../dto/MatchPairingDto.java` — `private boolean locked;` 追加
  - `karuta-tracker/.../service/MatchPairingService.java` — `convertToDto()` / `convertToDtoWithCache()` で `locked` をマッピング
- **本番対応:** マージ前後で **本番DB（Render PostgreSQL）へ SQL を適用**し、`\d match_pairings` で反映確認。
- **依存タスク:** なし
- **完了条件:** entity/DTO に `locked` が入り、既存ペアリング取得APIのレスポンスに `locked`（既存データは false）が含まれる。本番DBにカラムが存在する。
- **対応Issue:** #902

### タスク2: lock/unlock エンドポイント + Service（二重ブッキング検証）
- [x] 完了
- **概要:** 組単位のロック/解除APIを追加。lock 時に「1選手1組」をサーバーで担保する。
- **変更対象ファイル:**
  - `karuta-tracker/.../controller/MatchPairingController.java` — `PATCH /{id}/lock`・`PATCH /{id}/unlock` を追加（`@RequireRole({SUPER_ADMIN, ADMIN, PLAYER})`、`validateScopeByPairingId` 適用、戻り値 `MatchPairingDto`）
  - `karuta-tracker/.../service/MatchPairingService.java` — `lock(Long id)` / `unlock(Long id)`。`lock` は `findBySessionDateAndMatchNumber` で同回戦の他組を取得し、対象2選手の重複があれば例外（409 相当）
- **依存タスク:** タスク1
- **完了条件:** ロック/解除でDBの `locked` が切り替わる。二重ブッキング時はエラー。組織スコープ外は403、存在しないIDは404。
- **対応Issue:** #903

### タスク3: ロック判定の一般化（createBatch / autoMatch / 回戦削除）
- [ ] 完了
- **概要:** 保護対象判定を `hasResult` から `hasResult OR locked` に一般化し、手動ロック組も保持・除外対象に含める。
- **変更対象ファイル:**
  - `karuta-tracker/.../service/MatchPairingService.java`
    - `createBatch()`（223-235行のロック検出）— `locked` も保護対象に。共通ヘルパ `isLocked(pairing, matches)` 導入を推奨
    - `autoMatch()`（ロック検出箇所）— 手動ロック選手を除外、`lockedPairings` に手動ロック組も含めて返す
    - `deleteByDateAndMatchNumber()` — 手動ロック組は削除しない
- **依存タスク:** タスク1
- **完了条件:** 手動ロック組と両選手が、自動組み合わせ・一括保存・回戦削除で保護される。`locked=false` の既存挙動は不変。
- **対応Issue:** #904

### タスク4: フロントエンド（API クライアント + PairingGenerator UI）
- [ ] 完了
- **概要:** 鍵ボタン/解除ボタン、バッジ表示、保護判定の一般化を実装する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/api/pairings.js` — `lock(id)` / `unlock(id)`（PATCH）追加
  - `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx`
    - pairing オブジェクトに `locked` を反映
    - ロックボタン（未ロック・編集可能組: lock は未保存なら `createBatch` 保存→`lock`）／解除ボタン（手動ロック組: `unlock`→再取得）
    - 保護判定を `p.hasResult` → `(p.hasResult || p.locked)` に一般化（`handleSave` フィルタ 357/368-369行、ドラッグ可否、表示分岐）
    - `handleAutoMatch`（325-328行）— `lockedPairings` を `hasResult:true` 固定にせず、DTO の `hasResult`/`locked` を尊重
    - 手動ロック「🔒 ロック」バッジと結果ロック「結果入力済」を出し分け
- **依存タスク:** タスク2, タスク3
- **完了条件:** 画面から組をロック/解除でき、ロック組が保護される。結果ロックの既存表示・挙動が回帰しない。
- **対応Issue:** #905

### タスク5: テスト
- [ ] 完了
- **概要:** バックエンド・フロントのテストを追加し、CI カバレッジ（60%）を満たす。
- **変更対象ファイル:**
  - バックエンド: `MatchPairingService` / `MatchPairingController` のテスト — lock/unlock、二重ブッキング、createBatch/autoMatch/回戦削除の `locked` 保持、組織スコープ
  - フロント: `karuta-tracker-ui/src/pages/pairings/PairingGenerator.integration.test.jsx` — ロック表示・操作・自動組み合わせ保持の検証を追加
- **依存タスク:** タスク2, タスク3, タスク4
- **完了条件:** 主要パスのテストが通り、CI が緑。
- **対応Issue:** #906

### タスク6: ドキュメント更新
- [ ] 完了
- **概要:** 実装内容をドキュメントへ反映する（CLAUDE.md ドキュメント更新ルール）。
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` — 手動ロック仕様
  - `docs/SCREEN_LIST.md` — `/pairings` のロック操作
  - `docs/DESIGN.md` — `locked` カラム・lock/unlock API・判定一般化
- **依存タスク:** タスク1〜4
- **完了条件:** 3ドキュメントに手動ロックが反映され、実装コードと同一コミット/PRに含まれる。
- **対応Issue:** #907

---

## 実装順序
1. **タスク1**（基盤: DBマイグレーション + Entity/DTO）※本番DB適用を忘れない
2. **タスク2**（lock/unlock API + 二重ブッキング検証）／ **タスク3**（ロック判定の一般化）— タスク1完了後、並行可
3. **タスク4**（フロントエンド）— タスク2・3完了後
4. **タスク5**（テスト）— タスク2・3・4と並行〜直後
5. **タスク6**（ドキュメント更新）— 実装確定後、同一PRに含める
