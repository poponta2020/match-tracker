---
status: completed
---
# 抽選管理画面 実装手順書

## 実装タスク

### タスク1: バックエンド — previewLottery() メソッド追加
- [x] 完了
- **概要:** 抽選アルゴリズムを実行するがDBに保存しない `previewLottery()` メソッドを `LotteryService` に追加する。既存の `executeLottery()` の抽選ロジック（セッション取得→試合ごとの抽選処理）を再利用し、結果を `List<LotteryResultDto>` として返す。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryService.java` — `previewLottery(year, month, orgId)` メソッド追加。既存の `processSession`/`processMatch` のロジックを流用するが、DB保存（`saveAll`）を行わない。`@Transactional(readOnly = true)` で実行。
- **依存タスク:** なし

### タスク2: バックエンド — preview エンドポイント追加 + confirm 権限修正 + キャンセル待ち通知
- [x] 完了
- **概要:** `/lottery/preview` と `/lottery/notify-waitlisted` エンドポイントを新設し、既存の `/lottery/confirm` の権限を ADMIN にも拡張してスコープ検証を追加する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java`
    - 新規: `POST /preview` — `previewLottery()` を呼び出す。ADMIN/SUPER_ADMIN。締め切り前チェック・重複チェックあり。
    - 新規: `POST /notify-waitlisted` — WAITLISTED の参加者のみにアプリ内通知 + LINE通知を送信。ADMIN/SUPER_ADMIN。
    - 修正: `POST /confirm` — `@RequireRole` を `{Role.SUPER_ADMIN, Role.ADMIN}` に変更。ADMIN はリクエストの `organizationId` を自団体に強制。
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/NotificationService.java` — キャンセル待ちのみの通知生成メソッド追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java` — キャンセル待ちのみのLINE通知送信メソッド追加（必要に応じて）
- **依存タスク:** タスク1

### タスク3: フロントエンド — API クライアント更新
- [x] 完了
- **概要:** `lottery.js` に `preview` と `notifyWaitlisted` メソッドを追加する（`confirm` は既に追加済み）。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/api/lottery.js`
    - `preview: (year, month, organizationId) => apiClient.post('/lottery/preview', { year, month, organizationId })` を追加
    - `notifyWaitlisted: (year, month, organizationId) => apiClient.post('/lottery/notify-waitlisted', { year, month, organizationId })` を追加
- **依存タスク:** なし

### タスク4: フロントエンド — 抽選管理画面作成
- [ ] 完了
- **概要:** 抽選管理画面コンポーネントを新規作成する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/lottery/LotteryManagement.jsx` — 新規作成
    - ヘッダー: タイトル「抽選管理」+ 「システム設定」リンクボタン
    - 年月セレクター（デフォルト翌月）
    - 「抽選実行」ボタン → `lotteryAPI.preview()` を呼び出しプレビュー表示
    - プレビュー: セッション別・試合別の当選/キャンセル待ち一覧
    - 「確定」ボタン → `lotteryAPI.confirm()` を呼び出しDB保存+伝助書き戻し
    - 確定後: 「全員に通知送信」ボタン + 「キャンセル待ちのみ通知送信」ボタン
    - 既存の PracticeList.jsx の結果表示部を参考にしたUIデザイン
- **依存タスク:** タスク3

### タスク5: フロントエンド — ルーティング・ナビゲーション変更
- [ ] 完了
- **概要:** App.jsx にルート追加、SettingsPage のグリッドを変更する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/App.jsx` — `/admin/lottery` ルート追加（RoleProtectedPage、requiredRole="ADMIN"）+ LotteryManagement の import
  - `karuta-tracker-ui/src/pages/SettingsPage.jsx` — グリッドの「システム設定」を「抽選管理」に置き換え（path: '/admin/lottery'、icon は Dices or similar）
- **依存タスク:** タスク4

### タスク6: ドキュメント更新
- [ ] 完了
- **概要:** 仕様書・画面一覧・設計書を更新する。
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` — 抽選管理画面の仕様を追加
  - `docs/SCREEN_LIST.md` — 画面一覧に抽選管理画面を追加
  - `docs/DESIGN.md` — 設計書に preview/confirm API・LotteryManagement コンポーネントを追加
- **依存タスク:** タスク5

## 実装順序
1. タスク1（依存なし）+ タスク3（依存なし）— 並行実施可能
2. タスク2（タスク1に依存）
3. タスク4（タスク3に依存）
4. タスク5（タスク4に依存）
5. タスク6（タスク5に依存）
