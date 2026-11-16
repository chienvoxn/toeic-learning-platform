-- ADMIN-03: new writes only. Do not infer contributors for legacy versions.
CREATE TABLE moderation_versions (
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('contents','questions','groups')),
    version_id UUID NOT NULL,
    parent_id UUID NOT NULL,
    source_version_id UUID,
    cycle_key UUID NOT NULL,
    revision BIGINT NOT NULL CHECK (revision>=0),
    approved_revision BIGINT,
    evidence_complete BOOLEAN NOT NULL,
    PRIMARY KEY(kind,version_id)
);
CREATE INDEX idx_moderation_cycle ON moderation_versions(kind,parent_id,cycle_key);
CREATE TABLE moderation_contributors (
    event_id UUID PRIMARY KEY,
    kind VARCHAR(20) NOT NULL,
    version_id UUID NOT NULL,
    actor_id UUID NOT NULL REFERENCES users(user_id),
    action VARCHAR(12) NOT NULL CHECK(action IN ('CREATED','EDITED','SUBMITTED')),
    created_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY(kind,version_id) REFERENCES moderation_versions(kind,version_id) ON DELETE CASCADE
);
CREATE INDEX idx_moderation_contributor ON moderation_contributors(kind,version_id,actor_id);
CREATE TABLE moderation_decisions (
    decision_id UUID PRIMARY KEY,
    kind VARCHAR(20) NOT NULL,
    version_id UUID NOT NULL,
    revision BIGINT NOT NULL,
    actor_id UUID NOT NULL REFERENCES users(user_id),
    action VARCHAR(12) NOT NULL,
    reason VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY(kind,version_id) REFERENCES moderation_versions(kind,version_id) ON DELETE CASCADE
);
INSERT INTO permissions(code,description) VALUES
 ('content:review','Review learning content versions'),('content:publish','Publish learning content versions'),
 ('question:review','Review question versions'),('question:publish','Publish question versions') ON CONFLICT DO NOTHING;
INSERT INTO role_permissions(role_id,permission_id)
SELECT r.role_id,p.permission_id FROM roles r CROSS JOIN permissions p
WHERE r.name='ADMIN' AND p.code IN ('content:review','content:publish','question:review','question:publish') ON CONFLICT DO NOTHING;
