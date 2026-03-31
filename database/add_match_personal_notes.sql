-- 個人メモ・お手付き記録テーブル
CREATE TABLE IF NOT EXISTS match_personal_notes (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL,
    player_id BIGINT NOT NULL,
    notes TEXT,
    otetsuki_count INT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 外部キー制約
    CONSTRAINT fk_match_personal_notes_match
        FOREIGN KEY (match_id) REFERENCES matches(id) ON DELETE CASCADE,
    CONSTRAINT fk_match_personal_notes_player
        FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE RESTRICT,

    -- ユニーク制約（同一試合で同一選手は1レコードのみ）
    CONSTRAINT uq_match_personal_notes
        UNIQUE (match_id, player_id),

    -- お手付き回数の範囲チェック
    CONSTRAINT chk_otetsuki_count
        CHECK (otetsuki_count >= 0 AND otetsuki_count <= 20)
);

-- インデックス
CREATE INDEX IF NOT EXISTS idx_match_personal_notes_player ON match_personal_notes (player_id, match_id);

-- matchesテーブルからnotesカラムを削除
ALTER TABLE matches DROP COLUMN IF EXISTS notes;
