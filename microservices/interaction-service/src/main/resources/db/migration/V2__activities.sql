CREATE TABLE learning_activities(event_id UUID PRIMARY KEY,user_id UUID NOT NULL,attempt_id UUID NOT NULL,kind VARCHAR(50) NOT NULL,recorded_at TIMESTAMPTZ NOT NULL);
CREATE INDEX ix_activity_user ON learning_activities(user_id,recorded_at DESC);
