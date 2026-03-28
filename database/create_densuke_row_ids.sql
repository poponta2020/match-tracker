-- 伝助行IDキャッシュテーブル（日付×試合番号の join-{id} を保存）
CREATE TABLE densuke_row_ids (
  id               BIGSERIAL PRIMARY KEY,
  densuke_url_id   BIGINT NOT NULL REFERENCES densuke_urls(id),
  densuke_row_id   VARCHAR(50) NOT NULL,
  session_date     DATE NOT NULL,
  match_number     INT NOT NULL,
  created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
  UNIQUE (densuke_url_id, session_date, match_number)
);
