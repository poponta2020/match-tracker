-- メンターフィードバック機能: mentor_relationships テーブル追加
-- メンター（先輩）とメンティー（後輩）の関係を管理する

CREATE TABLE IF NOT EXISTS mentor_relationships (
    id BIGSERIAL PRIMARY KEY,
    mentor_id BIGINT NOT NULL REFERENCES players(id),
    mentee_id BIGINT NOT NULL REFERENCES players(id),
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'ACTIVE', 'REJECTED')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_mentor_relationship UNIQUE (mentor_id, mentee_id, organization_id)
);
