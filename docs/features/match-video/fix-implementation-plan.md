---
status: completed
parent_issue: 855
---
# match-video 改修実装手順書（日付起点候補API）

改修要件定義書: `docs/features/match-video/fix-requirements.md`
親Issue: [#855](https://github.com/poponta2020/match-tracker/issues/855)

## 実装タスク

### タスク1: 日付別候補API（DTO / Service / Controller）+ テスト
- [x] 完了
- **概要:** 動画登録用の「日付別候補取得API」`GET /api/match-videos/date-candidates?date=YYYY-MM-DD` を新設する。**参加日スコープ（hasSessionOnDateForUser）は適用せず**、組織スコープ（OrganizationScopeResolver）は維持。サーバ側で pairings（組み合わせ）+ matches（試合結果）を自然キー `(matchDate, matchNumber, min(p1,p2), max(p1,p2))` で統合・重複排除（matches 優先）し、各候補に `registered`（同自然キーの match_videos 存在）/ `hasResult` / `matchId` を付与。選手名はバッチ解決（N+1回避）
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/dto/MatchVideoDateCandidateDto.java` — 新規。`{ matchDate, matchNumber, player1Id, player1Name, player2Id, player2Name, hasResult, matchId, registered }`
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/service/MatchVideoService.java` — `getDateCandidates(LocalDate date, Long organizationId)` 追加。`MatchPairingService.getByDate(date, false, orgId)`（参加日スコープなし）/ `MatchRepository.findByMatchDateOrderByMatchNumber(date)` / `matchVideoRepository.findByMatchDate(date)` を統合
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/controller/MatchVideoController.java` — `GET /date-candidates` 追加（`@RequireRole` 全ロール、`OrganizationScopeResolver` で団体スコープ解決。既存 search/getByDate の流儀に合わせる）
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/service/MatchVideoServiceTest.java` — `getDateCandidates` のテスト追加（pairings+matches統合・重複排除でmatches優先・registered付与・非参加ユーザーでも候補が返る・選手名バッチ解決）
  - `docs/SPECIFICATION.md` — 新エンドポイント仕様を追記
  - `docs/DESIGN.md` — DTO/Service メソッドを追記
- **依存タスク:** なし
- **対応Issue:** #856

### タスク2: フロント DateSourceSelect を新APIに切り替え + テスト
- [ ] 完了
- **概要:** `matchVideoAPI.getDateCandidates(date)` を追加し、`VideoRegisterModal.jsx` の `DateSourceSelect` の候補取得を単一呼び出しに置き換える（pairings/matches/videos の3-wayクライアントマージを廃止）。`registered` フラグで「登録済み」グレーアウト＋選択不可、未登録相手（id 0/null）の「相手未登録」選択不可ガードは維持。選択時の送信ペイロード（自然キー）組み立ては従来どおり
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/api/matchVideos.js` — `getDateCandidates(date)` 追加（`GET /match-videos/date-candidates`）
  - `karuta-tracker-ui/src/components/VideoRegisterModal.jsx` — `DateSourceSelect` の候補取得・構築を新APIベースに変更（既存の「日付起点」表示・選択・送信の挙動は維持）。不要になった3-wayマージ/`candidateKey`等のクライアント側ロジックを整理
  - `karuta-tracker-ui/src/components/VideoRegisterModal.test.jsx` — 日付起点テストを新API（`getDateCandidates`）ベースに更新。非参加ユーザーでも候補が出る／`registered`で選択不可／未登録相手で選択不可／選択→register送信、を検証。既存の選手起点・固定モードのテストを壊さない
  - `docs/SCREEN_LIST.md` — 必要なら動画倉庫登録モーダルの説明を更新（ユーザー視点の挙動は不変のため軽微）
- **依存タスク:** タスク1（#856）
- **対応Issue:** #857

## 実装順序
1. **タスク1**（依存なし・バックエンドAPI）
2. **タスク2**（タスク1に依存・フロント切替）

## 運用メモ
- DBスキーマ変更なし・本番DBマイグレーション不要（既存テーブルの読み取りのみ）
- 参加日スコープを外すのは本エンドポイントのみ。既存 `GET /api/match-pairings/date` / `GET /api/matches?date=` は変更しない（他画面の挙動維持）
