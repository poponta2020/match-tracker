---
status: completed
audit_source: ユーザー直接リクエスト
selected_items: [1]
---
# メンティーメモ更新時のメンターLINE通知 改修要件定義書

## 1. 改修概要

- **対象機能**: 試合結果の個人メモ（`MatchPersonalNote`）保存処理（`MatchService.upsertPersonalNote()`）
- **改修の背景**: メンティーが試合結果のメモを更新しても、メンターにはその事実が通知されない。メンターはメンティーのメモを閲覧できるが、更新を知る手段がないため、能動的に確認しに行く必要がある。
- **改修スコープ**: バックエンドのみ（LINE通知ロジック追加）。フロントエンド変更なし。

## 2. 改修内容

### 2.1 メモ保存時のLINE即時通知

- **現状の問題**: `MatchService.upsertPersonalNote()` は `MatchPersonalNote` をDBに保存するのみで、通知トリガーが一切ない。メンターはメンティーのメモが更新されたことを知る手段がない。
- **修正方針**: `upsertPersonalNote()` 内でメモの変更を検知し、変更があった場合に全ACTIVEメンターへLINE Flex Messageを即時送信する。
- **修正後のあるべき姿**: メンティーがメモを新規入力または更新すると、メンターのLINEにメモ内容を含むFlex Messageが届く。

### 2.2 通知トリガー条件

| 条件 | 通知する？ |
|------|:---:|
| 新規試合作成時にメモを入力 | ○ |
| 既存試合編集時にメモを変更 | ○ |
| 既存試合編集時にメモ未変更（スコアのみ変更等） | × |
| メモが空白のみ / null | × |
| お手付き回数のみ変更（メモ変更なし） | × |

### 2.3 Flex Message構成

- **ヘッダー**: 背景色 `#4a6b5a`（コメント通知と統一）、タイトル「メモ更新」
- **ボディ**:
  - 「○○さんがメモを更新しました」
  - 試合情報（日付、第N試合、vs 対戦相手名）
  - 区切り線
  - メモ本文
- **altText**: 「○○さんが試合メモを更新しました」

### 2.4 通知方向・通知設定

- **方向**: メンティー → メンター（一方向）
- **通知設定**: 既存の `mentorComment` トグルで制御（独立トグルは新設しない）

## 3. 技術設計

### 3.1 DB変更

なし。新規カラム・テーブルの追加は不要。

### 3.2 API変更

なし。既存のメモ保存API（`PUT /api/matches/{id}`, `PUT /api/matches/{id}/detailed`, `POST /api/matches`, `POST /api/matches/detailed`）の内部動作として通知が追加される。

### 3.3 バックエンド変更

| ファイル | 変更内容 |
|---------|---------|
| `LineMessageLog.java` | `LineNotificationType` に `MENTEE_MEMO_UPDATE` を追加 |
| `LineNotificationService.java` | `isLineTypeEnabled()` に `MENTEE_MEMO_UPDATE → mentorComment` マッピング追加。`sendMemoUpdateFlexNotification()` メソッド追加。`buildMemoUpdateFlex()` プライベートメソッド追加 |
| `MatchService.java` | `LineNotificationService` を依存注入。`upsertPersonalNote()` 内で旧メモと比較し、変更時に通知メソッドを呼び出し |

### 3.4 フロントエンド変更

なし。

## 4. 影響範囲

| 影響箇所 | 影響内容 | リスク |
|---------|---------|--------|
| `MatchService.upsertPersonalNote()` | メモ変更検知 + 通知呼び出しを追加 | 4箇所の呼び出し元すべてで同じ動作。通知失敗時もメモ保存は正常に完了する（try-catch で囲む） |
| `LineNotificationService` | 新メソッド追加のみ。既存メソッドへの影響なし | なし |
| `LineNotificationType` enum | 値追加。既存の switch 文に case 追加が必要 | switch の網羅性チェックでコンパイル時に検出可能 |
| 通知設定 `mentorComment` | メモ更新通知もこのトグルで制御される | コメント通知OFFにするとメモ通知もOFF。意図した動作 |

## 5. 設計判断の根拠

| 判断 | 理由 |
|------|------|
| `upsertPersonalNote()` 内で通知を実装 | 4箇所の呼び出し元すべてで同じ動作が必要。単一の変更ポイントでDRYを保つ |
| 旧メモとの比較で変更検知 | フロントエンドは編集時に既存メモを常に再送するため、内容未変更でも保存APIが呼ばれる。比較なしだと不要な通知が大量に飛ぶ |
| `MENTEE_MEMO_UPDATE` を新設（`MENTOR_COMMENT` 再利用ではなく） | 通知ログ上でコメント通知とメモ通知を区別可能にするため |
| `mentorComment` トグル共用 | ユーザー要件。独立トグルは過剰 |
| フロントエンド変更なし | 保存操作に自動で紐づくため、UIの追加は不要 |
