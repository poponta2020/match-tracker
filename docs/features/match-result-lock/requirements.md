---
status: completed
---
# 結果入力済み対戦のロック機能 要件定義書

## 1. 概要

### 目的
対戦結果が入力済みのペアリング（対戦組み合わせ）を保護し、自動組み合わせの再生成や手動変更による意図しない上書き・削除を防止する。

### 背景・動機
練習当日はアプリで対戦組み合わせを作成する時間がなく、アナログで対戦を組んで試合をするケースがある。練習後に一部の選手がMatchFormから結果を入力するが、その後に別のユーザーがPairingGenerator画面で自動組み合わせを実行すると、既に入力済みの結果と矛盾するペアリングが生成される可能性がある。

## 2. ユーザーストーリー

### 対象ユーザー
- **選手（PLAYER）**: 練習後に自分の対戦結果を入力する
- **管理者（ADMIN / SUPER_ADMIN）**: 対戦組み合わせの作成・管理を行う

### 利用シナリオ

**シナリオ1: 結果入力による自動ロック**
1. 練習後、選手AがMatchFormから「選手Bと対戦して勝った（1回戦）」と入力する
2. システムが `matches` にレコードを保存すると同時に、`match_pairings` にも対応するレコードを自動生成する
3. 別のユーザーがPairingGenerator画面で1回戦の自動組み合わせを実行しても、選手A vs 選手Bのペアリングは保護され、残りの選手のみで自動組み合わせが実行される

**シナリオ2: ロック済みペアリングのリセット**
1. 管理者がPairingGenerator画面を開くと、結果入力済みのペアリングがグレーアウト＋「結果入力済」ラベルで表示される
2. 誤入力等でペアリングを解除したい場合、個別のリセットボタンを押す
3. 確認ダイアログに「この操作により以下の結果が消去されます」と対戦詳細が表示される
4. 「はい」を選択すると、対戦結果とペアリングの両方が削除され、ロックが解除される

## 3. 機能要件

### 3.1 画面仕様

#### PairingGenerator画面（対戦組み合わせ画面）の変更

**ロック済みペアリングの表示**
- 結果入力済みのペアリングはグレーアウトして表示する
- 「結果入力済」ラベルを表示する
- 操作ボタン（プレイヤー変更・削除）は非活性化する

**個別リセットボタン**
- 各ロック済みペアリングに「リセット」ボタンを表示する
- ボタン押下時に確認ダイアログを表示する：
  ```
  この操作により以下の結果が消去されます。よろしいですか？

  [選手A] vs [選手B]
  勝者: [勝者名]  枚差: [スコア]

  [はい] [いいえ]
  ```
- 「はい」選択時: `match_pairings` と `matches` の両方を削除する
- 「いいえ」選択時: 何もしない

**自動組み合わせボタンの挙動変更**
- ロック済みペアを除外し、未ロックのプレイヤーのみで自動組み合わせを実行する
- ロック済みペアはそのまま表示に残る

**バッチ保存の挙動変更**
- 保存時、ロック済みのペアリングは削除せず保持する
- 未ロックのペアリングのみ削除・再作成する

#### BulkResultInput画面（結果一括入力画面）の変更

- ある回戦でペアリング（`match_pairings`）が存在しない場合、「対戦組み合わせが作成されていません」とメッセージを表示する
- ADMIN以上のユーザーには、PairingGenerator画面へ遷移するボタンを表示する
  - ボタンテキスト例: 「対戦組み合わせを作成する」
  - 遷移先: `/pairings`（該当日付・回戦番号をクエリパラメータ等で渡す）

### 3.2 ビジネスルール

**ロック判定**
- ロック状態は `match_pairings` に対応する `matches` レコードが存在するかどうかで判定する（別フラグは持たない）
- 判定キー: `session_date` = `match_date`、`match_number` が一致、`player1_id`/`player2_id` が一致（正規化済み）

**結果入力時の match_pairings 自動生成**
- MatchForm/BulkResultInputから対戦結果を作成する際、同じ日付・回戦番号・プレイヤーペアの `match_pairings` レコードが存在しない場合のみ自動生成する
- 既に存在する場合はスキップする（重複作成しない）
- `match_pairings.created_by` は結果を入力したプレイヤー自身のIDとする

**結果編集時のロック維持**
- 対戦結果の編集（勝者・スコア変更）ではプレイヤーは変更されないため、`match_pairings` レコードへの操作は不要
- `matches` レコードが存在し続けるため、ロック状態は自動的に維持される

**結果削除時**
- 対戦結果が削除されると、対応する `matches` レコードがなくなるため、自動的にロック解除される
- `match_pairings` レコードは残る（ペアリング自体は維持）

**リセット操作**
- リセットは個別のペアリング単位で行う
- リセット時は `matches` レコードと `match_pairings` レコードの両方を削除する

## 4. 技術設計

### 4.1 API設計

#### 変更するエンドポイント

| メソッド | パス | 変更内容 |
|----------|------|----------|
| POST | `/api/matches` | 結果保存後、match_pairingが未存在なら自動生成 |
| POST | `/api/matches/detailed` | 同上 |
| PUT | `/api/matches/{id}` | 旧match_pairingを削除、保存後に再生成 |
| PUT | `/api/matches/{id}/detailed` | 同上 |
| GET | `/api/match-pairings/date-and-match` | レスポンスに `hasResult` フラグを追加 |
| POST | `/api/match-pairings/batch` | ロック済みペアリングを保持し、未ロックのみ削除・再作成 |
| POST | `/api/match-pairings/auto-match` | ロック済みプレイヤーを除外して自動組み合わせ |

