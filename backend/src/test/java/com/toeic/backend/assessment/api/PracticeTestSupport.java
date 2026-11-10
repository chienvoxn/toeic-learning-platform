package com.toeic.backend.assessment.api;

import com.toeic.backend.assessment.application.*;
import com.toeic.backend.identity.application.SessionPrincipal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
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
@Import(PracticeTestSupport.ClockConfig.class)
@Sql(scripts={"/fixtures/q03-authoring.sql","/fixtures/prac01.sql"},config=@SqlConfig(transactionMode=SqlConfig.TransactionMode.ISOLATED))
@Sql(scripts={"/fixtures/prac01-cleanup.sql","/fixtures/q03-authoring-cleanup.sql"},executionPhase=Sql.ExecutionPhase.AFTER_TEST_METHOD,
        config=@SqlConfig(transactionMode=SqlConfig.TransactionMode.ISOLATED))
abstract class PracticeTestSupport {
    static final UUID ACTOR=UUID.fromString("a4000000-0000-0000-0000-000000000001");
    static final UUID OTHER=UUID.fromString("a4000000-0000-0000-0000-000000000002");
    static final UUID TEACHER=UUID.fromString("a3000000-0000-0000-0000-000000000001");
    static final UUID ADMIN=UUID.fromString("a3000000-0000-0000-0000-000000000003");
    static final Instant NOW=Instant.parse("2026-11-10T12:00:00Z");
    static final String API="/api/v1/attempts",PERMISSION="practice:manage:self";
    private static final String PASSWORD="PracticeFixtureOnly123!";
    private static String hash;
    @TestConfiguration static class ClockConfig { @Bean @Primary Clock practiceClock(){return Clock.fixed(NOW,ZoneOffset.UTC);} }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired PracticeSessionService service;
    @Autowired PlatformTransactionManager transactions;
    static UUID q(int n){return UUID.fromString("d4000000-0000-0000-0000-%012d".formatted(n));}
    static UUID v(int n){return UUID.fromString("d4100000-0000-0000-0000-%012d".formatted(n));}
    static UUID g(int n){return UUID.fromString("c4000000-0000-0000-0000-%012d".formatted(n));}
    static UUID gv(int n){return UUID.fromString("c4100000-0000-0000-0000-%012d".formatted(n));}
    @BeforeEach void setup(){
        SecurityContextHolder.clearContext();if(hash==null)hash=encoder.encode(PASSWORD);
        for(var actor:List.of(ACTOR,OTHER,TEACHER,ADMIN))jdbc.update("update users set password_hash=? where user_id=?",hash,actor);
    }
    @AfterEach void clear(){
        SecurityContextHolder.clearContext();
        jdbc.update("update questions set question_type_id=(select question_type_id from question_types where code='SINGLE_CHOICE') where question_type_id in(select question_type_id from question_types where code='PRAC_OTHER') and question_id between 'd4000000-0000-0000-0000-000000000001' and 'd4000000-0000-0000-0000-000000000022'");
        jdbc.update("delete from question_types where code='PRAC_OTHER'");
    }
    int count(String sql,Object... args){return jdbc.queryForObject(sql,Integer.class,args);}
    String token(UUID actor)throws Exception {
        return tree(mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON).content(json.writeValueAsString(
                Map.of("email",jdbc.queryForObject("select email from users where user_id=?",String.class,actor),"password",PASSWORD))))
                .andExpect(status().isOk())).get("accessToken").asString();
    }
    ResultActions call(MockHttpServletRequestBuilder request,String token)throws Exception {
        if(token!=null)request.header("Authorization","Bearer "+token);return mvc.perform(request);
    }
    JsonNode tree(ResultActions result)throws Exception {return json.readTree(result.andReturn().getResponse().getContentAsString());}
    String payload(List<Integer> parts,int n){return json.writeValueAsString(Map.of("sourceType","PRACTICE_SNAPSHOT","practice",Map.of("partNumbers",parts,"questionCount",n)));}
    JsonNode create(String token,List<Integer> parts,int n)throws Exception {
        return tree(call(post(API).contentType(APPLICATION_JSON).content(payload(parts,n)),token).andExpect(status().isCreated()));
    }
    UUID id(JsonNode session){return UUID.fromString(session.get("attemptId").asString());}
    JsonNode read(String token,UUID id)throws Exception {return tree(call(get(API+"/"+id),token).andExpect(status().isOk()));}
    void safe(JsonNode response){
        var value=response.toString();for(var hidden:List.of("\"isCorrect\"","\"explanation\"","\"transcript\"","\"answerKey\"","\"passwordHash\"","\"storageUrl\"","\"storage_url\"","\"url\"","PRAC_SECRET","PRAC_PRIVATE","Q03_LEAK_STORAGE","Q03_LEGACY_TRANSCRIPT"))assertFalse(value.contains(hidden),hidden);
    }
    void authenticate(UUID actor,String... permissions){
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(new SessionPrincipal(UUID.randomUUID(),actor,List.of(),List.of(permissions)),null,
                Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList()));SecurityContextHolder.setContext(context);
    }
}
