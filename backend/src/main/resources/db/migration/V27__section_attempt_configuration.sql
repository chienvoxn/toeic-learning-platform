-- EXAM-01: configurable Section snapshots; existing Practice/Test rows remain unchanged.
ALTER TABLE attempts
    ADD COLUMN section_type VARCHAR(20),
    ADD COLUMN configured_duration_seconds INTEGER,
    ADD CONSTRAINT ck_section_attempt_configuration CHECK (
        (attempt_source_type = 'SECTION_SNAPSHOT'
            AND attempt_type = 'SECTION' AND test_version_id IS NULL
            AND section_type IS NOT NULL AND section_type IN ('LISTENING','READING')
            AND configured_duration_seconds IS NOT NULL
            AND configured_duration_seconds BETWEEN 60 AND 7200 AND configured_duration_seconds % 60 = 0
            AND total_questions IS NOT NULL AND total_questions BETWEEN 1 AND 100
            AND expires_at = started_at + configured_duration_seconds * INTERVAL '1 second')
        OR (attempt_source_type <> 'SECTION_SNAPSHOT' AND section_type IS NULL AND configured_duration_seconds IS NULL)
    );

INSERT INTO permissions(code,description) VALUES ('exam:manage:self','Lam Section va xem ket qua cua chinh minh')
ON CONFLICT (code) DO NOTHING;
INSERT INTO role_permissions(role_id,permission_id)
SELECT r.role_id,p.permission_id FROM roles r CROSS JOIN permissions p
WHERE r.name='LEARNER' AND p.code='exam:manage:self'
ON CONFLICT (role_id,permission_id) DO NOTHING;
