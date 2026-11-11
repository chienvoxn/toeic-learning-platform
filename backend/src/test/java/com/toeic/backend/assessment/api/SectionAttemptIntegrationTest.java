package com.toeic.backend.assessment.api;

import com.toeic.backend.assessment.application.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SectionAttemptIntegrationTest extends PracticeTestSupport {
    static final String EXAM="exam:manage:self";
    @Autowired SectionSessionService sections;
    @Autowired PracticeSubmissionService submissions;
    @AfterEach void restoreLearnerPermissions() {
        jdbc.update("insert into role_permissions(role_id,permission_id) select r.role_id,p.permission_id from roles r cross join permissions p where r.name='LEARNER' and p.code in (?,?) on conflict do nothing",EXAM,PERMISSION);
    }
    String payload(String section,int total,int minutes) {
        return json.writeValueAsString(Map.of("sourceType","SECTION_SNAPSHOT","section",Map.of("type",section,"questionCount",total,"durationMinutes",minutes)));
    }
    JsonNode section(String token,String type,int total,int minutes)throws Exception {
        return tree(call(post(API).contentType(APPLICATION_JSON).content(payload(type,total,minutes)),token).andExpect(status().isCreated()));
    }
    UUID version(JsonNode question){return UUID.fromString(question.get("questionVersionId").asString());}
    UUID choice(UUID version,String label){return jdbc.queryForObject("select choice_id from question_choices where question_version_id=? and choice_label=?",UUID.class,version,label);}
    void save(String token,UUID attempt,UUID version,String label)throws Exception {
        call(put(API+"/"+attempt+"/answers/"+version).contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(Collections.singletonMap("selectedChoiceId",label==null?null:choice(version,label)))),token).andExpect(status().isOk());
    }
    void expire(UUID attempt,int minutes){
        jdbc.update("update attempts set started_at=?,expires_at=? where attempt_id=?",
                OffsetDateTime.ofInstant(NOW.minusSeconds(minutes*60L),ZoneOffset.UTC),OffsetDateTime.ofInstant(NOW,ZoneOffset.UTC),attempt);
    }
    @ParameterizedTest @CsvSource({"LISTENING,7,1","READING,6,120"})
    void sectionPinsAllRequiredPartsConfigurationAndStableSafeSnapshot(String type,int total,int minutes)throws Exception {
        var token=token(ACTOR);var session=section(token,type,total,minutes);safe(session);
        assertEquals("SECTION_SNAPSHOT",session.get("sourceType").asString());assertEquals(type,session.get("section").get("type").asString());
        assertEquals(total,session.get("section").get("questionCount").asInt());assertEquals(minutes,session.get("section").get("durationMinutes").asInt());
        assertEquals(NOW,Instant.parse(session.get("startedAt").asString()));assertEquals(NOW.plusSeconds(minutes*60L),Instant.parse(session.get("expiresAt").asString()));
        var parts=new TreeSet<Integer>();var grouped=new HashMap<String,List<Integer>>();int order=0;
        for(var q:session.get("questions")) {
            assertEquals(++order,q.get("order").asInt());parts.add(q.get("partNumber").asInt());
            if(!q.get("passage").isNull())grouped.computeIfAbsent(q.get("passage").get("groupVersionId").asString(),k->new ArrayList<>())
                    .add(Integer.parseInt(q.get("questionVersionId").asString().substring(24)));
        }
        assertEquals(new TreeSet<>(new SectionStartQuery(type,total,minutes).partNumbers()),parts);
        for(var group:grouped.values())assertEquals(group.stream().sorted(Comparator.reverseOrder()).toList(),group);
        assertEquals(session.toString(),read(token,id(session)).toString());
        assertEquals(1,count("select count(*) from attempts where attempt_id=? and attempt_type='SECTION' and test_version_id is null and section_type=? and configured_duration_seconds=?",id(session),type,minutes*60));
        call(get(API+"/"+id(session)+"/result"),token).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESULT_NOT_READY"));
    }
    @ParameterizedTest @ValueSource(strings={
            "{}","{\"type\":\"FULL\",\"questionCount\":7,\"durationMinutes\":30}","{\"type\":\"listening\",\"questionCount\":7,\"durationMinutes\":30}",
            "{\"type\":null,\"questionCount\":7,\"durationMinutes\":30}","{\"type\":true,\"questionCount\":7,\"durationMinutes\":30}",
            "{\"type\":\"LISTENING\",\"questionCount\":0,\"durationMinutes\":30}","{\"type\":\"LISTENING\",\"questionCount\":101,\"durationMinutes\":30}",
            "{\"type\":\"LISTENING\",\"questionCount\":7.0,\"durationMinutes\":30}","{\"type\":\"LISTENING\",\"questionCount\":\"7\",\"durationMinutes\":30}",
            "{\"type\":\"LISTENING\",\"questionCount\":7,\"durationMinutes\":0}","{\"type\":\"LISTENING\",\"questionCount\":7,\"durationMinutes\":121}",
            "{\"type\":\"LISTENING\",\"questionCount\":7,\"durationMinutes\":1.5}","{\"type\":\"LISTENING\",\"questionCount\":7,\"durationMinutes\":\"30\"}",
            "{\"type\":\"LISTENING\",\"questionCount\":7}","{\"type\":\"LISTENING\",\"durationMinutes\":30}",
            "{\"type\":\"LISTENING\",\"questionCount\":7,\"durationMinutes\":30,\"partNumbers\":[1]}",
            "{\"type\":\"LISTENING\",\"questionCount\":7,\"durationMinutes\":30,\"durationMinutes\":60}"})
    void strictConfigurationRejectsInvalidTypeRangesCoercionsAndFields(String config)throws Exception {
        call(post(API).contentType(APPLICATION_JSON).content("{\"sourceType\":\"SECTION_SNAPSHOT\",\"section\":"+config+"}"),token(ACTOR))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertEquals(0,count("select count(*) from attempts where user_id=?",ACTOR));
    }
    @Test void rootAllowlistAndSourceSeparationPreventConfigurationInjection()throws Exception {
        var token=token(ACTOR);var valid=payload("READING",6,20);
        for(var body:List.of(valid.substring(0,valid.length()-1)+",\"userId\":\"spoof\"}",
                valid.substring(0,valid.length()-1)+",\"practice\":null}",
                "{\"sourceType\":\"SECTION_SNAPSHOT\",\"section\":null}",
                "{\"sourceType\":\"PRACTICE_SNAPSHOT\",\"section\":{\"type\":\"READING\",\"questionCount\":6,\"durationMinutes\":20}}"))
            call(post(API).contentType(APPLICATION_JSON).content(body),token).andExpect(status().isBadRequest());
        call(post(API).param("durationMinutes","1").contentType(APPLICATION_JSON).content(valid),token).andExpect(status().isBadRequest());
    }
    @ParameterizedTest @CsvSource({"LISTENING,1","LISTENING,4","LISTENING,6","READING,2","READING,3","READING,5","READING,100"})
    void structurallyInfeasibleCountsAre409WithoutPartialAttempt(String type,int total)throws Exception {
        call(post(API).contentType(APPLICATION_JSON).content(payload(type,total,20)),token(ACTOR)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SECTION_QUESTIONS_INSUFFICIENT"));
        assertEquals(0,count("select count(*) from attempts where user_id=?",ACTOR));assertEquals(0,count("select count(*) from attempt_question_snapshots"));
    }
    @Test void missingRequiredPartOrInvalidGroupCannotBeReplacedByAnotherPart()throws Exception {
        jdbc.update("update question_groups set status='ARCHIVED' where group_id=?",g(4));
        call(post(API).contentType(APPLICATION_JSON).content(payload("LISTENING",7,20)),token(ACTOR)).andExpect(status().isConflict());
        jdbc.update("update question_versions set group_question_order=null where question_version_id=?",v(18));
        call(post(API).contentType(APPLICATION_JSON).content(payload("READING",6,20)),token(ACTOR)).andExpect(status().isConflict());
        assertEquals(0,count("select count(*) from attempts where user_id=?",ACTOR));
    }
    @ParameterizedTest @CsvSource({"LISTENING,7,false","READING,6,false","LISTENING,7,true","READING,6,true"})
    void saveReplaceClearSubmitAndResultByEveryPartIncludingFirstSubmitAfterDeadline(String type,int total,boolean expired)throws Exception {
        var token=token(ACTOR);var session=section(token,type,total,2);var attempt=id(session);var qs=session.get("questions");
        save(token,attempt,version(qs.get(0)),"B");save(token,attempt,version(qs.get(0)),"A");
        save(token,attempt,version(qs.get(1)),"B");save(token,attempt,version(qs.get(2)),"A");save(token,attempt,version(qs.get(2)),null);
        var saved=jdbc.queryForList("select question_version_id,selected_choice_id,answered_at,updated_at from attempt_answers where attempt_id=? order by question_version_id",attempt);
        if(expired) {
            expire(attempt,2);
            for(var choice:Arrays.asList((UUID)null,choice(version(qs.get(0)),"C")))call(put(API+"/"+attempt+"/answers/"+version(qs.get(0)))
                    .contentType(APPLICATION_JSON).content(json.writeValueAsString(Collections.singletonMap("selectedChoiceId",choice))),token)
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ATTEMPT_EXPIRED"));
            var before=read(token,attempt);safe(before);assertTrue(before.get("expired").asBoolean());assertEquals("IN_PROGRESS",before.get("status").asString());
        }
        call(post(API+"/"+attempt+"/submit").contentType(APPLICATION_JSON).content("{\"answers\":[]}"),token).andExpect(status().isBadRequest());
        var result=tree(call(post(API+"/"+attempt+"/submit"),token).andExpect(status().isOk()));
        assertEquals(1,result.get("rawScore").asInt());assertEquals(1,result.get("incorrectCount").asInt());assertEquals(total-2,result.get("unansweredCount").asInt());
        assertEquals(type.equals("LISTENING")?14.29:16.67,result.get("accuracy").asDouble());
        int partTotal=0,partCorrect=0,partIncorrect=0,partUnanswered=0;
        assertEquals(type.equals("LISTENING")?4:3,result.get("parts").size());
        for(var part:result.get("parts")) {
            partTotal+=part.get("totalQuestions").asInt();partCorrect+=part.get("correctCount").asInt();partIncorrect+=part.get("incorrectCount").asInt();partUnanswered+=part.get("unansweredCount").asInt();
        }
        assertEquals(total,partTotal);assertEquals(1,partCorrect);assertEquals(1,partIncorrect);assertEquals(total-2,partUnanswered);
        for(var question:result.get("questions")) {
            assertEquals(choice(version(question),"A").toString(),question.get("correctChoiceId").asString());
            assertEquals("PRAC_SECRET_EXPLANATION",question.get("explanation").asString());
        }
        assertEquals(saved,jdbc.queryForList("select question_version_id,selected_choice_id,answered_at,updated_at from attempt_answers where attempt_id=? and question_version_id in (?,?,?) order by question_version_id",attempt,version(qs.get(0)),version(qs.get(1)),version(qs.get(2))));
        var snapshots=jdbc.queryForList("select * from attempt_answers where attempt_id=? order by question_version_id",attempt);
        assertEquals(result.toString(),tree(call(post(API+"/"+attempt+"/submit"),token).andExpect(status().isOk())).toString());
        assertEquals(result.toString(),tree(call(get(API+"/"+attempt+"/result"),token).andExpect(status().isOk())).toString());
        assertEquals(snapshots,jdbc.queryForList("select * from attempt_answers where attempt_id=? order by question_version_id",attempt));
        call(put(API+"/"+attempt+"/answers/"+version(qs.get(0))).contentType(APPLICATION_JSON).content("{\"selectedChoiceId\":null}"),token)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ATTEMPT_NOT_EDITABLE"));
        safe(read(token,attempt));
    }
    @Test void permissionsAreCheckedPerSourceNotJustEndpointUnion()throws Exception {
        var token=token(ACTOR);var section=section(token,"READING",6,20);var practice=create(token,List.of(2),1);
        jdbc.update("delete from role_permissions where role_id=(select role_id from roles where name='LEARNER') and permission_id=(select permission_id from permissions where code=?)",EXAM);
        for(var id:List.of(id(section))) {
            call(get(API+"/"+id),token).andExpect(status().isForbidden());call(post(API+"/"+id+"/submit"),token).andExpect(status().isForbidden());
            call(get(API+"/"+id+"/result"),token).andExpect(status().isForbidden());
            call(put(API+"/"+id+"/answers/"+version(section.get("questions").get(0))).contentType(APPLICATION_JSON).content("{\"selectedChoiceId\":null}"),token).andExpect(status().isForbidden());
        }
        call(post(API).contentType(APPLICATION_JSON).content(payload("READING",6,20)),token).andExpect(status().isForbidden());
        read(token,id(practice));
        jdbc.update("insert into role_permissions(role_id,permission_id) select r.role_id,p.permission_id from roles r cross join permissions p where r.name='LEARNER' and p.code=?",EXAM);
        jdbc.update("delete from role_permissions where role_id=(select role_id from roles where name='LEARNER') and permission_id=(select permission_id from permissions where code=?)",PERMISSION);
        read(token,id(section));call(post(API+"/"+id(section)+"/submit"),token).andExpect(status().isOk());
        call(get(API+"/"+id(practice)),token).andExpect(status().isForbidden());
        call(post(API).contentType(APPLICATION_JSON).content(payload(List.of(2),1)),token).andExpect(status().isForbidden());
        jdbc.update("insert into role_permissions(role_id,permission_id) select r.role_id,p.permission_id from roles r cross join permissions p where r.name='LEARNER' and p.code=?",PERMISSION);
    }
    @Test void authenticationOwnershipAndServiceActorGuardsRemainMandatory()throws Exception {
        var token=token(ACTOR);var session=section(token,"READING",6,20);var attempt=id(session);
        call(post(API).contentType(APPLICATION_JSON).content(payload("READING",6,20)),null).andExpect(status().isUnauthorized());
        call(get(API+"/"+attempt),"bad-token").andExpect(status().isUnauthorized());
        for(var actor:List.of(TEACHER,ADMIN))call(post(API).contentType(APPLICATION_JSON).content(payload("READING",6,20)),token(actor)).andExpect(status().isForbidden());
        var other=token(OTHER);
        call(get(API+"/"+attempt),other).andExpect(status().isNotFound());call(post(API+"/"+attempt+"/submit"),other).andExpect(status().isNotFound());
        call(get(API+"/"+attempt+"/result"),other).andExpect(status().isNotFound());
        call(put(API+"/"+attempt+"/answers/"+version(session.get("questions").get(0))).contentType(APPLICATION_JSON).content("{\"selectedChoiceId\":null}"),other).andExpect(status().isNotFound());
        authenticate(ACTOR,PERMISSION);assertThrows(AccessDeniedException.class,()->sections.create(ACTOR,new SectionStartQuery("READING",6,20)));
        assertThrows(AccessDeniedException.class,()->submissions.submit(ACTOR,attempt));
        authenticate(OTHER,EXAM);assertThrows(AccessDeniedException.class,()->sections.create(ACTOR,new SectionStartQuery("READING",6,20)));
        assertThrows(AccessDeniedException.class,()->submissions.submit(ACTOR,attempt));
    }
    @Test void questionMembershipAndChoiceVersionArePinnedAfterRepublishArchive()throws Exception {
        var token=token(ACTOR);var session=section(token,"READING",6,20);var attempt=id(session);var selected=version(session.get("questions").get(0));
        call(put(API+"/"+attempt+"/answers/"+v(3)).contentType(APPLICATION_JSON).content("{\"selectedChoiceId\":null}"),token).andExpect(status().isNotFound());
        call(put(API+"/"+attempt+"/answers/"+selected).contentType(APPLICATION_JSON).content(json.writeValueAsString(Map.of("selectedChoiceId",choice(v(3),"A")))),token)
                .andExpect(status().isUnprocessableContent());
        var replacement=UUID.randomUUID();var parent=UUID.fromString(session.get("questions").get(0).get("questionId").asString());
        jdbc.update("insert into question_versions(question_version_id,question_id,version_number,status,question_text,explanation,published_at,created_at) values (?,?,2,'PUBLISHED','NEW','NEW_EXPLANATION',now(),now())",replacement,parent);
        jdbc.update("insert into question_choices(choice_id,question_version_id,choice_label,content,is_correct,display_order) values (?,?,'B','NEW',true,1)",UUID.randomUUID(),replacement);
        jdbc.update("update questions set current_published_version_id=?,status='ARCHIVED' where question_id=?",replacement,parent);
        jdbc.update("update question_groups set status='ARCHIVED' where group_id in (?,?)",g(5),g(6));
        for(var question:session.get("questions"))save(token,attempt,version(question),"A");
        safe(read(token,attempt));
        var result=tree(call(post(API+"/"+attempt+"/submit"),token).andExpect(status().isOk()));assertEquals(6,result.get("rawScore").asInt());
        assertFalse(result.toString().contains("NEW_EXPLANATION"));
        jdbc.update("update question_versions set explanation='SQL_BYPASS' where question_version_id=?",selected);
        assertEquals(result.toString(),tree(call(post(API+"/"+attempt+"/submit"),token).andExpect(status().isOk())).toString());
        assertFalse(result.toString().contains("transcript"));assertFalse(result.toString().contains("storage"));
    }
    @Test void dbProtectsPinnedConfigurationAndCreateFailureRollsBackWholeAggregate()throws Exception {
        var token=token(ACTOR);var session=section(token,"READING",6,20);var attempt=id(session);
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("update attempts set configured_duration_seconds=60 where attempt_id=?",attempt));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("update attempts set section_type=null where attempt_id=?",attempt));
        authenticate(ACTOR,EXAM);
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).execute(status->{
            sections.create(ACTOR,new SectionStartQuery("LISTENING",7,10));throw new IllegalStateException("Fixture rollback after flush");
        }));
        assertEquals(1,count("select count(*) from attempts where user_id=?",ACTOR));assertEquals(6,count("select count(*) from attempt_question_snapshots"));
    }
}
