package com.toeic.backend.content.api;

import com.toeic.backend.content.application.ContentPersonalService;
import com.toeic.backend.content.application.PersonalContentQuery;
import com.toeic.backend.identity.application.SessionAuthenticationService;
import com.toeic.backend.identity.application.SessionPrincipal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
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
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"debug=false", "logging.level.org.hibernate.orm.jdbc.error=OFF"})
@AutoConfigureMockMvc
@Import(Content02IntegrationTest.Beans.class)
@Sql(scripts = {"/fixtures/content-read.sql", "/fixtures/content-bookmark.sql", "/fixtures/content02-search.sql"},
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED, encoding = "UTF-8"))
@Sql(scripts = {"/fixtures/content-bookmark-cleanup.sql", "/fixtures/content-read-cleanup.sql"},
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD,
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
class Content02IntegrationTest {
    static class TestClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>();
        void reset() { now.set(Instant.parse("2026-11-04T10:00:00Z")); }
        void advance() { now.updateAndGet(value -> value.plusSeconds(60)); }
        @Override public Instant instant() { return now.get(); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
    }
    @TestConfiguration
    static class Beans { @Bean @Primary TestClock content02Clock() { return new TestClock(); } }

    private static final UUID ACTOR = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final String PASSWORD = "Content02Fixture123!";
    private static final String BOOKMARKS = "/api/v1/users/me/bookmarks";
    private static final String HISTORY = "/api/v1/users/me/content-history";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper mapper;
    @Autowired TestClock clock;
    @Autowired ContentPersonalService personal;
    @Autowired SessionAuthenticationService authentication;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach
    void setup() {
        clock.reset();
        SecurityContextHolder.clearContext();
        jdbc.update("update users set password_hash=? where user_id in (?,?)", encoder.encode(PASSWORD), ACTOR, OTHER);
    }

    private static UUID id(int number) { return UUID.fromString("20000000-0000-0000-0000-%012d".formatted(number)); }
    private static UUID version(int number) { return UUID.fromString("30000000-0000-0000-0000-%012d".formatted(number)); }
    private static String action(int number, String action) { return "/api/v1/contents/" + id(number) + "/" + action; }
    private String token(UUID user) throws Exception {
        var email = user.equals(ACTOR) ? "content-fixture@example.invalid" : "bookmark-fixture@example.invalid";
        var result = mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
    }
    private ResultActions request(MockHttpServletRequestBuilder request, String token) throws Exception {
        if (token != null) { request.header("Authorization", "Bearer " + token); }
        return mvc.perform(request);
    }
    private ResultActions search(String q, String token) throws Exception {
        return request(get("/api/v1/contents").param("q", q), token);
    }
    private int rows(String table, UUID user) {
        if (!Set.of("bookmarks", "learning_content_history", "learning_progress").contains(table)) { throw new AssertionError(); }
        return jdbc.queryForObject("select count(*) from " + table + " where user_id=?", Integer.class, user);
    }
    private void safeJson(String json) {
        assertFalse(json.matches("(?is).*\"(passwordHash|password|accessToken|storageUrl|storage_url|transcript|authorId|createdBy|media|body)\"\\s*:.*"));
        assertFalse(json.contains("LEAK_") || json.contains("s3://"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ALPHA", "alpha", "BodyOnly", "ĐÚNG", "đúng ngữ", "  Shared\t \nneedle  ", "%", "_", "\\", "bang!end"})
    void searchFindsLiteralTextInPublishedTitleSummaryOrBody(String q) throws Exception {
        var result = search(q, null).andExpect(status().isOk()).andReturn();
        var json = mapper.readTree(result.getResponse().getContentAsString());
        assertTrue(json.get("totalElements").asLong() > 0);
        safeJson(result.getResponse().getContentAsString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DUNG", "LEAK_", "LEAK_OLD_VERSION", "LEAK_NEW_DRAFT", "LEAK_HIDDEN_BODY", "LEAK_LEGACY_DRAFT", "' OR 1=1 --"})
    void searchDoesNotRemoveAccentsOrReadHiddenLegacyAndNonCurrentVersions(String q) throws Exception {
        for (var token : Arrays.asList(null, token(ACTOR))) {
            search(q, token).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0))
                    .andExpect(jsonPath("$.content.length()").value(0));
        }
    }

    @Test
    void wildcardCharactersAreLiteralAndDoNotExpandMatches() throws Exception {
        for (var q : List.of("%", "_", "\\")) {
            search(q, null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].contentId").value(id(1).toString()));
        }
        search("100_", null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void normalizationBlankAndLengthAreValidatedAfterNormalization() throws Exception {
        for (var q : List.of("", "   \t\n", "\u00a0\u2003")) {
            search(q, null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        }
        search(" ".repeat(250) + "Alpha" + " ".repeat(250), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        jdbc.update("update learning_content_versions set body=? where content_version_id=?", "a".repeat(200), version(1));
        search("a".repeat(200), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        search("a".repeat(201), null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        search(String.valueOf((char)0), null).andExpect(status().isBadRequest());
        jdbc.update("update learning_content_versions set body=? where content_version_id=?", "%".repeat(200), version(1));
        search("%".repeat(200), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        request(get("/api/v1/contents").param("q", "one", "two"), null).andExpect(status().isBadRequest());
    }

    @Test
    void searchCombinesFiltersStableSortPaginationAndVisibilityInTheCount() throws Exception {
        var token = token(ACTOR);
        request(get("/api/v1/contents").param("q", "Shared needle").param("size", "1").param("page", "1")
                .param("sort", "title,asc"), token).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.content[0].title").value("Beta published"));
        request(get("/api/v1/contents").param("q", "Shared needle").param("size", "1").param("page", "1")
                .param("sort", "title,asc"), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        request(get("/api/v1/contents").param("q", "Shared needle").param("categoryId", "900001")
                .param("difficulty", "HARD").param("size", "1"), token).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].contentId").value(id(3).toString()));
        request(get("/api/v1/contents").param("q", "Shared needle").param("categoryId", "900001")
                .param("difficulty", "HARD"), null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void learnerCanCreateListAndRemoveOwnBookmarkWithExactRepeatContract() throws Exception {
        var token = token(ACTOR);
        request(post(action(1, "bookmarks")), token).andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentId").value(id(1).toString())).andExpect(jsonPath("$.createdAt").isNotEmpty());
        request(post(action(1, "bookmarks")), token).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKMARK_EXISTS"));
        var result = request(get(BOOKMARKS), token).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].content.contentVersionId").value(version(1).toString())).andReturn();
        safeJson(result.getResponse().getContentAsString());
        request(get(BOOKMARKS), token(OTHER)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        request(delete(action(1, "bookmarks")), token(OTHER)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKMARK_NOT_FOUND"));
        request(delete(action(1, "bookmarks")), token).andExpect(status().isNoContent()).andExpect(content().string(""));
        request(delete(action(1, "bookmarks")), token).andExpect(status().isNotFound());
        assertEquals(0, rows("bookmarks", ACTOR));
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 5, 6, 7, 8, 9, 10, 11, 999})
    void hiddenOrMissingContentCannotBeBookmarkedOrRecorded(int number) throws Exception {
        var token = token(ACTOR);
        for (var operation : List.of("bookmarks", "history")) {
            request(post(action(number, operation)), token).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CONTENT_NOT_FOUND"));
        }
        assertEquals(0, rows("bookmarks", ACTOR));
        assertEquals(0, rows("learning_content_history", ACTOR));
    }

    @Test
    void hiddenBookmarkRemainsRemovableWithoutLeakingContentOrItsStatus() throws Exception {
        var token = token(ACTOR);
        request(post(action(3, "bookmarks")), token).andExpect(status().isCreated());
        jdbc.update("update learning_contents set visibility='PRIVATE' where content_id=?", id(3));
        request(get(BOOKMARKS), token).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        request(delete(action(3, "bookmarks")), token).andExpect(status().isNoContent()).andExpect(content().string(""));
        var hidden = request(delete(action(3, "bookmarks")), token).andExpect(status().isNotFound()).andReturn();
        var missing = request(delete(action(999, "bookmarks")), token).andExpect(status().isNotFound()).andReturn();
        assertEquals(mapper.readTree(hidden.getResponse().getContentAsString()).get("message"),
                mapper.readTree(missing.getResponse().getContentAsString()).get("message"));
    }

    private void isolatedPermissions(boolean read) {
        jdbc.update("delete from user_roles where user_id=?", ACTOR);
        jdbc.update("insert into user_roles(user_id,role_id) values (?,900003)", ACTOR);
        jdbc.update("insert into role_permissions(role_id,permission_id) select 900003,permission_id from permissions "
                + "where code in ('content:bookmark:self','content:history:self') or (? and code='content:read')", read);
    }

    @Test
    void revokingReadScopeHidesMemberRowsInBothListsButAllowsOwnBookmarkRemoval() throws Exception {
        isolatedPermissions(true);
        var token = token(ACTOR);
        for (var number : List.of(1, 3)) {
            request(post(action(number, "bookmarks")), token).andExpect(status().isCreated());
            request(post(action(number, "history")), token).andExpect(status().isNoContent());
        }
        jdbc.update("delete from role_permissions where role_id=900003 and permission_id="
                + "(select permission_id from permissions where code='content:read')");
        for (var path : List.of(BOOKMARKS, HISTORY)) {
            var result = request(get(path).param("size", "1"), token).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].content.contentId").value(id(1).toString())).andReturn();
            safeJson(result.getResponse().getContentAsString());
        }
        request(delete(action(3, "bookmarks")), token).andExpect(status().isNoContent());
        request(post(action(3, "bookmarks")), token).andExpect(status().isNotFound());
        request(post(action(3, "history")), token).andExpect(status().isNotFound());
        jdbc.update("delete from role_permissions where role_id=900003");
        request(get(BOOKMARKS), token).andExpect(status().isForbidden());
        request(get(HISTORY), token).andExpect(status().isForbidden());
        request(delete(action(1, "bookmarks")), token).andExpect(status().isForbidden());
    }

    @Test
    void everyPersonalEndpointRequiresValidAuthentication() throws Exception {
        for (var token : Arrays.asList(null, "invalid")) {
            request(get(BOOKMARKS), token).andExpect(status().isUnauthorized());
            request(get(HISTORY), token).andExpect(status().isUnauthorized());
            request(post(action(1, "bookmarks")), token).andExpect(status().isUnauthorized());
            request(delete(action(1, "bookmarks")), token).andExpect(status().isUnauthorized());
            request(post(action(1, "history")), token).andExpect(status().isUnauthorized());
        }
        var token = token(ACTOR);
        jdbc.update("update users set status='LOCKED' where user_id=?", ACTOR);
        request(get(BOOKMARKS), token).andExpect(status().isUnauthorized());
        request(post(action(1, "history")), token).andExpect(status().isUnauthorized());
        search("Alpha", null).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"TEACHER", "ADMIN"})
    void otherRolesHaveNoDefaultGrantButCanUseAValidExplicitPermission(String role) throws Exception {
        jdbc.update("delete from user_roles where user_id=?", ACTOR);
        jdbc.update("insert into user_roles(user_id,role_id) select ?,role_id from roles where name=?", ACTOR, role);
        var token = token(ACTOR);
        request(get(BOOKMARKS), token).andExpect(status().isForbidden());
        request(get(HISTORY), token).andExpect(status().isForbidden());
        request(post(action(1, "bookmarks")), token).andExpect(status().isForbidden());
        request(delete(action(1, "bookmarks")), token).andExpect(status().isForbidden());
        request(post(action(1, "history")), token).andExpect(status().isForbidden());
        jdbc.update("insert into user_roles(user_id,role_id) values (?,900003)", ACTOR);
        jdbc.update("insert into role_permissions(role_id,permission_id) select 900003,permission_id from permissions "
                + "where code in ('content:bookmark:self','content:history:self')");
        request(post(action(1, "bookmarks")), token).andExpect(status().isCreated());
        request(post(action(1, "history")), token).andExpect(status().isNoContent());
        request(get(BOOKMARKS), token).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        request(get(HISTORY), token).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getsNeverWriteHistoryAndPostsUseServerTimeUpdateOneRowAndKeepActorsSeparate() throws Exception {
        var token = token(ACTOR);
        request(get("/api/v1/contents/" + id(1)), token).andExpect(status().isOk());
        search("Alpha", token).andExpect(status().isOk());
        request(get(BOOKMARKS), token).andExpect(status().isOk());
        request(get(HISTORY), token).andExpect(status().isOk());
        assertEquals(0, rows("learning_content_history", ACTOR));
        request(post(action(1, "history")), token).andExpect(status().isNoContent());
        var firstTime = jdbc.queryForObject("select last_accessed_at from learning_content_history where user_id=?", OffsetDateTime.class, ACTOR);
        assertEquals(clock.instant(), firstTime.toInstant());
        clock.advance();
        request(post(action(1, "history")), token).andExpect(status().isNoContent());
        assertEquals(1, rows("learning_content_history", ACTOR));
        assertEquals(0, rows("learning_progress", ACTOR));
        var result = request(get(HISTORY), token).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].content.contentVersionId").value(version(1).toString())).andReturn();
        safeJson(result.getResponse().getContentAsString());
        assertEquals(clock.instant(), jdbc.queryForObject("select last_accessed_at from learning_content_history where user_id=?",
                OffsetDateTime.class, ACTOR).toInstant());
        request(get(HISTORY), token(OTHER)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    private void prepareNewVersion() {
        jdbc.update("update learning_content_versions set title='Republished safe',body='Republished body',status='PUBLISHED',"
                + "published_at='2020-01-02' where content_version_id=?", version(102));
    }

    @Test
    void republishHidesOldHistoryWithoutDeletingItAndNextPostPinsTheNewCurrentVersion() throws Exception {
        var token = token(ACTOR);
        request(post(action(1, "history")), token).andExpect(status().isNoContent());
        request(post(action(1, "bookmarks")), token).andExpect(status().isCreated());
        prepareNewVersion();
        jdbc.update("update learning_contents set current_published_version_id=? where content_id=?", version(102), id(1));
        request(get(HISTORY).param("size", "1"), token).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        assertEquals(1, rows("learning_content_history", ACTOR));
        request(get(BOOKMARKS), token).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].content.contentVersionId").value(version(102).toString()));
        clock.advance();
        request(post(action(1, "history")), token).andExpect(status().isNoContent());
        var result = request(get(HISTORY), token).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].content.contentVersionId").value(version(102).toString())).andReturn();
        safeJson(result.getResponse().getContentAsString());
        assertEquals(1, rows("learning_content_history", ACTOR));
    }

    @Test
    void bothListsHaveStablePaginationAndHideRowsAfterPublicationIsRevoked() throws Exception {
        var token = token(ACTOR);
        for (var number : List.of(1, 2, 3)) {
            request(post(action(number, "bookmarks")), token).andExpect(status().isCreated());
            request(post(action(number, "history")), token).andExpect(status().isNoContent());
        }
        for (var path : List.of(BOOKMARKS, HISTORY)) {
            for (int page=0; page<3; page++) {
                request(get(path).param("size", "1").param("page", String.valueOf(page)), token)
                        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                        .andExpect(jsonPath("$.content[0].content.contentId").value(id(page+1).toString()));
            }
        }
        jdbc.update("update learning_content_versions set archived_at=now() where content_version_id=?", version(2));
        jdbc.update("update learning_contents set status='DRAFT' where content_id=?", id(3));
        for (var path : List.of(BOOKMARKS, HISTORY)) {
            request(get(path).param("size", "1"), token).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        }
    }

    @Test
    void personalListsSortByServerTimeAndHistoryRevisitMovesOnlyHistoryToTheFront() throws Exception {
        var token=token(ACTOR);
        for (var number : List.of(1,2)) {
            request(post(action(number,"bookmarks")),token).andExpect(status().isCreated());
            request(post(action(number,"history")),token).andExpect(status().isNoContent());
            clock.advance();
        }
        for (var path : List.of(BOOKMARKS,HISTORY)) {
            request(get(path),token).andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].content.contentId").value(id(2).toString()));
        }
        request(get(BOOKMARKS).param("sort","createdAt,asc"),token).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].content.contentId").value(id(1).toString()));
        request(get(HISTORY).param("sort","lastAccessedAt,asc"),token).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].content.contentId").value(id(1).toString()));
        request(post(action(1,"bookmarks")),token).andExpect(status().isConflict());
        request(post(action(1,"history")),token).andExpect(status().isNoContent());
        request(get(BOOKMARKS),token).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].content.contentId").value(id(2).toString()));
        request(get(HISTORY),token).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].content.contentId").value(id(1).toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"userId", "versionId", "visibility", "status", "q"})
    void personalListsRejectUnsupportedParameters(String field) throws Exception {
        var token = token(ACTOR);
        for (var path : List.of(BOOKMARKS, HISTORY)) {
            request(get(path).param(field, OTHER.toString()), token).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void paginationSortUuidAndWritePayloadValidationUseTheExistingErrorEnvelope() throws Exception {
        var token = token(ACTOR);
        for (var path : List.of(BOOKMARKS, HISTORY)) {
            for (var pair : List.of(Map.entry("page","-1"),Map.entry("page","2147483647"),Map.entry("size","101"),
                    Map.entry("size","0"),Map.entry("sort","title,asc"),Map.entry("sort","contentId,sideways"))) {
                request(get(path).param(pair.getKey(), pair.getValue()), token).andExpect(status().isBadRequest());
            }
            request(get(path).param("size","1","2"), token).andExpect(status().isBadRequest());
        }
        for (var operation : List.of("history", "bookmarks")) {
            request(post("/api/v1/contents/not-uuid/" + operation), token).andExpect(status().isBadRequest());
            request(post(action(1, operation)).contentType(APPLICATION_JSON).content("{\"userId\":\"other\"}"), token)
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.traceId").isNotEmpty());
            request(post(action(1, operation)).param("userId",OTHER.toString()), token).andExpect(status().isBadRequest());
        }
        assertEquals(0, rows("bookmarks", ACTOR));
        assertEquals(0, rows("learning_content_history", ACTOR));
    }

    private void setActor(SessionPrincipal principal) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("content:bookmark:self"),new SimpleGrantedAuthority("content:history:self"),
                        new SimpleGrantedAuthority("content:read"))));
        SecurityContextHolder.setContext(context);
    }

    @Test
    void servicesRejectMissingPermissionUntrustedPrincipalsAndOtherActors() throws Exception {
        var query = PersonalContentQuery.parse(Map.of(), PersonalContentQuery.Kind.BOOKMARKS);
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> personal.bookmark(ACTOR,id(1)));
        var principal = authentication.authenticate(token(ACTOR));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,List.of()));
        SecurityContextHolder.setContext(context);
        assertThrows(AccessDeniedException.class, () -> personal.bookmark(ACTOR,id(1)));
        assertThrows(AccessDeniedException.class, () -> personal.recordHistory(ACTOR,id(1)));
        setActor(principal);
        try {
            assertThrows(AccessDeniedException.class, () -> personal.bookmark(OTHER,id(1)));
            assertThrows(AccessDeniedException.class, () -> personal.removeBookmark(OTHER,id(1)));
            assertThrows(AccessDeniedException.class, () -> personal.bookmarks(OTHER,query));
            assertThrows(AccessDeniedException.class, () -> personal.recordHistory(OTHER,id(1)));
            assertThrows(AccessDeniedException.class, () -> personal.history(OTHER,
                    PersonalContentQuery.parse(Map.of(), PersonalContentQuery.Kind.HISTORY)));
            var rights = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("forged",null,rights));
            SecurityContextHolder.setContext(context);
            assertThrows(AccessDeniedException.class, () -> personal.bookmark(ACTOR,id(1)));
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test
    void concurrentPostsRespectBookmarkConflictAndHistoryUpsertContracts() throws Exception {
        var token = token(ACTOR);
        for (var operation : List.of("bookmarks","history")) {
            var start = new CountDownLatch(1);
            try (var pool = Executors.newFixedThreadPool(2)) {
                Callable<Integer> task = () -> {
                    assertTrue(start.await(10,TimeUnit.SECONDS));
                    return request(post(action(1,operation)),token).andReturn().getResponse().getStatus();
                };
                var one=pool.submit(task); var two=pool.submit(task); start.countDown();
                var statuses = new ArrayList<>(List.of(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS)));
                Collections.sort(statuses);
                assertEquals(operation.equals("bookmarks") ? List.of(201,409) : List.of(204,204), statuses);
            }
        }
        assertEquals(1,rows("bookmarks",ACTOR));
        assertEquals(1,rows("learning_content_history",ACTOR));
    }

    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(15,TimeUnit.SECONDS)); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
    }
    private void blocked(int pid) {
        var deadline=System.nanoTime()+Duration.ofSeconds(10).toNanos();
        while (System.nanoTime()<deadline) {
            if (Boolean.TRUE.equals(jdbc.queryForObject("select cardinality(pg_blocking_pids(?))>0",Boolean.class,pid))) { return; }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        fail("Expected the concurrent writer to wait on the publication lock");
    }

    @ParameterizedTest
    @ValueSource(strings = {"pointer", "version"})
    void historyPinsConsistentlyAndLocksAgainstConcurrentPublicationChanges(String target) throws Exception {
        var token=token(ACTOR);
        var principal=authentication.authenticate(token);
        prepareNewVersion();
        var recorded=new CountDownLatch(1); var release=new CountDownLatch(1);
        var waiterPid=new CompletableFuture<Integer>();
        try (var pool=Executors.newFixedThreadPool(2)) {
            var record=pool.submit(() -> {
                setActor(principal);
                try { new TransactionTemplate(transactions).executeWithoutResult(status -> {
                    personal.recordHistory(ACTOR,id(1)); recorded.countDown(); await(release);
                }); } finally { SecurityContextHolder.clearContext(); }
            });
            try {
                await(recorded);
                var publish=pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                    waiterPid.complete(jdbc.queryForObject("select pg_backend_pid()",Integer.class));
                    if (target.equals("pointer")) {
                        jdbc.update("update learning_contents set current_published_version_id=? where content_id=?",version(102),id(1));
                    } else { jdbc.update("update learning_content_versions set status='ARCHIVED' where content_version_id=?",version(1)); }
                }));
                blocked(waiterPid.get(15,TimeUnit.SECONDS)); release.countDown();
                record.get(15,TimeUnit.SECONDS); publish.get(15,TimeUnit.SECONDS);
                assertEquals(version(1),jdbc.queryForObject("select content_version_id from learning_content_history where user_id=?",UUID.class,ACTOR));
                request(get(HISTORY),token).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
                assertEquals(1,rows("learning_content_history",ACTOR));
            } finally { release.countDown(); }
        }
    }

    @Test
    void historyRequestWaitingForRepublishRecordsTheNewPointer() throws Exception {
        var token=token(ACTOR); var principal=authentication.authenticate(token); prepareNewVersion();
        var published=new CountDownLatch(1); var release=new CountDownLatch(1); var waiterPid=new CompletableFuture<Integer>();
        try (var pool=Executors.newFixedThreadPool(2)) {
            var publish=pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                jdbc.update("update learning_contents set current_published_version_id=? where content_id=?",version(102),id(1));
                published.countDown(); await(release);
            }));
            try {
                await(published);
                var record=pool.submit(() -> {
                    setActor(principal);
                    try { new TransactionTemplate(transactions).executeWithoutResult(status -> {
                        waiterPid.complete(jdbc.queryForObject("select pg_backend_pid()",Integer.class));
                        personal.recordHistory(ACTOR,id(1));
                    }); } finally { SecurityContextHolder.clearContext(); }
                });
                blocked(waiterPid.get(15,TimeUnit.SECONDS)); release.countDown(); publish.get(15,TimeUnit.SECONDS); record.get(15,TimeUnit.SECONDS);
                assertEquals(version(102),jdbc.queryForObject("select content_version_id from learning_content_history where user_id=?",UUID.class,ACTOR));
                request(get(HISTORY),token).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
            } finally { release.countDown(); }
        }
    }

    @Test
    void databaseRejectsHistoryVersionFromAnotherContent() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into learning_content_history(user_id,content_id,content_version_id,last_accessed_at) values (?,?,?,now())",
                ACTOR,id(1),version(2)));
        assertEquals(0,rows("learning_content_history",ACTOR));
    }
}
