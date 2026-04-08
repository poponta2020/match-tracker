---
status: completed
---
# 隣室空き確認通知 要件定義書

## 1. 概要

### 目的
かでる2・7の和室利用時に、定員に近づいた練習セッションの隣室の空き状況を自動で確認し、管理者に通知する。また、カレンダーのポップアップで全ユーザーが隣室の空き状況を確認できるようにする。

### 背景・動機
北大かるた会はかでる2・7の和室4部屋（すずらん、はまなす、あかなら、えぞまつ）を利用している。隣接する部屋は障子で仕切られているだけで、両方を予約すれば大きな1部屋として使える（すずらん↔はまなす、あかなら↔えぞまつ）。現在は手動で隣室の空きを確認・予約しているが、これを自動化したい。

## 2. ユーザーストーリー

### 対象ユーザー
- **管理者（SUPER_ADMIN / ADMIN）**: 定員接近時の隣室通知を受け取り、会場拡張の判断・操作を行う
- **一般プレイヤー（PLAYER）**: カレンダーで隣室の空き状況を確認し、定員近くても会場拡張の可能性があるなら参加を前向きに検討できる

### 利用シナリオ
1. **管理者への自動通知**: ある練習日の最も定員に近い試合で残り4人以下になった時、隣室の空き状況をスクレイピングで確認し、空いていれば管理者に通知する
2. **カレンダーでの確認**: 全ユーザーがカレンダーで日付をクリックした際のポップアップに、隣室の空き状況が表示される
3. **会場拡張**: 管理者が拡張ボタンを押すと、確認ダイアログ後に会場が「すずらん」→「すずらん・はまなす」に切り替わり、定員が拡張後の値に更新される

## 3. 機能要件

### 3.1 隣室ペアの定義

| 単体会場 | Venue ID | 隣接会場 | Venue ID | 拡張後会場 | Venue ID | 拡張後定員 |
|----------|----------|----------|----------|------------|----------|-----------|
| すずらん | 3 | はまなす | 11 | すずらん・はまなす | 7 | 24 |
| はまなす | 11 | すずらん | 3 | すずらん・はまなす | 7 | 24 |
| あかなら | 4 | えぞまつ | 8 | あかなら・えぞまつ | 9 | 24 |
| えぞまつ | 8 | あかなら | 4 | あかなら・えぞまつ | 9 | 24 |

隣室ペア情報はアプリ設定（定数）で管理する。変更頻度が低いため。

### 3.2 隣室空き確認（スクレイピング）
- **対象サイト**: https://k2.p-kashikan.jp/kaderu27/index.php
- **確認する時間帯**: 夜間(17:00-21:00)のみ
- **サイト上の部屋名とアプリVenue名の対応**:
  - サイト: 「すずらん(24人)」→ アプリ: 「すずらん」
  - サイト: 「はまなす(15人)」→ アプリ: 「はまなす」
  - サイト: 「あかなら(24人)」→ アプリ: 「あかなら」
  - サイト: 「えぞまつ(24人)」→ アプリ: 「えぞまつ」
- **空き状態**: ○=空き、×=予約済、-=利用不可、●=要問合せ、休館
- **実行方式**: Node.jsスクリプト（Playwright）が定期実行し、結果をPostgreSQLに書き込む。バックエンドはDBを参照するのみ
- **結果の保存**: 新テーブル `room_availability_cache` に保存

### 3.3 定員接近時の段階的通知
- **トリガー条件**: セッションの全試合のうち最も定員に近い試合で、定員まで残り4人以下
- **段階的通知**: 残り人数が変わるたびに通知する（残り4人→3人→2人→1人→定員到達の各段階で1回ずつ）
- **チェックタイミング**: バックエンドの定期バッチ（スケジューラー）。スクレイピング結果はDBにキャッシュ済みなのでDBを見るだけ
- **通知先**: SUPER_ADMIN全員 + 該当セッションの団体のADMIN
- **通知内容例**:
  - 残り4〜1人: 「4/12 すずらんの試合2が定員まで残り4人です。隣室（はまなす）は夜間(17-21)空きです。」
  - 定員到達: 「4/12 すずらんの試合2が定員に達しました。隣室（はまなす）は夜間(17-21)空きです。」
