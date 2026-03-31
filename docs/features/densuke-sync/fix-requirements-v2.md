---
status: completed
audit_source: 会話内レポート（2026-03-31）
selected_items: [1, 2, 3, 4]
---

# 伝助との双方向同期機能 改修要件定義書（v2）

## 1. 改修概要

- **対象機能:** 伝助との双方向同期機能（アプリ ↔ 伝助 出欠同期）
- **改修の背景:** `/audit-feature` による監査（2026-03-31）で検出された機能上の問題・設計上の懸念に対応する。前回改修（v1: 2026-03-29）で対処済みの項目とは異なる新規検出事項。
- **改修スコープ:** 監査レポートの推奨アクション4項目（高1件・中3件）

---

## 2. 改修内容

### 2.1 項目1: 参加者削除の伝助書き戻し（高優先度・機能改善）

**現状の問題:**
`PracticeParticipantService.removeParticipantFromMatch()` (`PracticeParticipantService.java:322-325`) がレコードを物理削除（`deleteBySessionIdAndPlayerIdAndMatchNumber`）するため、`DensukeWriteService` が変更を検知できない。伝助側では○のまま残り、次回の読み取り同期で再取り込みされる。

**修正方針:**
物理削除を論理削除に変更する。`status` を `CANCELLED` に、`dirty` を `true` に設定する。`DensukeWriteService.toJoinValue()` は既に `CANCELLED` → `x`（不参加）にマッピングしているため、次回の書き込み同期で伝助に「x」が書き戻される。書き込み完了後（dirty=false）、次回の読み取り同期で伝助側も不参加なら自動削除される。

**修正後のあるべき姿:**
管理者がアプリ側で参加者を削除すると、伝助側にも不参加（x）が反映される。

---

### 2.2 項目2: `import-densuke` エンドポイントの削除（中優先度・セキュリティ/コード品質）

**現状の問題:**
`POST /api/practice-sessions/import-densuke` (`PracticeSessionController.java:366-393`) はフロントエンドから未使用。`url` パラメータを直接受け取っており、`saveDensukeUrl` にあるドメインチェック（`https://densuke.biz/`）がないため、ADMIN権限を持つユーザーが任意URLへのSSRFを実行可能。

**修正方針:**
エンドポイントを削除する。`DensukeImportService.importFromDensuke()` 自体は `syncDensuke` やスケジューラーから引き続き利用されるため、Serviceレイヤーへの影響はない。

**修正後のあるべき姿:**
伝助インポートは `sync-densuke` エンドポイント経由でのみ実行可能。DBに登録済みの（ドメイン検証済み）URLのみが使用される。

---

### 2.3 項目3: `updateSession()` のdirtyフラグ消失修正（中優先度・機能改善）

**現状の問題:**
`PracticeSessionService.updateSession()` (`PracticeSessionService.java:377`) が `deleteBySessionId()` で全参加者を物理削除し、新しい参加者リストで再作成する。新規作成時に `dirty` のデフォルト値 `true` が適用されるため、元々 `dirty=false`（伝助同期済み）だった参加者も `dirty=true` になり、伝助への不要な書き込みが発生する。

**修正方針:**
deleteAll + re-create パターンを差分更新に変更する：
1. 既存参加者のplayerIdセットとリクエストのplayerIdセットを比較
2. **新規追加分** → `dirty=true` で作成（全試合分）
3. **削除分** → `status=CANCELLED`, `dirty=true` に変更（項目1の方針と統一）
4. **継続分** → 既存レコードを維持（dirty値を保持）
5. **totalMatches変更時** → 試合数が増えた場合は新しい試合番号分を追加、減った場合は超過分を削除

**修正後のあるべき姿:**
セッション更新時に、伝助同期済みの参加者のdirtyフラグが不必要にtrueに変わらない。

---

### 2.4 項目4: 同期アーキテクチャの刷新 — イベント駆動書き込み＋5分間隔読み取り（中優先度・アーキテクチャ/パフォーマンス）

