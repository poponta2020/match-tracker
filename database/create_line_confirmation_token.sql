-- LINE操作確認トークンテーブル
CREATE TABLE line_confirmation_tokens (
  id          BIGSERIAL PRIMARY KEY,
  token       VARCHAR(64) NOT NULL UNIQUE,
  action      VARCHAR(50) NOT NULL,
  params      TEXT NOT NULL,
  player_id   BIGINT NOT NULL,
  created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
  expires_at  TIMESTAMP NOT NULL,
  used_at     TIMESTAMP
);
CREATE INDEX idx_lct_token ON line_confirmation_tokens(token);
CREATE INDEX idx_lct_expires ON line_confirmation_tokens(expires_at);
