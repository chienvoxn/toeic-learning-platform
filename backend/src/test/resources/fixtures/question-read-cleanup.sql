DELETE FROM question_resources WHERE question_version_id IN (
 SELECT question_version_id FROM question_versions WHERE question_id IN (
 SELECT question_id FROM questions WHERE created_by = '63000000-0000-0000-0000-000000000001'));
DELETE FROM question_choices WHERE question_version_id IN (
 SELECT question_version_id FROM question_versions WHERE question_id IN (
 SELECT question_id FROM questions WHERE created_by = '63000000-0000-0000-0000-000000000001'));
UPDATE questions SET current_published_version_id = NULL WHERE created_by = '63000000-0000-0000-0000-000000000001';
DELETE FROM question_versions WHERE question_id IN (
 SELECT question_id FROM questions WHERE created_by = '63000000-0000-0000-0000-000000000001');
DELETE FROM questions WHERE created_by = '63000000-0000-0000-0000-000000000001';
DELETE FROM question_group_version_resources WHERE group_version_id IN (
 SELECT group_version_id FROM question_group_versions WHERE group_id BETWEEN
 '74000000-0000-0000-0000-000000000002' AND '74000000-0000-0000-0000-000000000024');
UPDATE question_groups SET current_published_version_id = NULL WHERE group_id BETWEEN
 '74000000-0000-0000-0000-000000000002' AND '74000000-0000-0000-0000-000000000024';
DELETE FROM question_group_versions WHERE group_id BETWEEN
 '74000000-0000-0000-0000-000000000002' AND '74000000-0000-0000-0000-000000000024';
DELETE FROM question_groups WHERE group_id BETWEEN
 '74000000-0000-0000-0000-000000000002' AND '74000000-0000-0000-0000-000000000024';
DELETE FROM media_resources WHERE resource_id BETWEEN
 '83000000-0000-0000-0000-000000000001' AND '83000000-0000-0000-0000-000000000009';
DELETE FROM toeic_parts WHERE part_id IN (930001, 930002);
DELETE FROM topics WHERE topic_id IN (930001, 930002);
DELETE FROM question_types WHERE question_type_id = 930001;
DELETE FROM auth_sessions WHERE user_id = '63000000-0000-0000-0000-000000000001';
DELETE FROM user_roles WHERE user_id = '63000000-0000-0000-0000-000000000001';
DELETE FROM users WHERE user_id = '63000000-0000-0000-0000-000000000001';
