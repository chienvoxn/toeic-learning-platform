\set ON_ERROR_STOP on
BEGIN;
DO $$ BEGIN
  IF current_database()<>'toeic_learning_demo' OR current_user<>'toeic_demo' OR current_schema()<>'public' THEN
    RAISE EXCEPTION 'Seed restricted to toeic_demo/public/toeic_learning_demo';
  END IF;
END $$;
SELECT pg_advisory_xact_lock(73521001);

-- Only explicit local/demo bootstrap. No trigger/constraint/permission is disabled.
INSERT INTO users(user_id,email,password_hash,full_name,status,created_at,updated_at)
VALUES ('de100000-0000-4000-8000-000000000001','learner@demo.example',:'demo_password_hash','Demo Learner','ACTIVE',now(),now()),
       ('de100000-0000-4000-8000-000000000002','teacher@demo.example',:'demo_password_hash','Demo Teacher','ACTIVE',now(),now()),
       ('de100000-0000-4000-8000-000000000003','admin@demo.example',:'demo_password_hash','Demo Admin','ACTIVE',now(),now())
ON CONFLICT(user_id) DO NOTHING;
INSERT INTO user_roles(user_id,role_id,assigned_at)
SELECT u.user_id,r.role_id,now() FROM users u JOIN roles r ON
 (u.user_id='de100000-0000-4000-8000-000000000001' AND r.name='LEARNER') OR
 (u.user_id='de100000-0000-4000-8000-000000000002' AND r.name='TEACHER') OR
 (u.user_id='de100000-0000-4000-8000-000000000003' AND r.name='ADMIN')
ON CONFLICT DO NOTHING;
INSERT INTO toeic_parts(part_number,skill,name)
SELECT n,CASE WHEN n<=4 THEN 'LISTENING' ELSE 'READING' END,'Part '||n FROM generate_series(1,7)n ON CONFLICT(part_number) DO NOTHING;
INSERT INTO topics(name,description) VALUES ('Local Demo Workplace','Local demo only; original educational examples') ON CONFLICT(name) DO NOTHING;

-- Published reference snapshots are seed data. Teacher CRUD currently creates drafts;
-- no publish API exists on this branch. We do not grant Learner question:read or expose keys.
INSERT INTO question_groups(group_id,group_type,created_by,status,created_at,updated_at)
VALUES ('de200000-0000-4000-8000-000000000006','TEXT_COMPLETION','de100000-0000-4000-8000-000000000002','PUBLISHED',now(),now()),
       ('de200000-0000-4000-8000-000000000007','READING_PASSAGE','de100000-0000-4000-8000-000000000002','PUBLISHED',now(),now())
ON CONFLICT(group_id) DO NOTHING;
INSERT INTO question_group_versions(group_version_id,group_id,version_number,instruction,passage_text,status,published_at,created_at)
VALUES ('de210000-0000-4000-8000-000000000006','de200000-0000-4000-8000-000000000006',1,'Complete the office notice.',
 E'Office notice\nOur customer workshop is scheduled for Friday. Please register [1] Thursday. The meeting room will be [2] at 9 a.m. The team [3] printed the handouts. Contact reception if you need [4] information.', 'PUBLISHED',now()-interval '1 minute',now()),
 ('de210000-0000-4000-8000-000000000007','de200000-0000-4000-8000-000000000007',1,'Read the email and answer the questions.',
 E'To: Workshop participants\nSubject: Friday workshop\nThe workshop starts at 9 a.m. in Room 3. Please bring a notebook. Coffee is available at reception. The session ends at noon.', 'PUBLISHED',now()-interval '1 minute',now())
ON CONFLICT(group_version_id) DO NOTHING;
UPDATE question_groups SET current_published_version_id=CASE WHEN group_id='de200000-0000-4000-8000-000000000006' THEN 'de210000-0000-4000-8000-000000000006'::uuid ELSE 'de210000-0000-4000-8000-000000000007'::uuid END
WHERE group_id IN ('de200000-0000-4000-8000-000000000006','de200000-0000-4000-8000-000000000007') AND current_published_version_id IS NULL;

