---
status: completed
audit_source: 会話内レポート (2026-04-27 抽選機能監査)
selected_items: [1, 2, 3, 4, 6, 9]
---

# 抽選機能 改修要件定義書（v3）

## 1. 改修概要

### 対象機能
抽選機能（`/api/lottery/*`、`/admin/lottery`、`/lottery/results`、`/lottery/waitlist`、`/lottery/offer-response`）

### 改修の背景
2026-04-27 の `/audit-feature 抽選機能` 実施により、以下の重大な問題が検出された:
- 確定ボタンの導線が二系統に分かれており、片方（`LotteryResults.jsx`）は seed なしで `confirm` を呼ぶため必ず 500 エラーになる dead UI
- `findMonthlyLoserPlayerIds` クエリが organization_id でフィルタされず、別団体の落選経験者が「優先当選」枠に混入しうる
- `/lottery/results`, `/my-results`, `/executions` が組織スコープを持たず、PLAYER が他団体の参加者・抽選履歴を取得できる
- 確定時の伝助書き戻し失敗が silent catch で握り潰され、確定済み DB と伝助の整合性が暗黙に崩れるリスクがある
- `LotteryExecution.getPriorityPlayerIds()` の JSON parse 失敗が silent swallow で、再抽選時に「優先選手なし」が無警告で発生しうる
- 仕様書 §3.7.7「LotteryScheduler 毎日0:00」と実装（`@Scheduled` コメントアウト中）が乖離

### 改修スコープ
監査レポートの推奨アクションのうち以下 6 項目に対応する。

| # | 優先度 | 内容 |
|---|------|------|
| 1 | 高 | `LotteryResults.jsx` の確定ボタン削除（dead UI） |
| 2 | 高 | `findMonthlyLoserPlayerIds` に organization_id フィルタ追加 |
| 3 | 高 | `/lottery/results`, `/my-results`, `/executions` に organization スコープ追加 |
| 4 | 中 | 確定時の伝助書き戻し失敗をレスポンスに伝搬 |
| 5 | 中 | 仕様書 §3.7.7 の自動抽選スケジューラ記述を実態（手動運用のみ）に合わせて更新 |
| 6 | 低 | `LotteryExecution.getPriorityPlayerIds()` の JSON parse 失敗時に `log.warn` 追加 |

スコープ外（別 PR で対応する/しない判断は今後）: プレビュー→確定間の PENDING 増減対策、`LotteryController` 責務分離、`validatePriorityPlayerIds` の N クエリ統合、`LotteryExecution` の DTO 化、`session.setCapacity` 副作用排除など。

---

## 2. 改修内容

### 2.1 `LotteryResults.jsx` の確定ボタン削除（高）

**現状の問題**
- `LotteryResults.jsx` L18-19, L50-65 に `confirmed`/`lotteryExecuted` state と `handleConfirmLottery` ハンドラが残存。
- `lotteryAPI.confirm(year, month, organizationId)` を **seed なし・priorityPlayerIds なし** で呼ぶため、`LotteryController.confirmLottery` 側で `seed == null` の `IllegalStateException("シード値が指定されていません")` が必ず発生する。
- 仕様書 §3.7.9 では確定フローは `/admin/lottery`（`LotteryManagement.jsx`）に統一されており、`/lottery/results` 側の確定ボタンは仕様外の dead UI。

**修正方針**
- `LotteryResults.jsx` から確定ボタン関連の state・ハンドラ・JSX 一切を削除する。
  - `confirmed`、`lotteryExecuted` state、`fetchConfirmStatus` 関数、`handleConfirmLottery` 関数、確定ボタン JSX を削除。
- `lotteryAPI.getExecutions` 呼び出しが他に存在するか確認し、`LotteryResults.jsx` で唯一の利用箇所であれば残してもよいが、確定済み判定が不要になるため呼び出し自体を削除する。
- 結果として `/lottery/results` は「結果の閲覧専用画面」に純化される（PLAYER + ADMIN 共通の閲覧 UI）。

