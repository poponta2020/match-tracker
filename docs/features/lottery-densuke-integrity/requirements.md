---
status: completed
completed_sections: [改修内容, 技術設計, 影響範囲]
next_section: null
audit_source: 会話内 /audit-feature レポート（抽選機能・伝助連携の整合性監査）
selected_items: [A-1, A-2, A-3, A-4, B-1, B-2, B-3, B-4, B-5, D, C]
spec_decisions:
  after_deadline_registration: PENDING（抽選対象化）
  duplicate_merge_scope: 統合SQLも含める
  capacity_expansion_offer: 要承諾に統一
  concurrent_edit: 楽観ロック
---

# 抽選機能・伝助連携 整合性改修 要件定義書

## 1. 改修概要

### 1.1 対象機能
競技かるた対戦記録アプリ（Match Tracker）の**抽選（Lottery）機能**と**伝助（densuke.biz）双方向連携**。参加者の出欠意図（○＝参加 / △＝キャンセル待ち希望 / ×＝不参加）が、アプリ⇄伝助の読み書き・抽選・繰り上げの各経路で**意図に反して丸/×に反転しない**ことを保証する。

### 1.2 改修の背景
`/audit-feature` の整合性監査で、参加者の意図と異なるステータス（丸/×）が確定・書き戻される経路が **確定4件（うち重大2件）＋条件付き5件** 検出された。加えてテスト網羅・仕様周知のギャップが判明した。デグレードと利用者トラブルを防ぐため、検出項目を体系的に改修する。

### 1.3 改修スコープ（監査レポートの全11項目）
| ID | 優先 | 分類 | 概要 |
|----|------|------|------|
| A-1 | 高 | バグ | 試合別参加者編集の保存で×/待機者が意図せずWON化 |
| A-2 | 高 | バグ | 締切後〜抽選実行前の登録が抽選バイパスで即WON＋定員超過 |
| A-3 | 高 | バグ | 抽選確定の一括書き戻しがLOCKED窓中の伝助変更(×)を○で上書き |
| A-4 | 高 | バグ | 正規化名キー衝突で別人に丸/×が付く（＋未統合重複4名の統合） |
| B-1 | 中 | バグ/仕様 | 当日12:00一括DECLINEが容量拡張確定OFFEREDも×化 |
| B-2 | 中 | バグ | プレビュー↔確定間の母集団変化で確定結果が相違 |
| B-3 | 中 | バグ | 伝助行の手動変更でrow_idズレ書込／件数不一致の無言スキップ |
| B-4 | 中低 | バグ | 月全置換登録で別端末/伝助の後入れ○が巻き戻る |
| B-5 | 低中 | バグ | Phase3-A6当日昇格の空き判定がOFFERED枠未算入 |
| D | 低 | テスト | 回帰テスト追加 |
| C | 低 | 改善 | 仕様周知（ドキュメント・画面文言） |

### 1.4 確定した仕様判断（ユーザー確認済み）
- **A-2**: 締切後〜抽選実行前の新規登録は **PENDING（抽選対象）** とする。
- **A-4**: 衝突検知コードに加え、**未統合重複4名（川瀬/高橋/山野/むらやま）の統合SQL作成・本番適用**もスコープに含める。
- **B-1**: 容量拡張で昇格するOFFEREDも **「要承諾」に統一**（応答期限付与＋オファー通知、auto-confirm廃止）。
- **B-4**: 参加登録に **楽観ロック**（版照合、競合時409で再読込を促す）を導入。

---

## 2. 改修内容（項目別）

> 記法: 各項目に「現状の問題」「修正方針」「あるべき姿」を記す。ファイル参照は差分の起点であり、全文再掲はしない。

