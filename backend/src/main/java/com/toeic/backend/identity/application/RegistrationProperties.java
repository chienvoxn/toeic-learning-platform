package com.toeic.backend.identity.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "app.registration")
public record RegistrationProperties(
        @Min(1) long otpTtlSeconds,
        @Min(1) int maxAttempts,
        @Min(1) long resendCooldownSeconds,
        @NotBlank @Email String mailFrom
) {
}