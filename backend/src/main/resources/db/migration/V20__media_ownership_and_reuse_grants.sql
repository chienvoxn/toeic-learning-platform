-- CONTENT-03 product decision: no ownership inference or automatic grants for legacy media.
ALTER TABLE media_resources ADD COLUMN owner_id UUID REFERENCES users(user_id);
CREATE INDEX idx_media_owner ON media_resources(owner_id);

CREATE TABLE media_reuse_grants (
    media_id UUID NOT NULL REFERENCES media_resources(resource_id),
    user_id UUID NOT NULL REFERENCES users(user_id),
    granted_by UUID NOT NULL REFERENCES users(user_id),
    granted_at TIMESTAMPTZ NOT NULL,
    revoked_by UUID REFERENCES users(user_id),
    revoked_at TIMESTAMPTZ,
    PRIMARY KEY (media_id, user_id),
    CONSTRAINT chk_media_grant_revocation CHECK ((revoked_at IS NULL) = (revoked_by IS NULL)),
    CONSTRAINT chk_media_grant_time CHECK (revoked_at IS NULL OR revoked_at >= granted_at)
);
CREATE INDEX idx_media_reuse_user ON media_reuse_grants(user_id);
