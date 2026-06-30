---
status: draft
---
# 対戦組み合わせへのキャンセル反映（pairing-cancelled-opponent）実装手順書

方式は read-time（非破壊）。DBスキーマ変更なし・マイグレーション不要。詳細は [requirements.md](./requirements.md) / [design-spec.md](./design-spec.md)。

## 実装タスク

### タスク1: バックエンド — キャンセル状態をDTOに付与（read-time enrich）
- [x] 完了
- **概要:** 取得API応答の各組に「その選手がその試合でキャンセル済みか」のフラグを付ける。`match_pairings` は非破壊。
- **変更対象ファイル:**
  - `karuta-tracker/.../dto/MatchPairingDto.java` — `boolean player1Cancelled` / `boolean player2Cancelled` を追加（デフォルト false）
  - `karuta-tracker/.../repository/PracticeParticipantRepository.java` — 指定日のセッションで `status = CANCELLED` の参加者を `(player_id, match_number)` 単位で取得するクエリを追加（1クエリでまとめ取得、N+1回避）
  - `karuta-tracker/.../service/MatchPairingService.java` — `enrichWithCancellation(dtos, sessionDate, matchNumber, organizationId)` を追加し、`getByDate` / `getByDateAndMatchNumber` のDTO構築後に呼ぶ。`(playerXId, matchNumber)` がキャンセル集合にあれば `playerXCancelled = true`。組織スコープ（`organizationId`）と null（SUPER_ADMIN/PLAYER・同日複数団体）の両対応は既存 `getSessionAllPlayerIds` / セッション解決と整合させる
  - `karuta-tracker/src/test/.../service/MatchPairingServiceTest.java` — 片方/両方キャンセル・別試合は非影響・組織スコープのケース
- **依存タスク:** なし

### タスク2: フロント — 閲覧モードのキャンセル表示
- [x] 完了
- **概要:** 閲覧モードで、キャンセル相手を「取消線＋gray-400名＋右端の丸タグ」で表示。両方キャンセルの組は非表示。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx`
    - `loadExistingPairingsToState`（L110-144）で `player1Cancelled` / `player2Cancelled` を state に引き継ぐ
    - 閲覧モード描画（L1045-1051）：**両方キャンセルの行は描画しない**／片方キャンセルは既存「結果入力済」行と**同一構造**（`flex items-center gap-2` > `flex-1 ... justify-center` ペア＋右端タグ）。キャンセル名 `font-medium text-gray-400 text-sm line-through`、タグ `flex items-center gap-1 text-xs bg-gray-100 text-gray-600 px-2 py-0.5 rounded-full whitespace-nowrap` ＋ `Ban`（w-3 h-3）＋「キャンセル」
  - `karuta-tracker-ui/src/pages/pairings/pairingDisplayLogic.js`（既存）— 「両キャンセルは非表示」「どちらがキャンセルか」等の判定を純粋関数に切り出し、本番・テスト共有
  - `karuta-tracker-ui/src/pages/pairings/PairingGenerator.integration.test.jsx` — 片方/両方キャンセル表示・非表示の回帰
- **依存タスク:** タスク1

### タスク3: フロント — 編集モードでキャンセル者を「空き」に
- [x] 完了
- **概要:** 編集モードではキャンセル者のスロットを「空き」扱い。キャンセル者は参加者プール・待機リストに出ない。保存しても生存側の選手は消えない（アクティブ参加者として残る）。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx` — 編集モードでキャンセルスロットを空き化（`playerXId` を空に）。キャンセル組は未完成として `buildSaveRequests` / `hasNothingToSave` の対象外（両選手揃った組のみ送信、の既存条件で自然に除外される。必要なら明示）。両キャンセル組は編集モードでも除去
  - `karuta-tracker-ui/src/pages/pairings/pairingLockLogic.js`（必要時）
  - `karuta-tracker-ui/src/pages/pairings/PairingGenerator.integration.test.jsx` — 「キャンセル者は空き表示」「保存で生存者が消えない」ケース
- **依存タスク:** タスク1

### タスク4: ドキュメント更新
- [ ] 完了
- **概要:** CLAUDE.md ルールに従い仕様・画面・設計ドキュメントへ反映。
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` / `docs/SCREEN_LIST.md` / `docs/DESIGN.md` — 対戦組み合わせのキャンセル反映（閲覧＝表示・編集＝空き）を追記
- **依存タスク:** タスク1〜3

## 実装順序
1. タスク1（バックエンド・依存なし）
2. タスク2（閲覧表示・タスク1依存）
3. タスク3（編集モード・タスク1依存）
4. タスク4（ドキュメント・実装後）
