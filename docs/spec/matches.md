# 対戦結果

> **責務:** 対戦記録の登録・閲覧・取り札記録・抜け番活動の仕様
> **関連画面:** `/matches`（`MatchList.jsx`）／`/matches/new`・`/matches/:id/edit`（`MatchForm.jsx`）／`/matches/:id`（`MatchDetail.jsx`）／`/matches/bulk-input/:sessionId`（`BulkResultInput.jsx`）／`/matches/results/:sessionId?`（`MatchResultsView.jsx`）
> **主要実装:**
> - バックエンド: `controller/MatchController.java`, `controller/ByeActivityController.java`, `controller/MatchCardRecordController.java`, `controller/CardRuleNonceController.java`, `service/MatchService.java`, `service/ByeActivityService.java`, `service/MatchCardRecordService.java`, `service/CardRuleNonceService.java`, `repository/MatchRepository.java`, `repository/MatchPersonalNoteRepository.java`, `repository/ByeActivityRepository.java`, `repository/MatchCardPlacementRepository.java`, `repository/MatchOtetsukiDetailRepository.java`, `repository/CardRuleNonceRepository.java`, `entity/Match.java`, `entity/MatchPersonalNote.java`, `entity/ByeActivity.java`, `entity/MatchCardPlacement.java`, `entity/MatchOtetsukiDetail.java`, `entity/CardRuleNonce.java`, `dto/MatchDto.java`, `dto/MatchCreateRequest.java`, `dto/MatchSimpleCreateRequest.java`, `dto/MatchStatisticsDto.java`, `dto/ByeActivityDto.java`, `dto/ByeActivityCreateRequest.java`, `dto/ByeActivityBatchItemRequest.java`, `dto/ByeActivityUpdateRequest.java`, `dto/MatchCardRecordDto.java`, `dto/CardRuleNonceDto.java`（すべて `karuta-tracker/src/main/java/com/karuta/matchtracker/` 配下）
> - フロントエンド: `karuta-tracker-ui/src/pages/matches/MatchForm.jsx`, `MatchResultsView.jsx`, `BulkResultInput.jsx`, `MatchList.jsx`, `byePlayersLogic.js`, `swipeGesture.js`, `tabScroll.js`, `defaultMatchNumber.js`、`karuta-tracker-ui/src/components/MatchCarousel.jsx`、`karuta-tracker-ui/src/utils/rank.js`、`karuta-tracker-ui/src/pages/pairings/cardRules.js`、`karuta-tracker-ui/src/data/kimariji.js`

## 機能仕様

### 対戦結果管理

#### 対戦記録（Match）

1対1の対戦結果を記録するエンティティ。

| フィールド | 型 | 必須 | 説明 |
|---|---|---|---|
| `matchDate` | LocalDate | Yes | 対戦日 |
| `matchNumber` | Integer | Yes | その日の第N試合目 |
| `player1Id` | Long | Yes | 選手1 ID（常に player1Id < player2Id） |
| `player2Id` | Long | Yes | 選手2 ID |
| `winnerId` | Long | Yes | 勝者ID |
| `scoreDifference` | Integer | No | 枚数差（0〜25）。指導試合では null |
| `isLesson` | Boolean | Yes | 指導試合フラグ（true=指導試合。勝者=指導した側、敗者=指導された側。デフォルト false） |
| `opponentName` | String(100) | No | 未登録の対戦相手名（簡易入力用） |
| `createdBy` | Long | Yes | 登録者ID |
| `updatedBy` | Long | Yes | 更新者ID |

**ビジネスルール:**
- `player1Id < player2Id` はエンティティの `@PrePersist` / `@PreUpdate` で自動保証（必要に応じてスワップ）
- これにより同じペアの対戦を一意に検索可能
- **指導試合（`isLesson = true`）**: 上級者が初心者に教えながら行う試合。勝ち側＝指導した側、負け側＝指導された側。`scoreDifference` は持たない（null）。登録済みプレイヤー同士のみ対象（簡易入力フローは対象外）。勝敗（`winnerId`）は通常どおり保持し、勝数・負数・勝率・試合数の統計にも通常試合と同様に計上される（指導フラグは表示の差し替えと指導回数/被指導回数の集計にのみ用いる）

#### 個人メモ・お手付き記録（MatchPersonalNote）

各プレイヤーが自分の試合に対して記録するプライベートデータ。1試合につき各プレイヤー1レコード。

| フィールド | 型 | 必須 | 説明 |
|---|---|---|---|
| `matchId` | Long | Yes | 対象試合ID（FK → matches） |
| `playerId` | Long | Yes | 記録者ID（FK → players） |
| `notes` | Text | No | 個人メモ（感想・反省点など） |
| `otetsukiCount` | Integer | No | お手付き回数（0〜20。nullは未入力、0は0回） |

**ビジネスルール:**
- UNIQUE制約: `(matchId, playerId)` — 1試合1プレイヤーにつき1レコード
- お手付き回数は 0〜20 の整数。null（未入力）と 0（0回）を区別する
- 個人メモ・お手付きは入力者のプライベートデータであり、他プレイヤーには表示しない
- 試合結果の保存と同時に同一トランザクションで保存される

#### 試合結果入力方式

