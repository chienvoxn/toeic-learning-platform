package com.toeic.backend.assessment.api;

import com.toeic.backend.assessment.application.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PracticeSubmissionIntegrationTest extends PracticeTestSupport {
    @Autowired PracticeSubmissionService submissions;
    @Autowired com.toeic.backend.assessment.infrastructure.PracticeAnswerRepository answerRepository;
    private UUID choice(UUID version,String label) {
        return jdbc.queryForObject("select choice_id from question_choices where question_version_id=? and choice_label=?",UUID.class,version,label);
    }
    private void save(String token,UUID attempt,UUID version,String label) throws Exception {
        var payload=json.writeValueAsString(Collections.singletonMap("selectedChoiceId",label==null?null:choice(version,label)));
        call(put(API+"/"+attempt+"/answers/"+version).contentType(APPLICATION_JSON).content(payload),token).andExpect(status().isOk());
    }
    @Test void mixedResultPersistsRawCountsKeysExplanationsAndPartResults() throws Exception {
        var token=token(ACTOR);var attempt=id(create(token,List.of(1,2,5),5));
        save(token,attempt,v(1),"A");save(token,attempt,v(2),"B");save(token,attempt,v(3),"C");save(token,attempt,v(3),null);save(token,attempt,v(16),"A");
        safe(read(token,attempt));
        call(get(API+"/"+attempt+"/result"),token).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESULT_NOT_READY"));
        var result=tree(call(post(API+"/"+attempt+"/submit"),token).andExpect(status().isOk()));
        assertEquals("GRADED",result.get("status").asString());assertEquals(NOW,Instant.parse(result.get("submittedAt").asString()));
        assertEquals(5,result.get("totalQuestions").asInt());assertEquals(2,result.get("correctCount").asInt());
        assertEquals(1,result.get("incorrectCount").asInt());assertEquals(2,result.get("unansweredCount").asInt());
        assertEquals(2,result.get("rawScore").asInt());assertEquals(40.0,result.get("accuracy").asDouble());
        assertEquals(1,result.get("listeningCorrectCount").asInt());assertEquals(1,result.get("readingCorrectCount").asInt());
        int order=0;
        for(var question:result.get("questions")) {
            assertEquals(++order,question.get("order").asInt());var version=UUID.fromString(question.get("questionVersionId").asString());
            assertEquals(choice(version,"A").toString(),question.get("correctChoiceId").asString());
            assertEquals("PRAC_SECRET_EXPLANATION",question.get("explanation").asString());
            assertEquals(version.equals(v(1))||version.equals(v(16)),question.get("isCorrect").asBoolean());
            assertEquals(version.equals(v(3))||version.equals(v(17))?"UNANSWERED":version.equals(v(2))?"INCORRECT":"CORRECT",question.get("outcome").asString());
        }
        var encoded=result.toString();
        for(var hidden:List.of("transcript","storageUrl","storage_url","PRAC_SECRET_TRANSCRIPT","Q03_LEAK_STORAGE","assets","passage"))assertFalse(encoded.contains(hidden),hidden);
        assertEquals(5,count("select count(*) from attempt_answers where attempt_id=? and is_correct is not null and correct_choice_id is not null",attempt));
        assertEquals(3,count("select count(*) from attempt_part_results where attempt_id=?",attempt));
        assertEquals(2,count("select sum(correct_count)::integer from attempt_part_results where attempt_id=?",attempt));
        var partSnapshots=new TransactionTemplate(transactions).execute(status->answerRepository.partResults(attempt));
        assertNotNull(partSnapshots);
        assertEquals(List.of(1,2,5),partSnapshots.stream().map(p->p.partNumber()).toList());
        assertEquals(List.of(2,1,2),partSnapshots.stream().map(p->p.totalQuestions()).toList());
        assertEquals(List.of(1,0,1),partSnapshots.stream().map(p->p.correctCount()).toList());
        assertEquals(List.of("50.00","0.00","50.00"),partSnapshots.stream().map(p->p.accuracy().toPlainString()).toList());
        assertEquals(result.toString(),tree(call(get(API+"/"+attempt+"/result"),token).andExpect(status().isOk())).toString());
        safe(read(token,attempt));
    }
    @ParameterizedTest @CsvSource({"1,2","2,1","3,3","4,2","5,2","6,2","7,3"})
    void allPartsGradePinnedSingleChoiceWithFullAccuracy(int part,int total) throws Exception {
        var token=token(ACTOR);var session=create(token,List.of(part),total);var attempt=id(session);
        for(var question:session.get("questions"))save(token,attempt,UUID.fromString(question.get("questionVersionId").asString()),"A");
        call(post(API+"/"+attempt+"/submit"),token).andExpect(status().isOk()).andExpect(jsonPath("$.rawScore").value(total))
                .andExpect(jsonPath("$.accuracy").value(100.0)).andExpect(jsonPath("$.incorrectCount").value(0)).andExpect(jsonPath("$.unansweredCount").value(0));
    }
    @Test void unansweredAttemptAndNullExplanationAreSupported() throws Exception {
        jdbc.update("update question_versions set explanation=null where question_version_id=?",v(3));
        var token=token(ACTOR);var attempt=id(create(token,List.of(2),1));
        call(post(API+"/"+attempt+"/submit"),token).andExpect(status().isOk()).andExpect(jsonPath("$.correctCount").value(0))
                .andExpect(jsonPath("$.incorrectCount").value(0)).andExpect(jsonPath("$.unansweredCount").value(1)).andExpect(jsonPath("$.accuracy").value(0.0))
                .andExpect(jsonPath("$.questions[0].outcome").value("UNANSWERED")).andExpect(jsonPath("$.questions[0].explanation").isEmpty());
        assertEquals(1,count("select count(*) from attempt_answers where attempt_id=? and selected_choice_id is null and not is_correct",attempt));
    }
    @Test void replayAfterExpiryReadsStoredResultWithoutRegradingEvenIfSourceIsTamperedByFixtureSql() throws Exception {
        var token=token(ACTOR);var attempt=id(create(token,List.of(2),1));save(token,attempt,v(3),"A");
        var first=tree(call(post(API+"/"+attempt+"/submit"),token).andExpect(status().isOk()));
        var rows=jdbc.queryForList("select * from attempt_answers where attempt_id=?",attempt);
        var parts=jdbc.queryForList("select * from attempt_part_results where attempt_id=?",attempt);
        expire(attempt);
        // Intentional SQL bypass of the immutable-version application contract: replay must never grade again.
        jdbc.update("update question_choices set is_correct=not is_correct where question_version_id=? and choice_label in ('A','B')",v(3));
        jdbc.update("update question_versions set explanation='TAMPERED' where question_version_id=?",v(3));
        assertEquals(first.toString(),tree(call(post(API+"/"+attempt+"/submit"),token).andExpect(status().isOk())).toString());
        assertEquals(first.toString(),tree(call(get(API+"/"+attempt+"/result"),token).andExpect(status().isOk())).toString());
        assertEquals(rows,jdbc.queryForList("select * from attempt_answers where attempt_id=?",attempt));
        assertEquals(parts,jdbc.queryForList("select * from attempt_part_results where attempt_id=?",attempt));
        for(String body:List.of("{\"selectedChoiceId\":null}","{\"selectedChoiceId\":\""+choice(v(3),"B")+"\"}"))
            call(put(API+"/"+attempt+"/answers/"+v(3)).contentType(APPLICATION_JSON).content(body),token)
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ATTEMPT_NOT_EDITABLE"));
    }
    private void expire(UUID attempt) {
        jdbc.update("update attempts set started_at=?,expires_at=? where attempt_id=?",OffsetDateTime.ofInstant(NOW.minusSeconds(86400),ZoneOffset.UTC),OffsetDateTime.ofInstant(NOW,ZoneOffset.UTC),attempt);
    }
    @Test void expiredActiveAttemptCannotSubmitAndHasNoResult() throws Exception {
        var token=token(ACTOR);var attempt=id(create(token,List.of(2),1));save(token,attempt,v(3),"A");expire(attempt);
        call(post(API+"/"+attempt+"/submit"),token).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ATTEMPT_EXPIRED"));
        call(get(API+"/"+attempt+"/result"),token).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESULT_NOT_READY"));
        assertEquals(0,count("select count(*) from attempt_answers where attempt_id=? and is_correct is not null",attempt));
        assertEquals(0,count("select count(*) from attempt_part_results where attempt_id=?",attempt));
    }
    @Test void submitAndResultEnforceAuthPermissionOwnershipAndSourceBeforeReveal() throws Exception {
        var token=token(ACTOR);var attempt=id(create(token,List.of(2),1));
        var teacher=token(TEACHER);var other=token(OTHER);
        for(var auth:Arrays.asList(null,"bad-token",teacher,other)) {
            int expected=auth==null||auth.equals("bad-token")?401:auth.equals(other)?404:403;
            call(post(API+"/"+attempt+"/submit"),auth).andExpect(status().is(expected));
            call(get(API+"/"+attempt+"/result"),auth).andExpect(status().is(expected));
        }
        call(post(API+"/"+attempt+"/submit"),token).andExpect(status().isOk());
        call(get(API+"/"+attempt+"/result"),other).andExpect(status().isNotFound());
        call(post(API+"/"+attempt+"/submit"),other).andExpect(status().isNotFound());
        call(get(API+"/"+UUID.randomUUID()+"/result"),token).andExpect(status().isNotFound());
        jdbc.update("update attempts set attempt_source_type='TEST_VERSION' where attempt_id=?",attempt);
        call(post(API+"/"+attempt+"/submit"),token).andExpect(status().isNotFound());
        call(get(API+"/"+attempt+"/result"),token).andExpect(status().isNotFound());
    }
    @Test void serviceGuardsSubmitAndResultAgainstMissingPermissionOrSpoofedActor() throws Exception {
        var attempt=id(create(token(ACTOR),List.of(2),1));
        authenticate(ACTOR);
        assertThrows(AccessDeniedException.class,()->submissions.submit(ACTOR,attempt));
        assertThrows(AccessDeniedException.class,()->submissions.result(ACTOR,attempt));
        authenticate(OTHER,PERMISSION);
        assertThrows(AccessDeniedException.class,()->submissions.submit(ACTOR,attempt));
        assertThrows(AccessDeniedException.class,()->submissions.result(ACTOR,attempt));
    }
    @Test void submitRejectsBodyAndQueryWithoutChangingAttempt() throws Exception {
        var token=token(ACTOR);var attempt=id(create(token,List.of(2),1));
        for(var body:List.of("{}","null","{\"answers\":[]}","{\"status\":\"GRADED\"}"))
            call(post(API+"/"+attempt+"/submit").contentType(APPLICATION_JSON).content(body),token).andExpect(status().isBadRequest());
        call(post(API+"/"+attempt+"/submit").param("actorId",OTHER.toString()),token).andExpect(status().isBadRequest());
        call(get(API+"/"+attempt+"/result").param("explanation","true"),token).andExpect(status().isBadRequest());
        call(post(API+"/bad-id/submit"),token).andExpect(status().isBadRequest());
        assertEquals(0,count("select count(*) from attempt_answers where attempt_id=?",attempt));
        assertEquals("IN_PROGRESS",jdbc.queryForObject("select status from attempts where attempt_id=?",String.class,attempt));
    }
    @Test void invalidLaterPinnedKeyRollsBackEarlierGradingAndCanRetryAfterRepair() throws Exception {
        var token=token(ACTOR);var attempt=id(create(token,List.of(1),2));save(token,attempt,v(1),"A");save(token,attempt,v(2),"A");
        var last=jdbc.queryForObject("select question_version_id from attempt_question_snapshots where attempt_id=? order by question_order desc limit 1",UUID.class,attempt);
        jdbc.update("update question_choices set is_correct=false where question_version_id=?",last);
        call(post(API+"/"+attempt+"/submit"),token).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRACTICE_SNAPSHOT_UNAVAILABLE"));
        assertEquals(0,count("select count(*) from attempt_answers where attempt_id=? and (is_correct is not null or correct_choice_id is not null)",attempt));
        assertEquals(0,count("select count(*) from attempt_part_results where attempt_id=?",attempt));
        assertEquals(1,count("select count(*) from attempts where attempt_id=? and status='IN_PROGRESS' and submitted_at is null and correct_count is null",attempt));
        jdbc.update("update question_choices set is_correct=true where question_version_id=? and choice_label='A'",last);
        call(post(API+"/"+attempt+"/submit"),token).andExpect(status().isOk()).andExpect(jsonPath("$.rawScore").value(2));
    }
    @Test void failureAfterGradingBeforeCommitRollsBackAnswersPartsAndHeaderTogether() throws Exception {
        var token=token(ACTOR);var attempt=id(create(token,List.of(2),1));save(token,attempt,v(3),"A");
        authenticate(ACTOR,PERMISSION);
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).execute(status->{
            assertEquals(1,submissions.submit(ACTOR,attempt).rawScore());
            assertEquals(1,count("select count(*) from attempt_part_results where attempt_id=?",attempt));
            throw new IllegalStateException("Fixture failure before commit");
        }));
        assertEquals(0,count("select count(*) from attempt_answers where attempt_id=? and is_correct is not null",attempt));
        assertEquals(0,count("select count(*) from attempt_part_results where attempt_id=?",attempt));
        assertEquals(1,count("select count(*) from attempts where attempt_id=? and status='IN_PROGRESS' and submitted_at is null",attempt));
        call(post(API+"/"+attempt+"/submit"),token).andExpect(status().isOk());
    }
    @Test void gradeUsesOldPinnedQuestionAndGroupAfterRepublishArchive() throws Exception {
        var token=token(ACTOR);var session=create(token,List.of(2,4),3);var attempt=id(session);var replacement=UUID.randomUUID();var groupReplacement=UUID.randomUUID();
        jdbc.update("""
                insert into question_versions(question_version_id,question_id,version_number,status,question_text,explanation,published_at,created_at)
                values (?,?,2,'PUBLISHED','New prompt','NEW_EXPLANATION',now(),now())
                """,replacement,q(3));
        jdbc.update("insert into question_choices(choice_id,question_version_id,choice_label,content,is_correct,display_order) values (?,?,'B','New B',true,1)",UUID.randomUUID(),replacement);
        jdbc.update("update questions set current_published_version_id=?,status='ARCHIVED' where question_id=?",replacement,q(3));
        jdbc.update("""
                insert into question_group_versions(group_version_id,group_id,version_number,status,instruction,passage_text,published_at,created_at)
                values (?,?,2,'PUBLISHED','NEW','NEW',now(),now())
                """,groupReplacement,g(4));
        jdbc.update("update question_groups set current_published_version_id=?,status='ARCHIVED' where group_id=?",groupReplacement,g(4));
        jdbc.update("update question_group_versions set status='ARCHIVED',archived_at=now() where group_version_id=?",gv(4));
        jdbc.update("update question_versions set status='ARCHIVED',archived_at=now() where question_version_id=? or group_version_id=?",v(3),gv(4));
        for(var question:session.get("questions"))save(token,attempt,UUID.fromString(question.get("questionVersionId").asString()),"A");
        var result=tree(call(post(API+"/"+attempt+"/submit"),token).andExpect(status().isOk()));
        assertEquals(3,result.get("rawScore").asInt());
        for(var question:result.get("questions"))assertEquals("PRAC_SECRET_EXPLANATION",question.get("explanation").asString());
        assertFalse(result.toString().contains("NEW_EXPLANATION"));
    }
}
