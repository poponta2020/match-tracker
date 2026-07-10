# 抽選

> **責務:** 練習参加抽選システム（アルゴリズム・締切・確定・キャンセル補充）の仕様
> **関連画面:** `/lottery/results`（LotteryResults.jsx）、`/lottery/waitlist`（WaitlistStatus.jsx）、`/lottery/offer-response`（OfferResponse.jsx）、`/admin/lottery`（LotteryManagement.jsx）、`/admin/settings`（SystemSettings.jsx）
> **主要実装:** `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java`、`karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryService.java`、`karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryQueryService.java`、`karuta-tracker/src/main/java/com/karuta/matchtracker/service/WaitlistPromotionService.java`、`karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryDeadlineHelper.java`、`karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/LotteryScheduler.java`、`karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/OfferExpiryScheduler.java`、`karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/SameDayConfirmationScheduler.java`、`karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/SameDayVacancyScheduler.java`、`karuta-tracker/src/main/java/com/karuta/matchtracker/entity/LotteryExecution.java`、`karuta-tracker-ui/src/api/lottery.js`、`karuta-tracker-ui/src/pages/lottery/LotteryManagement.jsx`、`karuta-tracker-ui/src/pages/lottery/LotteryResults.jsx`、`karuta-tracker-ui/src/pages/lottery/lotteryResultText.js`

## 機能仕様

練習参加者が定員を超えた場合に抽選を行い、公平に参加者を決定するシステム。
詳細な要件定義は [docs/requirements/lottery-system.md](../requirements/lottery-system.md) を参照。

### 抽選の流れ

1. **締め切り前**: 翌月分の練習に対し、プレイヤーは試合ごとに参加希望を登録（ステータス: `PENDING`）。参加登録画面には、北大かるた会（`code=hokudai`）に所属するユーザーに対して「締め切り: ○月○日（あと○日）（北大）」が表示される（締め切り後・締め切りなしモード時、または北大に所属していない場合は非表示。わすらもち会のみ所属するユーザーは SAME_DAY 運用のため締め切りアナウンスは表示しない）
2. **抽選実行**: 管理者が `/admin/lottery` 画面から手動で抽選を実行する（締切日はシステム設定 `lottery_deadline_days_before` で管理者が調整可能。デフォルト0=前月末日0:00。`-1`=締め切りなしモード）。**自動抽選スケジューラ（`LotteryScheduler`）は実装上存在するが現在は無効化されており、運用は手動実行のみ**
3. **結果確定**: 定員超過の試合では当選（`WON`）・キャンセル待ち（`WAITLISTED`、番号付き）に振り分け
4. **キャンセル→繰り上げ**: 当選者がキャンセル専用ページから理由付きでキャンセルするとキャンセル待ち1番に通知。応答期限内に承諾/辞退。PLAYERロールは過去の練習日のキャンセル不可（ADMIN+はデータ修正目的で可能）。**定員未達（キャンセル待ちなし）の場合は繰り上げ・管理者通知ともに送信しない**
5. **キャンセル待ち辞退**: キャンセル待ち中のプレイヤーはセッション単位でキャンセル待ちを辞退可能（`WAITLISTED`→`WAITLIST_DECLINED`）。辞退時に後続のキャンセル待ち番号は自動繰り上げ。辞退後の復帰も可能（最後尾番号が付与される）
6. **締切後の新規登録**: MONTHLY型の締切後の新規参加登録は、**抽選未実行の窓では `PENDING`（抽選対象）** として取り込む（A-2）。抽選前は `WON` が 0 のため空き判定が常に真となり即 `WON`＋定員超過になる問題を防ぎ、伝助経由○の `PENDING` 取込と挙動を揃えて公平に抽選へ載せる。**抽選実行済み**の場合は従来どおり `(WON + OFFERED) < capacity` かつ既存の `WAITLISTED` がなければ即 `WON`、定員超過または `WAITLISTED` 残存時は `WAITLISTED`（最後尾）。`OFFERED`（応答待ち）も定員カウントに含め、待機中の枠を新規申込が横取りしないようにする。SAME_DAY型は従来どおり先着（即WON/WAITLISTED、抽選なし）
7. **容量拡張時の昇格**: 会場拡張 (`POST /{id}/expand-venue`) や練習編集での `capacity` 増加時、その時点の `WAITLISTED` を `waitlist_number` 昇順に **`OFFERED`（期限付き・要承諾）** へ昇格し、昇格者へオファー通知（アプリ内＋LINE）を送信する（B-1）。新定員 `capacity - (WON + 既存OFFERED)` に収まらない超過分は `WAITLISTED` のまま据え置き。**auto-confirm（応答期限なし）および既存 `OFFERED` の応答期限一律クリアは廃止**し、全 `OFFERED` を「期限付き・要承諾」に統一（正午一括DECLINE・OfferExpiryScheduler と整合）。応答期限が既に過ぎている場合（当日12:00以降等）は即失効オファーを作らないため昇格しない
8. **既存登録解除の可否（「当月扱い／来月扱い」で切り替え）**: 参加登録画面の既存登録（保存済み）チェックボックスは、表示月の状態に応じて挙動を切り替える（判定は `AttendanceRegisterModal` と同じ `resolveAttendanceMode` を共用）。
   - **当月扱いの月**（現在年月、または未来月で抽選確定済みセッションが1つ以上ある月）: 既存登録のチェック外しを一律禁止（disabled＋グレーアウト）。既存登録のキャンセルはキャンセル専用画面（`/practice/cancel`）で理由付きキャンセルとして実施する
   - **来月扱いの月**（未来月で抽選確定済みセッションが0個）: 既存登録のチェックを自由に外せる（API上は未登録に戻す＝理由なしキャンセル）。ただし、月内に抽選確定済みのセッションが個別に存在する場合（混在しうるが、この場合は月全体が当月扱いに昇格するため通常は発生しない）はそのセッションのみステータス表示固定でチェック不可
   - **締切後**（来月扱いの月でも稀に発生）: 既存仕様どおり、既存登録のチェック外しは不可
   - **未登録試合への追加登録**: 当月扱い・来月扱いいずれでも可能（締切後でも可）

