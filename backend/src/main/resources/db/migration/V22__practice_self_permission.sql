-- PRAC-01: independent learner practice permission; no question:read or media reuse grants.
INSERT INTO permissions(code,description)
VALUES ('practice:manage:self','Tao va doc luot luyen tap cua chinh minh')
ON CONFLICT (code) DO NOTHING;
INSERT INTO role_permissions(role_id,permission_id)
SELECT r.role_id,p.permission_id FROM roles r CROSS JOIN permissions p
WHERE r.name='LEARNER' AND p.code='practice:manage:self'
ON CONFLICT (role_id,permission_id) DO NOTHING;
