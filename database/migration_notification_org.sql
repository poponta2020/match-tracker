-- ============================================
-- 通知設定の団体対応 マイグレーション
-- 関連Issue: #80, #97
-- ============================================

-- ============================================
-- 1. push_notification_preferences に organization_id 追加
-- ============================================
ALTER TABLE push_notification_preferences
  ADD COLUMN IF NOT EXISTS organization_id BIGINT;

-- 既存データをわすらもち会に紐づけ
UPDATE push_notification_preferences
SET organization_id = (SELECT id FROM organizations WHERE code = 'wasura')
WHERE organization_id IS NULL;

-- NOT NULL制約の追加
ALTER TABLE push_notification_preferences
  ALTER COLUMN organization_id SET NOT NULL;

-- 既存のユニーク制約を削除し、新しい複合ユニーク制約を追加
-- player_id 単体のユニーク制約を削除
DO $$
BEGIN
  -- Hibernateが自動生成した制約名を探して削除
  EXECUTE (
    SELECT 'ALTER TABLE push_notification_preferences DROP CONSTRAINT ' || conname
    FROM pg_constraint
    WHERE conrelid = 'push_notification_preferences'::regclass
      AND contype = 'u'
      AND array_length(conkey, 1) = 1
    LIMIT 1
  );
EXCEPTION WHEN OTHERS THEN
  -- 制約が存在しない場合は無視
  NULL;
END $$;

ALTER TABLE push_notification_preferences
  ADD CONSTRAINT uk_pnp_player_org UNIQUE (player_id, organization_id);

-- ============================================
-- 2. line_notification_preferences に organization_id 追加
-- ============================================
ALTER TABLE line_notification_preferences
  ADD COLUMN IF NOT EXISTS organization_id BIGINT;

-- 既存データをわすらもち会に紐づけ
UPDATE line_notification_preferences
SET organization_id = (SELECT id FROM organizations WHERE code = 'wasura')
WHERE organization_id IS NULL;

-- NOT NULL制約の追加
ALTER TABLE line_notification_preferences
  ALTER COLUMN organization_id SET NOT NULL;

-- 既存のユニーク制約を削除し、新しい複合ユニーク制約を追加
DO $$
BEGIN
  EXECUTE (
    SELECT 'ALTER TABLE line_notification_preferences DROP CONSTRAINT ' || conname
    FROM pg_constraint
    WHERE conrelid = 'line_notification_preferences'::regclass
      AND contype = 'u'
      AND array_length(conkey, 1) = 1
    LIMIT 1
  );
EXCEPTION WHEN OTHERS THEN
  NULL;
END $$;

ALTER TABLE line_notification_preferences
  ADD CONSTRAINT uk_lnp_player_org UNIQUE (player_id, organization_id);
