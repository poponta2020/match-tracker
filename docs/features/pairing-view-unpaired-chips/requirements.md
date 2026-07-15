---
status: completed
design_required: false
mode: 改修（delta）
completed_sections: [変更の動機と内容, 変更後の挙動, 変わらないもの, Acceptance Criteria]
---
# 対戦組み合わせ閲覧時に未組み合わせ選手をチップ表示（pairing-view-unpaired-chips）要件定義書

> 改修（delta）。対象は `/pairings`（`PairingGenerator.jsx`）の**閲覧まわりの表示のみ**。組み合わせ作成・自動組み合わせ・ロック・保存・キャンセル反映などの機能ロジックは一切変更しない。既存の画面仕様は `docs/SCREEN_LIST.md` の No.19、および [[pairing-manual-lock]]・[[pairing-lock-display-fixes]] が正典。本書はその「閲覧時の未組み合わせ選手の可視化」の差分のみを扱う。

## 1. 概要

`/pairings`（組み合わせ作成画面）を**閲覧している状態**で、一部の組だけが作られている（結果入力済み・手動ロック・未ロックのいずれでも）とき、まだどの組にも入っていない参加者が画面上にまったく表示されない。この改修では、その「未組み合わせの参加者」を、**組み合わせが1件も無いとき（＝参加者一覧）と同じ見た目のチップ**で閲覧時にも一覧表示する。純フロントの表示追加であり、バックエンド・DB・API には手を入れない。

- 対象は**閲覧モード（`isViewMode`）**と**読み取り専用モード（`isReadOnly`＝他試合に未保存の編集がある状態でこの試合を見ているとき）**。
- 編集モードでは従来どおり「待機中」リスト（D&D／タップ選択・活動プルダウン付き）が出るため、この改修の対象外（挙動不変）。

## 2. 変更の動機と内容

### 現状
- 参加者一覧のチップ表示は `shouldShowParticipantSection(pairings) === (pairings.length === 0)` でガードされ、**組み合わせが1件でもあると出ない**（`PairingGenerator.jsx` 946行付近）。
- 未組み合わせの参加者は、既存組み合わせ読込時に `waitingPlayers`（＝その試合の組み合わせ対象参加者のうち、どの組にも入っていない人）として**すでに算出・保持されている**（`loadExistingPairingsToState` 159–166行、初回ロード・タブ切替の両経路で設定される）。
- しかしその `waitingPlayers` を描画する「待機中」セクションは `!isReadOnly && !isViewMode`（＝編集モードのみ）でガードされている（1219行付近）ため、**閲覧モード・読み取り専用モードでは一切表示されない**。

### なぜ問題
- 一部だけ組が作られている試合を閲覧すると、「組まれている対戦」しか見えず、**まだ組まれていない人が誰なのかが画面から分からない**。組み合わせが1件も無いときは全員がチップで見えるのに、1組でも作った瞬間に残りの人が視界から消えるのは一貫性に欠ける。

### 変更内容
- 閲覧モード・読み取り専用モードで、`waitingPlayers` が1名以上いるとき、**読み取り専用のチップ一覧**を表示する。見た目・並びは参加者一覧セクション（970–980行）を踏襲する：
  - `PlayerChip`（操作不可の名前ピル）を `sortPlayersByRank(waitingPlayers)` の順で `flex flex-wrap` 表示。
  - 見出しは **「待機中 N名」**（編集モードの待機中セクションと同じ語で統一。N は `waitingPlayers.length`）。
  - **チップのみ**。活動プルダウン・活動内容の表示、選手追加ボタン、D&D／タップ選択、ドロップゾーンは出さない。
- 表示位置は、組み合わせ一覧（各組の行）の**直後**（編集モードの待機中セクションと同じ位置）。
- 表示可否は純関数に切り出す：`shouldShowViewModeUnpairedSection({ isReadOnly, isViewMode, pairings, waitingPlayers })` を `pairingDisplayLogic.js` に追加し、本番 JSX とユニットテストが同じ関数を import する（既存の `shouldShowParticipantSection` / `shouldShowReshuffleButton` と同じ退行防止パターン）。
  - 条件: `(isReadOnly || isViewMode) && pairings.length > 0 && waitingPlayers.length > 0`。
  - この条件は編集モードの待機中ガード `!isReadOnly && !isViewMode` の**厳密な補集合**であり、両セクションが同時に出ることはない（相互排他）。
  - `pairings.length > 0` を残すのは重要：`isReadOnly` は `pairings.length === 0`（他試合を編集中に、まだ組が無いこの試合を見ている）でも真になりうるため、これが無いと参加者一覧セクション（`pairings.length===0` で真）と二重表示になる。

## 3. 変更後の挙動と、変わらないもの（回帰の明示）

### 変更後の挙動
- 一部だけ組がある試合を**閲覧**すると、組み合わせ一覧の下に「待機中 N名」＋未組み合わせ選手の名前チップ（読み取り専用）が並ぶ。
- 未組み合わせ選手が0名なら、当該セクションは出ない（空メッセージも出さない）。
- 「編集」ボタンで編集モードに入ると、従来どおりの「待機中」セクション（活動プルダウン・D&D・選手追加付き）に切り替わる（この閲覧用チップ一覧は消える）。