**修正後のあるべき姿**
- `/lottery/results` から確定ボタンが消え、確定操作は `/admin/lottery` 一系統に統一される。
- ADMIN が `/lottery/results` にいる場合に「確定したい」と思ったら `/admin/lottery` へ遷移する流れになる（既存の `Settings` 画面のリンクで遷移可能）。

### 2.2 `findMonthlyLoserPlayerIds` に組織フィルタ追加（高）

**現状の問題**
- `PracticeParticipantRepository.findMonthlyLoserPlayerIds`（L320-327）の JPQL は `YEAR / MONTH / status IN (WAITLISTED, DECLINED)` のみで絞り込み、`PracticeSession.organizationId` 条件がない。
- 結果として団体 A のセッションを再抽選するとき、団体 B で WAITLISTED/DECLINED だった選手が「連続落選救済」枠に入りうる。
- 月次抽選フロー（`executeLottery` → `processSession` → `processMatch`）では `monthlyLosers` を in-memory で同一実行内のみで蓄積するため影響なし。**影響を受けるのは `reExecuteLottery`（セッション単位再抽選）からの呼び出しのみ**（L828-829）。

**修正方針**
1. `findMonthlyLoserPlayerIds` のシグネチャに `organizationId` を追加し、JPQL に `AND ps.organizationId = :organizationId` 条件を追加。
2. 呼び出し側 `LotteryService.reExecuteLottery`（L740 近辺）で対象セッションの `organizationId` を取得し、リポジトリ呼び出しに渡す。
3. テスト: `LotteryServiceTest`（あれば）に「別団体の落選者が救済枠に混入しない」リグレッションテストを追加。なければ Repository 単体テストで JPQL の絞り込みを検証。

**修正後のあるべき姿**
- 再抽選時の優先当選（連続落選救済）判定が、対象セッションの団体内に閉じる。
- 仕様書 §3.7.2「同月内の別セッションで落選経験」の「同月内」が暗黙に「同団体・同月内」を意図していた前提に整合する。

### 2.3 抽選結果系 API への組織スコープ追加（高）

**現状の問題**
- `GET /lottery/results?year=&month=` (L209-221): `findByYearAndMonth` を呼び全団体のセッションを返す。
- `GET /lottery/my-results?year=&month=` (L237-258): 同上。`existsBySessionIdAndPlayerId` で当該プレイヤーの参加有無で絞り込みは行うが、参加者の Player 名や試合別のキャンセル待ち番号などは含まれる。
- `GET /lottery/executions?year=&month=` (L791-797): `findByTargetYearAndTargetMonth` を呼び全団体の抽選履歴を返す。
- いずれも `@RequireRole({SUPER_ADMIN, ADMIN, PLAYER})` で PLAYER に開放しているため、PLAYER が他団体のデータにアクセス可能。
- フロントの `LotteryResults.jsx` L42 の `executions.find(e => e.status === 'SUCCESS' && !e.sessionId)` も、別団体の月次実行を拾ってしまう副作用がある。

**修正方針**
1. **API シグネチャ拡張**: 3 エンドポイントに `organizationId` クエリパラメータ（required = false）を追加。
   - `organizationId` 指定あり → 当該団体のみ返す
   - `organizationId` 未指定 → 既存挙動を維持（後方互換: SUPER_ADMIN/ADMIN は全団体可、PLAYER は所属団体に絞り込む）
   - **PLAYER の場合の補正**: `currentUserId` の `PlayerOrganization` から所属団体一覧を取得し、対象セッションの `organizationId` がそこに含まれるもののみ返す。
   - **ADMIN の場合**: `adminOrganizationId` を強制適用（指定された `organizationId` が異なる場合は 403）。
