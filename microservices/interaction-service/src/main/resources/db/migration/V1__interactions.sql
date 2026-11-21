CREATE TABLE content_reviews (id UUID PRIMARY KEY, user_id UUID NOT NULL, content_id UUID NOT NULL, stars INTEGER NOT NULL CHECK(stars BETWEEN 1 AND 5), comment VARCHAR(1000), created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, CONSTRAINT uq_user_content_review UNIQUE(user_id,content_id));
CREATE INDEX idx_content_reviews_content ON content_reviews(content_id,created_at DESC);
-- user_id/content_id are opaque UUID references; NO cross-database foreign keys.
