# 抽選API

> **責務:** 練習参加抽選システム（アルゴリズム・締切・確定・キャンセル補充）の仕様（docs/spec/lottery.md 参照）のAPIエンドポイント詳細
> **関連画面:** `/lottery/results`（LotteryResults.jsx）、`/lottery/waitlist`（WaitlistStatus.jsx）、`/lottery/offer-response`（OfferResponse.jsx）、`/admin/lottery`（LotteryManagement.jsx）、`/admin/settings`（SystemSettings.jsx）
> **主要実装:** `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java`、`karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryService.java`、`karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryQueryService.java`、`karuta-tracker/src/main/java/com/karuta/matchtracker/service/WaitlistPromotionService.java`、`karuta-tracker-ui/src/api/lottery.js`

## エンドポイント一覧

| メソッド | パス | 権限 | 説明 |
|---|---|---|---|
| GET | `/deadline?year=&month=` | 認証不要 | 指定年月の締め切り日時を取得（一般ユーザーの締め切り表示用） |
| POST | `/execute` | ADMIN+ | 手動抽選実行（年月指定、ADMINは自団体のみ） |
| POST | `/preview` | ADMIN+ | 抽選プレビュー（DB保存なし。priorityPlayerIds省略可） |
| POST | `/confirm` | ADMIN+ | 抽選結果確定（ADMINは自団体のみ。priorityPlayerIds省略可） |
| POST | `/re-execute/{sessionId}` | ADMIN+ | セッション再抽選（priorityPlayerIds: null=直近引き継ぎ、[]=クリア） |
| GET | `/monthly-applicants?year=&month=&organizationId=` | ADMIN+ | 対象月・団体の参加希望選手一覧（優先選手指定UI用） |
| GET | `/results?year=&month=` | ALL | 月別抽選結果取得 |
| GET | `/results/{sessionId}` | ALL | セッション別抽選結果 |
| GET | `/my-results?year=&month=&playerId=` | ALL | 自分の抽選結果 |
| POST | `/cancel` | ALL | 当選キャンセル（理由付き・複数対応） |
| POST | `/respond-offer` | ALL | 繰り上げへの応答（participantId, accept） |
| POST | `/respond-offer-all` | ALL | 繰り上げオファー一括応答（sessionId, accept） |
| GET | `/offer-detail/{participantId}` | ALL | 個別オファー詳細取得 |
| GET | `/session-offers/{sessionId}` | ALL | セッション内の自分のOFFERED一覧取得 |
| GET | `/waitlist-status?playerId=` | ALL | キャンセル待ち状況 |
| GET | `/is-confirmed?year=&month=&organizationId=` | ADMIN+ | 指定年月・団体の抽選が確定済みかを返す（ADMINは自団体に強制） |
| GET | `/notify-status?year=&month=&organizationId=` | ADMIN+ | 指定年月・団体の通知が既送信かを返す（重複送信防止の事前確認） |
| POST | `/notify-results` | ADMIN+ | 全員（当選者＋キャンセル待ち）にアプリ内通知 + LINE通知を送信（団体スコープ適用） |
| POST | `/notify-waitlisted` | ADMIN+ | キャンセル待ちのみにアプリ内通知 + LINE通知を送信（団体スコープ適用） |
| POST | `/decline-waitlist` | ALL | キャンセル待ち辞退（セッション単位。後続番号を自動繰り上げ） |
| POST | `/rejoin-waitlist` | ALL | キャンセル待ち復帰（セッション単位。最後尾番号を付与） |
| PUT | `/admin/edit-participants` | ADMIN+ | 管理者による手動編集 |
| GET | `/executions?year=&month=` | ALL | 抽選実行履歴 |
| POST | `/same-day-join` | ALL | 当日先着参加（sessionId, matchNumber, playerId）。先着1名がWON。枠なし時は409 Conflict |

## GET /api/lottery/deadline?year={year}&month={month}
**説明**: 指定年月の締め切り日時を取得（一般ユーザーの締め切り表示用）
**権限**: 認証不要
**レスポンス**:
```json
{
  "deadline": "2026-03-29T00:00:00",
  "noDeadline": false
}
```
締め切りなしモード時: `{ "deadline": null, "noDeadline": true }`