- **通知条件**: 隣室が空いている場合のみ通知（予約済みなら通知不要）
- **重複防止**: セッション × 残り人数の段階ごとに1回のみ通知（同じ段階では再通知しない）

### 3.4 カレンダーポップアップでの表示
- **表示位置**: ヘッダー部分の会場名の横
- **表示条件**: セッションの会場がかでる2・7の和室（Venue ID: 3, 4, 8, 11）の場合のみ
- **表示内容**: 隣室名と空き状況（空き/予約済 等）
- **対象ユーザー**: 全ロール（PLAYER含む）
- **データ取得**: セッション詳細取得時に、DBキャッシュから隣室の空き状況を付与して返す

### 3.5 会場拡張ボタン
- **表示条件**: 管理者（ADMIN/SUPER_ADMIN）のみ、かつ会場がかでる2・7の和室（ID: 3, 4, 8, 11）の場合
- **確認ダイアログ**: 「すずらんをすずらん・はまなすに拡張しますか？定員が14→24に変更されます」
- **動作**: venue_idを拡張後Venue IDに変更、capacityを拡張後Venueのcapacityに更新
- **API**: PracticeSessionの更新エンドポイントを利用するか、専用エンドポイントを追加

## 4. 技術設計

### 4.1 DB設計

**新テーブル: `room_availability_cache`**

| カラム | 型 | 説明 |
|--------|-----|------|
| id | BIGSERIAL PK | |
| room_name | VARCHAR(50) NOT NULL | かでるサイトでの部屋名（「すずらん」等） |
| target_date | DATE NOT NULL | 対象日付 |
| time_slot | VARCHAR(20) NOT NULL | 時間帯（「evening」= 夜間17-21） |
| status | VARCHAR(10) NOT NULL | 空き状態（○/×/-/●/休館） |
| checked_at | TIMESTAMP NOT NULL | スクレイピング実行日時 |

- ユニーク制約: (room_name, target_date, time_slot)
- UPSERT（INSERT ON CONFLICT UPDATE）で更新

**新テーブル: `adjacent_room_notifications`（段階的通知の重複防止用）**

| カラム | 型 | 説明 |
|--------|-----|------|
| id | BIGSERIAL PK | |
| session_id | BIGINT NOT NULL | 通知済みセッションID |
| remaining_count | INT NOT NULL | 通知時の残り人数（4, 3, 2, 1, 0） |
| notified_at | TIMESTAMP NOT NULL | 通知日時 |

- ユニーク制約: (session_id, remaining_count)
- 残り4人で通知済み → 残り3人になったら新たに通知 → ...と段階ごとに1回ずつ

### 4.2 バックエンド設計

**隣室ペア設定（定数クラス）**

`AdjacentRoomConfig.java` — 隣室ペアのマッピングを定数で管理
- `getAdjacentVenueId(Long venueId)` → 隣接会場ID
- `getExpandedVenueId(Long venueId)` → 拡張後会場ID
- `getKaderuRoomName(Long venueId)` → かでるサイト上の部屋名
- `isKaderuRoom(Long venueId)` → かでる和室かどうか

**新エンティティ**
- `RoomAvailabilityCache.java` — room_availability_cache テーブルのエンティティ

**新リポジトリ**
- `RoomAvailabilityCacheRepository.java` — キャッシュ検索用

**新サービス**
- `AdjacentRoomService.java`
  - `getAdjacentRoomAvailability(Long venueId, LocalDate date)` → 隣室の空き状況を返す
  - `expandVenue(Long sessionId)` → 会場拡張処理（venue_id変更 + capacity更新）

**新スケジューラー**
- `AdjacentRoomNotificationScheduler.java`
  - 30分間隔で実行
  - 未来のセッションのうち、かでる和室を利用するものを対象に
  - 各セッションの最大参加者数（全試合中で最も定員に近い試合）を計算
  - 定員まで残り4人以下かつ、その残り人数段階で未通知のセッションを抽出
  - `room_availability_cache`から隣室の空き状況を取得
  - 隣室が空きの場合、SUPER_ADMIN全員 + セッション団体のADMINに通知

