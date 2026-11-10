package com.toeic.backend.assessment.application;

import com.toeic.backend.identity.application.SessionPrincipal;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component("practiceAccess")
public class PracticeAccess {
    public boolean isActor(UUID actorId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof SessionPrincipal actor
                && actor.userId().equals(actorId);
    }
}
