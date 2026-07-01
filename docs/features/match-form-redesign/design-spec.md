---
status: locked           # draft | iterating | locked
slug: match-form-redesign
target: karuta-tracker-ui/src/pages/matches/MatchForm.jsx （/matches/new・/matches/:id/edit）
chosen_direction: final ＋ states 統一（Anti-Slop 階層再設計）  # 視覚は確定（2026-06-30）。残るDTO/挙動は /define-feature へ
round: 6                 # 調整ループの回数
---
# 試合フォーム（MatchForm）デザイン仕様（design-spec）

> `/design-screen` の成果物。**視覚デザインは確定（status: locked、2026-06-30）。**
> round 0〜6 で claude.ai/design 上の往復調整を経て、全カード（通常入力／状態バリエーション／抜け番／周辺状態／最新形＋①②ヘッダー横展開）が同一デザイン言語に統一された。残る論点は表示データの DTO と対戦相手の選択挙動のみ（§10 → `/define-feature`）。

## 1. 対象と狙い
- **対象画面:** `karuta-tracker-ui/src/pages/matches/MatchForm.jsx`（ルート `/matches/new`, `/matches/:id/edit`）。1試合ずつ結果を入力／編集するフォーム。
- **現状の不満:** （ヒアリング前。ユーザーが Claude Design 上で指示予定）
- **狙い（ゴール）:** （未確定）
- **主ユーザー / 主な使い方:** 選手本人が練習中／練習後に自分の試合結果を入力（推定・未確認）。

## 2. 採用した方向性（確定 = final ＋ states 統一）
- **Anti-Slop デザイン原則**（後述）を MatchForm に本適用。`preview/match-form-final.html` を頂点に、`states`／`bye`／`edge`／集約カード `latest` の全カードが同一言語で揃う。`preview/_match-form-final.css` ＋ `preview/_match-form-ext.css` を適用。
- **確定UIの要旨（vs 現状）:**
  - **ヘッダー＝日付＋会場:** 緑ナビ中央に `10/5(日)　近江勧学館`（`<span class="dnum">` で日付を軽く強調）。試合タブは **`N試合目`**（旧 `第N試合`）。アイブロウ（「第N試合・結果を記録」等）は**廃止**。
  - **対戦相手＝主題:** 左に名前 28px ＋ 級 `(A)`（小さく淡色）。**右端に「▽（プルダウン affordance）＋ 🔍検索」クラスタ**（名前が長くても ▽ は常に検索のすぐ左）。相手未選択時は名前位置が**当日参加者＋「抜け番」のプルダウン**になり、🔍 から未参加者（全選手）を検索。確定後は名前＋▽タップで変更。
  - **結果・枚数差・お手付きを1行に横並び**（3カラム grid）。
  - 結果は**面で塗らない分割トグル**（枠線＋選択時のみ文字色：勝ち green-700 `#15803d`／負け red-700 `#b91c1c`）。**ドットは廃止し文字色のみ**。現状の緑/赤ベタ塗りボタンを置換。
  - 枚数差・お手付きは**下線の数値ピッカー**（一括入力と同様式）。**数値＋単位（`枚`/`回`）を中央で密着**、下線はフィールド全幅、▽ は右端。指導／不明時は単位なし。
  - **アクション行 `.mf-actions`:** 左にテキスト「キャンセル」｜右に紺アクセント「登録」（編集時は「更新する」）。**アクセントはこの1か所のみ**。
  - 角丸 10px 統一・影なし・余白4の倍数。
- **設計言語（Anti-Slop／`preview/design-principles.html`）:** 「カードに逃げない」＝border/shadow/bg/radius を外しても意味が壊れないならカード化しない／区切りは hairline＋余白／階層はサイズ・太さ・コントラスト／アクセントは主アクション1か所／角丸10px・影なし・余白4の倍数。避ける＝全項目巨大化・影付きカードの羅列・グラデ/glassmorphism・フォント乱用・角丸語彙のバラつき。

