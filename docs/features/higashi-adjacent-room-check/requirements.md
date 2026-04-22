---
status: completed
---
# 東区民センター隣室空き確認 要件定義書

## 1. 概要

### 目的
東区民センターの和室利用時に、隣室（かっこう）の空き状況を自動で確認し、定員接近時に管理者へ通知する。カレンダーのポップアップで全ユーザーが隣室の空き状況を確認でき、管理者は会場拡張（東🌸→東全室）を実行できるようにする。

### 背景・動機
既にかでる2・7で稼働中の「[隣室空き確認通知](../adjacent-room-check/requirements.md)」機能の東区民センター版。北大かるた会は東区民センターのさくら（和室）を通常利用しており、参加者が定員に近づいた際は隣室のかっこうも借りて「和室全室」として拡張利用している。現在は手動確認・予約しているが、これを自動化し運用をかでると揃える。

## 2. ユーザーストーリー

### 対象ユーザー
- **管理者（SUPER_ADMIN / ADMIN）**: 定員接近時のかっこう空き通知を受け取り、会場拡張の判断・操作を行う
- **一般プレイヤー（PLAYER）**: カレンダーでかっこうの空き状況を確認し、定員近くても会場拡張の可能性があるなら参加を前向きに検討できる

### 利用シナリオ
1. **管理者への自動通知**: 東🌸（さくら）を使う練習日の試合で定員まで残り4人以下になった時、かっこうの空きをスクレイピングで確認し、空いていれば管理者に通知
2. **カレンダーでの確認**: 全ユーザーがカレンダーで東🌸のセッション詳細を開いた際、隣室（かっこう）の空き状況を確認できる
3. **会場拡張**: 管理者が拡張ボタンを押すと、確認ダイアログ後に会場が「東🌸」→「東全室」に切り替わり、定員が拡張後の値（14→18）に更新される

## 3. 機能要件

### 3.1 隣室ペアの定義

| 単体会場 | Venue ID | 隣接会場 | Venue ID | 拡張後会場 | Venue ID | 拡張後定員 |
|----------|----------|----------|----------|------------|----------|-----------|
| 東🌸（さくら） | 6 | かっこう | (新規) | 東全室 | 10 | 18 |

- かっこう単独での予約運用は発生しないため、かっこう→さくら方向の空き確認は行わない
- Config上は双方向を定義するが、スケジューラーのチェック対象は「東🌸」セッションのみ

### 3.2 新規 Venue「かっこう」

| 項目 | 値 |
|------|-----|
| name | `かっこう` |
| capacity | 4 |
| default_match_count | 2 |
| id | 自動採番 |

### 3.3 隣室空き確認（スクレイピング）
- **対象サイト**: `https://sapporo-community.jp/UserWebApp/Form/SsfSvrRoomAvailabilityMonth.aspx`
- **ログイン**: 不要（公開ページ）
- **チェック対象部屋**: かっこう のみ（さくら・和室全室は対象外）
- **チェック時間帯**: 18:00-21:00（夜間）のみ
- **対象期間**: 当月 + 翌月
- **取得方法**: 月表示ページから1リクエストで1ヶ月分まとめて取得
- **空き状態**: サイトの表記に従い `○`（空き）/ `×`（予約済）等を `room_availability_cache` にそのまま保存
- **実行方式**: Node.js + Playwright スクリプト（GitHub Actions で30分間隔）
- **結果の保存**: 既存 `room_availability_cache` テーブルに UPSERT（`room_name='かっこう'`）

### 3.4 定員接近時の段階的通知
既存 [隣室空き確認通知](../adjacent-room-check/requirements.md)の仕組みを流用する。
- **トリガー条件**: セッションの全試合のうち最も定員に近い試合で、定員まで残り4人以下
- **段階的通知**: 残り人数が変わるたびに通知（4→3→2→1→定員到達）
- **対象セッション**: venue_id=6（東🌸）のみ
- **通知先**: SUPER_ADMIN全員 + 該当セッションの団体のADMIN
- **通知タイプ**: 既存の `NotificationType.ADJACENT_ROOM_AVAILABLE` を流用
- **通知内容例**:
  - 残り4〜1人: 「4/25 東🌸の試合1が定員まで残り4人です。隣室（かっこう）は夜間(18-21)空きです。」
  - 定員到達: 「4/25 東🌸の試合1が定員に達しました。隣室（かっこう）は夜間(18-21)空きです。」
