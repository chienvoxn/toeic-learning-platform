-- Reproducible authorization fixtures, NOT a Part 1-7 learning catalogue.
INSERT INTO users (user_id, email, password_hash, full_name, status, email_verified_at, created_at, updated_at)
VALUES ('10000000-0000-0000-0000-000000000001', 'content-fixture@example.invalid',
        'replaced-by-test-setup', 'Content fixture', 'ACTIVE', now(), now(), now());
INSERT INTO user_roles (user_id, role_id)
SELECT '10000000-0000-0000-0000-000000000001', role_id FROM roles WHERE name = 'LEARNER';
INSERT INTO roles (role_id, name) VALUES (900003, 'CONTENT_READ_FIXTURE');
INSERT INTO content_categories (category_id, code, name) VALUES
    (900001, 'CONTENT_TEST_LISTENING', 'Fixture listening'),
    (900002, 'CONTENT_TEST_READING', 'Fixture reading');

INSERT INTO learning_contents
    (content_id, category_id, author_id, title, summary, body, difficulty, status, visibility, created_at, updated_at)
SELECT ('20000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
       CASE WHEN n IN (2, 4) THEN 900002 ELSE 900001 END,
       '10000000-0000-0000-0000-000000000001'::uuid,
       'LEAK_LEGACY_DRAFT_TITLE', 'LEAK_LEGACY_DRAFT_SUMMARY', 'LEAK_LEGACY_DRAFT_BODY',
       CASE n WHEN 1 THEN 'EASY' WHEN 2 THEN 'MEDIUM' WHEN 3 THEN 'HARD' WHEN 4 THEN 'SECRET' ELSE 'EASY' END,
       CASE n WHEN 5 THEN 'DRAFT' WHEN 11 THEN 'ARCHIVED' ELSE 'PUBLISHED' END,
       CASE n WHEN 3 THEN 'AUTHENTICATED' WHEN 4 THEN 'PRIVATE' ELSE 'PUBLIC' END,
       now(), now()
FROM generate_series(1, 11) n;

INSERT INTO learning_content_versions
    (content_version_id, content_id, version_number, title, summary, body, status,
     published_at, archived_at, created_by, created_at, updated_at)
SELECT ('30000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
       ('20000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
       1,
       CASE n WHEN 1 THEN 'Alpha published' WHEN 2 THEN 'Beta published' WHEN 3 THEN 'Member published'
              ELSE 'LEAK_HIDDEN_TITLE' END,
       CASE WHEN n <= 3 THEN 'Published fixture summary' ELSE 'LEAK_HIDDEN_SUMMARY' END,
       CASE WHEN n <= 3 THEN 'Published fixture body' ELSE 'LEAK_HIDDEN_BODY' END,
       CASE WHEN n IN (5, 7) THEN 'DRAFT' WHEN n = 6 THEN 'ARCHIVED' ELSE 'PUBLISHED' END,
       CASE n WHEN 9 THEN timestamptz '2999-01-01 00:00:00+00' WHEN 10 THEN NULL
              ELSE timestamptz '2020-01-01 00:00:00+00' END,
       CASE WHEN n = 6 THEN now() ELSE NULL END,
       '10000000-0000-0000-0000-000000000001'::uuid, now(), now()
FROM generate_series(1, 11) n;

UPDATE learning_contents c SET current_published_version_id = v.content_version_id
FROM learning_content_versions v WHERE v.content_id = c.content_id
  AND c.author_id = '10000000-0000-0000-0000-000000000001'
  AND c.content_id <> '20000000-0000-0000-0000-000000000008';

-- An old published and a newer draft must never replace the current pointer in reads.
INSERT INTO learning_content_versions
    (content_version_id, content_id, version_number, title, body, status, published_at, created_by, created_at, updated_at)
VALUES
    ('30000000-0000-0000-0000-000000000101', '20000000-0000-0000-0000-000000000001',
     2, 'LEAK_OLD_VERSION', 'LEAK_OLD_BODY', 'PUBLISHED', '2019-01-01',
     '10000000-0000-0000-0000-000000000001', now(), now()),
    ('30000000-0000-0000-0000-000000000102', '20000000-0000-0000-0000-000000000001',
     3, 'LEAK_NEW_DRAFT', 'LEAK_DRAFT_BODY', 'DRAFT', NULL,
     '10000000-0000-0000-0000-000000000001', now(), now()),
    ('30000000-0000-0000-0000-000000000107', '20000000-0000-0000-0000-000000000007',
     2, 'LEAK_FALLBACK_PUBLISHED', 'LEAK_FALLBACK_BODY', 'PUBLISHED', '2019-01-01',
     '10000000-0000-0000-0000-000000000001', now(), now());

INSERT INTO media_resources
    (resource_id, resource_type, storage_url, mime_type, file_size, duration_seconds, transcript, visibility, created_at)
SELECT ('40000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
       'AUDIO', 's3://internal/LEAK_STORAGE', 'audio/mpeg', 1234, 30, 'LEAK_TRANSCRIPT',
       CASE n WHEN 2 THEN 'AUTHENTICATED' WHEN 3 THEN 'PRIVATE' ELSE 'PUBLIC' END, now()
FROM generate_series(1, 6) n;
INSERT INTO learning_content_version_resources (content_version_id, resource_id, display_order) VALUES
    ('30000000-0000-0000-0000-000000000001', '40000000-0000-0000-0000-000000000001', 1),
    ('30000000-0000-0000-0000-000000000001', '40000000-0000-0000-0000-000000000002', 2),
    ('30000000-0000-0000-0000-000000000001', '40000000-0000-0000-0000-000000000003', 3),
    ('30000000-0000-0000-0000-000000000102', '40000000-0000-0000-0000-000000000004', 1),
    ('30000000-0000-0000-0000-000000000101', '40000000-0000-0000-0000-000000000006', 1);
INSERT INTO content_resources (content_id, resource_id) VALUES
    ('20000000-0000-0000-0000-000000000001', '40000000-0000-0000-0000-000000000005');
