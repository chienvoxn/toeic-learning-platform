-- TOEIC sample dataset v1. Run this entire UTF-8 file in pgAdmin Query Tool on toeic_db.
-- Requires Flyway V26. Opt-in only: this file is deliberately outside Flyway migrations.
-- Original educational examples, synthetic speech and an original office illustration.
-- No passwords/tokens/default credentials. Existing users, progress and published data are preserved.
BEGIN;
SELECT pg_advisory_xact_lock(hashtext('toeic-sample-data-v1'));
SET LOCAL client_encoding = 'UTF8';

CREATE OR REPLACE FUNCTION pg_temp.sample_id(key text) RETURNS uuid
LANGUAGE sql IMMUTABLE AS $$ SELECT md5('toeic-sample-v1:' || key)::uuid $$;

DO $$
BEGIN
  IF NOT EXISTS(SELECT 1 FROM flyway_schema_history WHERE version='26' AND success) THEN
    RAISE EXCEPTION 'Start the current backend first: Flyway V26 is required. No sample data was inserted.';
  END IF;
  IF (SELECT count(*) FROM users WHERE email IN ('admin@toeic.local','teacher@toeic.local') AND status='ACTIVE')<>2 THEN
    RAISE EXCEPTION 'The ACTIVE admin@toeic.local and teacher@toeic.local accounts must exist first. Passwords are never created/reset by this SQL.';
  END IF;
END $$;

CREATE TEMP TABLE sample_payload(document jsonb) ON COMMIT DROP;
INSERT INTO sample_payload VALUES (/* SAMPLE_PAYLOAD */);
CREATE TEMP TABLE sample_actors ON COMMIT DROP AS
 SELECT (SELECT user_id FROM users WHERE email='admin@toeic.local') admin_id,
        (SELECT user_id FROM users WHERE email='teacher@toeic.local') teacher_id;
-- Explicitly authorized demo accounts only. No role escalation for any other account.
INSERT INTO user_roles(user_id,role_id,assigned_at)
 SELECT u.user_id,r.role_id,CURRENT_TIMESTAMP FROM users u JOIN roles r ON
 (u.email='admin@toeic.local' AND r.name='ADMIN') OR (u.email='teacher@toeic.local' AND r.name='TEACHER')
 ON CONFLICT(user_id,role_id) DO NOTHING;
-- Personal sample decks/history are created for accounts already ACTIVE at the time of import.
CREATE TEMP TABLE sample_users ON COMMIT DROP AS SELECT user_id FROM users WHERE status='ACTIVE';

-- Media has owner+visibility and a real playable binary payload, not placeholder URLs.
INSERT INTO media_resources(resource_id,resource_type,storage_url,mime_type,file_size,duration_seconds,created_at,visibility,owner_id)
 SELECT (m->>'id')::uuid,m->>'type','db:'||(m->>'id'),m->>'mime',octet_length(decode(m->>'data','base64')),
 (m->>'duration')::integer,CURRENT_TIMESTAMP,'AUTHENTICATED',a.teacher_id
 FROM sample_payload s CROSS JOIN sample_actors a CROSS JOIN LATERAL jsonb_array_elements(s.document->'media') m
 ON CONFLICT(resource_id) DO NOTHING;
INSERT INTO media_uploads(resource_id,data)
 SELECT (m->>'id')::uuid,decode(m->>'data','base64') FROM sample_payload s
 CROSS JOIN LATERAL jsonb_array_elements(s.document->'media') m ON CONFLICT(resource_id) DO NOTHING;

