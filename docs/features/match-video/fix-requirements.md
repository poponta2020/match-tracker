---
status: completed
audit_source: クロスレビュー#854 6回目の指摘 + Issue #855
selected_items: [1]
parent_issue: 855
---
# match-video 改修要件定義書（日付起点候補API）

## 1. 改修概要

### 対象機能
試合動画アクセス機能（match-video / PR #854・親 #844）の **動画倉庫 登録モーダル「日付から（DateSourceSelect）」** の候補取得。

### 改修の背景（監査で検出された問題）
PR #854 のクロスレビュー6回目で検出（→ Issue #855）。

動画倉庫の登録モーダル「日付から」タブは、指定日の候補取得に既存の以下を**クライアント側で3-wayマージ**して使っている:
- `GET /api/match-pairings/date`（`pairingAPI.getByDate`）
- `GET /api/matches?date=`（`matchAPI.getByDate`）
- `GET /api/match-videos?date=`（`matchVideoAPI.getByDate`）

このうち前2つは Controller 層で `hasSessionOnDateForUser(date, currentUserId)` が false のとき**空配列**を返す（`MatchPairingController.java:54` / `MatchController.java:48`）。そのため「その日の練習に参加していないが動画登録は許可されるユーザー」（撮影担当者・第三者登録者・管理者代理登録）は、仕様（登録は全選手可）に反して**日付起点では対象試合を選べない**。

選手起点（PlayerSourceSelect）は参加日スコープに依存しない（対象選手基準の `getByPlayerId`）ため回避策として機能するが、日付起点の本来の利便性が損なわれている。

### 改修スコープ（選択した項目）
- **項目1（中）**: 動画登録用の「日付別候補取得API」を新設し、参加日スコープに依存せず日付から候補を選べるようにする

## 2. 改修内容

### 2.1 日付別候補API の新設（バックエンド）

**現状の問題**
- 日付起点の候補が参加日スコープ（`hasSessionOnDateForUser`）に縛られ、非参加ユーザーに空配列が返る
- マージ・重複排除・登録済み判定がクライアント側に散在（3 API 呼び出し）

**修正方針**
- 動画登録専用の読み取り専用エンドポイント `GET /api/match-videos/date-candidates?date=YYYY-MM-DD` を新設する
- **参加日スコープ（`hasSessionOnDateForUser`）は適用しない**。候補=試合カード情報（誰がいつ何試合目に対戦したか）は当日結果一覧・動画倉庫で既にアプリ全体に可視のため、スコープ緩和によるセキュリティ低下はない。`@RequireRole` は全ロール
- サーバ側で pairings（組み合わせ）+ matches（試合結果）を**自然キー `(matchDate, matchNumber, min(p1,p2), max(p1,p2))` で統合・重複排除**し（matches 優先＝結果情報を保持）、各候補に **`registered`（同自然キーの動画が登録済みか）** フラグと `hasResult` / `matchId` を付与して返す
- 選手名はバッチ解決（N+1回避）

**修正後のあるべき姿**
- 撮影担当者など非参加ユーザーでも、日付を選べばその日の全候補（組み合わせ・試合）が表示され、登録済み・未登録が一目で分かる
- フロントは1回の呼び出しで候補を取得し、マージ・登録済み判定のクライアント側ロジックを削減できる

## 3. 技術設計

### 3.1 API変更
新規エンドポイント:

| メソッド | URL | 説明 | 権限 |
|---|---|---|---|
| GET | `/api/match-videos/date-candidates?date=YYYY-MM-DD` | 指定日の動画登録候補（参加日スコープなし） | 全ロール |

