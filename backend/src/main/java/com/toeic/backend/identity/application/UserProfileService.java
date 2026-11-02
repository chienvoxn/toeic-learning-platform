package com.toeic.backend.identity.application;

import com.toeic.backend.identity.infrastructure.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {
    private final UserRepository users;

    public UserProfileService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('user:read:self') and #userId == authentication.principal.userId")
    public UserProfile me(UUID userId) {
        var user = users.findByUserId(userId).orElseThrow(AuthException::invalidToken);
        if (!"ACTIVE".equals(user.getStatus())) {
            throw AuthException.invalidToken();
        }
        return UserProfile.from(user);
    }
}
