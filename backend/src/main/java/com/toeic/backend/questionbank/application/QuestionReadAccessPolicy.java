package com.toeic.backend.questionbank.application;

import com.toeic.backend.identity.application.SessionPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Quyền lấy từ Bearer principal được nạp lại từ DB; không có ADMIN bypass. */
@Component
public class QuestionReadAccessPolicy {
    public boolean canRead() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SessionPrincipal)) { return false; }
        boolean role = authentication.getAuthorities().stream().anyMatch(authority ->
                authority.getAuthority().equals("ROLE_TEACHER") || authority.getAuthority().equals("ROLE_ADMIN"));
        return role && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("question:read"));
    }
}