## POST /api/lottery/execute
**説明**: 手動抽選実行（月単位）。「締め切りなし」モード時は締め切り前チェックをスキップ。重複チェック・確定チェックは団体単位で行われる
**権限**: SUPER_ADMIN, ADMIN（ADMINは自団体のみ）
**リクエスト**: `LotteryExecutionRequest`
```json
{
  "year": 2026,
  "month": 3
}
```
**レスポンス**: `LotteryExecution`

## POST /api/lottery/re-execute/{sessionId}
**説明**: セッション再抽選。リクエストボディ省略時は直近実行時の `priorityPlayerIds` を引き継ぐ。`priorityPlayerIds: []` で明示クリア可能。組織スコープを検証し、ADMIN/PLAYER は所属団体のセッション以外を再抽選できない（404 を返す）
**権限**: SUPER_ADMIN, ADMIN
**リクエスト** (任意):
```json
{ "priorityPlayerIds": [10, 20] }
```
**レスポンス**: `LotteryExecution`

## GET /api/lottery/results?year={year}&month={month}
**説明**: 月別抽選結果取得。ADMIN/PLAYER は自分の所属団体のセッションのみが対象（SUPER_ADMIN は全団体）
**権限**: SUPER_ADMIN, ADMIN, PLAYER
**レスポンス**: `List<LotteryResultDto>`

## GET /api/lottery/results/{sessionId}
**説明**: セッション別抽選結果取得。ADMIN/PLAYER は所属団体のセッション以外にアクセスすると 403 を返す（SUPER_ADMIN は全団体）
**権限**: SUPER_ADMIN, ADMIN, PLAYER

## GET /api/lottery/my-results?year={year}&month={month}
**説明**: 自分の抽選結果取得（ログインユーザーの結果のみ）。ADMIN/PLAYER は所属団体のセッションに紐づく結果のみ返す
**権限**: SUPER_ADMIN, ADMIN, PLAYER

## POST /api/lottery/cancel
**説明**: 参加キャンセル（理由付き・複数対応）。PLAYERは自分の参加のみキャンセル可能かつ過去日のキャンセル不可、ADMIN+は他人分・過去日も可。
受け付けるステータスは `WON`（当選後キャンセル）と `PENDING`（抽選前申込のキャンセル）。`PENDING` の場合は繰り上げ・当日補充フローは発動しない（待機者が存在しないため）。
**権限**: SUPER_ADMIN, ADMIN, PLAYER
**リクエスト**: `CancelRequest`
```json
{
  "participantId": 123,
  "participantIds": [123, 456],
  "cancelReason": "HEALTH",
  "cancelReasonDetail": "（OTHERの場合のみ）"
}
```

## POST /api/lottery/respond-offer
**説明**: 繰り上げオファーへの応答。PLAYERは自分のオファーのみ応答可能、ADMIN+は他人分も可。応答期限超過時はエラー。
**権限**: SUPER_ADMIN, ADMIN, PLAYER
**リクエスト**: `OfferResponseRequest`
```json
{
  "participantId": 123,
  "accept": true
}
```

## GET /api/lottery/offer-detail/{participantId}
**説明**: 個別オファー詳細取得。PLAYERは自分のレコードのみ参照可能、ADMIN+は全員参照可能。
**権限**: SUPER_ADMIN, ADMIN, PLAYER
**レスポンス**: `WaitlistStatusDto.WaitlistEntry`
```json
{
  "participantId": 123,
  "sessionId": 45,
  "sessionDate": "2026-04-05",
  "venueName": "市民館",
  "startTime": "13:00",
  "endTime": "17:00",
  "matchNumber": 2,
  "waitlistNumber": 1,
  "status": "OFFERED",
  "offerDeadline": "2026-04-04T23:59:59"
}
```