- **重複防止**: 既存 `adjacent_room_notifications` テーブルで (session_id, remaining_count) 単位で管理

### 3.5 カレンダーポップアップでの表示
- **表示条件**: セッションの venue_id=6（東🌸）の場合のみ
- **表示内容**: 隣室名（かっこう）と空き状況（空き/予約済 等）
- **対象ユーザー**: 全ロール（PLAYER含む）
- **データ取得**: 既存 `PracticeSessionService` の仕組みで `adjacentRoomStatus` を付与して返す

### 3.6 会場拡張ボタン
- **表示条件**: 管理者（ADMIN/SUPER_ADMIN）のみ、かつ venue_id=6（東🌸）、かつかっこうが空きの場合
- **確認ダイアログ**: 「東🌸 を 東全室 に拡張しますか？ 定員が14→18に変更されます」
- **動作**: 既存 `POST /api/practice-sessions/{id}/expand-venue` を利用。`AdjacentRoomConfig.getExpandedVenueId(6)` が 10 を返すよう拡張

## 4. 技術設計

### 4.1 DB設計

**新規INSERT**: `venues` テーブル
```sql
INSERT INTO venues (name, capacity, default_match_count, created_at, updated_at)
VALUES ('かっこう', 4, 2, NOW(), NOW());
```

- マイグレーションファイル: `database/insert_kakkou_venue.sql`
- 採番された新ID（仮に `K_ID` とする）を Config に反映する

**既存テーブル**: 変更なし
- `room_availability_cache` — `room_name='かっこう'` を追加するのみ（スキーマ変更不要）
- `adjacent_room_notifications` — そのまま流用

### 4.2 バックエンド設計

**`AdjacentRoomConfig.java` の拡張**
- 隣室ペアマップに「東🌸(6) ↔ かっこう(K_ID)」を追加
- `getExpandedVenueId(6)` → 10、`getExpandedVenueId(K_ID)` → 10 を追加
- 既存メソッドをより汎用的に（例: `getSiteRoomName` や `isTargetRoom`）。命名は実装時に判断
- 東🌸のスケジューラー判定用メソッド（`isHigashiSakuraRoom` 等）を追加、もしくは既存 `isKaderuRoom` を包含する `isAdjacentCheckTarget` に統一

**`AdjacentRoomNotificationScheduler.java` の拡張**
- `isKaderuRoom` フィルタを「隣室チェック対象の会場」に拡張し、venue_id=6 も含める
- 通知メッセージの時間帯表記を会場により切り替え:
  - かでる和室: `夜間(17-21)`
  - 東🌸: `夜間(18-21)`
- 時間帯文字列を `AdjacentRoomConfig` から取得する形に変更（例: `getNightTimeLabel(venueId)`）

**`AdjacentRoomService.java`**
- `getAdjacentRoomAvailability(venueId, date)` はそのまま流用可能
- 引数 `venueId=6` に対して「かっこう」の空きを返すよう、Config経由で部屋名を解決

**通知タイプ**: 既存 `NotificationType.ADJACENT_ROOM_AVAILABLE` を流用（新規追加なし）

**APIエンドポイント**: 既存 `POST /api/practice-sessions/{id}/expand-venue` を流用（追加なし）

### 4.3 フロントエンド設計

**`PracticeList.jsx`（モーダルヘッダー）**
- 既存ロジックに venue_id=6 を加えるだけ。新規コンポーネント不要
- 表示条件: venue_id ∈ {3, 4, 8, 11, 6}（かでる和室4部屋 + 東🌸）
- 会場拡張ボタンも同様に条件拡張

**`practices.js`**: 変更なし（`expandVenue()` は流用）

### 4.4 スクレイピングスクリプト（Node.js + Playwright）

**新規ファイル**: `scripts/room-checker/sync-higashi-availability-to-db.js`

**処理フロー**:
1. 対象期間（当月、翌月）の各月について、月表示ページへアクセス
   - URL: `https://sapporo-community.jp/UserWebApp/Form/SsfSvrRoomAvailabilityMonth.aspx`
   - クエリパラメータまたはフォーム送信で 施設=103、部屋=041（かっこう）、対象月を指定
2. 月ビューから各日付の 18-21時 スロット空き状況をDOM抽出
3. `room_availability_cache` に UPSERT
   - `room_name='かっこう'`, `target_date`, `time_slot='evening'`, `status`, `checked_at=NOW()`
