package com.toeic.backend.questionbank.api;

import com.toeic.backend.questionbank.application.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class QuestionAuthoringConcurrencyTest extends QuestionAuthoringTestSupport {
    private static void await(CountDownLatch latch) {
        try { if(!latch.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Latch timeout"); }
        catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IllegalStateException(ex);}
    }
    private void locksWaiting() throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<end) {
            if(count("select count(*) from pg_stat_activity where datname=current_database() and pid<>pg_backend_pid() and wait_event_type='Lock'")>0)return;
            Thread.sleep(10);
        }
        fail("Expected database lock wait");
    }
    @Test void concurrentQuestionEditsUseOnlyOneWorkingDraft() throws Exception {
        var created=create(token(ACTOR),1,null);publish(false,qid(created),qv(created,0));var edit=command(Map.of("questionText","Concurrent"));
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Object> work=()->{authenticate(ACTOR,"question:manage:self","question:read");try{await(start);return service.updateQuestion(ACTOR,qid(created),edit);}finally{SecurityContextHolder.clearContext();}};
            var a=pool.submit(work);var b=pool.submit(work);start.countDown();a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);
            assertEquals(2,count("select count(*) from question_versions where question_id=?",qid(created)));
            assertEquals(1,count("select count(*) from question_versions where question_id=? and status='DRAFT'",qid(created)));
            assertEquals("Q03 original prompt",jdbc.queryForObject("select question_text from question_versions where question_version_id=?",String.class,qv(created,0)));
        } finally {pool.shutdownNow();}
    }
    @Test void concurrentGroupEditsUseOnlyOneWorkingDraft() throws Exception {
        var created=group(token(ACTOR));publish(true,gid(created),gv(created,0));
        var edit=json.readValue("{\"passageText\":\"Concurrent\"}",QuestionAuthoringRequest.class).command(true);
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Object> work=()->{authenticate(ACTOR,"question:manage:self","question:read");try{await(start);return service.updateGroup(ACTOR,gid(created),edit);}finally{SecurityContextHolder.clearContext();}};
            var a=pool.submit(work);var b=pool.submit(work);start.countDown();a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);
            assertEquals(2,count("select count(*) from question_group_versions where group_id=?",gid(created)));
            assertEquals("Published passage",jdbc.queryForObject("select passage_text from question_group_versions where group_version_id=?",String.class,gv(created,0)));
        } finally {pool.shutdownNow();}
    }
    @Test void archiveAndUpdateSerializeWithoutChangingPublishedVersion() throws Exception {
        var created=create(token(ACTOR),1,null);publish(false,qid(created),qv(created,0));var edit=command(Map.of("questionText","Race"));
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var update=pool.submit(()->{authenticate(ACTOR,"question:manage:self","question:read");try{await(start);service.updateQuestion(ACTOR,qid(created),edit);return 200;}catch(QuestionAuthoringException ex){return ex.getStatus();}finally{SecurityContextHolder.clearContext();}});
            var archive=pool.submit(()->{authenticate(ACTOR,"question:manage:self","question:read");try{await(start);service.archiveQuestion(ACTOR,qid(created));}finally{SecurityContextHolder.clearContext();}});
            start.countDown();assertTrue(Set.of(200,409).contains(update.get(10,TimeUnit.SECONDS)));archive.get(10,TimeUnit.SECONDS);
            assertEquals("ARCHIVED",jdbc.queryForObject("select status from questions where question_id=?",String.class,qid(created)));
            assertEquals("Q03 original prompt",jdbc.queryForObject("select question_text from question_versions where question_version_id=?",String.class,qv(created,0)));
        } finally {pool.shutdownNow();}
    }
    @ParameterizedTest @CsvSource({"grant,false","grant,true","visibility,false","visibility,true"})
    void revocationFirstMakesAttachOrCloneFailAtomically(String mutation,boolean clone) throws Exception {
        int resource=mutation.equals("grant")?5:1;var body=payload(1,null);body.put("assets",List.of(Map.of("mediaId",media(resource))));
        JsonNode old=clone?create(token(ACTOR),body):null;if(clone)publish(false,qid(old),qv(old,0));
        var edit=clone?command(Map.of("questionText","After revoke")):command(body);
        var held=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var revoker=pool.submit(()->new TransactionTemplate(transactions).execute(status->{
                if(mutation.equals("grant"))revokeGrant();else jdbc.update("update media_resources set visibility='PRIVATE' where resource_id=?",media(resource));
                held.countDown();await(release);return null;
            }));assertTrue(held.await(5,TimeUnit.SECONDS));
            var writer=pool.submit(()->{authenticate(ACTOR,"question:manage:self","question:read");try{
                if(clone)service.updateQuestion(ACTOR,qid(old),edit);else service.createQuestion(ACTOR,edit);return 200;
            }catch(QuestionAuthoringException ex){return ex.getStatus();}finally{SecurityContextHolder.clearContext();}});
            locksWaiting();assertFalse(writer.isDone());release.countDown();revoker.get(10,TimeUnit.SECONDS);
            assertEquals(clone?409:404,writer.get(10,TimeUnit.SECONDS));
            assertEquals(clone?1:0,count("select count(*) from questions where created_by=?",ACTOR));
            if(clone)assertEquals(1,count("select count(*) from question_versions where question_id=?",qid(old)));
        } finally {release.countDown();pool.shutdownNow();}
    }
    @ParameterizedTest @CsvSource({"grant,false","grant,true","visibility,false","visibility,true"})
    void attachOrCloneFirstHoldsMediaAndGrantLocksUntilCommit(String mutation,boolean clone) throws Exception {
        int resource=mutation.equals("grant")?5:1;var body=payload(1,null);body.put("assets",List.of(Map.of("mediaId",media(resource))));
        JsonNode old=clone?create(token(ACTOR),body):null;if(clone)publish(false,qid(old),qv(old,0));
        var edit=clone?command(Map.of("questionText","Committed")):command(body);
        var held=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var writer=pool.submit(()->{authenticate(ACTOR,"question:manage:self","question:read");try{
                return new TransactionTemplate(transactions).execute(status->{var result=clone?service.updateQuestion(ACTOR,qid(old),edit):service.createQuestion(ACTOR,edit);held.countDown();await(release);return result;});
            }finally{SecurityContextHolder.clearContext();}});assertTrue(held.await(5,TimeUnit.SECONDS));
            var revoker=pool.submit(()->{if(mutation.equals("grant"))revokeGrant();else jdbc.update("update media_resources set visibility='PRIVATE' where resource_id=?",media(resource));});
            locksWaiting();assertFalse(revoker.isDone());release.countDown();writer.get(10,TimeUnit.SECONDS);revoker.get(10,TimeUnit.SECONDS);
            assertEquals(clone?2:1,count("select count(*) from question_versions v join questions q using(question_id) where q.created_by=?",ACTOR));
        } finally {release.countDown();pool.shutdownNow();}
    }
    @Test void multipleMediaAreLockedInUuidOrderEvenWhenPayloadIsReversed() throws Exception {
        var body=payload(1,null);body.put("assets",List.of(Map.of("mediaId",media(9)),Map.of("mediaId",media(1))));var edit=command(body);
        var held=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var mutation=pool.submit(()->new TransactionTemplate(transactions).execute(status->{
                jdbc.update("update media_resources set visibility='PUBLIC' where resource_id=?",media(1));held.countDown();await(release);
                jdbc.update("update media_resources set visibility='PRIVATE' where resource_id=?",media(9));return null;
            }));assertTrue(held.await(5,TimeUnit.SECONDS));
            var writer=pool.submit(()->{authenticate(ACTOR,"question:manage:self","question:read");try{service.createQuestion(ACTOR,edit);return 200;}catch(QuestionAuthoringException ex){return ex.getStatus();}finally{SecurityContextHolder.clearContext();}});
            locksWaiting();release.countDown();mutation.get(10,TimeUnit.SECONDS);assertEquals(404,writer.get(10,TimeUnit.SECONDS));
            assertEquals(0,count("select count(*) from questions where created_by=?",ACTOR));
        } finally {release.countDown();pool.shutdownNow();}
    }
    @Test void outerTransactionRollbackRemovesAggregateChoicesAndMediaLinks() throws Exception {
        var body=payload(1,null);body.put("assets",List.of(Map.of("mediaId",media(1),"transcript","Rollback")));var edit=command(body);
        authenticate(ACTOR,"question:manage:self","question:read");
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).execute(status->{service.createQuestion(ACTOR,edit);throw new IllegalStateException("rollback fixture");}));
        assertEquals(0,count("select count(*) from questions where created_by=?",ACTOR));
        assertEquals(0,count("select count(*) from question_resources where resource_id=?",media(1)));
    }
}
