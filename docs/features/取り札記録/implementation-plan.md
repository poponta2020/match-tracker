---
status: completed
---
# 取り札記録 実装手順書（2026-07-15 改修: 操作性向上と閲覧表示）

> 要件は [requirements.md](./requirements.md) の「## 追加改修（2026-07-15）」節（AC は C-1〜C-17）。
> **本改修はフロントエンドのみ**（`database/`・entity・migration の変更なし）。基盤の実装パスは §5・§6 を参照。
> 旧タスク（基盤の新規実装）は git 履歴が保持する。本ファイルは今回の改修タスクで上書き。

## 実装タスク

### タスク1: ②不明プールの決まり字順ソート
- [x] 完了
- **目的:** 不明プールのチップを「決まり字1文字目＝むすめふさほせ…順、同一1文字目内は五十音順」で並べる。
- **対応AC:** C-6, C-7
- **主な変更領域:**
  - `karuta-tracker-ui/src/data/kimariji.js` — 純関数追加: `FIRST_CHAR_ORDER`（`むすめふさほせうつしもゆいちひきはやよかみたこおわなあ`）／`KANA_COLLATION`（濁点込みの五十音辞書順文字列）＋ `kanaRank`／`compareKimariji(a,b)`（`・` を除去し仮名順マップで char 比較→長さ）／`compareCardsByDecisionOrder(noA,noB)`（1文字目順→五十音）。
  - `karuta-tracker-ui/src/pages/matches/TorifudaBoard.jsx` — `poolCards` を `.sort(compareCardsByDecisionOrder)` で整列（`placed`/計数ロジックは不変）。
- **依存タスク:** なし
- **必要なテスト:** 新規 `karuta-tracker-ui/src/data/kimariji.sort.test.js` — 既知の出札集合→期待順序（C-6）、「あ」群が `あい→あきか→あきの→あし…` 五十音順（ユーザー例）、共札 `わた・や`/`わた・こ` の `・` 除去ソート。TorifudaBoard: 並び替えても pool メンバー・`残り X / 母数` が不変（C-7）。
- **完了条件:** 新規・既存テスト green、lint green。
- **対応Issue:** #1053

### タスク2: ④保存後の遷移先を試合詳細へ
- [x] 完了
- **目的:** 個人結果入力（新規）の保存成功後、`/` ではなく `/matches/:id` へ遷移する。
- **対応AC:** C-11, C-16（回帰: 抜け番保存→`/`、編集→`/matches` は不変）
- **主な変更領域:**
  - `karuta-tracker-ui/src/pages/matches/MatchForm.jsx` — `handleSubmit` の新規保存を `/matches/:id` へ。**実体は2つの navigate サイト**: (a) 新規 create ブロック末尾（現 `MatchForm.jsx:763` の `navigate('/')`）— `createdRes` は `if/else` の各ブランチ内スコープなので、**外側に `let createdMatchId` を宣言し detailed/simple 両ブランチで代入**してからブロック後で `navigate('/matches/'+createdMatchId)`（id なしは `'/'` フォールバック）。(b) 409 overwrite update 経路（現 `~784`）は `existingMatchId` で `navigate('/matches/'+existingMatchId)`。**編集モード（`navigate('/matches')`）・抜け番（`navigate('/')`）は変更しない。**
- **依存タスク:** なし（`MatchForm.jsx` のみ・タスク1と変更領域が重ならない）
- **必要なテスト:** `karuta-tracker-ui/src/pages/matches/MatchForm.navigation.test.jsx` に追加 — 新規保存成功で `/matches/:id` へ遷移（`createDetailed` の返り id 使用）。抜け番保存後は `/` 維持。**既存 `matchAPI` モックに `createDetailed`/`create`（＋簡易経路検証時は `update`）を追加**（現状 `getById/getAll/getCardRecord/saveCardRecord` のみ）。
- **完了条件:** 新規・既存テスト green。
- **対応Issue:** #1054

