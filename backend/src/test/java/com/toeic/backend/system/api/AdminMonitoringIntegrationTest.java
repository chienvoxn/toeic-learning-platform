package com.toeic.backend.system.api;

import com.toeic.backend.identity.domain.User;
import com.toeic.backend.identity.infrastructure.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"debug=false","logging.level.org.hibernate.orm.jdbc.error=OFF","admin.monitoring.ai-health-url="})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
class AdminMonitoringIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    private final List<User> accounts=new ArrayList<>();
    private final List<UUID> auditIds=new ArrayList<>(),attemptIds=new ArrayList<>();
    private final List<Integer> roleIds=new ArrayList<>();
    private User admin,learner,teacher;
    private String token;
    private static String hash;
    private static final String PASSWORD="Admin07TestOnly123!";
    @BeforeEach void setup() throws Exception {
        if(hash==null)hash=encoder.encode(PASSWORD);
        admin=account("ADMIN");learner=account("LEARNER");teacher=account("TEACHER");token=token(admin);
    }
    private User account(String name) {
        var user=new User("admin07-"+UUID.randomUUID()+"@example.test",hash,"Private account name");user.verifyEmail();user.addRole(roles.findByName(name).orElseThrow());
        users.saveAndFlush(user);accounts.add(user);return user;
    }
    @AfterEach void cleanup() {
        for(UUID id:auditIds)jdbc.update("DELETE FROM audit_logs WHERE audit_id=?",id);
        for(UUID id:attemptIds)jdbc.update("DELETE FROM attempts WHERE attempt_id=?",id);
        for(var user:accounts) {
            jdbc.update("DELETE FROM audit_logs WHERE actor_user_id=? OR entity_id=?",user.getUserId(),user.getUserId());
            jdbc.update("DELETE FROM auth_sessions WHERE user_id=?",user.getUserId());jdbc.update("DELETE FROM user_roles WHERE user_id=?",user.getUserId());jdbc.update("DELETE FROM users WHERE user_id=?",user.getUserId());
        }
        for(int id:roleIds){jdbc.update("DELETE FROM role_permissions WHERE role_id=?",id);jdbc.update("DELETE FROM roles WHERE role_id=?",id);}
    }
    private JsonNode tree(ResultActions action) throws Exception {return json.readTree(action.andReturn().getResponse().getContentAsString());}
    private String token(User user) throws Exception {
        return tree(mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON).content(json.writeValueAsString(Map.of("email",user.getEmail(),"password",PASSWORD)))).andExpect(status().isOk())).get("accessToken").asString();
    }
    private ResultActions getAdmin(String path) throws Exception {return mvc.perform(get("/api/v1/admin"+path).header("Authorization","Bearer "+token));}
    private UUID audit(String action,String type,UUID entity,Object details,OffsetDateTime time) {
        UUID id=UUID.randomUUID();auditIds.add(id);
        jdbc.update("INSERT INTO audit_logs(audit_id,actor_user_id,action,entity_type,entity_id,details,created_at) VALUES (?,?,?,?,?,CAST(? AS jsonb),?)",id,admin.getUserId(),action,type,entity,json.writeValueAsString(details),time);return id;
    }
    private String actorFilter() {return "actor="+admin.getUserId();}
    private void attempt(String source,OffsetDateTime start,OffsetDateTime submitted) {
        UUID id=UUID.randomUUID();attemptIds.add(id);boolean section=source.equals("SECTION_SNAPSHOT");
        jdbc.update("INSERT INTO attempts(attempt_id,user_id,attempt_type,attempt_source_type,status,started_at,expires_at,submitted_at,created_at,total_questions,section_type,configured_duration_seconds) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                id,learner.getUserId(),section?"SECTION":"PRACTICE",source,submitted==null?"IN_PROGRESS":"GRADED",start,start.plusMinutes(2),submitted,start,1,section?"READING":null,section?120:null);
    }
    @Test void apiRequiresActiveAdminAndMatchingPermissionEvenOnExistingToken() throws Exception {
        for(String path:List.of("/audit-logs","/audit-logs/"+UUID.randomUUID(),"/health","/usage-statistics")) {
            mvc.perform(get("/api/v1/admin"+path)).andExpect(status().isUnauthorized());
            for(User user:List.of(learner,teacher))mvc.perform(get("/api/v1/admin"+path).header("Authorization","Bearer "+token(user))).andExpect(status().isForbidden());
        }
        var endpoints=Map.of("audit:read","/audit-logs","system:health:read","/health","system:usage:read","/usage-statistics");
        for(var entry:endpoints.entrySet()) {
            jdbc.update("DELETE FROM role_permissions WHERE role_id=(SELECT role_id FROM roles WHERE name='ADMIN') AND permission_id=(SELECT permission_id FROM permissions WHERE code=?)",entry.getKey());
            try {
                getAdmin(entry.getValue()).andExpect(status().isForbidden());
                var profile=tree(mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer "+token)).andExpect(status().isOk()));
                String capability=switch(entry.getKey()){case "audit:read" -> "auditRead";case "system:health:read" -> "healthRead";default -> "usageRead";};
                assertFalse(profile.path("adminCapabilities").path(capability).asBoolean());
            } finally {jdbc.update("INSERT INTO role_permissions SELECT role_id,permission_id FROM roles CROSS JOIN permissions WHERE name='ADMIN' AND code=? ON CONFLICT DO NOTHING",entry.getKey());}
        }
        jdbc.update("UPDATE users SET status='LOCKED' WHERE user_id=?",admin.getUserId());getAdmin("/audit-logs").andExpect(status().isUnauthorized());
    }
    @Test void allFiltersUtcBoundariesCountAndDeterministicPagingMatch() throws Exception {
        var start=OffsetDateTime.parse("2290-11-13T00:00:00Z");UUID resource=learner.getUserId();var expected=new ArrayList<String>();
        for(int i=0;i<23;i++)expected.add(audit("USER_STATUS_CHANGED","USER",resource,Map.of("from","ACTIVE","to","LOCKED"),start).toString());
        audit("USER_STATUS_CHANGED","USER",resource,Map.of(),start.plusDays(1));
        audit("USER_STATUS_CHANGED","USER",resource,Map.of(),start.minusNanos(1000));
        audit("ROLE_CREATED","ROLE",null,Map.of("after",Map.of("roleId",42)),start);
        expected.sort(String::compareTo);
        String filter="/audit-logs?"+actorFilter()+"&action=USER_STATUS_CHANGED&entityType=USER&entityId="+resource+"&from=2290-11-13T00:00:00Z&to=2290-11-14T00:00:00Z&size=10&sort=createdAt,asc";
        var first=tree(getAdmin(filter).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(23)).andExpect(jsonPath("$.items.length()").value(10)));
        var second=tree(getAdmin(filter+"&page=1").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(23)));
        var ids=new ArrayList<String>();first.get("items").forEach(node -> ids.add(node.get("auditId").asString()));second.get("items").forEach(node -> ids.add(node.get("auditId").asString()));
        assertEquals(expected.subList(0,20),ids);assertTrue(first.get("items").get(0).get("createdAt").asString().endsWith("Z"));
        getAdmin(filter+"&page=3").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(23)).andExpect(jsonPath("$.items.length()").value(0));
        getAdmin("/audit-logs?actor="+UUID.randomUUID()).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }
    @Test void sanitizedListAndDetailNeverExposeSecretsOrArbitraryPayload() throws Exception {
        String secret="private-payload-marker";var time=OffsetDateTime.now(ZoneOffset.UTC);
        var role=audit("ROLE_UPDATED","ROLE",null,Map.of("password",secret,"before",Map.of("roleId",42,"name",secret,"description",secret,"permissionIds",List.of(1,secret,true)),"after",Map.of("roleId",42,"displayName",secret,"description","jdbc:secret")),time);
        var status=audit("USER_STATUS_CHANGED","USER",learner.getUserId(),Map.of("from","ACTIVE","to","LOCKED","otp",secret,"nested",Map.of("token",secret)),time);
        var assignment=audit("USER_ROLES_CHANGED","USER",learner.getUserId(),Map.of("before",Map.of("roles",List.of(Map.of("roleId",1,"name",secret))),"after",Map.of("roles",List.of(Map.of("roleId",2,"displayName",secret)))),time);
        var unknown=audit("UNRECOGNIZED_EVENT","PRIVATE_TYPE",null,Map.of("payload",secret),time);
        for(UUID id:List.of(role,status,assignment,unknown)) {
            var result=tree(getAdmin("/audit-logs/"+id).andExpect(status().isOk()));String body=result.toString();
            for(String marker:List.of(secret,"jdbc:","password","otp","token","PRIVATE_TYPE","UNRECOGNIZED_EVENT"))assertFalse(body.contains(marker),marker);
        }
        getAdmin("/audit-logs/"+status).andExpect(jsonPath("$.changes.from").value("ACTIVE")).andExpect(jsonPath("$.changes.to").value("LOCKED"));
        getAdmin("/audit-logs/"+assignment).andExpect(jsonPath("$.changes.beforeRoleIds[0]").value(1)).andExpect(jsonPath("$.changes.afterRoleIds[0]").value(2));
        getAdmin("/audit-logs/"+unknown).andExpect(jsonPath("$.event.action").value("OTHER")).andExpect(jsonPath("$.changes").isEmpty());
        getAdmin("/audit-logs?"+actorFilter()+"&action=OTHER&entityType=OTHER").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        var list=tree(getAdmin("/audit-logs?"+actorFilter()+"&entityType=ROLE&entityId=42").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)));
        assertFalse(list.toString().contains(secret));assertEquals("42",list.get("items").get(0).get("entityId").asString());
    }
    @Test void malformedLegacyRoleMetadataCannotBreakOrLeakFromReadProjection() throws Exception {
        for(String id:List.of("2147483647","2147483648","99999999999999999999999999","jdbc:private"))audit("ROLE_DELETED","ROLE",null,Map.of("before",Map.of("roleId",id)),OffsetDateTime.now(ZoneOffset.UTC));
        var result=tree(getAdmin("/audit-logs?"+actorFilter()+"&entityType=ROLE").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(4)));
        long valid=0;for(var row:result.path("items"))if(!row.path("entityId").isNull()){valid++;assertEquals("2147483647",row.path("entityId").asString());}
        assertEquals(1,valid);assertFalse(result.toString().contains("private"));
    }
    @Test void realAdminMutationsCreateReadableAuditIncludingDeletedRoleAndRollback() throws Exception {
        var payload=Map.of("name","A07_"+UUID.randomUUID().toString().replace("-",""),"displayName","Private role display name");
        int role=tree(mvc.perform(post("/api/v1/admin/roles").header("Authorization","Bearer "+token).contentType(APPLICATION_JSON).content(json.writeValueAsString(payload))).andExpect(status().isCreated())).get("roleId").asInt();roleIds.add(role);
        mvc.perform(patch("/api/v1/admin/users/"+learner.getUserId()+"/status").header("Authorization","Bearer "+token).contentType(APPLICATION_JSON).content("{\"status\":\"LOCKED\"}")).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/admin/roles/"+role).header("Authorization","Bearer "+token)).andExpect(status().isNoContent());
        getAdmin("/audit-logs?"+actorFilter()+"&entityType=ROLE&entityId="+role).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        getAdmin("/audit-logs?"+actorFilter()+"&action=USER_STATUS_CHANGED&entityId="+learner.getUserId()).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT admin07_audit_failure CHECK (actor_user_id <> '"+admin.getUserId()+"'::uuid OR action <> 'USER_STATUS_CHANGED' OR details->>'to'='LOCKED')");
        try {
            // Existing lock audit remains valid; only this fixture's attempted unlock audit fails.
            assertThrows(jakarta.servlet.ServletException.class,() -> mvc.perform(patch("/api/v1/admin/users/"+learner.getUserId()+"/status").header("Authorization","Bearer "+token).contentType(APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}")));
            assertEquals("LOCKED",jdbc.queryForObject("SELECT status FROM users WHERE user_id=?",String.class,learner.getUserId()));
            getAdmin("/audit-logs?"+actorFilter()+"&action=USER_STATUS_CHANGED").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        } finally {jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT admin07_audit_failure");}
    }
    @Test void usageAggregatesRealRowsAndIndependentStartSubmitWindows() throws Exception {
        var start=OffsetDateTime.parse("2290-11-13T00:00:00Z");
        attempt("PRACTICE_SNAPSHOT",start,null);attempt("PRACTICE_SNAPSHOT",start.minusDays(1),start.plusHours(1));
        attempt("PRACTICE_SNAPSHOT",start.minusDays(1),start.plusHours(2));attempt("PRACTICE_SNAPSHOT",start.plusDays(1),null);
        attempt("SECTION_SNAPSHOT",start,start.plusMinutes(1));attempt("SECTION_SNAPSHOT",start.plusDays(1),null);
        attempt("TEST_VERSION",start,start.plusMinutes(1));
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,role_id FROM roles WHERE name='TEACHER' ON CONFLICT DO NOTHING",learner.getUserId());
        audit("USER_STATUS_CHANGED","USER",learner.getUserId(),Map.of(),start);audit("UNTRACKED","USER",null,Map.of(),start);audit("ROLE_CREATED","ROLE",null,Map.of(),start.plusDays(1));
        var result=tree(getAdmin("/usage-statistics?from=2290-11-13T00:00:00Z&to=2290-11-14T00:00:00Z").andExpect(status().isOk()));
        assertEquals(1,result.path("practice").path("startedCount").asLong());assertEquals(2,result.path("practice").path("submittedCount").asLong());
        assertEquals(1,result.path("section").path("startedCount").asLong());assertEquals(1,result.path("section").path("submittedCount").asLong());
        assertEquals(1,result.path("administrativeActivity").path("recordedCount").asLong());
        assertEquals(jdbc.queryForObject("SELECT count(*) FROM users",Long.class),result.path("users").path("total").asLong());
        long sum=0;for(var group:result.path("users").path("byStatus"))sum+=group.path("count").asLong();assertEquals(result.path("users").path("total").asLong(),sum);
        for(var group:result.path("users").path("byRole"))assertEquals(jdbc.queryForObject("SELECT count(DISTINCT user_id) FROM user_roles WHERE role_id=?",Long.class,group.get("roleId").asInt()),group.get("count").asLong());
        assertFalse(result.toString().contains(learner.getEmail()));assertFalse(result.toString().contains("Private account name"));
    }
    @Test void healthKeepsPublicContractAndReturnsOnlyProtectedStatusesAndUtcTimes() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP")).andExpect(jsonPath("$.message").value("TOEIC Backend is running"));
        mvc.perform(get("/api/health/database")).andExpect(status().isOk()).andExpect(jsonPath("$.database").isNotEmpty());
        var result=tree(getAdmin("/health").andExpect(status().isOk()));
        assertEquals("UP",result.path("components").path("database").path("status").asString());assertEquals("NOT_CONFIGURED",result.path("components").path("ai").path("status").asString());
        assertEquals(2,result.size());for(var component:result.path("components"))assertEquals(2,component.size());
        for(String marker:List.of("jdbc:","localhost","toeic_","password","stackTrace","config"))assertFalse(result.toString().contains(marker));
    }
    @ParameterizedTest @ValueSource(strings={"page=-1","page=1&page=2","size=101","sort=details,desc","actor=not-uuid","entityId=1","from=2026-11-13","unknown=x","from=2026-11-14T00:00Z&to=2026-11-13T00:00Z"})
    void invalidFiltersFailWithoutExposingDatabase(String query) throws Exception {getAdmin("/audit-logs?"+query).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));}
    @Test void auditEndpointsAreReadOnlyAndUnknownIdsReturn404() throws Exception {
        var id=audit("ROLE_DELETED","ROLE",null,Map.of(),OffsetDateTime.now(ZoneOffset.UTC));
        for(var builder:List.of(post("/api/v1/admin/audit-logs"),patch("/api/v1/admin/audit-logs/"+id),delete("/api/v1/admin/audit-logs/"+id)))mvc.perform(builder.header("Authorization","Bearer "+token)).andExpect(status().isMethodNotAllowed());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE audit_id=?",Integer.class,id));
        getAdmin("/audit-logs/"+UUID.randomUUID()).andExpect(status().isNotFound());getAdmin("/audit-logs/bad").andExpect(status().isBadRequest());
        getAdmin("/health?debug=true").andExpect(status().isBadRequest());getAdmin("/usage-statistics?sort=users").andExpect(status().isBadRequest());
    }
}
