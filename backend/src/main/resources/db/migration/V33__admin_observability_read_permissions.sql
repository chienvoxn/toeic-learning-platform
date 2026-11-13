-- ADMIN-07: read-only administration over existing audit/session/attempt data.
INSERT INTO permissions(code,description) VALUES
  ('audit:read','Read sanitized administration audit records'),
  ('system:health:read','Read protected component health'),
  ('system:usage:read','Read aggregate account and attempt usage')
ON CONFLICT(code) DO NOTHING;
INSERT INTO role_permissions(role_id,permission_id)
SELECT r.role_id,p.permission_id FROM roles r CROSS JOIN permissions p
WHERE r.name='ADMIN' AND p.code IN ('audit:read','system:health:read','system:usage:read')
ON CONFLICT DO NOTHING;
