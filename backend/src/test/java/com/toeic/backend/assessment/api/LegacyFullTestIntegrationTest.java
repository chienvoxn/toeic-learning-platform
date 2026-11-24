package com.toeic.backend.assessment.api;

import java.util.*;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import com.toeic.backend.assessment.application.AttemptExpiryWorker;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Reproduces develop's original 200-pin FULL_TEST factory shape, not a member DB or Flyway history. */
class LegacyFullTestIntegrationTest extends PracticeTestSupport {
    @Autowired AttemptExpiryWorker worker;
    UUID test=UUID.randomUUID(),version=UUID.randomUUID(),attempt=UUID.randomUUID();
    @BeforeEach void legacyFactoryFixture(){
        jdbc.update("""
            INSERT INTO questions(question_id,part_id,question_type_id,created_by,status,created_at,updated_at)
            SELECT ('e6000000-0000-0000-0000-'||lpad(n::text,12,'0'))::uuid,
              (SELECT part_id FROM toeic_parts WHERE part_number=5),
              (SELECT question_type_id FROM question_types WHERE code='SINGLE_CHOICE'),?,'PUBLISHED',now(),now()
            FROM generate_series(1,200)n
            """,TEACHER);
        jdbc.update("""
            INSERT INTO question_versions(question_version_id,question_id,version_number,question_text,explanation,status,published_at,created_at)
            SELECT ('e6100000-0000-0000-0000-'||lpad(n::text,12,'0'))::uuid,
              ('e6000000-0000-0000-0000-'||lpad(n::text,12,'0'))::uuid,1,'Legacy prompt '||n,'Pinned legacy explanation','PUBLISHED','2026-01-01',now()
            FROM generate_series(1,200)n
            """);
        jdbc.update("""
            UPDATE questions SET current_published_version_id=('e6100000-0000-0000-0000-'||right(question_id::text,12))::uuid
            WHERE question_id::text LIKE 'e6000000-%'
            """);
        jdbc.update("""
            INSERT INTO question_choices(choice_id,question_version_id,choice_label,content,is_correct,display_order)
            SELECT ('e6200000-0000-0000-'||lpad(c::text,4,'0')||'-'||lpad(n::text,12,'0'))::uuid,
              ('e6100000-0000-0000-0000-'||lpad(n::text,12,'0'))::uuid,chr(64+c),'Legacy choice '||c,c=1,c
            FROM generate_series(1,200)n CROSS JOIN generate_series(1,4)c
            """);
        jdbc.update("INSERT INTO tests(test_id,created_by,title,test_type,status,created_at,updated_at) VALUES (?,?,'Legacy full test','FULL_TEST','PUBLISHED',now(),now())",test,TEACHER);
        jdbc.update("INSERT INTO test_versions(test_version_id,test_id,version_number,duration_seconds,total_questions,status,published_at,created_at) VALUES (?,?,1,7200,200,'PUBLISHED','2026-01-01',now())",version,test);
        var now=OffsetDateTime.ofInstant(NOW,java.time.ZoneOffset.UTC);
        jdbc.update("INSERT INTO attempts(attempt_id,user_id,test_version_id,attempt_type,attempt_source_type,status,started_at,expires_at,created_at,total_questions) VALUES (?,?,?,'FULL_TEST','TEST_VERSION','IN_PROGRESS',?,?,?,200)",attempt,ACTOR,version,now,now.plusMinutes(120),now);
        jdbc.update("""
            INSERT INTO attempt_question_snapshots(snapshot_id,attempt_id,question_version_id,question_order,created_at)
            SELECT gen_random_uuid(),?,('e6100000-0000-0000-0000-'||lpad(n::text,12,'0'))::uuid,n,now() FROM generate_series(1,200)n
            """,attempt);
    }
    @AfterEach void removeLegacy(){
        for(String table:List.of("attempt_answers","attempt_part_results","attempt_question_snapshots","attempt_expiry_retries"))jdbc.update("DELETE FROM "+table+" WHERE attempt_id=?",attempt);
        jdbc.update("DELETE FROM attempts WHERE attempt_id=?",attempt);jdbc.update("DELETE FROM test_versions WHERE test_id=?",test);jdbc.update("DELETE FROM tests WHERE test_id=?",test);
        jdbc.update("DELETE FROM question_choices WHERE question_version_id::text LIKE 'e6100000-%'");
        jdbc.update("UPDATE questions SET current_published_version_id=NULL WHERE question_id::text LIKE 'e6000000-%'");
        jdbc.update("DELETE FROM question_versions WHERE question_version_id::text LIKE 'e6100000-%'");jdbc.update("DELETE FROM questions WHERE question_id::text LIKE 'e6000000-%'");
    }
    @Test void oldPinSaveResultAndHistoryStayReadableAndReplayUsesSavedKey() throws Exception {
        String learner=token(ACTOR);var old=read(learner,attempt);safe(old);assertFalse(old.hasNonNull("test"));assertEquals(200,old.get("questions").size());
        String q="e6100000-0000-0000-0000-000000000001",choice="e6200000-0000-0000-0001-000000000001";
        call(put(API+"/"+attempt+"/answers/"+q).contentType(APPLICATION_JSON).content("{\"selectedChoiceId\":\""+choice+"\"}"),learner).andExpect(status().isOk());
        assertEquals(choice,read(learner,attempt).get("answers").get(0).get("selectedChoiceId").asString());
        var result=tree(call(post(API+"/"+attempt+"/submit"),learner).andExpect(status().isOk()));assertEquals(1,result.get("correctCount").asInt());assertEquals(199,result.get("unansweredCount").asInt());
        jdbc.update("UPDATE question_choices SET is_correct=NOT is_correct WHERE question_version_id=?::uuid AND choice_label IN ('A','B')",q);
        assertEquals(result,tree(call(get(API+"/"+attempt+"/result"),learner).andExpect(status().isOk())));
        assertEquals(result,tree(call(post(API+"/"+attempt+"/submit"),learner).andExpect(status().isOk())));
        call(get(API+"?page=0&size=20"),learner).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].attemptType").value("FULL_TEST"));
        call(get(API+"/"+attempt),token(OTHER)).andExpect(status().isNotFound());
        assertNull(jdbc.queryForObject("SELECT test_blueprint_snapshot FROM attempts WHERE attempt_id=?",Object.class,attempt));
    }
    @Test void malformedLegacyShapeIsRejectedWithoutMutation() throws Exception {
        jdbc.update("UPDATE attempts SET expires_at=expires_at+interval '1 second' WHERE attempt_id=?",attempt);
        String learner=token(ACTOR);call(get(API+"/"+attempt),learner).andExpect(status().isNotFound());
        call(post(API+"/"+attempt+"/submit"),learner).andExpect(status().isNotFound());
        call(get(API+"?page=0&size=20"),learner).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        assertEquals(0,count("SELECT count(*) FROM attempt_answers WHERE attempt_id=?",attempt));
    }
    @Test void legacyExpiryPolicyIsUnchangedAndWorkerDoesNotClaimIt() throws Exception {
        jdbc.update("UPDATE attempts SET started_at=started_at-interval '121 minutes',expires_at=expires_at-interval '121 minutes' WHERE attempt_id=?",attempt);
        String learner=token(ACTOR);call(post(API+"/"+attempt+"/submit"),learner).andExpect(status().isConflict());
        worker.runBatch();assertEquals("IN_PROGRESS",jdbc.queryForObject("SELECT status FROM attempts WHERE attempt_id=?",String.class,attempt));
        assertEquals(0,count("SELECT count(*) FROM attempt_answers WHERE attempt_id=?",attempt));
    }
}
