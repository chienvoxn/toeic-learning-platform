package com.toeic.backend.content.api;

import com.toeic.backend.content.application.ContentQuery;
import com.toeic.backend.content.application.ContentReadService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.jpa.show-sql=false", "debug=false"})
@AutoConfigureMockMvc
@Sql(scripts = "/fixtures/content-read.sql", config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
@Sql(scripts = "/fixtures/content-read-cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD,
        config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
class ContentReadIntegrationTest {
    private static final String USER_ID = "10000000-0000-0000-0000-000000000001";
    private static final String TEST_PASSWORD = "ContentFixture123!";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper mapper;
    @Autowired ContentReadService contents;

    private static String contentId(int number) { return "20000000-0000-0000-0000-%012d".formatted(number); }
    private static String mediaId(int number) { return "40000000-0000-0000-0000-%012d".formatted(number); }

    @BeforeEach
    void password() {
        jdbc.update("update users set password_hash = ? where user_id = ?::uuid", encoder.encode(TEST_PASSWORD), USER_ID);
    }

    private String token() throws Exception {
        var result = mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", "content-fixture@example.invalid",
                                "password", TEST_PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
    }

    private ResultActions detail(int number, String token) throws Exception {
        var request = get("/api/v1/contents/" + contentId(number));
        if (token != null) { request.header("Authorization", "Bearer " + token); }
        return mvc.perform(request);
    }

    @Test
    void guestListReadsOnlyCurrentPublishedPublicVersionsAndDoesNotCountHiddenRows() throws Exception {
        mvc.perform(get("/api/v1/contents").param("size", "1").param("sort", "title,asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1)).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].contentId").value(contentId(1)))
                .andExpect(jsonPath("$.content[0].title").value("Alpha published"))
                .andExpect(jsonPath("$.content[0].category.code").value("CONTENT_TEST_LISTENING"))
                .andExpect(jsonPath("$.content[0].body").doesNotExist())
                .andExpect(jsonPath("$.content[0].authorId").doesNotExist())
                .andExpect(content().string(not(containsString("LEAK_"))));
    }

    @Test
    void publishedDetailReturnsPinnedVersionAndOnlyVisibleVersionMediaMetadata() throws Exception {
        detail(1, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.contentVersionId").value("30000000-0000-0000-0000-000000000001"))
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.body").value("Published fixture body"))
                .andExpect(jsonPath("$.media.length()").value(1))
                .andExpect(jsonPath("$.media[0].resourceId").value(mediaId(1)))
                .andExpect(jsonPath("$.media[0].resourceType").value("AUDIO"))
                .andExpect(jsonPath("$.media[0].mimeType").value("audio/mpeg"))
                .andExpect(jsonPath("$.media[0].fileSize").value(1234))
                .andExpect(jsonPath("$.media[0].durationSeconds").value(30))
                .andExpect(jsonPath("$.media[0].displayOrder").value(1))
                .andExpect(jsonPath("$.media[0].storageUrl").doesNotExist())
                .andExpect(jsonPath("$.media[0].transcript").doesNotExist())
                .andExpect(content().string(not(containsString("LEAK_"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"LEARNER", "TEACHER", "ADMIN"})
    void seededRoleCanReadMemberPublishedButCannotReadPrivateOrDraft(String role) throws Exception {
        jdbc.update("delete from user_roles where user_id = ?::uuid", USER_ID);
        jdbc.update("insert into user_roles (user_id, role_id) select ?::uuid, role_id from roles where name = ?", USER_ID, role);
        var token = token();
        mvc.perform(get("/api/v1/contents").header("Authorization", "Bearer " + token).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
        detail(3, token).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Member published"));
        detail(4, token).andExpect(status().isNotFound());
        detail(5, token).andExpect(status().isNotFound());
        detail(1, token).andExpect(status().isOk()).andExpect(jsonPath("$.media.length()").value(2))
                .andExpect(jsonPath("$.media[1].resourceId").value(mediaId(2)))
                .andExpect(content().string(not(containsString("LEAK_"))));
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4, 5, 6, 7, 8, 9, 10, 11, 999})
    void hiddenOrMissingContentAlwaysReturnsTheSame404(int number) throws Exception {
        detail(number, null).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Khong tim thay hoc lieu."))
                .andExpect(jsonPath("$.traceId").isNotEmpty()).andExpect(jsonPath("$.details").isEmpty())
                .andExpect(content().string(not(containsString("LEAK_"))));
    }

    @Test
    void filtersAndPaginationUseOnlyAllowedRows() throws Exception {
        mvc.perform(get("/api/v1/contents").param("categoryId", "900001").param("difficulty", "EASY"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].contentId").value(contentId(1)));
        mvc.perform(get("/api/v1/contents").param("difficulty", "SECRET"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/contents").param("sort", "title,asc").param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].contentId").value(contentId(2)))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/contents").param("page", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/contents").param("categoryId", "999999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/contents").param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
    }

    @ParameterizedTest
    @ValueSource(strings = {"title,desc", "publishedAt,asc", "difficulty,desc", "contentId,asc"})
    void allowedSortFieldsReturnStablePage(String sort) throws Exception {
        mvc.perform(get("/api/v1/contents").param("sort", sort).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "page=x", "page=2147483647", "size=0", "size=101", "size=x",
            "categoryId=0", "categoryId=bad", "difficulty=", "difficulty=123456789012345678901",
            "part=1", "visibility=PRIVATE", "sort=body,asc", "sort=title,sideways", "sort=title", "sort=title,asc,body"})
    void invalidOrUnsupportedInputReturns400(String parameter) throws Exception {
        var pieces = parameter.split("=", 2);
        mvc.perform(get("/api/v1/contents").param(pieces[0], pieces[1]))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.traceId").isNotEmpty()).andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void duplicateSortAndMalformedIdAreRejected() throws Exception {
        mvc.perform(get("/api/v1/contents").param("sort", "title,asc", "body,asc"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/contents/not-a-uuid")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void grantAndRevokePermissionApplyToSameTokenWithoutExposingTotalsOrMedia() throws Exception {
        jdbc.update("delete from user_roles where user_id = ?::uuid", USER_ID);
        jdbc.update("insert into user_roles (user_id, role_id) values (?::uuid, 900003)", USER_ID);
        var token = token();
        detail(3, token).andExpect(status().isNotFound());
        jdbc.update("insert into role_permissions select 900003, permission_id from permissions where code = 'content:read'");
        detail(3, token).andExpect(status().isOk());
        jdbc.update("delete from role_permissions where role_id = 900003");
        detail(3, token).andExpect(status().isNotFound());
        detail(1, token).andExpect(status().isOk()).andExpect(jsonPath("$.media.length()").value(1));
        mvc.perform(get("/api/v1/contents").header("Authorization", "Bearer " + token).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void invalidRevokedAndLockedSessionsCannotReadAsMembersOrSilentlyBecomeGuests() throws Exception {
        mvc.perform(get("/api/v1/contents").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", "Bearer"));
        var token = token();
        jdbc.update("update users set status = 'LOCKED' where user_id = ?::uuid", USER_ID);
        detail(1, token).andExpect(status().isUnauthorized());
        jdbc.update("update users set status = 'ACTIVE' where user_id = ?::uuid", USER_ID);
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        detail(1, token).andExpect(status().isUnauthorized());
    }

    @Test
    void serviceCannotBeGivenClientSuppliedMemberFlag() {
        var page = contents.list(new ContentQuery(null, null, 0, 1, "title,asc"));
        assertEquals(2, page.totalElements());
        assertThrows(com.toeic.backend.content.application.ContentException.class,
                () -> contents.detail(UUID.fromString(contentId(3))));
    }

    @Test
    void defaultVisibilityHidesContentAndMediaUnlessExplicitlyClassified() throws Exception {
        jdbc.update("update learning_contents set visibility = DEFAULT where content_id = ?::uuid", contentId(1));
        detail(1, null).andExpect(status().isNotFound());
        jdbc.update("update learning_contents set visibility = 'PUBLIC' where content_id = ?::uuid", contentId(1));
        jdbc.update("update media_resources set visibility = DEFAULT where resource_id = ?::uuid", mediaId(1));
        detail(1, null).andExpect(status().isOk()).andExpect(jsonPath("$.media").isEmpty());
    }
}
