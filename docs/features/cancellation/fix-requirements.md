---
status: completed
audit_source: 会話内レポート
selected_items: [1, 2, 3, 4, 5, 6]
---

# キャンセル周りの一連の処理 改修要件定義書

## 1. 改修概要

- **対象機能:** キャンセル実行、繰り上げオファー通知・応答、オファー期限切れ自動処理、管理者手動編集
- **改修の背景:** `/audit-feature` によるキャンセル周りの監査で、期限切れ後のオファー応答受付、タイムゾーン未指定によるデプロイ環境依存、フロントエンドの期限表示不足、管理者編集時の繰り上げフロー未発動、過去日キャンセルのバックエンドバリデーション不足、短期限オファーへの注意喚起不足が検出された。
- **改修スコープ:** 全6項目を対応

## 2. 改修内容

### 2.1 respondToOffer に offerDeadline 超過チェックを追加

**現状の問題:** `WaitlistPromotionService.respondToOffer()` はステータスが `OFFERED` であれば応答を受け付けるが、`offerDeadline` を過ぎているかチェックしていない。スケジューラは5分間隔のため、期限切れ後〜スケジューラ実行前の最大5分間に応答が通ってしまう。

**修正方針:**
- `WaitlistPromotionService.respondToOffer()` で `OFFERED` ステータスチェック後に `offerDeadline < now(JST)` を判定
- 期限超過の場合は `IllegalStateException`（「応答期限が過ぎています」）をスロー
- LINE Webhook 側は既存の try-catch でエラーメッセージが返されるため追加対応不要

**修正後のあるべき姿:** 期限切れ後は Web・LINE どちらの経路からも応答が拒否される。

### 2.2 タイムゾーンの明示（全ファイル対応）

**現状の問題:** プロジェクト全体で `LocalDate.now()` / `LocalDateTime.now()` がタイムゾーン指定なしで使用されている（約86箇所）。Render.com（UTC）にデプロイすると日本時間と最大9時間のズレが生じる。`GoogleCalendarSyncService` のみ `ZoneId.of("Asia/Tokyo")` を使用。

**修正方針:**
- `util/JstDateTimeUtil.java` ユーティリティクラスを新設し、JST定数 + `now()` / `today()` 静的メソッドを提供
- プロジェクト全体の `LocalDate.now()` → `JstDateTimeUtil.today()`、`LocalDateTime.now()` → `JstDateTimeUtil.now()` に置換
- 対象カテゴリ:
  - ビジネスロジック（期限判定、日付比較、スケジューリング）: 約20箇所
  - 状態変更タイムスタンプ（cancelledAt, offeredAt, respondedAt 等）: 約13箇所
  - Entity の @PrePersist / @PreUpdate（createdAt, updatedAt）: 約47箇所
  - ErrorResponse: 2箇所
  - GoogleCalendarSyncService: 既存の `ZoneId.of("Asia/Tokyo")` を `JstDateTimeUtil` に統一

**既存データとの整合性:** 既存データの移行は行わない。今後のデータからJSTタイムスタンプとなる。

### 2.3 OfferResponse 画面に期限表示と期限切れ判定を追加

**現状の問題:** `OfferResponse.jsx` は `participantId` のみをURLパラメータで受け取り、期限情報を表示せず、期限切れかどうかの事前チェックもない。

**修正方針:**
- 新規 API `GET /api/lottery/offer-detail/{participantId}` を追加（認可チェック付き: PLAYERは自分のみ、ADMIN+は全員）
- `OfferResponse.jsx` で画面表示時にオファー詳細を取得し、セッション日付・試合番号・応答期限を表示
- クライアント側で `offerDeadline < now` なら「応答期限が過ぎています」表示を出し、応答ボタンを無効化
- ステータスが `OFFERED` でない場合は「このオファーは処理済みです」表示

### 2.4 editParticipants で CANCELLED 変更時に繰り上げフローを発動

**現状の問題:** 管理者が `editParticipants` でステータスを WON → CANCELLED に変更しても、繰り上げフローが発動しない。

**修正方針:**
- `LotteryController.editParticipants()` のステータス変更処理で、旧ステータスが `WON` かつ新ステータスが `CANCELLED` の場合、`waitlistPromotionService.promoteNextWaitlisted()` を呼び出す
- `LotteryDeadlineHelper.isToday()` による当日チェックも適用する（当日変更なら繰り上げしない）

### 2.5 バックエンドに過去日キャンセル防止バリデーション追加

**現状の問題:** 過去の練習日のキャンセルを防ぐチェックがフロントエンドのみ。API を直接叩けば過去の練習のキャンセルが可能。

**修正方針:**
- `LotteryController.cancelParticipation()` でセッション日付が過去（`sessionDate < today(JST)`）かどうかをチェック
- PLAYER ロールの場合は `IllegalStateException`（「過去の練習のキャンセルはできません」）をスロー
- ADMIN / SUPER_ADMIN はデータ修正目的で過去日キャンセルも可能とする

### 2.6 短時間期限のオファー通知に注意喚起を追加

**現状の問題:** 練習前日の遅い時間に繰り上げ通知が来ると、応答期限まで数時間しかなく、気づけない可能性がある。

**修正方針:**
- `LineNotificationService.sendWaitlistOfferNotification()` でオファー期限と現在時刻の差を計算
- 残り時間が12時間未満の場合、LINE Flex Message のボディに注意文言を追加: 「※ 応答期限まで残りわずかです。お早めにご回答ください。」
- アプリ内通知（`NotificationService.createOfferNotification()`）にも同様の注意文言を追加