### A-1【重大】試合別参加者編集の×→○反転
- **現状の問題**: `PracticeSessionService.enrichDtoWithMatchDetails` が `matchParticipants` DTO に CANCELLED/WAITLISTED/OFFERED を含める（DECLINED系のみ除外）。編集モーダルはステータスを見ず全員を選択済みで初期化し、保存すると `setMatchParticipants` が当該試合の全アクティブ行をCANCELLEDにした上で選択リスト全員をWONで再登録する。結果、キャンセル済み(×)の復活・待機者の抽選なしWON昇格・選択外し者のCANCELLED(×書き戻し)が、確認ダイアログなしで発生。名前文字列でIDを引くため同姓同名/改名で静かに脱落する。
- **修正方針**:
  1. 編集モーダルの初期選択・対象を **WON/PENDING（＝`isActive()`）のみ** に限定。WAITLISTED/OFFERED/CANCELLED/DECLINED/WAITLIST_DECLINED は編集対象外とし、モーダル上は「この編集は当選/参加確定者のみを対象とします」と明示。
  2. `setMatchParticipants` を **WON/PENDINGのアクティブ行のみ全置換**する実装に変更（待機・キャンセル系の行は温存し、伝助の×/△を巻き込まない）。
  3. 保存前に**確認ダイアログ**（対象試合・追加/削除人数の要約）を表示。
  4. モーダルの選択初期化を **名前一致ではなくplayerId** ベースに変更（DTOにplayerIdを含める）。
- **あるべき姿**: 管理者が1人追加する目的で保存しても、既存のキャンセル/待機状態は保持され、伝助の×/△は変化しない。同姓同名・改名でも取りこぼしなし。

### A-2【重大】締切後〜抽選実行前の即WON＋定員超過
- **現状の問題**: `registerAfterDeadline` は空きがあれば即WON。抽選実行前はWONが0のため全員即WONになり、その後の `processMatch` は既存WONを定員から差し引かずPENDINGだけで定員を埋めるため、合計WONが定員超過する。伝助経由(○→PENDING)とアプリ経由(即WON)で公平性も食い違う。
- **修正方針**（確定: PENDING化）:
  1. MONTHLY団体で **抽選未実行の窓** に該当する新規登録は **PENDING** とする（`registerAfterDeadline` に「抽選未実行なら PENDING、実行済みなら従来の即WON/WAITLISTED」の分岐を追加）。「抽選未実行」の判定は `lotteryExecutionRepository.existsByTargetYearAndTargetMonthAndStatus(...SUCCESS)` を使用（`registerAfterDeadline` は既にこの変数 `lotteryExecuted` を取得済み）。
  2. **防御的措置**: `processMatch` の定員判定で、既存の WON/OFFERED を定員から差し引いてから PENDING を抽選する（管理者手動追加・自動参加登録等で抽選前にWONが存在するケースの超過を防ぐ）。差し引き後の残枠で従来の3層抽選を実施。
- **あるべき姿**: 締切後〜抽選前の登録者は抽選対象として公平に扱われ、定員超過が発生しない。SAME_DAY団体は従来どおり先着（抽選なし）を維持。

### A-3【高】確定一括書き戻しがLOCKED窓中の伝助変更を上書き
- **現状の問題**: LOCKED窓（抽選実行済み・未確定）は伝助読み取りを停止する。確定時 `writeAllForLotteryConfirmation` は dirtyフィルタなしで全マッピング済み選手のアクティブセルを書き込むため、直近同期（最大5分前）〜確定の間に伝助で○→×に変えた人は×が○に戻され、アプリもWONのまま「参加」扱いになる。
- **修正方針**:
  1. 確定書き戻しの**直前に一度伝助を読み取り**（scrape）、アプリ側 WON/OFFERED/PENDING（＝○書き戻し予定）に対し伝助側が×（不参加）になっている参加者を **差分として検知**。
  2. 差分がある場合は、**その選手の書き戻しをスキップせず**上書きするのではなく、**管理者へ通知（LINE＋アプリ内）＋WARNログ**を出し、書き戻し結果（`ConfirmLotteryResponse` / 書き込みステータス）に差分件数・対象を含める。差分自体で確定処理はブロックしない（確定DBは維持）。
  3. スコープを限定するため、対象は「伝助側×だがアプリ側○書き戻し予定」の反転リスクのみ（△↔○等の軽微差は対象外）。
- **あるべき姿**: 確定直前の伝助変更が黙って上書きされず、管理者が差分を認識して手動対応できる。