-- Twelve fully published text lessons, covering all six existing categories.
DO $$
DECLARE lesson jsonb; i integer:=0; cid uuid; vid uuid; actor uuid; category integer;
BEGIN
 SELECT teacher_id INTO actor FROM sample_actors;
 FOR lesson IN SELECT jsonb_array_elements(document->'lessons') FROM sample_payload LOOP
   i:=i+1;cid:=pg_temp.sample_id('content:'||i);vid:=pg_temp.sample_id('content-version:'||i);
   SELECT category_id INTO STRICT category FROM content_categories WHERE code=lesson->>'category';
   INSERT INTO learning_contents(content_id,category_id,author_id,title,summary,body,difficulty,status,published_at,created_at,updated_at,visibility)
    VALUES(cid,category,actor,'[Mẫu] '||(lesson->>'title'),lesson->>'summary',lesson->>'body',lesson->>'difficulty','PUBLISHED',now()-interval '10 days',now()-interval '12 days',now()-interval '10 days',CASE WHEN i%3=0 THEN 'AUTHENTICATED' ELSE 'PUBLIC' END)
    ON CONFLICT(content_id) DO NOTHING;
   INSERT INTO learning_content_versions(content_version_id,content_id,version_number,title,summary,body,status,published_at,created_by,created_at,updated_at)
    VALUES(vid,cid,1,'[Mẫu] '||(lesson->>'title'),lesson->>'summary',lesson->>'body','PUBLISHED',now()-interval '10 days',actor,now()-interval '12 days',now()-interval '10 days') ON CONFLICT(content_version_id) DO NOTHING;
   UPDATE learning_contents SET current_published_version_id=vid WHERE content_id=cid AND current_published_version_id IS NULL;
 END LOOP;
END $$;

-- Full question bank: 6/25/39/30/30/16/54 questions across Parts 1–7.
INSERT INTO topics(name,description) VALUES('[Mẫu] Công sở và dịch vụ','Ngữ cảnh tự biên soạn cho dữ liệu mẫu v1') ON CONFLICT(name) DO NOTHING;
CREATE TEMP TABLE sample_questions(position integer PRIMARY KEY,question_id uuid,version_id uuid,group_version_id uuid,part_id integer,doc jsonb) ON COMMIT DROP;
DO $$
DECLARE g jsonb;q jsonb;choice text;actor uuid;reviewer uuid;gid uuid;gv uuid;qid uuid;qv uuid;pid integer;tid integer;topic integer;i integer:=0;j integer;group_order integer;
BEGIN
 SELECT teacher_id,admin_id INTO actor,reviewer FROM sample_actors;
 SELECT question_type_id INTO STRICT tid FROM question_types WHERE code='SINGLE_CHOICE';
 SELECT topic_id INTO STRICT topic FROM topics WHERE name='[Mẫu] Công sở và dịch vụ';
 FOR g IN SELECT jsonb_array_elements(document->'full'->'groups') FROM sample_payload LOOP
   gid:=pg_temp.sample_id('group:'||(g->>'key'));gv:=pg_temp.sample_id('group-version:'||(g->>'key'));
   INSERT INTO question_groups(group_id,group_type,created_by,status,created_at,updated_at)
    VALUES(gid,'PART_'||(g->>'partNumber'),actor,'PUBLISHED',now()-interval '9 days',now()-interval '8 days') ON CONFLICT(group_id) DO NOTHING;
   INSERT INTO question_group_versions(group_version_id,group_id,version_number,instruction,passage_text,created_at,status,published_at)
    VALUES(gv,gid,1,g->>'instruction',g->>'passageText',now()-interval '9 days','PUBLISHED',now()-interval '8 days') ON CONFLICT(group_version_id) DO NOTHING;
   UPDATE question_groups SET current_published_version_id=gv WHERE group_id=gid AND current_published_version_id IS NULL;
   IF g->>'audioId' IS NOT NULL THEN INSERT INTO question_group_version_resources(group_version_id,resource_id,display_order,transcript) VALUES(gv,(g->>'audioId')::uuid,1,g->>'transcript') ON CONFLICT DO NOTHING; END IF;
   IF g->>'imageId' IS NOT NULL THEN INSERT INTO question_group_version_resources(group_version_id,resource_id,display_order) VALUES(gv,(g->>'imageId')::uuid,2) ON CONFLICT DO NOTHING; END IF;
 END LOOP;
 FOR q IN SELECT jsonb_array_elements(document->'full'->'questions') FROM sample_payload LOOP
   i:=i+1;qid:=pg_temp.sample_id('question:'||i);qv:=pg_temp.sample_id('question-version:'||i);gid:=NULL;gv:=NULL;group_order:=NULL;
   SELECT part_id INTO STRICT pid FROM toeic_parts WHERE part_number=(q->>'partNumber')::integer;
   IF q->>'groupKey' IS NOT NULL THEN
     gid:=pg_temp.sample_id('group:'||(q->>'groupKey'));gv:=pg_temp.sample_id('group-version:'||(q->>'groupKey'));
     SELECT count(*)+1 INTO group_order FROM sample_questions WHERE group_version_id=gv;
   END IF;
   INSERT INTO questions(question_id,group_id,part_id,topic_id,question_type_id,created_by,status,created_at,updated_at)
    VALUES(qid,gid,pid,topic,tid,actor,'PUBLISHED',now()-interval '9 days',now()-interval '8 days') ON CONFLICT(question_id) DO NOTHING;
   INSERT INTO question_versions(question_version_id,question_id,group_version_id,version_number,question_text,status,explanation,difficulty,reviewed_by,reviewed_at,approved_by,approved_at,published_at,created_at,group_question_order)
    VALUES(qv,qid,gv,1,q->>'questionText','PUBLISHED',q->>'explanation',CASE WHEN (q->>'partNumber')::integer>=6 THEN 'MEDIUM' ELSE 'EASY' END,reviewer,now()-interval '8 days',reviewer,now()-interval '8 days',now()-interval '8 days',now()-interval '9 days',group_order)
    ON CONFLICT(question_version_id) DO NOTHING;
   UPDATE questions SET current_published_version_id=qv WHERE question_id=qid AND current_published_version_id IS NULL;
   j:=0;
   FOR choice IN SELECT jsonb_array_elements_text(q->'choices') LOOP
     j:=j+1;INSERT INTO question_choices(choice_id,question_version_id,choice_label,content,is_correct,display_order)
      VALUES(pg_temp.sample_id('choice:'||i||':'||j),qv,chr(64+j),choice,(q->>'answer')=chr(64+j),j) ON CONFLICT(choice_id) DO NOTHING;
   END LOOP;
   IF q->>'audioId' IS NOT NULL THEN INSERT INTO question_resources(question_version_id,resource_id,transcript) VALUES(qv,(q->>'audioId')::uuid,q->>'transcript') ON CONFLICT DO NOTHING; END IF;
   IF q->>'imageId' IS NOT NULL THEN INSERT INTO question_resources(question_version_id,resource_id) VALUES(qv,(q->>'imageId')::uuid) ON CONFLICT DO NOTHING; END IF;
   INSERT INTO sample_questions VALUES(i,qid,qv,gv,pid,q);
 END LOOP;
