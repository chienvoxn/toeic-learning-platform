package com.toeic.backend.assessment.api;

import com.toeic.backend.identity.application.SessionPrincipal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="app.exams.expiry-enabled=false")
@AutoConfigureMockMvc
@Transactional
class ExamWorkflowIntegrationTest {
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper json;
    UUID teacher,admin,learner,other;
    @BeforeEach void setup(){teacher=user("TEACHER");admin=user("ADMIN");learner=user("LEARNER");other=user("TEACHER");}
    UUID user(String role){UUID id=UUID.randomUUID();jdbc.update("insert into users(user_id,email,full_name,status,created_at,updated_at) values(?,?,?,'ACTIVE',now(),now())",id,id+"@example.invalid",role);jdbc.update("insert into user_roles(user_id,role_id) select ?,role_id from roles where name=?",id,role);return id;}
    ResultActions call(UUID user,AbstractMockHttpServletRequestBuilder<?> request)throws Exception {
        var permissions=jdbc.queryForList("select distinct p.code from user_roles ur join role_permissions rp using(role_id) join permissions p using(permission_id) where ur.user_id=?",String.class,user);
        var roles=jdbc.queryForList("select r.name from user_roles ur join roles r using(role_id) where ur.user_id=?",String.class,user);
        var authorities=new ArrayList<SimpleGrantedAuthority>();permissions.forEach(p->authorities.add(new SimpleGrantedAuthority(p)));roles.forEach(r->authorities.add(new SimpleGrantedAuthority("ROLE_"+r)));
        return mvc.perform(request.with(authentication(UsernamePasswordAuthenticationToken.authenticated(new SessionPrincipal(UUID.randomUUID(),user,roles,permissions),null,authorities))));
    }
    JsonNode tree(ResultActions result)throws Exception{return json.readTree(result.andReturn().getResponse().getContentAsString());}
    String doc(){return """
        {"title":"Practice import","testType":"PRACTICE","durationSeconds":600,"maxAttempts":1,"groups":[],"questions":[
        {"partNumber":5,"questionText":"Starts ___ noon.","choices":["at","in","on","to"],"answer":"A","explanation":"at + time"}]}
        """;}
    JsonNode create()throws Exception{return tree(call(teacher,post("/api/v1/teacher/tests/import").contentType(APPLICATION_JSON).content(doc())).andExpect(status().isCreated()));}
    UUID id(JsonNode row){return UUID.fromString(row.get("versionId").asString());}
    JsonNode transition(UUID actor,JsonNode row,String action,String extra)throws Exception{return tree(call(actor,post("/api/v1/teacher/tests/"+id(row)+"/"+action).contentType(APPLICATION_JSON).content("{\"revision\":"+row.get("revision").asInt()+extra+"}")).andExpect(status().isOk()));}
    JsonNode published()throws Exception{var draft=create();draft=transition(teacher,draft,"submit-review","");draft=transition(admin,draft,"review",",\"decision\":\"APPROVE\"");return transition(admin,draft,"publish","");}
    JsonNode start(UUID version,UUID key)throws Exception{return tree(call(learner,post("/api/v1/exam-attempts").contentType(APPLICATION_JSON).content(json.writeValueAsString(Map.of("testVersionId",version,"startKey",key)))).andExpect(status().isCreated()));}

