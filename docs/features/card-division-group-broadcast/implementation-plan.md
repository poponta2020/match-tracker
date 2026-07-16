---
status: completed
---
# 札分けの全体LINE一斉配信（card-division-group-broadcast）実装手順書

要件: [requirements.md](./requirements.md)。テストファースト。DBスキーマ変更は entity と同一PR＋**本番 Render PostgreSQL 適用必須**（CLAUDE.md 最重要）。札組生成（`CardDivisionTextService` / `cardRules.js`）と個人通知経路は**不変**。

主要領域: `karuta-tracker`（entity/service/scheduler/controller/repository/webhook）・`database`（migration）・`karuta-tracker-ui`（管理画面）・`docs`。

## 実装タスク

### タスク1: スキーマ基盤（テーブル・カラム・ChannelType.GROUP・リポジトリ）
- [x] 完了
- **目的:** 全体配信のデータモデルを用意し、`GROUP` 種別で個人割当プールと分離する。
- **対応AC:** AC-2, AC-6, AC-13
- **主な変更領域:** `entity/ChannelType.java`（+GROUP）／新 `entity/LineBroadcastGroup.java`（`organization_id`・name・enabled・想定受信数nullable）／`entity/LineChannel.java`（+`broadcast_group_id` nullable・+`line_group_id` nullable）／新 `entity/LineBroadcastSend.java`（broadcast_group_id・session_id・line_channel_id・recipient_count・status[RESERVED/SUCCESS/FAILED/SKIPPED]・error・sent_at）／新リポジトリ3種／`database/add_card_division_group_broadcast.sql`。
- **依存タスク:** なし（他タスクの前提＝Wave 1 の唯一のブロッカー）
- **必要なテスト:** リポジトリのdedupe用 `INSERT ... ON CONFLICT DO NOTHING`（部分ユニーク index `(broadcast_group_id, session_id) WHERE status IN ('SUCCESS','RESERVED')`）が2回目に0行を返す統合テスト。`DataInitializer` に index 存在検証（既存 `idx_lml_dedupe_daily_unique` 検証に倣う）。**AC-2 スイープ**: `assignChannel` の type-aware 選択に加え、status のみ（型無視）の `findByStatus`/`findFirstByStatusOrderByIdAsc` を**個人割当に使う経路が無いこと**を確認（現状 `assignChannel` は type-aware で安全。`LineChannelReclaimScheduler` は player 割当済みチャネルのみ AVAILABLE に戻す＝GROUP botは非割当ゆえ回収対象外＝安全）。
- **完了条件:** entity＋migration＋repo が揃い BEテスト green。**本番introspectで `line_channels.channel_type` の CHECK制約有無を確認**し、あれば `GROUP` 追加で張り直し（[[reference_prod_matches_no_check_constraint]] の教訓＝テンプレ古さで有効値を潰さない）。本番適用まで実施。
- **担当:** main（スキーマ・本番適用）
- **対応Issue:** #1075

### タスク2: グループ人数取得API（LineMessagingService）
- [x] 完了
- **目的:** ローテのゲート `当月送信数 + 想定受信数 ≤ 200` に使う実グループ人数を取得する。
- **対応AC:** AC-4, AC-5
- **主な変更領域:** `service/LineMessagingService.java` に `getGroupMemberCount(token, groupId)`（`GET /v2/bot/group/{groupId}/members/count`。失敗時 -1、既存 `getMonthlyMessageConsumption` に倣う）。
- **依存タスク:** なし（既存ファイルにメソッド追加のみ・タスク1と非重複）
- **必要なテスト:** レスポンス `{"count": N}` のパース・失敗時 -1 のユニット（RestTemplateモック、既存 messaging テスト様式）。
- **完了条件:** テスト green。
- **担当:** main
- **対応Issue:** #1076

