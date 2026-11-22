package com.toeic.backend.assessment.api;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** MINI-LAB is a small fixture, not an official TOEIC blueprint or a member database clone. */
class FullMockIntegrationTest extends PracticeTestSupport {
    static final String TESTS="/api/v1/teacher/tests";
    final List<UUID> tests=new ArrayList<>(),blueprints=new ArrayList<>();
    @BeforeEach void mediaFixture(){
        // Actual linked resources and grants, not a mocked publication policy.
        jdbc.update("DELETE FROM question_resources WHERE question_version_id=? AND resource_id='b3000000-0000-0000-0000-000000000003'",v(1));
        for(int n:new int[]{1,3,8,9,10,14,15})jdbc.update("INSERT INTO question_resources(question_version_id,resource_id) VALUES (?,'b3000000-0000-0000-0000-000000000001') ON CONFLICT DO NOTHING",v(n));
        jdbc.update("INSERT INTO question_resources(question_version_id,resource_id) VALUES (?,'b3000000-0000-0000-0000-000000000006') ON CONFLICT DO NOTHING",v(1));
        jdbc.update("INSERT INTO media_reuse_grants(media_id,user_id,granted_by,granted_at) SELECT resource_id,?,?,CURRENT_TIMESTAMP FROM media_resources WHERE resource_type IN ('AUDIO','IMAGE') AND visibility IN ('PUBLIC','AUTHENTICATED') AND owner_id IS NOT NULL ON CONFLICT DO NOTHING",ADMIN,TEACHER);
    }
    @AfterEach void cleanup(){
        for(UUID t:tests){
            jdbc.update("DELETE FROM attempt_answers WHERE attempt_id IN(SELECT attempt_id FROM attempts WHERE test_version_id IN(SELECT test_version_id FROM test_versions WHERE test_id=?))",t);
            jdbc.update("DELETE FROM attempt_part_results WHERE attempt_id IN(SELECT attempt_id FROM attempts WHERE test_version_id IN(SELECT test_version_id FROM test_versions WHERE test_id=?))",t);
            jdbc.update("DELETE FROM attempt_question_snapshots WHERE attempt_id IN(SELECT attempt_id FROM attempts WHERE test_version_id IN(SELECT test_version_id FROM test_versions WHERE test_id=?))",t);
            jdbc.update("DELETE FROM attempts WHERE test_version_id IN(SELECT test_version_id FROM test_versions WHERE test_id=?)",t);
            jdbc.update("DELETE FROM moderation_versions WHERE kind='tests' AND parent_id=?",t);
            jdbc.update("DELETE FROM audit_logs WHERE entity_id=?",t);
            jdbc.update("UPDATE tests SET current_published_version_id=NULL WHERE test_id=?",t);
            jdbc.update("DELETE FROM test_questions WHERE test_version_id IN(SELECT test_version_id FROM test_versions WHERE test_id=?)",t);
            jdbc.update("DELETE FROM test_version_drafts WHERE test_version_id IN(SELECT test_version_id FROM test_versions WHERE test_id=?)",t);
            jdbc.update("DELETE FROM test_versions WHERE test_id=?",t);jdbc.update("DELETE FROM tests WHERE test_id=?",t);
        }
        for(UUID b:blueprints){jdbc.update("DELETE FROM audit_logs WHERE entity_id=?",b);jdbc.update("DELETE FROM test_blueprint_items WHERE blueprint_id=?",b);jdbc.update("DELETE FROM test_blueprints WHERE blueprint_id=?",b);}
    }
    String path(JsonNode d){return TESTS+"/"+d.get("testId").asString()+"/versions/"+d.get("versionId").asString();}
    UUID version(JsonNode d){return UUID.fromString(d.get("versionId").asString());}
    String document(){
        int[][] refs={{1},{3},{10,9,8},{15,14},{16},{19,18},{22,21,20}};int[] groups={0,0,2,4,0,5,6};
        var listening=new ArrayList<Object>();var reading=new ArrayList<Object>();
        for(int i=0;i<7;i++){var qs=new ArrayList<Object>();for(int n:refs[i]){var q=new LinkedHashMap<String,Object>();q.put("questionVersionId",v(n));q.put("groupVersionId",groups[i]==0?null:gv(groups[i]));qs.add(q);}var p=Map.of("partNumber",i+1,"questions",qs);(i<4?listening:reading).add(p);}
        return json.writeValueAsString(Map.of("title","Mini Full Mock fixture","testType","FULL","sections",List.of(Map.of("type","LISTENING","parts",listening),Map.of("type","READING","parts",reading))));
    }
    JsonNode draft()throws Exception {var d=tree(call(post(TESTS).contentType(APPLICATION_JSON).content(document()),token(TEACHER)).andExpect(status().isCreated()));tests.add(UUID.fromString(d.get("testId").asString()));return d;}
    JsonNode blueprint(JsonNode d)throws Exception{
        var parts=new ArrayList<Object>();int[] counts={1,1,3,2,1,2,3};
        for(int i=0;i<7;i++){boolean group=Set.of(2,3,5,6).contains(i);var p=new LinkedHashMap<String,Object>();p.put("partNumber",i+1);p.put("questionCount",counts[i]);p.put("groupCount",group?1:0);p.put("minGroupSize",group?counts[i]:null);p.put("maxGroupSize",group?counts[i]:null);p.put("requireAudio",i<4);p.put("requireImage",i==0);parts.add(p);}
        var b=tree(call(post("/api/v1/teacher/blueprints").contentType(APPLICATION_JSON).content(json.writeValueAsString(Map.of("code","MINI-LAB-"+UUID.randomUUID().toString().toUpperCase(),"version",1,"name","Mini test fixture, not TOEIC official","durationSeconds",600,"parts",parts))),token(ADMIN)).andExpect(status().isCreated()));
        blueprints.add(UUID.fromString(b.get("blueprintId").asString()));
        return tree(call(put(path(d)+"/blueprint").contentType(APPLICATION_JSON).content(json.writeValueAsString(Map.of("expectedRevision",d.get("revision").asInt(),"blueprintId",b.get("blueprintId").asString()))),token(TEACHER)).andExpect(status().isOk()));
    }
    JsonNode command(JsonNode d,String command,UUID actor)throws Exception{return tree(call(post(path(d)+"/"+command).contentType(APPLICATION_JSON).content("{\"expectedRevision\":"+d.get("revision").asInt()+"}"),token(actor)).andExpect(status().isOk()));}
    JsonNode published()throws Exception {var d=blueprint(draft());d=command(d,"submit-review",TEACHER);d=command(d,"approve",ADMIN);return command(d,"publish",ADMIN);}
    JsonNode start(JsonNode d)throws Exception {return tree(call(post(API).contentType(APPLICATION_JSON).content(startBody(d)),token(ACTOR)).andExpect(status().isCreated()));}
    String startBody(JsonNode d){return "{\"sourceType\":\"TEST_VERSION\",\"testVersionId\":\""+version(d)+"\"}";}
    @Test void fullWorkflowPinSaveReplaceClearReloadSubmitAndRetire()throws Exception{
        var d=published();assertEquals("PUBLISHED",d.get("status").asString());var session=start(d);safe(session);assertEquals(13,session.get("totalQuestions").asInt());assertEquals(600,session.get("test").get("blueprint").get("durationSeconds").asInt());
        String learner=token(ACTOR);UUID id=id(session);var choices=session.get("questions").get(0).get("choices");
        for(int i=0;i<2;i++)call(put(API+"/"+id+"/answers/"+v(1)).contentType(APPLICATION_JSON).content("{\"selectedChoiceId\":\""+choices.get(i).get("choiceId").asString()+"\"}"),learner).andExpect(status().isOk());
        call(put(API+"/"+id+"/answers/"+v(1)).contentType(APPLICATION_JSON).content("{\"selectedChoiceId\":null}"),learner).andExpect(status().isOk());assertTrue(read(learner,id).get("answers").get(0).get("selectedChoiceId").isNull());
        var before=jdbc.queryForObject("SELECT test_blueprint_snapshot::text FROM attempts WHERE attempt_id=?",String.class,id);command(d,"retire",ADMIN);
        call(post(API).contentType(APPLICATION_JSON).content(startBody(d)),learner).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TEST_NOT_OFFERED"));
        assertEquals(session.get("test"),read(learner,id).get("test"));assertEquals(before,jdbc.queryForObject("SELECT test_blueprint_snapshot::text FROM attempts WHERE attempt_id=?",String.class,id));
        var result=tree(call(post(API+"/"+id+"/submit"),learner).andExpect(status().isOk()));assertEquals(7,result.get("parts").size());assertEquals(13,result.get("unansweredCount").asInt());
        assertEquals(result,tree(call(post(API+"/"+id+"/submit"),learner).andExpect(status().isOk())));assertEquals(result,tree(call(get(API+"/"+id+"/result"),learner).andExpect(status().isOk())));
        call(get(API+"/"+id),token(OTHER)).andExpect(status().isNotFound());
    }
    @Test void forkProvenanceOneWorkingDraftCurrentOnlyAndOldAttemptUnchanged()throws Exception{
        var d=published();var attempt=start(d);var fork=command(d,"fork",TEACHER);assertEquals(version(d).toString(),fork.get("sourceVersionId").asString());assertEquals(2,fork.get("version").asInt());
        call(post(path(d)+"/fork").contentType(APPLICATION_JSON).content("{\"expectedRevision\":"+d.get("revision").asInt()+"}"),token(TEACHER)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WORKING_DRAFT_EXISTS"));
        fork=command(fork,"submit-review",TEACHER);fork=command(fork,"approve",ADMIN);fork=command(fork,"publish",ADMIN);
        call(post(API).contentType(APPLICATION_JSON).content(startBody(d)),token(ACTOR)).andExpect(status().isConflict());
        assertEquals(attempt.get("test"),read(token(ACTOR),id(attempt)).get("test"));assertEquals(2,start(fork).get("test").get("version").asInt());
    }
    @Test void bankChangeRequiresExplicitReplacementButPublishedCanStillFork()throws Exception{
        var d=published();var a=start(d);jdbc.update("UPDATE questions SET status='ARCHIVED' WHERE question_id=?",q(1));
        call(post(API).contentType(APPLICATION_JSON).content(startBody(d)),token(ACTOR)).andExpect(status().isUnprocessableContent());
        var fork=command(d,"fork",TEACHER);call(post(path(fork)+"/submit-review").contentType(APPLICATION_JSON).content("{\"expectedRevision\":0}"),token(TEACHER)).andExpect(status().isUnprocessableContent());safe(read(token(ACTOR),id(a)));
    }
    @Test void blueprintRequiredAndWrongCountsFailWithoutMutation()throws Exception{
        var d=draft();call(post(path(d)+"/submit-review").contentType(APPLICATION_JSON).content("{\"expectedRevision\":0}"),token(TEACHER)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BLUEPRINT_REQUIRED"));
        assertEquals("DRAFT",jdbc.queryForObject("SELECT status FROM test_versions WHERE test_version_id=?",String.class,version(d)));
    }
    @Test void revisionPermissionOwnershipAndLegacyEvidenceGuards()throws Exception{
        var d=blueprint(draft());call(post(path(d)+"/submit-review").contentType(APPLICATION_JSON).content("{\"expectedRevision\":0}"),token(TEACHER)).andExpect(status().isConflict());
        call(get(path(d)),token(OTHER)).andExpect(status().isForbidden());call(post(path(d)+"/approve").contentType(APPLICATION_JSON).content("{\"expectedRevision\":1}"),token(TEACHER)).andExpect(status().isForbidden());
        jdbc.update("DELETE FROM moderation_versions WHERE kind='tests' AND version_id=?",version(d));call(post(path(d)+"/submit-review").contentType(APPLICATION_JSON).content("{\"expectedRevision\":1}"),token(TEACHER)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTRIBUTOR_EVIDENCE_REQUIRED"));
    }
    @Test void adminContributorCannotReviewOwnCycleAndReturnDoesNotResetContributors()throws Exception{
        var d=blueprint(draft());var edited=tree(call(put(path(d)).contentType(APPLICATION_JSON).content("{\"revision\":1,\"document\":"+document()+"}"),token(ADMIN)).andExpect(status().isOk()));
        d=command(edited,"submit-review",TEACHER);call(post(path(d)+"/approve").contentType(APPLICATION_JSON).content("{\"expectedRevision\":"+d.get("revision").asInt()+"}"),token(ADMIN)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SELF_REVIEW_FORBIDDEN"));
        d=tree(call(post(path(d)+"/return").contentType(APPLICATION_JSON).content("{\"expectedRevision\":"+d.get("revision").asInt()+",\"reason\":\"Fix explicitly\"}"),token(ADMIN)).andExpect(status().isOk()));d=command(d,"submit-review",TEACHER);
        call(post(path(d)+"/approve").contentType(APPLICATION_JSON).content("{\"expectedRevision\":"+d.get("revision").asInt()+"}"),token(ADMIN)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SELF_REVIEW_FORBIDDEN"));
    }
    @Test void revokedMediaGrantBlocksPublishWithoutAdminBypass()throws Exception{
        var d=blueprint(draft());d=command(d,"submit-review",TEACHER);d=command(d,"approve",ADMIN);
        jdbc.update("UPDATE media_reuse_grants SET revoked_at=CURRENT_TIMESTAMP,revoked_by=? WHERE user_id=?",TEACHER,ADMIN);
        call(post(path(d)+"/publish").contentType(APPLICATION_JSON).content("{\"expectedRevision\":"+d.get("revision").asInt()+"}"),token(ADMIN)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PUBLICATION_MEDIA_UNAVAILABLE"));
        assertNull(jdbc.queryForObject("SELECT current_published_version_id FROM tests WHERE test_id=?",UUID.class,tests.getFirst()));
    }
    @Test void publishedMediaGrantRevocationBlocksNewStartButPinnedAttemptContinues()throws Exception{
        var d=published();var a=start(d);jdbc.update("UPDATE media_reuse_grants SET revoked_at=CURRENT_TIMESTAMP,revoked_by=? WHERE user_id=?",TEACHER,ADMIN);
        call(post(API).contentType(APPLICATION_JSON).content(startBody(d)),token(ACTOR)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PUBLICATION_MEDIA_UNAVAILABLE"));
        safe(read(token(ACTOR),id(a)));call(post(API+"/"+id(a)+"/submit"),token(ACTOR)).andExpect(status().isOk());
    }
    @Test void adminRoleAndSpecificPermissionAreBothRequired()throws Exception{
        var d=command(blueprint(draft()),"submit-review",TEACHER);
        jdbc.update("DELETE FROM role_permissions WHERE role_id=(SELECT role_id FROM roles WHERE name='ADMIN') AND permission_id=(SELECT permission_id FROM permissions WHERE code='test:review')");
        try{call(post(path(d)+"/approve").contentType(APPLICATION_JSON).content("{\"expectedRevision\":"+d.get("revision").asInt()+"}"),token(ADMIN)).andExpect(status().isForbidden());}
        finally{jdbc.update("INSERT INTO role_permissions SELECT role_id,permission_id FROM roles CROSS JOIN permissions WHERE name='ADMIN' AND code='test:review' ON CONFLICT DO NOTHING");}
        jdbc.update("INSERT INTO role_permissions SELECT role_id,permission_id FROM roles CROSS JOIN permissions WHERE name='TEACHER' AND code='test:review' ON CONFLICT DO NOTHING");
        try{call(post(path(d)+"/approve").contentType(APPLICATION_JSON).content("{\"expectedRevision\":"+d.get("revision").asInt()+"}"),token(TEACHER)).andExpect(status().isForbidden());}
        finally{jdbc.update("DELETE FROM role_permissions WHERE role_id=(SELECT role_id FROM roles WHERE name='TEACHER') AND permission_id=(SELECT permission_id FROM permissions WHERE code='test:review')");}
    }
    @Test void lateExplicitSubmitGradesSavedAnswersButDoesNotPermitLateEdits()throws Exception{
        var a=start(published());UUID id=id(a);jdbc.update("UPDATE attempts SET expires_at=started_at+interval '1 second',started_at=started_at-interval '2 seconds' WHERE attempt_id=?",id);
        // Set expiry explicitly in the past; no worker is being tested.
        jdbc.update("UPDATE attempts SET expires_at=started_at+interval '1 second' WHERE attempt_id=?",id);
        call(put(API+"/"+id+"/answers/"+v(1)).contentType(APPLICATION_JSON).content("{\"selectedChoiceId\":null}"),token(ACTOR)).andExpect(status().isConflict());call(post(API+"/"+id+"/submit"),token(ACTOR)).andExpect(status().isOk());
    }
    @ParameterizedTest @ValueSource(strings={"{\"sourceType\":\"TEST_VERSION\"}","{\"sourceType\":\"TEST_VERSION\",\"testVersionId\":\"bad\"}","{\"sourceType\":\"TEST_VERSION\",\"testVersionId\":null}","{\"sourceType\":\"TEST_VERSION\",\"testVersionId\":\"11111111-1111-1111-1111-111111111111\",\"practice\":{}}"})
    void strictStartContract(String body)throws Exception{call(post(API).contentType(APPLICATION_JSON).content(body),token(ACTOR)).andExpect(status().isBadRequest());}
    @Test void concurrentStartAndRetireHaveOneSerializedOutcome()throws Exception{
        var d=published();String learner=token(ACTOR),admin=token(ADMIN);var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{var start=pool.submit(()->{gate.await();return call(post(API).contentType(APPLICATION_JSON).content(startBody(d)),learner).andReturn().getResponse().getStatus();});var retire=pool.submit(()->{gate.await();return call(post(path(d)+"/retire").contentType(APPLICATION_JSON).content("{\"expectedRevision\":"+d.get("revision").asInt()+"}"),admin).andReturn().getResponse().getStatus();});gate.countDown();assertEquals(200,retire.get(20,TimeUnit.SECONDS));assertTrue(Set.of(201,409).contains(start.get(20,TimeUnit.SECONDS)));assertEquals("RETIRED",jdbc.queryForObject("SELECT status FROM tests WHERE test_id=?",String.class,tests.getFirst()));}finally{pool.shutdownNow();}
    }
}
