package com.toeic.backend.identity.application;

import com.toeic.backend.identity.infrastructure.AuthSessionRepository;
import com.toeic.backend.identity.infrastructure.UserRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordChangeService {
    private final UserRepository users;
    private final AuthSessionRepository sessions;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public PasswordChangeService(UserRepository users, AuthSessionRepository sessions,
                                 PasswordEncoder encoder, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Transactional
    @PreAuthorize("hasAuthority('user:password:change:self') and @selfProfileAccess.isActor(#userId)")
    public void change(UUID userId, PasswordChange change) {
        if (change == null) { throw ProfileException.validation(); }
        var user = users.findForUpdate(userId).orElseThrow(AuthException::invalidToken);
        var now = OffsetDateTime.now(clock);
        var principal = (SessionPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        // Recheck after waiting for the user lock: another password change may have revoked this session.
        var session = sessions.findById(principal.sessionId()).orElseThrow(AuthException::invalidToken);
        if (!"ACTIVE".equals(user.getStatus()) || !session.getUser().getUserId().equals(userId)
                || !session.isUsable(now)) { throw AuthException.invalidToken(); }
        if (user.getPasswordHash() == null || !encoder.matches(change.currentPassword(), user.getPasswordHash())) {
            throw ProfileException.currentPasswordInvalid();
        }
        user.changePasswordHash(encoder.encode(change.newPassword()), now);
        sessions.revokeAllForUser(userId, now);
    }
}
