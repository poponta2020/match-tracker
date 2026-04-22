---
status: completed
audit_source: ユーザー要望（新機能追加）
selected_items: []
---

# 抽選機能 管理者指定優先選手 要件定義書

## 1. 改修概要

### 対象機能
抽選機能（Lottery） — 月次抽選のプレビュー／確定フロー

### 改修の背景
現行の抽選は以下の優先順位で処理されている：
- 連続落選救済（同月の別セッションで落選経験がある選手）
- 一般枠（一般枠最低保証あり）

これに加え、**運営側の判断で「この選手は必ず通したい」という人を月単位で指定できる仕組み**がほしい（例: 大会直前の強化対象選手、休まず通い続けている選手など）。

### 改修スコープ

| # | 項目 | 分類 |
|---|------|------|
| 1 | 管理者が月×団体単位で「優先選手」を指定できる機能の追加 | 新機能 |
| 2 | 抽選ロジックの拡張（管理者指定優先 > 連続落選救済 > 一般） | 新機能 |
| 3 | 優先選手指定の監査ログ（確定時スナップショット） | 新機能 |

---

## 2. 改修内容

### 2.1 管理者指定優先選手の仕組み

**概要:**
抽選を実行する管理者が、対象月にいずれかの練習に参加希望を出している選手の中から、任意の人数を「優先選手」に指定できる。指定された選手は、その選手が希望しているセッションでの抽選で最優先扱いとなる。

**優先枠の扱い方:**
- 優先選手同士でも定員超過時は抽選する（全員必ず通るわけではない）
- ただし一般枠・連続落選救済枠より先に枠を確保する
- 優先選手が落選した場合はキャンセル待ちの最上位に入る

**優先順位（高→低）:**
1. 管理者指定優先選手
2. 連続落選救済（同月別セッションで落選経験あり）
3. 一般枠（一般枠最低保証 `lottery_normal_reserve_percent` を考慮）

**適用範囲:**
- 優先選手が参加希望を出しているセッションのみ
- 希望を出していないセッションに勝手に当選させることはしない

### 2.2 UI の追加

**配置:** `LotteryManagement.jsx` の「抽選実行（プレビュー）」ボタンの上に新セクションを追加する。

**表示内容:**
- セクション見出し: 「参加希望者一覧（優先選手指定）」
- 選択済み人数表示: 「優先選手: 3名選択中」
- 参加希望者一覧（チップUI、既存の選手チップと同じスタイル）
  - 選手名のみ表示（級・段などの追加情報は画面がごちゃつくので省略）
  - 級順でソート（既存の `PlayerSortHelper.playerComparator()` 相当のソートロジックを流用）
- チップをクリック → 選択／解除トグル
  - 選択中: 強調スタイル（既存の「選択済み」状態と同等）
  - 未選択: 通常スタイル

**対象リスト:**
- 選択中の `year / month / organizationId` において、いずれかの練習セッションに参加希望（`LotteryParticipant` が存在）を出している選手の一意リスト

**表示タイミング:**
- 年月・団体を切り替えたタイミングで再取得
- `phase === 'idle'` のとき選択可能
- `phase === 'preview'` では表示は維持するが変更は可能（間違えて押した場合の修正用に解放）
- `phase === 'confirmed'` ではチップは参照専用（グレーアウト）

### 2.3 状態管理・永続化

**フロントエンド（一時保持）:**
- React state + `sessionStorage`
- キー: `lottery_priority_{year}_{month}_{orgId}`
- 値: `[playerId, ...]`
- 年月・団体を切り替えるとキーが変わるので独立して保持される
- 確定成功後に該当キーをクリア

**バックエンド（監査ログ）:**
- `LotteryExecution` に `priority_player_ids`（JSON相当。TEXT列にJSON配列をシリアライズ）を追加
- 確定時（`executeAndConfirmLottery`）にリクエストの `priorityPlayerIds` を保存
- プレビュー単体では保存しない（DBに残さない）

### 2.4 抽選アルゴリズムの変更

`LotteryService.processMatch` を拡張し、`Set<Long> adminPriorityPlayers` を新規パラメータとして受け取る。各試合の参加希望者を以下の3つに分類：

1. **管理者指定優先**: `adminPriorityPlayers` に含まれる選手
2. **連続落選救済**: `monthlyLosers` に含まれるが `adminPriorityPlayers` に含まれない選手（従来通り）
3. **一般**: 上記いずれにも含まれない選手

抽選は以下の順で枠を埋める：
- 管理者指定優先: 残り枠 >= 人数なら全員当選、不足なら抽選で当選者決定、落選者はキャンセル待ちの先頭へ
- 連続落選救済: 一般枠最低保証（`normalReserveCount`）を確保した上で残り枠から当選者を決定、落選者はキャンセル待ち
- 一般: 残り枠で抽選、落選者はキャンセル待ち

**キャンセル待ち順:**
- 管理者指定優先落選者 → 連続落選救済落選者 → 一般落選者 の順に並べる
- 従来の「連続試合キャンセル待ち順番引き継ぎ」ロジックは維持

### 2.5 バリデーション