END $$;

-- Four published tests + draft, in-review, approved and retired examples.
DO $$
DECLARE e jsonb;d jsonb;clean jsonb;t uuid;v uuid;b uuid;actor uuid;reviewer uuid;q jsonb;i integer;pin record;part record;
BEGIN
 SELECT teacher_id,admin_id INTO actor,reviewer FROM sample_actors;
 FOR e IN SELECT jsonb_array_elements(document->'exams') FROM sample_payload LOOP
   d:=e->'doc';t:=pg_temp.sample_id('test:'||(e->>'key'));v:=pg_temp.sample_id('test-version:'||(e->>'key'));b:=pg_temp.sample_id('blueprint:'||(e->>'key'));
   SELECT jsonb_set(d,'{questions}',jsonb_agg(q.value-'_sourceOrder' ORDER BY q.ordinality)) INTO clean FROM jsonb_array_elements(d->'questions') WITH ORDINALITY q;
   INSERT INTO tests(test_id,created_by,title,description,test_type,status,created_at,updated_at)
    VALUES(t,actor,d->>'title',d->>'description',d->>'testType',e->>'state',now()-interval '8 days',now()-interval '7 days') ON CONFLICT(test_id) DO NOTHING;
   IF e->>'state' IN ('PUBLISHED','RETIRED') THEN
     INSERT INTO test_blueprints(blueprint_id,code,version_number,name,blueprint_type,status,expected_total_questions,created_at,updated_at)
      VALUES(b,'SAMPLE_V1_'||upper(e->>'key'),1,left(d->>'title',150),d->>'testType','ACTIVE',jsonb_array_length(d->'questions'),now()-interval '8 days',now()-interval '7 days') ON CONFLICT(blueprint_id) DO NOTHING;
   END IF;
   INSERT INTO test_versions(test_version_id,test_id,blueprint_id,version_number,duration_seconds,total_questions,max_attempts,status,published_at,retired_at,created_at,draft_document,revision,review_status,reviewed_by,review_note,title,description,test_type,listening_seconds)
    VALUES(v,t,CASE WHEN e->>'state' IN ('PUBLISHED','RETIRED') THEN b ELSE NULL END,1,(d->>'durationSeconds')::integer,jsonb_array_length(d->'questions'),NULL,e->>'state',CASE WHEN e->>'state' IN ('PUBLISHED','RETIRED') THEN now()-interval '7 days' END,CASE WHEN e->>'state'='RETIRED' THEN now()-interval '1 day' END,now()-interval '8 days',clean,0,e->>'review',CASE WHEN e->>'review'='APPROVED' THEN reviewer END,'Dữ liệu mẫu tự biên soạn; kiểm tra audio/đáp án trước khi dùng thực tế.',d->>'title',d->>'description',d->>'testType',CASE WHEN d->>'testType'='FULL' THEN 2700 END)
    ON CONFLICT(test_version_id) DO NOTHING;
   IF e->>'state' IN ('PUBLISHED','RETIRED') THEN
     i:=0;
     FOR q IN SELECT jsonb_array_elements(d->'questions') LOOP
       i:=i+1;SELECT * INTO STRICT pin FROM sample_questions WHERE position=(q->>'_sourceOrder')::integer;
       INSERT INTO test_questions(test_version_id,question_version_id,question_group_version_id,question_order,points) VALUES(v,pin.version_id,pin.group_version_id,i,1) ON CONFLICT DO NOTHING;
     END LOOP;
     FOR part IN SELECT sq.part_id,count(*) n,count(DISTINCT sq.group_version_id) groups_count FROM sample_questions sq
       WHERE sq.position IN (SELECT (q.value->>'_sourceOrder')::integer FROM jsonb_array_elements(d->'questions') q) GROUP BY sq.part_id LOOP
       INSERT INTO test_blueprint_items(blueprint_item_id,blueprint_id,part_id,expected_question_count,expected_group_count)
        VALUES(pg_temp.sample_id('blueprint-item:'||(e->>'key')||':'||part.part_id),b,part.part_id,part.n,part.groups_count) ON CONFLICT DO NOTHING;
     END LOOP;
   END IF;
   INSERT INTO audit_logs(audit_id,actor_user_id,action,entity_type,entity_id,details)
    VALUES(pg_temp.sample_id('audit:'||(e->>'key')),CASE WHEN e->>'state' IN ('PUBLISHED','RETIRED') THEN reviewer ELSE actor END,'SAMPLE_DATA_IMPORTED','TEST_VERSION',v,'{"dataset":"toeic-sample-v1"}') ON CONFLICT(audit_id) DO NOTHING;
 END LOOP;
