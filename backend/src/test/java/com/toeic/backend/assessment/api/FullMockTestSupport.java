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

/** Shared synthetic publication fixture; never evidence of G1 or a member database clone. */
abstract class FullMockTestSupport extends PracticeTestSupport {
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
}
