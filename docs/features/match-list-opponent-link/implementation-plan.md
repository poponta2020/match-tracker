---
status: completed
---
# match-list-opponent-link 実装手順書

## 実装タスク

### タスク1: MatchList.jsx にメンター関係チェックを追加
- [x] 完了
- **概要:** 他選手の対戦一覧を見ている時、その選手が自分の ACTIVE メンティーかどうかを判定するため、`mentorRelationshipAPI.getMyMentees()` を呼ぶ `useEffect` と関連 state を追加する。自分閲覧時は API を呼ばずに即決定。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/MatchList.jsx` — `isMentorOfTarget` / `mentorCheckLoading` state を追加。`targetPlayerId` 変更時に発火する新 `useEffect` でメンター関係をフェッチ。`isOtherPlayer === false` の場合は API を呼ばず `mentorCheckLoading = false` に設定。
- **依存タスク:** なし
- **対応Issue:** #712
- **完了条件:**
  - 自分の対戦一覧では API が呼ばれず、`mentorCheckLoading` が `false`
  - 他選手の対戦一覧では API が呼ばれ、`status === 'ACTIVE'` のメンティーであれば `isMentorOfTarget = true`
  - API 失敗時は `isMentorOfTarget = false` に固定し、エラーログを出す

### タスク2: MatchList.jsx の対戦行レンダリング変更
- [x] 完了
- **概要:** 各対戦行のレンダリングを変更する。①対戦相手名をボタン化して `/matches?playerId=<opponentId>` へ遷移、②メモアイコンを `<button>` でラップしてタップ可能にし、自分閲覧時かメンター閲覧時のみ表示、③行全体の `onClick` ハンドラを削除、④`opponentId` をフロント側で計算、⑤ゲスト選手の場合はリンク無効化。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/MatchList.jsx` — `filteredMatches.map(...)` 内を実装。具体的には:
    - 行コンテナの `onClick={() => navigate(...)}` と `cursor-pointer` を削除
    - `opponentId` を `match.player1Id === targetPlayerId ? match.player2Id : match.player1Id` で計算
    - 対戦相手名を `opponentId && opponentId !== 0` の場合 `<button>`（テーマ色 `#4a6b5a`、下線なし）、それ以外は通常 `<span>`
    - 既存の `StickyNote` 表示を `<button>` でラップし、`showDetailButton = !isOtherPlayer || isMentorOfTarget`、`!mentorCheckLoading` の時のみ表示。メモ有り = `text-gray-600`、メモ無し = `text-gray-300`
    - 並び順は現状維持（日付 → 相手名 → メモアイコン → 手N → 結果）
    - `aria-label="対戦詳細を見る"` を詳細ボタンに付与
- **依存タスク:** タスク1（state を参照するため）
- **対応Issue:** #713
- **完了条件:**
  - 対戦相手名タップで `/matches?playerId=<opponentId>` に遷移する
  - メモアイコンタップで `/matches/<id>` または `/matches/<id>?playerId=<targetPlayerId>` に遷移する
  - 行のその他の領域をタップしても何も起きない
  - ゲスト選手の場合は対戦相手名がリンクにならない
  - 一般選手が他人の対戦一覧を見ている時はメモアイコンが表示されない
  - メンター関係 API のロード中はメモアイコンが表示されない

### タスク3: MatchList のテストを新規作成
- [ ] 完了
- **概要:** Vitest + React Testing Library で `MatchList.jsx` のテストを新規作成。各閲覧ケースとエッジケースをカバーする。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/MatchList.test.jsx`（新規作成）— 以下のテストケースを実装:
    1. 自分閲覧時: 行が表示される、対戦相手名タップで `navigate('/matches?playerId=<opponentId>')`、メモアイコンタップで `navigate('/matches/<id>')`
    2. メンター閲覧時（ACTIVE）: 対戦相手名タップで `navigate('/matches?playerId=<opponentId>')`、メモアイコンタップで `navigate('/matches/<id>?playerId=<targetPlayerId>')`
    3. 他人閲覧時（非メンティー or INACTIVE）: 対戦相手名タップで遷移、メモアイコンは表示されない
    4. ゲスト選手（`player1Id === 0` または `null`）: 対戦相手名はリンクにならず、タップしても遷移しない
    5. メモ有り行: メモアイコンが濃色（`text-gray-600`）
    6. メモ無し行: メモアイコンが薄色（`text-gray-300`）
    7. メンター関係 API ロード中: メモアイコンが描画されない
    8. メンター関係 API 失敗時: メモアイコンが描画されない（エラーで画面が落ちない）
  - 必要なモック: `matchAPI`, `playerAPI`, `mentorRelationshipAPI`, `react-router-dom` の `useNavigate`
- **依存タスク:** タスク2
- **対応Issue:** #714
- **完了条件:**
  - 上記 8 ケースがパスする
  - `npm run lint` が通る
  - `npm test` で MatchList のテストが緑色

### タスク4: ドキュメント更新
- [ ] 完了
- **概要:** 仕様書と画面一覧ドキュメントを新仕様に合わせて更新する。
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` — 対戦一覧画面の仕様セクションに「対戦相手名タップで相手対戦一覧へ遷移」「メモアイコンタップで対戦詳細へ遷移（自分閲覧時とメンター閲覧時のみ）」「行全体タップによる詳細遷移は廃止」を追記
  - `docs/SCREEN_LIST.md` — `/matches` 画面の機能説明を更新（行内の挙動を明示）
- **依存タスク:** タスク2
- **対応Issue:** #715
- **完了条件:**
  - 新仕様がドキュメント上で正確に説明されている
  - 変更前の記述（「行タップで詳細へ遷移」等）が残っていない

## 実装順序
1. タスク1（メンター関係チェック追加）
2. タスク2（対戦行レンダリング変更）
3. タスク3（テスト追加）
4. タスク4（ドキュメント更新）

タスク1〜4 は 1 つの PR にまとめてマージする想定。タスク間で同じ `MatchList.jsx` を編集するため、論理的な依存順で進める。
