-- Reproducible practice references only; run after q03-authoring.sql. Not a production question bank.
INSERT INTO users(user_id,email,password_hash,full_name,status,email_verified_at,created_at,updated_at) VALUES
 ('a4000000-0000-0000-0000-000000000001','practice-learner@example.invalid','unused-fixture-value','Practice learner','ACTIVE',now(),now(),now()),
 ('a4000000-0000-0000-0000-000000000002','practice-other@example.invalid','unused-fixture-value','Practice other','ACTIVE',now(),now(),now());
INSERT INTO user_roles(user_id,role_id)
 SELECT u.user_id,r.role_id FROM users u CROSS JOIN roles r WHERE r.name='LEARNER'
 AND u.user_id BETWEEN 'a4000000-0000-0000-0000-000000000001' AND 'a4000000-0000-0000-0000-000000000002';

INSERT INTO question_groups(group_id,created_by,group_type,status,created_at,updated_at)
 SELECT ('c4000000-0000-0000-0000-'||lpad(n::text,12,'0'))::uuid,
 'a3000000-0000-0000-0000-000000000001','PASSAGE','PUBLISHED','2026-01-01T00:00:00Z','2026-01-01T00:00:00Z' FROM generate_series(1,6)n;
INSERT INTO question_group_versions(group_version_id,group_id,version_number,status,instruction,passage_text,published_at,created_at)
 SELECT ('c4100000-0000-0000-0000-'||lpad(n::text,12,'0'))::uuid,
 ('c4000000-0000-0000-0000-'||lpad(n::text,12,'0'))::uuid,1,'PUBLISHED','Practice instruction '||n,'Practice passage '||n,'2026-01-01T00:00:00Z','2026-01-01T00:00:00Z'
 FROM generate_series(1,6)n;
UPDATE question_groups SET current_published_version_id=('c4100000-0000-0000-0000-'||right(group_id::text,12))::uuid
 WHERE group_id BETWEEN 'c4000000-0000-0000-0000-000000000001' AND 'c4000000-0000-0000-0000-000000000006';

WITH data AS (
 SELECT n,CASE WHEN n<=2 THEN 1 WHEN n=3 THEN 2 WHEN n<=13 THEN 3 WHEN n<=15 THEN 4 WHEN n<=17 THEN 5 WHEN n<=19 THEN 6 ELSE 7 END part,
 CASE WHEN n BETWEEN 4 AND 7 THEN 1 WHEN n BETWEEN 8 AND 10 THEN 2 WHEN n BETWEEN 11 AND 13 THEN 3
 WHEN n BETWEEN 14 AND 15 THEN 4 WHEN n BETWEEN 18 AND 19 THEN 5 WHEN n BETWEEN 20 AND 22 THEN 6 END grp
 FROM generate_series(1,22)n
)
INSERT INTO questions(question_id,group_id,part_id,topic_id,question_type_id,created_by,status,created_at,updated_at)
 SELECT ('d4000000-0000-0000-0000-'||lpad(d.n::text,12,'0'))::uuid,
 CASE WHEN d.grp IS NOT NULL THEN ('c4000000-0000-0000-0000-'||lpad(d.grp::text,12,'0'))::uuid END,
 p.part_id,960001,t.question_type_id,'a3000000-0000-0000-0000-000000000001','PUBLISHED',
 '2026-01-01T00:00:00Z'::timestamptz+d.n*interval '1 second','2026-01-01T00:00:00Z'
 FROM data d JOIN toeic_parts p ON p.part_number=d.part CROSS JOIN question_types t WHERE t.code='SINGLE_CHOICE';
INSERT INTO question_versions(question_version_id,question_id,version_number,status,group_version_id,question_text,explanation,difficulty,published_at,created_at)
 SELECT ('d4100000-0000-0000-0000-'||right(q.question_id::text,12))::uuid,q.question_id,1,'PUBLISHED',
 g.current_published_version_id,'Practice prompt '||right(q.question_id::text,3),'PRAC_SECRET_EXPLANATION','EASY','2026-01-01T00:00:00Z','2026-01-01T00:00:00Z'
 FROM questions q LEFT JOIN question_groups g USING(group_id)
 WHERE q.question_id BETWEEN 'd4000000-0000-0000-0000-000000000001' AND 'd4000000-0000-0000-0000-000000000022';
-- Deliberately reverse creation/UUID order: business position is authoritative.
UPDATE question_versions SET group_question_order=1000-right(question_version_id::text,12)::integer
 WHERE group_version_id IS NOT NULL AND question_version_id BETWEEN
 'd4100000-0000-0000-0000-000000000001' AND 'd4100000-0000-0000-0000-000000000022';
UPDATE questions SET current_published_version_id=('d4100000-0000-0000-0000-'||right(question_id::text,12))::uuid
 WHERE question_id BETWEEN 'd4000000-0000-0000-0000-000000000001' AND 'd4000000-0000-0000-0000-000000000022';
INSERT INTO question_choices(choice_id,question_version_id,choice_label,content,is_correct,display_order)
 SELECT ('e4000000-0000-0000-'||lpad(c.n::text,4,'0')||'-'||right(q.question_id::text,12))::uuid,
 q.current_published_version_id,chr(64+c.n),'Practice choice '||chr(64+c.n),c.n=1,c.n
 FROM questions q JOIN toeic_parts p USING(part_id) CROSS JOIN generate_series(1,4)c(n)
 WHERE q.question_id BETWEEN 'd4000000-0000-0000-0000-000000000001' AND 'd4000000-0000-0000-0000-000000000022'
 AND c.n<=CASE WHEN p.part_number=2 THEN 3 ELSE 4 END;
INSERT INTO question_group_version_resources(group_version_id,resource_id,display_order,transcript)
 SELECT group_version_id,'b3000000-0000-0000-0000-000000000001',1,'PRAC_SECRET_TRANSCRIPT'
 FROM question_group_versions WHERE group_version_id BETWEEN 'c4100000-0000-0000-0000-000000000001' AND 'c4100000-0000-0000-0000-000000000006';
INSERT INTO question_resources(question_version_id,resource_id,transcript) VALUES
 ('d4100000-0000-0000-0000-000000000001','b3000000-0000-0000-0000-000000000005','PRAC_SECRET_TRANSCRIPT'),
 ('d4100000-0000-0000-0000-000000000001','b3000000-0000-0000-0000-000000000003','PRAC_PRIVATE_TRANSCRIPT'),
 ('d4100000-0000-0000-0000-000000000002','b3000000-0000-0000-0000-000000000006',null);
