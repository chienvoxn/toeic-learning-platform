package com.toeic.backend.identity.api;

import com.toeic.backend.identity.application.AuthException;
import com.toeic.backend.identity.application.AuthService;
import com.toeic.backend.identity.application.PasswordChange;
import com.toeic.backend.identity.application.PasswordChangeService;
import com.toeic.backend.identity.application.SessionAuthenticationService;
import com.toeic.backend.identity.application.SessionPrincipal;
import com.toeic.backend.identity.domain.User;
import com.toeic.backend.identity.infrastructure.RoleRepository;
import com.toeic.backend.identity.infrastructure.UserRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.jpa.show-sql=false", "debug=false", "logging.level.org.hibernate.orm.jdbc.error=OFF"})
@AutoConfigureMockMvc
class PasswordChangeIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordChangeService passwords;
    @Autowired SessionAuthenticationService authentication;
    @Autowired AuthService auth;
    @Autowired PlatformTransactionManager transactions;

    private static final String OLD = "PasswordOld123!";
    private static final String NEW = "PasswordNew456!";
    private final List<UUID> created = new ArrayList<>();
    private User actor;
    private User other;

    @BeforeEach
    void setup() {
        actor = account("LEARNER");
        other = account("LEARNER");
    }

    private User account(String role) {
        var user = new User("password-test-" + UUID.randomUUID() + "@example.com", encoder.encode(OLD), "Test User");
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
            jdbc.update("delete from auth_sessions where user_id=?", id);
            jdbc.update("delete from user_roles where user_id=?", id);
            jdbc.update("delete from users where user_id=?", id);
        }
    }

    private ResultActions login(User user, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", user.getEmail(), "password", password))));
    }

    private String token(User user, String password) throws Exception {
        var result = login(user, password).andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asString();
    }

    private ResultActions change(String token, String current, String next) throws Exception {
        return changeJson(token, mapper.writeValueAsString(Map.of("currentPassword", current, "newPassword", next)));
    }

    private ResultActions changeJson(String token, String json) throws Exception {
        return mvc.perform(post("/api/v1/users/me/password").header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON).content(json));
    }

    private String hash(User user) { return users.findById(user.getUserId()).orElseThrow().getPasswordHash(); }

    private int liveSessions(User user) {
        return jdbc.queryForObject("select count(*) from auth_sessions where user_id=? and revoked_at is null",
                Integer.class, user.getUserId());
    }

    private void setActor(SessionPrincipal principal) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("user:password:change:self"))));
        SecurityContextHolder.setContext(context);
    }

    @Test
    void successChangesHashRevokesEveryOldSessionAndRequiresNewLogin() throws Exception {
        var first = token(actor, OLD);
        var second = token(actor, OLD);
        var otherToken = token(other, OLD);
        var before = hash(actor);
        change(first, OLD, NEW).andExpect(status().isNoContent())
                .andExpect(content().string("")).andExpect(header().doesNotExist("Authorization"));
        assertNotEquals(before, hash(actor));
        assertTrue(encoder.matches(NEW, hash(actor)));
        assertEquals(0, liveSessions(actor));
        for (var oldToken : List.of(first, second)) {
            mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + oldToken))
                    .andExpect(status().isUnauthorized());
            change(oldToken, NEW, "AnotherPassword123!").andExpect(status().isUnauthorized());
        }
        authentication.authenticate(otherToken);
        login(actor, OLD).andExpect(status().isUnauthorized());
        var newToken = token(actor, NEW);
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.accessToken").doesNotExist());
        assertEquals(1, liveSessions(actor));
        assertEquals(1, liveSessions(other));
    }

    @Test
    void wrongCurrentPasswordDoesNotChangeHashOrRevokeAnySessionAndDoesNotEchoSecrets() throws Exception {
        var token = token(actor, OLD);
        var second = token(actor, OLD);
        var before = hash(actor);
        var result = change(token, "IncorrectPassword123!", NEW).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"))
                .andExpect(jsonPath("$.traceId").isNotEmpty()).andExpect(jsonPath("$.details").isArray())
                .andReturn().getResponse().getContentAsString();
        assertFalse(result.contains(NEW) || result.contains(OLD) || result.contains("IncorrectPassword123!")
                || result.contains(token) || result.contains(before));
        assertEquals(before, hash(actor));
        assertEquals(2, liveSessions(actor));
        authentication.authenticate(token);
        authentication.authenticate(second);
        login(actor, OLD).andExpect(status().isOk());
        login(actor, NEW).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "[]", "{", "{\"currentPassword\":null,\"newPassword\":\"Password123!\"}",
            "{\"currentPassword\":\"PasswordOld123!\"}", "{\"newPassword\":\"Password123!\"}",
            "{\"currentPassword\":123,\"newPassword\":\"Password123!\"}",
            "{\"currentPassword\":\"PasswordOld123!\",\"newPassword\":123}",
            "{\"currentPassword\":\"PasswordOld123!\",\"newPassword\":null}",
            "{\"currentPassword\":\"PasswordOld123!\",\"newPassword\":\"Password123!\",\"userId\":\"other\"}",
            "{\"currentPassword\":\"PasswordOld123!\",\"newPassword\":\"Password123!\",\"roles\":[\"ADMIN\"]}"})
    void malformedOrNonAllowlistedPayloadDoesNotChangeCredentialsOrSessions(String json) throws Exception {
        var token = token(actor, OLD);
        var before = hash(actor);
        var response = changeJson(token, json).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR")).andReturn().getResponse().getContentAsString();
        assertFalse(response.contains(OLD) || response.contains(token) || response.contains(before));
        assertEquals(before, hash(actor));
        authentication.authenticate(token);
        assertEquals(1, liveSessions(actor));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "       ", "short12", "ậậậậậậậậậậậậậậậậậậậậậậậậậ"})
    void newPasswordPolicyAndUtf8LimitRejectWithoutSideEffects(String next) throws Exception {
        var token = token(actor, OLD);
        var before = hash(actor);
        change(token, OLD, next).andExpect(status().isBadRequest());
        assertEquals(before, hash(actor));
        assertEquals(1, liveSessions(actor));
        authentication.authenticate(token);
    }

    @Test
    void rejectsSeventyThreeBytesAndBlankOrOversizedCurrentPassword() throws Exception {
        var token = token(actor, OLD);
        var before = hash(actor);
        for (var next : List.of("x".repeat(73), "ậ".repeat(24) + "x")) {
            change(token, OLD, next).andExpect(status().isBadRequest());
        }
        for (var current : List.of("", "   ", "x".repeat(73), "ậ".repeat(25))) {
            change(token, current, NEW).andExpect(status().isBadRequest());
        }
        assertEquals(before, hash(actor));
        authentication.authenticate(token);
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678", "ậậậậậậậậậậậậậậậậậậậậậậậậ"})
    void acceptsMinimumLengthAndExactlySeventyTwoUtf8BytesWithoutTrimming(String next) throws Exception {
        var token = token(actor, OLD);
        change(token, OLD, next).andExpect(status().isNoContent());
        token(actor, next);
    }

    @Test
    void acceptsSeventyTwoAsciiCharactersAndPreservesPasswordWhitespace() throws Exception {
        var token = token(actor, OLD);
        var next = " " + "x".repeat(70) + " ";
        change(token, OLD, next).andExpect(status().isNoContent());
        token(actor, next);
        login(actor, next.strip()).andExpect(status().isUnauthorized());
    }

    @Test
    void unicodeCurrentPasswordCanBeVerifiedAtTheBcryptByteBoundary() throws Exception {
        var boundary = "ậ".repeat(24);
        jdbc.update("update users set password_hash=? where user_id=?", encoder.encode(boundary), actor.getUserId());
        var token = token(actor, boundary);
        change(token, boundary, NEW).andExpect(status().isNoContent());
        login(actor, boundary).andExpect(status().isUnauthorized());
        token(actor, NEW);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LEARNER", "TEACHER", "ADMIN"})
    void allSeededRolesCanChangeOwnPassword(String role) throws Exception {
        var user = account(role);
        change(token(user, OLD), OLD, NEW).andExpect(status().isNoContent());
        assertTrue(encoder.matches(NEW, hash(user)));
    }

    @Test
    void authenticationPermissionAndActorIsolationAreEnforced() throws Exception {
        mvc.perform(post("/api/v1/users/me/password").contentType(APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("currentPassword", OLD, "newPassword", NEW))))
                .andExpect(status().isUnauthorized());
        change("invalid", OLD, NEW).andExpect(status().isUnauthorized());
        var token = token(actor, OLD);
        var otherToken = token(other, OLD);
        jdbc.update("delete from user_roles where user_id=?", actor.getUserId());
        change(token, OLD, NEW).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        authentication.authenticate(token);
        assertTrue(encoder.matches(OLD, hash(actor)));
        assertTrue(encoder.matches(OLD, hash(other)));
        authentication.authenticate(otherToken);
        jdbc.update("update users set status='LOCKED' where user_id=?", other.getUserId());
        change(otherToken, OLD, NEW).andExpect(status().isUnauthorized());
    }

    @Test
    void serviceRequiresPermissionTrustedPrincipalOwnUserAndLiveOwnSession() throws Exception {
        var token = token(actor, OLD);
        var principal = authentication.authenticate(token);
        var command = new PasswordChange(OLD, NEW);
        assertEquals("PasswordChange[redacted]", command.toString());
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> passwords.change(actor.getUserId(), command));
        var context = SecurityContextHolder.createEmptyContext();
        SecurityContextHolder.setContext(context);
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        assertThrows(AccessDeniedException.class, () -> passwords.change(actor.getUserId(), command));
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("forged", null,
                List.of(new SimpleGrantedAuthority("user:password:change:self"))));
        assertThrows(AccessDeniedException.class, () -> passwords.change(actor.getUserId(), command));
        setActor(principal);
        assertThrows(AccessDeniedException.class, () -> passwords.change(other.getUserId(), command));
        passwords.change(actor.getUserId(), command);
        // Even a request authenticated before the change must recheck its session after obtaining the lock.
        assertThrows(AuthException.class, () -> passwords.change(actor.getUserId(), new PasswordChange(NEW, OLD)));
        assertTrue(encoder.matches(NEW, hash(actor)));
    }

    @Test
    void hashAndAllRevocationsRollBackTogether() throws Exception {
        var first = token(actor, OLD);
        var second = token(actor, OLD);
        var before = hash(actor);
        setActor(authentication.authenticate(first));
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            passwords.change(actor.getUserId(), new PasswordChange(OLD, NEW));
            assertTrue(encoder.matches(NEW, jdbc.queryForObject("select password_hash from users where user_id=?",
                    String.class, actor.getUserId())));
            assertEquals(0, liveSessions(actor));
            throw new IllegalStateException("Force rollback after credential and revocation writes");
        }));
        assertEquals(before, hash(actor));
        assertEquals(2, liveSessions(actor));
        authentication.authenticate(first);
        authentication.authenticate(second);
    }

    @Test
    void revocationWriteFailureRollsBackTheAlreadyFlushedNewHash() throws Exception {
        var first = token(actor, OLD);
        var second = token(actor, OLD);
        var before = hash(actor);
        setActor(authentication.authenticate(first));
        // A fixture-scoped DB constraint forces the session write to fail after the password flush.
        // The UUID comes from the generated fixture, never from request input.
        jdbc.execute("alter table auth_sessions add constraint auth04_test_revoke_failure check (user_id <> '"
                + actor.getUserId() + "'::uuid or revoked_at is null) not valid");
        try {
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> passwords.change(actor.getUserId(), new PasswordChange(OLD, NEW)));
        } finally {
            jdbc.execute("alter table auth_sessions drop constraint auth04_test_revoke_failure");
        }
        assertEquals(before, hash(actor));
        assertEquals(2, liveSessions(actor));
        authentication.authenticate(first);
        authentication.authenticate(second);
    }

    @Test
    void revokesExpiredSessionsTooAndRejectsASessionOwnedByAnotherUser() throws Exception {
        var first = token(actor, OLD);
        var expired = token(actor, OLD);
        var otherToken = token(other, OLD);
        var principal = authentication.authenticate(first);
        var expiredSession = authentication.authenticate(expired).sessionId();
        var otherPrincipal = authentication.authenticate(otherToken);
        setActor(new SessionPrincipal(otherPrincipal.sessionId(), actor.getUserId(), principal.roles(), principal.permissions()));
        assertThrows(AuthException.class, () -> passwords.change(actor.getUserId(), new PasswordChange(OLD, NEW)));
        assertTrue(encoder.matches(OLD, hash(actor)));
        jdbc.update("update auth_sessions set created_at=current_timestamp-interval '2 hours', "
                + "expires_at=current_timestamp-interval '1 hour' where session_id=?", expiredSession);
        change(first, OLD, NEW).andExpect(status().isNoContent());
        assertEquals(0, liveSessions(actor));
        authentication.authenticate(otherToken);
    }

    // Latches fix transaction order; PostgreSQL's lock graph proves the second operation really waited.
    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(15, TimeUnit.SECONDS), "Timed out waiting for transaction checkpoint"); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
    }

    private void awaitDatabaseLock(int pid) {
        var deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            if (Boolean.TRUE.equals(jdbc.queryForObject("select cardinality(pg_blocking_pids(?)) > 0", Boolean.class, pid))) {
                return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        fail("Concurrent request did not wait on the PostgreSQL user lock");
    }

    @Test
    void loginWinningLockCreatesASessionThatPasswordChangeThenRevokes() throws Exception {
        var existingToken = token(actor, OLD);
        var principal = authentication.authenticate(existingToken);
        var loginReady = new CountDownLatch(1);
        var releaseLogin = new CountDownLatch(1);
        var waiterPid = new CompletableFuture<Integer>();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var login = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                var result = auth.login(actor.getEmail(), OLD);
                loginReady.countDown();
                await(releaseLogin);
                return result;
            }));
            try {
                await(loginReady);
                var change = pool.submit(() -> {
                    setActor(principal);
                    try {
                        new TransactionTemplate(transactions).executeWithoutResult(status -> {
                            waiterPid.complete(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                            passwords.change(actor.getUserId(), new PasswordChange(OLD, NEW));
                        });
                    } finally { SecurityContextHolder.clearContext(); }
                });
                awaitDatabaseLock(waiterPid.get(15, TimeUnit.SECONDS));
                releaseLogin.countDown();
                var result = login.get(15, TimeUnit.SECONDS);
                change.get(15, TimeUnit.SECONDS);
                assertThrows(AuthException.class, () -> authentication.authenticate(result.accessToken()));
                assertThrows(AuthException.class, () -> authentication.authenticate(existingToken));
                assertEquals(0, liveSessions(actor));
                login(actor, OLD).andExpect(status().isUnauthorized());
                token(actor, NEW);
            } finally { releaseLogin.countDown(); }
        }
    }

    @Test
    void passwordChangeWinningLockMakesConcurrentOldPasswordLoginFail() throws Exception {
        var existingToken = token(actor, OLD);
        var principal = authentication.authenticate(existingToken);
        var changeReady = new CountDownLatch(1);
        var releaseChange = new CountDownLatch(1);
        var waiterPid = new CompletableFuture<Integer>();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var change = pool.submit(() -> {
                setActor(principal);
                try {
                    new TransactionTemplate(transactions).executeWithoutResult(status -> {
                        passwords.change(actor.getUserId(), new PasswordChange(OLD, NEW));
                        changeReady.countDown();
                        await(releaseChange);
                    });
                } finally { SecurityContextHolder.clearContext(); }
            });
            try {
                await(changeReady);
                var login = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                    waiterPid.complete(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                    return auth.login(actor.getEmail(), OLD);
                }));
                awaitDatabaseLock(waiterPid.get(15, TimeUnit.SECONDS));
                releaseChange.countDown();
                change.get(15, TimeUnit.SECONDS);
                var failure = assertThrows(ExecutionException.class, () -> login.get(15, TimeUnit.SECONDS));
                assertInstanceOf(AuthException.class, failure.getCause());
                assertEquals(0, liveSessions(actor));
                assertThrows(AuthException.class, () -> authentication.authenticate(existingToken));
                token(actor, NEW);
            } finally { releaseChange.countDown(); }
        }
    }
}
