-- Only permissions consumed by implemented use cases are granted here.
-- Teacher review/publish and other business permission defaults remain DESIGN/OPEN.
INSERT INTO permissions (code, description)
VALUES ('user:read:self', 'Doc ho so cua chinh tai khoan dang xac thuc')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r CROSS JOIN permissions p
WHERE r.name IN ('LEARNER', 'TEACHER', 'ADMIN') AND p.code = 'user:read:self'
ON CONFLICT (role_id, permission_id) DO NOTHING;
