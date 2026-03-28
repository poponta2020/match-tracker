---
status: completed
---
# 団体管理 実装手順書

## 実装タスク

### タスク1: DBスキーマ・マイグレーション作成
- [x] 完了
- **概要:** `organizations` テーブル、`player_organizations` テーブルの新規作成。既存テーブルへの `organization_id` カラム追加。初期データ挿入とデータ移行を含む。
- **変更対象ファイル:**
  - `database/migration_organization.sql` — 新規作成。DDL + データ移行SQL
- **依存タスク:** なし
- **実装詳細:**
  1. `organizations` テーブル作成（id, code, name, color, deadline_type, created_at, updated_at）
  2. 初期データ挿入（わすらもち会, 北海道大学かるた会）
  3. `player_organizations` テーブル作成（id, player_id, organization_id, created_at）+ UNIQUE制約
  4. `practice_sessions` に `organization_id` カラム追加
  5. `system_settings` に `organization_id` カラム追加 + ユニーク制約変更
  6. `players` に `admin_organization_id` カラム追加
  7. `invite_tokens` に `organization_id` カラム追加
  8. データ移行: 既存players → player_organizations（わすらもち会）、既存practice_sessions/system_settings/invite_tokens → organization_id をわすらもち会に設定
- **対応Issue:** #81

### タスク2: バックエンド — Organization エンティティ・リポジトリ・サービス・コントローラ
- [x] 完了
- **概要:** 団体の基盤となるバックエンドクラス群を新規作成する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/Organization.java` — 新規作成
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/PlayerOrganization.java` — 新規作成
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/DeadlineType.java` — 新規作成（ENUM: SAME_DAY, MONTHLY）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/OrganizationRepository.java` — 新規作成
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/PlayerOrganizationRepository.java` — 新規作成
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/OrganizationDto.java` — 新規作成
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/OrganizationService.java` — 新規作成
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/OrganizationController.java` — 新規作成
- **依存タスク:** タスク1
- **実装詳細:**
  - `GET /api/organizations` — 団体一覧取得
  - `GET /api/players/{id}/organizations` — ユーザーの参加団体一覧
  - `PUT /api/players/{id}/organizations` — ユーザーの参加団体更新（最低1つ必須バリデーション）
  - `PUT /api/players/{id}/admin-organization` — ADMIN の団体紐づけ変更（SUPER_ADMIN のみ）
  - `PlayerOrganizationRepository` に `findByPlayerId`、`findByOrganizationId`、`existsByPlayerIdAndOrganizationId` 等のメソッド
- **対応Issue:** #82

### タスク3: バックエンド — Player エンティティに admin_organization_id 追加
- [x] 完了
- **概要:** Player エンティティ・DTO に `adminOrganizationId` を追加し、ADMIN の団体紐づけを実現する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/Player.java` — `adminOrganizationId` フィールド追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/PlayerDto.java` — `adminOrganizationId` フィールド追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/PlayerResponse.java` — 参加団体情報を含める（該当する場合）
- **依存タスク:** タスク1, タスク2
- **対応Issue:** #83

### タスク4: バックエンド — PracticeSession に organization_id 追加・自動フィルタ
- [x] 完了
- **概要:** 練習日に団体紐づけを追加し、ユーザーの参加団体に基づく自動フィルタを実装する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/PracticeSession.java` — `organizationId` フィールド追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/PracticeSessionDto.java` — `organizationId` フィールド追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/PracticeSessionRepository.java` — `findByOrganizationIdIn` 等のクエリ追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeSessionService.java` — ログインユーザーの参加団体に基づくフィルタロジック追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/PracticeSessionController.java` — 作成時の organization_id 自動設定（ADMIN → admin_organization_id, SUPER_ADMIN → リクエストパラメータ）
- **依存タスク:** タスク1, タスク2, タスク3
- **実装詳細:**
  - 一覧取得系エンドポイント: ログインユーザーの `player_organizations` を取得し、該当する `organization_id` の練習日のみ返す
  - 作成エンドポイント: ADMIN は `admin_organization_id` を自動設定。SUPER_ADMIN はリクエストで `organization_id` を指定
  - 編集・削除エンドポイント: ADMIN は自団体の練習日のみ操作可能（スコープチェック）
- **対応Issue:** #84

