---
status: completed
---
# 抽選管理画面 要件定義書

## 1. 概要

### 目的
管理者が手動で月次抽選を実行・確認・確定できる画面を提供する。

### 背景・動機
- 現在、抽選実行のUIが存在せず、自動スケジューラか直接API呼び出しでしか抽選を実行できない
- 管理者が締め切り後に手動で抽選を実行し、結果を確認してから確定したいという運用ニーズがある
- 抽選に関する設定（システム設定）も同じ画面からアクセスできるようにしたい

## 2. ユーザーストーリー

### 対象ユーザー
- ADMIN（各団体の管理者。自団体の抽選のみ操作可能）
- SUPER_ADMIN（全団体の抽選を操作可能）

### 利用シナリオ
1. 管理者が Settings ページから「抽選管理」を開く
2. 対象の年月を選択する
3. 「抽選実行」ボタンを押す
4. 抽選結果のプレビューが表示される（DB未保存）
5. 結果を確認し「確定」ボタンを押す
6. DBに抽選結果（WON/WAITLISTED等）が保存され、伝助への一括書き戻しが実行される
7. 必要に応じて「システム設定」リンクから締め切り日数や一般枠割合を変更する

## 3. 機能要件

### 3.1 画面仕様

#### 画面構成
抽選管理画面（`/admin/lottery`）に以下を配置：

**ヘッダー部**
- 画面タイトル「抽選管理」
- 「システム設定」へのリンクボタン

**操作部**
- 年月セレクター（デフォルト: 翌月）
- 「抽選実行」ボタン

**結果プレビュー部**（抽選実行後に表示）
- セッション別 → 試合番号別の当選/キャンセル待ち一覧
  - 既存の `LotteryResultDto` 形式に準拠
  - 当選者: 名前・段位/級位
  - キャンセル待ち: 名前・段位/級位・待ち順
- 「確定」ボタン

**確定後の操作部**（抽選確定後に表示）
- 「全員に通知送信」ボタン（既存の `notifyResults` API を使用。WON + WAITLISTED 全員にアプリ内通知 + LINE通知）
- 「キャンセル待ちの人にだけ通知送信」ボタン（WAITLISTED の人のみにアプリ内通知 + LINE通知）

**状態表示**
- 未実行: 操作部のみ表示
- プレビュー中: 結果プレビュー部 + 確定ボタン表示
- 確定済み: 確定完了メッセージ + 通知送信ボタン表示

#### バリデーション・制約
- 締め切り前は抽選実行不可（「締め切りなし」モードの場合はいつでも実行可能）
- 同一月に既に確定済みの抽選がある場合はエラー（再抽選はセッション単位で別途対応）
- ADMINは自団体のセッションのみ対象

#### エラーケース
- 締め切り前に実行しようとした場合 → エラーメッセージ「締め切り前です」
- 既に抽選確定済みの場合 → エラーメッセージ「既に実行済みです」
- 対象セッションが存在しない場合 → メッセージ「対象のセッションがありません」
- 伝助書き戻しに失敗した場合 → 抽選確定は成功するが書き戻しエラーを表示

### 3.2 ビジネスルール

- 抽選プレビューはDBに保存しない（画面を閉じると消える）
- 確定ボタン押下で初めてDBに保存（WON/WAITLISTED等のステータス反映）
- 確定時に伝助への一括書き戻しを実行（`DensukeWriteService.writeAllForLotteryConfirmation()`）
- 抽選アルゴリズム自体は既存の `LotteryService` のロジックをそのまま使用

### 3.3 SettingsPage の変更

- グリッドから「システム設定」を**削除**
- 代わりに「抽選管理」を追加（同じ位置、ADMIN以上に表示）

## 4. 技術設計

### 4.1 API設計

#### 新規: 抽選プレビュー
```
POST /api/lottery/preview
Request: { year: int, month: int, organizationId?: long }
Response: List<LotteryResultDto>
Role: ADMIN, SUPER_ADMIN
```
- 抽選アルゴリズムを実行するが、DBには保存しない
- 結果をプレビュー用のDTOとして返す