END $$;

-- Personal vocabulary/SRS. Every existing ACTIVE account gets its own independent copy.
DO $$
DECLARE u record;deck jsonb;word jsonb;di integer;wi integer;did uuid;wid uuid;rs uuid;reps integer;last_review timestamptz;intervals integer[]:=ARRAY[1,3,7,14,30];j integer;
BEGIN
 FOR u IN SELECT * FROM sample_users LOOP
   INSERT INTO learning_profiles(profile_id,user_id,target_score,updated_at) VALUES(pg_temp.sample_id('profile:'||u.user_id),u.user_id,750,now()) ON CONFLICT(user_id) DO NOTHING;
   di:=0;
   FOR deck IN SELECT jsonb_array_elements(document->'decks') FROM sample_payload LOOP
     di:=di+1;wi:=0;did:=pg_temp.sample_id('deck:'||u.user_id||':'||di);
     INSERT INTO vocabulary_decks(deck_id,user_id,name,description) VALUES(did,u.user_id,'[Mẫu] '||(deck->>'name'),'Bộ 12 từ TOEIC tự biên soạn, có ví dụ và lịch ôn mẫu. Có thể sửa, thêm hoặc luyện ngay.') ON CONFLICT(deck_id) DO NOTHING;
     FOR word IN SELECT jsonb_array_elements(deck->'words') LOOP
       wi:=wi+1;wid:=pg_temp.sample_id('vocab:'||u.user_id||':'||di||':'||wi);rs:=pg_temp.sample_id('review-state:'||u.user_id||':'||di||':'||wi);
       INSERT INTO vocabularies(vocabulary_id,created_by,word,meaning,part_of_speech,pronunciation,example,note)
        VALUES(wid,u.user_id,word->>'word',word->>'meaning',word->>'partOfSpeech',word->>'pronunciation',word->>'example','Dữ liệu mẫu TOEIC v1 — có thể sửa theo cách học của bạn.') ON CONFLICT(vocabulary_id) DO NOTHING;
       INSERT INTO deck_vocabularies(deck_id,vocabulary_id) VALUES(did,wid) ON CONFLICT DO NOTHING;
       reps:=CASE WHEN wi<=4 THEN 0 WHEN wi<=8 THEN 2 ELSE 5 END;
       last_review:=CASE WHEN reps=0 THEN NULL WHEN reps=2 THEN now()-interval '4 days' ELSE now()-interval '1 day' END;
       INSERT INTO review_states(review_state_id,user_id,vocabulary_id,repetition_count,interval_days,ease_factor,last_reviewed_at,next_review_at)
        VALUES(rs,u.user_id,wid,reps,CASE WHEN reps=0 THEN 0 ELSE intervals[reps] END,2.5,last_review,CASE WHEN reps=0 THEN now() WHEN reps=2 THEN last_review+interval '3 days' ELSE last_review+interval '30 days' END)
        ON CONFLICT(user_id,vocabulary_id) DO NOTHING;
       -- Only append sample history to the same sample state; never modify an existing personal state.
       FOR j IN 1..reps LOOP
         INSERT INTO review_history(review_history_id,review_state_id,rating,previous_interval,new_interval,reviewed_at)
          SELECT pg_temp.sample_id('review-history:'||u.user_id||':'||di||':'||wi||':'||j),rs,CASE WHEN j=1 THEN 2 WHEN j=2 THEN 3 ELSE 4 END,CASE WHEN j=1 THEN 0 ELSE intervals[j-1] END,intervals[j],last_review-((reps-j)||' days')::interval
          WHERE EXISTS(SELECT 1 FROM review_states WHERE review_state_id=rs) ON CONFLICT(review_history_id) DO NOTHING;
       END LOOP;
     END LOOP;
   END LOOP;
   FOR wi IN 1..4 LOOP
     INSERT INTO bookmarks(user_id,content_id,created_at) VALUES(u.user_id,pg_temp.sample_id('content:'||wi),now()-(wi||' hours')::interval) ON CONFLICT DO NOTHING;
     INSERT INTO learning_content_history(user_id,content_id,content_version_id,last_accessed_at)
      VALUES(u.user_id,pg_temp.sample_id('content:'||wi),pg_temp.sample_id('content-version:'||wi),now()-(wi||' hours')::interval) ON CONFLICT DO NOTHING;
   END LOOP;
 END LOOP;
