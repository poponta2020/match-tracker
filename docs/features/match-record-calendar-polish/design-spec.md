---
status: locked
slug: match-record-calendar-polish
target: /matches（MatchList のカレンダー/戦績確認タブ）+ MatchCalendar / YearMonthPicker、及び /practice（PracticeList）の年月ピッカー
chosen_direction: "単一案（実物ライブ調整で収束。方向性の分岐はなし）"
round: 9
prototype_branch: design/calendar-heading-full-bleed
prototype_base: f04d227a8c58c9a00c1e40a9d7099866f433680b
---
# 戦績確認（カレンダー/戦績確認タブ）画面 ポリッシュ design-spec

> `/design-screen` が出力する確定仕様。実装は `/implement match-record-calendar-polish` で行う（純UI＝薄い implementation-plan.md を同梱）。
> **レイアウトの詳細は `design-prototype.patch`（実差分・5ファイル）が正**。本ファイルは patch から読み取れない「意図・判断・申し送り・テスト影響」を記述する。

## 1. 対象と狙い
- **対象画面:** `/matches`（`MatchList`）の「カレンダー」タブ本体（`MatchCalendar`）と「戦績確認」タブ、共有 `YearMonthPicker`、および `/practice`（`PracticeList`）の年月ピッカー。
- **現状の不満:** 既出荷の `match-record-calendar-tabs`（[[impl_match_record_calendar_tabs]]）に対する見た目・操作の粗（帯の切れ・タブ位置の上下・年月表示の不統一・月移動導線の弱さ・ピッカーの再オープン不具合）。
- **狙い（ゴール）:** カレンダー⇄戦績確認を行き来しても違和感のない、統一感のある画面に磨く。BE 改修・スキーマ変更なしの純 FE ポリッシュ。

## 2. 採用した方向性
- **方向性:** 単一案。実物（実 `MatchList` を `?view=calendar` で描画するプレビュー）を Browser で見ながら1項目ずつ収束。方向性の A/B 比較は発生せず。
- **確定プロトタイプ:** `design-prototype.patch`（ブランチ `design/calendar-heading-full-bleed`・基点 `f04d227a`）。**patch は実5ファイルのみ**（プレビュー足場 `DesignCalendarFullBleedPreview.jsx`・`App.jsx` の proto ルートは除外済み）。
- **確認URL（プロト）:** `/design/calendar-fullbleed?view=calendar`（DESIGN-PROTO。実装後は不要）。

## 3. 確定した変更（9項目・patch に含まれる実差分の意図）
1. **緑見出し帯＋選択日リストの全幅化**（`MatchCalendar`）: 親 `<main>` の `px-4 sm:px-6 lg:px-8` を負マージン `-mx-4 sm:-mx-6 lg:-mx-8` で打ち消し、緑帯・試合リスト・空状態を画面両端まで届かせる。内側 `px-4` は維持し文字位置は不変（背景のみ全幅）。
2. **年月ピッカーの月別試合数バッジ**（`YearMonthPicker` + `MatchCalendar`）: 任意 prop `countForMonth(year, month)` を追加し、各月セルに件数（0件は非表示・選択月は白文字）。`MatchCalendar` は既存 `countMatchesInMonth(matches, y, m-1)` を渡す。**`PracticeList` は prop 未指定＝従来どおり（非退行）**。
3. **年月ピッカーのトグル閉じ修正**（`YearMonthPicker` + `MatchCalendar` + `PracticeList`）: 開いている時にラベル再タップで閉じるよう、任意 prop `triggerRef` を追加し外側クリック検出からトリガーを除外。原因＝`mousedown` で外側閉じ→直後の `click` トグルで再オープン。**同一バグを持つ `PracticeList` も同時に配線**（共有コンポーネント修正のため）。
4. **曜日ヘッダ（日月火水木金土）削除**（`MatchCalendar`）: 日セルの配色（日=赤/土=青）で曜日が充分伝わるため。カレンダーが1段コンパクトに。
5. **選択日見出しに曜日追加**（`MatchCalendar` + `utils/matchCalendar`）: 「7/21 会場名」→「7/21(火) 会場名」。純関数 `formatMonthDayDow(iso)` を新設（曜日は `WEEKDAYS` 参照・半角括弧）。
6. **相手名の級を半角括弧**（`MatchCalendar`）: 「ざきお（A）」→「ざきお(A)」。
7. **タブ位置の固定**（`MatchList`）: 戦績確認タブのフィルタ等を緑バーから外し**タブ帯の直下のサブバーへ切り出し**、緑バーを「名前＋級のみ」にして両タブで同一構造化。→ カレンダー/戦績確認どちらでも**タブ帯 top=53px で不変**（構造的にゼロシフト）。両分岐の外側ラッパから死に `space-y-6`（固定ヘッダに効く 24px の余白）を除去。
8. **戦績確認サブバーのスタイル**（`MatchList`）: 背景を白→クリーム `#f2ede6`（本文と同色・不透明）。年月フィルタを中央寄せ＋文字を**カレンダーの月ラベルと完全一致**（`text-lg` / `#374151` / `font-bold` / 下線なし / chevron `#6b7280`・表記も `YYYY年M月`）。検索・「自分に戻す」は中央位置を保つため右端へ絶対配置。
9. **カレンダーグリッドの左右スワイプで月移動**（`MatchCalendar` + `swipeGesture`）: **グリッド（日セル）領域のみ**に `onTouchStart`/`onTouchEnd`。左=翌月・右=前月。既存の共有 `swipeGesture.js`（`isHorizontalSwipe`/`resolveSwipe`）を再利用。縦優勢は無反応（`touchAction: pan-y`）、スワイプ直後の日セル誤選択は `swiped` フラグで抑制。

