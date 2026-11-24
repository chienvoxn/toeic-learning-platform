\set ON_ERROR_STOP on
BEGIN;

-- Ensure a fallback demo teacher exists if the database has no users yet
INSERT INTO users(user_id,email,password_hash,full_name,status,created_at,updated_at)
VALUES ('de100000-0000-4000-8000-000000000002','teacher@demo.example','$2a$10$7EqJtq98hPqEX7fNZaFWoOHi6M9e8ce2R36/4H5B2N4O7h1E/a5iG','Demo Teacher','ACTIVE',now(),now())
ON CONFLICT (email) DO NOTHING;

INSERT INTO toeic_parts(part_number,skill,name)
SELECT n,CASE WHEN n<=4 THEN 'LISTENING' ELSE 'READING' END,'Part '||n
FROM generate_series(1,7) n
ON CONFLICT(part_number) DO NOTHING;

INSERT INTO topics(name,description)
VALUES ('Local Demo Workplace','Local demo only; original educational examples')
ON CONFLICT(name) DO NOTHING;

INSERT INTO question_types(code,name,description)
VALUES ('SINGLE_CHOICE','Single choice','One correct choice')
ON CONFLICT(code) DO NOTHING;

WITH author_pick AS (
  SELECT COALESCE(
    (SELECT u.user_id FROM users u JOIN user_roles ur USING(user_id) JOIN roles r USING(role_id) WHERE r.name IN ('TEACHER','ADMIN') ORDER BY u.created_at LIMIT 1),
    (SELECT user_id FROM users ORDER BY created_at LIMIT 1)
  ) AS author_id
)
INSERT INTO media_resources(resource_id,resource_type,storage_url,mime_type,file_size,duration_seconds,transcript,visibility,owner_id,created_at)
SELECT r.resource_id::uuid, r.resource_type, r.storage_url, r.mime_type, r.file_size, r.duration_seconds, NULL, 'PUBLIC', a.author_id, now()
FROM author_pick a
CROSS JOIN (VALUES
 ('de500000-0000-4000-8000-000000000001','IMAGE','image/demo-part1-q1.svg','image/svg+xml',1420,NULL::integer),
 ('de500000-0000-4000-8000-000000000002','AUDIO','audio/demo-part1-q1.wav','audio/wav',64044,4),
 ('de500000-0000-4000-8000-000000000003','IMAGE','image/demo-part1-q2.svg','image/svg+xml',1420,NULL::integer),
 ('de500000-0000-4000-8000-000000000004','AUDIO','audio/demo-part1-q2.wav','audio/wav',64044,4),
 ('de500000-0000-4000-8000-000000000005','AUDIO','audio/demo-part2-q1.wav','audio/wav',64044,4),
 ('de500000-0000-4000-8000-000000000006','AUDIO','audio/demo-part2-q2.wav','audio/wav',64044,4),
 ('de500000-0000-4000-8000-000000000007','AUDIO','audio/demo-part3-g1.wav','audio/wav',96044,6),
 ('de500000-0000-4000-8000-000000000008','IMAGE','image/demo-part3-g1.svg','image/svg+xml',1560,NULL::integer),
 ('de500000-0000-4000-8000-000000000009','AUDIO','audio/demo-part4-g1.wav','audio/wav',96044,6)
) AS r(resource_id,resource_type,storage_url,mime_type,file_size,duration_seconds)
ON CONFLICT(resource_id) DO NOTHING;

WITH author_pick AS (
  SELECT COALESCE(
    (SELECT u.user_id FROM users u JOIN user_roles ur USING(user_id) JOIN roles r USING(role_id) WHERE r.name IN ('TEACHER','ADMIN') ORDER BY u.created_at LIMIT 1),
    (SELECT user_id FROM users ORDER BY created_at LIMIT 1)
  ) AS author_id
)
INSERT INTO question_groups(group_id,group_type,created_by,status,created_at,updated_at)
SELECT g.group_id::uuid, g.group_type, a.author_id, 'PUBLISHED', now(), now()
FROM author_pick a
CROSS JOIN (VALUES
 ('de200000-0000-4000-8000-000000000003','CONVERSATION'),
 ('de200000-0000-4000-8000-000000000004','SHORT_TALK'),
 ('de200000-0000-4000-8000-000000000006','TEXT_COMPLETION'),
 ('de200000-0000-4000-8000-000000000007','READING_PASSAGE')
) AS g(group_id,group_type)
ON CONFLICT(group_id) DO NOTHING;

