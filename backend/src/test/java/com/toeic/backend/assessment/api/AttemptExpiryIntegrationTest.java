package com.toeic.backend.assessment.api;

import com.toeic.backend.assessment.application.*;
import com.toeic.backend.questionbank.application.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AttemptExpiryIntegrationTest extends FullMockTestSupport {
    @MockitoBean(name="practiceClock") Clock controlled;
    @MockitoSpyBean PinnedQuestionGrading keys;
    PinnedQuestionGrading keyTarget;
    @Autowired AttemptExpiryWorker worker;
    @Autowired AttemptExpiryTransaction expiry;
    @BeforeEach void clock(){keyTarget=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(keys);when(controlled.getZone()).thenReturn(ZoneOffset.UTC);at(0);}
    void at(long seconds){when(controlled.instant()).thenReturn(NOW.plusSeconds(seconds));}
    JsonNode session()throws Exception{return start(published());}
    void choose(UUID id)throws Exception{
        var choice=jdbc.queryForObject("SELECT choice_id FROM question_choices WHERE question_version_id=? AND is_correct",UUID.class,v(1));
        call(put(API+"/"+id+"/answers/"+v(1)).contentType(APPLICATION_JSON).content("{\"selectedChoiceId\":\""+choice+"\"}"),token(ACTOR)).andExpect(status().isOk());
    }
    String state(UUID id){return jdbc.queryForObject("SELECT status FROM attempts WHERE attempt_id=?",String.class,id);}
    @Test void noRequestsOrValidTokenNeededAndOnlySavedBeforeDeadlineIsGraded()throws Exception{
        var s=session();UUID id=id(s);choose(id);String snapshot=jdbc.queryForObject("SELECT test_blueprint_snapshot::text FROM attempts WHERE attempt_id=?",String.class,id);
        jdbc.update("DELETE FROM auth_sessions WHERE user_id=?",ACTOR);jdbc.update("DELETE FROM user_roles WHERE user_id=?",ACTOR);
        at(601);SecurityContextHolder.clearContext();var batch=worker.runBatch();assertEquals(1,batch.graded());assertEquals("GRADED",state(id));
        assertEquals(1,count("SELECT correct_count FROM attempts WHERE attempt_id=?",id));assertEquals(600,count("SELECT duration_seconds FROM attempts WHERE attempt_id=?",id));
        assertEquals(13,count("SELECT count(*) FROM attempt_answers WHERE attempt_id=?",id));assertEquals(7,count("SELECT count(*) FROM attempt_part_results WHERE attempt_id=?",id));
        assertEquals(snapshot,jdbc.queryForObject("SELECT test_blueprint_snapshot::text FROM attempts WHERE attempt_id=?",String.class,id));assertEquals(0,worker.runBatch().graded());
    }
    @Test void twoWorkersAndManualSubmitGradeExactlyOnce()throws Exception{
        var s=session();UUID id=id(s);choose(id);String learner=token(ACTOR);at(601);SecurityContextHolder.clearContext();clearInvocations(keyTarget);
        var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(3);
        try{
            var a=pool.submit(()->{gate.await();return worker.runBatch();});var b=pool.submit(()->{gate.await();return new AttemptExpiryWorker(expiry,50).runBatch();});
            var manual=pool.submit(()->{gate.await();return call(post(API+"/"+id+"/submit"),learner).andReturn().getResponse().getStatus();});gate.countDown();
            a.get(20,TimeUnit.SECONDS);b.get(20,TimeUnit.SECONDS);assertEquals(200,manual.get(20,TimeUnit.SECONDS));
        }finally{pool.shutdownNow();}
        assertEquals("GRADED",state(id));assertEquals(7,count("SELECT count(*) FROM attempt_part_results WHERE attempt_id=?",id));verify(keyTarget,times(13)).key(any(UUID.class),nullable(UUID.class));
        String result=call(get(API+"/"+id+"/result"),learner).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(result,call(post(API+"/"+id+"/submit"),learner).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    @Test void failedGradingRollsBackBacksOffAndAnotherWorkerInstanceRetriesAfterRestart()throws Exception{
        UUID id=id(session());choose(id);at(601);SecurityContextHolder.clearContext();doThrow(new PracticeQuestionCatalog.Unavailable(false)).when(keyTarget).key(eq(v(3)),isNull());
        assertEquals(1,worker.runBatch().failed());assertEquals("IN_PROGRESS",state(id));assertEquals(0,count("SELECT count(*) FROM attempt_answers WHERE attempt_id=? AND is_correct IS NOT NULL",id));assertEquals(0,count("SELECT count(*) FROM attempt_part_results WHERE attempt_id=?",id));
        assertEquals(1,count("SELECT failure_count FROM attempt_expiry_retries WHERE attempt_id=?",id));
        var restarted=new AttemptExpiryWorker(expiry,50);assertEquals(0,restarted.runBatch().examined());reset(keyTarget);at(607);assertEquals(1,restarted.runBatch().graded());assertEquals(0,count("SELECT count(*) FROM attempt_expiry_retries WHERE attempt_id=?",id));assertEquals(0,restarted.runBatch().graded());
    }
    @Test void manualRetryClearsDurableWorkerFailureAndArchivedBankStillUsesPinnedKey()throws Exception{
        UUID id=id(session());choose(id);String learner=token(ACTOR);at(601);SecurityContextHolder.clearContext();doThrow(new PracticeQuestionCatalog.Unavailable(false)).when(keyTarget).key(eq(v(3)),isNull());
        assertEquals(1,worker.runBatch().failed());reset(keyTarget);jdbc.update("UPDATE questions SET status='ARCHIVED' WHERE question_id=?",q(1));
        call(post(API+"/"+id+"/submit"),learner).andExpect(status().isOk()).andExpect(jsonPath("$.correctCount").value(1));
        assertEquals(0,count("SELECT count(*) FROM attempt_expiry_retries WHERE attempt_id=?",id));assertEquals(0,worker.runBatch().examined());
    }
    @Test void batchLimitAndSkipLockedAllowOtherAttemptsToProgress()throws Exception{
        var d=published();UUID first=id(start(d)),second=id(start(d));at(601);SecurityContextHolder.clearContext();
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newSingleThreadExecutor();
        try{
            var blocker=pool.submit(()->new TransactionTemplate(transactions).execute(s->{jdbc.query("SELECT attempt_id FROM attempts WHERE attempt_id=? FOR UPDATE",(r,n)->r.getObject(1),first);locked.countDown();try{assertTrue(release.await(10,TimeUnit.SECONDS));}catch(InterruptedException e){throw new IllegalStateException(e);}return null;}));
            assertTrue(locked.await(5,TimeUnit.SECONDS));assertEquals(1,new AttemptExpiryWorker(expiry,1).runBatch().graded());assertEquals("IN_PROGRESS",state(first));assertEquals("GRADED",state(second));release.countDown();blocker.get(15,TimeUnit.SECONDS);assertEquals(1,worker.runBatch().graded());
        }finally{release.countDown();pool.shutdownNow();}
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"wrong-version","malformed-json"})
    void failingAttemptDoesNotStarveAnotherDueAttempt(String kind)throws Exception{
        var d=published();UUID bad=id(start(d)),good=id(start(d));
        if(kind.equals("wrong-version"))jdbc.update("UPDATE attempts SET test_blueprint_snapshot=jsonb_set(test_blueprint_snapshot,'{versionId}','\"11111111-1111-1111-1111-111111111111\"') WHERE attempt_id=?",bad);
        else jdbc.update("UPDATE attempts SET test_blueprint_snapshot=jsonb_set(test_blueprint_snapshot,'{blueprint}','[]') WHERE attempt_id=?",bad);
        at(601);SecurityContextHolder.clearContext();var batch=worker.runBatch();assertEquals(1,batch.failed());assertEquals(1,batch.graded());assertEquals("IN_PROGRESS",state(bad));assertEquals("GRADED",state(good));
    }
    @Test void practiceAndSectionTimeoutPoliciesAreUntouched()throws Exception{
        UUID full=id(session());String learner=token(ACTOR);UUID practice=id(create(learner,List.of(2),1));
        var section=tree(call(post(API).contentType(APPLICATION_JSON).content("{\"sourceType\":\"SECTION_SNAPSHOT\",\"section\":{\"type\":\"READING\",\"questionCount\":6,\"durationMinutes\":1}}"),learner).andExpect(status().isCreated()));
        at(86401);SecurityContextHolder.clearContext();assertEquals(1,worker.runBatch().graded());assertEquals("GRADED",state(full));assertEquals("IN_PROGRESS",state(practice));assertEquals("IN_PROGRESS",state(id(section)));
    }
    @Test void notYetDueOrLegacyTestVersionWithoutSnapshotIsNotClaimed()throws Exception{
        UUID id=id(session());SecurityContextHolder.clearContext();assertEquals(0,worker.runBatch().examined());at(601);jdbc.update("UPDATE attempts SET test_blueprint_snapshot=NULL WHERE attempt_id=?",id);assertEquals(0,worker.runBatch().examined());assertEquals("IN_PROGRESS",state(id));
    }
    @Test void equalityAtDeadlineRejectsSaveAndWorkerQuarantinesUnexpectedLatePersistedSelection()throws Exception{
        UUID id=id(session());choose(id);String learner=token(ACTOR);at(600);
        call(put(API+"/"+id+"/answers/"+v(1)).contentType(APPLICATION_JSON).content("{\"selectedChoiceId\":null}"),learner).andExpect(status().isConflict());
        // Deliberately invalid data fixture, never an API backdoor or applied-history mutation.
        jdbc.update("UPDATE attempt_answers SET updated_at=(SELECT expires_at FROM attempts WHERE attempt_id=?) WHERE attempt_id=?",id,id);
        SecurityContextHolder.clearContext();assertEquals(1,worker.runBatch().failed());assertEquals("IN_PROGRESS",state(id));assertEquals(0,count("SELECT count(*) FROM attempt_answers WHERE attempt_id=? AND is_correct IS NOT NULL",id));
    }
}
