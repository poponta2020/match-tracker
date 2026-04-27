---
status: completed
audit_source: 会話内レポート（2026-04-27 /audit-feature キャンセル待ち機能）
selected_items: [1, 2, 3, 5, 6, 8]
---

# キャンセル待ち機能（伝助/アプリ/LINE通知横断） 改修要件定義書

## 1. 改修概要

### 対象機能
キャンセル待ち（`WAITLISTED`/`OFFERED`）に関わる以下の一連のフロー：
- アプリ・伝助・LINE postback 経由のキャンセル → 繰り上げオファー
- 当日12:00確定／12:00以降キャンセル補充フロー
- キャンセル待ち辞退／復帰
- オファー応答（承諾／辞退／期限切れ）

### 改修の背景
2026-04-27 の `/audit-feature` 監査で、複数チャネルにまたがる本機能について12件の懸念事項を検出。うち高優先度3件（バグ・セキュリティ）と中優先度3件（パフォーマンス・UX）を本改修のスコープとする。

### 改修スコープ
監査レポート推奨アクション #1, #2, #3, #5, #6, #8 の6件。

| # | 監査優先度 | 概要 | 種別 |
|---|---|---|---|
| 1 | 高 | `LotteryService.editParticipants` の当日キャンセル分岐を統一 | bug |
| 2 | 高 | `decline-waitlist`/`rejoin-waitlist` の ADMIN 団体スコープ検証 | bug（セキュリティ） |
| 3 | 高 | `*Suppressed` 系メソッドのトランザクション境界の明確化 | improvement |
| 5 | 中 | `getWaitlistStatus` の N+1 解消 | improvement |
| 6 | 中 | `renumberRemainingWaitlist` の `saveAll` 化 | improvement |
| 8 | 中 | `WaitlistStatus.jsx` の fetch エラー表示 | improvement |

スコープ外（別タスク扱い）：#4（ドキュメント整理）、#7（大規模リファクタ）、#9〜#12（緊急性低）。

---

## 2. 改修内容

### 2.1 [#1] `editParticipants` の当日 WON→CANCELLED ロジック整合性