### タスク3: ローテ＋全体配信サービス（核）
- [x] 完了
- **目的:** 1配信グループ×1セッションを、残枠のあるbot1体で原子的に一度だけグループ配信する。
- **対応AC:** AC-1, AC-4, AC-5, AC-6, AC-8, AC-9
- **主な変更領域:** 新 `service/CardDivisionBroadcastService.java`。
  - 受信者テキスト = `cardDivisionTextService.buildTextForSession(session)`（AC-1・団体別セッションのみ AC-8）
  - bot選択（AC-4）: 当該グループの `GROUP` 種別・enabled・`line_group_id` 捕捉済み・`当月送信数 + 想定受信数 ≤ 200` のbotから規定方針（残枠のある先頭＝使い切ってから次へ）で1体。全滅なら「選択なし」→ SKIPPED記録＋アラート（AC-9）
  - 送信（AC-6）: `line_broadcast_send` に `tryAcquireBroadcastRight`（INSERT ON CONFLICT）→ `sendPushMessage(token, line_group_id, text)` → success/failed 反映
  - **クラッシュ回復（必須・advisor指摘）**: 個人版 `releaseStaleReservations` 相当の `releaseStaleBroadcastReservations`（RESERVED タイムアウト→FAILED）を追加し、送信権確保後に落ちた回を同一ウィンドウ内で再試行可能にする（残留RESERVEDが「送信済み」と誤認され永久未送信になるのを防ぐ）。**解放猶予をウィンドウ幅（30分前=最大30分／8:00=最大4h）と整合させる**。スケジューラ（T4）冒頭で呼ぶか短周期 `@Scheduled` に載せる。
  - 消費即時反映（AC-5）: 送信成功で `line_channels.monthly_message_count += 想定受信数`（毎時同期が後で実測へ補正）
  - 読み取り用 `getRotationStatus(group)`（次bot・各bot残枠・当月残り可能回数）＝タスク6が消費
- **依存タスク:** タスク1, タスク2
- **必要なテスト:** 残枠境界の選択決定性（AC-4）／1体のみ消費＋即時加算（AC-5）／同一(group,session)二度目は送信されない（AC-6）／**RESERVED残留を解放後、同一(group,session)を再送できる**／全枯渇でSKIPPED＋非送信（AC-9）／org跨ぎで他団体セッションを混ぜない（AC-8）。LINE送信はモック。
- **完了条件:** 上記テスト green。
- **担当:** main（ローテ核・冪等）
- **対応Issue:** #1077

### タスク4: 配信スケジューラ（30分前・8:00フォールバック・未配信残し）
- [ ] 完了
- **目的:** 各配信グループの当日セッションを、30分前（無ければ8:00）ウィンドウで1回配信し、逃したら未配信で残す。
- **対応AC:** AC-7
- **主な変更領域:** 新 `scheduler/CardDivisionBroadcastScheduler.java`（`@Scheduled` 数分・`zone="Asia/Tokyo"`・`processBroadcasts(today, now)` を引数化）。1試合目開始解決は個人版 `resolveFirstMatchStartTime` 相当を再利用/複製。トリガー = `開始-30分`、無ければ `8:00`。ウィンドウ = 30分前トリガー→`[開始-30分, 開始)`／8:00→`[8:00, 12:00]`。ウィンドウ超過は配信せず（未配信のまま）。有効な配信グループ×当日該当セッションを列挙しタスク3を呼ぶ。
- **依存タスク:** タスク3
- **必要なテスト:** 30分前ウィンドウ内/外の判定・8:00フォールバック発火・両情報源なし時に8:00が使われる・ウィンドウ超過で非送信（today/now注入の決定論テスト）。
- **完了条件:** テスト green。
- **担当:** main（scheduler・通知系＝高リスクパス）
- **対応Issue:** #1078

### タスク5: join Webhook でグループID捕捉
- [x] 完了
- **目的:** botが全体グループに招待された時、そのbotにグループIDを保存する。
- **対応AC:** AC-3
- **主な変更領域:** `controller/LineWebhookController.java` に `join`（必要なら `memberJoined`）分岐を追加 → `source.groupId` を発火チャネルの `line_channels.line_group_id` に保存（署名検証は既存経路のまま）。`leave`/`memberLeft` は `line_group_id` クリア（配信不能検知）。
- **依存タスク:** タスク1（`line_group_id` カラム）
- **必要なテスト:** `join` イベントで当該チャネルに groupId 保存・`leave` でクリア（既存 `LineWebhookControllerTest` 様式）。
- **完了条件:** テスト green。
- **担当:** main（webhook・認証隣接）
- **対応Issue:** #1079