INSERT INTO question_group_versions(group_version_id,group_id,version_number,instruction,passage_text,status,published_at,created_at)
VALUES
 ('de210000-0000-4000-8000-000000000003','de200000-0000-4000-8000-000000000003',1,
  'Listen to the conversation and look at the conference room schedule.',
  'Conference Room Schedule: Room A (9:00 AM - Design Review), Room B (10:30 AM - Budget Meeting), Room C (2:00 PM - Client Training).',
  'PUBLISHED',now()-interval '1 minute',now()),
 ('de210000-0000-4000-8000-000000000004','de200000-0000-4000-8000-000000000004',1,
  'Listen to the announcement at an office building.',
  'Questions refer to the following recorded office announcement.',
  'PUBLISHED',now()-interval '1 minute',now()),
 ('de210000-0000-4000-8000-000000000006','de200000-0000-4000-8000-000000000006',1,
  'Complete the office notice.',
  E'Office notice\nOur customer workshop is scheduled for Friday. Please register [1] Thursday. The meeting room will be [2] at 9 a.m. The team [3] printed the handouts. Contact reception if you need [4] information.',
  'PUBLISHED',now()-interval '1 minute',now()),
 ('de210000-0000-4000-8000-000000000007','de200000-0000-4000-8000-000000000007',1,
  'Read the email and answer the questions.',
  E'To: Workshop participants\nSubject: Friday workshop\nThe workshop starts at 9 a.m. in Room 3. Please bring a notebook. Coffee is available at reception. The session ends at noon.',
  'PUBLISHED',now()-interval '1 minute',now())
ON CONFLICT(group_version_id) DO NOTHING;

UPDATE question_groups
SET current_published_version_id = ('de210000-0000-4000-8000-'||right(group_id::text,12))::uuid
WHERE group_id IN (
  'de200000-0000-4000-8000-000000000003',
  'de200000-0000-4000-8000-000000000004',
  'de200000-0000-4000-8000-000000000006',
  'de200000-0000-4000-8000-000000000007'
) AND current_published_version_id IS NULL;

INSERT INTO question_group_version_resources(group_version_id,resource_id,display_order,transcript)
VALUES
 ('de210000-0000-4000-8000-000000000003','de500000-0000-4000-8000-000000000007',1,
  E'Woman: Hi Minh, the projector in Room B is being repaired this morning.\nMan: Thanks for letting me know. Our 10:30 budget meeting is scheduled there. Can we move it to Room C?\nWoman: Room C is free until the 2:00 PM client training, so I will update the booking for you.'),
 ('de210000-0000-4000-8000-000000000003','de500000-0000-4000-8000-000000000008',2,NULL),
 ('de210000-0000-4000-8000-000000000004','de500000-0000-4000-8000-000000000009',1,
  E'Attention all staff: This Friday afternoon, the IT department will upgrade the wireless network on the third floor starting at 4:00 PM. Please save your cloud documents before 3:45 PM and pick up your temporary security badge at the front desk.')
ON CONFLICT(group_version_id,resource_id) DO NOTHING;

CREATE TEMP TABLE seed_all_questions(n integer,part integer,position integer,prompt text,a text,b text,c text,d text,explanation text) ON COMMIT DROP;
INSERT INTO seed_all_questions VALUES
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
 (11,7,3,'Where is coffee available?','At reception','In Room 3','At the new office','At the airport','Coffee is available at reception, according to the email.'),
 (12,1,NULL,'Look at the picture and listen to the four statements in the audio.','A presenter is pointing at a chart on the screen.','Passengers are boarding a train at the station.','Boxes are being loaded onto a delivery truck.','A chef is preparing food in a restaurant kitchen.','Statement (A) best describes the workplace presentation shown in the photograph.'),
 (13,1,NULL,'Look at the picture and listen to the four statements in the audio.','Two colleagues are reviewing documents at a desk.','Workers are repairing a roof outdoors.','Customers are paying at a supermarket counter.','Bicycles are parked beside a subway entrance.','Statement (A) accurately describes the two colleagues working together at the desk.'),
 (14,2,NULL,'Listen to the question and choose the best response.','At 2:00 PM in Conference Room C.','Yes, I printed thirty copies.','No, the flight was on time.',NULL,'The question asks WHEN and WHERE the training session starts, so a time and room location is the natural answer.'),
 (15,2,NULL,'Listen to the question and choose the best response.','Ms. Lan from the accounting team.','About fifteen minutes by taxi.','It is on the second shelf.',NULL,'The question asks WHO approved the travel budget, so naming the person in charge is correct.'),
 (16,3,1,'Look at the graphic. Which meeting needs to be moved to another room?','The Budget Meeting','The Design Review','The Client Training','The New Hire Orientation','The man says their 10:30 meeting in Room B needs to move, which corresponds to the Budget Meeting on the schedule.'),
 (17,3,2,'What does the woman offer to do next?','Update the room reservation','Order lunch for the clients','Call the projector manufacturer','Print the training manual','The woman says she will update the booking to Room C.'),
 (18,4,1,'What will happen on Friday afternoon at 4:00 PM?','The wireless network will be upgraded.','The building lobby will be painted.','A fire drill will take place.','The cafeteria will open early.','The speaker announces that the IT department will upgrade the wireless network starting at 4:00 PM.'),
 (19,4,2,'What are employees asked to do before 3:45 PM?','Save their cloud documents','Move their cars from the garage','Submit their monthly timesheets','Return borrowed laptops','Employees are instructed to save their cloud documents before 3:45 PM.');

