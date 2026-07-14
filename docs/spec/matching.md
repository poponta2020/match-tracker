# 対戦組み合わせ

> **責務:** 対戦組み合わせの自動生成・管理の仕様
> **関連画面:** `/pairings`、`/pairings/summary`
> **主要実装:** `karuta-tracker-ui/src/pages/pairings/`（PairingGenerator・PairingSummary・PairingHelp）、`karuta-tracker/src/main/java/com/karuta/matchtracker/controller/MatchPairingController.java`、`karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchPairingService.java`

## 機能仕様

### 組み合わせ作成（PairingGenerator）

ADMIN以上が利用可能。練習日・試合番号ごとに対戦ペアを作成する。

**使い方ヘルプ（ⓘ）:**
- 画面上部（`PageHeader` 直下・右寄せ）に「ⓘ 使い方」ボタンを配置し、押下でドロップダウンパネルを開閉する（外側タップ・✕で閉じる）。
- パネルは4セクション（選手の入れ替え方／ロックの意味と使い方／保存の流れ／日付列の見方）を表示する。
- 初回訪問時のみ端末単位で自動表示する（`localStorage` キー `pairingHelpSeen` で既読管理。使用不可環境では毎回自動表示にフォールバック）。カレンダーの「記号の見方」と同方式。実装は独立コンポーネント `PairingHelp`。

**画面の表示モード（新規作成UI / 既存組み合わせ表示）:**
- 参加者一覧と「自動マッチング」ボタン（＝新規作成UI）は、その試合にまだ組み合わせが1つも無いとき（`pairings.length === 0`）のみ表示する。
- 既存の組み合わせがある試合は、結果入力済み・手動ロック・未入力のいずれであっても作成UIを表示せず、組み合わせ表示（閲覧モード／編集モード）に統一する。これにより結果未入力の試合と結果入力済みの試合の見た目を一貫させる。
- 「ロック済みの組はあるが未組の参加者が残っている」ケースで追加の自動マッチングを行いたい場合は、**再シャッフルボタン**（下記）で「ロックされた組以外をシャッフル」するか、「全削除」または編集モードでの手動配置で対応する。
- **再シャッフル導線（pairing-reshuffle-except-locked）:** 組み合わせが1件以上ある編集可能状態（`!isReadOnly && !isViewMode && pairings.length > 0`）では、ロック以外を組み直す再シャッフルボタンを常設する。文言はロック済みの組（`hasResult || locked`）の有無で動的に切り替わる（1件以上→「ロックされた組以外をシャッフル」／0件→「再シャッフル」）。実行前に `window.confirm` で確認し、キャンセル時は何も変えない。閲覧モードからは「編集」を1タップしてから使う。保存前の未保存ロックも尊重する（画面の現在状態を基準にする）。

**手動組み合わせ（ドラッグ&ドロップ / タップ選択）:**

編集モード時、以下の2つの操作方法で選手入替が可能。状態遷移ロジックは共通（`computeDragResult`）。

- *ドラッグ&ドロップ:*
  - 選手カードを長押し（スマホ、200ms）/ クリック（PC）で掴み、別の選手カードにドロップして入れ替え
- *タップ選択モード（スマホ向け代替操作）:*
  - 選手カードをシングルタップで選択（選択中は枠線・背景色でハイライト）
  - 続けて別の選手カード・空き枠・待機エリア・新規ペア作成ゾーンをタップして配置/スワップ
  - 同じカードを再タップ、または画面他領域をタップで選択解除
  - ドラッグ開始時は選択状態を自動解除（D&D との整合性確保）
- *共通の挙動:*
  - 対戦組み合わせ間のスワップ、同一ペア内の左右入れ替えに対応
  - 待機リストの選手をペアリング枠に移動して入れ替え、逆にペアリング枠から待機リストへの移動も可能
  - 組み合わせリスト下部のドロップゾーン/タップゾーンに待機選手を配置して新規ペアリングを作成
  - 片方が空欄のペアリングは保存不可（2人揃うまで保存ボタン無効化）
  - ペア変更時にリアルタイムで直近の対戦履歴を表示。当日他試合（自分の試合番号以外すべて）で既に同ペアが組まれている場合は `⚠今日` を赤字太字で警告表示
  - 同じ選手同士のペアは不可
  - 結果入力済み（ロック済み）ペア・手動ロック済みペア・閲覧モード・他試合に未保存変更がある時（`isReadOnly`）は入替操作を受け付けない（保護判定 = `hasResult || locked`）

**組み合わせ対象となる参加者の範囲:**
- 団体の運用設定により対象ステータスが切り替わる:
  - **抽選あり運用** (`DeadlineType.MONTHLY` で締め切りなしモードでない、北海道大学かるた会など): `status === 'WON'` のみ。抽選前の `PENDING` 参加希望者は対象外。
  - **抽選なし運用** (`DeadlineType.SAME_DAY` または `MONTHLY` + `isNoDeadline=true`、わすらもち会など): `status === 'WON'` または `status === 'PENDING'`。抽選を運用しないため参加登録時の `PENDING` のまま組み合わせ対象になる。
- `WAITLISTED` / `OFFERED` / `DECLINED` / `CANCELLED` / `WAITLIST_DECLINED` は常に組み合わせ対象から除外する。
- 対象画面: `PairingGenerator`（参加者表示・自動マッチング）、`BulkResultInput`（抜け番算出）、`MatchResultsView`（抜け番表示）すべて同一ルール。
- 判定ヘルパー: `LotteryDeadlineHelper.isLotteryDisabled(organizationId)` が `true` の場合に抽選なし運用扱い。
- バックエンドは `MatchPairingService.loadActiveParticipantIdsForMatch()` で上記ルールに従ってアクティブ参加者IDを取得する。
- フロント側は `PracticeSessionDto.pairingIncludesPending`（バックエンドが `isLotteryDisabled` を反映して返すフラグ）を見て判定する。これによりバックエンドとフロントを単一ルールに揃え、抽選前 `PENDING` の自動マッチング流入を防ぐ。