END $$;

-- Attempts and frozen results: full + reading + short practice graded, one resumable practice.
DO $$
DECLARE u record;config record;a uuid;v uuid;started timestamptz;expiry timestamptz;total integer;pin record;selected uuid;label text;truth text;choice_count integer;correct integer;listening integer;reading integer;result jsonb;result_questions jsonb;part record;created integer;
BEGIN
 FOR u IN SELECT * FROM sample_users LOOP
   FOR config IN SELECT * FROM (VALUES('full',6,3),('reading',3,5),('part5',1,7),('resume',0,0)) AS configs(key,days_ago,wrong_mod) LOOP
     a:=pg_temp.sample_id('attempt:'||u.user_id||':'||config.key);v:=pg_temp.sample_id('test-version:'||CASE WHEN config.key='resume' THEN 'part5' ELSE config.key END);
     SELECT total_questions INTO STRICT total FROM test_versions WHERE test_version_id=v;
     started:=CASE WHEN config.key='resume' THEN now() ELSE now()-(config.days_ago||' days')::interval-interval '2 hours' END;
     expiry:=CASE WHEN config.key='resume' THEN now()+interval '12 minutes' ELSE started+(SELECT duration_seconds FROM test_versions WHERE test_version_id=v)*interval '1 second' END;
     INSERT INTO attempts(attempt_id,user_id,test_version_id,attempt_type,attempt_source_type,expires_at,status,started_at,submitted_at,duration_seconds,total_questions,created_at,listening_ends_at)
      SELECT a,u.user_id,v,test_type,'TEST_VERSION',expiry,CASE WHEN config.key='resume' THEN 'IN_PROGRESS' ELSE 'GRADED' END,started,CASE WHEN config.key<>'resume' THEN expiry END,duration_seconds,total,started,CASE WHEN listening_seconds IS NOT NULL THEN started+listening_seconds*interval '1 second' END FROM test_versions WHERE test_version_id=v
      ON CONFLICT(attempt_id) DO NOTHING;
     GET DIAGNOSTICS created=ROW_COUNT;
     IF created=0 THEN CONTINUE; END IF;
     FOR pin IN SELECT tq.*,qv.question_text,qv.explanation,q.group_id,p.part_number FROM test_questions tq JOIN question_versions qv USING(question_version_id) JOIN questions q USING(question_id) JOIN toeic_parts p USING(part_id) WHERE tq.test_version_id=v ORDER BY tq.question_order LOOP
       INSERT INTO attempt_question_snapshots(snapshot_id,attempt_id,question_version_id,question_group_version_id,question_order,points,created_at)
        VALUES(pg_temp.sample_id('snapshot:'||a||':'||pin.question_order),a,pin.question_version_id,pin.question_group_version_id,pin.question_order,1,started);
       IF (config.key='resume' AND pin.question_order>3) OR (config.key<>'resume' AND pin.question_order%11=0) THEN CONTINUE; END IF;
       SELECT choice_label INTO STRICT truth FROM question_choices WHERE question_version_id=pin.question_version_id AND is_correct;
       SELECT count(*) INTO choice_count FROM question_choices WHERE question_version_id=pin.question_version_id;
       label:=CASE WHEN config.wrong_mod>0 AND pin.question_order%config.wrong_mod=0 THEN chr(65+((ascii(truth)-65+1)%choice_count)) ELSE truth END;
       SELECT choice_id INTO STRICT selected FROM question_choices WHERE question_version_id=pin.question_version_id AND choice_label=label;
       INSERT INTO attempt_answers(attempt_answer_id,attempt_id,question_version_id,selected_choice_id,is_correct,answered_at,updated_at,client_sequence)
        VALUES(pg_temp.sample_id('answer:'||a||':'||pin.question_order),a,pin.question_version_id,selected,CASE WHEN config.key<>'resume' THEN label=truth END,started+pin.question_order*interval '2 seconds',started+pin.question_order*interval '2 seconds',pin.question_order);
     END LOOP;
     IF config.key='resume' THEN CONTINUE; END IF;
     correct:=0;listening:=0;reading:=0;
     FOR part IN SELECT p.part_id,p.part_number,count(*) total,count(*) FILTER(WHERE aa.is_correct) correct FROM attempt_question_snapshots s JOIN question_versions qv USING(question_version_id) JOIN questions q USING(question_id) JOIN toeic_parts p USING(part_id) LEFT JOIN attempt_answers aa ON aa.attempt_id=s.attempt_id AND aa.question_version_id=s.question_version_id WHERE s.attempt_id=a GROUP BY p.part_id,p.part_number LOOP
       correct:=correct+part.correct;
       IF part.part_number<=4 THEN listening:=listening+part.correct; ELSE reading:=reading+part.correct; END IF;
       INSERT INTO attempt_part_results(attempt_part_result_id,attempt_id,part_id,total_questions,correct_count,accuracy)
        VALUES(pg_temp.sample_id('part-result:'||a||':'||part.part_id),a,part.part_id,part.total,part.correct,round(100.0*part.correct/part.total,2));
     END LOOP;
     SELECT jsonb_agg(jsonb_build_object('id',s.question_version_id,'orderIndex',s.question_order,'partNumber',p.part_number,'content',qv.question_text,'passage',g.passage_text,'instruction',g.instruction,'groupVersionId',s.question_group_version_id,'userAnswer',chosen.choice_label,'correctOption',answer.choice_label,'isCorrect',coalesce(aa.is_correct,false),'explanation',qv.explanation,
       'options',(SELECT jsonb_agg(jsonb_build_object('key',c.choice_label,'text',c.content,'choiceId',c.choice_id) ORDER BY c.display_order) FROM question_choices c WHERE c.question_version_id=s.question_version_id),
       'assets',coalesce((SELECT jsonb_agg(jsonb_build_object('resourceId',r.resource_id,'resourceType',m.resource_type,'scope',r.scope,'transcript',r.transcript)) FROM (SELECT resource_id,transcript,'QUESTION' scope FROM question_resources WHERE question_version_id=s.question_version_id UNION ALL SELECT resource_id,transcript,'GROUP' scope FROM question_group_version_resources WHERE group_version_id=s.question_group_version_id) r JOIN media_resources m USING(resource_id)), '[]'::jsonb)
       ) ORDER BY s.question_order) INTO result_questions
       FROM attempt_question_snapshots s JOIN question_versions qv USING(question_version_id) JOIN questions q USING(question_id) JOIN toeic_parts p USING(part_id)
       LEFT JOIN question_group_versions g ON g.group_version_id=s.question_group_version_id
       LEFT JOIN attempt_answers aa ON aa.attempt_id=s.attempt_id AND aa.question_version_id=s.question_version_id
       LEFT JOIN question_choices chosen ON chosen.choice_id=aa.selected_choice_id
       JOIN question_choices answer ON answer.question_version_id=s.question_version_id AND answer.is_correct WHERE s.attempt_id=a;
     result:=jsonb_build_object('attemptId',a,'status','GRADED','submittedAt',expiry,'rawCorrect',correct,'totalQuestions',total,'accuracyRate',round(100.0*correct/total),'estimatedListeningScore',NULL,'estimatedReadingScore',NULL,'estimatedTotalScore',NULL,'scoreLabel','Bài mẫu: chỉ số câu đúng, chưa có bảng quy đổi đã thẩm định.','questions',result_questions,
       'parts',(SELECT jsonb_agg(jsonb_build_object('partNumber',p.part_number,'totalQuestions',r.total_questions,'correctCount',r.correct_count) ORDER BY p.part_number) FROM attempt_part_results r JOIN toeic_parts p USING(part_id) WHERE r.attempt_id=a));
     UPDATE attempts SET correct_count=correct,listening_correct_count=listening,reading_correct_count=reading,result_document=result WHERE attempt_id=a;
   END LOOP;
 END LOOP;
END $$;

-- Verify before commit. A failure rolls back this entire import.
DO $$
BEGIN
 IF (SELECT count(*) FROM sample_questions)<>200 THEN RAISE EXCEPTION 'Expected 200 questions'; END IF;
 IF EXISTS(SELECT 1 FROM sample_questions s WHERE (SELECT count(*) FROM question_choices c WHERE c.question_version_id=s.version_id AND is_correct)<>1) THEN RAISE EXCEPTION 'Invalid answer keys'; END IF;
 IF EXISTS(SELECT 1 FROM sample_questions s WHERE s.group_version_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM question_group_versions g WHERE g.group_version_id=s.group_version_id AND g.status='PUBLISHED')) THEN RAISE EXCEPTION 'Invalid group publication'; END IF;
END $$;
SELECT 'Sample data ready' AS result,
 (SELECT count(*) FROM sample_users) AS accounts_with_personal_data,
 12 AS lessons,200 AS bank_questions,4 AS published_tests,3 AS decks_per_account,36 AS words_per_account,
 3 AS graded_attempts_per_account,1 AS resumable_attempt_per_account;
COMMIT;
