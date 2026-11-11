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

class PracticeAnswerConcurrencyTest extends PracticeTestSupport {
    @Autowired PracticeSubmissionService submissions;
    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(10,TimeUnit.SECONDS),"Latch timeout"); }
        catch(InterruptedException ex) { Thread.currentThread().interrupt();throw new AssertionError(ex); }
    }
    private void lockWaiting() throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<until) {
            if(count("select count(*) from pg_stat_activity where datname=current_database() and pid<>pg_backend_pid() and wait_event_type='Lock'")>0)return;
            Thread.sleep(10);
        }
        fail("Expected PostgreSQL row-lock wait");
    }
    private UUID choice(String label) {
        return jdbc.queryForObject("select choice_id from question_choices where question_version_id=? and choice_label=?",UUID.class,v(3),label);
    }
    @Test void concurrentSavesWaitAndCommitInLockOrderWithoutDuplicateRows() throws Exception {
        var attempt=id(create(token(ACTOR),List.of(2),1));var first=choice("A");var last=choice("B");
        var held=new CountDownLatch(1);var release=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
        try {
            var a=executor.submit(()->{
                authenticate(ACTOR,PERMISSION);
                try { return new TransactionTemplate(transactions).execute(status->{
                    var result=submissions.save(ACTOR,attempt,v(3),first);held.countDown();await(release);return result;
                }); } finally { SecurityContextHolder.clearContext(); }
            });
            await(held);
            var b=executor.submit(()->{
                authenticate(ACTOR,PERMISSION);
                try {return submissions.save(ACTOR,attempt,v(3),last);}finally{SecurityContextHolder.clearContext();}
            });
            lockWaiting();assertFalse(b.isDone());release.countDown();
            assertEquals(first,a.get(10,TimeUnit.SECONDS).selectedChoiceId());assertEquals(last,b.get(10,TimeUnit.SECONDS).selectedChoiceId());
            assertEquals(1,count("select count(*) from attempt_answers where attempt_id=?",attempt));
            assertEquals(last,jdbc.queryForObject("select selected_choice_id from attempt_answers where attempt_id=?",UUID.class,attempt));
        } finally {release.countDown();executor.shutdownNow();assertTrue(executor.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @ParameterizedTest @ValueSource(strings={"save","submit"})
    void waitingWriteRechecksCommittedExpiryInsteadOfUsingPreLockState(String operation) throws Exception {
        var attempt=id(create(token(ACTOR),List.of(2),1));var selected=choice("A");
        var held=new CountDownLatch(1);var release=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
        try {
            var expiry=executor.submit(()->new TransactionTemplate(transactions).execute(status->{
                jdbc.update("update attempts set started_at=?,expires_at=? where attempt_id=?",OffsetDateTime.ofInstant(NOW.minusSeconds(86400),ZoneOffset.UTC),OffsetDateTime.ofInstant(NOW,ZoneOffset.UTC),attempt);
                held.countDown();await(release);return true;
            }));
            await(held);
            var save=executor.submit(()->{
                authenticate(ACTOR,PERMISSION);
                try{return operation.equals("save")?submissions.save(ACTOR,attempt,v(3),selected):submissions.submit(ACTOR,attempt);}finally{SecurityContextHolder.clearContext();}
            });
            lockWaiting();release.countDown();assertTrue(expiry.get(10,TimeUnit.SECONDS));
            var ex=assertThrows(ExecutionException.class,()->save.get(10,TimeUnit.SECONDS));
            assertInstanceOf(PracticeException.class,ex.getCause());assertEquals("ATTEMPT_EXPIRED",((PracticeException)ex.getCause()).getCode());
            assertEquals(0,count("select count(*) from attempt_answers where attempt_id=?",attempt));
        } finally {release.countDown();executor.shutdownNow();assertTrue(executor.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @Test void saveCommitsBeforeSubmitThenGradingIncludesConfirmedSelection() throws Exception {
        var attempt=id(create(token(ACTOR),List.of(2),1));var selected=choice("A");
        var held=new CountDownLatch(1);var release=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
        try {
            var save=executor.submit(()->{
                authenticate(ACTOR,PERMISSION);
                try{return new TransactionTemplate(transactions).execute(status->{
                    var result=submissions.save(ACTOR,attempt,v(3),selected);held.countDown();await(release);return result;
                });}finally{SecurityContextHolder.clearContext();}
            });
            await(held);
            var submit=executor.submit(()->{
                authenticate(ACTOR,PERMISSION);
                try{return submissions.submit(ACTOR,attempt);}finally{SecurityContextHolder.clearContext();}
            });
            lockWaiting();assertFalse(submit.isDone());release.countDown();
            assertEquals(selected,save.get(10,TimeUnit.SECONDS).selectedChoiceId());
            var result=submit.get(10,TimeUnit.SECONDS);assertEquals(1,result.rawScore());assertEquals(selected,result.questions().getFirst().selectedChoiceId());
            assertEquals(1,count("select count(*) from attempt_answers where attempt_id=? and is_correct=true",attempt));
        }finally{release.countDown();executor.shutdownNow();assertTrue(executor.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @ParameterizedTest @ValueSource(strings={"save","clear","submit"})
    void submitCommitsFirstThenConcurrentWriteIsRejectedOrSubmitReplays(String operation) throws Exception {
        var attempt=id(create(token(ACTOR),List.of(2),1));var first=choice("A");var second=choice("B");
        authenticate(ACTOR,PERMISSION);submissions.save(ACTOR,attempt,v(3),first);SecurityContextHolder.clearContext();
        var held=new CountDownLatch(1);var release=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
        try {
            var submit=executor.submit(()->{
                authenticate(ACTOR,PERMISSION);
                try{return new TransactionTemplate(transactions).execute(status->{
                    var result=submissions.submit(ACTOR,attempt);held.countDown();await(release);return result;
                });}finally{SecurityContextHolder.clearContext();}
            });
            await(held);
            var contender=executor.submit(()->{
                authenticate(ACTOR,PERMISSION);
                try{return operation.equals("submit")?submissions.submit(ACTOR,attempt):submissions.save(ACTOR,attempt,v(3),operation.equals("clear")?null:second);}
                finally{SecurityContextHolder.clearContext();}
            });
            lockWaiting();assertFalse(contender.isDone());release.countDown();var result=submit.get(10,TimeUnit.SECONDS);
            if(operation.equals("submit"))assertEquals(result,contender.get(10,TimeUnit.SECONDS));
            else {
                var failure=assertThrows(ExecutionException.class,()->contender.get(10,TimeUnit.SECONDS));
                assertInstanceOf(PracticeException.class,failure.getCause());assertEquals("ATTEMPT_NOT_EDITABLE",((PracticeException)failure.getCause()).getCode());
            }
            assertEquals(1,result.rawScore());assertEquals(first,result.questions().getFirst().selectedChoiceId());
            assertEquals(1,count("select count(*) from attempt_answers where attempt_id=? and selected_choice_id=? and is_correct=true",attempt,first));
            assertEquals(1,count("select count(*) from attempt_part_results where attempt_id=?",attempt));
        }finally{release.countDown();executor.shutdownNow();assertTrue(executor.awaitTermination(10,TimeUnit.SECONDS));}
    }
}
