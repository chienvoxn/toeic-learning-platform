-- Default roles
INSERT INTO roles (name, description)
VALUES
    ('LEARNER', 'Hoc vien'),
    ('TEACHER', 'Giao vien'),
    ('ADMIN', 'Quan tri vien')
ON CONFLICT (name) DO NOTHING;

-- Store hashed OTPs; never store plaintext OTPs.
CREATE TABLE email_verification_otps (
    otp_id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(user_id),
    otp_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_email_otp_attempts
        CHECK (failed_attempts >= 0),

    CONSTRAINT chk_email_otp_expiry
        CHECK (expires_at > created_at)
);

CREATE INDEX idx_email_verification_otps_user_created
    ON email_verification_otps (user_id, created_at DESC);