## 3. 現状レイアウト（上→下）※コードからの抽出
1. **上部ナビ（固定・緑 `#4a6b5a`）:** 中央に日付 `2025年10月5日(日)` ＋ 横スクロールの試合番号タブ `第1試合…第N試合`（N = `practiceSession.totalMatches`、アクティブ＝白文字＋白下線、非アクティブ＝白60%）。
2. **本文（左右スワイプで前後の試合へ。新規入力かつタブ2件以上のときのみ）:**
   - スワイプ案内 `‹ スワイプで試合を切替 ›`
   - **対戦相手:** 確定時は名前を特大表示（30px/bold/中央）＋「変更する」リンク。未確定はプレーン下線セレクト（`availablePlayers` から選択）。
   - （対戦相手未選択時のみ）**抜け番として記録する** ボタン（黄破線）。
   - **結果:** 2分割の大ボタン `〇 勝ち`（選択時 green-500）/ `× 負け`（選択時 red-500）、非選択 `#e5ebe7`。
   - **枚数差:** 0〜25枚セレクト。登録相手との試合のみ末尾に「指導」（→ isLesson）。
   - **お手付き回数:** 未入力 / 0〜20回セレクト。
   - **メモ:** 自動伸長テキストエリア（下線）。
   - **アクション:** `登録する`/`更新する`（紺 `#1A3654`）＋ ×（キャンセル）。
3. **別モード:**
   - **抜け番:** 黄バナー「この試合は抜け番です」＋ 活動種別の縦並びボタン（読み/一人取り/見学/見学対応/その他/休み、選択時 緑 `#4a6b5a`）＋（その他のみ）自由記述 ＋ 登録/更新。
   - **練習日でない（新規 & セッション無し）:** ブロック画面（AlertCircle ＋「今日は練習日ではありません」＋「練習日を確認する」/「戻る」）。
   - **入力済み上書き:** 青バナー「入力済みの試合です。保存で上書きされます。」
4. **モーダル:** 試合切替の破棄確認（入力途中）／ 参加未登録の登録促し。
5. **下部ナビ（共通 Layout・固定・緑 `#4a6b5a`・56px）:** Home / Match / Schedule / Record / Settings（lucide: Home/Swords/Calendar/BarChart3/Settings）。非アクティブ `#b8ccbf`。`/matches/new` ではどのタブも非アクティブ。

## 4. 使用コンポーネント
- **既存:** 共通 `Layout`（下部ナビ）、`LoadingScreen`、lucide-react アイコン。**この画面は共通 `PageHeader` を使わず**独自の固定緑ナビ（日付＋試合タブ）を持つ。
- スワイプ実装: `./swipeGesture`（`isHorizontalSwipe`, `resolveSwipe`）、`./tabScroll`（`scrollActiveTabIntoView`）。

## 5. 状態（state）
- **通常:** 上記。
- **長大:** 試合数が多いと上部タブが横スクロール（`overflow-x-auto`、アクティブを自動スクロール表示）。
- **抜け番 / 練習日でない / 入力済み上書き / 参加未登録:** 上記別モード。
- **エラー:** 赤バナー（`bg-red-50 text-red-700`）。保存失敗・重複（409→上書き確認）。

## 6. 必要データ（API / DTO）
- `matchAPI`（getById / getByPlayerDateAndMatchNumber / create / createDetailed / update / updateDetailed）
- `playerAPI.getAll`、`practiceAPI`（getByDate / getPlayerParticipations / registerParticipations）、`pairingAPI.getByDate`、`byeActivityAPI`（getByDate / create / update）
- フォーム値: matchDate, opponentName, opponentId, result(勝ち/負け), scoreDifference(0–25), isLesson, matchNumber, personalNotes, otetsukiCount(0–20 or null)

## 7. インタラクション / レスポンシブ
- 390px 基準モバイル。左右スワイプで試合切替（新規 & 2件以上）。dirty 時は破棄確認モーダル。
- 上部・下部ナビは fixed。本文は `pt-28` でヘッダー回避。

## 8. ガードレール準拠メモ（重要）
- **この画面の実トークンは REFERENCE.md の汎用 gray パレットではなく暖色系の「和」配色**：背景クリーム `#f2ede6`、ナビ緑 `#4a6b5a`/`#3d5a4c`、主アクション紺 `#1A3654`/`#122740`、補助面 `#e5ebe7`/`#d5ddd8`、入力下線 `#c5cec8`、文字 `#374151`/`#6b7280`/`#9ca3af`。
- セマンティックは Tailwind 標準: 勝ち green-500 / 負け red-500 / 注意 yellow / 情報 blue / エラー red。
- **主色を勝手に変えない**（実装準拠）。配色刷新の要否はユーザー確認待ち。
- **final で導入された追加トークン（要・実装合意）:** ベース `#f2ede6`／面 `#fffdf9`／ink 紺 `#1A2744`／主アクション紺 `#1A3654`／緑フレーム `#4a6b5a`。落ち着いた warm-taupe 系の文字・hairline（`#8a8275`/`#9a9183`/`#b3ac9e`/`#ada697`／境界 `#d8cfbf`/`#e3dccf`）。セマンティックは**彩度を落とした** green-700 `#15803d`／red-700 `#b91c1c`（現状の 500 ベタ塗りから変更）。type 11/12/14/16/20/28、角丸10px、影なし。→ これらは現行 Tailwind 実装に無い値が多く、実装時に config/クラス方針の合意が必要。

