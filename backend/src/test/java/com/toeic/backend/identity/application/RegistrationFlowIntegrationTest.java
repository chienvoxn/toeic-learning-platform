package com.toeic.backend.identity.application;

import com.toeic.backend.identity.infrastructure.OtpMailService;
import com.toeic.backend.identity.infrastructure.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "spring.jpa.show-sql=false")
@Import(RegistrationFlowIntegrationTest.TestBeans.class)
class RegistrationFlowIntegrationTest {

    @TestConfiguration
    static class TestBeans {

        @Bean
        @Primary
        OtpMailService testMail() {
            return mock(OtpMailService.class);
        }

        @Bean
        @Primary
        SecureRandom testRandom() {
            return mock(SecureRandom.class);
        }
    }

    @Autowired
    RegistrationService registration;

    @Autowired
    EmailVerificationService verification;

    @Autowired
    UserRepository users;

    @Autowired
    OtpMailService mail;

    @Autowired
    SecureRandom random;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    JdbcTemplate jdbc;

    private final String email = UUID.randomUUID() + "@example.com";
    private final String password = "TestPass123!";

    @BeforeEach
    void prepare() {
        reset(mail, random);
        when(random.nextInt(1_000_000)).thenReturn(123456, 654321);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("""
                delete from email_verification_otps
                where user_id in (
                    select user_id from users where email = ?
                )
                """, email);

        jdbc.update("""
                delete from user_roles
                where user_id in (
                    select user_id from users where email = ?
                )
                """, email);

        jdbc.update("delete from users where email = ?", email);
    }

    @Test
    void registrationHashesPasswordAndOtpAndAssignsLearner() {
        var user = registration.register(
                email.toUpperCase(Locale.ROOT),
                password,
                " Learner "
        );

        var stored = users.findByEmail(email).orElseThrow();

        assertEquals("PENDING", stored.getStatus());
        assertEquals("Learner", stored.getFullName());
        assertNotEquals(password, stored.getPasswordHash());
        assertTrue(encoder.matches(password, stored.getPasswordHash()));

        String otpHash = jdbc.queryForObject("""
                select otp_hash from email_verification_otps
                where user_id = ?
                """, String.class, user.getUserId());

        assertNotEquals("123456", otpHash);
        assertTrue(encoder.matches("123456", otpHash));

        Long roleCount = jdbc.queryForObject("""
                select count(*)
                from user_roles ur
                join roles r on r.role_id = ur.role_id
                where ur.user_id = ? and r.name = 'LEARNER'
                """, Long.class, user.getUserId());

        assertEquals(1L, roleCount.longValue());
        verify(mail).sendVerification(email, "123456");

        var error = assertThrows(
                RegistrationException.class,
                () -> registration.register(
                        email.toUpperCase(Locale.ROOT),
                        password,
                        "Other"
                )
        );

        assertEquals("EMAIL_EXISTS", error.getCode());
    }

    @Test
    void incorrectAttemptCountCommitsEvenWhenVerificationThrows() {
        var user = registration.register(email, password, "Learner");

        for (int i = 0; i < 5; i++) {
            var error = assertThrows(
                    OtpRejectedException.class,
                    () -> verification.verify(email, "000000")
            );
            assertEquals("OTP_INVALID", error.getCode());
        }

        Integer attempts = jdbc.queryForObject("""
                select failed_attempts from email_verification_otps
                where user_id = ?
                """, Integer.class, user.getUserId());

        assertEquals(5, attempts.intValue());

        var error = assertThrows(
                OtpRejectedException.class,
                () -> verification.verify(email, "123456")
        );

        assertEquals("OTP_UNAVAILABLE", error.getCode());
        assertEquals(
                "PENDING",
                users.findByEmail(email).orElseThrow().getStatus()
        );
    }

    @Test
    void correctCodeActivatesAccountAndCannotBeReused() {
        var user = registration.register(email, password, "Learner");

        verification.verify(email, "123456");

        var stored = users.findByEmail(email).orElseThrow();
        assertEquals("ACTIVE", stored.getStatus());
        assertNotNull(stored.getEmailVerifiedAt());

        Integer consumed = jdbc.queryForObject("""
                select count(*) from email_verification_otps
                where user_id = ? and used_at is not null
                """, Integer.class, user.getUserId());

        assertEquals(1, consumed.intValue());

        assertThrows(
                OtpRejectedException.class,
                () -> verification.verify(email, "123456")
        );
    }

    @Test
    void resendInvalidatesPreviousCodeAndAppliesCooldown() {
        var user = registration.register(email, password, "Learner");

        var error = assertThrows(
                RegistrationException.class,
                () -> verification.resend(email)
        );

        assertEquals("OTP_RESEND_TOO_SOON", error.getCode());

        jdbc.update("""
                update email_verification_otps
                set created_at = created_at - interval '2 minutes'
                where user_id = ?
                """, user.getUserId());

        verification.resend(email);
        verify(mail).sendVerification(email, "654321");

        Integer invalidated = jdbc.queryForObject("""
                select count(*) from email_verification_otps
                where user_id = ? and used_at is not null
                """, Integer.class, user.getUserId());

        assertEquals(1, invalidated.intValue());

        assertThrows(
                OtpRejectedException.class,
                () -> verification.verify(email, "123456")
        );

        verification.verify(email, "654321");

        assertEquals(
                "ACTIVE",
                users.findByEmail(email).orElseThrow().getStatus()
        );
    }

    @Test
    void expiredCodeDoesNotActivateUser() {
        var user = registration.register(email, password, "Learner");

        jdbc.update("""
                update email_verification_otps
                set created_at = current_timestamp - interval '10 minutes',
                    expires_at = current_timestamp - interval '5 minutes'
                where user_id = ?
                """, user.getUserId());

        var error = assertThrows(
                OtpRejectedException.class,
                () -> verification.verify(email, "123456")
        );

        assertEquals("OTP_EXPIRED", error.getCode());
        assertEquals(
                "PENDING",
                users.findByEmail(email).orElseThrow().getStatus()
        );
    }

    @Test
    void smtpFailureRollsBackRegistration() {
        doThrow(new RegistrationException(
                "MAIL_UNAVAILABLE",
                "Test SMTP failure",
                HttpStatus.SERVICE_UNAVAILABLE
        )).when(mail).sendVerification(anyString(), anyString());

        assertThrows(
                RegistrationException.class,
                () -> registration.register(email, password, "Learner")
        );

        assertFalse(users.existsByEmail(email));
    }

    @Test
    void unknownResendDoesNotSendAnything() {
        verification.resend(email);

        verifyNoInteractions(mail);
    }
}