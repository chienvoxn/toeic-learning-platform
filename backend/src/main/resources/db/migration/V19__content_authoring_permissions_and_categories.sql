-- CONTENT-03 product decisions, 2026-11-04. Codes are stable identifiers; existing rows are preserved.
INSERT INTO content_categories (code, name) VALUES
    ('GRAMMAR', 'Grammar'), ('VOCAB', 'Vocabulary'), ('LISTENING', 'Listening'),
    ('READING', 'Reading'), ('STRATEGIES', 'Strategies'), ('DOCS', 'Documents')
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (code, description) VALUES
    ('content:manage:self', 'Quan ly hoc lieu cua chinh minh'),
    ('content:manage:any', 'Quan ly toan bo hoc lieu')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id FROM roles r CROSS JOIN permissions p
WHERE (r.name IN ('TEACHER', 'ADMIN') AND p.code='content:manage:self')
   OR (r.name='ADMIN' AND p.code='content:manage:any')
ON CONFLICT (role_id, permission_id) DO NOTHING;
