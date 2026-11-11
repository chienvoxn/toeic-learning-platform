package com.toeic.backend.identity.application;

import com.toeic.backend.identity.domain.PasswordPolicy;
import com.toeic.backend.identity.infrastructure.AuthSessionRepository;
import com.toeic.backend.identity.infrastructure.UserRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final AuthSessionRepository sessions;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final String dummyPasswordHash;
    private final SessionIssuer issuer;

    public AuthService(UserRepository users, AuthSessionRepository sessions, PasswordEncoder encoder,
                       Clock clock, SessionIssuer issuer) {
        this.users = users;
        this.sessions = sessions;
        this.encoder = encoder;
        this.clock = clock;
        this.dummyPasswordHash = encoder.encode(UUID.randomUUID().toString());
        this.issuer = issuer;
    }

    @Transactional
    public LoginResult login(String email, String password) {
        if (!PasswordPolicy.fitsBcrypt(password)) {
            throw AuthException.invalidCredentials();
        }
        var user = users.findForLogin(com.toeic.backend.identity.domain.EmailAddress.normalize(email));
        boolean matches = encoder.matches(password,
                user.isPresent() && user.get().getPasswordHash() != null ? user.get().getPasswordHash() : dummyPasswordHash);
        if (!matches || user.isEmpty() || !"ACTIVE".equals(user.get().getStatus())) {
            throw AuthException.invalidCredentials();
        }
        return issuer.issue(user.get());
    }

    @Transactional
    @PreAuthorize("isAuthenticated() and #sessionId == authentication.principal.sessionId")
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
