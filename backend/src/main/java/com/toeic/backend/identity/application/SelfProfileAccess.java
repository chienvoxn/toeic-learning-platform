package com.toeic.backend.identity.application;

import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SelfProfileAccess {
    public boolean isActor(UUID userId) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof SessionPrincipal principal
                && userId != null && userId.equals(principal.userId());
    }
}
