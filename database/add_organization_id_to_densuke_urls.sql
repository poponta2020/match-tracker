-- 伝助マルチ団体対応: densuke_urls に organization_id を追加
-- 既存データはわすらもち会(wasura)にマイグレーション

-- 1. organization_id カラムを追加（一旦NULL許容）
ALTER TABLE densuke_urls ADD COLUMN organization_id BIGINT REFERENCES organizations(id);

-- 2. 既存データをわすらもち会にマイグレーション
UPDATE densuke_urls SET organization_id = (SELECT id FROM organizations WHERE code = 'wasura');

-- 3. NOT NULL制約を追加
ALTER TABLE densuke_urls ALTER COLUMN organization_id SET NOT NULL;

-- 4. 既存の (year, month) ユニーク制約を削除
-- JPA自動生成の制約名を特定して削除
DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    SELECT c.conname INTO constraint_name
    FROM pg_constraint c
    JOIN pg_class t ON c.conrelid = t.oid
    WHERE t.relname = 'densuke_urls'
      AND c.contype = 'u'
      AND array_length(c.conkey, 1) = 2;

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE densuke_urls DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;

-- 5. 新しい (year, month, organization_id) ユニーク制約を追加
ALTER TABLE densuke_urls ADD CONSTRAINT densuke_urls_year_month_org_key UNIQUE (year, month, organization_id);
