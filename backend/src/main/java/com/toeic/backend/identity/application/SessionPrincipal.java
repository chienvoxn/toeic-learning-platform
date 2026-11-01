package com.toeic.backend.identity.application;

import java.util.List;
import java.util.UUID;

public record SessionPrincipal(UUID sessionId, UUID userId, List<String> roles, List<String> permissions) {
    public SessionPrincipal {
        roles = List.copyOf(roles);
        permissions = List.copyOf(permissions);
    }
}