CREATE TEMP TABLE demo_questions(n integer,part integer,position integer,prompt text,a text,b text,c text,d text,explanation text) ON COMMIT DROP;
INSERT INTO demo_questions VALUES
 (1,5,NULL,'The manager ___ the report every Monday.','reviews','review','reviewing','reviewed','The singular subject manager requires reviews in the present simple.'),
 (2,5,NULL,'Please send the invoice ___ email.','by','at','on','from','By email describes the method used to send something.'),
 (3,5,NULL,'Our new office is ___ than the previous one.','larger','large','largest','largely','Than introduces a comparison, so the comparative larger is needed.'),
 (4,5,NULL,'The meeting starts ___ 10 a.m.','at','on','in','by','At is used with a specific clock time.'),
 (5,6,1,'Choose the word for [1].','by','during','among','along','By Thursday means no later than Thursday.'),
 (6,6,2,'Choose the word for [2].','open','opening','opens','opened','After will be, open functions as an adjective describing the room.'),
 (7,6,3,'Choose the word for [3].','has','have','having','are','The singular collective subject team takes has here.'),
 (8,6,4,'Choose the word for [4].','additional','addition','additionally','add','The adjective additional modifies the noun information.'),
 (9,7,1,'When does the workshop begin?','At 9 a.m.','At noon','On Thursday','At 3 p.m.','The email explicitly says the workshop starts at 9 a.m.'),
 (10,7,2,'What should participants bring?','A notebook','An invoice','A laptop charger','A printed ticket','The email asks participants to bring a notebook.'),
 (11,7,3,'Where is coffee available?','At reception','In Room 3','At the new office','At the airport','Coffee is available at reception, according to the email.');
INSERT INTO questions(question_id,group_id,part_id,topic_id,question_type_id,created_by,status,created_at,updated_at)
SELECT ('de300000-0000-4000-8000-'||lpad(d.n::text,12,'0'))::uuid,
 CASE WHEN d.part=5 THEN NULL ELSE ('de200000-0000-4000-8000-'||lpad(d.part::text,12,'0'))::uuid END,
 p.part_id,t.topic_id,qt.question_type_id,'de100000-0000-4000-8000-000000000002','PUBLISHED',now(),now()
FROM demo_questions d JOIN toeic_parts p ON p.part_number=d.part JOIN topics t ON t.name='Local Demo Workplace' JOIN question_types qt ON qt.code='SINGLE_CHOICE'
ON CONFLICT(question_id) DO NOTHING;
INSERT INTO question_versions(question_version_id,question_id,group_version_id,version_number,question_text,status,explanation,difficulty,published_at,created_at,group_question_order)
SELECT ('de310000-0000-4000-8000-'||lpad(n::text,12,'0'))::uuid,('de300000-0000-4000-8000-'||lpad(n::text,12,'0'))::uuid,
 CASE WHEN part=5 THEN NULL ELSE ('de210000-0000-4000-8000-'||lpad(part::text,12,'0'))::uuid END,
 1,prompt,'PUBLISHED',explanation,'EASY',now()-interval '1 minute',now(),position FROM demo_questions
ON CONFLICT(question_version_id) DO NOTHING;
INSERT INTO question_choices(choice_id,question_version_id,choice_label,content,is_correct,display_order)
SELECT ('de320000-0000-4000-'||lpad(c.k::text,4,'0')||'-'||lpad(d.n::text,12,'0'))::uuid,
 ('de310000-0000-4000-8000-'||lpad(d.n::text,12,'0'))::uuid,chr(64+c.k),
 CASE c.k WHEN 1 THEN d.a WHEN 2 THEN d.b WHEN 3 THEN d.c ELSE d.d END,c.k=1,c.k
FROM demo_questions d CROSS JOIN generate_series(1,4)c(k) ON CONFLICT(choice_id) DO NOTHING;
UPDATE questions SET current_published_version_id=('de310000-0000-4000-8000-'||right(question_id::text,12))::uuid
WHERE question_id BETWEEN 'de300000-0000-4000-8000-000000000001' AND 'de300000-0000-4000-8000-000000000011' AND current_published_version_id IS NULL;

INSERT INTO learning_contents(content_id,category_id,author_id,title,summary,body,difficulty,status,published_at,created_at,updated_at,visibility)
SELECT 'de400000-0000-4000-8000-000000000001',category_id,'de100000-0000-4000-8000-000000000002','Workplace grammar: present simple','Original local demo lesson',
 'Use the present simple for routines. A singular subject takes an -s ending: The manager reviews the report every Monday. Practice this pattern with workplace sentences.', 'EASY','PUBLISHED',now()-interval '1 minute',now(),now(),'PUBLIC'
FROM content_categories WHERE code='GRAMMAR' ON CONFLICT(content_id) DO NOTHING;
INSERT INTO learning_content_versions(content_version_id,content_id,version_number,title,summary,body,status,published_at,created_by,created_at,updated_at)
SELECT 'de410000-0000-4000-8000-000000000001',content_id,1,title,summary,body,'PUBLISHED',published_at,author_id,now(),now()
FROM learning_contents WHERE content_id='de400000-0000-4000-8000-000000000001' ON CONFLICT(content_version_id) DO NOTHING;
UPDATE learning_contents SET current_published_version_id='de410000-0000-4000-8000-000000000001'
WHERE content_id='de400000-0000-4000-8000-000000000001' AND current_published_version_id IS NULL;
-- No attempts, answers, results, review history or fake progress are seeded.
COMMIT;