**本人入力（個人向け / `MatchForm`）:**
- ヘッダーは「日付(曜)＋会場名」を表示（会場名は `PracticeSession.venueId → Venue`。会場未設定時は日付のみ）。試合番号タブは `N試合目` 表記。
- **対戦相手の選択モデル:**
  - 既定のプルダウン母集団 = **その練習セッションのアクティブ参加者（WON/PENDING・自分を除く）＋「抜け番」**。`PracticeSessionDto.participants` はステータス未保持の全参加者（CANCELLED/DECLINED 等も含む）ため、`matchParticipants` のステータスで WON/PENDING に絞る（選手名は UNIQUE のため名前で突合。`matchParticipants` 欠落時は全参加者にフォールバック）。
  - 当日未参加の選手は「未参加から検索」ボタン（🔍）で**全選手 − 当日参加者 − 自分**を名前で簡易インクリメンタル検索して選択。
  - ペアリングがある試合は従来どおり相手を自動セット。確定後は名前（▽）タップでプルダウンに戻して変更可能。
  - 確定相手の級を名前脇に `(A)` 形式で控えめ表示（`Player.kyuRank` の頭文字を `utils/rank.kyuRankShortLabel` で整形。未設定は非表示）。
- 勝敗（選択時のみ文字着色のトグル）、枚数差（0〜25・登録相手のみ末尾「指導」）、お手付き回数（任意・「不明」/0〜20）、個人メモ（任意）を入力。
- 抜け番はプルダウンの「抜け番」選択に一本化（旧「抜け番として記録する」ボタンは廃止）。
- **編集モード（`/matches/:id/edit`）では対戦相手は読み取り専用**（更新APIは対戦者IDを変更しないため、相手変更UIは表示しない。相手を変えたい場合は削除して再登録）。

**詳細入力（一括入力）:**
- 全ロール（PLAYER+）が練習日単位で全ペアの結果を一括入力
- 組み合わせ済みのペアが表示され、勝者と枚数差を入力するだけ
- 枚数差ピッカーの末尾で「指導」を選択でき、選ぶとその試合は指導試合（`isLesson = true`・枚数差なし）として記録される（両者黒・中央「指導」表示）
- 右上の「対戦変更」ボタンで組み合わせ作成画面（`/pairings`）へ遷移して対戦内容を変更できる（未保存の入力結果がある場合は `window.confirm` で確認ダイアログを表示）
- お手付き・個人メモの入力は含まない（個人が別途入力）
- 対戦組み合わせが未作成の場合は「対戦組み合わせが作成されていません」メッセージを表示。組み合わせ作成画面への遷移ボタン（`/pairings?date=YYYY-MM-DD`）も全ロールに表示

**ペアリング自動生成（本人入力時）:**
- `MatchForm` から試合結果を登録する際、対応する `match_pairings` レコードが存在しない場合は自動生成する
- 対戦相手が未登録（`player2Id = 0`）の場合は自動生成しない
- プレイヤーIDは正規化して保存（`player1Id < player2Id`）

**未参加相手の自動参加登録（試合保存の副作用）:**
- 詳細登録（`MatchService.createMatch`）で、対戦に関与した登録済み選手が当日セッションの当該試合に未参加なら、サーバ側で `WON` の参加レコードを自動作成する（「未参加から検索」で選んだ相手を参加者一覧・結果閲覧・一括入力へ反映するため）。
- 冪等（既にアクティブ参加なら no-op）・試合保存と同一トランザクション。直接の参加登録API（`POST /participations`）の「PLAYER は自分のみ」ガードは温存し、本処理はサーバ主導の副作用として登録する。
- セッション特定: 同日セッションが1件ならそれ、複数（同日複数団体）なら会場一致または対戦選手の参加実績で一意化、決まらなければ安全側でスキップ。
- 伝助同期は即時トリガーせず、参加レコードを `dirty=true` で保存して次回の通常同期に委ねる（影響検証は `docs/features/match-form-redesign/requirements.md` §5.1）。

#### 試合結果閲覧

- ヘッダーは「日付(曜)＋会場名」を表示（`MatchResultsView` / `BulkResultInput` 共通。会場名は各セッションDTOの `venueName`、未設定時は日付のみ）。既存の日付前後移動・カレンダー・「対戦変更」・タブ操作は維持。
- 日付セレクターで過去の練習日を選択（直近30日分の練習日を表示）
- 試合番号ごとにタブ切り替え
- 各ペアの結果（勝者・枚数差）を表示
- 自分の試合には個人メモの有無とお手付き回数を表示（他プレイヤーのものは非表示）
- 抜け番の選手と活動内容をバッジ形式で表示（活動記録がある場合）
- 結果編集・一括入力画面へのリンク

#### 抜け番活動記録

奇数参加者の練習で対戦相手がいない選手（抜け番）の活動を試合番号ごとに記録する機能。

**抜け番の判定ルール:**
- 抜け番 = 「その試合番号のアクティブ参加者」のうち、組み合わせ（MatchPairing）に含まれない選手
- アクティブ参加者の対象ステータスは「組み合わせ対象となる参加者の範囲」（docs/spec/matching.md 参照）と完全に同一ルール:
  - **抽選あり運用**（`pairingIncludesPending === false`）: `status === 'WON'` のみ。抽選前の `PENDING` は抜け番候補にも含めない
  - **抽選なし運用**（`pairingIncludesPending === true`）: `status === 'WON' || status === 'PENDING'`。抽選を運用しないため `PENDING` のまま組み合わせ対象 = 抜け番候補
