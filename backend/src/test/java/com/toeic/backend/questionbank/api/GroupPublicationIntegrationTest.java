package com.toeic.backend.questionbank.api;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.JsonNode;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP + real PostgreSQL fixtures. No direct SQL publication or contributor bypass. */
class GroupPublicationIntegrationTest extends QuestionAuthoringTestSupport {
    String teacher,admin;
    record Item(String kind,UUID parent,UUID version) {
        String path(){return "/api/v1/moderation/"+kind+"/"+parent+"/versions/"+version;}
    }
    record Batch(Item group,List<Item> members) { }
    @BeforeEach void sessions() throws Exception {teacher=token(ACTOR);admin=token(ADMIN);}
    JsonNode detail(Item item) throws Exception {return tree(call(get(item.path()),admin).andExpect(status().isOk()));}
    void approve(Item item) throws Exception {
        call(post(item.path()+"/submit").contentType(APPLICATION_JSON).content(json.writeValueAsString(Map.of("expectedRevision",detail(item).get("revision").asLong()))),teacher).andExpect(status().isOk());
        call(post(item.path()+"/approve").contentType(APPLICATION_JSON).content(json.writeValueAsString(Map.of("expectedRevision",detail(item).get("revision").asLong()))),admin).andExpect(status().isOk());
    }
    Batch batch() throws Exception {
        var g=group(teacher);var group=new Item("groups",gid(g),gv(g,0));var members=new ArrayList<Item>();
        for(int order=1;order<=2;order++) {
            var body=payload(7,group.version());body.put("groupQuestionOrder",order);
            var q=create(teacher,body);assertEquals(order,q.get("versions").get(0).get("groupQuestionOrder").asInt());
            members.add(new Item("questions",qid(q),qv(q,0)));
        }
        approve(group);for(var q:members)approve(q);
        return new Batch(group,members);
    }
    Map<String,Object> payload(Batch batch) throws Exception {
        var result=new LinkedHashMap<String,Object>();result.put("expectedRevision",detail(batch.group()).get("revision").asLong());
        result.put("expectedPublishedVersionId",jdbc.queryForObject("SELECT current_published_version_id FROM question_groups WHERE group_id=?",UUID.class,batch.group().parent()));
        var members=new ArrayList<Map<String,Object>>();int order=0;
        for(var q:batch.members()) {
            var m=new LinkedHashMap<String,Object>();m.put("questionId",q.parent());m.put("questionVersionId",q.version());m.put("order",++order);
            m.put("expectedRevision",detail(q).get("revision").asLong());
            m.put("expectedPublishedVersionId",jdbc.queryForObject("SELECT current_published_version_id FROM questions WHERE question_id=?",UUID.class,q.parent()));members.add(m);
        }
        result.put("members",members);return result;
    }
    org.springframework.test.web.servlet.ResultActions publish(Batch batch,Map<String,Object> body,String token) throws Exception {
        return call(post(batch.group().path()+"/publish-batch").contentType(APPLICATION_JSON).content(json.writeValueAsString(body)),token);
    }
    @Test void approvedBatchPublishesAllPointersAndAuditsAtomicallyAndReplayIsStale() throws Exception {
        var b=batch();var body=payload(b);publish(b,body,admin).andExpect(status().isOk());
        assertEquals("PUBLISHED",detail(b.group()).get("status").asString());
        for(var q:b.members()) {
            assertEquals(q.version(),jdbc.queryForObject("SELECT current_published_version_id FROM questions WHERE question_id=?",UUID.class,q.parent()));
            assertEquals("PUBLISHED",detail(q).get("status").asString());
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_id=? AND action='QUESTION_PUBLISHED'",Integer.class,q.parent()));
        }
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_id=? AND action='QUESTION_GROUP_PUBLISHED'",Integer.class,b.group().parent()));
        publish(b,body,admin).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_REVISION"));
        // Published order cannot be edited in place: authoring creates a new DRAFT.
        var changed=patchQ(teacher,b.members().getFirst().parent(),"{\"groupQuestionOrder\":2}");
        assertEquals(2,changed.get("versions").size());
        assertEquals(1,jdbc.queryForObject("SELECT group_question_order FROM question_versions WHERE question_version_id=?",Integer.class,b.members().getFirst().version()));
    }
    @Test void badPointerOrderAndUnapprovedMemberLeaveEverythingUnpublished() throws Exception {
        var b=batch();var body=payload(b);body.put("expectedPublishedVersionId",UUID.randomUUID());
        publish(b,body,admin).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_REVISION"));
        body=payload(b);Collections.reverse(b.members());
        publish(b,payload(b),admin).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GROUP_MEMBER_SNAPSHOT_MISMATCH"));
        Collections.reverse(b.members());
        var member=b.members().get(1);
        call(post(member.path()+"/revoke").contentType(APPLICATION_JSON).content(json.writeValueAsString(Map.of("expectedRevision",detail(member).get("revision").asLong(),"reason","Revise before publication"))),admin).andExpect(status().isOk());
        publish(b,payload(b),admin).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MODERATION_STATE_CONFLICT"));
        assertNull(jdbc.queryForObject("SELECT current_published_version_id FROM question_groups WHERE group_id=?",UUID.class,b.group().parent()));
        assertEquals("APPROVED",detail(b.members().getFirst()).get("status").asString());
    }
    @Test void publisherRequiresActiveAdminPermissionAndCompleteContributorEvidence() throws Exception {
        var b=batch();var body=payload(b);
        publish(b,body,teacher).andExpect(status().isForbidden());publish(b,body,null).andExpect(status().isUnauthorized());
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,role_id FROM roles WHERE name='ADMIN' ON CONFLICT DO NOTHING",ACTOR);
        publish(b,body,token(ACTOR)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SELF_REVIEW_FORBIDDEN"));
        jdbc.update("DELETE FROM user_roles WHERE user_id=? AND role_id IN(SELECT role_id FROM roles WHERE name='ADMIN')",ACTOR);
        jdbc.update("UPDATE moderation_versions SET evidence_complete=false WHERE version_id=?",b.members().get(1).version());
        publish(b,body,admin).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTRIBUTOR_EVIDENCE_REQUIRED"));
        assertEquals("APPROVED",detail(b.group()).get("status").asString());
    }
    @Test void competingBatchesHaveOneWinnerAndNoDuplicateDecisions() throws Exception {
        var b=batch();var body=payload(b);var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<Integer> task=()->{start.await();return publish(b,body,admin).andReturn().getResponse().getStatus();};
            var a=pool.submit(task);var c=pool.submit(task);start.countDown();var statuses=new ArrayList<>(List.of(a.get(15,TimeUnit.SECONDS),c.get(15,TimeUnit.SECONDS)));
            Collections.sort(statuses);assertEquals(List.of(200,409),statuses);
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM moderation_decisions WHERE kind='groups' AND version_id=? AND action='publish'",Integer.class,b.group().version()));
        } finally {pool.shutdownNow();}
    }
    @Test void mediaDeniedRollsBackWholeBatchWithoutGrantBypass() throws Exception {
        var g=group(teacher);var group=new Item("groups",gid(g),gv(g,0));
        var body=payload(7,group.version());body.put("groupQuestionOrder",1);body.put("assets",List.of(Map.of("mediaId",media(1))));
        var q=create(teacher,body);var member=new Item("questions",qid(q),qv(q,0));approve(group);approve(member);
        var b=new Batch(group,new ArrayList<>(List.of(member)));
        publish(b,payload(b),admin).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PUBLICATION_MEDIA_UNAVAILABLE"));
        assertEquals("APPROVED",detail(group).get("status").asString());assertEquals("APPROVED",detail(member).get("status").asString());
    }
    @Test void replacementRequiresAllCurrentMembersAndKeepsExistingAttemptPins() throws Exception {
        var original=batch();publish(original,payload(original),admin).andExpect(status().isOk());
        var learner=token(LEARNER);
        var attempt=tree(call(post("/api/v1/attempts").contentType(APPLICATION_JSON).content("""
                {"sourceType":"PRACTICE_SNAPSHOT","practice":{"partNumbers":[7],"questionCount":2}}
                """),learner).andExpect(status().isCreated()));
        var g=patchG(teacher,original.group().parent(),"{\"passageText\":\"New passage version\"}");
        var next=new Item("groups",gid(g),gv(g,1));var members=new ArrayList<Item>();
        for(var q:original.members()) {
            var changed=patchQ(teacher,q.parent(),json.writeValueAsString(Map.of("groupVersionId",next.version())));
            members.add(new Item("questions",q.parent(),qv(changed,1)));
        }
        approve(next);for(var q:members)approve(q);
        var missing=new Batch(next,new ArrayList<>(List.of(members.getFirst())));
        publish(missing,payload(missing),admin).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GROUP_MEMBERS_REQUIRED"));
        var complete=new Batch(next,members);publish(complete,payload(complete),admin).andExpect(status().isOk());
        var read=tree(call(get("/api/v1/attempts/"+attempt.get("attemptId").asString()),learner).andExpect(status().isOk()));
        assertEquals(attempt.get("questions"),read.get("questions"));
        assertEquals(original.group().version().toString(),read.get("questions").get(0).get("passage").get("groupVersionId").asString());
        var result=tree(call(post("/api/v1/attempts/"+attempt.get("attemptId").asString()+"/submit"),learner).andExpect(status().isOk()));
        assertEquals(2,result.get("totalQuestions").asInt());
        var id=UUID.fromString(attempt.get("attemptId").asString());
        jdbc.update("DELETE FROM attempt_part_results WHERE attempt_id=?",id);jdbc.update("DELETE FROM attempt_answers WHERE attempt_id=?",id);
        jdbc.update("DELETE FROM attempt_question_snapshots WHERE attempt_id=?",id);jdbc.update("DELETE FROM attempts WHERE attempt_id=?",id);
    }
    @Test void auditFailureRollsBackGroupMembersAndDecisions() throws Exception {
        var b=batch();var body=payload(b);
        jdbc.execute("CREATE FUNCTION tv2_g1_audit_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action='QUESTION_PUBLISHED' THEN RAISE EXCEPTION 'fixture audit unavailable'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER tv2_g1_audit_failure BEFORE INSERT ON audit_logs FOR EACH ROW EXECUTE FUNCTION tv2_g1_audit_failure()");
        try {
            publish(b,body,admin).andExpect(status().isServiceUnavailable());
            assertEquals("APPROVED",detail(b.group()).get("status").asString());
            for(var member:b.members())assertEquals("APPROVED",detail(member).get("status").asString());
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM moderation_decisions WHERE kind='groups' AND version_id=? AND action='publish'",Integer.class,b.group().version()));
        } finally {
            jdbc.execute("DROP TRIGGER tv2_g1_audit_failure ON audit_logs");jdbc.execute("DROP FUNCTION tv2_g1_audit_failure()");
        }
    }
}
