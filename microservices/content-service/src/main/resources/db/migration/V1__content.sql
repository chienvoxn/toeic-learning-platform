CREATE TABLE learning_resources (
 resource_id UUID PRIMARY KEY,
 owner_user_id UUID NOT NULL,
 title VARCHAR(240) NOT NULL,
 body TEXT NOT NULL,
 visibility VARCHAR(20) NOT NULL CHECK (visibility IN ('PRIVATE','PUBLIC')),
 version INTEGER NOT NULL DEFAULT 1,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_resource_owner ON learning_resources(owner_user_id);