## 4. patch に現れない設計判断
- **タブ位置は「緑バー＝名前＋級のみ」で構造的に固定**（2つの高さを一致させ続けるのではなく、上位ブロックを同一にして by construction）。フィルタ等は全て**タブ帯の下**へ。advisor 助言に基づく。
- **年月フィルタとカレンダー月ラベルの座標一致は実測で合わせた**（同一 viewport で centerY=136±1・両方中央）。text-lg 化で背が伸びたぶんサブバー上パディング（`pt-5`）で吸収。
- **サブバー背景はクリーム（本文と同色）だが不透明必須**（固定ヘッダのオーバーレイ。透明だとスクロール内容が透ける）。
- **本文上部 offset（`pt-[…px]`）は固定ヘッダ実測から算出**：戦績確認＝`pt-[104px]`（既定）/`pt-[150px]`（選手検索オープン時＝サブバーが伸びるぶん）。カレンダー＝`pt-[42px]`。いずれも固定ヘッダ下端＋約6〜12px。
- **月別件数・曜日は既存データから導出**（新規 fetch/API/スキーマなし）。統計パネルは既存 `getStatisticsByRank` のまま（本改修では不変）。

## 5. 状態（state）
- **カレンダー通常/空/月内0件/複数試合/指導/ゲスト:** 既出荷仕様どおり（本改修で不変）。見出しに曜日が付く・級が半角括弧になる点のみ変化。
- **戦績確認 通常/選手検索オープン/他選手表示（自分に戻す）/フィルタ有効（フィルタN件バッジ）:** すべて proto で再現し、**全状態でタブ帯 top=53px 維持・本文が固定ヘッダをクリア・クラッシュ無し**を実測確認。
- **プロトでの再現方法:** `DesignCalendarFullBleedPreview.jsx`（DESIGN-PROTO）で実 `MatchList` を描画し、`matchAPI.getByPlayerId`/`getStatisticsByRank`/`byeActivityAPI`/`playerAPI`/`mentorRelationshipAPI` をスタブ。**このスタブ群は patch に含めない**（実データは既存配線のまま）。

## 6. 必要データ
- **新規データ・API・スキーマ変更なし。** 月別件数は既存 `matches`（`getByPlayerId`）から `countMatchesInMonth` で導出。曜日は日付から算出。統計パネルは既存 `getStatisticsByRank` のまま。

## 7. インタラクション / レスポンシブ
- **スワイプ:** カレンダーのグリッドのみ左右スワイプで月移動（左=翌月/右=前月）。縦スクロールは温存。
- **ピッカー:** ラベル再タップで開閉トグル（外側タップでも閉じる）。
- **モバイル 375px:** 全幅帯は横スクロールを出さない（負マージンは `<main>` パディング内に収まる。実描画域基準で左右gap=0）。タブレット（sm/lg）でも `-mx-6/-mx-8` で追従。

## 8. ガードレール準拠メモ
- 配色は既存トークンのみ（`#4a6b5a` / `#374151` / `#6b7280` / `#f2ede6` / `#f9f6f2` / `#eef2ef`）。新色の発明なし。
- 共有プリミティブ（`YearMonthPicker`・`MatchViewTabs`・`swipeGesture`）を再利用。ユーザー向け文言は日本語。

## 9. 残課題・実装への申し送り（テスト影響が主）
- **`git apply` は基点 `f04d227a`（＝現 local main）に対して検証済み（APPLY_OK）。** main が進んで衝突する場合は手動移植（対象は上記5ファイル）。
- **テスト更新が必須（`/implement` の緑化に直結）:**
  - `MatchCalendar.test.jsx`: 級表記を**全角 `（A）`→半角 `(A)`** に更新（`ざきお（A）` 等のアサート複数）。見出しの `toContain('7/21')` は `7/21(火)` でも通るため変更不要。曜日ヘッダ削除に関するアサートが無いことも確認。
  - `matchCalendar.test.js`: 新設 `formatMonthDayDow` の単体テストを追加（例 `2026-07-21`→`7/21(火)`・空入力→`''`）。
  - `MatchList.tabs.test.jsx`: 戦績確認ヘッダの restructure（フィルタをサブバーへ移動・緑バー簡素化）で**旧位置の年月/フィルタを参照するアサートが壊れる可能性**。要確認・更新。
  - `PracticeList.*.test.jsx`: `YearMonthPicker` をモックしているため `triggerRef` 追加の影響なし（要確認のみ）。
  - スワイプ: `swipeGesture.js` の純関数は既存テスト済み。`MatchCalendar` の配線は jsdom で実タッチ不可のため、synthetic dispatch のコンポーネントテストを追加するか、実機確認に委ねる（下記）。
- **実機確認の宿題:** スワイプはデスクトップの synthetic TouchEvent で検証済み。実機（スマホ）タッチでの月移動・縦スクロール非干渉・日セル誤選択なしを実装後に確認する。
- **DESIGN-PROTO:** 実5ファイルに `DESIGN-PROTO` マーカーは 0件（確認済み）。proto 足場（`DesignCalendarFullBleedPreview.jsx`・`App.jsx` の proto import/route）は patch に含めていないため、productionize で除去対象は無い。

## 10. 要件への宿題（→ /define-feature match-record-calendar-polish）
- （なし＝収束。純UI・新ロジック/データ/遷移の未確定なし。統計パネル等の既存挙動は不変）