### 抽選アルゴリズムの特徴

- **連鎖落選**: 同セッション内で先行試合に落選した人は、後続試合で低優先度扱い。定員超過時は優先的に落選するが、空き枠があれば当選する
- **優先当選**: 同月内の別セッションで落選経験がある人は、次の抽選で当選が保証される
- **管理者指定優先選手**: 管理者が指定した選手は抽選の最優先枠として扱われる。優先順位: **管理者指定優先 > 連続落選救済 > 一般枠**。優先選手同士でも定員超過時は抽選（全員当選とは限らない）。希望を出しているセッションのみ適用。優先選手が落選した場合、キャンセル待ちの最上位に入る
- **一般枠最低保証**: 優先当選者と一般参加者が共存する場合、定員の一定割合（システム設定 `lottery_normal_reserve_percent`、デフォルト30%）を一般枠として確保する。優先当選者だけで定員が埋まることを防ぎ、新規参加者にも機会を保証する
- **キャンセル待ち順番の引き継ぎ**: 連続する試合番号（第N試合→第N+1試合）では、前試合のキャンセル待ち順番を維持する。新規落選者はランダムに末尾へ追加される
- **応答期限**: min(通知から24時間, 練習日前日23:59) の早い方。期限超過後の応答はバックエンドで拒否される
- **短期限オファー注意喚起**: 応答期限まで12時間未満の場合、LINE・アプリ内通知に「※ 応答期限まで残りわずかです。お早めにご回答ください。」を付加
- **タイムゾーン**: 全ての日時判定（期限チェック、当日判定、タイムスタンプ記録）はJST（Asia/Tokyo）で統一。`JstDateTimeUtil` ユーティリティクラスにより、サーバーのデフォルトタイムゾーンに依存しない
- **管理者手動編集時のキャンセル動作**: `editParticipants` で WON→CANCELLED にステータス変更した場合、通常キャンセル経路（`/api/lottery/cancel`）と同じ三分岐ロジックに揃える：
  - 当日でない / 当日12:00前 → 通常繰り上げ + 管理者バッチ通知 + プレイヤー向けオファー統合通知
  - 当日12:00以降 → `SameDayCancelContext` 経由で当日補充フロー（キャンセル発生通知 + 空き募集通知 + 管理者通知）が `afterCommit` 登録される
- **キャンセル待ち辞退/復帰の団体スコープ**: `POST /api/lottery/decline-waitlist` `POST /api/lottery/rejoin-waitlist` は ADMIN ロールに対し対象セッションの `organizationId` 一致を強制（`AdminScopeValidator`）。SUPER_ADMIN は全団体可、PLAYER は自己のみ可

