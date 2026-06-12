-- 試合動画アクセス機能: match_videos テーブル追加
-- 練習試合の動画（YouTube限定公開）のURLを試合と紐付けて管理する「動画台帳」。
-- matches / match_pairings とは FK を持たず、(match_date, match_number, player1_id, player2_id)
-- の自然キーで対応付く（player1_id < player2_id をアプリ層で保証）。
-- これにより結果未入力（ペアリングのみ）の試合にも動画登録でき、結果入力後に自動で試合詳細へ表示される。

CREATE TABLE IF NOT EXISTS match_videos (
    id BIGSERIAL PRIMARY KEY,
    match_date DATE NOT NULL,
    match_number INTEGER NOT NULL,
    player1_id BIGINT NOT NULL REFERENCES players(id) ON DELETE RESTRICT,
    player2_id BIGINT NOT NULL REFERENCES players(id) ON DELETE RESTRICT,
    provider VARCHAR(20) NOT NULL DEFAULT 'YOUTUBE',
    video_url TEXT NOT NULL,
    youtube_video_id VARCHAR(20),
    title VARCHAR(255),
    created_by BIGINT NOT NULL REFERENCES players(id) ON DELETE RESTRICT,
    updated_by BIGINT NOT NULL REFERENCES players(id) ON DELETE RESTRICT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_match_videos_match UNIQUE (match_date, match_number, player1_id, player2_id)
);

-- 選手別の動画検索用インデックス（player1 OR player2 で絞り込む）
CREATE INDEX IF NOT EXISTS idx_match_videos_player1 ON match_videos(player1_id);
CREATE INDEX IF NOT EXISTS idx_match_videos_player2 ON match_videos(player2_id);
