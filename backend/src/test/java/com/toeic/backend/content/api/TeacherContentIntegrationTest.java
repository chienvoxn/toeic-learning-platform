package com.toeic.backend.content.api;

import com.toeic.backend.content.application.*;
import com.toeic.backend.identity.application.SessionPrincipal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"debug=false","logging.level.org.hibernate.orm.jdbc.error=OFF"})
@AutoConfigureMockMvc
@Sql(scripts={"/fixtures/content-read.sql","/fixtures/content-bookmark.sql","/fixtures/content03-manager.sql"},
        config=@SqlConfig(transactionMode=SqlConfig.TransactionMode.ISOLATED,encoding="UTF-8"))
@Sql(scripts={"/fixtures/content03-manager-cleanup.sql","/fixtures/content-bookmark-cleanup.sql","/fixtures/content-read-cleanup.sql"},
        executionPhase=Sql.ExecutionPhase.AFTER_TEST_METHOD,
        config=@SqlConfig(transactionMode=SqlConfig.TransactionMode.ISOLATED))
class TeacherContentIntegrationTest {
    private static final UUID ACTOR=UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OTHER=UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final String ROOT="/api/v1/teacher/contents";
    private static final String PASSWORD="Content03Fixture123!";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper mapper;
    @Autowired ContentManagementService service;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach void setup() {
        SecurityContextHolder.clearContext();
        var hash=encoder.encode(PASSWORD);
        jdbc.update("update users set password_hash=? where user_id in (?,?)",hash,ACTOR,OTHER);
    }
    @AfterEach void clearActor() { SecurityContextHolder.clearContext(); }
    private static UUID id(int n) { return UUID.fromString("20000000-0000-0000-0000-%012d".formatted(n)); }
    private static UUID version(int n) { return UUID.fromString("30000000-0000-0000-0000-%012d".formatted(n)); }
    private String token(UUID actor) throws Exception {
        var email=actor.equals(ACTOR)?"content-fixture@example.invalid":"bookmark-fixture@example.invalid";
        var json=mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email",email,"password",PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("accessToken").asString();
    }
    private ResultActions call(MockHttpServletRequestBuilder request,String token) throws Exception {
        if (token!=null) { request.header("Authorization","Bearer "+token); }
        return mvc.perform(request);
    }
    private JsonNode json(ResultActions action) throws Exception {
        return mapper.readTree(action.andReturn().getResponse().getContentAsString());
    }
    private JsonNode create(String token,String category) throws Exception {
        return json(call(post(ROOT).contentType(APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                "title","New "+category,"summary","Summary","body","Body","categoryCode",category,"difficulty","EASY"))),token)
                .andExpect(status().isCreated()));
    }
    private String path(JsonNode detail) { return ROOT+"/"+detail.get("content").get("contentId").asString(); }
    private void role(UUID actor,String role) {
        jdbc.update("delete from user_roles where user_id=?",actor);
        jdbc.update("insert into user_roles(user_id,role_id) select ?,role_id from roles where name=?",actor,role);
    }
    private int count(String sql,Object... parameters) { return jdbc.queryForObject(sql,Integer.class,parameters); }
    private record Snapshot(Map<String,Object> content, List<Map<String,Object>> versions, List<Map<String,Object>> assets) { }
    private Snapshot snapshot(UUID contentId) {
        return new Snapshot(jdbc.queryForMap("select * from learning_contents where content_id=?",contentId),
                jdbc.queryForList("select * from learning_content_versions where content_id=? order by version_number",contentId),
                jdbc.queryForList("select r.* from learning_content_version_resources r join learning_content_versions v using(content_version_id) where v.content_id=? order by r.content_version_id,r.resource_id",contentId));
    }
    private void authenticate(UUID actor,String... grants) {
        var context=SecurityContextHolder.createEmptyContext();
        // Only used for direct service boundary/transaction tests; HTTP tests use real DB sessions.
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new SessionPrincipal(UUID.randomUUID(),actor,List.of(),List.of(grants)),null,
                Arrays.stream(grants).map(SimpleGrantedAuthority::new).toList()));
        SecurityContextHolder.setContext(context);
    }
    private void safeJson(JsonNode tree) {
        var value=tree.toString();
        assertFalse(value.matches("(?is).*\"(passwordHash|password|accessToken|storageUrl|storage_url|transcript|authorId|createdBy)\"\\s*:.*"));
        assertFalse(value.contains("s3://") || value.contains("LEAK_STORAGE") || value.contains("LEAK_TRANSCRIPT"));
    }

    @ParameterizedTest @ValueSource(strings={"GRAMMAR","VOCAB","LISTENING","READING","STRATEGIES","DOCS"})
    void createReadUpdateArchiveForEveryProductCategory(String category) throws Exception {
        var token=token(ACTOR);
        var created=create(token,category);
        var content=created.get("content");
        var contentId=UUID.fromString(content.get("contentId").asString());
        assertEquals("DRAFT",content.get("status").asString());
        assertEquals("PRIVATE",content.get("visibility").asString());
        assertTrue(content.get("publishedVersionId").isNull());
        assertEquals(category,content.get("category").get("code").asString());
        assertEquals(1,created.get("versions").size());
        assertEquals("DRAFT",created.get("versions").get(0).get("status").asString());
        assertEquals(ACTOR,jdbc.queryForObject("select author_id from learning_contents where content_id=?",UUID.class,contentId));
        assertEquals(ACTOR,jdbc.queryForObject("select created_by from learning_content_versions where content_id=?",UUID.class,contentId));
        var updated=json(call(patch(path(created)).contentType(APPLICATION_JSON).content("{\"title\":\" Edited \",\"summary\":null,\"difficulty\":null}"),token)
                .andExpect(status().isOk()));
        assertEquals("Edited",updated.get("content").get("title").asString());
        assertTrue(updated.get("content").get("difficulty").isNull());
        assertTrue(updated.get("versions").get(0).get("summary").isNull());
        assertEquals("Body",updated.get("versions").get(0).get("body").asString());
        call(get(path(created)),token).andExpect(status().isOk());
        call(get("/api/v1/contents/"+contentId),token).andExpect(status().isNotFound());
        call(get("/api/v1/contents").param("q","Edited"),token).andExpect(jsonPath("$.totalElements").value(0));
        call(delete(path(created)),token).andExpect(status().isNoContent());
        call(delete(path(created)),token).andExpect(status().isNoContent());
        var archived=json(call(get(path(created)),token).andExpect(status().isOk()));
        assertEquals("ARCHIVED",archived.get("content").get("status").asString());
        assertEquals("DRAFT",archived.get("versions").get(0).get("status").asString());
        call(patch(path(created)).contentType(APPLICATION_JSON).content("{\"body\":\"blocked\"}"),token)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTENT_STATE_CONFLICT"));
        assertEquals(1,count("select count(*) from learning_content_versions where content_id=?",contentId));
        safeJson(archived);
    }

    @ParameterizedTest @ValueSource(strings={"owner","authorId","author_id","creator","createdBy","status","visibility",
            "publishedVersionId","currentPublishedVersionId","contentId","versionNumber","createdAt","permission","role",
            "assets","media","resourceIds","storageUrl","url","path"})
    void rejectsSystemFieldsUnsupportedAliasesAndNullAssets(String field) throws Exception {
        var token=token(ACTOR);
        var body="{\"title\":\"x\",\"body\":\"x\",\"categoryCode\":\"GRAMMAR\",\""+field+"\":null}";
        call(post(ROOT).contentType(APPLICATION_JSON).content(body),token).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        call(patch(ROOT+"/"+id(1)).contentType(APPLICATION_JSON).content("{\""+field+"\":null}"),token)
                .andExpect(status().isBadRequest());
        assertEquals(11,count("select count(*) from learning_contents where author_id in (?,?)",ACTOR,OTHER));
    }

    @ParameterizedTest @ValueSource(strings={"{}","null","[]","{\"title\":null}","{\"title\":\"   \"}",
            "{\"body\":null}","{\"body\":42}","{\"summary\":false}","{\"categoryCode\":\"OTHER\"}",
            "{\"categoryCode\":null}","{\"categoryCode\":900001}","{\"difficulty\":\"\"}",
            "{\"title\":\"first\",\"title\":\"second\"}","{\"body\":\"\\u0000\"}"})
    void invalidPatchReturnsTheExistingErrorEnvelopeWithoutMutation(String payload) throws Exception {
        var original=jdbc.queryForObject("select title from learning_content_versions where content_version_id=?",String.class,version(102));
        call(patch(ROOT+"/"+id(1)).contentType(APPLICATION_JSON).content(payload),token(ACTOR))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.traceId").isString()).andExpect(jsonPath("$.details").isArray());
        assertEquals(original,jdbc.queryForObject("select title from learning_content_versions where content_version_id=?",String.class,version(102)));
    }

    @Test void createRequiredFieldsAndTextBoundariesAndPartialMetadataAreValidated() throws Exception {
        var token=token(ACTOR);
        for (var payload:List.of("{\"title\":\"x\"}","{\"body\":\"\",\"categoryCode\":\"GRAMMAR\"}",
                "{\"title\":\"x\",\"body\":\"\"}")) {
            call(post(ROOT).contentType(APPLICATION_JSON).content(payload),token).andExpect(status().isBadRequest());
        }
        var boundary=Map.of("title","x".repeat(255),"body","","categoryCode","DOCS","difficulty","d".repeat(20));
        var created=json(call(post(ROOT).contentType(APPLICATION_JSON).content(mapper.writeValueAsString(boundary)),token)
                .andExpect(status().isCreated()));
        call(patch(path(created)).contentType(APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("title","x".repeat(256)))),token)
                .andExpect(status().isBadRequest());
        call(patch(path(created)).contentType(APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("difficulty","x".repeat(21)))),token)
                .andExpect(status().isBadRequest());
        var result=json(call(patch(path(created)).contentType(APPLICATION_JSON).content("{\"categoryCode\":\"VOCAB\",\"body\":\"Revised\"}"),token)
                .andExpect(status().isOk()));
        assertEquals("VOCAB",result.get("content").get("category").get("code").asString());
        assertEquals("d".repeat(20),result.get("content").get("difficulty").asString());
        assertEquals("Revised",result.get("versions").get(0).get("body").asString());
    }

    @Test void authenticationAndEndpointPermissionApplyToEveryCrudMethod() throws Exception {
        for (var token:Arrays.asList(null,"invalid",token(OTHER))) {
            var expected=token==null || token.equals("invalid")?401:403;
            for (var request:List.of(get(ROOT),get(ROOT+"/"+id(1)),post(ROOT).contentType(APPLICATION_JSON).content("{}"),
                    patch(ROOT+"/"+id(1)).contentType(APPLICATION_JSON).content("{}"),delete(ROOT+"/"+id(1)))) {
                assertEquals(expected,call(request,token).andReturn().getResponse().getStatus());
            }
        }
    }

    @Test void teacherScopeIsAppliedToDetailMutationListAndDatabaseCount() throws Exception {
        var token=token(ACTOR);
        for (var request:List.of(get(ROOT+"/"+id(2)),patch(ROOT+"/"+id(2)).contentType(APPLICATION_JSON).content("{\"title\":\"theft\"}"),
                delete(ROOT+"/"+id(2)),get(ROOT+"/"+UUID.randomUUID()),delete(ROOT+"/"+UUID.randomUUID()))) {
            call(request,token).andExpect(status().isNotFound());
        }
        var list=json(call(get(ROOT).param("size","1").param("sort","contentId,asc"),token).andExpect(status().isOk()));
        assertEquals(10,list.get("totalElements").asInt());
        assertEquals(10,list.get("totalPages").asInt());
        assertEquals(id(1).toString(),list.get("content").get(0).get("contentId").asString());
        call(get(ROOT).param("page","1").param("size","1").param("sort","contentId,asc"),token)
                .andExpect(jsonPath("$.content[0].contentId").value(id(3).toString()));
        // Equal updated_at fixtures verify the secondary contentId ordering at a page boundary.
        call(get(ROOT).param("size","1"),token).andExpect(jsonPath("$.content[0].contentId").value(id(1).toString()));
        call(get(ROOT).param("page","1").param("size","1"),token).andExpect(jsonPath("$.content[0].contentId").value(id(3).toString()));
        call(get(ROOT).param("size","100"),token).andExpect(jsonPath("$.content.length()").value(10));
        call(get(ROOT).param("categoryCode","READING"),token).andExpect(jsonPath("$.totalElements").value(0));
        call(get(ROOT).param("status","ARCHIVED"),token).andExpect(jsonPath("$.totalElements").value(1));
        safeJson(list);
    }

    @Test void adminPermissionManagesOthersAndPermissionsAreNotHardCodedToRoles() throws Exception {
        role(ACTOR,"ADMIN");
        var token=token(ACTOR);
        call(get(ROOT),token).andExpect(jsonPath("$.totalElements").value(11));
        call(get(ROOT+"/"+id(2)),token).andExpect(status().isOk());
        var updated=json(call(patch(ROOT+"/"+id(2)).contentType(APPLICATION_JSON).content("{\"title\":\"Admin draft\"}"),token)
                .andExpect(status().isOk()));
        assertEquals("Admin draft",updated.get("content").get("title").asString());
        var draft=UUID.fromString(updated.get("content").get("workingDraftVersionId").asString());
        assertEquals(ACTOR,jdbc.queryForObject("select created_by from learning_content_versions where content_version_id=?",UUID.class,draft));
        assertEquals(OTHER,jdbc.queryForObject("select author_id from learning_contents where content_id=?",UUID.class,id(2)));
        role(ACTOR,"CONTENT_READ_FIXTURE");
        jdbc.update("insert into role_permissions(role_id,permission_id) select 900003,permission_id from permissions where code='content:manage:any'");
        // Same bearer sees permission changes on the next request, even without a Teacher/Admin role.
        call(get(ROOT+"/"+id(2)),token).andExpect(status().isOk());
        call(delete(ROOT+"/"+id(2)),token).andExpect(status().isNoContent());
        role(ACTOR,"LEARNER");
        call(get(ROOT),token).andExpect(status().isForbidden());
    }

    @Test void migrationGrantsOnlyTheApprovedDefaultsAndSeedsStableCategoryCodes() {
        assertEquals(2,count("select count(*) from role_permissions rp join roles r using(role_id) join permissions p using(permission_id) where r.name in ('TEACHER','ADMIN') and p.code='content:manage:self'"));
        assertEquals(1,count("select count(*) from role_permissions rp join roles r using(role_id) join permissions p using(permission_id) where r.name='ADMIN' and p.code='content:manage:any'"));
        assertEquals(0,count("select count(*) from role_permissions rp join roles r using(role_id) join permissions p using(permission_id) where (r.name='TEACHER' and p.code='content:manage:any') or (r.name='LEARNER' and p.code like 'content:manage:%')"));
        for (var code:ContentEdit.CATEGORIES) { assertEquals(1,count("select count(*) from content_categories where code=?",code)); }
    }

    @Test void publishedEditReusesWorkingDraftAndNeverChangesPublishedTextAssetsOrPointer() throws Exception {
        var token=token(ACTOR);
        var result=json(call(patch(ROOT+"/"+id(1)).contentType(APPLICATION_JSON).content("{\"title\":\"Working revision\",\"body\":\"Draft only\"}"),token)
                .andExpect(status().isOk()));
        assertEquals(version(102).toString(),result.get("content").get("workingDraftVersionId").asString());
        assertEquals(version(1).toString(),result.get("content").get("publishedVersionId").asString());
        assertEquals(3,result.get("versions").size());
        assertEquals("Alpha published",jdbc.queryForObject("select title from learning_content_versions where content_version_id=?",String.class,version(1)));
        assertEquals(3,count("select count(*) from learning_content_version_resources where content_version_id=?",version(1)));
        assertEquals(1,count("select count(*) from learning_content_version_resources where content_version_id=?",version(102)));
        call(get("/api/v1/contents/"+id(1)),null).andExpect(jsonPath("$.title").value("Alpha published"));
        call(get("/api/v1/contents").param("q","Working revision"),null).andExpect(jsonPath("$.totalElements").value(0));
        safeJson(result);
    }

    @Test void publishedWithoutDraftCreatesOneFromTheExactPointerAndReusesItOnNextPatch() throws Exception {
        role(ACTOR,"ADMIN");
        var token=token(ACTOR);
        jdbc.update("insert into learning_content_versions(content_version_id,content_id,version_number,title,body,status,published_at,created_by,created_at,updated_at) values (?,?,7,'Non-current published','Not the pointer text','PUBLISHED',now(),?,now(),now())",UUID.randomUUID(),id(2),OTHER);
        var first=json(call(patch(ROOT+"/"+id(2)).contentType(APPLICATION_JSON).content("{\"summary\":null}"),token)
                .andExpect(status().isOk()));
        var draftId=first.get("content").get("workingDraftVersionId").asString();
        var draft=first.get("versions").get(2);
        assertEquals(8,draft.get("versionNumber").asInt());
        assertEquals("Beta published",draft.get("title").asString());
        assertEquals("Published fixture body",draft.get("body").asString());
        assertTrue(draft.get("summary").isNull());
        assertEquals("DRAFT",draft.get("status").asString());
        assertEquals(0,draft.get("media").size());
        var second=json(call(patch(ROOT+"/"+id(2)).contentType(APPLICATION_JSON).content("{\"title\":\"Second edit\"}"),token)
                .andExpect(status().isOk()));
        assertEquals(draftId,second.get("content").get("workingDraftVersionId").asString());
        assertEquals(3,second.get("versions").size());
        assertEquals(version(2).toString(),second.get("content").get("publishedVersionId").asString());
        call(patch(ROOT+"/"+id(2)).contentType(APPLICATION_JSON).content("{\"categoryCode\":\"DOCS\"}"),token)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTENT_METADATA_IMMUTABLE"));
    }

    @Test void managementMediaAreVersionSpecificVisibilityFilteredAndNeverStorageUrls() throws Exception {
        var token=token(ACTOR);
        var detail=json(call(get(ROOT+"/"+id(1)),token).andExpect(status().isOk()));
        assertEquals(2,detail.get("versions").get(0).get("media").size());
        assertEquals(1,detail.get("versions").get(1).get("media").size());
        assertEquals(1,detail.get("versions").get(2).get("media").size());
        assertEquals("40000000-0000-0000-0000-000000000004",detail.get("versions").get(2).get("media").get(0).get("resourceId").asString());
        safeJson(detail);
        role(ACTOR,"CONTENT_READ_FIXTURE");
        jdbc.update("insert into role_permissions(role_id,permission_id) select 900003,permission_id from permissions where code='content:manage:self'");
        var noRead=json(call(get(ROOT+"/"+id(1)),token).andExpect(status().isOk()));
        assertEquals(1,noRead.get("versions").get(0).get("media").size());
        assertFalse(noRead.toString().contains("40000000-0000-0000-0000-000000000002"));
        assertFalse(detail.toString().contains("40000000-0000-0000-0000-000000000003"));
        for (var resource:List.of(UUID.randomUUID().toString(),"40000000-0000-0000-0000-000000000003")) {
            call(patch(ROOT+"/"+id(1)).contentType(APPLICATION_JSON).content("{\"resourceIds\":[\""+resource+"\"]}"),token)
                    .andExpect(status().isBadRequest());
        }
    }

    @Test void archiveHidesReadSearchBookmarkAndHistoryDataAndCountsButRetainsReferences() throws Exception {
        jdbc.update("insert into user_roles(user_id,role_id) select ?,role_id from roles where name='LEARNER'",ACTOR);
        var token=token(ACTOR);
        call(post("/api/v1/contents/"+id(1)+"/bookmarks"),token).andExpect(status().isCreated());
        call(post("/api/v1/contents/"+id(1)+"/history"),token).andExpect(status().isNoContent());
        call(get("/api/v1/users/me/bookmarks"),token).andExpect(jsonPath("$.totalElements").value(1));
        call(get("/api/v1/users/me/content-history"),token).andExpect(jsonPath("$.totalElements").value(1));
        call(delete(ROOT+"/"+id(1)),token).andExpect(status().isNoContent());
        call(delete(ROOT+"/"+id(1)),token).andExpect(status().isNoContent());
        call(get("/api/v1/contents/"+id(1)),token).andExpect(status().isNotFound());
        call(get("/api/v1/contents").param("q","Alpha"),token).andExpect(jsonPath("$.totalElements").value(0));
        call(get("/api/v1/contents/"+id(1)),null).andExpect(status().isNotFound());
        call(get("/api/v1/contents").param("q","Alpha"),null).andExpect(jsonPath("$.totalElements").value(0));
        call(post("/api/v1/contents/"+id(1)+"/bookmarks"),token).andExpect(status().isNotFound());
        call(post("/api/v1/contents/"+id(1)+"/history"),token).andExpect(status().isNotFound());
        for (var path:List.of("/api/v1/users/me/bookmarks","/api/v1/users/me/content-history")) {
            call(get(path),token).andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.content.length()").value(0));
        }
        assertEquals(1,count("select count(*) from bookmarks where user_id=? and content_id=?",ACTOR,id(1)));
        assertEquals(version(1),jdbc.queryForObject("select content_version_id from learning_content_history where user_id=? and content_id=?",UUID.class,ACTOR,id(1)));
        assertEquals(version(1),jdbc.queryForObject("select current_published_version_id from learning_contents where content_id=?",UUID.class,id(1)));
        assertEquals(3,count("select count(*) from learning_content_versions where content_id=?",id(1)));
        call(delete("/api/v1/contents/"+id(1)+"/bookmarks"),token).andExpect(status().isNoContent());
        call(get(ROOT+"/"+id(1)),token).andExpect(status().isOk());
        call(patch(ROOT+"/"+id(1)).contentType(APPLICATION_JSON).content("{\"title\":\"blocked\"}"),token).andExpect(status().isConflict());
    }

    @ParameterizedTest @ValueSource(strings={"size=101","size=0","page=-1","page=999999999999","sort=title,asc",
            "sort=updatedAt,sideways","status=HIDDEN","categoryCode=UNKNOWN","owner=other","size=1&size=2"})
    void invalidManagementQueryIsRejected(String query) throws Exception {
        var request=get(ROOT);
        for (var part:query.split("&")) { var pair=part.split("=",2); request.param(pair[0],pair[1]); }
        call(request,token(ACTOR)).andExpect(status().isBadRequest());
    }

    @Test void invalidPathsAndExtraParametersOrDeletePayloadAreRejected() throws Exception {
        var token=token(ACTOR);
        call(get(ROOT+"/bad-id"),token).andExpect(status().isBadRequest());
        call(get(ROOT+"/"+id(1)).param("versionId",version(102).toString()),token).andExpect(status().isBadRequest());
        call(delete(ROOT+"/"+id(1)).contentType(APPLICATION_JSON).content("{}"),token).andExpect(status().isBadRequest());
        call(delete(ROOT+"/"+id(1)).param("owner",OTHER.toString()),token).andExpect(status().isBadRequest());
        call(post(ROOT).param("owner",OTHER.toString()).contentType(APPLICATION_JSON).content("{}"),token).andExpect(status().isBadRequest());
    }

    @Test void serviceRequiresPermissionAndTrustedActorAndKeepsOwnerScope() {
        var edit=new ContentEdit(Set.of("title"),"Service edit",null,null,null,null);
        authenticate(ACTOR,"content:manage:self");
        assertThrows(AccessDeniedException.class,()->service.get(OTHER,id(2)));
        assertThrows(ContentException.class,()->service.get(ACTOR,id(2)));
        authenticate(ACTOR,"content:read");
        assertThrows(AccessDeniedException.class,()->service.update(ACTOR,id(1),edit));
        SecurityContextHolder.clearContext();
        assertThrows(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class,()->service.archive(ACTOR,id(1)));
    }

    @Test void failureAfterVersionCreationRollsBackTheEntireAuthoringTransaction() {
        authenticate(ACTOR,"content:manage:any");
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).executeWithoutResult(tx->{
            service.update(ACTOR,id(2),new ContentEdit(Set.of("title"),"Rollback",null,null,null,null));
            throw new IllegalStateException("Simulated failure after draft creation");
        }));
        assertEquals(1,count("select count(*) from learning_content_versions where content_id=?",id(2)));
        assertEquals(version(2),jdbc.queryForObject("select current_published_version_id from learning_contents where content_id=?",UUID.class,id(2)));
        int before=count("select count(*) from learning_contents where author_id=?",ACTOR);
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).executeWithoutResult(tx->{
            service.create(ACTOR,new ContentEdit(Set.of("title","body","categoryCode"),"Rollback",null,"","GRAMMAR",null));
            throw new IllegalStateException("Simulated failure after content creation");
        }));
        assertEquals(before,count("select count(*) from learning_contents where author_id=?",ACTOR));
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).executeWithoutResult(tx->{
            service.archive(ACTOR,id(1));
            throw new IllegalStateException("Simulated failure after archive");
        }));
        assertEquals("PUBLISHED",jdbc.queryForObject("select status from learning_contents where content_id=?",String.class,id(1)));
    }

    @Test void concurrentPublishedPatchesCreateOnlyOneWorkingDraftAndPreserveBothPartialEdits() throws Exception {
        role(ACTOR,"ADMIN");
        var token=token(ACTOR);
        var pool=Executors.newFixedThreadPool(2);
        var start=new CountDownLatch(1);
        try {
            var title=pool.submit(()->{ start.await(); return call(patch(ROOT+"/"+id(2)).contentType(APPLICATION_JSON).content("{\"title\":\"Concurrent title\"}"),token).andReturn().getResponse().getStatus(); });
            var body=pool.submit(()->{ start.await(); return call(patch(ROOT+"/"+id(2)).contentType(APPLICATION_JSON).content("{\"body\":\"Concurrent body\"}"),token).andReturn().getResponse().getStatus(); });
            start.countDown();
            assertEquals(200,title.get(15,TimeUnit.SECONDS));
            assertEquals(200,body.get(15,TimeUnit.SECONDS));
            assertEquals(1,count("select count(*) from learning_content_versions where content_id=? and status='DRAFT'",id(2)));
            var draft=jdbc.queryForMap("select title,body,version_number from learning_content_versions where content_id=? and status='DRAFT'",id(2));
            assertEquals("Concurrent title",draft.get("title"));
            assertEquals("Concurrent body",draft.get("body"));
            assertEquals(2,draft.get("version_number"));
            assertEquals("Beta published",jdbc.queryForObject("select title from learning_content_versions where content_version_id=?",String.class,version(2)));
        } finally { pool.shutdownNow(); }
    }

    private void awaitDatabaseLock() {
        var limit=System.nanoTime()+Duration.ofSeconds(8).toNanos();
        while (System.nanoTime()<limit) {
            // pg_locks/pg_blocking_pids are live; pg_stat_activity can cache a snapshot inside this transaction.
            if (count("select count(*) from pg_locks l where not l.granted and pg_backend_pid()=any(pg_blocking_pids(l.pid))")>0) { return; }
            LockSupport.parkNanos(Duration.ofMillis(20).toNanos());
        }
        fail("Expected the API to wait for the parent content database lock");
    }

    @Test void archiveCommitWinsOverABlockedPatchWithoutCreatingADraft() throws Exception {
        role(ACTOR,"ADMIN");
        var token=token(ACTOR);
        var pool=Executors.newSingleThreadExecutor();
        var future=new java.util.concurrent.atomic.AtomicReference<Future<Integer>>();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx->{
                jdbc.queryForObject("select content_id from learning_contents where content_id=? for update",UUID.class,id(2));
                future.set(pool.submit(()->call(patch(ROOT+"/"+id(2)).contentType(APPLICATION_JSON).content("{\"title\":\"Must not commit\"}"),token).andReturn().getResponse().getStatus()));
                awaitDatabaseLock();
                jdbc.update("update learning_contents set status='ARCHIVED' where content_id=?",id(2));
            });
            assertEquals(409,future.get().get(15,TimeUnit.SECONDS));
            assertEquals(1,count("select count(*) from learning_content_versions where content_id=?",id(2)));
            assertEquals(version(2),jdbc.queryForObject("select current_published_version_id from learning_contents where content_id=?",UUID.class,id(2)));
        } finally { pool.shutdownNow(); }
    }

    @Test void archiveWaitsForContent02SharedParentLockAndHistorySeesArchiveAfterCommit() throws Exception {
        jdbc.update("insert into user_roles(user_id,role_id) select ?,role_id from roles where name='LEARNER'",ACTOR);
        var token=token(ACTOR);
        var pool=Executors.newSingleThreadExecutor();
        var future=new java.util.concurrent.atomic.AtomicReference<Future<Integer>>();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx->{
                jdbc.queryForObject("select content_id from learning_contents where content_id=? for share",UUID.class,id(1));
                future.set(pool.submit(()->call(delete(ROOT+"/"+id(1)),token).andReturn().getResponse().getStatus()));
                awaitDatabaseLock();
                jdbc.update("insert into learning_content_history(user_id,content_id,content_version_id,last_accessed_at) values (?,?,?,now())",ACTOR,id(1),version(1));
            });
            assertEquals(204,future.get().get(15,TimeUnit.SECONDS));
            call(post("/api/v1/contents/"+id(1)+"/history"),token).andExpect(status().isNotFound());
            call(get("/api/v1/users/me/content-history"),token).andExpect(jsonPath("$.totalElements").value(0));
            assertEquals(1,count("select count(*) from learning_content_history where user_id=?",ACTOR));
        } finally { pool.shutdownNow(); }
    }

    @Test void ambiguousLegacyDraftsAreNotSilentlyChosenAndMetadataChangesRollBack() throws Exception {
        jdbc.update("update learning_contents set current_published_version_id=null where content_id=?",id(5));
        jdbc.update("insert into learning_content_versions(content_version_id,content_id,version_number,title,body,status,created_by,created_at,updated_at) values (?,?,2,'Second draft','','DRAFT',?,now(),now())",UUID.randomUUID(),id(5),ACTOR);
        var before=jdbc.queryForObject("select category_id from learning_contents where content_id=?",Integer.class,id(5));
        call(patch(ROOT+"/"+id(5)).contentType(APPLICATION_JSON).content("{\"categoryCode\":\"DOCS\"}"),token(ACTOR))
                .andExpect(status().isConflict());
        assertEquals(before,jdbc.queryForObject("select category_id from learning_contents where content_id=?",Integer.class,id(5)));
        assertEquals(2,count("select count(*) from learning_content_versions where content_id=?",id(5)));
        var detail=json(call(get(ROOT+"/"+id(5)),token(ACTOR)).andExpect(status().isOk()));
        assertEquals(2,detail.get("versions").size());
        assertTrue(detail.get("content").get("workingDraftVersionId").isNull());
        call(get(ROOT),token(ACTOR)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(10));
    }

    @Test void databaseRejectsForeignPointersAndMissingOrDuplicateAssetLinks() {
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update(
                "update learning_contents set current_published_version_id=? where content_id=?",version(2),id(1)));
        assertEquals(version(1),jdbc.queryForObject("select current_published_version_id from learning_contents where content_id=?",UUID.class,id(1)));
        var resource=UUID.fromString("40000000-0000-0000-0000-000000000001");
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update(
                "insert into learning_content_version_resources values (?,?,1)",UUID.randomUUID(),resource));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update(
                "insert into learning_content_version_resources values (?,?,1)",version(102),UUID.randomUUID()));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update(
                "insert into learning_content_version_resources values (?,?,1)",version(1),resource));
        assertEquals(3,count("select count(*) from learning_content_version_resources where content_version_id=?",version(1)));
    }

    @Test void missingPublishedPointerDoesNotFallBackToAnyOtherVersion() throws Exception {
        jdbc.update("update learning_contents set current_published_version_id=null where content_id=?",id(1));
        jdbc.update("update learning_content_versions set status='ARCHIVED' where content_version_id=?",version(102));
        call(patch(ROOT+"/"+id(1)).contentType(APPLICATION_JSON).content("{\"title\":\"No fallback\"}"),token(ACTOR))
                .andExpect(status().isConflict());
        assertEquals(3,count("select count(*) from learning_content_versions where content_id=?",id(1)));
        assertEquals(0,count("select count(*) from learning_content_versions where content_id=? and title='No fallback'",id(1)));
    }

    @ParameterizedTest @ValueSource(ints={6,7,10})
    void inconsistentPublishedPointerDoesNotPermitEditingOrCreatingDrafts(int number) throws Exception {
        var before=count("select count(*) from learning_content_versions where content_id=?",id(number));
        call(patch(ROOT+"/"+id(number)).contentType(APPLICATION_JSON).content("{\"title\":\"Blocked state\"}"),token(ACTOR))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTENT_STATE_CONFLICT"));
        assertEquals(before,count("select count(*) from learning_content_versions where content_id=?",id(number)));
        assertEquals(0,count("select count(*) from learning_content_versions where content_id=? and title='Blocked state'",id(number)));
    }

    @Test void draftWithPublicationOrArchiveTimestampCannotBeEdited() throws Exception {
        var token=token(ACTOR);
        var created=create(token,"GRAMMAR");
        var draft=UUID.fromString(created.get("content").get("workingDraftVersionId").asString());
        for (var column:List.of("published_at","archived_at")) {
            var now=java.time.OffsetDateTime.now();
            jdbc.update("update learning_content_versions set published_at=?,archived_at=? where content_version_id=?",
                    column.equals("published_at")?now:null,column.equals("archived_at")?now:null,draft);
            call(patch(path(created)).contentType(APPLICATION_JSON).content("{\"title\":\"Forbidden\"}"),token)
                    .andExpect(status().isConflict());
            assertEquals("New GRAMMAR",jdbc.queryForObject("select title from learning_content_versions where content_version_id=?",String.class,draft));
        }
    }

    @Test void committedDraftUpdateThenBlockedArchiveKeepsBothVersionsAndHidesContent() throws Exception {
        role(ACTOR,"ADMIN");
        var token=token(ACTOR);
        authenticate(ACTOR,"content:manage:any");
        var pool=Executors.newSingleThreadExecutor();
        var future=new java.util.concurrent.atomic.AtomicReference<Future<Integer>>();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx->{
                service.update(ACTOR,id(2),new ContentEdit(Set.of("title"),"Before archive",null,null,null,null));
                future.set(pool.submit(()->call(delete(ROOT+"/"+id(2)),token).andReturn().getResponse().getStatus()));
                awaitDatabaseLock();
            });
            assertEquals(204,future.get().get(15,TimeUnit.SECONDS));
            assertEquals("ARCHIVED",jdbc.queryForObject("select status from learning_contents where content_id=?",String.class,id(2)));
            assertEquals(2,count("select count(*) from learning_content_versions where content_id=?",id(2)));
            assertEquals("Before archive",jdbc.queryForObject("select title from learning_content_versions where content_id=? and status='DRAFT'",String.class,id(2)));
            assertEquals(version(2),jdbc.queryForObject("select current_published_version_id from learning_contents where content_id=?",UUID.class,id(2)));
            call(get("/api/v1/contents/"+id(2)),null).andExpect(status().isNotFound());
        } finally { pool.shutdownNow(); }
    }

    @ParameterizedTest
    @CsvSource({"1,category","2,category","1,difficulty","2,difficulty","1,difficultyNull","2,difficultyNull",
            "1,both","2,both","1,same","2,same"})
    void publishedMetadataConflictRejectsTheEntireMixedPatchWithOrWithoutWorkingDraft(int number,String change) throws Exception {
        role(ACTOR,"ADMIN");
        var token=token(ACTOR);
        var before=snapshot(id(number));
        var publicBefore=json(call(get("/api/v1/contents/"+id(number)),token).andExpect(status().isOk()));
        var payload=new LinkedHashMap<String,Object>();
        payload.put("title","Rejected text edit");
        payload.put("summary",null);
        payload.put("body","Rejected body edit");
        switch (change) {
            case "category" -> payload.put("categoryCode","DOCS");
            case "difficulty" -> payload.put("difficulty","HARD");
            case "difficultyNull" -> payload.put("difficulty",null);
            case "both" -> { payload.put("categoryCode","DOCS"); payload.put("difficulty","HARD"); }
            case "same" -> { payload.put("categoryCode","GRAMMAR"); payload.put("difficulty",before.content().get("difficulty")); }
            default -> throw new AssertionError("Unknown fixture case");
        }
        call(patch(ROOT+"/"+id(number)).contentType(APPLICATION_JSON).content(mapper.writeValueAsString(payload)),token)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTENT_METADATA_IMMUTABLE"));
        // Includes timestamps, existing draft text, version count, parent metadata/pointer and every asset link.
        assertEquals(before,snapshot(id(number)));
        assertEquals(publicBefore,json(call(get("/api/v1/contents/"+id(number)),token).andExpect(status().isOk())));
    }

    @ParameterizedTest @ValueSource(ints={1,2})
    void textOnlyPublishedPatchKeepsTheEntirePublicRepresentationAndExistingAssetLinks(int number) throws Exception {
        role(ACTOR,"ADMIN");
        var token=token(ACTOR);
        var before=snapshot(id(number));
        var publicBefore=json(call(get("/api/v1/contents/"+id(number)),token).andExpect(status().isOk()));
        var result=json(call(patch(ROOT+"/"+id(number)).contentType(APPLICATION_JSON)
                .content("{\"title\":\"Text only revision\",\"summary\":null,\"body\":\"Working text\"}"),token)
                .andExpect(status().isOk()));
        var after=snapshot(id(number));
        var parentBefore=new HashMap<>(before.content());
        var parentAfter=new HashMap<>(after.content());
        parentBefore.remove("updated_at");
        parentAfter.remove("updated_at");
        assertEquals(parentBefore,parentAfter);
        var pointer=before.content().get("current_published_version_id");
        assertEquals(before.versions().stream().filter(v->v.get("content_version_id").equals(pointer)).findFirst().orElseThrow(),
                jdbc.queryForMap("select * from learning_content_versions where content_version_id=?",pointer));
        assertEquals(before.assets(),after.assets());
        assertEquals(1,count("select count(*) from learning_content_versions where content_id=? and status='DRAFT'",id(number)));
        assertEquals(number==1?before.versions().size():before.versions().size()+1,after.versions().size());
        assertEquals("Text only revision",result.get("content").get("title").asString());
        assertEquals(publicBefore,json(call(get("/api/v1/contents/"+id(number)),token).andExpect(status().isOk())));
        safeJson(result);
    }

    @ParameterizedTest @ValueSource(strings={"TEACHER","ADMIN"})
    void contentManagementPermissionDoesNotEnableUnsupportedMediaAliasesOrUrls(String role) throws Exception {
        role(ACTOR,role);
        var token=token(ACTOR);
        var before=snapshot(id(1));
        int parentCount=count("select count(*) from learning_contents where author_id=?",ACTOR);
        for (var resource:List.of("40000000-0000-0000-0000-000000000001", // PUBLIC
                "40000000-0000-0000-0000-000000000002", // AUTHENTICATED, actor has content:read
                "40000000-0000-0000-0000-000000000003", // PRIVATE
                UUID.randomUUID().toString())) {
            var payload=Map.of("title","No partial edit","resourceIds",List.of(resource));
            call(patch(ROOT+"/"+id(1)).contentType(APPLICATION_JSON).content(mapper.writeValueAsString(payload)),token)
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            var createPayload=Map.of("title","No media creation","body","","categoryCode","DOCS","resourceIds",List.of(resource));
            call(post(ROOT).contentType(APPLICATION_JSON).content(mapper.writeValueAsString(createPayload)),token)
                    .andExpect(status().isBadRequest());
        }
        call(patch(ROOT+"/"+id(1)).contentType(APPLICATION_JSON).content("{\"resourceIds\":[]}"),token)
                .andExpect(status().isBadRequest());
        var error=json(call(patch(ROOT+"/"+id(1)).contentType(APPLICATION_JSON)
                .content("{\"storageUrl\":\"https://client.invalid/file\"}"),token).andExpect(status().isBadRequest()));
        assertFalse(error.toString().contains("client.invalid"));
        assertEquals(before,snapshot(id(1)));
        assertEquals(parentCount,count("select count(*) from learning_contents where author_id=?",ACTOR));
        safeJson(json(call(get(ROOT+"/"+id(1)),token).andExpect(status().isOk())));
    }

    private static UUID mediaId(int n) { return UUID.fromString("40000000-0000-0000-0000-%012d".formatted(n)); }
    private void mediaOwner(UUID media,UUID owner) { jdbc.update("update media_resources set owner_id=? where resource_id=?",owner,media); }
    private void grantMedia(UUID media) {
        jdbc.update("insert into media_reuse_grants(media_id,user_id,granted_by,granted_at) values (?,?,?,now())",media,ACTOR,OTHER);
    }
    private void revokeMedia(UUID media) {
        jdbc.update("update media_reuse_grants set revoked_by=?,revoked_at=now() where media_id=? and user_id=?",OTHER,media,ACTOR);
    }
    private String assetPayload(UUID... ids) throws Exception {
        return mapper.writeValueAsString(Map.of("assets",Arrays.stream(ids).map(id->Map.of("mediaId",id.toString())).toList()));
    }
    private UUID draftId(JsonNode detail) { return UUID.fromString(detail.get("content").get("workingDraftVersionId").asString()); }
    private List<Map<String,Object>> assetRows(UUID versionId) {
        return jdbc.queryForList("select resource_id,display_order from learning_content_version_resources where content_version_id=? order by display_order,resource_id",versionId);
    }
    private void publishedAssets(UUID... ids) {
        for (int i=0;i<ids.length;i++) {
            jdbc.update("insert into learning_content_version_resources(content_version_id,resource_id,display_order) values (?,?,?)",version(2),ids[i],2+i*5);
        }
    }

    @ParameterizedTest @ValueSource(strings={"owner","memberOwner","grant","legacyGrant","revoked","foreign","private","missing","legacy","admin","noRead"})
    void mediaBindingRequiresBothVisibilityAndExplicitOwnerOrActiveGrant(String kind) throws Exception {
        var token=token(ACTOR);
        var created=create(token,"GRAMMAR");
        UUID media=mediaId(1);
        boolean allowed=Set.of("owner","memberOwner","grant","legacyGrant").contains(kind);
        switch(kind) {
            case "memberOwner" -> media=mediaId(2);
            case "grant" -> { mediaOwner(media,OTHER); grantMedia(media); }
            case "legacyGrant" -> { mediaOwner(media,null); grantMedia(media); }
            case "revoked" -> { mediaOwner(media,OTHER); grantMedia(media); revokeMedia(media); }
            case "foreign" -> mediaOwner(media,OTHER);
            case "private" -> { media=mediaId(3); grantMedia(media); }
            case "missing" -> media=UUID.randomUUID();
            case "legacy" -> mediaOwner(media,null);
            case "admin" -> { role(ACTOR,"ADMIN"); mediaOwner(media,OTHER); }
            case "noRead" -> {
                media=mediaId(2);
                role(ACTOR,"CONTENT_READ_FIXTURE");
                jdbc.update("insert into role_permissions(role_id,permission_id) select 900003,permission_id from permissions where code='content:manage:self'");
            }
            case "owner" -> { }
            default -> throw new AssertionError("Unknown fixture");
        }
        var contentId=UUID.fromString(created.get("content").get("contentId").asString());
        var before=snapshot(contentId);
        var result=call(patch(path(created)).contentType(APPLICATION_JSON).content(assetPayload(media)),token);
        if (allowed) {
            var detail=json(result.andExpect(status().isOk()));
            assertEquals(media,assetRows(draftId(detail)).getFirst().get("resource_id"));
            safeJson(detail);
            if (kind.equals("grant")) {
                var audit=jdbc.queryForMap("select granted_by,granted_at,revoked_at from media_reuse_grants where media_id=? and user_id=?",media,ACTOR);
                assertEquals(OTHER,audit.get("granted_by"));
                assertNotNull(audit.get("granted_at"));
                assertNull(audit.get("revoked_at"));
            }
        } else {
            var error=json(result.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND")));
            assertFalse(error.toString().contains(media.toString()));
            safeJson(error);
            assertEquals(before,snapshot(contentId));
        }
        int beforeCount=count("select count(*) from learning_contents where author_id=?",ACTOR);
        var payload=Map.of("title","Inline media","body","","categoryCode","DOCS","assets",List.of(Map.of("mediaId",media.toString())));
        var creation=call(post(ROOT).contentType(APPLICATION_JSON).content(mapper.writeValueAsString(payload)),token);
        if (allowed) {
            var detail=json(creation.andExpect(status().isCreated()));
            assertEquals(media,assetRows(draftId(detail)).getFirst().get("resource_id"));
            safeJson(detail);
        } else {
            creation.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
            assertEquals(beforeCount,count("select count(*) from learning_contents where author_id=?",ACTOR));
        }
    }

    @ParameterizedTest @ValueSource(strings={"null","{}","\"bad\"","[null]","[\"40000000-0000-0000-0000-000000000001\"]",
            "[{}]","[{\"mediaId\":null}]","[{\"mediaId\":1}]","[{\"mediaId\":\"bad-id\"}]",
            "[{\"mediaId\":\"1-1-1-1-1\"}]","[{\"mediaId\":\"40000000-0000-0000-0000-000000000001\",\"url\":\"https://client.invalid\"}]",
            "[{\"mediaId\":\"40000000-0000-0000-0000-000000000001\"},{\"mediaId\":\"40000000-0000-0000-0000-000000000001\"}]"})
    void assetsUseAStrictNonNullArrayOfUniqueMediaIds(String assets) throws Exception {
        var before=snapshot(id(1));
        call(patch(ROOT+"/"+id(1)).contentType(APPLICATION_JSON).content("{\"title\":\"No partial update\",\"assets\":"+assets+"}"),token(ACTOR))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertEquals(before,snapshot(id(1)));
    }

    @Test void bindingReplacingOrderingOmittingAndClearingOnlyChangeTheWorkingDraft() throws Exception {
        var token=token(ACTOR);
        var published=assetRows(version(1));
        var attached=json(call(patch(ROOT+"/"+id(1)).contentType(APPLICATION_JSON).content(assetPayload(mediaId(2),mediaId(1))),token)
                .andExpect(status().isOk()));
        var draft=draftId(attached);
        assertEquals(version(102),draft);
        assertEquals(List.of(mediaId(2),mediaId(1)),assetRows(draft).stream().map(r->r.get("resource_id")).toList());
        assertEquals(List.of(1,2),assetRows(draft).stream().map(r->r.get("display_order")).toList());
        call(patch(ROOT+"/"+id(1)).contentType(APPLICATION_JSON).content("{\"body\":\"Keep assets\"}"),token).andExpect(status().isOk());
        assertEquals(2,assetRows(draft).size());
        call(patch(ROOT+"/"+id(1)).contentType(APPLICATION_JSON).content(assetPayload(mediaId(1))),token).andExpect(status().isOk());
        assertEquals(1,assetRows(draft).size());
        call(patch(ROOT+"/"+id(1)).contentType(APPLICATION_JSON).content(assetPayload()),token).andExpect(status().isOk());
        assertTrue(assetRows(draft).isEmpty());
        assertEquals(published,assetRows(version(1)));
        safeJson(attached);
    }

    @Test void cloneCopiesAllAuthorizedPublishedLinksAndPreservesTheirOrderAndSource() throws Exception {
        role(ACTOR,"ADMIN");
        var token=token(ACTOR);
        mediaOwner(mediaId(2),OTHER);
        grantMedia(mediaId(2));
        publishedAssets(mediaId(1),mediaId(2));
        var source=assetRows(version(2));
        var cloned=json(call(patch(ROOT+"/"+id(2)).contentType(APPLICATION_JSON).content("{\"title\":\"Clone with media\"}"),token)
                .andExpect(status().isOk()));
        var draft=draftId(cloned);
        assertEquals(source,assetRows(draft));
        assertEquals(version(2).toString(),cloned.get("content").get("publishedVersionId").asString());
        call(patch(ROOT+"/"+id(2)).contentType(APPLICATION_JSON).content(assetPayload(mediaId(2))),token).andExpect(status().isOk());
        assertEquals(List.of(Map.of("resource_id",mediaId(2),"display_order",1)),assetRows(draft));
        call(patch(ROOT+"/"+id(2)).contentType(APPLICATION_JSON).content(assetPayload()),token).andExpect(status().isOk());
        assertTrue(assetRows(draft).isEmpty());
        assertEquals(source,assetRows(version(2)));
        safeJson(cloned);
    }

    @ParameterizedTest @ValueSource(strings={"foreign","private","legacy","revoked","noRead"})
    void cloneRejectsTheWholeRequestWhenAnyPublishedMediaCannotBeReused(String kind) throws Exception {
        role(ACTOR,"ADMIN");
        UUID bad=mediaId(2);
        switch(kind) {
            case "foreign" -> mediaOwner(bad,OTHER);
            case "private" -> bad=mediaId(3);
            case "legacy" -> mediaOwner(bad,null);
            case "revoked" -> { mediaOwner(bad,OTHER); grantMedia(bad); revokeMedia(bad); }
            case "noRead" -> {
                role(ACTOR,"CONTENT_READ_FIXTURE");
                jdbc.update("insert into role_permissions(role_id,permission_id) select 900003,permission_id from permissions where code='content:manage:any'");
            }
        }
        publishedAssets(mediaId(1),bad);
        var token=token(ACTOR);
        var before=snapshot(id(2));
        for (var payload:List.of("{\"title\":\"Must roll back\"}","{\"title\":\"Must roll back\",\"assets\":[]}")) {
            var error=json(call(patch(ROOT+"/"+id(2)).contentType(APPLICATION_JSON).content(payload),token)
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTENT_MEDIA_REUSE_CONFLICT")));
            assertEquals(before,snapshot(id(2)));
            assertFalse(error.toString().contains(bad.toString()));
        }
    }

    @Test void revokedRetainedMediaBlocksTextEditsButCanBeExplicitlyRemoved() throws Exception {
        var token=token(ACTOR);
        mediaOwner(mediaId(4),OTHER);
        grantMedia(mediaId(4));
        call(patch(ROOT+"/"+id(1)).contentType(APPLICATION_JSON).content("{\"title\":\"Granted edit\"}"),token).andExpect(status().isOk());
        revokeMedia(mediaId(4));
        var before=snapshot(id(1));
        call(patch(ROOT+"/"+id(1)).contentType(APPLICATION_JSON).content("{\"title\":\"Blocked\"}"),token)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTENT_MEDIA_REUSE_CONFLICT"));
        assertEquals(before,snapshot(id(1)));
        call(patch(ROOT+"/"+id(1)).contentType(APPLICATION_JSON).content("{\"title\":\"Remove revoked media\",\"assets\":[]}"),token)
                .andExpect(status().isOk());
        assertTrue(assetRows(version(102)).isEmpty());
        call(patch(ROOT+"/"+id(1)).contentType(APPLICATION_JSON).content("{\"body\":\"No retained media\"}"),token).andExpect(status().isOk());
        assertEquals(before.assets().stream().filter(r->r.get("content_version_id").equals(version(1))).count(),(long)assetRows(version(1)).size());
        var audit=jdbc.queryForMap("select revoked_by,revoked_at from media_reuse_grants where media_id=? and user_id=?",mediaId(4),ACTOR);
        assertEquals(OTHER,audit.get("revoked_by"));
        assertNotNull(audit.get("revoked_at"));
    }

    @Test void aMixedMetadataTextAndAssetPatchRollsBackWhenAnyNewMediaFails() throws Exception {
        var token=token(ACTOR);
        var created=create(token,"GRAMMAR");
        var contentId=UUID.fromString(created.get("content").get("contentId").asString());
        call(patch(path(created)).contentType(APPLICATION_JSON).content(assetPayload(mediaId(1))),token).andExpect(status().isOk());
        mediaOwner(mediaId(6),OTHER);
        var before=snapshot(contentId);
        var payload=Map.of("categoryCode","DOCS","difficulty","HARD","title","No partial edit","body","No partial body",
                "assets",List.of(Map.of("mediaId",mediaId(1).toString()),Map.of("mediaId",mediaId(6).toString())));
        call(patch(path(created)).contentType(APPLICATION_JSON).content(mapper.writeValueAsString(payload)),token)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
        assertEquals(before,snapshot(contentId));
    }

    @Test void revokingReuseDoesNotWithdrawPublishedMediaOrLearnerVisibility() throws Exception {
        role(ACTOR,"ADMIN");
        var token=token(ACTOR);
        mediaOwner(mediaId(1),OTHER);
        grantMedia(mediaId(1));
        publishedAssets(mediaId(1));
        var before=json(call(get("/api/v1/contents/"+id(2)),null).andExpect(status().isOk()));
        call(patch(ROOT+"/"+id(2)).contentType(APPLICATION_JSON).content("{\"title\":\"Granted clone\"}"),token).andExpect(status().isOk());
        revokeMedia(mediaId(1));
        call(patch(ROOT+"/"+id(2)).contentType(APPLICATION_JSON).content("{\"body\":\"Blocked retained link\"}"),token).andExpect(status().isConflict());
        assertEquals(before,json(call(get("/api/v1/contents/"+id(2)),null).andExpect(status().isOk())));
        assertEquals(1,assetRows(version(2)).size());
    }

    @Test void grantKeysAuditAndNullableLegacyOwnershipHaveDatabaseConstraints() {
        mediaOwner(mediaId(5),null);
        assertNull(jdbc.queryForObject("select owner_id from media_resources where resource_id=?",UUID.class,mediaId(5)));
        assertThrows(DataIntegrityViolationException.class,()->mediaOwner(mediaId(5),UUID.randomUUID()));
        grantMedia(mediaId(1));
        assertThrows(DataIntegrityViolationException.class,()->grantMedia(mediaId(1)));
        assertThrows(DataIntegrityViolationException.class,()->grantMedia(UUID.randomUUID()));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("insert into media_reuse_grants(media_id,user_id,granted_by,granted_at) values (?,?,?,now())",mediaId(2),UUID.randomUUID(),OTHER));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("insert into media_reuse_grants(media_id,user_id,granted_by,granted_at) values (?,?,?,now())",mediaId(2),ACTOR,UUID.randomUUID()));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("update media_reuse_grants set revoked_at=now() where media_id=? and user_id=?",mediaId(1),ACTOR));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("update media_reuse_grants set revoked_by=?,revoked_at=granted_at-interval '1 second' where media_id=? and user_id=?",OTHER,mediaId(1),ACTOR));
    }

    private UUID prepareMediaRace(String operation,String change,String token) throws Exception {
        if (change.equals("revoke")) { mediaOwner(mediaId(1),OTHER); grantMedia(mediaId(1)); }
        if (operation.equals("clone")) { publishedAssets(mediaId(1)); return id(2); }
        return UUID.fromString(create(token,"GRAMMAR").get("content").get("contentId").asString());
    }
    private void changeMediaRights(String change) {
        if (change.equals("revoke")) { revokeMedia(mediaId(1)); }
        else { jdbc.update("update media_resources set visibility='PRIVATE' where resource_id=?",mediaId(1)); }
    }

    @ParameterizedTest @CsvSource({"attach,revoke","clone,revoke","attach,visibility","clone,visibility"})
    void committedRightsChangeWinsAgainstBlockedAttachOrClone(String operation,String change) throws Exception {
        role(ACTOR,"ADMIN");
        var token=token(ACTOR);
        var contentId=prepareMediaRace(operation,change,token);
        var before=snapshot(contentId);
        var payload=operation.equals("attach")?assetPayload(mediaId(1)):"{\"title\":\"Blocked clone\"}";
        var pool=Executors.newSingleThreadExecutor();
        var future=new java.util.concurrent.atomic.AtomicReference<Future<Integer>>();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx->{
                changeMediaRights(change);
                future.set(pool.submit(()->call(patch(ROOT+"/"+contentId).contentType(APPLICATION_JSON).content(payload),token).andReturn().getResponse().getStatus()));
                awaitDatabaseLock();
            });
            assertEquals(operation.equals("attach")?404:409,future.get().get(15,TimeUnit.SECONDS));
            assertEquals(before,snapshot(contentId));
        } finally { pool.shutdownNow(); }
    }

    @ParameterizedTest @CsvSource({"attach,revoke","clone,revoke","attach,visibility","clone,visibility"})
    void successfulAttachOrCloneHoldsRightsLocksUntilCommitAndNextEditSeesRevocation(String operation,String change) throws Exception {
        role(ACTOR,"ADMIN");
        var token=token(ACTOR);
        var contentId=prepareMediaRace(operation,change,token);
        authenticate(ACTOR,"content:manage:any","content:read");
        var pool=Executors.newSingleThreadExecutor();
        var future=new java.util.concurrent.atomic.AtomicReference<Future<?>>();
        var edited=new java.util.concurrent.atomic.AtomicReference<ContentManagementDtos.Detail>();
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx->{
                var edit=operation.equals("attach")
                        ?new ContentEdit(Set.of("assets"),null,null,null,null,null,List.of(mediaId(1)))
                        :new ContentEdit(Set.of("title"),"Locked clone",null,null,null,null);
                edited.set(service.update(ACTOR,contentId,edit));
                future.set(pool.submit(()->changeMediaRights(change)));
                awaitDatabaseLock();
            });
            future.get().get(15,TimeUnit.SECONDS);
            assertEquals(1,assetRows(edited.get().content().workingDraftVersionId()).size());
            call(patch(ROOT+"/"+contentId).contentType(APPLICATION_JSON).content("{\"body\":\"New permission state\"}"),token)
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONTENT_MEDIA_REUSE_CONFLICT"));
            if (operation.equals("clone")) { assertEquals(1,assetRows(version(2)).size()); }
        } finally { pool.shutdownNow(); }
    }
}