## 9. 確定事項・実装への申し送り（round 2〜6 で確定）
- **視覚の最終形は §2 に集約**（ヘッダー＝日付＋会場／タブ `N試合目`／アイブロウ廃止／相手＝名前＋級＋右クラスタ「▽＋🔍」／結果トグルはドット廃止・文字色のみ／ピッカーは数値＋単位中央密着／アクション行「キャンセル｜登録」）。以下は個別の実装注意。
- **指導試合（isLesson）:** 枚数差ピッカー先頭に「指導」（登録相手の試合のみ表示）。選択時 `isLesson=true`・`scoreDifference=null`。小ヒント「指導試合＝枚数差なし」を表示。→ `match-form-states` ②。
- **数値レンジ（現状維持）:** 枚数差 **0〜25（0 を含む）**。お手付き **「未入力(null)」＋0〜20**。final/states モックの option は例示的に省略表示しているだけ。実装は現状レンジを厳守。
- **枚数差/お手付きピッカーの様式（round 3 決定）:** 現行の一括入力（`BulkResultInput.jsx` 455–493行）と**同じ数値ピッカー**を使う＝箱で囲わず「下線・中央・太字の数値セレクト」（枠線 `#d0c5b8`／focus `#82655a`、`-`＝未選択／0〜25／指導）。共有 `.mf-picker`（`_match-form-final.css`）を当該様式に変更し、全カードへ反映。横並びの一貫性のため**お手付きにも同様式を適用**（枠線ボックスのまま残す選択肢も可）。表示は **数値＋単位（`枚`/`回`）を中央で密着**させて確定（下線はフィールド全幅・▽ は右端／指導・不明時は単位なし）。
- **結果トグル:** 枠線＋選択時のみ文字色（勝ち `#15803d`／負け `#b91c1c`）。**ドットは廃止し文字色のみ**（round4 で決定）。面で塗らない静かな方針で確定。
- **相手未選択（簡易入力）/抜け番導線:** `match-form-states` ①（主題位置を相手セレクト＋破線の控えめ「抜け番として記録する」）。
- **抜け番モード:** `match-form-bye`（hairline 選択リスト・選択時のみ ink＋アイコン着色＋右チェック・「その他」は自由記述の hairline 行）。
- **編集モード（isEdit）:** `match-form-edge` ①（タブ/スワイプ無し・日付のみ・ボタン「更新する」）。
- **その他状態:** 練習日でないブロック／上書き hairline 通知／参加未登録・切替破棄モーダル = `match-form-edge` ②③④・`match-form-states` ③。
- **スワイプ:** 案内テキストは省略するが**スワイプ挙動自体は維持**（タブが主アフォーダンス）。最終確認は実装時。
- **データ面:** 会場名・相手の級の**表示用 DTO 追加が必要**（§10／→ `/define-feature`）。保存ロジックは不変。新トークン（warm-taupe／green-700・red-700／角丸10px 等）の Tailwind への落とし込み（config 追加 or 任意値クラス）は実装時に方針決定。

## 10. 要件への宿題（→ /define-feature match-form-redesign）
round 4 でユーザーが states に新規表示項目を追加。**純UIではなくなり、軽い backend/DTO 改修が必要**：
- **会場名をヘッダーに表示:** データ源 `PracticeSession.venueId` → `Venue`（会場マスタ名）。現状のセッション取得 API（`practiceAPI.getByDate`）の DTO に**会場名を含めるか**要確認・要追加。
- **対戦相手の級を名前脇に表示:** データ源 `Player.kyuRank`（A級〜E級）。簡易入力の選手一覧（`playerAPI.getAll`）と**ペアリング由来の相手**の双方に級を載せる必要（pairing DTO に kyuRank が無ければ追加 or opponentId で選手マスタ参照）。段位 `danRank`・所属 `karutaClub` も同様に出すなら同じ対応。
- 上記2点は表示専用（保存ロジックに影響なし）。`/define-feature` で DTO 追加範囲を薄く詰めてから実装が安全。
- **対戦相手の選択モデル（round 6・要件で確定）:** 相手プルダウン＝**その練習日の参加者＋「抜け番」**。参加者以外（システム全登録選手）は**「未参加の選手から検索」ボタン**から検索選択。確定後は名前タップ（右シェブロン）で再変更。→ 当日参加者リスト取得（`practiceAPI.getPlayerParticipations` 等）と全選手検索（`playerAPI.getAll`／検索UI）の使い分け、未参加者を相手に選んだ場合の参加登録要否（既存「参加未登録の登録促し」モーダルとの関係）を `/define-feature` で確定。
> **視覚は locked（2026-06-30、ユーザー承認済み）。** 残りは表示データの DTO 確定と対戦相手の選択挙動のみ＝`/define-feature match-form-redesign` で詰める（→ その後 `/implement`）。

