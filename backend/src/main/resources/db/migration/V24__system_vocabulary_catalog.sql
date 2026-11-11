-- System vocabulary catalog for shared TOEIC learning materials

CREATE TABLE system_vocabulary_decks (
    deck_id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    category VARCHAR(100),
    source_name VARCHAR(255),
    source_url TEXT,
    is_published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE system_vocabularies (
    vocabulary_id UUID PRIMARY KEY,
    word VARCHAR(150) NOT NULL,
    meaning TEXT NOT NULL,
    part_of_speech VARCHAR(40),
    pronunciation VARCHAR(255),
    example TEXT,
    note TEXT,
    source_name VARCHAR(255),
    source_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE system_deck_vocabularies (
    deck_id UUID NOT NULL,
    vocabulary_id UUID NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,

    PRIMARY KEY (deck_id, vocabulary_id),

    CONSTRAINT fk_system_deck_vocabularies_deck
        FOREIGN KEY (deck_id)
        REFERENCES system_vocabulary_decks(deck_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_system_deck_vocabularies_vocabulary
        FOREIGN KEY (vocabulary_id)
        REFERENCES system_vocabularies(vocabulary_id)
);

CREATE INDEX idx_system_decks_published
    ON system_vocabulary_decks(is_published);

CREATE INDEX idx_system_vocabularies_word
    ON system_vocabularies(LOWER(word));

CREATE INDEX idx_system_deck_vocabularies_order
    ON system_deck_vocabularies(deck_id, display_order);

CREATE UNIQUE INDEX uq_system_decks_name
    ON system_vocabulary_decks(LOWER(name));
