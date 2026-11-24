CREATE TABLE vocabulary_review_requests (
 user_id UUID NOT NULL REFERENCES users(user_id),
 request_key UUID NOT NULL,
 vocabulary_id UUID NOT NULL REFERENCES vocabularies(vocabulary_id),
 rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 4),
 response JSONB NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 PRIMARY KEY (user_id, request_key)
);