### 変わらないもの（維持すべき既存挙動＝回帰ACの対象）
- **編集モードの待機中セクション**（`!isReadOnly && !isViewMode`）は一切変更しない（D&D・タップ選択・活動プルダウン・選手追加・ドロップゾーン・空メッセージ「待機中の選手はいません」）。
- **参加者一覧セクション**（`pairings.length === 0` の新規作成時）は一切変更しない。
- 組み合わせ各組の行表示（結果入力済み／手動ロック／キャンセル反映＝取消線・「キャンセル」タグ・両キャンセル非表示）は一切変更しない。
- `waitingPlayers` の**算出ロジック**（どの選手を未組み合わせとみなすか）は変更しない。キャンセル済み（status CANCELLED）は元々 `matchParticipants`（組み合わせ対象）から除外されるため、閲覧チップにも現れない。
- バックエンド・DB・API・`/pairings/summary`（`PairingSummary`）は対象外。

## 4. Acceptance Criteria

| ID | 条件（客観的に判定できる文） | 検証手段 |
|----|------|------|
| AC-1 | 閲覧モード（`isViewMode:true`）で `pairings.length>0` かつ `waitingPlayers.length>0` のとき、`shouldShowViewModeUnpairedSection` が true を返す | auto-test |
| AC-2 | 読み取り専用モード（`isReadOnly:true`）で `pairings.length>0` かつ `waitingPlayers.length>0` のとき、`shouldShowViewModeUnpairedSection` が true を返す | auto-test |
| AC-3 | `pairings.length===0` のときは `isViewMode`/`isReadOnly` の値に関わらず `shouldShowViewModeUnpairedSection` が false を返す（参加者一覧との二重表示を防ぐ） | auto-test |
| AC-4 | `waitingPlayers.length===0` のときは false を返す（未組み合わせ0名では空セクションを出さない） | auto-test |
| AC-5 | 編集モード（`isReadOnly:false` かつ `isViewMode:false`）では、`pairings`/`waitingPlayers` の値に関わらず `shouldShowViewModeUnpairedSection` が false を返す（編集モードの待機中セクションと相互排他） | auto-test |
| AC-6 | 一部だけ組がある試合を閲覧すると、組み合わせ一覧の下に「待機中 N名」の見出しと未組み合わせ選手の名前チップ（読み取り専用・`sortPlayersByRank` 順）が表示され、活動プルダウン・選手追加ボタン・ドロップゾーンは表示されない | verify |
| AC-7 | 既存の pairing 関連ユニットテスト（`pairingDisplayLogic.test.js` 含む）・lint がすべて green（回帰なし） | auto-test |

- 検証手段内訳: **auto-test 6件 / verify 1件 / manual 0件**
- AC-6 が `verify` なのは、実際のマウント表示（チップ描画・活動プルダウン非表示）の確認であり、当画面に API モック込みのフルマウント回帰ハーネスが無いため。純関数 `shouldShowViewModeUnpairedSection` の真偽は AC-1〜5 の auto-test で担保し、実描画は実装後に `/verify` または手動で確認する。

## 5. Non-goals（今回やらないこと）

- 閲覧時のチップに活動内容（読み 等）や抜け番活動を表示すること（チップのみ）。
- 閲覧モードからチップを D&D／タップで操作できるようにすること（読み取り専用。操作は従来どおり「編集」ボタンで編集モードに入ってから）。
- 編集モードの待機中セクション・参加者一覧セクションの見た目や挙動の変更。
- `waitingPlayers` 算出ロジック・組み合わせ対象（WON/PENDING 判定）の変更。
- バックエンド・DB・API・`/pairings/summary` その他画面への波及。

## 6. 技術的制約・契約

- 純フロント（`karuta-tracker-ui`）のみ。BE・DB・API・entity は不変。
- 表示可否判定は `pairingDisplayLogic.js` の純関数に集約し、条件式をテスト側にコピーしない（既存パターン踏襲）。
- 見た目は参加者一覧セクション（`PlayerChip` + `sortPlayersByRank` + `flex flex-wrap gap-2`）を踏襲し、新規の視覚デザインは起こさない（＝`design_required: false`）。
- ロール制御（ADMIN/PLAYER は自/所属団体のみ）は既存の取得 API 側で担保済みで、本改修は取得済みデータの表示のみ。

## 7. 設計判断の根拠

- **`waitingPlayers` を再利用**: 未組み合わせ選手は閲覧経路でも既に算出済み。新たな状態やクエリを足さず、表示ガードだけを変えるのが最小で安全。
- **純関数＋補集合設計**: 表示条件を編集モードガードの厳密な補集合にすることで「編集の待機中」と「閲覧のチップ」が二重表示・二重欠落しないことをユニットテストで機械的に担保する。
- **`pairings.length>0` を条件に残す**: `isReadOnly` は組0件でも真になりうるため、参加者一覧（組0件で表示）との二重表示を防ぐガードとして必須。
- **見出しは「待機中」で統一**: 同じ選手集合が編集モードでは「待機中」と表示されるため、閲覧⇄編集で語がぶれないようにユーザー選択で統一。
- **design-screen はスキップ**: 既存の参加者チップ表示を位置違いで再利用するだけで視覚設計上の新規論点が無いため（プロジェクトのリーン運用に準拠）。
