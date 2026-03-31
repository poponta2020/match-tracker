---
status: completed
---
# 伝助双方向同期 要件定義書（ドラフト）

## 1. 概要

伝助とアプリの双方向同期を実現する。どちらから操作しても、もう一方に正しく反映される。抽選結果は伝助に○/△/×で可視化される。

## 2. ユーザーストーリー

### 対象ユーザー
- **練習参加者（PLAYER）**: 伝助またはアプリから参加登録・キャンセルを行う
- **管理者（ADMIN/SUPER_ADMIN）**: 参加状況を管理し、抽選を実行する
- **システム（スケジューラー）**: 定期的に伝助⇔アプリ間の同期を自動実行する

### ユーザーの目的
- 伝助とアプリのどちらから操作しても、もう一方に正しく反映される双方向同期
- 抽選結果（WON/WAITLISTED）が伝助に正確に表示される（○/△/×）
- 伝助上での操作（○→×でキャンセル、×→○で再登録、△でキャンセル待ち希望など）がアプリに反映される

### 背景・動機
- 利用者の一部は伝助のみを使っている。アプリと伝助の二重管理を解消したい
- 抽選結果を伝助で可視化することで、全員が自分のステータスを確認できるようにしたい

### 利用者への周知
- 「締切前の伝助は○か否かのみ意味がある」旨は口頭で周知する（アプリ内の表示は不要）

## 3. 機能要件

### 3.1 基本原則

#### dirty フラグによる競合制御
- アプリ側で変更が発生すると `dirty=true` が設定される
- `dirty=true` のレコードは伝助インポート時に**一切触らない（スキップ）**
- 書き戻し（アプリ→伝助）は `dirty=true` のレコードのみが対象
- 書き戻し成功後に `dirty=false` に更新される

#### 伝助の記号の意味
- **○（参加）**: 「参加したい」という明確な意思表示
- **△（未定）**: フェーズ1では無視。フェーズ3では「キャンセル待ち希望」として処理
- **×/空白（不参加）**: 「参加しない」という意思表示

#### 書き戻しマッピング（全フェーズ共通）

| アプリステータス | 伝助に書く値 |
|---------------|------------|
| PENDING / WON | 3（○） |
| WAITLISTED / OFFERED | 2（△） |
| CANCELLED / DECLINED / WAITLIST_DECLINED | 1（×） |
| 未登録（null） | 1（×）※現行の0（空白）から変更 |

#### 同期タイミング
- **定期同期（5分間隔）**: 伝助→アプリ取り込み + アプリ→伝助書き戻し（dirty=trueのみ）
- **イベント駆動書き戻し**: アプリ操作時に即座に `triggerWriteAsync()` で書き戻し
- **抽選確定時の一括書き戻し**: 管理者が抽選結果を確定した時点で、伝助マッピングがある全プレイヤー分を書き戻し

#### 論理削除
- 参加取消（全取消含む）は物理削除ではなく `CANCELLED + dirty=true` に変更する
- 書き戻しで×が送られ dirty=false になった後、次サイクルで安定状態に収束する

### 3.2 フェーズ1：締め切り前（MONTHLY型）

#### アプリ → 伝助

| # | 操作 | 処理 | 伝助 |
|---|------|------|------|
| 1-1 | 参加登録（未登録→） | `PENDING`, `dirty=true` | ○ |
| 1-2 | 参加変更（PENDING→） | 取消分を `CANCELLED + dirty=true`、新規分を `PENDING + dirty=true` | ○(参加)、×(取消) |
| 1-3 | 全取消 | 全レコードを `CANCELLED + dirty=true` に変更（論理削除） | × |

#### 伝助 → アプリ（○ vs not-○）

| # | 伝助 | 現ステータス | dirty | 処理 | 伝助書き戻し |
|---|------|------------|-------|------|------------|
| 1-A | ○ | 未登録 | — | `PENDING`, `dirty=false` で作成 | 不要（既に○） |
| 1-B | ○ | 既存 | false | スキップ（既存維持） | 不要 |
| 1-C | ○ | 既存 | true | スキップ（dirty保護） | dirty の通常書き戻し |
| 1-D | not-○ | 未登録 | — | 何もしない | 書き戻さない |
| 1-E | not-○ | 既存 | false | レコード削除 | 書き戻さない |
| 1-F | not-○ | 既存 | true | スキップ（dirty保護） | dirty の通常書き戻し |

