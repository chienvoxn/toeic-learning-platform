
-- =====================================================
-- V8: LISTENING PRACTICE
-- TOEIC Learning Platform
-- =====================================================

-- 1. DICTATION EXERCISES
CREATE TABLE dictation_exercises (
    dictation_id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    instruction TEXT,
    audio_resource_id UUID NOT NULL,
    transcript TEXT NOT NULL,
    difficulty VARCHAR(20),
    status VARCHAR(20) NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_dictation_exercises_audio
        FOREIGN KEY (audio_resource_id)
        REFERENCES media_resources(resource_id),

    CONSTRAINT fk_dictation_exercises_creator
        FOREIGN KEY (created_by)
        REFERENCES users(user_id)
);

-- 2. DICTATION ATTEMPTS
CREATE TABLE dictation_attempts (
    attempt_id UUID PRIMARY KEY,
    dictation_id UUID NOT NULL,
    user_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    score DECIMAL(5,2),
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_dictation_attempts_exercise
        FOREIGN KEY (dictation_id)
        REFERENCES dictation_exercises(dictation_id),

    CONSTRAINT fk_dictation_attempts_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id),

    CONSTRAINT chk_dictation_score
        CHECK (
            score IS NULL
            OR score BETWEEN 0 AND 100
        )
);

-- 3. DICTATION ANSWERS
CREATE TABLE dictation_answers (
    answer_id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL,
    user_text TEXT NOT NULL,
    accuracy DECIMAL(5,2),
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_dictation_answers_attempt
        FOREIGN KEY (attempt_id)
        REFERENCES dictation_attempts(attempt_id),

    CONSTRAINT chk_dictation_accuracy
        CHECK (
            accuracy IS NULL
            OR accuracy BETWEEN 0 AND 100
        )
);

-- 4. SHADOWING EXERCISES
CREATE TABLE shadowing_exercises (
    shadowing_id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    instruction TEXT,
    audio_resource_id UUID NOT NULL,
    transcript TEXT NOT NULL,
    difficulty VARCHAR(20),
    status VARCHAR(20) NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_shadowing_exercises_audio
        FOREIGN KEY (audio_resource_id)
        REFERENCES media_resources(resource_id),

    CONSTRAINT fk_shadowing_exercises_creator
        FOREIGN KEY (created_by)
        REFERENCES users(user_id)
);

-- 5. SHADOWING ATTEMPTS
CREATE TABLE shadowing_attempts (
    attempt_id UUID PRIMARY KEY,
    shadowing_id UUID NOT NULL,
    user_id UUID NOT NULL,
    recording_resource_id UUID,
    status VARCHAR(20) NOT NULL,
    pronunciation_score DECIMAL(5,2),
    fluency_score DECIMAL(5,2),
    overall_score DECIMAL(5,2),
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_shadowing_attempts_exercise
        FOREIGN KEY (shadowing_id)
        REFERENCES shadowing_exercises(shadowing_id),

    CONSTRAINT fk_shadowing_attempts_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id),

    CONSTRAINT fk_shadowing_attempts_recording
        FOREIGN KEY (recording_resource_id)
        REFERENCES media_resources(resource_id),

    CONSTRAINT chk_shadowing_scores
        CHECK (
            (pronunciation_score IS NULL OR
             pronunciation_score BETWEEN 0 AND 100)
            AND
            (fluency_score IS NULL OR
             fluency_score BETWEEN 0 AND 100)
            AND
            (overall_score IS NULL OR
             overall_score BETWEEN 0 AND 100)
        )
);

-- 6. SHADOWING FEEDBACK
CREATE TABLE shadowing_feedback (
    feedback_id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL,
    feedback_type VARCHAR(30),
    feedback_text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_shadowing_feedback_attempt
        FOREIGN KEY (attempt_id)
        REFERENCES shadowing_attempts(attempt_id)
);

-- =====================================================
-- INDEXES
-- =====================================================

CREATE INDEX idx_dictation_exercises_creator
    ON dictation_exercises(created_by);

CREATE INDEX idx_dictation_exercises_audio
    ON dictation_exercises(audio_resource_id);

CREATE INDEX idx_dictation_attempts_user
    ON dictation_attempts(user_id);

CREATE INDEX idx_dictation_attempts_exercise
    ON dictation_attempts(dictation_id);

CREATE INDEX idx_dictation_answers_attempt
    ON dictation_answers(attempt_id);

CREATE INDEX idx_shadowing_exercises_creator
    ON shadowing_exercises(created_by);

CREATE INDEX idx_shadowing_exercises_audio
    ON shadowing_exercises(audio_resource_id);

CREATE INDEX idx_shadowing_attempts_user
    ON shadowing_attempts(user_id);

CREATE INDEX idx_shadowing_attempts_exercise
    ON shadowing_attempts(shadowing_id);

CREATE INDEX idx_shadowing_attempts_recording
    ON shadowing_attempts(recording_resource_id);

CREATE INDEX idx_shadowing_feedback_attempt
    ON shadowing_feedback(attempt_id);
