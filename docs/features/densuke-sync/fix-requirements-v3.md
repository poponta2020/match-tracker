---
status: completed
audit_source: 会話内レポート（2026-04-01）
selected_items: [1, 2]
---

# 伝助との双方向同期機能 改修要件定義書（v3）

## 1. 改修概要

- **対象機能:** 伝助との双方向同期機能（アプリ ↔ 伝助 出欠同期）
- **改修の背景:** `/audit-feature` による監査（2026-04-01）で検出された品質・運用上の問題に対応する。前回改修（v1: 2026-03-29、v2: 2026-03-31）で対処済みの項目とは異なる新規検出事項。
- **改修スコープ:** 監査レポートの推奨アクション 優先度中 2項目

---

## 2. 改修内容

### 2.1 項目1: DensukeImportService の Phase3 ロジック ユニットテスト追加（品質改善）

**現状の問題:**
- Phase3 ロジック（`processPhase3Maru`, `processPhase3Sankaku`, `processPhase3Batsu`）のテストが **0件**
- 要件マトリクスで定義された 30 ケース（3-A: 12件、3-B: 9件、3-C: 9件）がすべて未テスト
- Phase3 はアプリの肝となる部分（抽選確定後の伝助→アプリ同期）であり、ステータス遷移・dirty保護・reactivate・demotion・promotion 等の複雑なビジネスロジックを含む

**修正方針:**
- 要件マトリクスの全 30 ケースを網羅するユニットテストを `DensukeImportServiceTest` に追加
- 既存テストと同じパターン（Mockito + AssertJ）を踏襲
- テストケースは要件番号（3-A1〜3-C9）に対応させ、トレーサビリティを確保

**修正後のあるべき姿:**
- Phase3 の全分岐がテストで保護される
- 仕様変更時にデグレードを検知可能

### 2.2 項目2: `parseAndSaveRowIds()` 行ID不一致時のログ引き上げ＋管理者通知（運用改善）

**現状の問題:**
- `DensukeWriteService.parseAndSaveRowIds()` で joinID 数と schedule 数が不一致の場合、`log.warn()` で警告するのみで行ID保存をスキップ
- 行IDが取得できないと、該当セッションへの書き込みがサイレントにスキップされ、参加者が `dirty=true` のまま滞留し続ける
- 管理者にこの状態が可視化されず、問題に気づけない

**修正方針:**
1. ログレベルを `log.warn()` → `log.error()` に引き上げ
2. 管理者へ LINE 通知＋アプリ内通知の両方で通知
   - 新しい通知タイプ `DENSUKE_ROW_ID_MISMATCH` を追加
   - 既存の `DENSUKE_UNMATCHED_NAMES` と同様のパターンで実装

**修正後のあるべき姿:**
- 行ID不一致が発生した際、管理者がLINEとアプリ内通知の両方で即座に認知できる
- 管理者は伝助側のデータ状態を確認し、手動同期等で対処可能

---

## 3. 技術設計

### 3.1 API変更

なし

### 3.2 DB変更

| テーブル | 変更内容 |
|---------|---------|
| `push_notification_preferences` | `densuke_row_id_mismatch BOOLEAN NOT NULL DEFAULT true` カラム追加 |
| `line_notification_preferences` | `densuke_row_id_mismatch BOOLEAN NOT NULL DEFAULT true` カラム追加 |

### 3.3 フロントエンド変更

| ファイル | 変更内容 |
|---------|---------|
| `NotificationSettings.jsx` | 通知設定画面に「伝助行ID不一致通知」のトグルを追加（Push通知・LINE通知それぞれ） |

### 3.4 バックエンド変更

#### 項目1: Phase3 テスト追加

| ファイル | 変更内容 |
|---------|---------|
| `DensukeImportServiceTest.java` | Phase3 の全 30 ケースのテストメソッドを追加 |

テストケース一覧:

**3-A: 伝助○の場合（12ケース）**
| ケース | テスト内容 |
|--------|----------|
| 3-A1 | 未登録 + 空きあり → WON で新規登録 |
| 3-A2 | 未登録 + 定員超過 → WAITLISTED で新規登録（順番号付与） |
| 3-A3 | 未登録 + 定員未設定 → WON で新規登録 |
| 3-A4 | WON + dirty=false → スキップ |
| 3-A5 | WON + dirty=true → スキップ（dirty保護） |
| 3-A6 | WAITLISTED + dirty=false → dirty=true に設定（△書き戻し） |
| 3-A7 | WAITLISTED + dirty=true → スキップ（dirty保護） |
| 3-A8a | OFFERED + dirty=false + 期限内 → オファー承認（WON） |
| 3-A8b | OFFERED + dirty=false + 期限切れ → スキップ |
| 3-A9 | OFFERED + dirty=true → スキップ（dirty保護） |
| 3-A10 | CANCELLED/DECLINED/WD + dirty=false + 空きあり → WON で再活性化 |
| 3-A11 | CANCELLED/DECLINED/WD + dirty=false + 定員超過 → WAITLISTED で再活性化 |