### タスク3: ①ドラッグ&ドロップでの札配置（全操作）
- [x] 完了
- **目的:** ドラッグで「不明→マス（配置）／マス→別マス（移動）／マス→不明（解除）」。既存タップ操作は維持。盤面ドラッグが試合番号スワイプ切替と干渉しない。
- **対応AC:** C-1, C-2, C-3, C-4, C-5
- **主な変更領域:**
  - 新規 `karuta-tracker-ui/src/pages/matches/torifudaDragLogic.js` — 純関数: droppable id（`cell:ENEMY:RIGHT:BOTTOM:SELF` / `pool`）のエンコード/パースと、`computeDrop({ activeCardNo, overId, placements })` → 次 placements（配置/移動/解除）または null。
  - `karuta-tracker-ui/src/pages/matches/TorifudaBoard.jsx` — `DndContext`＋`useSensors(PointerSensor{distance:8}, TouchSensor{delay:180,tolerance:8})`。プールチップ・配置チップを `useDraggable`、各 half・プールを `useDroppable` 化。`onDragEnd` で `computeDrop`→`onChange`。**盤面ルート `<div className="tr">` に `data-swipe-ignore`**（MatchForm の `onTouchStart` が `closest('[data-swipe-ignore]')` で除外）。既存 onClick（select/place/unplace/place-over）は残す（クラス名 `.tr-chip`/`.tr-pool-wrap`/`.tr-half` を維持）。
  - **⚠ trailing-click ガード必須**: 同一チップに `onClick` と `useDraggable` を併用するため、実ドラッグ（移動/解除）直後にブラウザが `click` を発火し、`selected==null` の onClick が `unplace(c)` を走らせて**移動を打ち消す**（ブラウザ/センサー依存で dev では動くが一部モバイルで壊れる footgun）。`onDragStart` で立て `onDragEnd` 後の1クリックまでで下ろす ref フラグを持ち、直後の `onClick` は早期 return する。
  - `karuta-tracker-ui/src/pages/matches/TorifudaRecord.css` — ドラッグ中のチップ・ドロップ可能 half のハイライト（既存 `.armed` と併存）。
- **依存タスク:** タスク1（同一 `TorifudaBoard.jsx` を編集するため直列）
- **必要なテスト:** 新規 `torifudaDragLogic.test.js` — 配置(C-1)／移動(C-2)／不明へ解除(C-3)／不正ドロップは null。既存 `TorifudaBoard.test.jsx` のタップ2件が green のまま（C-4）。`MatchForm.swipe.test.jsx` に追加 — 盤面（`data-swipe-ignore` 配下）起点の横タッチでは試合番号が切り替わらない（C-5）。**pointer down→move→up の drag-settle を1件検証**（pure `computeDrop`＋`fireEvent.click` はどちらも drag→trailing-click 列を通らないため、ガードが効くこと＝ドラッグ移動が click で打ち消されないことを別途担保する）。
- **完了条件:** 新規・既存テスト green、lint green。
- **対応Issue:** #1055

### タスク4: ③象限内の横幅動的吸収
- [x] 完了
- **目的:** 同一象限の「取った｜取られた」で、少ない側の未使用横幅を多い側が吸収し可能な限り1行に収める。吸収不能時のみ2行目へ。空/少数側は最小幅を保つ。
- **対応AC:** C-8, C-9, C-10
- **主な変更領域:**
  - `karuta-tracker-ui/src/pages/matches/TorifudaBoard.jsx` — 各 half に `style={{ flexGrow: Math.max(chipCount, 1) }}`（chipCount は `cellCards(...).length`）。
  - `karuta-tracker-ui/src/pages/matches/TorifudaRecord.css` — `.tr-quad` を `grid-template-columns:1fr 1fr` から `display:flex` へ。`.tr-half` に `flex-basis:0; min-width:<1枚分（約26px）>`。taken 側の区切り線（`border-left` 破線）は維持。
  - 根拠: 全札が同幅のため `幅 ∝ 札数 ＝ 必要量に比例`。両側が同率で折り返す＝要件どおりの「吸収→折り返し」。