- not-○ は書き戻し不要。利用者には「○か否かのみ意味がある」と周知する
- 1-E でレコード削除後、次サイクルでは 1-D（何もしない）で安定

### 3.3 フェーズ2：締め切り後・抽選前（MONTHLY型）

| # | 区分 | 処理 |
|---|------|------|
| 2-1 | アプリ操作 | 登録不可（スキップ） |
| 2-2 | 伝助○ | インポートしない |
| 2-3 | 伝助△ | インポートしない |
| 2-4 | 伝助×/空白 | インポートしない |
| 2-W1 | 書き戻し | `dirty=true` のみ通常書き戻し |

- 抽選前に伝助からの変更で結果が変わるのを防ぐため、全てインポートしない

### 3.4 フェーズ3：抽選後（MONTHLY型）

#### 抽選確定時の一括書き戻し

管理者が抽選結果を確定した時点で、伝助マッピングがある**全プレイヤー**について書き戻す。
抽選実行後、管理者が結果を確認し、再抽選せず「確定」とした場合にトリガーされる。

| アプリステータス | 伝助に書く値 |
|---------------|------------|
| WON | ○ |
| WAITLISTED | △ |
| OFFERED | △ |
| CANCELLED / DECLINED / WAITLIST_DECLINED | × |
| 未登録 | × |

書き戻し後、全レコードの dirty=false に更新。

#### アプリ → 伝助

| # | 操作 | 現ステータス | 定員 | 処理 | 伝助 |
|---|------|------------|------|------|------|
| 3-1 | 参加登録 | 未登録 | 空きあり | `WON`, `dirty=true` | ○ |
| 3-2 | 参加登録 | 未登録 | 定員超過 | `WAITLISTED`(最後尾), `dirty=true` | △ |
| 3-3 | 参加登録 | 未登録 | 定員なし | `WON`, `dirty=true` | ○ |
| 3-4 | 参加登録 | WON/WAITLISTED/OFFERED | — | スキップ（既にアクティブ） | — |
| 3-4b | 再登録 | CANCELLED/DECLINED/WD | 空きあり | `WON`, `dirty=true` | ○ |
| 3-4c | 再登録 | CANCELLED/DECLINED/WD | 定員超過 | `WAITLISTED`(最後尾), `dirty=true` | △ |
| 3-5 | キャンセル | WON | WAITLISTEDあり | `CANCELLED`, `dirty=true` → 繰り上げ発動 | × |
| 3-6 | キャンセル | WON | WAITLISTEDなし | `CANCELLED`, `dirty=true` | × |
| 3-7 | キャンセル待ち辞退 | WAITLISTED | — | `WAITLIST_DECLINED`, `dirty=true`, 後続繰り上げ | × |
| 3-8 | キャンセル待ち復帰 | WAITLIST_DECLINED | — | `WAITLISTED`(最後尾), `dirty=true` | △ |
| 3-9 | オファー承諾 | OFFERED | — | `WON`, `dirty=true` | ○ |
| 3-10 | オファー辞退 | OFFERED | — | `DECLINED`, `dirty=true` → 次繰り上げ | × |
| 3-11 | オファー期限切れ | OFFERED | — | `DECLINED`, `dirty=true` → 次繰り上げ | × |

#### 伝助 → アプリ：○の場合

| # | 現ステータス | dirty | 定員 | 処理 | 伝助書き戻し |
|---|------------|-------|------|------|------------|
| 3-A1 | 未登録 | — | 空きあり | `WON`, `dirty=true` | ○ |
| 3-A2 | 未登録 | — | 定員超過 | `WAITLISTED`(最後尾), `dirty=true` | △ |
| 3-A3 | 未登録 | — | 定員なし | `WON`, `dirty=true` | ○ |
| 3-A4 | WON | false | — | スキップ | — |
| 3-A5 | WON | true | — | スキップ（dirty保護） | dirty書き戻し |
| 3-A6 | WAITLISTED | false | — | ステータス変更なし、`dirty=true` に設定 | △に書き戻す |
| 3-A7 | WAITLISTED | true | — | スキップ（dirty保護） | dirty書き戻し |
| 3-A8 | OFFERED | false | — | オファー期限内なら `WON`, `dirty=true`（承諾扱い）。期限切れならスキップ（スケジューラーに委ねる） | ○ |
| 3-A9 | OFFERED | true | — | スキップ（dirty保護） | dirty書き戻し |
| 3-A10 | CANCELLED/DECLINED/WD | false | 空きあり | `WON`, `dirty=true` | ○ |
| 3-A11 | CANCELLED/DECLINED/WD | false | 定員超過 | `WAITLISTED`(最後尾), `dirty=true` | △ |
| 3-A12 | CANCELLED/DECLINED/WD | true | — | スキップ（dirty保護） | dirty書き戻し |

