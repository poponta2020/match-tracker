---
status: completed
---
# 級未設定ユーザーの「初心者」表示 要件定義書

## 1. 概要
- **目的:** 級（kyuRank）が未設定のユーザーを「初心者」として表示する
- **背景・動機:** 新規登録ユーザーは級が未設定（NULL）となるが、現在はラベルが空欄や「未設定」と表示され、わかりにくい。「初心者」と表示することで、ユーザーの立場を明確にする

## 2. ユーザーストーリー
- **対象ユーザー:** 全ユーザー（選手一覧・詳細画面などを閲覧する全員）
- **ユーザーの目的:** 級が未設定のユーザーが初心者であることを画面上で認識できる
- **利用シナリオ:** 選手一覧や詳細画面で、級が未設定のユーザーに「初心者」と表示される

## 3. 機能要件

### 3.1 画面仕様

表示ラベルの変更のみ。以下の画面で級がNULLの場合に「初心者」と表示する。

| 画面 | ファイル | 現状 | 変更後 |
|------|---------|------|--------|
| 選手一覧 | `PlayerList.jsx` | 級バッジ非表示 | 「初心者」バッジ表示 |
| プロフィール表示 | `Profile.jsx` | 級位行が非表示 | 級位「初心者」と表示 |
| 選手詳細 | `PlayerDetail.jsx` | 級位行が非表示 | 級位「初心者」と表示 |
| 対戦一覧 | `MatchList.jsx` | 級位非表示 | 「初心者」と表示 |
| 組み合わせ生成 | `PairingGenerator.jsx` | 「未設定」と表示 | 「初心者」と表示 |
| 選手編集（管理者） | `PlayerEdit.jsx` | `<option value="">未設定</option>` | `<option value="">初心者</option>` |

#### 変更しない箇所
- **PlayerChip枠線色:** 級未設定時は `border-gray-200`（グレー）のまま
- **Home.jsx / PracticeDetail.jsx / PracticeList.jsx / MatchParticipantsEditModal.jsx:** PlayerChipで名前のみ表示しており、級ラベルを表示していないため変更不要
- **ProfileEdit.jsx:** 級の選択肢に「初心者」は追加しない（ユーザーは既存のE級〜A級から選択する）
- **フィルタ（FilterBottomSheet.jsx）:** 「初心者」選択肢は追加しない。「全ての級」でのみ表示
- **級別統計:** 「初心者」は統計対象外のまま
- **ソート順:** E級の下（最後尾）のまま変更なし

### 3.2 ビジネスルール
- 級が未設定（NULL）= 初心者。DBやenumへの追加は行わない
- 表示ラベルの変更のみであり、ロジック（ソート・フィルタ・統計）は変更しない
- PrivateRoute.jsx の強制リダイレクト（級未設定時にプロフィール編集へ遷移）は現状のまま維持

## 4. 技術設計

### 4.1 API設計
変更なし

### 4.2 DB設計
変更なし

### 4.3 フロントエンド設計

変更はすべてフロントエンドの表示ロジックのみ。

#### 変更対象ファイル

1. **`karuta-tracker-ui/src/pages/players/PlayerList.jsx`**
   - `player.kyuRank && (...)` の条件を変更し、kyuRankがNULLの場合に「初心者」と表示するバッジを出す

2. **`karuta-tracker-ui/src/pages/Profile.jsx`**
   - 情報カードの級位行で、`player.kyuRank` がNULLの場合に `'初心者'` を値とする

3. **`karuta-tracker-ui/src/pages/players/PlayerDetail.jsx`**
   - 情報カードの級位行で、`player.kyuRank` がNULLの場合に `'初心者'` を値とする

4. **`karuta-tracker-ui/src/pages/matches/MatchList.jsx`**
   - ナビゲーションヘッダーと検索結果での級位表示で、NULLの場合に「初心者」を表示

5. **`karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx`**
   - 選択肢オプションの `'未設定'` フォールバックを `'初心者'` に変更

6. **`karuta-tracker-ui/src/pages/players/PlayerEdit.jsx`**
   - `<option value="">未設定</option>` を `<option value="">初心者</option>` に変更

### 4.4 バックエンド設計
変更なし

## 5. 影響範囲
- 変更は表示ラベルのみであり、既存のロジック（ソート・フィルタ・統計・API・DB）に影響しない
- PrivateRoute.jsx の強制リダイレクトにより、現時点では級未設定のユーザーがアプリを使うケースは限定的（管理者による変更、DB上の古いデータ等）

## 6. 設計判断の根拠
- **DBやenumに「初心者」を追加しない理由:** 級未設定（NULL）であることが初心者の条件であり、新しい値を追加する必要がない。DB変更を避けることで影響範囲を最小限にする
- **フィルタに「初心者」を追加しない理由:** 現時点では級未設定ユーザーが限定的であり、フィルタの必要性が低い
- **強制リダイレクトを維持する理由:** 現時点ではリダイレクト解除の要件がないため、既存動作を変えない
