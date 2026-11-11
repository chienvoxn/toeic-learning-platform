-- Only the current published versions receive these searchable markers.
UPDATE learning_content_versions SET summary='Shared needle Đúng ngữ pháp',
    body='BodyOnly 100% underscore_value slash\literal bang!end'
WHERE content_version_id='30000000-0000-0000-0000-000000000001';
UPDATE learning_content_versions SET summary='Shared needle reading'
WHERE content_version_id='30000000-0000-0000-0000-000000000002';
UPDATE learning_content_versions SET summary='Shared needle member'
WHERE content_version_id='30000000-0000-0000-0000-000000000003';
