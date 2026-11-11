CREATE TABLE vocabulary_catalog_enrollments (
    enrollment_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    system_deck_id UUID NOT NULL,
    personal_deck_id UUID NOT NULL,
    enrolled_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_catalog_enrollment_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id),

    CONSTRAINT fk_catalog_enrollment_system_deck
        FOREIGN KEY (system_deck_id)
        REFERENCES system_vocabulary_decks(deck_id),

    CONSTRAINT fk_catalog_enrollment_personal_deck
        FOREIGN KEY (personal_deck_id)
        REFERENCES vocabulary_decks(deck_id),

    CONSTRAINT uq_catalog_enrollment_user_deck
        UNIQUE (user_id, system_deck_id),

    CONSTRAINT uq_catalog_enrollment_personal_deck
        UNIQUE (personal_deck_id)
);

CREATE INDEX idx_catalog_enrollment_user
    ON vocabulary_catalog_enrollments(user_id);
