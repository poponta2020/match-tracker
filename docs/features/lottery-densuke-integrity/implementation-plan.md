---
status: completed
---
# 抽選機能・伝助連携 整合性改修 実装手順書

要件定義書: `docs/features/lottery-densuke-integrity/requirements.md`

> 方針: 各タスクは可能な限り独立してレビュー・マージできる粒度に分割する。定員判定の一貫化（A-2/B-5/B-1）は相互に関連するため近接して実装するが、タスクは分ける。テスト(T11)は各改修タスクに近接して随伴させることを推奨するが、独立タスクとしても管理する。

## 実装タスク

### タスク1（A-1）: 試合別参加者編集をWON/PENDING限定＋確認ダイアログ
- [x] 完了
- **概要:** 試合別参加者編集の保存でキャンセル済(×)/待機(△)が意図せずWON化する反転を止める。編集対象・全置換をWON/PENDINGのアクティブ行に限定し、モーダル初期選択をplayerIdベースにして確認ダイアログを追加する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeParticipantService.java` — `setMatchParticipants` をWON/PENDINGのみ全置換に変更（待機・キャンセル系行は温存）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/PracticeSessionDto.java` — `MatchParticipantInfo` に `playerId` 追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeSessionService.java` — `enrichDtoWithMatchDetails` で playerId を詰める
  - `karuta-tracker-ui/src/components/MatchParticipantsEditModal.jsx` — 初期選択をWON/PENDINGのplayerIdに限定、対象説明文、保存前確認ダイアログ
- **依存タスク:** なし
- **対応Issue:** #972

### タスク2（A-2）: 締切後〜抽選前登録のPENDING化＋抽選の定員差引
- [x] 完了
- **概要:** MONTHLYで抽選未実行の窓の新規登録をPENDING（抽選対象）にし、`processMatch` が既存WON/OFFEREDを定員から差し引いてから抽選することで即WON＋定員超過を防ぐ。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeParticipantService.java` — `registerAfterDeadline` に「抽選未実行→PENDING」の分岐
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryService.java` — `processMatch` の定員判定で既存WON/OFFEREDを控除
- **依存タスク:** なし
- **対応Issue:** #973

### タスク3（B-5）: Phase3-A6当日昇格の空き判定を統一
- [ ] 完了
- **概要:** 伝助○によるWAITLISTED→WON昇格の空き判定を `isFreeRegistrationOpen` に統一し、OFFERED枠算入・WAITLISTED残存の扱いを全経路で揃える。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeImportService.java` — `processPhase3Maru` の WAITLISTED 分岐の空き判定を差し替え
- **依存タスク:** なし
- **対応Issue:** #974

### タスク4（B-1）: 容量拡張OFFEREDを要承諾に統一
- [ ] 完了
- **概要:** 容量拡張による昇格OFFEREDに応答期限を付与しオファー通知を送る（auto-confirm廃止、既存OFFERED期限一律クリアも廃止）。全OFFEREDを「期限付き・要承諾」に統一する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/WaitlistPromotionService.java` — `promoteWaitlistedAfterCapacityIncrease` で `calculateOfferDeadline` を設定、オファー通知送信、期限一律クリア廃止
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java`（必要に応じて）— 昇格オファー通知の送信経路
- **依存タスク:** なし
- **対応Issue:** #975

### タスク5（A-4-a）: 正規化名キー衝突の検知＋管理者通知
- [ ] 完了
- **概要:** 読み取り/書き込みのメンバー名マップ構築時に、同一正規化キーへ複数のplayerId/選手名が対応する衝突を検知し、当該名の取込・書込をスキップして「名寄せ衝突」を管理者へ通知する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeImportService.java` — `playerNameMap` 構築時の衝突検知＋当該名スキップ
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeWriteService.java` — `extractAllMemberMappings` の衝突検知
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java` / `NotificationService.java` — 名寄せ衝突通知種別追加（`ADMIN_` プレフィックス規約準拠）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/entity/Notification.java`（NotificationType追加が必要な場合）
- **依存タスク:** なし
- **対応Issue:** #976

### タスク6（A-4-b）: 重複4名の統合スクリプト作成＋本番適用
- [ ] 完了
- **概要:** 正規化キー衝突源である未統合重複4名（川瀬/高橋/山野/むらやま）を、過去 #932 と同方式（マスター選択・参照付け替え・重複側 `deleted_at` 論理削除、`players.name` UNIQUE制約に配慮）で統合し本番PostgreSQLへ適用する。
- **変更対象ファイル:**
  - `docs/features/lottery-densuke-integrity/merge-duplicates/` — 統合SQL・手順書・適用ログ（`database/` には置かない＝スキーマ変更でないため）
  - 本番適用: `c:\tmp\dbtool` の JDBC ツール（IPv4強制でNAT64回避）
- **依存タスク:** タスク5（#976。衝突検知が入った後に統合すると、統合漏れがあれば通知で検知できる）
- **対応Issue:** #977

