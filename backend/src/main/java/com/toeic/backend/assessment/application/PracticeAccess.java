package com.toeic.backend.assessment.application;

import com.toeic.backend.identity.application.SessionPrincipal;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.security.access.AccessDeniedException;

@Component("practiceAccess")
public class PracticeAccess {
    public boolean isActor(UUID actorId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof SessionPrincipal actor
                && actor.userId().equals(actorId);
    }
    public void requireSource(UUID actorId,String sourceType) {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        String permission=switch(sourceType) {
            case "PRACTICE_SNAPSHOT"->"practice:manage:self";
            case "SECTION_SNAPSHOT"->"exam:manage:self";
            default->throw PracticeException.missing();
        };
        if(!isActor(actorId)||auth.getAuthorities().stream().noneMatch(a->a.getAuthority().equals(permission))) {
            throw new AccessDeniedException("Attempt permission required");
        }
    }
}
