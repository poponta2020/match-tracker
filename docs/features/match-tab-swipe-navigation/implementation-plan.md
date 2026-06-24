---
status: completed
---
# 試合番号スワイプ移動 実装手順書

## 実装タスク

### タスク1: 共通ジェスチャ判定ロジック `swipeGesture.js`（純粋関数＋テスト）
- [x] 完了
- **概要:** タッチ座標と設定値から「横スワイプか／方向（prev|next）／確定するか」を判定する純粋関数群を作成。UIに依存させず単体テスト可能にする（既存 `byePlayersLogic.js` / `pairingDragLogic.js` と同様の方針）。
- **想定API（例）:**
  - `isHorizontalSwipe(dx, dy, activationPx = 10)` → boolean（横移動が縦より優勢かつ閾値超）
  - `resolveSwipe({ dx, containerWidth, commitRatio = 0.25 })` → `'prev' | 'next' | null`（確定方向。閾値未満は null）
  - `clampOffset(dx, { atFirst, atLast })` → 端方向の動きを抑制した表示用オフセット
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/swipeGesture.js` — 新規
  - `karuta-tracker-ui/src/pages/matches/swipeGesture.test.js` — 新規
- **依存タスク:** なし
- **完了条件:** 関数が定義され、方向判定・端クランプ・閾値判定の単体テストがグリーン。
- **対応Issue:** #922

### タスク2: 共通カルーセル `MatchCarousel.jsx`（指追従）
- [x] 完了
- **概要:** 試合番号パネルを指追従で左右スライドさせる共通コンポーネント。結果一覧・一括入力で共用する。現在±1のウィンドウ描画、touchイベントでのドラッグ追従（translateX）、離したときのスナップ／戻し、端クランプ、確定時の `onChange` 呼び出しを担う。高さは表示中パネルに合わせて余白を出さない。
- **props:** `totalMatches`, `currentMatchNumber`, `onChange(matchNumber)`, `renderPanel(matchNumber)`
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/components/MatchCarousel.jsx` — 新規
- **依存タスク:** タスク1
- **完了条件:** 単体で、左右スワイプで `onChange` が前後の試合番号で呼ばれ、端では呼ばれない（端で止まる）。縦スクロール時は発動しない。タップは阻害しない。
- **対応Issue:** #923

### タスク3: 結果一覧 MatchResultsView をカルーセル化
- [x] 完了
- **概要:** 試合ごとの表示部（対戦組一覧・抜け番・下部の「結果を一括入力／対戦組み合わせを作成」ボタン）を `renderPanel(matchNumber)` に切り出し、`MatchCarousel` でラップ。`onChange` で `setCurrentMatchNumber`。試合切替時に上部タブのアクティブを画面内へ自動スクロール。日付ナビ・カレンダー・FABはカルーセル外で固定維持。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/MatchResultsView.jsx` — カルーセル化、タブ自動スクロール
  - `karuta-tracker-ui/src/pages/matches/MatchResultsView.swipe.test.jsx` — 新規（スワイプ挙動テスト）
- **依存タスク:** タスク2
- **完了条件:** 左右スワイプで試合が前後に切替、端で止まる。日付前後ボタン・FAB・動画バッジ・選手リンクが従来どおり動作。
- **対応Issue:** #924

### タスク4: 一括入力 BulkResultInput をカルーセル化
- [ ] 完了
- **概要:** 試合ごとの入力部（勝者選択・枚数差セレクト・抜け番活動）を `renderPanel(matchNumber)` に切り出し、`MatchCarousel` でラップ。`onChange` で `setCurrentMatchNumber`。タブ自動スクロール追加。入力結果は全試合分保持されるためスワイプで消えないこと、下部固定の保存バーはカルーセル外で維持することを担保。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/BulkResultInput.jsx` — カルーセル化、タブ自動スクロール
  - `karuta-tracker-ui/src/pages/matches/BulkResultInput.swipe.test.jsx` — 新規（スワイプ挙動テスト）
- **依存タスク:** タスク2
- **完了条件:** 左右スワイプで試合が前後に切替、端で止まる。勝者ボタン・枚数差セレクトのタップが従来どおり動作。スワイプ後も入力内容が保持され、保存バーが固定表示される。
- **対応Issue:** #925

### タスク5: 個人入力 MatchForm スライドイン＋警告＋dirty追跡
- [ ] 完了
- **概要:** MatchForm にスライドイン（方式B）と未保存警告を実装。
  - `isDirty` 状態を追加。ユーザー操作（結果ボタン・枚数差・お手付き・メモ・対戦相手選択・抜け番活動・手動抜け番切替）で true、保存成功時・確認後の試合切替時に false。`applyMatchData` では立てない。
  - 共通ガード `requestMatchNumberChange(num, { fromSwipe })` を追加。`isDirty` なら確認ダイアログ→OKで切替／キャンセルで据え置き。dirtyでなければ即切替。**既存タブonClickとスワイプ確定の両方をこのガード経由**にする。
  - スワイプ検出（touchstart/move/end、`swipeGesture.js` 利用）。確定時にガードを呼ぶ。`fromSwipe` のときのみスライドインアニメ（約0.2秒）。
  - タブのアクティブ自動スクロール追加。
  - 編集モード（`isEdit`）は対象外。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/MatchForm.jsx`
  - `karuta-tracker-ui/src/pages/matches/MatchForm.swipe.test.jsx` — 新規（警告ダイアログ・スライド挙動テスト）
- **依存タスク:** タスク1
- **完了条件:** 入力途中でスワイプ／タブタップ時に確認ダイアログが出る。OKで切替・スライドイン、キャンセルで据え置き。未入力時は確認なしで切替。端で止まる。既存の抜け番判定・上書き確認・参加登録ダイアログに影響なし。
- **対応Issue:** #926

### タスク6: ドキュメント更新
- [ ] 完了
- **概要:** CLAUDE.md のルールに従い、機能追加を仕様・画面・設計ドキュメントへ反映（実装と同コミット）。
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` — スワイプ操作の仕様追記
  - `docs/SCREEN_LIST.md` — 該当3画面の操作にスワイプ追記
  - `docs/DESIGN.md` — `swipeGesture.js` / `MatchCarousel.jsx` / MatchForm改修の設計追記
- **依存タスク:** タスク3・4・5
- **完了条件:** 3ドキュメントに本機能の内容が反映されている。
- **対応Issue:** #927

## 実装順序
1. **タスク1**（依存なし）— ジェスチャ純粋ロジック＋テスト
2. **タスク2**（タスク1）— 共通カルーセル
3. **タスク3 / タスク4**（タスク2）— 結果一覧・一括入力のカルーセル化（並行可）
4. **タスク5**（タスク1）— MatchForm スライドイン＋警告（タスク2と並行可）
5. **タスク6**（タスク3・4・5）— ドキュメント更新
