---
status: completed
---
# 組み合わせ作成：ロックの明示保存化＆使い方ヘルプ 実装手順書

## 実装タスク

### タスク1: バックエンド — 保存APIに `locked` を追加
- [x] 完了
- **概要:** `createBatch`（一括保存）がロック状態を持てるようにする。保存時の保護対象を「結果入力済み(hasResult)のみ」に変更し、手動ロック組はリクエストから `locked` 付きで再作成する。これにより「ロック」「解除」の両方が保存で永続化される。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/MatchPairingCreateRequest.java` — `private Boolean locked;` を追加（null は false 扱い）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchPairingService.java` — `createBatch` の保護判定を `isLockedPairing`（hasResult OR locked）から `hasResult` のみに変更。新規ペア構築に `.locked(Boolean.TRUE.equals(request.getLocked()))` を追加。`auto-match` / `deleteByDateAndMatchNumber` の保護判定は変更しない
  - `karuta-tracker/src/test/java/.../MatchPairingServiceTest.java`（該当テスト） — ①createBatch が `locked` を反映 ②保存時保護が hasResult のみ（手動ロック組は削除→再作成）③`locked=false` での再保存（解除）が反映される、の回帰テストを追加
- **依存タスク:** なし
- **対応Issue:** #948

### タスク2: フロントエンド — ロック/解除のローカル化・保存への `locked` 同梱・ロックボタンのアイコン化
- [x] 完了
- **概要:** 鍵アイコン/「解除」を即時サーバ反映からローカル状態トグルに変更し、「確定して保存」時に `locked` 込みで保存する。ロックボタンの「ロック」テキストを削除しアイコンのみにする。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/api/pairings.js` — `createBatch` の各 pairing 要素に `locked` を同梱（`{ player1Id, player2Id, locked }`）
  - `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx`:
    - `handleLockPairing` → ローカルで対象組の `locked=true`、`setHasUnsavedChanges(true)`、`saveDraft`。`createBatch`/`pairingAPI.lock`/再取得・`hasIncompleteUnlockedPairings` 無効化を削除
    - `handleUnlockPairing` → ローカルで `locked=false`、状態更新。`pairingAPI.unlock`/再取得/`pairing.id` 必須条件を削除
    - 「解除」ボタン表示条件から `pairing.id` を外す（`!isReadOnly && !isViewMode && pairing.locked`）
    - `handleSave` のフィルタを `!(p.hasResult || p.locked)` → `!p.hasResult` に変更し、`locked: !!p.locked` を付与。空判定を「完成した組（ロック含む）が0かつ待機者0」に一般化
    - ロックボタン: 「ロック」テキスト削除（`Lock` アイコンのみ）、`aria-label="ロック"`・`title` 付与
  - `karuta-tracker-ui/src/pages/pairings/PairingGenerator.integration.test.jsx` — ①ロック/解除でAPIを呼ばない ②保存時に `locked` を送る ③ロックボタンにテキストが無い ④`id` 無しのロック組も解除できる、の回帰テストを追加/更新
- **依存タスク:** タスク1（#948 / API契約）
- **対応Issue:** #949

### タスク3: フロントエンド — 使い方ヘルプ（ⓘ）追加
- [ ] 完了
- **概要:** 画面上部に `Info` ボタン＋ドロップダウンの使い方パネルを追加。初回訪問時のみ自動表示（`localStorage`）。カレンダーの「記号の見方」を踏襲。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx`:
    - `showHelp` state（初期値は `localStorage('pairingHelpSeen')` 未設定なら true、例外時 true フォールバック）
    - ローディング完了後に既読フラグ保存、外側タップ/✕で閉じる `useEffect`・`ref`
    - `PageHeader` 直下・右寄せに `Info` ボタン＋ドロップダウンパネル（4セクション: 選手の入れ替え方／ロックの意味と使い方／保存の流れ／日付列の見方）
  - `karuta-tracker-ui/src/pages/pairings/PairingGenerator.help.test.jsx`（新規） — パネルの開閉・初回自動表示・各セクション見出しの表示を検証
- **依存タスク:** タスク2（#949 / 同一ファイルのため、コンフリクト回避で後続）
- **対応Issue:** #950

### タスク4: ドキュメント更新
- [ ] 完了
- **概要:** 仕様・画面・設計ドキュメントに、ロックの明示保存フローと使い方ヘルプを反映する。
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` — 組み合わせ作成のロック保存フロー（下書き→確定して保存）を更新
  - `docs/SCREEN_LIST.md` — 組み合わせ作成画面に使い方ヘルプ（ⓘ）を追記
  - `docs/DESIGN.md` — `createBatch` の `locked` 同梱・保護判定変更、ヘルプUIを反映
- **依存タスク:** タスク1〜3（#948, #949, #950）
- **対応Issue:** #951

## 実装順序
1. タスク1（#948・依存なし・API契約を確定）
2. タスク2（#949・タスク1に依存）
3. タスク3（#950・タスク2と同一ファイルのため後続）
4. タスク4（#951・実装完了後、同一コミット群でドキュメント反映）

## 補足
- **DBスキーマ変更なし**（`match_pairings.locked` は適用済み）。マイグレーション・本番DB適用は不要。
- PATCH `/match-pairings/{id}/lock` `/unlock` エンドポイントと `pairings.js` の `lock`/`unlock` は未使用化するが、本変更では**残置**（将来クリーンアップ候補）。
