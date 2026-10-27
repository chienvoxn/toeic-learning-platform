
-- =====================================================
-- V6: VOCABULARY & LEARNING ANALYTICS
-- TOEIC Learning Platform
-- =====================================================

-- 1. VOCABULARY DECKS
CREATE TABLE vocabulary_decks (
    deck_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_vocabulary_decks_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
);

-- 2. VOCABULARIES
CREATE TABLE vocabularies (
    vocabulary_id UUID PRIMARY KEY,
    created_by UUID NOT NULL,
    word VARCHAR(150) NOT NULL,
    meaning TEXT NOT NULL,
    part_of_speech VARCHAR(40),
    pronunciation VARCHAR(255),
    pronunciation_audio_resource_id UUID,
    example TEXT,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_vocabularies_creator
        FOREIGN KEY (created_by)
        REFERENCES users(user_id)
);

-- 3. DECK VOCABULARIES
CREATE TABLE deck_vocabularies (
    deck_id UUID NOT NULL,
    vocabulary_id UUID NOT NULL,
    added_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (deck_id, vocabulary_id),

    CONSTRAINT fk_deck_vocabularies_deck
        FOREIGN KEY (deck_id)
        REFERENCES vocabulary_decks(deck_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_deck_vocabularies_vocabulary
        FOREIGN KEY (vocabulary_id)
        REFERENCES vocabularies(vocabulary_id)
);

-- 4. REVIEW STATES (SRS)
CREATE TABLE review_states (
    review_state_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    vocabulary_id UUID NOT NULL,
    repetition_count INTEGER NOT NULL DEFAULT 0,
    interval_days INTEGER NOT NULL DEFAULT 0,
    ease_factor DECIMAL(4,2) DEFAULT 2.50,
    last_reviewed_at TIMESTAMPTZ,
    next_review_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_review_states_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id),

    CONSTRAINT fk_review_states_vocabulary
        FOREIGN KEY (vocabulary_id)
        REFERENCES vocabularies(vocabulary_id),

    CONSTRAINT uq_review_state_user_vocabulary
        UNIQUE (user_id, vocabulary_id),

    CONSTRAINT chk_review_repetition
        CHECK (repetition_count >= 0),

    CONSTRAINT chk_review_interval
        CHECK (interval_days >= 0),

    CONSTRAINT chk_review_ease
        CHECK (ease_factor IS NULL OR ease_factor > 0)
);

-- 5. REVIEW HISTORY
CREATE TABLE review_history (
    review_history_id UUID PRIMARY KEY,
    review_state_id UUID NOT NULL,
    rating INTEGER NOT NULL,
    previous_interval INTEGER,
    new_interval INTEGER,
    reviewed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_review_history_state
        FOREIGN KEY (review_state_id)
        REFERENCES review_states(review_state_id),

    CONSTRAINT chk_review_rating
        CHECK (rating BETWEEN 1 AND 4),

    CONSTRAINT chk_review_previous_interval
        CHECK (
            previous_interval IS NULL
            OR previous_interval >= 0
        ),

    CONSTRAINT chk_review_new_interval
        CHECK (
            new_interval IS NULL
            OR new_interval >= 0
        )
);

-- 6. LEARNING PROFILES
CREATE TABLE learning_profiles (
    profile_id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    target_score INTEGER,
    estimated_score INTEGER,
    listening_level VARCHAR(30),
    reading_level VARCHAR(30),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_learning_profiles_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
);

-- 7. PART PERFORMANCE
CREATE TABLE part_performance (
    performance_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    part_id INTEGER NOT NULL,
    attempted_questions INTEGER NOT NULL DEFAULT 0,
    correct_questions INTEGER NOT NULL DEFAULT 0,
    accuracy DECIMAL(5,2),
    mastery_score DECIMAL(5,2),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_part_performance_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id),

    CONSTRAINT fk_part_performance_part
        FOREIGN KEY (part_id)
        REFERENCES toeic_parts(part_id),

    CONSTRAINT uq_part_performance_user_part
        UNIQUE (user_id, part_id),

    CONSTRAINT chk_part_performance_counts
        CHECK (
            attempted_questions >= 0
            AND correct_questions >= 0
            AND correct_questions <= attempted_questions
        ),

    CONSTRAINT chk_part_performance_accuracy
        CHECK (
            accuracy IS NULL
            OR accuracy BETWEEN 0 AND 100
        ),

    CONSTRAINT chk_part_performance_mastery
        CHECK (
            mastery_score IS NULL
            OR mastery_score BETWEEN 0 AND 100
        )
);

-- 8. RECOMMENDATIONS
CREATE TABLE recommendations (
    recommendation_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    recommendation_type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    source VARCHAR(50),
    priority INTEGER,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ,

    CONSTRAINT fk_recommendations_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
);

-- =====================================================
-- INDEXES
-- =====================================================

CREATE INDEX idx_vocabulary_decks_user
    ON vocabulary_decks(user_id);

CREATE INDEX idx_vocabularies_creator
    ON vocabularies(created_by);

CREATE INDEX idx_vocabularies_word
    ON vocabularies(word);

CREATE INDEX idx_deck_vocabularies_vocabulary
    ON deck_vocabularies(vocabulary_id);

CREATE INDEX idx_review_states_due
    ON review_states(user_id, next_review_at);

CREATE INDEX idx_review_history_state_date
    ON review_history(review_state_id, reviewed_at DESC);

CREATE INDEX idx_part_performance_part
    ON part_performance(part_id);

CREATE INDEX idx_recommendations_user_date
    ON recommendations(user_id, generated_at DESC);