**現状の問題:**
1. `DensukeSyncScheduler.syncDensuke()` (`DensukeSyncScheduler.java:36-37`) と `PracticeSessionController.syncDensuke()` (`PracticeSessionController.java:527-531`) の両方で「`writeToDensuke()` → `importFromDensuke()`」の処理フローを独立して実装している。
2. 60秒間隔のスケジューラーが全団体×当月・翌月のURLに対してHTTPリクエスト（書き込み＋読み取り）を発行しており、アプリの動作が重くなっている。

**修正方針:**

`DensukeSyncService`（新規クラス）を作成し、同期フローを集約する。加えて、書き込みをイベント駆動に、読み取りを5分間隔に変更する。

#### A. 読み取り（伝助→アプリ）: 5分間隔スケジューラー
- スケジューラーのインターバルを60秒→5分（300,000ms）に変更
- `DensukeSyncService.syncAll()` を呼び出す（当月+翌月の全団体を処理）

#### B. 書き込み（アプリ→伝助）: イベント駆動
参加者の状態が変更される操作の直後に、即時書き込みを非同期で実行する。

**トリガー箇所:**

| トリガー操作 | Service / メソッド |
|------------|-------------------|
| 参加者追加 | `PracticeParticipantService.addParticipantToMatch()` |
| 参加者削除 | `PracticeParticipantService.removeParticipantFromMatch()` |
| 抽選結果確定 | `PracticeParticipantService.setMatchParticipants()` |
| 当日登録 | `PracticeParticipantService.registerSameDay()` |
| 事前登録 | `PracticeParticipantService.registerBeforeDeadline()` |
| セッション更新 | `PracticeSessionService.updateSession()` |
| キャンセル待ち辞退（LINE） | `WaitlistPromotionService.declineWaitlistBySession()` |
| 繰り上げオファー承諾/辞退（LINE） | `WaitlistPromotionService.respondToOffer()` |

各トリガー箇所で `DensukeSyncService.triggerWriteAsync()` を呼び出し、`@Async` で非同期実行する。

#### C. フォールバック: 5分スケジューラーでの残存dirty書き込み
イベント駆動書き込みが失敗した場合のリカバリとして、5分間隔のスケジューラー（読み取りの前）で `dirty=true` が残存している参加者の書き込みも行う。

- **即時書き込み**: 通常時のレスポンシブな同期（ほとんどのケースでここで完了）
- **5分スケジューラー**: `dirty=true` が残っていれば書き込み → その後読み取り（フォールバック）

#### D. 同期フローの集約
スケジューラーとControllerの両方が `DensukeSyncService` のメソッドを呼び出す形に統一する：
- `syncForOrganization(year, month, organizationId, userId)` — 指定団体の書き込み→読み取り（Controller・スケジューラー共用）
- `syncAll()` — 当月+翌月の全団体を処理（スケジューラー用）
- `triggerWriteAsync()` — dirty参加者の即時書き込み（イベント駆動用、`@Async`）

**修正後のあるべき姿:**
- 書き込みはアプリ側操作の直後に即時実行され、伝助への反映がリアルタイムになる
- 読み取りは5分間隔に緩和され、アプリの動作負荷が軽減される
- 即時書き込み失敗時も5分スケジューラーがリカバリし、データの整合性が保たれる
- 同期フローが `DensukeSyncService` に集約され、保守性が向上する

---

## 3. 技術設計

### 3.1 API変更

| エンドポイント | 変更内容 |
|--------------|---------|
| `POST /api/practice-sessions/import-densuke` | **削除** |
| `DELETE /{sessionId}/matches/{matchNumber}/participants/{playerId}` | 内部動作を物理削除から論理削除（CANCELLED + dirty=true）に変更。レスポンスは変更なし（204 No Content） |

### 3.2 DB変更

なし（既存のカラムで対応可能）

### 3.3 フロントエンド変更

なし（削除するエンドポイントはフロントエンドから未使用。他のAPIレスポンス形式は変更なし）

### 3.4 バックエンド変更

