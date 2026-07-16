# 全体LINE配信 本番セットアップ手順（北海道大学かるた会・org 2）

このPRのコードがマージ・デプロイされた**後**に運用者が行う手順。bot群のデータ転用（`channel_type='GROUP'`）は
旧コードのデプロイ中に実行すると enum deserialize 障害を起こすため、**必ずデプロイ完了後**に実行する。

## 実行順序

1. **PR マージ → Render 自動デプロイ完了を確認**（新コードが serving＝ヘルスチェック healthy）。
   - これより前に手順2を実行しない（旧コードは `ChannelType.GROUP` を知らず `line_channels` の `findAll()` が壊れる）。
2. **bot群のデータ転用（seed）**: `database/seed_hokudai_broadcast_bots.sql` を本番に適用する。
   - `java -Djava.net.preferIPv4Stack=true -Dstdout.encoding=UTF-8 -cp "<pgjdbc.jar>;." RunMig.java database/seed_hokudai_broadcast_bots.sql`
   - org 2 の配信グループ（`enabled=FALSE` で作成）＋ 未使用 PLAYER/AVAILABLE チャネル10体を GROUP に転用・割当。
3. **各botの LINE チャネル設定**（人手・LINE Developers / 管理画面）:
   - 「グループトーク・複数人トークへの参加」を許可。
   - Webhook を有効化。既存の管理API `POST /api/admin/line/broadcast` ではなく、
     `POST /api/admin/line/channels/migrate-webhook-urls`（既存機能）で全チャネルの Webhook URL を一括設定できる。
4. **全体グループへ10体を人手で招待**（bot自動参加はLINE仕様上不可）。
   - 招待されると `join` Webhook が発火し、各チャネルの `line_group_id` が自動捕捉される。
5. **管理画面 `/admin/line/broadcast` で確認**:
   - 割当bot10体すべてが「グループID捕捉済み（未参加バッジが消える）」になっていること。
   - 想定受信数（必要なら設定値を入力。未設定なら送信時に実グループ人数APIで解決）。
6. **配信グループを有効化**（管理画面の有効/無効トグル → `enabled=TRUE`）。
   - 以降、当日セッションの1試合目30分前（無ければ8:00）に自動配信される。

## わすらもち会（org 1）を将来追加する場合

コード改修不要。管理画面 `/admin/line/broadcast` で org 1 の配信グループを作成し、未使用チャネルを割り当て、
上記3〜6と同じ人手手順を行うだけ（per-org 設計）。
