package com.toeic.backend.identity.api;

import com.toeic.backend.identity.domain.User;
import com.toeic.backend.identity.infrastructure.OtpMailService;
import com.toeic.backend.identity.infrastructure.RoleRepository;
import com.toeic.backend.identity.infrastructure.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.jpa.show-sql=false", "debug=false"})
@AutoConfigureMockMvc
@Import({AuthFlowIntegrationTest.TestBeans.class, AuthFlowIntegrationTest.AdminProbe.class})
class AuthFlowIntegrationTest {
    @TestConfiguration
    static class TestBeans {
        @Bean @Primary
        MutableClock authTestClock() { return new MutableClock(); }

        @Bean @Primary
        OtpMailService authTestMail() { return mock(OtpMailService.class); }
    }

    // Exists only in tests to exercise both sides of the production ADMIN rule.
    @RestController
    static class AdminProbe {
        @GetMapping("/api/v1/admin/auth-probe")
        Map<String, String> probe() { return Map.of("status", "ok"); }
    }

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>();
        void reset() { now.set(Instant.now().truncatedTo(ChronoUnit.SECONDS)); }
        void advance(long seconds) { now.updateAndGet(value -> value.plusSeconds(seconds)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return now.get(); }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired OtpMailService mail;
    @Autowired PlatformTransactionManager transactionManager;

    private final List<UUID> createdUsers = new ArrayList<>();
    private String email;
    private final String password = "TestPass123!";
    private User account;

    @BeforeEach
    void setup() {
        clock.reset();
        reset(mail);
        email = UUID.randomUUID() + "@example.com";
        account = new User(email, encoder.encode(password), "Learner");
        account.addRole(roles.findByName("LEARNER").orElseThrow());
        account.verifyEmail();
        users.saveAndFlush(account);
        createdUsers.add(account.getUserId());
    }

    @AfterEach
    void cleanup() {
        for (var id : createdUsers) {
            jdbc.update("delete from auth_sessions where user_id = ?", id);
            jdbc.update("delete from email_verification_otps where user_id = ?", id);
            jdbc.update("delete from user_roles where user_id = ?", id);
            jdbc.update("delete from users where user_id = ?", id);
        }
    }

    private ResultActions login(String loginEmail, String loginPassword) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", loginEmail, "password", loginPassword))));
    }

    private String token() throws Exception {
        var result = login(email, password).andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
    }

