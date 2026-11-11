-- AUTH-04: own-profile changes only; no administrative grant or role inheritance.
INSERT INTO permissions (code, description)
VALUES ('user:update:self', 'Cap nhat ho so cua chinh tai khoan dang xac thuc'),
       ('user:password:change:self', 'Doi mat khau cua chinh tai khoan dang xac thuc')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r CROSS JOIN permissions p
WHERE r.name IN ('LEARNER', 'TEACHER', 'ADMIN')
  AND p.code IN ('user:update:self', 'user:password:change:self')
ON CONFLICT (role_id, permission_id) DO NOTHING;