- **依存タスク:** タスク3（同一 `TorifudaBoard.jsx`／CSS を編集するため直列）
- **必要なテスト:** placements（どのマスに属するか）が折り返し変更後も不変であること（C-10・既存タップ/ドラッグテストで担保）。C-8/C-9 は視覚（verify・実機）。
- **完了条件:** lint・既存テスト green。実機で吸収→折り返しと最小幅を目視確認。
- **対応Issue:** #1056

### タスク5: ④試合詳細画面に取り札・お手付き詳細を読み取り専用表示
- [x] 完了
- **目的:** 試合詳細（本人閲覧）に、本人の取り札（不明除く・読み取り専用盤面）とお手付き詳細（読み取り専用）を表示。メンター閲覧・記録なしは非表示。
- **対応AC:** C-12, C-13, C-14, C-15
- **主な変更領域:**
  - `karuta-tracker-ui/src/pages/matches/TorifudaBoard.jsx` — `readOnly` prop 追加。true のとき `DndContext`・不明プール・info操作・onClick を出さず、配置済みチップのみ描画（`cards` に配置済み札番号を渡す前提）。見た目（盤面・畳・縦札）は現状踏襲。
  - `karuta-tracker-ui/src/pages/matches/OtetsukiDetails.jsx` — `readOnly` prop 追加。true のとき `count` ではなく `details`（type 有りのみ）を静的表示（種類ラベル＋種類別内容を `.tr` スタイルで、ボタン/セレクトなし）。
  - `karuta-tracker-ui/src/pages/matches/MatchDetail.jsx` — 本人閲覧時（`!isOtherPlayer`）のみ `matchAPI.getCardRecord(id)` を取得。`cardPlacements` があれば読み取り専用 `TorifudaBoard`（`cards`=配置済み札番号, `placements`=復元, `readOnly`）を統合カード下に表示。`otetsukiDetails` があれば読み取り専用 `OtetsukiDetails` を表示。`TorifudaRecord.css` を import。メンター閲覧（`?playerId=`）時は取得・表示しない。
- **依存タスク:** タスク3・タスク4（`TorifudaBoard.jsx` の readOnly を最終形に載せるため）。`OtetsukiDetails.jsx` は独立だが同タスク内で対応。
- **必要なテスト:** 新規 `karuta-tracker-ui/src/pages/matches/MatchDetail.cardRecord.test.jsx` — 本人閲覧で配置がある→盤面表示・不明プール非表示（C-12）／お手付き詳細表示（C-13）／メンター閲覧（`?playerId=`）で `getCardRecord` を呼ばず非表示（C-14）／記録なしでセクション非表示（C-15）。
- **完了条件:** 新規・既存テスト green、lint green。
- **対応Issue:** #1057

## 実装順序（Wave = 並行実装できるタスクの組）
- **Wave 1:** タスク1（②ソート・`TorifudaBoard`＋`kimariji`）, タスク2（④nav・`MatchForm` のみ） … 変更領域が重ならないため並行可
- **Wave 2:** タスク3（①D&D） … タスク1 の後（同一 `TorifudaBoard.jsx`）
- **Wave 3:** タスク4（③動的幅） … タスク3 の後（同一 `TorifudaBoard.jsx`／CSS）
- **Wave 4:** タスク5（④閲覧表示・readOnly） … タスク3・4 の後（`TorifudaBoard.jsx` 最終形に readOnly を追加）

> `TorifudaBoard.jsx` が①②③④共通のホットスポットのため、タスク1→3→4→5 は直列。タスク2（`MatchForm.jsx`）のみ Wave 1 で並行可能。

## ドキュメント更新（実装と同一コミット）
- `docs/spec/matches.md` の取り札記録セクション — D&D操作・不明プールの決まり字順・保存後の遷移先・試合詳細での読み取り専用表示を追記。
- `docs/SCREEN_LIST.md` — No.8/10（MatchForm）に D&D・決まり字順、No.9（MatchDetail）に取り札・お手付き詳細の読み取り専用表示、保存後遷移先の変更を追記。
