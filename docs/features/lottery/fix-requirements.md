---
status: completed
audit_source: 会話内レポート
selected_items: [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
---

# 抽選機能 改修要件定義書

## 1. 改修概要

### 対象機能
抽選機能（Lottery） — 練習参加者が定員超過時に抽選で参加者を決定するシステム

### 改修の背景
`/audit-feature` による監査レポート（2026-03-26実施）で検出された問題への対応。
重大バグ（lotteryId null）、セキュリティ上の欠陥（認可チェック漏れ）、設計不整合（スケジューラと締め切り設定の乖離）など、優先度の高い問題を含む。

### 改修スコープ

| # | 項目 | 分類 | 優先度 |
|---|------|------|--------|
| 0 | ユーザーID伝播基盤の整備 | 基盤改修 | 高 |
| 1 | lotteryId null バグ修正 | バグ | 高 |
| 2 | /cancel, /respond-offer 認可チェック追加 | セキュリティ | 高 |
| 3 | スケジューラの締め切り判定修正 | バグ | 高 |
| 4 | 抽選結果通知の管理者明示送信化 | 機能変更 | 高 |
| 5 | findMonthlyLoserPlayerIds クエリ修正 | バグ | 中 |
| 6 | buildLotteryResult の Service 層移動 | リファクタ | 中 |
| 7 | /my-results, /waitlist-status の playerId 廃止 | セキュリティ | 中 |
| 8 | 仕様書への未記載仕様の反映 | ドキュメント | 低 |
| 9 | details JSON 構築の ObjectMapper 化 | リファクタ | 低 |
| 10 | venueName フィールドの設定 | バグ | 低 |

---

## 2. 改修内容

### 2.0 ユーザーID伝播基盤の整備

**現状の問題:**
- `RoleCheckInterceptor` はロール（`X-User-Role`）のみ検証し、ユーザーIDは伝播しない
- 全 Controller で `Long currentUserId = 1L` がハードコードされている
- 「誰が操作しているか」をバックエンドで特定できない

**修正方針:**
- フロントエンド `client.js` で `X-User-Id` ヘッダーを `currentPlayer.id` から追加送信
- `RoleCheckInterceptor` で `X-User-Id` ヘッダーを抽出・検証し、`request.setAttribute("currentUserId", id)` でセット
  - `@RequireRole` 付きエンドポイント: `X-User-Id` 必須（未設定なら 401）
  - `@RequireRole` なしエンドポイント: オプショナル（あれば設定、なければスルー）
- 全 Controller の `Long currentUserId = 1L` を `(Long) request.getAttribute("currentUserId")` に置換

**修正後のあるべき姿:**
- 全 API リクエストにユーザーIDが含まれ、バックエンドで操作者を特定できる
- 監査証跡（executedBy 等）に正しいユーザーIDが記録される

### 2.1 lotteryId null バグ修正

**現状の問題:**
- `LotteryService.executeLottery` で `LotteryExecution` を save する前に `execution.getId()` を使用
- `GenerationType.IDENTITY` のため、save 前の `getId()` は null
- 全参加者の `lotteryId` カラムが null になり、抽選実行履歴との紐づけが不可能
- `reExecuteLottery` にも同じ問題あり

**修正方針:**
- `lotteryExecutionRepository.save(execution)` を processSession 呼び出し前に移動
- processSession 完了後に details を update して再 save

**修正後のあるべき姿:**
- 全参加者の `lotteryId` に正しい抽選実行IDが記録される

### 2.2 /cancel, /respond-offer 認可チェック追加

**現状の問題:**
- `POST /cancel` に `@RequireRole` がなく、認証済みユーザーなら誰でも他人の参加をキャンセル可能
- `POST /respond-offer` も同様に、他人の繰り上げオファーに応答可能

**修正方針:**
- 両エンドポイントで participant の `playerId` == `currentUserId` であることを検証
- ADMIN+ ロールは他人分の操作も許可（管理目的）
- 不一致の場合は 403 Forbidden を返す

**修正後のあるべき姿:**
- 一般ユーザーは自分のレコードのみ操作可能
- 管理者は他人分も操作可能（運用上必要）

### 2.3 スケジューラの締め切り判定修正

**現状の問題:**
- `LotteryScheduler.checkAndExecuteLottery` が「当日が月末日か」をハードコード判定
- `LotteryDeadlineHelper` は `lottery_deadline_days_before` 設定で可変だが、スケジューラ側はこの設定を参照していない
- 締め切りを前倒し（例: 3日前）に設定しても、自動抽選は月末日にしか実行されない

**修正方針:**
- 月末日判定を廃止
- `lotteryDeadlineHelper.isAfterDeadline(nextYear, nextMonth)` で翌月分の締め切り超過を判定
- 毎日0:00のチェックで、締め切りを過ぎていれば未実行の抽選を実行

**修正後のあるべき姿:**
- `lottery_deadline_days_before` の設定値に従って自動抽選が正しいタイミングで実行される

### 2.4 抽選結果通知の管理者明示送信化

**現状の問題:**
- `LotteryService.executeLottery` 完了時にアプリ内通知が自動生成される
- LINE 通知は既に管理者手動送信だが、エンドポイントが別（`/admin/line/send/lottery-result`）
- アプリ内通知とLINE通知が別フローで管理されている

**修正方針:**
- `executeLottery` からの `notificationService.createLotteryResultNotifications()` 呼び出しを削除
- 新規エンドポイント（統合通知送信）:
  - `GET /api/lottery/notify-status?year=&month=` — 通知送信済みかチェック（Notification テーブルで LOTTERY_WON / LOTTERY_WAITLISTED の存在を確認）。レスポンス: `{ sent: boolean, sentCount: int }`
  - `POST /api/lottery/notify-results` — アプリ内通知生成 + LINE通知送信を一括実行。リクエスト: `{ year, month }`。権限: ADMIN+
- 既存 `POST /admin/line/send/lottery-result` を廃止（新エンドポイントに統合）
- フロントエンド: PracticeList のモーダルに「通知送信」ボタンを追加
  - 初回: そのまま送信
  - 2回目以降: 「既に送信済みです。再送信しますか？」確認ダイアログを表示

**修正後のあるべき姿:**
- 抽選実行後、管理者が明示的に「通知送信」を押して初めてアプリ内通知・LINE通知が送信される
- 1つのエンドポイントで両方の通知が一括処理される

### 2.5 findMonthlyLoserPlayerIds クエリ修正

**現状の問題:**
- セッションIDの大小（`ps.id < :currentSessionId`）で「先行セッション」を判定
- セッションが日付順に作成されなかった場合、IDと日付の順序が一致しない
- 再抽選時の優先当選判定が誤る可能性がある

**修正方針:**
- `ps.id < :currentSessionId` → `ps.sessionDate < (SELECT ps2.sessionDate FROM PracticeSession ps2 WHERE ps2.id = :currentSessionId)` に変更

**修正後のあるべき姿:**
- セッションの作成順序に依存せず、日付で正しく先行セッションを判定

### 2.6 buildLotteryResult の Service 層移動

**現状の問題:**
- `LotteryController` に `buildLotteryResult` メソッドが存在し、4つのリポジトリを直接注入
- Controller にビジネスロジックが含まれるレイヤー違反

**修正方針:**
- `buildLotteryResult` を `LotteryService` に移動
- Controller から `PracticeParticipantRepository`, `PracticeSessionRepository`, `PlayerRepository` の直接注入を削除
- `LotteryService` に `VenueRepository` を追加注入（項目10 の venueName 対応と同時）

**修正後のあるべき姿:**
- Controller は Service のメソッドを呼ぶだけ。リポジトリへの直接依存なし

### 2.7 /my-results, /waitlist-status の playerId 廃止

**現状の問題:**
- `playerId` をクエリパラメータで受け取っており、任意のユーザーの情報を閲覧可能

**修正方針:**
- `playerId` パラメータを廃止し、リクエスト属性の `currentUserId` を使用
- フロントエンドの API 呼び出しから `playerId` パラメータを削除

**修正後のあるべき姿:**
- ログインユーザー自身の結果のみ取得可能

### 2.8 仕様書への未記載仕様の反映

**現状の問題:**
- `lottery_normal_reserve_percent`（一般枠最低保証）が仕様書に未記載
- キャンセル待ち順番の前試合引き継ぎロジックが仕様書に未記載

**修正方針:**
- `docs/SPECIFICATION.md` と `docs/requirements/lottery-system.md` に追記

### 2.9 details JSON 構築の ObjectMapper 化

**現状の問題:**
- `StringBuilder` による手動 JSON 構築。エスケープ漏れやフォーマットエラーのリスク

**修正方針:**
- Jackson `ObjectMapper` を注入し、構造化オブジェクト → JSON 文字列に変換
- details 用の内部 DTO クラスを作成

### 2.10 venueName フィールドの設定

**現状の問題:**
- `LotteryResultDto` に `venueName` フィールドがあるが、常に null が返される

**修正方針:**
- `buildLotteryResult`（Service 移動後）で `VenueRepository` を使い、`session.getVenueId()` から会場名を取得してセット

---

## 3. 技術設計

### 3.1 API 変更

| 変更種別 | メソッド | パス | 変更内容 |
|---------|---------|------|---------|
| 新規 | GET | `/api/lottery/notify-status?year=&month=` | 通知送信済みチェック（ADMIN+） |
| 新規 | POST | `/api/lottery/notify-results` | 統合通知送信（ADMIN+） |
| 変更 | GET | `/api/lottery/my-results?year=&month=` | playerId パラメータ削除 |
| 変更 | GET | `/api/lottery/waitlist-status` | playerId パラメータ削除 |
| 変更 | POST | `/api/lottery/cancel` | 認可チェック追加 |
| 変更 | POST | `/api/lottery/respond-offer` | 認可チェック追加 |
| 廃止 | POST | `/admin/line/send/lottery-result` | 新エンドポイントに統合 |

### 3.2 DB 変更

なし（既存スキーマで対応可能）

### 3.3 フロントエンド変更

| ファイル | 変更内容 |
|---------|---------|
| `api/client.js` | `X-User-Id` ヘッダー追加 |
| `api/lottery.js` | `myResults` / `waitlistStatus` から playerId 削除、`notifyStatus` / `notifyResults` 追加 |
| `api/line.js` | `sendLotteryResult` 削除 |
| `pages/lottery/LotteryResults.jsx` | API 呼び出し変更（playerId 削除） |
| `pages/lottery/WaitlistStatus.jsx` | API 呼び出し変更（playerId 削除） |
| `pages/practice/PracticeList.jsx` | モーダルに通知送信ボタン追加、確認ダイアログ、LINE 送信ボタン削除 |
| `pages/line/LineSettings.jsx` | 抽選結果送信参照がある場合は更新 |

### 3.4 バックエンド変更

| ファイル | 変更内容 |
|---------|---------|
| `interceptor/RoleCheckInterceptor.java` | `X-User-Id` ヘッダー抽出・検証・リクエスト属性セット |
| `service/LotteryService.java` | lotteryId 修正、buildLotteryResult 移入、自動通知削除、ObjectMapper 化 |
| `controller/LotteryController.java` | 認可チェック追加、playerId 廃止、notify エンドポイント新設、buildLotteryResult 削除 |
| `scheduler/LotteryScheduler.java` | 締め切り判定修正 |
| `repository/PracticeParticipantRepository.java` | findMonthlyLoserPlayerIds クエリ修正 |
| `controller/LineAdminController.java` | lottery-result 送信エンドポイント廃止 |
| `controller/SystemSettingController.java` | currentUserId 修正 |
| `controller/PracticeSessionController.java` | currentUserId 修正 |

---

## 4. 影響範囲

### 影響を受ける既存機能
- **練習一覧画面（PracticeList）**: 通知送信ボタン追加、LINE 送信ボタン統合
- **LINE 設定画面（LineSettings）**: 抽選結果送信参照の更新
- **フロントエンド全 API 呼び出し**: `X-User-Id` ヘッダーが自動付与される（既存機能に影響なし）
- **全 `@RequireRole` エンドポイント**: `X-User-Id` 必須になる（フロントエンドが送信するため影響なし）

### 破壊的変更
- `GET /my-results` の playerId パラメータ削除 → フロントエンド同時更新必須
- `GET /waitlist-status` の playerId パラメータ削除 → フロントエンド同時更新必須
- `POST /admin/line/send/lottery-result` 廃止 → フロントエンド同時更新必須

全て内部 API であり、フロントエンドとバックエンドを同時にデプロイすれば問題なし。

---

## 5. 設計判断の根拠

| 判断 | 理由 |
|------|------|
| JWT ではなくヘッダー方式を拡張 | プロトタイプ段階のため、現行アーキテクチャの延長で最小工数で対応。JWT 移行は別途改修 |
| 通知をアプリ内 + LINE 統合送信 | 片方のみ送信するユースケースがないため、一括送信で運用負荷を軽減 |
| playerId パラメータを完全廃止 | 管理者が他人の結果を見るケースは不要。管理者は `/results` で全体を確認可能 |
| 2回目以降の通知送信に確認ダイアログ | バックエンドで冪等にするのではなく、UX で防止。再送信は意図的に許可 |
