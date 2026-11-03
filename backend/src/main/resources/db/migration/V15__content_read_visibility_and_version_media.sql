-- Existing content/media remain hidden until explicitly classified by a trusted process.
ALTER TABLE learning_contents
    ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    ADD CONSTRAINT chk_content_visibility CHECK (visibility IN ('PUBLIC', 'AUTHENTICATED', 'PRIVATE'));

ALTER TABLE media_resources
    ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    ADD CONSTRAINT chk_media_visibility CHECK (visibility IN ('PUBLIC', 'AUTHENTICATED', 'PRIVATE'));

-- Legacy content_resources does not identify which version published an attachment.
-- Do not backfill potentially draft/private attachments into published snapshots.
CREATE TABLE learning_content_version_resources (
    content_version_id UUID NOT NULL REFERENCES learning_content_versions(content_version_id),
    resource_id UUID NOT NULL REFERENCES media_resources(resource_id),
    display_order INTEGER NOT NULL DEFAULT 1 CHECK (display_order >= 1),
    PRIMARY KEY (content_version_id, resource_id)
);
CREATE INDEX idx_lcvr_resource ON learning_content_version_resources(resource_id);

INSERT INTO permissions (code, description)
VALUES ('content:read', 'Doc hoc lieu published danh cho tai khoan da xac thuc')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id FROM roles r CROSS JOIN permissions p
WHERE r.name IN ('LEARNER', 'TEACHER', 'ADMIN') AND p.code = 'content:read'
ON CONFLICT (role_id, permission_id) DO NOTHING;
