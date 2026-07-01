---
status: completed
---
# 試合フォーム リデザイン 実装手順書

> 要件 = [requirements.md](requirements.md)（completed）／視覚 = [design-spec.md](design-spec.md)（locked）。
> 既存改修。テスト先行（書ける所は先にテスト）。**DB変更なし・マイグレーション不要**。

## 実装タスク

### タスク1: getById の会場名（venueName）確認・対応【backend】
- [x] 完了
- **対応Issue:** #962
- **概要:** BulkResultInput が使う `GET /practice-sessions/{id}` のレスポンスに `venueName` が入るか確認。`enrichSessionWithParticipants` を通っていなければ getById 経路でも `venueName` を埋める。
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/PracticeSessionController.java` — getById 系メソッドの enrich 経路確認
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/PracticeSessionService.java` — 必要なら venueName 埋め込み
  - `PracticeSessionServiceTest`（または Controller テスト）— getById で venueName が返る
- **依存タスク:** なし

### タスク2: 未参加相手の自動参加登録【backend・必須】
- [x] 完了
- **対応Issue:** #963
- **概要:** 試合保存（`MatchService.create`/`createDetailed`）で、相手が当日セッション未参加なら matchNumber に対しサーバ側で参加登録。**冪等**・同一トランザクション。直接API（`POST /participations`）の「PLAYERは自分のみ」ガードは温存し、試合記録の副作用としてサーバ内部で登録。
- **テスト先行（`MatchServiceTest`）:**
  - 未参加相手を含む試合作成 → 相手が参加登録される
  - 既に参加済み → 二重登録しない（冪等）
  - 相手なし／抜け番 → 登録しない
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchService.java` — 保存時の自動参加登録（`PracticeParticipantService` を内部利用）
  - 必要なら `PracticeParticipantService` にサーバ内部用メソッド（ガードなし）
  - `MatchServiceTest`（テスト）
- **依存タスク:** なし

### タスク3: 級の短縮表示ヘルパー【frontend】
- [x] 完了
- **対応Issue:** #964
- **概要:** kyuRank（"A級" 等）→ "(A)" を返すヘルパー。未設定は空文字。
- **テスト先行（`utils/rank.test.js`）:** "A級"→"(A)"、null/未設定→""
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/utils/rank.js` — ヘルパー追加
  - `karuta-tracker-ui/src/utils/rank.test.js` — テスト
- **依存タスク:** なし

### タスク4: MatchForm 刷新（選択モデル＋抜け番一本化＋級表示＋全面再スタイル）【frontend・主】
- [x] 完了
- **対応Issue:** #965
- **概要:** design-spec 最新形へ刷新。(a)対戦相手プルダウン = 当日参加者＋抜け番（全選手→参加者へ限定）、(b)「未参加から検索」簡易インクリメンタル検索（全選手−参加者−自分）、(c)抜け番プルダウン一本化（旧「抜け番として記録する」ボタン削除）、(d)相手の級 "(A)" 表示（タスク3利用・opponentId×全選手 突合）、(e)ヘッダー日付＋会場、(f)結果/枚数差/お手付き横並び・トグル文字色のみ・ピッカー数値＋単位中央密着・`mf-actions`、(g)デザイントークン（角丸10px/影なし/warm-taupe 等）。
- **テスト:** `MatchForm` ロジックテスト — 参加者母集団／検索フィルタ（参加者・自分を除外）／抜け番選択で bye モード／級表示
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/MatchForm.jsx`（主）
  - `karuta-tracker-ui/tailwind.config.js` または `src/index.css`（トークン）
  - 関連テスト
- **依存タスク:** タスク3

### タスク5: 会場名ヘッダーの横展開【frontend】
- [x] 完了
- **対応Issue:** #966
- **概要:** MatchResultsView / BulkResultInput のヘッダーに会場名（`venueName`）を表示（design-spec：日付＋会場ヘッダーの横展開）。既存の日付ナビ・操作は維持。`venueName` 無しは日付のみ。
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/matches/MatchResultsView.jsx`
  - `karuta-tracker-ui/src/pages/matches/BulkResultInput.jsx`
- **依存タスク:** タスク1（BulkResultInput は getById 経由で venueName が来ること）

### タスク6: 自動参加登録の影響範囲・回帰確認【backend】
- [x] 完了
- **対応Issue:** #967
- **概要:** 自動参加登録が 参加者数・容量（matchCapacityStatuses）・ペアリング・抽選・通知 に与える影響を確認し、想定外の副作用がないことを検証（必要なら回帰テスト追加）。
- **変更対象ファイル:**
  - 確認中心。必要に応じ関連サービス／テスト
- **依存タスク:** タスク2

### タスク7: ドキュメント更新【docs】
- [x] 完了
- **対応Issue:** #968
- **概要:** CLAUDE.md ルールに従い実装内容を反映。
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` / `docs/SCREEN_LIST.md` / `docs/DESIGN.md`
- **依存タスク:** タスク1〜6

## 実装順序
1. タスク1・タスク2・タスク3（依存なし。backend2点＋frontヘルパー、並行可）
2. タスク4（タスク3に依存）
3. タスク5（タスク1に依存）
4. タスク6（タスク2に依存）
5. タスク7（全タスクに依存）
