package com.toeic.backend.assessment.api;

import com.toeic.backend.assessment.application.PracticeStartQuery;
import com.toeic.backend.questionbank.application.PracticeQuestionCatalog;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

class PracticeConcurrencyTest extends PracticeTestSupport {
    private static void await(CountDownLatch latch) {
        try { if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("Latch timeout"); }
        catch(InterruptedException ex){Thread.currentThread().interrupt();throw new AssertionError(ex);}
    }
    private void lockWaiting()throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<until) {
            if(count("select count(*) from pg_stat_activity where datname=current_database() and pid<>pg_backend_pid() and wait_event_type='Lock'")>0)return;
            Thread.sleep(10);
        }
        fail("Expected PostgreSQL row-lock wait");
    }
    @ParameterizedTest @ValueSource(strings={"groupArchive","questionArchive","mediaVisibility"})
    void createHoldsCompatibleSourceAndMediaLocksUntilCommit(String mutation)throws Exception {
        var token=token(ACTOR);var ready=new CountDownLatch(1);var release=new CountDownLatch(1);
        var executor=Executors.newFixedThreadPool(2);
        try {
            var create=executor.submit(()->{
                authenticate(ACTOR,PERMISSION);
                try {return new TransactionTemplate(transactions).execute(status->{
                    var result=service.create(ACTOR,new PracticeStartQuery(List.of(4),2,null,null));ready.countDown();await(release);return result.attemptId();
                });}finally{SecurityContextHolder.clearContext();}
            });
            await(ready);
            var change=executor.submit(()->switch(mutation){
                case "groupArchive"->jdbc.update("update question_groups set status='ARCHIVED' where group_id=?",g(4));
                case "questionArchive"->jdbc.update("update questions set status='ARCHIVED' where question_id=?",q(14));
                default->jdbc.update("update media_resources set visibility='PRIVATE' where resource_id='b3000000-0000-0000-0000-000000000001'");
            });
            lockWaiting();assertFalse(change.isDone());release.countDown();var attempt=create.get(10,TimeUnit.SECONDS);assertEquals(1,change.get(10,TimeUnit.SECONDS));
            var session=read(token,attempt);assertEquals(2,session.get("questions").size());safe(session);
            if(mutation.equals("mediaVisibility"))assertFalse(session.toString().contains("b3000000-0000-0000-0000-000000000001"));
        } finally {release.countDown();executor.shutdownNow();assertTrue(executor.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @Test void archiveWinsDuringSelectionThenCreateRevalidatesAndRollsBack()throws Exception {
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
        try {
            var archive=executor.submit(()->new TransactionTemplate(transactions).execute(status->{
                jdbc.update("update question_groups set status='ARCHIVED' where group_id=?",g(4));locked.countDown();await(release);return true;
            }));
            await(locked);
            var create=executor.submit(()->{authenticate(ACTOR,PERMISSION);try{return service.create(ACTOR,new PracticeStartQuery(List.of(4),2,null,null));}finally{SecurityContextHolder.clearContext();}});
            lockWaiting();release.countDown();assertTrue(archive.get(10,TimeUnit.SECONDS));
            var failure=assertThrows(ExecutionException.class,()->create.get(10,TimeUnit.SECONDS));
            assertInstanceOf(PracticeQuestionCatalog.Unavailable.class,failure.getCause());
            assertTrue(((PracticeQuestionCatalog.Unavailable)failure.getCause()).sourceChanged());
            assertEquals(0,count("select count(*) from attempts where user_id=?",ACTOR));assertEquals(0,count("select count(*) from attempt_question_snapshots"));
        }finally{release.countDown();executor.shutdownNow();assertTrue(executor.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @Test void concurrentCreatesHaveSeparateCompleteStableSnapshots()throws Exception {
        var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);var futures=new ArrayList<Future<UUID>>();
        try {
            for(int n=0;n<2;n++)futures.add(executor.submit(()->{await(start);authenticate(ACTOR,PERMISSION);try{return service.create(ACTOR,new PracticeStartQuery(List.of(3),6,null,null)).attemptId();}finally{SecurityContextHolder.clearContext();}}));
            start.countDown();var a=futures.get(0).get(15,TimeUnit.SECONDS);var b=futures.get(1).get(15,TimeUnit.SECONDS);assertNotEquals(a,b);
            for(var id:List.of(a,b)) {
                assertEquals(6,count("select count(*) from attempt_question_snapshots where attempt_id=?",id));
                assertEquals(6,count("select count(distinct question_version_id) from attempt_question_snapshots where attempt_id=?",id));
                var token=token(ACTOR);assertEquals(read(token,id).toString(),read(token,id).toString());
            }
        } finally {start.countDown();executor.shutdownNow();assertTrue(executor.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @Test void outerFailureRollsBackParentAndEverySnapshotRow() {
        authenticate(ACTOR,PERMISSION);
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).execute(status->{
            service.create(ACTOR,new PracticeStartQuery(List.of(1),2,null,null));assertEquals(1,count("select count(*) from attempts where user_id=?",ACTOR));
            assertEquals(2,count("select count(*) from attempt_question_snapshots"));throw new IllegalStateException("Fixture rollback");
        }));
        assertEquals(0,count("select count(*) from attempts where user_id=?",ACTOR));assertEquals(0,count("select count(*) from attempt_question_snapshots"));
    }
}
