---
status: completed
---
# 参加登録画面からの登録解除禁止（締切後） 要件定義書

## 1. 概要
- **目的:** 締切後の参加登録画面で、既にサーバーに保存済みの登録をチェック外しで解除できないようにする
- **背景・動機:** 現状、参加登録画面のチェックボックスを外して保存すると、キャンセル画面を経由せずに登録を取り消せてしまう。締切後のキャンセルは専用画面（理由入力あり）から行うべきであり、参加登録画面からの暗黙的なキャンセルを防止する

## 2. ユーザーストーリー
- **対象ユーザー:** 全ユーザー（PLAYER / ADMIN / SUPER_ADMIN 共通）
- **ユーザーの目的:** 締切後に追加登録はしたいが、既存登録の取り消しは参加登録画面からは行えないようにしたい
- **利用シナリオ:**
  1. 締切後、参加登録画面を開く
  2. 既に登録済みの試合はチェック済み＋グレーアウト（disabled）で表示される
  3. 未登録の試合にはチェックを入れて追加登録できる
  4. 既存登録を取り消したい場合は、参加キャンセル画面から行う
  5. 締切前は従来通り、チェックの ON/OFF が自由にできる

## 3. 機能要件

### 3.1 画面仕様

**参加登録画面（PracticeParticipation.jsx）**

| 条件 | チェックボックスの動作 |
|------|----------------------|
| 締切前 + 未登録 | チェック ON/OFF 自由（現状通り） |
| 締切前 + 登録済み | チェック ON/OFF 自由（現状通り） |
| 締切後 + 未登録（抽選未実施） | チェック ON 可能、保存前なら OFF も可能 |
| 締切後 + 登録済み（抽選未実施） | **チェック済み＋disabled（グレーアウト）。外せない** |
| 締切後 + 抽選済み | ステータスバッジ表示（現状通り、チェックボックスなし） |

- 締切後に登録済みの試合は、チェックボックスが `disabled` でグレーアウト表示
- 今回のセッション中に新たにチェックを入れたが**まだ保存していない**ものは、保存前なら外せる

### 3.2 ビジネスルール
- 締切前: 現状の洗い替え方式を維持（全削除→再登録）
- 締切後: 既存登録の削除は不可。新規追加のみ許可
- 既存登録を取り消したい場合は、参加キャンセル画面（`/practice/cancel`）を使用する

### 3.3 エラーケース
- 締切後にAPIを直接叩いて既存登録を削除しようとした場合、バックエンドで拒否する（既存の `registerAfterDeadline` は既に新規追加のみの動作なので、現状のバックエンドロジックで担保済み）

## 4. 技術設計

### 4.1 API設計

**既存エンドポイント: `GET /api/practice-sessions/participations/player/{playerId}/status`**

レスポンスの `PlayerParticipationStatusDto` に `beforeDeadline` フィールドを追加する。

```json
{
  "participations": { ... },
  "lotteryExecuted": { ... },
  "beforeDeadline": true
}
```

- `beforeDeadline`: boolean — 対象月が締切前かどうか
- フロントエンドはこの値を使って、チェックボックスの disabled 制御を行う

### 4.2 DB設計
- テーブル変更なし

### 4.3 フロントエンド設計

**変更ファイル: `PracticeParticipation.jsx`**

- `statusRes.data` から `beforeDeadline` を取得して state に保持
- `toggleMatch` 関数に制御を追加:
  - 締切後 かつ `initialParticipations` に含まれる試合のチェック外しを無視
- チェックボックスの `disabled` 属性:
  - 締切後 かつ `initialParticipations` に含まれる → `disabled={true}` + グレーアウトスタイル

### 4.4 バックエンド設計

**変更ファイル: `PlayerParticipationStatusDto.java`**
- `beforeDeadline` フィールドを追加

**変更ファイル: `PracticeParticipantService.java`**
- `getPlayerParticipationStatus` メソッドで `beforeDeadline` を算出してDTOにセット

**バックエンドの `registerAfterDeadline` は変更不要** — 既に「既存があればスキップ、新規のみ追加」のロジックになっているため、フロントからチェックを外した登録が送信されなくても、既存登録は削除されない。

## 5. 影響範囲

| ファイル | 変更内容 |
|---------|---------|
| `PlayerParticipationStatusDto.java` | `beforeDeadline` フィールド追加 |
| `PracticeParticipantService.java` | ステータス取得時に `beforeDeadline` をセット |
| `PracticeParticipation.jsx` | 締切後の既存登録チェックボックスを disabled 化 |

- **既存機能への影響:** なし。締切前の動作は完全に現状維持。締切後も `registerAfterDeadline` のロジックは変更不要
- **API互換性:** `PlayerParticipationStatusDto` にフィールド追加のみ（後方互換）

## 6. 設計判断の根拠

- **バックエンドの `registerAfterDeadline` を変更しない理由:** 既に「既存をスキップして新規のみ追加」のロジックになっており、チェックを外した試合のデータがリクエストに含まれなくても既存登録は削除されない。締切前の `registerBeforeDeadline` は洗い替え方式のまま維持し、自由なON/OFFを実現する
- **締切情報をAPIで返す理由:** フロントエンドで独自に締切判定を実装するとバックエンドとの不整合リスクがあるため、バックエンドの `LotteryDeadlineHelper` を信頼源とする