### タスク6: 管理API（グループ/ bot割当/ 状況/ ログ）
- [ ] 完了
- **目的:** 管理者が配信グループ登録・bot群割当・稼働状況/ログ確認・枯渇把握を行える。
- **対応AC:** AC-9, AC-10
- **主な変更領域:** 新 `controller/LineBroadcastAdminController.java`（`/api/admin/line/broadcast`）＋ `service/LineBroadcastAdminService`（または既存Serviceに集約）＋DTO。エンドポイント（`@RequireRole(ADMIN+)`・`AdminScopeValidator` で org スコープ）:
  - `GET /groups`（自団体/全団体の配信グループ＋状態）／`POST /groups`（作成）／`PUT /groups/{id}`（有効化・想定受信数等）
  - `POST /groups/{id}/bots`（未使用PLAYERチャネルを`GROUP`に付替え＋割当）／`DELETE /groups/{id}/bots/{channelId}`（解除）
  - `GET /groups/{id}/status`（各bot: 当月送信数・残枠・捕捉済みグループID・セットアップ状態／次配信bot／当月残り可能回数＝タスク3の `getRotationStatus`）
  - `GET /groups/{id}/logs`（`line_broadcast_send` 一覧・枯渇アラート状態 AC-9）
- **依存タスク:** タスク1, タスク3
- **必要なテスト:** 各エンドポイントの正常系＋org スコープ拒否（ADMINは他団体不可）。bot割当で `channel_type` が `GROUP` に変わり個人割当対象外になること。
- **完了条件:** テスト green・`@RequireRole` 付与済み。
- **担当:** main（認可・スキーマ付替え）
- **対応Issue:** #1080

### タスク7: 管理画面 FE
- [ ] 完了
- **目的:** タスク6のAPIを、既存 `/admin/line` 系UIパターンで可視化・操作する。
- **対応AC:** AC-11
- **主な変更領域:** 新 `pages/line/CardDivisionBroadcastAdmin.jsx`＋`api/lineBroadcast.js`（または `api/line.js` 拡張）＋`App.jsx` ルート（`/admin/line/broadcast`・ADMIN+）＋`SettingsPage.jsx` 導線＋`docs/SCREEN_LIST.md`。グループ一覧/登録・bot割当・残枠バー・次配信bot強調・当月残り回数・配信ログ・枯渇アラート・未参加(グループID未捕捉)/セットアップ未完バッジ・運用手順ガイダンス。
- **依存タスク:** タスク6（API契約）
- **必要なテスト:** Vitest（状態表示・割当操作のレンダリング）。
- **完了条件:** テスト green・lint 0エラー。
- **担当:** task-implementer 委譲可（純FE・API契約確定後）
- **対応Issue:** #1081

### タスク8: 北大かるた会の初期セットアップ（bot 10体割当）＋運用手順
- [ ] 完了
- **目的:** 今回対象の北海道大学かるた会（**org_id=2**）の配信グループを作成し、未使用 `PLAYER/AVAILABLE`（在庫47体）から**10体を `GROUP` に付替え・割当**する。わすら(org 1)は将来同UIから追加（コード改修不要）。
- **対応AC:** AC-13（本番適用）
- **主な変更領域:** 管理UI/APIでの登録（タスク6/7）または seed SQL（`database/`）で org 2 の `line_broadcast_group` 作成＋10チャネルを `GROUP`＋`broadcast_group_id` 設定 → **本番適用**。あわせて**人手運用手順**を要件/画面に明示: ①各botのLINEチャネルで「グループ参加許可」＋Webhook有効化（既存 `migrate-webhook-urls` 流用）、②全体グループへ10体を招待（→ `join` でグループID自動捕捉）。
- **依存タスク:** タスク5, タスク6（UI/API経由の場合）／タスク1（seed直の場合）
- **完了条件:** org 2 の配信グループ＋10体割当が本番に存在。招待後にグループID捕捉済みになることを管理画面で確認（人手ステップは運用者）。
- **担当:** main（本番データ・運用手順）
- **対応Issue:** #1082

## 実装順序（Wave = 並行実装できるタスクの組）
- **Wave 1:** タスク1（スキーマ基盤・ブロッカー）／タスク2（messaging メソッド追加・非重複） … 並行可
- **Wave 2:** タスク3（配信サービス・要 T1+T2）／タスク5（join webhook・要 T1・別ファイル） … 並行可
- **Wave 3:** タスク4（scheduler・要 T3）／タスク6（管理API・要 T1+T3・別ファイル） … 並行可
- **Wave 4:** タスク7（FE・要 T6）
- **Wave 5:** タスク8（北大初期セットアップ・本番データ・要 T5+T6）

> スキーマ(T1)・scheduler(T4)・webhook(T5)・認可/本番付替え(T6/T8) は main 直（高リスクパス）。純FE(T7)のみ task-implementer 委譲可。
