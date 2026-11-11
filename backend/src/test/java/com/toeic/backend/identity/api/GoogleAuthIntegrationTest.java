package com.toeic.backend.identity.api;

import com.toeic.backend.identity.application.*;
import com.toeic.backend.identity.domain.User;
import com.toeic.backend.identity.infrastructure.*;
import jakarta.servlet.http.Cookie;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = { "app.auth.google.client-id=test-client.apps.googleusercontent.com", "debug=false" })
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(GoogleAuthIntegrationTest.Beans.class)
class GoogleAuthIntegrationTest {
    @TestConfiguration static class Beans {
        @Bean @Primary GoogleTokenVerifier googleVerifier() { return mock(GoogleTokenVerifier.class); }
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired GoogleTokenVerifier verifier;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    private String email, subject, credential, localPassword;
    private final List<UUID> challengeIds = new ArrayList<>();

    record Challenge(UUID id, String nonce, Cookie cookie) { }
    @BeforeEach void setup() {
        reset(verifier);
        email = UUID.randomUUID() + "@gmail.com"; subject = UUID.randomUUID().toString();
        credential = UUID.randomUUID().toString(); localPassword = UUID.randomUUID().toString();
    }
    @AfterEach void cleanup() {
        users.findByEmail(email).ifPresent(user -> {
            var id = user.getUserId();
            jdbc.update("delete from google_identities where user_id=?", id);
            jdbc.update("delete from auth_sessions where user_id=?", id);
            jdbc.update("delete from user_roles where user_id=?", id);
            jdbc.update("delete from users where user_id=?", id);
        });
        for (var id : challengeIds) jdbc.update("delete from google_login_challenges where challenge_id=?", id);
        challengeIds.clear();
    }
    private Challenge challenge() throws Exception {
        var response = mvc.perform(post("/api/v1/auth/google/challenge")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse();
        var body = json.readTree(response.getContentAsString());
        var id = UUID.fromString(body.get("challengeId").asString()); challengeIds.add(id);
        String cookie = response.getHeader("Set-Cookie");
        assertTrue(cookie.contains("HttpOnly")); assertTrue(cookie.contains("SameSite=Strict"));
        var pair = cookie.substring(0, cookie.indexOf(';')).split("=", 2);
        return new Challenge(id, body.get("nonce").asString(), new Cookie(pair[0], pair[1]));
    }
    private void accept(Challenge challenge) {
        when(verifier.verify(credential)).thenReturn(new GoogleTokenVerifier.Identity(subject, email, "Google learner", challenge.nonce(), true));
    }
    private ResultActions login(Challenge challenge, String password) throws Exception {
        var body = new HashMap<String, Object>(); body.put("challengeId", challenge.id()); body.put("credential", credential);
        if (password != null) body.put("currentPassword", password);
        return mvc.perform(post("/api/v1/auth/google").cookie(challenge.cookie()).contentType(APPLICATION_JSON).content(json.writeValueAsString(body)));
    }
    private User local(boolean active) {
        var user = new User(email, passwords.encode(localPassword), "Local learner");
        user.addRole(roles.findByName("LEARNER").orElseThrow()); if (active) user.verifyEmail();
        return users.saveAndFlush(user);
    }
    @Test void newGoogleUserGetsActiveLearnerAndNormalRevocableSession() throws Exception {
        var challenge = challenge(); accept(challenge);
        var response = login(challenge, null).andExpect(status().isOk()).andExpect(jsonPath("$.user.status").value("ACTIVE"))
                .andExpect(jsonPath("$.user.localLoginEnabled").value(false)).andExpect(jsonPath("$.user.roles[0]").value("LEARNER"))
                .andReturn().getResponse();
        var token = json.readTree(response.getContentAsString()).get("accessToken").asString();
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        assertNull(users.findByEmail(email).orElseThrow().getPasswordHash());
        login(challenge, null).andExpect(status().isUnauthorized());
    }
    @Test void existingEmailRequiresPasswordBeforeLinkingAndPreservesLocalLogin() throws Exception {
        var before = local(true); var challenge = challenge(); accept(challenge);
        login(challenge, null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GOOGLE_LINK_REQUIRED"));
        assertEquals(0, jdbc.queryForObject("select count(*) from google_identities where user_id=?", Integer.class, before.getUserId()));
        login(challenge, localPassword).andExpect(status().isOk()).andExpect(jsonPath("$.user.userId").value(before.getUserId().toString()));
        mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON).content(json.writeValueAsString(Map.of("email", email, "password", localPassword))))
                .andExpect(status().isOk());
        var next = challenge(); accept(next); login(next, null).andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject("select count(*) from google_identities where user_id=?", Integer.class, before.getUserId()));
    }
    @Test void wrongPasswordDoesNotLink() throws Exception {
        var before = local(true); var challenge = challenge(); accept(challenge);
        login(challenge, UUID.randomUUID().toString()).andExpect(status().isUnauthorized());
        assertEquals(0, jdbc.queryForObject("select count(*) from google_identities where user_id=?", Integer.class, before.getUserId()));
    }
    @Test void pendingAccountIsNeverSilentlyActivated() throws Exception {
        local(false); var challenge = challenge(); accept(challenge);
        login(challenge, localPassword).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GOOGLE_ACCOUNT_NOT_ACTIVE"));
        assertEquals("PENDING", users.findByEmail(email).orElseThrow().getStatus());
    }
    @Test void missingCookieWrongNonceExpiredChallengeAndReplayFail() throws Exception {
        var challenge = challenge(); accept(challenge);
        mvc.perform(post("/api/v1/auth/google").contentType(APPLICATION_JSON).content(json.writeValueAsString(Map.of("challengeId", challenge.id(), "credential", credential))))
                .andExpect(status().isUnauthorized());
        when(verifier.verify(credential)).thenReturn(new GoogleTokenVerifier.Identity(subject, email, "Learner", "x".repeat(43), true));
        login(challenge, null).andExpect(status().isUnauthorized());
        jdbc.update("update google_login_challenges set created_at=now()-interval '10 minutes',expires_at=now()-interval '5 minutes' where challenge_id=?", challenge.id());
        accept(challenge); login(challenge, null).andExpect(status().isUnauthorized());
        assertTrue(users.findByEmail(email).isEmpty());
    }
    @Test void thirdPartyGoogleEmailCanSignUpWithoutClaimingSystemEmailProof() throws Exception {
        var challenge = challenge();
        when(verifier.verify(credential)).thenReturn(new GoogleTokenVerifier.Identity(subject, email, "Learner", challenge.nonce(), false));
        login(challenge, null).andExpect(status().isOk()).andExpect(jsonPath("$.user.status").value("ACTIVE"));
        assertNull(users.findByEmail(email).orElseThrow().getEmailVerifiedAt());
    }
    @Test void blockedLinkedAccountCannotLogInAndNoRoleEscalation() throws Exception {
        var user = local(true); var challenge = challenge(); accept(challenge); login(challenge, localPassword).andExpect(status().isOk());
        jdbc.update("update users set status='BLOCKED' where user_id=?", user.getUserId());
        var next = challenge(); accept(next); login(next, null).andExpect(status().isUnauthorized());
    }
}