**自動マッチング:**
- 上記のアクティブ参加者IDリストを入力として最適なペアリングを生成
- アルゴリズム:
  1. 過去30日のペアリング履歴（`match_pairings` テーブル）と対戦履歴（`matches` テーブル）を統合取得
  2. 同日の前の試合で既に組まれたペアを除外
  3. 各候補ペアに対してスコアを計算（直近対戦からの日数が遠いほど高スコア）
  4. 貪欲法で最高スコアのペアから順に確定
  5. 奇数人数の場合は1名が待機者となる
- スコア計算: `-(100.0 / 最終対戦からの日数)`。初対戦は `0`、同日対戦は `-1000`
- **保持組の導出（`lockedPairs` 契約拡張・後方互換）:** `AutoMatchingRequest.lockedPairs`（nullable）で保持組の真をどこから取るかを切り替える。
  - `lockedPairs == null`（既存の新規作成フロー）: 従来どおり DB の `hasResult` / `locked` から保持組を導出（挙動不変）。
  - `lockedPairs != null`（空配列を含む・再シャッフル）: 手動ロックはクライアントの `lockedPairs` を正とし、DB の `locked` フラグは参照しない（ローカルで解除した組は再シャッフル対象・未保存でロックした組は保持）。DB 行の無い未保存組も保持するが、**両選手が当日アクティブ参加者（`loadActiveParticipantIdsForMatch`＝DB・組織スコープ適用済み）の場合のみ**（参加者の真は DB。任意ID・別団体IDの選手名を `lockedPairings` にエコーしない）。`null` 要素は無視する。**結果入力済み（`hasResult`）は常に DB から保護**する。保持組の選手を除外し、残り（非ロック組の選手＋待機者＝当日のアクティブ参加者）を上記アルゴリズムで再シャッフルする。

**一括保存（確定して保存）:**
- 試合番号ごとに全ペアを一括保存（既存の組み合わせは置き換え）。各組の手動ロック状態（`locked`）も `createBatch` に同梱して永続化する。
- 保存時の保護対象は結果入力済み（`hasResult`）のみ。手動ロック組はリクエストに `locked=true` で含めて送り、既存行を削除→再作成することでロック状態を再現する（これにより「解除」= `locked=false` での保存も正しく反映される）。
- 未保存の変更がある場合は他の試合番号に切り替え時に警告。編集中の試合を保存するまで他の試合は編集不可。

**結果入力済みロック:**
- 試合結果が入力済みのペアリングは自動マッチング・手動変更・一括保存で上書き・削除されない
- ロック判定: 対応する `matches` レコードの存在有無（`session_date` / `match_number` / `player1_id` / `player2_id` で照合）
- ロック済みペアリングのリセット: 全ロール（PLAYER+）が個別ペアリング単位で確認ダイアログ付きリセット可能（`match_pairings` と `matches` の両方を削除。ADMIN/PLAYER は自/所属団体のみ）
- 試合結果の編集・削除時はロックが自動解除される（`matches` レコードの変更・削除によりロック条件が消滅するため）

**手動ロック（pairing-manual-lock）:**
- 結果未入力の組でも、参加者（PLAYER/ADMIN/SUPER_ADMIN のいずれか）が「この相手と試合したい」組を明示的にロックできる。`match_pairings.locked` フラグで保持する。
- ロック/解除はローカル状態のトグルのみで、サーバには即時反映しない（下書き状態）。「確定して保存」を押したときに `createBatch` の `locked` で永続化する。ロック直後はその場でグレーアウト＋編集不可になり、もう一度「解除」を押すと編集可に戻る（いずれも保存するまでDB未反映）。
- 保護の挙動はロックの種類で異なる: **自動マッチング・回戦削除（全削除）** は結果ロックと同等に保護する（保護判定 = `hasResult OR locked`、対象2選手を自動組み合わせから除外し削除からも保持）。**一括保存（createBatch）** は結果入力済み（`hasResult`）のみ保護し、手動ロック組は削除→再作成で `locked` を再現する。
- 二重ブッキング（同一選手が同回戦の複数組）はUI上構造的に発生しない（選手移動で元の組から除去されるため）。ロック即時APIが行っていたサーバー側の二重ブッキング検証は、ロックのローカル化に伴い未使用となる。
- `PATCH /api/match-pairings/{id}/lock`・`/unlock` エンドポイントは残置するが PairingGenerator からは呼ばれない（即時ロック廃止に伴い未使用。将来クリーンアップ候補）。
- 解除は誰でも可能（ロックした本人に限定しない）。`locked` を false に戻すのみで組は残り、通常の未ロック組（編集・削除・自動再生成の対象）に戻る。保存済み（`id` あり）・未保存（`id` なし）いずれの手動ロック組も解除できる。
- 表示の区別: 手動ロック=「🔒 ロック」バッジ＋全ロール向け「解除」ボタン / 結果入力済み=「結果入力済」バッジ＋全ロール向け「リセット」ボタン（PLAYER+。バックエンドの団体スコープで自/所属団体のみに制限）。未ロック組のロックボタンは鍵アイコンのみ（テキストなし、`aria-label="ロック"` ＋ `title` で補足）。ロック表示・操作は編集画面（PairingGenerator）のみ（PairingSummary は対象外）。
- 結果ロックとの併存: 手動ロック組に後から結果が入れば `hasResult=true` となり結果ロックとしても保護される。結果入力済みの組では「解除」ボタンは表示しない（解除条件 = `locked && !hasResult`）。明示保存モデルでは結果入力済み組は保存リクエストに含めず手動ロック解除を永続化できないため、また結果ロックとして保護が継続するため。結果ごと取り消す場合は「リセット」を使う。

