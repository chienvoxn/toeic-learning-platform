-- Reuse only isolated content fixtures. Categories are the stable V19 product codes.
UPDATE learning_contents SET category_id=(SELECT category_id FROM content_categories WHERE code='GRAMMAR')
WHERE author_id='10000000-0000-0000-0000-000000000001';
UPDATE learning_contents SET author_id='10000000-0000-0000-0000-000000000002'
WHERE content_id='20000000-0000-0000-0000-000000000002';
DELETE FROM user_roles WHERE user_id='10000000-0000-0000-0000-000000000001';
INSERT INTO user_roles (user_id,role_id)
SELECT '10000000-0000-0000-0000-000000000001',role_id FROM roles WHERE name='TEACHER';

-- Explicit test ownership, not inferred from content links and not a production backfill.
UPDATE media_resources SET owner_id='10000000-0000-0000-0000-000000000001'
WHERE resource_id BETWEEN '40000000-0000-0000-0000-000000000001' AND '40000000-0000-0000-0000-000000000006';
