-- Remove authoring rows of BOTH fixture actors before the reused cleanup removes users.
DELETE FROM media_reuse_grants WHERE media_id BETWEEN
    '40000000-0000-0000-0000-000000000001' AND '40000000-0000-0000-0000-000000000006';
UPDATE media_resources SET owner_id=NULL WHERE owner_id IN
    ('10000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000002');
DELETE FROM learning_content_history WHERE content_id IN
    (SELECT content_id FROM learning_contents WHERE author_id IN
    ('10000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000002'));
DELETE FROM bookmarks WHERE content_id IN
    (SELECT content_id FROM learning_contents WHERE author_id IN
    ('10000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000002'));
DELETE FROM learning_content_version_resources WHERE content_version_id IN
    (SELECT v.content_version_id FROM learning_content_versions v JOIN learning_contents c USING(content_id)
    WHERE c.author_id IN ('10000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000002'));
DELETE FROM content_resources WHERE content_id IN
    (SELECT content_id FROM learning_contents WHERE author_id IN
    ('10000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000002'));
UPDATE learning_contents SET current_published_version_id=NULL WHERE author_id IN
    ('10000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000002');
DELETE FROM learning_content_versions WHERE content_id IN
    (SELECT content_id FROM learning_contents WHERE author_id IN
    ('10000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000002'));
DELETE FROM learning_contents WHERE author_id IN
    ('10000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000002');
