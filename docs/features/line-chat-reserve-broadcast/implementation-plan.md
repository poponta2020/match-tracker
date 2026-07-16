---
status: completed
---
# LINEチャット予約送信による札分け全体配信（line-chat-reserve-broadcast）実装手順書

要件: [requirements.md](requirements.md)。**タスク0（Phase 1 手動検証）が GO になるまでコードタスクに着手しない。**

## 実装タスク

### タスク0: Phase 1 手動検証（GO/NO-GOゲート・コード着手前）
- [x] 完了（2026-07-17 ユーザーGO判定: ①グループ予約送信成功 ②無料通数非増加 ③削除可 の3点実測OK）
- **目的:** 本機能の成立条件をコード1行も書く前に実測確認する
- **対応AC:** AC-11
- **内容（ユーザー実施・アシスタントは手順ガイド）:**
  1. 既存プールbotを1体選び、リネーム（例:「北大かるた会お知らせ」）・チャットON（応答メッセージOFF）・グループ参加許可ON
  2. 少人数のテストグループを作り、そのOAを招待
  3. LINE Official Account Manager のチャットからテストグループへ**予約送信を手動で実施**し、指定時刻に届くことを確認
  4. 「利用と請求」で**無料メッセージ通数が増えていない**ことを確認
  5. 予約の削除ができることを確認
- **完了条件:** 上記3点（グループで予約可・通数非増加・削除可）の実測OK。**いずれかNGなら本機能は中止し要再検討**
- **依存タスク:** なし
- **対応Issue:** #1085

### タスク1: DBスキーマ・エンティティ基盤
- [x] 完了
- **目的:** 予約キューの永続化基盤と通知種別を用意する
- **対応AC:** AC-13（＋AC-1/3/4の土台）
- **主な変更領域:** `database/create_line_chat_reservations.sql`（新規）、`entity/LineChatReservation.java`（新規）、`entity/LineBroadcastGroup.java`（chat_room_id 等の識別情報カラム追加）、`entity/LineMessageLog.java`（`ADMIN_CHAT_RESERVE_ALERT` 追加）、`repository/LineChatReservationRepository.java`（新規）、`docs/design/db.md`
- **設計要点:** status = PENDING/RESERVING/RESERVED/FAILED/MANUAL_REVIEW_REQUIRED/CANCEL_PENDING/CANCELLED/DRY_RUN_SUCCEEDED。部分ユニーク `(broadcast_group_id, session_id) WHERE status <> 'CANCELLED'`（非CANCELLEDの行は常に1件）。error_code/error_message/attempt_count/scheduled_send_at/message_text(TEXT)。`line_message_log` の notification_type CHECK 拡張も同SQLに含める（**本番introspectで現行許容値を確認してから張り直す**）
- **依存タスク:** タスク0（GO判定）
- **必要なテスト:** entity/repository の基本動作＋部分ユニークの重複拒否（既存 `DataInitializer` の index 検証パターン踏襲）
- **完了条件:** JUnit green・migration SQL 作成済み（本番適用は /ship 時に AC-13 として実施・PR に明示）
- **対応Issue:** #1086

### タスク2: 予約キュー生成スケジューラ（20:00バッチ＋リコンサイル）
- [ ] 完了
- **目的:** 前日20:00に翌日分の予約レコードを冪等生成し、変更検知・滞留検知を行う
- **対応AC:** AC-1, AC-5
- **主な変更領域:** `scheduler/LineChatReservationScheduler.java`（新規）、`service/LineChatReservationService.java`（新規）、`docs/spec/notifications.md`
- **設計要点:** ①cron 20:00 JST: 有効グループ×翌日当該団体セッション→本文（`buildTextForSession`）＋送信時刻（既存 `resolveFirstMatchStartTime` ロジック共通化・30分前/8:00）で INSERT（既存active行あればskip）②15分毎リコンサイル: 未来の active 行について本文/時刻を再計算し、セッション削除・不一致→CANCEL_PENDING。active 行が無く送信30分前より余裕のある未来セッション→再作成（取消後の再予約）③RESERVING が30分超滞留→MANUAL_REVIEW_REQUIRED＋アラート。`today/now` 引数化で決定論テスト
- **依存タスク:** タスク1
- **必要なテスト:** バッチ冪等（再実行で重複なし）・本文/時刻の正しさ・変更検知→CANCEL_PENDING・取消後再作成・マージン不足時は作らない・RESERVING滞留昇格
- **完了条件:** JUnit green
- **対応Issue:** #1087

