CREATE TABLE attempts (
 attempt_id UUID PRIMARY KEY,
 user_id UUID NOT NULL,
 status VARCHAR(20) NOT NULL CHECK (status IN ('IN_PROGRESS','GRADED')),
 correct_count INTEGER,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 graded_at TIMESTAMPTZ
);
CREATE TABLE attempt_questions (
 attempt_id UUID NOT NULL REFERENCES attempts(attempt_id),
 question_id UUID NOT NULL,
 prompt TEXT NOT NULL,
 correct_choice VARCHAR(100) NOT NULL,
 selected_choice VARCHAR(100),
 PRIMARY KEY (attempt_id,question_id)
);
CREATE INDEX ix_attempt_user ON attempts(user_id);
