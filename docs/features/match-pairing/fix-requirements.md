---
status: completed
audit_source: 会話内レポート
selected_items: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
---
# 対戦組み合わせ・対戦変更機能 改修要件定義書

## 1. 改修概要

- **対象機能:** 対戦組み合わせ機能（PairingGenerator）、対戦変更機能（BulkResultInput内）
- **改修の背景:** `/audit-feature` による監査で検出された問題点・仕様書との乖離の修正
- **改修スコープ:** 全10項目（高2件・中4件・低4件）

## 2. 改修内容

### 2.1 [高] createdBy ハードコード修正

- **現状の問題:** `MatchPairingController.java` の3箇所（行93, 115, 133）で `createdBy = 1L` にハードコードされており、全組み合わせの作成者がID=1で記録される
- **修正方針:** 他コントローラ（MatchController等）と同様に `(Long) httpRequest.getAttribute("currentUserId")` で実際のログインユーザーIDを取得する
- **修正後のあるべき姿:** 組み合わせの作成・更新時に実際の操作者IDが記録される

### 2.2 [高] 仕様書API権限一覧の修正

- **現状の問題:** SPECIFICATION.md のAPI一覧（行1444-1447付近）で `auto-match`, `updatePlayer`, `delete`, `deleteByDateAndMatchNumber` の権限が「ALL」と記載されている
- **修正方針:** 実装に合わせて「ADMIN+」に修正
- **修正後のあるべき姿:** 仕様書のAPI権限が実装と一致

### 2.3 [中] 対戦履歴参照日数の統一（90日→30日）

- **現状の問題:** `MatchPairingService.java` の `MATCH_HISTORY_DAYS = 90` だが、仕様書（SPECIFICATION.md:292, DESIGN.md:1775）とコメント（Service:293）は「30日」
- **修正方針:** コード側を30日に修正（ユーザー確認済み）
- **修正後のあるべき姿:** コード・仕様書・コメントが全て「30日」で統一

### 2.4 [中] DESIGN.md AutoMatchingRequest 仕様更新

- **現状の問題:** DESIGN.md:1090 に `participantIds` フィールドが記載されているが、実装は `sessionDate` + `matchNumber` のみで、参加者はサーバーサイドでWON参加者から動的取得
- **修正方針:** 設計書のリクエスト仕様を現行の実装に合わせて更新
- **修正後のあるべき姿:** 設計書が現行のAPI仕様を正しく反映

### 2.5 [中] DESIGN.md batch APIリクエスト形式更新

- **現状の問題:** DESIGN.md:1114-1123 の batch APIリクエスト形式が古い（単純配列）
- **修正方針:** `MatchPairingBatchRequest`（`pairings` + `waitingPlayerIds`）に更新
- **修正後のあるべき姿:** 設計書が現行のAPI仕様を正しく反映

### 2.6 [中] side パラメータバリデーション追加

- **現状の問題:** `MatchPairingService.updatePlayer` で `side` パラメータが `"player1"` 以外はすべて `player2` として扱われ、不正な値でもエラーにならない
- **修正方針:** `side` が `"player1"` / `"player2"` 以外の場合に `IllegalArgumentException` を投げる
- **修正後のあるべき姿:** 不正な `side` 値に対して400エラーが返る

### 2.7 [低] BulkResultInput の getByDate に light=true 追加

- **現状の問題:** `BulkResultInput.jsx:86` で `pairingAPI.getByDate(sessionData.sessionDate)` を呼んでいるが、`light` パラメータ未指定のため不要な recentMatches データを取得
- **修正方針:** `pairingAPI.getByDate(sessionData.sessionDate, { light: true })` に変更
- **修正後のあるべき姿:** 結果入力画面で不要なデータの取得が回避される

### 2.8 [低] PairingGenerator 二重削除の解消

- **現状の問題:** `PairingGenerator.jsx:321-322` で `isEditingExisting` 時に明示的に `deleteByDateAndMatchNumber` を呼んだ後、`createBatch` のService側（`MatchPairingService.java:109`）でも同じ削除が実行される
- **修正方針:** フロントエンド側の明示的な削除呼び出しを除去
- **修正後のあるべき姿:** 既存編集時の削除が1回のみ実行される

### 2.9 [低] MatchPairingRepository 直接注入のService移動

- **現状の問題:** `MatchPairingController.java:33` で `MatchPairingRepository` が直接注入され、`validateAdminScopeByPairingId` メソッド内で使用されており、レイヤー違反
- **修正方針:** `MatchPairingService` に `getSessionDateById(Long id)` メソッドを追加し、Controller は Service 経由でセッション日付を取得する。Controller から `MatchPairingRepository` の注入を削除
- **修正後のあるべき姿:** Controller → Service → Repository のレイヤー構造が守られる

### 2.10 [低] scoreDifference 範囲の仕様統一

- **現状の問題:** 仕様書「1〜25」、UI実装「0〜25」、Entity コメント「1〜50」と3箇所で不一致
- **修正方針:** 仕様書・Entity コメントを「0〜25」に統一（UI実装が正）
- **修正後のあるべき姿:** 全箇所で「0〜25」と統一

## 3. 技術設計

### 3.1 API変更
- API仕様の変更なし（バリデーション追加のみ）
- #6 の `side` バリデーション追加により、不正値で400エラーが返るようになる（既存の正常系には影響なし）

### 3.2 DB変更
- なし

### 3.3 フロントエンド変更
- `BulkResultInput.jsx`: `pairingAPI.getByDate` 呼び出しに `{ light: true }` パラメータ追加
- `PairingGenerator.jsx`: `isEditingExisting` 時の明示的削除呼び出しを除去

### 3.4 バックエンド変更
- `MatchPairingController.java`: `createdBy` をリクエスト属性から取得、`MatchPairingRepository` 注入を削除
- `MatchPairingService.java`: `MATCH_HISTORY_DAYS` を90→30に変更、`side` バリデーション追加、`getSessionDateById` メソッド追加
- `Match.java`: `scoreDifference` コメント修正

### 3.5 ドキュメント変更
- `docs/SPECIFICATION.md`: API権限一覧の修正、scoreDifference 範囲の修正
- `docs/DESIGN.md`: AutoMatchingRequest/batch API仕様の更新

## 4. 影響範囲

- **既存機能への影響:** 最小限。バリデーション追加は正常系に影響なし。createdBy修正は監査証跡の改善のみ。対戦履歴日数変更（90→30日）により自動マッチングの参照範囲が狭くなるが、仕様上の意図通り
- **破壊的変更:** なし。全てのAPI仕様（エンドポイント・リクエスト・レスポンス形式）は変更なし
- **フロントエンド⇔バックエンド整合性:** 影響なし

## 5. 設計判断の根拠

- **createdBy修正:** 既存の他コントローラ（MatchController等）と同じパターンを採用。interceptorが既に `currentUserId` を設定済みのため追加実装不要
- **MATCH_HISTORY_DAYS:** ユーザー確認の結果、30日が正しい仕様。90日は実装時の変更が仕様書に反映されていなかった
- **side バリデーション:** 入力値の明示的検証はAPIの堅牢性向上に寄与。既存のフロントエンドは `"player1"` / `"player2"` のみ送信するため影響なし
- **二重削除解消:** Service側で既に削除を実施しているため、フロントエンドからの明示的削除は冗長。Service側に任せることで責務が明確になる
- **Repository直接注入の排除:** 標準的なレイヤードアーキテクチャの原則に従い、Controller から Repository への直接依存を排除
