-- Compatibility V35: retain applied history, do not repeat existing grants.
DO $bridge$
BEGIN
    IF (SELECT count(*) FROM flyway_schema_history WHERE version='35' AND success AND
      ((script='V35__content_review_publication.sql' AND checksum=-949250551) OR
       (script='V35__ai_conversation_delete_cascade.sql' AND checksum=359517897))) <> 1 THEN
        RAISE EXCEPTION 'Unrecognized V35 lineage';
    END IF;
    IF EXISTS (SELECT 1 FROM flyway_schema_history WHERE version='35' AND script='V35__content_review_publication.sql') THEN
        IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='ai_conversations' AND column_name='deleted_at')
            OR to_regclass('public.idx_ai_conversations_active_user') IS NOT NULL THEN
            RAISE EXCEPTION 'Partial or unexpected conversation soft-delete feature';
        END IF;
ALTER TABLE ai_conversations ADD COLUMN deleted_at TIMESTAMPTZ;
CREATE INDEX idx_ai_conversations_active_user ON ai_conversations(user_id, updated_at DESC)
    WHERE deleted_at IS NULL;

    ELSE
        IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='ai_conversations' AND column_name='deleted_at'
            AND data_type='timestamp with time zone' AND is_nullable='YES' AND column_default IS NULL)
            OR to_regclass('public.idx_ai_conversations_active_user') IS NULL THEN
            RAISE EXCEPTION 'Conversation soft-delete invariant mismatch';
        END IF;
    END IF;
END $bridge$;
