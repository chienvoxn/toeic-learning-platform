-- Compatibility bridge for the released develop vocabulary lineage only.
-- Historical SQL/history stays immutable; complete dependencies before V41.
DO $bridge$
BEGIN
    IF (SELECT count(*) FROM flyway_schema_history WHERE success AND
       ((version='34' AND script='V34__rag_chat_runtime.sql' AND checksum=2001005912) OR
        (version='35' AND script='V35__ai_conversation_delete_cascade.sql' AND checksum=359517897) OR
        (version='36' AND script='V36__vocabulary_archive_and_catalog_deck_deletion.sql' AND checksum=747431657) OR
        (version='37' AND script='V37__vocabulary_review_idempotency.sql' AND checksum=-469993577) OR
        (version='38' AND script='V38__vocabulary_learning_permission.sql' AND checksum=-2132296234)))<>5 THEN
        RAISE EXCEPTION 'Unrecognized vocabulary release history';
    END IF;
    IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='system_settings' AND column_name='revision')
       OR to_regclass('public.moderation_versions') IS NOT NULL OR to_regclass('public.moderation_contributors') IS NOT NULL
       OR to_regclass('public.moderation_decisions') IS NOT NULL
       OR EXISTS(SELECT 1 FROM permissions WHERE code IN ('system:settings:manage','content:review','content:publish','question:review','question:publish')) THEN
        RAISE EXCEPTION 'Partial or unexpected completion schema/catalog';
    END IF;
    IF to_regclass('public.ai_indexed_versions') IS NULL OR to_regclass('public.ai_chat_requests') IS NULL
       OR to_regclass('public.ai_citation_snapshots') IS NULL
       OR (SELECT count(*) FROM permissions WHERE code IN ('ai:chat:self','ai:knowledge:manage'))<>2 THEN
        RAISE EXCEPTION 'Missing released RAG dependency';
    END IF;
-- ADMIN-06: optimistic versions on the existing settings table; preserve legacy keys/values.
ALTER TABLE system_settings ADD COLUMN revision BIGINT NOT NULL DEFAULT 0
    CONSTRAINT ck_system_settings_revision CHECK (revision>=0);
INSERT INTO system_settings(setting_key,setting_value) VALUES
 ('vocabulary.recommendations.defaultLimit','10'::jsonb),
 ('learning.progress.defaultDays','7'::jsonb)
ON CONFLICT(setting_key) DO NOTHING;
INSERT INTO permissions(code,description) VALUES ('system:settings:manage','Read and update allowlisted business defaults')
ON CONFLICT(code) DO NOTHING;
INSERT INTO role_permissions(role_id,permission_id)
SELECT r.role_id,p.permission_id FROM roles r CROSS JOIN permissions p
WHERE r.name='ADMIN' AND p.code='system:settings:manage' ON CONFLICT DO NOTHING;

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

END $bridge$;
