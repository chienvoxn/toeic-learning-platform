-- EXAM-03: durable retry scheduling. Never change a pinned attempt deadline/history.
CREATE TABLE attempt_expiry_retries (
    attempt_id UUID PRIMARY KEY REFERENCES attempts(attempt_id) ON DELETE CASCADE,
    failure_count INTEGER NOT NULL CHECK(failure_count BETWEEN 1 AND 31),
    next_retry_at TIMESTAMPTZ NOT NULL,
    last_failed_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_full_mock_expiry_pending ON attempts(expires_at,attempt_id)
    WHERE attempt_source_type='TEST_VERSION' AND attempt_type='FULL_MOCK'
      AND status='IN_PROGRESS' AND test_blueprint_snapshot IS NOT NULL AND test_version_id IS NOT NULL;
