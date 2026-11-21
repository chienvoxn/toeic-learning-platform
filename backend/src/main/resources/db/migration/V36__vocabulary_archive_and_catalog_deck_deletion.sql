ALTER TABLE vocabularies ADD COLUMN archived_at TIMESTAMPTZ;

ALTER TABLE vocabulary_catalog_enrollments
    DROP CONSTRAINT fk_catalog_enrollment_personal_deck,
    ADD CONSTRAINT fk_catalog_enrollment_personal_deck
        FOREIGN KEY (personal_deck_id) REFERENCES vocabulary_decks(deck_id) ON DELETE CASCADE;