**出欠整合性・反転防止（抽選・伝助連携 整合性改修）:**
- **A-1 試合別参加者編集の限定**: `PUT /{sessionId}/matches/{matchNumber}/participants`（`setMatchParticipants`）は **WON/PENDING のアクティブ行のみを全置換**し、`WAITLISTED`/`OFFERED`/`CANCELLED`/`DECLINED`/`WAITLIST_DECLINED` は温存する。編集モーダルは当選/参加確定者のみを対象とし（初期選択は `playerId` 基準）、保存前に追加/削除人数の確認ダイアログを表示。キャンセル済(×)の復活・待機者の抽選なしWON昇格・伝助の×/△巻き込みを防ぐ
- **管理者による手動繰り上げ（キャンセル待ち→当選）**: `PUT /api/lottery/admin/edit-participants`（`editParticipants`、SUPER_ADMIN / ADMIN。ADMINは自団体のみ・`AdminScopeValidator`）の `statusChanges` で `WAITLISTED`/`OFFERED` → `WON` を指定した場合、当該者の `waitlist_number` をクリアし、残存キュー（`WAITLISTED` + `OFFERED`）を `waitlist_number` 昇順で 1..N に再採番（`WaitlistPromotionService.renumberRemainingWaitlist`。`OFFERED` を含めて再付番し欠番・重複を防ぐ）＋オファー関連フィールドをクリア＋`dirty=true`（○書き戻し）。フロントは試合別参加者編集モーダル（`MatchParticipantsEditModal`）内の「キャンセル待ち」一覧に **繰り上げ** ボタンを表示（**ADMIN / SUPER_ADMIN のみ**）。上記 A-1 の `setMatchParticipants` は待機者を昇格しない制約があるため、意図的な手動繰り上げはこの経路で行う
  - **権限境界（IDOR防止）**: `editParticipants` は `statusChanges`/`waitlistReorders` の各 `participantId` が **リクエストの `sessionId`＋`matchNumber` に属すること**を検証し（`findScopedParticipant`）、属さない場合は 400 で拒否する（Controller のスコープ検証は `request.sessionId` のみのため、別セッション・別団体の participantId を混ぜた越境更新を防ぐ）
  - **定員ガード**: `WAITLISTED`→`WON` の繰り上げは当選総数が増えるため、`WON + OFFERED >= capacity`（他経路と揃えた空き判定）なら 400 で拒否する（`OFFERED`→`WON` は定員に算入済みで総数不変のためチェック不要。capacity 未設定時は無制限）。定員を増やす場合は会場拡張フロー（隣室予約）で `capacity` を増やす。フロント側も満員時は「繰り上げ」ボタンを無効化し「定員満（会場拡張が必要）」を表示
- **A-3 確定書き戻し直前の伝助差分検知**: 抽選確定の一括書き戻し直前に伝助を1回読み、アプリ側○書き戻し予定（WON/OFFERED/PENDING）なのに伝助側が×（不参加）になっている反転リスクを検知。確定/書き戻しはブロックせず（確定DBは維持）、WARNログ＋管理者へLINE通知（`ADMIN_DENSUKE_CONFIRM_DIFF`）＋ `ConfirmLotteryResponse.densukeDiffs` で可視化
- **A-4 名寄せ衝突の検知**: 正規化後に同名となる複数選手（名寄せ衝突）を読取（`playerNameMap`）・書込（`extractAllMemberMappings`）の両側で検知し、当該名は取込・書込ともスキップ（黙って先勝ち/後勝ちで別人に○×を付けない）。読取側は `DENSUKE_NAME_COLLISION` 通知で管理者へ、書込側は書き込みステータス `errors[]` で可視化。根本原因の重複選手は統合で解消する（`docs/features/lottery-densuke-integrity/merge-duplicates/`）
- **B-2 プレビュー↔確定の母集団突合**: プレビュー応答に母集団シグネチャ（対象PENDING参加者ID集合のハッシュ）を含め、確定時に再計算・照合。不一致なら **409** で確定を拒否し再プレビューを促す（5分同期での母集団変化で当落がプレビューと相違するのを防ぐ）。後方互換: シグネチャ未送信なら検証スキップ
- **B-3 伝助行不一致の可視化・row_id防御**: 編集フォームの join-ID 件数とスケジュール件数の不一致を書き込みステータス `errors[]` に記録（無言スキップの解消）。キャッシュ済み `densuke_row_ids` が現フォーム構造と矛盾する場合は当該URLの row_id を破棄して再構築し、別日/別試合への誤書き込みを防ぐ
- **B-4 参加登録の楽観ロック**: 参加状況取得に版情報（`version`＝対象月×プレイヤーの参加行のハッシュ）を付与し、参加登録リクエスト（`expectedVersion`）で版照合。不一致なら **409** を返し再読込を促す（全置換方式のまま、古いタブ/別端末での保存で後入れ○が黙って巻き戻る事故を防ぐ）。後方互換: 版未送信なら検証スキップ

### 抽選関連エンティティ

**LotteryExecution（抽選実行履歴）:**

| フィールド | 型 | 説明 |
|---|---|---|
| `targetYear` / `targetMonth` | int | 対象年月 |
| `sessionId` | Long | 対象セッションID（再抽選時） |
| `executionType` | Enum | `AUTO` / `MANUAL` / `MANUAL_RELOTTERY` |
| `status` | Enum | `SUCCESS` / `FAILED` / `PARTIAL` |
| `executedAt` | LocalDateTime | 実行日時 |
| `details` | Text | 処理詳細 |
| `confirmedAt` | LocalDateTime | 確定日時（NULL = 未確定） |
| `confirmedBy` | Long | 確定者のプレイヤーID |
| `organizationId` | Long | 団体ID |
| `seed` | Long | 抽選シード値（再現性担保） |
| `priorityPlayerIds` | List<Long> | 管理者指定優先選手IDリスト（JSON形式で保存）。確定時に記録 |

**Notification（アプリ内通知）:**