- 3-A6: ○に変えても抽選はバイパスできない。△で返してキャンセル待ち中であることを伝える
- 3-A8: `respondToOffer(true)` 相当。オファー期限は OfferExpiryScheduler が管理するため、期限切れの場合はスキップ

#### 伝助 → アプリ：△の場合

△ = 「キャンセル待ち希望」。定員に関係なく常に WAITLISTED。

| # | 現ステータス | dirty | 処理 | 伝助書き戻し |
|---|------------|-------|------|------------|
| 3-B1 | 未登録 | — | `WAITLISTED`(最後尾), `dirty=true` | △ |
| 3-B2 | WON | false | `WAITLISTED`(最後尾), `dirty=true` → 繰り上げ発動 | △ |
| 3-B3 | WON | true | スキップ（dirty保護） | dirty書き戻し |
| 3-B4 | WAITLISTED | false | スキップ | — |
| 3-B5 | WAITLISTED | true | スキップ（dirty保護） | dirty書き戻し |
| 3-B6 | OFFERED | false | スキップ | — |
| 3-B7 | OFFERED | true | スキップ（dirty保護） | dirty書き戻し |
| 3-B8 | CANCELLED/DECLINED/WD | false | `WAITLISTED`(最後尾), `dirty=true` | △ |
| 3-B9 | CANCELLED/DECLINED/WD | true | スキップ（dirty保護） | dirty書き戻し |

- △は本来システムが設定する値だが、手動入力された場合も一貫してキャンセル待ちとして処理
- 3-B2: WON枠が空く → promoteNextWaitlisted() で先頭のWAITLISTEDにオファー（通常フロー）

#### 伝助 → アプリ：× or 空白の場合

| # | 現ステータス | dirty | 処理 | 繰り上げ | 伝助書き戻し |
|---|------------|-------|------|---------|------------|
| 3-C1 | 未登録 | — | 何もしない | — | — |
| 3-C2 | WON | false | `CANCELLED`, `dirty=true` | 繰り上げ発動 | × |
| 3-C3 | WON | true | スキップ（dirty保護） | — | dirty書き戻し |
| 3-C4 | WAITLISTED | false | `WAITLIST_DECLINED`, `dirty=true` | 後続番号繰り上げ | × |
| 3-C5 | WAITLISTED | true | スキップ（dirty保護） | — | dirty書き戻し |
| 3-C6 | OFFERED | false | `DECLINED`, `dirty=true` | 次に繰り上げ | × |
| 3-C7 | OFFERED | true | スキップ（dirty保護） | — | dirty書き戻し |
| 3-C8 | CANCELLED/DECLINED/WD | false | 何もしない | — | — |
| 3-C9 | CANCELLED/DECLINED/WD | true | スキップ（dirty保護） | — | dirty書き戻し |

- 3-C6: `respondToOffer(false)` 相当。DECLINED にして既存の辞退→繰り上げロジックを再利用

### 3.5 SAME_DAY型（わすらもち会）

| # | 条件 | 処理 | MONTHLY型との違い |
|---|------|------|-----------------|
| S-1 | 締切前（当日12:00前）のアプリ操作 | 即 `WON`/`WAITLISTED`（先着順） | PENDINGではなく即確定 |
| S-2 | 締切前の伝助取り込み | ○ vs not-○ でフェーズ1と同じだが、ステータスは `WON`/`WAITLISTED` | PENDINGではなく即確定 |
| S-3 | 締切後のアプリ操作 | フェーズ3と同じ（定員判定あり） | 抽選なし |
| S-4 | 締切後の伝助取り込み | フェーズ3と同じ（3-A〜3-C適用） | フェーズ2のスキップなし。常に取り込む |
| S-5 | 一括書き戻し | なし（各操作のdirtyベース書き戻しのみ） | MONTHLY型と異なり一括書き戻し不要 |

