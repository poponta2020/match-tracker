---
status: completed
audit_source: 会話内レポート
selected_items: [2, 3, 4, 5, 6]
---
# 対戦記録機能 改修要件定義書

## 1. 改修概要

- **対象機能:** 選手個人の対戦記録・お手付き・メモ記録機能
- **改修の背景:** `/audit-feature` による監査レポートで検出された設計上の問題点・コード品質の改善
- **改修スコープ:** 推奨アクション #2〜#6 の5項目

## 2. 改修内容

### 2.1 upsertPersonalNote の null クリア対応

- **現状の問題:** `personalNotes == null && otetsukiCount == null` で早期リターンするため、既存の個人メモ・お手付きを削除（クリア）できない。API を直接呼ぶ場合にリスクがある。(`MatchService.java:715-718`)
- **修正方針:** 既存の `MatchPersonalNote` レコードが存在する場合は、null が渡されても更新処理を実行し、値をクリア可能にする。既存レコードがなく、かつ両方 null の場合のみ早期リターンする。
- **修正後のあるべき姿:** ユーザーがメモ・お手付きを空にして保存した場合、既存レコードの値がクリアされる。

### 2.2 getOpponentPlayer の N+1 解消

- **現状の問題:** `findPlayerMatchesWithFilters()` と `getPlayerStatisticsByRank()` で、各試合の対戦相手を1件ずつ `playerRepository.findById()` で取得している。試合数 N に対して最大 N 回の DB アクセスが発生する。(`MatchService.java:158-173`)
- **修正方針:** フィルタリング前に全対戦相手IDを収集し、`playerRepository.findAllById()` で一括取得して `Map<Long, Player>` に変換。ループ内では Map から参照する。
- **修正後のあるべき姿:** 対戦相手の取得が1回の DB アクセスで完了する。

### 2.3 デッドコード削除

- **現状の問題:** `matches.js` の `getByDateRange()` が `/matches/search` を呼んでいるが、バックエンドに対応するエンドポイントが存在しない。(`matches.js:33-36`)
- **修正方針:** `getByDateRange()` メソッドを削除する。
- **修正後のあるべき姿:** 使用されていないデッドコードが除去される。

### 2.4 scoreDifference の仕様統一と「不明」対応

- **現状の問題:** 仕様書は scoreDifference の範囲を「1〜25」と記載しているが、実装は「0〜25」を許容している。フロントエンドのセレクトボックスでは0を「0 枚」として表示している。
- **修正方針:**
  - 仕様書を「0〜25」に統一する
  - scoreDifference = 0 を「枚数差不明」として扱う
  - フロントエンド全画面で scoreDifference = 0 の表示を「不明」に変更
  - 統計計算（平均枚数差など）では scoreDifference = 0 の試合を計算から除外する（現状は平均枚数差フィールドは未実装だが、将来追加時の方針として定義）
- **修正後のあるべき姿:** 結果はわかるが枚数差がわからないケースに対応できる。

#### フロントエンド表示変更箇所

| 画面 | ファイル | 現状表示 | 修正後表示 |
|------|---------|---------|----------|
| 試合結果入力 | `MatchForm.jsx:802-806` | `0 枚` | `不明` |
| 試合一覧 | `MatchList.jsx:267-269` | `〇0` / `×0` | `〇不明` / `×不明` |
| 試合詳細 | `MatchDetail.jsx:162-164` | `+0枚` | `不明` |
| 練習日ビュー | `MatchResultsView.jsx:551-552` | `0` | `不明` |
| 一括入力 | `BulkResultInput.jsx:566-567` | `0` | `不明` |

### 2.5 hasSessionOnDateForUser のレイヤー違反修正

- **現状の問題:** `MatchController` が `PracticeSessionRepository` と `OrganizationService` に直接依存してロジックを実装している。Controller が Repository に直接依存するのはレイヤー違反。(`MatchController.java:296-304`)
- **修正方針:** `hasSessionOnDateForUser()` のロジックを `PracticeSessionService`（既存）に移動し、Controller からは Service 経由で呼び出す。
- **修正後のあるべき姿:** Controller は Service のみに依存する標準的なレイヤードアーキテクチャに準拠する。

## 3. 技術設計

### 3.1 API変更
- なし（既存APIのインターフェースに変更なし）

### 3.2 DB変更
- なし

### 3.3 フロントエンド変更

| ファイル | 変更内容 |
|---------|---------|
| `matches.js` | `getByDateRange()` メソッド削除 |
| `MatchForm.jsx` | 枚数差セレクトボックスの value=0 の表示を「不明」に変更 |
| `MatchList.jsx` | `getResultDisplay()` で scoreDifference === 0 の場合「不明」を表示 |
| `MatchDetail.jsx` | scoreDifference === 0 の場合「不明」を表示 |
| `MatchResultsView.jsx` | scoreDifference === 0 の場合「不明」を表示 |
| `BulkResultInput.jsx` | 枚数差セレクトボックスの value=0 の表示を「不明」に変更 |

### 3.4 バックエンド変更

| ファイル | 変更内容 |
|---------|---------|
| `MatchService.java` | `upsertPersonalNote()`: 既存レコードがある場合は null でもクリア処理を実行 |
| `MatchService.java` | `findPlayerMatchesWithFilters()`: 対戦相手の一括取得に変更（N+1 解消） |
| `MatchService.java` | `getPlayerStatisticsByRank()`: 同上 |
| `MatchController.java` | `hasSessionOnDateForUser()` を削除し、Service 経由の呼び出しに変更。`PracticeSessionRepository` と `OrganizationService` の直接依存を削除 |
| `PracticeSessionService.java` | `hasSessionOnDateForUser(LocalDate, Long)` メソッドを追加 |

## 4. 影響範囲

### 影響を受ける既存機能

- **個人メモ・お手付き記録:** upsertPersonalNote の変更により、API 経由で null を送った場合の動作が変わる（クリアされるようになる）。ただし現在のフロントエンドでは personalNotes は空文字列 `""` として送信されるため、既存の UI 操作に影響なし。
- **試合一覧・詳細・入力画面:** scoreDifference = 0 の表示が「0」から「不明」に変わる。既存の scoreDifference = 0 のデータがある場合、表示が変更される。
- **フィルタリング付き試合取得:** N+1 解消の内部リファクタリングのため、外部から見た動作に変更なし。

### 破壊的変更の有無
- **なし。** API インターフェース、DB スキーマともに変更なし。フロントエンドの表示のみ変更。

## 5. 設計判断の根拠

- **scoreDifference = 0 を「不明」として扱う理由:** 競技かるたでは引き分け（枚数差0）はほぼ発生しないため、0を「枚数差が不明なケース」に活用する。DB スキーマの変更（nullable 化など）よりもシンプルな方法。
- **N+1 解消に findAllById を使う理由:** JOINクエリの新規追加よりも既存の Repository メソッドを活用する方がシンプルで、練習会の試合数（数十〜数百件）では十分なパフォーマンス。
- **hasSessionOnDateForUser の移動先を PracticeSessionService にする理由:** 練習セッションの存在チェックというドメインロジックであり、MatchService よりも PracticeSessionService が適切。
