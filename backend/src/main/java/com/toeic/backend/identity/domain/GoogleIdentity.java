package com.toeic.backend.identity.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "google_identities")
public class GoogleIdentity {
    @Id @Column(name = "google_subject", length = 255) private String subject;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true) private User user;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    protected GoogleIdentity() { }
    public GoogleIdentity(String subject, User user, OffsetDateTime now) { this.subject = subject; this.user = user; this.createdAt = now; }
    public User getUser() { return user; }
}
