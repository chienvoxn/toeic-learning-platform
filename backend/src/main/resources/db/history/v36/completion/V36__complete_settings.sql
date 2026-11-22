-- REHEARSAL ONLY: no history repair, no repeated grants.
DO $bridge$
BEGIN
    IF (SELECT count(*) FROM flyway_schema_history WHERE version='34' AND success AND
      ((script='V34__managed_system_defaults.sql' AND checksum=1299604777) OR
       (script='V34__rag_chat_runtime.sql' AND checksum=2001005912))) <> 1 THEN
        RAISE EXCEPTION 'Unrecognized V34 lineage';
    END IF;

    IF EXISTS (SELECT 1 FROM flyway_schema_history WHERE version='34' AND script='V34__rag_chat_runtime.sql') THEN
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='system_settings' AND column_name='revision')
            OR EXISTS (SELECT 1 FROM permissions WHERE code='system:settings:manage') THEN
            RAISE EXCEPTION 'Partial or unexpected Settings schema/catalog';
        END IF;
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
    ELSE
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='system_settings'
            AND column_name='revision' AND data_type='bigint' AND is_nullable='NO' AND column_default='0')
            OR NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='system_settings'::regclass AND conname='ck_system_settings_revision'
                AND pg_get_constraintdef(oid)='CHECK ((revision >= 0))')
            OR NOT EXISTS (SELECT 1 FROM permissions WHERE code='system:settings:manage') THEN
            RAISE EXCEPTION 'Settings invariant mismatch';
        END IF;
    END IF;
END $bridge$;