**3-B: 伝助△の場合（9ケース）**
| ケース | テスト内容 |
|--------|----------|
| 3-B1 | 未登録 → WAITLISTED（末尾）で新規登録 |
| 3-B2 | WON + dirty=false → WAITLISTED に降格 + 繰り上げトリガー |
| 3-B3 | WON + dirty=true → スキップ（dirty保護） |
| 3-B4 | WAITLISTED + dirty=false → スキップ |
| 3-B5 | WAITLISTED + dirty=true → スキップ（dirty保護） |
| 3-B6 | OFFERED + dirty=false → スキップ |
| 3-B7 | OFFERED + dirty=true → スキップ（dirty保護） |
| 3-B8 | CANCELLED/DECLINED/WD + dirty=false → WAITLISTED（末尾）で再活性化 |
| 3-B9 | CANCELLED/DECLINED/WD + dirty=true → スキップ（dirty保護） |

**3-C: 伝助×/空白の場合（9ケース）**
| ケース | テスト内容 |
|--------|----------|
| 3-C1 | 未登録 → スキップ（何もしない） |
| 3-C2 | WON + dirty=false → キャンセル + 繰り上げトリガー |
| 3-C3 | WON + dirty=true → スキップ（dirty保護） |
| 3-C4 | WAITLISTED + dirty=false → WAITLIST_DECLINED + 後続の順番繰り上げ |
| 3-C5 | WAITLISTED + dirty=true → スキップ（dirty保護） |
| 3-C6 | OFFERED + dirty=false → オファー辞退 + 次の繰り上げトリガー |
| 3-C7 | OFFERED + dirty=true → スキップ（dirty保護） |
| 3-C8 | CANCELLED/DECLINED/WD + dirty=false → スキップ |
| 3-C9 | CANCELLED/DECLINED/WD + dirty=true → スキップ（dirty保護） |

#### 項目2: 行ID不一致通知

| ファイル | 変更内容 |
|---------|---------|
| `Notification.java` | `NotificationType` enum に `DENSUKE_ROW_ID_MISMATCH` を追加 |
| `LineMessageLog.java` | `LineNotificationType` enum に `DENSUKE_ROW_ID_MISMATCH` を追加 |
| `PushNotificationPreference.java` | `densukeRowIdMismatch` フィールド追加 |
| `LineNotificationPreference.java` | `densukeRowIdMismatch` フィールド追加 |
| `NotificationService.java` | `isTypeEnabled()` に `DENSUKE_ROW_ID_MISMATCH` の分岐追加 |
| `LineNotificationService.java` | `isTypeEnabled()` に `DENSUKE_ROW_ID_MISMATCH` の分岐追加、`sendAdminRowIdMismatchNotification()` メソッド追加 |
| `DensukeWriteService.java` | `parseAndSaveRowIds()` のログを `log.error()` に変更、通知メソッド呼び出しを追加 |
| DBマイグレーション | 2テーブルにカラム追加のSQLファイル |

---

## 4. 影響範囲

### 項目1: Phase3 テスト追加

- **影響を受ける既存機能:** なし（テストコードの追加のみ）
- **破壊的変更:** なし

### 項目2: 行ID不一致通知

- **影響を受ける既存機能:**
  - `DensukeWriteService.parseAndSaveRowIds()` — ログレベル変更 + 通知呼び出し追加
  - 通知設定画面 — 新しいトグル項目の追加
  - `NotificationService.isTypeEnabled()` — 新しい case 追加（既存の case に影響なし）
  - `LineNotificationService.isTypeEnabled()` — 新しい case 追加（既存の case に影響なし）
- **破壊的変更:** なし（既存のAPIレスポンス・DBスキーマに対して後方互換）

---

## 5. 設計判断の根拠

| 判断 | 理由 |
|------|------|
| Phase3 テストを全 30 ケース網羅 | Phase3 はアプリの肝（抽選確定後のステータス遷移）であり、部分的なテストではリスクが残る |
| 通知は LINE + アプリ内の両方 | 管理者が確実に気づけるよう、既存の二重通知パターン（`DENSUKE_UNMATCHED_NAMES` 等）と同様に両チャネルで通知 |
| 通知タイプに専用の enum 値を新設 | 既存タイプの流用ではなく専用タイプとすることで、通知の有効/無効を個別に制御可能にする |
| ログレベルを ERROR に引き上げ | 書き込みが継続的にスキップされる運用上の問題であり、WARN よりも ERROR が適切 |
