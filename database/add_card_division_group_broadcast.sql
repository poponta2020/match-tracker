-- 札分けの全体LINE一斉配信（card-division-group-broadcast）
--   1. line_channels に broadcast_group_id / line_group_id を追加（GROUP 種別 bot 用）。
--      NOTE: line_channels.channel_type には本番で CHECK 制約が存在しない
--            （2026-07-16 introspect: 制約は status のみ）。よって ChannelType に GROUP を
--            追加しても CHECK の張り直しは不要（テンプレ古さで有効値を潰す事故の対象外）。
--   2. line_broadcast_group（配信グループ設定・per-org）。
--   3. line_broadcast_send（全体配信の送信ログ兼 dedupe）。
--      部分ユニークインデックス idx_lbs_dedupe で (グループ, セッション) の一度きりを担保する。
--      Hibernate(ddl-auto=update) は部分インデックスを自動生成しないため、DataInitializer でも検証・自動作成する。
--
-- 本番 Render PostgreSQL への適用必須（entity 変更と同一 PR）。

-- 1. line_channels 拡張
ALTER TABLE line_channels ADD COLUMN IF NOT EXISTS broadcast_group_id BIGINT;
ALTER TABLE line_channels ADD COLUMN IF NOT EXISTS line_group_id VARCHAR(50);
CREATE INDEX IF NOT EXISTS idx_line_channel_broadcast_group ON line_channels (broadcast_group_id);

-- 2. 配信グループ設定
CREATE TABLE IF NOT EXISTS line_broadcast_group (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    expected_recipient_count INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);
-- 1団体1配信グループを DB でも担保（並行作成・手動投入での重複＝同一セッション多重配信を防ぐ）
CREATE UNIQUE INDEX IF NOT EXISTS idx_lbg_org_unique ON line_broadcast_group (organization_id);

-- 3. 全体配信ログ兼 dedupe
CREATE TABLE IF NOT EXISTS line_broadcast_send (
    id BIGSERIAL PRIMARY KEY,
    broadcast_group_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    line_channel_id BIGINT,
    recipient_count INTEGER,
    status VARCHAR(20) NOT NULL CHECK (status IN ('RESERVED', 'SUCCESS', 'FAILED', 'SKIPPED')),
    error_message TEXT,
    sent_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_lbs_group_session ON line_broadcast_send (broadcast_group_id, session_id);
CREATE INDEX IF NOT EXISTS idx_lbs_group_sent ON line_broadcast_send (broadcast_group_id, sent_at);

-- 原子的な一度きり送信を担保する部分ユニークインデックス（RESERVED/SUCCESS のみ対象）。
-- FAILED/SKIPPED は再試行を許すため対象外。
CREATE UNIQUE INDEX IF NOT EXISTS idx_lbs_dedupe
    ON line_broadcast_send (broadcast_group_id, session_id)
    WHERE status IN ('SUCCESS', 'RESERVED');