| フィールド | 型 | 説明 |
|---|---|---|
| `playerId` | Long | 通知先プレイヤー |
| `type` | Enum | `LOTTERY_WON`(廃止) / `LOTTERY_ALL_WON` / `LOTTERY_REMAINING_WON` / `LOTTERY_WAITLISTED` / `WAITLIST_OFFER` / `OFFER_EXPIRING` / `OFFER_EXPIRED` |
| `title` / `message` | String | 通知内容 |
| `referenceId` | Long | 参照先ID（参加者レコードID等） |
| `isRead` | Boolean | 既読フラグ |
| `deletedAt` | LocalDateTime | 論理削除日時 |

**PushSubscription（Web Push購読）:**

| フィールド | 型 | 説明 |
|---|---|---|
| `playerId` | Long | プレイヤーID |
| `endpoint` | String | Push APIエンドポイント |
| `p256dhKey` / `authKey` | String | 暗号化キー |

**PushNotificationPreference（Web Push通知設定）:**

| フィールド | 型 | 説明 |
|---|---|---|
| `playerId` | Long | プレイヤーID（UNIQUE） |
| `enabled` | Boolean | Web Push全体のON/OFF |
| `lotteryResult` | Boolean | 抽選結果（LOTTERY_ALL_WON/REMAINING_WON/WAITLISTEDをまとめて制御） |
| `waitlistOffer` | Boolean | キャンセル待ち繰り上げ |
| `offerExpiring` | Boolean | 繰り上げ期限切れ警告 |
| `offerExpired` | Boolean | 繰り上げ期限切れ |
| `channelReclaimWarning` | Boolean | LINEチャネル回収警告 |
| `densukeUnmatched` | Boolean | 伝助未登録者 |

### 抽選関連画面

| パス | 画面 | 説明 |
|---|---|---|
| `/lottery/results` | 抽選結果確認 | 月別の全セッション・全試合の抽選結果一覧。ADMIN/SUPER_ADMIN かつ当該月が抽選確定済の場合、画面下部に「LINE告知用コピーテキスト」エリア（編集可能なtextarea + コピーボタン）が表示される。テキストはキャンセル待ち（`WAITLISTED`）のみを抽選順に整形し、月切替・再フェッチで自動再生成。月内のキャンセル待ちが0名のときはボタン非活性 |
| `/lottery/waitlist` | キャンセル待ち状況 | 自分のキャンセル待ち一覧（番号・ステータス表示） |
| `/lottery/offer-response` | 繰り上げ参加承認 | セッション日付・会場・試合番号・応答期限を表示。期限切れ時はボタン無効化、処理済み時は結果表示 |
| `/notifications` | 通知一覧 | 全通知の時系列表示（未読/既読管理、一括削除、タップで遷移） |

### 「締め切りなし」モード

`lottery_deadline_days_before` を `-1` に設定すると「締め切りなし」モードになる。

| 処理 | 挙動 |
|------|------|
| 参加登録チェックボックス | 常に有効 |
| 自動抽選スケジューラ | 実行しない |
| 手動抽選（管理者） | いつでも実行可能 |
| 参加登録の処理パス | 常に通常登録 |
| 参加登録画面の締め切り表示 | 非表示 |

### システム設定管理画面

管理者（ADMIN / SUPER_ADMIN）がシステム設定をUI上で確認・変更できる画面。パス: `/admin/settings`

**対象団体:**
- ADMIN は自団体の設定のみ確認・変更できる
- SUPER_ADMIN は対象団体を選択して、団体別の設定を確認・変更できる
- 抽選管理画面から遷移する場合は、選択中の団体IDを `organizationId` クエリで引き継ぐ

**設定項目（初期リリース）:**

| 項目 | 設定キー | バリデーション | 説明 |
|------|---------|---------------|------|
| 抽選締め切り日数 | `lottery_deadline_days_before` | -1〜20の整数 | 対象月の初日からN日前。-1=締め切りなし |
| 一般枠の最低保証割合 | `lottery_normal_reserve_percent` | 0〜100の整数 | 抽選時の一般枠確保割合（%） |

**操作フロー:**
- 全設定をまとめて1回の「保存」ボタンで保存
- 保存前に確認ダイアログを表示
- 保存成功時にトースト通知
- 締め切り日のプレビュー表示（「来月の締め切り: ○月○日」）

### スケジューラ

| スケジューラ | タイミング | 処理 |
|---|---|---|
| `LotteryScheduler` | **現在無効化（手動運用のみ）** | 翌月分の自動抽選。`@Scheduled` がコメントアウトされており、運用は管理者が `/admin/lottery` 画面から手動で実行する。将来的に再有効化する場合はコード側の対応が必要 |
| `OfferExpiryScheduler` | 5分間隔 | 応答期限切れのOFFERED → DECLINED遷移、次のキャンセル待ちへ通知 |
| `SameDayConfirmationScheduler` | 毎日12:00 JST | `WaitlistPromotionService.expireOfferedForSameDayConfirmation()`に委譲しOFFERED→DECLINED一括変更（dirty=true設定）+ 空き枠発生時は当日補充フロー自動トリガー + WON参加者にメンバーリストFlex Message送信 |

### Densuke同期との整合性