## 4. 技術設計

### 4.1 API設計

#### 新規エンドポイント

| メソッド | URL | 権限 | 説明 |
|---------|-----|------|------|
| POST | `/api/lottery/confirm` | SUPER_ADMIN | 抽選結果を確定し、伝助への一括書き戻しをトリガー |

リクエスト:
```json
{
  "year": 2026,
  "month": 4,
  "organizationId": 1
}
```

レスポンス:
```json
{
  "lotteryExecutionId": 123,
  "confirmedAt": "2026-03-31T10:00:00",
  "confirmedBy": 1,
  "densukeWritebackTriggered": true
}
```

#### 既存エンドポイントへの影響
- `POST /api/lottery/execute` — 変更なし（実行のみ、確定はしない）
- `GET /api/lottery/results` — `confirmed` フラグを追加して確定状態を返す

### 4.2 DB設計

#### LotteryExecution テーブル（既存・カラム追加）

| カラム | 型 | 説明 |
|-------|---|------|
| `confirmed_at` | TIMESTAMP | 確定日時（null = 未確定） |
| `confirmed_by` | BIGINT | 確定者のプレイヤーID（null = 未確定） |

### 4.3 バックエンド設計

#### DensukeScraper（既存・軽微な改修）
- `DensukeData` に `memberNames: List<String>` フィールドを追加
- ヘッダー行で取得済みの全メンバー名リストを返す
- ×/空白の判定に使用:「memberNames に含まれるが participants にも maybeParticipants にもいない = ×/空白」

#### DensukeImportService（既存・大幅改修）
- **フェーズ判定を追加**: セッションごとに「締切前 / 締切後・抽選確定前 / 抽選確定後」を判定
  - 抽選確定済み = `LotteryExecution` に `confirmedAt` が非null
  - SAME_DAY型は「締切前 / 締切後」の2分岐（フェーズ2なし）
- **抽選済みセッションの全スキップを廃止** → フェーズ3ロジックに置き換え
- **○リスト処理**: 既存ロジックを拡張（フェーズ別の分岐）
- **△リスト処理（新規）**: `entry.getMaybeParticipants()` をフェーズ3で処理
- **×/空白処理**: `memberNames - participants - maybeParticipants` でフェーズ3を処理
- **dirty=false のレコードのみ処理**。dirty=true は一切触らない

#### DensukeWriteService（既存・軽微な改修）
- `toJoinValue(null)`: 0（空白）→ 1（×）に変更
- **新メソッド `writeAllForLotteryConfirmation()`**: 抽選確定時に全マッピング済みプレイヤーの書き戻し。対象は dirty に関係なく全員。書き戻し後に全レコードの dirty=false に更新

#### LotteryService / LotteryController（既存・拡張）
- `confirmLottery()` メソッドを追加
  - `LotteryExecution` の `confirmedAt`, `confirmedBy` を設定
  - `DensukeWriteService.writeAllForLotteryConfirmation()` を呼び出し
- 結果取得APIに `confirmed` フラグを追加

#### PracticeParticipantService（既存・改修）
- `registerParticipations()` の物理削除を論理削除に変更
  - `deleteByPlayerIdAndSessionIds()` → ステータスを `CANCELLED + dirty=true` に更新
  - 新規分は従来通り作成

#### WaitlistPromotionService（既存・拡張）
- 3-B2（WON→WAITLISTED）処理: WON枠を開放し `promoteNextWaitlisted()` を呼ぶ新メソッド
- 3-C6（OFFERED→DECLINED）処理: `respondToOffer(false)` 相当のロジックを再利用

### 4.4 フェーズ判定ロジック

```
MONTHLY型:
  if (confirmedAt != null)  → フェーズ3（抽選確定後）
  else if (isAfterDeadline) → フェーズ2（締切後・抽選確定前）
  else                      → フェーズ1（締切前）

SAME_DAY型:
  if (isAfterDeadline)      → フェーズ3相当（S-3, S-4）
  else                      → フェーズ1相当（S-1, S-2）
```

### 4.5 スケジューラー

