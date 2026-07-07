-- 伝助側で削除された試合（日付×試合番号）の削除候補テーブル
-- 検知しても承認されるまでは何もデータを変更しない。承認時に該当出欠エントリを削除する。
CREATE TABLE densuke_deletion_candidates (
  id               BIGSERIAL PRIMARY KEY,
  densuke_url_id   BIGINT NOT NULL REFERENCES densuke_urls(id),
  organization_id  BIGINT NOT NULL,
  session_date     DATE NOT NULL,
  match_number     INT NOT NULL,
  status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  detected_at      TIMESTAMP NOT NULL DEFAULT NOW(),
  notified_at      TIMESTAMP,
  resolved_at      TIMESTAMP,
  resolved_by      BIGINT REFERENCES players(id),
  UNIQUE (densuke_url_id, session_date, match_number)
);

CREATE INDEX idx_densuke_deletion_candidates_org_status
  ON densuke_deletion_candidates (organization_id, status);