- `PracticeSessionDto.matchParticipants` には `DECLINED` / `WAITLIST_DECLINED` 以外の全ステータス（`WON`/`PENDING`/`WAITLISTED`/`OFFERED`/`CANCELLED`）が含まれるため、**抜け番候補を算出する際は `PracticeSessionDto.pairingIncludesPending` を見て上記の通りフィルタすること**（true なら `WON||PENDING`、false なら `WON` のみ）
- キャンセル済み（`CANCELLED`）・キャンセル待ち（`WAITLISTED`/`OFFERED`）の選手は抜け番として扱わない
- 対象画面: `PairingGenerator` / `BulkResultInput` / `MatchResultsView` いずれも同じルールで算出する（フロントロジックは `byePlayersLogic.js`）

**活動種別:**

| Enum値 | 表示名 | 説明 |
|--------|--------|------|
| READING | 読み | 読み手として試合進行を担当 |
| SOLO_PICK | 一人取り | 一人で札を取る練習 |
| OBSERVING | 見学 | 他の選手の試合を見学 |
| ASSIST_OBSERVING | 見学対応 | 見学者への説明・案内を担当 |
| OTHER | その他 | 上記以外の活動（自由テキストで補足） |
| ABSENT | 休み | 登録済みだが当日無断欠席した選手 |

**入力方法:**
- **本人入力（MatchForm）:** ペアリング作成済みで抜け番判定された試合番号タブを選択すると自動で活動種別選択UIが表示される。ペアリング未作成の場合は対戦相手プルダウンで「抜け番」を選択して切り替える（旧「抜け番として記録する」ボタンは廃止し、プルダウン一本化）
- **管理者一括入力（BulkResultInput）:** ペアリング一覧の下に抜け番セクションが表示され、活動種別をドロップダウンで選択
- **組み合わせ作成時（PairingGenerator）:** 待機選手に活動種別を選択して、ペアリング保存時に一括登録

**ビジネスルール:**
- 活動の入力は任意（未入力でもエラーにしない）
- 「その他」選択時のみ自由テキスト入力欄が表示される
- 同一試合・同一選手で1レコードのみ（部分ユニーク制約: `deleted_at IS NULL` のレコード間で適用）
- 削除は論理削除（`deleted_at` にタイムスタンプを設定）
- 「休み」（ABSENT）を選択した場合:
  - 自由テキストは不要（OTHERのみ）
  - その日の全ByeActivityがABSENTの場合、`PracticeParticipant`（`matchNumber=null`）を削除し参加率にカウントしない
  - 一部のみABSENTの場合は`PracticeParticipant`は維持（来ている試合があるなら参加扱い）
  - 「休み」→他アクティビティへ変更時は`PracticeParticipant`を復元
  - 無断欠席回数は`activity_type=ABSENT`のレコード数を試合単位で集計（内部データとしてのみ保持、UI非表示）

#### 対戦一覧画面（`/matches`）の行内タップ動線

対戦一覧画面（`MatchList.jsx`）の各対戦行には2つの独立したタップ対象があり、行全体タップによる詳細画面遷移は廃止されている。

**タップ対象と遷移先:**

| タップ対象 | 遷移先 | 表示条件 |
|---|---|---|
| 対戦相手名（テキスト部分） | `/matches?playerId=<opponentId>` | 常時（ゲスト選手の場合は無効化） |
| メモアイコン（📝） | `/matches/<matchId>` （自分閲覧時）<br>`/matches/<matchId>?playerId=<targetPlayerId>` （メンター閲覧時） | 自分閲覧時 ＋ メンターがメンティー対戦一覧を見ている時のみ表示 |
| その他の行領域（日付・勝敗・会場・手N） | 反応なし（タップ無効） | — |

**対戦相手名のスタイル:**
- リンク化時はテーマ色 `#4a6b5a`（下線なし）
- ゲスト選手（`player1Id` または `player2Id` が `null` / `0`）の場合は通常テキスト色 `#374151` で表示し、タップしても遷移しない

**行内の要素配置（左→右）:**

各行は CSS Grid による 6 列のテーブル風レイアウトで描画し、全行で各列の左端 x 座標が揃うようにする。

| 列 | 要素 | 書式 | 幅ポリシー / スタイル |
|---|---|---|---|
| 1 | 日付 | `M/D`（例: `5/23`） | 固定幅。`text-xs text-[#9ca3af]` |
| 2 | 対戦相手名 | 選手名 | 固定幅（`6.125rem` = `text-sm` の全角 7 文字分 + `truncate`）。リンク化時はテーマ色 `#4a6b5a` |
| 3 | 勝敗 | `〇N` / `×N` / `△N`（N は枚数差）。指導試合は「指導」 | 固定幅。`text-sm font-bold`、勝ち=緑 / 負け=赤 / 引き分け=グレー。指導試合は色付け・マークなしのグレーで「指導」表示 |
| 4 | 会場・試合番号 | `会場名 N試合目`（例: `あかなら・すずらん 3試合目`） | 可変幅（`minmax(0,1fr)` + `truncate`、残り幅を全て受け取る）。`text-xs text-[#9ca3af]` |
| 5 | メモアイコン | 📝（`StickyNote`） | 固定幅。非表示条件の行でも `invisible` プレースホルダで列幅を確保 |
| 6 | お手付き回数 | `手N`（例: `手2`） | 固定幅。`null` の行でも `invisible` プレースホルダで列幅を確保 |

