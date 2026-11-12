package com.toeic.backend.identity.api;

import com.toeic.backend.identity.application.*;
import com.toeic.backend.identity.domain.User;
import com.toeic.backend.identity.infrastructure.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"debug=false","logging.level.org.hibernate.orm.jdbc.error=OFF"})
@AutoConfigureMockMvc
class AdminUserIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired AdminUserService service;
    @Autowired SessionAuthenticationService authentication;
    @Autowired PlatformTransactionManager transactions;
    private final List<User> accounts=new ArrayList<>();
    private User admin,learner,teacher;
    private static final String PASSWORD="AdminTestOnly123!";
    private static String hash;
    @BeforeEach void setup() {
        if(hash==null)hash=encoder.encode(PASSWORD);
        admin=account("ADMIN"); learner=account("LEARNER"); teacher=account("TEACHER");
    }
    private User account(String role) {
        var user=new User("tv1-admin-"+UUID.randomUUID()+"@example.test",hash,"Nguyễn QA");
        user.verifyEmail();user.addRole(roles.findByName(role).orElseThrow());users.saveAndFlush(user);accounts.add(user);return user;
    }
    @AfterEach void cleanup() {
        SecurityContextHolder.clearContext();
        for(var user:accounts) {
            jdbc.update("DELETE FROM audit_logs WHERE actor_user_id=? OR entity_id=?",user.getUserId(),user.getUserId());
            jdbc.update("DELETE FROM auth_sessions WHERE user_id=?",user.getUserId());
            jdbc.update("DELETE FROM user_roles WHERE user_id=?",user.getUserId());
            jdbc.update("DELETE FROM users WHERE user_id=?",user.getUserId());
        }
    }
    private String token(User user) throws Exception {
        return json.readTree(login(user).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("accessToken").asString();
    }
    private ResultActions login(User user) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON).content(json.writeValueAsString(Map.of("email",user.getEmail(),"password",PASSWORD))));
    }
    private ResultActions change(String token,UUID id,String body) throws Exception {
        return mvc.perform(patch("/api/v1/admin/users/"+id+"/status").header("Authorization","Bearer "+token).contentType(APPLICATION_JSON).content(body));
    }
    private int audits() { return jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE actor_user_id=?",Integer.class,admin.getUserId()); }
    private String state(User user) { return jdbc.queryForObject("SELECT status FROM users WHERE user_id=?",String.class,user.getUserId()); }
    private void authenticate(String token) {
        var principal=authentication.authenticate(token);
        var rights=new ArrayList<SimpleGrantedAuthority>();
        principal.roles().forEach(role -> rights.add(new SimpleGrantedAuthority("ROLE_"+role)));
        principal.permissions().forEach(permission -> rights.add(new SimpleGrantedAuthority(permission)));
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,rights));
    }

    @Test void apiAndServiceRequireAdminAndLivePermission() throws Exception {
        mvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
        for(var actor:List.of(learner,teacher)) {
            var token=token(actor);
            mvc.perform(get("/api/v1/admin/users").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
            change(token,learner.getUserId(),"{\"status\":\"LOCKED\"}").andExpect(status().isForbidden());
            authenticate(token); assertThrows(AccessDeniedException.class,() -> service.changeStatus(learner.getUserId(),"LOCKED"));
            SecurityContextHolder.clearContext();
        }
        var adminToken=token(admin);
        mvc.perform(get("/api/v1/admin/users").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk());
        jdbc.update("DELETE FROM role_permissions WHERE role_id=(SELECT role_id FROM roles WHERE name='ADMIN') AND permission_id=(SELECT permission_id FROM permissions WHERE code='user:manage')");
        try {
            mvc.perform(get("/api/v1/admin/users").header("Authorization","Bearer "+adminToken)).andExpect(status().isForbidden());
            authenticate(adminToken); assertThrows(AccessDeniedException.class,() -> service.search(AdminUserQuery.parse(Map.of())));
        } finally {
            jdbc.update("INSERT INTO role_permissions SELECT role_id,permission_id FROM roles CROSS JOIN permissions WHERE name='ADMIN' AND code='user:manage' ON CONFLICT DO NOTHING");
        }
        assertEquals("ACTIVE",state(learner)); assertEquals(0,audits());
    }
    @Test void searchFiltersCountsDeduplicatesAndDoesNotLeakSecretsOrAdmin() throws Exception {
        var multi=account("LEARNER");
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,role_id FROM roles WHERE name='TEACHER'",multi.getUserId());
        var token=token(admin);
        var result=mvc.perform(get("/api/v1/admin/users").header("Authorization","Bearer "+token)
                .param("q",multi.getEmail().toUpperCase(Locale.ROOT)).param("role","TEACHER").param("status","ACTIVE").param("size","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].roles.length()").value(2)).andReturn().getResponse().getContentAsString();
        assertFalse(result.matches("(?is).*(password|token|hash|secret).*"));
        mvc.perform(get("/api/v1/admin/users").header("Authorization","Bearer "+token).param("q",admin.getEmail()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/admin/users").header("Authorization","Bearer "+token).param("q","%_' OR 1=1 --"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/admin/users").header("Authorization","Bearer "+token).param("q",learner.getEmail()).param("page","1").param("size","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/v1/admin/users").header("Authorization","Bearer "+token).param("q","NGUYỄN").param("sort","fullName,asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
    }
    @ParameterizedTest @ValueSource(strings={"role=ADMIN","status=DELETED","page=-1","size=0","size=101","page=2147483648","sort=passwordHash,asc","sort=email,sideways","unknown=value","role=","page=nope"})
    void rejectsInvalidQueries(String query) throws Exception {
        mvc.perform(get("/api/v1/admin/users?"+query).header("Authorization","Bearer "+token(admin))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
    @Test void rejectsDuplicateAndOversizedQuery() throws Exception {
        var token=token(admin);
        mvc.perform(get("/api/v1/admin/users").header("Authorization","Bearer "+token).param("role","LEARNER","TEACHER")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/users").header("Authorization","Bearer "+token).param("q","x".repeat(201))).andExpect(status().isBadRequest());
    }
    @Test void locksAndUnlocksBothRolesWithIdempotentAuditAndExistingSessionPolicy() throws Exception {
        var adminToken=token(admin);
        for(var actor:List.of(learner,teacher)) {
            var actorToken=token(actor);var second=token(actor);
            change(adminToken,actor.getUserId(),"{\"status\":\"LOCKED\"}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("LOCKED"));
            int afterLock=audits();
            change(adminToken,actor.getUserId(),"{\"status\":\"LOCKED\"}").andExpect(status().isOk());assertEquals(afterLock,audits());
            login(actor).andExpect(status().isUnauthorized());
            for(var token:List.of(actorToken,second)) mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM auth_sessions WHERE user_id=? AND revoked_at IS NOT NULL",Integer.class,actor.getUserId()));
            change(adminToken,actor.getUserId(),"{\"status\":\"ACTIVE\"}").andExpect(status().isOk());
            for(var token:List.of(actorToken,second)) mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer "+token)).andExpect(status().isOk());
            login(actor).andExpect(status().isOk());
        }
        assertEquals(4,audits());
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE actor_user_id=? AND details->>'from'='ACTIVE' AND details->>'to'='LOCKED'",Integer.class,admin.getUserId()));
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"status\":null}","{\"status\":true}","{\"status\":\"DISABLED\"}","{\"status\":\"ACTIVE\",\"roles\":[\"ADMIN\"]}","{\"status\":\"LOCKED\",\"userId\":\"bad\"}","{\"status\":\"LOCKED\",\"status\":\"ACTIVE\"}","[]"})
    void rejectsMassAssignmentAndInvalidStatus(String payload) throws Exception {
        change(token(admin),learner.getUserId(),payload).andExpect(status().isBadRequest());
        assertEquals("ACTIVE",state(learner));assertEquals(0,audits());
    }
    @Test void protectsAdminMixedAdminMissingAndNonActiveStates() throws Exception {
        var token=token(admin);
        var mixed=account("LEARNER");
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,role_id FROM roles WHERE name='ADMIN'",mixed.getUserId());
        change(token,admin.getUserId(),"{\"status\":\"LOCKED\"}").andExpect(status().isConflict());
        change(token,mixed.getUserId(),"{\"status\":\"LOCKED\"}").andExpect(status().isOk());
        change(token,mixed.getUserId(),"{\"status\":\"ACTIVE\"}").andExpect(status().isOk());
        change(token,UUID.randomUUID(),"{\"status\":\"LOCKED\"}").andExpect(status().isNotFound());
        for(var state:List.of("PENDING","DISABLED")) {
            jdbc.update("UPDATE users SET status=? WHERE user_id=?",state,learner.getUserId());
            change(token,learner.getUserId(),"{\"status\":\"ACTIVE\"}").andExpect(status().isConflict());
            assertEquals(state,state(learner));
        }
        assertEquals(2,audits());
    }
    @Test void statusAndAuditRollBackTogether() throws Exception {
        authenticate(token(admin));
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            service.changeStatus(learner.getUserId(),"LOCKED");users.flush();
            assertEquals("LOCKED",state(learner));assertEquals(1,audits());tx.setRollbackOnly();
        });
        assertEquals("ACTIVE",state(learner));assertEquals(0,audits());
    }
    @Test void failedAuditInsertRollsBackStatus() throws Exception {
        authenticate(token(admin));
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT tv1_audit_failure CHECK (actor_user_id <> '"+admin.getUserId()+"'::uuid)");
        try {
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,() -> service.changeStatus(learner.getUserId(),"LOCKED"));
            assertEquals("ACTIVE",state(learner)); assertEquals(0,audits());
        } finally { jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT tv1_audit_failure"); }
    }
    @Test void concurrentLoginWaitsForCommittedAdminLock() throws Exception {
        var token=token(admin); var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var writer=pool.submit(() -> {
                authenticate(token);
                try {
                    new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                        service.changeStatus(learner.getUserId(),"LOCKED");users.flush();entered.countDown();
                        try { assertTrue(release.await(10,TimeUnit.SECONDS)); } catch(InterruptedException e) { Thread.currentThread().interrupt();throw new AssertionError(e); }
                    });
                } finally { SecurityContextHolder.clearContext(); }
            });
            assertTrue(entered.await(10,TimeUnit.SECONDS));
            var login=pool.submit(() -> login(learner).andReturn().getResponse().getStatus());
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5); boolean blocked=false;
            while(System.nanoTime()<deadline) {
                blocked=jdbc.queryForObject("SELECT count(*)>0 FROM pg_stat_activity WHERE datname=current_database() AND cardinality(pg_blocking_pids(pid))>0",Boolean.class);
                if(blocked)break;Thread.sleep(20);
            }
            assertTrue(blocked,"Login must actually wait on PostgreSQL, not a sleep assumption");
            release.countDown();writer.get(10,TimeUnit.SECONDS);assertEquals(401,login.get(10,TimeUnit.SECONDS));
            assertEquals("LOCKED",state(learner));assertEquals(1,audits());
        } finally {release.countDown();pool.shutdownNow();}
    }
}