### タスク3: ワーカーAPI＋サービストークン認証
- [ ] 完了
- **目的:** ワーカーが予約タスクを取得し結果を報告する専用APIを新設する
- **対応AC:** AC-2, AC-3
- **主な変更領域:** `interceptor/ServiceTokenInterceptor.java`（新規）、`config/WebConfig.java`（interceptor 登録）、`controller/LineChatWorkerController.java`（新規）、`dto/LineChatWorkerTaskDto.java`・`dto/LineChatWorkerResultRequest.java`（新規）
- **設計要点:** `X-Service-Token` を env（`LINE_CHAT_WORKER_TOKEN`）と定数時間比較、`/api/line-chat-worker/**` のみに適用（未設定envなら全拒否）。GET tasks（PENDING/CANCEL_PENDING、グループ検証情報つき）／POST `/{id}/result`（遷移バリデーション: PENDING→RESERVING→RESERVED|FAILED|MANUAL_REVIEW_REQUIRED、CANCEL_PENDING→CANCELLED|MANUAL_REVIEW_REQUIRED 等。不正遷移は409）。日時はISO 8601（+09:00明示）で返す
- **依存タスク:** タスク1
- **必要なテスト:** トークン無し/不正=401/403・正当トークンで取得/報告・不正遷移拒否・既存ロールエンドポイントにサービストークンが効かないこと
- **完了条件:** JUnit green
- **対応Issue:** #1088

### タスク4: フォールバック統合＋管理者アラート
- [ ] 完了
- **目的:** 予約状態に応じて既存push経路をガードし、異常時に管理者へ通知する
- **対応AC:** AC-4, AC-10
- **主な変更領域:** `service/CardDivisionBroadcastService.java`（冒頭ガード追加のみ）、`service/LineNotificationService.java`（ADMIN_CHAT_RESERVE_ALERT 送信メソッド）、`docs/features/card-division-group-broadcast/requirements.md`（変更履歴追記: 1グループ1bot制約でローテ不成立→単一botフォールバックへ役割変更）
- **設計要点:** ガードは (group, session) の予約状態で分岐: RESERVED→push しない／RESERVING・MANUAL_REVIEW_REQUIRED→push せずアラート1回／それ以外（PENDING/FAILED/CANCELLED/行なし）→既存どおり push（＝フォールバック発動をアラート）。通知はベストエフォート（失敗しても配信処理を壊さない）
- **依存タスク:** タスク1（タスク2とは変更ファイルが重ならない）
- **必要なテスト:** 各予約状態×push発火有無のマトリクス・アラート記録・通知失敗時も本処理継続
- **完了条件:** JUnit green・既存 broadcast テスト非破壊
- **対応Issue:** #1089

### タスク5: 管理API＋管理画面（予約状況セクション）
- [ ] 完了
- **目的:** 予約状況の可視化・アラート表示・手動再試行を管理画面に追加する
- **対応AC:** AC-9
- **主な変更領域:** `controller/LineBroadcastAdminController.java`（一覧・再試行エンドポイント追加）、`service/LineBroadcastAdminService.java`、`dto/`（予約DTO）、`karuta-tracker-ui/src/pages/line/CardDivisionBroadcastAdmin.jsx`＋`src/api/lineBroadcast.js`、`docs/SCREEN_LIST.md`
- **設計要点:** ADMIN+・団体スコープ（既存パターン踏襲）。再試行は FAILED かつ送信予定時刻まで余裕（30分・タスク2と同値）がある行のみ PENDING に戻す。一覧は直近N件＋activeを状態バッジ表示、MANUAL_REVIEW_REQUIRED/AUTH失効はアラート枠
- **依存タスク:** タスク1（タスク2〜4と変更ファイルが重ならない）
- **必要なテスト:** API（一覧・スコープ・再試行の条件）JUnit＋画面 Vitest（表示・再試行ボタン活性条件）
- **完了条件:** JUnit/Vitest/lint green
- **対応Issue:** #1090