**新NotificationType**
- `ADJACENT_ROOM_AVAILABLE` — 隣室空き通知

**PracticeSessionDtoの拡張**
- `adjacentRoomStatus` フィールド追加（隣室名・空き状態・拡張後Venue情報）

**新APIエンドポイント**
- `POST /api/practice-sessions/{id}/expand-venue` — 会場拡張（管理者のみ）

### 4.3 フロントエンド設計

**PracticeList.jsx モーダルヘッダーの変更（行512-514付近）**
- 会場名の横に隣室空き状況バッジを表示
  - 例: `すずらん 🟢 隣室(はまなす)空き` / `すずらん 🔴 隣室(はまなす)予約済`
- かでる和室以外の会場では非表示

**会場拡張ボタン（モーダル内、管理者のみ）**
- 隣室が空きの場合に「会場を拡張」ボタンを表示
- クリックで確認ダイアログ → API呼び出し → セッション情報を再読み込み

**APIクライアント追加**
- `practices.js` に `expandVenue(sessionId)` メソッド追加

### 4.4 スクレイピングスクリプト（Node.js）

`scripts/room-checker/` に配置（既存のテストスクリプトをベースに改修）

**`sync-to-db.js`（新規）**
- かでる2・7サイトから4部屋（すずらん、はまなす、あかなら、えぞまつ）の空き状況を取得
- 対象日: 今日〜40日先（サイトで表示可能な範囲）
- 夜間スロットのみ抽出
- PostgreSQLに直接UPSERT
- 手動実行: `node sync-to-db.js`
- 定期実行: 30分間隔（デプロイ方式は後日決定）

## 5. 影響範囲

### 変更が必要な既存ファイル

**バックエンド:**
- `Notification.java` — NotificationType に `ADJACENT_ROOM_AVAILABLE` 追加
- `NotificationService.java` — `isTypeEnabled()` に新タイプ追加
- `PracticeSessionDto.java` — `adjacentRoomStatus` フィールド追加
- `PracticeSessionService.java` — セッション詳細取得時に隣室情報を付与
- `PracticeSessionController.java` — 会場拡張エンドポイント追加
- `PushNotificationPreference.java` — 隣室通知のON/OFF設定追加（任意）

**フロントエンド:**
- `PracticeList.jsx` — モーダルヘッダーに隣室状況表示・拡張ボタン追加
- `practices.js` — `expandVenue()` APIメソッド追加

**DB:**
- 新テーブル2つ（`room_availability_cache`, `adjacent_room_notifications`）
- `push_notification_preferences` テーブルに `adjacent_room` カラム追加（任意）

**スクリプト:**
- `scripts/room-checker/sync-to-db.js` 新規作成
- `scripts/room-checker/package.json` に `pg` パッケージ追加

### 既存機能への影響
- PracticeSessionDtoにフィールドが増えるが、既存フィールドは変更しないため**後方互換あり**
- Notification.NotificationType にenum値を追加するが、既存の値は変更しないため**後方互換あり**
- カレンダーのモーダル表示にUI要素が増えるが、かでる和室以外では変化なし

## 6. 設計判断の根拠

| 判断 | 理由 |
|------|------|
| 隣室ペアをDB管理でなく定数で管理 | 変更頻度が極めて低い（かでる2・7の4部屋のみ）。DBに入れるとテーブル増加の割にメリットが少ない |
| スクレイピングをNode.js別プロセスで実行 | Render.comのSpring Bootデプロイ環境にChromiumを追加するのは困難。別プロセスならバックエンド負荷もゼロ |
| 結果をDBキャッシュする方式 | バックエンドはDBを読むだけなので軽い。スクレイピングの失敗がバックエンドに影響しない |
| 定員接近の閾値を「残り4人」に固定 | ユーザーの運用実態に合わせた値。将来的にシステム設定化も可能だが、現時点では不要 |
| 重複通知防止に専用テーブルを使用 | セッション×残り人数の段階で通知済みフラグを管理。Notificationテーブルを検索するより効率的 |