**対戦相手キャンセルの反映（pairing-cancelled-opponent）:**
- 組み合わせ作成後に参加者がその試合をキャンセル（`PracticeParticipant.status = CANCELLED`）した場合、作成済みの対戦組み合わせ表示へ反映する。**read-time（表示時に動的判定・非破壊）方式**で `match_pairings` テーブルは変更しない（DBスキーマ変更なし）。取得API（`getByDate` / `getByDateAndMatchNumber`）が各組DTOに `player1Cancelled` / `player2Cancelled` を付与する。判定 = `(その日のセッション, 選手, 試合番号)` の `practice_participants` 行が `CANCELLED`。**試合単位**で判定し（1試合だけキャンセルしても他試合の組には影響しない）、抜け番マーカー `match_number=null` 行は対象外。組織スコープは既存の取得APIと同一（org指定=当該団体セッション / org非指定=その日の全セッション）。さらに各組は**両選手が共に属するセッション単位**で判定し、org非指定で同一選手が同日同試合番号で別団体セッションにも居る場合のクロス団体誤反映を防ぐ。
- **閲覧モード:** 片方がキャンセルした組は、結果入力済みの行と同一構造（`flex-1` のペア＋右端タグ）で表示し、キャンセルした選手名を取消線＋グレー（`text-gray-400 line-through`）に、右端に「キャンセル」タグ（`Ban` アイコン＋グレー丸タグ `bg-gray-100 text-gray-600`）を付ける。取消線が付いた方がキャンセルした選手。**両方がキャンセルした組は行ごと非表示**。
- **編集モード:** 「編集」ボタンで編集へ入る際にキャンセルスロットを「空き」として実体化する（`materializeCancelledSlots`: 片方キャンセルは当該スロットを空きに・フラグ解除＋`cancelledEmptied` マーカー付与、両方キャンセルの組は除去）。キャンセルした選手は参加者プール・待機リストにも現れない（アクティブ参加者でないため既存ロジックで自然に除外）。空きのまま保存した場合、未完成組は `createBatch` に送られず（`buildSaveRequests` は両選手揃った組のみ送信）、キャンセルしていない生存側の選手はアクティブ参加者として残る（データ消失なし）。
  - 「確定して保存」ボタンの未完成ガード（片側空欄の作りかけ組があると無効化）は、**キャンセル由来で空き化した組（`cancelledEmptied`）では無効化しない**（`hasBlockingIncompletePair` から除外）。これによりキャンセルで相手が抜けた組は空きのまま保存できる。通常の作りかけ（片側空欄）は従来どおり保存ボタンを無効化する。また、キャンセル空き組のみが残った試合（完成ペア・待機者なし）でも `hasNothingToSave` は保存対象ありと判定し、`handleSave` は空の `pairings` で `createBatch` を呼んで孤立した既存の組レコードを削除する（`cancelledEmptied` を考慮）。
- **結果入力済み・手動ロックとの優先順位**: 結果入力済み（`hasResult`）の組は結果が正のためキャンセルを反映しない（結果入力済表示のまま／両方キャンセルでも非表示にしない）。手動ロック（`locked`・結果未入力）の組は片方キャンセルがあればロック表示よりキャンセル表示／空き化を優先する（ロックは崩れたとみなし、編集モードへ入る際に `locked` を解除して空きにする）。行描画の優先順位は純粋関数 `showsResultLockedRow` / `shouldHideRow` に集約し本番・テストで共有する。
- LINE送信用テキスト（`/pairings/summary`）はスコープ外（従来どおりキャンセル者も名前が出力される）。
- 反映の仕組みは read-time のため、キャンセル取り消し・当日補充での再参加時は表示が自動的に元へ戻る（組レコードを残している間）。

### 組み合わせ（MatchPairing）

| フィールド | 型 | 必須 | 説明 |
|---|---|---|---|
| `sessionDate` | LocalDate | Yes | 練習日 |
| `matchNumber` | Integer | Yes | 試合番号 |
| `player1Id` | Long | Yes | 選手1 ID |
| `player2Id` | Long | Yes | 選手2 ID |
| `createdBy` | Long | Yes | 作成者ID |

### 組み合わせサマリー（PairingSummary）

百人一首の札番号（01〜00）に基づく試合の進行ルールを表示する。

- 3試合サイクルで札の使い方を決定:
  - **第1試合**: 1の位 — 10種から5種を選択
  - **第2試合**: 抜き — 第1試合の残り5種から3種を選択、1枚を抜く
  - **第3試合**: 十の位 — 残り7種から5種を選択
- テキストをクリップボードにコピー可能
- **読手表示**: 各試合に「読み」に設定された抜け番選手（`ByeActivity.activityType = READING`）がいる場合、`{N}試合目` 行の直後に `【読手：○○】` 行を出力する（同一試合に複数いる場合は「、」区切り）。読手は `byeActivityAPI.getByDate(date)` で取得し、取得失敗時は読手なしでテキスト生成を継続する

