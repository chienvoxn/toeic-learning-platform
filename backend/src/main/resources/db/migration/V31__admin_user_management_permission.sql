-- ADMIN-01: capability only, no role/permission CRUD or implicit ADMIN bypass.
INSERT INTO permissions(code,description)
VALUES ('user:manage','Search Learner/Teacher accounts and lock/unlock eligible accounts')
ON CONFLICT(code) DO NOTHING;
INSERT INTO role_permissions(role_id,permission_id)
SELECT r.role_id,p.permission_id FROM roles r CROSS JOIN permissions p
WHERE r.name='ADMIN' AND p.code='user:manage'
ON CONFLICT DO NOTHING;
