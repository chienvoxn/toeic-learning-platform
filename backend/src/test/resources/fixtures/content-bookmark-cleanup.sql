-- Scoped to the fixtures from content-read.sql/content-bookmark.sql only.
DELETE FROM learning_content_history
WHERE user_id IN ('10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002')
   OR content_id IN (SELECT content_id FROM learning_contents
                    WHERE author_id = '10000000-0000-0000-0000-000000000001');
DELETE FROM bookmarks
WHERE user_id IN ('10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002')
   OR content_id IN (SELECT content_id FROM learning_contents
                    WHERE author_id = '10000000-0000-0000-0000-000000000001');
DELETE FROM auth_sessions WHERE user_id = '10000000-0000-0000-0000-000000000002';
DELETE FROM user_roles WHERE user_id = '10000000-0000-0000-0000-000000000002';
DELETE FROM users WHERE user_id = '10000000-0000-0000-0000-000000000002';
