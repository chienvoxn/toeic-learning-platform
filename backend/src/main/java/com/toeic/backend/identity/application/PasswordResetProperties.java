package com.toeic.backend.identity.application;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.password-reset")
public record PasswordResetProperties(@Min(1) long otpTtlSeconds, @Min(1) int maxAttempts,
        @Min(1) long resendCooldownSeconds, @NotBlank @Email String mailFrom) { }
