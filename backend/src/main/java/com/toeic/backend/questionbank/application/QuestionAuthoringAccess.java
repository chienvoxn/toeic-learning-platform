package com.toeic.backend.questionbank.application;

import com.toeic.backend.identity.application.SessionPrincipal;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component("questionAuthoringAccess")
public class QuestionAuthoringAccess {
    public boolean isActor(UUID actor) {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        return auth!=null && auth.isAuthenticated() && auth.getPrincipal() instanceof SessionPrincipal principal
                && principal.userId().equals(actor);
    }
    public boolean any() { return has("question:manage:any"); }
    public boolean readMedia() { return has("question:read"); }
    private boolean has(String permission) {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        return auth!=null && auth.getAuthorities().stream().anyMatch(a->permission.equals(a.getAuthority()));
    }
}