WITH author_pick AS (
  SELECT COALESCE(
    (SELECT u.user_id FROM users u JOIN user_roles ur USING(user_id) JOIN roles r USING(role_id) WHERE r.name IN ('TEACHER','ADMIN') ORDER BY u.created_at LIMIT 1),
    (SELECT user_id FROM users ORDER BY created_at LIMIT 1)
  ) AS author_id
)
INSERT INTO questions(question_id,group_id,part_id,topic_id,question_type_id,created_by,status,created_at,updated_at)
SELECT ('de300000-0000-4000-8000-'||lpad(d.n::text,12,'0'))::uuid,
 CASE WHEN d.part IN (1,2,5) THEN NULL ELSE ('de200000-0000-4000-8000-'||lpad(d.part::text,12,'0'))::uuid END,
 p.part_id,t.topic_id,qt.question_type_id,a.author_id,'PUBLISHED',now(),now()
FROM seed_all_questions d
CROSS JOIN author_pick a
JOIN toeic_parts p ON p.part_number=d.part
JOIN topics t ON t.name='Local Demo Workplace'
JOIN question_types qt ON qt.code='SINGLE_CHOICE'
ON CONFLICT(question_id) DO NOTHING;

INSERT INTO question_versions(question_version_id,question_id,group_version_id,version_number,question_text,status,explanation,difficulty,published_at,created_at,group_question_order)
SELECT ('de310000-0000-4000-8000-'||lpad(n::text,12,'0'))::uuid,
 ('de300000-0000-4000-8000-'||lpad(n::text,12,'0'))::uuid,
 CASE WHEN part IN (1,2,5) THEN NULL ELSE ('de210000-0000-4000-8000-'||lpad(part::text,12,'0'))::uuid END,
 1,prompt,'PUBLISHED',explanation,'EASY',now()-interval '1 minute',now(),position
FROM seed_all_questions
ON CONFLICT(question_version_id) DO NOTHING;

INSERT INTO question_choices(choice_id,question_version_id,choice_label,content,is_correct,display_order)
SELECT ('de320000-0000-4000-'||lpad(c.k::text,4,'0')||'-'||lpad(d.n::text,12,'0'))::uuid,
 ('de310000-0000-4000-8000-'||lpad(d.n::text,12,'0'))::uuid,chr(64+c.k),
 CASE c.k WHEN 1 THEN d.a WHEN 2 THEN d.b WHEN 3 THEN d.c ELSE d.d END,c.k=1,c.k
FROM seed_all_questions d CROSS JOIN generate_series(1,4)c(k)
WHERE c.k <= CASE WHEN d.part=2 THEN 3 ELSE 4 END
ON CONFLICT(choice_id) DO NOTHING;

UPDATE questions SET current_published_version_id=('de310000-0000-4000-8000-'||right(question_id::text,12))::uuid
WHERE question_id BETWEEN 'de300000-0000-4000-8000-000000000001' AND 'de300000-0000-4000-8000-000000000019'
  AND current_published_version_id IS NULL;

INSERT INTO question_resources(question_version_id,resource_id,transcript)
VALUES
 ('de310000-0000-4000-8000-000000000012','de500000-0000-4000-8000-000000000001',NULL),
 ('de310000-0000-4000-8000-000000000012','de500000-0000-4000-8000-000000000002',
  '(A) A presenter is pointing at a chart on the screen. (B) Passengers are boarding a train at the station. (C) Boxes are being loaded onto a delivery truck. (D) A chef is preparing food in a restaurant kitchen.'),
 ('de310000-0000-4000-8000-000000000013','de500000-0000-4000-8000-000000000003',NULL),
 ('de310000-0000-4000-8000-000000000013','de500000-0000-4000-8000-000000000004',
  '(A) Two colleagues are reviewing documents at a desk. (B) Workers are repairing a roof outdoors. (C) Customers are paying at a supermarket counter. (D) Bicycles are parked beside a subway entrance.'),
 ('de310000-0000-4000-8000-000000000014','de500000-0000-4000-8000-000000000005',
  'Question: When and where will the client training session take place? — (A) At 2:00 PM in Conference Room C. (B) Yes, I printed thirty copies. (C) No, the flight was on time.'),
 ('de310000-0000-4000-8000-000000000015','de500000-0000-4000-8000-000000000006',
  'Question: Who approved the travel budget for the regional conference? — (A) Ms. Lan from the accounting team. (B) About fifteen minutes by taxi. (C) It is on the second shelf.')
ON CONFLICT(question_version_id,resource_id) DO NOTHING;

COMMIT;
