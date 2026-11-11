package com.toeic.backend.questionbank.api;

import com.toeic.backend.identity.application.SessionAuthenticationService;
import com.toeic.backend.questionbank.application.QuestionReadQuery;
import com.toeic.backend.questionbank.application.QuestionReadService;
import com.toeic.backend.questionbank.infrastructure.QuestionVersionRepository;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.jpa.show-sql=false", "debug=false"})
@AutoConfigureMockMvc
@Sql(scripts = "/fixtures/question-read.sql", config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
@Sql(scripts = "/fixtures/question-read-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD,
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
class QuestionReadIntegrationTest {
    private static final String USER = "63000000-0000-0000-0000-000000000001";
    private static final String PASSWORD = "QuestionFixture123!";
    private static final Pattern HIDDEN_KEYS = Pattern.compile(
            "\"(?:isCorrect|correct|answerKey|correctChoiceIds|explanation|storageUrl|storage_url|transcript|reviewedBy|approvedBy)\"\\s*:",
            Pattern.CASE_INSENSITIVE);
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired SessionAuthenticationService authentication;
    @Autowired QuestionReadService service;
    @Autowired QuestionVersionRepository versions;
    @Autowired PlatformTransactionManager transactions;

    private static String questionId(int number) { return "73000000-0000-0000-0000-%012d".formatted(number); }
    private static String versionId(int number) { return "76000000-0000-0000-0000-%012d".formatted(number); }
    private static String groupId(int number) { return "74000000-0000-0000-0000-%012d".formatted(number); }
    private static String groupVersionId(int number) { return "75000000-0000-0000-0000-%012d".formatted(number); }
    private static String assetId(int number) { return "83000000-0000-0000-0000-%012d".formatted(number); }

    @BeforeEach
    void credentials() {
        jdbc.update("update users set password_hash = ? where user_id = ?::uuid", encoder.encode(PASSWORD), USER);
    }
    private void role(String role) {
        jdbc.update("delete from user_roles where user_id = ?::uuid", USER);
        jdbc.update("insert into user_roles select ?::uuid, role_id, null from roles where name = ?", USER, role);
    }
    private String token() throws Exception {
        var result = mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", "q02-fixture@example.invalid", "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
    }
    private ResultActions detail(int number, String token) throws Exception {
        return mvc.perform(get("/api/v1/questions/" + questionId(number)).header("Authorization", "Bearer " + token));
    }
    private void assertSafe(String json) {
        assertFalse(HIDDEN_KEYS.matcher(json).find(), "Hidden key at some JSON depth");
        assertFalse(json.contains("HIDDEN_"), "Hidden version, explanation, path or transcript leaked");
        for (int id : List.of(3, 5, 6, 7, 8, 9)) {
            assertFalse(json.contains(assetId(id)), "Private or different-version asset ID leaked");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"TEACHER", "ADMIN"})
    void approvedRolesReadOnlyCurrentPublishedSnapshotsAndSafeNestedJson(String role) throws Exception {
        role(role);
        var token = token();
        var list = mvc.perform(get("/api/v1/questions").header("Authorization", "Bearer " + token)
                        .param("size", "1").param("sort", "questionId,asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].questionId").value(questionId(1)))
                .andExpect(jsonPath("$.content[0].questionVersionId").value(versionId(1)))
                .andExpect(jsonPath("$.content[0].versionNumber").value(1))
                .andExpect(jsonPath("$.content[0].questionText").value("Alpha prompt"))
                .andExpect(jsonPath("$.content[0].part.partNumber").value(1))
                .andExpect(jsonPath("$.content[0].topic.topicId").value(930001))
                .andExpect(jsonPath("$.content[0].choices").doesNotExist())
                .andExpect(header().string("Cache-Control", containsString("no-store"))).andReturn();
        assertSafe(list.getResponse().getContentAsString());
        var standalone = detail(1, token).andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.choices.length()").value(2))
                .andExpect(jsonPath("$.choices[0].label").value("A"))
                .andExpect(jsonPath("$.choices[1].displayOrder").value(2))
                .andExpect(jsonPath("$.assets.length()").value(2))
                .andExpect(jsonPath("$.assets[0].resourceId").value(assetId(1)))
                .andExpect(jsonPath("$.assets[0].mimeType").value("audio/mpeg"))
                .andExpect(jsonPath("$.assets[0].fileSize").value(1200))
                .andExpect(jsonPath("$.assets[0].durationSeconds").value(30))
                .andExpect(jsonPath("$.passage").isEmpty()).andReturn();
        assertSafe(standalone.getResponse().getContentAsString());
        var grouped = detail(2, token).andExpect(status().isOk())
                .andExpect(jsonPath("$.questionVersionId").value(versionId(2)))
                .andExpect(jsonPath("$.passage.groupId").value(groupId(2)))
                .andExpect(jsonPath("$.passage.groupVersionId").value(groupVersionId(2)))
                .andExpect(jsonPath("$.passage.versionNumber").value(1))
                .andExpect(jsonPath("$.passage.passageText").value("Pinned passage"))
                .andExpect(jsonPath("$.passage.assets.length()").value(2))
                .andExpect(jsonPath("$.passage.assets[0].resourceId").value(assetId(2)))
                .andExpect(jsonPath("$.passage.assets[0].displayOrder").value(1))
                .andExpect(jsonPath("$.passage.assets[1].resourceId").value(assetId(4)))
                .andExpect(jsonPath("$.passage.assets[1].displayOrder").value(2)).andReturn();
        assertSafe(grouped.getResponse().getContentAsString());
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 999})
    void missingOrIneligibleQuestionHasIdentical404AndNoFallback(int number) throws Exception {
        var result = detail(number, token()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUESTION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Khong tim thay cau hoi."))
                .andExpect(jsonPath("$.traceId").isNotEmpty()).andExpect(jsonPath("$.details").isEmpty())
                .andExpect(header().doesNotExist("WWW-Authenticate")).andReturn();
        assertSafe(result.getResponse().getContentAsString());
    }

    @Test
    void filtersPagesAndTotalsUseExactlyTheReadableSet() throws Exception {
        var token = token();
        mvc.perform(get("/api/v1/questions").header("Authorization", "Bearer " + token)
                        .param("partId", jdbc.queryForObject("select part_id from toeic_parts where part_number=1", Integer.class).toString()).param("topicId", "930001").param("difficulty", "EASY"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].questionId").value(questionId(1)));
        mvc.perform(get("/api/v1/questions").header("Authorization", "Bearer " + token).param("partId", jdbc.queryForObject("select part_id from toeic_parts where part_number=7", Integer.class).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].questionId").value(questionId(2)));
        mvc.perform(get("/api/v1/questions").header("Authorization", "Bearer " + token).param("topicId", "999999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/questions").header("Authorization", "Bearer " + token).param("difficulty", "UNKNOWN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/questions").header("Authorization", "Bearer " + token).param("page", "99"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/v1/questions").header("Authorization", "Bearer " + token).param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100)).andExpect(jsonPath("$.content.length()").value(3));
    }

    @ParameterizedTest
    @ValueSource(strings = {"questionId,desc", "partId,asc", "topicId,desc", "difficulty,asc", "publishedAt,desc"})
    void everyAllowedSortPagesStablyWithoutDroppingNullableTaxonomy(String sort) throws Exception {
        var token = token();
        var ids = new HashSet<String>();
        for (int page = 0; page < 3; page++) {
            var result = mvc.perform(get("/api/v1/questions").header("Authorization", "Bearer " + token)
                            .param("sort", sort).param("size", "1").param("page", String.valueOf(page)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.totalElements").value(3)).andReturn();
            var id = mapper.readTree(result.getResponse().getContentAsString()).get("content").get(0).get("questionId").asString();
            assertTrue(ids.add(id), "Duplicate result across stable pages");
        }
        assertEquals(Set.of(questionId(1), questionId(2), questionId(3)), ids);
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "page=2147483647", "size=101", "partId=x", "topicId=0", "difficulty=",
            "sort=explanation,asc", "sort=question.createdBy,asc", "sort=questionId,asc,partId", "status=DRAFT", "versionId=x"})
    void malformedAndUnrecognizedInputReturnsContract400(String parameter) throws Exception {
        var parts = parameter.split("=", 2);
        mvc.perform(get("/api/v1/questions").header("Authorization", "Bearer " + token()).param(parts[0], parts[1]))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.traceId").isNotEmpty()).andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void duplicateParametersAndMalformedQuestionIdReturn400() throws Exception {
        var token = token();
        mvc.perform(get("/api/v1/questions").header("Authorization", "Bearer " + token).param("partId", "1", "2"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/questions/not-a-uuid").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/v1/questions/" + questionId(1)).header("Authorization", "Bearer " + token)
                        .param("versionId", versionId(101))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/questions/" + versionId(1)).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingAuthenticationIs401RatherThanPublicContentBehavior() throws Exception {
        for (String path : List.of("/api/v1/questions", "/api/v1/questions/" + questionId(1))) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", "Bearer"))
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer bad-token", "Basic abc", "Bearer", "Bearer AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
    void invalidAuthenticationIs401(String header) throws Exception {
        mvc.perform(get("/api/v1/questions").header("Authorization", header)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/questions/" + questionId(1)).header("Authorization", header))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredRevokedOrLockedSessionCannotReadQuestions() throws Exception {
        var token = token();
        jdbc.update("update users set status = 'LOCKED' where user_id = ?::uuid", USER);
        detail(1, token).andExpect(status().isUnauthorized());
        jdbc.update("update users set status = 'ACTIVE' where user_id = ?::uuid", USER);
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        detail(1, token).andExpect(status().isUnauthorized());
        var anotherToken = token();
        jdbc.update("update auth_sessions set created_at = now() - interval '2 hours', expires_at = now() - interval '1 hour' where user_id = ?::uuid", USER);
        detail(1, anotherToken).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    void learnerNeverGetsEndpointAccessEvenWithAnUnexpectedPermissionGrant() throws Exception {
        role("LEARNER");
        var token = token();
        detail(1, token).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        jdbc.update("insert into role_permissions select r.role_id, p.permission_id from roles r cross join permissions p where r.name = 'LEARNER' and p.code = 'question:read'");
        try {
            mvc.perform(get("/api/v1/questions").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/questions/not-a-uuid").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        } finally {
            jdbc.update("delete from role_permissions where role_id = (select role_id from roles where name = 'LEARNER') and permission_id = (select permission_id from permissions where code = 'question:read')");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"TEACHER", "ADMIN"})
    void revokingEndpointPermissionTakesEffectNextRequestWithoutAdminBypass(String role) throws Exception {
        role(role);
        var token = token();
        detail(1, token).andExpect(status().isOk());
        jdbc.update("delete from role_permissions where role_id = (select role_id from roles where name = ?) and permission_id = (select permission_id from permissions where code = 'question:read')", role);
        try {
            detail(1, token).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
            mvc.perform(get("/api/v1/questions").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        } finally {
            jdbc.update("insert into role_permissions select r.role_id, p.permission_id from roles r cross join permissions p where r.name = ? and p.code = 'question:read'", role);
        }
        detail(1, token).andExpect(status().isOk());
    }

    @Test
    void publicationChangeHidesDetailAndCountOnTheNextRequest() throws Exception {
        var token = token();
        detail(2, token).andExpect(status().isOk());
        jdbc.update("update question_groups set status = 'ARCHIVED' where group_id = ?::uuid", groupId(2));
        detail(2, token).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/questions").header("Authorization", "Bearer " + token).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void defaultPointersAndGroupPublicationRemainHidden() throws Exception {
        var token = token();
        jdbc.update("update questions set current_published_version_id = DEFAULT where question_id = ?::uuid", questionId(1));
        detail(1, token).andExpect(status().isNotFound());
        jdbc.update("update question_groups set status = DEFAULT where group_id = ?::uuid", groupId(2));
        detail(2, token).andExpect(status().isNotFound());
        jdbc.update("update questions set current_published_version_id = ?::uuid where question_id = ?::uuid", versionId(1), questionId(1));
        jdbc.update("update media_resources set visibility = DEFAULT where resource_id = ?::uuid", assetId(1));
        detail(1, token).andExpect(status().isOk()).andExpect(jsonPath("$.assets.length()").value(1))
                .andExpect(jsonPath("$.assets[0].resourceId").value(assetId(2)));
    }

    @Test
    void queryDeniesBothRowsAndCountWhenTrustedReadScopeIsFalse() {
        new TransactionTemplate(transactions).executeWithoutResult(ignored -> {
            var page = versions.findReadable(false, OffsetDateTime.now(), null, null, null,
                    QuestionReadQuery.parse(Map.of()).pageable());
            assertEquals(0, page.getTotalElements());
            assertTrue(page.getContent().isEmpty());
            assertTrue(versions.findReadableByQuestionId(UUID.fromString(questionId(1)), false, OffsetDateTime.now()).isEmpty());
        });
    }

    @Test
    @WithMockUser(authorities = "question:read")
    void directServiceCannotBeCalledWithAnUntrustedAuthenticationType() {
        assertThrows(AccessDeniedException.class, () -> service.detail(UUID.fromString(questionId(1))));
        assertThrows(AccessDeniedException.class, () -> service.list(QuestionReadQuery.parse(Map.of())));
    }

    @Test
    void directServiceRequiresTheSessionRoleAndPermissionTogether() throws Exception {
        var principal = authentication.authenticate(token());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))));
        SecurityContextHolder.setContext(context);
        try {
            assertThrows(AccessDeniedException.class, () -> service.detail(UUID.fromString(questionId(1))));
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null,
                    List.of(new SimpleGrantedAuthority("ROLE_TEACHER"), new SimpleGrantedAuthority("question:read"))));
            assertEquals(UUID.fromString(questionId(1)), service.detail(UUID.fromString(questionId(1))).questionId());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void assertForeignKey(Runnable action) {
        var error = assertThrows(DataIntegrityViolationException.class, action::run);
        Throwable cause = error;
        while (cause != null && !(cause instanceof SQLException)) { cause = cause.getCause(); }
        assertInstanceOf(SQLException.class, cause);
        assertEquals("23503", ((SQLException) cause).getSQLState());
    }

    @Test
    void questionPointerCannotReferenceAnotherQuestionVersion() {
        assertForeignKey(() -> jdbc.update("update questions set current_published_version_id = ?::uuid where question_id = ?::uuid", versionId(2), questionId(1)));
    }

    @Test
    void groupPointerCannotReferenceAnotherGroupVersion() {
        assertForeignKey(() -> jdbc.update("update question_groups set current_published_version_id = ?::uuid where group_id = ?::uuid", groupVersionId(12), groupId(2)));
    }
}
