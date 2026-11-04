-- Reuse content-read.sql before this file. These users/content belong only to automated tests.
INSERT INTO users (user_id, email, password_hash, full_name, status, email_verified_at, created_at, updated_at)
VALUES ('10000000-0000-0000-0000-000000000002', 'bookmark-fixture@example.invalid',
        'not-used-for-authentication', 'Bookmark fixture', 'ACTIVE', now(), now(), now());
INSERT INTO user_roles (user_id, role_id)
SELECT '10000000-0000-0000-0000-000000000002', role_id FROM roles WHERE name = 'LEARNER';