### タスク7（A-3）: 確定書き戻し直前の伝助差分検知＋管理者通知
- [ ] 完了
- **概要:** 抽選確定の一括書き戻し直前に伝助を読み、○書き戻し予定なのに伝助側×の選手を差分検知し、確定はブロックせず管理者へ通知＋WARN、レスポンスに差分情報を含める。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeWriteService.java` — `writeAllForLotteryConfirmation` 直前の scrape 差分検知
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryService.java` — `executeAndConfirmLottery`/`confirmLottery` で差分情報を集約
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/ConfirmLotteryResponse.java` — 伝助差分フィールド追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java` — 確定前伝助差分通知
  - `karuta-tracker-ui/src/pages/lottery/LotteryManagement.jsx` — 差分の表示（alert からの改善）
- **依存タスク:** なし
- **対応Issue:** #978

### タスク8（B-2）: プレビュー↔確定の母集団突合
- [ ] 完了
- **概要:** プレビュー応答に母集団シグネチャ（対象PENDING参加者ID集合のハッシュ）を含め、確定時に再計算・照合して不一致なら409を返し再プレビューを促す。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryService.java` — シグネチャ算出、confirm時の照合
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java` — preview応答/confirmリクエストの入出力拡張、409処理
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/`（プレビュー応答DTO / `LotteryExecutionRequest`）— シグネチャフィールド
  - `karuta-tracker-ui/src/pages/lottery/LotteryManagement.jsx` — シグネチャ保持・送信、409ハンドリング＋再プレビュー誘導
- **依存タスク:** なし
- **対応Issue:** #979

### タスク9（B-4）: 参加登録の楽観ロック
- [ ] 完了
- **概要:** 参加状況取得に版情報（対象行の `max(updated_at)` 等）を付与し、参加登録リクエストで版照合、不一致なら409で再読込を促す。全置換方式は維持。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/PlayerParticipationStatusDto.java` — 版フィールド追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/PracticeParticipationRequest.java` — 版フィールド追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeParticipantService.java` — `getPlayerParticipationStatusByMonth` で版算出、`registerParticipations` で版照合409
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/PracticeSessionController.java` — 409レスポンス
  - `karuta-tracker-ui/src/pages/practice/PracticeParticipation.jsx` — 版保持・送信、409ハンドリング＋再読込
- **依存タスク:** なし
- **対応Issue:** #980

### タスク10（B-3）: 伝助行不一致の可視化＋row_id整合防御
- [ ] 完了
- **概要:** join-ID件数とスケジュール件数の不一致を書き込みステータス/管理者通知で可視化し、キャッシュ済み `densuke_row_ids` が現フォーム構造と矛盾する場合は破棄して再取得する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeWriteService.java` — `parseAndSaveRowIds`/`ensureRowIds` の不一致可視化・row_id整合検証・再構築
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LineNotificationService.java` — 伝助行不一致通知
- **依存タスク:** なし
- **対応Issue:** #981

### タスク11（D）: 回帰テスト追加
- [ ] 完了
- **概要:** 本改修の各項目（A-1〜A-4, B-1〜B-5）の反転経路回帰テストと、既存未カバーのアルゴリズム特性（連鎖落選・月内救済・一般枠30%保証・キャンセル待ち番号引き継ぎ・シード再現性）・正午12:00境界のテストを追加する。
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/LotteryServiceTest.java` ほか（processMatch特性・A-2定員差引・B-2シグネチャ）
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/DensukeImportServiceTest.java` / `PhaseCoverageTest`（A-4衝突・B-5空き判定）
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/DensukeWriteServiceTest.java`（A-3差分・B-3不一致）
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/WaitlistPromotionServiceTest.java`（B-1要承諾）
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/PracticeParticipantServiceTest.java`（A-1/A-2/B-4）
  - `karuta-tracker-ui/src/pages/**`（A-1モーダル・B-2/B-4の409ハンドリング・正午境界）
- **依存タスク:** タスク1〜10（#972〜#981）
- **対応Issue:** #982

### タスク12（C）: 仕様書・画面文言・ドキュメント整合
- [ ] 完了
- **概要:** 本改修で変わる挙動を仕様書/設計書に反映し、監査で見つかったドキュメント不整合（§番号重複、`WAITLIST_DECLINED`記載揺れ、`SameDayVacancyScheduler`欠落等）を修正、利用者が誤解しやすい点を画面文言に注記する。
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` — 抽選/伝助/参加の変更点反映、不整合修正
  - `docs/DESIGN.md` — 同上
  - `docs/SCREEN_LIST.md`（画面文言変更があれば）
  - フロント各画面（伝助管理・参加登録・抽選結果）の注記文言
- **依存タスク:** タスク1〜10（#972〜#981）
- **対応Issue:** #983

## 実装順序
1. タスク1（A-1、独立・小）
2. タスク2（A-2、独立）
3. タスク3（B-5、独立・小）
4. タスク4（B-1、独立）
5. タスク5（A-4-a 衝突検知、独立）
6. タスク6（A-4-b 重複統合、タスク5に依存・本番DB操作）
7. タスク7（A-3、独立）
8. タスク8（B-2、独立）
9. タスク9（B-4、独立）
10. タスク10（B-3、独立）
11. タスク11（D テスト、タスク1〜10に依存）
12. タスク12（C ドキュメント、タスク1〜10に依存）
