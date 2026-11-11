-- Google identities are keyed by Google's immutable subject, not by email.
-- Existing password accounts and OTP flows are unchanged.
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

CREATE TABLE google_identities (
    google_subject VARCHAR(255) PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(user_id),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE google_login_challenges (
    challenge_id UUID PRIMARY KEY,
    nonce_hash VARCHAR(64) NOT NULL,
    binding_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    CONSTRAINT chk_google_challenge_expiry CHECK (expires_at > created_at),
    CONSTRAINT chk_google_challenge_consumed CHECK (consumed_at IS NULL OR consumed_at >= created_at)
);
CREATE INDEX idx_google_challenges_expiry ON google_login_challenges(expires_at);
