package com.toeic.backend.questionbank.api;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ModerationIntegrationTest extends QuestionAuthoringTestSupport {
    static final String C="/api/v1/teacher/contents";
    String teacher,admin,learner;final List<UUID> contents=new ArrayList<>(),attempts=new ArrayList<>();
    record Item(String kind,UUID parent,UUID version) { String path(){return "/api/v1/moderation/"+kind+"/"+parent+"/versions/"+version;} }
    @BeforeEach void sessions() throws Exception {teacher=token(ACTOR);admin=token(ADMIN);learner=token(LEARNER);}
    @AfterEach void cleanupContent(){
        for(UUID id:attempts){jdbc.update("DELETE FROM attempt_answers WHERE attempt_id=?",id);jdbc.update("DELETE FROM attempt_part_results WHERE attempt_id=?",id);jdbc.update("DELETE FROM attempt_question_snapshots WHERE attempt_id=?",id);jdbc.update("DELETE FROM attempts WHERE attempt_id=?",id);}
        for(UUID id:contents){
            jdbc.update("DELETE FROM audit_logs WHERE entity_id=?",id);
            jdbc.update("DELETE FROM moderation_versions WHERE kind='contents' AND parent_id=?",id);
            jdbc.update("DELETE FROM learning_content_history WHERE content_id=?",id);jdbc.update("DELETE FROM bookmarks WHERE content_id=?",id);
            jdbc.update("UPDATE learning_contents SET current_published_version_id=null WHERE content_id=?",id);
            jdbc.update("DELETE FROM learning_content_version_resources WHERE content_version_id IN (SELECT content_version_id FROM learning_content_versions WHERE content_id=?)",id);
            jdbc.update("DELETE FROM learning_content_versions WHERE content_id=?",id);jdbc.update("DELETE FROM learning_contents WHERE content_id=?",id);
        }
    }
    Item content(String token) throws Exception {
        var d=tree(call(post(C).contentType(APPLICATION_JSON).content("{\"title\":\"Review test\",\"body\":\"secret-body-marker\",\"categoryCode\":\"GRAMMAR\"}"),token).andExpect(status().isCreated()));
        UUID id=UUID.fromString(d.path("content").path("contentId").asString());contents.add(id);
        return new Item("contents",id,UUID.fromString(d.path("versions").get(0).path("contentVersionId").asString()));
    }
    Item question() throws Exception {var q=create(teacher,5,null);return new Item("questions",qid(q),qv(q,0));}
    JsonNode view(Item item,String token) throws Exception {return tree(call(get(item.path()),token).andExpect(status().isOk()));}
    ResultActions command(Item item,String action,String token,long revision) throws Exception {
        var payload=new LinkedHashMap<String,Object>();payload.put("expectedRevision",revision);
        if(action.equals("return")||action.equals("revoke"))payload.put("reason","Please revise");
        if(action.equals("publish")&&item.kind.equals("contents"))payload.put("visibility","PUBLIC");
        return call(post(item.path()+"/"+action).contentType(APPLICATION_JSON).content(json.writeValueAsString(payload)),token);
    }
    JsonNode step(Item item,String action,String token) throws Exception {return tree(command(item,action,token,view(item,token).path("revision").asLong()).andExpect(status().isOk()));}
    void publishItem(Item item) throws Exception {step(item,"submit",teacher);step(item,"approve",admin);step(item,"publish",admin);}
    @Test void contentReturnResubmitApprovePublishAndLearnerReadsExactPointer() throws Exception {
        var i=content(teacher);call(get("/api/v1/contents/"+i.parent),learner).andExpect(status().isNotFound());
        step(i,"submit",teacher);assertEquals("IN_REVIEW",view(i,admin).path("status").asString());step(i,"return",admin);
        call(patch(C+"/"+i.parent).contentType(APPLICATION_JSON).content("{\"title\":\"Revised\"}"),teacher).andExpect(status().isOk());
        step(i,"submit",teacher);step(i,"approve",admin);step(i,"publish",admin);
        call(get("/api/v1/contents/"+i.parent),learner).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Revised"));
        assertEquals(i.version,jdbc.queryForObject("SELECT current_published_version_id FROM learning_contents WHERE content_id=?",UUID.class,i.parent));
        var audit=tree(call(get("/api/v1/admin/audit-logs?action=CONTENT_RETURNED&entityId="+i.parent),admin).andExpect(status().isOk()));
        assertEquals(1,audit.path("totalElements").asInt());
        var detail=tree(call(get("/api/v1/admin/audit-logs/"+audit.path("items").get(0).path("auditId").asString()),admin).andExpect(status().isOk()));
        assertFalse(detail.toString().contains("secret-body-marker"));assertFalse(detail.toString().contains("Please revise"));
    }
    @Test void reviewAndApprovedLockEditRevokeRequiresNewApproval() throws Exception {
        var i=content(teacher);step(i,"submit",teacher);
        call(patch(C+"/"+i.parent).contentType(APPLICATION_JSON).content("{\"body\":\"blocked\"}"),teacher).andExpect(status().isConflict());
        step(i,"approve",admin);long approved=view(i,admin).path("revision").asLong();assertEquals(approved,view(i,admin).path("approvedRevision").asLong());
        call(patch(C+"/"+i.parent).contentType(APPLICATION_JSON).content("{\"body\":\"blocked\"}"),admin).andExpect(status().isConflict());
        step(i,"revoke",admin);command(i,"publish",admin,approved).andExpect(status().isConflict());
        call(patch(C+"/"+i.parent).contentType(APPLICATION_JSON).content("{\"body\":\"revision two\"}"),teacher).andExpect(status().isOk());
        publishItem(i);
    }
    @Test void selfReviewStaysForbiddenAfterReturnAndResubmission() throws Exception {
        var i=content(teacher);call(patch(C+"/"+i.parent).contentType(APPLICATION_JSON).content("{\"title\":\"Admin edit\"}"),admin).andExpect(status().isOk());
        step(i,"submit",teacher);command(i,"approve",admin,view(i,admin).path("revision").asLong()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SELF_REVIEW_FORBIDDEN"));
        step(i,"return",admin);step(i,"submit",teacher);
        command(i,"approve",admin,view(i,admin).path("revision").asLong()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SELF_REVIEW_FORBIDDEN"));
    }
    @Test void historicalContributorDoesNotBanNewRoundButNewCloneCreatorIsBanned() throws Exception {
        var i=content(teacher);call(patch(C+"/"+i.parent).contentType(APPLICATION_JSON).content("{\"title\":\"Historical Admin edit\"}"),admin).andExpect(status().isOk());
        // Explicit old publication fixture, not a workflow bypass in production.
        jdbc.update("UPDATE learning_content_versions SET status='PUBLISHED',published_at=now() WHERE content_version_id=?",i.version);
        jdbc.update("UPDATE learning_contents SET status='PUBLISHED',visibility='PUBLIC',current_published_version_id=? WHERE content_id=?",i.version,i.parent);
        var old=jdbc.queryForMap("SELECT * FROM learning_content_versions WHERE content_version_id=?",i.version);
        var d=tree(call(patch(C+"/"+i.parent).contentType(APPLICATION_JSON).content("{\"title\":\"Teacher next cycle\"}"),teacher).andExpect(status().isOk()));
        var next=new Item("contents",i.parent,UUID.fromString(d.path("content").path("workingDraftVersionId").asString()));publishItem(next);
        assertEquals(old,jdbc.queryForMap("SELECT * FROM learning_content_versions WHERE content_version_id=?",i.version));
        assertEquals(i.version.toString(),view(next,admin).path("sourceVersionId").asString());
        var clone=tree(call(patch(C+"/"+i.parent).contentType(APPLICATION_JSON).content("{\"title\":\"Admin clone\"}"),admin).andExpect(status().isOk()));
        var latest=new Item("contents",i.parent,UUID.fromString(clone.path("content").path("workingDraftVersionId").asString()));step(latest,"submit",teacher);
        command(latest,"approve",admin,view(latest,admin).path("revision").asLong()).andExpect(status().isConflict());
    }
    @Test void missingEvidenceFailsClosedEvenAfterLegacyDraftEdit() throws Exception {
        var i=content(teacher);jdbc.update("DELETE FROM moderation_versions WHERE version_id=?",i.version);
        call(patch(C+"/"+i.parent).contentType(APPLICATION_JSON).content("{\"title\":\"Legacy edit\"}"),teacher).andExpect(status().isOk());
        assertFalse(view(i,admin).path("evidenceComplete").asBoolean());command(i,"submit",teacher,0).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTRIBUTOR_EVIDENCE_REQUIRED"));
    }
    @Test void enforces401403OwnershipUnknownFieldsAndDuplicateRevision() throws Exception {
        var i=content(teacher);call(get(i.path()),null).andExpect(status().isUnauthorized());call(get(i.path()),learner).andExpect(status().isForbidden());
        call(get(i.path()),token(OTHER)).andExpect(status().isNotFound());command(i,"approve",teacher,0).andExpect(status().isForbidden());
        call(post(i.path()+"/submit").contentType(APPLICATION_JSON).content("{\"expectedRevision\":0,\"status\":\"PUBLISHED\"}"),teacher).andExpect(status().isBadRequest());
        call(post(i.path()+"/submit").contentType(APPLICATION_JSON).content("{\"expectedRevision\":0,\"expectedRevision\":0}"),teacher).andExpect(status().isBadRequest());
        call(get("/api/v1/moderation/contents?page=0&page=1"),admin).andExpect(status().isBadRequest());
        call(post(i.path()+"/submit").contentType(APPLICATION_JSON).content("{\"expectedRevision\":\"0\"}"),teacher).andExpect(status().isBadRequest());
        call(get("/api/v1/moderation/contents"),learner).andExpect(status().isForbidden());
    }
    @Test void reasonAndVisibilityAreMandatoryAndCannotPublishPrivate() throws Exception {
        var i=content(teacher);step(i,"submit",teacher);
        call(post(i.path()+"/return").contentType(APPLICATION_JSON).content("{\"expectedRevision\":1,\"reason\":\" \"}"),admin).andExpect(status().isBadRequest());
        step(i,"approve",admin);long rev=view(i,admin).path("revision").asLong();
        for(String body:List.of("{\"expectedRevision\":"+rev+"}","{\"expectedRevision\":"+rev+",\"visibility\":\"PRIVATE\"}"))call(post(i.path()+"/publish").contentType(APPLICATION_JSON).content(body),admin).andExpect(status().isBadRequest());
        assertEquals("APPROVED",view(i,admin).path("status").asString());
    }
    @Test void staleRevisionAfterEditAndAfterTransition() throws Exception {
        var i=content(teacher);call(patch(C+"/"+i.parent).contentType(APPLICATION_JSON).content("{\"title\":\"Edited\"}"),teacher).andExpect(status().isOk());
        command(i,"submit",teacher,0).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_REVISION"));
        var submitted=step(i,"submit",teacher);step(i,"approve",admin);
        command(i,"return",admin,submitted.path("revision").asLong()).andExpect(status().isConflict());
    }
    @Test void mediaWithoutPublisherGrantRejectsAtomicallyAndPublicDoesNotBypassReuse() throws Exception {
        var i=content(teacher);jdbc.update("INSERT INTO learning_content_version_resources(content_version_id,resource_id,display_order) VALUES (?,?,1)",i.version,media(1));
        step(i,"submit",teacher);step(i,"approve",admin);command(i,"publish",admin,view(i,admin).path("revision").asLong()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PUBLICATION_MEDIA_UNAVAILABLE"));
        assertEquals("APPROVED",view(i,admin).path("status").asString());assertNull(jdbc.queryForObject("SELECT current_published_version_id FROM learning_contents WHERE content_id=?",UUID.class,i.parent));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_id=? AND action='CONTENT_PUBLISHED'",Integer.class,i.parent));
    }
    @Test void publisherDoesNotNeedManageAndRevocationAppliesNextRequest() throws Exception {
        var i=content(teacher);step(i,"submit",teacher);step(i,"approve",admin);
        var links=jdbc.queryForList("SELECT role_id,permission_id FROM role_permissions JOIN roles USING(role_id) JOIN permissions USING(permission_id) WHERE name='ADMIN' AND code IN ('content:manage:self','content:manage:any','content:publish')");
        try {
            jdbc.update("DELETE FROM role_permissions WHERE (role_id,permission_id) IN (SELECT r.role_id,p.permission_id FROM roles r CROSS JOIN permissions p WHERE r.name='ADMIN' AND p.code IN ('content:manage:self','content:manage:any'))");
            call(get(C),admin).andExpect(status().isForbidden());step(i,"publish",admin);
            jdbc.update("DELETE FROM role_permissions WHERE (role_id,permission_id) IN (SELECT r.role_id,p.permission_id FROM roles r CROSS JOIN permissions p WHERE r.name='ADMIN' AND p.code='content:publish')");
            command(i,"publish",admin,view(i,admin).path("revision").asLong()).andExpect(status().isForbidden());
        } finally {for(var row:links)jdbc.update("INSERT INTO role_permissions(role_id,permission_id) VALUES (?,?) ON CONFLICT DO NOTHING",row.get("role_id"),row.get("permission_id"));}
    }
    @Test void auditFailureRollsBackApprovalAndDecision() throws Exception {
        var i=content(teacher);step(i,"submit",teacher);var before=view(i,admin);
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT moderation_audit_failure CHECK(action<>'CONTENT_APPROVED') NOT VALID");
        try {command(i,"approve",admin,before.path("revision").asLong()).andExpect(status().isServiceUnavailable());assertEquals(before,view(i,admin));}
        finally {jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT moderation_audit_failure");}
    }
    @Test void concurrentSubmitOnlyOneCommits() throws Exception {
        var i=content(teacher);var statuses=race(()->command(i,"submit",teacher,0).andReturn().getResponse().getStatus(),()->command(i,"submit",teacher,0).andReturn().getResponse().getStatus());
        assertEquals(List.of(200,409),statuses);assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_id=? AND action='CONTENT_SUBMITTED'",Integer.class,i.parent));
    }
    @Test void concurrentEditSubmitCannotApproveStaleContent() throws Exception {
        var i=content(teacher);var statuses=race(()->call(patch(C+"/"+i.parent).contentType(APPLICATION_JSON).content("{\"title\":\"Race edit\"}"),teacher).andReturn().getResponse().getStatus(),()->command(i,"submit",teacher,0).andReturn().getResponse().getStatus());
        assertEquals(List.of(200,409),statuses);var current=view(i,admin);assertEquals(1,current.path("revision").asLong());
    }
    @Test void concurrentPublishOnlyOneAuditAndArchiveNeverResurrects() throws Exception {
        var i=content(teacher);step(i,"submit",teacher);step(i,"approve",admin);long rev=view(i,admin).path("revision").asLong();
        assertEquals(List.of(200,409),race(()->command(i,"publish",admin,rev).andReturn().getResponse().getStatus(),()->command(i,"publish",admin,rev).andReturn().getResponse().getStatus()));
        call(delete(C+"/"+i.parent),teacher).andExpect(status().isNoContent());command(i,"publish",admin,view(i,admin).path("revision").asLong()).andExpect(status().isConflict());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_id=? AND action='CONTENT_PUBLISHED'",Integer.class,i.parent));
    }
    @Test void groupAndGroupedQuestionPublicationGuardPreservesPointers() throws Exception {
        var g=group(teacher);var group=new Item("groups",gid(g),gv(g,0));publish(true,group.parent,group.version);
        var q=create(teacher,6,group.version);var i=new Item("questions",qid(q),qv(q,0));step(i,"submit",teacher);step(i,"approve",admin);
        command(i,"publish",admin,view(i,admin).path("revision").asLong()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GROUP_PUBLICATION_NOT_AVAILABLE"));
        assertEquals(group.version,jdbc.queryForObject("SELECT current_published_version_id FROM question_groups WHERE group_id=?",UUID.class,group.parent));
        var fresh=group(teacher);var gi=new Item("groups",gid(fresh),gv(fresh,0));step(gi,"submit",teacher);step(gi,"approve",admin);
        command(gi,"publish",admin,view(gi,admin).path("revision").asLong()).andExpect(status().isConflict());
    }
    @Test void newQuestionPublicationPreservesAttemptPinAndSavedResult() throws Exception {
        var i=question();publishItem(i);var old=jdbc.queryForMap("SELECT * FROM question_versions WHERE question_version_id=?",i.version);
        var attempt=tree(call(post("/api/v1/attempts").contentType(APPLICATION_JSON).content("{\"sourceType\":\"PRACTICE_SNAPSHOT\",\"practice\":{\"partNumbers\":[5],\"questionCount\":1}}"),learner).andExpect(status().isCreated()));
        var id=UUID.fromString(attempt.path("attemptId").asString());attempts.add(id);String path="/api/v1/attempts/"+id;
        var result=tree(call(post(path+"/submit"),learner).andExpect(status().isOk()));
        var edited=patchQ(teacher,i.parent,"{\"questionText\":\"New prompt\"}");var next=new Item("questions",i.parent,qv(edited,1));publishItem(next);
        assertEquals(old,jdbc.queryForMap("SELECT * FROM question_versions WHERE question_version_id=?",i.version));
        assertEquals(result,tree(call(post(path+"/submit"),learner).andExpect(status().isOk())));
        call(get(path),learner).andExpect(status().isOk()).andExpect(jsonPath("$.questions[0].questionVersionId").value(i.version.toString()));
    }
    @Test void concurrentReviewOnlyOneApprovalAndEditRemainsLocked() throws Exception {
        var i=content(teacher);step(i,"submit",teacher);long rev=view(i,admin).path("revision").asLong();
        assertEquals(List.of(200,409),race(()->command(i,"approve",admin,rev).andReturn().getResponse().getStatus(),()->command(i,"approve",admin,rev).andReturn().getResponse().getStatus()));
        call(patch(C+"/"+i.parent).contentType(APPLICATION_JSON).content("{\"title\":\"stale edit\"}"),teacher).andExpect(status().isConflict());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_id=? AND action='CONTENT_APPROVED'",Integer.class,i.parent));
    }
    @Test void concurrentArchivePublishNeverResurrectsAndAuditMatchesCommittedState() throws Exception {
        var i=content(teacher);step(i,"submit",teacher);step(i,"approve",admin);long rev=view(i,admin).path("revision").asLong();
        var statuses=race(()->command(i,"publish",admin,rev).andReturn().getResponse().getStatus(),()->call(delete(C+"/"+i.parent),teacher).andReturn().getResponse().getStatus());
        assertTrue(statuses.equals(List.of(200,204))||statuses.equals(List.of(204,409)),statuses.toString());
        assertEquals("ARCHIVED",jdbc.queryForObject("SELECT status FROM learning_contents WHERE content_id=?",String.class,i.parent));
        assertEquals(statuses.contains(200)?1:0,jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_id=? AND action='CONTENT_PUBLISHED'",Integer.class,i.parent));
        call(get("/api/v1/contents/"+i.parent),learner).andExpect(status().isNotFound());
    }
    @Test void publicationAuditFailureRollsBackPointerVisibilityAndVersion() throws Exception {
        var i=content(teacher);step(i,"submit",teacher);step(i,"approve",admin);var before=view(i,admin);
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT moderation_publish_failure CHECK(action<>'CONTENT_PUBLISHED') NOT VALID");
        try {
            command(i,"publish",admin,before.path("revision").asLong()).andExpect(status().isServiceUnavailable());assertEquals(before,view(i,admin));
            assertEquals("PRIVATE",jdbc.queryForObject("SELECT visibility FROM learning_contents WHERE content_id=?",String.class,i.parent));
            assertNull(jdbc.queryForObject("SELECT current_published_version_id FROM learning_contents WHERE content_id=?",UUID.class,i.parent));
        }finally{jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT moderation_publish_failure");}
    }
    @Test void creatorCannotPublishEvenIfAnotherAdminApproved() throws Exception {
        jdbc.update("INSERT INTO user_roles SELECT ?,role_id FROM roles WHERE name='ADMIN' ON CONFLICT DO NOTHING",OTHER);
        var i=content(admin);step(i,"submit",admin);step(i,"approve",token(OTHER));
        command(i,"publish",admin,view(i,admin).path("revision").asLong()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SELF_REVIEW_FORBIDDEN"));
        assertEquals("APPROVED",view(i,admin).path("status").asString());
    }
    @Test void existingGrantAllowsPublishThenRevocationBlocksNextPublicationWithoutHidingOldContent() throws Exception {
        // Reuse the grant already declared in q03-authoring.sql; no grant bootstrap/API is added.
        jdbc.update("INSERT INTO user_roles SELECT ?,role_id FROM roles WHERE name='ADMIN' ON CONFLICT DO NOTHING",ACTOR);
        var other=token(OTHER);var first=content(other);
        String assets=json.writeValueAsString(Map.of("assets",List.of(Map.of("mediaId",media(5)))));
        call(patch(C+"/"+first.parent).contentType(APPLICATION_JSON).content(assets),other).andExpect(status().isOk());
        step(first,"submit",other);step(first,"approve",admin);
        long revision=view(first,teacher).path("revision").asLong();
        call(post(first.path()+"/publish").contentType(APPLICATION_JSON).content(json.writeValueAsString(Map.of("expectedRevision",revision,"visibility","AUTHENTICATED"))),teacher).andExpect(status().isOk());
        revokeGrant();call(get("/api/v1/contents/"+first.parent),learner).andExpect(status().isOk()).andExpect(jsonPath("$.media.length()").value(1));
        var next=content(other);call(patch(C+"/"+next.parent).contentType(APPLICATION_JSON).content(assets),other).andExpect(status().isOk());
        step(next,"submit",other);step(next,"approve",admin);long current=view(next,teacher).path("revision").asLong();
        call(post(next.path()+"/publish").contentType(APPLICATION_JSON).content(json.writeValueAsString(Map.of("expectedRevision",current,"visibility","AUTHENTICATED"))),teacher).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PUBLICATION_MEDIA_UNAVAILABLE"));
    }
    List<Integer> race(Callable<Integer> a,Callable<Integer> b) throws Exception {
        var start=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)){
            var one=pool.submit(()->{start.await();return a.call();});var two=pool.submit(()->{start.await();return b.call();});start.countDown();
            var result=new ArrayList<>(List.of(one.get(30,TimeUnit.SECONDS),two.get(30,TimeUnit.SECONDS)));Collections.sort(result);return result;
        }
    }
}
