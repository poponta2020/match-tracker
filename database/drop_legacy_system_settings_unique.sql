-- ============================================
-- system_settings の不要な unique 制約を削除
-- 関連Issue: #557
-- ============================================
--
-- 経緯:
--   - 初期版で `setting_key VARCHAR(100) NOT NULL UNIQUE` で作成した単独 unique 制約が
--     Hibernate 自動生成名 `uknm18l4pyovtvd8y3b3x0l2y64` で本番DBに残存していた。
--   - `migration_organization.sql` で `system_settings_setting_key_key` /
--     `uk_system_settings_setting_key` の名前で DROP を試みたが実際の名前と一致せず、
--     `(setting_key, organization_id)` 複合 unique を追加した結果、単独 unique も生き残った。
--   - これにより別団体で同じ setting_key を保存しようとすると重複違反になる。
--
-- また、複合 unique も `uk_system_settings_key_org` と Hibernate 自動生成名
-- `uk3uukkredspdgpq65rggt9l5m4` の2つに重複しているため自動生成名側を削除する。

ALTER TABLE system_settings
  DROP CONSTRAINT IF EXISTS uknm18l4pyovtvd8y3b3x0l2y64;

ALTER TABLE system_settings
  DROP CONSTRAINT IF EXISTS uk3uukkredspdgpq65rggt9l5m4;
