package com.toeic.backend.identity.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "auth_sessions")
public class AuthSession {

    @Id
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    protected AuthSession() {
    }

    public AuthSession(User user, String tokenHash, OffsetDateTime now, OffsetDateTime expiresAt) {
        if (user == null || tokenHash == null || !tokenHash.matches("[a-f0-9]{64}")
                || now == null || expiresAt == null || !expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("Invalid session data");
        }
        this.sessionId = UUID.randomUUID();
        this.user = user;
        this.tokenHash = tokenHash;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(OffsetDateTime now) {
        return revokedAt == null && !now.isBefore(createdAt) && now.isBefore(expiresAt);
    }

    public UUID getSessionId() { return sessionId; }
    public User getUser() { return user; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
}
