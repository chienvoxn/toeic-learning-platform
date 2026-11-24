package com.toeic.backend.questionbank.api;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.JsonNode;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** API/transaction thật trên DB test riêng; fixture media không được gọi là playback E2E. */
class MediaReuseGrantIntegrationTest extends QuestionAuthoringTestSupport {
    String owner,admin,other,learner;
    String api(UUID media,UUID user){return "/api/v1/teacher/media/"+media+"/reuse-grants/"+user;}
    @BeforeEach void sessions() throws Exception {owner=token(ACTOR);admin=token(ADMIN);other=token(OTHER);learner=token(LEARNER);}
    @AfterEach void cleanupOwnAuditAndAttempts(){
        jdbc.update("DELETE FROM attempt_part_results WHERE attempt_id IN(SELECT attempt_id FROM attempts WHERE user_id=?)",LEARNER);
        jdbc.update("DELETE FROM attempt_answers WHERE attempt_id IN(SELECT attempt_id FROM attempts WHERE user_id=?)",LEARNER);
        jdbc.update("DELETE FROM attempt_question_snapshots WHERE attempt_id IN(SELECT attempt_id FROM attempts WHERE user_id=?)",LEARNER);
        jdbc.update("DELETE FROM attempts WHERE user_id=?",LEARNER);
        jdbc.update("DELETE FROM audit_logs WHERE actor_user_id=? AND action IN ('MEDIA_REUSE_GRANTED','MEDIA_REUSE_REVOKED')",ACTOR);
    }
    JsonNode change(UUID media,UUID user,boolean active,String token) throws Exception {return tree(call(active?put(api(media,user)):delete(api(media,user)),token).andExpect(status().isOk()));}
    @Test void grantRevokeRegrantAndRepeatRequestsKeepOneRowAndAuditOnlyStateTransitions() throws Exception {
        var first=change(media(1),ADMIN,true,owner);assertTrue(first.get("changed").asBoolean());assertTrue(first.get("active").asBoolean());
        var repeat=change(media(1),ADMIN,true,owner);assertFalse(repeat.get("changed").asBoolean());assertEquals(first.get("grantedAt"),repeat.get("grantedAt"));
        var revoked=change(media(1),ADMIN,false,owner);assertFalse(revoked.get("active").asBoolean());assertNotNull(revoked.get("revokedAt"));
        var repeatRevoke=change(media(1),ADMIN,false,owner);assertFalse(repeatRevoke.get("changed").asBoolean());assertEquals(revoked.get("revokedAt"),repeatRevoke.get("revokedAt"));
        assertTrue(change(media(1),ADMIN,true,owner).get("active").asBoolean());
        assertEquals(1,count("SELECT count(*) FROM media_reuse_grants WHERE media_id=? AND user_id=?",media(1),ADMIN));
        assertEquals(3,count("SELECT count(*) FROM audit_logs WHERE entity_id=? AND action IN ('MEDIA_REUSE_GRANTED','MEDIA_REUSE_REVOKED')",media(1)));
        assertFalse(change(media(6),OTHER,false,owner).get("changed").asBoolean());
        assertEquals(ACTOR,jdbc.queryForObject("SELECT owner_id FROM media_resources WHERE resource_id=?",UUID.class,media(1)));
        assertEquals("PUBLIC",jdbc.queryForObject("SELECT visibility FROM media_resources WHERE resource_id=?",String.class,media(1)));
    }
    @Test void onlyOwnerWithPermissionCanListOrChangeAndRecipientCannotSelfGrant() throws Exception {
        call(put(api(media(1),OTHER)),null).andExpect(status().isUnauthorized());
        call(put(api(media(1),OTHER)),learner).andExpect(status().isForbidden());
        for(var who:List.of(admin,other))call(put(api(media(1),LEARNER)),who).andExpect(status().isNotFound());
        // ACTOR has a pre-existing active reuse grant to media5, but is not its owner.
        call(put(api(media(5),ADMIN)),owner).andExpect(status().isNotFound());
        call(get("/api/v1/teacher/media/"+media(1)+"/reuse-grants"),admin).andExpect(status().isNotFound());
        var owned=tree(call(get("/api/v1/teacher/media?size=100"),owner).andExpect(status().isOk()));
        assertTrue(owned.toString().contains(media(1).toString()));assertFalse(owned.toString().contains(media(2).toString()));
        assertFalse(owned.toString().contains("storageUrl")||owned.toString().contains("X-Amz")||owned.toString().contains("Q03_LEAK"));
        call(put(api(media(1),ACTOR)),owner).andExpect(status().isBadRequest());
        call(put(api(media(1),UUID.randomUUID())),owner).andExpect(status().isNotFound());
    }
    @Test void strictInputsCannotChangeOwnerVisibilityOrGrantOtherPermissions() throws Exception {
        call(put(api(media(1),ADMIN)).contentType(APPLICATION_JSON).content("{\"ownerId\":\""+ADMIN+"\"}"),owner).andExpect(status().isBadRequest());
        call(put(api(media(1),ADMIN)).queryParam("active","true"),owner).andExpect(status().isBadRequest());
        for(var query:List.of("size=101","page=-1","ownerId="+OTHER,"size=10&size=20"))call(get("/api/v1/teacher/media?"+query),owner).andExpect(status().isBadRequest());
        int roles=count("SELECT count(*) FROM user_roles WHERE user_id=?",OTHER);change(media(1),OTHER,true,owner);
        assertEquals(roles,count("SELECT count(*) FROM user_roles WHERE user_id=?",OTHER));
        // Recipient may use the asset but cannot grant it onward even with Teacher permission.
        call(delete(api(media(1),ADMIN)),other).andExpect(status().isNotFound());
    }
    @Test void auditFailureRollsBackGrant() throws Exception {
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT media_reuse_audit_test CHECK(action<>'MEDIA_REUSE_GRANTED') NOT VALID");
        try {call(put(api(media(1),ADMIN)),owner).andExpect(status().isServiceUnavailable());assertEquals(0,count("SELECT count(*) FROM media_reuse_grants WHERE media_id=? AND user_id=?",media(1),ADMIN));}
        finally {jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT media_reuse_audit_test");}
    }
    @Test void concurrentRepeatGrantsAreSerializedAndDoNotDuplicateAudit() throws Exception {
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            Callable<Integer> task=()->{start.await();return call(put(api(media(1),ADMIN)),owner).andReturn().getResponse().getStatus();};
            var a=pool.submit(task);var b=pool.submit(task);start.countDown();assertEquals(200,a.get(20,TimeUnit.SECONDS));assertEquals(200,b.get(20,TimeUnit.SECONDS));
        }
        assertEquals(1,count("SELECT count(*) FROM audit_logs WHERE entity_id=? AND action='MEDIA_REUSE_GRANTED'",media(1)));
    }
    String approveMediaQuestion() throws Exception {
        var body=payload(2,null);body.put("assets",List.of(Map.of("mediaId",media(1),"transcript","Original test transcript")));var q=create(owner,body);
        String path="/api/v1/moderation/questions/"+qid(q)+"/versions/"+qv(q,0);
        for(var action:List.of("submit","approve")){
            long revision=tree(call(get(path),admin).andExpect(status().isOk())).get("revision").asLong();
            call(post(path+"/"+action).contentType(APPLICATION_JSON).content("{\"expectedRevision\":"+revision+"}"),action.equals("submit")?owner:admin).andExpect(status().isOk());
        }return path;
    }
    @Test void publishRechecksCurrentGrantAndRevokePreservesPublishedPinsAndStoredResult() throws Exception {
        String path=approveMediaQuestion();long revision=tree(call(get(path),admin).andExpect(status().isOk())).get("revision").asLong();
        change(media(1),ADMIN,true,owner);change(media(1),ADMIN,false,owner);
        call(post(path+"/publish").contentType(APPLICATION_JSON).content("{\"expectedRevision\":"+revision+"}"),admin).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PUBLICATION_MEDIA_UNAVAILABLE"));
        change(media(1),ADMIN,true,owner);call(post(path+"/publish").contentType(APPLICATION_JSON).content("{\"expectedRevision\":"+revision+"}"),admin).andExpect(status().isOk());
        var attempt=tree(call(post("/api/v1/attempts").contentType(APPLICATION_JSON).content("{\"sourceType\":\"PRACTICE_SNAPSHOT\",\"practice\":{\"partNumbers\":[2],\"questionCount\":1}}"),learner).andExpect(status().isCreated()));
        String attemptPath="/api/v1/attempts/"+attempt.get("attemptId").asString();var result=tree(call(post(attemptPath+"/submit"),learner).andExpect(status().isOk()));
        change(media(1),ADMIN,false,owner);
        assertEquals(result,tree(call(get(attemptPath+"/result"),learner).andExpect(status().isOk())));assertEquals(result,tree(call(post(attemptPath+"/submit"),learner).andExpect(status().isOk())));
        assertEquals(attempt.get("questions"),tree(call(get(attemptPath),learner).andExpect(status().isOk())).get("questions"));
        call(get(attemptPath+"/media"),learner).andExpect(status().isOk()).andExpect(jsonPath("$.questions[0].assets[0].transcript").value("Original test transcript"));
        call(get(path),admin).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
    }
    @Test void publicationAndRevokeRaceIsSerializedWithoutPartialPublication() throws Exception {
        String path=approveMediaQuestion();long revision=tree(call(get(path),admin).andExpect(status().isOk())).get("revision").asLong();change(media(1),ADMIN,true,owner);
        var start=new CountDownLatch(1);int published;
        try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->{start.await();return call(post(path+"/publish").contentType(APPLICATION_JSON).content("{\"expectedRevision\":"+revision+"}"),admin).andReturn().getResponse().getStatus();});
            var b=pool.submit(()->{start.await();return call(delete(api(media(1),ADMIN)),owner).andReturn().getResponse().getStatus();});start.countDown();published=a.get(20,TimeUnit.SECONDS);assertEquals(200,b.get(20,TimeUnit.SECONDS));
        }
        assertTrue(Set.of(200,409).contains(published));assertEquals(published==200?"PUBLISHED":"APPROVED",tree(call(get(path),admin).andExpect(status().isOk())).get("status").asString());
        assertEquals(1,count("SELECT count(*) FROM media_reuse_grants WHERE media_id=? AND user_id=? AND revoked_at IS NOT NULL",media(1),ADMIN));
    }
}
