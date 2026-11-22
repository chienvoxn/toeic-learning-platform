-- Reconcile vocabulary schema after the released completion V42.
-- Vocabulary DDL/permission semantics from Phuoc's eae9562, 4b3fcea, e2a2f8d (PR66).
-- Existing vocabulary history/grants are validated, never replayed.
DO $bridge$
BEGIN
    IF EXISTS(SELECT 1 FROM flyway_schema_history WHERE version='36' AND success AND script='V36__complete_settings.sql' AND checksum=-725710829) THEN
        IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='vocabularies' AND column_name='archived_at')
           OR to_regclass('public.vocabulary_review_requests') IS NOT NULL
           OR EXISTS(SELECT 1 FROM permissions WHERE code='vocabulary:learn:self') THEN
            RAISE EXCEPTION 'Partial or unexpected vocabulary schema/catalog';
        END IF;
ALTER TABLE vocabularies ADD COLUMN archived_at TIMESTAMPTZ;

ALTER TABLE vocabulary_catalog_enrollments
    DROP CONSTRAINT fk_catalog_enrollment_personal_deck,
    ADD CONSTRAINT fk_catalog_enrollment_personal_deck
        FOREIGN KEY (personal_deck_id) REFERENCES vocabulary_decks(deck_id) ON DELETE CASCADE;

CREATE TABLE vocabulary_review_requests (
 user_id UUID NOT NULL REFERENCES users(user_id),
 request_key UUID NOT NULL,
 vocabulary_id UUID NOT NULL REFERENCES vocabularies(vocabulary_id),
 rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 4),
 response JSONB NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 PRIMARY KEY (user_id, request_key)
);

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

    ELSIF EXISTS(SELECT 1 FROM flyway_schema_history WHERE version='36' AND success
        AND script='V36__vocabulary_archive_and_catalog_deck_deletion.sql' AND checksum=747431657) THEN
        IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='vocabularies'
            AND column_name='archived_at' AND data_type='timestamp with time zone' AND is_nullable='YES' AND column_default IS NULL)
           OR NOT EXISTS(SELECT 1 FROM pg_constraint WHERE conrelid='vocabulary_catalog_enrollments'::regclass
               AND conname='fk_catalog_enrollment_personal_deck' AND confdeltype='c' AND convalidated)
           OR to_regclass('public.vocabulary_review_requests') IS NULL
           OR NOT EXISTS(SELECT 1 FROM permissions WHERE code='vocabulary:learn:self') THEN
            RAISE EXCEPTION 'Vocabulary invariant mismatch';
        END IF;
    ELSE
        RAISE EXCEPTION 'Unrecognized V36 lineage';
    END IF;
END $bridge$;
