-- 抜け番活動記録テーブル
CREATE TABLE IF NOT EXISTS bye_activities (
    id BIGSERIAL PRIMARY KEY,
    session_date DATE NOT NULL,
    match_number INT NOT NULL,
    player_id BIGINT NOT NULL,
    activity_type VARCHAR(20) NOT NULL,
    free_text VARCHAR(255),
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 外部キー制約
    CONSTRAINT fk_bye_activities_player
        FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bye_activities_created_by
        FOREIGN KEY (created_by) REFERENCES players(id) ON DELETE RESTRICT,
    CONSTRAINT fk_bye_activities_updated_by
        FOREIGN KEY (updated_by) REFERENCES players(id) ON DELETE RESTRICT,

    -- ユニーク制約（同一試合で同一選手は1レコードのみ）
    CONSTRAINT uk_bye_activities_unique
        UNIQUE (session_date, match_number, player_id)
);

-- インデックス
CREATE INDEX IF NOT EXISTS idx_bye_activities_date ON bye_activities (session_date);
CREATE INDEX IF NOT EXISTS idx_bye_activities_date_match ON bye_activities (session_date, match_number);
CREATE INDEX IF NOT EXISTS idx_bye_activities_player ON bye_activities (player_id);