- 会場情報が取得できない場合は `N試合目` のみ表示
- `N` は `Match.matchNumber`（その日の第N試合）をそのまま表示
- 試合詳細画面（`/matches/:id`）では、試合日・試合番号・会場名は統合カード中段にテキスト1行（半角スペース2個区切り）で表示する
- `opponentId` の計算: `match.player1Id === targetPlayerId ? match.player2Id : match.player1Id`
- 列幅は固定 rem 値（実装値: `grid-cols-[2rem_6.125rem_2.5rem_minmax(0,1fr)_1.5rem_2rem]`）。`auto` を使うと行ごとに track 幅が変わり列揃え要件を満たせないため避ける

**メモアイコンの表示ルール:**

| 条件 | 表示 |
|---|---|
| 詳細導線を表示するべき閲覧ケース ＋ メモあり | 濃色アイコン（`text-gray-600`） |
| 詳細導線を表示するべき閲覧ケース ＋ メモなし | 薄色アイコン（`text-gray-300`） |
| 詳細導線を表示しない閲覧ケース | 非表示 |
| メンター関係 API がフェッチ中（他選手閲覧時） | 非表示 |

**詳細導線の表示条件:**
- 自分閲覧時: `targetPlayerId === currentPlayer.id`
- メンター閲覧時: `targetPlayerId !== currentPlayer.id` かつ `mentorRelationshipAPI.getMyMentees()` のレスポンスに当該 `targetPlayerId` が `status === 'ACTIVE'` で含まれる
- メンター関係 API 取得失敗時はエラーログを出すが画面表示は継続し、詳細導線は非表示（安全側）

**仕様変更点（過去仕様との差分）:**
- **行全体タップ → 対戦詳細遷移** は廃止
- 一般選手が他人（非メンティー）の対戦一覧を見る場合、対戦詳細へは遷移できない（プライバシー・利用シナリオ整理の観点）
- 対戦相手名タップと詳細ボタンの2つのタップ対象を明確に分離することで誤タップを防ぐ

#### 対戦一覧への読み・一人取りの表示

対戦一覧画面（`MatchList.jsx`）には、試合（`matches`）に加えて、その選手が抜け番で
**読み（`READING`）・一人取り（`SOLO_PICK`）** を行った回を同じリストに差し込み表示する
（見学・見学対応・その他・休みは対象外）。

**データ取得:**
- 既存の `GET /api/bye-activities/player/{playerId}`（`type` 指定なし）で全抜け番を取得し、フロントで `READING` / `SOLO_PICK` のみに絞る（試合一覧の取得と同じ `Promise.all` 内で同時取得）
- 取得失敗時は読み・一人取り表示なしでフォールバック（試合履歴は従来どおり表示、コンソールエラーのみ）

**行の表示:**
- 形式は「`会場名 N試合目 活動名`」（例: `クラ館 1試合目 読み`）。活動アイコン（読み=`BookOpen` / 一人取り=`User`）付き
- 会場名は抜け番データに含まれないため、**その選手の同日の試合（`matches`）の会場名を借用**する。同日に試合が1件もない日（読み・一人取りのみの日）は会場名を省略し「`N試合目 活動名`」と表示する
- 日付列の左端を試合行と揃え、タップ無効（表示のみ）。対戦相手・勝敗・動画・メモ・お手付きは持たない

**並び順:**
- 試合行と読み・一人取り行をマージし、**日付降順 → 同日内は試合番号降順**で並べて該当試合番号の位置に差し込む（試合の取得順 `matchDate DESC, matchNumber DESC` と一致）

**フィルタとの関係:**
- 期間（年/月）フィルタ: 読み・一人取り行・回数表示の両方に常に連動。年/月セレクトの選択肢は試合日と抜け番日の両方から生成し、試合がなく読み・一人取りのみの年月も選択して表示できる（抜け番のみの月を選んでも試合月へ戻さない）
- 結果（勝ち/負け）・対戦相手名検索・級/性別/利き手フィルタ: いずれか有効時は読み・一人取り行を**非表示**（対戦相手前提のフィルタであり、相手のいない読み・一人取りは絞り込み対象外）

**回数表示:**
- 統計エリア（級別統計の下）に、対象期間内の「`読み n回 ・ 一人取り m回`」を活動別に併記（期間フィルタ連動）
- 回数が0の活動は非表示（両方0なら回数表示自体を出さない）。勝敗統計（総計・級別）には含めない
- 同じく統計エリア（総合統計の下）に「`指導 n回 ・ 被指導 m回`」を併記（値が1以上のときのみ表示・期間/属性フィルタ連動）。指導回数＝指導試合で勝ち（指導した側）だった試合数、被指導回数＝指導試合で負け（指導された側）だった試合数。級別統計には含めない
- 自分・他選手のどちらの対戦一覧でも表示する

