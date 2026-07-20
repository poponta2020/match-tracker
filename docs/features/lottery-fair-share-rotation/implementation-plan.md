---
status: completed
---
# 抽選の公平化（一巡保証 ＋ 直近30日の重み付き抽選）実装手順書

> 正典は [requirements.md](./requirements.md)。本書は薄いタスク分割。詳細はコードベースが正。
> **DB スキーマ変更なし・本番 migration 不要**（設定は `system_settings` の汎用 KV）。

## 技術設計サマリ（実装前提）

- **削除**: `LotteryService.processMatch` / `processSession` の cascade（`sessionLosers`/`cascadeCandidates`）、二値救済（`monthlyLosers` 分類・rescue枠）、一般枠30%（`getLotteryNormalReservePercent`）、連続試合の待ち番号引き継ぎ（`previousMatchWaitlistOrder`/`currentMatchWaitlistOrder`）。
- **新設 `LotteryFairShareTracker`（純ロジッククラス）**: 選手ごとの当選日多重集合 `Map<Long,List<LocalDate>>` を保持。
  - `recentTaken(playerId, sessionDate)` = 窓 `[sessionDate-30日, sessionDate)` 内の件数
  - `todayTaken(playerId, sessionDate)` = `== sessionDate` の件数
  - `recordWin(playerId, sessionDate)` = 1件追加
  - `pickWeighted(candidates, sessionDate, capPercentile, rng)` = ルール2の重み付き抽選（キャップ算出＋ガード＋累積和走査）
- **選抜手続き（ルール1＋ルール2）**: プール内で `todayTaken` 最小の候補に絞り（ルール1）、その中を `1/(min(recentTaken,cap)+1)` の重み付き抽選（ルール2）で1名ずつ確定。「管理者優先プール → その他プール」の順に定員まで。**キャンセル待ち番号も同手続きの続行**で採番（バケット順維持＝管理者優先落選者が最上位）。
- **`recentTaken` の初期化**: 抽選開始時に対象団体の WON 参加（`(playerId, sessionDate)` 行）を `[最早セッション日-30, 最遅セッション日+1)` の範囲で一括ロード（新クエリ。半開区間の終端 `+1` で最遅当日の既存 WON も含め、当日分は `todayTaken` に算入する）。対象月の抽選対象は PENDING のため二重計上されない。当選確定のたびに `recordWin` で加算（プレビュー=非永続でも in-memory で正しく累積）。
- **決定性**: 候補は ID 昇順固定。単一 `Random(seed)` を実行全体で共有 → プレビューと確定が同一シードで一致（AC-R3）。
- **設定**: 新 KV `lottery_weight_cap_percentile`（デフォルト30）。`SystemSettingService` に getter（0〜100 クランプ）を追加。BE 保存経路のバリデーションは既存同様なし＝getter クランプで防御。

## 実装タスク

### タスク1: パーセンタイル設定の追加（SystemSettingService）
- [x] 完了
- **目的:** 新設定キー `lottery_weight_cap_percentile`（デフォルト30・0〜100 クランプ）の getter を追加し、抽選ロジックが読める状態にする。旧 `lottery_normal_reserve_percent` の getter/定数はここでは残す（T4 が最終利用者を消してから撤去）。
- **対応AC:** AC-11, AC-12
- **主な変更領域:** `karuta-tracker/.../service/SystemSettingService.java`（＋`SystemSettingServiceTest` があれば）
- **依存タスク:** なし
- **必要なテスト:** 未設定→30、保存値の読取、範囲外（負数・101等）のクランプ、非数値→デフォルト
- **完了条件:** テスト green・`./gradlew test --tests "*SystemSettingService*"`
- **対応Issue:** #1120
### タスク2: recentTaken 用クエリの追加（PracticeParticipantRepository）
- [x] 完了
- **目的:** 「対象団体・指定日付範囲・status=WON の参加行 `(playerId, sessionDate)`」を取得するクエリを追加（`recentTaken` ベースライン用）。`findVenueIdsByPlayerIdAndSessionDateAndMatchNumber` の JOIN 形を流用。
- **対応AC:** AC-5, AC-8
- **主な変更領域:** `karuta-tracker/.../repository/PracticeParticipantRepository.java`（＋`@DataJpaTest`）
- **依存タスク:** なし
- **必要なテスト:** 範囲内WONのみ返す・範囲外/非WONを除外・団体フィルタ・境界日（>= from, < to）
- **完了条件:** `@DataJpaTest` green
- **対応Issue:** #1121
### タスク3: 公平抽選ロジッククラスの新設（LotteryFairShareTracker）
- [x] 完了
- **目的:** 窓カウント（recentTaken/todayTaken）・パーセンタイルキャップ（nearest-rank＋最小値一致時+1ガード）・シード付き重み付き抽選を純ロジックとして実装し、単体テストで固める。
- **対応AC:** AC-3, AC-4, AC-4b, AC-5, AC-8
- **主な変更領域:** `karuta-tracker/.../service/LotteryFairShareTracker.java`（新規）＋専用テスト
- **依存タスク:** なし
- **必要なテスト:** 30日窓の境界（`[d-30, d)`）・同日複数WONの計上・キャップ算出（例11人 p=30→cap=3, p=50→cap=6）・最小値一致時の+1ガード・同一シードで同一選択（決定性）・重みが `1/(min(recent,cap)+1)` に一致
- **完了条件:** テスト green
- **対応Issue:** #1122
### タスク4: 抽選アルゴリズム本体の置換（LotteryService）★中核
- [x] 完了
- **目的:** `processMatch`/`processSession` を2ルール方式へ全面置換し、`executeLottery`/`previewLottery`/`reExecuteLottery` の3経路で `LotteryFairShareTracker` をベースラインから構築して差し込む。cascade・rescue・reserve・待ち番号引き継ぎを撤去。`reExecuteLottery` の `findMonthlyLoserPlayerIds` 依存を廃止。旧 `getLotteryNormalReservePercent` の定数/getter を撤去。
- **対応AC:** AC-1, AC-2, AC-6, AC-7, AC-9, AC-R1, AC-R2, AC-R3, AC-R4, AC-R5, AC-R6
- **主な変更領域:** `karuta-tracker/.../service/LotteryService.java`（processMatch 署名変更：sessionLosers/monthlyLosers/待ち順マップを除去し tracker＋capPercentile を受ける）、`SystemSettingService.java`（旧 getReserve 撤去）、`LotteryServiceTest.java`・`LotteryServiceExecuteAndConfirmTest.java`（新署名へ全 processMatch 呼び出し更新、(b) rescue テスト削除/書換、(c) reserve スタブ削除、`reExecute...inherits...` の monthly スタブ除去）
- **依存タスク:** タスク1・2・3（順序制約：T1/T2/T3 の後）
- **必要なテスト（テストファースト）:**
  - AC-2: 全員全試合希望・毎試合定員超過で「一巡するまで同一人が2回落選しない」（落選回数差≤1）
  - AC-1: `todayTaken` 最小の候補のみが対象
  - AC-6: 管理者優先が最上位（定員内全当選 / 超過時は優先同士）＋落選優先者が待ち最上位（現行 `processMatch_adminPriorityLoser_getsTopWaitlistPosition` を新署名で維持）
  - AC-R3: 同一シードで preview と execute の当落一致
  - AC-R1（定員以下全当選）・AC-R2（既存WON/OFFERED控除）・AC-R4（優先バリデーション）・AC-R5（reExecute 繰上承諾維持）を非退行として保持