伝助インポートはフェーズ別に動作する:
- **フェーズ1（抽選未実行）**: ○の人を取り込み（SAME_DAY型は空き有無で WON/WAITLISTED、MONTHLY型は PENDING）。not-○の dirty=false レコードは削除（ただしCANCELLED/DECLINED/WAITLIST_DECLINEDなどキャンセル履歴は保持）。MONTHLY型では締切日時にかかわらず本フェーズで動作する
  - **△（キャンセル待ち希望）の扱い**: **SAME_DAY型（例: わすらもち会）のみ**、締切前でもフェーズ3と同一の △ 処理を行う（`processPhase3Sankaku` を再利用）。未登録→末尾WAITLISTED / WON→キャンセル待ちへ降格 / WAITLISTED・OFFERED→変更なし / キャンセル済み→WAITLISTED復活。これにより当日12:00前でも伝助の △ が忠実に反映される。△に付け替えた人の既存レコードは not-○ 削除ループの対象から除外する。**MONTHLY型は抽選前で定員が未確定のため △ を従来どおり無視**（北大かるた会の抽選前挙動を維持）
- **LOCKED（抽選実行済み・未確定）**: 伝助インポートを停止する。確定時の一括書き戻し（`writeAllForLotteryConfirmation`）が PENDING を ○ として伝助に書き出すため、この窓で新規○を Phase1 として PENDING 登録すると、抽選を経ていないプレイヤーが当選者として混入してしまう。書き戻し（dirty=true → 伝助）は通常通り実行される
- **フェーズ3（抽選確定後）**: ○/△/×の全パターンを処理。dirty=trueのレコードは一切触らない
  - **3-A6（WAITLISTED + 伝助○）**: 当日12:00 JST以降かつ**空き枠が自分の待ち順位まで（自分を含む、`waitlist_number` 昇順）の人数以上ある**場合、WAITLISTED→WONに昇格（dirty=false、伝助は既に○のため書き戻し不要）。空き枠は `WON + OFFERED < 定員`（OFFERED算入）から算出。自分より前の待ち順位の人の枠は必ず確保された状態を維持するため待ち行列を飛ばした昇格は起きないが、**待ち行列の厳密な先頭でなくても、空きが複数人分あれば2番目以降も正しく昇格する**（#1008: 空きが十分あるのに先頭以外が永久に昇格できず伝助○が毎回△へ強制上書きされる不具合の修正）。後続のキャンセル待ち番号を自動繰り上げ。12:00前・または空き枠が自分の待ち順位までをカバーしない場合は従来通りdirty=trueにして△で書き戻す（抽選バイパス防止・B-5で他経路と空き判定を統一）

> 旧「フェーズ2（締切後・抽選確定前のスキップ）」は廃止された（Issue #616）。締切日時ではなく**抽選実行と確定**の状態のみでフェーズが切り替わるため、締切なしモード（`lottery_deadline_days_before = -1`）でも同じ判定になる。

⚠要確認: 伝助フェーズ処理・書き戻しの詳細な連携仕様は（docs/spec/densuke.md 参照）とも一部重複する可能性がある。本節は旧仕様書の抽選側（Densuke同期との整合性）の記述をそのまま採用。

### 抽選結果の確定

抽選実行後、管理者が結果を確認し「確定」操作を行うまでは LOCKED が維持され、伝助インポートは停止される。確定時に伝助への一括書き戻し（WON→○、WAITLISTED→△、それ以外→×）がトリガーされ、フェーズ3に移行する。

- **API**: `POST /api/lottery/confirm`（SUPER_ADMIN / ADMIN権限。ADMINは自団体のみ）
- **DB**: `lottery_executions.confirmed_at`（確定日時）, `confirmed_by`（確定者ID）, `organization_id`（団体ID）
- 確定状態は団体単位で管理される。団体Aが確定済みでも団体Bは未確定の状態がありうる
- 確定前は再抽選が可能。確定後はフェーズ3に移行し、伝助との双方向同期が開始される

### 抽選管理画面

管理者が手動で月次抽選を実行・確認・確定できる画面（`/admin/lottery`）。

**画面構成:**
- ヘッダー: 画面タイトル「抽選管理」+ 「システム設定」リンクボタン
- 年月セレクター（デフォルト: 翌月）
- 「抽選実行」ボタン

**状態遷移:**
- 未実行（idle）: 操作部のみ表示
- プレビュー中（preview）: セッション別・試合別の当選/キャンセル待ち一覧 + 「確定」ボタン + LINE告知用コピー領域（警告色オレンジ・「※ プレビュー（未確定）」ラベル付き）。DB未保存状態
- 確定済み（confirmed）: 確定完了メッセージ + 「全員に通知送信」ボタン + 「キャンセル待ちのみ通知送信」ボタン + LINE告知用コピー領域（青系、ラベルなし）
- 該当月が未終了かつ確定済みの抽選が存在する場合は、再表示時にも通知ボタンを表示し続ける（カレンダー画面に独立した通知導線は持たない）