    private ResultActions me(String token) throws Exception {
        return mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token));
    }

    @Test
    void loginNormalizesEmailAndPersistsOnlyTokenHash() throws Exception {
        var result = login(email.toUpperCase(Locale.ROOT), password)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.userId").value(account.getUserId().toString()))
                .andExpect(jsonPath("$.user.roles[0]").value("LEARNER"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn();
        String token = mapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
        assertTrue(token.matches("[A-Za-z0-9_-]{43}"));
        String hash = jdbc.queryForObject("select token_hash from auth_sessions where user_id = ?",
                String.class, account.getUserId());
        assertNotEquals(token, hash);
        assertTrue(hash.matches("[a-f0-9]{64}"));
        assertNotNull(users.findById(account.getUserId()).orElseThrow().getLastLoginAt());
        me(token).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordAndUnknownEmailHaveSameGenericErrorAndCreateNoSession() throws Exception {
        for (var credentials : List.of(Map.entry(email, "wrong-password"),
                Map.entry("unknown-" + email, password))) {
            login(credentials.getKey(), credentials.getValue())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.traceId").isNotEmpty())
                    .andExpect(jsonPath("$.details").isArray());
        }
        assertEquals(0, jdbc.queryForObject("select count(*) from auth_sessions where user_id = ?",
                Integer.class, account.getUserId()));
        assertNull(users.findById(account.getUserId()).orElseThrow().getLastLoginAt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "LOCKED", "DISABLED"})
    void nonActiveAccountCannotLogin(String status) throws Exception {
        jdbc.update("update users set status = ? where user_id = ?",
                status, account.getUserId());
        if ("PENDING".equals(status)) {
            jdbc.update("update users set email_verified_at = null where user_id = ?", account.getUserId());
        }
        login(email, password).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        assertEquals(0, jdbc.queryForObject("select count(*) from auth_sessions where user_id = ?",
                Integer.class, account.getUserId()));
    }

    @Test
    void meUsesAuthenticatedAccountAndNeverReturnsSecrets() throws Exception {
        var token = token();
        mvc.perform(get("/api/v1/users/me").param("userId", UUID.randomUUID().toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(account.getUserId().toString()))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.fullName").value("Learner"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.roles[0]").value("LEARNER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password_hash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.tokenHash").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void missingTokenReturns401ForMeAndLogout() throws Exception {
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Bearer", "Basic abc", "Bearer bad-token", "Bearer AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
    void invalidAuthorizationReturns401(String authorization) throws Exception {
        mvc.perform(get("/api/v1/users/me").header("Authorization", authorization))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void duplicateAuthorizationHeadersAreRejected() throws Exception {
        var token = token();
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiryIsEnforcedAtExactBoundaryForMeAndLogout() throws Exception {
        var token = token();
        clock.advance(899);
        me(token).andExpect(status().isOk());
        clock.advance(1);
        me(token).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    void logoutRevokesOnlyCurrentSessionAndReplayFails() throws Exception {
        var first = token();
        var second = token();
        assertNotEquals(first, second);
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + first))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        me(first).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + first))
                .andExpect(status().isUnauthorized());
        me(second).andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from auth_sessions where user_id = ? and revoked_at is not null",
                Integer.class, account.getUserId()));
    }

    @Test
    void lockingAnAccountStopsItsExistingToken() throws Exception {
        var token = token();
        jdbc.update("update users set status = 'LOCKED' where user_id = ?", account.getUserId());
        me(token).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void loginTimestampUpdateDoesNotUndoAConcurrentAccountLock() throws Exception {
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
            var staleUser = users.findById(account.getUserId()).orElseThrow();
            var concurrentTransaction = new TransactionTemplate(transactionManager);
            concurrentTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            concurrentTransaction.executeWithoutResult(other -> jdbc.update(
                    "update users set status = 'LOCKED' where user_id = ?", account.getUserId()));
            // Login loaded ACTIVE before another transaction committed the lock.
            staleUser.recordLogin(OffsetDateTime.now(clock));
        });
        assertEquals("LOCKED", users.findById(account.getUserId()).orElseThrow().getStatus());
        login(email, password).andExpect(status().isUnauthorized());
    }

    @Test
    void roleChangesApplyToExistingTokenAndDistinguish401From403() throws Exception {
        mvc.perform(get("/api/v1/admin/auth-probe")).andExpect(status().isUnauthorized());
        var token = token();
        mvc.perform(get("/api/v1/admin/auth-probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        var adminRoleId = roles.findByName("ADMIN").orElseThrow().getRoleId();
        jdbc.update("insert into user_roles (user_id, role_id) values (?, ?)", account.getUserId(), adminRoleId);
        mvc.perform(get("/api/v1/admin/auth-probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        me(token).andExpect(jsonPath("$.roles[0]").value("ADMIN"));
        jdbc.update("delete from user_roles where user_id = ? and role_id = ?", account.getUserId(), adminRoleId);
        mvc.perform(get("/api/v1/admin/auth-probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginValidationHandlesMalformedInputAndBcryptByteLimit() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        login("bad-email", "").andExpect(status().isBadRequest());
        login(email, "ậ".repeat(30)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void registrationResendAndVerificationRemainPublicAndFeedLogin() throws Exception {
        var newEmail = "new-" + email;
        mvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer stale-token")
                        .content(mapper.writeValueAsString(Map.of("email", newEmail, "password", password,
                                "fullName", "New learner"))))
                .andExpect(status().isCreated());
        createdUsers.add(users.findByEmail(newEmail).orElseThrow().getUserId());
        login(newEmail, password).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/resend-otp").contentType(APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", "unknown-" + email))))
                .andExpect(status().isOk());
        var otp = ArgumentCaptor.forClass(String.class);
        verify(mail).sendVerification(eq(newEmail), otp.capture());
        mvc.perform(post("/api/v1/auth/verify-email").contentType(APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", newEmail, "otp", otp.getValue()))))
                .andExpect(status().isOk());
        login(newEmail, password).andExpect(status().isOk());
    }
}
