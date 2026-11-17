-- ADMIN-02 reuses the existing RBAC tables; name remains the authority code.
ALTER TABLE roles ADD COLUMN display_name VARCHAR(150);
INSERT INTO permissions(code,description) VALUES ('rbac:manage','Manage roles, permission assignments and user roles')
ON CONFLICT(code) DO NOTHING;
INSERT INTO role_permissions(role_id,permission_id)
SELECT r.role_id,p.permission_id FROM roles r CROSS JOIN permissions p
WHERE r.name='ADMIN' AND p.code='rbac:manage' ON CONFLICT DO NOTHING;
