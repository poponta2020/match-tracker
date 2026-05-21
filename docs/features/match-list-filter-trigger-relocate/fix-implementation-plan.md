---
status: completed
---
# 対戦結果一覧 フィルタートリガー再配置 改修実装手順書

## 実装タスク

### タスク1: MatchList.jsx の import 整理と適用中フィルタ件数の算出ロジック追加
- [x] 完了
- **概要:**
  - `lucide-react` から `ChevronDown` を追加 import する
  - 同 import から `Filter` を削除する（FAB削除に伴い `MatchList.jsx` 内では未使用になる）
  - レンダリング内で `activeFilterCount` を算出する式を追加する（年月以外のフィルタが何件アクティブかを数える）
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/MatchList.jsx` — import 文の修正、`activeFilterCount` 算出ロジック追加
- **依存タスク:** なし
- **対応Issue:** #681

### タスク2: 上部ナビゲーションバーの「年月」トリガー化
- [x] 完了
- **概要:**
  - `<p className="text-sm text-white/70 mt-0.5">...</p>`（[MatchList.jsx:296-302](../../../karuta-tracker-ui/src/pages/matches/MatchList.jsx#L296-L302)）を `<button>` に変更
  - `onClick={() => setIsFilterOpen(true)}` を設定
  - 年月テキストの右に `ChevronDown` アイコン（`w-4 h-4` 程度）を追加
  - 下線スタイル（`underline decoration-dotted underline-offset-4` または `border-b border-white/40`）を追加
  - 状態クラス: `hover:bg-white/10 active:scale-95 active:bg-white/10 transition-all`
  - パディング・rounded など適切な余白とタップターゲットサイズを確保（最低 44px 相当）
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/MatchList.jsx` — 上部ナビゲーションバーの構造変更
- **依存タスク:** #681 (タスク1)
- **対応Issue:** #682

### タスク3: 適用中フィルタ件数バッジの表示
- [x] 完了
- **概要:**
  - タスク1 で算出した `activeFilterCount` が 1 以上の場合、年月ボタンの右に「フィルタ N件」バッジを表示
  - バッジスタイル: `bg-white/20 text-white text-xs px-2 py-0.5 rounded-full ml-2`
  - 0 件の場合はバッジを表示しない（`{activeFilterCount > 0 && ...}`）
  - レイアウトは flex で年月ボタンとバッジを横並び、画面幅が狭い場合は折り返しも確認
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/MatchList.jsx` — 件数バッジ表示の JSX 追加
- **依存タスク:** #681, #682 (タスク1, 2)
- **対応Issue:** #683

### タスク4: 右下FABのコメントアウト
- [x] 完了
- **概要:**
  - [MatchList.jsx:473-480](../../../karuta-tracker-ui/src/pages/matches/MatchList.jsx#L473-L480) の FAB ブロックを JSX コメント `{/* ... */}` で囲んでコメントアウト
  - 将来の参考用に残す（ユーザー指示）
  - コメントの先頭に「FAB は上部年月ボタンへ移行済み（YYYY-MM-DD）」のような短いメモを 1 行入れる
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/MatchList.jsx` — FAB ブロックのコメントアウト
- **依存タスク:** なし（タスク1〜3 と並行可能）
- **対応Issue:** #684

### タスク5: 開発サーバーでの動作確認
- [ ] 完了
- **概要:**
  - `cd karuta-tracker-ui && npm run dev` で開発サーバーを起動
  - `/matches` 画面を開いて以下を確認:
    - 上部の年月テキストが押せる見た目になっている（下線・ChevronDown）
    - クリックすると `FilterBottomSheet` が開く
    - ボトムシートでフィルタを設定すると、上部に件数バッジが表示される
    - フィルタを全て解除すると件数バッジが消える
    - 右下FABが表示されない
    - タップ時に少し縮む/光るアニメーションが効く
    - 他選手の対戦結果一覧（`/matches?playerId=X`）でも同様に動作する
  - レスポンシブも確認（モバイル幅と PC 幅の両方）
- **変更対象ファイル:** なし（動作確認のみ）
- **依存タスク:** #681, #682, #683, #684 (タスク1〜4)
- **対応Issue:** #685

### タスク6: lint・ビルド・既存テストの実行
- [x] 完了
- **概要:**
  - `cd karuta-tracker-ui && npm run lint` で ESLint エラーがないことを確認
  - `npm run build` でプロダクションビルドが通ることを確認
  - 既存テスト（`MatchForm.navigation.test.jsx` 等）は `MatchList.jsx` を直接対象としていないが、関連テストが落ちないこと（必要に応じて `npm test` 相当のコマンド）
- **変更対象ファイル:** なし
- **依存タスク:** #681, #682, #683, #684 (タスク1〜4)
- **対応Issue:** #686

### タスク7: ドキュメントの更新
- [x] 完了
- **概要:**
  - `docs/SPECIFICATION.md`, `docs/SCREEN_LIST.md`, `docs/DESIGN.md` を確認し、対戦結果一覧画面のフィルタリングUIに関する記述があれば最新の内容（年月クリックでフィルタを開く、件数バッジ表示、FABなし）に更新
  - 該当記述がなければ更新不要
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md`（必要に応じて）
  - `docs/SCREEN_LIST.md`（必要に応じて）
  - `docs/DESIGN.md`（必要に応じて）
- **依存タスク:** #681, #682, #683, #684 (タスク1〜4)
- **対応Issue:** #687

## 実装順序

1. **タスク1**: import 整理と件数算出ロジック追加（依存なし）
2. **タスク2**: 上部年月トリガー化（タスク1に依存）
3. **タスク3**: 件数バッジ表示（タスク1, 2に依存）
4. **タスク4**: FABコメントアウト（並行可能）
5. **タスク5**: 開発サーバーでの動作確認（タスク1〜4 完了後）
6. **タスク6**: lint・ビルド・テスト確認（タスク1〜4 完了後、タスク5と並行可能）
7. **タスク7**: ドキュメント更新（タスク1〜4 完了後）

タスク1〜4はすべて `MatchList.jsx` の変更なので、実際には1つのPRで一気に行うのが効率的。Issueとしては分けても、コミット/PRはまとめて良い。
