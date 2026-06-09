---
status: completed
---
# 札ルールの日付別永続化（pairing-card-rule-persistence）実装手順書

## 実装タスク

### タスク1: `generateCardRules` を末尾から続行可能なシグネチャに拡張
- [x] 完了
- **概要:** 既存の `generateCardRules(totalMatches)` を `generateCardRules(totalMatches, prefix = [])` に拡張する。`prefix` が空配列のときは現状と完全に同じ挙動。`prefix` が与えられた場合は、`prefix` の末尾を初期状態（`prevUsedDigits` / `prevUnusedDigits` および 3試合サイクル位置）として引き継ぎ、`totalMatches - prefix.length` 試合分を末尾に追加生成し、`prefix.concat(extra)` を返す。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/pairings/PairingSummary.jsx` — `generateCardRules` のシグネチャ拡張と内部実装の変更
- **依存タスク:** なし
- **対応Issue:** #836
- **完了条件:**
  - `generateCardRules(3)` の戻り値の構造・型が現状と互換であること
  - `generateCardRules(5, generateCardRules(3))` を呼んだ結果が、長さ5の有効な札ルール配列であること
  - 末尾追加された札ルールが3試合サイクル（一の位→抜き→十の位→一の位…）を `prefix` の末尾位置から正しく継続していること

### タスク2: localStorage アクセス用ユーティリティ関数の追加
- [ ] 完了
- **概要:** `PairingSummary.jsx` 内に以下のユーティリティ関数を追加する。
  - `STORAGE_PREFIX = 'karuta-tracker:card-rules:'`
  - `loadCardRules(date)`: localStorage から復元。JSON パース失敗・配列でない・null は全て `null` を返す
  - `saveCardRules(date, rules)`: localStorage に保存。try-catch で例外を握る
  - `getTodayLocalDateStr()`: クライアント端末ローカルタイムの今日（YYYY-MM-DD）
  - `cleanupOldCardRules()`: `STORAGE_PREFIX` で始まるキーのうち、今日（`getTodayLocalDateStr()`）と一致しないものを削除
  - `reconcileCardRules(stored, totalMatches)`: 保存済み長と試合数を突き合わせて表示用 `rules` と「localStorage 更新が必要か」のフラグを返す
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/pairings/PairingSummary.jsx` — ユーティリティ関数群の追加
- **依存タスク:** タスク1
- **対応Issue:** #837
- **完了条件:**
  - 各ユーティリティ関数が要件定義書 4.3 のシグネチャ通りに実装されていること
  - localStorage 不可環境（try で SecurityError 等を投げる場合）でも例外が外に漏れないこと

### タスク3: 画面ロード時の `useEffect` を localStorage 復元優先に変更
- [ ] 完了
- **概要:** `PairingSummary.jsx` の `useEffect` 内で、`generateCardRules` を直接呼ぶ箇所を localStorage 復元優先のロジックに置き換える。具体的には：
  1. `cleanupOldCardRules()` を最初に呼んで古いキーを掃除
  2. 既存の対戦データ取得処理はそのまま
  3. `loadCardRules(date)` で復元を試みる
  4. 復元できた場合: `reconcileCardRules(stored, totalMatches)` の結果を使用し、`changed` フラグが立っていれば `saveCardRules` で上書き
  5. 復元できなかった場合: `generateCardRules(totalMatches)` で新規生成し、`saveCardRules` で保存
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/pairings/PairingSummary.jsx` — `useEffect` 内の札ルール生成部分の置き換え
- **依存タスク:** タスク1、タスク2
- **対応Issue:** #838
- **完了条件:**
  - 初回アクセス時（localStorage 空）に新規生成され、localStorage に保存されること
  - 2回目以降の同日アクセスで、localStorage の値が復元されて画面に表示されること
  - 別日（過去日）の保存データが画面ロード時に削除されること
  - 試合数不一致のケースで `reconcileCardRules` の挙動どおりに表示されること

### タスク4: 「札を再生成」ボタンに確認ダイアログを追加し、上書き保存させる
- [ ] 完了
- **概要:** `handleRegenerate` に `window.confirm` の確認ダイアログを追加し、OK 時のみ既存の再生成処理を実行する。再生成後は `saveCardRules(date, rules)` で localStorage を上書きする。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/pairings/PairingSummary.jsx` — `handleRegenerate` 関数の変更
- **依存タスク:** タスク2
- **対応Issue:** #839
- **完了条件:**
  - 「札を再生成」ボタンを押すと `window.confirm('現在の札ルールを上書きして再生成します。よろしいですか？')` が表示されること
  - OK を押した場合のみ札ルールが再生成され、localStorage が上書きされること
  - キャンセルを押した場合は何も変化しないこと

### タスク5: 手動動作確認
- [ ] 完了
- **概要:** 以下のシナリオを開発環境で実行し、要件どおり動作することを確認する。
  - **シナリオA（基本フロー）:** 札ルール一覧画面を初回アクセス → 札ルール表示 → ブラウザリロード → 同じ札ルールが復元される
  - **シナリオB（対戦変更時）:** 札ルール一覧画面アクセス → 別タブで対戦組み合わせを再生成 → 札ルール一覧画面に戻ってリロード → 札ルールは同じ、対戦テキストのみ変わる
  - **シナリオC（再生成ボタン）:** 「札を再生成」ボタン → ダイアログ表示 → キャンセルで何も変わらない、OK で札ルールが変わる
  - **シナリオD（日付切り替え）:** 6/9 にアクセスして札ルール保存 → 翌日 6/10 にアクセス → 6/9 のキーが localStorage から削除されていることを DevTools で確認
  - **シナリオE（PWA再起動）:** ホーム画面ショートカットから起動 → 札ルール表示 → アプリ落とす → 再度ショートカットから起動 → 同じ札ルールが復元される
- **変更対象ファイル:**
  - なし（動作確認のみ）
- **依存タスク:** タスク3、タスク4
- **対応Issue:** #840
- **完了条件:** 上記5シナリオが期待どおり動作する

### タスク6: ドキュメント更新
- [ ] 完了
- **概要:** CLAUDE.md の「ドキュメント更新ルール」に従い、対象ドキュメントに本機能を反映する。
  - `docs/SPECIFICATION.md`: 札ルール一覧画面の挙動説明に「localStorage による日付単位の永続化」を追記
  - `docs/SCREEN_LIST.md`: 札ルール一覧画面の説明があれば「再生成時の確認ダイアログ」を追記
  - `docs/DESIGN.md`: 札ルール一覧画面のフロントエンド設計に localStorage キー命名と保存ロジックを追記
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md`
  - `docs/SCREEN_LIST.md`
  - `docs/DESIGN.md`
- **依存タスク:** タスク5
- **対応Issue:** #841
- **完了条件:** 3ドキュメントに本機能の挙動が反映されている

## 実装順序

1. タスク1（依存なし） — `generateCardRules` のシグネチャ拡張
2. タスク2（タスク1に依存） — localStorage ユーティリティ追加
3. タスク3（タスク1・2に依存） — `useEffect` を復元優先に変更
4. タスク4（タスク2に依存） — 再生成ボタンに確認ダイアログ追加
5. タスク5（タスク3・4に依存） — 手動動作確認
6. タスク6（タスク5に依存） — ドキュメント更新

タスク3とタスク4は依存関係上は並行作業可能だが、同一ファイル（`PairingSummary.jsx`）の編集なので順次実施を推奨。