4. エラー時は `process.exit(1)`（次回cron再試行）

**入力**
- CLI: `--months <N>`（デフォルト 2）
- 環境変数: `DATABASE_URL`

**注意事項**
- スクレイピング対象ページ（月表示）のDOM構造は実装時に実地調査して確定する
- 既存 `scrape-higashi-history.js` とは別ファイルとする（データ源が異なる）

### 4.5 GitHub Actions ワークフロー

**新規ファイル**: `.github/workflows/check-higashi-availability.yml`

`check-kaderu-availability.yml` を複製し以下を変更:
- `name`: `Check Higashi Community Center Room Availability`
- `concurrency.group`: `higashi-availability-check`
- 実行コマンド: `node sync-higashi-availability-to-db.js --months 2`
- 環境変数: `DATABASE_URL`: `${{ secrets.KADERU_DATABASE_URL }}`（既存流用）

cron: `*/30 * * * *`（30分間隔）

## 5. 影響範囲

### 5.1 変更が必要な既存ファイル

**バックエンド:**
- `AdjacentRoomConfig.java` — 隣室ペアに東🌸↔かっこう を追加、時間帯ラベル取得メソッド追加
- `AdjacentRoomNotificationScheduler.java` — フィルタ条件に東🌸を追加、通知メッセージの時間帯を会場別に切り替え

**フロントエンド:**
- `PracticeList.jsx` — 隣室状況表示・拡張ボタンの表示条件に venue_id=6 を追加

**DB:**
- 新規マイグレーション `database/insert_kakkou_venue.sql`

**スクリプト:**
- `scripts/room-checker/sync-higashi-availability-to-db.js` 新規作成
- `scripts/room-checker/package.json` — 依存パッケージ不足があれば追加（`pg`, `playwright` は既存）

**GitHub Actions:**
- `.github/workflows/check-higashi-availability.yml` 新規作成

### 5.2 既存機能への影響
- **かでる隣室通知**: 同一スケジューラーだが venue_id でフィルタ分岐するので影響なし
- **東区民センター予約同期**: 無関係（別ワークフロー・別データ源）
- **`PracticeSessionDto.adjacentRoomStatus`**: 既存フィールド。venue_id=6 の場合も値が埋まるようになる（新動作）
- **venues テーブル**: 新規INSERTのみ、既存レコードは変更しない
- **後方互換**: 全体として破壊的変更なし

### 5.3 データ整合性
- かっこうの venue_id は自動採番のため、Config に反映するタイミングで整合を取る必要あり（実装手順書で明示）
- 1日に東区民センター予約とかでる予約が併存しないことは、`higashi-community-center-sync` のQ13と同じ前提

### 5.4 セキュリティ
- スクレイピング対象ページはログイン不要のため資格情報不要
- DB接続情報のみ GitHub Secrets の `KADERU_DATABASE_URL` を流用

## 6. 設計判断の根拠

| 判断 | 理由 |
|------|------|
| かっこう単体Venue を新規追加（案a採用） | かでる既存実装との整合性。`AdjacentRoomConfig` で双方向の隣室ペアを定義する構造を流用できる。拡張後会場が常に `getExpandedVenueId()` で解決される既存ロジックに乗る |
| かっこう単独のスクレイピングは実施しない | かっこう単独予約の運用実態がないため。さくら側で定員接近した場合のみかっこう空き確認すればよい |
| スクレイピングスクリプトを兄弟構成で新規作成 | `higashi-community-center-sync` の兄弟構成（`sync-reservations.js` / `sync-higashi-reservations.js`）に倣う。ファイル分割により各サイトのDOM変更時の影響が閉じ込められる |
| GitHub Actions workflowも別ファイル | concurrency group を分け、一方の障害が他方に波及しないようにする |
| 単一スケジューラーで両サイト処理 | 定員接近判定・通知重複防止ロジックが完全共通。venue_id で分岐するだけで十分 |
| 通知タイプを流用 | 「隣室空きあり」という本質は同じ。ユーザー設定（通知ON/OFF）も会場ごとに分ける必然性がない |
| 時間帯ラベル(17-21 vs 18-21)を会場別に | ハードコードされた現在の "夜間(17-21)" を東区民センターの実利用時間 18-21 に合わせるため。Config に切り出すのが保守的 |
