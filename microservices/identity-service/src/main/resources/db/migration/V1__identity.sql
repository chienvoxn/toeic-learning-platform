CREATE TABLE identity_users (
 user_id UUID PRIMARY KEY,
 email VARCHAR(255) NOT NULL UNIQUE,
 display_name VARCHAR(200) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE identity_user_roles (
 user_id UUID NOT NULL REFERENCES identity_users(user_id),
 role_code VARCHAR(40) NOT NULL,
 PRIMARY KEY(user_id, role_code)
);