**LINE告知用コピー領域（preview / confirmed フェーズで表示）:**
- 抽選落ち（キャンセル待ち）のみを整形した文面を持つ編集可能 textarea
- コピーボタンで `navigator.clipboard.writeText` を実行（キャンセル待ち0件のときはボタンを無効化）
- プレビュー段階は誤配信防止のためボタンをオレンジ色 + 「※ プレビュー（未確定）」ラベルで視覚的に警告。確定後は青色に切り替えて `/lottery/results` 側のコピー領域とスタイルを統一する
- 整形ロジックは `lotteryResultText.js` の `buildCopyText` / `hasAnyWaitlisted` を共有（`/lottery/results` と同一実装）

**API:**
- `POST /api/lottery/preview`: 抽選プレビュー（DB保存なし）。締め切り前チェック・確定済みチェックあり
- `POST /api/lottery/confirm`: 抽選確定（DB保存 + 伝助書き戻し）
- `GET /api/lottery/is-confirmed`: 指定年月・団体の抽選が確定済みかを返す（通知ボタンの再表示判定に使用）
- `GET /api/lottery/notify-status`: 指定年月・団体の通知が既送信かを返す（重複送信防止の事前確認）
- `POST /api/lottery/notify-results`: 全員（当選者＋キャンセル待ち）にアプリ内通知 + LINE通知を送信（団体スコープ適用）
- `POST /api/lottery/notify-waitlisted`: キャンセル待ちのみにアプリ内通知 + LINE通知を送信（団体スコープ適用）

**通知送信時の重複防止:**
- 「全員に通知送信」「キャンセル待ちのみ通知送信」押下時に `notify-status` を呼び、既に通知が送信済みであれば「既にN件の通知を送信済みです。再送信しますか？」と件数付きで確認ダイアログを表示する

**バリデーション:**
- 締め切り前は抽選実行不可（「締め切りなし」モードの場合はいつでも実行可能）
- 同一月に既に確定済みの抽選がある場合はエラー
- ADMINは自団体のセッションのみ対象

**SettingsPageの変更:**
- グリッドから「システム設定」を削除し、「抽選管理」を追加（Dicesアイコン、ADMIN以上に表示）

### 共通レイアウトの変更

`Layout.jsx` にヘッダーバーを追加:
- ページタイトル表示
- プロフィールアイコン → `/profile` に遷移

ナビゲーションメニューに「抽選結果」「キャンセル待ち」リンクを追加。

## 画面

### 抽選結果
**パス**: `/lottery/results`

**全ユーザー共通の表示内容**:
- 月ナビゲーション
- セッション別の抽選結果
  - 当選者リスト（WON）
  - キャンセル待ちリスト（WAITLISTED、番号順）
  - 各参加者のステータス表示（WAITLIST_DECLINED バッジを含む）
- 自分のキャンセル待ちセッションに対する辞退/復帰ボタン

**ADMIN / SUPER_ADMIN 向けの追加表示**:
- 抽選確定済の月にのみ表示する LINE告知用テキスト領域
  - 抽選落ち（キャンセル待ち）のみを整形した文面を初期値として持つ編集可能 textarea
  - コピーボタンで `navigator.clipboard.writeText` を実行（キャンセル待ち0件の月はボタンを無効化）
  - 整形ロジックは `lotteryResultText.js` に切り出し（`buildCopyText` / `hasAnyWaitlisted`）
- SUPER_ADMIN かつ複数団体所属時は団体スコープ切替セレクタを表示
  - 選択された団体IDを `/api/lottery/results` および `/api/lottery/is-confirmed` の両方に渡し、コピー領域と確定状態判定を同じ団体スコープで揃える
  - 団体一覧取得前および切替直後の stale レスポンスを捨てるためのリクエストIDガードを `fetchResults` に持たせる

**備考**: 抽選確定・参加者の手動編集等の主要な管理操作は `/admin/lottery` 側に集約している。本画面で管理者向けに提供するのは、確定済み月の抽選落ちを LINE 告知用に整形・コピーする機能のみで、確定は行わない。なお同等のコピー領域は `/admin/lottery`（抽選管理画面）のプレビュー段階／確定済み段階にも表示され、プレビュー段階のみ警告色で誤配信を抑止する。**セッション単位の再抽選**は専用UIを提供せず、バックエンドAPI `POST /api/lottery/re-execute/{sessionId}`（ADMIN+）のみが稼働している（旧仕様で `/practice` の練習日ポップアップに存在した「再抽選」ボタンはUIから撤去済み）。

## フロー

### 抽選フロー