### A-4【高】正規化名キー衝突で別人に丸/×
- **現状の問題**: 読み取り側 `playerNameMap`（正規化名→playerId）を `(a,b)->a` 先勝ちで構築、書き込み側 `extractAllMemberMappings`（正規化名→列ID）を後勝ちで構築するため、正規化後に同名となるアカウントが2つあると出欠が一方に集約され、別人に丸/×が付く。未統合重複4名（川瀬/高橋/山野/むらやま）が現存し、実データで発生しうる。
- **修正方針**:
  1. **衝突検知**: `playerNameMap` / `extractAllMemberMappings` 構築時に、同一正規化キーに複数のplayerId（またはDB選手名）が対応する場合を検知する。
  2. 衝突を検知した対象名は、**取り込み・書き込みとも当該名についてはスキップ（黙って先勝ち/後勝ちで進めない）** し、`unmatchedNames` とは別枠で **「名寄せ衝突」として管理者へ通知**（LINE＋アプリ内、対象名一覧付き）。
  3. **重複4名の統合（本番DB）**: 過去の星野統合（#932）と同方式（UNIQUE制約 `players.name` によりリネーム不可 → マスター選択・参照付け替え・重複側 `deleted_at` 論理削除）で川瀬/高橋/山野/むらやまを統合するSQL/スクリプトを作成し、本番PostgreSQLへ適用する（`c:\tmp\dbtool` の JDBC ツール経由、`database/` 配下には**スキーマ変更でないため置かない**が、監査可能な形でスクリプトと適用ログを feature ディレクトリに残す）。
- **あるべき姿**: 名寄せ衝突が起きても別人に出欠が混入せず管理者が気づける。既存の重複4名は統合され衝突源が消える。

### B-1【中】容量拡張確定OFFEREDの正午DECLINE（→要承諾に統一）
- **現状の問題**: `promoteWaitlistedAfterCapacityIncrease` は昇格OFFEREDに `offer_deadline=null`（＝確定扱い）を設定し既存OFFEREDの期限も一律クリアするが、`SameDayConfirmationScheduler` は期限有無を問わず全OFFEREDを当日12:00にDECLINED(×)化する。「確定」と通知された人が承諾操作をしないまま当日を迎えると自動辞退＋伝助×になる。JavadocとSchedulerの意図が矛盾。
- **修正方針**（確定: 要承諾に統一）:
  1. `promoteWaitlistedAfterCapacityIncrease` の昇格OFFEREDに **通常オファーと同じ応答期限**（`calculateOfferDeadline`）を設定し、`offer_deadline=null`（auto-confirm）をやめる。既存OFFEREDの期限一律クリアも廃止。
  2. 昇格した選手に**オファー通知**（アプリ内＋LINE）を送信し、承諾操作を促す（現状の無通知昇格を改める）。
  3. これにより全OFFEREDが「期限付き・要承諾」に統一され、12:00一括DECLINE・OfferExpiryScheduler の挙動と整合する。
- **あるべき姿**: 容量拡張後の昇格も本人の承諾が必要で、承諾すればWON、未承諾なら期限/正午で辞退という一貫した挙動になる。「確定」の誤表現を排除。

### B-2【中】プレビュー↔確定間の母集団変化
- **現状の問題**: 確定は同一seedで抽選を再実行するが、その間に5分同期が新○をPENDING取り込み・キャンセル等で母集団が変わると、シャッフル列がずれてプレビューと異なる当落が黙って確定する。フロントは優先選手変更時のみ再プレビュー強制で、母集団変化は検知しない。
- **修正方針**:
  1. プレビュー応答に **母集団シグネチャ**（対象セッション群の PENDING 参加者ID集合のハッシュ）を含めて返す。
  2. 確定リクエストにそのシグネチャを添付。確定時にサーバが現在の母集団シグネチャを再計算し、**不一致なら確定を拒否**（409/専用エラー）してフロントに再プレビューを促す。
  3. フロントは不一致エラーを受けて `phase` を `idle` に戻し「参加状況が変わったため再プレビューが必要」と表示。
- **あるべき姿**: プレビューで見た結果と異なる抽選が黙って確定することがなくなる。

### B-3【中】伝助行の手動変更でズレ書込／無言スキップ
- **現状の問題**: 書き込み行の対応は「アプリの日付×試合番号昇順」と「伝助フォームの join-ID 出現順」の位置合わせで、件数不一致なら安全側でスキップするが、伝助側で行を削除/並べ替えるとキャッシュ済み `densuke_row_ids` が無効化されず、件数が偶然一致するとズレたまま書き込む。件数不一致時の書き込みは無言で永続スキップされ dirty が滞留する。
- **修正方針**:
  1. **件数不一致スキップの可視化**: `parseAndSaveRowIds` / 書き込み経路で join-ID件数とスケジュール件数の不一致を検知したら、書き込みステータス `errors[]` に記録し**管理者へ通知**（伝助管理画面 pendingCount と併せて気づける形に）。
  2. **row_id整合の防御**: 編集フォーム取得時に、キャッシュ済み `densuke_row_ids` の (日付×試合番号→join-id) 対応が現在のフォーム構造と矛盾しないか軽量検証し、矛盾検知時は当該URLの `densuke_row_ids` を破棄して再取得（次回同期で正しく再構築）。
  3. 監査ログに「伝助フォーム構造変化を検知・row_id再構築」を残す。
