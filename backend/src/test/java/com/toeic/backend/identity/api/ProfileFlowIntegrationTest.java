package com.toeic.backend.identity.api;

import com.toeic.backend.identity.application.ProfileUpdate;
import com.toeic.backend.identity.application.SessionAuthenticationService;
import com.toeic.backend.identity.application.UserProfileService;
import com.toeic.backend.identity.domain.User;
import com.toeic.backend.identity.infrastructure.RoleRepository;
import com.toeic.backend.identity.infrastructure.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.jpa.show-sql=false", "debug=false"})
@AutoConfigureMockMvc
class ProfileFlowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserProfileService profiles;
    @Autowired SessionAuthenticationService authentication;

    private static final String PASSWORD = "ProfileTest123!";
    private final List<UUID> created = new ArrayList<>();
    private User actor;
    private User other;

    @BeforeEach
    void setup() {
        actor = account("LEARNER");
        other = account("LEARNER");
    }

    private User account(String role) {
        var user = new User("profile-" + UUID.randomUUID() + "@example.com", encoder.encode(PASSWORD), "Original");
        user.addRole(roles.findByName(role).orElseThrow());
        user.verifyEmail();
        users.saveAndFlush(user);
        created.add(user.getUserId());
        return user;
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        for (var id : created) {
            jdbc.update("delete from learning_profiles where user_id = ?", id);
            jdbc.update("delete from auth_sessions where user_id = ?", id);
            jdbc.update("delete from user_roles where user_id = ?", id);
            jdbc.update("delete from users where user_id = ?", id);
        }
    }

    private String token(User user) throws Exception {
        var result = mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", user.getEmail(), "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
    }

    private ResultActions update(String token, String json) throws Exception {
        return mvc.perform(patch("/api/v1/users/me").header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON).content(json));
    }

    @Test
    void updatesOwnFullNameAndPersistsWithoutChangingSystemFields() throws Exception {
        var before = users.findById(actor.getUserId()).orElseThrow();
        var token = token(actor);
        var response = update(token, "{\"fullName\":\"  Nguyễn Văn A  \"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Nguyễn Văn A"))
                .andExpect(jsonPath("$.userId").value(actor.getUserId().toString()))
                .andExpect(jsonPath("$.roles[0]").value("LEARNER"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(response.matches("(?is).*(password|token).*"));
        var after = users.findById(actor.getUserId()).orElseThrow();
        assertEquals("Nguyễn Văn A", after.getFullName());
        assertEquals(before.getEmail(), after.getEmail());
        assertEquals(before.getPasswordHash(), after.getPasswordHash());
        assertEquals(before.getStatus(), after.getStatus());
        assertEquals(before.getEmailVerifiedAt().toInstant(), after.getEmailVerifiedAt().toInstant());
        assertEquals("Original", users.findById(other.getUserId()).orElseThrow().getFullName());
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Nguyễn Văn A"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"LEARNER", "TEACHER", "ADMIN"})
    void allSeededRolesCanUpdateOwnProfile(String role) throws Exception {
        var user = account(role);
        update(token(user), "{\"fullName\":\"Updated\"}").andExpect(status().isOk());
    }

    @Test
    void nameLengthBoundary() throws Exception {
        var token = token(actor);
        update(token, mapper.writeValueAsString(Map.of("fullName", "a".repeat(150))))
                .andExpect(status().isOk());
        update(token, mapper.writeValueAsString(Map.of("fullName", "a".repeat(151))))
                .andExpect(status().isBadRequest());
        assertEquals("a".repeat(150), users.findById(actor.getUserId()).orElseThrow().getFullName());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "[]", "{", "{\"fullName\":null}", "{\"fullName\":\"\"}",
            "{\"fullName\":\"   \"}", "{\"fullName\":true}", "{\"fullName\":123}",
            "{\"fullName\":{}}", "{\"fullName\":[]}"})
    void invalidPatchIsAtomicAndUsesErrorEnvelope(String json) throws Exception {
        update(token(actor), json).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.traceId").isNotEmpty()).andExpect(jsonPath("$.details").isArray());
        assertEquals("Original", users.findById(actor.getUserId()).orElseThrow().getFullName());
    }

    @ParameterizedTest
    @ValueSource(strings = {"userId", "email", "avatarUrl", "role", "roles", "permission", "permissions",
            "status", "emailVerifiedAt", "passwordHash", "password", "createdAt", "updatedAt", "lastLoginAt",
            "estimatedScore", "listeningLevel", "readingLevel", "targetDate", "unknown"})
    void rejectsEveryNonAllowlistedFieldWithoutPartialWrite(String field) throws Exception {
        update(token(actor), mapper.writeValueAsString(Map.of("fullName", "Must not persist", field, "injected")))
                .andExpect(status().isBadRequest());
        assertEquals("Original", users.findById(actor.getUserId()).orElseThrow().getFullName());
    }

    @Test
    void ignoresForgedQueryActorAndCannotUpdateAnotherPath() throws Exception {
        var token = token(actor);
        mvc.perform(patch("/api/v1/users/me").param("userId", other.getUserId().toString())
                .header("Authorization", "Bearer " + token).contentType(APPLICATION_JSON)
                .content("{\"fullName\":\"Actor only\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(actor.getUserId().toString()));
        mvc.perform(patch("/api/v1/users/" + other.getUserId()).header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON).content("{\"fullName\":\"Other\"}"))
                .andExpect(status().isNotFound());
        assertEquals("Original", users.findById(other.getUserId()).orElseThrow().getFullName());
    }

    @Test
    void missingInvalidAndLockedSessionsReturn401() throws Exception {
        mvc.perform(patch("/api/v1/users/me").contentType(APPLICATION_JSON).content("{\"fullName\":\"A\"}"))
                .andExpect(status().isUnauthorized());
        update("invalid", "{\"fullName\":\"A\"}").andExpect(status().isUnauthorized());
        var token = token(actor);
        jdbc.update("update users set status = 'LOCKED' where user_id = ?", actor.getUserId());
        update(token, "{\"fullName\":\"A\"}").andExpect(status().isUnauthorized());
    }

    @Test
    void revokingPermissionAffectsNextRequestWithoutRelogin() throws Exception {
        var token = token(actor);
        // Remove actor's roles, leaving global grants untouched for other test fixtures.
        jdbc.update("delete from user_roles where user_id = ?", actor.getUserId());
        update(token, "{\"fullName\":\"Denied\"}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertEquals("Original", users.findById(actor.getUserId()).orElseThrow().getFullName());
    }

    @Test
    void serviceGuardsPermissionOwnershipAndPrincipalType() throws Exception {
        var update = new ProfileUpdate("Valid");
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> profiles.update(actor.getUserId(), update));
        var principal = authentication.authenticate(token(actor));
        var context = SecurityContextHolder.createEmptyContext();
        SecurityContextHolder.setContext(context);
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        assertThrows(AccessDeniedException.class, () -> profiles.update(actor.getUserId(), update));
        for (var permission : List.of("user:read:self", "user:update:self")) {
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null,
                    List.of(new SimpleGrantedAuthority(permission))));
            assertThrows(AccessDeniedException.class, () -> profiles.update(actor.getUserId(), update));
        }
        var rights = List.of(new SimpleGrantedAuthority("user:read:self"), new SimpleGrantedAuthority("user:update:self"));
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("forged", null, rights));
        assertThrows(AccessDeniedException.class, () -> profiles.update(actor.getUserId(), update));
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, rights));
        assertThrows(AccessDeniedException.class, () -> profiles.update(other.getUserId(), update));
        assertEquals("Valid", profiles.update(actor.getUserId(), update).fullName());
        var goal = new ProfileUpdate(false, null, true, 750);
        assertThrows(AccessDeniedException.class, () -> profiles.update(other.getUserId(), goal));
        assertEquals(750, profiles.update(actor.getUserId(), goal).targetScore());
    }

    @Test
    void missingGoalReadsAsNullWithoutCreatingAnAnalyticsRow() throws Exception {
        var json = mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token(actor)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(mapper.readTree(json).get("targetScore").isNull());
        assertEquals(0, jdbc.queryForObject("select count(*) from learning_profiles where user_id = ?",
                Integer.class, actor.getUserId()));
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 15, 750, 985, 990})
    void targetScoreBoundariesArePersistedAndReadByOwnProfile(int score) throws Exception {
        var token = token(actor);
        update(token, "{\"targetScore\":" + score + "}").andExpect(status().isOk())
                .andExpect(jsonPath("$.targetScore").value(score)).andExpect(jsonPath("$.fullName").value("Original"));
        assertEquals(score, jdbc.queryForObject("select target_score from learning_profiles where user_id = ?",
                Integer.class, actor.getUserId()));
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.targetScore").value(score));
        var login = mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", actor.getEmail(), "password", PASSWORD))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.targetScore").value(score))
                .andReturn().getResponse().getContentAsString();
        assertFalse(mapper.readTree(login).get("user").toString().matches("(?is).*(password|token).*"));
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token(other)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.targetScore").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"9", "995", "0", "-5", "11", "751", "991", "2147483648", "990.1", "750.0",
            "1e2", "\"750\"", "true", "{}", "[]"})
    void rejectsInvalidGoalWithoutUpdatingEitherField(String score) throws Exception {
        var token = token(actor);
        update(token, "{\"targetScore\":750}").andExpect(status().isOk());
        update(token, "{\"fullName\":\"Must not persist\",\"targetScore\":" + score + "}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertEquals("Original", users.findById(actor.getUserId()).orElseThrow().getFullName());
        assertEquals(750, jdbc.queryForObject("select target_score from learning_profiles where user_id = ?",
                Integer.class, actor.getUserId()));
    }

    @Test
    void partialPatchPreservesOmittedFieldsAndClearsExplicitNullWithoutTouchingAnalytics() throws Exception {
        var token = token(actor);
        update(token, "{\"fullName\":\"Combined\",\"targetScore\":800}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Combined"))
                .andExpect(jsonPath("$.targetScore").value(800));
        jdbc.update("update learning_profiles set estimated_score=600, listening_level='B1', reading_level='B2' where user_id=?",
                actor.getUserId());
        update(token, "{\"fullName\":\"Name only\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.targetScore").value(800));
        update(token, "{\"targetScore\":850}").andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Name only"));
        var result = update(token, "{\"targetScore\":null}").andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Name only")).andReturn();
        assertTrue(mapper.readTree(result.getResponse().getContentAsString()).get("targetScore").isNull());
        assertNull(jdbc.queryForObject("select target_score from learning_profiles where user_id=?",
                Integer.class, actor.getUserId()));
        var analytics = jdbc.queryForMap("select estimated_score,listening_level,reading_level from learning_profiles where user_id=?",
                actor.getUserId());
        assertEquals(600, analytics.get("estimated_score"));
        assertEquals("B1", analytics.get("listening_level"));
        assertEquals("B2", analytics.get("reading_level"));
        assertEquals(1, jdbc.queryForObject("select count(*) from learning_profiles where user_id=?",
                Integer.class, actor.getUserId()));
    }

    @Test
    void clearingMissingGoalDoesNotCreateRowAndInvalidNamePreventsGoalWrite() throws Exception {
        var token = token(actor);
        update(token, "{\"targetScore\":null}").andExpect(status().isOk());
        update(token, "{\"fullName\":null,\"targetScore\":750}").andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("select count(*) from learning_profiles where user_id=?",
                Integer.class, actor.getUserId()));
    }
}
