
-- =====================================================
-- V10: COMPLETE ERD V1.6
-- 14 missing tables + deferred foreign keys
-- PostgreSQL / Flyway
-- =====================================================

-- =====================================================
-- A. CONTENT VERSIONING
-- =====================================================

-- 1. LEARNING CONTENT VERSIONS
CREATE TABLE learning_content_versions (
    content_version_id UUID PRIMARY KEY,
    content_id UUID NOT NULL,
    version_number INTEGER NOT NULL,
    title VARCHAR(255) NOT NULL,
    summary TEXT,
    body TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    published_at TIMESTAMPTZ,
    archived_at TIMESTAMPTZ,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_lcv_content
        FOREIGN KEY (content_id)
        REFERENCES learning_contents(content_id),

    CONSTRAINT fk_lcv_creator
        FOREIGN KEY (created_by)
        REFERENCES users(user_id),

    CONSTRAINT uq_lcv_number
        UNIQUE (content_id, version_number),

    CONSTRAINT uq_lcv_content_version
        UNIQUE (content_id, content_version_id)
);

-- =====================================================
-- B. QUESTION GROUP VERSIONING
-- =====================================================

-- 2. QUESTION GROUP VERSIONS
CREATE TABLE question_group_versions (
    group_version_id UUID PRIMARY KEY,
    group_id UUID NOT NULL,
    version_number INTEGER NOT NULL,
    instruction TEXT,
    passage_text TEXT,
    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_qgv_group
        FOREIGN KEY (group_id)
        REFERENCES question_groups(group_id),

    CONSTRAINT uq_qgv_number
        UNIQUE (group_id, version_number)
);

-- 3. QUESTION GROUP VERSION RESOURCES
CREATE TABLE question_group_version_resources (
    group_version_id UUID NOT NULL,
    resource_id UUID NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 1,

    PRIMARY KEY (group_version_id, resource_id),

    CONSTRAINT fk_qgvr_version
        FOREIGN KEY (group_version_id)
        REFERENCES question_group_versions(group_version_id),

    CONSTRAINT fk_qgvr_resource
        FOREIGN KEY (resource_id)
        REFERENCES media_resources(resource_id)
);

-- =====================================================
-- C. TEST BLUEPRINTS & SNAPSHOTS
-- =====================================================

-- 4. TEST BLUEPRINTS
CREATE TABLE test_blueprints (
    blueprint_id UUID PRIMARY KEY,
    code VARCHAR(80) NOT NULL,
    version_number INTEGER NOT NULL,
    name VARCHAR(150) NOT NULL,
    blueprint_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    expected_total_questions INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT uq_test_blueprint_version
        UNIQUE (code, version_number)
);

-- 5. TEST BLUEPRINT ITEMS
CREATE TABLE test_blueprint_items (
    blueprint_item_id UUID PRIMARY KEY,
    blueprint_id UUID NOT NULL,
    part_id INTEGER NOT NULL,
    expected_question_count INTEGER NOT NULL,
    expected_group_count INTEGER,

    CONSTRAINT fk_tbi_blueprint
        FOREIGN KEY (blueprint_id)
        REFERENCES test_blueprints(blueprint_id),

    CONSTRAINT fk_tbi_part
        FOREIGN KEY (part_id)
        REFERENCES toeic_parts(part_id),

    CONSTRAINT uq_tbi_blueprint_part
        UNIQUE (blueprint_id, part_id)
);

-- 6. ATTEMPT QUESTION SNAPSHOTS
CREATE TABLE attempt_question_snapshots (
    snapshot_id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL,
    question_version_id UUID NOT NULL,
    question_group_version_id UUID,
    question_order INTEGER NOT NULL,
    points DECIMAL(6,2),
    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_aqs_attempt
        FOREIGN KEY (attempt_id)
        REFERENCES attempts(attempt_id),

    CONSTRAINT fk_aqs_question_group_version
        FOREIGN KEY (
            question_version_id,
            question_group_version_id
        )
        REFERENCES question_versions(
            question_version_id,
            group_version_id
        ),

    CONSTRAINT fk_aqs_question_version
        FOREIGN KEY (question_version_id)
        REFERENCES question_versions(question_version_id),

    CONSTRAINT uq_aqs_order
        UNIQUE (attempt_id, question_order),

    CONSTRAINT uq_aqs_question
        UNIQUE (attempt_id, question_version_id)
);

-- =====================================================
-- D. TOEIC SCORE CONVERSION
-- =====================================================