- **あるべき姿**: 伝助側の行変更があっても別日/別試合に出欠が書かれるデータ破壊を防ぎ、書き込みが滞留したら管理者が検知できる。

### B-4【中低】月全置換登録による後入れ○の巻き戻し（→楽観ロック）
- **現状の問題**: 締切前登録は「その月の自分の登録を全ソフトデリート→リクエスト内容で再作成」、SAME_DAYも差分＋非該当キャンセルで、フロントは画面ロード時stateを丸ごと送る。古いタブ/別端末で保存すると、その後に付けた○が黙ってCANCELLED(×書き戻し)になる。
- **修正方針**（確定: 楽観ロック）:
  1. 参加状況取得API（`PlayerParticipationStatusDto`）に、その月×プレイヤーの**版情報**（例: 対象参加行の `max(updated_at)` またはリビジョン値）を含めて返す。
  2. 参加登録リクエスト（`PracticeParticipationRequest`）にクライアントが保持する版情報を添付。`registerParticipations` の冒頭で現DBの版と照合し、**不一致なら409（競合）** を返して保存を中止し、フロントへ再読込を促す。
  3. フロントは409受信時に「他の端末または伝助で参加状況が更新されました。最新を読み込みます」と表示し再取得。
- **あるべき姿**: 並行編集で後から付けた○が黙って巻き戻ることがなくなる。

### B-5【低中】Phase3-A6当日昇格の空き判定不一致
- **現状の問題**: 伝助○によるWAITLISTED→WON昇格の空き判定が `WON < capacity` のみで、他所で使う `isFreeRegistrationOpen`（OFFEREDも定員算入・WAITLISTED残存で不可）と基準が不一致。瞬間的定員超過や待ち行列を飛ばした昇格が起こりうる。
- **修正方針**: Phase3-A6（`processPhase3Maru` の WAITLISTED 分岐）の空き判定を、他経路の `isFreeRegistrationOpen` と**同等の基準に揃える**。ただし対象者自身が WAITLISTED のため `isFreeRegistrationOpen` をそのまま呼ぶと「WAITLISTED残存で不可」が常に真になり当日昇格が全無効化される。そこで**昇格は維持しつつ判定のみ厳格化**する（ユーザー確認済み）：`WON + OFFERED < capacity`（OFFERED算入）**かつ**対象者が待ち行列の先頭（最小 `waitlistNumber` の WAITLISTED）のときのみ WON へ昇格する（キュー飛ばし防止）。
- **あるべき姿**: 当日昇格の空き判定が他経路と一貫し、OFFEREDを含めた定員管理が守られ、待ち行列を飛ばした昇格・瞬間的定員超過が起きない。

### D【低】回帰テスト追加
- **現状の問題**: 抽選アルゴリズムの連鎖落選・月内救済・一般枠30%保証・キャンセル待ち番号引き継ぎ・シード再現性が単体テスト未整備。A-1〜A-3の反転経路、キー衝突、正午12:00境界のテストも無い。
- **修正方針**: 本改修で変更する各項目に対する回帰テスト、および既存未カバーのアルゴリズム特性テストを追加（下記 4.5 参照）。
- **あるべき姿**: 反転経路が再発したらCIで検出できる。

### C【低】仕様周知
- **現状の問題**: 「Phase1で△は無視・記録されない」「正午前/満員で伝助○→△に戻す（抽選バイパス防止）」等、利用者の直感に反しうる仕様がドキュメント/画面に明示されていない。ドキュメント間の軽微な不整合（§番号重複、`WAITLIST_DECLINED`記載揺れ、`SameDayVacancyScheduler`欠落等）も残存。
- **修正方針**: 本改修で変わる挙動を `docs/SPECIFICATION.md` / `docs/DESIGN.md` に反映し、監査で見つかったドキュメント不整合（Section D）を修正。利用者が誤解しやすい点は画面文言（伝助管理・参加登録・抽選結果）に注記。
- **あるべき姿**: 仕様と実装・画面表示が一致し、利用者/管理者が挙動を予測できる。

