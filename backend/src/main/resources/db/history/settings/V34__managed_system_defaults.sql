-- ADMIN-06: optimistic versions on the existing settings table; preserve legacy keys/values.
ALTER TABLE system_settings ADD COLUMN revision BIGINT NOT NULL DEFAULT 0
    CONSTRAINT ck_system_settings_revision CHECK (revision>=0);
INSERT INTO system_settings(setting_key,setting_value) VALUES
 ('vocabulary.recommendations.defaultLimit','10'::jsonb),
 ('learning.progress.defaultDays','7'::jsonb)
ON CONFLICT(setting_key) DO NOTHING;
INSERT INTO permissions(code,description) VALUES ('system:settings:manage','Read and update allowlisted business defaults')
ON CONFLICT(code) DO NOTHING;
INSERT INTO role_permissions(role_id,permission_id)
SELECT r.role_id,p.permission_id FROM roles r CROSS JOIN permissions p
WHERE r.name='ADMIN' AND p.code='system:settings:manage' ON CONFLICT DO NOTHING;
