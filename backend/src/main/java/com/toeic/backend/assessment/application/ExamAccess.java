package com.toeic.backend.assessment.application;

import com.toeic.backend.identity.application.SessionPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component("examAccess")
public class ExamAccess {
    public boolean actor(UUID id) {
        var a=SecurityContextHolder.getContext().getAuthentication();
        return a!=null && a.isAuthenticated() && a.getPrincipal() instanceof SessionPrincipal p && p.userId().equals(id);
    }
    public boolean has(String permission) {
        var a=SecurityContextHolder.getContext().getAuthentication();
        return a!=null && a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals(permission));
    }
}
