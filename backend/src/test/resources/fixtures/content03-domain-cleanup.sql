-- Remove drafts created by another fixture actor before the reused fixture deletes that actor.
DELETE FROM learning_content_versions
WHERE content_id IN (SELECT content_id FROM learning_contents
                     WHERE author_id='10000000-0000-0000-0000-000000000001')
  AND created_by='10000000-0000-0000-0000-000000000002';
