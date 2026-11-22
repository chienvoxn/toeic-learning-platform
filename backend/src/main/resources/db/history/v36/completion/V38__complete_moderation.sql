-- Compatibility V35: retain applied history, do not repeat existing grants.
DO $bridge$
BEGIN
    IF (SELECT count(*) FROM flyway_schema_history WHERE version='35' AND success AND
      ((script='V35__content_review_publication.sql' AND checksum=-949250551) OR
       (script='V35__ai_conversation_delete_cascade.sql' AND checksum=359517897))) <> 1 THEN
        RAISE EXCEPTION 'Unrecognized V35 lineage';
    END IF;
    IF EXISTS (SELECT 1 FROM flyway_schema_history WHERE version='35' AND script='V35__ai_conversation_delete_cascade.sql') THEN
        IF to_regclass('public.moderation_versions') IS NOT NULL OR to_regclass('public.moderation_contributors') IS NOT NULL
            OR to_regclass('public.moderation_decisions') IS NOT NULL
            OR EXISTS (SELECT 1 FROM permissions WHERE code IN ('content:review','content:publish','question:review','question:publish')) THEN
            RAISE EXCEPTION 'Partial or unexpected moderation feature';
        END IF;
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

    ELSE
        IF to_regclass('public.moderation_versions') IS NULL OR to_regclass('public.moderation_contributors') IS NULL
            OR to_regclass('public.moderation_decisions') IS NULL
            OR (SELECT count(*) FROM permissions WHERE code IN ('content:review','content:publish','question:review','question:publish'))<>4 THEN
            RAISE EXCEPTION 'Moderation invariant mismatch';
        END IF;
    END IF;
END $bridge$;
