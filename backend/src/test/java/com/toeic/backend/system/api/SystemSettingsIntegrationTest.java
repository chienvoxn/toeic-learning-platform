package com.toeic.backend.system.api;

import com.toeic.backend.identity.domain.User;
import com.toeic.backend.identity.infrastructure.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"debug=false","logging.level.org.hibernate.orm.jdbc.error=OFF"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
class SystemSettingsIntegrationTest {
    static final String LIMIT="vocabulary.recommendations.defaultLimit",DAYS="learning.progress.defaultDays";
    static final String PASSWORD="SettingsTestOnly123!";
    static String hash;
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;@Autowired RoleRepository roles;@Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager transactions;
    final List<User> accounts=new ArrayList<>();
    List<Map<String,Object>> original,grants;
    User admin,learner,teacher;String token;
    @BeforeEach void setup() throws Exception {
        original=jdbc.queryForList("SELECT setting_key,setting_value::text AS value,revision,updated_at,updated_by FROM system_settings WHERE setting_key IN (?,?)",LIMIT,DAYS);
        grants=jdbc.queryForList("SELECT role_id,permission_id FROM role_permissions WHERE role_id=(SELECT role_id FROM roles WHERE name='ADMIN')");
        if(hash==null)hash=encoder.encode(PASSWORD);admin=account("ADMIN");learner=account("LEARNER");teacher=account("TEACHER");token=token(admin);
    }
    User account(String role){var u=new User("admin06-"+UUID.randomUUID()+"@example.test",hash,"Settings QA");u.verifyEmail();u.addRole(roles.findByName(role).orElseThrow());users.saveAndFlush(u);accounts.add(u);return u;}
    @AfterEach void cleanup(){
        for(var row:original)jdbc.update("UPDATE system_settings SET setting_value=CAST(? AS jsonb),revision=?,updated_at=?,updated_by=? WHERE setting_key=?",row.get("value"),row.get("revision"),row.get("updated_at"),row.get("updated_by"),row.get("setting_key"));
        jdbc.update("DELETE FROM system_settings WHERE setting_key='admin06.private.fixture'");
        jdbc.update("DELETE FROM role_permissions WHERE role_id=(SELECT role_id FROM roles WHERE name='ADMIN')");
        for(var row:grants)jdbc.update("INSERT INTO role_permissions(role_id,permission_id) VALUES (?,?)",row.get("role_id"),row.get("permission_id"));
        for(var u:accounts){
            jdbc.update("DELETE FROM audit_logs WHERE actor_user_id=? OR entity_id=?",u.getUserId(),u.getUserId());
            jdbc.update("DELETE FROM auth_sessions WHERE user_id=?",u.getUserId());jdbc.update("DELETE FROM vocabularies WHERE created_by=?",u.getUserId());
            jdbc.update("DELETE FROM user_roles WHERE user_id=?",u.getUserId());jdbc.update("DELETE FROM users WHERE user_id=?",u.getUserId());
        }
    }
    JsonNode tree(ResultActions actions) throws Exception{return json.readTree(actions.andReturn().getResponse().getContentAsString());}
    String token(User u) throws Exception{return tree(mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON).content(json.writeValueAsString(Map.of("email",u.getEmail(),"password",PASSWORD)))).andExpect(status().isOk())).get("accessToken").asString();}
    ResultActions getWith(String path,String bearer) throws Exception{return mvc.perform(get("/api/v1"+path).header("Authorization","Bearer "+bearer));}
    ResultActions update(String key,Object value,long expected,String bearer) throws Exception{return mvc.perform(patch("/api/v1/admin/system-settings/"+key).header("Authorization","Bearer "+bearer).contentType(APPLICATION_JSON).content(json.writeValueAsString(Map.of("value",value,"expectedRevision",expected))));}
    JsonNode setting(String key) throws Exception{var list=tree(getWith("/admin/system-settings",token).andExpect(status().isOk()));for(var value:list)if(value.get("key").asString().equals(key))return value;throw new AssertionError("Missing setting");}
    long version(String key) throws Exception{return setting(key).get("revision").asLong();}
    int audits(){return jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE actor_user_id=? AND action='SYSTEM_SETTING_UPDATED'",Integer.class,admin.getUserId());}
    @Test void readsOnlyAllowlistAndPersistsVersionedAuditWithoutSecrets() throws Exception {
        jdbc.update("INSERT INTO system_settings(setting_key,setting_value) VALUES ('admin06.private.fixture','\"private-marker\"'::jsonb)");
        var values=tree(getWith("/admin/system-settings",token).andExpect(status().isOk()));assertEquals(2,values.size());assertFalse(values.toString().contains("private-marker"));
        var before=setting(LIMIT);long version=before.get("revision").asLong();int value=before.get("value").asInt()==15?16:15;
        var result=tree(update(LIMIT,value,version,token).andExpect(status().isOk()));assertEquals(value,result.get("value").asInt());assertEquals(version+1,result.get("revision").asLong());
        assertEquals(result,setting(LIMIT));assertEquals(1,audits());
        var list=tree(getWith("/admin/audit-logs?action=SYSTEM_SETTING_UPDATED&entityType=SYSTEM_SETTING&entityId="+LIMIT+"&actor="+admin.getUserId(),token).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)));
        var detail=tree(getWith("/admin/audit-logs/"+list.get("items").get(0).get("auditId").asString(),token).andExpect(status().isOk()));
        assertEquals(value,detail.path("changes").path("afterValue").asInt());assertEquals(version,detail.path("changes").path("beforeRevision").asLong());assertFalse(detail.toString().contains("private-marker"));
        update(LIMIT,value,version+1,token).andExpect(status().isOk());assertEquals(1,audits());
        update(LIMIT,value,version,token).andExpect(status().isConflict());assertEquals(1,audits());
    }
    @Test void missingPermissionNonAdminGuestAndLockedActorCannotManage() throws Exception {
        mvc.perform(get("/api/v1/admin/system-settings")).andExpect(status().isUnauthorized());
        for(User user:List.of(learner,teacher)){String other=token(user);getWith("/admin/system-settings",other).andExpect(status().isForbidden());update(DAYS,5,version(DAYS),other).andExpect(status().isForbidden());}
        jdbc.update("DELETE FROM role_permissions WHERE role_id=(SELECT role_id FROM roles WHERE name='ADMIN') AND permission_id=(SELECT permission_id FROM permissions WHERE code='system:settings:manage')");
        getWith("/admin/system-settings",token).andExpect(status().isForbidden());update(DAYS,5,0,token).andExpect(status().isForbidden());
        getWith("/users/me",token).andExpect(status().isOk()).andExpect(jsonPath("$.adminCapabilities.settingsManagement").value(false));
        jdbc.update("UPDATE users SET status='LOCKED' WHERE user_id=?",admin.getUserId());getWith("/admin/system-settings",token).andExpect(status().isUnauthorized());assertEquals(0,audits());
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"value\":null,\"expectedRevision\":0}","{\"value\":\"12\",\"expectedRevision\":0}","{\"value\":10.0,\"expectedRevision\":0}","{\"value\":true,\"expectedRevision\":0}","{\"value\":0,\"expectedRevision\":0}","{\"value\":101,\"expectedRevision\":0}","{\"value\":10,\"expectedRevision\":-1}","{\"value\":10,\"expectedRevision\":0,\"key\":\"secret\"}","{\"value\":10,\"value\":20,\"expectedRevision\":0}"})
    void malformedUpdatesDoNotMutateOrAudit(String payload) throws Exception {
        var before=setting(LIMIT);mvc.perform(patch("/api/v1/admin/system-settings/"+LIMIT).header("Authorization","Bearer "+token).contentType(APPLICATION_JSON).content(payload)).andExpect(status().isBadRequest());assertEquals(before,setting(LIMIT));assertEquals(0,audits());
    }
    @Test void noCreateDeleteOrUnknownKeyAndProgressRangeIsNinety() throws Exception {
        update("spring.datasource.password",1,0,token).andExpect(status().isNotFound());update(DAYS,91,version(DAYS),token).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/system-settings").header("Authorization","Bearer "+token).contentType(APPLICATION_JSON).content("{}")).andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/api/v1/admin/system-settings/"+DAYS).header("Authorization","Bearer "+token)).andExpect(status().isMethodNotAllowed());
        getWith("/admin/system-settings?key=anything",token).andExpect(status().isBadRequest());assertEquals(0,audits());
    }
    @Test void realRuntimeConsumersUseNewDefaultsButExplicitInputWins() throws Exception {
        String learnerToken=token(learner);
        for(int i=0;i<12;i++)jdbc.update("INSERT INTO vocabularies(vocabulary_id,word,meaning,created_by,created_at) VALUES (?,?,?,?,?)",UUID.randomUUID(),"settingsword"+i,"test meaning",learner.getUserId(),OffsetDateTime.now(ZoneOffset.UTC));
        update(LIMIT,4,version(LIMIT),token).andExpect(status().isOk());getWith("/vocabulary/recommendations",learnerToken).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
        getWith("/vocabulary/recommendations?limit=2",learnerToken).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        update(LIMIT,6,version(LIMIT),token).andExpect(status().isOk());getWith("/vocabulary/recommendations",learnerToken).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(6));
        getWith("/vocabulary/recommendations?limit=2",learnerToken).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        update(DAYS,11,version(DAYS),token).andExpect(status().isOk());getWith("/learning-profile/progress",learnerToken).andExpect(status().isOk()).andExpect(jsonPath("$.days").value(11)).andExpect(jsonPath("$.dailyProgress.length()").value(11));
        getWith("/learning-profile/progress?days=3",learnerToken).andExpect(status().isOk()).andExpect(jsonPath("$.days").value(3));
        for(String query:List.of("limit=","limit=1.5","limit=101","limit=2&limit=3"))getWith("/vocabulary/recommendations?"+query,learnerToken).andExpect(status().isBadRequest());
        for(String query:List.of("days=","days=1.5","days=91","days=2&days=3"))getWith("/learning-profile/progress?"+query,learnerToken).andExpect(status().isBadRequest());
    }
    @Test void failedAuditRollsBackValueRevisionAndMetadata() throws Exception {
        var before=setting(DAYS);
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT admin06_audit_failure CHECK (actor_user_id <> '"+admin.getUserId()+"'::uuid OR action <> 'SYSTEM_SETTING_UPDATED')");
        try {update(DAYS,12,before.get("revision").asLong(),token).andExpect(status().isServiceUnavailable());assertEquals(before,setting(DAYS));assertEquals(0,audits());}
        finally {jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT admin06_audit_failure");}
    }
    @Test void corruptionDoesNotLeakOrSilentlyReplaceExplicitParameters() throws Exception {
        jdbc.update("UPDATE system_settings SET setting_value='\"private-marker\"'::jsonb WHERE setting_key=?",DAYS);
        var response=getWith("/admin/system-settings",token).andExpect(status().isServiceUnavailable()).andReturn().getResponse().getContentAsString();assertFalse(response.contains("private-marker"));
        String other=token(learner);getWith("/learning-profile/progress",other).andExpect(status().isServiceUnavailable());getWith("/learning-profile/progress?days=3",other).andExpect(status().isOk()).andExpect(jsonPath("$.days").value(3));
    }
    @Test void concurrentEditorsHaveOneWinnerAndOneConflict() throws Exception {
        long expected=version(DAYS);var pool=Executors.newFixedThreadPool(2);var results=new ArrayList<Future<Integer>>();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.query("SELECT pg_advisory_xact_lock(?)",rs -> {},73532002L);
                for(int value:List.of(12,13))results.add(pool.submit(() -> update(DAYS,value,expected,token).andReturn().getResponse().getStatus()));
                awaitWaiters(2);
            });
            var statuses=new ArrayList<Integer>();for(var result:results)statuses.add(result.get(10,TimeUnit.SECONDS));Collections.sort(statuses);
            assertEquals(List.of(200,409),statuses);assertEquals(expected+1,version(DAYS));assertEquals(1,audits());
        } finally {pool.shutdownNow();}
    }
    @Test void permissionRevokedWhileEditorWaitsIsRecheckedAfterLock() throws Exception {
        var before=setting(DAYS);var pool=Executors.newSingleThreadExecutor();var result=new ArrayList<Future<Integer>>();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.query("SELECT pg_advisory_xact_lock(?)",rs -> {},73532002L);
                result.add(pool.submit(() -> update(DAYS,12,before.get("revision").asLong(),token).andReturn().getResponse().getStatus()));awaitWaiters(1);
                jdbc.update("DELETE FROM role_permissions WHERE role_id=(SELECT role_id FROM roles WHERE name='ADMIN') AND permission_id=(SELECT permission_id FROM permissions WHERE code='system:settings:manage')");
            });
            assertEquals(403,result.getFirst().get(10,TimeUnit.SECONDS));assertEquals(0,audits());
            assertEquals(before.get("revision").asLong(),jdbc.queryForObject("SELECT revision FROM system_settings WHERE setting_key=?",Long.class,DAYS));
        } finally {pool.shutdownNow();}
    }
    void awaitWaiters(int number){
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);boolean waiting=false;
        while(System.nanoTime()<until){jdbc.query("SELECT pg_stat_clear_snapshot()",rs -> {});waiting=jdbc.queryForObject("SELECT count(*)>=? FROM pg_stat_activity WHERE datname=current_database() AND wait_event='advisory'",Boolean.class,number);if(waiting)break;try{Thread.sleep(20);}catch(InterruptedException e){throw new AssertionError(e);}}
        assertTrue(waiting,"Mutation must actually wait on PostgreSQL before releasing the shared RBAC lock");
    }
}