**札ルールの日付シード決定論化:**
- 札ルールは `(date, 再生成カウンタ nonce)` をシードにした決定論的生成で導出する（`hashSeed(date, nonce)` → `mulberry32`（決定論PRNG）→ seeded Fisher-Yates）。同じ日・同じ nonce・同じ試合番号なら、いつ・どの端末で開いても常に同じ札ルールになる
- 札ルール配列そのものは保存しない。保存するのは日付ごとの再生成カウンタ `nonce`（既定 0、キー `karuta-tracker:card-nonce:<YYYY-MM-DD>`）のみ。`nonce=0`（既定）はシードが日付のみに依存するため、保存が無くても全端末・過去日・再訪で完全一致する
- `totalMatches`（試合数）を増やしても、決定論列の先頭が安定するため先頭の試合の札ルールは変わらない
- 「札を再生成」ボタン押下時は `window.confirm('現在の札ルールを上書きして再生成します。よろしいですか？')` で確認し、OK のみ当日の `nonce` を +1 して保存し、`hash(date + nonce)` で再計算する。再生成していない既定状態は全端末で不変、再生成した端末のみ枝分かれする（サーバ保存しない仕様上の許容事項）
- 画面ロード時、旧形式の札ルール配列キー（`karuta-tracker:card-rules:<date>`）は全削除し、`nonce` キーは「今日」以外を削除する

**LINE送信用テキスト（全試合／単一試合）:**
- URL クエリ `?matchNumber=N` の有無で表示モードを切り替える:
  - **なし**: 従来どおり全試合分のテキスト
  - **あり（1..totalMatches の有効値）**: 単一試合モード。日付見出し（`M月D日`）＋対象 `N試合目` のブロック（札ルール・読手・ペア）のみを、全試合テキストの該当ブロックと完全一致する形式で表示する。札ルールは決定論化により全試合テキストと一致する
