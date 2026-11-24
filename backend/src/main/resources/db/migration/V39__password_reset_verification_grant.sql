-- Only a verified OTP can issue a short-lived, one-use password reset grant.
ALTER TABLE password_reset_otps
    ADD COLUMN reset_token_hash VARCHAR(255),
    ADD COLUMN reset_token_expires_at TIMESTAMPTZ;
