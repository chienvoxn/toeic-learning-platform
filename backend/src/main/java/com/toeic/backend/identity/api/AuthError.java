package com.toeic.backend.identity.api;

import java.util.List;
import java.util.UUID;

public record AuthError(String code, String message, String traceId, List<Object> details) {
    public static AuthError of(String code, String message) {
        return new AuthError(code, message, UUID.randomUUID().toString(), List.of());
    }
}
