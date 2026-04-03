---
status: completed
note: statusは要件定義プロセスの完了（ドラフト承認済み）を意味する。各タスクの実装完了は個別のチェックボックスで管理する。
---

# 伝助同期の未入力保護 実装手順書

## 実装タスク

### タスク1: BYE生成時のdirty=false化
- [x] 完了
- **概要:** BYE（matchNumber=null）エントリが伝助同期をトリガーしないよう、生成時にdirty=falseで保存する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchPairingService.java` — createBatchのBYE生成（line 134）に`.dirty(false)`追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/ByeActivityService.java` — evaluatePracticeParticipantのBYE復元（line 217）に`.dirty(false)`追加
- **依存タスク:** なし
- **対応Issue:** #270

### タスク2: softDelete・updateSessionのBYE除外
- [x] 完了
- **概要:** 一括キャンセルやセッション更新でBYEエントリにdirty=trueが立たないようにする
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/PracticeParticipantRepository.java` — `softDeleteByPlayerIdAndSessionIds`のWHERE句に`AND p.matchNumber IS NOT NULL`追加。`softDeleteBySessionIdAndMatchNumber`にも防御的に同条件追加
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeSessionService.java` — updateSessionの削除ループ（line 391）先頭に`if (p.getMatchNumber() == null) continue;`追加
- **依存タスク:** なし
- **対応Issue:** #271

### タスク3: 通常同期のdirty行限定送信（最重要）
- [x] 完了
- **概要:** 通常同期でdirty行に対応するスロットのみformDataに含めるよう変更し、未登録マスの×上書きを防止する
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/repository/PracticeParticipantRepository.java` — `findDirtyForDensukeSync`メソッド追加（dirty=true AND matchNumber IS NOT NULL）
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeWriteService.java` — writeToDensukeInternalのdirty抽出を新メソッドに切替（line 155-156）。writePlayerToDensukeのループにdirtyKeysフィルタ追加（line 440-461）
- **依存タスク:** タスク1, タスク2（BYE隔離が先に完了していること）
- **対応Issue:** #272

### タスク4: テスト追加
- [x] 完了
- **概要:** 通常同期のdirty行限定送信、抽選確定同期の既存挙動維持、BYE生成のdirty=falseを検証するテストを追加する
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/DensukeWriteServiceTest.java` — 通常同期でdirty行のみ送信されること、未登録マスが送信されないこと、抽選確定同期の既存挙動維持
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/MatchPairingServiceTest.java` — createBatchでBYE生成時にdirty=falseであること
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/ByeActivityServiceTest.java` — BYE復元時にdirty=falseであること
- **依存タスク:** タスク1, タスク2, タスク3
- **対応Issue:** #273

#### テストシナリオ詳細

**シナリオ1: 通常同期 — dirty行のみ送信**
- テストデータ: プレイヤーAが2セッション（4/12, 4/19）×3試合の月。4/12の1試合目のみdirty=true（WON）、他はdirty=false or 未登録
- 期待値: formDataに`join-{4/12の1試合目のrowId}=3`のみ含まれる。4/12の2,3試合目、4/19の全マスはformDataに含まれない
- 検証ポイント: `Jsoup.connect().data()`に渡されるformDataのキーを検証

**シナリオ2: 通常同期 — 未登録マスが送信されない**
- テストデータ: プレイヤーBが4/12の1試合目にdirty=true（WON）、4/19には一切登録なし（practice_participantsに行なし）
- 期待値: 4/19に関するjoin-キーがformDataに一切含まれない
- 検証ポイント: pp=null→value=1(×)の経路が通らないこと

**シナリオ3: 通常同期 — BYE（matchNumber=null）が同期対象外**
- テストデータ: プレイヤーCにmatchNumber=null, dirty=trueのBYEエン��リのみ存在（他にdirtyな正規エントリなし）
- 期待値: プレイヤーCは同期対象にならない（writePlayerToDensukeが呼ばれない）
- 検証ポイント: findDirtyForDensukeSyncがBYEを返さないこと

**シナリオ4: 抽選確定同期 — 既存挙動維持**
- テストデータ: 同月の全プレイヤーの全マス（WON/WAITLISTED/未登録混在）
- 期待値: WON/WAITLISTED/OFFERED/PENDINGのマスのみformDataに含まれる。未登録・CANCELLED・DECLINEDは含まれない
- 検証ポイント: lotteryConfirmation=trueのパスで既存のisActiveStatusフィルタが維持されること

**シナリオ5: BYE生成 — dirty=false**
- テストデータ: createBatchで奇数人数の組み��わせ作成、waitingPlayerIds指定あり
- 期待値: 生成されたBYEエントリ（matchNumber=null）のdirtyがfalseであること

**シナリオ6: BYE復元 — dirty=false**
- テストデータ: 全ABSENTだったByeActivityを非ABSENTに更新し、evaluatePracticeParticipantでBYE復元
- 期待値: 復元されたBYEエントリのdirtyがfalseであること

### タスク5: 本番データ補正SQL
- [x] 完了
- **概要:** 既存のBYE dirtyノイズを一掃する補正SQLを作成・実行する
- **変更対象ファイル:**
  - `database/fix_bye_dirty_flag.sql` — 補正SQL格納
- **依存タスク:** タスク3（コード修正がデプロイされた直後に実行）
- **対応Issue:** #274

#### 実行手順

1. タスク1〜4のコード変更をmainブランチにマージし、Render.comにデプロイする
2. デプロイ完了を確認（`/actuator/health`でUP確認）
3. Render PostgreSQLに接続し、以下のSQLを実行する：
   ```sql
   -- 事前確認: 対象件数の把握
   SELECT COUNT(*) FROM practice_participants WHERE match_number IS NULL AND dirty = true;

   -- 補正実行
   UPDATE practice_participants SET dirty = false WHERE match_number IS NULL AND dirty = true;

   -- 事後確認: 対象が0件になったことを確認
   SELECT COUNT(*) FROM practice_participants WHERE match_number IS NULL AND dirty = true;
   ```
4. 次回の通常同期サイクル（5分以内）で不要な×上書きが発生しなくなったことを伝助上で確認する

## 実装順序

1. **タスク1**（BYE dirty=false化）— 依存なし、単純な変更
2. **タスク2**（softDelete・updateSession BYE除外）— 依存なし、タスク1と並行可
3. **タスク3**（通常同期dirty行限定送信）— タスク1,2のBYE隔離が前提
4. **タスク4**（テスト追加）— 全コード変更完了後
5. **タスク5**（本番データ補正SQL）— デプロイ直後に実行
