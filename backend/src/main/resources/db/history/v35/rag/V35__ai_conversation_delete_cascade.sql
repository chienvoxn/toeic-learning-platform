ALTER TABLE ai_conversations ADD COLUMN deleted_at TIMESTAMPTZ;
CREATE INDEX idx_ai_conversations_active_user ON ai_conversations(user_id, updated_at DESC)
    WHERE deleted_at IS NULL;
