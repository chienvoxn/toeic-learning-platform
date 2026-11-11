package com.toeic.backend.identity.application;

import com.toeic.backend.identity.domain.User;
import com.toeic.backend.identity.infrastructure.*;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SessionIssuerTest {
    @Test void timestampsMatchPostgresPrecisionAndCannotRoundIntoTheFuture() {
        var instant = Instant.parse("2026-11-11T01:00:00.123456789Z");
        var now = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
        var user = new User("learner@example.com", "test-only-hash", "Learner");
        user.activateWithoutEmailVerification(now);
        var sessions = mock(AuthSessionRepository.class);
        var profiles = mock(LearningProfileRepository.class);
        when(profiles.findByUserId(user.getUserId())).thenReturn(Optional.empty());
        var tokens = new SessionToken(new java.security.SecureRandom());
        var issuer = new SessionIssuer(sessions, profiles, tokens, new AuthProperties(900), Clock.fixed(instant, ZoneOffset.UTC));
        issuer.issue(user);
        verify(sessions).save(argThat(session -> session.getExpiresAt().getNano() % 1000 == 0
                && session.getExpiresAt().equals(now.truncatedTo(java.time.temporal.ChronoUnit.MICROS).plusSeconds(900))
                && session.isUsable(now)));
    }
}
