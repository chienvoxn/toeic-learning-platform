
package com.toeic.backend.vocabulary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "vocabulary_decks")
public class VocabularyDeck {

    @Id
    @Column(name = "deck_id", nullable = false)
    private UUID deckId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected VocabularyDeck() {
    }

    public VocabularyDeck(UUID userId, String name, String description) {
        this.deckId = UUID.randomUUID();
        this.userId = userId;
        this.name = name;
        this.description = description;
    }

    @PrePersist
    protected void onCreate() {
        if (deckId == null) {
            deckId = UUID.randomUUID();
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        if (createdAt == null) {
            createdAt = now;
        }

        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getDeckId() {
        return deckId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