#### 試合番号のスワイプ移動

「その日の対戦結果一覧」（`MatchResultsView`）・「結果の一括入力」（`BulkResultInput`）・「個人の結果入力」（`MatchForm`）の3画面で、画面上部の試合番号タブを**左右スワイプ**でも切り替えられる。タブUIは存続し、スワイプは追加の操作手段として提供する。

**共通仕様:**
- スワイプ対象は試合番号の切替のみ（日付は対象外）。左スワイプ（指を右→左）＝次の試合（+1）、右スワイプ（指を左→右）＝前の試合（−1）
- **スワイプ有効範囲**: 各画面の**共通ヘッダー（日付ナビ・試合番号タブを含む固定ナビバー）と共通フッター（ボトムナビ）を除いたコンテンツ全域**でスワイプを受け付ける。カルーセル本体やフォームの外側・パネル周囲の余白を触っても試合を切り替えられる。実装上はルート要素に `swipeAreaRef` でリスナーを張り、固定ヘッダーに `data-swipe-ignore` を付けて除外する（ヘッダー内の試合番号タブは横スクロールするため競合回避）
- 端の挙動: 最初の試合で「前へ」、最後の試合で「次へ」はスワイプしても何も起きない（端で止まる。ループ・日付移動はしない）
- 横移動量が約10pxを超え、かつ横移動 > 縦移動のときだけ横スワイプとして扱う（縦スクロール・タップと明確に区別し、ボタン/セレクト操作や誤発動を防ぐ）
- 確定閾値はコンテナ幅の約25%、または素早いフリック
- 対象はタッチ操作（スマホ/タブレット）。PCのマウス操作は対象外（従来どおりタブで切替）
- 試合数（`totalMatches`）が1以下のときはスワイプ無効
- **操作ヒント表示**: スワイプで試合を切り替えられることを、コンテンツ上部に控えめな案内テキスト「‹ スワイプで試合を切替 ›」で常時表示する（うるさくならないよう `text-xs`・淡色・中央寄せ）。スワイプが有効な条件（2試合以上。`MatchForm` は新規入力時かつタブ2件以上）のときだけ表示し、1試合のみ・編集モードでは出さない。色は各画面の既存トーンに合わせる（結果一覧/個人入力は `#9ca3af`、一括入力は既存ヒントと揃え `#9b8a7e`）
- スワイプで試合が切り替わったら上部タブのアクティブ表示を連動させ、アクティブタブが画面内に見えるよう自動スクロールする
- スワイプによる移動はフロントエンドの表示状態のみを変更し、サーバーへの保存・送信は行わない

**画面別の方式:**
- **結果一覧・一括入力（指追従カルーセル `MatchCarousel`）:** 試合ごとの内容を指の動きに追従して左右スライドさせ、隣の試合がチラ見えする。離した時点で移動量が確定閾値を超えていれば隣の試合へスナップ確定、未満なら元へ戻る。一括入力は入力結果を全試合分保持しているためスワイプで移動しても入力済み内容は消えない。日付ナビ/カレンダー/試合番号タブは共通ヘッダー（`data-swipe-ignore`）に含まれるためスワイプ対象外。下部固定の保存バー（一括入力）・FAB（結果一覧）は固定表示のまま据え置くが、スワイプ有効範囲（コンテンツ全域）には含まれる。
- **個人入力（スライドイン＋警告 `MatchForm`）:** 指追従はせず、指を離したときに移動量を判定し、確定したら新しい試合の内容が約0.2秒のトランジションでスライドインする。入力途中（未保存の変更がある状態）でスワイプまたはタブタップにより試合番号を変えようとした場合は「入力中の内容は破棄されます。移動しますか？」の確認ダイアログを表示し、OKで切替（＋スワイプ起点ならスライドイン）・キャンセルで据え置きとする（未保存変更がなければ確認なしで即移動）。スワイプ・タブタップの両経路を共通ガード（`requestMatchNumberChange`）に通す。試合番号タブが機能する新規入力フローでのみ有効（編集モードは対象外）。

**実装:** ジェスチャ判定は純粋関数 `swipeGesture.js`（横スワイプ判定・確定方向判定・端クランプ）に切り出して単体テスト可能にしている。指追従カルーセルは共通コンポーネント `MatchCarousel.jsx`、タブ自動スクロールは `tabScroll.js`。

#### 初期表示試合番号のデフォルト（時刻・入力状況ベース）

「その日の対戦結果一覧」（`MatchResultsView`）・「結果の一括入力」（`BulkResultInput`）の2画面で、画面を開いたときの**初期表示試合番号**を、現在時刻と入力状況に応じて自動で決める。従来は両画面とも常に1試合目固定（`useState(1)`）だったが、練習の進行に合わせて「今おそらく見たい／入力したい試合番号」を初期選択する。**初回データ取得時に1回だけ適用**し、以降ユーザーがタブタップ／スワイプで切り替えた後の挙動は一切変えない。

