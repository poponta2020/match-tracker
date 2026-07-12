---
status: completed
---
# 組み合わせ画面 試合番号タブの整形 実装手順書

対象は `karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx` の試合番号タブ描画部（現状 `:864-896` 付近）と、その直下の連結パネル冒頭（`:898-899` 付近）。**BE・API・DB・スキーマ変更なし。** 実装は1コンポーネント内で完結するため main が直接実装（サブエージェント委譲は不要）。

## 技術設計メモ
- **タブ本数**: 現状 `Array.from({ length: currentSession?.totalMatches || 10 })`。本数ロジックは不変（Non-goal）。
- **リレイアウト**: タブバー `<div>` から非アクティブの `flex-1` を撤廃し、全ボタンを **content幅・同一フットプリント**（`flex-none`、同一 padding・同一 `text-[15px]`、数字のみ表示）にする。並びは**左寄せ**（`justify-start`）、`gap-1` 程度、コンテナに `overflow-x-auto`（≤7では発火しない／8以上で横スクロール）。アクティブでも「N試合目」ラベルでボタン幅を変えない（＝リフロー除去の要）。
- **「N試合目」の意味保持**: タブから「試合目」サフィックスを外す代わりに、連結パネル冒頭（`:899` の cream パネル内の先頭）に静的な小見出し `{matchNumber}試合目`（muted・小サイズ）を置く。切替でレイアウトは動かない。
- **滑る cream ハイライト（②・承認済み）**:
  - タブバーコンテナを `relative` にし、各タブボタンを `tabRefs.current[num]` で参照。テキストは `relative z-10`。
  - `useLayoutEffect`（import 追加が必要。現状 `useState, useEffect, useRef, useCallback` のみ）で、アクティブタブ要素の `offsetLeft` / `offsetWidth` を計測し `indicator = { left, width }` を state 保持。deps は `[matchNumber, currentSession?.totalMatches]`。`window` resize リスナでも再計測（フォント読込・回転対応）。
  - ハイライトは `<span aria-hidden>` を `absolute bottom-0`（`-bottom-px` で下辺を境界線に重ね連結パネルへ接続）・`bg-[#ebe4d8]`・`rounded-t-lg`・`z-0`・`transition-[transform,width] duration-200 ease-out`、`style={{ transform: 'translateX(${left}px)', width: '${width}px' }}` で描画。切替で位置/幅がアニメーション遷移する。
  - **連結パネル接続**: パネル（`:899`）の上辺角丸を撤去（`rounded-tr-xl` を外し top をフラットに、`rounded-b-xl` は維持）し、任意位置のハイライトと cream 面がシームレスに繋がる形にする。
  - （任意）8試合以上のとき、切替時にアクティブタブをコンテナ内で可視化（`scrollLeft` 調整 or `scrollIntoView({ inline:'nearest', block:'nearest' })`。ページ縦スクロールを誘発しない形で）。
- **回帰の状態表現を維持**: 非アクティブの緑チェック（`exists && !isActive` の丸バッジ）・未保存オレンジ文字（`isUnsaved`）・plain muted は現行ロジックのまま残す。`onClick={() => setMatchNumber(num)}` と `isReadOnly` ガードは不変。

## 実装タスク

### タスク1: タブバーのリレイアウト（左寄せ・content幅・横スクロール・状態色維持）
- [x] 完了
- **対応Issue:** #1025
- **目的:** 非アクティブ `flex-1` を撤廃し、全タブを同一フットプリントの数字タブに。3試合は左寄せ、7試合は1行に収まり、8以上は横スクロール。緑チェック/オレンジ未保存の区別は維持。
- **対応AC:** AC-1, AC-4, AC-5
- **主な変更領域:** `PairingGenerator.jsx` タブバー `<div>`（`:866-896`）
- **依存タスク:** なし
- **必要なテスト:** なし（AC-1/4/5 は verify）。`PairingGenerator.integration.test.jsx` はフルページ描画ハーネスを持たず（純ロジック関数＋サブコンポーネントのみ）、タブ本数・切替は verify で担保するのが AC の検証手段に整合。本数は Non-goal のため `Array.from({ length: totalMatches || 10 })` を保持し純ヘルパー抽出はしない（ついでリファクタ禁止）。既存 603 テスト＋lint(0 error) を green 維持。
- **完了条件:** 3/7/10 試合で verify（左寄せ・7収まる・10スクロール）、緑/オレンジ表示が残る。lint green。

### タスク2: 滑る cream ハイライト＋連結パネル接続＋「N試合目」静的見出し
- [x] 完了
- **対応Issue:** #1026
- **目的:** アクティブ表示を「滑る cream ハイライト」に。切替でタブは動かずハイライトのみアニメで移動し、下辺で連結パネルに接続。タブから外した「N試合目」をパネル冒頭の静的見出しで保持。
- **対応AC:** AC-2, AC-3
- **主な変更領域:** `PairingGenerator.jsx` タブバー（ハイライト span・refs・`useLayoutEffect`）と連結パネル冒頭（`:898-899`、上辺角丸撤去＋見出し追加）、`useLayoutEffect` の import 追加（`:1`）
- **依存タスク:** タスク1
- **必要なテスト:** なし（AC-2/3 は verify）。ハイライトの滑走・フットプリント不変・パネル冒頭 `{matchNumber}試合目` 見出しはいずれも視覚要件で、`useLayoutEffect` の `offsetLeft/offsetWidth` 計測は jsdom がレイアウトしないため単体テスト不能。verify で担保。既存テスト green 維持。
- **完了条件:** 切替でタブのフットプリント不変（AC-2）＋ハイライトがアニメ移動（AC-3）を verify。「N試合目」見出しが出る。

### タスク3: 回帰確認とドキュメント同期
- [ ] 完了
- **対応Issue:** #1027
- **目的:** 未保存ドラフト時の isReadOnly・LINE生成導線・保存/自動組み合わせが不変であることを担保し、正典ドキュメントを in-place 更新。
- **対応AC:** AC-6, AC-7, AC-8
- **主な変更領域:** `docs/SCREEN_LIST.md` #19 タブ記述、`docs/features/pairings-ui-change/design-spec.md`「試合番号は下線タブ」記述。AC-6（isReadOnly 導出＝`:71`）・AC-7（LINE/保存）のロジックは本改修の対象外（タブの見た目と `useLayoutEffect` のみ変更）のため、既存テストが無改修で green＝回帰を実証。新規テスト追加はしない。
- **依存タスク:** タスク1, タスク2
- **必要なテスト:** `cd karuta-tracker-ui && npm run test`（フレーク時 `vitest run --no-file-parallelism`）＋ `npm run lint` green。回帰 AC-6/AC-7 は既存テスト＋verify で担保（本改修が当該ロジックを触らないため）。
- **完了条件:** 全 Vitest/lint green（AC-8）、回帰 AC-6/AC-7 が既存テスト＋verify で担保、docs 2ファイルを実装と同じコミットで更新。

## 実装順序
1. タスク1（依存なし）
2. タスク2（タスク1に依存）
3. タスク3（タスク1・2に依存）
