-- Exam drafts are edited optimistically; publication materializes existing question-bank versions.
ALTER TABLE test_versions ADD COLUMN draft_document JSONB;
ALTER TABLE test_versions ADD COLUMN revision INTEGER NOT NULL DEFAULT 0;
ALTER TABLE test_versions ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT';
ALTER TABLE test_versions ADD COLUMN reviewed_by UUID REFERENCES users(user_id);
ALTER TABLE test_versions ADD COLUMN review_note TEXT;
ALTER TABLE test_versions ADD COLUMN title VARCHAR(255);
ALTER TABLE test_versions ADD COLUMN description TEXT;
ALTER TABLE test_versions ADD COLUMN test_type VARCHAR(30);
ALTER TABLE test_versions ADD COLUMN listening_seconds INTEGER;
ALTER TABLE test_versions ADD COLUMN conversion_id UUID REFERENCES score_conversion_tables(score_conversion_table_id);
ALTER TABLE attempts ADD COLUMN start_key UUID;
ALTER TABLE attempts ADD COLUMN result_document JSONB;
ALTER TABLE attempts ADD COLUMN listening_ends_at TIMESTAMPTZ;
CREATE UNIQUE INDEX uq_attempt_start_key ON attempts(user_id,start_key) WHERE start_key IS NOT NULL;
ALTER TABLE attempt_answers ADD COLUMN client_sequence BIGINT NOT NULL DEFAULT 0;
CREATE TABLE media_uploads (
 resource_id UUID PRIMARY KEY REFERENCES media_resources(resource_id),
 data BYTEA NOT NULL,
 CONSTRAINT chk_upload_size CHECK (octet_length(data) BETWEEN 1 AND 20971520)
);
CREATE INDEX idx_attempt_expiry ON attempts(expires_at) WHERE status='IN_PROGRESS';
INSERT INTO permissions(code,description) VALUES
 ('test:manage:self','Soan va gui duyet de cua minh'),
 ('test:manage:any','Quan ly tat ca de'),
 ('test:publish','Duyet va xuat ban de'),
 ('test:take','Lam de va xem ket qua cua minh'),
 ('media:upload','Tai audio va anh cho de thi'),
 ('score_conversion:manage','Quan ly bang diem uoc tinh')
ON CONFLICT(code) DO NOTHING;
INSERT INTO role_permissions(role_id,permission_id)
SELECT r.role_id,p.permission_id FROM roles r CROSS JOIN permissions p
WHERE (r.name IN ('TEACHER','ADMIN') AND p.code IN ('test:manage:self','media:upload'))
 OR (r.name='ADMIN' AND p.code IN ('test:manage:any','test:publish','score_conversion:manage'))
 OR (r.name IN ('LEARNER','TEACHER','ADMIN') AND p.code='test:take')
ON CONFLICT DO NOTHING;
INSERT INTO toeic_parts(part_number,skill,name) VALUES
 (1,'LISTENING','Photographs'),(2,'LISTENING','Question-Response'),
 (3,'LISTENING','Conversations'),(4,'LISTENING','Talks'),
 (5,'READING','Incomplete Sentences'),(6,'READING','Text Completion'),(7,'READING','Reading Comprehension')
ON CONFLICT(part_number) DO NOTHING;
