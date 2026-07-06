-- 取り札記録 機能: 札ルールnonce共有 / 取り札配置 / お手付き詳細
-- CLAUDE.md「DBマイグレーション適用ルール」に従い本番PostgreSQLにも適用すること。

-- 1) 札ルール再生成カウンタ(nonce)を日付単位でDB共有（端末間で出札50枚を一致させる）
CREATE TABLE IF NOT EXISTS card_rule_nonce (
    id BIGSERIAL PRIMARY KEY,
    session_date DATE NOT NULL,
    nonce INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_card_rule_nonce_date UNIQUE (session_date),
    CONSTRAINT chk_card_rule_nonce CHECK (nonce >= 0)
);

-- 2) 取り札配置（各自の私的データ。1試合×1プレイヤー×1札で最大1レコード。不明は行なし）
CREATE TABLE IF NOT EXISTS match_card_placements (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL,
    player_id BIGINT NOT NULL,
    card_no SMALLINT NOT NULL,
    taken_by VARCHAR(16) NOT NULL,   -- SELF / OPPONENT
    field VARCHAR(8) NOT NULL,       -- ENEMY / OWN
    side VARCHAR(8) NOT NULL,        -- LEFT / RIGHT
    tier VARCHAR(8) NOT NULL,        -- TOP / MIDDLE / BOTTOM
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_match_card_placements_match
        FOREIGN KEY (match_id) REFERENCES matches(id) ON DELETE CASCADE,
    CONSTRAINT fk_match_card_placements_player
        FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE RESTRICT,
    CONSTRAINT uq_match_card_placements
        UNIQUE (match_id, player_id, card_no),
    CONSTRAINT chk_mcp_card_no CHECK (card_no >= 1 AND card_no <= 100),
    CONSTRAINT chk_mcp_taken_by CHECK (taken_by IN ('SELF', 'OPPONENT')),
    CONSTRAINT chk_mcp_field CHECK (field IN ('ENEMY', 'OWN')),
    CONSTRAINT chk_mcp_side CHECK (side IN ('LEFT', 'RIGHT')),
    CONSTRAINT chk_mcp_tier CHECK (tier IN ('TOP', 'MIDDLE', 'BOTTOM'))
);

CREATE INDEX IF NOT EXISTS idx_match_card_placements_player
    ON match_card_placements (player_id, match_id);

-- 3) お手付き詳細（各自の私的データ。お手付き回数分、seqで順序管理）
CREATE TABLE IF NOT EXISTS match_otetsuki_details (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL,
    player_id BIGINT NOT NULL,
    seq INT NOT NULL,
    otetsuki_type VARCHAR(16) NOT NULL,          -- HIKKAKE / ANKI_MISS / MISHEARING / OTHER
    hikkake_target VARCHAR(24),                  -- OWN_RIGHT_TOP / OWN_LEFT_TOP / ENEMY_RIGHT_TOP / ENEMY_LEFT_TOP
    anki_direction VARCHAR(40),                  -- SENT_TO_ENEMY_TOUCHED_OWN / RECEIVED_FROM_ENEMY_TOUCHED_ENEMY
    mishearing_read_card_no SMALLINT,            -- 読まれた札 1..100
    mishearing_touched_card_no SMALLINT,         -- 触った札 1..100
    other_text TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_match_otetsuki_details_match
        FOREIGN KEY (match_id) REFERENCES matches(id) ON DELETE CASCADE,
    CONSTRAINT fk_match_otetsuki_details_player
        FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE RESTRICT,
    CONSTRAINT uq_match_otetsuki_details
        UNIQUE (match_id, player_id, seq),
    CONSTRAINT chk_mod_type
        CHECK (otetsuki_type IN ('HIKKAKE', 'ANKI_MISS', 'MISHEARING', 'OTHER')),
    CONSTRAINT chk_mod_read_no
        CHECK (mishearing_read_card_no IS NULL OR (mishearing_read_card_no >= 1 AND mishearing_read_card_no <= 100)),
    CONSTRAINT chk_mod_touched_no
        CHECK (mishearing_touched_card_no IS NULL OR (mishearing_touched_card_no >= 1 AND mishearing_touched_card_no <= 100))
);

CREATE INDEX IF NOT EXISTS idx_match_otetsuki_details_player
    ON match_otetsuki_details (player_id, match_id);