- **LotteryScheduler**: 変更なし（抽選実行のみ。確定は管理者操作）。機能は残すがUI非表示
- **DensukeSyncScheduler**: 変更なし（5分間隔の定期同期）
- **OfferExpiryScheduler**: 変更なし（OFFERED→DECLINED の自動処理）

## 5. 影響範囲

### 5.1 バックエンド変更ファイル

| ファイル | 変更内容 | 影響度 |
|---------|---------|--------|
| `DensukeImportService.java` | フェーズ分岐・△/×処理追加。ほぼ全面書き換え | **大** |
| `DensukeWriteService.java` | `toJoinValue(null)` 変更、一括書き戻しメソッド追加 | 中 |
| `DensukeScraper.java` | `DensukeData` に `memberNames` 追加 | 小 |
| `DensukeSyncService.java` | フェーズ判定の組み込み | 中 |
| `LotteryService.java` | `confirmLottery()` 追加 | 中 |
| `LotteryController.java` | confirm エンドポイント追加 | 小 |
| `LotteryExecution.java` | `confirmedAt`, `confirmedBy` フィールド追加 | 小 |
| `PracticeParticipantService.java` | `registerParticipations` の物理削除→論理削除 | 中 |
| `WaitlistPromotionService.java` | 3-B2, 3-C6 用の処理追加 | 中 |

### 5.2 既存機能への影響

| 既存機能 | 影響内容 |
|---------|---------|
| 参加登録フロー | 論理削除化により、物理削除に依存する箇所（重複チェック等）の修正が必要 |
| 抽選フロー | 確定ステップの追加。確定前は伝助インポートがフェーズ2（スキップ）になる点が現行と異なる |
| 伝助インポート | 「抽選済みセッション全スキップ」が廃止 → フェーズ3ロジックに置き換わる。現行動作と大きく異なる |
| 伝助書き戻し | `toJoinValue(null)` が 0→1 に変わるため、未登録者の伝助表示が空白→×に変わる |
| キャンセル待ち繰り上げ | 伝助経由のキャンセル（3-C2）・オファー承諾（3-A8）・オファー辞退（3-C6）で既存の繰り上げロジックが呼ばれる |

### 5.3 DBマイグレーション

- `lottery_executions` テーブルに `confirmed_at` (TIMESTAMP), `confirmed_by` (BIGINT) カラムを追加

### 5.4 フロントエンド

- 抽選結果画面に「確定」ボタンの追加が必要（詳細は後日検討）
- その他のUI変更は本要件定義書確定後に追記する

## 6. 設計判断の根拠

| 判断 | 理由 |
|------|------|
| ○ vs not-○ の二値判定（フェーズ1） | △と×/空白の処理が同一であるため、ロジックを簡略化。利用者には○のみ意味があると周知 |
| △ = キャンセル待ち希望（フェーズ3） | △はシステムが WAITLISTED に対して設定する値。手動入力された場合もキャンセル待ちとして一貫処理することで、全ステータスで△の意味が統一される |
| 論理削除（CANCELLED + dirty=true） | 物理削除では dirty レコードが消滅し書き戻しがトリガーされない。論理削除により既存の dirty → 書き戻しフローを活用可能 |
| 抽選確定フローの導入 | 管理者が結果を確認・再抽選できるようにするため。確定前はフェーズ2を維持し、確定時に一括書き戻し |
| dirty=false のみインポート処理 | dirty=true のレコードはアプリ側の変更が優先。伝助の値で上書きされることを防ぐ |
| SAME_DAY型は一括書き戻しなし | 抽選がなく、各操作時の dirty ベース書き戻しで十分。不要な一括処理を省略 |
| OFFERED + 伝助○ = オファー承諾 | △→○ は「参加する」の意思表示。respondToOffer(true) 相当として既存ロジックを再利用。期限切れの場合はスケジューラーに委ねる |
| OFFERED + 伝助× = DECLINED | respondToOffer(false) 相当。WAITLIST_DECLINED ではなくDECLINEDが正しいステータス。既存の辞退→繰り上げロジックを再利用 |
| WON + 伝助△ = WAITLISTED（最後尾） | ○→△ は「確定ではなくキャンセル待ちでいい」という意思。WON枠を開放し通常の繰り上げフローを発動。エッジケース（他にWAITLISTEDがいない場合に自分自身がOFFEREDになる）は許容 |