2. **Repository 拡張**: `LotteryExecutionRepository` に `findByTargetYearAndTargetMonthAndOrganizationId` を追加（既に類似メソッドあり、JpaRepository 命名規則で派生）。`PracticeSessionRepository` の `findByYearAndMonthAndOrganizationId`／`findByOrganizationIdInAndYearAndMonth` は既存。
3. **フロントエンド対応**: `LotteryResults.jsx` で表示中の組織を選択する仕組み（既存の `LotteryManagement.jsx` 風の Org セレクタ）を追加するか、または `currentPlayer` のデフォルト所属団体を初期値とし `organizationId` を必ず付与して呼ぶ。**v3 では「PLAYER は所属団体に強制絞り込み」というサーバ側補正のみで対応**し、フロントの UI 変更は最小限にとどめる（現状 `LotteryResults.jsx` は `organizationId` を渡していないので変更不要、サーバ側で PLAYER の所属を見て絞る）。

**修正後のあるべき姿**
- PLAYER は自分の所属団体のセッション・抽選結果・抽選履歴のみ取得できる。
- SUPER_ADMIN は引き続き全団体を取得できる（`organizationId` を指定すれば絞り込み）。
- ADMIN は自団体に強制スコープ。

### 2.4 確定時の伝助書き戻し失敗をレスポンスに伝搬（中）

**現状の問題**
- `LotteryService.executeAndConfirmLottery`（L168-173）で `densukeWriteService.writeAllForLotteryConfirmation` を try/catch し `log.error` のみで握り潰す。
- 失敗時、確定済み DB（`confirmed_at != null`）と伝助の状態が乖離するが、API のレスポンスは正常終了として返るため、管理者が乖離に気付かない。
- 仕様書 §3.7.9「確定時に伝助への一括書き戻し（WON→○、WAITLISTED→△、それ以外→×）がトリガー」の暗黙前提が崩れている。

**修正方針**
1. `LotteryExecution` のレスポンスをそのまま返すのではなく、`ConfirmLotteryResponse` DTO に包んで返す:
   ```java
   public class ConfirmLotteryResponse {
       LotteryExecution execution;
       boolean densukeWriteSucceeded;
       String densukeWriteError; // 失敗時の概要メッセージ
   }
   ```
2. `executeAndConfirmLottery` のシグネチャに boolean 戻り値（または上記 DTO 戻り値）を追加し、catch 内で結果フラグを立てて返す。
3. フロントエンド（`LotteryManagement.jsx`）で `confirm` の結果を見て、`densukeWriteSucceeded == false` のとき警告ダイアログを出す:
   - 「抽選結果は確定されましたが、伝助への書き戻しに失敗しました。手動で伝助の状態を確認してください。」
4. ログレベル `error` は維持。

**修正後のあるべき姿**
- 伝助書き戻しが失敗してもレスポンスは 200 OK だが、`densukeWriteSucceeded: false` を含むレスポンスにより管理者が即座に乖離を認識できる。
- DB 側の確定状態は維持される（部分成功）ため、ロールバックはしない。

### 2.5 仕様書 §3.7.7 の自動抽選スケジューラ記述を実態に合わせる（中）

**現状の問題**
- 仕様書 L626（§3.7.7）に「`LotteryScheduler` 毎日0:00（月末日のみ実行）翌月分の自動抽選」と記載。
- `LotteryScheduler.java` L40, L57 で `@Scheduled` と `@EventListener` がコメントアウトされており、実際には自動抽選は動かない（手動のみ）。
- §3.7.1 L509 にも「自動抽選: 締切日時に LotteryScheduler が翌月全セッションの抽選を実行」とある。

**修正方針**
- 仕様書（`docs/SPECIFICATION.md`）を更新し、「自動抽選は現在停止中であり、運用は管理者の手動実行（`/admin/lottery`）のみ」と明記する。
- §3.7.1 の自動抽選の記述を「自動抽選（現在停止中・将来再開予定）」に書き換える、または手動運用に置き換える。
- §3.7.7 の `LotteryScheduler` の行は「現在停止中」を明記する。
- `LotteryScheduler.java` のクラスコメントにも同様の意図を補足。
- 実装側の有効化は今回スコープ外（別途経営判断・運用判断が必要）。

