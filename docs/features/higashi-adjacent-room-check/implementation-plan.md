---
status: completed
---
# 東区民センター隣室空き確認 実装手順書

## 実装タスク

### タスク1: かっこう venue のDBマイグレーション
- [x] 完了（venue_id = **12** で採番）
- **概要:** `venues` テーブルに「かっこう」を新規INSERTし、採番された ID を以降のタスクで使用する
- **変更対象ファイル:**
  - `database/insert_kakkou_venue.sql` — 新規。
    ```sql
    INSERT INTO venues (name, capacity, default_match_count, created_at, updated_at)
    VALUES ('かっこう', 4, 2, NOW(), NOW())
    ON CONFLICT (name) DO NOTHING;
    ```
- **実行**: Render PostgreSQL に接続してマイグレーション実行後、採番された ID を確認
- **依存タスク:** なし
- **対応Issue:** #502

### タスク2: スクレイピングページのDOM実地調査
- [ ] 完了
- **概要:** `SsfSvrRoomAvailabilityMonth.aspx` の月表示ページにアクセスし、かっこうの空き状況を取得するためのDOM構造・URL パラメータ・施設/部屋コードを実地調査する
- **変更対象ファイル:**
  - `scripts/room-checker/explore-higashi-availability.js` — 新規（一時的な探査用スクリプト、最終的にはコミット不要だが調査結果をタスク3で利用）
  - `docs/operations/higashi-availability-scraping-report.md` — 新規。調査結果（URL構造、セレクタ、空き記号の意味等）をまとめる
- **確認事項**:
  - 月表示ページへのURL/クエリ（施設=103、部屋=041 指定方法）
  - 各日付 × 時間帯の空き状況セル DOM セレクタ
  - 18-21時スロットの特定方法
  - 空き記号（○/×/- 等）の確認
- **依存タスク:** なし（タスク1と並行可能）
- **対応Issue:** #503

### タスク3: スクレイピングスクリプト作成
- [ ] 完了
- **概要:** 東区民センター かっこうの空き状況を月単位で取得し、`room_availability_cache` に UPSERT するNode.jsスクリプト
- **変更対象ファイル:**
  - `scripts/room-checker/sync-higashi-availability-to-db.js` — 新規。
    - CLI: `--months <N>`（デフォルト 2）
    - 月表示ページから当月〜翌月の空き状況を取得
    - 18-21時スロットのみ抽出
    - `room_name='かっこう'`, `time_slot='evening'` で UPSERT
    - ログイン不要のためCredential処理なし
- **依存タスク:** タスク2
- **対応Issue:** #504

### タスク4: GitHub Actions ワークフロー追加
- [ ] 完了
- **概要:** タスク3のスクリプトを30分間隔で実行するワークフローを追加
- **変更対象ファイル:**
  - `.github/workflows/check-higashi-availability.yml` — 新規。`check-kaderu-availability.yml` を複製して以下を変更:
    - `name`: `Check Higashi Community Center Room Availability`
    - `concurrency.group`: `higashi-availability-check`
    - 実行コマンド: `node sync-higashi-availability-to-db.js --months 2`
    - `DATABASE_URL`: `${{ secrets.KADERU_DATABASE_URL }}` 流用
- **依存タスク:** タスク3
- **対応Issue:** #505

