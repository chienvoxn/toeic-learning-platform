package com.toeic.backend.identity.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "email_verification_otps")
public class EmailVerificationOtp {

    @Id
    @Column(name = "otp_id", nullable = false)
    private UUID otpId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "otp_hash", nullable = false, length = 255)
    private String otpHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected EmailVerificationOtp() {
    }

    public EmailVerificationOtp(
            User user,
            String otpHash,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt
    ) {
        this.user = Objects.requireNonNull(user, "User is required");
        this.otpHash = Objects.requireNonNull(
                otpHash, "OTP hash is required");
        this.createdAt = Objects.requireNonNull(
                createdAt, "Creation time is required");
        this.expiresAt = Objects.requireNonNull(
                expiresAt, "Expiry time is required");

        if (otpHash.isBlank() || otpHash.length() > 255) {
            throw new IllegalArgumentException(
                    "OTP hash must contain 1 to 255 characters");
        }

        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException(
                    "Expiry must be after creation time");
        }

        this.otpId = UUID.randomUUID();
        this.failedAttempts = 0;
    }

    public boolean canUse(OffsetDateTime now, int maxAttempts) {
        Objects.requireNonNull(now, "Current time is required");

        if (maxAttempts < 1) {
            throw new IllegalArgumentException(
                    "Maximum attempts must be positive");
        }

        return usedAt == null
                && !now.isBefore(createdAt)
                && now.isBefore(expiresAt)
                && failedAttempts < maxAttempts;
    }

    public void recordFailedAttempt(
            OffsetDateTime now, int maxAttempts
    ) {
        requireUsable(now, maxAttempts);
        this.failedAttempts++;
    }

    public void markUsed(OffsetDateTime now, int maxAttempts) {
        requireUsable(now, maxAttempts);
        this.usedAt = now;
    }

    private void requireUsable(
            OffsetDateTime now, int maxAttempts
    ) {
        if (!canUse(now, maxAttempts)) {
            throw new IllegalStateException(
                    "OTP is expired, used or unavailable");
        }
    }

    public UUID getOtpId() {
        return otpId;
    }

    public User getUser() {
        return user;
    }

    public String getOtpHash() {
        return otpHash;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getUsedAt() {
        return usedAt;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}