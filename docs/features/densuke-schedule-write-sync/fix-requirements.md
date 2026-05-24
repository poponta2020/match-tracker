---
status: completed
audit_source: 会話内ソースコード調査 + スパイク調査（2026-05-24）
selected_items: [1]
spike_results: docs/features/densuke-schedule-write-sync/spike-findings.md
---

# 伝助スケジュール書き込み同期 (アプリ→伝助 練習日 push) 改修要件定義書

## 1. 改修概要

- **対象機能:** アプリ側で `practice_sessions` を新規作成した際の伝助ページへの自動反映
- **改修の背景:**
  - 現在、アプリ→伝助の同期は「参加者の出欠」(`PracticeParticipant.dirty` フラグ経由) のみが実装されており、**「練習日 (PracticeSession)」自体の伝助への push は未実装**。
  - 結果として、伝助ページ作成後にアプリで新しい練習日を追加しても伝助ページには反映されず、現状は管理者が `densuke_urls` を削除して「作り直す」運用 ([DensukeManagement.jsx:231-260](../../../karuta-tracker-ui/src/pages/practice/DensukeManagement.jsx#L231-L260)) を強いられている。
  - スパイク調査（2026-05-24）により、伝助の `POST /update` エンドポイントを利用して**既存スケジュールに新規日程を末尾追記する API** が動作することを実証済み。既存 `densuke_row_ids` のインデックスは破壊されない。
- **改修スコープ:** PracticeSession 新規作成時の伝助スケジュール自動同期（**追加のみ**、削除は対象外）

---

## 2. 改修内容

### 2.1 項目1: PracticeSession 新規作成時に伝助スケジュールへ追記する

#### 現状の問題

- アプリで `practice_sessions` を新規作成しても、伝助ページの `schedule` 欄には反映されない
- 既存セッション群と伝助スケジュールの一貫性は、管理者の手動操作（伝助ページを作り直す）に依存している
- 「あとから1日追加したい」運用ケースで、伝助ページのURLが変わることになり、参加者・LINE通知の手間が大きい

#### 修正方針

**スパイク調査で判明した API (`POST https://densuke.biz/update`) を活用して、新規 `PracticeSession` 作成時に対応する伝助ページへ追加スケジュール文字列を push する。**

##### 同期トリガー
- `PracticeSessionService.createSession()` 等で新規セッションが作成された後、`afterCommit` フックで非同期 (`@Async`) に伝助 push を発火
- 既存 `DensukePageCreateService` の AFTER_COMMIT + @Async パターン ([DensukePageCreateService.java:157-167](../../../karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukePageCreateService.java#L157-L167)) を踏襲

##### push 対象判定
- 作成された `PracticeSession` の `(year, month, organizationId)` をキーに `densuke_urls` を検索
- 該当する伝助URLが存在する場合のみ push
- 存在しない場合は何もしない（伝助ページ未作成のケース）

##### スケジュール文字列の組み立て
- 既存の [`DensukePageCreateService.buildScheduleText()`](../../../karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukePageCreateService.java#L209-L262) を再利用
- `buildScheduleText` を package-private 維持 or ユーティリティに抽出して新サービスから呼び出し可能にする
- 追加分の `PracticeSession` のみを渡すことで「追記すべき文字列」が得られる

##### 重複防止（差分検出）
- 伝助側に既に同じ日付が存在する可能性がある（管理者が手動で追加していた等）
- push 前に [`DensukeScraper.scrape()`](../../../karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeScraper.java) で現在の伝助スケジュールを取得し、既存日付集合と比較
- 差分（伝助に無い日付）のみを `schedule` パラメータに含める
- 既に全て存在すれば push をスキップ

##### HTTP 呼び出し
- POST `https://densuke.biz/update` (Content-Type: application/x-www-form-urlencoded)
- 必須パラメータ:
  - `cd` = `densuke_urls.url` から抽出
  - `id` = 編集ページ HTML の `<input type="hidden" name="id" value="...">` から取得（既存 `DensukeWriteService.extractPageId()` 流用）
  - `postfix` = 空文字
  - `schedule` = 追加分の文字列
- Cookie: 必須かどうかスパイクでは未検証 — 安全側として GET `/list?cd=...` で `<CD>EDT` Cookie を取得しておき付与（既存 `DensukeWriteService` パターン踏襲）
- 期待レスポンス: 302 Found, Location `edit?cd=...&pw=...`
- 302 以外の場合は失敗扱い

##### 失敗時の挙動
- 即時 push に失敗した場合は WARN ログに記録
- リトライは行わない（実装シンプル化）
- 5分スケジューラ (`DensukeSyncScheduler`) で**フォロー同期**を追加し、伝助と practice_sessions の差分があれば push（自動回復）
- 重大な失敗 (HTTP 4xx/5xx) は管理者通知 (LINE/アプリ内通知) ✅

##### 競合条件
- 同一年月で並行 push が走ると、伝助の現スケジュール read → write の間に他のリクエストが入り、差分計算がズレるリスクあり
- 解決策: organization+year+month 単位で `synchronized` または DB 行ロックでシリアライズ
- 簡易案: `densuke_urls` レコードに対する `SELECT FOR UPDATE` で排他

#### 修正後のあるべき姿

- アプリ管理者が練習日を1日追加すると、5秒〜数十秒以内に伝助ページの候補日程欄にも反映される
- 既存日程・既存参加者出欠データは破壊されない
- 伝助ページを「作り直す」運用が不要になる
- 5分スケジューラのフォロー同期により、即時 push 失敗時も自動回復する
- 削除は引き続き手動運用（伝助ページの管理者編集 UI 経由）

---

## 3. 技術設計

### 3.1 API変更

なし。フロントエンドから見た既存の `POST /api/practice-sessions` 等のエンドポイントの挙動は変わらない（裏で同期処理が追加されるのみ）。

ただし、レスポンスに「伝助同期がトリガーされた」旨を含めるかは検討の余地（実装簡略化のため**含めない**方針）。

### 3.2 DB変更

なし（既存 `densuke_urls`, `practice_sessions`, `venues`, `venue_match_schedules` のみ利用）。

将来のリトライ実装等で「未同期練習日」を管理するなら `practice_sessions.densuke_added_at` のようなカラムが欲しくなるが、本改修では実装しない（5分スケジューラのフォロー同期で代替）。

### 3.3 フロントエンド変更

なし。

将来的に「同期失敗の警告表示」を出すなら DensukeManagement.jsx の改修が必要だが、本改修では LINE/通知センターのみで対応。

### 3.4 バックエンド変更

| ファイル | 変更内容 |
|---------|---------|
| `DensukeScheduleWriteService.java` (新規) | 伝助への追加スケジュール push を担う新サービス。`pushSchedulesToDensuke(year, month, organizationId)` がエントリポイント。差分検出 → `POST /update` を実行。 |
| `DensukePageCreateService.java` | `buildScheduleText()` の可視性を package-private 維持（既に package-private）。新サービスから利用可能。仕様変更なし。 |
| `PracticeSessionService.java` | 練習日新規作成メソッドに `afterCommit` フックを追加し、新サービスの `@Async` メソッドを呼ぶ。複数セッションを一括作成する API なら、organization+year+month 単位でまとめて1回 push。 |
| `DensukeSyncScheduler.java` / `DensukeSyncService.java` | 5分スケジューラに「スケジュール差分の自動補完同期」ステップを追加。`practice_sessions` と伝助の差分があれば push。 |
| `DensukeWriteService.java` | `extractPageId()` メソッドを package-private に維持（既にそう）。新サービスからの利用を許可。または共通ユーティリティへ抽出。 |
| `LineNotificationService.java` | 同期失敗時の管理者通知メソッドを追加（既存の `sendDensukePageCreatedNotification` 等のパターン踏襲）。 |
| `DensukeScheduleWriteServiceTest.java` (新規) | 差分検出、push 動作、失敗時挙動、競合制御のテストを追加。 |
| `docs/SPECIFICATION.md` | アプリ→伝助の練習日同期セクションを追記 |
| `docs/DESIGN.md` | 新サービスのアーキテクチャ図・シーケンス追記 |
| `docs/伝助双方向同期.md` | 新しい同期方向を反映 |

---

## 4. 影響範囲

### 影響を受ける既存機能

- **PracticeSession 新規作成 API** — `afterCommit` フックの追加により、レスポンス時間に影響しないが、トランザクション後に非同期 push が走るようになる
- **DensukeSyncScheduler** — 5分ごとの実行内容に「スケジュール差分同期」ステップが追加される（既存の出欠同期 + 新規追加）
- **DensukePageCreateService** — 仕様は変更しないが、`buildScheduleText` を外部から呼ばれるようになる

### 影響を受けない既存機能

- 伝助→アプリの参加者同期 (`DensukeImportService`)
- アプリ→伝助の参加者同期 (`DensukeWriteService.writeToDensuke`)
- 抽選・キャンセル待ち・繰り上げ
- 伝助ページ初回作成 (`DensukePageCreateService.createPage`)
- DBスキーマ
- フロントエンド UI

### 破壊的変更

- なし
- 既存伝助スケジュールへの追記のみで、既存日程・参加者データは保護される
- 同期失敗時もアプリ側の練習日作成自体は成功する（疎結合）

### 注意事項（運用）

- 伝助 API は公式仕様ではなくリバースエンジニアリングに依存
- 伝助側の HTML 構造や API パスが将来変更されると壊れるリスクあり ([DensukeWriteService.java:704-714](../../../karuta-tracker/src/main/java/com/karuta/matchtracker/service/DensukeWriteService.java#L704-L714) と同じリスク)
- 障害発生時の調査用に、push 内容と response status をログに残す

### DB マイグレーション

- 不要

---

## 5. 設計判断の根拠

- **追加のみ同期、削除は手動:** ユーザー確認済み。削除は誤操作リスクが高く、参加者データも巻き添えになるため、安全側に倒して手動対応とする。`editdate` エンドポイントの調査は本改修のスコープ外。
- **トリガーは PracticeSession 作成付随 + afterCommit + @Async:** ユーザー確認済み。即時 UX 向上のため作成付随で起動する。トランザクション成立後に発火することで、ロールバック時に伝助だけ更新される事故を防ぐ。
- **5分スケジューラでフォロー同期:** 即時 push が失敗した場合の自動回復のため。リトライキューを別途実装するより軽量。
- **buildScheduleText の再利用:** 液フォーマットと バリデーションを単一ソースとして保つ。ロジック分岐を避ける。
- **重複防止に伝助 read を必須化:** push 前に伝助の現状を読み取って差分計算することで、二重追加事故を防ぐ。既存 `DensukeScraper` を流用してコスト低い。
- **競合制御に DB 行ロック (SELECT FOR UPDATE):** Spring シングルインスタンス前提の `synchronized` ではなく、DB レベルでロックすることで将来のマルチインスタンス化にも耐える。
- **失敗通知は LINE/アプリ内のみ、自動リトライなし:** 過剰実装を避ける。5分スケジューラの自動補完で実質的にリトライ動作する。
- **スパイク調査のテストイベント (`cd=Mswvm6w4XEYJAXse`) は実装完了後に削除予定:** 削除 API 調査は本改修の範囲外なので、削除コマンドが分かれば手動で消す。
