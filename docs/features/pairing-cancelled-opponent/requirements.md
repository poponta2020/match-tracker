---
status: completed
completed_sections: [ユーザーストーリー, 機能要件, 技術設計, 影響範囲]
next_section: null
---
# 対戦組み合わせへのキャンセル反映（pairing-cancelled-opponent）要件定義書

## 1. 概要

### 目的
参加者がその日の練習をキャンセルした場合に、**すでに作成済みの対戦組み合わせ**へキャンセルを反映する。
- 組み合わせ（閲覧モード）から、キャンセルした選手を「対戦相手キャンセル」として可視化する。
- 編集モードでは、キャンセルした選手のスロットを単なる「空き」として扱い、管理者が組み直せるようにする。

### 背景・動機
現状、`match_pairings` は選手ID（`player1_id` / `player2_id`）を直接保持するだけで、参加者（`practice_participants`）の `status = CANCELLED` とは**一切連動していない**。
そのため、組み合わせ作成後に誰かがキャンセルしても、組み合わせ画面にはその選手が残り続け、対戦相手は「相手がいるつもり」で当日を迎えてしまう。これを画面上で正しく見せたい。

### 方式（最重要の設計判断）
**read-time（表示時に自動判定）方式**を採用する。`match_pairings` のレコードは変更せず（非破壊）、取得APIのDTO構築時に各選手のキャンセル状態を判定してフラグを付与し、フロントの表示で除外・明示する。
→ 設計判断の根拠は [6章](#6-設計判断の根拠) を参照。

---

## 2. ユーザーストーリー

### 対象ユーザー
- **管理者（ADMIN / SUPER_ADMIN）**: 組み合わせ画面（`/pairings`）で当日の組を確認・編集する。
- **選手（PLAYER）**: 組み合わせ画面の閲覧モードで自分や全体の組を確認する（閲覧のみ）。

### 利用シナリオ
1. 管理者が午前に対戦組み合わせを作成・保存する。
2. その後、選手Aが「2試合目」をキャンセルする（本人キャンセル / 管理者編集 / 当日登録時の自動キャンセルのいずれか）。
3. 管理者・選手が組み合わせ画面（閲覧モード）を開くと、2試合目でAと組んでいた相手の行が「鈴木 vs 山田（キャンセル）」のように、相手がキャンセルで不在であると分かる。
4. 管理者が編集モードに切り替えると、その組はAのスロットが「空き」になっており、別の選手をドラッグして組み直せる。Aは（キャンセル済みのため）待機リストにも参加者プールにも現れない。
5. キャンセルしていない1試合目・3試合目の組はそのまま影響を受けない。

---

## 3. 機能要件

> 画面レイアウト・操作フローの大枠は既存の [PairingGenerator.jsx](../../../karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx)（閲覧モード／編集モード）を踏襲する。本機能は既存挙動への**差分**のみを定義する。視覚的な表現（バッジ色・記号など）は [デザインへの宿題](#デザインへの宿題--design-screen-pairing-cancelled-opponent) に委ねる。

### 3.1 キャンセル判定ルール（共通）

- ある組（日付 D・試合番号 M）の選手 X が「キャンセル済み」とは、**`(Dのセッション, X, M)` に対応する `practice_participants` 行の `status = CANCELLED`** であることを指す。
  - `practice_participants` は `(session_id, player_id, match_number)` にユニーク制約があるため、`(セッション, X, M)` に対応する行は最大1件。その1件が `CANCELLED` か否かで判定する。
  - `match_number = null` の抜け番（セッション共有）マーカー行は本判定の対象外（試合単位の判定に使わない）。
- **粒度は試合単位（per-match）**。Xが2試合目をキャンセルしても、1試合目・3試合目の組には影響しない。
- 判定対象のステータスは `CANCELLED` のみ（`DECLINED` / `WAITLIST_DECLINED` は繰り上げ枠の辞退で、組に配置済みの選手のキャンセルとは別物。組に入っているのは `WON` 由来の選手であり、その離脱は `CANCELLED` になる）。

### 3.2 閲覧モード（`/pairings` の `isViewMode` 表示）

| ケース | 表示 |
|--------|------|
| 片方がキャンセル済み | キャンセルした選手を **「{名前}（キャンセル）」** と表記し、相手がキャンセルで不在と分かるようにする（例: `鈴木 vs 山田（キャンセル）`）。元の相手名は表示する。 |
| 両方キャンセル済み | その**組（行）は閲覧モードで非表示**にする（試合として成立しないため）。 |
| キャンセルなし | 現状どおり `player1Name vs player2Name`。 |

- 表示する名前はキャンセルした選手の元の名前（DTOの `player1Name` / `player2Name`）。
- 視覚的強調（色・取り消し線・アイコン等）は[デザインへの宿題](#デザインへの宿題--design-screen-pairing-cancelled-opponent)とする。

### 3.3 編集モード（`/pairings` のドラッグ＆ドロップ表示）

- キャンセルした選手のスロットは **「空き」**（既存の空きスロット表示 `空き` と同一）として扱う。キャンセル表記は出さない（ユーザー要望どおり「単に相手が空になるだけ」）。
- キャンセルした選手は **参加者プール・待機（抜け番）リストにも現れない**（`CANCELLED` はアクティブ参加者ではないため、既存の参加者取得ロジックで自然に除外される）。
- 両方キャンセルの組は、編集モードでは**行ごと除去**する（「空き vs 空き」の無意味な行を残さない）。
- 管理者は空いたスロットに別の選手をドラッグして組み直せる（既存挙動）。組み直して保存すれば新しい組が確定する。
- **保存（createBatch）時の挙動**: キャンセルで空いた（未完成の）組は保存リクエストに含めない（既存 `buildSaveRequests` が両選手揃った組のみ送信）。その結果、保存時に元の組レコードは削除され、キャンセルしていない生存側の選手はアクティブ参加者として参加者プールに戻る（**データ消失なし**）。

### 3.4 スコープ外（今回やらないこと）

- **LINE送信用テキスト（`/pairings/summary`, [PairingSummary.jsx](../../../karuta-tracker-ui/src/pages/pairings/PairingSummary.jsx)）は対象外**。同画面は `getByDateAndMatchNumber` を呼ぶためDTOにキャンセルフラグは届くが、テキスト生成（`generateText`）は改修せず、キャンセル者も従来どおり名前が出力される。
  - ⚠️ 注意点として[5章](#5-影響範囲)・最終確認に明記する（必要なら別機能で対応）。
- ホーム・試合一覧など `/pairings` 以外の画面でのキャンセル表示は対象外。
- キャンセルの取り消し・当日補充で再参加した場合の組への自動復帰はしない（read-time方式では、組レコードが残っていれば表示は自動回復するが、組み直し後は元に戻らない。挙動の保証はしない）。

---

## 4. 技術設計

### 4.1 API設計

**エンドポイントの新規追加・URL変更はなし。** 既存の取得APIのレスポンス（DTO）にフィールドを追加する。

| エンドポイント | 変更内容 |
|----------------|----------|
| `GET /api/match-pairings/date-and-match`（`getByDateAndMatchNumber`） | レスポンスの各 `MatchPairingDto` に `player1Cancelled` / `player2Cancelled`（boolean）を付与。 |
| `GET /api/match-pairings/date`（`getByDate`） | 同上（将来の利用に備えて同じ enrich を通すが、現状の主利用は前者）。 |

- フィールドはデフォルト `false`。後方互換（既存フロントは無視するだけ）。

### 4.2 DB設計

- **スキーマ変更なし**（read-time方式の最大の利点）。`match_pairings` は非破壊。マイグレーションSQL不要・本番DB適用不要。

### 4.3 バックエンド設計

**DTO: [MatchPairingDto.java](../../../karuta-tracker/src/main/java/com/karuta/matchtracker/dto/MatchPairingDto.java)**
- `private boolean player1Cancelled;` / `private boolean player2Cancelled;` を追加。

**Service: [MatchPairingService.java](../../../karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchPairingService.java)**
- `getByDateAndMatchNumber` / `getByDate` のDTOリスト構築後に enrich メソッドを1つ追加して呼ぶ:
  ```
  enrichWithCancellation(dtos, sessionDate, matchNumber, organizationId)
  ```
- `enrichWithCancellation` の処理:
  1. 対象日 `sessionDate`（必要なら `organizationId`）のセッションを解決する。`getByDate`（全試合）の場合は対象試合番号を限定せず全件、`getByDateAndMatchNumber` の場合は `matchNumber` 限定。
  2. `practice_participants` から `status = CANCELLED` の行を**1クエリでまとめて取得**し、`(player_id, match_number)` のキャンセル集合を作る（N+1回避）。
     - リポジトリに `findBySessionIdInAndStatus(sessionIds, CANCELLED)` 等のメソッドを用意（既存 `findBySessionIdAndMatchNumberAndStatus` の拡張でも可）。
  3. 各 `MatchPairingDto` について、`(player1Id, matchNumber)` / `(player2Id, matchNumber)` がキャンセル集合に含まれれば `player1Cancelled` / `player2Cancelled` を `true` にする。
- **組織スコープ / 複数団体の同日対応**: `organizationId != null` のときは当該団体セッションのみ。`null`（SUPER_ADMIN / PLAYER）のときは同日複数セッションを許容し、`player_id` 一致でキャンセル集合を引く（`player_id` は全体ユニーク）。既存 `getSessionAllPlayerIds` / セッション解決ロジックと整合させる。

**Repository: [PracticeParticipantRepository.java](../../../karuta-tracker/src/main/java/com/karuta/matchtracker/repository/PracticeParticipantRepository.java)**
- 上記キャンセル集合取得用のクエリメソッドを追加（既存メソッドで賄えれば追加不要）。

### 4.4 フロントエンド設計

**[PairingGenerator.jsx](../../../karuta-tracker-ui/src/pages/pairings/PairingGenerator.jsx)**
1. `loadExistingPairingsToState`（L110-144）: 変換時に `player1Cancelled` / `player2Cancelled` を state に引き継ぐ。
2. **閲覧モード描画**（L1045-1051）:
   - 両方キャンセルの組は描画しない（行を除外。`.filter()` で両キャンセル行をスキップ、または map 内で `null` を返す）。
   - 片方キャンセルなら、その選手名を「{名前}（キャンセル）」表記＋視覚強調で描画。
3. **閲覧→編集モード切替**（L988-990 の「編集」ボタン `setIsViewMode(false)`）:
   - 切替時にキャンセルスロットを実体化（partialキャンセル＝そのスロットの `playerXId`/`playerXName` を `null` にしてフラグ解除、両キャンセル行は除去）してから編集モードに入る。
   - これにより編集モードの描画・ドラッグ・保存（`buildSaveRequests` / `hasNothingToSave`）は既存ロジックのまま「空き」として正しく動作し、生存側選手も自然に扱える。
   - ※ ドラフト復元（[pairingDraftLogic.js](../../../karuta-tracker-ui/src/pages/pairings/pairingDraftLogic.js)）経由の編集モード復帰時も整合するよう確認する。

> 上記「編集モード切替時に実体化」案を基本とする。もし閲覧/編集で同一 `pairings` state を共有したまま分岐させる方が綺麗なら、代替として `buildSaveRequests` / `hasNothingToSave` 側でキャンセル組を除外する実装でもよい（実装時に詳細確定）。

### 4.5 テスト方針
- バックエンド: `MatchPairingServiceTest` に `enrichWithCancellation` のケース追加（片方/両方キャンセル、別試合は非影響、組織スコープ）。
- フロント: `PairingGenerator.integration.test.jsx` に閲覧モードのキャンセル表示・両キャンセル行非表示・編集モードの空き化＋保存で生存者が消えないケースを追加。純粋関数化できる判定は `pairingDisplayLogic.js` 等へ切り出し本番・テスト共有（既存方針踏襲）。

---

## 5. 影響範囲

### 変更が必要な既存ファイル
| ファイル | 変更内容 |
|----------|----------|
| `dto/MatchPairingDto.java` | `player1Cancelled` / `player2Cancelled` 追加 |
| `service/MatchPairingService.java` | `enrichWithCancellation` 追加、`getByDate*` から呼ぶ |
| `repository/PracticeParticipantRepository.java` | キャンセル集合取得クエリ（必要なら） |
| `pages/pairings/PairingGenerator.jsx` | 閲覧モード描画・編集切替時の実体化・state引き継ぎ |
| `pages/pairings/pairingDisplayLogic.js`（または新規純粋関数） | キャンセル表示/除外判定の純粋関数化 |
| 各テスト | 上記に対応するケース追加 |

### 既存機能への影響と確認事項
- **DTOフィールド追加のみ**で既存APIの後方互換は保たれる。
- **`getByDateAndMatchNumber` は [PairingSummary.jsx](../../../karuta-tracker-ui/src/pages/pairings/PairingSummary.jsx)（LINEテキスト）も使用**する。enrich でフラグは増えるが `generateText` は未改修なのでテキスト出力は不変（キャンセル者も従来どおり出る）。→ スコープ外として意図的。
- 保存（`createBatch`）ロジック自体は無改修。キャンセル組を「空き」として送らないことで、既存の削除→再作成フローに自然に乗る。
- 自動組み合わせ（`autoMatch`）はそもそも `CANCELLED` を除外済みのため、新規作成時は影響なし（本機能は「作成後にキャンセルが起きた組」が対象）。

---

## デザインへの宿題（→ /design-screen pairing-cancelled-opponent）【解決済み】

視覚表現は [design-spec.md](./design-spec.md)（`status: locked`）で確定。
- 閲覧モード：キャンセル選手名は `text-gray-400 line-through`、右端に既存pillと同じ丸タグ（`bg-gray-100 text-gray-600` ＋ Banアイコン「キャンセル」）。両方キャンセルの組は非表示。
- 現行画面のまま（リデザインしない）。実装は既存 className を流用。

---

## 6. 設計判断の根拠

| 判断 | 根拠 |
|------|------|
| **read-time（非破壊）方式を採用** | ① `match_pairings.player1_id/player2_id` は NOT NULL ＋ 順不同ユニーク関数インデックスがあり「空」を持てない。write-time だとスキーマ変更＋本番DB適用が必要。② キャンセル入口が3経路（本人キャンセル `/lottery/cancel`・管理者編集 `/lottery/admin/edit-participants`・当日登録 `registerSameDay`）あり、write-time は全フックが必要で漏れやすい。③ `createBatch` は削除→再作成のため、書き換えた状態と競合する。④ 直近の #958 修正も非破壊方向。read-time は表示側で一元判定でき、これら全てを回避。 |
| **粒度は試合単位** | キャンセル自体が `(session, player, match_number)` 単位で行われる（1試合だけキャンセル可能）ため、除外も試合単位が自然。 |
| **キャンセル者名を「（キャンセル）」付きで表示** | 「誰が抜けたか」が分かる方が有用で、DTOに名前が既にあるため追加コストなし。 |
| **両方キャンセルは行ごと非表示** | 試合として成立しないため。 |
| **LINEテキストはスコープ外** | ユーザーが閲覧モードのみを対象に選択。テキスト生成は別ライフサイクル。 |