レスポンス（`MatchVideoDateCandidateDto` の配列・`matchNumber` 昇順）:
```json
[
  {
    "matchDate": "2026-06-12",
    "matchNumber": 1,
    "player1Id": 1,
    "player1Name": "山田太郎",
    "player2Id": 2,
    "player2Name": "佐藤花子",
    "hasResult": true,
    "matchId": 100,
    "registered": false
  }
]
```
- `hasResult`/`matchId`: 同自然キーの `matches` が存在する場合のみ true / 非null
- `registered`: 同自然キーの `match_videos` が存在する場合 true（フロントの「登録済み」グレーアウト判定に使用）
- 組織スコープ: 既存 `getByDate` と同様に `OrganizationScopeResolver` を適用（他団体の候補混入を防ぐ）。**参加日スコープのみ外す**

### 3.2 DB変更
なし（既存テーブル `match_pairings` / `matches` / `match_videos` の読み取りのみ）。本番DBマイグレーション不要。

### 3.3 フロントエンド変更
- `api/matchVideos.js`: `getDateCandidates(date)` を追加（`GET /match-videos/date-candidates`）
- `components/VideoRegisterModal.jsx` の `DateSourceSelect`:
  - 候補取得を `matchVideoAPI.getDateCandidates(date)` の単一呼び出しに置き換える（pairings/matches/videos の3-wayクライアントマージを廃止）
  - `registered` フラグで「登録済み」グレーアウト＋選択不可（従来挙動を維持）
  - 未登録相手（`player1Id`/`player2Id` が 0/null）の「相手未登録」選択不可ガードは維持（DTOの生の選手IDで判定。round-4 の整合性修正を踏襲）
  - 選択時の送信ペイロード（自然キー）組み立ては従来どおり

### 3.4 バックエンド変更
| 種別 | クラス | 内容 |
|---|---|---|
| dto | `MatchVideoDateCandidateDto` | 新規。`{ matchDate, matchNumber, player1Id, player1Name, player2Id, player2Name, hasResult, matchId, registered }` |
| service | `MatchVideoService` | `getDateCandidates(date, organizationId)` を追加。`MatchPairingService.getByDate`（参加日スコープなし）・`MatchRepository.findByMatchDateOrderByMatchNumber`・`matchVideoRepository.findByMatchDate` を統合し候補を構築。選手名バッチ解決 |
| controller | `MatchVideoController` | `GET /date-candidates` を追加（`@RequireRole` 全ロール、`OrganizationScopeResolver` で団体スコープ解決） |

## 4. 影響範囲

| 項目 | 影響 |
|---|---|
| 既存 `GET /api/match-pairings/date` `GET /api/matches?date=` | **変更なし**（参加日スコープを残す。他画面の挙動維持）。新エンドポイントを別途追加するのみ |
| `DateSourceSelect`（日付起点） | 候補取得元を新APIに差し替え。表示・選択・送信の挙動はユーザー視点で不変（非参加日でも候補が出るよう改善） |
| `PlayerSourceSelect`（選手起点）・対象固定モード | 影響なし |
| 動画倉庫の一覧・登録・再生 | 影響なし |
| DBスキーマ | 変更なし（読み取りのみ） |
| 破壊的変更 | なし（新規エンドポイント追加 + フロントの内部実装変更のみ。APIレスポンス互換性に影響なし） |

## 5. 設計判断の根拠

- **専用エンドポイント方式を採用**（既存 getByDate にスコープ回避フラグを足す案は不採用）: 参加日スコープを外す意図を1つの用途特化エンドポイントに**明示的に閉じ込め**、共有エンドポイントの契約を緩めないため。誤用リスクが小さい
- **参加日スコープを外すがセキュリティ低下なし**: 返すのは試合カードのメタ情報（対戦カード・試合番号・結果有無）であり、当日結果一覧（`MatchResultsView`）・動画倉庫で既に全選手に可視。組織スコープは維持して他団体混入は防ぐ
- **サーバ側統合 + registered フラグ**: マージ・重複排除・登録済み判定のロジックをサービス層に集約し単体テストしやすくする。フロントの3-way呼び出し/マージを削減
- **未登録相手ガードはフロント維持**: round-4 で入れた `id=0/null → 相手未登録` の選択不可は、DTOの生選手IDでフロント判定を継続（バックエンドは候補を返し、表示制御はフロント責務という既存の役割分担に合わせる）
