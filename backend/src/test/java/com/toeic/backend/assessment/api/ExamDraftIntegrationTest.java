package com.toeic.backend.assessment.api;

import com.toeic.backend.assessment.application.*;
import com.toeic.backend.identity.infrastructure.RbacRepository;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ExamDraftIntegrationTest extends PracticeTestSupport {
    static final String TESTS="/api/v1/teacher/tests";
    @Autowired ExamDraftService exams;
    @MockitoSpyBean RbacRepository audit;
    final List<UUID> created=new ArrayList<>();
    String doc(String title,int... numbers)throws Exception {
        var parts=new TreeMap<Integer,List<Object>>();
        for(int number:numbers) {
            int part=number<=2?1:number==3?2:number<=13?3:number<=15?4:number<=17?5:number<=19?6:7;
            var ref=new LinkedHashMap<String,Object>();ref.put("questionVersionId",v(number));
            int group=number<=3||number==16||number==17?0:number<=7?1:number<=10?2:number<=13?3:number<=15?4:number<=19?5:6;
            if(group>0)ref.put("groupVersionId",gv(group));
            parts.computeIfAbsent(part,k->new ArrayList<>()).add(ref);
        }
        var sections=new ArrayList<Object>();
        for(var skill:List.of("LISTENING","READING")) {
            var ps=parts.entrySet().stream().filter(p->(p.getKey()<=4)==skill.equals("LISTENING"))
                    .map(p->Map.of("partNumber",p.getKey(),"questions",p.getValue())).toList();
            if(!ps.isEmpty())sections.add(Map.of("type",skill,"parts",ps));
        }
        return json.writeValueAsString(Map.of("title",title,"testType","FULL","sections",sections));
    }
    JsonNode draft(String body)throws Exception {
        var d=tree(call(post(TESTS).contentType(APPLICATION_JSON).content(body),token(TEACHER)).andExpect(status().isCreated()));
        created.add(UUID.fromString(d.get("testId").asString()));return d;
    }
    String path(JsonNode d) {return TESTS+"/"+d.get("testId").asString()+"/versions/"+d.get("versionId").asString();}
    String edit(int revision,String document){return "{\"revision\":"+revision+",\"document\":"+document+"}";}
    @AfterEach void cleanupDrafts() {
        reset(audit);
        for(var test:created) {
            jdbc.update("delete from moderation_versions where kind='tests' and parent_id=?",test);
            jdbc.update("delete from audit_logs where entity_type='TEST' and entity_id=?",test);
            jdbc.update("update attempts set test_version_id=null where test_version_id in(select test_version_id from test_versions where test_id=?)",test);
            jdbc.update("delete from test_questions where test_version_id in(select test_version_id from test_versions where test_id=?)",test);
            jdbc.update("delete from test_version_drafts where test_version_id in(select test_version_id from test_versions where test_id=?)",test);
            jdbc.update("delete from test_versions where test_id=?",test);jdbc.update("delete from tests where test_id=?",test);
        }
    }
    @Test void createEditReadVersionReferencesAndAuditWithoutPublishing()throws Exception {
        var d=draft(doc("Draft",1,3,10,9,8,17,16,19,18,22,21,20));safe(d);
        assertEquals(0,d.get("revision").asInt());assertEquals("DRAFT",d.get("status").asString());
        var updated=tree(call(put(path(d)).contentType(APPLICATION_JSON).content(edit(0,doc("Revised",2,17))),token(TEACHER)).andExpect(status().isOk()));
        assertEquals(1,updated.get("revision").asInt());assertEquals("Revised",updated.get("document").get("title").asString());safe(updated);
        assertEquals(updated,tree(call(get(path(d)),token(TEACHER)).andExpect(status().isOk())));
        assertEquals(2,count("select count(*) from test_questions where test_version_id=?",UUID.fromString(d.get("versionId").asString())));
        assertEquals(2,count("select count(*) from audit_logs where entity_id=? and action in('TEST_CREATED','TEST_UPDATED')",created.getFirst()));
        call(post(TESTS+"/"+created.getFirst()+"/publish"),token(ADMIN)).andExpect(status().isNotFound());
    }
    @Test void emptyDraftAndUnspecifiedTimeAreAllowed()throws Exception {var d=draft(doc("Incomplete"));assertTrue(d.get("document").get("sections").isEmpty());}
    @Test void catalogUsesPublishedUnitsAndServerBusinessOrderWithoutMediaOrAnswers()throws Exception {
        var units=tree(call(get(TESTS+"/question-units").param("partNumber","3"),token(TEACHER)).andExpect(status().isOk()));safe(units);
        var found=false;
        for(var unit:units) {
            if(unit.get("questions").get(0).get("groupVersionId").asString().equals(gv(2).toString())) {
                found=true;assertEquals(List.of(v(10).toString(),v(9).toString(),v(8).toString()),
                    java.util.stream.StreamSupport.stream(unit.get("questions").spliterator(),false).map(q->q.get("questionVersionId").asString()).toList());
            }
        }
        assertTrue(found);
        call(get(TESTS+"/question-units").param("partNumber","3").param("page","-1"),token(TEACHER)).andExpect(status().isBadRequest());
        jdbc.update("update questions set status='ARCHIVED' where question_id=?",q(8));
        var hidden=tree(call(get(TESTS+"/question-units").param("partNumber","3"),token(TEACHER)).andExpect(status().isOk()));
        assertFalse(hidden.toString().contains(gv(2).toString()));
    }
    @Test void owner404Permission403Anonymous401AndAdminAny()throws Exception {
        var d=draft(doc("Owner",1));var other=UUID.fromString("a3000000-0000-0000-0000-000000000002");
        jdbc.update("update users set password_hash=? where user_id=?",encoder.encode("PracticeFixtureOnly123!"),other);
        call(get(path(d)),null).andExpect(status().isUnauthorized());
        call(get(path(d)),token(ACTOR)).andExpect(status().isForbidden());
        call(get(path(d)),token(other)).andExpect(status().isNotFound());
        call(put(path(d)).contentType(APPLICATION_JSON).content(edit(0,doc("Steal"))),token(other)).andExpect(status().isNotFound());
        call(get(path(d)),token(ADMIN)).andExpect(status().isOk());
        assertTrue(tree(call(get(TESTS),token(other)).andExpect(status().isOk())).isEmpty());
        assertEquals(1,tree(call(get(TESTS),token(TEACHER)).andExpect(status().isOk())).size());
        authenticate(ACTOR,"test:manage:self");assertThrows(org.springframework.security.access.AccessDeniedException.class,()->exams.read(TEACHER,created.getFirst(),null));
    }
    @Test void staleAndMismatchedParentCannotOverwrite()throws Exception {
        var d=draft(doc("Original",1));
        call(put(path(d)).contentType(APPLICATION_JSON).content(edit(0,doc("New",2))),token(TEACHER)).andExpect(status().isOk());
        call(put(path(d)).contentType(APPLICATION_JSON).content(edit(0,doc("Stale",1))),token(TEACHER)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TEST_REVISION_CONFLICT"));
        call(get(TESTS+"/"+created.getFirst()+"/versions/"+UUID.randomUUID()),token(TEACHER)).andExpect(status().isNotFound());
        assertEquals("New",examsDocument(d).get("title").asString());
    }
    JsonNode examsDocument(JsonNode d)throws Exception {return tree(call(get(path(d)),token(TEACHER)).andExpect(status().isOk())).get("document");}
    @ParameterizedTest @ValueSource(strings={"split","order","groupMismatch","partMismatch","crossPartGroup","unpublished","duplicate","archivedMember","unknownGroupOrder"})
    void invalidReferencesDoNotMutateDraft(String kind)throws Exception {
        var d=draft(doc("Original",1));String body=doc("Invalid",10,9,8);
        switch(kind) {
            case "split"->body=doc("Split",10,9);
            case "order"->body=doc("Order",8,9,10);
            case "groupMismatch"->body=body.replace(gv(2).toString(),gv(1).toString());
            case "partMismatch"->body=body.replace("\"partNumber\":3","\"partNumber\":4");
            case "crossPartGroup"->body="{\"title\":\"Wrong Part\",\"testType\":\"FULL\",\"sections\":[{\"type\":\"LISTENING\",\"parts\":[{\"partNumber\":3,\"questions\":[{\"questionVersionId\":\""+v(10)+"\",\"groupVersionId\":\""+gv(2)+"\"}]},{\"partNumber\":4,\"questions\":[{\"questionVersionId\":\""+v(9)+"\",\"groupVersionId\":\""+gv(2)+"\"},{\"questionVersionId\":\""+v(8)+"\",\"groupVersionId\":\""+gv(2)+"\"}]}]}]}";
            case "unpublished"->jdbc.update("update question_versions set status='DRAFT' where question_version_id=?",v(8));
            case "duplicate"->body=doc("Duplicate",1,1);
            case "archivedMember"->jdbc.update("update questions set status='ARCHIVED' where question_id=?",q(8));
            case "unknownGroupOrder"->jdbc.update("update question_versions set group_question_order=null where question_version_id=?",v(8));
        }
        call(put(path(d)).contentType(APPLICATION_JSON).content(edit(0,body)),token(TEACHER)).andExpect(status().is(kind.equals("duplicate")?400:422));
        assertEquals("Original",examsDocument(d).get("title").asString());
    }
    @ParameterizedTest @ValueSource(strings={"unknown","duplicateField","numberString","fraction","negative","badSection","sectionPart","badTitle","badUUID","repeatedSection"})
    void invalidPayloadCannotCreate(String kind)throws Exception {
        String body=doc("Valid",1);
        body=switch(kind) {
            case "unknown"->body.replace("\"title\":","\"ownerId\":\"spoof\",\"title\":");
            case "duplicateField"->body.replace("\"partNumber\":1","\"partNumber\":1,\"partNumber\":1");
            case "numberString"->body.replace("\"partNumber\":1","\"partNumber\":\"1\"");
            case "fraction"->body.replace("\"partNumber\":1","\"partNumber\":1.0");
            case "negative"->body.replace("\"title\":","\"durationSeconds\":-1,\"title\":");
            case "badSection"->body.replace("LISTENING","OTHER");
            case "sectionPart"->body.replace("LISTENING","READING");
            case "badTitle"->body.replace("Valid","");
            case "badUUID"->body.replace(v(1).toString(),"bad");
            case "repeatedSection"->"{\"title\":\"X\",\"testType\":\"FULL\",\"sections\":[{\"type\":\"READING\",\"parts\":[]},{\"type\":\"LISTENING\",\"parts\":[]}]}";
            default->throw new AssertionError();
        };
        call(post(TESTS).contentType(APPLICATION_JSON).content(body),token(TEACHER)).andExpect(status().isBadRequest());
        assertEquals(0,count("select count(*) from tests where created_by=?",TEACHER));
    }
    @Test void pinnedDraftAndPublishedVersionAreImmutableAndOldResultSurvives()throws Exception {
        var d=draft(doc("Pinned",1));var learner=token(ACTOR);var a=create(learner,List.of(5),1);var aid=id(a);
        call(post(API+"/"+aid+"/submit"),learner).andExpect(status().isOk());
        var before=tree(call(get(API+"/"+aid+"/result"),learner).andExpect(status().isOk()));
        jdbc.update("update attempts set test_version_id=? where attempt_id=?",UUID.fromString(d.get("versionId").asString()),aid);
        call(put(path(d)).contentType(APPLICATION_JSON).content(edit(0,doc("Changed"))),token(TEACHER)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TEST_VERSION_NOT_EDITABLE"));
        assertEquals(before,tree(call(get(API+"/"+aid+"/result"),learner).andExpect(status().isOk())));
        jdbc.update("update attempts set test_version_id=null where attempt_id=?",aid);
        for(var status:List.of("PUBLISHED","RETIRED")) {
            jdbc.update("update test_versions set status=? where test_version_id=?",status,UUID.fromString(d.get("versionId").asString()));
            call(put(path(d)).contentType(APPLICATION_JSON).content(edit(0,doc("Changed"))),token(TEACHER)).andExpect(status().isConflict());
        }
        assertEquals("Pinned",examsDocument(d).get("title").asString());
    }
    @Test void questionReadPermissionIsRequiredForReferencesButNoMediaIsExposed()throws Exception {
        authenticate(TEACHER,"test:manage:self");
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->exams.create(TEACHER,ExamDraftRequest.parse(doc("Private",1),false).document()));
        var d=draft(doc("No media access",1));safe(d);
        assertFalse(d.toString().contains("resource"));
    }
    @Test void auditFailureRollsBackDocumentRevisionAndReferences()throws Exception {
        var d=draft(doc("Original",1));authenticate(TEACHER,"test:manage:self","question:read");
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("Synthetic audit failure")).when(audit).audit(any(),eq("TEST_UPDATED"),any(),any(),any(),any(),any());
        assertThrows(org.springframework.dao.DataAccessException.class,()->exams.update(TEACHER,created.getFirst(),UUID.fromString(d.get("versionId").asString()),0,ExamDraftRequest.parse(doc("Failed",2),false).document()));
        reset(audit);assertEquals("Original",examsDocument(d).get("title").asString());
        assertEquals(0,count("select revision from test_version_drafts where test_version_id=?",UUID.fromString(d.get("versionId").asString())));
    }
    @Test void concurrentUpdatesHaveExactlyOneWinner()throws Exception {
        var d=draft(doc("Concurrent",1));var document=ExamDraftRequest.parse(doc("Winner",2),false).document();
        var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<String> task=()-> {authenticate(TEACHER,"test:manage:self","question:read");gate.await();
                try {exams.update(TEACHER,created.getFirst(),UUID.fromString(d.get("versionId").asString()),0,document);return "OK";}
                catch(ExamDraftException e){return e.getCode();}finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}};
            var a=pool.submit(task);var b=pool.submit(task);gate.countDown();
            assertEquals(Set.of("OK","TEST_REVISION_CONFLICT"),Set.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS)));
        } finally { pool.shutdownNow(); }
    }
}
