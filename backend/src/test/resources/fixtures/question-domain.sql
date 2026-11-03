-- Test references only, not a real Part 1-7 question bank. Tests roll back this fixture.
INSERT INTO users (user_id, email, password_hash, full_name, status, created_at, updated_at)
VALUES ('51000000-0000-0000-0000-000000000001', 'q01-fixture@example.invalid',
        'unused-fixture-value', 'Q01 fixture', 'PENDING', now(), now());
INSERT INTO toeic_parts (part_id, part_number, skill, name)
VALUES (910001, 1, 'LISTENING', 'Q01 fixture part');
INSERT INTO topics (topic_id, name, description)
VALUES (910001, 'Q01 fixture topic', 'Only test data');
INSERT INTO question_types (question_type_id, code, name, description)
VALUES (910001, 'Q01_FIXTURE', 'Q01 fixture type', 'No choice count policy implied');
INSERT INTO media_resources (resource_id, resource_type, storage_url, mime_type, file_size, duration_seconds, transcript, created_at)
VALUES
 ('62000000-0000-0000-0000-000000000001', 'AUDIO', 'internal://fixture/one', 'audio/mpeg', 1200, 30, 'HIDDEN_TRANSCRIPT', now()),
 ('62000000-0000-0000-0000-000000000002', 'IMAGE', 'internal://fixture/two', 'image/png', 500, NULL, NULL, now()),
 ('62000000-0000-0000-0000-000000000003', 'AUDIO', 'internal://fixture/three', NULL, NULL, NULL, 'HIDDEN_TRANSCRIPT_2', now());