```
[管理者操作]
1. 抽選結果画面で「抽選実行」ボタンクリック
   ↓
[バックエンド: LotteryService.executeLottery()]
2. 対象月のセッション一覧取得
   ↓
3. セッションごとに、試合番号ごとの参加希望者を取得
   ↓
4. 定員超過判定（capacity vs 参加希望者数）
   ↓
5. 定員内 → 全員WON、定員超過 → 3層優先抽選
   - **層1（管理者指定優先）**: 管理者が指定した選手を優先枠で抽選。定員を超えた場合は優先選手同士で抽選
   - **層2（連続落選救済）**: 同月内の別セッションで落選経験のある選手を救済枠で抽選（normalReservePercent考慮）
   - **層3（一般枠）**: 残り定員に対して通常抽選
   - 当選者: status = WON
   - 落選者: status = WAITLISTED + waitlist_number付与（優先落選 → 救済落選 → 一般落選の順で番号付与）
   ↓
6. 通知作成（LOTTERY_WON / LOTTERY_WAITLISTED）
   ↓
7. Web Push通知送信（購読者に対して）
   ↓
8. LotteryExecution履歴保存
   ↓
[フロントエンド]
9. 抽選結果表示（当選者・キャンセル待ちリスト）

[キャンセル時]
1. 当選者が「キャンセル」→ status = CANCELLED
   ↓
2. キャンセル待ちが存在する場合のみ以下を実行（定員未達時は通知なし）:
   キャンセル待ち1番を繰り上げ → status = OFFERED, offerDeadline設定
   ↓
3. 繰り上げ通知（WAITLIST_OFFER）+ Web Push + LINE Flex Message（参加/辞退ボタン付き）
   ↓
4. 繰り上げ者が応答（Webアプリ or LINEボタン、どちらからでも可）:
   - 承諾 → status = WON
   - 辞退/期限切れ → status = DECLINED → 次の待ち番号を繰り上げ
```

### 当日キャンセル補充フロー

```
[12:00確定フェーズ — SameDayConfirmationScheduler → WaitlistPromotionService.expireOfferedForSameDayConfirmation()]
1. 当日セッションのOFFERED参加者を一括でDECLINEDに変更（dirty=true設定で伝助同期による再活性化を防止）
   ↓
2. 空き枠が発生した場合、当日キャンセル補充フロー（先着ボタン方式）を自動トリガー
   - 非WON参加者に空き募集Flex Message送信（オレンジヘッダー、「参加する」ボタン付き）
   ↓
3. WON参加者にメンバーリストFlex Message（青ヘッダー）をLINE送信
   - 通知トグル: sameDayConfirmation

[当日キャンセル→補充フェーズ — WaitlistPromotionService.cancelParticipation() / dispatchSameDayCancelNotifications()]
1. 12:00以降にWON参加者がキャンセル（管理者手動編集 `editParticipants` 経由を含む）
   ↓
2. LotteryDeadlineHelper.isAfterSameDayNoon() で12:00以降判定
   ↓
3. cancelParticipationInternal が SameDayCancelContext 付き AdminWaitlistNotificationData を返却
   ↓
4. 呼び出し元（LotteryController / DensukeImportService / LotteryService.editParticipants）で
   (sessionId, playerId) 単位に集約し、afterCommit で handleSameDayCancelAndRecruitBatch を実行
   - editParticipants は `cancelParticipationSuppressed` に委譲し、通常キャンセル経路と同じ三分岐ロジックに揃える（WON→CANCELLED 直書き経路は廃止）
   ↓
5. キャンセル発生通知（統合版）: 「〇〇さんが今日の1、3試合目をキャンセルしました」
   - 同一セッション×同一プレイヤーの複数試合は1通にまとまる（通知トグル: sameDayCancel）
   - 異なるプレイヤーはプレイヤー単位で別通知
   ↓
6. 空き募集通知（統合版）: sendConsolidatedSameDayVacancyNotification
   - セッション内の複数空き試合を1通のFlex Messageに集約（通知トグル: sameDayVacancy）
   - postback: action=same_day_join&sessionId={id}&matchNumber={num}
   ↓
7. 管理者通知: sendBatchedAdminWaitlistNotifications
   - セッション単位で1通にまとまる

【トランザクション境界の契約】
WaitlistPromotionService の `*Suppressed` 系メソッド（`cancelParticipationSuppressed` /
`respondToOfferDeclineSuppressed` / `expireOfferSuppressed` / `demoteToWaitlistSuppressed`）は
`@Transactional`（伝播 `REQUIRED`）で宣言されているが、個別コミットは保証しない：
- `DensukeImportService` / `LotteryService.editParticipants` などインポート/編集TX配下から
  呼ぶ場合、上流TXに参加して整合性を保つ（失敗時はキャンセル含めロールバック）
- ループ内で1件ずつコミットしたい呼び出し元（`LotteryController#cancelParticipation` など）は
  呼び出し元側に `@Transactional` を付けてはならない。付与するとループ全件が単一TXに化け、
  途中の例外で全件ロールバックされる
- `LotteryControllerCancelTest` にメソッド単位 `@Transactional` 不在のセンチネルテストあり

[先着参加フェーズ — LINEボタン or アプリ]
1. LINEボタンpostback → LineWebhookController（same_day_joinアクション）
   または アプリ → LotteryController POST /api/lottery/same-day-join
   ↓
2. WaitlistPromotionService.handleSameDayJoin(sessionId, matchNumber, playerId)
   ↓
3. 空き枠チェック → 先着1名がWON（2人目以降: 409 Conflict）
   ↓
4. 参加者本人に参加確定通知送信
   ↓
