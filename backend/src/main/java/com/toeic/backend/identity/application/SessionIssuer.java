package com.toeic.backend.identity.application;

import com.toeic.backend.identity.domain.AuthSession;
import com.toeic.backend.identity.domain.LearningProfile;
import com.toeic.backend.identity.domain.User;
import com.toeic.backend.identity.infrastructure.AuthSessionRepository;
import com.toeic.backend.identity.infrastructure.LearningProfileRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Cấp cùng một loại phiên opaque cho đăng nhập mật khẩu và Google. */
@Component
public class SessionIssuer {
    private final AuthSessionRepository sessions;
    private final LearningProfileRepository profiles;
    private final SessionToken tokens;
    private final AuthProperties properties;
    private final Clock clock;

    public SessionIssuer(AuthSessionRepository sessions, LearningProfileRepository profiles,
                         SessionToken tokens, AuthProperties properties, Clock clock) {
        this.sessions = sessions; this.profiles = profiles; this.tokens = tokens;
        this.properties = properties; this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AuthService.LoginResult issue(User user) {
        // PostgreSQL timestamps have microsecond precision; never round creation into the future.
        var now = OffsetDateTime.now(clock).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        user.recordLogin(now);
        var token = tokens.generate();
        sessions.save(new AuthSession(user, tokens.hash(token), now, now.plusSeconds(properties.sessionTtlSeconds())));
        var target = profiles.findByUserId(user.getUserId()).map(LearningProfile::getTargetScore).orElse(null);
        return new AuthService.LoginResult(token, "Bearer", properties.sessionTtlSeconds(), UserProfile.from(user, target));
    }
}
