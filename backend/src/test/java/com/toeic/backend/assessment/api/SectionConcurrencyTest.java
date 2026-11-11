package com.toeic.backend.assessment.api;

import com.toeic.backend.assessment.application.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

class SectionConcurrencyTest extends PracticeTestSupport {
    private static final String EXAM="exam:manage:self";
    @Autowired SectionSessionService sections;
    @Autowired PracticeSubmissionService submissions;
    private static void await(CountDownLatch latch) {
        try{assertTrue(latch.await(10,TimeUnit.SECONDS),"Latch timeout");}
        catch(InterruptedException ex){Thread.currentThread().interrupt();throw new AssertionError(ex);}
    }
    private void waiting()throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<until){
            if(count("select count(*) from pg_stat_activity where datname=current_database() and pid<>pg_backend_pid() and wait_event_type='Lock'")>0)return;
            Thread.sleep(10);
        }
        fail("Expected PostgreSQL lock wait");
    }
    private UUID attempt(){
        authenticate(ACTOR,EXAM);
        try{return sections.create(ACTOR,new SectionStartQuery("LISTENING",7,1)).attemptId();}
        finally{SecurityContextHolder.clearContext();}
    }
    private UUID selected(){return jdbc.queryForObject("select choice_id from question_choices where question_version_id=? and choice_label='A'",UUID.class,v(3));}
    @Test void saveCommitBeforeSectionSubmitIsIncludedInGrade()throws Exception {
        var attempt=attempt();var choice=selected();var held=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var save=pool.submit(()->{
                authenticate(ACTOR,EXAM);
                try{return new TransactionTemplate(transactions).execute(status->{var result=submissions.save(ACTOR,attempt,v(3),choice);held.countDown();await(release);return result;});}
                finally{SecurityContextHolder.clearContext();}
            });
            await(held);
            var submit=pool.submit(()->{authenticate(ACTOR,EXAM);try{return submissions.submit(ACTOR,attempt);}finally{SecurityContextHolder.clearContext();}});
            waiting();assertFalse(submit.isDone());release.countDown();assertEquals(choice,save.get(10,TimeUnit.SECONDS).selectedChoiceId());
            var result=submit.get(10,TimeUnit.SECONDS);assertEquals(1,result.rawScore());assertEquals(4,result.parts().size());
            assertEquals(choice,result.questions().stream().filter(q->q.questionVersionId().equals(v(3))).findFirst().orElseThrow().selectedChoiceId());
        }finally{release.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @ParameterizedTest @ValueSource(strings={"save","clear","submit"})
    void sectionSubmitCommitsOnceAndRejectsLaterWritesOrReplaysContendingSubmit(String operation)throws Exception {
        var attempt=attempt();var choice=selected();authenticate(ACTOR,EXAM);submissions.save(ACTOR,attempt,v(3),choice);SecurityContextHolder.clearContext();
        var held=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var submit=pool.submit(()->{
                authenticate(ACTOR,EXAM);
                try{return new TransactionTemplate(transactions).execute(status->{var result=submissions.submit(ACTOR,attempt);held.countDown();await(release);return result;});}
                finally{SecurityContextHolder.clearContext();}
            });
            await(held);
            var contender=pool.submit(()->{
                authenticate(ACTOR,EXAM);
                try{return operation.equals("submit")?submissions.submit(ACTOR,attempt):submissions.save(ACTOR,attempt,v(3),operation.equals("clear")?null:choice);}
                finally{SecurityContextHolder.clearContext();}
            });
            waiting();assertFalse(contender.isDone());release.countDown();var result=submit.get(10,TimeUnit.SECONDS);
            if(operation.equals("submit"))assertEquals(result,contender.get(10,TimeUnit.SECONDS));
            else{
                var failure=assertThrows(ExecutionException.class,()->contender.get(10,TimeUnit.SECONDS));
                assertEquals("ATTEMPT_NOT_EDITABLE",((PracticeException)failure.getCause()).getCode());
            }
            assertEquals(1,result.rawScore());assertEquals(7,count("select count(*) from attempt_answers where attempt_id=?",attempt));
            assertEquals(4,count("select count(*) from attempt_part_results where attempt_id=?",attempt));
        }finally{release.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @ParameterizedTest @ValueSource(strings={"save","submit"})
    void writerWaitingAcrossDeadlineRechecksExpiryAndOnlySubmitMayGrade(String operation)throws Exception {
        var attempt=attempt();var choice=selected();var held=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var expiry=pool.submit(()->new TransactionTemplate(transactions).execute(status->{
                jdbc.update("update attempts set started_at=?,expires_at=? where attempt_id=?",OffsetDateTime.ofInstant(NOW.minusSeconds(60),ZoneOffset.UTC),OffsetDateTime.ofInstant(NOW,ZoneOffset.UTC),attempt);
                held.countDown();await(release);return true;
            }));
            await(held);
            var write=pool.submit(()->{
                authenticate(ACTOR,EXAM);try{return operation.equals("save")?submissions.save(ACTOR,attempt,v(3),choice):submissions.submit(ACTOR,attempt);}
                finally{SecurityContextHolder.clearContext();}
            });
            waiting();assertFalse(write.isDone());release.countDown();assertTrue(expiry.get(10,TimeUnit.SECONDS));
            if(operation.equals("save")){
                var error=assertThrows(ExecutionException.class,()->write.get(10,TimeUnit.SECONDS));
                assertEquals("ATTEMPT_EXPIRED",((PracticeException)error.getCause()).getCode());
                assertEquals(0,count("select count(*) from attempt_answers where attempt_id=?",attempt));
            }else{
                var result=(PracticeResultDtos.Result)write.get(10,TimeUnit.SECONDS);assertEquals("GRADED",result.status());
                assertEquals(7,result.unansweredCount());assertEquals(0,result.rawScore());
            }
        }finally{release.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @Test void sectionGradingFailureBeforeCommitRollsBackMarksPartsAndHeader() {
        var attempt=attempt();authenticate(ACTOR,EXAM);submissions.save(ACTOR,attempt,v(3),selected());
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).execute(status->{
            assertEquals(1,submissions.submit(ACTOR,attempt).rawScore());throw new IllegalStateException("Fixture commit failure");
        }));
        assertEquals(0,count("select count(*) from attempt_answers where attempt_id=? and is_correct is not null",attempt));
        assertEquals(0,count("select count(*) from attempt_part_results where attempt_id=?",attempt));
        assertEquals(1,count("select count(*) from attempts where attempt_id=? and status='IN_PROGRESS' and submitted_at is null",attempt));
    }
}