**共通：時刻ベースのデフォルト（適用条件を満たす場合のみ発動）**
- 適用条件（両方成立）: ①表示対象が**当日**（端末ローカル日付 === 練習の `sessionDate`）、②その練習の会場に**試合番号ごとの開始・終了時刻（`venueSchedules`）が定義済み**。
- 判定: 猶予 `GRACE_MINUTES = 15`（固定値）。試合番号を昇順に走査し、`now < (N試合目の終了時刻 + 15分)` を満たす**最小の試合番号 N** をデフォルトとする。どの境界にも該当しない（最終試合の「終了+15分」を超過）場合は**1試合目に戻す**。`endTime` 未定義の試合番号はスキップ。
- 例（1試合目 17:05–18:15 / 2試合目 18:15–19:30）: 〜18:30未満 → 1試合目、18:30〜19:45未満 → 2試合目、19:45以降 → 1試合目。試合切替後も**15分間は前の試合番号がデフォルトのまま**（結果入力の猶予）。
- 適用条件を満たさない場合（過去日・未来日、会場スケジュール未定義など）: 時刻ベースは発動せず**1試合目**。

**一括入力画面（`BulkResultInput`）の上位制約：入力済みに応じたデフォルト**
- 時刻ベースより**優先**する。結果が全入力済みの試合番号のうち最大を `n` とし、`n` が存在すればデフォルト = `min(n + 1, totalMatches)`。
  - 1試合目が未入力でも2試合目が全入力済みなら3試合目をデフォルトにする（`n−1` の入力状況は問わない）。全試合入力済みなら最終試合。
- 「全入力済み」= その試合番号に紐づく**全ペアリング**に対応する**試合結果（`Match`）が存在**すること（ペアリング0件の試合番号は対象外。選手IDは min/max 正規化で突合。既存 `isMatchCompleted` と同一判定）。
- 入力済みの試合番号が1件もなければ時刻ベースにフォールバック。

**一覧画面（`MatchResultsView`）の優先順位**
1. **URLクエリ `matchNumber` 指定時**（一括入力画面からの保存後遷移など）→ その試合番号を**最優先**で初期表示（時刻ベースより優先）。`1〜totalMatches` の範囲外の値は無視。
2. それ以外は時刻ベース（当日かつスケジュールあり）。
3. いずれにも該当しなければ1試合目。
- 入力状況による上位制約は一覧画面には設けない。

**保存後遷移の番号引き継ぎ**
- 一括入力画面で保存すると `/matches/results/:sessionId?date=<sessionDate>&matchNumber=<currentMatchNumber>` へ遷移し、**保存元セッションの日付**と入力していた試合番号を URL クエリで引き継ぐ。一覧画面は `sessionId` ではなく日付（`dateParam || 当日`）でセッションを解決するため、`date` を付与しないと過去日・未来日のセッションを保存しても当日の一覧が開いてしまう。`date` 引き継ぎにより保存元の日付の一覧がその試合番号（最優先表示）で開き、「たった今入力していた試合番号」がそのまま見える。

**実装:** 決定ロジックは純粋関数 `defaultMatchNumber.js`（`timeBasedDefaultMatchNumber` / `getCompletedMatchNumbers` / `defaultForResultsView` / `defaultForBulkInput`）に切り出し、2画面から呼ぶ。現在時刻は端末ローカル時刻。**バックエンド・DB・APIの変更はなし**（必要データ＝`venueSchedules`・ペアリング・試合結果はいずれも取得済み）。

### 取り札記録（試合結果入力の詳細記録・任意）

競技かるたの取り札位置と、お手付きの内容を各プレイヤーが私的に記録する機能。`MatchForm`（`/matches/new`・`/matches/:id/edit`）に「取り札・お手付きを記録」の**折りたたみブロック（任意・初期は閉じ）**として追加する。既存の「お手付き回数」入力とは併存。詳細＝`docs/features/取り札記録/`（requirements.md / design-spec.md / kimariji-master.md）。

- **出札50枚の導出**: 対戦組み合わせの「札ルール」（`(日付, nonce)` から決定論生成）を桁条件で展開して50枚を得る（`cardRules.js` の `expandRule`/`getMatchCards`）。`nonce`（再生成カウンタ）は端末間で出札を一致させるため **DB共有**（新テーブル `card_rule_nonce`）。従来 localStorage 保存だった nonce を DB 化し、`PairingSummary` の「札を再生成」も DB 更新に変更（同日内固定は維持）。
- **盤面**: 敵陣（画面上・**180°回転**：上段が手前/下段が奥、敵陣右＝画面左）／自陣（画面下・自分視点）を畳表現で描画。各マス（敵自 × 左右 × 上中下段＝12エリア）を**「取った(左・緑)｜取られた(右・赤)」の2分割**にし、決まり字の**縦書きチップ**を配置。不明プールは陣の間に置き、残数の母数＝`50 − 枚数差`（指導試合・枚数差不明は50）。
- **お手付き詳細**: お手付き回数分の枠を出し、種類（ひっかけ / 暗記間違え / 聞き間違い / その他）別に項目を出し分ける（ひっかけ＝上段位置4択、暗記＝方向2択、聞き間違い＝読札・触札を100枚から選択、その他＝自由記述）。
- **決まり字マスター**: 100枚（最大4文字・共札は「共通字・区別字」記法。例 わた・や、あさぼあ）を `karuta-tracker-ui/src/data/kimariji.js` に定数化。
- **私的データ**: 取り札配置・お手付き詳細は**記録者本人のみ**読み書き（他プレイヤー非公開）。保存は試合保存後に `PUT /api/matches/{matchId}/card-record`（全置換）、編集時は `GET` で復元。
- **新テーブル**: `card_rule_nonce` / `match_card_placements` / `match_otetsuki_details`（`database/add_torifuda_record.sql`）。
- **新API**: `GET|PUT /api/card-rule-nonce`（nonce）、`GET|PUT /api/matches/{matchId}/card-record`（本人の取り札記録）。