**修正後のあるべき姿**
- 仕様書を読んだ人が「自動抽選は現在動いていない」ことを正確に把握できる。

### 2.6 `LotteryExecution.getPriorityPlayerIds()` の log.warn 追加（低）

**現状の問題**
- `LotteryExecution.java` L86-91 で JSON parse 失敗時に `Collections.emptyList()` を返すが、ログ出力なし。
- 結果として、再抽選時に「直近の抽選から priorityPlayerIds を引き継ぐ」ロジックが silently 空リストを返し、優先選手指定が無警告で消える。

**修正方針**
- catch 句内で `log.warn("Failed to parse priorityPlayerIdsJson for LotteryExecution id={}: {}", this.id, e.getMessage())` を追加。
- `setPriorityPlayerIds` 側の catch も同様に `log.warn` を追加（こちらは保存失敗）。
- `@Slf4j` アノテーションを `LotteryExecution` クラスに追加する必要あり（Entity への Lombok 追加）。

**修正後のあるべき姿**
- 異常な JSON が DB に紛れ込んだ場合、ログでただちに検知できる。

---

## 3. 技術設計

### 3.1 API 変更

| エンドポイント | 変更内容 |
|--------------|---------|
| `POST /api/lottery/confirm` | レスポンスを `ConfirmLotteryResponse`（`{execution, densukeWriteSucceeded, densukeWriteError}`）に変更 |
| `GET /api/lottery/results` | `organizationId` クエリ追加（required = false）。PLAYER は所属団体に強制絞り込み |
| `GET /api/lottery/my-results` | 同上 |
| `GET /api/lottery/executions` | 同上 |

### 3.2 DB 変更

なし。

### 3.3 フロントエンド変更

| ファイル | 変更内容 |
|---------|---------|
| `src/pages/lottery/LotteryResults.jsx` | 確定ボタン関連の state/handler/JSX/`getExecutions` 呼び出しを削除 |
| `src/pages/lottery/LotteryManagement.jsx` | `confirm` レスポンスを参照し `densukeWriteSucceeded == false` のとき警告アラート表示 |
| `src/api/lottery.js` | `confirm` のレスポンス型に変化なし（中身は呼び出し側でハンドル） |

### 3.4 バックエンド変更

| ファイル | 変更内容 |
|---------|---------|
| `controller/LotteryController.java` | `getLotteryResults`, `getMyLotteryResults`, `getLotteryExecutions` に `organizationId` 引数追加 + PLAYER スコープ補正 + ADMIN スコープ強制。`confirmLottery` のレスポンスを `ConfirmLotteryResponse` に変更 |
| `service/LotteryService.java` | `executeAndConfirmLottery` の戻り値を `ConfirmLotteryResponse` に変更（または別メソッド追加）。`reExecuteLottery` で `findMonthlyLoserPlayerIds` 呼び出しに `organizationId` を渡す |
| `repository/PracticeParticipantRepository.java` | `findMonthlyLoserPlayerIds` に `organizationId` 引数追加・JPQL 修正 |
| `repository/LotteryExecutionRepository.java` | `findByTargetYearAndTargetMonthAndOrganizationId(int, int, Long)` を追加（既存の `findTopBy...` とは別の List 取得用） |
| `entity/LotteryExecution.java` | `@Slf4j` 追加。`getPriorityPlayerIds`/`setPriorityPlayerIds` の catch 句に `log.warn` を追加 |
| `dto/ConfirmLotteryResponse.java` | 新規作成 |
| `docs/SPECIFICATION.md` | §3.7.1 / §3.7.7 の自動抽選スケジューラ記述を更新 |

### 3.5 ドキュメント変更

| ファイル | 変更内容 |
|---------|---------|
| `docs/SPECIFICATION.md` | §3.7.1 自動抽選フロー、§3.7.7 スケジューラ表に「現在停止中」を明記 |

---

## 4. 影響範囲

### 影響を受ける既存機能

