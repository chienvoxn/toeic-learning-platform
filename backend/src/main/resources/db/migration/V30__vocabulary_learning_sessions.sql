-- User-owned active learning-round snapshot; SRS remains in review_states/review_history.
CREATE TABLE vocabulary_learning_sessions (
  user_id UUID NOT NULL REFERENCES users(user_id),
  deck_id UUID NOT NULL REFERENCES vocabulary_decks(deck_id) ON DELETE CASCADE,
  session_data JSONB NOT NULL,
  revision BIGINT NOT NULL DEFAULT 1,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (user_id, deck_id),
  CONSTRAINT chk_learning_session_object CHECK (jsonb_typeof(session_data) = 'object')
);
