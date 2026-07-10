# docs 分割 対応表（旧見出し → 新配置）

旧 `docs/SPECIFICATION.md` / `docs/DESIGN.md` の全見出し（レベル1〜3）の新配置を定義する。
照合は `scripts/docs-migration/check-migration.mjs` が行う（`--mode=coverage`: 対応の過不足、`--mode=presence`: 移設先に見出しが存在するか）。

## ルール

- **処置**: `移設`（新配置ファイルへ移動。レベル4以下の子見出しは同じ配置を継承）／`分配`（子見出し単位で複数ファイルへ分ける。レベル4の子は新配置グロブのいずれかに存在すればよい）／`廃止`（見出し自体は消える。子見出しは各自の行で扱う）／`ハブ残置`（ハブ化後のファイルに概要として残る。presence 照合は対象外）
- **新見出し**: 空欄なら「旧見出しから章番号を除いた文字列」。明示指定は統合先の共通見出し（例: API 系は統合先ドメインファイルの `API` 節へ合流）
- 新しいドメインファイルの標準構成: 冒頭メタブロック（責務・関連画面・主要実装パス）→ `機能仕様` → `画面` → `フロー` → `API`

## SPECIFICATION.md

| 旧ファイル | 旧見出し | 処置 | 新配置 | 新見出し |
|---|---|---|---|---|
| SPECIFICATION | かるた対戦記録管理システム — 仕様書 | ハブ残置 | docs/SPECIFICATION.md | |
| SPECIFICATION | 1. システム概要 | ハブ残置 | docs/SPECIFICATION.md | |
| SPECIFICATION | 1.1 目的 | ハブ残置 | docs/SPECIFICATION.md | |
| SPECIFICATION | 1.2 対象ユーザー | ハブ残置 | docs/SPECIFICATION.md | |
| SPECIFICATION | 1.3 技術スタック | 移設 | docs/design/architecture.md | 技術スタック |
| SPECIFICATION | 1.4 主要ライブラリ | 移設 | docs/design/architecture.md | 主要ライブラリ |
| SPECIFICATION | 2. ユーザー管理と認証 | 移設 | docs/spec/players-auth.md | 機能仕様 |
| SPECIFICATION | 2.1 ロール定義 | 移設 | docs/spec/players-auth.md | ロール定義 |
| SPECIFICATION | 2.1.1 選手登録方式 | 移設 | docs/spec/players-auth.md | 選手登録方式 |
| SPECIFICATION | 2.2 認証方式（プロトタイプ仕様） | 移設 | docs/spec/players-auth.md | 認証方式（プロトタイプ仕様） |
| SPECIFICATION | 2.3 選手プロパティ | 移設 | docs/spec/players-auth.md | 選手プロパティ |
| SPECIFICATION | 3. 機能仕様 | 廃止 | | |
| SPECIFICATION | 3.0 団体管理 | 移設 | docs/spec/players-auth.md | 団体管理 |
| SPECIFICATION | 3.1 ホーム画面（ダッシュボード） | 移設 | docs/spec/practice-sessions.md | ホーム画面（ダッシュボード） |
| SPECIFICATION | 3.2 練習日管理 | 移設 | docs/spec/practice-sessions.md | 練習日管理 |
| SPECIFICATION | 3.3 対戦組み合わせ | 移設 | docs/spec/matching.md | 機能仕様 |
| SPECIFICATION | 3.4 対戦結果管理 | 移設 | docs/spec/matches.md | 対戦結果管理 |
| SPECIFICATION | 3.5 統計機能 | 移設 | docs/spec/stats.md | 機能仕様 |
| SPECIFICATION | 3.6 会場管理 | 移設 | docs/spec/venues.md | 会場管理 |
| SPECIFICATION | 3.7 練習参加抽選システム | 移設 | docs/spec/lottery.md | 機能仕様 |
| SPECIFICATION | 3.9 メンター機能 | 移設 | docs/spec/mentor.md | 機能仕様 |
| SPECIFICATION | 3.8 選手プロフィール履歴 | 移設 | docs/spec/players-auth.md | 選手プロフィール履歴 |
| SPECIFICATION | 3.20 取り札記録（試合結果入力の詳細記録・任意） | 移設 | docs/spec/matches.md | 取り札記録（試合結果入力の詳細記録・任意） |
| SPECIFICATION | 4. 外部連携 | 廃止 | | |
| SPECIFICATION | 4.1 伝助（Densuke）連携 | 移設 | docs/spec/densuke.md | 機能仕様 |
| SPECIFICATION | 4.2 カレンダー購読（iCalフィード） | 移設 | docs/spec/calendar.md | 機能仕様 |
| SPECIFICATION | 4.3 LINE通知連携 | 移設 | docs/spec/notifications.md | LINE通知連携 |
| SPECIFICATION | 4.4 かでる2・7 予約同期 | 移設 | docs/spec/venue-reservations.md | かでる2・7 予約同期 |
| SPECIFICATION | 4.5 東区民センター予約同期 | 移設 | docs/spec/venue-reservations.md | 東区民センター予約同期 |
| SPECIFICATION | 4.6 隣室空き確認通知 | 移設 | docs/spec/venues.md | 隣室空き確認通知 |
| SPECIFICATION | 4.7 会場予約プロキシ | 移設 | docs/spec/venues.md | 会場予約プロキシ |
| SPECIFICATION | 5. 画面一覧とルーティング | 廃止 | | |
| SPECIFICATION | 5.1 公開ページ | 移設 | docs/SCREEN_LIST.md | 公開ページ |
| SPECIFICATION | 5.2 認証必須ページ | 移設 | docs/SCREEN_LIST.md | 認証必須ページ |
| SPECIFICATION | 5.3 ナビゲーション構造 | 移設 | docs/SCREEN_LIST.md | ナビゲーション構造 |
| SPECIFICATION | 6. データモデル | 廃止 | | |
| SPECIFICATION | 6.1 ER図（テキスト表記） | 移設 | docs/design/db.md | ER図 |
| SPECIFICATION | 6.2 テーブル一覧 | 移設 | docs/design/db.md | テーブル定義 |
| SPECIFICATION | 7. API仕様 | 廃止 | | |
| SPECIFICATION | 7.1 共通仕様 | 移設 | docs/design/architecture.md | API共通仕様 |
| SPECIFICATION | 7.2 選手管理 (`/api/players`) | 移設 | docs/spec/players-auth.md | API |
| SPECIFICATION | 7.2.1 招待トークン (`/api/invite-tokens`) | 移設 | docs/spec/players-auth.md | API |
| SPECIFICATION | 7.3 対戦結果 (`/api/matches`) | 移設 | docs/spec/matches.md | API |
| SPECIFICATION | 7.3.1 試合動画 (`/api/match-videos`) | 移設 | docs/spec/match-videos.md | API |
| SPECIFICATION | 7.4 抜け番活動 (`/api/bye-activities`) | 移設 | docs/spec/matches.md | API |
| SPECIFICATION | 7.5 組み合わせ (`/api/match-pairings`) | 移設 | docs/spec/matching.md | API |
| SPECIFICATION | 7.6 練習日 (`/api/practice-sessions`) | 移設 | docs/spec/practice-sessions.md | API |
| SPECIFICATION | 7.7 伝助連携 (`/api/practice-sessions`) | 移設 | docs/spec/densuke.md | API |
| SPECIFICATION | 7.7.1 伝助削除候補 (`/api/densuke-deletion-candidates`) | 移設 | docs/spec/densuke.md | API |
| SPECIFICATION | 7.8 選手プロフィール (`/api/player-profiles`) | 移設 | docs/spec/players-auth.md | API |
| SPECIFICATION | 7.9 会場管理 (`/api/venues`) | 移設 | docs/spec/venues.md | API |
| SPECIFICATION | 7.10 カレンダー購読 (iCalフィード) | 移設 | docs/spec/calendar.md | API |
| SPECIFICATION | 7.11 抽選 (`/api/lottery`) | 移設 | docs/spec/lottery-api.md | API |
| SPECIFICATION | 7.12 通知 (`/api/notifications`) | 移設 | docs/spec/notifications.md | API |
| SPECIFICATION | 7.13 Push購読 (`/api/push-subscriptions`) | 移設 | docs/spec/notifications.md | API |
| SPECIFICATION | 7.14 LINE通知 (`/api/line`) | 移設 | docs/spec/notifications.md | API |
| SPECIFICATION | 7.15 LINE管理 (`/api/admin/line`) | 移設 | docs/spec/notifications.md | API |
| SPECIFICATION | 7.17 メンター関係 (`/api/mentor-relationships`) | 移設 | docs/spec/mentor.md | API |
| SPECIFICATION | 7.18 メンターコメント (`/api/matches/{matchId}/comments`) | 移設 | docs/spec/mentor.md | API |
| SPECIFICATION | 7.16 ヘルスチェック | 移設 | docs/design/architecture.md | ヘルスチェック |
| SPECIFICATION | 8. デプロイ構成 | 移設 | docs/design/architecture.md | デプロイ構成 |
| SPECIFICATION | 8.1 環境プロファイル | 移設 | docs/design/architecture.md | 環境プロファイル |
| SPECIFICATION | 8.2 Docker構成 | 移設 | docs/design/architecture.md | Docker構成 |
| SPECIFICATION | 8.3 Render.com構成 | 移設 | docs/design/architecture.md | Render.com構成 |
| SPECIFICATION | 9. 未実装・今後の予定 | 移設 | docs/spec/backlog.md | その他の未実装・予定 |

