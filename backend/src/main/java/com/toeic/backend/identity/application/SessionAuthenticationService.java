package com.toeic.backend.identity.application;

import com.toeic.backend.identity.infrastructure.AuthSessionRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionAuthenticationService {
    private final AuthSessionRepository sessions;
    private final SessionToken tokens;
    private final Clock clock;

    public SessionAuthenticationService(AuthSessionRepository sessions, SessionToken tokens, Clock clock) {
        this.sessions = sessions;
        this.tokens = tokens;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SessionPrincipal authenticate(String rawToken) {
        var session = sessions.findByTokenHash(tokens.hash(rawToken))
                .orElseThrow(AuthException::invalidToken);
        var now = OffsetDateTime.now(clock);
        if (!session.isUsable(now) || !"ACTIVE".equals(session.getUser().getStatus())) {
            if (!now.isBefore(session.getExpiresAt())) {
                throw new AuthException("TOKEN_EXPIRED", "Phien dang nhap da het han. Vui long dang nhap lai.");
            }
            throw AuthException.invalidToken();
        }
        return new SessionPrincipal(session.getSessionId(), session.getUser().getUserId(),
                session.getUser().getRoles().stream().map(role -> role.getName()).sorted().toList());
    }
}
