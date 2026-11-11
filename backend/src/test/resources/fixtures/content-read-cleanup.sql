DELETE FROM learning_content_version_resources WHERE content_version_id IN
    (SELECT content_version_id FROM learning_content_versions WHERE created_by = '10000000-0000-0000-0000-000000000001');
DELETE FROM content_resources WHERE content_id IN
    (SELECT content_id FROM learning_contents WHERE author_id = '10000000-0000-0000-0000-000000000001');
UPDATE learning_contents SET current_published_version_id = NULL WHERE author_id = '10000000-0000-0000-0000-000000000001';
DELETE FROM learning_content_versions WHERE created_by = '10000000-0000-0000-0000-000000000001';
DELETE FROM learning_contents WHERE author_id = '10000000-0000-0000-0000-000000000001';
DELETE FROM media_resources WHERE resource_id BETWEEN '40000000-0000-0000-0000-000000000001' AND '40000000-0000-0000-0000-000000000006';
DELETE FROM content_categories WHERE category_id IN (900001, 900002);
DELETE FROM auth_sessions WHERE user_id = '10000000-0000-0000-0000-000000000001';
DELETE FROM user_roles WHERE user_id = '10000000-0000-0000-0000-000000000001';
DELETE FROM role_permissions WHERE role_id = 900003;
DELETE FROM roles WHERE role_id = 900003;
DELETE FROM users WHERE user_id = '10000000-0000-0000-0000-000000000001';
