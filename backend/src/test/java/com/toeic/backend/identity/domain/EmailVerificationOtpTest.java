package com.toeic.backend.identity.domain;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

class EmailVerificationOtpTest {

    private final OffsetDateTime issuedAt =
            OffsetDateTime.parse("2026-10-31T15:00:00Z");

    private EmailVerificationOtp createOtp() {
        User user = new User(
                "learner@example.com",
                "test-password-hash",
                "Learner"
        );

        return new EmailVerificationOtp(
                user,
                "test-otp-hash",
                issuedAt,
                issuedAt.plusMinutes(5)
        );
    }

    @Test
    void validOtpCanBeUsed() {
        EmailVerificationOtp otp = createOtp();

        assertTrue(otp.canUse(issuedAt.plusMinutes(1), 5));
        assertEquals(0, otp.getFailedAttempts());
        assertNull(otp.getUsedAt());
    }

    @Test
    void otpExpiresAtExactExpiryTime() {
        EmailVerificationOtp otp = createOtp();
        OffsetDateTime expiry = issuedAt.plusMinutes(5);

        assertTrue(otp.canUse(expiry.minusNanos(1), 5));
        assertFalse(otp.canUse(expiry, 5));

        assertThrows(
                IllegalStateException.class,
                () -> otp.markUsed(expiry, 5)
        );

        assertNull(otp.getUsedAt());
    }

    @Test
    void usedOtpCannotBeUsedAgain() {
        EmailVerificationOtp otp = createOtp();
        OffsetDateTime verifiedAt = issuedAt.plusMinutes(1);

        otp.markUsed(verifiedAt, 5);

        assertEquals(verifiedAt, otp.getUsedAt());
        assertFalse(otp.canUse(issuedAt.plusMinutes(2), 5));

        assertThrows(
                IllegalStateException.class,
                () -> otp.markUsed(issuedAt.plusMinutes(2), 5)
        );
    }

    @Test
    void otpIsBlockedAfterMaximumFailedAttempts() {
        EmailVerificationOtp otp = createOtp();
        OffsetDateTime now = issuedAt.plusMinutes(1);

        for (int i = 0; i < 5; i++) {
            otp.recordFailedAttempt(now, 5);
        }

        assertEquals(5, otp.getFailedAttempts());
        assertFalse(otp.canUse(now, 5));

        assertThrows(
                IllegalStateException.class,
                () -> otp.markUsed(now, 5)
        );

        assertThrows(
                IllegalStateException.class,
                () -> otp.recordFailedAttempt(now, 5)
        );

        assertEquals(5, otp.getFailedAttempts());
    }

    @Test
    void equivalentTimezoneOffsetsGiveSameExpiryResult() {
        EmailVerificationOtp otp = createOtp();

        assertTrue(otp.canUse(
                OffsetDateTime.parse("2026-10-31T22:04:00+07:00"),
                5
        ));

        assertFalse(otp.canUse(
                OffsetDateTime.parse("2026-10-31T22:05:00+07:00"),
                5
        ));
    }
}