- `matchNumber` が数値でない／1未満／`totalMatches` 超過は無効として全試合モードにフォールバックする。対象試合のペアが空でも日付見出し＋`N試合目　札ルール` を表示する（URL直接アクセス防御）
- 「札を再生成」ボタンは**当日（今日）かつ全試合モードのときのみ表示**する。単一試合モードでは非表示(札ルールはその日全体の概念であり単一試合画面からの全体再生成は誤操作・混乱のもと）。過去日・他日も非表示とし、決定論の既定札ルール（全端末一致）を表示する（cleanup の「今日以外の nonce 削除」方針と整合）
- テキストはクリップボードにコピー可能。対戦組み合わせ画面（PairingGenerator）の「全試合 / {N}試合目」セグメントトグル＋生成ボタンから、対象に応じた URL（全試合 `?date=...` / 単一試合 `?date=...&matchNumber=N`）で本画面へ遷移する

## 画面

**パス**: `/pairings`

> UI は脱カードデザインにリデザイン済み（`pairings-ui-change`）。現在の画面詳細は [docs/SCREEN_LIST.md](../SCREEN_LIST.md) #19 が正典。以下は機能・API 観点の表示内容。

**表示内容**:
- 日付・会場名の表示（ヘッダに `日付＋会場名`。この画面での日付変更＝日付入力／「今日」ボタンは廃止し、`?date=` クエリ／当日デフォルトから受け取る）
- 試合番号選択（同一フットプリントの数字タブを左寄せ表示。アクティブは滑る cream ハイライトで表現し連結パネルに接続、≤7 は1行・8以上は横スクロール）
- 参加者一覧（新規作成時のみ・折りたたみ廃止で常時展開）
- 主アクション「対戦編集」ボタン（＝従来の自動マッチング。文言変更のみ・挙動不変）→ `POST /api/match-pairings/auto-match`
- **参加者一覧・「自動マッチング」ボタンは、その試合にまだ組み合わせが1つも無いとき（新規作成時 = `pairings.length === 0`）のみ表示**。既存の組み合わせがある試合は、結果入力済み・未入力に関わらず組み合わせ表示（閲覧モード/編集）に統一し、作成UIは出さない（結果未入力の試合と表示を一貫させるため）
- 組み合わせ表示ヘッダ（編集可能状態）に**再シャッフルボタン**（動的文言・確認ダイアログ）→ `POST /api/match-pairings/auto-match`（`lockedPairs` にロック組を同梱）。表示可否は `shouldShowReshuffleButton`、文言は `reshuffleButtonLabel`（pairingDisplayLogic）。送信 body は `computeLockedPairsInput` ＋ `buildAutoMatchBody`（pairingLockLogic）で組み立てる（`undefined`＝対戦編集は `lockedPairs` を送らず、配列＝再シャッフルは空配列でも必ず送る）
- 提案されたペア一覧（ドラッグ&ドロップ / タップ選択対応）
  - 選手カード（DraggablePlayerChip）を長押し/クリックでドラッグして入れ替え
  - 選手カードをシングルタップで選択 → 別カード/空き枠/待機/新規ペアゾーンをタップして配置（タップ選択モード）
  - 最近の対戦履歴（日付、何日前）
- 手動調整: 選手カード同士のスワップ、待機リストとの入れ替え
- 新規ペアリング作成ドロップゾーン（待機選手をドロップ/タップして新規行作成、待機選手選択時のみ表示）
- 「確定して保存」ボタン → `POST /api/match-pairings/batch`（片方空欄時は無効化。手動ロック組は空欄チェック対象外）。各組は `{ player1Id, player2Id, locked }` を送信し、`handleSave` のフィルタは `!hasResult`（手動ロック組も送信）。空判定は「完成した組（ロック含む）が0かつ待機者0」。
- 待機者リスト（DroppableSlot、選手はDraggablePlayerChip）
- 手動ロック（pairing-manual-lock → ロックの明示保存化 pairing-lock-explicit-save-and-help）:
  - 編集可能で両選手が揃った各組に鍵アイコンのみのロックボタン（テキストなし、`aria-label="ロック"` ＋ `title`）。`handleLockPairing(index)` は**ローカル状態の `locked=true` トグルのみ**（サーバ通信なし）。`setHasUnsavedChanges(true)` ＋ `saveDraft` で未保存ドラフトに反映する。ロック直後はその場でグレーアウト＋編集不可になる。
  - 手動ロック済み組は「🔒 ロック」バッジ＋全ロール向け「解除」ボタン。`handleUnlockPairing(index)` も**ローカル状態の `locked=false` トグルのみ**。表示条件から `pairing.id` を撤廃し、保存済み（id あり）・未保存（id なし）いずれの組も解除できる。結果入力済みロックは「結果入力済」バッジ＋ADMIN以上の「リセット」と区別。
  - ロック/解除は「確定して保存」を押すまでDBに反映しない（下書き）。永続化は `createBatch` の `locked` 同梱で行う。自動マッチング・回戦削除からの保護は保存済みロック状態に対して有効（保護判定 = `hasResult || locked`）。一括保存（createBatch）の保護は `hasResult` のみで、手動ロック組は削除→再作成で `locked` を再現する。
  - 二重ブッキング（同一選手が同回戦の複数組）は選手移動で元から除去されるためUI上構造的に発生せず、ロック時のサーバー検証（旧 `PATCH /lock`）は未使用。
- 使い方ヘルプ（ⓘ）: `PairingHelp` コンポーネントを `PageHeader` 直下・右寄せに `<PairingHelp ready={!matchLoading} />` で配置。`Info` ボタン＋ドロップダウンパネル（4セクション）。`showHelp` state は `localStorage('pairingHelpSeen')` 未設定なら初期 true（例外時も true フォールバック）。`ready`（=ローディング完了）後に既読フラグ保存、外側タップ（`mousedown`）・✕ で閉じる `useEffect`/`ref`。カレンダー（PracticeList）の「記号の見方」と同方式。

**タップ選択モードの state 設計**:
- `selectedPlayer`: `{ playerId, playerName, source }` 形式（`source` は `DraggablePlayerChip.data.source` と同形）
- `handleChipClick` / `handleSlotClick` でクリック発火、`executePlacement(dest)` 共通関数で `computeDragResult` 呼出し〜state 更新〜`fetchPairHistory` 発火までを実行
- `handleDragStart` 冒頭で `setSelectedPlayer(null)` を呼び、D&D との状態不整合を防止
- `selectedPlayer` が非 null の時のみ `document` クリックリスナーを張り、チップ/スロット以外のクリックで選択解除（チップ/スロット側は `e.stopPropagation()` で伝播停止）
- 編集モード時のみ有効（`isReadOnly` / `isViewMode` / `hasResult` / `locked` 時は早期 return）

**アルゴリズム**:
- 過去30日の対戦履歴取得
- スコア = -(100 / 最終対戦日からの日数)
- 初対戦: スコア0（優先）
- 1日前: -100点、2日前: -50点、7日前: -14点
- 貪欲法で最適ペアリング

### 札ルール一覧（PairingSummary） — 札ルールの日付シード決定論化 ＆ LINE単一試合テキスト

**パス**: `/pairings/summary?date=YYYY-MM-DD[&matchNumber=N]`

**目的**:
- 同じ日の札ルール（一の位／十の位／抜き）を、いつ・どの端末で開いても同じにする（保存に頼らず決定論的に再現し、LINE 再配信や別端末でも整合）。
- 全試合だけでなく、選択中の1試合分の組み合わせだけを、全試合と同一フォーマット・同一札ルールでテキスト化できるようにする。

**決定論生成（`cardRules.js`）**:
- 札ルールは `(date, 再生成カウンタ nonce)` の純関数として生成する:
  - `hashSeed(date, nonce)`: 文字列 `date#nonce` を 32bit 符号なし整数へ（FNV-1a 32bit）
  - `mulberry32(seed)`: 32bit シードから決定論PRNG `() => number(0..1)` を生成
  - `pickRandom(arr, n, rng)`: **seeded Fisher-Yates** で n 個選択（`sort(() => rng()-0.5)` は分布が偏り、決定論乱数では比較関数が非推移的になりエンジン依存になるため不採用）
  - `generateCardRules(totalMatches, rng)`: 既存の3試合サイクル（1の位→抜き→十の位）と各サイクル間制約を `rng` で駆動。各試合が消費する乱数の本数・順序は試合番号のみで決まり `totalMatches` に依存しないため、**`totalMatches` を増やしても先頭の試合の札ルールは安定**する
  - 公開ヘルパ `getCardRules(date, totalMatches)` = `generateCardRules(totalMatches, mulberry32(hashSeed(date, loadNonce(date))))`

**nonce（再生成カウンタ）の localStorage 管理**:
- キー名: `karuta-tracker:card-nonce:<YYYY-MM-DD>`（`karuta-tracker:` プレフィックスで認証系キーと衝突回避）
- 値: 整数文字列（既定 0）。`loadNonce`/`saveNonce` で入出力（不正値・利用不可環境では 0 にフォールバック）
- `nonce=0`（既定）はシードが日付のみに依存 → **保存が無くても全端末・過去日・再訪で完全一致**。札ルール配列そのものは保存しない

**画面ロード時の処理順**（`useEffect`、依存は `[date, matchNumberParam]`）:
1. `cleanupOldCardRules()`: 旧形式の札ルール配列キー（`karuta-tracker:card-rules:<date>`）は全削除、`nonce` キーは「今日」以外を削除
2. 対戦データを `pairingAPI.getByDateAndMatchNumber` で全試合分取得。あわせて `byeActivityAPI.getByDate(date)` で抜け番活動を取得し、`activityType = READING`（読み）のものを「試合番号→読手名」マップ（`readersByMatch`）に集約（取得失敗時は読手なしで継続）
3. URL `matchNumber` を検証し、`1..totalMatches` の有効値なら単一試合モードの対象試合番号（`targetMatchNumber`）、数値でない／範囲外なら `null`（全試合モード）
4. `getCardRules(date, totalMatches)` で決定論的に札ルールを生成
5. `generateText(date, matchData, cardRules, readersByMatch, targetMatchNumber)` でテキスト生成（対戦変更は自動反映）

**`generateText` の単一試合モード**:
- `targetMatchNumber` 指定時は、日付見出し（`M月D日`）＋対象 `N試合目` のブロック（札ルール `cardRules[N-1]`・読手・ペア）のみを出力する。出力ブロックは全試合テキストの該当ブロックと完全一致（札ルールが決定論化により一致するため）
- 試合番号の対応: `matchNumber` は1始まり、`cardRules`/`matchData` は0始まり配列。`matchNumber=N → index N-1` を厳守し、表示番号も `N`
- 対象試合のペアが空でもエラーにせず、日付見出し＋`N試合目　札ルール` を表示（URL直接アクセス防御）

**「札を再生成」ボタン**:
- **当日（今日）かつ全試合モード（`targetMatchNumber == null`）のときのみ表示**（`canRegenerate = targetMatchNumber == null && date === getTodayLocalDateStr()`）。単一試合モード・過去日・他日では非表示
  - 過去日・他日を非表示にする理由: 決定論の既定札ルール（全端末一致）を表示して共有時の一貫性を保ち、cleanup の「今日以外の nonce 削除」方針との不整合（過去日で再生成しても次回ロードで既定に戻る）を解消する
- 押下時 `window.confirm('現在の札ルールを上書きして再生成します。よろしいですか？')` を表示し、OK のみ `const nextNonce = loadNonce(date)+1; saveNonce(date, nextNonce)` の後 `getCardRules(date, totalMatches, nextNonce)` で再計算。`saveNonce` は localStorage 例外を握り潰すため、`nextNonce` を明示で渡して保存成否に依存せず画面を再生成する。キャンセル時は何も変化しない
- 結果として、再生成していない既定状態は全端末で不変、再生成した端末（＝当日に再生成した端末）のみ枝分かれする（サーバ保存しない仕様上の許容事項）

**対戦組み合わせ画面（PairingGenerator）の生成導線**:
- 試合番号タブ直下に「全試合 / {N}試合目」セグメントトグル＋生成ボタンを表示（純粋ロジックは `lineTextTarget.js` に extract: `computeLineTextAvailability` / `resolveLineTextTarget` / `buildSummaryUrl`）
- 有効条件: 全試合=全試合の組み合わせが揃っている（`allComplete`）、単一試合=選択中の試合が完成（`matchExistsMap[matchNumber]`）。両方無効ならセクション非表示
- 希望ターゲット（`lineTextTarget` state）が無効になったら有効な方へ自動フォールバック（全試合優先）。タブ切替でラベルの数字と単一試合の対象が追従
- 生成ボタンは対象に応じた URL（全試合 `/pairings/summary?date=...` / 単一試合 `…&matchNumber=N`）で本画面へ遷移

**設計判断**:
- 日付シード決定論（保存不要）を採用: 保存ゼロで全端末・過去日・再訪が一致し実装も最小。サーバ保存（DB変更・本番マイグレーション・API追加）のコストと Issue #518 型の事故リスクを避けつつ「ずれない」を満たす
- 再生成を nonce で残す: 日付決定論のままでは同日で別ルールを出せないため、再生成カウンタで両立
- seeded Fisher-Yates を採用: 均一かつエンジン非依存の決定性を担保
- Part A（決定論化）を Part B（単一試合テキスト）より先に実装: 単一試合と全試合で札ルールが食い違わない一貫性を保存に頼らず根本保証
- 旧形式の札ルール配列キーは読まず掃除のみ: デプロイ当日に旧形式で当日分が保存済みでも、新方式は日付シードから再計算するため当日の表示札ルールが一度だけ変わり得る（運用上の軽微な一回性）

**詳細仕様・要件**: `docs/features/pairing-card-rule-persistence/requirements.md`

### 札分け確認・通知（札組テキストのサーバー生成）

全プレイヤーが「その日の札分け（各試合の出札ルール＝札組番号）」をテキストで確認でき（設定→札分け確認）、希望者は1試合目開始3時間前に LINE で受け取れる（購読制・デフォルト OFF）。LINE はサーバー送信で送信時にクライアントが介在しないため、**札組テキストをバックエンドで一元生成**する（`CardDivisionTextService`）。画面（`GET /api/card-division`）も LINE 送信も同一サービスを使い、JS/Java 二重実装のドリフトを防ぐ。

- **決定論生成の Java 移植**: `cardRules.js` の `hashSeed`（FNV-1a 32bit）→ `mulberry32` → 部分 Fisher-Yates → 3試合サイクルを `CardDivisionTextService` に移植（32bit 符号なし演算を厳密再現）。`cardRules.js` は**変更しない**。移植の一致は `CardDivisionTextServiceTest` の**ゴールデン・クロス言語パリティテスト**（cardRules.js を実行して採取した (date, nonce, totalMatches) フィクスチャと各試合の (種別, digits, removedCard) 一致）で担保する。同日なら わすら・北大の札組は完全一致（団体非依存＝日付シードのまま）。
- **テキスト形式**: `【M/D 会場名】`（月・日は10の位0を省略）＋各行 `N試合目：<札ルール>`。抜き行のみ `番号(決まり字)抜き`（例 `41(こひ)`、`100(もも)`）。決まり字マスタは `util/Kimariji.java`（`kimariji.js` の `KIMARIJI` を補正値込みで複製）。抜き札番号は `parseInt(removedCard)||100`（"00"→100）。対戦ペアは載せない（札組のみ）。
- **会場名・試合数・nonce**: `PracticeSession`（date+org）→ 会場名（`Venue`）・`totalMatches`・`CardRuleNonceService.getNonce(date)` から解決。会場未設定なら `【M/D】`。当日該当団体のセッションが無ければ `hasSession=false`・`text=null`。
- **API（`CardDivisionController`・`@RequireRole` PLAYER+）**:
  - `GET /api/card-division?playerId=&organizationId=&date=`（date 既定＝JST 今日）→ `{ hasSession, date, organizationId, text, subscribed }`。`subscribed` はその (player, org) の `card_division_reminder` 現在値（既定 OFF）。テキスト閲覧は購読状態に依存しない。
  - `PUT /api/card-division/subscription`（body `{ playerId, organizationId, enabled }`）→ `card_division_reminder` のみを per-(player, org) で部分更新。既存行は他種別を保持、行が無ければ他種別 ON の既定で新規作成（`updatePreferences` の全上書きと違い「札分けを ON にしたら他の通知が消える」事故を防ぐ）。
- LINE 送信（スケジューラ・通知種別）は `docs/spec/notifications.md` を参照。

## フロー

### 自動マッチングフロー

```
[ユーザー操作]
1. /pairings へ移動
   ↓
2. 日付・試合番号選択
   ↓
[フロントエンド]
3. 参加者一覧取得・表示（デフォルト全員選択）
   ↓
[ユーザー操作]
4. 参加者選択調整 → 「自動マッチング」ボタンクリック
   ↓
[バックエンド: MatchPairingService.autoMatch()]
5. ロック済みペアリング判定（既存ペアリング + 対応するmatchesの存在チェック）
   ↓
6. ロック済みプレイヤーを参加者リストから除外
   ↓
7. 過去30日の対戦履歴を取得
   ↓
8. ペアごとの最終対戦日マップ作成
   ↓
9. 同日既存対戦を除外
   ↓
10. 参加者シャッフル → 貪欲法でスコア最高ペアを選択
   ↓
11. レスポンス: AutoMatchingResult（ペア一覧 + 待機者リスト + ロック済みペア一覧）
   ↓
[ユーザー操作]
12. 手動調整 → 「組み合わせ確定」ボタンクリック
   ↓
[バックエンド]
13. ロック済みペアリングを保持しつつ、未ロック分を削除 → 新規一括登録
```

## API

### 組み合わせ (`/api/match-pairings`)

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `/date?date=&light=` | ALL | 日付別取得 |
| GET | `/date-and-match?date=&matchNumber=` | ALL | 日付+試合番号で取得 |
| GET | `/exists?date=&matchNumber=` | ALL | 存在確認 |
| GET | `/pair-history?player1Id=&player2Id=&sessionDate=` | ALL | ペアの対戦履歴 |
| GET | `/player/{playerId}` | ALL | 選手起点の最近ペアリング取得（動画倉庫の登録モーダル「選手起点」で結果未入力の試合も選べるようにする用途。閲覧は全選手可のため団体スコープなし） |
| POST | `/` | PLAYER+ | 単一作成（ADMIN/PLAYERは自/所属団体のみ） |
| POST | `/batch?date=&matchNumber=` | PLAYER+ | 一括作成（ADMIN/PLAYERは自/所属団体のみ） |
| POST | `/auto-match` | PLAYER+ | 自動マッチング（ADMIN/PLAYERは自/所属団体のみ） |
| PUT | `/{id}/player?newPlayerId=&side=` | PLAYER+ | 選手差し替え（ADMIN/PLAYERは自/所属団体のみ） |
| DELETE | `/{id}` | ADMIN+ | 単一削除 |
| DELETE | `/{id}/with-result` | PLAYER+ | ペアリング+対応する試合結果を同時削除（リセット）（ADMIN/PLAYERは自/所属団体のみ） |
| DELETE | `/date-and-match?date=&matchNumber=` | PLAYER+ | 日付+試合番号の全削除（ADMIN/PLAYERは自/所属団体のみ） |

### GET `/api/match-pairings/date?date={date}`
**説明**: 日付別組み合わせ取得
**権限**: なし

### GET `/api/match-pairings/player/{playerId}`
指定選手が `player1` または `player2` に含まれる**最近の**ペアリングを返す。動画倉庫の登録モーダル「選手起点」で、結果未入力（`match_pairings` にのみ存在し `matches` にはない）の試合も選択肢に含められるようにするための参照系API。

- 並び順: `sessionDate DESC, matchNumber DESC`（新しい順）
- 件数: 直近30件に制限
- 権限: 全ロール（PLAYER含む。閲覧は全選手可のため団体スコープは適用しない）
- 返すのは**ペアリング（組み合わせ）**であり、結果（`matches`）とは別物。フロントは「日付起点」と同様に `matches` と `pairings` を自然キーで統合・重複排除する
- 選手名は `players` からバッチ解決（N+1回避）。`recentMatches` や試合結果（`hasResult` 等）は付与しない軽量レスポンス

**レスポンス**（`MatchPairingDto` のリスト。日付起点と同じ形で扱える）:
```json
[
  {
    "id": 10,
    "sessionDate": "2026-06-12",
    "matchNumber": 1,
    "player1Id": 1,
    "player1Name": "山田太郎",
    "player2Id": 2,
    "player2Name": "佐藤花子"
  }
]
```
- 該当ペアリングが無い場合は空配列を返す

### POST `/api/match-pairings/auto-match`
**説明**: 自動マッチング
**権限**: SUPER_ADMIN, ADMIN
**リクエスト**: `AutoMatchingRequest`
```json
{
  "sessionDate": "2025-11-20",
  "matchNumber": 1,
  "lockedPairs": [
    {"player1Id": 3, "player2Id": 4}
  ]
}
```
**リクエスト補足**: `lockedPairs`（nullable・後方互換）は「ロック以外を再シャッフル」でクライアントの現在ロック状態を渡すための任意フィールド。
- 省略（`null`）: 従来どおり DB の `hasResult` / `locked` から保持組を導出（新規作成フロー・挙動不変）。
- 指定（空配列 `[]` を含む）: 手動ロックはこの `lockedPairs` を正とし、DB の `locked` は無視する（未保存ロックも保持）。結果入力済み（`hasResult`）は常に DB から保護。各要素は `{player1Id, player2Id}`（順不同）。

**レスポンス**: `AutoMatchingResult`
```json
{
  "pairings": [
    {
      "player1Id": 1,
      "player1Name": "田中太郎",
      "player2Id": 2,
      "player2Name": "佐藤花子",
      "score": -14.28,
      "recentMatches": [
        {"date": "2025-11-13", "daysAgo": 7}
      ]
    }
  ],
  "waitingPlayers": [
    {"id": 5, "name": "山田太郎"}
  ],
  "lockedPairings": [
    {
      "id": 12,
      "player1Id": 3,
      "player1Name": "鈴木一郎",
      "player2Id": 4,
      "player2Name": "高橋次郎",
      "score": 0.0,
      "recentMatches": [],
      "hasResult": true,
      "locked": false
    }
  ]
}
```
**補足**: `lockedPairings` は結果入力済みロック（`hasResult=true`）と手動ロック（`locked=true`）の両方を含む。フロントは各要素の `hasResult` / `locked` でバッジ（「結果入力済」/「🔒 ロック」）を出し分ける（`hasResult:true` 固定にしない）。

### POST `/api/match-pairings/batch?date={date}&matchNumber={matchNumber}`
**説明**: 一括組み合わせ作成。保存時の保護対象は結果入力済み（`hasResult`）のみ（両選手を新規ペアから除外し保持）。手動ロック組はリクエスト要素の `locked` を反映して削除→再作成し、ロック/解除を永続化する。
**権限**: SUPER_ADMIN, ADMIN, PLAYER
**リクエスト**: `MatchPairingBatchRequest`（各 `pairings` 要素は `MatchPairingCreateRequest`。`locked` は手動ロック状態、null は false 扱い）
```json
{
  "pairings": [
    {"player1Id": 1, "player2Id": 2, "locked": true},
    {"player1Id": 3, "player2Id": 4, "locked": false}
  ],
  "waitingPlayerIds": [5]
}
```

### DELETE `/api/match-pairings/{id}/with-result`
**説明**: ペアリングと対応する試合結果を同時削除（リセット）
**権限**: SUPER_ADMIN, ADMIN, PLAYER（`validateScopeByPairingId`。ADMIN/PLAYER は自/所属団体のペアリングのみ、他団体は 403）
**レスポンス**: 削除されたペアリング情報（`MatchPairingDto`、`hasResult=true`、`matchId`付き）

### DELETE `/api/match-pairings/date-and-match?date={date}&matchNumber={matchNumber}`
**説明**: 組み合わせ削除（結果入力済み・手動ロックの組は保持）
**権限**: SUPER_ADMIN, ADMIN, PLAYER（`validateScopeByDate`。ADMIN/PLAYER は自/所属団体のセッションのみ、他団体は 403）

### PATCH `/api/match-pairings/{id}/lock`
**説明**: 指定組を手動ロック（二重ブッキング検証付き）。同一 `(session_date, match_number)`・同一組織スコープ内で対象2選手のいずれかが別の組に含まれる場合は 409 Conflict（`DuplicateResourceException`）
**権限**: SUPER_ADMIN, ADMIN, PLAYER（`validateScopeByPairingId`）
**レスポンス**: 更新後の `MatchPairingDto`（`locked=true`）
**注**: ロックの明示保存化（pairing-lock-explicit-save-and-help）以降、PairingGenerator はロックをローカル状態で扱い `POST /batch` の `locked` で永続化するため、本エンドポイントは**未使用（残置・将来クリーンアップ候補）**。

### PATCH `/api/match-pairings/{id}/unlock`
**説明**: 指定組の手動ロックを解除（`locked=false`。組は残り通常の未ロック組に戻る）
**権限**: SUPER_ADMIN, ADMIN, PLAYER（`validateScopeByPairingId`）
**レスポンス**: 更新後の `MatchPairingDto`（`locked=false`）
**注**: `/lock` と同様、明示保存化以降は**未使用（残置・将来クリーンアップ候補）**。
