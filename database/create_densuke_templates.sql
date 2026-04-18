-- 伝助テンプレート管理テーブル（団体単位）
-- 団体ごとに1レコード。作成時のタイトル・説明・連絡先メアドのデフォルト値を保持する
CREATE TABLE densuke_templates (
  id              BIGSERIAL PRIMARY KEY,
  organization_id BIGINT NOT NULL UNIQUE REFERENCES organizations(id),
  title_template  VARCHAR(200) NOT NULL,
  description     TEXT,
  contact_email   VARCHAR(255),
  created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
