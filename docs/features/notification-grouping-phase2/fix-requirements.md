---
status: completed
audit_source: バグ報告（ユーザー直接報告）
selected_items: [1, 2, 3]
---

# 空き枠通知セッション統合 + オファー承諾管理者通知修正 改修要件定義書

## 1. 改修概要

### 対象機能
- 空き枠のお知らせ（SAME_DAY_VACANCY）LINE通知
- オファー承諾時の管理者LINE通知

### 改修の背景
1. **空き枠のお知らせ**: `SameDayVacancyScheduler`（0:00）および `expireOfferedForSameDayConfirmation`（12:00）で、セッション内の各試合ごとに個別のFlex Messageを送信している。3試合空きがあると1人に3通届く。
2. **オファー承諾管理者通知**: `respondToOfferAll(accept=true)` で全試合を一括承諾した場合、管理者通知が一切送信されていない（バグ）。

### 改修スコープ
1. 空き枠通知のセッション単位統合（オファー用Flex Messageと同形式）
2. 「全試合参加」一括処理の新設（統合Flex Messageのボタン対応）
3. `respondToOfferAll(accept=true)` の管理者通知バグ修正

## 2. 改修内容

### 2.1 空き枠通知のセッション単位統合

**現状の問題:**
- `SameDayVacancyScheduler.processSession()` (L81-99): 試合ごとにループし、`sendSameDayVacancyNotification` + `sendAdminVacancyNotification` を個別呼び出し
- `WaitlistPromotionService.expireOfferedForSameDayConfirmation()` (L922-934): 影響試合ごとにループし、`sendSameDayVacancyNotification` を個別呼び出し
- 結果: 同一セッションで3試合空きがあると、選手に3通・管理者に3通の個別通知

**修正方針:**
- `LineNotificationService` に統合版メソッド `sendConsolidatedSameDayVacancyNotification` を新設
- Flex Messageは `buildConsolidatedOfferFlex` と同じ構造:
  - ヘッダー: "空き枠のお知らせ"（オレンジ #FF9800）
  - ボディ: セッション名 + 各試合の空き枠数
  - フッター: 試合ごとの「参加する」ボタン + 「全試合参加」ボタン（2試合以上の場合）
- 管理者向けも統合版 `sendConsolidatedAdminVacancyNotification` を新設（ボタンなし）
- `SameDayVacancyScheduler` でループ後にまとめて統合版を呼ぶ
- `expireOfferedForSameDayConfirmation` でも同様にまとめて統合版を呼ぶ
- 単一試合用の `sendSameDayVacancyNotification` は `handleSameDayCancelAndRecruit`（当日キャンセル=常に1試合）で引き続き使用するためそのまま残す

**修正後のあるべき姿:**
- 同一セッションの空き枠情報が1通のFlex Messageにまとまる
- 各試合に個別の「参加する」ボタンがあり、2試合以上なら「全試合参加」ボタンも表示
- 管理者にもセッション単位で1通にまとまった通知が届く

### 2.2 「全試合参加」一括処理の新設

**現状の問題:**
- 統合Flex Messageに「全試合参加」ボタンを配置するが、対応するpostbackハンドラーが存在しない
- 現在の `handleSameDayJoin` は1試合ずつの処理

**修正方針:**
- `LineWebhookController` に `same_day_join_all` アクションを追加
  - `CONFIRMABLE_ACTIONS` に追加（確認ダイアログ対象）
  - 確認ダイアログのメッセージ対応を追加
  - `handleSameDayJoinAll` ハンドラーを新設
- `WaitlistPromotionService` に `handleSameDayJoinAll(Long sessionId, Long playerId)` を新設
  - セッション内の全空き試合を取得
  - 各試合に参加登録（空き枠チェック + 既存WONチェック含む）
  - 参加通知 + 枠状況通知を送信

**修正後のあるべき姿:**
- LINEの「全試合参加」ボタンを押すと、確認ダイアログ後に空き枠のある全試合に一括参加
- 参加できた試合数がリプライメッセージで返る

### 2.3 `respondToOfferAll(accept=true)` 管理者通知バグ修正

**現状の問題:**
- `respondToOfferAll()` (L587-616) の accept=true 分岐に管理者通知の処理が一切ない
- 一括承諾してもアプリ内通知もLINE通知も管理者に届かない
- 対照的に、accept=false（一括辞退）の分岐 (L679-680) では `sendBatchedAdminWaitlistNotifications` が呼ばれている

**修正方針:**
- accept=true 分岐のループ内で `AdminWaitlistNotificationData` を蓄積
- ループ後に `sendBatchedAdminWaitlistNotifications(notificationDataList, session)` を呼ぶ
- 辞退側と対称的な実装

**修正後のあるべき姿:**
- 一括承諾時に管理者へLINE通知が届く（全承諾試合分が1通にまとまる）
- 単体承諾 `respondToOffer(accept=true)` の既存動作（1試合分の管理者通知）は変更なし

## 3. 技術設計

### 3.1 API変更
なし

### 3.2 DB変更
なし

### 3.3 フロントエンド変更
なし

### 3.4 バックエンド変更

