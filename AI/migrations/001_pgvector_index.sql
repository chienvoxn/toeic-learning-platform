-- Dedicated derived RAG index. Does not modify backend Flyway business tables.
CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE IF NOT EXISTS rag_index_config (
    id INTEGER PRIMARY KEY CHECK (id = 1), signature VARCHAR(500) NOT NULL
);
CREATE TABLE IF NOT EXISTS rag_documents (
    id VARCHAR(36) PRIMARY KEY, title VARCHAR(300) NOT NULL, source_url TEXT,
    chunk_count INTEGER NOT NULL, updated_at VARCHAR(40) NOT NULL
);
CREATE TABLE IF NOT EXISTS rag_chunks (
    id VARCHAR(36) PRIMARY KEY,
    document_id VARCHAR(36) NOT NULL REFERENCES rag_documents(id),
    content TEXT NOT NULL, page INTEGER,
    embedding vector(__DIMENSIONS__) NOT NULL, position INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_rag_chunks_document_id ON rag_chunks(document_id);
