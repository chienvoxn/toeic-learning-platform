package com.toeic.backend.questionbank.api;

import com.toeic.backend.identity.application.SessionPrincipal;
import com.toeic.backend.questionbank.application.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"debug=false","logging.level.org.hibernate.orm.jdbc.error=OFF"})
@AutoConfigureMockMvc
@Sql(scripts="/fixtures/q03-authoring.sql",config=@SqlConfig(transactionMode=SqlConfig.TransactionMode.ISOLATED))
@Sql(scripts="/fixtures/q03-authoring-cleanup.sql",executionPhase=Sql.ExecutionPhase.AFTER_TEST_METHOD,
        config=@SqlConfig(transactionMode=SqlConfig.TransactionMode.ISOLATED))
abstract class QuestionAuthoringTestSupport {
    protected static final UUID ACTOR=actor(1),OTHER=actor(2),ADMIN=actor(3),LEARNER=actor(4);
    protected static final String Q="/api/v1/teacher/questions",G="/api/v1/teacher/question-groups";
    private static final String PASSWORD="Q03FixturePasswordOnly!";
    private static String hash;
    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected ObjectMapper json;
    @Autowired protected PasswordEncoder encoder;
    @Autowired protected QuestionAuthoringService service;
    @Autowired protected PlatformTransactionManager transactions;
    protected static UUID actor(int n) { return UUID.fromString("a3000000-0000-0000-0000-%012d".formatted(n)); }
    protected static UUID media(int n) { return UUID.fromString("b3000000-0000-0000-0000-%012d".formatted(n)); }
    @BeforeEach void setup() {
        SecurityContextHolder.clearContext();
        if(hash==null)hash=encoder.encode(PASSWORD);
        jdbc.update("update users set password_hash=? where user_id between ? and ?",hash,ACTOR,LEARNER);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    protected int count(String sql,Object... args) { return jdbc.queryForObject(sql,Integer.class,args); }
    protected String token(UUID actor) throws Exception {
        var email=jdbc.queryForObject("select email from users where user_id=?",String.class,actor);
        return tree(mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email",email,"password",PASSWORD)))).andExpect(status().isOk()))
                .get("accessToken").asString();
    }
    protected ResultActions call(MockHttpServletRequestBuilder request,String token) throws Exception {
        if(token!=null)request.header("Authorization","Bearer "+token);return mvc.perform(request);
    }
    protected JsonNode tree(ResultActions result) throws Exception { return json.readTree(result.andReturn().getResponse().getContentAsString()); }
    protected List<Map<String,Object>> choices(int part) {
        var result=new ArrayList<Map<String,Object>>();
        for(int i=0;i<(part==2?3:4);i++)result.add(Map.of("label",String.valueOf((char)('A'+i)),"content","Choice "+i,"isCorrect",i==0,"displayOrder",i+1));
        return result;
    }
    protected Map<String,Object> payload(int part,UUID groupVersion) {
        var body=new LinkedHashMap<String,Object>();
        body.put("partId",jdbc.queryForObject("select part_id from toeic_parts where part_number=?",Integer.class,part));
        body.put("questionText","Q03 original prompt");body.put("explanation","Q03_ANSWER_EXPLANATION");
        body.put("difficulty","EASY");body.put("choices",choices(part));if(groupVersion!=null)body.put("groupVersionId",groupVersion);return body;
    }
    protected JsonNode create(String token,int part,UUID groupVersion) throws Exception { return create(token,payload(part,groupVersion)); }
    protected JsonNode create(String token,Map<String,Object> body) throws Exception {
        return tree(call(post(Q).contentType(APPLICATION_JSON).content(json.writeValueAsString(body)),token).andExpect(status().isCreated()));
    }
    protected JsonNode group(String token) throws Exception {
        return tree(call(post(G).contentType(APPLICATION_JSON).content("{\"groupType\":\"PASSAGE\",\"instruction\":\"Instruction\",\"passageText\":\"Published passage\"}"),token).andExpect(status().isCreated()));
    }
    protected UUID qid(JsonNode d) { return UUID.fromString(d.get("question").get("questionId").asString()); }
    protected UUID gid(JsonNode d) { return UUID.fromString(d.get("group").get("groupId").asString()); }
    protected UUID qv(JsonNode d,int n) { return UUID.fromString(d.get("versions").get(n).get("questionVersionId").asString()); }
    protected UUID gv(JsonNode d,int n) { return UUID.fromString(d.get("versions").get(n).get("groupVersionId").asString()); }
    protected void publish(boolean group,UUID parent,UUID version) {
        jdbc.update("update "+(group?"question_group_versions":"question_versions")+" set status='PUBLISHED',published_at=now()-interval '1 minute' where "+(group?"group_version_id":"question_version_id")+"=?",version);
        jdbc.update("update "+(group?"question_groups":"questions")+" set status='PUBLISHED',current_published_version_id=? where "+(group?"group_id":"question_id")+"=?",version,parent);
    }
    protected JsonNode patchQ(String token,UUID id,String body) throws Exception { return tree(call(patch(Q+"/"+id).contentType(APPLICATION_JSON).content(body),token).andExpect(status().isOk())); }
    protected JsonNode patchG(String token,UUID id,String body) throws Exception { return tree(call(patch(G+"/"+id).contentType(APPLICATION_JSON).content(body),token).andExpect(status().isOk())); }
    protected void safe(String value) {
        assertFalse(value.contains("storageUrl")||value.contains("storage_url")||value.contains("Q03_LEAK_STORAGE")||value.contains("Q03_LEGACY_TRANSCRIPT"));
        assertFalse(value.contains("passwordHash")||value.contains("password_hash")||value.contains("accessToken")||value.contains("ownerId"));
    }
    protected void readSafe(JsonNode value) {
        var text=value.toString();assertFalse(text.contains("isCorrect")||text.contains("explanation")||text.contains("transcript")||text.contains("Q03_ANSWER_EXPLANATION"));safe(text);
    }
    protected void authenticate(UUID actor,String... permissions) {
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(new SessionPrincipal(UUID.randomUUID(),actor,List.of(),List.of(permissions)),null,
                Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList()));SecurityContextHolder.setContext(context);
    }
    protected QuestionAuthoringEdit command(Map<String,Object> body) throws Exception {
        return json.readValue(json.writeValueAsString(body),QuestionAuthoringRequest.class).command(false);
    }
    protected void revokeGrant() { jdbc.update("update media_reuse_grants set revoked_at=now(),revoked_by=? where media_id=? and user_id=?",ADMIN,media(5),ACTOR); }
}