#### 新規エンドポイント

| メソッド | パス | 説明 | 権限 |
|----------|------|------|------|
| DELETE | `/api/match-pairings/{id}/with-result` | ペアリングと対応する結果の両方を削除（リセット） | ADMIN以上 |

**リセットエンドポイント詳細:**
- リクエスト: `DELETE /api/match-pairings/{id}/with-result`
- レスポンス: 削除された結果の情報（勝者名、スコア差等）
- エラー: ペアリングが存在しない場合は404

#### レスポンス変更: MatchPairingDto に追加するフィールド

```java
// 既存フィールドに追加
private boolean hasResult;        // 対応するmatchesレコードが存在するか
private String winnerName;        // 勝者名（結果入力済みの場合）
private Integer scoreDifference;  // 枚差（結果入力済みの場合）
private Long matchId;             // 対応するmatchesのID（リセット時に使用）
```

### 4.2 DB設計

- **スキーマ変更なし**（アプローチb: matchesレコードの存在で判定）
- 必要なクエリ追加:

```sql
-- match_pairingに対応するmatchが存在するかチェック
SELECT m.id, m.winner_id, m.score_difference
FROM matches m
WHERE m.match_date = :sessionDate
  AND m.match_number = :matchNumber
  AND ((m.player1_id = :p1 AND m.player2_id = :p2)
    OR (m.player1_id = :p2 AND m.player2_id = :p1))
  AND m.deleted_at IS NULL
```

### 4.3 フロントエンド設計

#### PairingGenerator.jsx の変更
- ペアリング取得時にレスポンスの `hasResult` フラグでロック判定
- ロック済みペアリング:
  - グレーアウト表示 + 「結果入力済」バッジ
  - プレイヤー変更・削除ボタンを `disabled`
  - 「リセット」ボタンを追加
- 自動組み合わせ: ロック済みプレイヤーを `availablePlayers` から除外
- バッチ保存: ロック済みペアリングを送信対象から除外

#### BulkResultInput.jsx の変更
- ペアリング未存在時のメッセージ表示ロジック追加
- ADMIN以上にPairingGenerator画面への遷移ボタンを表示

### 4.4 バックエンド設計

#### MatchService の変更
- `createMatch()` / `createMatchSimple()`: 保存後に `MatchPairingRepository` で既存ペアリングをチェックし、なければ自動生成
- `updateMatch()` / `updateMatchDetailed()`: プレイヤー変更なし（勝者・スコアのみ更新）のため、ペアリング操作は不要
- 上記処理は `@Transactional` の範囲内で実行

#### MatchPairingService の変更
- `createBatch()`: 削除前にロック済みペアリングを取得し、保持リストに追加。未ロックのみ削除
- `autoMatch()`: ロック済みプレイヤーIDリストをアルゴリズムの入力から除外
- 新メソッド `resetWithResult(Long pairingId)`: ペアリングと対応するmatchを両方削除

#### MatchPairingDto の変更
- `hasResult`, `winnerName`, `scoreDifference`, `matchId` フィールド追加
- `fromEntity()` メソッドでmatch情報をマージ

## 5. 影響範囲

### 変更が必要な既存ファイル

**バックエンド:**
| ファイル | 変更内容 |
|----------|----------|
| `MatchService.java` | createMatch/updateMatch にペアリング自動生成・削除ロジック追加 |
| `MatchPairingService.java` | createBatch のロック保持ロジック、autoMatch のロック除外ロジック、resetWithResult 新メソッド |
| `MatchPairingController.java` | リセットエンドポイント追加 |
| `MatchPairingDto.java` | hasResult, winnerName, scoreDifference, matchId フィールド追加 |
| `MatchPairingRepository.java` | match存在チェッククエリ追加 |
| `MatchRepository.java` | ペアリング対応match検索クエリ追加 |

**フロントエンド:**
| ファイル | 変更内容 |
|----------|----------|
| `PairingGenerator.jsx` | ロック表示・リセットボタン・自動組み合わせのロック除外 |
| `BulkResultInput.jsx` | ペアリング未存在メッセージ・PairingGeneratorへの遷移ボタン |
| `pairings.js` | リセットAPI呼び出し追加 |

### 既存機能への影響

- **対戦結果入力（MatchForm）**: 結果作成時にmatch_pairingsも生成されるようになる（追加動作のみ、既存動作に影響なし）
- **一括結果入力（BulkResultInput）**: ペアリング未存在時のUI追加（既存動作に影響なし）
- **自動組み合わせ**: ロック済みプレイヤーが除外される（動作変更あり）
- **バッチ保存**: ロック済みペアリングが保持される（動作変更あり）
- **DBスキーマ**: 変更なし

## 6. 設計判断の根拠

| 判断 | 理由 |
|------|------|
| ロック状態を別フラグではなくmatch存在で判定 | matchesとの整合性が自動的に保たれ、不整合リスクがない |
| match_pairingsを結果入力時に自動生成 | 選手がペアリング画面を使わずに結果を入力するユースケースに対応 |
| リセットを個別ペアリング単位にする | 一括リセットだと他の結果入力済みペアリングも巻き込むリスクがある |
| リセット時にmatchとpairingの両方を削除 | 中途半端な状態を残さず、完全にやり直せるようにする |
| BulkResultInputではmatch_pairings自動生成せず誘導 | 一括入力画面は既にペアリングがある前提の画面であり、ペアリング作成は専用画面で行うべき |
