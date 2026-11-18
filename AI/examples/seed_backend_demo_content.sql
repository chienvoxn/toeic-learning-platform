\set ON_ERROR_STOP on

-- Local demo fixture only. Run with:
-- psql ... -v author_id='UUID_OF_AN_EXISTING_ADMIN_OR_TEACHER' -f seed_backend_demo_content.sql
\if :{?author_id}
\else
  \echo 'Missing -v author_id=... (use an existing Admin or Teacher user UUID)'
  \quit
\endif

SELECT EXISTS (
    SELECT 1 FROM users WHERE user_id = :'author_id'::uuid
) AS author_exists \gset

\if :author_exists
\else
  \echo 'author_id does not exist in the backend users table'
  \quit
\endif

BEGIN;

INSERT INTO learning_contents (
    content_id, current_published_version_id, category_id, author_id,
    title, summary, body, difficulty, status, published_at,
    created_at, updated_at, visibility
)
SELECT
    '0bd8ce55-6f85-4eef-8e02-64b7aaad39be'::uuid, NULL, category_id,
    :'author_id'::uuid, 'TOEIC grammar sample: present perfect',
    'Local RAG demo content for for/since and the present perfect.',
    'The present perfect uses have or has plus the past participle. Use for with a duration, such as for three years. Use since with a starting point, such as since 2023. Use the simple past with a finished past time: She submitted the report yesterday.',
    'A2', 'PUBLISHED', now(), now(), now(), 'AUTHENTICATED'
FROM content_categories
WHERE code = 'GRAMMAR'
ON CONFLICT (content_id) DO UPDATE SET
    author_id = EXCLUDED.author_id,
    category_id = EXCLUDED.category_id,
    title = EXCLUDED.title,
    summary = EXCLUDED.summary,
    body = EXCLUDED.body,
    difficulty = EXCLUDED.difficulty,
    status = EXCLUDED.status,
    published_at = EXCLUDED.published_at,
    updated_at = now(),
    visibility = EXCLUDED.visibility;

INSERT INTO learning_content_versions (
    content_version_id, content_id, version_number, title, summary, body,
    status, published_at, archived_at, created_by, created_at, updated_at
)
VALUES (
    '27bd260f-9558-4884-a9d5-d3058e112d49'::uuid,
    '0bd8ce55-6f85-4eef-8e02-64b7aaad39be'::uuid,
    1, 'TOEIC grammar sample: present perfect',
    'Local RAG demo content for for/since and the present perfect.',
    'The present perfect uses have or has plus the past participle. Use for with a duration, such as for three years. Use since with a starting point, such as since 2023. Use the simple past with a finished past time: She submitted the report yesterday.',
    'PUBLISHED', now(), NULL, :'author_id'::uuid, now(), now()
)
ON CONFLICT (content_version_id) DO UPDATE SET
    title = EXCLUDED.title,
    summary = EXCLUDED.summary,
    body = EXCLUDED.body,
    status = EXCLUDED.status,
    published_at = EXCLUDED.published_at,
    archived_at = NULL,
    updated_at = now();

UPDATE learning_contents
SET current_published_version_id = '27bd260f-9558-4884-a9d5-d3058e112d49'::uuid,
    updated_at = now()
WHERE content_id = '0bd8ce55-6f85-4eef-8e02-64b7aaad39be'::uuid;

COMMIT;

\echo 'Demo content ready. Call POST /api/v1/admin/ai/contents/0bd8ce55-6f85-4eef-8e02-64b7aaad39be/index next.'
