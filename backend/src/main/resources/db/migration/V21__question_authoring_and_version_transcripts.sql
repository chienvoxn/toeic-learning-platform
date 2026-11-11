-- Q-03 product decisions. Legacy groups remain unowned; no ownership/grant/transcript backfill.
ALTER TABLE question_groups ADD COLUMN created_by UUID REFERENCES users(user_id);
CREATE INDEX idx_question_groups_creator ON question_groups(created_by);
ALTER TABLE question_resources ADD COLUMN transcript TEXT;
ALTER TABLE question_group_version_resources ADD COLUMN transcript TEXT;

INSERT INTO question_types(code,name,description)
VALUES ('SINGLE_CHOICE','Single choice','One correct choice; Q-03 validates labels by TOEIC Part')
ON CONFLICT (code) DO NOTHING;
INSERT INTO permissions(code,description) VALUES
 ('question:manage:self','Quan ly cau hoi va nhom cua chinh minh'),
 ('question:manage:any','Quan ly moi cau hoi va nhom')
ON CONFLICT (code) DO NOTHING;
INSERT INTO role_permissions(role_id,permission_id)
SELECT r.role_id,p.permission_id FROM roles r CROSS JOIN permissions p
WHERE (r.name IN ('TEACHER','ADMIN') AND p.code='question:manage:self')
   OR (r.name='ADMIN' AND p.code='question:manage:any')
ON CONFLICT (role_id,permission_id) DO NOTHING;