## フロー

### 試合記録登録フロー（簡易登録）

```
[ユーザー操作]
1. /matches/new へ移動（ホーム画面から遷移時はlocation.stateで自動入力）
   ↓
2. フォーム入力（試合日、試合番号、対戦相手名、結果、札差、コメント）
   ↓
3. 「登録」ボタンクリック
   ↓
[バックエンド: MatchService.createMatchSimple()]
4. 重複チェック → Match エンティティ構築 → 保存
   ↓
5. 選手名付与（enrichMatchesWithPlayerNames） → レスポンス: 201 Created
   ↓
[フロントエンド]
6. 成功メッセージ表示 → /matches へリダイレクト
```

### 取り札記録フロー

`MatchForm` で「取り札・お手付きを記録」（任意・折りたたみ）を展開すると、対象試合 `(日付, 試合番号)` の出札50枚を札ルールから導出し（`nonce` は `GET /api/card-rule-nonce` で DB 共有値を取得 → `cardRules.getMatchCards`）、決まり字の縦書きチップとして不明プールに並べる。プレイヤーは不明の札をタップ → 盤面のマス（敵自 × 左右 × 上中下段の「取った/取られた」）をタップで配置。お手付き回数分の詳細（ひっかけ/暗記間違え/聞き間違い/その他）も入力する。試合を作成/更新（`/matches` または `/matches/detailed`）した後、確定した matchId に対して `PUT /api/matches/{matchId}/card-record` で本人の配置＋お手付き詳細を**全置換**保存する。編集時は `GET .../card-record` で復元。

- **権威ある札組**: `nonce` を `card_rule_nonce`（DB）で共有。`PairingSummary` の「札を再生成」も DB の nonce を更新し、記録画面と出札50枚を一致させる（従来の端末ローカル localStorage から移行）。
- **私的データ**: `match_card_placements` / `match_otetsuki_details` は記録者本人（`X-User-Id`）のみ読み書き。既存の「お手付き回数」入力と併存。
- 決まり字マスターは `karuta-tracker-ui/src/data/kimariji.js`（100枚・最大4文字）。詳細＝`docs/features/取り札記録/`。

## API

### 試合記録

`/api/matches`

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `/?date=` | ALL | 日付別取得 |
| GET | `/exists?date=` | ALL | 指定日に対戦存在確認 |
| GET | `/{id}` | ALL | ID指定で取得 |
| GET | `/player/{id}/date/{date}/match/{num}` | ALL | 選手・日付・試合番号で取得 |
| GET | `/player/{id}` | ALL | 選手の対戦履歴（フィルタ付き） |
| GET | `/player/{id}/period?startDate=&endDate=` | ALL | 期間指定取得 |
| GET | `/player/{id}/period/count?startDate=&endDate=` | ALL | 期間内件数（軽量） |
| GET | `/between?player1Id=&player2Id=` | ALL | 2選手間の対戦履歴 |
| GET | `/player/{id}/statistics` | ALL | 選手統計（勝率等） |
| GET | `/player/{id}/statistics-by-rank` | ALL | 級別統計（総合に指導回数 `lessonGivenCount` / 被指導回数 `lessonReceivedCount` を含む） |
| POST | `/` | ALL | 簡易登録 |
| POST | `/detailed` | ALL | 詳細登録（`isLesson` 対応。指導時は枚数差 null） |
| PUT | `/{id}` | ALL | 簡易更新 |
| PUT | `/{id}/detailed` | ALL | 詳細更新（`isLesson` クエリパラメータ対応・`scoreDifference` 任意） |
| DELETE | `/{id}` | ALL | 削除 |

#### POST /api/matches
**説明**: 簡易試合登録（対戦相手名）
**権限**: なし
**リクエスト**: `MatchSimpleCreateRequest`
```json
{
  "matchDate": "2025-11-17",
  "matchNumber": 1,
  "playerId": 1,
  "opponentName": "佐藤花子",
  "result": "勝ち",
  "scoreDifference": 5,
  "personalNotes": "右下段が甘かった",
  "otetsukiCount": 3
}
```

#### POST /api/matches/detailed
**説明**: 詳細試合登録（両選手ID）
**権限**: なし
**リクエスト**: `MatchCreateRequest`
```json
{
  "matchDate": "2025-11-17",
  "matchNumber": 1,
  "player1Id": 1,
  "player2Id": 2,
  "winnerId": 1,
  "scoreDifference": 5,
  "personalNotes": "右下段が甘かった",
  "otetsukiCount": 3
}
```

#### GET /api/matches?date={date}
**説明**: 日付別試合一覧
**権限**: なし
**レスポンス**: `List<MatchDto>`（リクエストユーザーの個人メモ・お手付きを `myPersonalNotes` / `myOtetsukiCount` として含む）

