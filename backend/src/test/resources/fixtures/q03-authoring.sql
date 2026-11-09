-- Reproducible Q-03 fixtures only; not a Part 1-7 production question bank.
INSERT INTO users(user_id,email,password_hash,full_name,status,email_verified_at,created_at,updated_at) VALUES
 ('a3000000-0000-0000-0000-000000000001','q03-author@example.invalid','unused-fixture-value','Q03 author','ACTIVE',now(),now(),now()),
 ('a3000000-0000-0000-0000-000000000002','q03-other@example.invalid','unused-fixture-value','Q03 other','ACTIVE',now(),now(),now()),
 ('a3000000-0000-0000-0000-000000000003','q03-admin@example.invalid','unused-fixture-value','Q03 admin','ACTIVE',now(),now(),now()),
 ('a3000000-0000-0000-0000-000000000004','q03-learner@example.invalid','unused-fixture-value','Q03 learner','ACTIVE',now(),now(),now());
INSERT INTO user_roles(user_id,role_id)
 SELECT u.user_id,r.role_id FROM users u CROSS JOIN roles r WHERE
 (u.user_id IN ('a3000000-0000-0000-0000-000000000001','a3000000-0000-0000-0000-000000000002') AND r.name='TEACHER')
 OR (u.user_id='a3000000-0000-0000-0000-000000000003' AND r.name='ADMIN')
 OR (u.user_id='a3000000-0000-0000-0000-000000000004' AND r.name='LEARNER');
INSERT INTO toeic_parts(part_id,part_number,skill,name)
 SELECT 960000+n,n,CASE WHEN n<=4 THEN 'LISTENING' ELSE 'READING' END,'Q03 fixture Part '||n
 FROM generate_series(1,7) n WHERE NOT EXISTS(SELECT 1 FROM toeic_parts p WHERE p.part_number=n);
INSERT INTO topics(topic_id,name) VALUES (960001,'Q03 fixture topic');
INSERT INTO media_resources(resource_id,resource_type,storage_url,mime_type,transcript,visibility,owner_id,created_at) VALUES
 ('b3000000-0000-0000-0000-000000000001','AUDIO','internal://Q03_LEAK_STORAGE_1','audio/mpeg','Q03_LEGACY_TRANSCRIPT','PUBLIC','a3000000-0000-0000-0000-000000000001',now()),
 ('b3000000-0000-0000-0000-000000000002','IMAGE','internal://Q03_LEAK_STORAGE_2','image/png',null,'PUBLIC','a3000000-0000-0000-0000-000000000002',now()),
 ('b3000000-0000-0000-0000-000000000003','AUDIO','internal://Q03_LEAK_STORAGE_3','audio/mpeg',null,'PRIVATE','a3000000-0000-0000-0000-000000000001',now()),
 ('b3000000-0000-0000-0000-000000000004','AUDIO','internal://Q03_LEAK_STORAGE_4','audio/mpeg',null,'PUBLIC',null,now()),
 ('b3000000-0000-0000-0000-000000000005','AUDIO','internal://Q03_LEAK_STORAGE_5','audio/mpeg',null,'AUTHENTICATED','a3000000-0000-0000-0000-000000000002',now()),
 ('b3000000-0000-0000-0000-000000000006','IMAGE','internal://Q03_LEAK_STORAGE_6','image/png',null,'PUBLIC','a3000000-0000-0000-0000-000000000001',now()),
 ('b3000000-0000-0000-0000-000000000007','VIDEO','internal://Q03_LEAK_STORAGE_7','video/mp4',null,'PUBLIC','a3000000-0000-0000-0000-000000000001',now()),
 ('b3000000-0000-0000-0000-000000000008','AUDIO','internal://Q03_LEAK_STORAGE_8','audio/mpeg',null,'PUBLIC','a3000000-0000-0000-0000-000000000002',now()),
 ('b3000000-0000-0000-0000-000000000009','AUDIO','internal://Q03_LEAK_STORAGE_9','audio/mpeg',null,'PUBLIC','a3000000-0000-0000-0000-000000000001',now());
INSERT INTO media_reuse_grants(media_id,user_id,granted_by,granted_at,revoked_by,revoked_at) VALUES
 ('b3000000-0000-0000-0000-000000000005','a3000000-0000-0000-0000-000000000001','a3000000-0000-0000-0000-000000000003',now(),null,null),
 ('b3000000-0000-0000-0000-000000000008','a3000000-0000-0000-0000-000000000001','a3000000-0000-0000-0000-000000000003',now()-interval '2 minutes','a3000000-0000-0000-0000-000000000003',now()-interval '1 minute');
