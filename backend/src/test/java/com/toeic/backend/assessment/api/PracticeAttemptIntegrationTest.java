package com.toeic.backend.assessment.api;

import com.toeic.backend.assessment.application.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PracticeAttemptIntegrationTest extends PracticeTestSupport {
    @ParameterizedTest @CsvSource({"1,1","2,1","3,3","4,2","5,1","6,2","7,3"})
    void eachPartCreatesPublishedSnapshotWithSafePayload(int part,int size)throws Exception {
        var session=create(token(ACTOR),List.of(part),size);safe(session);
        assertEquals(size,session.get("totalQuestions").asInt());
        assertEquals("PRACTICE_SNAPSHOT",session.get("sourceType").asString());assertFalse(session.get("expired").asBoolean());
        assertEquals(NOW,Instant.parse(session.get("startedAt").asString()));
        assertEquals(NOW.plus(Duration.ofHours(24)),Instant.parse(session.get("expiresAt").asString()));
        int order=0;
        for(var question:session.get("questions")) {
            assertEquals(++order,question.get("order").asInt());assertEquals(part,question.get("partNumber").asInt());
            assertEquals(part==2?3:4,question.get("choices").size());
            for(int n=0;n<question.get("choices").size();n++)assertEquals(String.valueOf((char)('A'+n)),question.get("choices").get(n).get("label").asString());
            if(Set.of(3,4,6,7).contains(part))assertFalse(question.get("passage").isNull());
        }
        assertEquals(0,count("select count(*) from attempt_answers where attempt_id=?",id(session)));
        assertEquals(size,count("select count(*) from attempt_question_snapshots where attempt_id=?",id(session)));
        assertEquals(1,count("select count(*) from attempts where attempt_id=? and user_id=? and test_version_id is null and attempt_type='PRACTICE'",id(session),ACTOR));
    }
    @Test void combinesAllPartsAndReloadKeepsPinnedOrder()throws Exception {
        var token=token(ACTOR);var session=create(token,List.of(7,5,3,1,6,2,4),13);
        assertEquals(session.toString(),read(token,id(session)).toString());
        var parts=new ArrayList<Integer>();var ids=new HashSet<String>();
        for(var question:session.get("questions")){parts.add(question.get("partNumber").asInt());assertTrue(ids.add(question.get("questionId").asString()));}
        assertEquals(parts.stream().sorted().toList(),parts);assertEquals(Set.of(1,2,3,4,5,6,7),new HashSet<>(parts));safe(session);
    }
    @Test void nonGreedyGroupsExactCountAndPersistedWithinGroupOrder()throws Exception {
        var session=create(token(ACTOR),List.of(3),6);var members=new HashMap<String,List<Integer>>();
        for(var question:session.get("questions"))members.computeIfAbsent(question.get("passage").get("groupVersionId").asString(),k->new ArrayList<>())
                .add(Integer.parseInt(question.get("questionId").asString().substring(24)));
        assertEquals(2,members.size());assertEquals(List.of(10,9,8),members.get(gv(2).toString()));assertEquals(List.of(13,12,11),members.get(gv(3).toString()));
        assertEquals(session.toString(),read(token(ACTOR),id(session)).toString());
    }
    @ParameterizedTest @ValueSource(strings={"{\"partNumbers\":[],\"questionCount\":1}","{\"partNumbers\":[0],\"questionCount\":1}","{\"partNumbers\":[8],\"questionCount\":1}",
            "{\"partNumbers\":[1,1],\"questionCount\":2}","{\"partNumbers\":[1.0],\"questionCount\":1}","{\"partNumbers\":[1],\"questionCount\":0}",
            "{\"partNumbers\":[1],\"questionCount\":201}","{\"partNumbers\":[1],\"questionCount\":1.5}","{\"partNumbers\":[1],\"questionCount\":\"1\"}",
            "{\"partNumbers\":[1],\"questionCount\":1,\"difficulty\":\"BAD\"}","{\"partNumbers\":[1],\"questionCount\":1,\"topicId\":0}",
            "{\"partIds\":[1],\"questionCount\":1}","{\"partNumbers\":[1],\"questionCount\":1,\"ownerId\":\"spoof\"}","{\"partNumbers\":[1],\"questionCount\":1,\"questionCount\":2}"})
    void invalidFiltersReturn400WithoutPartialAttempt(String filter)throws Exception {
        call(post(API).contentType(APPLICATION_JSON).content("{\"sourceType\":\"PRACTICE_SNAPSHOT\",\"practice\":"+filter+"}"),token(ACTOR))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));assertEquals(0,count("select count(*) from attempts where user_id=?",ACTOR));
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"sourceType\":\"TEST_VERSION\",\"practice\":{}}","{\"sourceType\":\"PRACTICE_SNAPSHOT\",\"practice\":null}",
            "{\"sourceType\":\"PRACTICE_SNAPSHOT\",\"sourceType\":\"PRACTICE_SNAPSHOT\",\"practice\":{\"partNumbers\":[1],\"questionCount\":1}}",
            "{\"sourceType\":\"PRACTICE_SNAPSHOT\",\"practice\":{\"partNumbers\":[1],\"questionCount\":1},\"userId\":\"spoof\"}"})
    void rootAllowlistAndSourceContract(String body)throws Exception {call(post(API).contentType(APPLICATION_JSON).content(body),token(ACTOR)).andExpect(status().isBadRequest());}
    @Test void invalidUuidQueryAndUnsupportedRoutes()throws Exception {
        var token=token(ACTOR);call(get(API+"/bad-id"),token).andExpect(status().isBadRequest());
        call(post(API).param("size","1").contentType(APPLICATION_JSON).content(payload(List.of(1),1)),token).andExpect(status().isBadRequest());
        call(get(API+"/"+UUID.randomUUID()).param("version","1"),token).andExpect(status().isBadRequest());
        call(post(API+"/"+UUID.randomUUID()+"/submit"),token).andExpect(status().isNotFound());
    }
    @Test void insufficientCombinationAndPartCoverageRollBack()throws Exception {
        var token=token(ACTOR);
        for(var parts:List.of(List.of(3),List.of(1,3)))call(post(API).contentType(APPLICATION_JSON).content(payload(parts,parts.size()==1?5:1)),token)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRACTICE_QUESTIONS_INSUFFICIENT"));
        assertEquals(0,count("select count(*) from attempts where user_id=?",ACTOR));assertEquals(0,count("select count(*) from attempt_question_snapshots"));
    }
    @ParameterizedTest @ValueSource(strings={"noCorrect","twoCorrect","missingChoice","badLabel","badOrder","blankChoice","blankText","wrongType","draftVersion","archivedQuestion","futureVersion","nullPointer"})
    void onlyValidPublishedSingleChoiceQuestionsAreSelected(String problem)throws Exception {
        switch(problem){
            case "noCorrect"->jdbc.update("update question_choices set is_correct=false where question_version_id=?",v(3));
            case "twoCorrect"->jdbc.update("update question_choices set is_correct=true where question_version_id=? and choice_label='B'",v(3));
            case "missingChoice"->jdbc.update("delete from question_choices where question_version_id=? and choice_label='C'",v(3));
            case "badLabel"->jdbc.update("update question_choices set choice_label='X' where question_version_id=? and choice_label='B'",v(3));
            case "badOrder"->jdbc.update("update question_choices set display_order=4 where question_version_id=? and choice_label='B'",v(3));
            case "blankChoice"->jdbc.update("update question_choices set content=E'\\t\\n' where question_version_id=? and choice_label='B'",v(3));
            case "blankText"->jdbc.update("update question_versions set question_text=' ' where question_version_id=?",v(3));
            case "wrongType"->{jdbc.update("insert into question_types(code,name) values ('PRAC_OTHER','Other')");jdbc.update("update questions set question_type_id=(select question_type_id from question_types where code='PRAC_OTHER') where question_id=?",q(3));}
            case "draftVersion"->jdbc.update("update question_versions set status='DRAFT' where question_version_id=?",v(3));
            case "archivedQuestion"->jdbc.update("update questions set status='ARCHIVED' where question_id=?",q(3));
            case "futureVersion"->jdbc.update("update question_versions set published_at=? where question_version_id=?",OffsetDateTime.ofInstant(NOW.plusSeconds(60),ZoneOffset.UTC),v(3));
            case "nullPointer"->jdbc.update("update questions set current_published_version_id=null where question_id=?",q(3));
        }
        call(post(API).contentType(APPLICATION_JSON).content(payload(List.of(2),1)),token(ACTOR)).andExpect(status().isConflict());
        assertEquals(0,count("select count(*) from attempts where user_id=?",ACTOR));
    }
    @Test void topicAndDifficultyApplyToWholeGroupWithoutCutting()throws Exception {
        var token=token(ACTOR);
        jdbc.update("update question_versions set difficulty='HARD' where question_version_id in (?,?)",v(4),v(8));
        String body="{\"sourceType\":\"PRACTICE_SNAPSHOT\",\"practice\":{\"partNumbers\":[3],\"questionCount\":6,\"topicId\":960001,\"difficulty\":\"EASY\"}}";
        call(post(API).contentType(APPLICATION_JSON).content(body),token).andExpect(status().isConflict());
        var session=tree(call(post(API).contentType(APPLICATION_JSON).content(body.replace("\"questionCount\":6","\"questionCount\":3")),token).andExpect(status().isCreated()));
        for(var question:session.get("questions"))assertEquals(gv(3).toString(),question.get("passage").get("groupVersionId").asString());
        jdbc.update("update questions set topic_id=null where question_id=?",q(11));
        call(post(API).contentType(APPLICATION_JSON).content(body),token).andExpect(status().isConflict());
    }
    @ParameterizedTest @ValueSource(strings={"childArchived","childPointerMissing","childGroupPinMissing","groupArchived","groupVersionArchived","groupDraft","groupPointerMissing"})
    void unavailableGroupOrMemberExcludesWholeUnit(String problem)throws Exception {
        switch(problem){
            case "childArchived"->jdbc.update("update questions set status='ARCHIVED' where question_id=?",q(8));
            case "childPointerMissing"->jdbc.update("update questions set current_published_version_id=null where question_id=?",q(8));
            case "childGroupPinMissing"->jdbc.update("update question_versions set group_version_id=null where question_version_id=?",v(8));
            case "groupArchived"->jdbc.update("update question_groups set status='ARCHIVED' where group_id=?",g(2));
            case "groupVersionArchived"->jdbc.update("update question_group_versions set status='ARCHIVED',archived_at=now() where group_version_id=?",gv(2));
            case "groupDraft"->jdbc.update("update question_groups set status='DRAFT' where group_id=?",g(2));
            case "groupPointerMissing"->jdbc.update("update question_groups set current_published_version_id=null where group_id=?",g(2));
        }
        call(post(API).contentType(APPLICATION_JSON).content(payload(List.of(3),6)),token(ACTOR)).andExpect(status().isConflict());
    }
    @Test void authPermissionOwnershipAndRevocationAreIndependentOfRole()throws Exception {
        call(post(API).contentType(APPLICATION_JSON).content(payload(List.of(1),1)),null).andExpect(status().isUnauthorized());
        call(get(API+"/"+UUID.randomUUID()),"invalid").andExpect(status().isUnauthorized());
        for(var actor:List.of(TEACHER,ADMIN)) {
            var token=token(actor);call(post(API).contentType(APPLICATION_JSON).content(payload(List.of(1),1)),token).andExpect(status().isForbidden());
            jdbc.update("insert into role_permissions select r.role_id,p.permission_id from roles r cross join permissions p where r.name=? and p.code=? on conflict do nothing",actor.equals(TEACHER)?"TEACHER":"ADMIN",PERMISSION);
            create(token,List.of(1),1);
        }
        var actorToken=token(ACTOR);var session=create(actorToken,List.of(1),1);
        call(get(API+"/"+id(session)),token(OTHER)).andExpect(status().isNotFound());
        call(get(API+"/"+UUID.randomUUID()),actorToken).andExpect(status().isNotFound());
        call(get("/api/v1/questions"),actorToken).andExpect(status().isForbidden());
        jdbc.update("delete from user_roles where user_id=?",ACTOR);
        call(get(API+"/"+id(session)),actorToken).andExpect(status().isForbidden());
    }
    @Test void serviceRejectsMissingPermissionAndSpoofedActor() {
        authenticate(ACTOR,PERMISSION);assertThrows(AccessDeniedException.class,()->service.get(OTHER,UUID.randomUUID()));
        authenticate(ACTOR);assertThrows(AccessDeniedException.class,()->service.create(ACTOR,new PracticeStartQuery(List.of(1),1,null,null)));
    }
    @Test void mediaUsesViewPolicyNotOwnerOrReuseGrantAndIsRecheckedOnReload()throws Exception {
        var token=token(ACTOR);var session=create(token,List.of(1),2);safe(session);
        assertTrue(session.toString().contains("b3000000-0000-0000-0000-000000000005"));assertFalse(session.toString().contains("b3000000-0000-0000-0000-000000000003"));
        jdbc.update("update media_resources set visibility='PRIVATE' where resource_id='b3000000-0000-0000-0000-000000000005'");
        var after=read(token,id(session));safe(after);assertFalse(after.toString().contains("b3000000-0000-0000-0000-000000000005"));
        assertEquals(2,after.get("totalQuestions").asInt());
    }
    @Test void expiryBoundaryIsReadOnlyAndDoesNotDeleteOrRepin()throws Exception {
        var token=token(ACTOR);var session=create(token,List.of(2),1);
        jdbc.update("update attempts set started_at=?,expires_at=? where attempt_id=?",OffsetDateTime.ofInstant(NOW.minusSeconds(86400),ZoneOffset.UTC),OffsetDateTime.ofInstant(NOW,ZoneOffset.UTC),id(session));
        var after=read(token,id(session));assertTrue(after.get("expired").asBoolean());assertEquals("IN_PROGRESS",after.get("status").asString());
        assertEquals(session.get("questions").toString(),after.get("questions").toString());assertEquals(1,count("select count(*) from attempts where attempt_id=?",id(session)));
    }
    @Test void archiveAndNewPublishedPointersNeverChangePinnedSnapshot()throws Exception {
        var token=token(ACTOR);var session=create(token,List.of(4),2);
        var newGroup=UUID.randomUUID();
        jdbc.update("insert into question_group_versions(group_version_id,group_id,version_number,status,instruction,passage_text,published_at,created_at) values (?,?,2,'PUBLISHED','NEW','NEW','2026-01-02T00:00:00Z','2026-01-02T00:00:00Z')",newGroup,g(4));
        for(int n:List.of(14,15)) {
            var newer=UUID.randomUUID();jdbc.update("insert into question_versions(question_version_id,question_id,version_number,status,group_version_id,question_text,explanation,difficulty,published_at,created_at) values (?,?,2,'PUBLISHED',?,'NEW','NEW','HARD','2026-01-02T00:00:00Z','2026-01-02T00:00:00Z')",newer,q(n),newGroup);
            jdbc.update("update questions set current_published_version_id=?,status='ARCHIVED' where question_id=?",newer,q(n));
            jdbc.update("update question_versions set status='ARCHIVED',archived_at=now() where question_version_id=?",v(n));
        }
        jdbc.update("update question_groups set current_published_version_id=?,status='ARCHIVED' where group_id=?",newGroup,g(4));
        jdbc.update("update question_group_versions set status='ARCHIVED',archived_at=now() where group_version_id=?",gv(4));
        assertEquals(session.toString(),read(token,id(session)).toString());
    }
    @Test void corruptedSnapshotReturnsConflictWithoutCurrentFallback()throws Exception {
        var token=token(ACTOR);var session=create(token,List.of(4),2);
        // The DB composite FK permits NULL, so application must detect this corrupted group pin.
        jdbc.update("update attempt_question_snapshots set question_group_version_id=null where attempt_id=?",id(session));
        call(get(API+"/"+id(session)),token).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRACTICE_SNAPSHOT_UNAVAILABLE"));
    }
    @Test void maximumTwoHundredIsValidButInsufficientBankIs409()throws Exception {
        call(post(API).contentType(APPLICATION_JSON).content(payload(List.of(1,2,3,4,5,6,7),200)),token(ACTOR))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRACTICE_QUESTIONS_INSUFFICIENT"));
    }
    @Test void draftVersionIsNeverChosenAndMissingPointerNeverFallsBack()throws Exception {
        var newer=UUID.randomUUID();jdbc.update("insert into question_versions(question_version_id,question_id,version_number,status,question_text,created_at) values (?,?,2,'DRAFT','HIDDEN DRAFT',now())",newer,q(3));
        var token=token(ACTOR);var session=create(token,List.of(2),1);
        assertEquals(v(3).toString(),session.get("questions").get(0).get("questionVersionId").asString());assertFalse(session.toString().contains("HIDDEN DRAFT"));
        jdbc.update("update questions set current_published_version_id=null where question_id=?",q(3));
        call(post(API).contentType(APPLICATION_JSON).content(payload(List.of(2),1)),token).andExpect(status().isConflict());
        assertEquals(session.toString(),read(token,id(session)).toString());
    }
    @Test void groupWithMemberInAnotherPartCannotBeSilentlyCut()throws Exception {
        jdbc.update("update questions set part_id=(select part_id from toeic_parts where part_number=4) where question_id=?",q(8));
        call(post(API).contentType(APPLICATION_JSON).content(payload(List.of(3),6)),token(ACTOR)).andExpect(status().isConflict());
    }
    @ParameterizedTest @ValueSource(strings={"missing","duplicate"})
    void unknownOrDuplicateBusinessOrderRejectsWholeGroup(String problem)throws Exception {
        if(problem.equals("missing"))jdbc.update("update question_versions set group_question_order=null where question_version_id=?",v(8));
        else jdbc.update("update question_versions set group_question_order=(select group_question_order from question_versions where question_version_id=?) where question_version_id=?",v(9),v(8));
        call(post(API).contentType(APPLICATION_JSON).content(payload(List.of(3),6)),token(ACTOR))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRACTICE_QUESTIONS_INSUFFICIENT"));
        assertEquals(0,count("select count(*) from attempts where user_id=?",ACTOR));
    }
    @Test void workingDraftPositionDoesNotChangePublishedGroupOrder()throws Exception {
        var draft=UUID.randomUUID();
        jdbc.update("insert into question_versions(question_version_id,question_id,version_number,status,group_version_id,group_question_order,question_text,created_at) select ?,question_id,2,'DRAFT',group_version_id,group_question_order,'DRAFT',now() from question_versions where question_version_id=?",draft,v(8));
        var session=create(token(ACTOR),List.of(3),6);
        var ids=new ArrayList<String>();for(var question:session.get("questions"))ids.add(question.get("questionVersionId").asString());
        assertTrue(ids.contains(v(8).toString()));assertFalse(ids.contains(draft.toString()));
    }
}
