CREATE TABLE identity_roles (code VARCHAR(40) PRIMARY KEY, description VARCHAR(200) NOT NULL);
CREATE TABLE identity_permissions (code VARCHAR(80) PRIMARY KEY, description VARCHAR(200) NOT NULL);
CREATE TABLE identity_role_permissions (role_code VARCHAR(40) NOT NULL REFERENCES identity_roles(code),permission_code VARCHAR(80) NOT NULL REFERENCES identity_permissions(code),PRIMARY KEY (role_code,permission_code));
INSERT INTO identity_roles VALUES ('LEARNER','Learner access'),('TEACHER','Teacher authoring access'),('ADMIN','System administrator');
INSERT INTO identity_permissions VALUES ('content:read','Read quizzes'),('content:write','Author quizzes'),('assessment:submit','Start and grade attempt'),('interaction:write','Write reviews'),('membership:read','Read own entitlements'),('rbac:manage','Manage user roles');
INSERT INTO identity_role_permissions VALUES
('LEARNER','content:read'),('LEARNER','assessment:submit'),('LEARNER','interaction:write'),('LEARNER','membership:read'),
('TEACHER','content:read'),('TEACHER','content:write'),('TEACHER','assessment:submit'),('TEACHER','interaction:write'),('TEACHER','membership:read'),
('ADMIN','content:read'),('ADMIN','content:write'),('ADMIN','assessment:submit'),('ADMIN','interaction:write'),('ADMIN','membership:read'),('ADMIN','rbac:manage');
ALTER TABLE identity_user_roles ADD CONSTRAINT fk_identity_user_role_catalog FOREIGN KEY (role_code) REFERENCES identity_roles(code);
