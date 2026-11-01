package com.toeic.backend.identity.application;

import com.toeic.backend.identity.domain.AuthSession;
import com.toeic.backend.identity.infrastructure.AuthSessionRepository;
import com.toeic.backend.identity.infrastructure.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final AuthSessionRepository sessions;
    private final PasswordEncoder encoder;
    private final SessionToken tokens;
    private final AuthProperties properties;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthService(UserRepository users, AuthSessionRepository sessions, PasswordEncoder encoder,
                       SessionToken tokens, AuthProperties properties, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.encoder = encoder;
        this.tokens = tokens;
        this.properties = properties;
        this.clock = clock;
        this.dummyPasswordHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public LoginResult login(String email, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw AuthException.invalidCredentials();
        }
        var user = users.findByEmailIgnoreCase(EmailVerificationService.normalizeEmail(email));
        boolean matches = encoder.matches(password,
                user.isPresent() ? user.get().getPasswordHash() : dummyPasswordHash);
        if (!matches || user.isEmpty() || !"ACTIVE".equals(user.get().getStatus())) {
            throw AuthException.invalidCredentials();
        }
        var now = OffsetDateTime.now(clock);
        var rawToken = tokens.generate();
        var session = new AuthSession(user.get(), tokens.hash(rawToken), now,
                now.plusSeconds(properties.sessionTtlSeconds()));
        sessions.save(session);
        user.get().recordLogin(now);
        return new LoginResult(rawToken, "Bearer", properties.sessionTtlSeconds(), UserProfile.from(user.get()));
    }

    @Transactional
    public void logout(UUID sessionId) {
        sessions.revoke(sessionId, OffsetDateTime.now(clock));
    }

    public record LoginResult(String accessToken, String tokenType, long expiresIn, UserProfile user) {
        @Override
        public String toString() {
            return "LoginResult[tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
        }
    }
}