### タスク5: バックエンド — ADMIN 団体スコープの権限チェック
- [x] 完了
- **概要:** ADMIN が自団体以外の練習日を編集・削除できないようにスコープチェックを追加する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/interceptor/RoleCheckInterceptor.java` — ADMIN の団体スコープ情報をリクエスト属性に追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeSessionService.java` — 編集・削除時にスコープチェック
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeParticipantService.java` — 参加者管理時にスコープチェック
- **依存タスク:** タスク3, タスク4
- **実装詳細:**
  - `RoleCheckInterceptor` で ADMIN の場合、`admin_organization_id` をリクエスト属性に設定
  - 各サービスで、ADMIN が操作対象の `practice_session.organization_id` と `admin_organization_id` の一致を確認
  - 不一致の場合は `ForbiddenException` をスロー
  - SUPER_ADMIN はスコープチェックをスキップ
- **対応Issue:** #85

### タスク6: バックエンド — SystemSetting の団体対応
- [x] 完了
- **概要:** システム設定を団体ごとに独立させる。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/SystemSetting.java` — `organizationId` フィールド追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/SystemSettingRepository.java` — `findBySettingKeyAndOrganizationId` 追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/SystemSettingService.java` — 全メソッドに `organizationId` パラメータ追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/SystemSettingController.java` — ADMIN は自団体の設定のみ取得・更新。SUPER_ADMIN は団体指定
- **依存タスク:** タスク1, タスク5
- **実装詳細:**
  - `getValue(key)` → `getValue(key, organizationId)` に変更
  - `getLotteryDeadlineDaysBefore()` → `getLotteryDeadlineDaysBefore(organizationId)` に変更
  - `SystemSettingService` を呼び出している箇所を全て `organizationId` を渡すよう修正
- **対応Issue:** #86

### タスク7: バックエンド — LotteryDeadlineHelper の団体別分岐
- [x] 完了
- **概要:** 締切判定を団体の `deadline_type` に応じて分岐させる。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryDeadlineHelper.java` — 団体の deadline_type に応じた分岐ロジック追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryService.java` — organizationId を考慮した抽選実行
- **依存タスク:** タスク2, タスク6
- **実装詳細:**
  - `getDeadline(year, month)` → `getDeadline(year, month, organizationId)` に変更
  - SAME_DAY の場合: 練習当日12:00を返す（個別の練習日ごとに判定）
  - MONTHLY の場合: 既存ロジック（前月N日）を維持
  - `isBeforeDeadline` も同様に団体対応
  - `LotteryService`: 北大かるた会の練習日のみを抽選対象とする（わすらもち会は抽選なし）
- **対応Issue:** #87

### タスク8: バックエンド — PracticeParticipantService のわすらもち会対応
- [x] 完了
- **概要:** わすらもち会の先着順ロジック（PENDING不使用、即WON/WAITLISTED）を実装する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeParticipantService.java` — 団体の deadline_type に応じた登録ロジック分岐
- **依存タスク:** タスク4, タスク7
- **実装詳細:**
  - 参加登録時に練習日の `organization_id` から団体の `deadline_type` を取得
  - SAME_DAY（わすらもち会）の場合:
    - 空きあり → 即 `WON`
    - 定員超過 → 即 `WAITLISTED`（waitlistNumber を自動割り当て）
    - `PENDING` は使わない
  - MONTHLY（北大）の場合: 既存ロジック維持（締切前は PENDING、締切後は空きあれば WON / 満席なら WAITLISTED）
- **対応Issue:** #88

### タスク9: バックエンド — InviteToken の団体対応
- [x] 完了
- **概要:** 招待トークンに団体を紐づけ、登録時に `player_organizations` の初期値を設定する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/InviteToken.java` — `organizationId` フィールド追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/InviteTokenResponse.java` — `organizationId` フィールド追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/InviteTokenService.java` — トークン作成時に `organizationId` 保持、登録時に `player_organizations` レコード作成
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/InviteTokenController.java` — 作成リクエストに `organizationId` パラメータ追加。ADMIN は自団体のトークンのみ作成可能
- **依存タスク:** タスク1, タスク2, タスク5
- **実装詳細:**
  - ADMIN がトークン作成 → `admin_organization_id` が自動設定される
  - SUPER_ADMIN がトークン作成 → `organization_id` を指定
  - `registerWithToken` で Player 作成後に `player_organizations` に初期レコード挿入
- **対応Issue:** #89

### タスク10: バックエンド — 通知の団体フィルタ
- [x] 完了
- **概要:** 通知送信時に対象ユーザーの参加団体でフィルタリングする。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java` — 送信前に `player_organizations` を確認してフィルタ
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/` — スケジュールタスクの通知対象フィルタ
- **依存タスク:** タスク2, タスク4
- **注意:** Webプッシュ通知実装時に再ヒアリングが必要。ここではLINE通知のフィルタのみ対応。
- **対応Issue:** #90

### タスク11: フロントエンド — 団体登録設定画面
- [x] 完了
- **概要:** ユーザーが参加する練習会を選択する設定画面を新規作成する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/settings/OrganizationSettings.jsx` — 新規作成。チェックボックスで参加練習会選択（最低1つ必須）
  - `karuta-tracker-ui/src/api/organizations.js` — 新規作成。団体関連APIクライアント
  - `karuta-tracker-ui/src/App.jsx` — ルート追加（`/settings/organizations`）
  - `karuta-tracker-ui/src/components/NavigationMenu.jsx` — 設定メニューに「団体登録」リンク追加
- **依存タスク:** タスク2
- **対応Issue:** #91

### タスク12: フロントエンド — カレンダーの団体色分け・練習日詳細の団体名表示
- [x] 完了
- **概要:** カレンダー上で練習日を団体の色で色分けし、詳細画面に団体名を表示する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/practice/` — カレンダー表示コンポーネントで `organization.color` を使った色分け
  - `karuta-tracker-ui/src/pages/practice/` — 練習日詳細画面に団体名を表示
