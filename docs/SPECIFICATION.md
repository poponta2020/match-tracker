# かるた対戦記録管理システム — 仕様書（ハブ）

> このファイルは索引（ハブ）。機能仕様の本文は `docs/spec/` のドメインファイルが正典。
> 更新規律（どの事実をどのファイルに書くか）は `.claude/project-profile.md` の `## docs` を参照。

## システム概要

### 目的

わすらもち会と北海道大学かるた会の2団体が利用するかるた練習運営・対戦記録管理Webアプリケーション。
練習日の出欠管理、対戦組み合わせの生成、試合結果の記録・統計分析を行い、練習の質と運営効率を向上させる。

各団体は異なる締め切り・参加管理ルールを持ち、ユーザーは自分が参加する練習会の情報のみ閲覧できる。

### 対象ユーザー

かるた会のメンバー全員が利用する。ロール（SUPER_ADMIN / ADMIN / PLAYER）に応じて利用可能な機能が異なる。
ロール定義・認証方式は [spec/players-auth.md](spec/players-auth.md)、権限マトリックスは [design/architecture.md](design/architecture.md) を参照。

## ドメイン別仕様（正典）

改修時は該当ドメインのファイルだけを読めばよい。各ファイルは「機能仕様 → 画面 → フロー → API」の構成で、冒頭のメタブロックに関連画面と主要実装パスを持つ。

| ドメイン | 責務 | ファイル |
|---|---|---|
| 選手・認証・団体 | 選手アカウント・ロール・認証・団体管理・プロフィール履歴・招待トークン | [spec/players-auth.md](spec/players-auth.md) |
| 練習日・ホーム | 練習日管理・参加管理・ホーム画面・参加キャンセル・当日キャンセル補充 | [spec/practice-sessions.md](spec/practice-sessions.md) |
| 出欠登録 | 出欠登録画面・出欠モーダル・カレンダーグリッド表示 | [spec/practice-attendance.md](spec/practice-attendance.md) |
| 対戦組み合わせ | 組み合わせ自動生成・組み合わせ管理・札ルール | [spec/matching.md](spec/matching.md) |
| 対戦結果 | 試合記録の登録・閲覧・取り札記録・抜け番活動 | [spec/matches.md](spec/matches.md) |
| 試合動画 | 試合動画の登録・動画ライブラリ | [spec/match-videos.md](spec/match-videos.md) |
| 統計 | 選手別・級別統計 | [spec/stats.md](spec/stats.md) |
| 抽選 | 参加抽選・締切・キャンセル待ち・繰り上げ | [spec/lottery.md](spec/lottery.md)（API詳細: [spec/lottery-api.md](spec/lottery-api.md)） |
| メンター | メンター関係の管理・コメントフィードバック | [spec/mentor.md](spec/mentor.md) |
| 会場 | 会場マスタ・隣室空き確認通知・会場予約プロキシ | [spec/venues.md](spec/venues.md) |
| 予約同期 | かでる2・7／東区民センターの予約→練習日自動登録 | [spec/venue-reservations.md](spec/venue-reservations.md) |
| 伝助連携 | 出欠スクレイピング・双方向同期・ページ自動作成・削除検知 | [spec/densuke.md](spec/densuke.md) |
| 通知・LINE・Push | アプリ内通知・Web Push・LINE通知連携・LINE管理 | [spec/notifications.md](spec/notifications.md) |
| カレンダー購読 | iCal フィードによるカレンダー購読 | [spec/calendar.md](spec/calendar.md) |

## 横断ドキュメント

| 内容 | ファイル |
|---|---|
| 画面一覧・ルーティング・ナビゲーション（画面のSSOT） | [SCREEN_LIST.md](SCREEN_LIST.md) |
| アーキテクチャ・権限設計・API共通仕様・デプロイ・開発環境 | [design/architecture.md](design/architecture.md) |
| データベース設計（テーブル定義のSSOT・本番introspect照合済み） | [design/db.md](design/db.md) |
| 未実装・今後の予定（バックログ） | [spec/backlog.md](spec/backlog.md) |
| 用語集 | [spec/glossary.md](spec/glossary.md) |
| 過去の改修履歴（機能別） | [features/INDEX.md](features/INDEX.md) |

## 書き込み規律（要約）

- 1つの事実は1ファイルにのみ書く（重複掲載の禁止）。更新は該当セクションの in-place 書き換え
- 見出しに連番を付けない。実装参照はファイルパス粒度（行番号を書かない）
- 本文への変更履歴の追記禁止（履歴は git と `docs/features/<slug>/` が持つ）
- 詳細なレジストリと更新手順: `.claude/project-profile.md` の `## docs`
