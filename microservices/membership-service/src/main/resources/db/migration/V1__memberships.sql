CREATE TABLE membership_plans (code VARCHAR(32) PRIMARY KEY, display_name VARCHAR(100) NOT NULL, max_practice_per_day INTEGER NOT NULL CHECK(max_practice_per_day >= 0));
INSERT INTO membership_plans(code,display_name,max_practice_per_day) VALUES ('FREE','Free',10),('PRO','Pro',1000);
CREATE TABLE user_memberships (id UUID PRIMARY KEY, user_id UUID NOT NULL UNIQUE, plan_code VARCHAR(32) NOT NULL REFERENCES membership_plans(code), status VARCHAR(20) NOT NULL CHECK(status IN ('ACTIVE','CANCELED')), updated_at TIMESTAMPTZ NOT NULL);
CREATE INDEX idx_memberships_plan ON user_memberships(plan_code);