## 3. 技術設計

### 3.1 API変更

| 変更種別 | エンドポイント | 内容 |
|---------|-------------|------|
| **新規** | `GET /api/lottery/offer-detail/{participantId}` | 個別オファー詳細取得 |
| 挙動強化 | `POST /api/lottery/cancel` | 過去日チェック追加（PLAYERのみ制限） |
| 挙動強化 | `POST /api/lottery/respond-offer` | 期限超過時にエラーレスポンス |
| 挙動強化 | `PUT /api/lottery/admin/edit-participants` | WON→CANCELLED 時に繰り上げ発動 |

**新規API詳細:**
- パス: `GET /api/lottery/offer-detail/{participantId}`
- 認可: `@RequireRole({SUPER_ADMIN, ADMIN, PLAYER})`（PLAYERは自分のレコードのみ）
- レスポンス:
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
- 存在しない場合: 404
- 他人のレコード（PLAYERの場合）: 403

### 3.2 DB変更

なし

### 3.3 フロントエンド変更

| ファイル | 変更内容 |
|---------|---------|
| `karuta-tracker-ui/src/api/lottery.js` | `getOfferDetail(participantId)` メソッド追加 |
| `karuta-tracker-ui/src/pages/lottery/OfferResponse.jsx` | 画面表示時にオファー詳細取得・期限表示・期限切れ判定・処理済み判定 |

### 3.4 バックエンド変更

| ファイル | 変更内容 |
|---------|---------|
| **新規** `util/JstDateTimeUtil.java` | `ZoneId JST` 定数 + `now()` / `today()` 静的メソッド |
| `service/WaitlistPromotionService.java` | respondToOffer に期限チェック追加、`now()` → JST |
| `service/LotteryDeadlineHelper.java` | `now()` / `today()` → JST |
| `scheduler/OfferExpiryScheduler.java` | `now()` → JST |
| `controller/LotteryController.java` | offer-detail エンドポイント追加、editParticipants に繰り上げ追加、cancel に過去日チェック追加 |
| `service/LineNotificationService.java` | 短期限オファーの注意文言追加 |
| `service/NotificationService.java` | 短期限オファーの注意文言追加 |
| `service/GoogleCalendarSyncService.java` | 既存の `ZoneId.of("Asia/Tokyo")` → `JstDateTimeUtil` に統一 |
| `scheduler/LotteryScheduler.java` | `today()` → JST |
| `scheduler/LineReminderScheduler.java` | `today()` → JST |
| `scheduler/LineChannelReclaimScheduler.java` | `now()` → JST |
| `scheduler/DensukeSyncScheduler.java` | `now()` → JST |
| `service/LotteryService.java` | `now()` → JST |
| `service/InviteTokenService.java` | `now()` → JST |
| `service/LineLinkingService.java` | `now()` → JST |
| `service/LineChannelService.java` | `now()` → JST |
| `service/PlayerService.java` | `now()` → JST |
| `service/PracticeSessionService.java` | `today()` → JST |
| `service/PracticeParticipantService.java` | `today()` → JST |
| `service/DensukeImportService.java` | `now()` → JST |
| `controller/HomeController.java` | `now()` → JST |
| `dto/ErrorResponse.java` | `now()` → JST |
| `dto/PlayerProfileDto.java` | `today()` → JST |
| `entity/InviteToken.java` | `now()` → JST |
| `entity/LineLinkingCode.java` | `now()` → JST |
| 全 Entity の @PrePersist/@PreUpdate（15ファイル） | `now()` → JST |

## 4. 影響範囲

### 影響を受ける既存機能
- **抽選機能:** 期限判定・当日判定のタイムゾーン変更、期限超過チェック追加
- **繰り上げ機能:** 応答期限の厳密化、管理者編集からの繰り上げ発動
- **LINE連携:** リンク/リンク解除・リマインダーのタイムスタンプ変更、オファー通知文言変更
- **招待トークン:** 有効期限判定のタイムゾーン変更
- **Googleカレンダー同期:** ユーティリティクラスへの統一（動作変更なし）
- **全Entity:** createdAt/updatedAt のタイムゾーン変更

### 破壊的変更
- **API:** なし（新規追加のみ、既存エンドポイントは挙動強化で後方互換）
- **DB:** なし
- **タイムスタンプ:** 既存データ（UTC想定）と今後のデータ（JST）で不整合が発生するが、ビジネスロジックには影響なし。表示上の軽微な不一致のみ。移行は行わない。

## 5. 設計判断の根拠

| 判断 | 理由 |
|------|------|
| `JstDateTimeUtil` ユーティリティクラスの新設 | サーバーの `TZ` 環境変数に依存せず、コード上でタイムゾーンを明示する。全箇所で `ZoneId.of("Asia/Tokyo")` を書くより保守性が高い |
| 既存データのタイムゾーン移行は行わない | createdAt/updatedAt はビジネスロジックに使われておらず、表示上の軽微な不一致のみ。移行コスト > 実害 |
| offer-detail API の新設（既存API流用ではなく） | `getWaitlistStatus` は全エントリを返すため無駄が多い。個別取得の方が効率的で、認可チェックも明確 |
| 過去日キャンセルは ADMIN+ は許可 | データ修正目的で過去の参加レコードを操作する必要がある |
| 短期限の閾値を12時間に設定 | 24時間の半分。深夜〜早朝に通知が来た場合、翌朝気づいても対応できるラインとして妥当 |
