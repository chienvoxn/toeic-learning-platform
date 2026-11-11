-- Reproducible read/publication fixtures only, not a real TOEIC bank.
INSERT INTO users (user_id, email, password_hash, full_name, status, email_verified_at, created_at, updated_at)
VALUES ('63000000-0000-0000-0000-000000000001', 'q02-fixture@example.invalid', 'replaced-by-test-setup',
        'Q02 fixture', 'ACTIVE', now(), now(), now());
INSERT INTO user_roles (user_id, role_id)
SELECT '63000000-0000-0000-0000-000000000001', role_id FROM roles WHERE name = 'TEACHER';
INSERT INTO toeic_parts (part_id, part_number, skill, name) VALUES
    (930001, 1, 'LISTENING', 'Q02 fixture part one'), (930002, 7, 'READING', 'Q02 fixture part seven') ON CONFLICT (part_number) DO NOTHING;
INSERT INTO topics (topic_id, name) VALUES (930001, 'Q02 topic one'), (930002, 'Q02 topic two');
INSERT INTO question_types (question_type_id, code, name) VALUES (930001, 'Q02_FIXTURE', 'Q02 fixture type');

INSERT INTO question_groups (group_id, group_type, status, created_at, updated_at)
SELECT ('74000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid, 'PASSAGE',
       CASE n WHEN 12 THEN 'DRAFT' WHEN 13 THEN 'ARCHIVED' ELSE 'PUBLISHED' END, now(), now()
FROM generate_series(2, 24) n WHERE n = 2 OR n >= 12;
INSERT INTO question_group_versions
    (group_version_id, group_id, version_number, instruction, passage_text, status, published_at, archived_at, created_at)
SELECT ('75000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
       ('74000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid, 1,
       CASE WHEN n = 2 THEN 'Pinned instruction' ELSE 'HIDDEN_GROUP_INSTRUCTION' END,
       CASE WHEN n = 2 THEN 'Pinned passage' ELSE 'HIDDEN_GROUP_PASSAGE' END,
       CASE n WHEN 14 THEN 'DRAFT' WHEN 15 THEN 'ARCHIVED' ELSE 'PUBLISHED' END,
       CASE n WHEN 16 THEN timestamptz '2999-01-01 00:00:00+00' WHEN 17 THEN NULL
              ELSE timestamptz '2020-01-01 00:00:00+00' END,
       CASE WHEN n IN (15, 18) THEN now() ELSE NULL END, now()
FROM generate_series(2, 24) n WHERE n = 2 OR n >= 12;
UPDATE question_groups g SET current_published_version_id = v.group_version_id
FROM question_group_versions v WHERE v.group_id = g.group_id AND g.group_type = 'PASSAGE'
  AND g.group_id BETWEEN '74000000-0000-0000-0000-000000000002' AND '74000000-0000-0000-0000-000000000024'
  AND g.group_id <> '74000000-0000-0000-0000-000000000019';

INSERT INTO questions (question_id, group_id, part_id, topic_id, question_type_id, created_by, status, created_at, updated_at)
SELECT ('73000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
       CASE WHEN n = 2 OR n BETWEEN 12 AND 23 AND n <> 22
            THEN ('74000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid ELSE NULL END,
       (SELECT part_id FROM toeic_parts WHERE part_number=CASE WHEN n = 2 THEN 7 ELSE 1 END),
       CASE n WHEN 2 THEN 930002 WHEN 3 THEN NULL ELSE 930001 END,
       930001, '63000000-0000-0000-0000-000000000001'::uuid,
       CASE n WHEN 4 THEN 'DRAFT' WHEN 5 THEN 'ARCHIVED' ELSE 'PUBLISHED' END, now(), now()
FROM generate_series(1, 25) n;
INSERT INTO question_versions
    (question_version_id, question_id, group_version_id, version_number, question_text, status,
     explanation, difficulty, published_at, archived_at, created_at)
SELECT ('76000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
       ('73000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
       CASE WHEN n = 23 THEN '75000000-0000-0000-0000-000000000024'::uuid
            WHEN n = 2 OR n BETWEEN 12 AND 22 AND n <> 21
            THEN ('75000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid ELSE NULL END,
       1, CASE n WHEN 1 THEN 'Alpha prompt' WHEN 2 THEN 'Beta group prompt' WHEN 3 THEN 'Gamma no topic'
                 ELSE 'HIDDEN_QUESTION_TEXT' END,
       CASE n WHEN 6 THEN 'DRAFT' WHEN 7 THEN 'ARCHIVED' WHEN 24 THEN 'IN_REVIEW' WHEN 25 THEN 'APPROVED'
              ELSE 'PUBLISHED' END,
       'HIDDEN_EXPLANATION', CASE n WHEN 2 THEN 'HARD' WHEN 3 THEN NULL ELSE 'EASY' END,
       CASE n WHEN 9 THEN timestamptz '2999-01-01 00:00:00+00' WHEN 10 THEN NULL
              ELSE timestamptz '2020-01-01 00:00:00+00' END,
       CASE WHEN n IN (7, 11) THEN now() ELSE NULL END, now()
FROM generate_series(1, 25) n;
UPDATE questions q SET current_published_version_id = v.question_version_id
FROM question_versions v WHERE v.question_id = q.question_id AND q.created_by = '63000000-0000-0000-0000-000000000001'
  AND q.question_id <> '73000000-0000-0000-0000-000000000008';

INSERT INTO question_versions
    (question_version_id, question_id, version_number, question_text, status, explanation, published_at, created_at)
VALUES
 ('76000000-0000-0000-0000-000000000101', '73000000-0000-0000-0000-000000000001', 2,
  'HIDDEN_OTHER_PUBLISHED', 'PUBLISHED', 'HIDDEN_OTHER_EXPLANATION', '2021-01-01', now()),
 ('76000000-0000-0000-0000-000000000102', '73000000-0000-0000-0000-000000000001', 3,
  'HIDDEN_NEW_DRAFT', 'DRAFT', 'HIDDEN_DRAFT_EXPLANATION', NULL, now()),
 ('76000000-0000-0000-0000-000000000106', '73000000-0000-0000-0000-000000000006', 2,
  'HIDDEN_FALLBACK_PUBLISHED', 'PUBLISHED', 'HIDDEN_FALLBACK_EXPLANATION', '2019-01-01', now());
INSERT INTO question_group_versions
    (group_version_id, group_id, version_number, instruction, passage_text, status, published_at, created_at)
VALUES
 ('75000000-0000-0000-0000-000000000102', '74000000-0000-0000-0000-000000000002', 2,
  'HIDDEN_OTHER_INSTRUCTION', 'HIDDEN_OTHER_PASSAGE', 'PUBLISHED', '2021-01-01', now()),
 ('75000000-0000-0000-0000-000000000103', '74000000-0000-0000-0000-000000000002', 3,
  'HIDDEN_DRAFT_INSTRUCTION', 'HIDDEN_DRAFT_PASSAGE', 'DRAFT', NULL, now()),
 ('75000000-0000-0000-0000-000000000120', '74000000-0000-0000-0000-000000000020', 2,
  'HIDDEN_NEW_INSTRUCTION', 'HIDDEN_NEW_PASSAGE', 'PUBLISHED', '2021-01-01', now());
UPDATE question_groups SET current_published_version_id = '75000000-0000-0000-0000-000000000120'
WHERE group_id = '74000000-0000-0000-0000-000000000020';

INSERT INTO question_choices (choice_id, question_version_id, choice_label, content, is_correct, display_order)
SELECT md5(v.question_version_id::text || ':' || c.n::text)::uuid, v.question_version_id,
       CASE WHEN c.n = 1 THEN 'A' ELSE 'B' END, 'Fixture choice ' || c.n::text, c.n = 1, c.n
FROM question_versions v CROSS JOIN generate_series(1, 2) c(n)
WHERE v.question_id IN (SELECT question_id FROM questions WHERE created_by = '63000000-0000-0000-0000-000000000001');

INSERT INTO media_resources
    (resource_id, resource_type, storage_url, mime_type, file_size, duration_seconds, transcript, visibility, created_at)
SELECT ('83000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
       CASE WHEN n = 2 THEN 'IMAGE' ELSE 'AUDIO' END, 'internal://HIDDEN_STORAGE_PATH',
       CASE WHEN n = 2 THEN 'image/png' ELSE 'audio/mpeg' END, 1200, CASE WHEN n = 2 THEN NULL ELSE 30 END,
       'HIDDEN_TRANSCRIPT', CASE WHEN n IN (3, 5, 9) THEN 'PRIVATE' WHEN n = 2 THEN 'AUTHENTICATED' ELSE 'PUBLIC' END, now()
FROM generate_series(1, 9) n;
INSERT INTO question_resources VALUES
 ('76000000-0000-0000-0000-000000000001', '83000000-0000-0000-0000-000000000001'),
 ('76000000-0000-0000-0000-000000000001', '83000000-0000-0000-0000-000000000002'),
 ('76000000-0000-0000-0000-000000000001', '83000000-0000-0000-0000-000000000003'),
 ('76000000-0000-0000-0000-000000000002', '83000000-0000-0000-0000-000000000002'),
 ('76000000-0000-0000-0000-000000000101', '83000000-0000-0000-0000-000000000006'),
 ('76000000-0000-0000-0000-000000000102', '83000000-0000-0000-0000-000000000009');
INSERT INTO question_group_version_resources VALUES
 ('75000000-0000-0000-0000-000000000002', '83000000-0000-0000-0000-000000000004', 2),
 ('75000000-0000-0000-0000-000000000002', '83000000-0000-0000-0000-000000000002', 1),
 ('75000000-0000-0000-0000-000000000002', '83000000-0000-0000-0000-000000000005', 3),
 ('75000000-0000-0000-0000-000000000102', '83000000-0000-0000-0000-000000000007', 1),
 ('75000000-0000-0000-0000-000000000103', '83000000-0000-0000-0000-000000000009', 1),
 ('75000000-0000-0000-0000-000000000120', '83000000-0000-0000-0000-000000000008', 1);
