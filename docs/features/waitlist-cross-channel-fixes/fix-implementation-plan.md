---
status: completed
---
# キャンセル待ち機能（伝助/アプリ/LINE通知横断） 改修実装手順書

## 実装タスク

### タスク1: `decline-waitlist` / `rejoin-waitlist` のADMIN団体スコープ検証追加（[#2]）
- [x] 完了
- **概要:** `LotteryController.declineWaitlist` / `rejoinWaitlist` で、ADMIN ロール時に対象セッションの `organizationId` と ADMIN の管理団体IDを `AdminScopeValidator.validateScope` で照合し、不一致なら 403 を返す。PLAYER の自己判定は現状維持。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java` — `declineWaitlist`/`rejoinWaitlist` の冒頭で `practiceSessionRepository.findById(sessionId)` してセッション取得 → ADMIN なら `AdminScopeValidator.validateScope(role, adminOrgId, session.getOrganizationId(), "他団体のキャンセル待ちは操作できません")` を呼ぶ
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/controller/LotteryControllerDeclineWaitlistTest.java`（新規 or 既存に追加） — ADMINが他団体セッション宛に呼んだ場合 403、自団体は 200 のテスト
- **依存タスク:** なし
- **対応Issue:** #590

---

### タスク2: `getWaitlistStatus` のN+1解消（[#5]）
- [ ] 完了
- **概要:** `LotteryController.getWaitlistStatus` で各 participant ごとに `findById` していた `practiceSessionRepository` 呼び出しを `findAllById(sessionIds)` の1回にまとめる。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java` — `getWaitlistStatus` を `Set<Long>` 抽出 → `findAllById` → `Map<Long, PracticeSession>` 化 → ループ参照に変更
- **依存タスク:** なし
- **対応Issue:** #591

---

### タスク3: `renumberRemainingWaitlist` の `saveAll` 化（[#6]）
- [ ] 完了
- **概要:** 個別 `save()` ループを `saveAll` に置換する。Hibernate のバッチ更新設定（`hibernate.jdbc.batch_size` および `order_updates`）も確認し、未設定なら `application.yml` に追加する。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/WaitlistPromotionService.java` — `renumberRemainingWaitlist` 内で番号差分があるレコードを `dirtyList` に蓄積し、ループ後に `practiceParticipantRepository.saveAll(dirtyList)` を1回呼ぶ
  - `karuta-tracker/src/main/resources/application.yml` または `application-*.yml` — `spring.jpa.properties.hibernate.jdbc.batch_size: 50` および `hibernate.order_updates: true` を未設定なら追加（要既存設定確認）
- **依存タスク:** なし
- **対応Issue:** #592

---

### タスク4: `*Suppressed` 系メソッドのトランザクション境界をJavadocで明記（[#3]）
- [ ] 完了
- **概要:** `cancelParticipationSuppressed` / `respondToOfferDeclineSuppressed` / `expireOfferSuppressed` / `demoteToWaitlistSuppressed` の Javadoc に「呼び出し元のTX境界を引き継ぐ（個別コミットには非依存）」「個別コミットが必要な場合は呼び出し元側で `@Transactional` を付けないこと」を明記する。`LotteryController.cancelParticipation` のコメントも、なぜこのメソッドに `@Transactional` を付けてはならないかを補足する形に書き換える。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/WaitlistPromotionService.java` — 4メソッドの Javadoc 更新
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java` — `cancelParticipation` 冒頭コメントの更新
- **依存タスク:** なし
- **対応Issue:** #593

---