### タスク6: ワーカー本体（line-chat-worker/ 新設）
- [ ] 完了
- **目的:** VM常駐のPlaywrightワーカー一式（ロジック層＋雛形Page Object＋運用資材）を作る
- **対応AC:** AC-6, AC-7, AC-8（ロジック層）, AC-14（ワーカーunit）
- **主な変更領域:** `line-chat-worker/`（新規ディレクトリ全体: package.json/tsconfig/src/config・appApi/client・usecases/reserveMessage・cancelReservation・detect/authState・line/pages（インターフェース＋雛形）・index.ts メインループ）、`Dockerfile`・`docker-compose.yml`、`line-chat-worker/RUNBOOK.md`（初回ログイン storageState 作成→VM配置・デプロイ・認証更新・障害対応）、`scripts/create-auth-state.ts`（ローカルheadedログイン）、CI へ worker の lint+unit test ジョブ追加
- **設計要点:** 直列処理・ポーリング（5分・env可変）。usecase は Page Object インターフェース越しに書き、**モックPOでユニットテスト**（「送信予定」検証を通過した時のみ reserved／確定後タイムアウト→manual_review／ログイン画面・CAPTCHA検出→即中止 LINE_AUTH_EXPIRED／dry-run は確定ボタン押下せずスクショ）。storageState は実行毎に再エクスポートしてローリング更新。本文・Cookieをログ出力しない。**実セレクタはこのタスクでは確定しない**（推測実装禁止・タスク7で実DOM調査）
- **依存タスク:** タスク3（API契約）。タスク1〜5とディレクトリが完全に分離
- **必要なテスト:** usecase 状態機械のユニットテスト（モックPO）・appApi client のリクエスト/エラー処理・日時変換（ISO→JST入力値）
- **完了条件:** worker の lint・unit test green（実ブラウザ操作はCI外）
- **対応Issue:** #1091

### タスク7: Phase 2 ローカルPoC＝実DOM調査とPage Object確定（ユーザー協働）
- [ ] 完了
- **目的:** chat.line.biz の実DOMを調査してセレクタを確定し、テストグループで予約登録→検証→削除→dry-run を実走する
- **対応AC:** AC-6/7/8 の実機成立、複数行テキスト入力の忠実性確認（要件§6の未解決論点）
- **主な変更領域:** `line-chat-worker/src/line/pages/*.ts`（セレクタ実装）、必要に応じ usecase 微修正、RUNBOOK 追記
- **依存タスク:** タスク0（テストグループ・OA準備済み）、タスク6。**実LINEアカウントでの操作を伴うためユーザー協働・実機タスク**（自動レビューループの対象外挙動は verify で担保）
- **必要なテスト:** テストグループでの実走: 予約登録成功→OAM上で「送信予定」確認→削除→dry-run スクショ確認→実際に指定時刻に届く
- **完了条件:** テストグループでの一連の実走成功（記録をRUNBOOKに残す）
- **対応Issue:** #1092

### タスク8: Phase 3 VMデプロイ＋セッション生存観測＋本番投入（運用・ユーザー協働）
- [ ] 完了
- **目的:** Oracle VMへデプロイし、セッション維持を観測してから本番グループへ投入する
- **対応AC:** AC-12
- **主な変更領域:** VM上の docker compose 環境（コードはタスク6の資材）、`line_broadcast_group` への本番グループ識別情報登録、OAの本番全体グループへの招待
- **内容:** ①VMへ手動デプロイ（RUNBOOK手順）②テストグループ向け設定で**2週間以上**、毎日の予約登録が失効なしで成功することを観測（失効した場合は検知→通知→再ログイン運用が回ることの確認で代替）③観測合格後、OAを本番全体グループへ招待・グループ識別情報を登録し、最初の数回は送信前に人が予約内容を目視確認④問題なければ完全自動化
- **依存タスク:** タスク7
- **完了条件:** 本番グループでの初回自動配信成功＋2週間観測記録
- **対応Issue:** #1093

## 実装順序（Wave = 並行実装できるタスクの組）

- **Wave 0: タスク0**（手動GO/NO-GO。NGなら以降すべて中止）
- **Wave 1: タスク1**（スキーマ基盤・共有ホットスポットを先行）
- **Wave 2: タスク2, タスク3**（互いに変更ファイルが重ならない）
- **Wave 3: タスク4, タスク5, タスク6**（4=既存service冒頭ガード、5=管理API/UI、6=新規ディレクトリ。相互に重ならない）
- **Wave 4: タスク7**（実機・ユーザー協働）
- **Wave 5: タスク8**（運用・観測）

**PR分割方針:** タスク1〜6で PR#1（アプリ側＋ワーカー骨格。migration 本番適用を明示）。タスク7のセレクタ確定分は実走結果を含めて PR#2。タスク8はコード変更を伴わない運用（必要が生じた修正のみ都度PR）。