---

## 3. 技術設計

### 3.1 API変更
| エンドポイント | 変更 |
|---|---|
| `GET /api/practice-sessions/participations/player/{playerId}/status` | レスポンス `PlayerParticipationStatusDto` に**版情報**フィールド追加（B-4） |
| `POST /api/practice-sessions/participations` | リクエスト `PracticeParticipationRequest` に**版情報**追加。競合時 **409** を返す（B-4） |
| `POST /api/lottery/preview` | レスポンスに**母集団シグネチャ**追加（B-2） |
| `POST /api/lottery/confirm` | リクエストにシグネチャ追加、母集団不一致で **409/専用エラー**（B-2）。レスポンス `ConfirmLotteryResponse` に**伝助差分情報**追加（A-3） |
| `PUT /api/practice-sessions/{sessionId}/matches/{matchNumber}/participants` | 対象を WON/PENDING のみに限定（A-1、リクエスト形は不変） |
| `GET /api/practice-sessions/{...}`（セッション詳細） | `matchParticipants` の各要素に **playerId** 追加（A-1、モーダルのID一致用） |

破壊的変更の扱い: 追加フィールドは後方互換（未送信時は従来挙動へフォールバック可能な設計とし、旧クライアントが即座に壊れないようにする）。B-2/B-4の版・シグネチャは「未送信なら検証スキップ＋WARN」の緩和を初期リリースで許容し、フロント更新後に必須化を検討。

### 3.2 DB変更
- **原則スキーマ変更なし**（既存カラムで対応）。B-4の版情報は既存 `updated_at` を利用する想定。もし専用リビジョン列が必要と判断した場合のみ `database/*.sql` を追加し、CLAUDE.md の本番適用ルールに従う（要件確定時に判断、現時点ではスキーマ追加なしを第一候補）。
- **データ移行（スキーマ非変更）**: A-4の重複4名統合スクリプト（参照付け替え＋論理削除）。`database/` には置かず `docs/features/lottery-densuke-integrity/merge-duplicates/` にSQL・手順・適用ログを保存。本番適用は `c:\tmp\dbtool` の JDBC ツール（IPv4強制でNAT64回避）を使用。

### 3.3 フロントエンド変更
- `components/MatchParticipantsEditModal.jsx`: 初期選択をWON/PENDINGのplayerIdに限定、保存前確認ダイアログ、対象説明文（A-1）。
- `pages/lottery/LotteryManagement.jsx`: プレビュー結果のシグネチャ保持、確定時に添付、409（母集団変化）ハンドリング＋再プレビュー誘導（B-2）。確定レスポンスの伝助差分表示（A-3、現状 alert からの改善）。
- `pages/practice/PracticeParticipation.jsx`（およびSAME_DAY登録経路）: 版情報の保持・送信、409（並行編集）ハンドリング＋再読込（B-4）。
- 文言追記（C）: 伝助管理・参加登録・抽選結果の各画面に、直感に反しうる挙動の注記。
- ※新規画面はなし。UI変更は既存画面への注記・ダイアログ・エラー処理の追加に留まるため、design-screen ループは実施しない（純ロジック改修への軽微なUI付随）。

### 3.4 バックエンド変更（レイヤ別）
- **Service**
  - `PracticeParticipantService`: `setMatchParticipants`（A-1: WON/PENDING限定全置換）、`registerAfterDeadline`（A-2: 抽選未実行→PENDING）、`registerParticipations`（B-4: 版照合409）。
  - `LotteryService`: `processMatch`（A-2: 既存WON/OFFEREDを定員から差引）、`previewLottery`/`confirmLottery`・`executeAndConfirmLottery`（B-2: シグネチャ算出・照合、A-3: 確定前伝助差分検知の起動）。
  - `DensukeImportService`: `playerNameMap` 構築（A-4: 衝突検知）、`processPhase3Maru`（B-5: `isFreeRegistrationOpen` 統一）。
  - `DensukeWriteService`: `extractAllMemberMappings`（A-4: 衝突検知）、`writeAllForLotteryConfirmation`（A-3: 差分検知連携）、`parseAndSaveRowIds`/`ensureRowIds`（B-3: 不一致可視化・row_id整合検証）。
  - `WaitlistPromotionService`: `promoteWaitlistedAfterCapacityIncrease`（B-1: 期限付与＋オファー通知、auto-confirm廃止）。
  - `DensukeScraper`: 確定前差分検知用の scrape 再利用（A-3、既存 `scrape`/`parse` を利用、新設は最小限）。
