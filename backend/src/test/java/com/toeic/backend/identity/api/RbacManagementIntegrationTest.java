package com.toeic.backend.identity.api;

import com.toeic.backend.identity.application.RbacMutationGuard;
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
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
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
class RbacManagementIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired RbacMutationGuard guard;
    @Autowired PlatformTransactionManager transactions;
    private final List<User> accounts=new ArrayList<>();
    private final List<Integer> customRoles=new ArrayList<>();
    private List<Map<String,Object>> grants,metadata;
    private User admin,peer,learner;
    private String token;
    private static String hash;
    private static final String PASSWORD="RbacTestOnly123!";
    @BeforeEach void setup() throws Exception {
        grants=jdbc.queryForList("SELECT * FROM role_permissions WHERE role_id IN (SELECT role_id FROM roles WHERE name IN ('ADMIN','TEACHER','LEARNER'))");
        metadata=jdbc.queryForList("SELECT role_id,display_name,description FROM roles WHERE name IN ('ADMIN','TEACHER','LEARNER')");
        if(hash==null)hash=encoder.encode(PASSWORD);
        admin=account("ADMIN");peer=account("ADMIN");learner=account("LEARNER");token=token(admin);
    }
    private User account(String role) {
        var u=new User("admin02-"+UUID.randomUUID()+"@example.test",hash,"RBAC QA");u.verifyEmail();u.addRole(roles.findByName(role).orElseThrow());
        users.saveAndFlush(u);accounts.add(u);return u;
    }
    @AfterEach void cleanup() {
        for(var u:accounts) {
            jdbc.update("DELETE FROM audit_logs WHERE actor_user_id=? OR entity_id=?",u.getUserId(),u.getUserId());
            jdbc.update("DELETE FROM auth_sessions WHERE user_id=?",u.getUserId());
            jdbc.update("DELETE FROM user_roles WHERE user_id=?",u.getUserId());
            jdbc.update("DELETE FROM users WHERE user_id=?",u.getUserId());
        }
        for(int id:customRoles) {jdbc.update("DELETE FROM role_permissions WHERE role_id=?",id);jdbc.update("DELETE FROM roles WHERE role_id=?",id);}
        jdbc.update("DELETE FROM role_permissions WHERE role_id IN (SELECT role_id FROM roles WHERE name IN ('ADMIN','TEACHER','LEARNER'))");
        for(var row:grants)jdbc.update("INSERT INTO role_permissions(role_id,permission_id) VALUES (?,?)",row.get("role_id"),row.get("permission_id"));
        for(var row:metadata)jdbc.update("UPDATE roles SET display_name=?,description=? WHERE role_id=?",row.get("display_name"),row.get("description"),row.get("role_id"));
    }
    private String token(User u) throws Exception {
        return tree(mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON).content(json.writeValueAsString(Map.of("email",u.getEmail(),"password",PASSWORD)))).andExpect(status().isOk())).get("accessToken").asString();
    }
    private JsonNode tree(ResultActions result) throws Exception {return json.readTree(result.andReturn().getResponse().getContentAsString());}
    private ResultActions request(String method,String path,Object body,String bearer) throws Exception {
        var builder=switch(method) {case "POST" -> post(path);case "PUT" -> put(path);case "PATCH" -> patch(path);case "DELETE" -> delete(path);default -> get(path);};
        if(bearer!=null)builder.header("Authorization","Bearer "+bearer);
        if(body!=null)builder.contentType(APPLICATION_JSON).content(body instanceof String s?s:json.writeValueAsString(body));
        return mvc.perform(builder);
    }
    private ResultActions req(String method,String path,Object body) throws Exception {return request(method,"/api/v1/admin"+path,body,token);}
    private int role(String name) {return jdbc.queryForObject("SELECT role_id FROM roles WHERE name=?",Integer.class,name);}
    private int permission(String code) {return jdbc.queryForObject("SELECT permission_id FROM permissions WHERE code=?",Integer.class,code);}
    private List<Integer> permissions(int id) {return jdbc.queryForList("SELECT permission_id FROM role_permissions WHERE role_id=? ORDER BY permission_id",Integer.class,id);}
    private int custom() throws Exception {
        int id=tree(req("POST","/roles",Map.of("name","QA_"+UUID.randomUUID().toString().replace("-", ""),"displayName","Vai trò kiểm thử")).andExpect(status().isCreated())).get("roleId").asInt();customRoles.add(id);return id;
    }
    private ResultActions grant(User u,List<Integer> ids) throws Exception {return req("PUT","/users/"+u.getUserId()+"/roles",Map.of("roleIds",ids));}
    private ResultActions rights(int id,List<Integer> ids) throws Exception {return req("PUT","/roles/"+id+"/permissions",Map.of("permissionIds",ids));}
    private int auditCount() {return jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE actor_user_id IN (?,?)",Integer.class,admin.getUserId(),peer.getUserId());}

    @Test void crudCatalogPaginationAndDeletionKeepIdentityAudit() throws Exception {
        int id=custom();int p=permission("content:read");
        req("GET","/permissions",null).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.code == 'rbac:manage')]").isNotEmpty());
        mvc.perform(get("/api/v1/admin/roles").header("Authorization","Bearer "+token).param("q","VAI TRÒ").param("size","1")).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        req("PATCH","/roles/"+id,Map.of("displayName","Tên mới","description","Mô tả")).andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("Tên mới"));
        rights(id,List.of(p)).andExpect(status().isOk());
        int before=auditCount();rights(id,List.of(p)).andExpect(status().isOk());assertEquals(before,auditCount());
        grant(learner,List.of(role("LEARNER"),id)).andExpect(status().isOk());
        req("DELETE","/roles/"+id,null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ROLE_IN_USE"));
        assertEquals(before+1,auditCount());
        grant(learner,List.of(role("LEARNER"))).andExpect(status().isOk());
        req("DELETE","/roles/"+id,null).andExpect(status().isNoContent());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM permissions WHERE permission_id=?",Integer.class,p));
        assertTrue(users.existsById(learner.getUserId()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE actor_user_id=? AND action='ROLE_DELETED' AND (details->'before'->>'roleId')::int=? AND details->'before'->>'displayName'='Tên mới'",Integer.class,admin.getUserId(),id));
        req("GET","/roles/"+id,null).andExpect(status().isNotFound());
        grant(learner,List.of()).andExpect(status().isOk()).andExpect(jsonPath("$.canChangeStatus").value(false));
        req("GET","/users/"+learner.getUserId()+"/roles",null).andExpect(status().isOk()).andExpect(jsonPath("$.canChangeStatus").value(false));
        req("PATCH","/users/"+learner.getUserId()+"/status",Map.of("status","LOCKED")).andExpect(status().isNotFound());
    }
    @Test void systemCodesProtectedButMetadataAndPermissionsEditable() throws Exception {
        for(String code:List.of("ADMIN","LEARNER","TEACHER")) {
            int id=role(code);
            req("PATCH","/roles/"+id,Map.of("name","NEW_"+code)).andExpect(status().isConflict());
            req("DELETE","/roles/"+id,null).andExpect(status().isConflict());
            req("PATCH","/roles/"+id,Map.of("displayName","Tên "+code,"description","Đã sửa")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value(code));
        }
        rights(role("TEACHER"),List.of(permission("user:read:self"))).andExpect(status().isOk());
        rights(role("ADMIN"),permissions(role("ADMIN"))).andExpect(status().isOk());
        req("POST","/permissions",Map.of("code","arbitrary")).andExpect(status().isMethodNotAllowed());
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"name\":null}","{\"name\":true}","{\"name\":\"ROLE_X\"}","{\"name\":\"A\"}","{\"name\":\"OK\",\"permissionIds\":[1]}","{\"name\":\"OK\",\"name\":\"OTHER\"}","{\"name\":\"OK\",\"displayName\":\" \"}","[]"})
    void strictCreateValidation(String body) throws Exception {req("POST","/roles",body).andExpect(status().isBadRequest());assertEquals(0,auditCount());}
    @ParameterizedTest @ValueSource(strings={"{}","{\"permissionIds\":null}","{\"permissionIds\":[1,1]}","{\"permissionIds\":[true]}","{\"permissionIds\":[1.0]}","{\"permissionIds\":[-1]}","{\"permissionIds\":[\"1\"]}","{\"permissionIds\":[],\"other\":1}"})
    void strictIdValidation(String body) throws Exception {req("PUT","/roles/"+role("ADMIN")+"/permissions",body).andExpect(status().isBadRequest());assertEquals(0,auditCount());}
    @Test void duplicatesUnknownReferencesAndInvalidQueries() throws Exception {
        req("POST","/roles",Map.of("name","admin")).andExpect(status().isConflict());
        req("GET","/roles/-1",null).andExpect(status().isNotFound());
        rights(role("TEACHER"),List.of(Integer.MAX_VALUE)).andExpect(status().isNotFound());
        grant(learner,List.of(Integer.MAX_VALUE)).andExpect(status().isNotFound());
        req("PUT","/users/"+UUID.randomUUID()+"/roles",Map.of("roleIds",List.of())).andExpect(status().isNotFound());
        for(String query:List.of("size=101","page=-1","other=1","page=1&page=2"))req("GET","/roles?"+query,null).andExpect(status().isBadRequest());
        assertEquals(0,auditCount());
    }
    @Test void authenticationAndLivePermissionAreCheckedAtServer() throws Exception {
        request("GET","/api/v1/admin/roles",null,null).andExpect(status().isUnauthorized());
        for(var u:List.of(learner,account("TEACHER")))request("GET","/api/v1/admin/roles",null,token(u)).andExpect(status().isForbidden());
        String same=token(peer);int backup=custom();
        rights(backup,List.copyOf(permissions(role("ADMIN")))).andExpect(status().isOk());
        grant(admin,List.of(role("ADMIN"),backup)).andExpect(status().isOk());
        var less=new ArrayList<>(permissions(role("ADMIN")));less.remove(Integer.valueOf(permission("rbac:manage")));
        rights(role("ADMIN"),less).andExpect(status().isOk());
        request("GET","/api/v1/admin/roles",null,same).andExpect(status().isForbidden());
        req("GET","/roles",null).andExpect(status().isOk());
        request("GET","/api/v1/users/me",null,same).andExpect(status().isOk()).andExpect(jsonPath("$.adminCapabilities.rbacManagement").value(false));
    }
    @Test void cannotEscalateSelfThroughRoleOrPermissionEdit() throws Exception {
        int extra=permission("practice:manage:self");assertFalse(permissions(role("ADMIN")).contains(extra));
        var more=new ArrayList<>(permissions(role("ADMIN")));more.add(extra);
        rights(role("ADMIN"),more).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SELF_PRIVILEGE_ESCALATION"));
        int id=custom();rights(id,List.of(extra)).andExpect(status().isOk());int before=auditCount();
        grant(admin,List.of(role("ADMIN"),id)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SELF_PRIVILEGE_ESCALATION"));
        assertEquals(before,auditCount());assertFalse(permissions(role("ADMIN")).contains(extra));
        grant(learner,List.of(role("LEARNER"),id)).andExpect(status().isOk());
    }
    @Test void activeAdminGrantRevocationAndSameSessionAuthorityRefresh() throws Exception {
        String same=token(learner);
        grant(learner,List.of(role("LEARNER"),role("ADMIN"))).andExpect(status().isOk());
        request("GET","/api/v1/admin/roles",null,same).andExpect(status().isOk());
        grant(learner,List.of(role("LEARNER"))).andExpect(status().isOk());
        request("GET","/api/v1/admin/roles",null,same).andExpect(status().isForbidden());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM auth_sessions WHERE user_id=? AND revoked_at IS NOT NULL",Integer.class,learner.getUserId()));
        for(String status:List.of("LOCKED","PENDING","DISABLED")) {
            jdbc.update("UPDATE users SET status=? WHERE user_id=?",status,learner.getUserId());
            grant(learner,List.of(role("ADMIN"))).andExpect(status().isConflict());
        }
    }
    @Test void lastManagerAndSelfLockoutCoverRolesPermissionsAndAdmin01Status() throws Exception {
        grant(admin,List.of(role("LEARNER"))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SELF_LOCKOUT"));
        grant(peer,List.of(role("LEARNER"))).andExpect(status().isOk());
        grant(admin,List.of()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_RBAC_ADMIN"));
        rights(role("ADMIN"),List.of()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_RBAC_ADMIN"));
        req("PATCH","/users/"+admin.getUserId()+"/status",Map.of("status","LOCKED")).andExpect(status().isConflict());
        assertEquals(1,guard.activeManagers());assertEquals(1,auditCount());
    }
    @Test void lockingOtherAdminUsesExistingSessionPolicyAndNeedsBothPermissions() throws Exception {
        String same=token(peer);
        req("PATCH","/users/"+peer.getUserId()+"/status",Map.of("status","LOCKED")).andExpect(status().isOk());
        request("GET","/api/v1/users/me",null,same).andExpect(status().isUnauthorized());
        req("PATCH","/users/"+peer.getUserId()+"/status",Map.of("status","ACTIVE")).andExpect(status().isOk());
        request("GET","/api/v1/admin/roles",null,same).andExpect(status().isOk());
        var less=new ArrayList<>(permissions(role("ADMIN")));less.remove(Integer.valueOf(permission("rbac:manage")));
        int backup=custom();rights(backup,List.of(permission("rbac:manage"))).andExpect(status().isOk());
        grant(admin,List.of(role("ADMIN"),backup)).andExpect(status().isOk());rights(role("ADMIN"),less).andExpect(status().isOk());
        request("PATCH","/api/v1/admin/users/"+admin.getUserId()+"/status",Map.of("status","LOCKED"),same).andExpect(status().isForbidden());
    }
    @Test void failedAuditRollsBackRolePermissionAndUserRoleMutations() throws Exception {
        int id=custom();int before=auditCount();
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT admin02_audit_failure CHECK (actor_user_id <> '"+admin.getUserId()+"'::uuid OR action='ROLE_CREATED')");
        try {
            req("PATCH","/roles/"+id,Map.of("displayName","Must roll back")).andExpect(status().isInternalServerError());
            rights(id,List.of(permission("content:read"))).andExpect(status().isInternalServerError());
            grant(learner,List.of(role("ADMIN"))).andExpect(status().isInternalServerError());
            req("DELETE","/roles/"+id,null).andExpect(status().isInternalServerError());
            assertEquals("Vai trò kiểm thử",jdbc.queryForObject("SELECT display_name FROM roles WHERE role_id=?",String.class,id));
            assertTrue(permissions(id).isEmpty());assertEquals(before,auditCount());
            assertEquals(List.of(role("LEARNER")),jdbc.queryForList("SELECT role_id FROM user_roles WHERE user_id=?",Integer.class,learner.getUserId()));
        } finally {jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT admin02_audit_failure");}
    }
    @Test void concurrentAdminsCannotRemoveEachOtherAfterWaitingForSameGuard() throws Exception {
        String other=token(peer);var pool=Executors.newFixedThreadPool(2);var futures=new ArrayList<Future<Integer>>();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.query("SELECT pg_advisory_xact_lock(?)",rs -> {},73532002L);
                futures.add(pool.submit(() -> request("PUT","/api/v1/admin/users/"+peer.getUserId()+"/roles",Map.of("roleIds",List.of(role("LEARNER"))),token).andReturn().getResponse().getStatus()));
                futures.add(pool.submit(() -> request("PUT","/api/v1/admin/users/"+admin.getUserId()+"/roles",Map.of("roleIds",List.of(role("LEARNER"))),other).andReturn().getResponse().getStatus()));
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);boolean waiting=false;
                while(System.nanoTime()<deadline) {
                    jdbc.query("SELECT pg_stat_clear_snapshot()",rs -> {});
                    waiting=jdbc.queryForObject("SELECT count(*)>=2 FROM pg_stat_activity WHERE datname=current_database() AND wait_event='advisory'",Boolean.class);
                    if(waiting)break;try {Thread.sleep(20);}catch(InterruptedException e){throw new AssertionError(e);}
                }
                assertTrue(waiting,"Both mutations must actually wait on the shared PostgreSQL lock");
            });
            var statuses=new ArrayList<Integer>();for(var f:futures)statuses.add(f.get(10,TimeUnit.SECONDS));Collections.sort(statuses);
            assertEquals(List.of(200,403),statuses);assertEquals(1,guard.activeManagers());assertEquals(1,auditCount());
        } finally {pool.shutdownNow();}
    }
    @Test void concurrentAdmin01LockAndPermissionChangeUseSameInvariantLock() throws Exception {
        int backup=custom();rights(backup,permissions(role("ADMIN"))).andExpect(status().isOk());
        grant(admin,List.of(role("ADMIN"),backup)).andExpect(status().isOk());
        var less=new ArrayList<>(permissions(role("ADMIN")));less.remove(Integer.valueOf(permission("user:manage")));
        String other=token(peer);int before=auditCount();var pool=Executors.newFixedThreadPool(2);var futures=new ArrayList<Future<Integer>>();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.query("SELECT pg_advisory_xact_lock(?)",rs -> {},73532002L);
                futures.add(pool.submit(() -> request("PUT","/api/v1/admin/roles/"+role("ADMIN")+"/permissions",Map.of("permissionIds",less),token).andReturn().getResponse().getStatus()));
                futures.add(pool.submit(() -> request("PATCH","/api/v1/admin/users/"+admin.getUserId()+"/status",Map.of("status","LOCKED"),other).andReturn().getResponse().getStatus()));
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);boolean waiting=false;
                while(System.nanoTime()<deadline) {
                    jdbc.query("SELECT pg_stat_clear_snapshot()",rs -> {});
                    waiting=jdbc.queryForObject("SELECT count(*)>=2 FROM pg_stat_activity WHERE datname=current_database() AND wait_event='advisory'",Boolean.class);
                    if(waiting)break;try {Thread.sleep(20);}catch(InterruptedException e){throw new AssertionError(e);}
                }
                assertTrue(waiting,"ADMIN-01 status and RBAC writes must share the PostgreSQL guard");
            });
            var statuses=new ArrayList<Integer>();for(var f:futures)statuses.add(f.get(10,TimeUnit.SECONDS));Collections.sort(statuses);
            assertEquals(List.of(200,403),statuses);assertEquals(1,guard.activeManagers());assertEquals(before+1,auditCount());
        } finally {pool.shutdownNow();}
    }
    @Test void failedCreationAuditLeavesNoRole() throws Exception {
        String code="FAIL_"+UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT admin02_create_audit_failure CHECK (actor_user_id <> '"+admin.getUserId()+"'::uuid)");
        try {
            req("POST","/roles",Map.of("name",code)).andExpect(status().isInternalServerError());
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM roles WHERE name=?",Integer.class,code));assertEquals(0,auditCount());
        } finally {jdbc.execute("ALTER TABLE audit_logs DROP CONSTRAINT admin02_create_audit_failure");}
    }
}