## DESIGN.md

| 旧ファイル | 旧見出し | 処置 | 新配置 | 新見出し |
|---|---|---|---|---|
| DESIGN | かるたトラッカー システム設計書 | ハブ残置 | docs/DESIGN.md | |
| DESIGN | 目次 | 廃止 | | |
| DESIGN | 1. システム概要 | ハブ残置 | docs/DESIGN.md | |
| DESIGN | 1.1 目的 | ハブ残置 | docs/DESIGN.md | |
| DESIGN | 1.2 主要機能 | ハブ残置 | docs/DESIGN.md | |
| DESIGN | 1.3 技術スタック | 移設 | docs/design/architecture.md | 技術スタック |
| DESIGN | 2. アーキテクチャ | 移設 | docs/design/architecture.md | アーキテクチャ |
| DESIGN | 2.1 システム構成 | 移設 | docs/design/architecture.md | システム構成 |
| DESIGN | 2.2 レイヤー構成 | 移設 | docs/design/architecture.md | レイヤー構成 |
| DESIGN | 2.3 認証・認可 | 移設 | docs/design/architecture.md | 認証・認可 |
| DESIGN | 3. データベース設計 | 移設 | docs/design/db.md | |
| DESIGN | 3.1 ER図 | 移設 | docs/design/db.md | ER図 |
| DESIGN | 3.2 テーブル定義 | 移設 | docs/design/db.md | テーブル定義 |
| DESIGN | 4. API設計 | 廃止 | | |
| DESIGN | 4.1 共通仕様 | 移設 | docs/design/architecture.md | API共通仕様 |
| DESIGN | 4.1.1 団体API (`/api/organizations`) | 移設 | docs/spec/players-auth.md | API |
| DESIGN | 4.2 選手API | 移設 | docs/spec/players-auth.md | API |
| DESIGN | 4.2.1 招待トークンAPI (`/api/invite-tokens`) | 移設 | docs/spec/players-auth.md | API |
| DESIGN | 4.3 試合記録API | 移設 | docs/spec/matches.md | API |
| DESIGN | 4.3.1 試合動画API (`/api/match-videos`) | 移設 | docs/spec/match-videos.md | API |
| DESIGN | 4.4 抜け番活動API | 移設 | docs/spec/matches.md | API |
| DESIGN | 4.5 練習日API | 移設 | docs/spec/practice-sessions.md | API |
| DESIGN | 4.6 練習参加登録API | 移設 | docs/spec/practice-sessions.md | API |
| DESIGN | 4.7 対戦組み合わせAPI | 移設 | docs/spec/matching.md | API |
| DESIGN | 4.8 会場API | 移設 | docs/spec/venues.md | API |
| DESIGN | 4.9 抽選API | 移設 | docs/spec/lottery-api.md | API |
| DESIGN | 4.10 通知API | 移設 | docs/spec/notifications.md | API |
| DESIGN | 4.10 カレンダー購読 API（iCalフィード） | 移設 | docs/spec/calendar.md | API |
| DESIGN | 4.11 Web Push API | 移設 | docs/spec/notifications.md | API |
| DESIGN | 4.13 LINE通知API | 移設 | docs/spec/notifications.md | API |
| DESIGN | 4.14 LINE管理者API | 移設 | docs/spec/notifications.md | API |
| DESIGN | 4.15 システム設定API (`/api/system-settings`) | 移設 | docs/design/architecture.md | システム設定API |
| DESIGN | 4.16 メンター関係API (`/api/mentor-relationships`) | 移設 | docs/spec/mentor.md | API |
| DESIGN | 4.17 メンターコメントAPI (`/api/matches/{matchId}/comments`) | 移設 | docs/spec/mentor.md | API |
| DESIGN | 5. 画面設計 | 廃止 | | |
| DESIGN | 5.1 画面一覧 | 移設 | docs/SCREEN_LIST.md | |
| DESIGN | 5.2 画面遷移と導線 | 移設 | docs/SCREEN_LIST.md | 画面遷移と導線 |
| DESIGN | 5.3 主要画面設計 | 分配 | docs/spec/*.md,docs/SCREEN_LIST.md | |
| DESIGN | 6. 権限設計 | 移設 | docs/design/architecture.md | 権限設計 |
| DESIGN | 6.1 ロール定義 | 移設 | docs/design/architecture.md | 権限設計 |
| DESIGN | 6.2 権限マトリックス | 移設 | docs/design/architecture.md | 権限マトリックス |
| DESIGN | 6.3 実装方法 | 移設 | docs/design/architecture.md | 権限の実装方法 |
| DESIGN | 7. 主要機能フロー | 廃止 | | |
| DESIGN | 7.1 練習参加登録フロー | 移設 | docs/spec/practice-sessions.md | 練習参加登録フロー |
| DESIGN | 7.2 自動マッチングフロー | 移設 | docs/spec/matching.md | 自動マッチングフロー |
| DESIGN | 7.3 試合記録登録フロー（簡易登録） | 移設 | docs/spec/matches.md | 試合記録登録フロー（簡易登録） |
| DESIGN | 7.4 抽選フロー | 移設 | docs/spec/lottery.md | 抽選フロー |
| DESIGN | 7.5 当日キャンセル補充フロー | 移設 | docs/spec/lottery.md | 当日キャンセル補充フロー |
| DESIGN | 7.6 メンター指名・コメントフロー | 移設 | docs/spec/mentor.md | メンター指名・コメントフロー |
| DESIGN | 7.6.1 試合動画フロー | 移設 | docs/spec/match-videos.md | 試合動画フロー |
| DESIGN | 7.7 隣室予約→会場拡張フロー | 移設 | docs/spec/venues.md | 隣室予約→会場拡張フロー |
| DESIGN | 7.8 かでる予約 → 練習日自動登録フロー | 移設 | docs/spec/venue-reservations.md | かでる予約 → 練習日自動登録フロー |
| DESIGN | 7.9 東区民センター予約 → 練習日自動登録フロー | 移設 | docs/spec/venue-reservations.md | 東区民センター予約 → 練習日自動登録フロー |
| DESIGN | 7.10 取り札記録フロー | 移設 | docs/spec/matches.md | 取り札記録フロー |
| DESIGN | 8. 未実装機能・TODO | 廃止 | | |
| DESIGN | 8.1 優先度: 高 | 移設 | docs/spec/backlog.md | 優先度: 高 |
| DESIGN | 8.2 優先度: 中 | 移設 | docs/spec/backlog.md | 優先度: 中 |
| DESIGN | 8.3 優先度: 低 | 移設 | docs/spec/backlog.md | 優先度: 低 |
| DESIGN | 9. 補足事項 | 廃止 | | |
| DESIGN | 9.1 設計上の重要ポイント | 移設 | docs/design/architecture.md | 設計上の重要ポイント |
| DESIGN | 9.2 開発環境セットアップ | 移設 | docs/design/architecture.md | 開発環境セットアップ |
| DESIGN | 9.3 リリースノート | 移設 | docs/design/release-notes.md | |
| DESIGN | 付録 | 廃止 | | |
| DESIGN | A. 用語集 | 移設 | docs/spec/glossary.md | |
| DESIGN | B. データベース初期データ | 移設 | docs/design/db.md | データベース初期データ |
