-- 伝助メンバーIDマッピングテーブル（URL単位）
CREATE TABLE densuke_member_mappings (
  id                BIGSERIAL PRIMARY KEY,
  densuke_url_id    BIGINT NOT NULL REFERENCES densuke_urls(id),
  player_id         BIGINT NOT NULL REFERENCES players(id),
  densuke_member_id VARCHAR(50) NOT NULL,
  created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
  UNIQUE (densuke_url_id, player_id)
);