#### LineNotificationService.java
- `sendConsolidatedSameDayVacancyNotification(PracticeSession session, Map<Integer, Integer> vacanciesByMatch, Long cancelledPlayerId)` を新設
  - 送信先: 団体全メンバー（全対象試合のWON参加者とキャンセル者を除外）
  - Flex Message: `buildConsolidatedSameDayVacancyFlex` で構築
- `sendConsolidatedAdminVacancyNotification(PracticeSession session, Map<Integer, Integer> vacanciesByMatch)` を新設
  - 送信先: 該当団体ADMIN + 全SUPER_ADMIN
  - Flex Message: ボタンなし版
- `buildConsolidatedSameDayVacancyFlex(String sessionLabel, Map<Integer, Integer> vacanciesByMatch, Long sessionId, boolean includeButtons)` を新設
  - ヘッダー: "空き枠のお知らせ"（#FF9800）
  - ボディ: セッション名 + 各試合の空き枠数
  - フッター（includeButtons=true時）: 試合ごと「参加する」ボタン（オレンジ） + 「全試合参加」ボタン（青、2試合以上） 
- `buildVacancyJoinButtons(Map<Integer, Integer> vacanciesByMatch, Long sessionId)` を新設
  - 個別: `action=same_day_join&sessionId=X&matchNumber=N`
  - 全試合: `action=same_day_join_all&sessionId=X`

#### SameDayVacancyScheduler.java
- `processSession()` を改修
  - ループ内: 空き試合の matchNumber → vacancies を `Map<Integer, Integer>` に蓄積
  - ループ後: `sendConsolidatedSameDayVacancyNotification` + `sendConsolidatedAdminVacancyNotification` を1回呼ぶ

#### WaitlistPromotionService.java
- `expireOfferedForSameDayConfirmation()` (L921-934) を改修
  - ループ内: 空き試合の matchNumber → vacancies を蓄積
  - ループ後: `sendConsolidatedSameDayVacancyNotification` を1回呼ぶ
- `handleSameDayJoinAll(Long sessionId, Long playerId)` を新設
  - セッション内の全試合を取得
  - 各試合で空き枠 + 未WONチェックし、参加可能な試合にWON登録
  - 参加通知 + 枠状況通知を送信
- `respondToOfferAll()` accept=true 分岐を修正
  - ループ内: `AdminWaitlistNotificationData` を蓄積
  - ループ後: `sendBatchedAdminWaitlistNotifications(notificationDataList, session)` を呼ぶ

#### LineWebhookController.java
- `CONFIRMABLE_ACTIONS` に `"same_day_join_all"` を追加
- 確認ダイアログ分岐にて `same_day_join_all` のセッションラベル取得・メッセージ生成を追加
- `executeOriginalAction` の switch に `"same_day_join_all"` を追加
- `handleSameDayJoinAll(channel, replyToken, params, playerId)` メソッドを新設

## 4. 影響範囲

### 影響を受ける既存機能
- `SameDayVacancyScheduler`: 通知送信ロジックのみ変更（空き枠検出ロジックは変更なし）
- `expireOfferedForSameDayConfirmation`: 空き枠通知部分のみ変更（期限切れ処理本体は変更なし）
- `respondToOfferAll(accept=true)`: 管理者通知追加のみ（承諾処理本体は変更なし）
- `handleSameDayCancelAndRecruit`: **変更なし**（常に1試合のため従来の個別版を継続使用）

### 破壊的変更の有無
- なし
- 既存の `sendSameDayVacancyNotification`（単一試合版）はそのまま残す
- 既存の `sendAdminVacancyNotification`（単一試合版）もそのまま残す
- postbackアクション `same_day_join` は既存のまま（個別ボタン用）

### テスト影響
- `SameDayVacancySchedulerTest`: verify対象を統合版メソッドに変更
- `WaitlistPromotionServiceTest`: `expireOfferedForSameDayConfirmation` 関連テストのverify更新、`respondToOfferAll` accept=trueの管理者通知テスト追加、`handleSameDayJoinAll` のテスト追加
- `LineWebhookControllerTest`: `same_day_join_all` postback処理のテスト追加
- `LineNotificationServiceTest`: 統合版空き枠通知のテスト追加

## 5. 設計判断の根拠

### なぜオファー用Flex Messageと同じ形式にするか
- ユーザーからの指定。UIの一貫性が保たれ、利用者が操作に迷わない。
- `buildConsolidatedOfferFlex` のパターン（ヘッダー + ボディ + 試合別ボタン + 全試合ボタン）が実績あり。

### なぜ `sendSameDayVacancyNotification`（単一試合版）を残すか
- `handleSameDayCancelAndRecruit` は当日キャンセル（1試合単位の操作）で使用。常に1試合なので統合不要。
- 将来的に別の経路から単一試合の空き枠通知が必要になる可能性もある。

### なぜ `respondToOffer`（単体承諾）は改修しないか
- 単体承諾は1試合のみの操作 → 管理者通知も1試合分で自然。
- 既に `sendBatchedAdminWaitlistNotifications(List.of(notifData), session)` で送信済み。
