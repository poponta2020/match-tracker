-- メンターフィードバック機能: match_comments テーブル追加
-- 試合に対するメンター・メンティー間のコメントスレッド

CREATE TABLE IF NOT EXISTS match_comments (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL REFERENCES matches(id),
    mentee_id BIGINT NOT NULL REFERENCES players(id),
    author_id BIGINT NOT NULL REFERENCES players(id),
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL
);

-- コメント取得クエリ用の複合インデックス
CREATE INDEX IF NOT EXISTS idx_match_comments_thread ON match_comments(match_id, mentee_id, deleted_at, created_at);