---
## 進行ログ
- **round 0（現状スナップショット）:** Claude Design「Match Tracker Design System」プロジェクト（projectId `1f747846-a832-423a-90d2-fdc9d0b5e59b`）に push。グループ `試合フォーム (Redesign)` / カード「試合フォーム — 現状 (MatchForm)」/ `preview/match-form-current.html`。通常入力（ペアリング確定）状態を再現。ユーザーのフィードバック待ち。
- 作業ディレクトリ: `C:/tmp/design-screen/match-form-redesign/`（`preview/_card.css`, `preview/_match-form.css`, `preview/match-form-current.html`）。
- **round 1（ユーザーがデザイン側で再設計 → pull-back）:** ユーザーが claude.ai/design 上で Anti-Slop 原則を確立し `match-form-final`（途中版 `match-form-redesign` / `match-form-refined`）と `design-principles.html` を作成。`match-form-final.html` ＋ `_match-form-final.css` をローカルへ pull-back 同期（`_card.css` は変更なし）。レビュー実施＝方向性は良好だがハッピーパスのみのため§9の機能ギャップを要解決。デザイン側に自動生成物（`CLAUDE.md`/`styles.css`/`_ds_bundle.js`/`_ds_manifest.json`/`_adherence.oxlintrc.json`）あり。途中版 `redesign`/`refined` は未レビュー。
- **デザイン確定（locked・2026-06-30）:** ユーザー承認「デザインはこれで確定」。最後に **▽（プルダウン affordance）＋🔍検索を常に行の右端クラスタ化**（`.mf-opp-chev` に `margin-left:auto`／名前の長短に依らず ▽ は検索のすぐ左）して `_match-form-ext.css` 再 push。集約レビュー用カード `preview/match-form-latest.html`（通常入力／相手未選択／指導／抜け番／編集の5状態）を追加 push。status=locked。次の一手＝`/define-feature match-form-redesign`（§10：会場名・級の表示DTO＋対戦相手の選択挙動を薄く確定）→ `/implement`。
- **round 6（states の対戦相手フロー刷新を pull-back＋未定義2クラス補完）:** ユーザーが claude.ai/design 上で `match-form-states.html` ＋ `_match-form-ext.css` を編集し、**対戦相手の選択フローを刷新**。(1) 相手未選択時は主題位置に**当日参加者＋「抜け番」のプルダウン**（`.mf-oppselect`・下線撤去のプレーン大文字 26px）、(2) その右に**「未参加の選手から検索」アイコンボタン**（`.mf-search-other`＝控えめ hairline 38px・システム全選手を検索）、(3) 相手確定時は**名前＋右シェブロン**（`.mf-opp-chev`）で「名前タップ＝相手変更」を表現（旧「変更」テキストリンクを置換）＋同じ検索ボタン、(4) 枚数差・お手付きに**単位 `枚`/`回`**（`.mf-unit`／指導・不明時は markup で出さない）。`.mf-subject--pick`・`.mf-oppselect`・`.mf-search-other` は**ユーザーが CSS まで自前定義済み**。**Claude 補完＝未定義の2クラスのみ**＝`.mf-opp-chev`（中央寄せ・淡色）／`.mf-unit`（下線ピッカー右端に絶対配置）。`_match-form-ext.css` を**リモート正に同期**して2クラス追記し再 push（writes=`preview/_match-form-ext.css` のみ）。対戦相手の選択モデルは§10に要件宿題として記録。**確認2点を反映:** (a) 検索ボタンを行の右端へ（`.mf-search-other { margin-left:auto }`）、(b) 数値ピッカーを「数値＋単位を中央密着・下線は全幅」へ改修（`.mf-picker` を flex 化＋`.mf-unit` の絶対配置を解除）。**全カード統一を完了:** `final.html` は既にユーザーが states パターンをマークアップ反映済みだったが `_match-form-ext.css` への `<link>` 欠落で未適用だった→link 追加で復旧。`edge.html`（①編集／③④モーダル背景）と `bye.html` は 日付＋会場ヘッダー・N試合目・アイブロウ削除・相手フロー（級＋▽＋検索）・結果ドット削除・単位・`.mf-actions`（キャンセル｜登録、編集は更新する）を反映。②ブロック画面は会場なし日付のみ。ext.css・final・edge・bye の4ファイルを再 push（written:4）。→ **全カードが states と同一言語に統一**。残る論点は§10の表示データDTO（会場名・級）と対戦相手の選択モデル要件のみ。
- **round 5（未定義クラス修正＋日付＋会場ヘッダーを①②へ横展開）:** (1) states でユーザーが使った未定義クラスを `_match-form-ext.css` に定義（`.mf-grade`＝小さめ淡色／`.mf-actions`＝キャンセル左｜登録右の行／`.dnum`）＋ユーザー編集（oppselect 22→26px）同期。(2) ユーザー指示「画面上部の日付(曜)＋会場表示を**①対戦組み合わせ・結果閲覧（MatchResultsView）**②**結果の一括入力（BulkResultInput）**にも適用」に対応。新カード `preview/session-header.html`＋`preview/_session-screens.css` を追加（緑ナビ中央に `10/5(日)　近江勧学館`、①は‹日付›前後移動・②は「対戦変更」＋タブの既存操作を維持）。→ **セッション共通ヘッダーを3画面で統一**。データ＝会場名は `PracticeSession.venueId→Venue`（各セッションDTOに会場名を載せる小改修が必要、§10）。**未了:** states の各種変更（N試合目/eyebrow削除/ドット削除/登録ラベル/級）の **final・edge への波及は未反映**（次ラウンド）。
- **round 4（ユーザーが states を design 側で大幅編集 → pull-back 確認）:** `match-form-states.html` を claude.ai/design で編集。主な変更＝(1) ヘッダーを「`10/5`(日)　近江勧学館」＝**日付短縮＋会場名**、(2) タブ表記 `第N試合`→`N試合目`、(3) **アイブロウ（第N試合・結果を記録）を削除**、(4) 結果トグルの**ドット削除**（文字色のみ）、(5) **相手名に級** `佐藤 美咲(A)` / 選手一覧に `（A級）`、(6) 枚数差ピッカーを**フル option 化**（指導＋0〜25枚、接尾辞 `枚`/`回` は残置＝bare 不採用）、指導は「枚数差0とは別概念」と明記（note: `matchType=lesson/scoreDifference=null`）、(7) アクションを `.mf-actions` で `キャンセル｜登録`（「登録する」→「登録」）。`_match-form-ext.css` は `.mf-oppselect` を 22→26px のみ変更。**データ確認:** 会場名=PracticeSession.venueId→Venue、級=Player.kyuRank(A〜E) で裏付けあり（→§10 で DTO 追加が宿題）。**未定義クラス3つ**（`.dnum`/`.mf-grade`/`.mf-actions`）はCSS未定義で意図通り描画されない可能性（要スタイル定義）。**変更は states のみ**＝final/edge へ波及するか要確認。**注意: remote の states.html / _match-form-ext.css がローカルより先行**（次に編集/再pushする前に get_file で pull-back すること）。
- **round 3（枚数差ピッカーを一括入力スタイルへ）:** ユーザー指示「現行の一括入力の枚数差ピッカーを使う」。`BulkResultInput.jsx` の枚数差 select（下線・中央・太字・`-`/0〜25/指導、枠線 `#d0c5b8`/focus `#82655a`）を確認し、共有 `.mf-picker` を箱→下線様式へ変更（`preview/_match-form-final.css` 1枚を再 push、全カード反映）。取り込み範囲＝「枚数差フィールドだけ差し替え」（結果トグルは final 維持）。お手付きも横並び一貫性で同様式に。接尾辞 枚/回 はモック残置（bare 化要確認）。
- **round 2（final を拡張モック化 → push）:** §9ギャップを同 Anti-Slop 原則で補完するカードを追加 push。`preview/match-form-states.html`（①相手未選択＋抜け番導線 ②指導 ③負け＋上書き通知）／`preview/match-form-bye.html`（抜け番モード＝hairline 選択リスト）／`preview/match-form-edge.html`（①編集 ②練習日でない ③参加未登録モーダル ④切替破棄モーダル）＋ 共有 `preview/_match-form-ext.css`。**残る確認待ち判断:** 指導の置き場所（枚数差ピッカー内 案を提示／トグル案は代替）・枚数差0/お手付き未入力&最大の要否・結果トグルの視認性（面塗り廃止の是非）。これらが決まれば収束ゲートへ。