## POST /api/lottery/respond-offer-all
**説明**: 繰り上げオファー一括応答。同一セッション内の自分の全OFFEREDを一括承諾/辞退する。
**権限**: SUPER_ADMIN, ADMIN, PLAYER
**リクエスト**: `OfferBatchResponseRequest`
```json
{
  "sessionId": 45,
  "accept": true
}
```
**レスポンス**:
```json
{
  "result": "accepted",
  "count": 3
}
```

## GET /api/lottery/session-offers/{sessionId}
**説明**: セッション内の自分のOFFERED一覧取得。ログインユーザーのOFFEREDのみ返す。
**権限**: SUPER_ADMIN, ADMIN, PLAYER
**レスポンス**: `List<WaitlistStatusDto.WaitlistEntry>`
```json
[
  {
    "participantId": 123,
    "sessionId": 45,
    "sessionDate": "2026-04-05",
    "venueName": "市民館",
    "matchNumber": 1,
    "status": "OFFERED",
    "offerDeadline": "2026-04-04T23:59:59"
  },
  {
    "participantId": 124,
    "sessionId": 45,
    "sessionDate": "2026-04-05",
    "venueName": "市民館",
    "matchNumber": 3,
    "status": "OFFERED",
    "offerDeadline": "2026-04-04T23:59:59"
  }
]
```

## GET /api/lottery/waitlist-status
**説明**: キャンセル待ち状況取得（ログインユーザーの状況のみ）
**権限**: SUPER_ADMIN, ADMIN, PLAYER
**レスポンス**: `WaitlistStatusDto`

## GET /api/lottery/is-confirmed?year={year}&month={month}&organizationId={organizationId}
**説明**: 指定年月・団体の抽選が確定済みかどうかを返す（ADMINは自団体に強制）
**権限**: SUPER_ADMIN, ADMIN
**レスポンス**:
```json
{ "confirmed": true }
```

## GET /api/lottery/notify-status?year={year}&month={month}&organizationId={organizationId}
**説明**: 抽選結果通知の送信済みチェック（ADMINは自団体に強制）。対象月・団体の練習セッションIDを引き当て、`LOTTERY_WAITLISTED` / `LOTTERY_ALL_WON` / `LOTTERY_REMAINING_WON` 通知のうち `referenceId` が該当セッションに紐づくレコード数を返す
**権限**: SUPER_ADMIN, ADMIN
**レスポンス**:
```json
{ "sent": true, "sentCount": 24 }
```

## POST /api/lottery/notify-results
**説明**: 抽選結果通知の統合送信（アプリ内通知 + LINE通知を一括送信）。ADMINは自団体に強制
**権限**: SUPER_ADMIN, ADMIN
**リクエスト**:
```json
{ "year": 2026, "month": 4, "organizationId": 1 }
```
**レスポンス**:
```json
{ "inAppCount": 24, "lineSent": 20, "lineFailed": 0, "lineSkipped": 4 }
```

## POST /api/lottery/decline-waitlist
**説明**: キャンセル待ち辞退（セッション単位）。辞退後、後続のキャンセル待ち番号を自動繰り上げ。
**権限**: SUPER_ADMIN, ADMIN, PLAYER（PLAYERは自分のみ。ADMINは自団体セッションのみ — `AdminScopeValidator` で検証、不一致は 403）
**リクエスト**:
```json
{ "sessionId": 100, "playerId": 10 }
```
**レスポンス**:
```json
{ "declinedCount": 2, "message": "2件のキャンセル待ちを辞退しました" }
```

## POST /api/lottery/rejoin-waitlist
**説明**: キャンセル待ち復帰（セッション単位）。復帰時のキャンセル待ち番号は最後尾。
**権限**: SUPER_ADMIN, ADMIN, PLAYER（PLAYERは自分のみ。ADMINは自団体セッションのみ — `AdminScopeValidator` で検証、不一致は 403）
**リクエスト**:
```json
{ "sessionId": 100, "playerId": 10 }
```
**レスポンス**:
```json
{ "rejoinedCount": 2, "message": "キャンセル待ちに復帰しました（2件）" }
```

