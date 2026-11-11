package com.toeic.backend.identity.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth.google")
public record GoogleAuthProperties(String clientId, boolean secureCookie) {
    public boolean enabled() { return clientId != null && !clientId.isBlank(); }
}
