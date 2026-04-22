---
status: completed
---

# 対戦組み合わせ画面 当日重複ペア警告表示 改修実装手順書

## 実装タスク

### タスク1: バックエンド - 当日他試合検知ロジックの修正
- [x] 完了
- **概要:** `MatchPairingService.java` に3箇所ある「同日の前の試合番号のみ検知」条件を「自分の試合番号以外すべて検知」に修正する。表示用の `recentMatches` 生成ロジックに同じ修正パターンを3箇所適用。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchPairingService.java`
    - L385-L397 `getPairRecentMatches` 内 — `matchNumber > 1` 条件を除去し、`< matchNumber` → `!= matchNumber` に変更
    - L822-L834 `enrichWithRecentMatches` 内 — 同上の修正
    - L524-L537 `autoMatch` 内 recentMatches 生成部分 — 同上の修正
- **依存タスク:** なし
- **対応Issue:** #512

---

### タスク2: フロントエンド - 「⚠今日」警告表示の実装
- [x] 完了
- **概要:** `PairingGenerator.jsx` の直近対戦日表示部分で、`recentMatches[0].matchDate === sessionDate` のとき `⚠今日` を赤字太字で表示するように変更。表示枠幅を `w-12` から `w-14` に拡張。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx`
    - L963-L970 表示ブロック — 当日判定の条件分岐追加、スタイル変更、枠幅拡張
- **依存タスク:** なし（タスク1とは独立して実装可能。ただし結合動作確認はタスク1完了後）
- **対応Issue:** #513

---

### タスク3: バックエンドテスト - 当日他試合検知の動作確認
- [x] 完了
- **概要:** `getPairRecentMatches` / `enrichWithRecentMatches` / `autoMatch` について、「試合3編集中に試合1・試合2・試合4・試合5のペアを検知する」ケースのテストを追加（存在しないメソッドについては新規追加、既存テストで近いケースがあれば拡張）。
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/MatchPairingServiceTest.java` — テストケース追加
    - 「同日の後方試合番号のペアが `recentMatches` に含まれる」
    - 「自分の試合番号のペアは `recentMatches` に含まれない」
    - `matchNumber=1` でも同日他試合のペアを検知する
- **依存タスク:** タスク1 (#512)
- **対応Issue:** #514

---

### タスク4: フロントエンドテスト確認・必要に応じて調整
- [x] 完了
- **概要:** `PairingGenerator.integration.test.jsx` の既存アサーションで表示形式に依存しているものがないか確認。あれば調整。必要に応じて「当日ペアで `⚠今日` が表示される」ケースを追加。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/pairings/PairingGenerator.integration.test.jsx` — 必要に応じて修正/追加
- **依存タスク:** タスク2 (#513)
- **対応Issue:** #515

---

### タスク5: ドキュメント更新
- [x] 完了
- **概要:** CLAUDE.md のドキュメント更新ルールに従い、改修内容を仕様書・設計書・画面一覧に反映する。
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` — 対戦組み合わせ画面の「直近対戦日表示」仕様に「当日重複ペアは ⚠今日 表示」と「同日他試合全体を検知対象とする」旨を追記
  - `docs/SCREEN_LIST.md` — 対戦組み合わせ画面の説明に変更点があれば反映（表示仕様の軽微な変更のため更新範囲は小さい）
  - `docs/DESIGN.md` — `MatchPairingService` の `recentMatches` 生成ロジックの説明を更新
- **依存タスク:** タスク1 (#512), タスク2 (#513)
- **対応Issue:** #516

---

## 実装順序

1. **タスク1**（バックエンド修正） — 独立、先行可能
2. **タスク2**（フロントエンド修正） — 独立、先行可能（タスク1と並行可）
3. **タスク3**（バックエンドテスト） — タスク1完了後
4. **タスク4**（フロントエンドテスト） — タスク2完了後
5. **タスク5**（ドキュメント更新） — タスク1・タスク2完了後

効率的には、タスク1とタスク2は独立しているため並行実装可能。PR分割は1つにまとめるのが適切（影響範囲が狭く、フロント/バック両方の協調変更のため）。

---

## 動作確認シナリオ（PR作成時の手動テスト）

1. 過去日（例: 2日前）に A vs B で組まれた練習データを用意
2. 当日の試合1で A vs B を組み、保存
3. 当日の試合2で A vs B を組む → 表示枠に **⚠今日** が赤字太字で表示されること
4. 当日の試合5で C vs D を組み、保存
5. 当日の試合3に戻り、C vs D を組む → 表示枠に **⚠今日** が赤字太字で表示されること（前方試合番号 1-2 も後方 4-5 も両方検知されることの確認）
6. 試合3の別のペア E vs F を組む（過去日に対戦履歴あり） → 従来通り `MM/DD` 表示
7. 試合3の別のペア G vs H を組む（初対戦） → `初` 表示
