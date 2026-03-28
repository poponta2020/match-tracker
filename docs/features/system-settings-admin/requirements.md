---
status: completed
---
# システム設定管理画面 要件定義書（ドラフト）

## 1. 概要
- 目的: システム設定（抽選締め切り日数、一般枠保証割合など）をUI上で確認・変更できるようにする
- 背景: 現状DB直接操作でしか設定変更ができず、非エンジニアの管理者が操作できない

## 2. ユーザーストーリー
### 管理者（ADMIN / SUPER_ADMIN）
- システム設定の現在値をUI上で確認したい
- システム設定の値をUI上で変更したい
- DB操作なしで運用できるようにしたい

### 一般ユーザー（PLAYER）
- 参加登録の締め切り日がいつなのかを画面上で確認したい

## 3. 機能要件

### 3.1 管理画面（ADMIN / SUPER_ADMIN のみ）

#### 画面仕様
- ハンバーガーメニューに「システム設定」を追加
- 設定項目ごとにラベル・現在値・変更フォームを縦に並べる

#### 設定項目（初期リリース）
1. **抽選締め切り日数**（`lottery_deadline_days_before`）
   - 「締め切りなし」チェックボックス + 日数入力フィールド
   - 「締め切りなし」チェック時は日数入力を disabled にする
   - 日数のバリデーション: 0〜20 の整数
   - 内部的に「締め切りなし」は `-1` として保存
   - 締め切りプレビュー表示: 「来月の締め切り: ○月○日」
   - 「締め切りなし」時はプレビューに「締め切りなし（いつでも登録変更可能）」と表示

2. **一般枠の最低保証割合**（`lottery_normal_reserve_percent`）
   - 数値入力フィールド（%）
   - バリデーション: 0〜100 の整数

#### 操作フロー
- 全設定をまとめて1回の「保存」ボタンで保存
- 保存前に確認ダイアログ「設定を変更しますか？」を表示
- 保存成功時に「保存しました」のトースト通知を表示

### 3.2 「締め切りなし」モードの挙動
| 処理 | 挙動 |
|------|------|
| 参加登録チェックボックス | 常に有効 |
| 自動抽選スケジューラ | 実行しない |
| 手動抽選（管理者） | いつでも実行可能 |
| 参加登録の処理パス | 常に通常登録 |

### 3.3 一般ユーザーへの締め切り表示
- 表示場所: 参加登録画面（PracticeParticipation）の上部
- 表示内容: 「締め切り: ○月○日（あと○日）」
- 締め切り後は非表示
- 「締め切りなし」モードの場合は何も表示しない

## 4. 技術設計

### 4.1 API設計

#### 既存API（変更なし）
- `GET /api/system-settings` — 全設定取得（ADMIN以上）
- `PUT /api/system-settings/{key}` — 設定値更新（ADMIN以上）

#### 新規API
- `GET /api/lottery/deadline?year={year}&month={month}` — 締め切り日時取得（認証不要）
  - レスポンス: `{ "deadline": "2026-03-29T00:00:00", "noDeadline": false }`
  - 「締め切りなし」時: `{ "deadline": null, "noDeadline": true }`

### 4.2 DB設計
- テーブル変更なし
- `system_settings` テーブルの `lottery_deadline_days_before` に `-1` を保存することで「締め切りなし」を表現

### 4.3 フロントエンド設計
- **新規** `pages/settings/SystemSettings.jsx` — 管理画面コンポーネント
- **新規** `api/systemSettings.js` — APIクライアント
- **変更** `components/NavigationMenu.jsx` — メニュー項目追加
- **変更** `App.jsx` — ルーティング追加
- **変更** `pages/practice/PracticeParticipation.jsx` — 締め切り表示追加

### 4.4 バックエンド設計
- **変更** `LotteryDeadlineHelper.java`
  - `getDeadline()`: `-1` の場合は `null` を返す
  - `isBeforeDeadline()`: `-1` の場合は常に `true`
  - `isAfterDeadline()`: `-1` の場合は常に `false`
  - `isNoDeadline()`: 新規メソッド追加
- **変更** `LotteryController.java`
  - 手動抽選の締め切り前チェック: 「締め切りなし」時はスキップ
  - `GET /api/lottery/deadline` エンドポイント追加
- **変更なし** `LotteryScheduler.java` — `isAfterDeadline()` が false を返すため自動的に対応
- **変更なし** `PracticeParticipantService.java` — `isBeforeDeadline()` が true を返すため自動的に対応

## 5. 影響範囲

### 変更対象ファイル
#### バックエンド
| ファイル | 変更内容 |
|---------|---------|
| `LotteryDeadlineHelper.java` | `-1`対応（getDeadline で null、isBeforeDeadline で常に true） |
| `LotteryController.java` | 手動抽選ガード調整 + 新規エンドポイント追加 |

#### フロントエンド
| ファイル | 変更内容 |
|---------|---------|
| `NavigationMenu.jsx` | 「システム設定」メニュー項目追加（ADMIN以上） |
| `App.jsx` | `/admin/settings` ルーティング追加 |
| `PracticeParticipation.jsx` | 締め切り日表示追加 |
| **新規** `pages/settings/SystemSettings.jsx` | 管理画面 |
| **新規** `api/systemSettings.js` | APIクライアント |

### 既存機能への影響
- `LotteryScheduler`: 「締め切りなし」モードでは自動抽選が走らない（意図通り）
- `PracticeParticipantService`: 「締め切りなし」モードでは常に通常登録パス（意図通り）
- 既存の締め切りあり運用（`daysBefore >= 0`）には影響なし

## 6. 設計判断の根拠
- **「締め切りなし」を `-1` で表現**: DB スキーマ変更不要。既存の `system_settings` テーブルをそのまま活用できる
- **締め切り日時取得を専用エンドポイントに**: フロントエンドで日数→日付の変換ロジックを持つ必要がなく、「締め切りなし」の判定もバックエンドに集約できる
- **管理画面を独立ページに**: 将来的に設定項目を追加しやすい構造
