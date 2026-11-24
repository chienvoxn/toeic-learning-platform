-- Independent DRAFT authoring release; NOT a rename/replay of PR61 V26__exam_workflow.sql.
-- No changes to historical TestVersion/attempt rows, publication, media or conversion policy.
CREATE TABLE test_version_drafts (
    test_version_id UUID PRIMARY KEY REFERENCES test_versions(test_version_id),
    revision INTEGER NOT NULL DEFAULT 0 CHECK (revision >= 0),
    document JSONB NOT NULL CHECK (jsonb_typeof(document) = 'object')
);

INSERT INTO permissions(code, description) VALUES
    ('test:manage:self', 'Bien soan bo de DRAFT cua minh'),
    ('test:manage:any', 'Quan ly bo de DRAFT cua moi nguoi');

INSERT INTO role_permissions(role_id, permission_id)
SELECT r.role_id,p.permission_id FROM roles r CROSS JOIN permissions p
WHERE (r.name IN ('TEACHER','ADMIN') AND p.code='test:manage:self')
   OR (r.name='ADMIN' AND p.code='test:manage:any');
