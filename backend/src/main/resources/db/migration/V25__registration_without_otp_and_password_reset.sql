-- New policy: registration is active immediately, without claiming email ownership.
-- Preserve blocked/disabled accounts and historical email-verification records.
UPDATE users SET status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP
WHERE status = 'PENDING' AND password_hash IS NOT NULL;

CREATE TABLE password_reset_otps (
    otp_id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(user_id),
    otp_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_password_reset_attempts CHECK (failed_attempts >= 0),
    CONSTRAINT chk_password_reset_expiry CHECK (expires_at > created_at)
);
CREATE INDEX idx_password_reset_user_created ON password_reset_otps(user_id, created_at DESC);