- **完了条件:** `./gradlew test`（抽選関連）green
- **対応Issue:** #1123
### タスク5: システム設定画面の改修（SystemSettings.jsx）
- [x] 完了
- **目的:** 「一般枠の最低保証割合」カードを削除し、「重み付けの基準（パーセンタイル）」カード（0〜100・デフォルト30・効果方向のインライン説明）と「抽選の仕組み」説明セクション（ルール1/ルール2/パーセンタイルの意味）を追加。`fetchSettings`/`handleSave` を新キー `lottery_weight_cap_percentile` に差し替え。
- **対応AC:** AC-9, AC-10, AC-13, AC-14
- **主な変更領域:** `karuta-tracker-ui/src/pages/settings/SystemSettings.jsx`・`SystemSettings.test.jsx`・`SystemSettings.pageHeader.test.jsx`（reserve アサーションを percentile へ更新／説明文の存在アサーション追加）。API（`systemSettings.js`）は汎用のため変更なし。
- **依存タスク:** なし（BE と別領域＝並行可）。※権限 AC-14 は既存 `SystemSettingController` の @RequireRole＋AdminScopeValidator を踏襲＝BE 変更不要
- **必要なテスト:** 保存で `update('lottery_weight_cap_percentile', ...)` が呼ばれる・取得値が反映される・0〜100 バリデーション・「抽選の仕組み」説明文が表示される・reserve フィールドが存在しない
- **完了条件:** `npm run lint` 0 err・`npm run test`（SystemSettings 関連）green
- **対応Issue:** #1124
### タスク6: ドキュメント更新（spec / requirements）
- [x] 完了
- **目的:** 正典ドキュメントを新アルゴリズムに更新（cascade/優先当選/救済/30%一般枠の記述を除去し、2ルール＋直近30日＋パーセンタイル設定へ）。
- **対応AC:**（D2 ドキュメント整合。機能 AC には非対応だが DoD 必須）
- **主な変更領域:** `docs/spec/lottery.md`（抽選アルゴリズムの特徴・システム設定・スケジューラ節）、`docs/requirements/lottery-system.md`（アルゴリズム説明・用語・テストケース）。用語は「取る」に統一。
- **依存タスク:** なし（コードと別ファイル＝並行可。記述は requirements.md §3.3/§3.4 と整合させる）
- **完了条件:** 記述が新挙動と一致（cascade/rescue/reserve の記述残存なし）
- **対応Issue:** #1125
## 実装順序（Wave = 並行実装できるタスクの組）
- **Wave 1**（互いに変更領域が重ならない・並行可）: タスク1（SystemSettingService）／タスク2（Repository）／タスク3（新規 Tracker クラス）／タスク5（FE settings）／タスク6（docs）
- **Wave 2**: タスク4（LotteryService 本体。T1・T2・T3 に依存。SystemSettingService の旧 getter 撤去も含むため T1 の後）
