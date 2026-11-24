-- TV3: vocabulary, SRS, learning-profile and vocabulary recommendations are
-- personal learner functions. Keep authorization explicit instead of relying
-- on the generic "authenticated" fallback in the security chain.
INSERT INTO permissions (code, description)
VALUES ('vocabulary:learn:self', 'Quan ly tu vung ca nhan, on tap SRS va xem tien do hoc cua chinh minh')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON p.code = 'vocabulary:learn:self'
WHERE r.name = 'LEARNER'
ON CONFLICT (role_id, permission_id) DO NOTHING;