以下のケースはすべて **400 Bad Request / 403 Forbidden** で明示的に拒否する：

| 条件 | レスポンス |
|------|----------|
| 指定された `priorityPlayerIds` に、対象月・団体で参加希望を出していない選手ID | 400「参加希望を出していない選手が含まれています」 |
| 指定された `priorityPlayerIds` に、他団体所属の選手ID | 403「他団体の選手を優先選手に指定することはできません」 |
| ADMIN が `organizationId` を自団体以外に指定 | 403（既存の `AdminScopeValidator.validateScope` で対応済み。preview にも追加する） |

---

## 3. 技術設計

### 3.1 API変更

#### 新規エンドポイント: 月次参加希望者一覧取得

```
GET /api/lottery/monthly-applicants?year={year}&month={month}&organizationId={orgId}
```

- **権限:** `@RequireRole({SUPER_ADMIN, ADMIN})` + `AdminScopeValidator.validateScope`
- **ADMIN の場合:** `organizationId` は自団体のIDに強制上書き
- **レスポンス:**
  ```json
  {
    "applicants": [
      { "playerId": 1, "name": "山田太郎" },
      { "playerId": 7, "name": "佐藤花子" }
    ]
  }
  ```
- **ソート順:** 級順（級位→段位→名前）

#### 既存エンドポイント変更

**`POST /api/lottery/preview`** のリクエストDTO `LotteryExecutionRequest` に `priorityPlayerIds: List<Long>`（optional, デフォルト空配列）を追加。

**`POST /api/lottery/confirm`** の `LotteryConfirmRequest` にも同じ `priorityPlayerIds` を追加。preview と同じバリデーションを適用。

**`LotteryController.previewLottery`** に `AdminScopeValidator.validateScope` を追加（現状未適用）。

### 3.2 DB変更

**マイグレーションファイル:** `database/add_priority_player_ids_to_lottery_executions.sql`

```sql
ALTER TABLE lottery_executions
ADD COLUMN priority_player_ids TEXT NULL;

COMMENT ON COLUMN lottery_executions.priority_player_ids IS
  '管理者指定優先選手IDのJSON配列（例: [1,7,12]）。指定なしの場合はNULLまたは[]';
```

- 型: `TEXT`（JSON文字列を格納）※PostgreSQLなので `JSONB` も選択肢だが、既存の他カラムとの整合性と検索要件のなさから `TEXT` を採用
- `LotteryExecution` エンティティに `@Column(columnDefinition = "TEXT") private String priorityPlayerIdsJson;` + `@Transient` な `getPriorityPlayerIds()` / `setPriorityPlayerIds(List<Long>)` ヘルパーを追加
- 既存レコードは NULL のまま（マイグレーションで後方互換）

### 3.3 フロントエンド変更

**ファイル:** `karuta-tracker-ui/src/pages/lottery/LotteryManagement.jsx`

- 「参加希望者一覧」セクションのJSXを追加
- `applicants` state、`priorityPlayerIds` state を追加
- `sessionStorage` との同期 useEffect を追加
- 年月・団体切り替え時に `lotteryAPI.getMonthlyApplicants()` を呼び出し
- プレビュー・確定APIコール時に `priorityPlayerIds` を含める
- 確定成功時に `sessionStorage.removeItem(key)` で該当キーをクリア

**ファイル:** `karuta-tracker-ui/src/api/lottery.js`
- `getMonthlyApplicants: (year, month, organizationId) => apiClient.get('/lottery/monthly-applicants', { params: { year, month, organizationId } })` を追加
- `preview(year, month, organizationId, priorityPlayerIds)` に4引数目を追加
- `confirm(year, month, organizationId, seed, priorityPlayerIds)` に5引数目を追加

チップUIは既存の選手チップスタイル（例: `PracticeSession` や他画面で使われているチップ）を調査のうえ流用する。

### 3.4 バックエンド変更

**Controller** `LotteryController.java`:
- `GET /monthly-applicants` エンドポイント追加
- `previewLottery` / `confirmLottery` で `priorityPlayerIds` のバリデーション実装
- `previewLottery` に `AdminScopeValidator.validateScope` 追加

**Service** `LotteryService.java`:
- `getMonthlyApplicants(int year, int month, Long organizationId)` メソッド新設
- `validatePriorityPlayerIds(List<Long> ids, int year, int month, Long organizationId)` メソッド新設（「希望なし」「他団体」を検出）
- `previewLottery` / `executeLottery` / `executeAndConfirmLottery` のシグネチャに `List<Long> priorityPlayerIds` を追加
- `processSession` / `processMatch` に `Set<Long> adminPriorityPlayers` を伝搬
- `processMatch` の参加者分類ロジックを3層分類に変更
- `executeAndConfirmLottery` で確定時に `LotteryExecution.priorityPlayerIds` を保存

**DTO** `LotteryExecutionRequest.java` / `LotteryConfirmRequest.java`:
- `List<Long> priorityPlayerIds` フィールド追加（デフォルト空配列）