### タスク5: `LotteryController.cancelParticipation` の個別TX保証テスト追加（[#3]）
- [ ] 完了
- **概要:** 複数 participantId のうち1件で例外が発生しても他のキャンセルが確定（個別コミット）することを検証する統合テストを追加。これにより上流に `@Transactional` が誤って付与された場合に CI で検出できる。
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/controller/LotteryControllerCancelTest.java` — 既存テストに「3件中1件で例外 → 残り2件は CANCELLED に確定」のケースを追加
- **依存タスク:** タスク4（#593）
- **対応Issue:** #594

---

### タスク6: `editParticipants` の WON→CANCELLED を `cancelParticipationSuppressed` 委譲に変更（[#1]）
- [ ] 完了
- **概要:** `LotteryService.editParticipants` 内の `statusChanges` ループで、WON→CANCELLED の場合に既存の独自実装（直接 `setStatus` + `promoteNextWaitlisted` + `!isToday` ガード）を撤去し、`waitlistPromotionService.cancelParticipationSuppressed(participantId, null, null)` を呼び出す形に置き換える。返り値の `AdminWaitlistNotificationData` を蓄積し、`dispatchSameDayCancelNotifications` で当日分を分離してから、残った通常分を `sendBatchedAdminWaitlistNotifications` + プレイヤー向け統合通知でディスパッチする。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryService.java` — `editParticipants` の `statusChanges` ループ内の WON→CANCELLED 分岐を書き換え。`!isToday` ガードを撤去。当日12:00以降の `SameDayCancelContext` は `dispatchSameDayCancelNotifications` 内で afterCommit 登録される
- **依存タスク:** タスク4（#593: Javadoc更新）
- **対応Issue:** #595

---

### タスク7: `editParticipants` の当日分岐テスト追加（[#1]）
- [ ] 完了
- **概要:** `editParticipants` の WON→CANCELLED について以下3ケースをカバーするテストを追加：
  - (a) 当日でないセッション → 通常繰り上げが発動
  - (b) 当日12:00前 → 通常繰り上げが発動
  - (c) 当日12:00以降 → `SameDayCancelContext` 経由で当日補充フローが afterCommit 登録される
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/LotteryServiceEditParticipantsTest.java`（既存ファイル名は要確認）にケース追加、または `LotteryServiceTest` に追加
- **依存タスク:** タスク6（#595）
- **対応Issue:** #596

---

### タスク8: `WaitlistStatus.jsx` のエラーバナー表示（[#8]）
- [ ] 完了
- **概要:** `fetchStatus` の catch 節で `error` ステートをセットし、JSX 上部にエラーバナーを表示する。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/lottery/WaitlistStatus.jsx` — `useState(null)` で error ステート追加、catch でセット、JSX に `{error && <div className="bg-red-50 border border-red-200 text-red-700 p-3 rounded mb-3">{error}</div>}` を追加
- **依存タスク:** なし
- **対応Issue:** #597

---

### タスク9: ドキュメント更新
- [ ] 完了
- **概要:** 改修内容を以下のドキュメントに反映する。
  - `docs/SPECIFICATION.md` 3.7（抽選結果操作）に「管理者手動編集の WON→CANCELLED 時、当日12:00を境界に通常繰り上げ／当日補充フローに分岐する」旨を追記
  - `docs/SPECIFICATION.md` セキュリティ表に「`/decline-waitlist` `/rejoin-waitlist` は ADMIN 団体スコープ検証あり」を追記
  - `docs/DESIGN.md` の `WaitlistPromotionService` 周辺に `cancelParticipationSuppressed` の TX境界契約を1行追記
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md`
  - `docs/DESIGN.md`
- **依存タスク:** タスク1〜8（実装が確定してから記述）
- **対応Issue:** #598

---

## 実装順序

1. **タスク1**（独立・小規模）: ADMIN スコープ検証 → 動作確認しやすい
2. **タスク2**（独立・小規模）: N+1解消
3. **タスク3**（独立・小規模）: `saveAll` 化
4. **タスク4**（独立）: Javadoc/コメント更新
5. **タスク5**（タスク4依存）: 個別TX保証テスト追加
6. **タスク6**（タスク4依存）: `editParticipants` 委譲リファクタ
7. **タスク7**（タスク6依存）: 当日分岐テスト追加
8. **タスク8**（独立・フロントエンド）: WaitlistStatus.jsx エラー表示
9. **タスク9**（最後）: ドキュメント反映

タスク1, 2, 3, 4, 8 は完全に独立しており並行実装可能。タスク5, 7 は対応するリファクタ完了後に追加。タスク9 は最後にまとめて。