## PUT /api/lottery/admin/edit-participants
**説明**: 管理者による参加者手動編集。WON→CANCELLED へのステータス変更時は通常キャンセル経路（`/api/lottery/cancel`）の `cancelParticipationSuppressed` に委譲し、当日12:00を境界に通常繰り上げ／当日補充フローへ自動分岐する。
**権限**: SUPER_ADMIN, ADMIN
**リクエスト**: `AdminEditParticipantsRequest`

## POST /api/lottery/confirm
**説明**: 抽選結果を確定し、伝助への一括書き戻しをトリガー。`confirmed_at`/`confirmed_by`/`priority_player_ids`/`seed` を記録。団体単位で確定状態を管理。伝助書き戻しは別トランザクション（REQUIRES_NEW）で実行され、書き戻しが失敗しても抽選確定の DB 更新は維持される
**権限**: SUPER_ADMIN, ADMIN（ADMINは自団体のみ）
**リクエスト**: `LotteryExecutionRequest`
```json
{ "year": 2026, "month": 5, "organizationId": 1, "seed": 12345, "priorityPlayerIds": [10, 20] }
```
**レスポンス**: `ConfirmLotteryResponse`
```json
{
  "execution": { "...": "LotteryExecution の全フィールド" },
  "densukeWriteSucceeded": true,
  "densukeWriteError": null
}
```
- `densukeWriteSucceeded`: 伝助書き戻しが全件成功した場合 true。部分失敗・例外発生時は false
- `densukeWriteError`: 失敗内容の概要（成功時は null）。フロント側ではこの値を確認して伝助同期の再実行を促す

## POST /api/lottery/preview
**説明**: 抽選プレビュー。抽選アルゴリズムを実行するがDBには保存しない。締め切り前チェック・確定済みチェック・AdminScopeValidation・priorityPlayerIdsバリデーションあり
**権限**: SUPER_ADMIN, ADMIN（ADMINは自団体のみ）
**リクエスト**: `LotteryExecutionRequest`
```json
{ "year": 2026, "month": 5, "organizationId": 1, "priorityPlayerIds": [10, 20] }
```
**レスポンス**: `List<LotteryResultDto>`

## GET /api/lottery/monthly-applicants
**説明**: 対象月・団体で参加希望を出している選手一覧を取得（優先選手指定UI用）。重複排除・級順ソート済み
**権限**: SUPER_ADMIN, ADMIN（ADMINは自団体のみ）
**クエリパラメータ**: `year`, `month`, `organizationId`
**レスポンス**: `List<MonthlyApplicantDto>` (`playerId`, `name`)

## POST /api/lottery/notify-waitlisted
**説明**: キャンセル待ち（WAITLISTED）の参加者のみにアプリ内通知 + LINE通知を送信。ADMINは自団体に強制
**権限**: SUPER_ADMIN, ADMIN（ADMINは自団体のみ）
**リクエスト**: `{ year, month, organizationId }`
**レスポンス**: `{ inAppCount, lineSent, lineFailed, lineSkipped }`

## GET /api/lottery/executions?year={year}&month={month}
**説明**: 抽選実行履歴取得。`confirmedAt` フィールドで確定状態を確認可能。ADMIN/PLAYER は自分の所属団体の履歴のみ返す（SUPER_ADMIN は全団体）
**権限**: SUPER_ADMIN, ADMIN, PLAYER

## POST /api/lottery/same-day-join
**説明**: 当日先着参加。12:00以降にキャンセルで空いた枠に先着1名がWONとして参加登録される。枠が既に埋まっている場合は409 Conflict。LINEのpostback `action=same_day_join` またはアプリから呼び出し可能。
**権限**: SUPER_ADMIN, ADMIN, PLAYER
**リクエスト**:
```json
{
  "sessionId": 100,
  "matchNumber": 2,
  "playerId": 10
}
```
**レスポンス（成功）**: 200 OK
```json
{
  "message": "参加が確定しました"
}
```
**レスポンス（枠なし）**: 409 Conflict
```json
{
  "error": "この枠は既に埋まっています"
}
```