    @Test void importReviewPublishTakeGradeAndReviewPinnedResult()throws Exception{
        var version=published();var key=UUID.randomUUID();var attempt=start(id(version),key);var aid=attempt.get("id").asString();var qid=attempt.get("questions").get(0).get("id").asString();
        assertEquals(aid,start(id(version),key).get("id").asString());
        assertFalse(attempt.toString().contains("correctOption"));assertFalse(attempt.toString().contains("explanation"));
        call(learner,get("/api/v1/teacher/tests/"+id(version))).andExpect(status().isForbidden());
        call(other,get("/api/v1/exam-attempts/"+aid)).andExpect(status().isNotFound());
        call(learner,put("/api/v1/exam-attempts/"+aid+"/answers").contentType(APPLICATION_JSON).content("{\"questionId\":\""+qid+"\",\"selectedOption\":\"A\",\"clientSeq\":2}")).andExpect(status().isOk());
        call(learner,put("/api/v1/exam-attempts/"+aid+"/answers").contentType(APPLICATION_JSON).content("{\"questionId\":\""+qid+"\",\"selectedOption\":\"B\",\"clientSeq\":1}")).andExpect(status().isConflict());
        var result=tree(call(learner,post("/api/v1/exam-attempts/"+aid+"/submit")).andExpect(status().isOk()));assertEquals(1,result.get("rawCorrect").asInt());assertTrue(result.get("estimatedTotalScore").isNull());
        assertEquals(result,tree(call(learner,post("/api/v1/exam-attempts/"+aid+"/submit")).andExpect(status().isOk())));
        call(learner,put("/api/v1/exam-attempts/"+aid+"/answers").contentType(APPLICATION_JSON).content("{\"questionId\":\""+qid+"\",\"selectedOption\":\"B\",\"clientSeq\":3}")).andExpect(status().isConflict());
        call(admin,post("/api/v1/teacher/tests/"+id(version)+"/retire")).andExpect(status().isOk());
        assertEquals(result,tree(call(learner,get("/api/v1/exam-attempts/"+aid+"/result")).andExpect(status().isOk())));
        assertEquals(1,tree(call(learner,get("/api/v1/users/me/exam-progress")).andExpect(status().isOk())).size());
    }
    @Test void teacherCannotApproveOrPublishAndOtherOwnerCannotEdit()throws Exception{
        var draft=create();call(other,get("/api/v1/teacher/tests/"+id(draft))).andExpect(status().isNotFound());
        draft=transition(teacher,draft,"submit-review","");
        call(teacher,post("/api/v1/teacher/tests/"+id(draft)+"/review").contentType(APPLICATION_JSON).content("{\"revision\":1,\"decision\":\"APPROVE\"}")).andExpect(status().isForbidden());
        call(teacher,post("/api/v1/teacher/tests/"+id(draft)+"/publish").contentType(APPLICATION_JSON).content("{\"revision\":1}")).andExpect(status().isForbidden());
        var returned=transition(admin,draft,"review",",\"decision\":\"RETURN\",\"note\":\"Fix wording\"");assertEquals("DRAFT",returned.get("reviewStatus").asString());
    }
    @Test void invalidImportIsAtomicAndReportsQuestionPath()throws Exception{
        int before=jdbc.queryForObject("select count(*) from tests",Integer.class);
        var body=tree(call(teacher,post("/api/v1/teacher/tests/import").contentType(APPLICATION_JSON).content(doc().replace("\"answer\":\"A\"","\"answer\":\"Z\""))).andExpect(status().isUnprocessableEntity()));
        assertTrue(body.toString().contains("questions[0].answer"));assertEquals(before,jdbc.queryForObject("select count(*) from tests",Integer.class));
    }
    @Test void staleDraftAndEditAfterSubmitAreRejected()throws Exception{
        var draft=create();var saved=tree(call(teacher,put("/api/v1/teacher/tests/"+id(draft)).contentType(APPLICATION_JSON).content("{\"revision\":0,\"document\":"+doc()+"}")).andExpect(status().isOk()));
        call(teacher,put("/api/v1/teacher/tests/"+id(draft)).contentType(APPLICATION_JSON).content("{\"revision\":0,\"document\":"+doc()+"}")).andExpect(status().isConflict());
        transition(teacher,saved,"submit-review","");
        call(teacher,put("/api/v1/teacher/tests/"+id(draft)).contentType(APPLICATION_JSON).content("{\"revision\":2,\"document\":"+doc()+"}")).andExpect(status().isConflict());
    }
    @Test void maxAttemptsAndServerExpiryCannotBeBypassed()throws Exception{
        var version=published();var attempt=start(id(version),UUID.randomUUID());var aid=UUID.fromString(attempt.get("id").asString());
        call(learner,post("/api/v1/exam-attempts").contentType(APPLICATION_JSON).content(json.writeValueAsString(Map.of("testVersionId",id(version),"startKey",UUID.randomUUID())))).andExpect(status().isConflict());
        jdbc.update("update attempts set expires_at=now()-interval '1 minute' where attempt_id=?",aid);
        var resumed=tree(call(learner,get("/api/v1/exam-attempts/"+aid)).andExpect(status().isOk()));assertEquals("GRADED",resumed.get("status").asString());
        assertEquals(0,tree(call(learner,get("/api/v1/exam-attempts/"+aid+"/result")).andExpect(status().isOk())).get("rawCorrect").asInt());
    }
    @Test void mediaUploadRejectsHtmlAndEnforcesOwnership()throws Exception{
        var html=new org.springframework.mock.web.MockMultipartFile("file","fake.mp3","audio/mpeg","<html>unsafe</html>".getBytes());
        call(teacher,multipart("/api/v1/teacher/media").file(html)).andExpect(status().isBadRequest());
        var png=new org.springframework.mock.web.MockMultipartFile("file","image.png","image/png",new byte[]{(byte)137,80,78,71,13,10,26,10,0});
        var media=tree(call(teacher,multipart("/api/v1/teacher/media").file(png)).andExpect(status().isCreated()));
        call(teacher,get("/api/v1/media/"+media.get("resourceId").asString())).andExpect(status().isOk());
        call(learner,get("/api/v1/media/"+media.get("resourceId").asString())).andExpect(status().isNotFound());
        call(learner,multipart("/api/v1/teacher/media").file(png)).andExpect(status().isForbidden());
    }
    @Test void groupedPartSevenImportsAndPinsAsOnePassage()throws Exception {
        String text="""
            {"title":"Part 7","testType":"PRACTICE","durationSeconds":600,"groups":[{"key":"g1","partNumber":7,"passageText":"A notice"}],"questions":[
            {"partNumber":7,"groupKey":"g1","questionText":"One","choices":["A","B","C","D"],"answer":"A"},
            {"partNumber":7,"groupKey":"g1","questionText":"Two","choices":["A","B","C","D"],"answer":"B"}]}
            """;
        var d=tree(call(teacher,post("/api/v1/teacher/tests/import").contentType(APPLICATION_JSON).content(text)).andExpect(status().isCreated()));d=transition(teacher,d,"submit-review","");d=transition(admin,d,"review",",\"decision\":\"APPROVE\"");d=transition(admin,d,"publish","");
        var attempt=start(id(d),UUID.randomUUID());assertEquals(2,attempt.get("questions").size());assertEquals(attempt.get("questions").get(0).get("groupVersionId"),attempt.get("questions").get(1).get("groupVersionId"));
    }
}
