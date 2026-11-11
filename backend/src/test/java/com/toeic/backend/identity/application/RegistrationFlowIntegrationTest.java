package com.toeic.backend.identity.application;

import com.toeic.backend.identity.domain.User;
import com.toeic.backend.identity.infrastructure.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.jpa.show-sql=false", "debug=false"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(RegistrationFlowIntegrationTest.TestBeans.class)
class RegistrationFlowIntegrationTest {
    @TestConfiguration static class TestBeans {
        @Bean @Primary OtpMailService testMail() { return mock(OtpMailService.class); }
        @Bean @Primary java.security.SecureRandom testRandom() {
            return new java.security.SecureRandom() {
                private final java.util.concurrent.atomic.AtomicInteger sequence = new java.util.concurrent.atomic.AtomicInteger();
                @Override public int nextInt(int bound) { return Math.floorMod(sequence.incrementAndGet(), bound); }
            };
        }
    }
    @Autowired RegistrationService registration;
    @Autowired PasswordResetService reset;
    @Autowired UserRepository users;
    @Autowired OtpMailService mail;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired SessionAuthenticationService sessions;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    private String email, password;
    @BeforeEach void prepare() { reset(mail); email = UUID.randomUUID() + "@example.com"; password = UUID.randomUUID().toString(); }
    @AfterEach void cleanup() {
        users.findByEmail(email).ifPresent(user -> {
            var id = user.getUserId();
            for (var table : List.of("auth_sessions", "password_reset_otps", "email_verification_otps", "user_roles")) jdbc.update("delete from " + table + " where user_id=?", id);
            jdbc.update("delete from users where user_id=?", id);
        });
    }
    private User register() { return registration.register(email.toUpperCase(Locale.ROOT), password, " Learner "); }
    private String requestCode() {
        clearInvocations(mail); reset.request(email);
        var code = ArgumentCaptor.forClass(String.class); verify(mail).sendPasswordReset(eq(email), code.capture());
        return code.getValue();
    }
    private void allowResend(User user) { jdbc.update("update password_reset_otps set created_at=created_at-interval '2 minutes' where user_id=?", user.getUserId()); }
    private int count(String table, User user) { return jdbc.queryForObject("select count(*) from " + table + " where user_id=?", Integer.class, user.getUserId()); }