- **依存タスク:** タスク4, タスク11
- **対応Issue:** #92

### タスク13: フロントエンド — わすらもち会の12:00以降確認ダイアログ
- [x] 完了
- **概要:** わすらもち会の練習日に対し、当日12:00以降の参加登録・キャンセル時に確認ダイアログを表示する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/practice/` — 参加登録・キャンセル処理の前に、団体と現在時刻をチェックしてダイアログ表示
- **依存タスク:** タスク4, タスク12
- **実装詳細:**
  - 練習日の `organization` が SAME_DAY タイプ かつ 当日12:00以降の場合にダイアログ表示
  - メッセージ:「12時以降の参加登録・キャンセルは管理者への連絡が必須です。連絡しましたか？」
  - 「はい」→ 処理続行、「いいえ」→ 処理中断
- **対応Issue:** #93

### タスク14: フロントエンド — 新規登録画面の団体対応
- [x] 完了
- **概要:** InviteRegister 画面でトークンの団体情報を取得し、初期登録に反映する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/InviteRegister.jsx` — トークン検証レスポンスから `organizationId` を取得し、登録後の `player_organizations` 初期値に反映（バックエンド側で処理されるため、フロントは表示のみ調整）
- **依存タスク:** タスク9, タスク11
- **実装詳細:**
  - トークン検証時に団体情報を受け取り、登録画面に「○○の練習会への登録」等を表示
- **対応Issue:** #94

### タスク15: フロントエンド — ADMIN管理画面の団体スコープ
- [x] 完了
- **概要:** ADMIN がログインした際、管理系画面を自団体スコープに制限する。SUPER_ADMIN の練習日作成時に団体選択UIを追加する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/practice/` — 練習日作成フォームに団体選択（SUPER_ADMIN のみ表示）
  - `karuta-tracker-ui/src/pages/admin/` — システム設定画面の団体対応
  - `karuta-tracker-ui/src/context/AuthContext.jsx` — ログインユーザーの `adminOrganizationId` と参加団体情報を保持
- **依存タスク:** タスク4, タスク5, タスク6, タスク11
- **実装詳細:**
  - AuthContext に `adminOrganizationId` と `organizations`（参加団体一覧）を追加
  - ADMIN 用の管理画面はバックエンド側で自動フィルタされるため、フロントは追加のフィルタ不要
  - SUPER_ADMIN の練習日作成フォームに団体選択ドロップダウンを追加
  - システム設定画面: ADMIN は自団体の設定のみ表示。SUPER_ADMIN は団体切り替えで設定管理
- **対応Issue:** #95

### タスク16: バックエンド — テスト
- [ ] 完了
- **概要:** 団体管理に関する単体テスト・統合テストを追加する。
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/OrganizationServiceTest.java` — 新規作成
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/PracticeParticipantServiceTest.java` — わすらもち会の先着順ロジックのテスト追加
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/LotteryDeadlineHelperTest.java` — 団体別分岐のテスト追加
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/SystemSettingServiceTest.java` — 団体別設定のテスト追加
  - 既存テストの修正（organization_id パラメータ追加対応）
- **依存タスク:** タスク1〜10
- **対応Issue:** #96

## 実装順序

1. **タスク1** — DBスキーマ・マイグレーション（依存なし）
2. **タスク2** — Organization エンティティ・リポジトリ・サービス・コントローラ（タスク1に依存）
3. **タスク3** — Player に admin_organization_id 追加（タスク1,2に依存）
4. **タスク5** — ADMIN 団体スコープの権限チェック（タスク3に依存）
5. **タスク4** — PracticeSession の organization_id 追加・自動フィルタ（タスク1,2,3に依存）
6. **タスク6** — SystemSetting の団体対応（タスク1,5に依存）
7. **タスク7** — LotteryDeadlineHelper の団体別分岐（タスク2,6に依存）
8. **タスク8** — PracticeParticipantService のわすらもち会対応（タスク4,7に依存）
9. **タスク9** — InviteToken の団体対応（タスク1,2,5に依存）
10. **タスク10** — 通知の団体フィルタ（タスク2,4に依存）
11. **タスク11** — フロントエンド: 団体登録設定画面（タスク2に依存）
12. **タスク12** — フロントエンド: カレンダー色分け・詳細画面（タスク4,11に依存）
13. **タスク13** — フロントエンド: 確認ダイアログ（タスク4,12に依存）
14. **タスク14** — フロントエンド: 新規登録画面の団体対応（タスク9,11に依存）
15. **タスク15** — フロントエンド: ADMIN管理画面の団体スコープ（タスク4,5,6,11に依存）
16. **タスク16** — テスト（タスク1〜10に依存）
