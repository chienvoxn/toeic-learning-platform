package com.toeic.backend.questionbank.api;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Dùng authoring thật, test DB riêng; không triển khai quyết định duyệt/publish còn OPEN. */
class ContentAdministrationIntegrationTest extends QuestionAuthoringTestSupport {
    static final String C="/api/v1/teacher/contents";
    final List<UUID> contents=new ArrayList<>();
    final List<UUID> attempts=new ArrayList<>();
    String administrator,owner,learner;
    record Item(String kind,UUID id,UUID version) {
        String path(){return (kind.equals("CONTENT")?C:kind.equals("QUESTION")?Q:G)+"/"+id;}
        String table(){return kind.equals("CONTENT")?"learning_contents":kind.equals("QUESTION")?"questions":"question_groups";}
        String column(){return kind.equals("CONTENT")?"content_id":kind.equals("QUESTION")?"question_id":"group_id";}
    }
    @BeforeEach void adminTokens() throws Exception {administrator=token(ADMIN);owner=token(ACTOR);learner=token(LEARNER);}
    @AfterEach void removeContent() {
        for(UUID id:attempts) {
            jdbc.update("DELETE FROM attempt_answers WHERE attempt_id=?",id);
            jdbc.update("DELETE FROM attempt_part_results WHERE attempt_id=?",id);
            jdbc.update("DELETE FROM attempt_question_snapshots WHERE attempt_id=?",id);
            jdbc.update("DELETE FROM attempts WHERE attempt_id=?",id);
        }
        for(UUID id:contents) {
            jdbc.update("DELETE FROM audit_logs WHERE entity_id=?",id);
            jdbc.update("DELETE FROM learning_content_history WHERE content_id=?",id);jdbc.update("DELETE FROM bookmarks WHERE content_id=?",id);
            jdbc.update("UPDATE learning_contents SET current_published_version_id=null WHERE content_id=?",id);
            jdbc.update("DELETE FROM learning_content_version_resources WHERE content_version_id IN (SELECT content_version_id FROM learning_content_versions WHERE content_id=?)",id);
            jdbc.update("DELETE FROM learning_content_versions WHERE content_id=?",id);jdbc.update("DELETE FROM learning_contents WHERE content_id=?",id);
        }
    }
    Item item(String kind,boolean published) throws Exception {
        if(kind.equals("CONTENT")) {
            var result=tree(call(post(C).contentType(APPLICATION_JSON).content("{\"categoryCode\":\"GRAMMAR\",\"title\":\"ADMIN03 QA\",\"body\":\"private-content-marker\"}"),owner).andExpect(status().isCreated()));
            var id=UUID.fromString(result.path("content").path("contentId").asString());contents.add(id);
            var version=UUID.fromString(result.path("versions").get(0).path("contentVersionId").asString());
            if(published) {
                jdbc.update("UPDATE learning_content_versions SET status='PUBLISHED',published_at=now() WHERE content_version_id=?",version);
                jdbc.update("UPDATE learning_contents SET status='PUBLISHED',visibility='PUBLIC',published_at=now(),current_published_version_id=? WHERE content_id=?",version,id);
            }
            return new Item(kind,id,version);
        }
        var result=kind.equals("QUESTION")?create(owner,5,null):group(owner);
        var id=kind.equals("QUESTION")?qid(result):gid(result);var version=kind.equals("QUESTION")?qv(result,0):gv(result,0);
        if(published)publish(kind.equals("QUESTION_GROUP"),id,version);
        return new Item(kind,id,version);
    }
    Map<String,Object> state(Item item){return jdbc.queryForMap("SELECT status,current_published_version_id,updated_at FROM "+item.table()+" WHERE "+item.column()+"=?",item.id());}
    int events(Item item){return count("SELECT count(*) FROM audit_logs WHERE entity_id=? AND action=?",item.id(),item.kind()+"_ARCHIVED");}
    JsonNode detail(Item item) throws Exception{return tree(call(get(item.path()),administrator).andExpect(status().isOk()));}
    @Test void existingApiEnforcesOwnershipPermissionsAndUsesServerCapabilities() throws Exception {
        call(get(C),null).andExpect(status().isUnauthorized());
        for(String path:List.of(C,Q,G))call(get(path),learner).andExpect(status().isForbidden());
        for(String kind:List.of("CONTENT","QUESTION","QUESTION_GROUP")) {
            var item=item(kind,false);
            call(get(item.path()),token(OTHER)).andExpect(status().isNotFound());call(delete(item.path()),token(OTHER)).andExpect(status().isNotFound());
            call(get(item.path()),owner).andExpect(status().isOk());call(get(item.path()),administrator).andExpect(status().isOk());
            call(delete(item.path()),learner).andExpect(status().isForbidden());assertEquals(0,events(item));
        }
        call(get("/api/v1/users/me"),administrator).andExpect(jsonPath("$.adminCapabilities.contentManagement").value(true)).andExpect(jsonPath("$.adminCapabilities.questionManagement").value(true));
        call(get("/api/v1/users/me"),owner).andExpect(jsonPath("$.adminCapabilities.contentManagement").value(false));
        call(get(C+"?status=UNKNOWN"),administrator).andExpect(status().isBadRequest());
    }
    @Test void archiveThreeKindsIsIdempotentPreservesVersionsAndShowsOnlySanitizedAudit() throws Exception {
        for(String kind:List.of("CONTENT","QUESTION","QUESTION_GROUP")) {
            var item=item(kind,true);var versions=detail(item).path("versions");
            call(delete(item.path()),administrator).andExpect(status().isNoContent());call(delete(item.path()),administrator).andExpect(status().isNoContent());
            assertEquals("ARCHIVED",state(item).get("status"));assertEquals(item.version(),state(item).get("current_published_version_id"));assertEquals(versions,detail(item).path("versions"));assertEquals(1,events(item));
            var audit=tree(call(get("/api/v1/admin/audit-logs?action="+kind+"_ARCHIVED&entityType="+kind+"&entityId="+item.id()),administrator).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)));
            var row=tree(call(get("/api/v1/admin/audit-logs/"+audit.path("items").get(0).path("auditId").asString()),administrator).andExpect(status().isOk()));
            assertEquals("PUBLISHED",row.path("changes").path("beforeStatus").asString());assertEquals("ARCHIVED",row.path("changes").path("afterStatus").asString());
            assertFalse(row.toString().contains("private-content-marker"));assertFalse(row.toString().contains("Q03_ANSWER_EXPLANATION"));assertFalse(row.toString().contains("transcript"));
            var body=kind.equals("CONTENT")?"{\"title\":\"forbidden\"}":kind.equals("QUESTION")?"{\"questionText\":\"forbidden\"}":"{\"passageText\":\"forbidden\"}";
            call(patch(item.path()).contentType(APPLICATION_JSON).content(body),administrator).andExpect(status().isConflict());
        }
    }
    @ParameterizedTest @ValueSource(strings={"CONTENT","QUESTION","QUESTION_GROUP"})
    void failedAuditRollsBackArchiveCompletely(String kind) throws Exception {
        var item=item(kind,true);var before=state(item);var versions=detail(item).path("versions");
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT admin03_audit_failure CHECK (entity_id <> '"+item.id()+"'::uuid OR action <> '"+kind+"_ARCHIVED')");
        try {
            var response=call(delete(item.path()),administrator).andExpect(status().isServiceUnavailable()).andReturn().getResponse().getContentAsString();
            assertFalse(response.contains("admin03_audit_failure"));assertEquals(before,state(item));assertEquals(versions,detail(item).path("versions"));assertEquals(0,events(item));
        } finally {jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT admin03_audit_failure");}
    }
    @ParameterizedTest @ValueSource(strings={"CONTENT","QUESTION","QUESTION_GROUP"})
    void concurrentArchiveAndEditKeepPublishedVersionAndOneArchiveEvent(String kind) throws Exception {
        var item=item(kind,true);var published=detail(item).path("versions").get(0);var pool=Executors.newFixedThreadPool(2);var futures=new ArrayList<Future<Integer>>();
        String body=kind.equals("CONTENT")?"{\"title\":\"race edit\"}":kind.equals("QUESTION")?"{\"questionText\":\"race edit\"}":"{\"passageText\":\"race edit\"}";
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.query("SELECT "+item.column()+" FROM "+item.table()+" WHERE "+item.column()+"=? FOR UPDATE",rs -> {},item.id());
                futures.add(pool.submit(() -> call(delete(item.path()),administrator).andReturn().getResponse().getStatus()));
                futures.add(pool.submit(() -> call(patch(item.path()).contentType(APPLICATION_JSON).content(body),administrator).andReturn().getResponse().getStatus()));
                awaitLocks(2);
            });
            assertEquals(204,futures.get(0).get(15,TimeUnit.SECONDS));assertTrue(Set.of(200,409).contains(futures.get(1).get(15,TimeUnit.SECONDS)));
            assertEquals("ARCHIVED",state(item).get("status"));assertEquals(published,detail(item).path("versions").get(0));assertEquals(1,events(item));
        } finally {pool.shutdownNow();}
    }
    @Test void archivedQuestionCannotBeSelectedButExistingAttemptStillGradesPinnedSnapshot() throws Exception {
        var item=item("QUESTION",true);
        var attempt=tree(call(post("/api/v1/attempts").contentType(APPLICATION_JSON).content("{\"sourceType\":\"PRACTICE_SNAPSHOT\",\"practice\":{\"partNumbers\":[5],\"questionCount\":1}}"),learner).andExpect(status().isCreated()));
        attempts.add(UUID.fromString(attempt.path("attemptId").asString()));
        String path="/api/v1/attempts/"+attempt.path("attemptId").asString();var question=attempt.path("questions").get(0);assertEquals(item.version().toString(),question.path("questionVersionId").asString());
        String choice=question.path("choices").get(0).path("choiceId").asString();
        call(put(path+"/answers/"+item.version()).contentType(APPLICATION_JSON).content("{\"selectedChoiceId\":\""+choice+"\"}"),learner).andExpect(status().isOk());
        call(delete(item.path()),administrator).andExpect(status().isNoContent());
        call(get("/api/v1/questions/"+item.id()),administrator).andExpect(status().isNotFound());
        call(post(path+"/submit"),learner).andExpect(status().isOk()).andExpect(jsonPath("$.correctCount").value(1));
        var result=tree(call(get(path+"/result"),learner).andExpect(status().isOk()));
        call(delete(item.path()),administrator).andExpect(status().isNoContent());assertEquals(result,tree(call(get(path+"/result"),learner).andExpect(status().isOk())));
    }
    @Test void archivedGroupDoesNotCascadeChildrenOrChangePinnedAttemptPassageAndResult() throws Exception {
        var group=item("QUESTION_GROUP",true);
        for(int order=1;order<=2;order++) {
            var q=create(owner,6,group.version());
            // Explicit fixture order; no publication/order-edit API is implied by this setup.
            jdbc.update("UPDATE question_versions SET group_question_order=? WHERE question_version_id=?",order,qv(q,0));
            publish(false,qid(q),qv(q,0));
        }
        var attempt=tree(call(post("/api/v1/attempts").contentType(APPLICATION_JSON).content("{\"sourceType\":\"PRACTICE_SNAPSHOT\",\"practice\":{\"partNumbers\":[6],\"questionCount\":2}}"),learner).andExpect(status().isCreated()));
        var id=UUID.fromString(attempt.path("attemptId").asString());attempts.add(id);String path="/api/v1/attempts/"+id;
        var before=tree(call(get(path),learner).andExpect(status().isOk()));
        call(delete(group.path()),administrator).andExpect(status().isNoContent());
        assertEquals(2,count("SELECT count(*) FROM questions WHERE group_id=? AND status='PUBLISHED'",group.id()));
        assertEquals(before,tree(call(get(path),learner).andExpect(status().isOk())));
        call(post(path+"/submit"),learner).andExpect(status().isOk()).andExpect(jsonPath("$.unansweredCount").value(2));
        assertEquals(1,events(group));
    }
    @ParameterizedTest @ValueSource(strings={"CONTENT","QUESTION","QUESTION_GROUP"})
    void twoConcurrentArchivesProduceOnlyOneAudit(String kind) throws Exception {
        var item=item(kind,false);var pool=Executors.newFixedThreadPool(2);var futures=new ArrayList<Future<Integer>>();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.query("SELECT "+item.column()+" FROM "+item.table()+" WHERE "+item.column()+"=? FOR UPDATE",rs -> {},item.id());
                for(int i=0;i<2;i++)futures.add(pool.submit(() -> call(delete(item.path()),administrator).andReturn().getResponse().getStatus()));
                awaitLocks(2);
            });
            for(var result:futures)assertEquals(204,result.get(15,TimeUnit.SECONDS));
            assertEquals(1,events(item));assertEquals("ARCHIVED",state(item).get("status"));
        } finally {pool.shutdownNow();}
    }
    void awaitLocks(int count) {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);boolean ready=false;
        while(System.nanoTime()<end) {
            jdbc.query("SELECT pg_stat_clear_snapshot()",rs -> {});
            ready=jdbc.queryForObject("SELECT count(*)>=? FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock'",Boolean.class,count);
            if(ready)break;try {Thread.sleep(20);}catch(InterruptedException e){throw new AssertionError(e);}
        }
        assertTrue(ready,"Both writes must wait on the aggregate row before release");
    }
    Object payload(String kind) {
        if(kind.equals("QUESTION"))return payload(5,null);
        return kind.equals("CONTENT")?Map.of("categoryCode","GRAMMAR","title","Audit private title","body","authoring-private-marker")
                :Map.of("groupType","PASSAGE","instruction","authoring-private-marker","passageText","group secret");
    }
    String collection(String kind) {return kind.equals("CONTENT")?C:kind.equals("QUESTION")?Q:G;}
    String editPayload(String kind,String value) throws Exception {return json.writeValueAsString(Map.of(kind.equals("CONTENT")?"title":kind.equals("QUESTION")?"questionText":"passageText",value));}
    @ParameterizedTest @ValueSource(strings={"CONTENT","QUESTION","QUESTION_GROUP"})
    void createAndUpdateAuditContainOnlyTypedMetadataAndKeepPublishedVersion(String kind) throws Exception {
        var item=item(kind,false);assertEquals(1,count("SELECT count(*) FROM audit_logs WHERE entity_id=? AND action=?",item.id(),kind+"_CREATED"));
        call(patch(item.path()).contentType(APPLICATION_JSON).content(editPayload(kind,"first-private-marker")),administrator).andExpect(status().isOk());
        if(kind.equals("CONTENT")) {
            jdbc.update("UPDATE learning_content_versions SET status='PUBLISHED',published_at=now() WHERE content_version_id=?",item.version());
            jdbc.update("UPDATE learning_contents SET status='PUBLISHED',current_published_version_id=? WHERE content_id=?",item.version(),item.id());
        } else publish(kind.equals("QUESTION_GROUP"),item.id(),item.version());
        var published=detail(item).path("versions").get(0);
        call(patch(item.path()).contentType(APPLICATION_JSON).content(editPayload(kind,"next-private-marker")),administrator).andExpect(status().isOk());
        assertEquals(published,detail(item).path("versions").get(0));assertEquals(2,detail(item).path("versions").size());
        var logs=tree(call(get("/api/v1/admin/audit-logs?entityType="+kind+"&entityId="+item.id()+"&sort=createdAt,asc"),administrator).andExpect(status().isOk()));assertEquals(3,logs.path("totalElements").asInt());
        for(var log:logs.path("items")) {
            var audit=tree(call(get("/api/v1/admin/audit-logs/"+log.path("auditId").asString()),administrator).andExpect(status().isOk()));
            assertTrue(audit.path("changes").has("afterVersionId"));assertTrue(audit.path("changes").has("requestedFields"));
            assertFalse(audit.toString().contains("private-marker"));assertFalse(audit.toString().contains("Q03_ANSWER_EXPLANATION"));
        }
    }
    @ParameterizedTest @ValueSource(strings={"CONTENT","QUESTION","QUESTION_GROUP"})
    void failedCreateAuditLeavesNoAggregateOrVersion(String kind) throws Exception {
        String table=kind.equals("CONTENT")?"learning_contents":kind.equals("QUESTION")?"questions":"question_groups";
        int before=count("SELECT count(*) FROM "+table);
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT admin03_create_failure CHECK (actor_user_id <> '"+ADMIN+"'::uuid OR action <> '"+kind+"_CREATED')");
        try {
            call(post(collection(kind)).contentType(APPLICATION_JSON).content(json.writeValueAsString(payload(kind))),administrator).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("CONTENT_AUDIT_UNAVAILABLE"));
            assertEquals(before,count("SELECT count(*) FROM "+table));
        } finally {jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT admin03_create_failure");}
    }
    @ParameterizedTest @ValueSource(strings={"CONTENT","QUESTION","QUESTION_GROUP"})
    void failedUpdateAuditRollsBackNewDraftAndAllMetadata(String kind) throws Exception {
        var item=item(kind,true);var before=detail(item);var state=state(item);
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT admin03_update_failure CHECK (entity_id <> '"+item.id()+"'::uuid OR action <> '"+kind+"_UPDATED')");
        try {
            call(patch(item.path()).contentType(APPLICATION_JSON).content(editPayload(kind,"rollback-private-marker")),administrator).andExpect(status().isServiceUnavailable());
            assertEquals(before,detail(item));assertEquals(state,state(item));
            assertEquals(0,count("SELECT count(*) FROM audit_logs WHERE entity_id=? AND action=?",item.id(),kind+"_UPDATED"));
        } finally {jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT admin03_update_failure");}
    }
    @ParameterizedTest @ValueSource(strings={"CONTENT","QUESTION","QUESTION_GROUP"})
    void concurrentEditsReuseOneDraftAndAuditBothCommittedWrites(String kind) throws Exception {
        var item=item(kind,true);var published=detail(item).path("versions").get(0);var pool=Executors.newFixedThreadPool(2);var results=new ArrayList<Future<Integer>>();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.query("SELECT "+item.column()+" FROM "+item.table()+" WHERE "+item.column()+"=? FOR UPDATE",rs -> {},item.id());
                for(String value:List.of("edit-a","edit-b"))results.add(pool.submit(() -> call(patch(item.path()).contentType(APPLICATION_JSON).content(editPayload(kind,value)),administrator).andReturn().getResponse().getStatus()));
                awaitLocks(2);
            });
            for(var result:results)assertEquals(200,result.get(15,TimeUnit.SECONDS));
            assertEquals(2,detail(item).path("versions").size());assertEquals(published,detail(item).path("versions").get(0));
            assertEquals(2,count("SELECT count(*) FROM audit_logs WHERE entity_id=? AND action=?",item.id(),kind+"_UPDATED"));
        } finally {pool.shutdownNow();}
    }
    @Test void questionReferenceOptionsAreRealAndPermissionProtected() throws Exception {
        String path="/api/v1/teacher/question-options";
        call(get(path),null).andExpect(status().isUnauthorized());call(get(path),learner).andExpect(status().isForbidden());
        var options=tree(call(get(path),owner).andExpect(status().isOk()));
        assertEquals(count("SELECT count(*) FROM toeic_parts"),options.path("parts").size());
        assertTrue(options.path("parts").findValues("partId").stream().allMatch(node -> node.isIntegralNumber()));
        call(get(path+"?status=DRAFT"),administrator).andExpect(status().isBadRequest());
    }
}