    @Test void registrationIsActiveWithoutEmailOtpAndCanLoginImmediately() {
        var user = register();
        var stored = users.findByEmail(email).orElseThrow();
        assertEquals("ACTIVE", stored.getStatus()); assertNull(stored.getEmailVerifiedAt());
        assertEquals("Learner", stored.getFullName()); assertTrue(encoder.matches(password, stored.getPasswordHash()));
        assertEquals(0, count("email_verification_otps", user)); assertEquals(0, count("password_reset_otps", user));
        assertNotNull(auth.login(email, password).accessToken()); verifyNoInteractions(mail);
        assertEquals("EMAIL_EXISTS", assertThrows(RegistrationException.class, this::register).getCode());
    }
    @Test void registrationWorksWhenSmtpIsUnavailable() {
        doThrow(new RegistrationException("MAIL_UNAVAILABLE", "Test mail failure", HttpStatus.SERVICE_UNAVAILABLE)).when(mail).sendPasswordReset(anyString(), anyString());
        assertEquals("ACTIVE", register().getStatus()); verifyNoInteractions(mail);
    }
    @Test void resetOtpIsHashedSingleUseAndRevokesAllSessions() {
        var user = register(); var first = auth.login(email, password); var second = auth.login(email, password);
        var code = requestCode();
        String stored = jdbc.queryForObject("select otp_hash from password_reset_otps where user_id=?", String.class, user.getUserId());
        assertNotEquals(code, stored); assertTrue(encoder.matches(code, stored));
        var next = UUID.randomUUID().toString(); reset.reset(email, code, next);
        assertThrows(AuthException.class, () -> auth.login(email, password)); assertNotNull(auth.login(email, next).accessToken());
        assertThrows(AuthException.class, () -> sessions.authenticate(first.accessToken()));
        assertThrows(AuthException.class, () -> sessions.authenticate(second.accessToken()));
        assertThrows(OtpRejectedException.class, () -> reset.reset(email, code, UUID.randomUUID().toString()));
        assertNotNull(users.findByEmail(email).orElseThrow().getEmailVerifiedAt());
    }
    @Test void failedAttemptCountersCommitAndStopBruteForce() {
        var user = register(); var code = requestCode(); var wrong = code.equals("000000") ? "999999" : "000000";
        for (int attempt = 0; attempt < 5; attempt++) assertEquals("PASSWORD_RESET_INVALID", assertThrows(OtpRejectedException.class, () -> reset.reset(email, wrong, password)).getCode());
        assertEquals(5, jdbc.queryForObject("select failed_attempts from password_reset_otps where user_id=?", Integer.class, user.getUserId()));
        assertThrows(OtpRejectedException.class, () -> reset.reset(email, code, password));
        assertNotNull(auth.login(email, password).accessToken());
    }
    @Test void resendEnforcesCooldownAndInvalidatesPreviousCode() {
        var user = register(); var first = requestCode();
        reset.request(email); verify(mail, times(1)).sendPasswordReset(eq(email), anyString());
        allowResend(user); var second = requestCode();
        if (first.equals(second)) { allowResend(user); second = requestCode(); }
        var newest = second;
        assertThrows(OtpRejectedException.class, () -> reset.reset(email, first, password));
        reset.reset(email, newest, UUID.randomUUID().toString());
    }
    @Test void expiredCodeDoesNotChangePassword() {
        var user = register(); var code = requestCode();
        jdbc.update("update password_reset_otps set created_at=now()-interval '10 minutes',expires_at=now()-interval '5 minutes' where user_id=?", user.getUserId());
        assertThrows(OtpRejectedException.class, () -> reset.reset(email, code, UUID.randomUUID().toString()));
        assertNotNull(auth.login(email, password).accessToken());
    }
    @Test void legacyRegistrationOtpCannotResetPassword() {
        var user = register();
        jdbc.update("insert into email_verification_otps(otp_id,user_id,otp_hash,created_at,expires_at) values (?,?,?,now(),now()+interval '5 minutes')", UUID.randomUUID(), user.getUserId(), encoder.encode("123456"));
        assertThrows(OtpRejectedException.class, () -> reset.reset(email, "123456", UUID.randomUUID().toString()));
    }
    @Test void invalidPasswordDoesNotConsumeValidCode() {
        register(); var code = requestCode();
        assertThrows(RegistrationException.class, () -> reset.reset(email, code, "ậ".repeat(30)));
        reset.reset(email, code, UUID.randomUUID().toString());
    }
    @Test void unknownBlockedAndGoogleOnlyAccountsDoNotSendResetMail() {
        reset.request(email); verifyNoInteractions(mail);
        var user = register(); jdbc.update("update users set status='BLOCKED' where user_id=?", user.getUserId());
        reset.request(email); verifyNoInteractions(mail);
        jdbc.update("update users set status='ACTIVE',password_hash=null where user_id=?", user.getUserId());
        reset.request(email); verifyNoInteractions(mail);
    }
    @Test void mailFailureRollsBackOtpAndPublicResponseDoesNotExposeAccount() throws Exception {
        var user = register();
        doThrow(new RegistrationException("MAIL_UNAVAILABLE", "Mail failed", HttpStatus.SERVICE_UNAVAILABLE)).when(mail).sendPasswordReset(anyString(), anyString());
        String existing = mvc.perform(post("/api/v1/auth/forgot-password").contentType(APPLICATION_JSON).content(json.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String missing = mvc.perform(post("/api/v1/auth/forgot-password").contentType(APPLICATION_JSON).content(json.writeValueAsString(Map.of("email", "unknown-" + email))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(existing, missing); assertEquals(0, count("password_reset_otps", user));
    }
}
