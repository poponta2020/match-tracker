-- ============================================
-- 団体管理機能 マイグレーション
-- 関連Issue: #80, #81
-- ============================================

-- ============================================
-- 1. organizations テーブル作成
-- ============================================
CREATE TABLE IF NOT EXISTS organizations (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(50) NOT NULL UNIQUE,
  name VARCHAR(200) NOT NULL,
  color VARCHAR(10) NOT NULL,
  deadline_type VARCHAR(20) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 初期データ挿入
INSERT INTO organizations (code, name, color, deadline_type)
VALUES ('wasura', 'わすらもち会', '#22c55e', 'SAME_DAY')
ON CONFLICT (code) DO NOTHING;

INSERT INTO organizations (code, name, color, deadline_type)
VALUES ('hokudai', '北海道大学かるた会', '#ef4444', 'MONTHLY')
ON CONFLICT (code) DO NOTHING;

-- ============================================
-- 2. player_organizations テーブル作成
-- ============================================
CREATE TABLE IF NOT EXISTS player_organizations (
  id BIGSERIAL PRIMARY KEY,
  player_id BIGINT NOT NULL,
  organization_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (player_id, organization_id)
);

-- ============================================
-- 3. practice_sessions に organization_id 追加
-- ============================================
ALTER TABLE practice_sessions
  ADD COLUMN IF NOT EXISTS organization_id BIGINT;

-- ============================================
-- 4. system_settings に organization_id 追加
-- ============================================
ALTER TABLE system_settings
  ADD COLUMN IF NOT EXISTS organization_id BIGINT;

-- 既存のユニーク制約を削除し、新しい複合ユニーク制約を追加
-- 注意: 制約名は環境によって異なる場合あり。Hibernateが自動生成した制約名を確認すること
ALTER TABLE system_settings
  DROP CONSTRAINT IF EXISTS system_settings_setting_key_key;
ALTER TABLE system_settings
  DROP CONSTRAINT IF EXISTS uk_system_settings_setting_key;

ALTER TABLE system_settings
  ADD CONSTRAINT uk_system_settings_key_org UNIQUE (setting_key, organization_id);

-- ============================================
-- 5. players に admin_organization_id 追加
-- ============================================
ALTER TABLE players
  ADD COLUMN IF NOT EXISTS admin_organization_id BIGINT;

-- ============================================
-- 6. invite_tokens に organization_id 追加
-- ============================================
ALTER TABLE invite_tokens
  ADD COLUMN IF NOT EXISTS organization_id BIGINT;

-- ============================================
-- 7. データ移行（既存データをわすらもち会に紐づけ）
-- ============================================

-- 既存の全プレイヤーをわすらもち会に登録
INSERT INTO player_organizations (player_id, organization_id)
SELECT p.id, (SELECT id FROM organizations WHERE code = 'wasura')
FROM players p
WHERE p.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM player_organizations po
    WHERE po.player_id = p.id
      AND po.organization_id = (SELECT id FROM organizations WHERE code = 'wasura')
  );

-- 既存の練習日をわすらもち会に紐づけ
UPDATE practice_sessions
SET organization_id = (SELECT id FROM organizations WHERE code = 'wasura')
WHERE organization_id IS NULL;

-- 既存のシステム設定をわすらもち会に紐づけ
UPDATE system_settings
SET organization_id = (SELECT id FROM organizations WHERE code = 'wasura')
WHERE organization_id IS NULL;

-- 既存の招待トークンをわすらもち会に紐づけ
UPDATE invite_tokens
SET organization_id = (SELECT id FROM organizations WHERE code = 'wasura')
WHERE organization_id IS NULL;

-- ============================================
-- 8. NOT NULL制約の追加（データ移行後）
-- ============================================
ALTER TABLE practice_sessions
  ALTER COLUMN organization_id SET NOT NULL;

ALTER TABLE invite_tokens
  ALTER COLUMN organization_id SET NOT NULL;

-- system_settings の organization_id は NOT NULL にする
ALTER TABLE system_settings
  ALTER COLUMN organization_id SET NOT NULL;