**Entity** `LotteryExecution.java`:
- `priorityPlayerIdsJson` カラム追加
- ヘルパーメソッド `getPriorityPlayerIds()` / `setPriorityPlayerIds(List<Long>)`

**再抽選** `LotteryService.reExecuteLottery`:
- 初回抽選時に保存された `priorityPlayerIds` をデフォルトとして引き継ぐ
- 再抽選リクエストで `priorityPlayerIds` を明示的に指定された場合は、指定された内容で上書きする（変更可能）
- 再抽選後の `LotteryExecution` に、実際に適用された `priorityPlayerIds` を記録
- 再抽選画面側（`LotteryResults.jsx` 等）でも優先選手の再選択UIを提供する必要あり（本改修のスコープ外の場合は別Issueとして切り出す）

**再抽選API** `POST /api/lottery/re-execute/{sessionId}` のリクエストボディ:
- `priorityPlayerIds: List<Long>` を optional として受け付ける
- 省略時は初回抽選の `priorityPlayerIds` をそのまま使用
- 指定時はバリデーション（参加希望あり・同一団体）を実行した上で使用

---

## 4. 影響範囲

### 4.1 影響を受ける既存機能

| 機能 | 影響内容 | リスク |
|------|---------|--------|
| 抽選プレビュー／確定フロー | リクエストボディに `priorityPlayerIds` フィールド追加（optional） | 低（後方互換） |
| 抽選アルゴリズム | `processMatch` の分類ロジックが3層になる。`priorityPlayerIds` が空なら従来通りの動作 | 中（既存テストで動作不変を確認） |
| セッション再抽選 | 初回の `priorityPlayerIds` を引き継ぐ。リクエストで上書き指定も可能 | 中（従来は優先選手概念がなかった） |
| `LotteryExecution` エンティティ | カラム追加のみ。既存コードは影響なし | 低 |
| `LotteryResults.jsx` | 無影響 | なし |
| `WaitlistStatus.jsx` / `OfferResponse.jsx` | 無影響 | なし |
| キャンセル待ち繰り上げ（`WaitlistPromotionService`） | キャンセル待ち順は抽選時点で `waitlistOrder` が確定するため、以降の繰り上げ処理には影響なし | 低 |
| 伝助書き戻し（`DensukeWriteService`） | 抽選結果の当選／キャンセル待ちは従来通りのステータスで保存されるため無影響 | 低 |
| 通知送信 | 当選・落選・キャンセル待ちの判定ロジックは不変のため無影響 | 低 |

### 4.2 破壊的変更

- **なし**（API は新規フィールド追加のみ、DB はカラム追加のみ、既存UI機能への変更なし）
- `priorityPlayerIds` を指定しないリクエストは、すべて従来と同じ動作になる

### 4.3 共通コンポーネント・ユーティリティへの影響

- `PlayerSortHelper.playerComparator()` を `getMonthlyApplicants` で流用（読み取り専用なので影響なし）
- `AdminScopeValidator.validateScope` を `previewLottery` で追加呼び出し（既存動作を厳格化する方向。ADMINが他団体を preview してもエラーにならなかったのが、エラーになる）

---

## 5. 設計判断の根拠

### 5.1 DBに専用テーブルではなく `LotteryExecution` へのJSON列として保存する理由
- 確定後の再利用（再抽選時のみ）に限定されるため、正規化テーブルにするほどの関連操作がない
- ユーザー要件「確定後は使わない」「履歴として確認できれば良い」に対してオーバーエンジニアリングを避ける
- 検索条件に使う予定がないので `TEXT(JSON)` で十分

### 5.2 優先順位を「管理者指定 > 連続落選救済 > 一般」とする理由
- 管理者が明示的に選んだ意思を最優先するのが運営意図に沿う
- 連続落選救済は「システムによる自動救済」なので、運営者の意図より下位が妥当
- 優先選手同士で定員超過時は抽選（ランダム性を維持）することで公平性と運営意図のバランスを取る

### 5.3 `sessionStorage` を使う理由
- ユーザー要件「再プレビューのときくらいは保持してほしい／確定後は不要」に合致
- `localStorage` だと別端末・別セッションを跨いで永続化されてしまい、チェック漏れ・古いデータの残存リスクがある
- React state のみだと画面遷移・リロードで消え、再プレビュー時に再選択が必要で不便

### 5.4 バリデーションを厳格（エラー）にする理由
- 「希望を出していない選手」が混入した場合、黙って無視するとUI上は優先選手に見えるのに結果には反映されないという不整合が生じる
- 他団体選手は認可違反なので403で明示的に拒否すべき（黙って無視するとセキュリティログに痕跡が残らない）

### 5.5 `previewLottery` に `AdminScopeValidator` を追加する理由
- 監査レポートで指摘されていた一貫性の欠如（execute/confirm にはあり、preview にはない）をこの改修に便乗して解消する
- 優先選手指定リクエストでは他団体選手の検証が必要になるため、スコープ検証が前提として必須になる

---

## 6. 未確定事項

- 参加希望者が極めて多い場合（数百人規模）の画面パフォーマンスは現時点で懸念なし。もし問題が出れば後日ページング等を検討。
