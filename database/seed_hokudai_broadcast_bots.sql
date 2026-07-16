-- 北海道大学かるた会（org_id=2）の全体LINE配信グループ初期セットアップ（データseed）。
--
-- ★★ 実行タイミング（最重要）★★
-- **このseedは新コード（ChannelType.GROUP を持つ）が本番にデプロイされ、ヘルスチェックが
--   healthy になった後に実行すること。** デプロイ前に channel_type='GROUP' を書き込むと、
--   旧コードの ChannelType enum（PLAYER/ADMIN のみ）が deserialize に失敗し、毎時の
--   LineMessageCountSyncScheduler.findAll() 等が壊れる（本番障害）。
--   → PR マージ → Render デプロイ完了確認 → 本seed → migrate-webhook-urls → bot招待 の順。
--
-- 冪等: グループは無ければ作成（enabled=FALSE で作成＝招待完了までスケジューラの SKIPPED ノイズを避ける）。
--        bot は「不足分だけ」PLAYER/AVAILABLE を GROUP に転用（再実行しても合計10体を超えない）。
--        ASSIGNED/LINKED の稼働中チャネルは絶対に転用しない（選手の通知を壊さないため status='AVAILABLE' 限定）。
--
-- 適用: java -Djava.net.preferIPv4Stack=true -Dstdout.encoding=UTF-8 -cp "<pgjdbc.jar>;." RunMig.java database/seed_hokudai_broadcast_bots.sql

-- 1. 配信グループ（enabled=FALSE。招待・グループID捕捉が済んだら管理画面で有効化する）
INSERT INTO line_broadcast_group (organization_id, name, enabled, expected_recipient_count)
SELECT 2, '北海道大学かるた会 全体グループ', FALSE, NULL
WHERE NOT EXISTS (SELECT 1 FROM line_broadcast_group WHERE organization_id = 2);

-- 2. 未使用（PLAYER/AVAILABLE）チャネルを不足分だけ GROUP に転用して割り当てる
UPDATE line_channels
SET channel_type = 'GROUP',
    broadcast_group_id = (SELECT id FROM line_broadcast_group WHERE organization_id = 2)
WHERE id IN (
    SELECT id FROM line_channels
    WHERE channel_type = 'PLAYER' AND status = 'AVAILABLE'
    ORDER BY id
    LIMIT GREATEST(0, 10 - (
        SELECT count(*) FROM line_channels
        WHERE broadcast_group_id = (SELECT id FROM line_broadcast_group WHERE organization_id = 2)
    ))
);