5. WON参加者全員に枠状況通知送信
   - 残り枠あり: オレンジヘッダー + 「参加する」ボタン
   - 枠埋まり: グレーヘッダー + ボタンなし

[アプリ経由参加登録通知 — PracticeParticipantService]
1. 12:00以降にアプリ経由でWON登録された場合
   ↓
2. WONメンバーに通知送信

[伝助同期経由参加登録通知 — DensukeImportService]
1. 12:00以降に伝助上で○に変更され、伝助同期によりWONとして登録・昇格された場合
   ↓
2. DensukeImportService.notifyVacancyUpdateIfNeeded() が枠状況通知を送信
   - 対象: 新規WON登録 / WAITLISTED→WON昇格 / 再有効化（CANCELLED等→WON）
   - 内容: sendSameDayVacancyUpdateNotification()（残り枠あり or 枠埋まり）
```

**変更クラス一覧:**

| クラス | 変更内容 |
|--------|---------|
| `SameDayConfirmationScheduler`（新規） | scheduler/ — 毎日12:00 JSTに`WaitlistPromotionService.expireOfferedForSameDayConfirmation()`へ委譲 + メンバーリスト送信 |
| `SameDayVacancyScheduler`（新規） | scheduler/ — 毎日0:00 JSTに当日セッションの空き枠検出＋`SAME_DAY_VACANCY`/`ADMIN_SAME_DAY_CANCEL`自動送信 |
| `WaitlistPromotionService` | `expireOfferedForSameDayConfirmation()`追加（dirty=true設定＋空き枠補充トリガー）、cancelParticipationの12:00以降分岐追加、handleSameDayJoinメソッド追加。**管理者通知バッチ化**: `cancelParticipationSuppressed()`・`demoteToWaitlistSuppressed()`で通知データ（`AdminWaitlistNotificationData`）を返し、`sendBatchedAdminWaitlistNotifications()`でセッション×トリガー×プレイヤー単位でまとめ送信 |
| `LineNotificationService` | 確定通知/キャンセル通知/空き募集通知/参加通知/枠状況通知メソッド追加、`getAdminRecipientsForSession()`ヘルパー追加（該当団体ADMIN + 全SUPER_ADMIN）、`sendAdminVacancyNotification()`追加、ADMIN向け送信をキャンセル/参加/確定/キャンセル待ち通知に追加、`isNotificationEnabled()`で全ADMIN_系通知を`organizationId=0`判定に統一、SAME_DAY_VACANCY送信先を団体全メンバーに拡大。**管理者通知Flex改修**: `sendAdminWaitlistNotification()`が`List<Integer> matchNumbers`+試合別キャンセル待ち列を受け取り、トリガー種別に応じたヘッダー/イベント文言、複数試合まとめ表示、キャンセル待ち列の全試合同一判定に対応 |
| `LotteryDeadlineHelper` | isAfterSameDayNoon()追加、calculateOfferDeadline当日対応 |
| `PracticeParticipantService` | 12:00以降参加時の通知送信追加 |
| `DensukeImportService` | 伝助同期でWON登録時の枠状況通知送信追加（notifyVacancyUpdateIfNeeded） |
| `LineWebhookController` | same_day_joinポストバックハンドリング追加 |
| `LotteryController` | POST /api/lottery/same-day-joinエンドポイント追加 |
| `PlayerRepository` | `findByRoleAndAdminOrganizationIdAndActive()`メソッド追加 |
| `PlayerEdit.jsx` | ADMIN管理団体ドロップダウン追加（SUPER_ADMIN専用） |
| `NotificationSettings.jsx` | `adminSameDayCancel`トグル追加 |

**Flex Messageデザイン:**

| メッセージ種別 | ヘッダー色 | ボタン | 送信先 |
|---------------|-----------|--------|--------|
| 参加者確定（メンバーリスト） | 青（#1E88E5） | なし | WON参加者 + 該当団体ADMIN + 全SUPER_ADMIN（管理者通知） |
| 空き募集 | オレンジ（#FF6B00） | 「参加する」（postback） | 団体全メンバー（該当試合WON除く） |
| 残り枠あり（枠状況通知） | オレンジ（#FF6B00） | 「参加する」（postback） | 団体全メンバー（該当試合WON除く） |
| 枠埋まり（枠状況通知） | グレー（#9E9E9E） | なし | 団体全メンバー（該当試合WON除く） |

**DB変更:**

| 対象 | 変更内容 |
|------|---------|
| `LineNotificationType` enum | `SAME_DAY_CONFIRMATION`, `SAME_DAY_CANCEL`, `ADMIN_SAME_DAY_CANCEL`, `SAME_DAY_VACANCY`, `ADMIN_SAME_DAY_CONFIRMATION` の5値追加 |
| `line_notification_preferences` テーブル | `same_day_confirmation`, `same_day_cancel`, `same_day_vacancy`, `admin_same_day_confirmation`, `admin_same_day_cancel` の5カラム追加 |

## API

（docs/spec/lottery-api.md 参照）
