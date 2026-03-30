---
status: completed
---
# 級未設定ユーザーの「初心者」表示 実装手順書

## 実装タスク

### タスク1: 選手一覧の「初心者」バッジ表示
- [ ] 完了
- **概要:** PlayerList.jsx で級がNULLの場合に「初心者」バッジを表示する
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/players/PlayerList.jsx` — `player.kyuRank && (...)` の条件を変更し、NULLの場合に「初心者」テキストのバッジを表示
- **依存タスク:** なし
- **対応Issue:** #122

### タスク2: プロフィール表示・選手詳細の級位行表示
- [ ] 完了
- **概要:** Profile.jsx と PlayerDetail.jsx で級がNULLの場合に「初心者」と表示する
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/Profile.jsx` — 情報カードの級位 value を `player.kyuRank || '初心者'` に変更
  - `karuta-tracker-ui/src/pages/players/PlayerDetail.jsx` — 同上
- **依存タスク:** なし
- **対応Issue:** #123

### タスク3: 対戦一覧の級位表示
- [ ] 完了
- **概要:** MatchList.jsx で級がNULLの場合に「初心者」と表示する
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/MatchList.jsx` — ナビゲーションヘッダーと検索結果での級位表示で、NULLの場合に「初心者」を表示
- **依存タスク:** なし
- **対応Issue:** #124

### タスク4: 組み合わせ生成・選手編集のラベル変更
- [ ] 完了
- **概要:** PairingGenerator.jsx の「未設定」を「初心者」に、PlayerEdit.jsx の選択肢ラベルを変更する
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx` — `'未設定'` フォールバックを `'初心者'` に変更
  - `karuta-tracker-ui/src/pages/players/PlayerEdit.jsx` — `<option value="">未設定</option>` を `<option value="">初心者</option>` に変更
- **依存タスク:** なし
- **対応Issue:**

- **対応Issue:** #125

## 実装順序
1. タスク1〜4は全て独立しており、任意の順序で実装可能