- **Controller**: `LotteryController`（confirm/preview の入出力拡張）、`PracticeSessionController`（participations の版・409、セッション詳細の playerId 露出）。
- **DTO**: `PlayerParticipationStatusDto`（版）、`PracticeParticipationRequest`（版）、`LotteryResultDto`系/プレビュー応答（シグネチャ）、`ConfirmLotteryResponse`（伝助差分）、`PracticeSessionDto.MatchParticipantInfo`（playerId）。
- **通知**: `LineNotificationService` / `NotificationService` に「名寄せ衝突通知」「確定前伝助差分通知」「伝助行不一致通知」「容量拡張オファー通知」を追加（既存の通知種別・チャネルルーティング規約 `ADMIN_` プレフィックス等に準拠）。

### 3.5 処理フローの要点
- A-2＋B-5＋B-1で「WON/OFFEREDを定員に算入する」基準を全経路で `isFreeRegistrationOpen` 系に寄せ、定員判定の一貫性を担保する。
- A-3の差分検知はネットワークI/O（scrape）を伴うため、確定処理のクリティカルパスに載せるが**失敗しても確定DBは維持**（既存 REQUIRES_NEW / afterCommit の設計思想を踏襲、差分検知失敗はWARN＋通知のみで確定を止めない）。

---

## 4. 影響範囲

### 4.1 影響を受ける既存機能
- 抽選実行・プレビュー・確定（B-2で確定に前提条件追加）。
- 参加登録（B-4で409の可能性、A-2で締切後の付与ステータス変更）。
- 試合別参加者編集（A-1で対象・初期選択変更）。
- 伝助双方向同期（A-3/A-4/B-3/B-5で読み書きの分岐追加）。
- 容量拡張（expand-venue）後の昇格挙動（B-1で通知＋要承諾化）。

### 4.2 共通コンポーネント・ユーティリティ
- `isFreeRegistrationOpen`（定員判定）の呼び出し箇所拡大（A-2/B-5）。回帰に注意。
- `calculateOfferDeadline`（B-1で新たに容量拡張経路から利用）。
- 通知サービス（複数の新規通知種別）。

### 4.3 互換性・破壊的変更
- API追加フィールドは後方互換設計（未送信フォールバック＋WARN）。フロント更新前でも既存クライアントが即座に壊れないことを必須要件とする。
- A-2はMONTHLY団体の締切後登録者の体験を「即確定」→「抽選対象」に変える（仕様変更。Cで周知）。
- B-1は容量拡張後の昇格を「自動確定」→「要承諾」に変える（仕様変更。Cで周知）。
- 本番DB: A-4の重複統合はデータ移行を伴う（`players.name` UNIQUE制約への配慮必須。過去 #932 と同方式）。

---

## 5. 設計判断の根拠
- **A-2をPENDING化**: 伝助経由の○がPENDING（抽選対象）になるのと挙動を揃え、アプリ/伝助の公平性の食い違いと定員超過を同時に解消できる（先着確定の維持よりも公平性・整合性を優先というユーザー判断）。
- **A-4に統合SQLを含める**: 検知コードだけでは現存する衝突源（重複4名）が残り「名寄せ衝突通知」が鳴り続けるため、根本原因の統合まで行う（本番DB操作は過去実績 #932 と同方式で安全側に）。
- **B-1を要承諾に統一**: 「容量拡張＝確定」と「12:00一括DECLINE」の矛盾を、確定側でなく承諾必須側に統一することで全OFFEREDの挙動を単純化し、期限管理・スケジューラと整合させる。
- **B-4を楽観ロック**: 全置換方式の登録ロジックを大きく作り替える差分更新化よりも、版照合による競合検知は影響範囲が小さく、既存の全置換設計を維持したまま巻き戻し事故を防げる。
- **A-3を非ブロッキング検知**: 確定を止めると運用が滞るため、既存の「書き戻し失敗でも確定DB維持」思想を踏襲し、差分は通知で管理者に委ねる。

## 6. デザインへの宿題（→ /design-screen）
- 現時点でなし（新規画面なし、UI変更は既存画面への注記・ダイアログ・エラー処理のみ）。実装中に視覚設計が要ると判明した場合のみ追記する。