#### 新規: キャンセル待ちのみ通知送信
```
POST /api/lottery/notify-waitlisted
Request: { year: int, month: int, organizationId?: long }
Response: { inAppCount: int, lineSent: int, lineFailed: int, lineSkipped: int }
Role: ADMIN, SUPER_ADMIN
```
- WAITLISTED ステータスの参加者のみにアプリ内通知 + LINE通知を送信

#### 新規: 抽選確定
```
POST /api/lottery/confirm
Request: { year: int, month: int, organizationId?: long }
Response: LotteryExecution
Role: ADMIN, SUPER_ADMIN
```
- 抽選を実行してDBに保存
- 伝助への一括書き戻しをトリガー
- ※コントローラ・APIクライアントは既にユーザーが追加済み（権限をADMINにも拡張する必要あり）

#### 既存: 締め切り情報取得
```
GET /api/lottery/deadline?year={year}&month={month}&organizationId={orgId}
Response: { deadline: datetime, noDeadline: boolean }
```

### 4.2 DB設計

新規テーブル・カラムの追加は不要。既存の `LotteryExecution` / `PracticeParticipant` で対応。

### 4.3 フロントエンド設計

#### 新規コンポーネント
- `karuta-tracker-ui/src/pages/lottery/LotteryManagement.jsx` — 抽選管理画面

#### 変更コンポーネント
- `karuta-tracker-ui/src/pages/SettingsPage.jsx` — グリッドの「システム設定」を「抽選管理」に置き換え
- `karuta-tracker-ui/src/App.jsx` — `/admin/lottery` ルート追加
- `karuta-tracker-ui/src/api/lottery.js` — `preview` メソッド追加（`confirm` は追加済み）

### 4.4 バックエンド設計

#### LotteryService
- 新規メソッド `previewLottery(year, month, orgId)`: 既存の抽選アルゴリズムを実行するが、DBに保存せずに `List<LotteryResultDto>` を返す
- 新規メソッド `confirmLottery(year, month, executedBy, orgId)`: 抽選を実行してDB保存 + 伝助書き戻し

#### LotteryController
- 新規エンドポイント `POST /preview`: `previewLottery()` を呼び出す
- 既存エンドポイント `POST /confirm`: 権限を `ADMIN` にも拡張、ADMINスコープ検証を追加

## 5. 影響範囲

### 変更が必要な既存ファイル
| ファイル | 変更内容 |
|---------|---------|
| `LotteryService.java` | `previewLottery()`, `confirmLottery()` メソッド追加 |
| `LotteryController.java` | `/preview`, `/notify-waitlisted` エンドポイント追加、`/confirm` の権限修正・スコープ検証追加 |
| `NotificationService.java` | キャンセル待ちのみの通知生成メソッド追加 |
| `SettingsPage.jsx` | グリッド項目の変更（システム設定 → 抽選管理） |
| `App.jsx` | `/admin/lottery` ルート追加 |
| `lottery.js` | `preview` メソッド追加 |

### 新規ファイル
| ファイル | 内容 |
|---------|------|
| `LotteryManagement.jsx` | 抽選管理画面 |

### 既存機能への影響
- SettingsPage から「システム設定」への直接リンクがなくなる（抽選管理画面経由でアクセス）
- 既存の自動抽選スケジューラ（`LotteryScheduler`）は変更なし（そのまま残す）
- 既存の `/lottery/execute` エンドポイントは変更なし（スケジューラが使用）

## 6. 設計判断の根拠

| 判断 | 理由 |
|------|------|
| プレビュー方式（B案: DB未保存）を採用 | シンプルな実装。確定前に画面を閉じても副作用なし |
| 既存の抽選アルゴリズムを再利用 | ロジックの重複を避け、一貫性を保つ |
| `/execute` は変更せず `/preview` + `/confirm` を新設 | 自動スケジューラが `/execute` 経由で `executeLottery()` を使用しており、既存動作を壊さない |
| `LotteryScheduler` は残す | 将来的に自動抽選を使う可能性があるため |
