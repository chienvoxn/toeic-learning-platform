package com.toeic.backend.content.application;

import com.toeic.backend.identity.application.SessionPrincipal;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Chỉ lấy quyền từ SecurityContext do BearerSessionFilter đã xác thực. */
@Component
public class ContentAccessPolicy {
    public boolean isActor(UUID userId) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof SessionPrincipal principal
                && userId != null && userId.equals(principal.userId());
    }

    public boolean canReadMemberContent() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof SessionPrincipal
                && authentication.getAuthorities().stream().anyMatch(grant -> grant.getAuthority().equals("content:read"));
    }
}