#### GET /api/matches/{id}?playerId={viewedPlayerId}
**説明**: 対戦詳細を取得
**権限**:
- `playerId` 指定なし: 認証ユーザーであればアクセス可
- `playerId` 指定あり かつ `playerId !== currentUserId`: `currentUserId` が `playerId` の ACTIVE メンターであること（非メンターは 403 Forbidden）

**レスポンス**: `MatchDto`
- `playerId` 指定あり時は、指定された選手視点で勝敗・対戦相手名が算出される（メンターがメンティーの試合を見るときに「メンティー視点で負けた」と正しく表示するため）
- メンター閲覧時はメンティーの個人メモ（`menteePersonalNotes` / `menteeOtetsukiCount`）も含まれる

#### GET /api/matches/player/{playerId}
**説明**: 選手の試合履歴
**権限**: なし

#### GET /api/matches/player/{playerId}/statistics
**説明**: 選手統計
**権限**: なし
**レスポンス**: `MatchStatisticsDto`
```json
{
  "playerId": 1,
  "playerName": "田中太郎",
  "totalMatches": 100,
  "wins": 65,
  "winRate": 0.65
}
```

### 抜け番活動

`/api/bye-activities`

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `/?date=&matchNumber=` | ALL | 日付別取得（matchNumber指定時はその試合のみ） |
| GET | `/player/{playerId}?type=` | ALL | 選手別活動履歴（type指定でフィルタ） |
| POST | `/` | ALL | 作成（本人入力） |
| POST | `/batch?date=&matchNumber=` | ADMIN+ | 一括作成（既存レコード削除後に再作成） |
| PUT | `/{id}` | ALL | 更新 |
| DELETE | `/{id}` | ADMIN+ | 削除 |

#### GET /api/bye-activities?date={date}&matchNumber={matchNumber}
**説明**: 指定日の抜け番活動を取得（matchNumber指定時はその試合のみ）
**権限**: なし
**レスポンス**: `List<ByeActivityDto>`

#### POST /api/bye-activities
**説明**: 抜け番活動を作成（本人入力）
**権限**: なし
**リクエスト**: `ByeActivityCreateRequest`
```json
{
  "sessionDate": "2026-03-24",
  "matchNumber": 1,
  "playerId": 5,
  "activityType": "READING",
  "freeText": null
}
```

#### POST /api/bye-activities/batch?date={date}&matchNumber={matchNumber}
**説明**: 抜け番活動を一括作成（既存レコード削除後に再作成）
**権限**: ADMIN+
**リクエスト**: `List<ByeActivityBatchItemRequest>`
```json
[
  { "playerId": 5, "activityType": "READING", "freeText": null },
  { "playerId": 8, "activityType": "OTHER", "freeText": "審判練習" }
]
```

#### PUT /api/bye-activities/{id}
**説明**: 抜け番活動を更新
**権限**: なし
**リクエスト**: `ByeActivityUpdateRequest`
```json
{ "activityType": "SOLO_PICK", "freeText": null }
```

#### GET /api/bye-activities/player/{playerId}?type={activityType}
**説明**: 選手別の活動履歴（集計用）
**権限**: なし

#### DELETE /api/bye-activities/{id}
**説明**: 抜け番活動を削除
**権限**: ADMIN+

#### 対戦履歴一覧（MatchList）での読み・一人取り表示（フロント設計）

対戦履歴一覧画面 `MatchList.jsx` は、`GET /api/matches/player/{id}`（試合）と `GET /api/bye-activities/player/{id}`（抜け番）を併用し、選手が**読み（READING）・一人取り（SOLO_PICK）** を行った回を試合行と同じリストに統合表示する（他の活動種別は対象外）。

- **取得**: 抜け番は `type` 指定なしで全件取得し、フロントで READING / SOLO_PICK のみに絞る。試合一覧（`matches`）・統計と同じ `Promise.all` 内で同時取得し、失敗時は空配列でフォールバック（試合は表示）。
- **マージ/並び**: 試合行と抜け番行を統合し `matchDate DESC, matchNumber DESC` で並べ、該当試合番号の位置に差し込む。
- **会場名**: `bye_activities` は会場を持たないため、同日の試合（`matches`）の `venueName` を借用する。同日に試合がない日は会場名を省略し「N試合目 活動名」と表示する。
- **期間フィルタ候補**: 年/月セレクトの候補（`availableYears` / `availableMonths`）は試合の `matchDate` と抜け番の `sessionDate` の**両方**から生成する（試合がなく抜け番のみの年月にも期間フィルタで到達できるようにするため）。選択中月の補正も試合＋抜け番の合算月で判定し、抜け番のみの月を選んでも試合月へ戻さない。
- **フィルタ連動**: 期間（年/月）フィルタは抜け番行・回数表示の両方に常に連動。結果（勝ち/負け）・対戦相手名検索・級/性別/利き手フィルタが有効なときは抜け番行を非表示にする（対戦相手前提のフィルタのため）。
- **回数表示**: 統計エリアに期間内の「読み n回 ・ 一人取り m回」を活動別に併記（0回は非表示、勝敗統計には不算入）。
