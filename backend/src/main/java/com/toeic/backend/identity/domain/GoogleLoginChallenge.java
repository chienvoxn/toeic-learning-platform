package com.toeic.backend.identity.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "google_login_challenges")
public class GoogleLoginChallenge {
    @Id @Column(name = "challenge_id") private UUID id;
    @Column(name = "nonce_hash", nullable = false, length = 64) private String nonceHash;
    @Column(name = "binding_hash", nullable = false, length = 64) private String bindingHash;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "expires_at", nullable = false) private OffsetDateTime expiresAt;
    @Column(name = "consumed_at") private OffsetDateTime consumedAt;
    protected GoogleLoginChallenge() { }
    public GoogleLoginChallenge(UUID id, String nonceHash, String bindingHash, OffsetDateTime now) {
        this.id = id; this.nonceHash = nonceHash; this.bindingHash = bindingHash;
        this.createdAt = now; this.expiresAt = now.plusMinutes(5);
    }
    public String getNonceHash() { return nonceHash; }
    public String getBindingHash() { return bindingHash; }
    public boolean usable(OffsetDateTime now) { return consumedAt == null && now.isBefore(expiresAt); }
    public void consume(OffsetDateTime now) { consumedAt = now; }
}