| 機能 | 影響内容 |
|------|---------|
| `/admin/lottery` 抽選確定 | レスポンス形が変わるため UI 側の参照を修正（軽微） |
| `/lottery/results`, `/my-results` | PLAYER は他団体データを取得できなくなる（仕様修正・期待動作） |
| `/lottery/executions` | 同上 |
| 月次抽選（`executeLottery`） | 影響なし（in-memory `monthlyLosers` を使用） |
| セッション再抽選（`reExecuteLottery`） | DB クエリの絞り込みが厳格化される（仕様修正・期待動作） |
| `confirmLottery` 旧パス（`LotteryResults.jsx`） | 削除されるため呼ばれなくなる |
| 伝助書き戻し（`writeAllForLotteryConfirmation`） | ロジック自体は変更なし、失敗時のレスポンス通知のみ追加 |

### 破壊的変更

- **API レスポンス**: `POST /api/lottery/confirm` のレスポンスが `LotteryExecution` から `ConfirmLotteryResponse` に変わる。フロントエンドで `confirm` を呼んでいる箇所は `LotteryManagement.jsx` のみ → 同 PR で更新する。
- **API パラメータ追加**: `organizationId` は optional のため後方互換あり。
- **PLAYER の閲覧範囲縮小**: PLAYER が他団体データを見られなくなるのは仕様修正だが、過去にこの挙動に依存した運用があった場合は要確認。本番運用上、団体は北大かるた会と わすらもち会の 2 つで、相互の選手の重複もあるため通常は問題にならないが、SUPER_ADMIN ロールで運用しているユーザーが PLAYER 視点で確認している場合は影響あり（その場合は SUPER_ADMIN ロールで開けば問題なし）。

---

## 5. 設計判断の根拠

### 5.1 なぜ `LotteryResults.jsx` の確定ボタンを「修正」ではなく「削除」するのか
- 仕様書 §3.7.9 で確定フローが `/admin/lottery` に明確に統一されている。
- 確定はプレビュー → seed 引き継ぎ → 確定の 3 ステップで成立しており、`LotteryResults.jsx` 側でこれを再現するには優先選手指定 UI など `LotteryManagement.jsx` の機能を移植する必要があり、二重実装になる。
- 既存の dead UI を削除する方が、責務分離が明確になる。

### 5.2 なぜ PLAYER の組織スコープをサーバ側で強制するのか
- フロントエンドの `LotteryResults.jsx` は現状 `organizationId` を渡しておらず、UI 改修なしで安全側に倒すには、サーバが PLAYER の所属を見て絞り込むのが最もシンプル。
- フロントから `organizationId` を必ず指定する設計に変えると、複数団体に所属するプレイヤーの場合に UI で団体選択させる必要があり、要件が膨らむ。今回は「自分の所属団体すべてを表示」で十分。

### 5.3 なぜ確定 API のレスポンスを破壊的に変えるのか
- 既存呼び出し元は `LotteryManagement.jsx` の 1 箇所のみで、同 PR で更新できる。
- 失敗状態の伝搬を `LotteryExecution` のフィールドに混ぜるより、ラッパー DTO の方が責務が明確。

### 5.4 なぜ自動抽選スケジューラの実装は触らず仕様書だけ更新するのか
- 自動抽選を有効化するか否かは運用判断（締め切りタイミング、リトライ戦略、失敗時通知など）が必要で、コード変更だけで済まない。
- 監査の趣旨は「仕様と実装が乖離している」点の解消であり、ドキュメント側を実態に合わせるのが現実的。
- 将来的に有効化する場合は別 PR で改めて要件定義する。

### 5.5 なぜ `findMonthlyLoserPlayerIds` の修正は再抽選パスのみで十分か
- 月次抽選（`executeLottery`）では `monthlyLosers` を in-memory で「同一実行内・同一団体内のセッション処理中に追加」していくため、DB クエリは使われない。
- 再抽選（`reExecuteLottery`）のみが DB から月内落選者を取得し、ここに organization_id がない問題が局在する。