| ファイル | 変更内容 |
|---------|---------|
| `PracticeSessionController.java` | `importFromDensuke()` メソッドを削除。`syncDensuke()` を `DensukeSyncService` への委譲に変更 |
| `PracticeParticipantService.java` | `removeParticipantFromMatch()` を物理削除から論理削除（CANCELLED + dirty=true）に変更。`addParticipantToMatch()`, `setMatchParticipants()`, `registerSameDay()`, `registerBeforeDeadline()` の各メソッド末尾に `DensukeSyncService.triggerWriteAsync()` 呼び出しを追加 |
| `PracticeSessionService.java` | `updateSession()` を差分更新に変更。末尾に `DensukeSyncService.triggerWriteAsync()` 呼び出しを追加 |
| `WaitlistPromotionService.java` | `declineWaitlistBySession()`, `respondToOffer()` の末尾に `DensukeSyncService.triggerWriteAsync()` 呼び出しを追加 |
| `DensukeSyncService.java`（**新規**） | `syncForOrganization()` / `syncAll()` / `triggerWriteAsync()` メソッドを持つ同期サービスクラス |
| `DensukeSyncScheduler.java` | `syncDensuke()` を `DensukeSyncService.syncAll()` の呼び出しに変更。インターバルを60秒→5分に変更 |
| `AsyncConfig.java`（**新規** or 既存の設定クラス） | `@EnableAsync` を追加（未設定の場合） |

---

## 4. 影響範囲

### 4.1 影響を受ける既存機能

| 機能 | 影響内容 |
|-----|---------|
| 参加者削除（管理画面） | 物理削除→論理削除に変わるが、UIからは「削除された」ように見える（CANCELLEDステータスは参加者一覧に表示されない前提） |
| セッション更新（管理画面） | 差分更新に変わるが、フロントエンドからの見た目は変わらない |
| 伝助読み取り同期 | CANCELLED + dirty=false の参加者は、伝助側で不参加なら自動削除される既存ロジックで回収される。読み取り間隔が60秒→5分に変わるため、伝助側の変更がアプリに反映されるまでの遅延が最大5分に増加する |
| 伝助書き込み同期 | CANCELLED + dirty=true の参加者が「x」として書き込まれるようになる（既存のマッピングを活用）。イベント駆動により、アプリ側操作直後に即時書き込みが実行される |
| LINE通知からの操作 | キャンセル待ち辞退・繰り上げオファー承諾/辞退の操作後にも即時書き込みが実行されるようになる |

### 4.2 破壊的変更

- `POST /import-densuke` の削除は破壊的変更だが、フロントエンドから未使用のため実影響なし
- 伝助読み取り同期の間隔が60秒→5分に変更されるが、伝助側の変更頻度を考慮すると実用上の問題はない

---

## 5. 設計判断の根拠

| 判断 | 理由 |
|-----|------|
| 項目1: 論理削除（CANCELLED + dirty=true） | 物理削除のままでは伝助への変更通知手段がない。既存の `toJoinValue()` マッピング（CANCELLED→x）を活用でき、追加の変換ロジックが不要 |
| 項目2: エンドポイント削除（SSRFガード追加ではなく） | フロントエンドから未使用のデッドコードであり、残す理由がない。`syncDensuke` が統合エンドポイントとして十分に機能している |
| 項目3: 差分更新（deleteAll + re-create の維持ではなく） | dirty フラグの消失は機能的な不具合であり、差分更新で根本解決する。re-create時にdirty値を引き継ぐ方法もあるが、不要な削除・再作成のDB負荷も避けられる差分更新が望ましい |
| 項目4: 新規 `DensukeSyncService` クラス | 既存の `DensukeWriteService` や `DensukeImportService` に同期フロー制御を入れると責務が曖昧になる。「同期フローの制御」という独立した責務を持つクラスが適切 |
| 項目4: イベント駆動書き込み + 5分フォールバック | 60秒ポーリングの書き込みは無駄なHTTPリクエストが多く、アプリの動作負荷の原因。イベント駆動で即時性を確保しつつ、5分スケジューラーをフォールバックとして残すことで、書き込み失敗時のリカバリも保証される |
| 項目4: 読み取り間隔5分 | 伝助側の変更は人間の操作であり、秒単位の即時性は不要。5分間隔で十分な鮮度を確保できる。負荷が大幅に軽減される |
| 項目4: `@Async` による非同期書き込み | イベント駆動の書き込みは外部HTTPリクエストを伴うため、ユーザー操作のレスポンスをブロックしないよう非同期で実行する |
