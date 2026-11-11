package com.toeic.backend.vocabulary.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "vocabulary_catalog_enrollments")
public class VocabularyCatalogEnrollment {

    @Id
    @Column(name = "enrollment_id", nullable = false)
    private UUID enrollmentId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "system_deck_id", nullable = false)
    private UUID systemDeckId;

    @Column(name = "personal_deck_id", nullable = false)
    private UUID personalDeckId;

    @Column(name = "enrolled_at", nullable = false)
    private OffsetDateTime enrolledAt;

    protected VocabularyCatalogEnrollment() {}

    public VocabularyCatalogEnrollment(
            UUID userId, UUID systemDeckId, UUID personalDeckId) {
        this.enrollmentId = UUID.randomUUID();
        this.userId = userId;
        this.systemDeckId = systemDeckId;
        this.personalDeckId = personalDeckId;
    }

    @PrePersist
    void onCreate() {
        if (enrolledAt == null) {
            enrolledAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public UUID getPersonalDeckId() {
        return personalDeckId;
    }

    public UUID getSystemDeckId() {
        return systemDeckId;
    }

    public UUID getUserId() {
        return userId;
    }
}