**現状の問題:**
[LotteryService.java:1010](karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryService.java#L1010) の管理者手動編集経路で、WON→CANCELLED 時に「当日全体（午前午後問わず）」を繰り上げ対象から除外している：
```java
if (session != null && !lotteryDeadlineHelper.isToday(session.getSessionDate())) {
    // 繰り上げ発動
}
```
一方、通常キャンセル経路 [WaitlistPromotionService.cancelParticipationInternal:233](karuta-tracker/src/main/java/com/karuta/matchtracker/service/WaitlistPromotionService.java#L233) は `isAfterSameDayNoon()` で12:00を境界に分岐し、午前は通常繰り上げ、12:00以降は当日補充フロー（`SameDayCancelContext`）を起動する。

結果、**管理者が当日午前中に WON→CANCELLED 編集すると繰り上げが発動せず、12:00以降に編集すると当日補充通知も飛ばない**という静かな不具合がある。

**修正方針:**
`editParticipants` の WON→CANCELLED 分岐を、通常キャンセル経路と同じ三分岐ロジックに揃える。実装方法は2案：

- **案A（推奨）**: `editParticipants` から既存の `cancelParticipationSuppressed(participantId, null, null)` を呼び出して通知データを取得し、`dispatchSameDayCancelNotifications` に流す。当日12:00分岐・通常繰り上げ分岐・通知集約の全てが共通化される。
- 案B: `editParticipants` 内で `isAfterSameDayNoon` を判定し、当日12:00以降は `registerSameDayCancelAfterCommit` を呼び、それ以前は現状通り `promoteNextWaitlisted` を呼ぶ。

採用：**案A**。理由は監査レポート 5b の指摘通り、通知集約パターンを一本化することで重複ロジックを削減できるため。`editParticipants` 内で重複していた `setStatus(CANCELLED)` / `setDirty(true)` / `save` の3行も内部実装に委ねられる。

**修正後のあるべき姿:**
- 当日午前のキャンセル → 通常繰り上げ + 管理者バッチ通知 + プレイヤー向けオファー統合通知
- 当日12:00以降のキャンセル → `SameDayCancelContext` を介した当日補充フロー（キャンセル発生通知 + 空き募集通知 + 管理者通知）
- 当日以外のキャンセル → 通常繰り上げ + 管理者バッチ通知 + プレイヤー向けオファー統合通知

---

### 2.2 [#2] `decline-waitlist` / `rejoin-waitlist` の ADMIN 団体スコープ検証

**現状の問題:**
[LotteryController.java:534](karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java#L534) `declineWaitlist` および同557 `rejoinWaitlist` は `body.get("playerId")` を信用しており、ADMIN/SUPER_ADMIN は他団体のプレイヤーのキャンセル待ちを操作できる。PLAYERは自分のみに制限されているが、ADMIN は無制限。`AdminScopeValidator` パターンが他エンドポイント（`/monthly-applicants` など）で適用されているのに、ここだけ抜けている。

**修正方針:**
両エンドポイントで以下を実施：
1. `sessionId` から `PracticeSession` を取得
2. `session.getOrganizationId()` を ADMIN の管理団体IDと照合
3. ADMIN の場合のみ `AdminScopeValidator.validateScope(role, adminOrgId, session.getOrganizationId(), "他団体のキャンセル待ちは操作できません")` を呼ぶ
4. SUPER_ADMIN は従来通り全団体OK
5. PLAYER は従来通り `playerId == currentUserId` チェックを残す

**修正後のあるべき姿:**
ADMIN は自団体のセッションに対する `decline-waitlist` / `rejoin-waitlist` のみ実行可能。他団体セッションには `403 ForbiddenException`。

---

### 2.3 [#3] `*Suppressed` 系メソッドのトランザクション境界の明確化

**現状の問題:**
[LotteryController.java:292](karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java#L292) のコメントは「`cancelParticipationSuppressed` は個別 TX でコミットされる」と謳う。実装上 `cancelParticipationSuppressed` は `@Transactional`（デフォルト=`REQUIRED`）で、コントローラ自体は `@Transactional` 無しのため、結果的に1呼び出し1TXとなり**現状は意図通り動作**している。

ただし上流（コントローラまたは他の呼び出し元）に `@Transactional` が後から追加された瞬間、`REQUIRED` は既存TXに参加するため**ループ全件が単一TXに化け、途中で例外が出ると全件ロールバック**する。コメントの想定が崩れる潜在バグ。

なお同種の `*Suppressed` メソッドは `DensukeImportService`（`@Transactional` 配下）からも呼ばれており、こちらは**意図的にインポートTXに参加させて整合性を保っている**（インポート失敗時はキャンセル含めロールバック）。`REQUIRES_NEW` への一律変更は伝助同期の整合性を破壊するためNG。

**修正方針:**
`@Transactional` の伝播種別を一律で変えるのではなく、**呼び出し元の契約を契約として明示する**：

1. `LotteryController.cancelParticipation` のコメントを更新し、「このメソッドに `@Transactional` を付けてはならない理由」を明記する。
2. `WaitlistPromotionService` の `cancelParticipationSuppressed` / `respondToOfferDeclineSuppressed` / `expireOfferSuppressed` / `demoteToWaitlistSuppressed` の Javadoc に、「呼び出し元のTX境界を引き継ぐ（個別コミットには非依存）」「個別コミットが必要な場合は呼び出し元側で `@Transactional` を付けないこと」を明記する。
3. `LotteryController.cancelParticipation` の単体テストとして、複数 participantId のうち1件で例外が起きても他のキャンセルは確定する（個別TX）ことを検証する統合テストを追加する。これにより上流に `@Transactional` を付与した場合の意図せぬ挙動変更が CI で検出される。

**修正後のあるべき姿:**
- メソッドの契約と実装の意図がコメントで一致
- 上流に `@Transactional` を不用意に付けた場合、テストが失敗して気付ける

---

### 2.4 [#5] `getWaitlistStatus` の N+1 解消

**現状の問題:**
[LotteryController.java:499-527](karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java#L499-L527) `getWaitlistStatus` で、プレイヤーの WAITLISTED/OFFERED 件数ぶん `practiceSessionRepository.findById(p.getSessionId())` を発行している。10件待ちなら10回SELECT。

**修正方針:**
1. `waitlisted` から `sessionId` の集合を抽出
2. `practiceSessionRepository.findAllById(sessionIds)` で一括取得（Spring Data JPA 標準メソッド）
3. `Map<Long, PracticeSession>` に詰めて `entries` ループで参照

`PracticeSessionRepository` には `findAllById` が `JpaRepository` 経由で既に存在するため新規メソッド追加不要。

**修正後のあるべき姿:**
セッション件数Nに対し、SELECT は2回（参加者一覧 + セッション一括）に固定される。

---

### 2.5 [#6] `renumberRemainingWaitlist` の `saveAll` 化

**現状の問題:**
[WaitlistPromotionService.java:1390-1402](karuta-tracker/src/main/java/com/karuta/matchtracker/service/WaitlistPromotionService.java#L1390-L1402) で WAITLISTED/OFFERED の番号差分があるレコードを個別に `save()` ループ呼び出ししている。容量拡張時など大量再採番でN回UPDATEが発行される。

**修正方針:**
1. ループで番号変更が必要なレコードを `List<PracticeParticipant> dirtyList` に蓄積
2. ループ後に `practiceParticipantRepository.saveAll(dirtyList)` で1回だけバッチ保存

`saveAll` は内部的にループするが、Hibernate のバッチ更新設定（`hibernate.jdbc.batch_size`）が有効ならまとめてフラッシュされる。設定確認は調査タスクに含める。

**修正後のあるべき姿:**
番号変更件数Nに対し、`save()` 呼び出しは1回（`saveAll`）に集約される。

---

### 2.6 [#8] `WaitlistStatus.jsx` の fetch エラー表示

**現状の問題:**
[WaitlistStatus.jsx:14-28](karuta-tracker-ui/src/pages/lottery/WaitlistStatus.jsx#L14-L28) の `fetchStatus()` は API エラー時 `console.error` のみで `setEntries([])` のまま。ユーザーには「キャンセル待ちはありません」と表示され、500エラーで誤認する。

**修正方針:**
1. `error` ステートを追加（`useState(null)`）
2. catch 節で `setError('キャンセル待ち情報の取得に失敗しました。時間をおいて再度お試しください。')` をセット
3. JSX で `{error && <div className="bg-red-50 border border-red-200 text-red-700 p-3 rounded mb-3">{error}</div>}` を表示
4. 再試行ボタン（任意）はスコープ外

**修正後のあるべき姿:**
500エラー時、画面上部に赤色のエラーバナーが表示され、ユーザーが状態を正しく認識できる。

---

## 3. 技術設計

### 3.1 API 変更
**新規エンドポイント:** なし
**既存エンドポイントの変更:** なし（戻り値・リクエスト形式とも変更なし）
**振る舞い変更:**
- `PUT /api/lottery/admin/edit-participants`: 当日キャンセル時の通知が発火するようになる（従来は無音）
- `POST /api/lottery/decline-waitlist` `POST /api/lottery/rejoin-waitlist`: ADMIN が他団体プレイヤーを操作した場合 `403` を返す

### 3.2 DB 変更
なし。

### 3.3 フロントエンド変更
| ファイル | 変更内容 |
|---|---|
| [karuta-tracker-ui/src/pages/lottery/WaitlistStatus.jsx](karuta-tracker-ui/src/pages/lottery/WaitlistStatus.jsx) | `error` ステート追加、catch 節でエラーメッセージ設定、JSX にエラーバナー表示を追加 |

### 3.4 バックエンド変更
| ファイル | 変更内容 |
|---|---|
| [karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryService.java](karuta-tracker/src/main/java/com/karuta/matchtracker/service/LotteryService.java) | `editParticipants` 内の WON→CANCELLED 処理を `waitlistPromotionService.cancelParticipationSuppressed` 委譲に変更し、通知集約は `dispatchSameDayCancelNotifications` 経由でディスパッチ |
| [karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java](karuta-tracker/src/main/java/com/karuta/matchtracker/controller/LotteryController.java) | `declineWaitlist`/`rejoinWaitlist` に `AdminScopeValidator.validateScope` 追加。`getWaitlistStatus` を `findAllById` でN+1解消。`cancelParticipation` のコメントを更新 |
| [karuta-tracker/src/main/java/com/karuta/matchtracker/service/WaitlistPromotionService.java](karuta-tracker/src/main/java/com/karuta/matchtracker/service/WaitlistPromotionService.java) | `renumberRemainingWaitlist` を `saveAll` 化。`*Suppressed` 系4メソッドの Javadoc に契約を明記 |
| 新規テスト: `LotteryControllerCancelTest`（既存）に「ループ途中で例外が出ても他件は確定する」テスト追加 | 個別TX保証の検証 |
| 新規テスト: `LotteryControllerDeclineWaitlistTest`（新規 or 既存に追加）に ADMIN スコープ違反 → 403 のテスト | スコープ検証 |
| 新規テスト: `LotteryServiceEditParticipantsTest` または既存テストに「当日午前キャンセルで繰り上げ」「当日午後キャンセルで補充通知」のテスト | 当日分岐の整合性検証 |

### Controller / Service / Repository / Entity / DTO の変更まとめ
- **Controller**: `LotteryController` の3エンドポイント
- **Service**: `LotteryService.editParticipants`, `WaitlistPromotionService.renumberRemainingWaitlist`
- **Repository**: 変更なし（既存メソッドのみ利用）
- **Entity**: 変更なし
- **DTO**: 変更なし

---

## 4. 影響範囲

### 4.1 影響を受ける既存機能

| 機能 | 影響内容 |
|---|---|
| 抽選結果管理画面（`LotteryManagement.jsx` の「抽選確定後の参加者編集」） | 当日キャンセル時に通知が発火するようになる（従来は無音）。管理者UX改善 |
| 当日キャンセル補充フロー | `editParticipants` 経由のキャンセルでも 12:00 以降は当日補充フローが走る。LINE通知件数が増える可能性あり |
| キャンセル待ち辞退/復帰（PLAYER経路） | 振る舞い変更なし。PLAYERは引き続き自分のみ操作可能 |
| キャンセル待ち辞退/復帰（管理者経路） | ADMIN は自団体のみ操作可能に制限される。SUPER_ADMINは従来通り |
| キャンセル待ち状況画面 (`/lottery/waitlist`) | 表示性能改善（N+1解消）+ エラー時にメッセージ表示 |
| 容量拡張時の昇格（`promoteWaitlistedAfterCapacityIncrease`） | `renumberRemainingWaitlist` の `saveAll` 化で大量再採番時の性能改善 |
| 伝助同期（DensukeImportService）からの cancelParticipationSuppressed 呼び出し | 振る舞い変更なし（`@Transactional` 配下で `REQUIRED` 維持。Javadoc明記のみ） |
| 通常キャンセル経路（`/api/lottery/cancel`） | 振る舞い変更なし。コメント更新のみ |

### 4.2 共通コンポーネント・ユーティリティへの影響
- `AdminScopeValidator`: 既存ユーティリティを使うのみ。本体変更なし
- `lotteryDeadlineHelper`: 既存メソッド `isAfterSameDayNoon` を使うのみ。本体変更なし
- `dispatchSameDayCancelNotifications`: 既存メソッドを `editParticipants` から呼ぶのみ。本体変更なし

### 4.3 API・DBスキーマの互換性
- **API**: 破壊的変更なし。リクエスト/レスポンス形式は不変
- **DB**: スキーマ変更なし。`database/*.sql` の追加なし
- **フロントエンド契約**: WaitlistStatus.jsx は内部UI追加のみで API クライアントへの影響なし

### 4.4 デグレード懸念と緩和策
| 懸念 | 緩和策 |
|---|---|
| `editParticipants` の通知発火追加で大量LINE通知 | 既存のバッチ通知パターン（`sendBatchedAdminWaitlistNotifications`）を使うため、セッション×プレイヤー単位で集約済み |
| `cancelParticipationSuppressed` 委譲で `editParticipants` の処理順が変わる | 既存の追加処理（addition / waitlistReorders）は `editParticipants` 内で先後関係を維持。`statusChanges` 内の処理だけが `cancelParticipationSuppressed` に置き換わる |
| ADMIN のスコープ検証追加で、運用上必要だった操作が止まる | 現状 ADMIN が他団体のキャンセル待ちを操作する運用は無いと判断（要ユーザー確認）。万が一必要なら SUPER_ADMIN を使う運用に切り替え |
| `saveAll` 化で Hibernate バッチ設定が無効だと逆に遅くなる可能性 | `application.yml` の `hibernate.jdbc.batch_size` を確認し、未設定なら明示的に追加（実装タスク内で対応） |

---

## 5. 設計判断の根拠

### 5.1 #1 を「案A: cancelParticipationSuppressed 委譲」とした理由
監査レポートでも `WaitlistPromotionService` の通知集約パターンは「`*Suppressed` メソッドが通知データを返す → 呼び出し元で集約 → `sendBatched...` で1通送信」と一貫していると評価されている。`editParticipants` だけが独自実装で `setStatus(CANCELLED)` を直書きしているため、ここを既存パターンに揃えることで仕様乖離も将来の保守コストも同時に減らせる。

### 5.2 #3 で `REQUIRES_NEW` に変更しない理由
`DensukeImportService`（`@Transactional` 配下）が `cancelParticipationSuppressed`/`demoteToWaitlistSuppressed`/`respondToOfferDeclineSuppressed` を呼んでおり、現状は **意図的にインポートTXに参加して整合性を保っている**（インポート失敗時にキャンセルもロールバック）。`REQUIRES_NEW` に一律変更すると、伝助同期の途中で失敗した場合にキャンセルだけが先行コミットされてしまい、データ整合性が崩れる。コントローラ側のループは現状で個別TXとして動作しているため、契約をJavadoc/コメントで明示し、テストで保証する方針を採る。

### 5.3 #6 で `saveAll` を選び HQL UPDATE を選ばない理由
`renumberRemainingWaitlist` は対象レコードのうち番号差分があるものだけを更新する条件分岐がある。HQL `UPDATE` で同等の条件（`waitlist_number = newNumber WHERE id = ? AND waitlist_number != newNumber`）を一括発行するには、CASE WHEN や複数クエリが必要で複雑化する。`saveAll` + Hibernate バッチでも実用上十分な性能改善が見込めるため、シンプルな選択肢を採る。

### 5.4 #8 のスコープを最小限にした理由
監査レポートでは「再試行ボタン」の追加も提案されたが、過去のフロントエンド改修方針（`PR #449` などのパターン）に倣い、まずはエラー認識を確実にすることを優先。再試行ボタンや自動リトライは別UXタスクで扱う。

### 5.5 監査レポート #4 / #7 / #9 / #10 / #11 / #12 をスコープ外にした理由
- #4（ドキュメント整合）: 実装PRと別に「ドキュメント整理」PRで一括対応するほうが見通しが良い
- #7（`WaitlistPromotionService` 分割）: 1,404行のリファクタは別PRで設計議論が必要
- #9（DTO化）/ #10（分散ロック）/ #12（一括チェック）: 緊急性が低く、現状で問題が顕在化していない
- #11（キャンセル理由の管理者UI）: 機能追加に該当し、別途要望ヒアリングが必要
