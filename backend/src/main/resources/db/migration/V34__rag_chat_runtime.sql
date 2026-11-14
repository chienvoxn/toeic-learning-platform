-- Business conversation data stays in Spring/Flyway; Python owns only its derived index.
CREATE TABLE ai_indexed_versions (
    content_version_id UUID PRIMARY KEY REFERENCES learning_content_versions(content_version_id),
    document_id UUID NOT NULL UNIQUE,
    indexed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE ai_chat_requests (
    request_id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES ai_conversations(conversation_id),
    user_id UUID NOT NULL REFERENCES users(user_id),
    question TEXT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','COMPLETED','FAILED')),
    response_json TEXT,
    error_code VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ
);
CREATE INDEX idx_ai_chat_requests_user_time ON ai_chat_requests(user_id,created_at DESC);
CREATE TABLE ai_citation_snapshots (
    message_id UUID PRIMARY KEY REFERENCES ai_messages(message_id) ON DELETE CASCADE,
    response_json TEXT NOT NULL
);
INSERT INTO permissions(code,description) VALUES
 ('ai:chat:self','Hoi dap RAG va quan ly hoi thoai ca nhan'),
 ('ai:knowledge:manage','Dong bo hoc lieu published vao RAG');
INSERT INTO role_permissions(role_id,permission_id)
 SELECT r.role_id,p.permission_id FROM roles r CROSS JOIN permissions p
 WHERE (p.code='ai:chat:self' AND r.name IN ('LEARNER','TEACHER','ADMIN'))
 OR (p.code='ai:knowledge:manage' AND r.name='ADMIN');
