
-- =====================================================
-- V5: TEST & ATTEMPT
-- TOEIC Learning Platform
-- Requires: users, toeic_parts, question_versions,
--           question_choices (V2-V4)
-- =====================================================

-- 1. TESTS
CREATE TABLE tests (
    test_id UUID PRIMARY KEY,
    created_by UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    test_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_tests_creator
        FOREIGN KEY (created_by)
        REFERENCES users(user_id)
);

-- 2. TEST VERSIONS
CREATE TABLE test_versions (
    test_version_id UUID PRIMARY KEY,
    test_id UUID NOT NULL,
    blueprint_id UUID,
    version_number INTEGER NOT NULL,
    duration_seconds INTEGER,
    total_questions INTEGER,
    max_attempts INTEGER,
    status VARCHAR(20) NOT NULL,
    published_at TIMESTAMPTZ,
    retired_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_test_versions_test
        FOREIGN KEY (test_id)
        REFERENCES tests(test_id),

    CONSTRAINT uq_test_version_number
        UNIQUE (test_id, version_number)
);

-- 3. TEST QUESTIONS
CREATE TABLE test_questions (
    test_version_id UUID NOT NULL,
    question_version_id UUID NOT NULL,
    question_group_version_id UUID,
    question_order INTEGER NOT NULL,
    points DECIMAL(6,2),

    PRIMARY KEY (test_version_id, question_version_id),

    CONSTRAINT fk_test_questions_test_version
        FOREIGN KEY (test_version_id)
        REFERENCES test_versions(test_version_id),

    CONSTRAINT fk_test_questions_question_version
        FOREIGN KEY (question_version_id)
        REFERENCES question_versions(question_version_id),

    CONSTRAINT uq_test_question_order
        UNIQUE (test_version_id, question_order)
);

-- 4. ATTEMPTS
CREATE TABLE attempts (
    attempt_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    test_version_id UUID,
    attempt_type VARCHAR(30) NOT NULL,
    attempt_source_type VARCHAR(30) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    max_attempts_snapshot INTEGER,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    submitted_at TIMESTAMPTZ,
    duration_seconds INTEGER,
    total_questions INTEGER,
    correct_count INTEGER,
    listening_correct_count INTEGER,
    reading_correct_count INTEGER,
    listening_estimated_score INTEGER,
    reading_estimated_score INTEGER,
    total_estimated_score INTEGER,
    score_conversion_table_id UUID,
    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_attempts_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id),

    CONSTRAINT fk_attempts_test_version
        FOREIGN KEY (test_version_id)
        REFERENCES test_versions(test_version_id)
);

-- 5. ATTEMPT ANSWERS
CREATE TABLE attempt_answers (
    attempt_answer_id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL,
    question_version_id UUID NOT NULL,
    selected_choice_id UUID,
    is_correct BOOLEAN,
    answered_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_attempt_answers_attempt
        FOREIGN KEY (attempt_id)
        REFERENCES attempts(attempt_id),

    CONSTRAINT fk_attempt_answers_question_version
        FOREIGN KEY (question_version_id)
        REFERENCES question_versions(question_version_id),

    CONSTRAINT fk_attempt_answers_choice_version
        FOREIGN KEY (question_version_id, selected_choice_id)
        REFERENCES question_choices(
            question_version_id, choice_id
        ),

    CONSTRAINT uq_attempt_question
        UNIQUE (attempt_id, question_version_id)
);

-- 6. ATTEMPT PART RESULTS
CREATE TABLE attempt_part_results (
    attempt_part_result_id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL,
    part_id INTEGER NOT NULL,
    total_questions INTEGER NOT NULL,
    correct_count INTEGER NOT NULL,
    accuracy DECIMAL(5,2),

    CONSTRAINT fk_attempt_part_results_attempt
        FOREIGN KEY (attempt_id)
        REFERENCES attempts(attempt_id),

    CONSTRAINT fk_attempt_part_results_part
        FOREIGN KEY (part_id)
        REFERENCES toeic_parts(part_id),

    CONSTRAINT uq_attempt_part
        UNIQUE (attempt_id, part_id)
);

-- =====================================================
-- INDEXES
-- =====================================================

CREATE INDEX idx_tests_creator
    ON tests(created_by);

CREATE INDEX idx_test_versions_test
    ON test_versions(test_id);

CREATE INDEX idx_test_questions_question
    ON test_questions(question_version_id);

CREATE INDEX idx_attempts_user
    ON attempts(user_id);

CREATE INDEX idx_attempts_test_version
    ON attempts(test_version_id);

CREATE INDEX idx_attempts_status
    ON attempts(status);

CREATE INDEX idx_attempt_answers_question
    ON attempt_answers(question_version_id);

CREATE INDEX idx_attempt_part_results_part
    ON attempt_part_results(part_id);