-- 7. SCORE CONVERSION TABLES
CREATE TABLE score_conversion_tables (
    score_conversion_table_id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    version_number INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    activated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

-- 8. SCORE CONVERSION RULES
CREATE TABLE score_conversion_rules (
    rule_id UUID PRIMARY KEY,
    score_conversion_table_id UUID NOT NULL,
    skill VARCHAR(20) NOT NULL,
    correct_count INTEGER NOT NULL,
    estimated_score INTEGER NOT NULL,

    CONSTRAINT fk_scr_table
        FOREIGN KEY (score_conversion_table_id)
        REFERENCES score_conversion_tables(
            score_conversion_table_id
        ),

    CONSTRAINT uq_scr_skill_count
        UNIQUE (
            score_conversion_table_id,
            skill,
            correct_count
        )
);

-- =====================================================
-- E. TAGS & ERROR ANALYTICS
-- =====================================================

-- 9. TAGS
CREATE TABLE tags (
    tag_id UUID PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- 10. QUESTION TAGS
CREATE TABLE question_tags (
    question_id UUID NOT NULL,
    tag_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,

    PRIMARY KEY (question_id, tag_id),

    CONSTRAINT fk_question_tags_question
        FOREIGN KEY (question_id)
        REFERENCES questions(question_id),

    CONSTRAINT fk_question_tags_tag
        FOREIGN KEY (tag_id)
        REFERENCES tags(tag_id)
);

-- 11. ERROR CATEGORIES
CREATE TABLE error_categories (
    error_category_id UUID PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- 12. QUESTION ERROR CATEGORIES
CREATE TABLE question_error_categories (
    question_id UUID NOT NULL,
    error_category_id UUID NOT NULL,

    PRIMARY KEY (question_id, error_category_id),

    CONSTRAINT fk_qec_question
        FOREIGN KEY (question_id)
        REFERENCES questions(question_id),

    CONSTRAINT fk_qec_category
        FOREIGN KEY (error_category_id)
        REFERENCES error_categories(error_category_id)
);

-- 13. USER ERROR STATISTICS
CREATE TABLE user_error_statistics (
    user_id UUID NOT NULL,
    error_category_id UUID NOT NULL,
    attempted_count INTEGER NOT NULL,
    incorrect_count INTEGER NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    PRIMARY KEY (user_id, error_category_id),

    CONSTRAINT fk_ues_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id),

    CONSTRAINT fk_ues_category
        FOREIGN KEY (error_category_id)
        REFERENCES error_categories(error_category_id),

    CONSTRAINT chk_ues_counts
        CHECK (
            attempted_count >= 0
            AND incorrect_count >= 0
            AND incorrect_count <= attempted_count
        )
);

-- =====================================================
-- F. AI REQUEST LOGGING
-- =====================================================

-- 14. AI REQUEST LOGS
CREATE TABLE ai_request_logs (
    request_id UUID PRIMARY KEY,
    conversation_id UUID,
    user_id UUID,
    provider VARCHAR(100) NOT NULL,
    model VARCHAR(150) NOT NULL,
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    total_tokens INTEGER,
    latency_ms INTEGER,
    estimated_cost DECIMAL(16,8),
    cost_currency VARCHAR(3),
    status VARCHAR(30) NOT NULL,
    error_code VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_arl_conversation
        FOREIGN KEY (conversation_id)
        REFERENCES ai_conversations(conversation_id),

    CONSTRAINT fk_arl_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
);

-- =====================================================
-- G. COMPLETE DEFERRED FOREIGN KEYS
-- =====================================================

-- Learning content -> published version
ALTER TABLE learning_contents
ADD CONSTRAINT fk_lc_published_version
FOREIGN KEY (
    content_id,
    current_published_version_id
)
REFERENCES learning_content_versions(
    content_id,
    content_version_id
);

-- Learning progress -> completed version
ALTER TABLE learning_progress
ADD CONSTRAINT fk_lp_completed_version
FOREIGN KEY (completed_content_version_id)
REFERENCES learning_content_versions(content_version_id);

-- Question version -> group version
ALTER TABLE question_versions
ADD CONSTRAINT fk_qv_group_version
FOREIGN KEY (group_version_id)
REFERENCES question_group_versions(group_version_id);

-- Test version -> blueprint
ALTER TABLE test_versions
ADD CONSTRAINT fk_tv_blueprint
FOREIGN KEY (blueprint_id)
REFERENCES test_blueprints(blueprint_id);

-- Test question -> matching question/group version
ALTER TABLE test_questions
ADD CONSTRAINT fk_tq_question_group_version
FOREIGN KEY (
    question_version_id,
    question_group_version_id
)
REFERENCES question_versions(
    question_version_id,
    group_version_id
);

-- Attempt -> score conversion table
ALTER TABLE attempts
ADD CONSTRAINT fk_attempt_score_conversion
FOREIGN KEY (score_conversion_table_id)
REFERENCES score_conversion_tables(
    score_conversion_table_id
);

-- Vocabulary -> pronunciation audio
ALTER TABLE vocabularies
ADD CONSTRAINT fk_vocab_pronunciation_audio
FOREIGN KEY (pronunciation_audio_resource_id)
REFERENCES media_resources(resource_id);

-- Knowledge document -> learning content version
ALTER TABLE knowledge_documents
ADD CONSTRAINT fk_kd_content_version
FOREIGN KEY (learning_content_version_id)
REFERENCES learning_content_versions(content_version_id);

-- Knowledge chunk -> learning content version
ALTER TABLE knowledge_chunks
ADD CONSTRAINT fk_kc_content_version
FOREIGN KEY (learning_content_version_id)
REFERENCES learning_content_versions(content_version_id);

-- =====================================================
-- H. INDEXES
-- =====================================================

CREATE INDEX idx_lcv_content
    ON learning_content_versions(content_id);

CREATE INDEX idx_qgv_group
    ON question_group_versions(group_id);

CREATE INDEX idx_qgvr_resource
    ON question_group_version_resources(resource_id);

CREATE INDEX idx_tbi_part
    ON test_blueprint_items(part_id);

CREATE INDEX idx_aqs_question
    ON attempt_question_snapshots(question_version_id);

CREATE INDEX idx_scr_table
    ON score_conversion_rules(score_conversion_table_id);

CREATE INDEX idx_question_tags_tag
    ON question_tags(tag_id);

CREATE INDEX idx_qec_category
    ON question_error_categories(error_category_id);

CREATE INDEX idx_ues_category
    ON user_error_statistics(error_category_id);

CREATE INDEX idx_arl_user_created
    ON ai_request_logs(user_id, created_at DESC);

CREATE INDEX idx_arl_conversation
    ON ai_request_logs(conversation_id);
