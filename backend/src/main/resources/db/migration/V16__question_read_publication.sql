-- Publication metadata for read APIs only. No publish workflow or published backfill.
ALTER TABLE questions ADD COLUMN current_published_version_id UUID;
ALTER TABLE questions ADD CONSTRAINT fk_question_current_version
    FOREIGN KEY (current_published_version_id, question_id)
    REFERENCES question_versions(question_version_id, question_id);

ALTER TABLE question_groups
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN current_published_version_id UUID;
ALTER TABLE question_group_versions
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN published_at TIMESTAMPTZ,
    ADD COLUMN archived_at TIMESTAMPTZ,
    ADD CONSTRAINT uq_qgv_version_group UNIQUE (group_version_id, group_id);
ALTER TABLE question_groups ADD CONSTRAINT fk_group_current_version
    FOREIGN KEY (current_published_version_id, group_id)
    REFERENCES question_group_versions(group_version_id, group_id);

INSERT INTO permissions (code, description)
VALUES ('question:read', 'Doc cau hoi published danh cho teacher va admin')
ON CONFLICT (code) DO NOTHING;
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id FROM roles r CROSS JOIN permissions p
WHERE r.name IN ('TEACHER', 'ADMIN') AND p.code = 'question:read'
ON CONFLICT (role_id, permission_id) DO NOTHING;