### タスク5: AdjacentRoomConfig 拡張
- [x] 完了
- **概要:** 隣室ペアマップに「東🌸(6) ↔ かっこう(K_ID)」を追加し、時間帯ラベルを会場別に返すメソッドを追加
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/config/AdjacentRoomConfig.java`
    - 隣室ペアマップに東🌸↔かっこうを追加（K_ID=タスク1で採番）
    - 拡張後 venue_id マップに `6 → 10`, `K_ID → 10` を追加
    - サイト部屋名マップに `6 → "さくら"`, `K_ID → "かっこう"` を追加（既存 `getKaderuRoomName` を汎用化）
    - 新規メソッド `getNightTimeLabel(Long venueId)` 追加:
      - かでる和室: `"17-21"`
      - 東🌸: `"18-21"`
    - 新規メソッド `isAdjacentCheckTarget(Long venueId)` 追加（`isKaderuRoom` + 東🌸）
- **依存タスク:** タスク1
- **対応Issue:** #506

### タスク6: AdjacentRoomNotificationScheduler 拡張
- [x] 完了
- **概要:** 東🌸のセッションも隣室チェック対象に含め、通知メッセージの時間帯を会場別に切り替え
- **変更対象ファイル:**
  - `karuta-tracker/src/main/java/com/karuta/matchtracker/scheduler/AdjacentRoomNotificationScheduler.java`
    - セッションフィルタ: `AdjacentRoomConfig.isKaderuRoom` → `AdjacentRoomConfig.isAdjacentCheckTarget` に変更
    - 通知メッセージ内ハードコードの `夜間(17-21)` を `AdjacentRoomConfig.getNightTimeLabel(venueId)` から動的取得に変更
    - 会場名取得も `getKaderuRoomName` → より汎用的なメソッド（タスク5で決定）
    - ログ出力メッセージも `kaderu` 固定表現から汎用化（`adjacent check target sessions` 等）
- **依存タスク:** タスク5
- **対応Issue:** #507

### タスク7: フロントエンド — 隣室状況表示・拡張ボタン対象拡大
- [ ] 完了
- **概要:** カレンダーポップアップの隣室表示・会場拡張ボタンの表示条件に venue_id=6（東🌸）を追加
- **変更対象ファイル:**
  - `karuta-tracker-ui/src/pages/practice/PracticeList.jsx`
    - 隣室状況表示の表示条件判定に venue_id=6 を追加
    - 会場拡張ボタンの表示条件判定に venue_id=6 を追加
    - 表示テキストは `adjacentRoomStatus` から来るためコード上のハードコード変更は不要（バックエンドで動的に生成）
- **依存タスク:** タスク5, タスク6（`adjacentRoomStatus` に venue_id=6 のデータが入ってくる状態）
- **対応Issue:** #508

### タスク8: ユニットテスト追加
- [ ] 完了
- **概要:** 拡張後の Config・Scheduler の動作確認テスト
- **変更対象ファイル:**
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/config/AdjacentRoomConfigTest.java` — 既存テストに東🌸↔かっこうのケース追加、`getNightTimeLabel` / `isAdjacentCheckTarget` のテスト追加
  - `karuta-tracker/src/test/java/com/karuta/matchtracker/scheduler/AdjacentRoomNotificationSchedulerTest.java` — 既存テストに venue_id=6 のセッションに対する通知生成テストを追加（時間帯ラベルが `18-21` になることの確認含む）
- **依存タスク:** タスク5, タスク6
- **対応Issue:** #509

### タスク9: ドキュメント更新
- [ ] 完了
- **概要:** プロジェクト全体の仕様書・設計書・画面一覧に本機能を反映
- **変更対象ファイル:**
  - `docs/SPECIFICATION.md` — 隣室空き確認機能の対象会場に東🌸を追加
  - `docs/DESIGN.md` — AdjacentRoomConfig の拡張、スクレイピングスクリプトの追加を反映
  - `docs/SCREEN_LIST.md` — 会場拡張ボタンの表示条件に東🌸を追記（必要に応じて）
- **依存タスク:** タスク1〜8（実装完了後にまとめて更新）
- **対応Issue:** #510

## 実装順序

1. **タスク1**: かっこう venue DBマイグレーション（依存なし。採番IDを確保）
2. **タスク2**: スクレイピングページ実地調査（依存なし。タスク1と並行可能）
3. **タスク3**: スクレイピングスクリプト作成（タスク2に依存）
4. **タスク4**: GitHub Actions ワークフロー追加（タスク3に依存）
5. **タスク5**: AdjacentRoomConfig 拡張（タスク1に依存）
6. **タスク6**: Scheduler 拡張（タスク5に依存）
7. **タスク7**: フロントエンド表示条件拡大（タスク5, 6に依存）
8. **タスク8**: ユニットテスト追加（タスク5, 6に依存）※タスク7と並行可能
9. **タスク9**: ドキュメント更新（全タスク完了後）

## 実装上の注意点

- **タスク1のID反映**: venueのID採番後、タスク5で定数として `AdjacentRoomConfig` に埋め込む必要がある。既存の Kaderu 和室IDが定数として埋め込まれている方針を踏襲
- **タスク2の実地調査**: URL パラメータや DOM 構造が既存 `scrape-higashi-history.js` と異なる可能性が高いため、先に調査してから実装
- **タスク6の後方互換**: かでる和室のセッションに対する既存通知メッセージ（`夜間(17-21)`）が壊れないこと。Config メソッドがかでる和室でも正しい値を返すことをユニットテストで担保
