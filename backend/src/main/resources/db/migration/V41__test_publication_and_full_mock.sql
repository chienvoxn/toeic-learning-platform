-- Extend existing moderation and assessment; never infer contributors or pointers for legacy rows.
ALTER TABLE moderation_versions DROP CONSTRAINT moderation_versions_kind_check;
ALTER TABLE moderation_versions ADD CONSTRAINT moderation_versions_kind_check
    CHECK (kind IN ('contents','questions','groups','tests'));

ALTER TABLE test_versions ADD CONSTRAINT uq_test_version_parent UNIQUE(test_id,test_version_id);
ALTER TABLE tests ADD COLUMN current_published_version_id UUID,
    ADD CONSTRAINT fk_test_current_published FOREIGN KEY(test_id,current_published_version_id)
        REFERENCES test_versions(test_id,test_version_id);
ALTER TABLE test_versions ADD COLUMN blueprint_snapshot JSONB,
    ADD CONSTRAINT ck_test_blueprint_snapshot CHECK (blueprint_snapshot IS NULL OR jsonb_typeof(blueprint_snapshot)='object');
CREATE UNIQUE INDEX uq_test_working_version ON test_versions(test_id)
    WHERE status IN ('DRAFT','IN_REVIEW','APPROVED');

ALTER TABLE test_blueprints ADD COLUMN duration_seconds INTEGER,
    ADD COLUMN rules JSONB,
    ADD CONSTRAINT ck_blueprint_duration CHECK(duration_seconds IS NULL OR duration_seconds>0),
    ADD CONSTRAINT ck_blueprint_rules CHECK(rules IS NULL OR jsonb_typeof(rules)='object');
ALTER TABLE attempts ADD COLUMN test_blueprint_snapshot JSONB,
    ADD CONSTRAINT ck_attempt_test_snapshot CHECK(test_blueprint_snapshot IS NULL OR
        (attempt_source_type='TEST_VERSION' AND test_version_id IS NOT NULL AND attempt_type='FULL_MOCK'
         AND jsonb_typeof(test_blueprint_snapshot)='object'));

INSERT INTO permissions(code,description) VALUES
    ('test:review','Review test versions'),('test:publish','Publish and retire test versions');
INSERT INTO role_permissions(role_id,permission_id)
SELECT r.role_id,p.permission_id FROM roles r CROSS JOIN permissions p
WHERE r.name='ADMIN' AND p.code IN ('test:review','test:publish');
-- No numeric Full Mock preset is activated until its blueprint configuration is confirmed.
