
-- =====================================================
-- V7: AI TUTOR & RAG
-- TOEIC Learning Platform
-- Embedding stored temporarily as REAL[]
-- =====================================================

-- 1. AI CONVERSATIONS
CREATE TABLE ai_conversations (
    conversation_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    title VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_ai_conversations_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
);

-- 2. AI MESSAGES
CREATE TABLE ai_messages (
    message_id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_ai_messages_conversation
        FOREIGN KEY (conversation_id)
        REFERENCES ai_conversations(conversation_id)
        ON DELETE CASCADE
);

-- 3. KNOWLEDGE DOCUMENTS
CREATE TABLE knowledge_documents (
    document_id UUID PRIMARY KEY,
    learning_content_version_id UUID,
    title VARCHAR(255) NOT NULL,
    source_type VARCHAR(50),
    source_url TEXT,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. KNOWLEDGE CHUNKS
CREATE TABLE knowledge_chunks (
    chunk_id UUID PRIMARY KEY,
    document_id UUID NOT NULL,
    learning_content_version_id UUID,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    embedding REAL[],
    embedding_model VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_knowledge_chunks_document
        FOREIGN KEY (document_id)
        REFERENCES knowledge_documents(document_id)
        ON DELETE CASCADE,

    CONSTRAINT uq_knowledge_chunk_index
        UNIQUE (document_id, chunk_index),

    CONSTRAINT chk_embedding_dimensions
        CHECK (
            embedding IS NULL
            OR array_length(embedding, 1) = 768
        )
);

-- 5. AI MESSAGE SOURCES
CREATE TABLE ai_message_sources (
    message_id UUID NOT NULL,
    chunk_id UUID NOT NULL,
    relevance_score DECIMAL(6,5),

    PRIMARY KEY (message_id, chunk_id),

    CONSTRAINT fk_ai_sources_message
        FOREIGN KEY (message_id)
        REFERENCES ai_messages(message_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_ai_sources_chunk
        FOREIGN KEY (chunk_id)
        REFERENCES knowledge_chunks(chunk_id)
);

-- 6. AI CONFIGS
CREATE TABLE ai_configs (
    config_id UUID PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT NOT NULL,
    description TEXT,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_ai_configs_updated_by
        FOREIGN KEY (updated_by)
        REFERENCES users(user_id)
);

-- =====================================================
-- INDEXES
-- =====================================================

CREATE INDEX idx_ai_conversations_user
    ON ai_conversations(user_id);

CREATE INDEX idx_ai_messages_conversation_date
    ON ai_messages(conversation_id, created_at);

CREATE INDEX idx_knowledge_chunks_document
    ON knowledge_chunks(document_id);

CREATE INDEX idx_ai_sources_chunk
    ON ai_message_sources(chunk_id);

CREATE INDEX idx_knowledge_documents_status
    ON knowledge_documents(status);
